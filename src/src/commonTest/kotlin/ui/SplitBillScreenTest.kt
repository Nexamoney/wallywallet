package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.SplitBillScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.Test

class SplitBillScreenTest
{
    init {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun displaySplitBillScreen() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("mock", 0U, "", cs)!!
        }
        setContent {
            SplitBillScreen(ScreenNav(), account)
        }

        onNodeWithText(i18n(S.SplitBillDescription)).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun amountInputTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("mock", 0U, "", cs)!!
        }
        setContent {
            SplitBillScreen(ScreenNav(), account)
        }

        onNodeWithTag("SplitBillScreenAmountInput").assertIsDisplayed()
        onNodeWithTag("SplitBillScreenAmountInput").performTextClearance()
        onNodeWithTag("SplitBillScreenAmountInput").performTextInput("100")
        onNodeWithTag("SplitBillScreenAmountInput").assert(hasText("100"))

        onNodeWithTag("SplitBillScreenTipInput").assertIsDisplayed()
        onNodeWithTag("SplitBillScreenTipInput").performTextClearance()
        onNodeWithTag("SplitBillScreenTipInput").performTextInput("42")

        onNodeWithText("42").assertIsDisplayed()
    }
}