package info.bitcoinunlimited.www.wally.uiv2

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui2.ThumbButtonFAB
import info.bitcoinunlimited.www.wally.ui.accountGuiSlots
import info.bitcoinunlimited.www.wally.ui.gatherAssets
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui2.selectedAccountUi2
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.*
import info.bitcoinunlimited.www.wally.ui2.AccountListViewUi2
import info.bitcoinunlimited.www.wally.ui2.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurpleExtraLight
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.nexa.libnexakotlin.*


class SyncViewModel : ViewModel() {
    val isSynced = MutableStateFlow(false)

    /*
        Checks every second if all accounts are synced
     */
    init {
        viewModelScope.launch {
            while (true) {
                isSynced.value = withContext(Dispatchers.IO) {
                    wallyApp!!.isSynced()
                }
                delay(1000)
            }
        }
    }
}

@Composable
fun IconTextButtonUi2(
  icon: ImageVector,
  modifier: Modifier = Modifier,
  description: String = "",
  color: Color = Color.White,
  rotateIcon: Boolean = false,
  onClick: () -> Unit
)
{
    val iconModifier = if (rotateIcon)
        Modifier.graphicsLayer(
          rotationZ = 90f // Rotate the icon 90 degrees
        )
    else
        Modifier

    Column(
      modifier = modifier.wrapContentWidth().wrapContentHeight().padding(
        top = 8.dp,
        bottom = 8.dp,
        start = 2.dp,
        end = 2.dp
      ),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
          modifier = iconModifier.wrapContentWidth().wrapContentHeight().clickable {
              onClick()
          },
          imageVector = icon,
          contentDescription = description,
          tint = color,
        )
        Box(
          modifier = Modifier.wrapContentWidth().wrapContentHeight().clickable {
              onClick()
          },
          contentAlignment = Alignment.Center
        ) {
            Text(
              modifier = Modifier.clickable {
                  onClick()
              },
              style = MaterialTheme.typography.labelSmall.copy(
                color = color
              ),
              text = description,
            )
        }
    }
}

// Data class for å representere elementene i TabRow
data class TabRowItem(
  val icon: ImageVector,
  val description: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenUi2(isShowingRecoveryWarning: Boolean = false)
{
    val assetViewModel = viewModel { AssetViewModel() }
    val assets = assetViewModel.assets.collectAsState().value
    val coroutineScope = rememberCoroutineScope()
    val clipmgr: ClipboardManager = LocalClipboardManager.current
    val pagerState = rememberPagerState(
      initialPage = 0,
      pageCount = { 2 }
    )
    var isScanningQr by remember { mutableStateOf(false) }
    val accountUiDataViewModel = viewModel { AccountUiDataViewModel() }
    val accountUIData = accountUiDataViewModel.accountUIData.collectAsState().value
    val accounts = accountGuiSlots.collectAsState().value

    accounts.fastForEach {
        if (accountUIData[it.name] == null) accountUiDataViewModel.setAccountUiDataForAccount(it)
    }

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
            AccountPill()
            Spacer(modifier = Modifier.height(8.dp))
            if (assets.isNotEmpty())
            {
                Spacer(Modifier.height(8.dp))
                AssetCarousel()
                Spacer(Modifier.height(8.dp))
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
                            TransactionsList()
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
                  clearAlerts()
                  val cliptext = clipmgr.getText()?.text
                  if (cliptext != null && cliptext != "")
                  {
                      wallyApp?.handlePaste(cliptext)
                  }
                  else
                  {
                      displayNotice(S.pasteIsEmpty)
                  }
              }
            )
            Spacer(Modifier.height(80.dp))
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

class AssetViewModel: ViewModel()
{
    val assets = MutableStateFlow(listOf<AssetInfo>())
    var assetsJob: Job? = null
    var accountJob: Job? = null

    init {
        selectedAccountUi2.value?.let {
            assets.value = getAssetInfoList(it)
        }
        observeSelectedAccount()
    }

    private fun observeSelectedAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            selectedAccountUi2.onEach {
                it?.let { account ->
                    observeAssets(account)
                }
            }.launchIn(this)
        }
    }

    private fun getAssetInfoList(account: Account): List<AssetInfo>
    {
        val assetInfoList = mutableListOf<AssetInfo>()
        account.assets.values.forEach {
            assetInfoList.add(it.assetInfo)
        }
        return assetInfoList
    }

    private fun observeAssets(account: Account)
    {
        assetsJob?.cancel()
        assetsJob = viewModelScope.launch {
            account.assetsObservable.onEach { it ->
                val assetInfoList = mutableListOf<AssetInfo>()
                it.values.forEach { assetPerAccount ->
                    assetInfoList.add(assetPerAccount.assetInfo)
                }
                assets.value = assetInfoList
            }.launchIn(this)
        }
    }

    override fun onCleared()
    {
        super.onCleared()
        accountJob?.cancel()
        assetsJob?.cancel()
    }
}

@Composable
fun AssetCarousel() {
    val viewModel = viewModel { AssetViewModel() }
    val assets = viewModel.assets.collectAsState().value
    val assetList = assets.toList().sortedBy { it.nft?.title ?: it.name ?: it.ticker ?: it.groupId.toString() }

    LazyRow(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        assetList.forEach { assetInfo ->
            item {
                AssetCarouselItem(assetInfo)
            }
        }
    }
}

@Composable
fun AssetCarouselItemNameOverlay(name: String, maxWidth: Dp, modifier: Modifier = Modifier)
{
    Box(
      modifier = modifier
        .fillMaxHeight()
        .widthIn(max = maxWidth)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startY = 50f,
            endY = 200f,
          )
        )
    ) {
        Text(
          text = name,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = MaterialTheme.typography.labelSmall.fontSize,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.align(Alignment.BottomStart)
            .wrapContentWidth()
            .padding(start = 8.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun AssetCarouselItem(asset: AssetInfo, hasNameOverLay: Boolean = false)
{
    val nft = asset.nft
    val maxSize = 60.dp

    Box (
      modifier = Modifier
        .wrapContentSize()
        .clip(RoundedCornerShape(16.dp)).clickable {
            nav.go(ScreenId.Assets)
            nav.go(ScreenId.Assets, asset.groupId.toByteArray())
        },
    ) {
        MpMediaView(asset.iconImage, asset.iconBytes, asset.iconUri?.toString(), hideMusicView = true) { mi, draw ->
            val m = Modifier.background(Color.Transparent).size(maxSize).clickable {
                nav.go(ScreenId.Assets)
                nav.go(ScreenId.Assets, asset.groupId.toByteArray())
            }
            draw(m)
        }
        if (hasNameOverLay)
            AssetCarouselItemNameOverlay(
              name = nft?.title ?: asset.name ?: "",
              maxWidth = maxSize,
              modifier = Modifier.matchParentSize().clickable {
                  nav.go(ScreenId.Assets)
                  nav.go(ScreenId.Assets, asset.groupId.toByteArray())
              }
            )
    }
}

class BalanceViewModel: ViewModel()
{
    val balance = MutableStateFlow("Loading...")
    val fiatBalance = MutableStateFlow("")
    var balanceJob: Job? = null
    var accountJob: Job? = null

    init {
        selectedAccountUi2.value?.let { account ->
            observeBalance(account)
        }
        observeSelectedAccount()
        setFiatBalance()
    }

    fun setFiatBalance()
    {
        selectedAccountUi2.value?.let {
            val qty: BigDecimal = try
            {
                it.fromFinestUnit(it.wallet.balance)
            }
            catch (e: NumberFormatException)
            {
                displayError(i18n(S.invalidQuantity))
                return@let
            }
            catch (e: ArithmeticException)
            {
                displayError(i18n(S.invalidQuantityTooManyDecimalDigits))
                return@let
            }
            catch (e: Exception) // This used to be a catch (e: java.text.ParseException)
            {
                displayError(i18n(S.invalidQuantity))
                return@let
            }

            val fpc = it.fiatPerCoin
            val fiatDisplay = qty * fpc
            if(fpc < 0) // Usd value is not fetched
                fiatBalance.value = ""
            else
                fiatBalance.value = FiatFormat.format(fiatDisplay)
        }
    }

    private fun observeSelectedAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            selectedAccountUi2.onEach {
                it?.let { account ->
                    setFiatBalance()
                    observeBalance(account)
                }
            }.launchIn(this)
        }
    }

    private fun observeBalance(account: Account)
    {
        balanceJob?.cancel()
        balance.value = account.format(account.balanceState.value)
        balanceJob = viewModelScope.launch {
            account.balanceState.onEach {
                balance.value = account.format(it)
                setFiatBalance()
            }.launchIn(this)
        }
    }

    override fun onCleared()
    {
        super.onCleared()
        balanceJob?.cancel()
        accountJob?.cancel()
    }
}

@Composable fun AccountPillHeader()
{
    val balanceViewModel = viewModel { BalanceViewModel() }
    val account = selectedAccountUi2.collectAsState().value
    val fiatBalance = balanceViewModel.fiatBalance.collectAsState().value
    val balance = balanceViewModel.balance.collectAsState().value

    /*
        Runs the callback every time account?.fiatPerCoin changes
     */
    LaunchedEffect(account?.fiatPerCoin) {
        balanceViewModel.setFiatBalance()
    }

    Row(
      modifier = Modifier.wrapContentHeight()
    ){
        Text(
          text = "NEX",
          style = MaterialTheme.typography.headlineMedium.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold
          ),
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = balance,
          style = MaterialTheme.typography.headlineMedium.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold
          ),
          textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
      modifier = Modifier.wrapContentHeight()
    ){
        if (fiatBalance.isNotEmpty())
        {
            Text(
              text = fiatCurrencyCode,
              style = MaterialTheme.typography.labelLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
              ),
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = fiatBalance,
              style = MaterialTheme.typography.labelLarge.copy(
                color = Color.White
              ),
              textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(12.dp))
            VerticalDivider(
              color = Color.White,
              modifier = Modifier
                .width(1.dp)
                .height(12.dp)
                .align(Alignment.CenterVertically)
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
          text = account?.name ?: "",
          style = MaterialTheme.typography.labelLarge.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold
          ),
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(12.dp))
        VerticalDivider(
          color = Color.White,
          modifier = Modifier
            .width(1.dp)
            .height(12.dp)
            .align(Alignment.CenterVertically)
        )
        Spacer(Modifier.width(12.dp))
        Syncing()
    }
}

@Composable fun AccountPill(buttonsEnabled: Boolean = true)
{
    val accountUiDataViewModel = viewModel { AccountUiDataViewModel() }
    val account = selectedAccountUi2.collectAsState().value
    val accountUIData = accountUiDataViewModel.accountUIData.collectAsState().value
    val roundedCorner = 16.dp

    LaunchedEffect(true) {
        accountUiDataViewModel.setup()
    }

    account?.let { selAct ->
        if (accountUIData[selAct.name] == null) accountUiDataViewModel.setAccountUiDataForAccount(selAct)
    }
    val selectedAccountUIData = account?.uiData()
    val curSync = selectedAccountUIData?.account?.wallet?.chainstate?.syncedDate ?: 0
    val offerFastForward = (millinow()/1000 - curSync) > OFFER_FAST_FORWARD_GAP
    val isFastForwarding = accountUIData[account?.name]?.fastForwarding ?: false

    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.Center
    ) {
        Column(
          modifier = Modifier
            .shadow(
              elevation = 4.dp,
              shape = RoundedCornerShape(roundedCorner),
              clip = false,
            )
            .clip(RoundedCornerShape(roundedCorner))
            .background(wallyPurple)
            .wrapContentHeight()
            .fillMaxWidth(0.95f)
            .background(
              Brush.linearGradient(
                colors = listOf(
                  wallyPurple,
                  Color.White.copy(alpha = 0.2f)
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, 0f)
              )
            )
            .padding(
              horizontal = 32.dp,
              vertical = 8.dp
            ),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            AccountPillHeader()
            if (buttonsEnabled)
            {
                Spacer(Modifier.height(4.dp))
                Row(
                  modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                  horizontalArrangement = Arrangement.Center
                ) {
                    val verticalDividerModifier = Modifier
                      .width(1.dp)
                      .height(40.dp)
                      .padding(vertical = 8.dp)
                      .align(Alignment.CenterVertically)

                    IconTextButtonUi2(
                      icon = Icons.Outlined.ArrowUpward,
                      modifier = Modifier.weight(1f),
                      description = i18n(S.Send),
                    ) {
                        nav.go(ScreenId.Send)
                    }
                    VerticalDivider(
                      color = Color.White,
                      modifier = verticalDividerModifier
                    )
                    IconTextButtonUi2(
                      icon = Icons.Outlined.ArrowDownward,
                      modifier = Modifier.weight(1f),
                      description = i18n(S.Receive)
                    ) {
                        nav.go(ScreenId.Receive)
                    }
                    VerticalDivider(
                      color = Color.White,
                      modifier = verticalDividerModifier
                    )
                    IconTextButtonUi2(
                      icon = Icons.Outlined.CallSplit,
                      modifier = Modifier.weight(1f),
                      description = i18n(S.title_split_bill),
                      rotateIcon = true
                    ) {
                        nav.go(ScreenId.SplitBill)
                    }
                    VerticalDivider(
                      color = Color.White,
                      modifier = verticalDividerModifier
                    )
                    IconTextButtonUi2(
                      icon = Icons.Outlined.ManageAccounts,
                      modifier = Modifier.weight(1f),
                      description = i18n(S.account)
                    ) {
                        nav.go(ScreenId.AccountDetails)
                    }
                    if (offerFastForward && !isFastForwarding)
                    {
                        VerticalDivider(
                          color = Color.White,
                          modifier = verticalDividerModifier
                        )
                        IconTextButtonUi2(
                          icon = Icons.Outlined.FastForward,
                          modifier = Modifier.weight(1f),
                          description = i18n(S.fastSync)
                        ) {
                            accountUiDataViewModel.fastForwardSelectedAccount()
                        }
                    }
                }
            }
            else
                Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun Syncing(syncColor: Color = Color.White) {
    val syncViewModel = viewModel { SyncViewModel() }
    val isSynced = syncViewModel.isSynced.collectAsState().value
    val infiniteTransition = rememberInfiniteTransition()
    val syncingText = "Syncing" // TODO: Move to string resource
    val syncedText = "Synced" // TODO: Move to string resource

    val animation by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(
        animation = tween(1000, easing = LinearEasing), // 1 second for full rotation
        repeatMode = RepeatMode.Restart
      )
    )

    Row {
        if (isSynced)
            Text(text = syncedText, style = MaterialTheme.typography.labelLarge.copy(
              color = syncColor,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            ))
        else
            Text(text = syncingText, style = MaterialTheme.typography.labelLarge.copy(
              color = syncColor,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            ))
        Spacer(modifier = Modifier.width(4.dp))
        if (isSynced)
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = syncedText,
              tint = syncColor,
              modifier = Modifier.size(18.dp)
            )
        else
            Icon(
              imageVector = Icons.Default.Sync,
              contentDescription = syncingText,
              tint = syncColor,
              modifier = Modifier
                .size(18.dp)
                .rotate(animation)
            )
    }
}

data class RecentTransactionUIData(
  val transaction: TransactionHistory,
  val type: String,
  val icon: ImageVector,
  val contentDescription: String,
  val amount: String,
  val currency: String,
  val dateEpochMiliseconds: Long,
  val date: String = formatLocalEpochMilliseconds(dateEpochMiliseconds),
  val assets: List<AssetPerAccount> = listOf()
)

class TxHistoryViewModel: ViewModel()
{
    val txHistory = MutableStateFlow<List<RecentTransactionUIData>>(listOf())
    var accountJob: Job? = null

    init {
        selectedAccountUi2.value?.let { account ->
            getAllTransactions(account)
        }
    }

    fun getAllTransactions(acc: Account)
    {
        val transactions = mutableListOf<RecentTransactionUIData>()
        acc.wallet.forEachTxByDate {
            val amount = it.incomingAmt - it.outgoingAmt
            val txType = if (amount == 0L) "Unknown" else if (amount > 0) "Received" else "Send"
            val txIcon = if (amount == 0L) Icons.Outlined.QuestionMark else if (amount > 0) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward
            val assetsTransacted = it.tx.gatherAssets({
                // We are going to use the native coin as a hint as to whether this transaction is sending or receiving
                // If its sending, just look for assets that left this wallet
                // If its receiving, look for assets coming in.
                // TODO: look at inputs and accurately describing sending/receiving
                if (it == null) false
                else
                {
                    val result: Boolean = if (amount > 0) acc.wallet.isWalletAddress(it)
                    else !acc.wallet.isWalletAddress(it)
                    result
                }
            })
            val txUiData = RecentTransactionUIData(
              type = txType,
              icon = txIcon,
              contentDescription = "Transaction",
              amount = acc.cryptoFormat.format(acc.fromFinestUnit(amount)),
              currency = acc.currencyCode,
              dateEpochMiliseconds = it.date,
              assets = assetsTransacted,
              transaction = it
            )
            transactions.add(txUiData)
            false
        }
        txHistory.value = transactions.toList().sortedByDescending { it.dateEpochMiliseconds }
    }

    override fun onCleared()
    {
        super.onCleared()
        accountJob?.cancel()
    }
}

@Composable
fun TransactionsList(modifier: Modifier = Modifier)
{
    val viewModel = viewModel { TxHistoryViewModel() }
    val transactions = viewModel.txHistory.collectAsState().value
    val account = selectedAccountUi2.collectAsState().value
    account?.let {
        val balance = it.balanceState.collectAsState().value
        LaunchedEffect(balance) {
            viewModel.getAllTransactions(it)
        }
    }

    if (transactions.isEmpty())
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("No transaction history available.")
            Text("Start by receiving funds")
        }

    LazyColumn(
      modifier = modifier
    ) {
        transactions.forEach { tx ->
            item {
                RecentTransactionListItem(tx)
                Spacer(Modifier.height(8.dp))
                if (tx.assets.isNotEmpty())
                {
                    tx.assets.forEach { asset ->
                        // TODO: Check if assets were actually sent or received here. How?
                        AssetListItem(asset, tx)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        // Since the thumb buttons cover the bottom most row, this blank bottom row allows the user to scroll the account list upwards enough to
        // uncover the last account.  Its not necessary if there are just a few accounts though.
        if (transactions.size >= 2)
            item {
                Spacer(Modifier.height(144.dp))
            }
    }
}

class AssetListItemViewModel(): ViewModel()
{
    fun getHost (docUrl: String?): String?
    {
        if (docUrl != null)
        {
            val url = com.eygraber.uri.Url.parseOrNull(docUrl)
            val host = try
            {
                url?.host  // although host is supposedly not null, I can get "java.lang.IllegalArgumentException: Url requires a non-null host"
            }
            catch (e: IllegalArgumentException)
            {
                null
            }
            return host
        }
        return null
    }
}

@Composable
fun AssetListItem(asset: AssetPerAccount, tx: RecentTransactionUIData)
{
    val viewModel = viewModel { AssetListItemViewModel() }
    val assetInfo = asset.assetInfo
    val assetName = assetInfo.nameObservable.collectAsState().value
    val nft = assetInfo.nftObservable.collectAsState().value
    val name = (if ((nft != null) && (nft.title.isNotEmpty())) nft.title else assetName)

    ListItem(
      colors = ListItemDefaults.colors(
        containerColor = wallyPurpleExtraLight
      ),
      leadingContent = {
          Row {
              Icon(
                tx.icon,
                tx.contentDescription,
              )
              Text(
                text = tx.type
              )
          }
      },
      headlineContent = {
          Row(
            modifier = Modifier.fillMaxWidth(),
          ){
              Column(
                modifier = Modifier.weight(1f)
              ) {
                  nft?.series?.let {
                      Text(
                          text = it,
                          style = MaterialTheme.typography.labelMedium.copy(
                              color = wallyPurple
                          )
                      )
                  }
                  name?.let {
                      Text(
                          text = it,
                          style = MaterialTheme.typography.bodyLarge.copy(
                              color = wallyPurple,
                              fontWeight = FontWeight.Bold
                          )
                      )
                  }
                  viewModel.getHost(assetInfo.docUrl)?.let {
                      Text(
                          text = it,
                          style = MaterialTheme.typography.labelSmall.copy(
                              color = wallyPurple,
                              fontStyle = FontStyle.Italic
                          )
                      )
                  }
              }
          }
      },
        trailingContent = {
            Box (
                modifier = Modifier
                    .wrapContentSize()
                    .clip(RoundedCornerShape(16.dp)).clickable {
                        nav.go(ScreenId.Assets)
                    }
            ) {
                MpMediaView(assetInfo.iconImage, assetInfo.iconBytes, assetInfo.iconUri?.toString(), hideMusicView = true) { mi, draw ->
                    val m = Modifier.background(Color.Transparent).size(60.dp)
                    draw(m)
                }
            }
        }
    )
}

@Composable
fun RecentTransactionListItem(tx: RecentTransactionUIData)
{
    ListItem(
      colors = ListItemDefaults.colors(
        containerColor = wallyPurpleExtraLight
      ),
      leadingContent = {
          Row {
              Icon(
                tx.icon,
                tx.contentDescription,
              )
              Text(
                text = tx.type
              )
          }
      },
      headlineContent = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
              ) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                      ResImageView("icons/nexa_icon.png", Modifier.size(16.dp), "Blockchain icon")
                      Spacer(Modifier.width(8.dp))
                      Text(
                        text = tx.amount,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = wallyPurple,
                      )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = tx.date,
                    fontSize = 12.sp,
                    color = wallyPurple
                  )
              }
          }
      }
    )
}
