package info.bitcoinunlimited.www.wally

import androidx.compose.ui.window.ComposeUIViewController
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
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
    wallyApp = CommonApp()
    wallyApp!!.onCreate()
}

enum class ScreenNav {
    Settings,
    Something
}


fun MainViewController() = ComposeUIViewController {
    NavigationRoot()
}

