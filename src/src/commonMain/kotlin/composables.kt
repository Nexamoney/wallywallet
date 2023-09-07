package info.bitcoinunlimited.www.wally

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.blockchains

fun _x_(s:String): String = s

@Composable fun GreetingScreen(text: String)
{
    Text(text = text)
}


var dashboardFontSize = 14.sp
@Composable
fun DashboardPanel(dashWidth: Dp, accounts: MutableMap<String, Bip44Wallet>)
{
    var walletInfo by mutableStateOf("")
    var blockchainInfo by mutableStateOf("")
    for ((k,v) in accounts)
    {
        v.setOnWalletChange {
            walletInfo = updateWalletDashboard(accounts)
        }
    }
    for (b in blockchains.values)
    {
        b.onChange = {
            blockchainInfo = updateBlockchainDashboard(accounts)
        }
    }
    walletInfo = updateWalletDashboard(accounts)
    blockchainInfo = updateBlockchainDashboard(accounts)

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
fun updateBlockchainDashboard(accounts: MutableMap<String, Bip44Wallet>):String
{
    val result = StringBuilder()
    for (b in blockchains.values)
    {
        result.append(b.name + " at " + b.curHeight + " cnxns " + b.net.p2pCnxns.size + "\n")
    }
    return result.toString()
}


fun updateWalletDashboard(accounts: MutableMap<String, Bip44Wallet>):String
{
    val result = StringBuilder()

    for (w in accounts)
    {
        val curHeight = w.value.syncedHeight
        var nOutUnconf = 0
        var nInUnconf = 0
        result.append(w.key + " on " + w.value.blockchain.chainSelector + " at " + w.value.syncedHeight + " balance " + w.value.balance + ":" + w.value.balanceUnconfirmed + "\n" )
        for (txo in w.value.txos)
        {
            if (txo.value.spentUnconfirmed)
            {
                nOutUnconf++
            }
            // count the incoming ignoring change
            if (txo.value.spendableUnconfirmed > 0 && !txo.value.spentUnconfirmed)
            {
                nInUnconf++
            }
        }
        result.append("  " + nOutUnconf + " outgoing unconfirmed transactions\n")
        result.append("  " + nInUnconf + " incoming unconfirmed transactions\n")
        for (txo in w.value.txos)
        {
            if (txo.value.isUnspent)
            {
                result.append("  " + txo.value.amount + " on " + txo.value.addr + " (" + (curHeight - txo.value.commitHeight) + "confs)  tx: ${txo.value.commitTxIdem.toHex()}\n")
            }

        }
    }
    return result.toString()
}