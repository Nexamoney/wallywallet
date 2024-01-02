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
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav

private val LogIt = GetLog("BU.wally.IdentityActivity")

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

    var topWindow = mutableStateOf(i18n(S.app_name))
}


fun guiNewPanel()
{
    val nav = ScreenNav()
    val currentRootScreen = mutableStateOf(ScreenId.Home)

    application(true)
    {
        var isOpen by remember { mutableStateOf(true) }
        //var w = mutableStateOf(i18n(S.app_name))
        //WallyJvmApp.topWindow = remember { w }
        //var windowTitle by remember { w }
        //val currentRootScreen = remember { mutableStateOf(ScreenId.Home) }
        nav.reset(currentRootScreen)

        if (isOpen)
        {
            val w = Window(
              onCloseRequest = { isOpen = false },
              title = nav.title(),
              state = rememberWindowState(width = (4 * 160).dp, height = (5 * 160).dp)
            )
            {
                //WallyJvmApp.topWindow = w
                MaterialTheme()
                {
                    NavigationRoot(nav)
                }
               // DashboardScreen((4 * 160).dp, WallyJvmApp.accounts)
            }
        }
    }
}
