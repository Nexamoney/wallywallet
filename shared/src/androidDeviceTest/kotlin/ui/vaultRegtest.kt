import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.PayAddress
import org.nexa.threads.millisleep
import repositories.TimeLockContractRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ui.WallyUiTestBase

/**
 * Vault integration run against the regtest node the host publishes on 127.0.0.1 (via `adb reverse`).
 *
 * The host funder script services `VAULTREQ <kind> <address>` lines printed here - paying coin, sending
 * group tokens, or handing over a mint/melt baton - and mines while the run proceeds. Everything the
 * test asserts is read back from the repository, so a scenario passes only if the wallet itself agrees.
 */
class VaultRegtestCoinTest : WallyUiTestBase()
{
    private lateinit var repo: TimeLockContractRepository
    private lateinit var account: Account
    private val failures = mutableListOf<String>()
    private var passed = 0

    private fun log(m: String) = println("$TAG $m")

    /** Ask the host for a deposit into [addr] and wait for the vault's balance to reflect it. */
    private fun request(kind: String, addr: PayAddress?, name: String, want: (TimeLockContractRepository.VaultSnapshot) -> Boolean): Boolean
    {
        log("VAULTREQ $kind $addr")
        for (i in 0 until 40)
        {
            millisleep(1500u)
            repo.refreshBalances()
            val s = repo.snapshot(name) ?: continue
            if (want(s)) return true
        }
        return false
    }

    private fun check(scenario: String, ok: Boolean, detail: String = "")
    {
        if (ok) { passed++; log("PASS $scenario") }
        else { failures += "$scenario $detail"; log("FAIL $scenario $detail") }
    }

    private fun mature(): Long = (repo.currentBlockHeight() ?: 0L)

    @Test
    fun thirtyCoinVaultScenarios()
    {
        wallyApp!!.openAllAccounts()
        account = wallyApp!!.accounts.values.firstOrNull { it.name == ACCT }
            ?: wallyApp!!.newAccount(ACCT, 0U, "", ChainSelector.NEXAREGTEST)!!
        repo = account.timeLockVaults
        runBlocking { repo.start() }

        // Top up: each vault costs its deposit plus a fee, and the run makes fifty of them.
        val fundAddr = repo.nextReceiveAddress()
        log("VAULTREQ nexa $fundAddr")
        for (i in 0 until 60)
        {
            millisleep(2000u)
            if (account.wallet.balance > 400_000L && (repo.currentBlockHeight() ?: 0L) > 0L) break
        }
        log("start balance=${account.wallet.balance} height=${repo.currentBlockHeight()}")
        assertTrue(account.wallet.balance > 100_000L, "regtest account was never funded")

        val payTo = repo.nextReceiveAddress()!!

        // --- 1..30: plain coin vaults, claimed one at a time while siblings stay funded -------------
        var made = 0
        val live = mutableListOf<String>()
        for (n in 1..30)
        {
            val created = try { repo.addVault(mature(), VAULT_SAT) }
                catch (e: Throwable) { check("coin-$n create", false, "$e"); continue }
            made++
            live += created.name
            // Claim only the oldest live vault, so most of the run has several mature vaults side by side.
            if (live.size >= 3)
            {
                val target = live.removeAt(0)
                val before = repo.snapshotAll().filter { it.name in live }.associate { it.name to it.nativeBalanceSat }
                val r = try { repo.claimVault(target, payTo) } catch (e: Throwable) { check("coin-$n claim", false, "$e"); continue }
                repo.refreshBalances()
                val after = repo.snapshotAll().filter { it.name in live }.associate { it.name to it.nativeBalanceSat }
                // A scoped claim must not take a satoshi from another vault. A sibling can still be
                // gaining its own deposit while this runs, so only a decrease is a failure.
                val robbed = before.filter { (nm, bal) -> (after[nm] ?: 0L) < bal }
                check("coin-$n claim $target", r.payoutSat > 0L && robbed.isEmpty(),
                    "payout=${r.payoutSat} robbed=$robbed")
            }
            else check("coin-$n create", created.name.isNotEmpty())
        }
        log("created=$made live=${live.size}")

        log("SUMMARY passed=$passed failed=${failures.size}")
        failures.forEach { log("FAILURE $it") }
        assertEquals(emptyList(), failures, "regtest vault scenarios failed")
    }

    companion object
    {
        const val TAG = "VAULTTEST"
        const val ACCT = "vaulttest"
        const val VAULT_SAT = 2000L
    }
}
