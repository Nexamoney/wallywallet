package ui

import info.bitcoinunlimited.www.wally.wallyAccountDbFileName
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.deleteWalletFile
import repositories.TimeLockContractRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val LogIt = GetLog("BU.wally.timeLockVmTest")

/**
 * Unit tests for [TimeLockViewModel].
 *
 * These exercise the view-model logic that does **not** require a live chain
 * connection or funded wallet: the initial empty snapshot, the unit-name
 * derivation, the unsynced-height contract, and — importantly — that a failed
 * [TimeLockViewModel.addVault] surfaces as `null` rather than crashing the app.
 * Funded create/claim/maturity flows live in the regtest-backed
 * repository tests.
 */
class TimeLockViewModelTest : WallyUiTestBase()
{
    private val createdAccounts = mutableListOf<String>()

    /**
     * Build a fresh [TimeLockViewModel] over a brand-new (empty, unsynced)
     * account on [chain]. The account name is unique per call so tests don't
     * collide on persisted state; any prior account of the same name is
     * removed first.
     */
    private fun freshVm(
        chain: ChainSelector = ChainSelector.NEXATESTNET,
        name: String,
    ): TimeLockViewModel
    {
        wallyApp!!.accounts[name]?.let { wallyApp!!.deleteAccount(it) }
        // deleteAccount is two-phase and async (Phase B on libNexaJobPool); newAccount
        // refuses a name whose deletion is still pending. A stale account can be loaded
        // from a previous crashed/aborted run's persisted activeAccountNames, so wait for
        // the deletion to actually finish before recreating the same name.
        awaitDeletionsComplete()
        // A killed prior run can leave this name's wallet DB file on disk (no live account,
        // stale WAL sidecars) that openWalletDB can never open again; remove any such debris.
        deleteWalletFile(wallyAccountDbFileName(name))
        val account = wallyApp!!.newAccount(name, 0U, "", chain)
            ?: error("could not create test account $name")
        createdAccounts += name
        return TimeLockViewModel(account.timeLockVaults)
    }

    @AfterTest
    fun cleanupAccounts()
    {
        for (name in createdAccounts)
        {
            wallyApp!!.accounts[name]?.let { runCatching { wallyApp!!.deleteAccount(it) } }
        }
        createdAccounts.clear()
    }

    @Test
    fun freshViewModelHasNoVaults()
    {
        val vm = freshVm(name = "tlVmEmpty")
        assertTrue(vm.vaults.value.isEmpty(), "a brand-new account should hold no vaults")
    }

    @Test
    fun currentBlockHeightIsNullWhenUnsynced()
    {
        val vm = freshVm(name = "tlVmHeight")
        // A freshly-created account has never synced, so the VM must report a
        // null tip rather than a bogus 0 / negative height.
        assertNull(vm.currentBlockHeight(), "an unsynced wallet has no block height")
    }

    @Test
    fun nativeUnitNameFollowsChain()
    {
        assertEquals("tNexa", freshVm(ChainSelector.NEXATESTNET, "tlVmUnitT").nativeUnitName)
        assertEquals("rNexa", freshVm(ChainSelector.NEXAREGTEST, "tlVmUnitR").nativeUnitName)
    }

    @Test
    fun addVaultBelowMinimumReturnsNullAndDoesNotCrash()
    {
        val vm = freshVm(name = "tlVmMinFund")
        // 100 sat is well below MIN_VAULT_FUNDING_SAT (2000). The repository
        // require() throws; the VM must catch it, surface an error, and return
        // null — never propagate to the (Compose) caller and crash.
        // addVault joins a viewModelScope job, which under the installed test-Main
        // dispatcher only runs while the scheduler is pumped — a bare runBlocking
        // would deadlock.
        val result = runBlockingWithinTest { vm.addVault(unlockBlockHeight = 1_100_000L, amountSat = 100L) }
        assertNull(result, "below-minimum funding must fail softly with null")
        assertTrue(vm.vaults.value.isEmpty(), "no vault should be created on a failed addVault")
    }

    @Test
    fun addVaultWithInsufficientBalanceReturnsNullAndCreatesNoVault()
    {
        val vm = freshVm(name = "tlVmNoFunds")
        // A valid amount on an empty wallet: the repository's pre-flight
        // balance check throws before any vault state is created; the VM
        // catches it, surfaces the error, and returns null — the user must
        // never end up with a silent empty vault card.
        val result = runBlockingWithinTest { vm.addVault(unlockBlockHeight = 1_100_000L, amountSat = 2_000L) }
        assertNull(result, "insufficient balance must fail softly with null")
        assertTrue(vm.vaults.value.isEmpty(), "no vault may be registered when funding cannot succeed")
    }

    @Test
    fun vaultTransactionsForUnknownVaultIsEmpty()
    {
        val vm = freshVm(name = "tlVmTxs")
        assertTrue(runBlockingWithinTest { vm.vaultTransactions("does-not-exist") }.isEmpty())
    }

    @Test
    fun companionConstantsMatchNexaBlockCadence()
    {
        // Nexa targets 2-minute blocks; the poll cadence is the documented 15 s.
        assertEquals(120L, TimeLockViewModel.BLOCK_SECONDS)
        assertEquals(15_000L, TimeLockViewModel.BALANCE_POLL_INTERVAL_MS)
    }
}
