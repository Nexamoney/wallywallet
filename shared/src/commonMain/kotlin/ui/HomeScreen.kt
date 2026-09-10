package info.bitcoinunlimited.www.wally.ui

import AudioPlayer
import AudioPlayerViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.views.*
import ui.views.ContractListView
import ui.views.ContractListViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.nexa.libnexakotlin.*
import org.nexa.threads.Gate
import org.nexa.threads.Mutex
import org.nexa.threads.millinow
import org.nexa.threads.millisleep

private val LogIt = GetLog("wally.HomeScreen")

// stores the account name we are receiving into and the receive address as a pair
var sendToAddress: MutableStateFlow<String> = MutableStateFlow("")

abstract class SyncViewModel: ViewModel()
{
    abstract val isSynced : StateFlow<Boolean>
}

class SyncViewModelFake: SyncViewModel()
{
    val syncValue = MutableStateFlow(false)
    override val isSynced: StateFlow<Boolean> =
      syncValue.map { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), false)
}

class SyncViewModelImpl : SyncViewModel()
{
    val syncValue = MutableStateFlow(false)

    override val isSynced: StateFlow<Boolean> =
      syncValue.map { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), false)

    /*
        Checks every second if all accounts are synced
     */
    init {
        viewModelScope.launch {
            while (true)
            {
                syncValue.value = withContext(Dispatchers.IO) {
                    wallyApp?.isSynced() ?: false
                }
                delay(1000)
            }
        }
    }
}

private val FalseFlow = MutableStateFlow<Boolean>(false)
class SyncViewModelAccount(val account: MutableStateFlow<Account?>) : SyncViewModel()
{
    // Flatmap takes a MutableStateFlow of MutableStateFlows and lets you apply a transform and get the final flow.
    // (that takes the first, resolved the second, then xforms the second), resulting in a StateFlow of your transformed second MSF.
    override val isSynced: StateFlow<Boolean> = account.flatMapLatest { act ->
          if (act == null) FalseFlow  // no account
          else {
              if ((act.wallet.chainstate?.chain?.net?.size ?: 0) == 0) FalseFlow  // no net connection
              else
              {
                  act.syncedDate.map {
                      val now = millinow() / 1000
                      it + 5 * 60 > now  // Within 5 minutes
                  }
              }
          }
      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), false)
}


// Data class for a TabRow
data class TabRowItem(
  val icon: ImageVector,
  val description: String
)

// One-shot tab request consumed by the next HomeScreen composition (set before navigating home).
var requestedHomeTab: Int? = null

/** The home header tab row (accounts / transactions / contracts). Also drawn by the TimeLock screen so its sub-screens keep the home chrome. */
@Composable
fun HomeTabRow(selectedIndex: Int, onSelect: (Int) -> Unit)
{
    val tabRowItems = listOf(
        TabRowItem(
            icon = Icons.Outlined.Group,
            description = "Accounts"
        ),
        TabRowItem(
            icon = Icons.Outlined.History,
            description = "Transactions"
        ),
        TabRowItem(
            icon = Icons.Outlined.Description,
            description = "Contracts"
        ),
    )
    TabRow(
      selectedTabIndex = selectedIndex
    ) {
        tabRowItems.forEachIndexed { index, item ->
            Tab(
              // text = { Text(text = item.description)},
              icon = { Icon(imageVector = item.icon, item.description) },
              selected = selectedIndex == index,
              onClick = { onSelect(index) }
            )
        }
    }
}

val txHistViewModel = TxHistoryViewModel()

@Composable
fun HomeScreen(
  isShowingRecoveryWarning: Boolean = false,
  pill: AccountPillViewModel,
  assetViewModel: AssetViewModel,
  accountUiDataViewModel: AccountUiDataViewModel = viewModel { AccountUiDataViewModel() },
  audioPlayerViewModel: AudioPlayerViewModel = viewModel { AudioPlayerViewModel() },
  unlock: UnlockViewModel)
{
    val assets = assetViewModel.assets.collectAsState().value
    val coroutineScope = rememberCoroutineScope()
    val initialTab = remember { requestedHomeTab?.also { requestedHomeTab = null } ?: 0 }
    val pagerState = rememberPagerState(
      initialPage = initialTab,
      pageCount = { 3 }
    )
    var isScanningQr by remember { mutableStateOf(false) }
    val contractVm: ContractListViewModel = viewModel { ContractListViewModel() }


    Box (
      modifier = Modifier.fillMaxSize(),
    ) {
        RecomposeCounter("Home ")
        Column {
            if (!isShowingRecoveryWarning)
                Spacer(Modifier.height(16.dp))
            // The contracts tab only lists contracts, so the pill stays the account's own; the vault
            // summary line belongs to the vault screen, once that contract is actually open.
            pill.draw()
            Spacer(modifier = Modifier.height(8.dp))
            if (assets.isNotEmpty())
            {
                Spacer(Modifier.height(6.dp))
                AssetCarousel(assetViewModel)
                Spacer(Modifier.height(6.dp))
            }
            HomeTabRow(pagerState.currentPage) { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 ->
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LaunchedEffect(true)
                            {
                                accountUiDataViewModel.setup()
                            }

                            AccountListView(nav, accountUiDataViewModel, unlock = unlock)
                        }
                    1 ->
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            TransactionsList(Modifier, txHistViewModel)
                        }
                    2 ->
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            ContractListView(
                                onOpenTimeLock = { nav.go(ScreenId.TimeLock) },
                                vm = contractVm,
                            )
                        }
                }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .wrapContentHeight()
                .fillMaxWidth()
        ) {
            ThumbButtonFAB(
              pasteIcon = Icons.Outlined.ContentPasteGo,
              onScanQr = { isScanningQr = true },
              onResult = {
                  tlater("handlePastedUri") { wallyApp?.handlePaste(it) }
              }
            )
            Spacer(Modifier.height(24.dp))
        }
        if (isScanningQr && platform().hasQrScanner)
        {
            QrScannerDialog(
              onDismiss = {
                  clearAlerts()
                  isScanningQr = false
              },
              onScan = {
                  if (it.isNotEmpty() && isScanningQr)
                  {
                      isScanningQr = false
                      tlater("handleScannedQr") { wallyApp?.handlePaste(it) }
                      audioPlayerViewModel.playScanQrSound()
                  }
              }
            )
        }
    }
}
