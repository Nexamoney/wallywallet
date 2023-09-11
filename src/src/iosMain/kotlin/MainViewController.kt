package info.bitcoinunlimited.www.wally

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.openOrNewWallet

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

enum class ScreenNav {
    Settings,
    Something
}

fun MainViewController() = ComposeUIViewController {
    val currentScreen = remember { mutableStateOf(ScreenNav.Settings) }

    // TODO call some other startup function
    if (accounts.size == 0 ) {
        initializeLibNexa()
        val wal = openOrNewWallet("reg", ChainSelector.NEXAREGTEST)
        wal.blockchain.req.net.exclusiveNodes(setOf("192.168.1.5"))
        accounts[wal.name] = wal
    }
    MaterialTheme {
        NavigationRoot(accounts)
    }
}

