package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
//import androidx.compose.ui.window.Events
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
import androidx.compose.material.*

import androidx.compose.ui.window.rememberWindowState
import info.bitcoinunlimited.www.wally.ui.NavigationRoot

private val LogIt = GetLog("BU.wally.IdentityActivity")

val WallyTitle = "Wally Enterprise Wallet"
object WallyJvmApp
{
    @JvmStatic
    fun main(args: Array<String>)
    {
        initializeLibNexa()
        setLocale()
        LogIt.warning("Starting Wally Enterprise Wallet")
        val wal = openOrNewWallet("reg", ChainSelector.NEXAREGTEST)
        wal.blockchain.req.net.exclusiveNodes(setOf("192.168.1.5"))
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        // TODO REMOVE THIS:  This creates 2 dummy accounts if there are no accounts (until the account creation screen is done)
        launch {
            LogIt.info("Have ${wallyApp!!.accounts.size} accounts")
            if (wallyApp!!.accounts.size == 0)
            {
                LogIt.info("Creating new accounts")
                wallyApp!!.newAccount("test1", 0UL, "", ChainSelector.NEXAREGTEST)
                wallyApp!!.newAccount("test2", 0UL, "", ChainSelector.NEXAREGTEST)
            }
        }
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
                {
                    NavigationRoot()
                }
               // DashboardScreen((4 * 160).dp, WallyJvmApp.accounts)
            }
        }
    }
}
