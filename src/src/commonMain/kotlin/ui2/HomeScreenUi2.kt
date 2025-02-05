package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurpleExtraLight
import info.bitcoinunlimited.www.wally.ui2.views.*
import info.bitcoinunlimited.www.wally.ui2.views.uiData
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

@Composable
fun HomeScreenUi2(
  isShowingRecoveryWarning: Boolean = false,
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
                Spacer(Modifier.height(8.dp))
                AssetCarousel(assetViewModel)
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

open class AssetViewModel: ViewModel()
{
    val assets = MutableStateFlow(listOf<AssetInfo>())
    var assetsJob: Job? = null
    var accountJob: Job? = null

    init {
        wallyApp?.focusedAccount?.value?.let {
            assets.value = getAssetInfoList(it)
        }
        observeSelectedAccount()
    }

    open fun observeSelectedAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            wallyApp?.focusedAccount?.onEach {
                if (it != null) observeAssets(it)
                else assets.value = listOf()
            }?.launchIn(this)
        }
    }

    open fun getAssetInfoList(account: Account): List<AssetInfo>
    {
        val assetInfoList = mutableListOf<AssetInfo>()
        account.assets.values.forEach {
            assetInfoList.add(it.assetInfo)
        }
        return assetInfoList
    }

    open fun observeAssets(account: Account)
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

class AssetViewModelFake: AssetViewModel()
{
    override fun observeSelectedAccount()
    {

    }
    override fun getAssetInfoList(account: Account): List<AssetInfo>
    {
        return listOf()
    }
    override fun observeAssets(account: Account)
    {
    }
}

@Composable
fun AssetCarousel(viewModel: AssetViewModel = viewModel { AssetViewModel() })
{
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
    val iconImage = asset.iconImageState.collectAsState().value
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
        MpMediaView(iconImage, asset.iconBytes, asset.iconUri?.toString(), hideMusicView = true) { mi, draw ->
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

/*
    Root class for BalanceViewModel used for testing
 */
abstract class BalanceViewModel(val dispatcher: CoroutineDispatcher = Dispatchers.Main): ViewModel()
{
    open val balance = MutableStateFlow("Loading...")
    open val fiatBalance = MutableStateFlow("Loading...")

    // Set which account's balance we are tracking
    abstract fun setAccount(act: Account)

    abstract fun setFiatBalance(account: Account)
    abstract fun observeBalance(account: Account)
    abstract fun observeSelectedAccount()
}

class BalanceViewModelFake: BalanceViewModel()
{
    override fun setAccount(act: Account) {}
    override fun setFiatBalance(account: Account) {}
    override fun observeBalance(account: Account) {}
    override fun observeSelectedAccount() {}
}


class BalanceViewModelImpl(val account : MutableStateFlow<Account?>): BalanceViewModel()
{
    constructor(act: Account?) : this(MutableStateFlow(act))

    override val balance = MutableStateFlow("Loading...")
    override val fiatBalance = MutableStateFlow("")
    var balanceJob: Job? = null
    var accountJob: Job? = null

    init {
        account.value?.let { act ->
            observeBalance(act)
            setFiatBalance(act)
        }
    }

    override fun setAccount(act: Account)
    {
        onCleared()
        account.value = act
        observeBalance(act)
        setFiatBalance(act)
    }

    override fun setFiatBalance(account: Account)
    {
        laterJob {  // Do this outside of coroutines because getting the wallet balance may block with DB access
            account.let {
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
                if (fpc < 0) // Usd value is not fetched
                    fiatBalance.value = ""
                else
                    fiatBalance.value = FiatFormat.format(fiatDisplay)
            }
        }
    }

    override fun observeSelectedAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch(dispatcher) {
            wallyApp!!.focusedAccount.onEach {
                it?.let { account ->
                    setFiatBalance(account)
                    observeBalance(account)
                }
            }.launchIn(this)
        }
    }

    override fun observeBalance(act: Account)
    {
        balanceJob?.cancel()
        account.value = act
        balance.value = act.format(act.balanceState.value)
        balanceJob = viewModelScope.launch(dispatcher) {
            act.balanceState.onEach {
                try
                {
                    balance.value = act.format(it)
                }
                catch (e: Exception)
                {
                    balance.value = ""
                }
                setFiatBalance(act)
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

abstract class AccountPillViewModel(val account:MutableStateFlow<Account?>, val dispatcher: CoroutineDispatcher = Dispatchers.Main): ViewModel()
{
    abstract val balance: BalanceViewModel
    abstract val sync: SyncViewModel
    // Set which account's balance we are tracking
    abstract fun setAccount(act: Account?)

    @Composable
    fun AccountPillHeader()
    {
        val act = account.collectAsState().value
        val currencyCode = act?.uiData()?.currencyCode ?: ""
        val fiatBalance = balance.fiatBalance.collectAsState().value
        val bal = balance.balance.collectAsState().value

        // If no account is available, do not show the pill
        if (account == null) return

        // Runs the callback every time account?.fiatPerCoin changes
        if (act != null)
        {
            LaunchedEffect(act.fiatPerCoin) {
                balance.setFiatBalance(act)
            }
        }

        Row(
          modifier = Modifier.wrapContentHeight()
        ) {
            Text(
              text = currencyCode,
              style = MaterialTheme.typography.headlineMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
              ),
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("AccountPillCurrencyCode") // Added test tag
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = bal,
              style = MaterialTheme.typography.headlineMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
              ),
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("AccountPillBalance") // Added test tag
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.wrapContentHeight()
        ) {
            if (fiatBalance.isNotEmpty())
            {
                Text(
                  text = fiatCurrencyCode,
                  style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                  ),
                  textAlign = TextAlign.Center,
                  modifier = Modifier.testTag("AccountPillFiatCurrencyCode") // Added test tag
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = fiatBalance,
                  style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White
                  ),
                  textAlign = TextAlign.Center,
                  modifier = Modifier.testTag("AccountPillFiatBalance") // Added test tag
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
              text = act?.name ?: "",
              style = MaterialTheme.typography.labelLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
              ),
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("AccountPillAccountName")
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
            Syncing(Color.White, sync)
        }
    }

    @Composable
    fun draw(buttonsEnabled: Boolean = true)
    {
        val roundedCorner = 16.dp
        val act = account.collectAsState().value
        val curSync = act?.wallet?.chainstate?.syncedDate ?: 0
        val offerFastForward = (millinow() / 1000 - curSync) > OFFER_FAST_FORWARD_GAP
        val uiData = act?.uiData()  // TODO this data needs to be persistent?
        val isFastForwarding = uiData?.fastForwarding ?: false


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
                                act?.let { fastForwardAccount(it) }
                            }
                        }
                    }
                }
                else
                    Spacer(Modifier.height(8.dp))
            }
        }
    }
}

class AccountPillViewModelFake(account:MutableStateFlow<Account?>, override val balance: BalanceViewModel = BalanceViewModelImpl(account), override val sync: SyncViewModel = SyncViewModelImpl()): AccountPillViewModel(account)
{
    override fun setAccount(act: Account?) {}
}

class AccountPill(account:MutableStateFlow<Account?>): AccountPillViewModel(account)
{
    constructor(act: Account?) : this(MutableStateFlow(act))

    override val balance = BalanceViewModelImpl(account.value)
    override val sync = SyncViewModelImpl()

    override fun setAccount(act: Account?)
    {
        account.value = act
    }

    var job: Job? = viewModelScope.launch(dispatcher) {
        account.onEach {
            if (it != null) balance.setAccount(it)
        }.launchIn(this)
    }

    override fun onCleared()
    {
        super.onCleared()
        job?.cancel()
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
  val date: String = if (dateEpochMiliseconds > 1231006505000L) formatLocalEpochMilliseconds(dateEpochMiliseconds) else "",
  val assets: List<AssetPerAccount> = listOf()
)

class TxHistoryViewModel: ViewModel()
{
    val txHistory = MutableStateFlow<List<RecentTransactionUIData>>(listOf())

    init {
        wallyApp!!.focusedAccount.value?.let { account ->
            getAllTransactions(account)
        }
    }

    fun getAllTransactions(acc: Account)
    {
        laterJob {  // Do not do anything blocking (in this case DB access) within a UI or coroutine thread
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
                // This places some data onscreen, in case the actual number of transactions is so large that it takes a lot of time to go through them all
                if (transactions.size == 10 && txHistory.value.size == 0)
                {
                    transactions.sortByDescending { it.dateEpochMiliseconds }
                    txHistory.value = transactions
                }
                false
            }
            // all transactions loaded, so add to the list
            transactions.sortByDescending { it.dateEpochMiliseconds }
            txHistory.value = transactions
        }
    }

    override fun onCleared()
    {
        txHistory.value = listOf()
        super.onCleared()
    }
}

@Composable
fun TransactionsList(modifier: Modifier = Modifier, viewModel: TxHistoryViewModel = viewModel { TxHistoryViewModel() })
{
    val transactions = viewModel.txHistory.collectAsState(emptyList()).value
    val account = wallyApp!!.focusedAccount.collectAsState().value
    if (account != null)
    {
        val balance = account.balanceState.collectAsState().value
        LaunchedEffect(balance) {
            viewModel.getAllTransactions(account)
        }
    }
    else
    {
        viewModel.txHistory.value = listOf()
    }

    if (transactions.isEmpty())
    {
        Spacer(Modifier.height(32.dp))
        CenteredText(i18n(S.NoAccountActivity))
    }

    LazyColumn(
      modifier = modifier
    ) {
        items(transactions) { tx ->
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
