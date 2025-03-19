package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.views.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("wally.HomeScreen.Ui2")

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
fun HomeScreenUi2(
  isShowingRecoveryWarning: Boolean = false,
  assetViewModel: AssetViewModel = viewModel { AssetViewModel() },
  accountUiDataViewModel: AccountUiDataViewModel = viewModel { AccountUiDataViewModel() }
)
{
    val assets = assetViewModel.assets.collectAsState().value
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
      initialPage = 0,
      pageCount = { 2 }
    )
    var isScanningQr by remember { mutableStateOf(false) }
    var accountUIData = accountUiDataViewModel.accountUIData.collectAsState().value
    val accounts = accountGuiSlots.collectAsState().value

    accounts.fastForEach {
        if (accountUIData[it.name] == null) accountUiDataViewModel.setAccountUiDataForAccount(it)
    }
    accountUIData = accountUiDataViewModel.accountUIData.collectAsState().value

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
            val pill = AccountPill(wallyApp!!.focusedAccount)
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

                            AccountListViewUi2(nav, accountUIData, accounts)
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
