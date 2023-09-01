package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
//import androidx.compose.ui.window.Events
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.*

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.rememberWindowState

private val LogIt = GetLog("BU.wally.IdentityActivity")

fun _x_(s:String): String = s

val WallyTitle = "Wally Enterprise Wallet"
object WallyJvmApp
{
    val accounts: MutableMap<String, Bip44Wallet> = mutableMapOf()
    @JvmStatic
    fun main(args: Array<String>)
    {
        initializeLibNexa()
        LogIt.warning("Starting Wally Enterprise Wallet")
        val wal = openOrNewWallet("reg", ChainSelector.NEXAREGTEST)
        accounts[wal.name] = wal
        guiNewPanel()
    }
}


fun guiNewPanel()
{
    application(true)
    {
        var isOpen by remember { mutableStateOf(true) }
        if (isOpen)
        {
            val w = Window(
              onCloseRequest = { isOpen = false },
              title = WallyTitle,
              state = rememberWindowState(width = (4 * 160).dp, height = (5 * 160).dp)
            )
            {
                MaterialTheme()
                {}
                DashboardPanel((4 * 160).dp)
            }
        }
    }
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
            walletInfo = updateWalletDashboard()
        }
    }
    for (b in blockchains.values)
    {
        b.onChange = {
            blockchainInfo = updateBlockchainDashboard()
        }
    }

    SelectionContainer()
    {
        Column(Modifier.fillMaxHeight().width(dashWidth), verticalArrangement = Arrangement.Top)
        {
            val headerStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = dashboardFontSize)
            val textStyle = TextStyle(fontSize = dashboardFontSize)
            Text(_x_("Blockchains"), Modifier.fillMaxWidth(), style = headerStyle)
            Text(blockchainInfo, style = textStyle, overflow = TextOverflow.Clip)

            Text(_x_("Wallets"), style = headerStyle)
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


fun updateWalletDashboard():String
{
    val result = StringBuilder()

    for (w in WallyJvmApp.accounts)
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