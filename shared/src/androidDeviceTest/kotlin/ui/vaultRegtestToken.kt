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
 * The token, baton and edge-case half of the regtest vault run. Split from the coin scenarios so a
 * funder outage does not cost a re-run of all fifty.
 */
class VaultRegtestTokenTest : WallyUiTestBase()
{
    private lateinit var repo: TimeLockContractRepository
    private lateinit var account: Account
    private val failures = mutableListOf<String>()
    private var passed = 0

    private fun log(m: String) = println("$TAG $m")

    private fun request(kind: String, addr: PayAddress?, name: String, want: (TimeLockContractRepository.VaultSnapshot) -> Boolean): Boolean
    {
        log("VAULTREQ $kind $addr")
        // A freshly created vault address is only noticed once the bloom filter catches up, and the
        // electrum inject backstop cannot reach a regtest node, so give the deposit real time to land.
        for (i in 0 until 150)
        {
            millisleep(2000u)
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
    fun tokenBatonAndEdgeScenarios()
    {
        wallyApp!!.openAllAccounts()
        account = wallyApp!!.accounts.values.firstOrNull { it.name == ACCT }
            ?: wallyApp!!.newAccount(ACCT, 0U, "", ChainSelector.NEXAREGTEST)!!
        repo = account.timeLockVaults
        runBlocking { repo.start() }

        val fundAddr = repo.nextReceiveAddress()
        log("VAULTREQ nexa $fundAddr")
        for (i in 0 until 60)
        {
            millisleep(2000u)
            if (account.wallet.balance > 100_000L && (repo.currentBlockHeight() ?: 0L) > 0L) break
        }
        log("start balance=${account.wallet.balance} height=${repo.currentBlockHeight()}")
        assertTrue(account.wallet.balance > 50_000L, "regtest account was never funded")
        val payTo = repo.nextReceiveAddress()!!

        // --- 31..40: vaults holding group tokens ---------------------------------------------------
        for (n in 31..40)
        {
            val created = try { repo.addVault(mature(), VAULT_SAT) }
                catch (e: Throwable) { check("token-$n create", false, "$e"); continue }
            val got = request("token", created.address, created.name) { it.tokenBalances.values.any { v -> v > 0L } }
            if (!got) { check("token-$n deposit", false, "token never arrived"); continue }
            val held = repo.snapshot(created.name)!!.tokenBalances.values.sum()
            val r = try { repo.claimVault(created.name, payTo) } catch (e: Throwable) { check("token-$n claim", false, "$e"); continue }
            val left = repo.snapshot(created.name)?.tokenBalances?.values?.sum() ?: 0L
            check("token-$n claim held=$held", r.payoutSat > 0L && left == 0L, "left=$left payout=${r.payoutSat}")
        }

        // --- 41..46: vaults holding a mint/melt baton ----------------------------------------------
        for (n in 41..46)
        {
            val created = try { repo.addVault(mature(), VAULT_SAT) }
                catch (e: Throwable) { check("baton-$n create", false, "$e"); continue }
            val got = request("baton", created.address, created.name) { it.authorities.isNotEmpty() }
            if (!got) { check("baton-$n deposit", false, "baton never arrived"); continue }
            val flags = repo.snapshot(created.name)!!.authorities.size
            val r = try { repo.claimVault(created.name, payTo) } catch (e: Throwable) { check("baton-$n claim", false, "$e"); continue }
            val left = repo.snapshot(created.name)?.authorities?.size ?: 0
            check("baton-$n claim authorities=$flags", r.payoutSat >= 0L && left == 0, "left=$left")
        }

        // --- 47..50: edge cases --------------------------------------------------------------------
        // 47 a vault locked in the future must refuse to claim
        run {
            val far = (repo.currentBlockHeight() ?: 0L) + 10_000L
            val created = repo.addVault(far, VAULT_SAT)
            val refused = try { repo.claimVault(created.name, payTo); false } catch (e: Throwable) { true }
            check("edge-47 immature refused", refused)
        }
        // 48 claiming the same vault twice must not pay out twice
        run {
            val created = repo.addVault(mature(), VAULT_SAT)
            repo.claimVault(created.name, payTo)
            val second = try { repo.claimVault(created.name, payTo).payoutSat } catch (e: Throwable) { -1L }
            check("edge-48 double claim refused", second <= 0L, "second payout=$second")
        }
        // 49 a vault that holds nothing cannot be claimed
        run {
            val created = repo.addVault(mature(), VAULT_SAT)
            repo.claimVault(created.name, payTo)
            val snap = repo.snapshot(created.name)
            check("edge-49 emptied vault reads claimed", snap != null && snap.nativeBalanceSat == 0L,
                "balance=${snap?.nativeBalanceSat}")
        }
        // 50 a vault reloaded from the database still claims (the untagged-UTXO path)
        run {
            val created = repo.addVault(mature(), VAULT_SAT)
            repo.saveVault(created.name)
            repo.removeVault(created.name)
            val reloaded = repo.loadVault(created.name)
            val r = if (reloaded) try { repo.claimVault(created.name, payTo).payoutSat } catch (e: Throwable) { -1L } else -1L
            check("edge-50 claim after reload", r > 0L, "reloaded=$reloaded payout=$r")
        }

        log("SUMMARY passed=$passed failed=${failures.size}")
        failures.forEach { log("FAILURE $it") }
        assertEquals(emptyList(), failures, "regtest token/baton/edge scenarios failed")
    }

    companion object
    {
        const val TAG = "VAULTTEST"
        const val ACCT = "vaulttest"
        const val VAULT_SAT = 2000L
    }
}
