package info.bitcoinunlimited.www.wally

import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import kotlinx.cinterop.ExperimentalForeignApi
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.initializeLibNexa
import platform.UIKit.UIViewController

private val LogIt = GetLog("BU.wally.iosMain.MainViewController")
val accounts = mutableMapOf<String, Bip44Wallet>()

fun OnAppStartup()
{
    initializeLibNexa()
    setLocale()
    wallyApp = CommonApp()
    wallyApp!!.onCreate()
}


val nav = ScreenNav()
@OptIn(ExperimentalForeignApi::class, InternalComposeApi::class)
fun MainViewController(): UIViewController
{
    val view = ComposeUIViewController({
        val currentRootScreen = remember { mutableStateOf(ScreenId.Home) }
        nav.reset(currentRootScreen)
        NavigationRoot(nav)
    })

    // Wrong selector
    //    val dc = NSNotificationCenter.defaultCenter
    //    dc.addObserver(dc, selector = NSSelectorFromString("keyboardWillShow:"), name = UIKeyboardWillShowNotification, null)
    return view
}

