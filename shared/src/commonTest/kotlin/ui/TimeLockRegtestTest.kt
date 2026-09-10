package ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.wallyApp
import info.bitcoinunlimited.www.wally.ui.views.TransactionsList
import info.bitcoinunlimited.www.wally.ui.views.TxHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.MinFeeSatPerByte
import org.nexa.libnexakotlin.REG_TEST_ONLY
import org.nexa.nexarpc.NexaRpc
import org.nexa.threads.millinow
import org.nexa.threads.millisleep
import repositories.TimeLockContractRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val LogIt = GetLog("BU.wally.timeLockRegtest")

/**
 * End-to-end, all-levels tests for the time-lock vault against a **real Nexa
 * regtest full node** (same harness [SystemTests] uses: `getNexaRpc()` /
 * [REGTEST_IP], `rpc.sendtoaddress`, `rpc.generate`, the token RPCs).
 *
 * Each test requires a Nexa regtest node reachable at
 * [REGTEST_IP]:`NexaRegtestRpcPort` (rpc user/password `regtest`/`regtest`).
 * When none is running the test logs and **skips** (returns) rather than
 * failing, so the default offline suite stays green; run a regtest `nexad` to
 * actually execute these.
 *
 * Coverage (beyond the create→lock→mature→claim happy path):
 *   - [refreshBalancesReadsVaultFromElectrum] — the electrum balance path
 *     (token pagination / authority decoding), not the local UTXO fallback.
 *   - [reloadedVaultAfterWalletReopenKeepsWalletChain] — the regression for the
 *     chain bug: after a wallet reload (where `TimeLockVaultType.create`
 *     runs), the vault must still derive the wallet's chain, not mainnet.
 *   - [discoverVaultsFromChainRecoversVault] — recovery from on-chain history.
 *   - [claimEmptiesMultiUtxoVaultCompletely] — a claim sweeps every UTXO.
 *   - [claimDoesNotTouchSiblingVault] — a claim stays within its own address.
 *   - [claimRefusedWhenBalanceAtHeadroomFloor] — the headroom gate on-chain.
 *   - [multipleVaultsGetDistinctIndicesAndAddresses] — index allocation.
 *   - [vaultTransactionsListsDeposit] — per-vault history.
 *   - [tokenVaultSweptOnClaim] — claim of a vault holding a group token.
 */
@OptIn(ExperimentalTestApi::class, kotlin.time.ExperimentalTime::class)
class TimeLockRegtestTest : WallyUiTestBase(openAllAccounts = false)
{
    private val createdAccounts = mutableListOf<String>()

    private class Harness(
        val rpc: NexaRpc,
        val account: Account,
        val repo: TimeLockContractRepository,
        val vm: TimeLockViewModel,
    )

    /** Connect to the regtest node, or null (→ skip) when unreachable. */
    private fun rpcOrNull(): NexaRpc? =
        try { getNexaRpc() }
        catch (e: Throwable)
        {
            LogIt.warning("regtest node unavailable — skipping regtest vault test: ${e.message}")
            null
        }

    private fun waitUntil(timeoutMs: Long, desc: String, cond: () -> Boolean)
    {
        val start = millinow()
        while (millinow() - start < timeoutMs)
        {
            if (cond()) return
            millisleep(300U)
        }
        error("timed out after ${timeoutMs}ms waiting for: $desc")
    }

    /**
     * Create a fresh regtest account pinned to the local node, fund it from the
     * faucet, and return a repo/VM over it — or null when no node is reachable
     * (the caller then returns to skip).
     */
    private fun harnessOrNull(name: String, fundNexa: Int = 1000): Harness?
    {
        val rpc = rpcOrNull() ?: return null
        cleanupAccounts(name, 3)
        val account = wallyApp!!.newAccount(name, 0U, "", ChainSelector.NEXAREGTEST)
            ?: error("could not create regtest account $name")
        createdAccounts += name
        account.chain.net.exclusiveNodes(setOf(REGTEST_IP))
        val repo = account.timeLockVaults
        // Heavy startup lives in start(); run it (off the test thread, mirroring
        // production) before exercising the repo.
        runBlocking(Dispatchers.IO) { repo.start() }
        val vm = TimeLockViewModel(repo)
        waitUntil(30_000, "receive address for $name") { repo.nextReceiveAddress() != null }
        rpc.sendtoaddress(repo.nextReceiveAddress()!!.toString(), BigDecimal.fromInt(fundNexa))
        rpc.generate(1)
        waitUntil(60_000, "account $name funded") { account.wallet.balanceConfirmed > 0L }
        return Harness(rpc, account, repo, vm)
    }

    /**
     * Create a vault whose unlock height is already in the past (block 1) so it
     * is claimable the moment its funding confirms — no maturity mining, just
     * one block to bury the funding tx. Returns the vault name.
     */
    private fun createUnlockableVault(h: Harness, amountSat: Long = 5_000L): String
    {
        val created = h.repo.addVault(unlockBlockHeight = 1L, amountSat)
        waitForNodeToSee(h, created.fundingTxIdem)
        h.rpc.generate(1)   // confirm the funding tx; unlock height 1 is long past
        waitUntil(60_000, "vault ${created.name} funded and unlockable") {
            val s = h.repo.snapshot(created.name)
            s != null && s.nativeBalanceSat >= amountSat && s.isUnlockable &&
                s.earliestDepositHeight != null
        }
        return created.name
    }

    /**
     * Block until the node itself holds [idem]. `wallet.send` pushes over P2P and
     * returns as soon as the tx is queued, so mining or spending its outputs
     * straight after can beat the relay and the node rejects the spend with
     * "Missing inputs" — the wallet's own unconfirmed view cannot tell.
     */
    private fun waitForNodeToSee(h: Harness, idem: Hash256)
    {
        waitUntil(60_000, "node to accept funding tx $idem") {
            runCatching { h.rpc.getrawtransaction(idem.toHex()) }.isSuccess
        }
    }

    /**
     * Create a vault funded with [amountSat], then mine past its unlock height
     * so it is claimable. Returns the vault name.
     */
    private fun createMaturedVault(h: Harness, amountSat: Long = 5_000L): String
    {
        val tip = h.repo.currentBlockHeight() ?: error("wallet unsynced")
        val unlock = tip + 3L
        val created = h.repo.addVault(unlock, amountSat)
        waitForNodeToSee(h, created.fundingTxIdem)
        h.rpc.generate(4)   // mine the funding tx + advance past the unlock height
        waitUntil(60_000, "vault ${created.name} matured") {
            val s = h.repo.snapshot(created.name)
            s != null && s.nativeBalanceSat >= amountSat && s.isUnlockable
        }
        return created.name
    }

    @AfterTest
    fun cleanupAccounts()
    {
        wallyApp!!.focusedAccount.value = null
        for (name in createdAccounts)
        {
            wallyApp!!.accounts[name]?.let { runCatching { wallyApp!!.deleteAccount(it) } }
        }
        createdAccounts.clear()
    }

    // --- Lifecycle ------------------------------------------------------

    @Test
    fun vaultLifecycleCreateLockMatureClaim()
    {
        val h = harnessOrNull("tlRtLifecycle") ?: return
        val tip = h.repo.currentBlockHeight()
        assertNotNull(tip, "wallet should be synced to a regtest tip")
        val unlockHeight = tip + 5L

        val created = h.repo.addVault(unlockHeight, amountSat = 5_000L)
        assertTrue(created.address.toString().startsWith("nexareg:"),
            "regtest vault address must use the regtest prefix, got ${created.address}")
        h.rpc.generate(1)

        // Locked snapshot (tip < unlockHeight).
        waitUntil(60_000, "vault balance on chain") {
            (h.repo.snapshot(created.name)?.nativeBalanceSat ?: 0L) > 0L
        }
        val locked = h.repo.snapshot(created.name)!!
        assertEquals(5_000L, locked.nativeBalanceSat)
        assertFalse(locked.isUnlockable, "vault must be locked before maturity")
        h.vm.refresh()
        assertFalse(h.vm.vaults.value.first { it.snapshot.name == created.name }.snapshot.isUnlockable,
            "view model must report the vault locked before maturity")

        // Advance past the unlock height → claimable.
        h.rpc.generate(6)
        waitUntil(60_000, "vault matured") { h.repo.snapshot(created.name)?.isUnlockable == true }
        h.vm.refresh()
        assertTrue(h.vm.vaults.value.first { it.snapshot.name == created.name }.snapshot.isUnlockable,
            "view model must report the vault claimable after maturity")

        // Claim sweeps the vault back to the wallet.
        val claim = h.repo.claimVault(created.name, h.repo.nextReceiveAddress()!!)
        assertNotNull(claim, "claim of a matured, funded vault should succeed")
        assertTrue(claim.payoutSat > 0L)
        h.rpc.generate(1)
        waitUntil(60_000, "vault swept empty") {
            (h.repo.snapshot(created.name)?.nativeBalanceSat ?: -1L) == 0L
        }
    }

    // --- Electrum balance path -----------------------------------------

    @Test
    fun refreshBalancesReadsVaultFromElectrum()
    {
        val h = harnessOrNull("tlRtRefresh") ?: return
        val name = createMaturedVault(h, amountSat = 5_000L)

        // Exercise the electrum-backed balance fetch (the lifecycle test reads
        // via snapshot()'s local UTXO fallback; this hits refreshBalances()).
        runBlocking(Dispatchers.IO) { h.repo.refreshBalances() }

        val onChainSat = h.repo.onChainNativeSat.value[name]
        assertNotNull(onChainSat, "refreshBalances() should publish an on-chain balance")
        assertTrue(onChainSat >= 5_000L,
            "electrum balance should reflect the funded amount, got $onChainSat")
    }

    // --- Chain-bug regression ------------------------------------------

    @Test
    fun reloadedVaultAfterWalletReopenKeepsWalletChain()
    {
        val h = harnessOrNull("tlRtReload") ?: return
        val tip = h.repo.currentBlockHeight()!!
        val created = h.repo.addVault(tip + 50L, 5_000L)
        h.rpc.generate(1)
        waitUntil(60_000, "funding on chain") {
            (h.repo.snapshot(created.name)?.nativeBalanceSat ?: 0L) > 0L
        }
        val originalAddr = created.address
        assertTrue(originalAddr.toString().startsWith("nexareg:"))

        // Force the wallet to reload its contracts from disk — this is the path
        // TimeLockVaultType.create runs. The reloaded vault must rederive the
        // SAME regtest address, not a nexa: one.
        REG_TEST_ONLY = false   // openAllAccounts() silently no-ops when a prior test leaks this global
        wallyApp!!.closeAllAccounts()
        wallyApp!!.openAllAccounts()
        val reopened = wallyApp!!.accounts["tlRtReload"]
            ?: error("account missing after reopen")
        reopened.chain.net.exclusiveNodes(setOf(REGTEST_IP))

        // The reopened account is a fresh object, so it owns a new repository
        // bound to the reloaded wallet (not the stale pre-reopen one) — exactly
        // the rederive-from-disk path under test.
        val repo2 = reopened.timeLockVaults
        // Drive start() so the reloaded factory rebuilds its derived view from disk.
        runBlocking(Dispatchers.IO) { repo2.start() }
        val reloaded = repo2.snapshotAll().firstOrNull { it.address == originalAddr }
        assertNotNull(reloaded,
            "reloaded factory must rederive $originalAddr; a mainnet-defaulted " +
            "chain would produce a different (nexa:) address")
        assertTrue(reloaded.address!!.toString().startsWith("nexareg:"))
    }

    // --- Recovery -------------------------------------------------------

    @Test
    fun discoverVaultsFromChainRecoversVault()
    {
        val h = harnessOrNull("tlRtDiscover") ?: return
        val tip = h.repo.currentBlockHeight()!!
        val created = h.repo.addVault(tip + 50L, 5_000L)
        h.rpc.generate(1)
        waitUntil(60_000, "funding in history") {
            h.repo.vaultTransactions(created.name).isNotEmpty()
        }

        // Drop the vault from the in-memory view, then prove it is rediscovered
        // purely from on-chain history (the recovery path).
        assertTrue(h.repo.removeVault(created.name))
        assertFalse(h.repo.names().contains(created.name))

        val rediscovered = h.repo.discoverVaultsFromChain()
        assertTrue(h.repo.names().contains(created.name),
            "discoverVaultsFromChain should re-add the vault; got $rediscovered / ${h.repo.names()}")
        assertEquals(created.address, h.repo.snapshot(created.name)?.address)
    }

    /**
     * Regression: after a seed-only recovery, a vault that was already claimed
     * must surface its claim — marked claimed ([VaultSnapshot.hasClaimTx]) and
     * with the claim spend in its history. The recovered wallet never watched
     * the vault address during sync, so the claim input's `priorOutScript` (and
     * the wallet's getTxo for the spent vault output) are empty; detection
     * instead resolves the spent prevout by matching the input's outpoint hash
     * against the vault's own deposit output. Without that fix the vault
     * recovers as merely "Unlocked" and the claim tx is missing from its history.
     */
    @Test
    fun recoveredClaimedVaultShowsClaimHistory()
    {
        val h = harnessOrNull("tlRtClaimRecover") ?: return
        val startHeight = h.repo.currentBlockHeight() ?: error("wallet unsynced")
        val name = createMaturedVault(h, amountSat = 5_000L)
        val vaultAddr = h.repo.snapshot(name)?.address ?: error("vault $name has no address")

        // Claim the matured vault and confirm the sweep on chain.
        assertNotNull(h.repo.claimVault(name, h.repo.nextReceiveAddress()!!),
            "claim of a matured vault should succeed")
        h.rpc.generate(1)
        waitUntil(60_000, "vault swept empty") {
            (h.repo.snapshot(name)?.nativeBalanceSat ?: -1L) == 0L
        }
        val tipAfterClaim = h.repo.currentBlockHeight()!!

        // Recover the SAME seed into a fresh wallet. The deposit and claim are on
        // chain, but this wallet never watched the vault address, so neither the
        // vault output nor the claim input's priorOutScript is recorded locally.
        val words = h.account.getRecoveryPhrase()
        val recoverName = "tlRtClaimRecover2"
        cleanupAccounts(recoverName, 3)
        val recovered = runBlocking(Dispatchers.IO) {
            wallyApp!!.recoverAccount(
                recoverName, 0UL, "", words, ChainSelector.NEXAREGTEST,
                earliestActivity = null, earliestHeight = startHeight, nonstandardActivity = null,
            )
        }
        createdAccounts += recoverName
        recovered.chain.net.exclusiveNodes(setOf(REGTEST_IP))
        waitUntil(90_000, "recovered wallet synced past the claim") {
            recovered.wallet.syncedHeight >= tipAfterClaim
        }

        val repo2 = recovered.timeLockVaults
        runBlocking(Dispatchers.IO) { repo2.start() }
        // start() runs discovery once; re-run it (idempotent) until the vault
        // surfaces, in case sync hadn't ingested its funding tx yet.
        waitUntil(60_000, "vault rediscovered from chain after recovery") {
            repo2.discoverVaultsFromChain()
            repo2.snapshotAll().any { it.address == vaultAddr }
        }

        val snap = repo2.snapshotAll().first { it.address == vaultAddr }
        assertTrue(snap.hasClaimTx,
            "a recovered, already-claimed vault must be marked claimed, not just unlocked")
        val txs = repo2.vaultTransactions(snap.name)
        val kinds = txs.map { it.kind }
        assertTrue(TimeLockContractRepository.VaultTxKind.Claim in kinds,
            "recovered vault history must include the claim spend; got $kinds")
        assertTrue(TimeLockContractRepository.VaultTxKind.Deposit in kinds,
            "recovered vault history must still include the deposit; got $kinds")
        // The claim row must surface the network fee deducted from the sweep,
        // computed from the resolved vault inputs (the recovered wallet has no
        // local priorOutScript, so this exercises the outpoint-resolve path).
        val claimTx = txs.first { it.kind == TimeLockContractRepository.VaultTxKind.Claim }
        assertTrue(claimTx.feeSat in 1 until claimTx.amountSat,
            "recovered claim must show a positive fee below the gross swept amount; " +
                "got fee=${claimTx.feeSat} gross=${claimTx.amountSat}")
    }

    @Test
    fun recoveredFundedVaultShowsBalanceFromHistory()
    {
        // Regression for the "vault reads 0.00" bug: a recovered wallet never
        // watched the vault address during its initial sync, so the deposit
        // output is in tx history but was never materialised as a tracked
        // spendable UTXO — and electrum can report the address empty. snapshot()
        // must derive the balance from tx history regardless, with no electrum
        // call and no UTXO injection.
        val h = harnessOrNull("tlRtBalRecover") ?: return
        val startHeight = h.repo.currentBlockHeight() ?: error("wallet unsynced")
        // Lock far ahead so the vault stays funded (unclaimed) through recovery.
        val created = h.repo.addVault(startHeight + 5_000L, amountSat = 5_000L)
        h.rpc.generate(1)
        waitUntil(60_000, "vault funded on chain") {
            (h.repo.snapshot(created.name)?.nativeBalanceSat ?: 0L) >= 5_000L
        }
        val vaultAddr = created.address
        val tipAfterFund = h.repo.currentBlockHeight()!!

        val words = h.account.getRecoveryPhrase()
        val recoverName = "tlRtBalRecover2"
        cleanupAccounts(recoverName, 3)
        val recovered = runBlocking(Dispatchers.IO) {
            wallyApp!!.recoverAccount(
                recoverName, 0UL, "", words, ChainSelector.NEXAREGTEST,
                earliestActivity = null, earliestHeight = startHeight, nonstandardActivity = null,
            )
        }
        createdAccounts += recoverName
        recovered.chain.net.exclusiveNodes(setOf(REGTEST_IP))
        waitUntil(90_000, "recovered wallet synced past the funding") {
            recovered.wallet.syncedHeight >= tipAfterFund
        }

        val repo2 = recovered.timeLockVaults
        runBlocking(Dispatchers.IO) { repo2.start() }
        waitUntil(60_000, "vault rediscovered from chain after recovery") {
            repo2.discoverVaultsFromChain()
            repo2.snapshotAll().any { it.address == vaultAddr }
        }

        // No refreshBalances() call: the balance must come from tx history alone.
        val snap = repo2.snapshotAll().first { it.address == vaultAddr }
        assertEquals(5_000L, snap.nativeBalanceSat,
            "recovered funded vault must report its on-chain balance from tx " +
                "history without electrum; got ${snap.nativeBalanceSat}")
        assertFalse(snap.hasClaimTx, "an unclaimed recovered vault must not be marked claimed")
    }

    // --- Full sweep -----------------------------------------------------

    @Test
    fun claimEmptiesMultiUtxoVaultCompletely()
    {
        val h = harnessOrNull("tlRtDrain") ?: return
        val tip = h.repo.currentBlockHeight() ?: error("wallet unsynced")
        val unlock = tip + 3L
        val created = h.repo.addVault(unlock, amountSat = 5_000L)
        // A second deposit straight to the vault address spreads the balance
        // across two UTXOs.
        h.rpc.sendtoaddress(created.address.toString(), BigDecimal.fromInt(50))   // 50 NEXA = 5000 sat
        h.rpc.generate(4)   // mine both deposits + advance past the unlock height
        waitUntil(60_000, "two-UTXO vault matured") {
            val s = h.repo.snapshot(created.name)
            s != null && s.nativeBalanceSat >= 10_000L && s.isUnlockable
        }

        val claim = h.repo.claimVault(created.name, h.repo.nextReceiveAddress()!!)
        assertNotNull(claim, "claim of a multi-UTXO vault should succeed")
        // The fee must be the real (small) tx fee, not the held-back headroom.
        assertTrue(
            claim.feeSat in 1 until TimeLockContractRepository.CLAIM_FEE_HEADROOM_SAT,
            "claim fee should be the real tx fee, well under the headroom, got ${claim.feeSat}",
        )
        h.rpc.generate(1)
        // The whole vault must be swept — no residual balance left at the address.
        waitUntil(60_000, "multi-UTXO vault emptied to zero") {
            (h.repo.snapshot(created.name)?.nativeBalanceSat ?: -1L) == 0L
        }
    }

    @Test
    fun claimLeavesMaturedSiblingVaultFunded()
    {
        val h = harnessOrNull("tlRtSibling") ?: return
        val tip = h.repo.currentBlockHeight() ?: error("wallet unsynced")
        // Two vaults at the SAME unlock height (distinct index → distinct
        // address). Every vault shares the factory's contractId, so only the
        // claim's VaultScope.At address gate keeps B out of A's claim: the
        // maturity gate cannot, both being mature.
        val a = h.repo.addVault(tip + 3L, amountSat = 5_000L)
        h.rpc.generate(1)
        val b = h.repo.addVault(tip + 3L, amountSat = 5_000L)
        h.rpc.generate(4)
        waitUntil(60_000, "both vaults matured") {
            val sa = h.repo.snapshot(a.name)
            val sb = h.repo.snapshot(b.name)
            sa != null && sb != null && sa.isUnlockable && sb.isUnlockable &&
                sa.nativeBalanceSat >= 5_000L && sb.nativeBalanceSat >= 5_000L
        }

        val claim = h.repo.claimVault(a.name, h.repo.nextReceiveAddress()!!)
        assertNotNull(claim, "claiming vault A should succeed")
        h.rpc.generate(1)
        waitUntil(60_000, "vault A emptied by its claim") {
            (h.repo.snapshot(a.name)?.nativeBalanceSat ?: -1L) == 0L
        }
        assertEquals(5_000L, h.repo.snapshot(b.name)?.nativeBalanceSat,
            "claiming A must leave the matured sibling B funded")
        assertTrue(claim.payoutSat in 1 until 5_000L,
            "payout ${claim.payoutSat} must be vault A's coin alone, less the fee")
    }

    @Test
    fun claimDoesNotTouchImmatureSiblingVault()
    {
        val h = harnessOrNull("tlRtSiblingLocked") ?: return
        val tip = h.repo.currentBlockHeight() ?: error("wallet unsynced")
        // A matured vault and one still locked far ahead. Address scoping now
        // protects the locked sibling too, but maturity gating must still hold
        // on its own: a claim of the matured vault leaves the locked one full.
        val mature = h.repo.addVault(tip + 3L, amountSat = 5_000L)
        h.rpc.generate(1)
        val locked = h.repo.addVault(tip + 5_000L, amountSat = 5_000L)
        h.rpc.generate(4)
        waitUntil(60_000, "one vault matured, the other still locked") {
            val sm = h.repo.snapshot(mature.name)
            val sl = h.repo.snapshot(locked.name)
            sm != null && sl != null && sm.isUnlockable && !sl.isUnlockable &&
                sm.nativeBalanceSat >= 5_000L && sl.nativeBalanceSat >= 5_000L
        }

        assertNotNull(h.repo.claimVault(mature.name, h.repo.nextReceiveAddress()!!),
            "claiming the matured vault should succeed")
        h.rpc.generate(1)
        waitUntil(60_000, "matured vault emptied") {
            (h.repo.snapshot(mature.name)?.nativeBalanceSat ?: -1L) == 0L
        }
        assertEquals(5_000L, h.repo.snapshot(locked.name)?.nativeBalanceSat,
            "a still-locked sibling must keep its coin")
    }

    // --- Headroom gate --------------------------------------------------

    @Test
    fun claimAllowedWhenBalanceAtHeadroomFloor()
    {
        val h = harnessOrNull("tlRtFloor") ?: return
        // Fund at exactly the floor (== the claim headroom, 20 NEXA). The
        // threshold is inclusive (>=), so a vault funded at the minimum is
        // claimable: the real fee is far below the headroom, leaving a positive
        // payout.
        val name = createMaturedVault(h, amountSat = TimeLockContractRepository.MIN_VAULT_FUNDING_SAT)
        val result = h.repo.claimVault(name, h.repo.nextReceiveAddress()!!)
        assertNotNull(result, "a vault funded at exactly the headroom floor must be claimable")
        assertTrue(result.payoutSat > 0L, "payout must be positive, got ${result.payoutSat}")
        assertTrue(result.feeSat < TimeLockContractRepository.CLAIM_FEE_HEADROOM_SAT,
            "the real fee must sit below the headroom reserve, got ${result.feeSat}")
        h.rpc.generate(1)
        waitUntil(60_000, "floor vault swept empty") {
            (h.repo.snapshot(name)?.nativeBalanceSat ?: -1L) == 0L
        }
    }

    // --- Multiple vaults ------------------------------------------------

    @Test
    fun multipleVaultsGetDistinctIndicesAndAddresses()
    {
        val h = harnessOrNull("tlRtMulti") ?: return
        val tip = h.repo.currentBlockHeight()!!
        val v1 = h.repo.addVault(tip + 50L, 5_000L)
        h.rpc.generate(1)
        val v2 = h.repo.addVault(tip + 60L, 5_000L)
        h.rpc.generate(1)

        assertTrue(v1.name != v2.name, "vault names must be distinct")
        assertTrue(v1.address != v2.address, "vault addresses must be distinct")
        assertTrue(v1.address.toString().startsWith("nexareg:") && v2.address.toString().startsWith("nexareg:"))
        assertTrue(h.repo.size() >= 2)
    }

    // --- Transaction history -------------------------------------------

    @Test
    fun vaultTransactionsListsDeposit()
    {
        val h = harnessOrNull("tlRtTxs") ?: return
        val tip = h.repo.currentBlockHeight()!!
        val created = h.repo.addVault(tip + 50L, 5_000L)
        h.rpc.generate(1)
        waitUntil(60_000, "deposit in history") {
            h.repo.vaultTransactions(created.name).isNotEmpty()
        }
        val txs = h.repo.vaultTransactions(created.name)
        assertTrue(
            txs.any { it.kind == TimeLockContractRepository.VaultTxKind.Deposit && it.amountSat == 5_000L },
            "deposit of 5000 sat should appear in the vault's history: $txs",
        )
    }

    @Test
    fun claimRecordsSweepInVaultHistorySynchronously()
    {
        // Regression for the silent-claim bug: claimVault broadcasts the sweep
        // synchronously (electrum sendTx) and records it inline, so the claim is
        // in the vault's transaction list the instant the call returns — no block
        // mined, no deferred job awaited.
        val h = harnessOrNull("tlRtClaimSync") ?: return
        val name = createMaturedVault(h, amountSat = 5_000L)

        // Drive the claim through the view model (the UI path). claimVault joins a
        // viewModelScope job, which under the installed test-Main dispatcher only runs
        // while the scheduler is pumped — a bare runBlocking would deadlock.
        val claim = runBlockingWithinTest { h.vm.claimVault(name, h.repo.nextReceiveAddress()!!) }
        assertNotNull(claim, "claim of a matured vault should succeed")

        // No generate(), no waitUntil: the sweep must already be recorded.
        val kinds = runBlockingWithinTest { h.vm.vaultTransactions(name) }.map { it.kind }
        assertTrue(TimeLockContractRepository.VaultTxKind.Claim in kinds,
            "claim sweep must be recorded in the vault's history synchronously; got $kinds")
    }

    @Test
    fun claimFeeMeetsRelayMinimum()
    {
        // Regression for "mempool min fee not met": claimVault broadcasts via
        // electrum.sendTx, so a sub-minimum fee would throw and the VM would
        // return null; a non-null result already proves the node accepted it.
        // Also assert the recorded tx's fee rate explicitly clears MinFeeSatPerByte.
        val h = harnessOrNull("tlRtClaimFee") ?: return
        val name = createMaturedVault(h, amountSat = 5_000L)

        val claim = runBlockingWithinTest { h.vm.claimVault(name, h.repo.nextReceiveAddress()!!) }
        assertNotNull(claim, "claim must succeed — a sub-min-fee sweep is rejected by the node")

        val txh = h.account.wallet.getTx(claim.txIdem)
        assertNotNull(txh, "claim tx must be recorded in the wallet")
        assertTrue(txh.tx.feeRate >= MinFeeSatPerByte,
            "claim fee rate ${txh.tx.feeRate} must clear the relay minimum $MinFeeSatPerByte (fee=${claim.feeSat} sat)")
    }

    @Test
    fun claimVaultThroughComposeUiIsAcceptedByNode()
    {
        // End-to-end UI test against the node: drive the real Compose claim flow
        // (tap Claim → Claim again on the claim card) wired to the live view model.
        // The "Claimed" card is rendered only when vm.claimVault returns a result —
        // i.e. the node ACCEPTED the broadcast sweep (adequate fee AND a valid
        // signature). It times out on "Claimed" if the node rejects the tx.
        val h = harnessOrNull("tlRtClaimUi") ?: return
        val name = createMaturedVault(h, amountSat = 5_000L)
        waitUntil(60_000, "vault shows CLAIMABLE in the view model") {
            h.vm.refresh()
            h.vm.vaults.value.any {
                it.snapshot.name == name && it.snapshot.isUnlockable
            }
        }
        val payout = h.repo.nextReceiveAddress()!!

        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = h.vm.vaults.value,
                    currentBlockHeight = h.repo.currentBlockHeight(),
                    unitName = h.vm.nativeUnitName,
                    onAddVault = { _, _ -> null },
                    nextReceiveAddress = { payout },
                    // Real claim against the node — this is what's under test.
                    onClaim = { vaultName, payoutAddress -> h.vm.claimVault(vaultName, payoutAddress) },
                    pill = {},
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            // Master–detail: open the vault's detail level, then claim from
            // its contract pill.
            onNodeWithTag("VaultEntry-$name").performClick()
            settle()
            // jvmTest loads no locale, so every label resolves through i18n, never English text.
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaimTo).uppercase()).assertIsDisplayed()
            // On the claim card the same "Claim" label is the confirm action.
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            // The sweep broadcast is async (network); wait for the claimed card
            // rather than settling, then assert it. Times out only if the node
            // rejected the tx (fee/signature regression).
            waitUntil(timeoutMillis = 90_000L) {
                onAllNodesWithText(i18n(S.tlvClaimed)).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText(i18n(S.tlvClaimed)).assertIsDisplayed()
        }
    }

    @Test
    fun claimedVaultSweepAppearsInHomeTransactionList()
    {
        // Regression for "claim tx missing from the home screen tx list": the
        // sweep pays the user's main account, so it must surface in the home
        // page's transaction history — the same list `TransactionsList` renders
        // from `TxHistoryViewModel`, which walks `wallet.forEachTxByDate`.
        // 1) fund a wallet  2) create an unlockable vault  3) claim it
        // 4) the claim tx must appear in the home transaction list.
        val h = harnessOrNull("tlRtHomeList") ?: return
        wallyApp!!.focusedAccount.value = h.account

        val name = createUnlockableVault(h, amountSat = 5_000L)
        // Drive the claim through the repository (what the view model delegates
        // to); the harness already ran repo.start(). The view model's claimVault
        // joins a viewModelScope startJob that never runs under the test's
        // unadvanced Main dispatcher, so calling it from a plain runBlocking would
        // hang — the UI surface under test here is the home transaction list.
        val claim = h.repo.claimVault(name, h.repo.nextReceiveAddress()!!)
        assertTrue(claim.payoutSat > 0L, "claim of an unlockable vault should pay out")

        // The home page builds its list off the focused account's wallet. Drive
        // the real view model and assert the claim tx is in it (matched by idem,
        // which uniquely identifies the sweep regardless of in/out classification).
        val homeVm = TxHistoryViewModel()
        waitUntil(30_000, "claim tx in home transaction list") {
            homeVm.getAllTransactions(h.account)
            homeVm.txHistory.value.any { it.transaction.tx.idem == claim.txIdem }
        }
        val claimEntry = homeVm.txHistory.value.first {
            it.transaction.tx.idem == claim.txIdem
        }

        // Render the actual home transaction list and assert the claim row shows.
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent { TransactionsList(viewModel = homeVm) }
            settle()
            onNodeWithText(claimEntry.amount).assertIsDisplayed()
        }
    }

    @Test
    fun lockedVaultFundsAreExcludedFromWalletBalance()
    {
        // Requirement: coins locked in a vault must NOT be reflected in the
        // wallet's spendable balance — creating (funding) a vault deducts the
        // locked amount from the balance; only change returns.
        val h = harnessOrNull("tlRtBalExcl") ?: return
        waitUntil(60_000, "account funded") { h.account.wallet.balanceConfirmed > 0L }
        val before = h.account.wallet.balance
        val vaultSat = 10_000L
        val created = h.repo.addVault(unlockBlockHeight = 1L, vaultSat)
        h.rpc.generate(1)
        waitUntil(60_000, "vault funded on chain") {
            (h.repo.snapshot(created.name)?.nativeBalanceSat ?: 0L) >= vaultSat
        }
        // The locked funds must leave the spendable balance (down by at least the
        // vault amount); if they were still counted, the balance would only drop
        // by the fee.
        waitUntil(60_000, "locked funds excluded from wallet balance") {
            h.account.wallet.balance <= before - vaultSat
        }
        val after = h.account.wallet.balance
        assertTrue(after <= before - vaultSat,
            "locked vault funds ($vaultSat sat) must be excluded from the wallet balance: " +
            "before=$before after=$after (dropped only ${before - after})")
    }

    @Test
    fun claimReflectsTotalReclaimedAmountInTxList()
    {
        // Requirement: a claim must show the full amount swept back into the
        // wallet (the payout) in the transaction list — shown as Received — not
        // the net of (payout − vault input), which collapses to just the fee and
        // renders as a tiny Send.
        val h = harnessOrNull("tlRtReclaim") ?: return
        wallyApp!!.focusedAccount.value = h.account
        val name = createUnlockableVault(h, amountSat = 10_000L)
        val claim = h.repo.claimVault(name, h.repo.nextReceiveAddress()!!)
        assertTrue(claim.payoutSat > 0L, "claim should pay out")

        val homeVm = TxHistoryViewModel()
        waitUntil(30_000, "claim tx in home transaction list") {
            homeVm.getAllTransactions(h.account)
            homeVm.txHistory.value.any { it.transaction.tx.idem == claim.txIdem }
        }
        val entry = homeVm.txHistory.value.first {
            it.transaction.tx.idem == claim.txIdem
        }
        val net = entry.transaction.incomingAmt - entry.transaction.outgoingAmt
        assertEquals(claim.payoutSat, net,
            "claim must reflect the full reclaimed amount (${claim.payoutSat} sat) into the wallet, " +
            "not the net fee; got incoming=${entry.transaction.incomingAmt} " +
            "outgoing=${entry.transaction.outgoingAmt} net=$net")
        assertEquals("Received", entry.type,
            "a claim sweeps funds back into the wallet, so it must show as Received, not ${entry.type}")
    }

    // --- Token / authority sweep ---------------------------------------

    @Test
    fun tokenVaultSweptOnClaim()
    {
        val h = harnessOrNull("tlRtToken", fundNexa = 2000) ?: return
        // Mint a group token on the node, send some into a matured vault, then
        // claim — the sweep must move both the native coin and the token group
        // out of the vault (exercises the per-output headroom + token outputs).
        val nodeAddr = h.rpc.getnewaddress()
        // Do NOT pass decimals here: nexarpckotlin 1.6.7 builds the positional
        // 'token new' arg list with listOfNotNull, so decimals=0 with null
        // descUrl/descHash shifts "0" into the descUrl slot and the node rejects
        // the call. The node defaults decimals to 0 anyway.
        val (groupId, _) = h.rpc.tokenNew(
            address = nodeAddr, tokenTicker = "TLT", tokenName = "TimeLockTest",
        )
        h.rpc.tokenMint(groupId, nodeAddr, 1000)
        h.rpc.generate(1)

        val name = createMaturedVault(h, amountSat = 6_000L)
        val vaultAddr = h.repo.snapshot(name)!!.address!!.toString()
        h.rpc.tokenSend(groupId, vaultAddr, 500)
        h.rpc.generate(1)
        waitUntil(60_000, "token deposited in vault") {
            h.repo.snapshot(name)?.tokenBalances?.isNotEmpty() == true
        }

        val result = h.repo.claimVault(name, h.repo.nextReceiveAddress()!!)
        assertNotNull(result, "claim should sweep a vault holding native + a token group")
        h.rpc.generate(1)
        waitUntil(60_000, "vault emptied of native and token") {
            val s = h.repo.snapshot(name)
            s != null && s.nativeBalanceSat == 0L && s.tokenBalances.isEmpty()
        }
    }

    /**
     * Regression for a node-rejected sweep ("group-token-imbalance") on a
     * vault holding several UTXOs of ONE token group, claimed by a wallet
     * that learned those UTXOs through refreshBalances' electrum INJECTION
     * (e.g. after a restart or seed recovery) rather than its own sync.
     *
     * Rostrum's listunspent includes token UTXOs, and the electrum client
     * stamps every injected Spendable with the UNGROUPED address script — so
     * an injected token UTXO used to read as pure native, and the sweep spent
     * it without re-emitting its group: an illegal melt the node rejects.
     * refreshBalances now rebuilds the real grouped script from the token
     * index before injecting; this test pins that path end to end.
     */
    @Test
    fun injectedTokenUtxosSweptOnClaimAfterRecovery()
    {
        val h = harnessOrNull("tlRtMultiTok", fundNexa = 2000) ?: return
        val startHeight = h.repo.currentBlockHeight() ?: error("wallet unsynced")
        val nodeAddr = h.rpc.getnewaddress()
        // No decimals arg — see tokenVaultSweptOnClaim for the nexarpc quirk.
        val (groupId, _) = h.rpc.tokenNew(
            address = nodeAddr, tokenTicker = "TLM", tokenName = "TimeLockMulti",
        )
        h.rpc.tokenMint(groupId, nodeAddr, 1000)
        h.rpc.generate(1)

        val name = createMaturedVault(h, amountSat = 8_000L)
        val vaultAddr = h.repo.snapshot(name)!!.address!!.toString()
        // Four separate deposits → four distinct token UTXOs of one group.
        repeat(4) {
            h.rpc.tokenSend(groupId, vaultAddr, 250)
            h.rpc.generate(1)
        }
        waitUntil(60_000, "all four same-group token deposits visible in the vault") {
            h.repo.snapshot(name)?.tokenBalances?.values?.sum() == 1000L
        }
        val tipAfterDeposits = h.repo.currentBlockHeight()!!

        // Recover the SAME seed into a fresh wallet: the token deposits are on
        // chain but this wallet never watched the vault address, so the claim
        // can only spend them through refreshBalances' electrum injection.
        val words = h.account.getRecoveryPhrase()
        val recoverName = "tlRtMultiTok2"
        cleanupAccounts(recoverName, 3)
        val recovered = runBlocking(Dispatchers.IO) {
            wallyApp!!.recoverAccount(
                recoverName, 0UL, "", words, ChainSelector.NEXAREGTEST,
                earliestActivity = null, earliestHeight = startHeight, nonstandardActivity = null,
            )
        }
        createdAccounts += recoverName
        recovered.chain.net.exclusiveNodes(setOf(REGTEST_IP))
        waitUntil(90_000, "recovered wallet synced past the deposits") {
            recovered.wallet.syncedHeight >= tipAfterDeposits
        }

        val repo2 = recovered.timeLockVaults
        runBlocking(Dispatchers.IO) { repo2.start() }
        waitUntil(60_000, "vault rediscovered from chain after recovery") {
            repo2.discoverVaultsFromChain()
            repo2.snapshotAll().any { it.address.toString() == vaultAddr }
        }
        val name2 = repo2.snapshotAll().first { it.address.toString() == vaultAddr }.name
        // The injection pass: pulls the vault's native AND token UTXOs from
        // electrum, rebuilding the grouped scripts for the token ones.
        runBlocking(Dispatchers.IO) { repo2.refreshBalances() }

        val result = repo2.claimVault(name2, repo2.nextReceiveAddress()!!)
        assertNotNull(result,
            "claim must sweep a recovered vault whose four same-group token UTXOs were electrum-injected")
        h.rpc.generate(1)
        waitUntil(60_000, "vault emptied of native and all token UTXOs") {
            runBlocking(Dispatchers.IO) { repo2.refreshBalances() }
            val s = repo2.snapshot(name2)
            s != null && s.nativeBalanceSat == 0L && s.tokenBalances.isEmpty()
        }
    }
}
