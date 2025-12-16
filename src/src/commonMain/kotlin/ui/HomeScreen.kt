package info.bitcoinunlimited.www.wally.ui

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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("wally.HomeScreen")

// stores the account name we are receiving into and the receive address as a pair
var sendToAddress: MutableStateFlow<String> = MutableStateFlow("")

abstract class SyncViewModel: ViewModel()
{
    val isSynced = MutableStateFlow(false)
}

class SyncViewModelFake: SyncViewModel()

class SyncViewModelImpl : SyncViewModel()
{
    /*
        Checks every second if all accounts are synced
     */
    init {
        viewModelScope.launch {
            while (true) {
                isSynced.value = withContext(Dispatchers.IO) {
                    wallyApp?.isSynced() ?: false
                }
                delay(1000)
            }
        }
    }
}


// Data class for å representere elementene i TabRow
data class TabRowItem(
  val icon: ImageVector,
  val description: String
)

val txHistViewModel = TxHistoryViewModel()

@Composable
fun HomeScreen(
  isShowingRecoveryWarning: Boolean = false,
  pill: AccountPillViewModel,
  assetViewModel: AssetViewModel = viewModel { AssetViewModel() },
  accountUiDataViewModel: AccountUiDataViewModel = viewModel { AccountUiDataViewModel() },
)
{
    val assets = assetViewModel.assets.collectAsState().value
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
      initialPage = 0,
      pageCount = { 2 }
    )
    var isScanningQr by remember { mutableStateOf(false) }


    val tabRowItems = listOf(
        TabRowItem(
            icon = Icons.Outlined.Group,
            description = "Accounts"
        ),
        TabRowItem(
            icon = Icons.Outlined.History,
            description = "Transactions"
        ),
    )

    Box (
      modifier = Modifier.fillMaxSize(),
    ) {
        Column {
            if (!isShowingRecoveryWarning)
                Spacer(Modifier.height(16.dp))
            pill.draw(true)
            Spacer(modifier = Modifier.height(8.dp))
            if (assets.isNotEmpty())
            {
                Spacer(Modifier.height(6.dp))
                AssetCarousel(assetViewModel)
                Spacer(Modifier.height(6.dp))
            }
            TabRow(
              selectedTabIndex = pagerState.currentPage
            ) {
                tabRowItems.forEachIndexed { index, item ->
                    Tab(
                      // text = { Text(text = item.description)},
                      icon = { Icon(imageVector = item.icon,"") },
                      selected = pagerState.currentPage == index,
                      onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                    )
                }
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

                            AccountListView(nav, accountUiDataViewModel)
                        }
                    1 ->
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            TransactionsList(Modifier, txHistViewModel)
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
                  wallyApp?.handlePaste(it)
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
                      isScanningQr = false
                      wallyApp?.handlePaste(it)
                  }
            )
        }
    }
}
