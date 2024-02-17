package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.accountlistview")


private val accountListState:MutableStateFlow<LazyListState?> = MutableStateFlow(null)

@Composable fun AccountListView(nav: ScreenNav, selectedAccount: MutableStateFlow<Account?>, accounts: ListifyMap<String,Account>,
  modifier: Modifier = Modifier, onAccountSelected: (Account) -> Unit)
{
    val accountUIData = remember { mutableStateMapOf<String,AccountUIData>() }
    if (accountListState.value == null) accountListState.value = rememberLazyListState()

    LaunchedEffect(true)
        {
            for(c in accountChangedNotification)
            {
                if (c == "*all changed*")  // this is too long to be a valid account name
                {
                    wallyApp?.accounts?.forEach {
                        val uid = it.value.uiData()
                        accountUIData[it.value.name] = uid
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

    LazyColumn(state=tmp, horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        accounts.forEachIndexed { idx, it ->
            item(key=it.name) {
                        // I would think that capturing this data would control redraw of each item, but it appears to not do so.
                        // Redraw is controlled of the entire AccountListView, or not at all.
                        //val anyChanges: MutableState<AccountUIData> = remember { mutableStateOf(it.uiData()) }
                // scope.launch { listState.animateScrollToItem(idx, -1) }  // -1 puts our item more in the center
                if (accountUIData[it.name] == null) accountUIData[it.name] = it.uiData()
                AccountItemView(accountUIData[it.name]!!, idx, selectedAccount.value == it, devMode,
                          onClickAccount = { onAccountSelected(it) },
                          onClickGearIcon = {
                              nav.go(ScreenId.AccountDetails)
                          })
            }
        }
        // Since the thumb buttons cover the bottom most row, this blank bottom row allows the user to scroll the account list upwards enough to
        // uncover the last account.  Its not necessary if there are just a few accounts though.
        if (accounts.size > 3)
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
  var devinfo: String="",
  var locked: Boolean = false,
  var lockable: Boolean = false)

/** Look into this account and produce the strings and modifications needed to display it */
fun Account.uiData():AccountUIData
{
    val ret = AccountUIData(this)

    var delta = balance - confirmedBalance

    ret.name = name
    ret.lockable = lockable
    ret.locked = locked
    ret.chainSelector = wallet.chainSelector
    val chainstate = wallet.chainstate
    if (chainstate != null)
    {
        if (chainstate.isSynchronized(1, 60 * 60))  // ignore 1 block desync or this displays every time a new block is found
        {
            ret.unconfBal =
              if (CURRENCY_ZERO == unconfirmedBalance)
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
            ret.unconfBal = if (chainstate.syncedDate <= 1231416000) i18n(S.unsynced)  // for fun: bitcoin genesis block
            else i18n(S.balanceOnTheDate) % mapOf("date" to epochToDate(chainstate.syncedDate))

            ret.balFontWeight = FontWeight.Light
            ret.unconfBalColor = unsyncedStatusColor
        }
    }
    else
    {
        ret.balFontWeight = FontWeight.Light
        ret.unconfBal = i18n(S.walletDisconnectedFromBlockchain)
    }

    ret.balance = format(balance)

    if (chainstate != null)
    {
        val cnxnLst = wallet.chainstate?.chain?.net?.mapConnections() { it.name }

        val trying: List<String> = if (chainstate.chain.net is MultiNodeCnxnMgr) (chainstate.chain.net as MultiNodeCnxnMgr).initializingCnxns.map { it.name } else listOf()
        val peers = cnxnLst?.joinToString(", ") + (if (trying.isNotEmpty()) (" " + i18n(S.trying) + " " + trying.joinToString(", ")) else "")

        ret.devinfo = i18n(S.at) + " " + (wallet.chainstate?.syncedHash?.toHex()?.take(8) ?: "") + ", " + (wallet.chainstate?.syncedHeight
          ?: "") + " " + i18n(S.of) + " " + (wallet.chainstate?.chain?.curHeight
          ?: "") + " blocks, " + (wallet.chainstate?.chain?.net?.size ?: "") + " peers\n" + peers
    }
    else
    {
        ret.devinfo = i18n(S.walletDisconnectedFromBlockchain)
    }

    if (fiatPerCoin > BigDecimal.ZERO)
    {
        var fiatDisplay = balance * fiatPerCoin
        ret.approximately = i18n(S.approximatelyT) % mapOf("qty" to FiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode)
    }
    else ret.approximately = null
    return ret
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
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(2.dp)
        .background(backgroundColor)
        .clickable(onClick = onClickAccount),
      contentAlignment = Alignment.Center
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxHeight())
            {
                ResImageView(getAccountIconResPath(uidata.chainSelector), Modifier.size(32.dp), "Blockchain icon")
            }
            Column(
              modifier = Modifier.weight(1f).padding(2.dp),
              verticalArrangement = Arrangement.Top,
            ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = uidata.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (uidata.lockable)
                    {
                        ResImageView(if (uidata.locked) "icons/lock.xml" else "icons/unlock.xml",
                          modifier = Modifier.size(26.dp).absoluteOffset(0.dp, -8.dp).clickable {
                              onClickAccount()  // I want to select the whole account & then try to unlock/lock
                              if (uidata.locked)
                              {
                                  triggerUnlockDialog()
                              }
                              else
                              {
                                  uidata.account.pinEntered = false
                                  later {
                                      triggerAssignAccountsGuiSlots()  // In case it should be hidden
                                      accountChangedNotification.send(uidata.name)
                                  }
                              }
                          })
                    }
                    Spacer(Modifier.width(16.dp))
                    Row(
                      verticalAlignment = Alignment.Bottom
                    ) {
                        Text(text = uidata.balance, fontSize = 28.sp, color = colorDebit)
                        Text(text = uidata.currencyCode, fontSize = 14.sp)
                    }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center
                ) {
                    uidata.approximately?.let {
                        Text(text = it, fontSize = 16.sp)
                    }
                }
                if (uidata.unconfBal.isNotEmpty())
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = uidata.unconfBal, color = uidata.unconfBalColor)
                    }
                if (devMode) Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = uidata.devinfo, fontSize = 12.sp)
                }
            }
            // Show the account settings gear at the end
            if (isSelected)
            {
                ResImageView("icons/gear.xml", Modifier.align(Alignment.CenterVertically).padding(0.dp, 0.dp).size(32.dp).clickable(onClick = onClickGearIcon))
            }
            else Spacer(Modifier.align(Alignment.CenterVertically).padding(0.dp, 0.dp).size(32.dp))  // by putting a blank here, the other columns don't change
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
