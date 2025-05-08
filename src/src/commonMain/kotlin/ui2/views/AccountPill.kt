package info.bitcoinunlimited.www.wally.ui2.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.theme.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.theme.wallyTileHeader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.FiatFormat
import org.nexa.libnexakotlin.laterJob
import org.nexa.libnexakotlin.millinow

/*
    Root class for BalanceViewModel used for testing
 */
abstract class BalanceViewModel(val dispatcher: CoroutineDispatcher = Dispatchers.Main): ViewModel()
{
    open val balance = MutableStateFlow(i18n(S.loading))
    open val fiatBalance = MutableStateFlow(i18n(S.loading))

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


abstract class AccountPillViewModel(val account: MutableStateFlow<Account?>, val dispatcher: CoroutineDispatcher = Dispatchers.Main): ViewModel()
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
        if (act == null) return

        // Runs the callback every time account?.fiatPerCoin changes
        LaunchedEffect(act.fiatPerCoin) {
            balance.setFiatBalance(act)
        }

        Row(
          modifier = Modifier.wrapContentHeight()
        ) {
            FittedText(2, 4.sp, wallyTileHeader()) { mod, ts, onTlr ->
                Text(
                  text = currencyCode,
                  style = ts,
                  textAlign = TextAlign.Center,
                  modifier = mod.testTag("AccountPillCurrencyCode"),
                  onTextLayout = onTlr
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = bal,
                  style = ts,
                  textAlign = TextAlign.Center,
                  modifier = mod.testTag("AccountPillBalance"),
                  onTextLayout = onTlr
                )
            }
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
              text = act.name ?: "",
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
                  horizontal = 4.dp,
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
                      horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        val verticalDividerModifier = Modifier
                          .width(1.dp)
                          .height(40.dp)
                          .padding(vertical = 2.dp)
                          .align(Alignment.CenterVertically)

                        IconTextButtonUi2(
                          icon = Icons.Outlined.ArrowUpward,
                          modifier = Modifier.weight(1f).testTag("SendButton"),
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
                          modifier = Modifier.weight(1f).testTag("ReceiveButton"),
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
                          modifier = Modifier.weight(1f).testTag("SplitBillButton"),
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
                          modifier = Modifier.weight(1f).testTag("AccountButton"),
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

class AccountPillViewModelFake(account: MutableStateFlow<Account?>, override val balance: BalanceViewModel = BalanceViewModelImpl(account), override val sync: SyncViewModel = SyncViewModelImpl()): AccountPillViewModel(account)
{
    override fun setAccount(act: Account?) {}
}

class AccountPill(account: MutableStateFlow<Account?>): AccountPillViewModel(account)
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
