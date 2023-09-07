package info.bitcoinunlimited.www.wally

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.openOrNewWallet
import platform.Foundation.NSBundle

val accounts = mutableMapOf<String, Bip44Wallet>()

fun OnAppStartup()
{
    initializeLibNexa()
    println("APP STARTUP")
    //setLocale("sl","")
    setLocale()
    val wal = openOrNewWallet("reg", ChainSelector.NEXAREGTEST)
    wal.blockchain.req.net.exclusiveNodes(setOf("192.168.1.5"))
    accounts[wal.name] = wal
}

fun MainViewController() = ComposeUIViewController {
    // TODO call some other startup function
    if (accounts.size == 0 ) {
        initializeLibNexa()
        val wal = openOrNewWallet("reg", ChainSelector.NEXAREGTEST)
        wal.blockchain.req.net.exclusiveNodes(setOf("192.168.1.5"))
        accounts[wal.name] = wal
    }
    MaterialTheme {
        DashboardPanel(400.dp, accounts )
        //GreetingScreen("MVC")
    }
}

