package ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.TricklePayDomainView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.Test
import kotlinx.coroutines.IO

class TricklePayScreenTest
{
    init {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tricklePayDomainViewTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA

        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("testaccountitemview", 0U, "", cs)!!
        }

        val td = TdppDomain(
          "domain",
          "topic",
          "addr",
          "currency",
          -1,
          2,
          3,
          4,
          "per",
          "day",
          "perweek",
          "permonth",
          false,
          "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw", // Mock Addr
          "nexa:nqtsq5g55t9699mcue00frjqql5275r3et45c3dqtxzfz8ru", // Mock Addr
          "nexa:nqtsq5g5skc5xfw7dzu2jw7hktf3tg053drevxx94yx44gjc" // Mock Addr
        )

        setContent {
            TricklePayDomainView(null, td, Modifier, account)
        }

        onNodeWithTag("TricklePayDomainViewTag").isDisplayed()
    }
}