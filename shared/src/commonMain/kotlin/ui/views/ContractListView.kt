package ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.later
import info.bitcoinunlimited.www.wally.onetlater
import info.bitcoinunlimited.www.wally.wallyApp
import info.bitcoinunlimited.www.wally.ui.chainToName
import info.bitcoinunlimited.www.wally.ui.theme.focusedChainIconResPath
import info.bitcoinunlimited.www.wally.ui.views.MpMediaView
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import org.nexa.assets.AssetInfo
import org.nexa.assets.tokenAmountString
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.GroupAuthorityFlags
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import org.nexa.libnexakotlin.rem
import repositories.TimeLockContractRepository
import ui.shortGroupId
import ui.timeLockVaultDeep
import ui.timeLockVaultTint

private val LogIt = GetLog("BU.wally.ContractListView")

/** How long the chain tip must hold still before the contract counts re-derive from it. */
private const val TIP_SETTLE_MS = 2_000L

/** The TimeLock screens' own sheen and baton accent are private to that file, so the row carries its own copy. */
private val timeLockSheen = Brush.horizontalGradient(listOf(timeLockVaultDeep, Color(0xFFAF6A58)))
private val vaultBatonTint = Color(0xFFE8590C)

/**
 * Top-level list of wallet-contract *kinds* available on this screen — one entry per contract
 * category that is supported, not per individual contract instance.
 *
 * Each contract category carries the row's summary (counts, status). Tapping a row navigates
 * to that contract category's detail screen, which constructs its own screen-scoped view model
 * against the shared repository — this ViewModel holds **no** child view models, keeping the screen tree flat.
 */
open class ContractListViewModel(private val observeFocusedAccount: Boolean = true) : ViewModel()
{
    /**
     * Supported contract categories. Sealed so the UI's `when` over a row stays exhaustive at compile time as new kinds are added.
     */
    sealed class ContractKind
    {
        /** The total amount of claimable, locked, and claimed time lock contracts, and what those contracts hold. */
        data class TimeLock(
            val claimableCount: Int,
            val lockedCount: Int,
            val claimedCount: Int = 0,
            val isLoading: Boolean = false,
            /** Native coin, tokens and batons are summed over the vaults that have not been claimed — a claimed vault holds nothing. */
            val lockedSat: Long = 0L,
            val tokens: Map<GroupId, Long> = emptyMap(),
            val authorities: List<GroupInfo> = emptyList(),
            /** Icon/ticker metadata for the groups above; a group is absent until the asset manager resolves it. */
            val assets: Map<GroupId, AssetInfo> = emptyMap(),
            val unitName: String = "",
        ) : ContractKind()
        // Add more contracts here such as multisig
    }

    protected val _kinds = MutableStateFlow<List<ContractKind>>(
        listOf(ContractKind.TimeLock(claimableCount = 0, lockedCount = 0, isLoading = true)),
    )
    val kinds: StateFlow<List<ContractKind>> = _kinds.asStateFlow()

    /** Watches the focused account; canceled and restarted on re-observe. */
    private var accountJob: Job? = null

    /** The dispatcher for this view model's history-walking jobs; repoint them all here. */
    private val jobDispatcher = Dispatchers.Default

    init
    {
        if (observeFocusedAccount) observeSelectedAccount()
    }

    /**
     * Track the focused account and keep this row's counts in step with it. The account owns its
     * repository, so there is nothing to bind — but the watch re-runs that repository's startup
     * walk, so an account switch — or returning here after creating a vault on the detail screen
     * (which rebuilds this VM) — picks up the current set of vaults.
     *
     * [collectLatest] cancels the previous account's watch when focus moves, so only the focused
     * account's sync feeds the counts.
     */
    @OptIn(FlowPreview::class)
    open fun observeSelectedAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            wallyApp?.focusedAccount?.collectLatest { account ->
                if (account == null)
                {
                    emitCounts(emptyList())
                    return@collectLatest
                }
                // Show the loading affordance until the (possibly slow) rediscovery walk has produced
                // an authoritative time lock vault list — distinguishes "still counting" from a genuine
                // "0 claimable · 0 locked".
                _kinds.value = listOf(ContractKind.TimeLock(claimableCount = 0, lockedCount = 0, isLoading = true))
                // The account's own time lock repository — the detail screen reuses the very same one, so there
                // is only ever one time lock vault factory contract per wallet. Its first construction and the
                // rediscovery walk inside start() both take the wallet lock, so they run as an app worker job;
                // it emits the first count when done.
                val pending = CompletableDeferred<TimeLockContractRepository>()
                later {
                    val repo = account.timeLockVaults
                    pending.complete(repo)
                    try { repo.start() }
                    catch (e: Throwable) { LogIt.error("contract list: time lock start failed: $e") }
                    emitCountsIfFocused(account, repo)
                }
                val repo = pending.await()
                // As the blockchain chain tip advances, re-run discovery and re-derive the split. Discovery:
                // start()'s one-shot walk can run before a freshly-recovered wallet has synced the vaults'
                // funding txs, so vaults surface here as sync catches up. Split: a time lock vault whose
                // unlock height passes flips locked → claimable. Both walk tx history, so neither runs on Main.
                coroutineScope {
                    // The vault list re-derives on every balance refresh, so counts that only move with the
                    // chain tip read stale beside it: a vault claimed elsewhere shows claimed there and still
                    // locked here until the next block lands. Track the same signal so the two agree.
                    launch(jobDispatcher) { repo.onChainNativeSat.collect { emitCountsIfFocused(account, repo) } }
                    // Debounced: a syncing wallet ticks per block, and one walk once it settles is enough.
                    account.syncedDate.debounce(TIP_SETTLE_MS).collect {
                        onetlater("timeLockContractCounts") {
                            try { repo.discoverVaultsFromChain() }
                            catch (e: Throwable) { LogIt.error("contract list: vault rediscovery failed: $e") }
                            emitCountsIfFocused(account, repo)
                        }
                    }
                }
            }
        }
    }

    /**
     * Re-derive the focused account's counts. The list is a pager page that can be returned to long
     * after the last chain-tip tick, so a vault claimed in between would still read locked here. Emits
     * the local snapshot first, then again once the on-chain reread lands — both from a worker, since
     * a snapshot walks the wallet's tx history.
     */
    open fun refresh()
    {
        val account = wallyApp?.focusedAccount?.value ?: return
        later {
            val repo = account.timeLockVaults
            emitCountsIfFocused(account, repo)
            try { repo.refreshBalances() }
            catch (e: Throwable) { LogIt.error("contract list: refresh failed: $e") }
            emitCountsIfFocused(account, repo)
        }
    }

    /**
     * Emit [repo]'s counts, but only while the [account] still holds focus. The app worker jobs that produce
     * these counts run outside the [viewModelScope], so neither an account switch nor [onCleared] stops one
     * already in flight; without this check a walk started for the previous account could land afterward
     * and overwrite the focused account's counts.
     */
    private fun emitCountsIfFocused(account: Account, repo: TimeLockContractRepository)
    {
        if (wallyApp?.focusedAccount?.value === account) emitCounts(repo.snapshotAll(), chainToName[repo.chain] ?: "nexa")
    }

    private fun emitCounts(vaults: List<TimeLockContractRepository.VaultSnapshot>, unitName: String = "")
    {
        val claimed = vaults.count { it.hasClaimTx }
        val claimable = vaults.count { it.isUnlockable && !it.hasClaimTx }
        val held = vaults.filter { !it.hasClaimTx }
        val tokens = mutableMapOf<GroupId, Long>()
        for (vault in held)
        {
            for ((group, amount) in vault.tokenBalances) tokens[group] = (tokens[group] ?: 0L) + amount
        }
        val authorities = held.flatMap { it.authorities }
        _kinds.value = listOf(
            ContractKind.TimeLock(
                claimableCount = claimable,
                lockedCount = vaults.size - claimed - claimable,
                claimedCount = claimed,
                lockedSat = held.sumOf { it.nativeBalanceSat },
                tokens = tokens,
                authorities = authorities,
                assets = trackAssets(tokens.keys + authorities.map { it.groupId }),
                unitName = unitName,
            ),
        )
    }

    /**
     * Resolve name/ticker/icon for the vault [groups] so the row's chips can render them. Vault groups sit at the
     * contract address rather than in the wallet, so the asset manager has to fetch them from the chain — only
     * groups not already resolved are asked for, and every caller of this is an app worker job, never the UI thread.
     */
    private fun trackAssets(groups: Set<GroupId>): Map<GroupId, AssetInfo>
    {
        val known = (_kinds.value.firstOrNull() as? ContractKind.TimeLock)?.assets ?: emptyMap()
        val missing = groups - known.keys
        if (missing.isEmpty()) return known
        val resolved = known.toMutableMap()
        for (group in missing)
        {
            val info = try { wallyApp?.assetManager?.track(group) }
            catch (e: Throwable) { LogIt.error("contract list: asset lookup failed for ${group.toHex()}: $e"); null }
            if (info != null) resolved[group] = info
        }
        return resolved
    }

    override fun onCleared()
    {
        super.onCleared()
        accountJob?.cancel()
    }
}

/** Preview/test fake/double: no wallet wiring, fixed sample counts. */
class ContractListViewModelFake : ContractListViewModel(observeFocusedAccount = false)
{
    override fun refresh() = Unit

    init
    {
        val moon = GroupId(ChainSelector.NEXA, ByteArray(32) { 0x11.toByte() })
        val star = GroupId(ChainSelector.NEXA, ByteArray(32) { 0x22.toByte() })
        _kinds.value = listOf(
            ContractKind.TimeLock(
                claimableCount = 1,
                lockedCount = 2,
                claimedCount = 3,
                lockedSat = 50_000_000L,
                tokens = mapOf(moon to 1200L, star to 42L),
                authorities = listOf(GroupInfo(moon, 0L, GroupAuthorityFlags.AUTHORITY or GroupAuthorityFlags.MINT)),
                unitName = "nexa",
            ),
        )
    }
}

/**
 * Compose entry point for the smart Contracts tab.
 *
 * Renders one row per [ContractListViewModel.ContractKind].
 */
@Composable
fun ContractListView(
    onOpenTimeLock: () -> Unit = {},
    vm: ContractListViewModel = viewModel { ContractListViewModel() },
)
{
    val kinds by vm.kinds.collectAsState()

    // Navigating back to this list must re-read the vaults: see refresh().
    LaunchedEffect(Unit) { vm.refresh() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(kinds) { kind ->
            ContractRow(
                kind = kind,
                onClick = {
                    when (kind)
                    {
                        is ContractListViewModel.ContractKind.TimeLock -> onOpenTimeLock()
                    }
                },
            )
        }
    }
}

@Composable
private fun ContractRow(kind: ContractListViewModel.ContractKind, onClick: () -> Unit)
{
    /**
     * Renders one @Composable view for each [ContractListViewModel.ContractKind].
     * To add a new contract type to the HomeScreen contract list. Add a new ContractListViewModel.ContractKind.<NAME> and <NAME>Row here.
     */
    when (kind)
    {
        is ContractListViewModel.ContractKind.TimeLock -> TimeLockRow(kind, onClick)
    }
}

@Composable
private fun TimeLockRow(
    kind: ContractListViewModel.ContractKind.TimeLock,
    onClick: () -> Unit,
)
{
    ContractRowFrame(
        background = timeLockSheen,
        glyphIcon = Icons.Outlined.Lock,
        name = i18n(S.tlvTitle),
        onClick = onClick,
    ) {
        if (kind.isLoading)
        {
            // Mirror the detail screen's loading affordance (spinner + label) so the summary row reads as
            // "still counting" rather than an authoritative "0 claimable · 0 locked".
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    color = Color.White,
                    strokeWidth = 1.5.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = i18n(S.tlvLoadingVaults),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
        }
        else
        {
            Text(
                text = i18n(S.tlvContractCounts) % mapOf("claimable" to kind.claimableCount.toString(), "locked" to kind.lockedCount.toString(), "claimed" to kind.claimedCount.toString()),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
            )
            val tokens = kind.tokens.filter { it.value > 0L }
            if (kind.lockedSat > 0L || tokens.isEmpty())
            {
                Spacer(Modifier.height(8.dp))
                NexaAmount(
                    text = nexaAmountText(kind.lockedSat),
                    fontSize = 20.sp,
                    color = Color.White,
                    unit = kind.unitName.takeIf { it.isNotBlank() },
                    logoSize = 17.dp,
                )
            }
            if (tokens.isNotEmpty())
            {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tokens.entries.forEach { (group, amount) ->
                        TokenChip(group = group, amount = amount, assets = kind.assets)
                    }
                }
            }
            if (kind.authorities.isNotEmpty())
            {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = i18n(S.tlvAuthorityBatonsTitle) % mapOf("count" to kind.authorities.size.toString()),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                    )
                    kind.authorities.forEach { authority ->
                        BatonChip(authority = authority, assets = kind.assets)
                    }
                }
            }
        }
    }
}

/** A contract kind's summary tile: the kind's own sheen, a bare white glyph, its name, and whatever [detail] adds. */
@Composable
private fun ContractRowFrame(
    background: Brush,
    glyphIcon: ImageVector,
    name: String,
    onClick: () -> Unit,
    detail: @Composable () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = glyphIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            detail()
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A native-coin amount led by the official nexa logo, as every nexa amount on the time lock surfaces renders it. */
@Composable
private fun NexaAmount(
    text: String,
    fontSize: TextUnit,
    color: Color,
    unit: String? = null,
    logoSize: Dp = 16.dp,
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
                color = color.copy(alpha = 0.80f),
                fontSize = 11.sp,
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
private fun AssetCoin(asset: AssetInfo?, group: GroupId, size: Dp)
{
    // Recompose when the asset finishes loading so the icon appears once its image data is decoded.
    asset?.loadStateObservable?.collectAsState()
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
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** One token holding across every vault: the token's own coin, then its amount and ticker. */
@Composable
private fun TokenChip(
    group: GroupId,
    amount: Long,
    assets: Map<GroupId, AssetInfo>,
    iconSize: Dp = 18.dp,
)
{
    val asset = assets[group]
    val decimals = asset?.tokenInfo?.genesisInfo?.decimal_places
    val amt = if (decimals != null && decimals > 0) tokenAmountString(amount, decimals) else groupDigits(amount)
    val sym = asset?.ticker?.takeIf { it.isNotBlank() } ?: shortGroupId(group.toHex())
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssetCoin(asset = asset, group = group, size = iconSize)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$amt $sym",
            color = Color.White,
            fontSize = 12.sp,
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
    iconSize: Dp = 18.dp,
)
{
    val asset = assets[authority.groupId]
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
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

/** "1,204,988" */
private fun groupDigits(v: Long): String =
    v.toString().reversed().chunked(3).joinToString(",").reversed()

/** "12,502.60" — satoshis as grouped nexa; a whole amount carries no decimals at all. */
private fun nexaAmountText(sat: Long): String =
    groupDigits(sat / 100) + (if (sat % 100 == 0L) "" else "." + (sat % 100).toString().padStart(2, '0'))

// https://youtrack.jetbrains.com/projects/KMT/issues/KMT-2076/Preview-in-Compose-1.10.0-fails
@Preview
@Composable
fun ContractListViewPreview()
{
    Box(modifier = Modifier.background(Color.White)) {
        ContractListView(vm = ContractListViewModelFake())
    }
}
