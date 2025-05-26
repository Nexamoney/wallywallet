package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.TxHistoryScreen
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TxHistoryScreenTest: WallyUiTestBase()
{
    @Test
    fun txHistoryScreenTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("txhis", 0U, "", cs)!!
        }
        setContent {
            TxHistoryScreen(account, ScreenNav())
        }
        wallyApp!!.deleteAccount(account)
    }
}