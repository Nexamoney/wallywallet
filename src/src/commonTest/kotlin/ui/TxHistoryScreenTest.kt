package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.TxHistoryScreen
import info.bitcoinunlimited.www.wally.ui2.ScreenNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TxHistoryScreenTest
{
    init {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @Test
    fun txHistoryScreenTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("txhis", 0U, "", cs)!!
        }
        setContent {
            TxHistoryScreen(account, ScreenNav())
        }
    }
}