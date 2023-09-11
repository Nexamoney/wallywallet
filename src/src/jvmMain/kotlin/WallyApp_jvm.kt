package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
//import androidx.compose.ui.window.Events
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
import androidx.compose.material.*

import androidx.compose.ui.window.rememberWindowState
import info.bitcoinunlimited.www.wally.ui.DashboardScreen

private val LogIt = GetLog("BU.wally.IdentityActivity")

val WallyTitle = "Wally Enterprise Wallet"
object WallyJvmApp
{
    val accounts: MutableMap<String, Bip44Wallet> = mutableMapOf()
    @JvmStatic
    fun main(args: Array<String>)
    {
        initializeLibNexa()
        setLocale()
        LogIt.warning("Starting Wally Enterprise Wallet")
        val wal = openOrNewWallet("reg", ChainSelector.NEXAREGTEST)
        wal.blockchain.req.net.exclusiveNodes(setOf("192.168.1.5"))
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
                DashboardScreen((4 * 160).dp, WallyJvmApp.accounts)
            }
        }
    }
}
