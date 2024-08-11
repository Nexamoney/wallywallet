package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.nexa.libnexakotlin.*
import kotlin.math.roundToLong
import org.nexa.threads.Thread
import org.nexa.threads.iThread
import org.nexa.threads.millisleep

const val OFFER_FAST_FORWARD_GAP = 86400*7  // 1 week in seconds
private val LogIt = GetLog("BU.wally.accountlistview")
private val accountListState:MutableStateFlow<LazyListState?> = MutableStateFlow(null)


@Composable fun AccountListView(nav: ScreenNav, selectedAccount: MutableStateFlow<Account?>,
  modifier: Modifier = Modifier, onAccountSelected: (Account) -> Unit)
{
    val accountUIData = remember { mutableStateMapOf<String,AccountUIData>() }
    val accounts = accountGuiSlots.collectAsState()
    if (accountListState.value == null) accountListState.value = rememberLazyListState()


    LaunchedEffect(true)
    {
        for(c in accountChangedNotification)
        {
            if (c == "*all changed*")  // this is too long to be a valid account name
            {
                wallyApp?.orderedAccounts(true)?.forEach {
                    val uid = it.uiData()
                    accountUIData[it.name] = uid
                }
            }
            else
            {
                val act = wallyApp?.accounts?.get(c)
                if (act != null)
                {
                    accountUIData[c] = act.uiData()
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val tmp = accountListState.collectAsState(scope.coroutineContext).value ?: rememberLazyListState()

    val selAct = selectedAccount.collectAsState().value
    LazyColumn(state=tmp, horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        accounts.value.forEachIndexed { idx, it ->
            item(key=it.name) {
                // I would think that capturing this data would control redraw of each item, but it appears to not do so.
                // Redraw is controlled of the entire AccountListView, or not at all.
                //val anyChanges: MutableState<AccountUIData> = remember { mutableStateOf(it.uiData()) }
                // scope.launch { listState.animateScrollToItem(idx, -1) }  // -1 puts our item more in the center
                if (accountUIData[it.name] == null) accountUIData[it.name] = it.uiData()
                AccountItemView(accountUIData[it.name]!!, idx, selAct == it, devMode,
                  onClickAccount = { onAccountSelected(it) },
                  onClickGearIcon = {
                      nav.go(ScreenId.AccountDetails)
                  })
            }
        }
        // Since the thumb buttons cover the bottom most row, this blank bottom row allows the user to scroll the account list upwards enough to
        // uncover the last account.  Its not necessary if there are just a few accounts though.
        if (accounts.value.size >= 2)
        {
            item(key = "") {
                Spacer(Modifier.height(150.dp))
            }
        }

    }

}


data class AccountUIData(
  val account: Account,
  var name: String = "",
  var chainSelector: ChainSelector = ChainSelector.NEXA,
  var currencyCode: String = "",
  var balance: String = "",
  var balFontWeight: FontWeight = FontWeight.Normal,
  var balColor: Color = colorCredit,
  var unconfBal: String="",
  var unconfBalColor: Color = colorCredit,
  var approximately: String? = null,
  var approximatelyColor: Color = colorPrimaryDark,
  var approximatelyWeight: FontWeight = FontWeight.Normal,
  var devinfo: String="",
  var locked: Boolean = false,
  var lockable: Boolean = false,
  var fastForwarding: Boolean = false,
  var ffStatus: String? = null)

/** Look into this account and produce the strings and modifications needed to display it */
fun Account.uiData():AccountUIData
{
    val ret = AccountUIData(this)

    var delta = unconfirmedBalance

    ret.name = name
    ret.lockable = lockable
    ret.locked = locked
    ret.chainSelector = wallet.chainSelector
    ret.currencyCode = currencyCode
    ret.fastForwarding = (fastforward != null)
    ret.ffStatus = fastforwardStatus
    val chainstate = wallet.chainstate
    var synced = true
    if (chainstate != null)
    {
        synced = chainstate.isSynchronized(1, 60 * 60)
        if (synced)  // ignore 1 block desync or this displays every time a new block is found
        {
            ret.unconfBal =
              if (CURRENCY_ZERO == delta)
                  ""
              else
                  i18n(S.incoming) % mapOf(
                    "delta" to (if (delta > BigDecimal.ZERO) "+" else "") + format(delta),
                    "unit" to currencyCode
                  )

            ret.balFontWeight = FontWeight.Normal
            ret.unconfBalColor = if (delta > BigDecimal.ZERO) colorCredit else colorDebit
        }
        else
        {
        }
    }
    else
    {
        ret.balFontWeight = FontWeight.Light
        ret.unconfBal = i18n(S.walletDisconnectedFromBlockchain)
    }

    ret.balance = format(balance)

    if (!devMode) ret.devinfo = ""
    else
    {
        if (chainstate != null)
        {
            val now = millinow()
            val cnxnLst = chainstate.chain.net.mapConnections()
            {
                val recentRecv = (now - it.lastReceiveTime) < 75L
                val recentSend = (now - it.lastSendTime) < 75L
                val sr = (if (recentSend&&recentRecv) "⇅" else if (recentSend) "↑" else if (recentRecv) "↓" else " ")
                val latencyStr = if (it.bytesReceived + it.bytesSent > 2000) (it.aveLatency.roundToLong().toString() + "ms") else ""
                it.name + " (" + sr + latencyStr + ")"
            }
            val trying:List<String> = if (chainstate.chain.net is MultiNodeCnxnMgr) (chainstate.chain.net as MultiNodeCnxnMgr).initializingCnxns().map { it.name } else listOf()
            val peers = cnxnLst.joinToString(", ") + (if (trying.isNotEmpty()) (" " + i18n(S.trying) + " " + trying.joinToString(", ")) else "")

            ret.devinfo = i18n(S.at) + " " + chainstate.syncedHash.toHex().take(8) + ", " + chainstate.syncedHeight + " " + i18n(S.of) + " " + chainstate.chain.curHeight + " blocks, " + (chainstate.chain.net.size ?: "") + " peers\n" + peers
        }
        else
        {
            ret.devinfo = i18n(S.walletDisconnectedFromBlockchain)
        }
    }

    // Only show the approx fiat amount if synced and we have the conversion data
    if (synced)
    {
        if (fiatPerCoin > BigDecimal.ZERO)
        {
            var fiatDisplay = balance * fiatPerCoin
            ret.approximately = i18n(S.approximatelyT) % mapOf("qty" to FiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode)
            ret.approximatelyColor = colorPrimaryDark
            ret.approximatelyWeight = FontWeight.Normal
        }
        else ret.approximately = null
    }
    else
    {

        ret.approximately = if ((chainstate == null) || (chainstate.syncedDate <= 1231416000)) i18n(S.unsynced)  // for fun: bitcoin genesis block
        else
        {
            val instant = kotlinx.datetime.Instant.fromEpochSeconds(chainstate.syncedDate)
            val localTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

            val td = localTime.format(DATE_TIME_FORMAT)
            i18n(S.balanceOnTheDate) % mapOf("date" to td)
        }

        ret.balFontWeight = FontWeight.Light
        ret.approximatelyColor = unsyncedStatusColor
        ret.approximatelyWeight = FontWeight.Bold
        ret.balColor = unsyncedBalanceColor
    }

    return ret
}

@Composable fun AccountItemLineTop(uidata: AccountUIData, isSelected: Boolean,onClickAccount: () -> Unit, onClickGearIcon: () -> Unit)
{
    val curSync = uidata.account.wallet.chainstate?.syncedDate ?: 0
    val offerFastForward = (millinow()/1000 - curSync) > OFFER_FAST_FORWARD_GAP

    Row(modifier = Modifier.fillMaxWidth())
    {
        // Show blockchain icon
        Column(Modifier.align(Alignment.CenterVertically).padding(0.dp,0.dp,4.dp, 0.dp)) {
            ResImageView(getAccountIconResPath(uidata.chainSelector), Modifier.size(32.dp).align(Alignment.Start), "Blockchain icon")
        }
        // Account name and Nexa amount
        Column(modifier = Modifier.weight(1f)) {
            // Account Name
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = uidata.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            // Nexa Amount
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val startingBalStyle = FontScaleStyle(1.75)
                val startingCcStyle = FontScaleStyle(0.6)
                var balTextStyle by remember { mutableStateOf(startingBalStyle) }
                var ccTextStyle by remember { mutableStateOf(startingCcStyle) }
                var showingCurrencyCode:String by remember { mutableStateOf(uidata.currencyCode) }
                var drawBal by remember { mutableStateOf(false) }
                var drawCC by remember { mutableStateOf(false) }
                var scale by remember { mutableStateOf(1.0) }
                Text(text = uidata.balance, style = balTextStyle, color = uidata.balColor, modifier = Modifier.padding(0.dp).drawWithContent { if (drawBal) drawContent() }, textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
                  onTextLayout = { textLayoutResult ->
                      if (textLayoutResult.didOverflowWidth)
                      {
                          scale = scale * 0.90
                          balTextStyle = startingBalStyle.copy(fontSize = startingBalStyle.fontSize * scale)
                      }
                      else drawBal = true
                  })

                if (showingCurrencyCode.length > 0) Text(text = showingCurrencyCode ?: "", style = ccTextStyle, modifier = Modifier.padding(5.dp, 0.dp).fillMaxWidth().drawWithContent { if (drawCC) drawContent() }, textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
                  onTextLayout = { textLayoutResult ->
                      if (textLayoutResult.didOverflowWidth)
                      {
                          scale = scale * 0.90
                          if (scale > 0.40) // If this field gets too small, just drop it
                          {
                              ccTextStyle = ccTextStyle.copy(fontSize = startingCcStyle.fontSize * scale)
                          }
                          else
                          {
                              showingCurrencyCode = ""
                              drawCC = true
                          }
                      }
                      else drawCC = true
                  }
                )
            }
            // Approximately amount or as of date (we don't want to show a fiat amount if we are syncing)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                uidata.approximately?.let {
                    Text(modifier = Modifier.fillMaxWidth(), text = it, fontSize = 16.sp, color = uidata.approximatelyColor, fontWeight = uidata.approximatelyWeight, textAlign = TextAlign.Start)
                }
            }
        }

        // Account-specific buttons
        Column(modifier = Modifier.align(Alignment.CenterVertically)) {
            Row(
              modifier = Modifier.wrapContentWidth(),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically
            ) {
                val actButtonSize = Modifier.padding(5.dp, 0.dp).size(28.dp)
                // Fast forward button
                if (offerFastForward && !uidata.fastForwarding)
                {
                    ResImageView("icons/fastforward.png", modifier = actButtonSize.clickable {
                        uidata.fastForwarding = true
                        startAccountFastForward(uidata.account) {
                            uidata.account.fastforwardStatus = it
                            triggerAccountsChanged(uidata.account)
                        }
                    })
                }
                // Lock
                if (uidata.lockable)
                {
                    ResImageView(if (uidata.locked) "icons/lock.xml" else "icons/unlock.xml",
                      modifier = actButtonSize.clickable {
                          onClickAccount()  // I want to select the whole account & then try to unlock/lock
                          if (uidata.locked)
                          {
                              triggerUnlockDialog()
                          }
                          else
                          {
                              uidata.account.pinEntered = false
                              tlater("assignGuiSlots") {
                                  triggerAssignAccountsGuiSlots()  // In case it should be hidden
                                  later { accountChangedNotification.send(uidata.name) }
                              }
                          }
                      })
                }
                // else Spacer(actButtonSize) // these would stop the buttons from moving when other buttons disappear

                // Show the account settings gear at the end
                if (isSelected)
                {
                    ResImageView("icons/gear.xml", actButtonSize.clickable(onClick = onClickGearIcon).testTag("accountSettingsGearIcon"))
                }
                // else Spacer(actButtonSize) // these would stop the buttons from moving when other buttons disappear
            }
        }

    }

}

@Composable
fun AccountItemView(
  uidata: AccountUIData,
  index: Int,
  isSelected: Boolean,
  devMode: Boolean,
  onClickAccount: () -> Unit,
  onClickGearIcon: () -> Unit
) {
    val backgroundColor = if (isSelected) defaultListHighlight else if (index and 1 == 0) WallyRowAbkg1 else WallyRowAbkg2
    Box(modifier = Modifier.testTag("AccountItemView").fillMaxWidth().padding(2.dp).background(backgroundColor).clickable(onClick = onClickAccount), contentAlignment = Alignment.Center) {
        // Each account
        Row(modifier = Modifier.padding(5.dp, 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

            Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                // top line, icon, quantity, and fastforward
                Row(modifier = Modifier.fillMaxWidth()) {
                    AccountItemLineTop(uidata, isSelected, onClickAccount, onClickGearIcon)
                }

                // Fast Forwarding status
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    val ffs = uidata.ffStatus
                    if (uidata.fastForwarding && (ffs != null))
                    {
                        Text(modifier = Modifier.fillMaxWidth(), text = i18n(S.fastforwardStatus) % mapOf("info" to ffs), fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }

                // includes (amount)   --- NEXA pending amount
                if (uidata.unconfBal.isNotEmpty()) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Text(text = uidata.unconfBal, color = uidata.unconfBalColor)
                }
                // Devmode connectivity text
                if (devMode)
                {
                    // Give a little extra height because the unicode up and down arrows don't fit causing the line to go bigger.
                    var lh = LocalTextStyle.current.lineHeight
                    if (lh == TextUnit.Unspecified) lh = defaultFontSize
                    val devModeTextStyle = LocalTextStyle.current.copy(lineHeightStyle = LineHeightStyle(
                      alignment = LineHeightStyle.Alignment.Proportional,
                      trim = LineHeightStyle.Trim.None),
                      lineHeight = lh.times(1.05)
                      )
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp,4.dp,4.dp, 4.dp), horizontalArrangement = Arrangement.Start) {
                        Text(text = uidata.devinfo, fontSize = 12.sp, maxLines = 5, minLines = 3, style = devModeTextStyle, textAlign = TextAlign.Center)
                    }
                }

            }
        }

    }
}

private fun getAccountIconResPath(chainSelector: ChainSelector): String
{
    return when(chainSelector)
    {
        ChainSelector.NEXA -> "icons/nexa_icon.png"
        ChainSelector.NEXATESTNET -> "icons/nexatest_icon.png"
        ChainSelector.NEXAREGTEST -> "icons/nexareg_icon.png"
        ChainSelector.BCH -> "icons/bitcoin_cash_token.xml"
        ChainSelector.BCHTESTNET -> "icons/bitcoin_cash_token.xml"
        ChainSelector.BCHREGTEST -> "icons/bitcoin_cash_token.xml"
    }
}


data class DerivationPathSearchProgress(var aborter: Objectify<Boolean>,var progress: String?, var progressInt: Int, var results:AccountSearchResults? = null)


fun derivationPathSearch(progress: DerivationPathSearchProgress, wallet: Bip44Wallet, coin: Long, account: Long, change: Boolean, idxMaxGap: Int, start: Long = 0, event: (()->Unit)? = null): iThread
{
    val cnxn = wallet.blockchain.net
    val secret = wallet.secret

    return Thread("ff_${wallet.name}")
    {
        var ec: ElectrumClient? = null
        fun getEc():ElectrumClient {
            return retry(10) {
                val tmp = ec
                if (tmp != null && tmp.open) ec
                else
                {
                    progress.progress = i18n(S.trying)
                    ec = cnxn.getElectrum()
                    if (ec == null)
                    {
                        progress.progress = i18n(S.NoNodes)
                        event?.invoke()
                        millisleep(200U)
                    }
                    ec
                }
            }
        }
        progress.results = try
        {
            searchDerivationPathActivity(::getEc, wallet.chainSelector, idxMaxGap, {
                if (progress.aborter.obj) throw EarlyExitException()
                val key = libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, coin, account, change, it).first
                val us = UnsecuredSecret(key)
                val dest = Pay2PubKeyTemplateDestination(wallet.chainSelector, us, it.toLong())
                progress.progress = ""
                progress.progressInt = it
                event?.invoke()
                dest
            },
              {
                  //summaryText = "\n" + i18n(S.discoveredAccountDetails) % mapOf("tx" to it.txh.size.toString(), "addr" to it.addrCount.toString(),
                  //  "bal" to NexaFormat.format(fromFinestUnit(it.balance, chainSelector = chainSelector)), "units" to (chainToDisplayCurrencyCode[chainSelector] ?:""))
                  // displayFastForwardInfo(i18n(S.NewAccountSearchingForAllTransactions) + addrText + summaryText)
                  event?.invoke()
              }
            )
        }
        catch (e: EarlyExitException)
        {
            null
        }
        catch (e: Exception)
        {
            displayUnexpectedException(e)
            null
        }
    }
}

fun startAccountFastForward(account: Account, displayFastForwardInfo: (String?) -> Unit)
{
    if (account.fastforward != null)
    {
        displayNotice(i18n(S.inProgress))
        return
    }
    val wallet = account.wallet
    // val passphrase = "" // TODO: support a passphrase
    val addressDerivationCoin = Bip44AddressDerivationByChain(wallet.chainSelector)

    var aborter = Objectify<Boolean>(false)
    account.fastforward = aborter
    displayFastForwardInfo(i18n(S.fastforwardStart))

    Thread("fastforward_${wallet.name}")
    {
        var normal: DerivationPathSearchProgress = DerivationPathSearchProgress(aborter, null, 0, null)
        var change: DerivationPathSearchProgress = DerivationPathSearchProgress(aborter, null, 0, null)

        // This code basically assumes that the contacted Rostrum nodes are synced with each other (which basically means on the tip)
        // otherwise you could get into a situation where some Rostrum connection says no activity on address X, but its really
        // reporting that for blocks 0-N whereas another request reports for blocks 0-N+10.  And so N+10 is used as the synced height.
        val t1 = derivationPathSearch(normal, wallet, addressDerivationCoin, 0, false, WALLET_FULL_RECOVERY_DERIVATION_PATH_MAX_GAP) {
            // displayFastForwardInfo((normal.progressInt + change.progressInt).toString() + " " + (normal.progress ?: "") + " " + (change.progress ?: ""))
            displayFastForwardInfo(normal.progressInt.toString() + " " + (normal.progress ?: ""))
        }
        t1.join()
        /* skip searching the change for speed */
        val t2 = derivationPathSearch(change, wallet, addressDerivationCoin, 0, true, WALLET_FULL_RECOVERY_CHANGE_DERIVATION_PATH_MAX_GAP) {
            displayFastForwardInfo((normal.progressInt + change.progressInt).toString() + " " + (normal.progress ?: "") + " " + (change.progress ?: ""))
        }
        t2.join()

        normal.results?.let {
            var lastHeight = it.lastHeight
            var lastDate = it.lastDate
            var lastHash = it.lastHash
            val ch: AccountSearchResults? = change.results
            var txh = it.txh

            if (ch!=null)
            {
                if (ch.lastHeight > it.lastHeight)
                {
                    lastHeight = ch.lastHeight
                    lastDate = ch.lastDate
                    lastHash = ch.lastHash
                }
                txh = it.txh + ch.txh
            }
            wallet.generateAddressesUntil(it.lastAddressIndex)
            wallet.fastforward(lastHeight, lastDate, lastHash, txh)
        }
        triggerAssetCheck()
        displayFastForwardInfo(null)
        account.fastforward = null
        triggerAccountsChanged(account)
    }
}