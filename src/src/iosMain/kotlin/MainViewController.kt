package info.bitcoinunlimited.www.wally

import androidx.compose.ui.window.ComposeUIViewController
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.hasNativeTitleBar
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.initializeLibNexa

val accounts = mutableMapOf<String, Bip44Wallet>()

fun OnAppStartup()
{
    initializeLibNexa()
    println("APP STARTUP")
    //setLocale("sl","")
    setLocale()
    /*
    val wal = openOrNewWallet("reg", ChainSelector.NEXAREGTEST)
    wal.blockchain.req.net.exclusiveNodes(setOf("192.168.1.5"))
    accounts[wal.name] = wal
     */
    hasNativeTitleBar = false
    wallyApp = CommonApp()
    wallyApp!!.onCreate()
}

val nav = ScreenNav()
val currentRootScreen = mutableStateOf(ScreenId.Home)
fun MainViewController() = ComposeUIViewController {
    nav.reset(currentRootScreen)
    NavigationRoot(nav)
}

