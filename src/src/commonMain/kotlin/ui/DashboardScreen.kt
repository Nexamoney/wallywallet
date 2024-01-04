package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui.theme.WallyDropdownMenu
import info.bitcoinunlimited.www.wally.ui.theme.WallyDropdownStyle
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.blockchains

val testDropDown = listOf("big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "this_is_a_test_of_a_long_string",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  )

fun _x_(s:String): String = s

@Composable fun GreetingScreen(text: String)
{
    Text(text = text)
}


var dashboardFontSize = 14.sp
@Composable
fun TestScreen(dashWidth: Dp)
{
    Text("HomeScreen")

    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("any") }

    //Row() {  // bug leaves a big gap
    Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Text("Drop boxes: ")
        var selectedIndex by remember { mutableStateOf(-1) }
        WallyDropdownMenu(
          modifier = Modifier.width(IntrinsicSize.Min),
          label = "Succinct",
          items = testDropDown,
          selectedIndex = selectedIndex,
          style = WallyDropdownStyle.Succinct,
          onItemSelected = { index, _ -> selectedIndex = index },
        )

        Text(", ")
        var selectedIndex2 by remember { mutableStateOf(-1) }
        WallyDropdownMenu(
          modifier = Modifier.width(IntrinsicSize.Min).weight(1f),
          label = "Field",
          items = testDropDown,
          selectedIndex = selectedIndex2,
          style = WallyDropdownStyle.Field,
          onItemSelected = { index, _ -> selectedIndex2 = index },
        )

        Text(", and ")
        var selectedIndex3 by remember { mutableStateOf(-1) }
        WallyDropdownMenu(
          modifier = Modifier.width(IntrinsicSize.Min).weight(1f),
          label = "Outlined",
          items = testDropDown,
          selectedIndex = selectedIndex3,
          style = WallyDropdownStyle.Outlined,
          onItemSelected = { index, _ -> selectedIndex3 = index },
        )
    }

    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
          text = selected,
          modifier = Modifier.clickable(onClick = { expanded = true })
        )
        IconButton(onClick = {expanded = true}) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = "")
        }
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.background(Color.Magenta)
    ) {
        testDropDown.forEachIndexed { _, s ->
            DropdownMenuItem(
              onClick = {
                  expanded = false
                  selected = s
              },
              text = { Text(text = s) }
            )
        }
    }
    WallyDivider()


    var walletInfo by mutableStateOf("")
    var blockchainInfo by mutableStateOf("")
    val accounts = wallyApp!!.accounts
    for ((_,v) in accounts)
    {
        v.wallet.setOnWalletChange {
            walletInfo = updateWalletDashboard(accounts)
        }
    }
    for (b in blockchains.values)
    {
        b.onChange.add({ blockchainInfo = updateBlockchainDashboard() })
    }
    walletInfo = updateWalletDashboard(accounts)
    blockchainInfo = updateBlockchainDashboard()

    SelectionContainer()
    {
        Column(Modifier.fillMaxHeight().width(dashWidth), verticalArrangement = Arrangement.Top)
        {
            val headerStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = dashboardFontSize)
            val textStyle = TextStyle(fontSize = dashboardFontSize)
            Text(i18n(S.Blockchain), Modifier.fillMaxWidth(), style = headerStyle)
            Text(blockchainInfo, style = textStyle, overflow = TextOverflow.Clip)

            Text(i18n(S.AccountListHeader), style = headerStyle)
            Text(walletInfo, style = textStyle, overflow = TextOverflow.Clip, softWrap = false)
        }
    }

    /*
    val tmp = generateQRCode("this is a test")
    if (tmp != null)
        androidx.compose.foundation.Image(bitmap = tmp, "QR")

     */

    //Text(_x_("Bindings"), style = headerStyle)
    //Text(panelState.bindingInfo, style = textStyle)
}

/** Update the dashboard blockchain text */
fun updateBlockchainDashboard():String
{
    val result = StringBuilder()
    for (b in blockchains.values)
    {
        result.append(b.name + " at " + b.curHeight + " cnxns " + b.net.p2pCnxns.size + "\n")
    }
    return result.toString()
}


fun updateWalletDashboard(accounts: MutableMap<String, Account>):String
{
    val result = StringBuilder()

    for (a in accounts)
    {
        val w = a.value.wallet
        val curHeight = w.syncedHeight
        var nOutUnconf = 0
        var nInUnconf = 0
        result.append(a.key + " on " + w.blockchain.chainSelector + " at " + w.syncedHeight + " balance " + w.balance + ":" + w.balanceUnconfirmed + "\n" )
        w.forEachTxo { sp ->
            if (sp.spentUnconfirmed)
            {
                nOutUnconf++
            }
            // count the incoming ignoring change
            if (sp.commitUnconfirmed > 0 && !sp.spentUnconfirmed)
            {
                nInUnconf++
            }
            false
        }
        result.append("  " + nOutUnconf + " outgoing unconfirmed transactions\n")
        result.append("  " + nInUnconf + " incoming unconfirmed transactions\n")
        w.forEachTxo { sp ->
            if (sp.isUnspent)
            {
                result.append("  " + sp.amount + " on " + sp.addr + " (" + (curHeight - sp.commitHeight) + "confs)  tx: ${sp.commitTxIdem.toHex()}\n")
            }
            false
        }
    }
    return result.toString()
}