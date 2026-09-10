@file:OptIn(ExperimentalTime::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.bitcoinunlimited.www.wally.KotlinTarget
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.abbreviatedMonthNames
import info.bitcoinunlimited.www.wally.displayError
import info.bitcoinunlimited.www.wally.displayNotice
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.later
import info.bitcoinunlimited.www.wally.onetlater
import info.bitcoinunlimited.www.wally.tlater
import info.bitcoinunlimited.www.wally.openUrl
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.setTextClipboard
import info.bitcoinunlimited.www.wally.systemTimeZone
import info.bitcoinunlimited.www.wally.wallyApp
import info.bitcoinunlimited.www.wally.ui.views.MpMediaView
import info.bitcoinunlimited.www.wally.ui.HomeTabRow
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.chainToName
import info.bitcoinunlimited.www.wally.ui.requestedHomeTab
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.vaultSuccessAnimationIsPlaying
import info.bitcoinunlimited.www.wally.ui.theme.WallyBorder
import info.bitcoinunlimited.www.wally.ui.theme.focusedChainIconResPath
import info.bitcoinunlimited.www.wally.ui.theme.colorConfirm
import info.bitcoinunlimited.www.wally.ui.theme.colorCredit
import info.bitcoinunlimited.www.wally.ui.theme.wallyPlaceholder
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurple
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurpleExtraLight
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurpleLight
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModel
import info.bitcoinunlimited.www.wally.ui.views.QrCode
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import info.bitcoinunlimited.www.wally.ui.SyncViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModelFake
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.nexa.assets.AssetInfo
import org.nexa.assets.tokenAmountString
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.GroupAuthorityFlags
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.NexaDecimal
import org.nexa.libnexakotlin.NexaToSat
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.rem
import org.nexa.libnexakotlin.SatToNexa
import org.nexa.libnexakotlin.SatToString
import org.nexa.libnexakotlin.TransactionHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import repositories.TimeLockContractRepository
import kotlin.time.Duration.Companion.milliseconds

private val LogIt = GetLog("BU.timelockscreen")

/**
 * The View model for the time lock vault list.
 *
 * The single backing [repositories.TimeLockContractRepository] is held privately and never reaches
 * the view; the view sees [TimeLockVaultUiData] and [Status].
 *
 * The View Model does not subscribe to wallet events itself — [refresh] re-snapshots the repository
 * and emits a new list. Callers (or a Compose `LaunchedEffect`) drive the refresh cadence.
 */
class TimeLockViewModel(private val repo: TimeLockContractRepository) : ViewModel()
{
    companion object
    {
        /**
         * The average seconds per block, used to project an unlock-height into a wall-clock date.
         * Nexa targets 2 minutes per block.
         */
        const val BLOCK_SECONDS: Long = 120L

        /**
         * How often to re-query Electrum for time lock vault balances. A time lock vault address is not
         * in the wallet's general UTXO subscription, so the changes only surface through this poll.
         * 15 s is responsive enough for a user interface (UI) that users only watch occasionally and
         * cheap enough not to hammer the connection manager.
         */
        const val BALANCE_POLL_INTERVAL_MS: Long = 15_000L
    }

    /**
     * The time lock vault repository's [TimeLockContractRepository.VaultSnapshot] plus the wall-clock
     * projections the view needs, which the wallet state cannot supply on its own: they depend on "now"
     * and on a per-chain block-time constant.
     */
    data class TimeLockVaultUiData(
        val snapshot: TimeLockContractRepository.VaultSnapshot,
        /** The estimated unlock instant. `null` once already claimable or when the wallet is unsynced. */
        val estimatedUnlock: Instant?,
        /** Number of Days remaining until unlock. `null` once already claimable or when the wallet is unsynced. */
        val daysUntilUnlock: Double?,
        /**
         * Fraction of the lock-in window that has elapsed, in `[0.0, 1.0]`. `null` if the vault has no
         * deposits yet or when the wallet is unsynced.
         */
        val percentLocked: Float?,
    )

    /** The dispatcher for this view model's launched jobs; repoint them all here. */
    private val jobDispatcher = Dispatchers.Default

    private val _vaults = MutableStateFlow<List<TimeLockVaultUiData>>(emptyList())
    val vaults: StateFlow<List<TimeLockVaultUiData>> = _vaults.asStateFlow()

    /**
     * Resolved asset metadata (name, ticker, icon) for every token and baton group held across the time lock vaults,
     * keyed by group-id hex. Populated off the main thread by [trackVaultAssets] whenever the time lock vault list
     * changes: vault groups live at the contract address, not in the wallet, so they're absent from the wallet's own
     * asset registry and must be tracked from the blockchain. The user interface looks groups up here rather than
     * reaching into the account itself.
     */
    private val _assets = MutableStateFlow<Map<GroupId, AssetInfo>>(emptyMap())
    val assets: StateFlow<Map<GroupId, AssetInfo>> = _assets.asStateFlow()

    /**
     * `true` from construction until the one-time [startJob] startup walk (time lock vault rediscovery) has finished
     * and the loaded list has been published. The UI uses this to tell "no time lock vaults yet" apart from
     * "time lock vaults not loaded yet" when [vaults] is empty: while loading it shows a spinner, once loaded-and-empty
     * it shows the create prompt.
     */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * The screen's UI-flow state (create/claim wizard stages, receipts, the open QR), folded into this
     * view model instead of hoisted `remember`s in the composables — see [TimeLockFlowState].
     */
    internal val flow = TimeLockFlowState()

    private var pollJob: Job? = null

    /**
     * Completes when the one-time time lock vault repository startup (vault rediscovery, UTXO retag, tx-history walk)
     * has finished. Run as an app worker job rather than in the repo constructor so it never blocks the Compose UI
     * composition thread when the screen first opens. The mutating operations ([addVault], [claimVault]) and the balance
     * poll [join] this before touching the repo, so they always act on a fully-loaded vault list (correct index
     * allocation, no half-built view).
     */
    private val startJob = CompletableDeferred<Unit>()

    init
    {
        later {
            try
            {
                repo.start()
            }
            catch (e: Throwable)
            {
                LogIt.error("time lock repository startup failed: $e")
                displayError(e.message ?: i18n(S.tlvLoadFailed))
            }
            finally { startJob.complete(Unit) }
        }
        // Once the (local, non-network) startup load finishes, this publishes the loaded vault list
        // and kicks off a first balance fetch. Deliberately kept out of [startJob] so addVault/claimVault
        // — which join startJob — wait only on the local load and never on this network refresh.
        // On jobDispatcher: publishSnapshot walks the wallet's tx history.
        viewModelScope.launch(jobDispatcher) {
            startJob.join()
            publishSnapshot()
            // Here the local rediscovery walk is done, so the list is now authoritative even though the network balance fetch below
            // is still pending — drop the loading affordance here.
            _isLoading.value = false
            startBalancesReporting()
        }
        // Re-emit UI data whenever the time lock vault repo publishes a fresh on-chain balance snapshot. Drives the per-time lock vault
        // balance updates the user sees after new deposits land.
        viewModelScope.launch(jobDispatcher) {
            repo.onChainNativeSat.collect { publishSnapshot() }
        }
        // Poll on a timer so newly-arrived funds and freshly-discovered time lock vaults show up without user action: each tick re-runs
        // the tx-history time lock vault discovery walk and refreshes balances (which re-publishes the snapshot). On jobDispatcher,
        // never the Main default: the Compose test clock fast-forwards Main-dispatched delays, spinning this loop forever.
        pollJob = viewModelScope.launch(jobDispatcher) {
            startJob.join()
            while (true)
            {
                rediscoverReporting()
                startBalancesReporting()
                // The chain tip moves without any balance change, and onChainNativeSat only emits on change —
                // republish every tick so the per-block state (percent locked, time until unlock, the claimable
                // flip) tracks the tip instead of freezing until the next deposit or claim.
                publishSnapshot()
                delay(BALANCE_POLL_INTERVAL_MS.milliseconds)
            }
        }
    }

    /**
     * Re-run the on-chain time lock vault discovery walk and republish if it surfaced any new time lock vaults.
     * The one-time walk in [repo].start() can fire before a freshly-recovered (or just-reinstalled) wallet has
     * synced the time lock vaults' funding txs into local history — leaving the screen empty with no retry.
     * Repeating it on the poll lets those time lock vaults appear as sync catches up. Local and idempotent
     * (skips vaults already held); runs as a deduped app worker job since it walks the full tx history.
     */
    private fun rediscoverReporting()
    {
        onetlater("timeLockRediscover") {
            try
            {
                val added = repo.discoverVaultsFromChain()
                if (added.isNotEmpty()) publishSnapshot()
            }
            catch (e: Throwable) { LogIt.error("time lock vault rediscovery failed: $e") }
        }
    }

    override fun onCleared()
    {
        pollJob?.cancel()
        super.onCleared()
    }

    /**
     * Refreshes the on-chain balances as a deduped app worker job (poll ticks and user refreshes never stack).
     * Any failure is logged via [LogIt] and surfaced to the end user via [displayError] instead of being silently
     * swallowed.
     */
    fun startBalancesReporting()
    {
        onetlater("timeLockBalances") {
            try
            {
                repo.refreshBalances()
            }
            catch (e: Throwable)
            {
                LogIt.error("time lock balance refresh failed: $e")
                displayError(S.connectionException)
            }
        }
    }

    /**
     * Subscribe to electrum notifications for a [vaultName]'s address until cancelled. Each notification triggers
     * an on-chain refresh, so when funds land the vault card updates without waiting on the 15 s poll.
     *
     * Intended to be driven from a `LaunchedEffect(qrForVault)` in the @Composable: when the user opens a vault's QR,
     * the effect launches this; when they close it (or open another), structured cancellation tears the subscription
     * down through the repo's finally block.
     */
    suspend fun observeVault(vaultName: String) = repo.observeVaultAddress(vaultName)

    /** Recomputes the UI snapshot from the repository. Blocking (walks tx history): call from a worker, not the UI thread. */
    fun refresh()
    {
        publishSnapshot()
        startBalancesReporting()
    }

    /**
     * Block-explorer URL for a time lock vault transaction, e.g. `https://explorer.nexa.org/tx/<idem>`.
     * The vault-transactions list opens this when a row is tapped.
     */
    fun explorerTxUrl(tx: TransactionHistory): String = repo.explorerUrl(tx)

    /**
     * Create a new time lock vault at [unlockBlockHeight] and fund it with [amountSat] native satoshis from
     * the user's main account. Returns the [TimeLockContractRepository.VaultCreated] result so the UI can render
     * a success card with the address and funding tx idem, or `null` when funding failed — the underlying error
     * is surfaced via [displayError] so the UI click handler never crashes the app (mirrors [claimVault]).
     *
     * Time lock vault naming is the repository's concern — the user is never asked to pick a name.
     */
    suspend fun addVault(unlockBlockHeight: Long, amountSat: Long): TimeLockContractRepository.VaultCreated?
    {
        // Wait for the one-time startup walk so the index allocation sees every already-held time lock vault,
        // then build + fund the vault as an app worker job — the funding tx is built and signed synchronously,
        // which must never run on the UI thread.
        startJob.join()
        val pending = CompletableDeferred<TimeLockContractRepository.VaultCreated?>()
        tlater("timeLockAddVault") {
            try
            {
                pending.complete(repo.addVault(unlockBlockHeight, amountSat))
            }
            catch (e: Throwable)
            {
                LogIt.error("time lock vault creation failed: $e")
                displayError(e.message ?: i18n(S.tlvCreateFailed))
                pending.complete(null)
            }
        }
        val created = pending.await()
        refresh()
        return created
    }

    /** Current wallet tip in block height; `null` if the wallet is unsynced. */
    fun currentBlockHeight(): Long? = repo.currentBlockHeight()

    /**
     * Fresh receive address on the user's main account. Used as the default sweep target in the claim flow
     * ("Pay to: <addr> (your wallet)"). `null` if the wallet can't produce a destination.
     */
    fun nextReceiveAddress(): PayAddress? = repo.nextReceiveAddress()

    /**
     * Sweep the time lock vault to [toAddress] using the repo's adjustable-fee claim. Returns the real
     * `(payout, fee, txIdem)` triple on success so the success card can show actuals instead of the placeholders
     * the confirmation card displayed; `null` when the sweep failed — the repository throws, and this catch logs
     * it and surfaces the error via [displayError] so the UI click handler never crashes the app
     * (mirrors [addVault]).
     */
    suspend fun claimVault(vaultName: String, toAddress: PayAddress): TimeLockContractRepository.ClaimResult?
    {
        // The sweep walks the time lock vault's UTXOs and builds + signs the claim tx synchronously, so run it as an app worker job (mirrors [addVault]).
        startJob.join()
        val pending = CompletableDeferred<TimeLockContractRepository.ClaimResult?>()
        tlater("timeLockClaim") {
            try
            {
                pending.complete(repo.claimVault(vaultName, toAddress))
            }
            catch (e: Throwable)
            {
                LogIt.error("time lock claim failed: $e")
                displayError(e.message ?: i18n(S.tlvClaimFailed))
                pending.complete(null)
            }
        }
        val result = pending.await() ?: return null
        refresh()
        return result
    }

    /**
     * Read the local tx history at the given time lock vault's address — used by the time lock vault-transactions sub-screen.
     * Empty when the time lock vault is unknown or the wallet hasn't seen any txs there yet.
     * Walks the wallet's tx history, so it runs as an app worker job.
     */
    suspend fun vaultTransactions(vaultName: String): List<TimeLockContractRepository.VaultTx>
    {
        val pending = CompletableDeferred<List<TimeLockContractRepository.VaultTx>>()
        tlater("timeLockVaultTxs") {
            try { pending.complete(repo.vaultTransactions(vaultName)) }
            catch (e: Throwable)
            {
                LogIt.error("time lock vault tx list failed: $e")
                pending.complete(emptyList())
            }
        }
        return pending.await()
    }

    /**
     * Display name for the chain's native coin (e.g. "nexa", "tNexa"). Used wherever the @Composable screen prints an amount with a unit suffix.
     */
    val nativeUnitName: String = chainToName[repo.chain] ?: "nexa"

    // --- Internals -----------------------------------------------------

    /**
     * Recompute the time lock vault snapshot, publish it, and kick off loading the asset metadata for any newly-seen
     * token / baton groups. The single funnel for every vault-list update so the asset map never drifts from the vaults.
     */
    private fun publishSnapshot()
    {
        val snap = snapshot()
        _vaults.value = snap
        trackVaultAssets(snap)
    }

    /**
     * Resolve (and start loading) the asset metadata for every asset/token and baton group across [vaults], publishing
     * results in [assets]. Only groups not already resolved are fetched, and the fetch runs off the main thread because
     * [assetFor]'s `track` may restart the blockchain connection.
     */
    private fun trackVaultAssets(vaults: List<TimeLockVaultUiData>)
    {
        val groups = buildSet {
            for (v in vaults)
            {
                addAll(v.snapshot.tokenBalances.keys)
                for (a in v.snapshot.authorities) add(a.groupId)
            }
        }
        val missing = groups - _assets.value.keys
        if (missing.isEmpty()) return
        viewModelScope.launch(jobDispatcher) {
            val resolved = _assets.value.toMutableMap()
            var changed = false
            for (gid in missing)
            {
                val info = runCatching { assetFor(gid) }.getOrNull() ?: continue
                resolved[gid] = info
                changed = true
            }
            if (changed) _assets.value = resolved
        }
    }

    /**
     * Asset metadata for one token/baton group held in a vault. Asks the shared [org.nexa.assets.AssetManager] to track the group — which returns
     * an already-loaded entry for wallet-held tokens and otherwise fetches the group's genesis metadata (name, ticker, icon) from the chain. `null` when
     * no app/asset manager is available (previews/tests).
     */
    private fun assetFor(gid: GroupId): AssetInfo? = wallyApp?.assetManager?.track(gid)

    private fun snapshot(): List<TimeLockVaultUiData>
    {
        val all = repo.snapshotAll().map { it.toUiData() }
        val claimable = all.filter { !it.snapshot.hasClaimTx && it.snapshot.isUnlockable }
        val locked = all.filter { !it.snapshot.hasClaimTx && !it.snapshot.isUnlockable }
        val claimed = all.filter { it.snapshot.hasClaimTx }
        return claimable + locked + claimed
    }

    private fun TimeLockContractRepository.VaultSnapshot.toUiData(): TimeLockVaultUiData
    {
        val estimatedUnlock = blocksUntilUnlock?.takeIf { it > 0L }?.let { remaining ->
            Clock.System.now() + (remaining * BLOCK_SECONDS).seconds
        }
        val daysUntilUnlock = blocksUntilUnlock?.takeIf { it > 0L }?.let { remaining ->
            (remaining * BLOCK_SECONDS).toDouble() / SECONDS_PER_DAY
        }
        return TimeLockVaultUiData(
            snapshot = this,
            estimatedUnlock = estimatedUnlock,
            daysUntilUnlock = daysUntilUnlock,
            percentLocked = fractionComplete(this),
        )
    }

    /**
     * Fraction of the lock-in window that has elapsed, from the moment funds arrived until the unlock time.
     *
     * Returns null if the vault has no deposits yet or the wallet is unsynced.
     */
    private fun fractionComplete(snap: TimeLockContractRepository.VaultSnapshot): Float?
    {
        val blocksUntil = snap.blocksUntilUnlock ?: return null
        val earliest = snap.earliestDepositHeight ?: return null
        val unlockHeight = snap.unlockBlockHeight ?: return null
        val window = unlockHeight - earliest
        if (window <= 0L) return 1.0f
        return (1.0 - blocksUntil.toDouble() / window.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}

private const val SECONDS_PER_DAY: Double = 86_400.0

/**
 * Time Lock Vault @Composable UI screen.
 *
 * Resolves the time lock vault list and asset metadata from [vm], then renders either the time lock vault-transactions
 * sub-screen (when that TimeLock nav sub-state is active) or the stateless [TimeLockScreenContent] body. Fires an immediate
 * balance refresh on entry, and while a vault's QR is open subscribes to its electrum status so deposits push into the UI right away.
 */
@Composable
fun TimeLockScreen(
    pill: AccountPillViewModel,
    vm: TimeLockViewModel,
)
{
    val vaults by vm.vaults.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val assets by vm.assets.collectAsState()
    // The screen's UI-flow state lives on the view model ([TimeLockViewModel.flow]). When the screen
    // composes with a detail already current (nav restore under a fresh view model), that vault owns
    // the detail-stage state.
    val flow = remember(vm) {
        if (nav.currentSubState.value?.contentEquals(TIME_LOCK_DETAIL_SUBSTATE) == true)
            (nav.curData.value as? String)?.let { vm.flow.openVault(it) }
        vm.flow
    }
    val flowUi by flow.ui.collectAsState()
    // Fire an immediate refresh whenever this screen enters composition so the user doesn't have to wait up to
    // one BALANCE_POLL_INTERVAL_MS for the next poll tick after navigating in.
    LaunchedEffect(Unit) {
        vm.startBalancesReporting()
    }
    // While the user has a QR open, subscribe to that time lock vault's electrum status so deposits push into
    // the UI immediately. The key is the time lock vault name — switching time lock vaults or closing the QR
    // cancels the previous subscription via structured concurrency.
    LaunchedEffect(flowUi.qrForVault) {
        flowUi.qrForVault?.let { vm.observeVault(it) }
    }

    // The TimeLock screen lives inside the home contracts tab, so every sub-screen keeps the home
    // header: the account pill plus the home tab row. Tab taps unwind the nav stack back to home.
    val homeHeader: @Composable () -> Unit = {
        pill.draw(buttonsEnabled = false) {
            VaultPillContractLine(
                lockedSat = vaults.filter { !it.snapshot.hasClaimTx }.sumOf { it.snapshot.nativeBalanceSat },
                unitName = vm.nativeUnitName,
                onCreate = {
                    if (!inSubState(TIME_LOCK_CREATE_SUBSTATE))
                        nav.go(screen = ScreenId.TimeLock, screenSubState = TIME_LOCK_CREATE_SUBSTATE)
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        HomeTabRow(selectedIndex = 2) { index -> leaveTimeLockToHomeTab(index) }
    }

    // Account switched via the pill: any open sub-state references the old account's vaults, so land on the contract list.
    val firstVm = remember { vm }
    LaunchedEffect(vm) { if (vm !== firstVm) leaveTimeLockToHomeTab(2) }

    // Time Lock Vault-transactions is modeled as a TimeLock sub-state on the global Wally nav stack so the
    // action bar's back arrow / Android BACK pops it cleanly without us needing a separate Compose BackHandler.
    // The byte sentinel just signals "in tx list"; the time lock vault name rides on curData.
    val subState = nav.currentSubState.collectAsState().value
    val navData = nav.curData.collectAsState().value
    val txsVaultName = if (subState != null && subState.contentEquals(TIME_LOCK_TXS_SUBSTATE))
        navData as? String else null
    val txsVault = txsVaultName?.let { name -> vaults.firstOrNull { it.snapshot.name == name } }

    if (txsVault != null)
    {
        val transactions by produceState(emptyList(), txsVault.snapshot.name, vaults) {
            value = vm.vaultTransactions(txsVault.snapshot.name)
        }
        VaultTxListScreen(
            unitName = vm.nativeUnitName,
            transactions = transactions,
            assets = assets,
            explorerTxUrl = vm::explorerTxUrl,
            vault = txsVault,
            pill = homeHeader,
        )
        return
    }

    // The per-time lock vault detail is a second TimeLock sub-state pushed on the nav stack, so the action bar back arrow / Android BACK
    // pops detail → master (and txs → detail, because the txs push preserves this entry beneath it).
    val detailVaultName = if (subState != null && subState.contentEquals(TIME_LOCK_DETAIL_SUBSTATE))
        navData as? String else null
    // Create is a child screen of the list and is pushed the same way.
    val createOpen = subState != null && subState.contentEquals(TIME_LOCK_CREATE_SUBSTATE)

    TimeLockScreenContent(
        vaults = vaults,
        isLoading = isLoading,
        currentBlockHeight = vm.currentBlockHeight(),
        unitName = vm.nativeUnitName,
        onAddVault = { unlockBlockHeight, amountSat ->
            vm.addVault(unlockBlockHeight, amountSat)
        },
        pill = homeHeader,
        nextReceiveAddress = { vm.nextReceiveAddress() },
        onClaim = { vaultName, payoutAddress ->
            vm.claimVault(vaultName, payoutAddress)
        },
        onShowTxs = { vaultName ->
            if (!inSubState(TIME_LOCK_TXS_SUBSTATE, vaultName))
                nav.go(
                    screen = ScreenId.TimeLock,
                    screenSubState = TIME_LOCK_TXS_SUBSTATE,
                    data = vaultName,
                )
        },
        assets = assets,
        detailVaultName = detailVaultName,
        onOpenVault = { vaultName ->
            if (!inSubState(TIME_LOCK_DETAIL_SUBSTATE, vaultName))
                nav.go(
                    screen = ScreenId.TimeLock,
                    screenSubState = TIME_LOCK_DETAIL_SUBSTATE,
                    data = vaultName,
                )
        },
        onCloseDetail = { nav.back() },
        createOpen = createOpen,
        onOpenCreate = {
            if (!inSubState(TIME_LOCK_CREATE_SUBSTATE))
                nav.go(
                    screen = ScreenId.TimeLock,
                    screenSubState = TIME_LOCK_CREATE_SUBSTATE,
                )
        },
        onCloseCreate = {
            // The close can arrive from an async failure continuation after the user already backed out of the child
            // — only pop when the create child is actually still current, else a second back() would throw the user
            // off the TimeLock screen entirely.
            if (inSubState(TIME_LOCK_CREATE_SUBSTATE)) nav.back()
        },
        flow = flow,
    )
}

/** Pop every TimeLock entry off the nav stack and land on the home tab [tab]. */
private fun leaveTimeLockToHomeTab(tab: Int)
{
    requestedHomeTab = tab
    while (nav.currentScreen.value == ScreenId.TimeLock && nav.hasBack() != ScreenId.None) nav.back()
}

/**
 * True when the nav's current sub-state (read at event time, not the composed snapshot) is [want] carrying [data].
 * Guards the sub-state pushes against repeat taps landing before recomposition — a second push would bury one
 * child entry under another, making the first Cancel/BACK a visual no-op — and guards async closes against popping
 * a screen that is no longer the current screen.
 */
private fun inSubState(want: ByteArray, data: Any? = null): Boolean
{
    val cur = nav.currentSubState.value ?: return false
    return cur.contentEquals(want) && (data == null || nav.curData.value == data)
}

/**
 * Navigation marker byte sequence on `nav.currentSubState` that means "TimeLock sub-screen: vault transactions".
 * The actual time lock vault name rides on `nav.curData`. Picked as a stable constant so both the writer
 * ([TimeLockScreen] when the user taps a vault's Transactions button) and the reader (the same composable on the next recomposition) agree.
 */
private val TIME_LOCK_TXS_SUBSTATE: ByteArray = byteArrayOf(0x54, 0x58, 0x53) // "TXS"

/**
 * Navigation marker byte sequence on `nav.currentSubState` for the per-time lock vault detail (master–detail's detail level).
 * The time lock vault name rides on `nav.curData`, exactly like [TIME_LOCK_TXS_SUBSTATE].
 */
private val TIME_LOCK_DETAIL_SUBSTATE: ByteArray = byteArrayOf(0x44, 0x54, 0x4C) // "DTL"

/**
 * Navigation marker byte sequence on `nav.currentSubState` for the create-time lock vault child screen —
 * the create flow is a child of the master list, not an inline card, so BACK/Cancel pops back to the list.
 */
internal val TIME_LOCK_CREATE_SUBSTATE: ByteArray = byteArrayOf(0x4E, 0x45, 0x57) // "NEW"

/**
 * Snapshot of what the success card needs to render after the [onAddVault] returns — combines the repo's
 * [TimeLockContractRepository.VaultCreated] with the unlock-height the user picked (which only the UI screen knows).
 */
internal data class VaultCreatedView(
    val created: TimeLockContractRepository.VaultCreated,
    val unlockBlockHeight: Long,
    val amountSat: Long,
)

/**
 * Stateless screen body — master–detail.
 *
 * With [detailVaultName] null, this renders the master level: the header and one tappable
 * [VaultEntryCard] pill per vault. Tapping a time lock vault card fires [onOpenVault];
 * the caller ([TimeLockScreen]) pushes the detail nav sub-state and re-renders with [detailVaultName] set, which shows the
 * detail level: the vault's own pill, carrying its holdings, the claim flow and the facts. [onCloseDetail] pops
 * back to the master level.
 *
 * The pill is rendered through the [pill] slot so the `@Preview` can pass a placeholder without constructing an [AccountPillViewModel].
 */
@Composable
internal fun TimeLockScreenContent(
    vaults: List<TimeLockViewModel.TimeLockVaultUiData>,
    isLoading: Boolean = false,
    currentBlockHeight: Long?,
    unitName: String,
    onAddVault: suspend (unlockBlockHeight: Long, amountSat: Long) -> TimeLockContractRepository.VaultCreated?,
    pill: @Composable () -> Unit,
    nextReceiveAddress: () -> PayAddress? = { null },
    onClaim: suspend (vaultName: String, payoutAddress: PayAddress) -> TimeLockContractRepository.ClaimResult? = { _, _ -> null },
    onShowTxs: (vaultName: String) -> Unit = {},
    assets: Map<GroupId, AssetInfo> = emptyMap(),
    detailVaultName: String? = null,
    onOpenVault: (vaultName: String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    createOpen: Boolean = false,
    onOpenCreate: () -> Unit = {},
    onCloseCreate: () -> Unit = {},
    /** The screen's UI-flow state, one object shared across the levels — see [TimeLockFlowState]. */
    flow: TimeLockFlowState = remember { TimeLockFlowState() },
)
{
    val scope = rememberCoroutineScope()
    val ui by flow.ui.collectAsState()

    if (detailVaultName != null)
    {
        val detailVault = vaults.firstOrNull { it.snapshot.name == detailVaultName }
        VaultDetailContent(
            vault = detailVault,
            unitName = unitName,
            currentBlockHeight = currentBlockHeight,
            assets = assets,
            pill = pill,
            flow = flow,
            nextReceiveAddress = nextReceiveAddress,
            onConfirmClaim = confirm@{ payoutAddress ->
                if (flow.submitting || detailVault == null) return@confirm
                flow.submitting = true
                scope.launch {
                    try
                    {
                        LogIt.info("TimeLockScreen: Confirm & claim tapped for $detailVaultName → $payoutAddress")
                        val result = onClaim(detailVaultName, payoutAddress)
                        LogIt.info("TimeLockScreen: claim result=$result")
                        if (result != null) vaultSuccessAnimationIsPlaying.update { it + 1 }
                        // Land the receipt only if the detail state still belongs to this time lock vault
                        // (the user may have opened a different one while the sweep broadcast).
                        if (result != null && flow.detailStateFor == detailVaultName)
                        {
                            flow.claimedView = VaultClaimedView(
                                vaultName = detailVaultName,
                                payoutAddress = payoutAddress,
                                result = result,
                                snapshot = detailVault.snapshot,
                            )
                            flow.detailStage = DetailStage.Claimed
                        }
                        // On failure the repository has already raised a displayError; keep the claim card
                        // open so the user can hit Cancel or retry once the condition clears.
                    }
                    finally { flow.submitting = false }
                }
            },
            onShowTxs = { onShowTxs(detailVaultName) },
            onCloseDetail = onCloseDetail,
        )
        return
    }

    // The create time lock vault flow is a child screen of the list (like the detail level), not
    // an inline card: the pill's New vault pushes it, BACK/Cancel pops it. Entering it always starts
    // a fresh form — whoever opened it — so a success card dismissed with BACK can never come back.
    LaunchedEffect(createOpen) { if (createOpen) flow.openCreate() }
    if (createOpen)
    {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            Spacer(Modifier.height(16.dp))
            pill()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(10.dp))
                when (ui.stage)
                {
                    Stage.Overview, Stage.Create ->
                        CreateVaultCard(
                            currentBlockHeight = currentBlockHeight,
                            unitName = unitName,
                            draft = ui.draft,
                            onCancel = {
                                flow.stage = Stage.Overview
                                onCloseCreate()
                            },
                            onReview = { d ->
                                flow.draft = d
                                flow.stage = Stage.Confirm
                            },
                        )
                    Stage.Confirm ->
                        ui.draft?.let { d ->
                            ConfirmVaultCard(
                                draft = d,
                                unitName = unitName,
                                currentBlockHeight = currentBlockHeight,
                                busy = ui.submitting,
                                onBack = { flow.stage = Stage.Create },
                                onConfirm = {
                                    if (!flow.submitting)
                                    {
                                        flow.submitting = true
                                        scope.launch {
                                            try
                                            {
                                                val created = onAddVault(d.unlockBlock, d.amountSat)
                                                if (created != null)
                                                {
                                                    flow.createdView = VaultCreatedView(
                                                        created = created,
                                                        unlockBlockHeight = d.unlockBlock,
                                                        amountSat = d.amountSat,
                                                    )
                                                    flow.draft = null
                                                    flow.stage = Stage.Success
                                                    vaultSuccessAnimationIsPlaying.update { it + 1 }
                                                }
                                                else
                                                {
                                                    // The time lock vault funding failed;
                                                    // the error is shown via the global
                                                    // alert sink. Close the child screen
                                                    // so the user can retry from the list
                                                    // rather than facing a half- finished
                                                    // confirm card.
                                                    flow.draft = null
                                                    flow.stage = Stage.Overview
                                                    onCloseCreate()
                                                }
                                            }
                                            finally { flow.submitting = false }
                                        }
                                    }
                                },
                            )
                        }
                    Stage.Success ->
                        ui.createdView?.let { v ->
                            VaultCreatedCard(
                                view = v,
                                unitName = unitName,
                                currentBlockHeight = currentBlockHeight,
                                onDone = {
                                    flow.createdView = null
                                    flow.stage = Stage.Overview
                                    onCloseCreate()
                                },
                            )
                        }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Spacer(Modifier.height(16.dp))
        pill()
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))
            // Empty list: tell the user whether we're still loading or whether there are genuinely are no vaults.
            if (vaults.isEmpty())
            {
                Spacer(Modifier.height(8.dp))
                if (isLoading) VaultsLoading() else VaultsEmpty()
            }
            for (vault in vaults)
            {
                Spacer(Modifier.height(8.dp))
                VaultEntryCard(
                    vault = vault,
                    unitName = unitName,
                    assets = assets,
                    onOpen = {
                        flow.openVault(vault.snapshot.name)
                        onOpenVault(vault.snapshot.name)
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

internal enum class Stage { Overview, Create, Confirm, Success }

/** Detail-level vault stages: viewing the vault, the claim card, the claimed (success) card. */
internal enum class DetailStage { View, Claim, Claimed }

/**
 * Immutable snapshot of the TimeLock screen's cross-level UI-flow state; the live copy is the
 * [TimeLockFlowState.ui] StateFlow on [TimeLockViewModel.flow].
 */
internal data class TimeLockFlowUi(
    /** The create flow's stage. */
    val stage: Stage = Stage.Overview,
    /** The create inputs snapshotted at Review; the confirm card renders it, Back restores it. */
    val draft: VaultReviewDraft? = null,
    /** The created-vault receipt the success card renders; null once dismissed. */
    val createdView: VaultCreatedView? = null,
    /**
     * True while a create/claim transaction is building + broadcasting on a background coroutine
     * dispatcher. Disables the confirm buttons so a second tap can't fire a duplicate funding/sweep tx
     * while the first is still in flight.
     */
    val submitting: Boolean = false,
    /** The detail level's stage: viewing, the claim card, or the claimed receipt. */
    val detailStage: DetailStage = DetailStage.View,
    /** The claim receipt, captured at confirm time because the swept vault disappears from the list. */
    val claimedView: VaultClaimedView? = null,
    /**
     * The time lock vault the detail-stage state belongs to. Reset happens when a different time lock
     * vault is opened (see [TimeLockFlowState.openVault]), not when the detail closes — so a claim
     * finishing after the user backed out still lands its receipt for the next visit.
     */
    val detailStateFor: String? = null,
    /** The vault whose deposit QR is revealed; null when none is. */
    val qrForVault: String? = null,
)

/**
 * The screen's cross-level UI-flow state — one [TimeLockFlowUi] in a [StateFlow], owned by
 * [TimeLockViewModel.flow] and passed whole to the levels that use it instead of separate hoisted
 * `remember`s handed down value-by-value, so every read and update of a value is findable from its
 * single property (review feedback on !702).
 *
 * Composables read the current snapshot by collecting [ui]; event handlers read and write through the
 * accessors and transition methods, which see the freshest value rather than the composed snapshot.
 * A standalone instance (no view model, no repository) is enough for previews and UI tests.
 */
internal class TimeLockFlowState
{
    private val _ui = MutableStateFlow(TimeLockFlowUi())
    val ui: StateFlow<TimeLockFlowUi> = _ui.asStateFlow()

    var stage: Stage
        get() = _ui.value.stage
        set(v) { _ui.update { it.copy(stage = v) } }

    var draft: VaultReviewDraft?
        get() = _ui.value.draft
        set(v) { _ui.update { it.copy(draft = v) } }

    var createdView: VaultCreatedView?
        get() = _ui.value.createdView
        set(v) { _ui.update { it.copy(createdView = v) } }

    var submitting: Boolean
        get() = _ui.value.submitting
        set(v) { _ui.update { it.copy(submitting = v) } }

    var detailStage: DetailStage
        get() = _ui.value.detailStage
        set(v) { _ui.update { it.copy(detailStage = v) } }

    var claimedView: VaultClaimedView?
        get() = _ui.value.claimedView
        set(v) { _ui.update { it.copy(claimedView = v) } }

    var detailStateFor: String?
        get() = _ui.value.detailStateFor
        set(v) { _ui.update { it.copy(detailStateFor = v) } }

    var qrForVault: String?
        get() = _ui.value.qrForVault
        set(v) { _ui.update { it.copy(qrForVault = v) } }

    /** Entry-card tap: the detail-stage state is reset when a different vault is opened. */
    fun openVault(name: String)
    {
        _ui.update {
            if (it.detailStateFor == name) it
            else it.copy(detailStateFor = name, detailStage = DetailStage.View, claimedView = null)
        }
    }

    /**
     * Header tap: always land on the form. A Create/Confirm left by backing out resumes, but a stale
     * Success — BACK-dismissed or landed after the user left mid-submit — is dropped: the button says
     * "New vault", not "last receipt".
     */
    fun openCreate()
    {
        _ui.update {
            if (it.stage == Stage.Overview || it.stage == Stage.Success)
                it.copy(createdView = null, draft = null, stage = Stage.Create)
            else it
        }
    }

    /** Cancel returns to an un-dismissed receipt rather than dropping it. */
    fun cancelClaim()
    {
        _ui.update { it.copy(detailStage = if (it.claimedView != null) DetailStage.Claimed else DetailStage.View) }
    }

    fun dismissClaimed()
    {
        _ui.update { it.copy(claimedView = null, detailStage = DetailStage.View) }
    }

    fun toggleQr(name: String)
    {
        _ui.update { it.copy(qrForVault = if (it.qrForVault == name) null else name) }
    }
}

/**
 * Detail level of the master–detail screen. One brick pill holds the whole vault: state icon and name,
 * what it holds, the lock dates, the Claim action and a white window with the deposit address, the
 * history link and the footer notes. Tapping Claim replaces that pill with the claim pill, and the
 * receipt pill replaces both once the sweep is signed.
 *
 * The stage transitions that touch only view state (open/cancel/dismiss claim, QR toggle) are written to
 * [flow] directly; [onConfirmClaim] stays a callback because the sweep itself runs in the caller.
 *
 * [vault] is null when the time lock vault has vanished from the snapshot (e.g. a post-sweep refresh); the claimed success card, captured
 * at confirm time, is then the only thing rendered so the user can still read and dismiss the outcome.
 */
@Composable
private fun VaultDetailContent(
    vault: TimeLockViewModel.TimeLockVaultUiData?,
    unitName: String,
    currentBlockHeight: Long?,
    assets: Map<GroupId, AssetInfo>,
    pill: @Composable () -> Unit = {},
    flow: TimeLockFlowState,
    nextReceiveAddress: () -> PayAddress?,
    onConfirmClaim: (payoutAddress: PayAddress) -> Unit,
    onShowTxs: () -> Unit,
    onCloseDetail: () -> Unit,
)
{
    val ui by flow.ui.collectAsState()
    val onCopy = {
        vault?.snapshot?.address?.let { addr ->
            setTextClipboard(addr.toString())
            displayNotice(S.copiedToClipboard)
        }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Spacer(Modifier.height(16.dp))
        pill()
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            if (vault == null)
            {
                // Post-sweep: the time lock vault is gone from the list. Keep the outcome card visible; Done pops back to the master list.
                ui.claimedView?.let { v ->
                    Spacer(Modifier.height(10.dp))
                    VaultClaimedCard(
                        view = v,
                        unitName = unitName,
                        currentBlockHeight = currentBlockHeight,
                        assets = assets,
                        onDone = onCloseDetail,
                    )
                }
            }
            else
            {
                Spacer(Modifier.height(10.dp))
                when (ui.detailStage)
                {
                    DetailStage.Claim ->
                        ClaimVaultCard(
                            vault = vault,
                            unitName = unitName,
                            currentBlockHeight = currentBlockHeight,
                            assets = assets,
                            payoutAddress = nextReceiveAddress(),
                            busy = ui.submitting,
                            onCancel = flow::cancelClaim,
                            onConfirm = onConfirmClaim,
                        )
                    DetailStage.Claimed ->
                        ui.claimedView?.let { v ->
                            VaultClaimedCard(
                                view = v,
                                unitName = unitName,
                                currentBlockHeight = currentBlockHeight,
                                assets = assets,
                                onDone = flow::dismissClaimed,
                            )
                        }
                    DetailStage.View ->
                        VaultContractPill(
                            vault = vault,
                            unitName = unitName,
                            currentBlockHeight = currentBlockHeight,
                            assets = assets,
                            qrShown = ui.qrForVault == vault.snapshot.name,
                            onToggleQr = { flow.toggleQr(vault.snapshot.name) },
                            onCopy = onCopy,
                            onShowTxs = onShowTxs,
                            onClaim = { flow.detailStage = DetailStage.Claim },
                        )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Sub-screen showing every transaction that touched one time lock vault's address. Not a modal takeover:
 * the account pill stays above the vault's own brick pill, so the user keeps their bearings while
 * reading the history.
 *
 * No in-screen back affordance — the end user returns via the Wally action bar's back arrow (or Android BACK),
 * which pops the TimeLock sub-state we pushed on `nav` when entering. Keeps the header consistent with
 * the rest of the app instead of double-decorating with a custom row.
 *
 * Layout: the account pill, then one brick pill carrying the vault header, the "Vault transactions"
 * heading with its count, and a white window of [VaultTxRow]s. Newest-first; the earliest deposit is
 * decorated as the vault's creation tx.
 */
@Composable
internal fun VaultTxListScreen(
    unitName: String,
    transactions: List<TimeLockContractRepository.VaultTx>,
    assets: Map<GroupId, AssetInfo> = emptyMap(),
    explorerTxUrl: (tx: TransactionHistory) -> String? = { null },
    vault: TimeLockViewModel.TimeLockVaultUiData? = null,
    pill: @Composable () -> Unit = {},
)
{
    // The earliest deposit is the time lock vault's creation funding tx — tag it so the row reads
    // "Created" instead of the generic top-up wording.
    val creationIdem = transactions
        .filter { it.kind == TimeLockContractRepository.VaultTxKind.Deposit }
        .minByOrNull { it.history.date }
        ?.history?.tx?.idem
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Spacer(Modifier.height(16.dp))
        pill()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .background(if (vault?.snapshot?.hasClaimTx == true) timeLockVaultClaimedWash else timeLockVaultSheen, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                if (vault != null)
                {
                    VaultPillHeader(vault = vault, unitName = unitName, assets = assets, amountFontSize = 21.sp)
                    Spacer(Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = i18n(S.tlvTransactions),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = transactions.size.toString(),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                PillWindow {
                    if (transactions.isEmpty())
                    {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = i18n(S.tlvNoTransactions),
                                color = Color(0xFF707070),
                                fontSize = 13.sp,
                            )
                        }
                    }
                    transactions.forEachIndexed { i, tx ->
                        if (i > 0) Spacer(Modifier.height(2.dp))
                        VaultTxRow(
                            tx = tx,
                            unitName = unitName,
                            isCreation = tx.history.tx.idem == creationIdem &&
                                tx.kind == TimeLockContractRepository.VaultTxKind.Deposit,
                            assets = assets,
                            explorerTxUrl = explorerTxUrl,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * One transaction band inside the [VaultTxListScreen]'s window, in the home screen's transaction-row
 * shape: direction arrow and type label on the left, the amounts and the timestamp on the right.
 * A vault claim always sweeps everything, so a claim row is the whole vault leaving at once.
 */
@Composable
private fun VaultTxRow(
    tx: TimeLockContractRepository.VaultTx,
    unitName: String,
    isCreation: Boolean = false,
    assets: Map<GroupId, AssetInfo> = emptyMap(),
    explorerTxUrl: (tx: TransactionHistory) -> String? = { null },
)
{
    val pending = tx.history.confirmedHeight <= 0L
    val isClaim = tx.kind == TimeLockContractRepository.VaultTxKind.Claim
    val accent = if (pending) colorConfirm else wallyPurple
    val icon = if (isClaim) Icons.AutoMirrored.Outlined.ArrowForward else Icons.Outlined.ArrowDownward
    val label = when
    {
        isCreation -> i18n(S.tlvTxCreated)
        isClaim -> i18n(S.tlvClaim)
        else -> i18n(S.tlvTopUp)
    }
    // A claim's headline is the net the wallet received — the gross time lock vault balance less the
    // network fee (broken out on the fee line below). feeSat is 0 for deposits and for claims whose
    // fee can't be attributed, so this is a no-op there.
    val netSat = tx.amountSat - tx.feeSat
    val sign = if (isClaim) "−" else "+"
    val hasAssets = tx.tokenMovements.isNotEmpty() || tx.authorities.isNotEmpty()
    // A token-only claim/deposit moves no pure native value (the dust rides on the token outputs),
    // so suppress the "+0" headline and let the asset lines carry the value instead.
    val showNative = tx.amountSat != 0L || !hasAssets

    // Tapping a row opens the TXes in the chain's block-explorer (same /tx/<idem> link the main tx history uses).
    // Disabled when no block explorer resolver is supplied (previews/tests).
    val explorerUri = explorerTxUrl(tx.history)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (explorerUri != null) Modifier.clickable { openUrl(explorerUri) }
                else Modifier,
            )
            .background(wallyPurpleExtraLight)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = Color(0xFF1A1A1A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // The headline is the net received; state the fee that was deducted from the gross vault balance to reach it.
            if (isClaim && tx.feeSat > 0L)
            {
                Text(
                    text = i18n(S.tlvNetworkFee) % mapOf("amt" to SatToString(tx.feeSat), "unit" to unitName),
                    color = Color(0xFF707070),
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (showNative)
            {
                NexaAmount(
                    text = sign + nexaAmountText(netSat),
                    fontSize = 15.sp,
                    color = accent,
                    unit = unitName,
                    logoSize = 14.dp,
                    unitFontSize = 10.sp,
                )
            }
            // Token and baton movements at the time lock vault address for this tx, so an asset deposit/claim shows what actually moved.
            for ((group, tokenAmount) in tx.tokenMovements)
            {
                Spacer(Modifier.height(2.dp))
                TokenChip(
                    group = group,
                    amount = tokenAmount,
                    assets = assets,
                    color = accent,
                    sign = sign,
                    fontSize = 13.sp,
                    iconSize = 16.dp,
                )
            }
            for (authority in tx.authorities)
            {
                Spacer(Modifier.height(2.dp))
                val controlled = controlledTokenLabel(assets[authority.groupId], authority.groupId)
                Text(
                    text = sign + (i18n(S.tlvAuthorityMovement) % mapOf("caps" to authorityCapabilities(authority), "token" to controlled)),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = formatTxDate(tx.history.date),
                color = wallyPurple,
                fontSize = 11.sp,
            )
        }
    }
}

/** "30 Apr 2024 · 14:54" — matches the design's mono timestamp format. */
private fun formatTxDate(epochMillis: Long): String
{
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val ldt = instant.toLocalDateTime(systemTimeZone)
    val month = abbreviatedMonthNames()[ldt.month.ordinal]
    val hh = ldt.hour.toString().padStart(2, '0')
    val mm = ldt.minute.toString().padStart(2, '0')
    return "${ldt.day} $month ${ldt.year} · $hh:$mm"
}

/**
 * Compact rendering of a 64-char group id hex for the time lock vault UI: first 10 and last 4 characters with an ellipsis,
 * enough to recognise a token/baton's group without spilling the full id across a card or history row.
 * Short IDs (≤ 16 chars) are returned unchanged.
 */
internal fun shortGroupId(groupIdHex: String): String =
    if (groupIdHex.length <= 16) groupIdHex
    else groupIdHex.take(10) + "…" + groupIdHex.takeLast(4)

/**
 * Human-readable capability list for an authority (baton) UTXO. This is derived from its decoded flag bits,
 * e.g. "Mint·Melt". Returns "Authority" when no known capability bit is set (a bare authority carrying no powers).
 */
internal fun authorityCapabilities(authority: GroupInfo): String =
    buildList {
        val flags = authority.authorityFlags
        if (flags and GroupAuthorityFlags.MINT != 0UL) add(i18n(S.tlvCapMint))
        if (flags and GroupAuthorityFlags.MELT != 0UL) add(i18n(S.tlvCapMelt))
        if (flags and GroupAuthorityFlags.BATON != 0UL) add(i18n(S.tlvCapBaton))
        if (flags and GroupAuthorityFlags.RESCRIPT != 0UL) add(i18n(S.tlvCapRescript))
        if (flags and GroupAuthorityFlags.SUBGROUP != 0UL) add(i18n(S.tlvCapSubgroup))
    }.joinToString("·").ifEmpty { i18n(S.tlvAuthority) }

/**
 * The display label for the token group a baton controls: the token's name and ticker (e.g. "TimeLockTest (TLT)")
 * which is taken from the [asset] the view model resolved for the group (see [TimeLockViewModel.assets]),
 * falling back to the name or ticker alone, then the token address, then a shortened group id when the asset hasn't resolved (previews/tests).
 */
@Composable
internal fun controlledTokenLabel(asset: AssetInfo?, group: GroupId): String
{
    // Recompose when the asset finishes loading so the ticker/name fill in.
    asset?.loadStateObservable?.collectAsState()
    val nftTitle = asset?.nftObservable?.collectAsState()?.value?.title?.takeIf { it.isNotEmpty() }
    val assetName = asset?.nameObservable?.collectAsState()?.value?.takeIf { it.isNotBlank() }
    val name = nftTitle ?: assetName
    val ticker = asset?.ticker?.takeIf { it.isNotBlank() }
    return when
    {
        name != null && ticker != null -> "$name ($ticker)"
        name != null -> name
        ticker != null -> ticker
        asset != null -> shortGroupId(asset.groupId.toStringNoPrefix())
        else -> shortGroupId(group.toHex())
    }
}

// --- Brick-pill design language -------------------------------------------------------
// Every TimeLock surface is a brick sheen pill carrying white text; structured data inside a pill
// lives in a white [PillWindow]; in-pill actions are [PillGhostButton] or [PillWhiteButton].

/**
 * A native-coin amount led by the official nexa logo. Every nexa amount on these screens renders
 * through this, so the logo is never dropped and the digits are always monospaced.
 */
/**
 * The contract line the account pill grows while the user is inside the Time Lock Vault contract:
 * what this account has locked, and the way to lock more. It replaces the pill's account action row,
 * which is account-level and has nothing to do with the contract.
 */
@Composable
internal fun VaultPillContractLine(lockedSat: Long, unitName: String, onCreate: () -> Unit)
{
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = i18n(S.tlvContractLineLabel),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(8.dp))
        NexaAmount(
            text = nexaAmountText(lockedSat),
            fontSize = 13.sp,
            color = Color.White,
            unit = unitName,
            logoSize = 15.dp,
            unitFontSize = 10.sp,
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                .clickable(onClick = onCreate)
                .testTag("PillNewVaultButton")
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = i18n(S.tlvNewVault),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NexaAmount(
    text: String,
    fontSize: TextUnit,
    color: Color,
    unit: String? = null,
    logoSize: Dp = 16.dp,
    unitFontSize: TextUnit = 11.sp,
    unitColor: Color = color.copy(alpha = 0.80f),
)
{
    Row(verticalAlignment = Alignment.CenterVertically) {
        ResImageView(focusedChainIconResPath(), Modifier.size(logoSize), "Blockchain icon")
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        if (unit != null)
        {
            Spacer(Modifier.width(5.dp))
            Text(
                text = unit,
                color = unitColor,
                fontSize = unitFontSize,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * The token group's own coin: its asset icon clipped to a circle, or — until an icon is available —
 * a filled disc carrying the ticker's first letter.
 */
@Composable
private fun AssetCoin(
    asset: AssetInfo?,
    group: GroupId,
    size: Dp,
    initialFontSize: TextUnit = 9.sp,
)
{
    // ticker is a plain var; these two flows are what republish once the asset resolves.
    asset?.nameObservable?.collectAsState()?.value
    asset?.loadStateObservable?.collectAsState()?.value
    val iconImage = asset?.iconImageState?.collectAsState()?.value
    val iconBytes = asset?.iconBytes?.collectAsState()?.value
    val iconUri = asset?.iconUri?.toString() ?: ""
    val hasIcon = iconImage != null || iconBytes != null || iconUri.isNotEmpty()
    val ticker = asset?.ticker?.takeIf { it.isNotBlank() } ?: group.toHex()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hasIcon) Color.Transparent else timeLockVaultTint),
        contentAlignment = Alignment.Center,
    ) {
        if (hasIcon)
        {
            MpMediaView(iconImage, iconBytes, iconUri, hideMusicView = true) { _, draw ->
                draw(Modifier.fillMaxSize())
            }
        }
        else
        {
            Text(
                text = ticker.take(1).uppercase(),
                color = Color.White,
                fontSize = initialFontSize,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** One token holding: the token's own coin, then its amount and ticker. */
@Composable
private fun TokenChip(
    group: GroupId,
    amount: Long,
    assets: Map<GroupId, AssetInfo>,
    color: Color,
    sign: String = "",
    fontSize: TextUnit = 12.sp,
    iconSize: Dp = 18.dp,
)
{
    val asset = assets[group]
    // ticker and the genesis decimals are plain vars; these two flows are what republish once the asset resolves.
    asset?.nameObservable?.collectAsState()?.value
    asset?.loadStateObservable?.collectAsState()?.value
    val decimals = asset?.tokenInfo?.genesisInfo?.decimal_places
    val amt = if (decimals != null && decimals > 0) tokenAmountString(amount, decimals) else groupDigits(amount)
    val sym = asset?.ticker?.takeIf { it.isNotBlank() } ?: shortGroupId(group.toHex())
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssetCoin(asset = asset, group = group, size = iconSize)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$sign$amt $sym",
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

/** One authority baton: the medal glyph, then the coin and ticker of the group it controls. */
@Composable
private fun BatonChip(
    authority: GroupInfo,
    assets: Map<GroupId, AssetInfo>,
    color: Color,
    fontSize: TextUnit = 12.sp,
    iconSize: Dp = 18.dp,
)
{
    val asset = assets[authority.groupId]
    // ticker is a plain var; these two flows are what republish once the asset resolves.
    asset?.nameObservable?.collectAsState()?.value
    asset?.loadStateObservable?.collectAsState()?.value
    val sym = asset?.ticker?.takeIf { it.isNotBlank() } ?: shortGroupId(authority.groupId.toHex())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.WorkspacePremium,
            contentDescription = null,
            tint = vaultBatonTint,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(5.dp))
        AssetCoin(asset = asset, group = authority.groupId, size = iconSize)
        Spacer(Modifier.width(5.dp))
        Text(
            text = sym,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

/** Locked vault: a closed lock. Unlocked: an open lock. Claimed: a check. */
@Composable
private fun VaultStateIcon(
    vault: TimeLockViewModel.TimeLockVaultUiData,
    size: Dp = 17.dp,
    tint: Color = Color.White,
)
{
    Icon(
        imageVector = when
        {
            vault.snapshot.hasClaimTx -> Icons.Outlined.Check
            vault.snapshot.isUnlockable -> Icons.Outlined.LockOpen
            else -> Icons.Outlined.Lock
        },
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

/** The white window a pill puts structured data in. */
@Composable
private fun PillWindow(content: @Composable ColumnScope.() -> Unit)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        content = content,
    )
}

/** Label / value row inside a [PillWindow]; [right] carries whatever the value is made of. */
@Composable
private fun WindowRow(
    label: String,
    subLabel: String? = null,
    onTap: (() -> Unit)? = null,
    right: @Composable () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(0.4f, fill = false)) {
            Text(
                text = label,
                color = Color(0xFF707070),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            if (subLabel != null)
            {
                Text(
                    text = subLabel,
                    color = Color(0xFF707070),
                    fontSize = 10.sp,
                )
            }
        }
        Box(
            modifier = Modifier.weight(0.6f, fill = false),
            contentAlignment = Alignment.CenterEnd,
        ) {
            right()
        }
    }
}

/** Plain value text for a [WindowRow]. */
@Composable
private fun WindowValue(text: String, fontSize: TextUnit = 13.sp)
{
    Text(
        text = text,
        color = Color(0xFF1A1A1A),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
    )
}

/** Label above a full-width value: for addresses and tx ids, which are too long for a [WindowRow]'s value column. */
@Composable
private fun WindowStack(
    label: String,
    subLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFF707070),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
        if (subLabel != null)
        {
            Text(
                text = subLabel,
                color = Color(0xFF707070),
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Plain value text for a [WindowStack]. */
@Composable
private fun WindowStackText(text: String)
{
    Text(
        text = text,
        color = Color(0xFF1A1A1A),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A full address or tx id in a [WindowStack], wrapped over as many lines as it needs, never abbreviated. */
@Composable
private fun WindowStackValue(text: String, color: Color = Color(0xFF1A1A1A))
{
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        lineHeight = 17.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Uppercase caption on a brick pill. */
@Composable
private fun PillLabel(text: String)
{
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

/** Secondary in-pill action: transparent, white hairline border, white label. */
@Composable
private fun PillGhostButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
)
{
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.5f),
        ),
    ) {
        if (icon != null)
        {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Primary in-pill action: filled, with the fill's own contrasting label and icon tint. */
@Composable
private fun PillWhiteButton(
    label: String,
    icon: ImageVector? = null,
    iconTrailing: Boolean = false,
    enabled: Boolean = true,
    fill: Color = Color.White,
    fg: Color = timeLockVaultDeep,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
)
{
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = fg,
            disabledContainerColor = Color.White.copy(alpha = 0.45f),
            disabledContentColor = fg.copy(alpha = 0.55f),
        ),
    ) {
        if (icon != null && !iconTrailing)
        {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (icon != null && iconTrailing)
        {
            Spacer(Modifier.width(6.dp))
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * The pill's identity block, shared by every vault surface: the state icon and the monospace vault
 * name, then what the vault holds — nexa, one [TokenChip] per token, one [BatonChip] per baton.
 */
@Composable
private fun VaultPillHeader(
    vault: TimeLockViewModel.TimeLockVaultUiData,
    unitName: String,
    assets: Map<GroupId, AssetInfo>,
    amountFontSize: TextUnit = 24.sp,
)
{
    Row(verticalAlignment = Alignment.CenterVertically) {
        VaultStateIcon(vault)
        Spacer(Modifier.width(8.dp))
        Text(
            text = vault.snapshot.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(9.dp))
    if (!vault.snapshot.isBalanceLoaded)
    {
        Text(
            text = i18n(S.tlvBalanceLoading),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
        )
    }
    else if (vault.snapshot.nativeBalanceSat > 0L || vault.snapshot.tokenBalances.none { it.value > 0L })
    {
        NexaAmount(
            text = nexaAmountText(vault.snapshot.nativeBalanceSat),
            fontSize = amountFontSize,
            color = Color.White,
            unit = unitName,
            logoSize = 18.dp,
            unitFontSize = 12.sp,
        )
    }
    VaultAssetLines(vault = vault, assets = assets)
}

/** The token line and the baton line of a vault pill; each is omitted when the vault holds none. */
@Composable
private fun VaultAssetLines(
    vault: TimeLockViewModel.TimeLockVaultUiData,
    assets: Map<GroupId, AssetInfo>,
    color: Color = Color.White,
)
{
    val tokens = vault.snapshot.tokenBalances.filter { it.value > 0L }
    if (tokens.isNotEmpty())
    {
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tokens.entries.forEach { (group, amount) ->
                TokenChip(group = group, amount = amount, assets = assets, color = color)
            }
        }
    }
    if (vault.snapshot.authorities.isNotEmpty())
    {
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            vault.snapshot.authorities.forEach { authority ->
                BatonChip(authority = authority, assets = assets, color = color)
            }
        }
    }
}

/**
 * What the time lock "Vault claimed" success card needs to render after the user
 * confirms a time lock vault claim — captured at confirm time because the underlying
 * time lock vault disappears from the list once swept.
 */
internal data class VaultClaimedView(
    val vaultName: String,
    val payoutAddress: PayAddress,
    val result: TimeLockContractRepository.ClaimResult,
    /** The vault as it stood when the sweep was signed — the receipt outlives the vault's row in the list. */
    val snapshot: TimeLockContractRepository.VaultSnapshot? = null,
    val claimedAt: Instant = Clock.System.now(),
)

/**
 * Snapshot of the create-time mock vault inputs at the moment the user pressed
 * "Review". Carries everything the confirm card needs to render *and* the
 * resolved values the caller needs to actually fund the time lock vault.
 */
internal data class VaultReviewDraft(
    val mode: LockMode,
    val blockText: String,
    val approxDate: String?,
    val amountSat: Long,
    val feeSat: Long,
    val totalSat: Long,
    val unlockBlock: Long,
)

@Composable
private fun CreateVaultCard(
    currentBlockHeight: Long?,
    unitName: String,
    draft: VaultReviewDraft? = null,
    onCancel: () -> Unit,
    onReview: (VaultReviewDraft) -> Unit,
)
{
    // Defaults the amount to the minimum funding (20 NEXA), derived from the repo floor so it can't drift from MIN_VAULT_FUNDING_SAT.
    var amount by remember {
        mutableStateOf(
            draft?.let { SatToNexa(it.amountSat).toStringExpanded() }
                ?: (TimeLockContractRepository.MIN_VAULT_FUNDING_SAT / 100).toString(),
        )
    }
    var mode by remember { mutableStateOf(LockMode.BlockHeight) }
    var blockText by remember { mutableStateOf(draft?.blockText ?: "") }
    val datePickerState = rememberDatePickerState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // When a user picks a date, convert it to block height, drop it into the block-height field,
    // and close the picker by switching back to the BlockHeight mode. Clearing `selectedDateMillis`
    // re-arms the picker so returning to FutureDate later opens it fresh.
    LaunchedEffect(datePickerState.selectedDateMillis)
    {
        val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        val block = millisToUnlockBlock(millis, currentBlockHeight) ?: return@LaunchedEffect
        blockText = block.toString()
        mode = LockMode.BlockHeight
        datePickerState.selectedDateMillis = null
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    val amountSat =
        if (amount.isBlank()) null  // the untouched field is the initial state, not bad input
        else try { NexaToSat(NexaDecimal(amount.trim())) }
             catch (e: ArithmeticException) { LogIt.error("time lock vault amount '$amount' is not a number: $e"); null }
             catch (e: IllegalArgumentException) { LogIt.error("time lock vault amount '$amount' is out of range: $e"); null }
    val unlockBlock = blockText.trim().toLongOrNull()
    val tip = currentBlockHeight
    val approxDate = unlockBlock?.let { estimateUnlockDate(it, tip) }

    // Estimate from the wallet's own fee rate; txCompleter supplies the exact fee at sign time.
    val feeSat = remember { wallyApp?.focusedAccount?.value?.timeLockVaults?.estimatedFundingFeeSat() ?: 0L }
    val totalSat = (amountSat ?: 0L) + feeSat

    val meetsMinFunding = amountSat != null &&
        amountSat >= TimeLockContractRepository.MIN_VAULT_FUNDING_SAT
    // When a value is above the nLockTime threshold, a full node reads the value as a Unix epoch time, not a height — the library rejects it, so refuse it here too.
    val exceedsMaxHeight = unlockBlock != null &&
        unlockBlock > TimeLockContractRepository.MAX_UNLOCK_BLOCK_HEIGHT
    val valid = meetsMinFunding && unlockBlock != null && unlockBlock > 0L && !exceedsMaxHeight

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(timeLockVaultSheen, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PillLabel(i18n(S.tlvCreateTitle))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = i18n(S.SendCancel),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = i18n(S.tlvCreateExplainer),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        Spacer(Modifier.height(12.dp))
        PillLabel(i18n(S.amountPlain))
        Spacer(Modifier.height(6.dp))
        InputBox {
            ResImageView(focusedChainIconResPath(), Modifier.size(22.dp), "Blockchain icon")
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    color = Color(0xFF1A1A1A),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.weight(1f).testTag("VaultAmountInput"),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = unitName,
                color = Color(0xFF707070),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            IosKeyboardDoneButton()
        }
        Spacer(Modifier.height(6.dp))
        // Minimum is enforced both here and inside the repository's addVault function. We turn the hint red once a user
        // has typed something below the floor so they see why the Review button stayed disabled.
        val amountEntered = amount.isNotBlank()
        Text(
            text = i18n(S.tlvMinimumAmount) % mapOf("amt" to nexaAmountText(TimeLockContractRepository.MIN_VAULT_FUNDING_SAT), "unit" to unitName),
            color = if (amountEntered && !meetsMinFunding) pillWarn else Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(12.dp))
        PillLabel(i18n(S.tlvLockUntil))
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                active = mode == LockMode.BlockHeight,
                icon = Icons.Outlined.Layers,
                label = i18n(S.tlvBlockHeight),
                onClick = { mode = LockMode.BlockHeight },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            SegmentedButton(
                active = mode == LockMode.FutureDate,
                icon = Icons.Outlined.Event,
                label = i18n(S.tlvFutureDate),
                onClick = { mode = LockMode.FutureDate },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        InputBox {
            Icon(
                imageVector = Icons.Outlined.Layers,
                contentDescription = null,
                tint = wallyPurple,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = blockText,
                onValueChange = { blockText = it.filter { c -> c.isDigit() } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = Color(0xFF1A1A1A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.weight(1f).testTag("VaultBlockInput"),
            )
            if (approxDate != null)
            {
                Text(
                    text = i18n(S.tlvApproxDate) % mapOf("date" to approxDate),
                    color = wallyPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IosKeyboardDoneButton()
        }
        if (exceedsMaxHeight)
        {
            Spacer(Modifier.height(4.dp))
            Text(
                text = i18n(S.tlvMaxBlockHeight) % mapOf("num" to TimeLockContractRepository.MAX_UNLOCK_BLOCK_HEIGHT.toString()),
                color = pillWarn,
                fontSize = 11.sp,
            )
        }

        // The material3's DatePicker has a fixed minimum width that overflows this card on some phone screens —
        // host it in a DatePickerDialog instead so the platform handles sizing/centering.
        if (mode == LockMode.FutureDate)
        {
            DatePickerDialog(
                onDismissRequest = { mode = LockMode.BlockHeight },
                confirmButton = {
                    TextButton(onClick = { mode = LockMode.BlockHeight }) { Text(i18n(S.tlvOk)) }
                },
                dismissButton = {
                    TextButton(onClick = { mode = LockMode.BlockHeight }) { Text(i18n(S.SendCancel)) }
                },
            ) {
                DatePicker(
                    state = datePickerState,
                    showModeToggle = false,
                    title = null,
                    headline = null,
                    colors = DatePickerDefaults.colors(
                        containerColor = Color.White,
                        selectedDayContainerColor = wallyPurple,
                        todayDateBorderColor = wallyPurple,
                        todayContentColor = wallyPurple,
                    ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(i18n(S.tlvFeeSatB), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            NexaAmount(
                text = SatToString(feeSat),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                unit = unitName,
                logoSize = 12.dp,
                unitFontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PillLabel(i18n(S.tlvTotal))
            NexaAmount(
                text = nexaAmountText(totalSat),
                fontSize = 23.sp,
                color = Color.White,
                unit = unitName,
                logoSize = 18.dp,
                unitFontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            PillGhostButton(
                label = i18n(S.SendCancel),
                icon = Icons.Outlined.Close,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            Spacer(Modifier.width(8.dp))
            PillWhiteButton(
                label = i18n(S.tlvReview),
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                iconTrailing = true,
                enabled = valid,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (unlockBlock != null && amountSat != null && valid)
                    {
                        onReview(
                            VaultReviewDraft(
                                mode = mode,
                                blockText = blockText.trim(),
                                approxDate = approxDate,
                                amountSat = amountSat,
                                feeSat = feeSat,
                                totalSat = totalSat,
                                unlockBlock = unlockBlock,
                            ),
                        )
                    }
                },
            )
        }
    }
}

internal enum class LockMode { BlockHeight, FutureDate }

@Composable
private fun ConfirmVaultCard(
    draft: VaultReviewDraft,
    unitName: String,
    currentBlockHeight: Long?,
    busy: Boolean = false,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
)
{
    // The repository names the vault when it funds it, so before signing the only honest name is the generic one.
    val vaultName = i18n(S.tlvNewTimeLockVault)
    val unlockDate = draft.approxDate ?: dateForHeight(draft.unlockBlock, currentBlockHeight)
    val lockedUntilText =
        if (unlockDate != null) i18n(S.tlvBlockNumApproxDate) % mapOf("num" to groupDigits(draft.unlockBlock), "date" to unlockDate)
        else i18n(S.tlvBlockNum) % mapOf("num" to groupDigits(draft.unlockBlock))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(timeLockVaultSheen, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = vaultName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = i18n(S.tlvLockExplainer) % mapOf(
                "amt" to nexaAmountText(draft.amountSat),
                "unit" to unitName,
                "name" to vaultName,
                "date" to (unlockDate ?: (i18n(S.tlvBlockNum) % mapOf("num" to groupDigits(draft.unlockBlock)))),
            ),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        PillWindow {
            WindowRow(label = i18n(S.tlvPayTo).uppercase()) { WindowValue(vaultName) }
            ThinDivider()
            WindowRow(label = i18n(S.tlvLockedUntil).uppercase()) { WindowValue(lockedUntilText) }
            ThinDivider()
            WindowRow(label = i18n(S.amountPlain).uppercase()) {
                NexaAmount(
                    text = nexaAmountText(draft.amountSat),
                    fontSize = 13.sp,
                    color = Color(0xFF1A1A1A),
                    unit = unitName,
                    logoSize = 14.dp,
                    unitFontSize = 10.sp,
                    unitColor = Color(0xFF707070),
                )
            }
            ThinDivider()
            WindowRow(label = i18n(S.fee).uppercase()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexaAmount(
                        text = SatToString(draft.feeSat),
                        fontSize = 13.sp,
                        color = Color(0xFF1A1A1A),
                        unit = unitName,
                        logoSize = 14.dp,
                        unitFontSize = 10.sp,
                        unitColor = Color(0xFF707070),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(wallyPurpleLight)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = i18n(S.tlvTotal).uppercase(),
                    color = wallyPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                NexaAmount(
                    text = nexaAmountText(draft.totalSat),
                    fontSize = 17.sp,
                    color = wallyPurple,
                    unit = unitName,
                    logoSize = 16.dp,
                    unitFontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            PillGhostButton(
                label = i18n(S.Back),
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = onBack,
            )
            Spacer(Modifier.width(8.dp))
            PillWhiteButton(
                label = if (busy) i18n(S.tlvWorking) else i18n(S.tlvLock),
                icon = Icons.Outlined.Lock,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
            )
        }
    }
}


@Composable
private fun ClaimVaultCard(
    vault: TimeLockViewModel.TimeLockVaultUiData,
    unitName: String,
    currentBlockHeight: Long?,
    assets: Map<GroupId, AssetInfo> = emptyMap(),
    payoutAddress: PayAddress?,
    busy: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: (payoutAddress: PayAddress) -> Unit,
)
{
    val claimFeeSat = vault.snapshot.claimFeeSat
    val somethingToClaim = hasSomethingToClaim(vault)
    // The claim pays its fee only from the coin the vault itself holds, down to the dust floor.
    val claimable = somethingToClaim && !needsFeeCoin(vault)
    val accountName = wallyApp?.preferredVisibleAccountOrNull()?.name

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(timeLockVaultSheen, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        // The assets are still in the contract until the sweep confirms, so the pill still leads with what it holds.
        VaultPillHeader(vault = vault, unitName = unitName, assets = assets)
        Spacer(Modifier.height(10.dp))
        Text(
            text = i18n(S.tlvClaimExplainerSingle),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        // Wrap the readable body in a SelectionContainer so the end user can long-press to select and copy any of the
        // displayed values (payout address, fee). The Cancel / Claim buttons stay outside so their tap targets aren't shadowed by drag.
        SelectionContainer {
            PillWindow {
                val dates = lockDatesValue(vault, currentBlockHeight)
                if (dates.isNotEmpty())
                {
                    WindowStack(label = i18n(S.tlvLockedUnlockedFields).uppercase()) {
                        WindowStackText(dates)
                    }
                    ThinDivider()
                }
                WindowStack(label = i18n(S.tlvClaimTo).uppercase(), subLabel = i18n(S.tlvYourWallet)) {
                    if (accountName != null)
                    {
                        Text(
                            text = accountName,
                            color = Color(0xFF1A1A1A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    WindowStackValue(payoutAddress?.toString() ?: "—", color = Color(0xFF707070))
                }
                ThinDivider()
                WindowRow(label = i18n(S.fee).uppercase()) {
                    NexaAmount(
                        text = SatToString(claimFeeSat),
                        fontSize = 13.sp,
                        color = Color(0xFF1A1A1A),
                        unit = unitName,
                        logoSize = 14.dp,
                        unitFontSize = 10.sp,
                        unitColor = Color(0xFF707070),
                    )
                }
                if (!claimable)
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x14B00020))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (somethingToClaim) i18n(S.tlvUnlockedNeedsFeeCoin) else i18n(S.tlvNothingToClaim),
                            color = Color(0xFFB00020),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            PillGhostButton(
                label = i18n(S.SendCancel),
                icon = Icons.Outlined.Close,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            Spacer(Modifier.width(8.dp))
            PillWhiteButton(
                label = if (busy) i18n(S.tlvWorking) else i18n(S.tlvClaim),
                icon = Icons.Outlined.LockOpen,
                enabled = !busy && payoutAddress != null && claimable,
                fill = colorCredit,
                fg = Color.White,
                modifier = Modifier.weight(1f),
                onClick = {
                    val addr = payoutAddress ?: return@PillWhiteButton
                    onConfirm(addr)
                },
            )
        }
    }
}

@Composable
private fun VaultClaimedCard(
    view: VaultClaimedView,
    unitName: String,
    currentBlockHeight: Long?,
    assets: Map<GroupId, AssetInfo> = emptyMap(),
    onDone: () -> Unit,
)
{
    val tokens = view.snapshot?.tokenBalances?.filter { it.value > 0L } ?: emptyMap()
    val authorities = view.snapshot?.authorities ?: emptyList()
    val accountName = wallyApp?.preferredVisibleAccountOrNull()?.name
    // The row's label names the three fields, so the value carries the dates alone.
    val dates = listOfNotNull(
        dateForHeight(view.snapshot?.earliestDepositHeight, currentBlockHeight),
        dateForHeight(view.snapshot?.unlockBlockHeight, currentBlockHeight),
        formatInstantDate(view.claimedAt),
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(timeLockVaultClaimedWash, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = view.vaultName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = i18n(S.tlvClaimed),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = i18n(S.tlvOnItsWayBack) % mapOf("amt" to nexaAmountText(view.result.payoutSat), "unit" to unitName),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        // Wrap the readable body in a SelectionContainer so the end user can long-press to select and copy any of the tx outcome details
        // (TX ID, address, amounts). The Done button stays outside so its tap target isn't shadowed by selection drag.
        SelectionContainer {
            PillWindow {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1470A01E))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = unitName.uppercase(),
                            color = Color(0xFF3A6614),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        NexaAmount(
                            text = nexaAmountText(view.result.payoutSat),
                            fontSize = 15.sp,
                            color = Color(0xFF3A6614),
                            logoSize = 14.dp,
                        )
                    }
                    if (tokens.isNotEmpty())
                    {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = i18n(S.tlvTokens).uppercase(),
                                color = Color(0xFF3A6614),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                for ((group, amount) in tokens)
                                {
                                    TokenChip(
                                        group = group,
                                        amount = amount,
                                        assets = assets,
                                        color = Color(0xFF3A6614),
                                        fontSize = 13.sp,
                                        iconSize = 16.dp,
                                    )
                                }
                            }
                        }
                    }
                    if (authorities.isNotEmpty())
                    {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = i18n(S.tlvBatons).uppercase(),
                                color = Color(0xFF3A6614),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                for (authority in authorities)
                                {
                                    BatonChip(
                                        authority = authority,
                                        assets = assets,
                                        color = Color(0xFF3A6614),
                                        fontSize = 13.sp,
                                        iconSize = 16.dp,
                                    )
                                }
                            }
                        }
                    }
                }
                WindowStack(label = i18n(S.tlvLockedUnlockedClaimedFields).uppercase()) {
                    WindowStackText(dates)
                }
                ThinDivider()
                WindowStack(label = i18n(S.tlvClaimedTo).uppercase(), subLabel = i18n(S.tlvYourWallet)) {
                    if (accountName != null)
                    {
                        Text(
                            text = accountName,
                            color = Color(0xFF1A1A1A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    WindowStackValue(view.payoutAddress.toString(), color = Color(0xFF707070))
                }
                ThinDivider()
                WindowStack(label = i18n(S.tlvTxId)) {
                    WindowStackValue(view.result.txIdem.toString())
                }
                ThinDivider()
                WindowRow(label = i18n(S.fee).uppercase()) {
                    NexaAmount(
                        text = SatToString(view.result.feeSat),
                        fontSize = 13.sp,
                        color = Color(0xFF1A1A1A),
                        unit = unitName,
                        logoSize = 14.dp,
                        unitFontSize = 10.sp,
                        unitColor = Color(0xFF707070),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillWhiteButton(
            label = i18n(S.tlvDone),
            icon = Icons.Outlined.Check,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDone,
        )
    }
}

@Composable
private fun VaultCreatedCard(
    view: VaultCreatedView,
    unitName: String,
    currentBlockHeight: Long?,
    onDone: () -> Unit,
)
{
    val unlockDate = dateForHeight(view.unlockBlockHeight, currentBlockHeight)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(timeLockVaultSheen, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = view.created.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(9.dp))
        NexaAmount(
            text = nexaAmountText(view.amountSat),
            fontSize = 25.sp,
            color = Color.White,
            unit = unitName,
            logoSize = 18.dp,
            unitFontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = i18n(S.tlvFundsOnTheirWay),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            PillWindow {
                WindowStack(label = i18n(S.Address).uppercase()) {
                    WindowStackValue(view.created.address.toString())
                }
                ThinDivider()
                WindowRow(label = i18n(S.tlvLockedUntil).uppercase()) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (unlockDate != null)
                        {
                            Text(
                                text = i18n(S.tlvApproxDate) % mapOf("date" to unlockDate),
                                color = timeLockVaultDeep,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                            )
                        }
                        Text(
                            text = i18n(S.tlvBlockNum) % mapOf("num" to groupDigits(view.unlockBlockHeight)),
                            color = timeLockVaultDeep,
                            fontSize = if (unlockDate != null) 14.sp else 24.sp,
                            fontWeight = if (unlockDate != null) FontWeight.SemiBold else FontWeight.Bold,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                ThinDivider()
                WindowStack(label = i18n(S.tlvTxId)) {
                    WindowStackValue(view.created.fundingTxIdem.toString())
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillWhiteButton(
            label = i18n(S.tlvDone),
            icon = Icons.Outlined.Check,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDone,
        )
    }
}

/** The white inset a brick pill takes typed input in. */
@Composable
private fun InputBox(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun IosKeyboardDoneButton()
{
    if (platform().target == KotlinTarget.iOS)
    {
        val keyboardController = LocalSoftwareKeyboardController.current
        Spacer(Modifier.width(6.dp))
        Text(
            text = i18n(S.tlvDone),
            color = wallyPurple,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(wallyPurpleLight, RoundedCornerShape(8.dp))
                .clickable { keyboardController?.hide() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** One half of the lock-mode selector on the create pill: a ghost button that fills white when selected. */
@Composable
private fun SegmentedButton(
    active: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    val fg = if (active) timeLockVaultDeep else Color.White
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) Color.White else Color.Transparent)
            .then(if (active) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Resolve the date picker's `selectedDateMillis` (epoch ms encoding the chosen calendar day as UTC midnight)
 * into a target block height, anchored at the wallet's current [tip] and projected via [TimeLockViewModel.BLOCK_SECONDS].
 *
 * Returns null if either input is null, the date is in the past, or the wallet has no tip yet.
 */
private fun millisToUnlockBlock(millis: Long?, tip: Long?): Long?
{
    if (millis == null || tip == null) return null
    // Picker millis encode the picked day as UTC midnight; re-anchor at local noon to tolerate block drift.
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    val target = date.atTime(12, 0).toInstant(systemTimeZone)
    val now = Clock.System.now()
    val seconds = (target - now).inWholeSeconds
    if (seconds <= 0L) return null
    return tip + seconds / TimeLockViewModel.BLOCK_SECONDS
}

/** Render an unlock-height Long and tip Long as an approximate "MMM dd, yyyy HH:mm" string; null if no tip. */
private fun estimateUnlockDate(unlockBlock: Long, tip: Long?): String?
{
    if (tip == null) return null
    val blocksAhead = unlockBlock - tip
    if (blocksAhead < 0L) return null
    val instant = Clock.System.now() + (blocksAhead * TimeLockViewModel.BLOCK_SECONDS).seconds
    return formatInstantDateTime(instant)
}

/**
 * Shown while the one-time startup walk is still rediscovering time lock vaults and the list is empty
 * — distinguishes "loading" from "you have none" (see [VaultsEmpty]). A Material indeterminate spinner
 * plus a UI label.
 */
@Composable
private fun VaultsLoading()
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = wallyPurple,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = i18n(S.tlvLoadingVaults),
            color = Color(0xFF707070),
            fontSize = 13.sp,
        )
    }
}

/**
 * Shown once the startup walk has finished and there genuinely are no vaults — points the user at
 * the Create button in the header card above. Centered gray text mirrors the empty state in
 * [VaultTxListScreen].
 */
@Composable
private fun VaultsEmpty()
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = i18n(S.tlvExplainer),
            color = Color(0xFF4A4A4A),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = i18n(S.tlvNoVaultsYet),
            color = Color(0xFF707070),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One vault in the master list: its own brick pill. The top row states the vault (state icon, name)
 * and where it stands (unlocked / estimated unlock date / claimed); under it comes what the vault
 * holds. A claimed vault is washed out and keeps only the top row — it holds nothing any more.
 * It reveals and routes; tapping anywhere opens the vault's detail level.
 */
@Composable
private fun VaultEntryCard(
    vault: TimeLockViewModel.TimeLockVaultUiData,
    unitName: String,
    assets: Map<GroupId, AssetInfo> = emptyMap(),
    onOpen: () -> Unit,
)
{
    val claimed = vault.snapshot.hasClaimTx
    val ready = vault.snapshot.isUnlockable && !claimed
    val unlockDate = vault.estimatedUnlock?.let { formatInstantDateTime(it) }
    val statusText = when
    {
        ready -> readyStatusText(vault)
        claimed -> i18n(S.tlvClaimed)
        unlockDate != null -> i18n(S.tlvApproxDate) % mapOf("date" to unlockDate)
        else -> unlockBlockText(vault) ?: i18n(S.tlvLocked)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (claimed) timeLockVaultClaimedWash else timeLockVaultSheen)
            .clickable(onClick = onOpen)
            .testTag("VaultEntry-${vault.snapshot.name}")
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            VaultStateIcon(vault, size = 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = vault.snapshot.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                color = Color.White.copy(alpha = if (ready) 1f else 0.85f),
                fontSize = 12.sp,
                fontWeight = if (ready) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp),
            )
        }
        // A claimed vault holds nothing: the balance, token and baton lines would all read zero.
        if (!claimed)
        {
            if (!vault.snapshot.isBalanceLoaded)
            {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = i18n(S.tlvBalanceLoading),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            }
            else if (vault.snapshot.nativeBalanceSat > 0L || vault.snapshot.tokenBalances.none { it.value > 0L })
            {
                Spacer(Modifier.height(8.dp))
                NexaAmount(
                    text = nexaAmountText(vault.snapshot.nativeBalanceSat),
                    fontSize = 21.sp,
                    color = Color.White,
                    unit = unitName,
                    logoSize = 17.dp,
                )
            }
            VaultAssetLines(vault = vault, assets = assets)
        }
    }
}

/**
 * The detail level's brick pill: it carries the whole vault. The header block states what the vault is
 * and holds, then the lock dates, the progress bar while locked, the Claim action once there is
 * something to sweep, and a white window with the deposit address (with its QR reveal), the history
 * link and the footer notes.
 */
@Composable
private fun VaultContractPill(
    vault: TimeLockViewModel.TimeLockVaultUiData,
    unitName: String,
    currentBlockHeight: Long?,
    assets: Map<GroupId, AssetInfo>,
    qrShown: Boolean,
    onToggleQr: () -> Unit,
    onCopy: () -> Unit,
    onShowTxs: () -> Unit,
    onClaim: () -> Unit,
)
{
    val claimed = vault.snapshot.hasClaimTx
    val ready = vault.snapshot.isUnlockable && !claimed
    val locked = !vault.snapshot.isUnlockable && !claimed
    val somethingToClaim = hasSomethingToClaim(vault)
    val lockedFraction = (vault.percentLocked ?: 0f).coerceIn(0f, 1f)
    val timeLeft = vault.daysUntilUnlock?.let { timeLeftText(it) }
    val funded = somethingToClaim || claimed
    // Whatever stops a matured vault from being swept, and whatever a finished vault still has to say.
    val gate = when
    {
        // A locked vault cannot be claimed yet, so name the exact block it is waiting for.
        locked -> listOfNotNull(
            i18n(S.tlvPercentElapsed) % mapOf("pct" to pct1(lockedFraction)),
            timeLeft,
            unlockBlockText(vault),
        ).joinToString(" · ")
        ready -> readyStatusText(vault)
        claimed && somethingToClaim -> i18n(S.tlvClaimedFundsArrivedSince)
        claimed -> i18n(S.tlvVaultEmptied)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(if (claimed) timeLockVaultClaimedWash else timeLockVaultSheen, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        VaultPillHeader(vault = vault, unitName = unitName, assets = assets)
        val dates = lockDatesText(vault, currentBlockHeight)
        if (dates.isNotEmpty())
        {
            Spacer(Modifier.height(9.dp))
            Text(
                text = dates,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
            )
        }
        if (locked)
        {
            Spacer(Modifier.height(9.dp))
            VaultProgressBar(
                fraction = lockedFraction,
                track = Color.White.copy(alpha = 0.24f),
                fill = Color.White,
            )
        }
        if (gate != null)
        {
            Spacer(Modifier.height(7.dp))
            Text(
                text = gate,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        PillWindow {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (vault.snapshot.address != null) Modifier.clickable(onClick = onToggleQr) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = i18n(S.tlvDepositAddress).uppercase(),
                        color = Color(0xFF707070),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                    if (!claimed && vault.snapshot.address != null)
                    {
                        Icon(
                            imageVector = if (qrShown) Icons.Outlined.Close else Icons.Outlined.QrCode,
                            contentDescription = if (qrShown) i18n(S.tlvCloseQr) else i18n(S.tlvQr),
                            tint = wallyPurple,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // The address is always the full string, wrapped over as many lines as it needs — never
                // abbreviated, since address ellipses have been exploited in other wallets.
                SelectionContainer {
                    Text(
                        text = vault.snapshot.address?.toString() ?: i18n(S.tlvNoAddress),
                        color = Color(0xFF1A1A1A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp,
                    )
                }
            }
            // QR reveal. A claimed time lock vault keeps its address readable — it is the permanent record of the contract
            // — but drops the QR and the deposit warnings, since depositing to a finished vault is a mistake, not a flow.
            if (qrShown && vault.snapshot.address != null)
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!claimed)
                    {
                        QrCode(
                            vault.snapshot.address.toString(),
                            Modifier
                                .background(Color.White)
                                .size(220.dp)
                                .border(1.dp, WallyBorder),
                        )
                    }
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, wallyPurpleLight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = wallyPurple),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = i18n(S.CopyAddress),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(i18n(S.CopyAddress), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!claimed)
                    {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = i18n(S.tlvDepositWarning),
                            color = wallyPlaceholder,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = i18n(S.tlvTokenFeeWarning) % mapOf("unit" to unitName),
                            color = wallyPlaceholder,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            ThinDivider()
            WindowRow(label = i18n(S.tlvHistory).uppercase(), onTap = onShowTxs) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WindowValue(i18n(S.tlvView))
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = wallyPurple,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            val notes = buildList {
                if (locked) add(i18n(S.tlvNoEarlyOpen))
                if (funded) add(i18n(S.tlvPublicOnChain))
            }
            if (notes.isNotEmpty())
            {
                ThinDivider()
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    notes.forEachIndexed { i, line ->
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        Text(
                            text = line,
                            color = wallyPlaceholder,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
        // A sweep pays its fee only from the coin the vault itself holds, so a coinless vault cannot claim.
        if (!claimed || somethingToClaim)
        {
            Spacer(Modifier.height(12.dp))
            PillWhiteButton(
                label = if (claimed) i18n(S.tlvReclaim) else i18n(S.tlvClaim),
                icon = Icons.Outlined.LockOpen,
                enabled = vault.snapshot.isUnlockable && somethingToClaim && !needsFeeCoin(vault),
                modifier = Modifier.fillMaxWidth(),
                onClick = onClaim,
            )
        }
    }
}

/** "1,204,988" — block heights are grouped */
private fun groupDigits(v: Long): String =
    v.toString().reversed().chunked(3).joinToString(",").reversed()

/** "12,502.60" — satoshis as grouped nexa; a whole amount carries no decimals at all. */
private fun nexaAmountText(sat: Long): String =
    groupDigits(sat / 100) + (if (sat % 100 == 0L) "" else "." + (sat % 100).toString().padStart(2, '0'))

/**
 * Wall-clock projection of any block [height] from the wallet [tip] via [TimeLockViewModel.BLOCK_SECONDS] —
 * heights already behind the tip project backwards. `null` when either is unknown.
 */
private fun dateForHeight(height: Long?, tip: Long?): String?
{
    if (height == null || tip == null) return null
    return formatInstantDate(Clock.System.now() + ((height - tip) * TimeLockViewModel.BLOCK_SECONDS).seconds)
}

/** The date the vault was funded, as far as the wallet can project it. */
private fun lockedDateOf(vault: TimeLockViewModel.TimeLockVaultUiData, tip: Long?): String? =
    dateForHeight(vault.snapshot.earliestDepositHeight, tip)

/** [dateForHeight] carrying the clock time, for the unlock moment a locked vault is waiting on. */
private fun dateTimeForHeight(height: Long?, tip: Long?): String?
{
    if (height == null || tip == null) return null
    return formatInstantDateTime(Clock.System.now() + ((height - tip) * TimeLockViewModel.BLOCK_SECONDS).seconds)
}

/** The moment the vault opens: the view model's estimate when it has one, else the projection from the tip. */
private fun unlockDateOf(vault: TimeLockViewModel.TimeLockVaultUiData, tip: Long?): String? =
    vault.estimatedUnlock?.let { formatInstantDateTime(it) } ?: dateTimeForHeight(vault.snapshot.unlockBlockHeight, tip)

/**
 * "Unlocks block 1,134,900" — null when the vault commits a timestamp instead, because past the
 * nLockTime split the same field holds epoch seconds and is not a block number.
 */
private fun unlockBlockText(vault: TimeLockViewModel.TimeLockVaultUiData): String?
{
    val height = vault.snapshot.unlockBlockHeight ?: return null
    if (height > TimeLockContractRepository.MAX_UNLOCK_BLOCK_HEIGHT) return null
    return i18n(S.tlvUnlocksBlock) % mapOf("num" to groupDigits(height))
}

/** "Locked May 2, 2026 · Unlocked Nov 2, 2026 09:14" — whichever of the two the wallet can project. */
private fun lockDatesText(vault: TimeLockViewModel.TimeLockVaultUiData, tip: Long?): String
{
    val locked = lockedDateOf(vault, tip)
    val unlocked = unlockDateOf(vault, tip)
    return when
    {
        locked != null && unlocked != null ->
            i18n(S.tlvLockedUnlockedOn) % mapOf("locked" to locked, "unlocked" to unlocked)
        locked != null -> i18n(S.tlvLockedOn) % mapOf("date" to locked)
        unlocked != null -> i18n(S.tlvUnlockedOn) % mapOf("date" to unlocked)
        else -> ""
    }
}

/** The same two dates with no field names, for a row whose label already names them. */
private fun lockDatesValue(vault: TimeLockViewModel.TimeLockVaultUiData, tip: Long?): String =
    listOfNotNull(lockedDateOf(vault, tip), unlockDateOf(vault, tip)).joinToString(" · ")

/**
 * Holds assets but no native coin at all. A sweep pays its fee only from the coin the vault itself holds, so without a
 * deposit the tokens and batons cannot come out. A vault that merely holds *little* coin is fine: the fee comes out of
 * the payout, down to the dust floor, and the library raises a typed error in the rare case it cannot.
 */
private fun needsFeeCoin(vault: TimeLockViewModel.TimeLockVaultUiData): Boolean =
    vault.snapshot.nativeBalanceSat == 0L &&
        (vault.snapshot.tokenBalances.values.any { it > 0L } || vault.snapshot.authorities.isNotEmpty())

private fun hasSomethingToClaim(vault: TimeLockViewModel.TimeLockVaultUiData): Boolean =
    vault.snapshot.nativeBalanceSat > 0L ||
        vault.snapshot.tokenBalances.values.any { it > 0L } ||
        vault.snapshot.authorities.isNotEmpty()

/** Status line for a matured, unclaimed vault. */
private fun readyStatusText(vault: TimeLockViewModel.TimeLockVaultUiData): String = when
{
    !hasSomethingToClaim(vault) -> i18n(S.tlvUnlockedWaitingDeposit)
    needsFeeCoin(vault) -> i18n(S.tlvUnlockedNeedsFeeCoin)
    else -> i18n(S.tlvUnlockedReadyToClaim)
}

/**
 * Countdown label at the finest sensible unit: days normally, hours once within 24 hours, minutes once
 * within the hour — so a short lock visibly counts down block by block. Ceil, so the label never promises
 * less time than actually remains; singular at exactly one unit.
 */
internal fun timeLeftText(daysUntilUnlock: Double): String
{
    val minutes = ceil(daysUntilUnlock * MINUTES_PER_DAY).toLong().coerceAtLeast(1L)
    return when
    {
        minutes > MINUTES_PER_DAY -> unitsLeft(ceil(daysUntilUnlock).toLong(), S.tlvOneDayLeft, S.tlvDaysLeft)
        minutes >= 60L -> unitsLeft(ceil(daysUntilUnlock * 24.0).toLong(), S.tlvOneHourLeft, S.tlvHoursLeft)
        else -> unitsLeft(minutes, S.tlvOneMinuteLeft, S.tlvMinutesLeft)
    }
}

private const val MINUTES_PER_DAY: Long = 24L * 60L

private fun unitsLeft(n: Long, singular: Int, plural: Int): String =
    if (n == 1L) i18n(singular) else i18n(plural) % mapOf("num" to n.toString())

/** One-decimal percent string for a `[0,1]` fraction, e.g. "10.4" — commonMain has no printf. */
internal fun pct1(fraction: Float): String
{
    val tenths = (fraction.toDouble() * 1000.0 + 0.5).toLong().coerceIn(0L, 1000L)
    return "${tenths / 10}.${tenths % 10}"
}

@Composable
private fun ThinDivider()
{
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(wallyPurpleLight))
}

/**
 * The unlock progress bar. [fraction] is the raw `[0,1]` locked-window fraction — deliberately not
 * quantized to whole percent, so the fill moves at the finest granularity the layout can render
 * (a single block advances it on any realistically-sized lock window).
 */
@Composable
private fun VaultProgressBar(
    fraction: Float,
    track: Color = wallyPurpleLight,
    fill: Color = wallyPurple,
)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(fill),
        )
    }
}

/**
 * Time-lock-vault surface colors. [timeLockVaultSheen] is the design's signature brick sheen that every
 * pill is painted with; [timeLockVaultClaimedWash] is its washed-out twin for a vault that is done.
 * [timeLockVaultTint] is the accent (token placeholder coins, the contract list glyph).
 */
internal val timeLockVaultDeep = Color(0xFF96453A)
internal val timeLockVaultTint = Color(0xFFC97A62)
private val vaultBatonTint = Color(0xFFE8590C)
private val timeLockVaultSheen = Brush.horizontalGradient(listOf(timeLockVaultDeep, Color(0xFFAF6A58)))
private val timeLockVaultClaimedWash = Brush.horizontalGradient(listOf(Color(0xFFBA9F97), Color(0xFFC9B4AD)))

/** Warning text that has to stay readable on the brick sheen. */
private val pillWarn = Color(0xFFFFD5CE)

/** Render a [kotlin.time.Instant] in the system timezone as "MMM dd, yyyy"*/
/**
 * The same instant with the projected clock time — "Aug 28, 2026 00:27". Used for the moment a vault
 * unlocks, where the hour matters once the wait is down to hours.
 */
private fun formatInstantDateTime(instant: Instant): String
{
    val ldt = instant.toLocalDateTime(systemTimeZone)
    val hh = ldt.hour.toString().padStart(2, '0')
    val mm = ldt.minute.toString().padStart(2, '0')
    return "${formatInstantDate(instant)} $hh:$mm"
}

private fun formatInstantDate(instant: Instant): String
{
    val ldt = instant.toLocalDateTime(systemTimeZone)
    val month = abbreviatedMonthNames()[ldt.month.ordinal]
    return "$month ${ldt.day}, ${ldt.year}"
}

private fun previewSnapshot(
    name: String,
    address: PayAddress,
    unlockBlockHeight: Long,
    isUnlockable: Boolean,
    nativeBalanceSat: Long,
) = TimeLockContractRepository.VaultSnapshot(
    name = name,
    address = address,
    unlockBlockHeight = unlockBlockHeight,
    isUnlockable = isUnlockable,
    blocksUntilUnlock = null,
    nativeBalanceSat = nativeBalanceSat,
    claimFeeSat = 0L,
    tokenBalances = emptyMap(),
    authorities = emptyList(),
    earliestDepositHeight = null,
    isBalanceLoaded = true,
    hasClaimTx = false,
)

// https://youtrack.jetbrains.com/projects/KMT/issues/KMT-2076/Preview-in-Compose-1.10.0-fails
@Preview
@Composable
fun TimeLockScreenPreview()
{
    val mockPill = AccountPillViewModelFake(
        account = MutableStateFlow(null),
        balance = BalanceViewModelFake(),
        sync = SyncViewModelFake(),
    )
    val mockVaults = listOf(
        TimeLockViewModel.TimeLockVaultUiData(
            snapshot = previewSnapshot(
                name = "vault-1",
                address = PayAddress("nexa:nqcqq9zxrtf9pqwtqyvaqdpctlc4fjxn444a6as55l0u22cnjqxkr3qyvezf363utc0hvsvsqdsryyz3aky3plse"),
                unlockBlockHeight = 1_061_472L,
                isUnlockable = false,
                nativeBalanceSat = 2_000L,
            ),
            estimatedUnlock = Clock.System.now() + (73L * 86_400L).seconds,
            daysUntilUnlock = 73.0,
            percentLocked = 0.27f,
        ),
        TimeLockViewModel.TimeLockVaultUiData(
            snapshot = previewSnapshot(
                name = "vault-2",
                address = PayAddress("nexa:nqcqq8jrz5w3lt9k7v3fxsd8hs7r9pqwt5pq8jrgxz9pqgkr3qy8w5pqcjqwt0v3utc0hvsvsq2k7y3aky3pl"),
                unlockBlockHeight = 1_042_100L,
                isUnlockable = true,
                nativeBalanceSat = 1_250L,
            ),
            estimatedUnlock = null,
            daysUntilUnlock = null,
            percentLocked = 1.0f,
        ),
    )
    TimeLockScreenContent(
        vaults = mockVaults,
        currentBlockHeight = 1_000_000L,
        unitName = "NEXA",
        onAddVault = { _, _ ->
            TimeLockContractRepository.VaultCreated(
                name = "vault-1",
                address = PayAddress("nexa:nqcqq…preview…uvw"),
                fundingTxIdem = Hash256(),
            )
        },
        pill = { mockPill.draw(buttonsEnabled = false) },
    )
}
