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
                DashboardPanel((4 * 160).dp, WallyJvmApp.accounts)
            }
        }
    }
}
