package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.LOCAL_CURRENCY_PREF
import info.bitcoinunlimited.www.wally.ui.ChildNav
import info.bitcoinunlimited.www.wally.ui.accountChangedNotification
import info.bitcoinunlimited.www.wally.ui.assignAccountsGuiSlots
import info.bitcoinunlimited.www.wally.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.nexa.libnexakotlin.*

//val triggerRecompose = MutableStateFlow(0)
val accountUIData = mutableMapOf<String,MutableState<AccountUIData>>()

@Composable fun AccountListView(accounts: MutableState<ListifyMap<String,Account>>, selectedAccount: MutableStateFlow<Account?>, nav: ChildNav)
{
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 1.dp, horizontal = 0.dp)
        .height(350.dp), // TODO: Position relative to parent view
    ) {

        LaunchedEffect(true)
        {
            for(c in accountChangedNotification)
            {
                val act = wallyApp?.accounts?.get(c)
                if (act != null)
                {
                    accountUIData[c]?.value = act.uiData()
                }
            }
        }

        LazyColumn(
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            accounts.value.forEachIndexed { idx, it ->
                    item(key=it.name) {
                        // I would think that capturing this data would control redraw of each item, but it appears to not do so.
                        // Redraw is controlled of the entire AccountListView, or not at all.
                        val anyChanges: MutableState<AccountUIData> = remember { mutableStateOf(it.uiData()) }
                        accountUIData[it.name] = anyChanges
                        AccountItemView(anyChanges.value, idx, selectedAccount.value == it, devMode,
                          onClickAccount = {
                              selectedAccount.value = it
                          },
                          onClickGearIcon = {
                              nav.displayAccount(it)
                          })
                    }
                }
            }
        }
}


data class AccountUIData(
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
    val ret = AccountUIData()

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
  anyChanges: AccountUIData,
  index: Int,
  isSelected: Boolean,
  devMode: Boolean,
  onClickAccount: () -> Unit,
  onClickGearIcon: () -> Unit
) {
    val uidata = anyChanges //.value
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
                if (isSelected) ResImageView("icons/gear.xml", Modifier.padding(0.dp, 20.dp).size(32.dp).clickable(onClick = onClickGearIcon))
            }
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
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
                              // TODO lock clicked
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
        }
    }
}


/*
@OptIn(ExperimentalResourceApi::class)
@Composable
fun AccountItemView(
  accountName: String,
  balance: String,
  chainSelector: ChainSelector,
  currencyCode: String,
  lockable: Boolean,
  locked: Boolean,
  isSelected: Boolean,
  info: String,
  devInfo: String,
  devMode: Boolean,
  unconfirmedValue: String,
  onClickAccount: () -> Unit,
  onClickGearIcon: () -> Unit
) {
    val backgroundColor = if(isSelected) defaultListHighlight else WallyRowAbkg2
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(backgroundColor)
        .clickable(onClick = onClickAccount),
      contentAlignment = Alignment.Center
    ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
          verticalArrangement = Arrangement.Top  , // SpaceBetween,
        ) {
            Row {

                Row(
                  verticalAlignment = Alignment.Bottom
                ) {
                    ResImageView(getAccountIconResPath(chainSelector), Modifier.size(32.dp), "Blockchain icon")
                    Spacer(Modifier.width(32.dp))
                    Text(text = accountName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Row(
                      verticalAlignment = Alignment.Bottom
                    ) {
                        Text(text = balance, fontSize = 28.sp, color = colorDebit)
                        Text(text = currencyCode, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(16.dp))

                    if(lockable)
                    {
                        ResImageView(if (locked) "icons/lock.xml" else "icons/unlock.xml", modifier = Modifier.size(26.dp))
                    }
                }

                Row(
                  horizontalArrangement = Arrangement.End,
                  verticalAlignment = Alignment.Bottom,
                  modifier = Modifier.fillMaxWidth()
                ) {
                    if(isSelected)
                        ResImageView("icons/gear.xml", Modifier.size(26.dp).clickable(onClick = onClickGearIcon))
                }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center
            ) {
                Text(text = info, fontSize = 16.sp)
            }
            if(unconfirmedValue.isNotEmpty())
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = unconfirmedValue, )
                }
            if(devMode)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center
                ){
                    Text(text = devInfo, fontSize = 12.sp)
                }
        }
    }
}

 */

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
