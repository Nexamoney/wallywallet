package info.bitcoinunlimited.www.wally

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.hasNativeTitleBar
import kotlinx.coroutines.delay
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.threads.millisleep

val accounts = mutableMapOf<String, Bip44Wallet>()

fun OnAppStartup()
{
    initializeLibNexa()
    setLocale()
    hasNativeTitleBar = false
    wallyApp = CommonApp()
    wallyApp!!.onCreate()
}

val nav = ScreenNav()
fun MainViewController() = ComposeUIViewController {

        while (!coinsCreated) millisleep(250UL)
        val currentRootScreen = remember { mutableStateOf(ScreenId.Home) }
        nav.reset(currentRootScreen)
        NavigationRoot(nav)
}

