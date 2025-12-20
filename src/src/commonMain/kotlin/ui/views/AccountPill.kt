package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.SyncViewModel
import info.bitcoinunlimited.www.wally.ui.SyncViewModelImpl
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurple
import info.bitcoinunlimited.www.wally.ui.theme.wallyTileHeader
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.FiatFormat
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.millinow
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

private val LogIt = GetLog("wally.actpill")
/*
    Root class for BalanceViewModel used for testing
 */
abstract class BalanceViewModel(val dispatcher: CoroutineDispatcher = Dispatchers.Main): ViewModel()
{
    abstract @Composable fun cBalanceString(): String
    abstract fun balanceString(): String

    abstract @Composable fun cFiatBalance():String
    abstract fun fiatBalance():String

    // Set which account's balance we are tracking
    abstract fun setAccount(act: Account)

    // abstract fun setFiatBalance()
    abstract fun observeBalance()
    abstract fun observeSelectedAccount()
}

class BalanceViewModelFake: BalanceViewModel()
{
    override @Composable fun cBalanceString(): String = "fake balance"
    override fun balanceString(): String = "fake balance"
    override @Composable fun cFiatBalance():String = "fake fiat bal"
    override fun fiatBalance():String = "fake fiat bal"

    override fun setAccount(act: Account) {}
    override fun observeBalance() {}
    override fun observeSelectedAccount() {}
}


class BalanceViewModelImpl(val account : MutableStateFlow<Account?>): BalanceViewModel()
{
    constructor(act: Account?) : this(MutableStateFlow(act))
    var balanceJob: Job? = null
    var accountJob: Job? = null

    override @Composable fun cBalanceString(): String
    {
        val act = account.collectAsState().value
        if (act == null) return i18n(S.loading)
        val bal = act.balanceState.collectAsState()
        val bg = bal.value ?: return i18n(S.loading)
        return act.cryptoFormat.format(bg)
    }


    override fun balanceString(): String
    {
        val act = account.value
        if (act == null) return i18n(S.loading)
        val bal = act.balanceState
        val bg = bal.value ?: return i18n(S.loading)
        return act.cryptoFormat.format(bg)
    }

    init {
        account.value?.let { act ->
            observeBalance()
        }
    }

    override fun setAccount(act: Account)
    {
        onCleared()
        account.value = act
        observeBalance()
    }

    override @Composable fun cFiatBalance():String
    {
        val act = account.collectAsState().value
        if (act == null) return ""

        val bal = act.balanceState.collectAsState()
        val qty = bal.value ?: return i18n(S.loading)

        val fpc = act.fiatPerCoinObservable.collectAsState().value
        val fiatDisplay = qty * fpc
        val ret = if (fpc <= 0) // Usd value is not fetched
            ""
        else
            FiatFormat.format(fiatDisplay)
        return ret
    }
    override fun fiatBalance():String
    {
        val act = account.value
        if (act == null) return ""

        val bal = act.balanceState
        val qty = bal.value ?: return i18n(S.loading)

        val fpc = act.fiatPerCoinObservable.value
        val fiatDisplay = qty * fpc
        val ret = if (fpc <= 0) // Usd value is not fetched
            ""
        else
            FiatFormat.format(fiatDisplay)
        return ret
    }

    override fun observeSelectedAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch(dispatcher) {
            wallyApp!!.focusedAccount.onEach {
                it?.let { account ->
                    setAccount(it)
                }
            }.launchIn(this)
        }
    }

    override fun observeBalance()
    {
        balanceJob?.cancel()
        balanceJob = viewModelScope.launch(dispatcher) {
            account.value?.balanceState?.onEach {
                // setFiatBalance()
            }?.launchIn(this)
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

    // The account choices to show in the pill.  If null, allow swiping through all visible accounts
    var choices:List<Account>? = null
        set(v)
        {
            field = v
            if ((v!=null)&&(account.value == null)&&(v.isNotEmpty())) this.setAccount(v.first())
        }

    @Composable
    fun AccountPillHeader(act: Account?)
    {
        val bal = if (act == account.collectAsState().value) balance else BalanceViewModelImpl(act)
        val currencyCode = act?.uiData()?.currencyCode ?: ""
        // If no account is available, do not show the pill
        if (act == null) return

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
                  text = bal.cBalanceString(),
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
            val fiatBalance = bal.cFiatBalance()
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
              text = act.name,
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
    protected fun renderPill(act: Account?, animatedOffset:Float, buttonsEnabled: Boolean = true)
    {
        val curSync = act?.wallet?.chainstate?.syncedDate ?: 0
        val offerFastForward = (millinow() / 1000 - curSync) > OFFER_FAST_FORWARD_GAP
        val uiData = act?.uiData()
        val isFastForwarding = uiData?.fastForwarding ?: false

        val roundedCorner = 16.dp
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
              modifier = Modifier.offset { IntOffset(animatedOffset.roundToInt(), 0) }
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
                AccountPillHeader(act)
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

                        IconTextButton(
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
                        IconTextButton(
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
                        IconTextButton(
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
                        IconTextButton(
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
                            IconTextButton(
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

    fun nextAct(dir: Float, cur: Account?, actLst: List<Account>?): Account?
    {
        actLst?.let {
            if (actLst.isNotEmpty())
            {
                val myIdx = actLst.indexOf(cur)
                if (myIdx != -1)
                {
                    val newIdx = (myIdx + (if (dir > 0) 1 else if (dir<0) -1 else 0)).mod(actLst.size)
                    // LogIt.info("nextAct() index: $newIdx act: ${actLst[newIdx].name}")
                    return actLst[newIdx]
                }
                else
                {
                    // LogIt.info("nextAct() current account does not exist in the list")
                    return actLst[0]
                }
            }
            // LogIt.info("nextAct() list is empty")
        }
        // LogIt.info("nextAct() list is null")
        return account.value
    }

    @Composable
    fun draw(buttonsEnabled: Boolean = true)
    {
        val ANI_DUR = 300
        val act = account.collectAsState().value
        var boxSize by remember { mutableStateOf(IntSize.Zero) }
        val scope = rememberCoroutineScope()

        val tw = tween<Float>(ANI_DUR.toInt(), easing = LinearOutSlowInEasing)
        val animatedOffset = remember { Animatable(0f) }

        var dupSide by remember { mutableStateOf(0f)}

        var actLst = choices ?: wallyApp?.orderedAccounts(true)?.toList()

        Box(modifier = Modifier.fillMaxWidth().onSizeChanged { boxSize = it }.pointerInput(Unit) {
            var dragAmount = 0f
            val velocityTracker = VelocityTracker()
            detectHorizontalDragGestures(
              onDragStart = { dragAmount = 0f; scope.launch { animatedOffset.stop()} },
              onDragEnd = {
                  var changed = false
                  LogIt.info("velocity ${velocityTracker.calculateVelocity()}")
                  if ((dragAmount.absoluteValue >= boxSize.width / 5)||(velocityTracker.calculateVelocity().x.absoluteValue>1000))  // dragged far enough or quickly enough
                  {
                      wallyApp?.let {
                          if (account.value == null)  // how would this happen if the pill is showing, but anyway set to first or last depending on swipe dir.
                          {
                              if (actLst?.isNotEmpty() == true)
                              {
                                  //setAccount(if (dragAmount > 0) actLst.first() else actLst.last())
                                  changed = true
                              }
                          }
                          else
                          {
                              changed = true
                          }
                      }
                  }
                  if (!changed) // let it relax back
                  {
                      scope.launch {
                            animatedOffset.animateTo(0f)
                      }
                  }
                  else  // switching
                  {
                      val velocity = velocityTracker.calculateVelocity().x
                      scope.launch {
                          // Animate the box entirely off the screen, pulling the other one into the center
                          animatedOffset.animateTo(boxSize.width.toFloat() * dragAmount.sign, tw, velocity)
                          // reload the current list of accounts whenever I finish a drag
                          actLst = choices ?: wallyApp?.orderedAccounts(true)?.toList()
                          // go to the next account
                          val nextAccount = nextAct(dupSide, account.value, actLst)
                          setAccount(nextAccount)
                          // Now snap the original box right on top of the other one, resetting the position
                          animatedOffset.snapTo(0f)
                          dupSide = 0f
                      }
                  }
              },
              onHorizontalDrag = { change, delta ->
                  change.consume() // mark gesture as handled
                  dragAmount += delta
                  if (dragAmount < 0) dupSide = 1f else dupSide = -1f
                  velocityTracker.addPosition(change.uptimeMillis, change.position)
                  scope.launch {
                      animatedOffset.snapTo(dragAmount) // drag directly
                  }
              }
            )
        }) {
            // Renders the main pill
            renderPill(act, animatedOffset.value, buttonsEnabled)
            // Renders the pill next to the main one during a drag
            if (dupSide != 0f) renderPill(nextAct(dupSide, account.collectAsState().value, actLst), animatedOffset.value + (dupSide * boxSize.width), buttonsEnabled)
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

    constructor(actChoices: List<Account>):this(MutableStateFlow(actChoices.firstOrNull()))
    {
        choices = actChoices
    }

    override val balance = BalanceViewModelImpl(account.value)
    override val sync = SyncViewModelImpl()

    override fun setAccount(act: Account?)
    {
        if (choices?.contains(act) == false)
        {
            LogIt.info("setting pill to an out-of-bounds account")
        }
        // LogIt.info("AccountPill setAccount: $account set to ${act?.name}")
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
