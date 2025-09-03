package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import org.nexa.libnexakotlin.SearchDerivationPathActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.nexa.libnexakotlin.*
import org.nexa.libnexakotlin.AccountSearchResults
import org.nexa.libnexakotlin.EarlyExitException
import org.nexa.threads.Thread
import org.nexa.threads.iThread
import org.nexa.threads.millisleep
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val LogIt = GetLog("BU.wally.AccountListView")

data class AccountUIData(
  val account: Account, // Do not copy unchangeable fields from the account into this data structure, just use them from here
  var currencyCode: String = "",
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
  var ffStatus: String? = null,
  var recentHistory: List<TransactionHistory> = listOf())

/** Look into this account and produce the strings and modifications needed to display it */
@OptIn(ExperimentalTime::class)
fun Account.uiData(): AccountUIData
{
    val ret = AccountUIData(this)
    val delta = unconfirmedBalance.value
    ret.lockable = lockable
    ret.locked = locked
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
              if ((CURRENCY_ZERO == delta)||(null == delta))
              {
                  ret.unconfBalColor = colorCredit
                  ""
              }
              else
              {
                  ret.unconfBalColor = if (delta > BigDecimal.ZERO) colorCredit else colorDebit
                  i18n(S.incoming) % mapOf(
                    "delta" to (if (delta > BigDecimal.ZERO) "+" else "") + format(delta),
                    "unit" to currencyCode
                  )
              }
            ret.balFontWeight = FontWeight.Normal
        }
        else
        {
            // If unsynced we don't want to show unconfirmed balances separately since they could actually be confirmed
        }
    }
    else
    {
        ret.balFontWeight = FontWeight.Light
        ret.unconfBal = i18n(S.walletDisconnectedFromBlockchain)
    }

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
        val b = balance
        if ((fiatPerCoin > BigDecimal.ZERO)&&(b != null))
        {
            val fiatDisplay = b * fiatPerCoin
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
            val instant = Instant.fromEpochSeconds(chainstate.syncedDate)
            val localTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

            val td = localTime.format(DATE_TIME_FORMAT)
            i18n(S.balanceOnTheDate) % mapOf("date" to td)
        }

        ret.balFontWeight = FontWeight.Light
        ret.approximatelyColor = unsyncedStatusColor
        ret.approximatelyWeight = FontWeight.Bold
        ret.balColor = unsyncedBalanceColor
    }

    // Reload transaction history outside of the UI processing thread.
    laterJob {
        val txh = mutableListOf<TransactionHistory>()
        /* This code puts a fake tx at the top that keeps updating based on the current time
       so you can see how often this is regenerating.
    val fakeTx = txFor(wallet.chainSelector)
    val fakeHistory = TransactionHistory(wallet.chainSelector, fakeTx)
    fakeHistory.date = millinow()
    fakeHistory.outgoingAmt=0
    fakeHistory.incomingAmt=millinow()
    txh.add(fakeHistory)
    */
        wallet.forEachTxByDate {
            txh.add(it)
            (txh.size >= 10) // just get the most recent 10
        }
        ret.recentHistory = txh.sortedByDescending { it.date }
    }
    return ret
}

open class AccountUiDataViewModel: ViewModel()
{
    val accountUIData: MutableStateFlow<Map<String, AccountUIData>> = MutableStateFlow(mapOf())

    open fun setup()
    {
        viewModelScope.launch {
            for(c in accountChangedNotification)
            {
                if (c == "*all changed*")  // this is too long to be a valid account name
                {
                    wallyApp?.orderedAccounts(true)?.forEach { account ->
                        setAccountUiDataForAccount(account)
                    }
                }
                else
                {
                    val act = wallyApp?.accounts?.get(c)
                    if (act != null)
                    {
                        accountUIData.update {
                            val updatedMap = it.toMutableMap()
                            updatedMap[c] = act.uiData()
                            updatedMap.toMap()
                        }
                    }
                }
            }
        }
    }

    open fun setAccountUiDataForAccount(account: Account)
    {
        // Updates the MutableStateFlow.value atomically
        accountUIData.update {
            val updatedMap = it.toMutableMap()
            updatedMap[account.name] = account.uiData()
            updatedMap.toMap()
        }
    }

    // This should probably be moved to a viewModel with only one account
    open fun fastForwardSelectedAccount()
    {
        wallyApp!!.focusedAccount.value?.let { selectedAccount ->
            val allAccountsUiData = accountUIData.value.toMutableMap()
            val uiData = allAccountsUiData[selectedAccount.name] ?: AccountUIData(selectedAccount)
            uiData.fastForwarding = true
            allAccountsUiData[selectedAccount.name] = uiData
            accountUIData.value = allAccountsUiData

            startAccountFastForward(selectedAccount) {
                val tmp = accountUIData.value.toMutableMap()
                val uiDatatmp = allAccountsUiData[selectedAccount.name] ?: AccountUIData(selectedAccount)
                uiDatatmp.fastForwarding = it != null
                tmp[selectedAccount.name] = uiData
                accountUIData.value = tmp

                uiData.account.fastforwardStatus = it
                triggerAccountsChanged(uiData.account)
            }
        }
    }
}

// This should probably be moved to a viewModel with only one account
fun fastForwardAccount(act: Account)
{
    val t = AccountUiDataViewModel()  // TODO USE A SINGLETON
    val allAccountsUiData = t.accountUIData.value.toMutableMap()
    val uiData = allAccountsUiData[act.name] ?: AccountUIData(act)
    uiData.fastForwarding = true
    allAccountsUiData[act.name] = uiData
    t.accountUIData.value = allAccountsUiData

    startAccountFastForward(act) {
        val tmp = t.accountUIData.value.toMutableMap()
        val uiDatatmp = allAccountsUiData[act.name] ?: AccountUIData(act)
        uiDatatmp.fastForwarding = it != null
        tmp[act.name] = uiData
        t.accountUIData.value = tmp
        uiData.account.fastforwardStatus = it
        triggerAccountsChanged(uiData.account)
    }
}

class AccountUiDataViewModelFake: AccountUiDataViewModel()
{
    override fun setup()
    {

    }

    override fun setAccountUiDataForAccount(account: Account)
    {

    }

    override fun fastForwardSelectedAccount()
    {

    }
}

@Composable fun AccountListView(
    nav: ScreenNav,
    accountUIData: Map<String, AccountUIData>,
    accounts: ListifyMap<String, Account>
)
{
    val selAct = wallyApp!!.focusedAccount.collectAsState().value

    Column (
      modifier = Modifier.wrapContentHeight()
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
        ,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        accounts.forEachIndexed { idx, it ->
            val backgroundColor = if (selAct == it) wallyPurpleLight else wallyPurpleExtraLight
            accountUIData[it.name]?.let {  uiData ->
                AccountItemView(uiData, idx, selAct == it, devMode, backgroundColor, hasFastForwardButton = false,
                    onClickAccount = {
                        setSelectedAccount(it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(0.8f).wrapContentHeight(), contentAlignment = Alignment.Center) {
            Button(modifier = Modifier.wrapContentSize().testTag("AddAccount"),
              colors = ButtonDefaults.buttonColors().copy(contentColor = Color.Black, containerColor = Color.White),
              onClick = {
                  clearAlerts()
                  nav.go(ScreenId.NewAccount)
              }) {
                Text(i18n(S.addAccountPlus))
            }
        }

        // Since the thumb buttons cover the bottom most row, this blank bottom row allows the user to scroll the account list upwards enough to
        // uncover the last account.  Its not necessary if there are just a few accounts though.
        if (accounts.size >= 2)
            Spacer(Modifier.height(144.dp).testTag("AccountListBottomSpace"))
    }
}


@Composable
fun AccountListItem(
  uidata: AccountUIData,
  hasFastForwardButton: Boolean = true,
  isSelected: Boolean,
  backgroundColor: Color,
  onClickAccount: () -> Unit
) {
    val curSync = uidata.account.wallet.chainstate?.syncedDate ?: 0
    val offerFastForward = (millinow() /1000 - curSync) > OFFER_FAST_FORWARD_GAP
    ListItem(
      colors = ListItemDefaults.colors(containerColor = backgroundColor),
      modifier = Modifier.fillMaxWidth(),
      leadingContent = {
          // Show blockchain icon
          ResImageView(getAccountIconResPath(uidata.account.wallet.chainSelector), Modifier.size(32.dp), "Blockchain icon")
      },
      headlineContent = {
          // Account name and Nexa amount
          Column {
              // Account Name
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                  Text(text = uidata.account.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("CarouselAccountName"))
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
                  val mod = Modifier.padding(0.dp).drawWithContent { if (drawBal and drawCC) drawContent() }.testTag("AccountCarouselBalance_${uidata.account.name}")
                  val balance = uidata.account.balanceState.collectAsState().value
                  val balString = if (balance != null) uidata.account.cryptoFormat.format(balance) else i18n(S.loading)

                  Text(text = balString, style = balTextStyle, color = uidata.balColor, modifier = mod, textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.didOverflowWidth)
                        {
                            scale = scale * 0.97
                            if (scale > 0.40) // If this field gets too small, just drop the currency code
                            {
                                balTextStyle = startingBalStyle.copy(fontSize = startingBalStyle.fontSize * scale)
                                ccTextStyle = ccTextStyle.copy(fontSize = startingCcStyle.fontSize * scale)
                            }
                            else
                            {
                                showingCurrencyCode = ""
                                drawCC = true
                            }
                            // LogIt.info("Scale is $scale (num)")
                        }
                        else drawBal = true
                    })

                  if (showingCurrencyCode.length > 0) Text(text = showingCurrencyCode ?: "", style = ccTextStyle, modifier = Modifier.padding(5.dp, 0.dp).fillMaxWidth().drawWithContent { if (drawBal and drawCC) drawContent() }, textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.didOverflowWidth)
                        {
                            scale = scale * 0.97
                            if (scale > 0.50) // If this field gets too small, just drop the currency code
                            {
                                ccTextStyle = ccTextStyle.copy(fontSize = startingCcStyle.fontSize * scale)
                                balTextStyle = startingBalStyle.copy(fontSize = startingBalStyle.fontSize * scale)
                            }
                            else
                            {
                                showingCurrencyCode = ""
                                drawCC = true
                            }
                            // LogIt.info("Scale is $scale (currency code)")
                        }
                        else drawCC = true
                    }
                  )
              }

              if (uidata.fastForwarding)
              {
                // Fast Forwarding status
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    val ffs = uidata.account.fastForwardStatusState.collectAsState().value
                    if (uidata.fastForwarding && (ffs != null))
                    {
                        Text(modifier = Modifier.fillMaxWidth(), text = i18n(S.fastforwardStatus) % mapOf("info" to ffs), fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }
              }
          }
      },
      trailingContent = {
          // Account-specific buttons
          Column {
              Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
              ) {
                  val actButtonSize = Modifier.padding(5.dp, 0.dp).size(28.dp)

                  // Account settings gear
                  if (isSelected)
                  {
                      IconButton(
                        onClick = { nav.go(ScreenId.AccountDetails) },
                        modifier=Modifier.testTag("AccountDetailsButton"),
                        content = {
                            Icon(Icons.Outlined.ManageAccounts, contentDescription = "Account detail")
                        }
                      )
                  }

                  // Fast forward button
                  if (offerFastForward && !uidata.fastForwarding && hasFastForwardButton)
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
                      if (uidata.locked)
                          IconButton(
                            onClick = {
                                onClickAccount()
                                triggerUnlockDialog()
                            },modifier = Modifier.testTag("LockIcon(${uidata.account.name})")
                          ) {
                              Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                              )
                          }
                      else
                          IconButton(
                            onClick = {
                                uidata.account.pinEntered = false
                                onClickAccount()
                                tlater("assignGuiSlots") {
                                    triggerAssignAccountsGuiSlots()  // In case it should be hidden
                                    triggerAccountsChanged(uidata.account)
                                }
                            }, modifier = Modifier.testTag("UnlockIcon(${uidata.account.name})")
                          ) {
                              Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Locked",
                              )
                          }
                  }
              }
          }
      }
    )
}

@Composable
fun AccountItemView(
    uidata: AccountUIData,
    index: Int,
    isSelected: Boolean,
    devMode: Boolean,
    backgroundColor: Color,
    hasFastForwardButton: Boolean,
    onClickAccount: () -> Unit
) {
        Row(
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp).fillMaxWidth().testTag("AccountItemView").clickable(onClick = onClickAccount),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(2f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(backgroundColor),
              verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                AccountListItem(uidata, hasFastForwardButton, isSelected, backgroundColor, onClickAccount)

                if (!uidata.fastForwarding)
                {
                    // Approximately amount or as of date (we don't want to show a fiat amount if we are syncing)
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp,0.dp,4.dp, 0.dp), horizontalArrangement = Arrangement.Center) {
                        uidata.approximately?.let {
                            Text(modifier = Modifier.fillMaxWidth(), text = it, fontSize = 16.sp, color = uidata.approximatelyColor, fontWeight = uidata.approximatelyWeight, textAlign = TextAlign.Center)
                        }
                    }
                    // includes (amount)   --- NEXA pending amount
                    if (uidata.unconfBal.isNotEmpty()) Row(modifier = Modifier.fillMaxWidth().padding(4.dp,0.dp,4.dp, 0.dp), horizontalArrangement = Arrangement.Center) {
                        Text(text = uidata.unconfBal, color = uidata.unconfBalColor, textAlign = TextAlign.Center)
                    }
                }

                /*
                    Devmode connectivity text.
                    Don't occupy space with row .padding if the text is empty.
                 */
                if (devMode && uidata.devinfo.isNotBlank())
                {
                    // Give a little extra height because the unicode up and down arrows don't fit causing the line to go bigger.
                    val devModeTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.times(0.90),
                      lineHeight = MaterialTheme.typography.bodySmall.fontSize.times(0.91))
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp,2.dp,4.dp, 2.dp), horizontalArrangement = Arrangement.Start) {
                        Text(text = uidata.devinfo, maxLines = 5, minLines = 3, style = devModeTextStyle, textAlign = TextAlign.Center)
                    }
                }
            }
        }
}

data class DerivationPathSearchProgress(var aborter: Objectify<Boolean>,var progress: String?, var progressInt: Int, var results: AccountSearchResults? = null)

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
                    progress.progress = i18n(S.NoNodes)
                    event?.invoke()
                    millisleep(200U)
                    ec
                }
            }
        }
        progress.results = try
        {
            SearchDerivationPathActivity(wallet.chainSelector, ::getEc, false) {
                event?.invoke()
            }.search(idxMaxGap) {
                if (progress.aborter.obj) throw EarlyExitException()
                val key = libnexa.deriveHd44ChildKey(secret.getSecret(), AddressDerivationKey.BIP44, coin, account, change, it).first
                val us = UnsecuredSecret(key)
                val dest = Pay2PubKeyTemplateDestination(wallet.chainSelector, us, it.toLong())
                progress.progress = ""
                progress.progressInt = it
                event?.invoke()
                dest
              }
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

    val aborter = Objectify<Boolean>(false)
    account.fastforward = aborter
    displayFastForwardInfo(i18n(S.fastforwardStart))

    Thread("fastforward_${wallet.name}")
    {
        val normal: DerivationPathSearchProgress = DerivationPathSearchProgress(aborter, null, 0, null)
        val change: DerivationPathSearchProgress = DerivationPathSearchProgress(aborter, null, 0, null)

        // This code basically assumes that the contacted Rostrum nodes are synced with each other (which basically means on the tip)
        // otherwise you could get into a situation where some Rostrum connection says no activity on address X, but its really
        // reporting that for blocks 0-N whereas another request reports for blocks 0-N+10.  And so N+10 is used as the synced height.
        val t1 = derivationPathSearch(normal, wallet, addressDerivationCoin, 0, false, WALLET_FULL_RECOVERY_DERIVATION_PATH_MAX_GAP) {
            // displayFastForwardInfo((normal.progressInt + change.progressInt).toString() + " " + (normal.progress ?: "") + " " + (change.progress ?: ""))
            displayFastForwardInfo(normal.progressInt.toString() + " " + (normal.progress ?: ""))
        }
        /* skip searching the change for speed */
        val t2 = derivationPathSearch(change, wallet, addressDerivationCoin, 0, true, WALLET_FULL_RECOVERY_CHANGE_DERIVATION_PATH_MAX_GAP) {
            displayFastForwardInfo((normal.progressInt + change.progressInt).toString() + " " + (normal.progress ?: "") + " " + (change.progress ?: ""))
        }
        t1.join()
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
                txh.putAll(ch.txh)
            }
            wallet.generateAddressesUntil(it.lastAddressIndex)
            wallet.fastForward(lastHeight, lastDate, lastHash, txh.values.toList())
            wallet.save(true)
        }
        triggerAssetCheck()
        displayFastForwardInfo(null)
        account.fastforward = null
        triggerAccountsChanged(account)
    }
}
