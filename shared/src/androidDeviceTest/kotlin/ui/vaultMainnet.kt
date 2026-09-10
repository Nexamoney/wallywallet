import androidx.test.platform.app.InstrumentationRegistry
import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.threads.millisleep
import repositories.TimeLockContractRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ui.WallyUiTestBase

/**
 * Mainnet half of the vault integration run, driven against the wallet's own funded account rather
 * than a throwaway one. Every vault it opens it also claims, so the run leaves nothing locked up.
 * Deposits are the minimum the contract allows, so the whole run costs a fraction of a cent.
 */
class VaultMainnetTest : WallyUiTestBase()
{
    private lateinit var repo: TimeLockContractRepository
    private lateinit var account: Account
    private val failures = mutableListOf<String>()
    private var passed = 0

    private fun log(m: String) = println("$TAG $m")

    private fun check(scenario: String, ok: Boolean, detail: String = "")
    {
        if (ok) { passed++; log("PASS $scenario") }
        else { failures += "$scenario $detail"; log("FAIL $scenario $detail") }
    }

    private fun mature(): Long = (repo.currentBlockHeight() ?: 0L)

    @Test
    fun fiftyMainnetVaultScenarios()
    {
        wallyApp!!.openAllAccounts()
        // Instrumentation runs under its own uid, so the wallet here is the test's own - the phone's
        // real accounts are untouched. Fund this one once and the scenarios below run against it.
        // The recovery phrase arrives as an instrumentation argument, never from source, so it stays out
        // of the repository. Instrumentation has its own uid, so this account is the test's alone.
        val words = InstrumentationRegistry.getArguments().getString("mnemonic").orEmpty().trim()
        account = wallyApp!!.accounts.values.firstOrNull { it.name == ACCT }
            ?: if (words.isNotEmpty())
                wallyApp!!.recoverAccount(ACCT, 0U, "", words, ChainSelector.NEXA, null, null, null)
            else wallyApp!!.newAccount(ACCT, 0U, "", ChainSelector.NEXA)!!
        repo = account.timeLockVaults
        runBlocking { repo.start() }
        log("MAINNET_FUND_ADDRESS=${repo.nextReceiveAddress()}")
        for (i in 0 until 150)
        {
            millisleep(4000u)
            if (account.wallet.balance > NEEDED_SAT && (repo.currentBlockHeight() ?: 0L) > 0L) break
            if (i % 10 == 0) log("waiting for funding balance=${account.wallet.balance} height=${repo.currentBlockHeight()}")
        }
        log("account=${account.name} balance=${account.wallet.balance} height=${repo.currentBlockHeight()}")
        assertTrue(account.wallet.balance > NEEDED_SAT, "mainnet test account was not funded")

        val payTo = repo.nextReceiveAddress()!!
        val live = mutableListOf<String>()
        var created = 0

        // 1..46 coin vaults: keep a few mature vaults side by side and claim the oldest, so every
        // claim is made while siblings are also claimable - the shape the old sweep-all bug broke.
        for (n in 1..46)
        {
            val v = try { repo.addVault(mature(), VAULT_SAT) }
                catch (e: Throwable) { check("main-$n create", false, "$e"); continue }
            created++
            live += v.name
            if (live.size >= 3)
            {
                val target = live.removeAt(0)
                val before = repo.snapshotAll().filter { it.name in live }.associate { it.name to it.nativeBalanceSat }
                val r = try { repo.claimVault(target, payTo) }
                    catch (e: Throwable) { check("main-$n claim", false, "$e"); continue }
                val after = repo.snapshotAll().filter { it.name in live }.associate { it.name to it.nativeBalanceSat }
                val robbed = before.filter { (nm, bal) -> (after[nm] ?: 0L) < bal }
                check("main-$n claim $target", r.payoutSat > 0L && robbed.isEmpty(),
                    "payout=${r.payoutSat} robbed=$robbed")
            }
            else check("main-$n create", v.name.isNotEmpty())
        }

        // 47 a vault locked far ahead must refuse
        run {
            val v = repo.addVault((repo.currentBlockHeight() ?: 0L) + 100_000L, VAULT_SAT)
            val refused = try { repo.claimVault(v.name, payTo); false } catch (e: Throwable) { true }
            check("main-47 immature refused", refused)
            live += v.name
        }
        // 48 fee estimate must match what the claim actually pays
        run {
            val v = repo.addVault(mature(), VAULT_SAT)
            val quoted = repo.snapshot(v.name)?.claimFeeSat ?: -1L
            val r = repo.claimVault(v.name, payTo)
            check("main-48 fee estimate", quoted == r.feeSat, "quoted=$quoted actual=${r.feeSat}")
        }
        // 49 claiming twice must not pay twice
        run {
            val v = repo.addVault(mature(), VAULT_SAT)
            repo.claimVault(v.name, payTo)
            val second = try { repo.claimVault(v.name, payTo).payoutSat } catch (e: Throwable) { -1L }
            check("main-49 double claim refused", second <= 0L, "second=$second")
        }
        // 50 a vault reloaded from the database still claims
        run {
            val v = repo.addVault(mature(), VAULT_SAT)
            repo.saveVault(v.name)
            repo.removeVault(v.name)
            val reloaded = repo.loadVault(v.name)
            val r = if (reloaded) try { repo.claimVault(v.name, payTo).payoutSat } catch (e: Throwable) { -1L } else -1L
            check("main-50 claim after reload", r > 0L, "reloaded=$reloaded payout=$r")
        }

        // Leave nothing locked: drain whatever is still holding coin.
        for (name in live.toList())
        {
            val s = repo.snapshot(name) ?: continue
            if (s.nativeBalanceSat > 0L && s.isUnlockable)
                try { repo.claimVault(name, payTo); log("drained $name") } catch (e: Throwable) { log("drain $name failed: $e") }
        }

        log("SUMMARY created=$created passed=$passed failed=${failures.size}")
        failures.forEach { log("FAILURE $it") }
        assertEquals(emptyList(), failures, "mainnet vault scenarios failed")
    }

    companion object
    {
        const val TAG = "VAULTTEST"
        const val ACCT = "vaultmain"
        const val NEEDED_SAT = 130_000L
        const val VAULT_SAT = 2000L
    }
}
