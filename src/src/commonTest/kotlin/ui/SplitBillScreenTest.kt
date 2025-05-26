package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.SplitBillScreen
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test

class SplitBillScreenTest
{
    init {
        setupTestEnv()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun displaySplitBillScreen()
    {
        val cs = ChainSelector.NEXA
        val account: Account = wallyApp!!.newAccount("mock", 0U, "", cs)!!
        runComposeUiTest {
            setContent {
                SplitBillScreen(account)
            }
            settle()
            onNodeWithText(i18n(S.SplitBillDescription)).assertIsDisplayed()
        }
        account.delete()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun amountInputTest()
    {
        val cs = ChainSelector.NEXA
        val account: Account = wallyApp!!.newAccount("mock", 0U, "", cs)!!
        runComposeUiTest {
            setContent {
                SplitBillScreen(account)
            }
            settle()

            onNodeWithTag("SplitBillScreenAmountInput").assertIsDisplayed()
            onNodeWithTag("SplitBillScreenAmountInput").performTextClearance()
            onNodeWithTag("SplitBillScreenAmountInput").performTextInput("100")
            settle()
            onNodeWithTag("SplitBillScreenAmountInput").assert(hasText("100"))

            onNodeWithTag("SplitBillScreenTipInput").assertIsDisplayed()
            onNodeWithTag("SplitBillScreenTipInput").performTextClearance()
            onNodeWithTag("SplitBillScreenTipInput").performTextInput("42")
            settle()
            onNodeWithText("42").assertIsDisplayed()
        }
        account.delete()
    }
}