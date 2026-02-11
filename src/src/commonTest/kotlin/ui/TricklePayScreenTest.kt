package ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.TricklePayDomainView
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test

class TricklePayScreenTest: WallyUiTestBase()
{
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tricklePayDomainViewTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("testaccountitemview", 0U, "", cs)!!
        runComposeUiTest {
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
                TricklePayDomainView(td, Modifier, account)
            }
            check(waitForCatching { onNodeWithTag("TricklePayDomainViewDomainName").isDisplayed() })
        }
        wallyApp!!.deleteAccount(account)
        println("tricklePayDomainViewTest COMPLETED!")
    }
}