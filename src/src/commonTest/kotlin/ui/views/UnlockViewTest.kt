package ui.views

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui2.views.UnlockView
import ui2.WallyUiTestBase
import kotlin.test.Test

class UnlockViewTest:WallyUiTestBase()
{
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unlockViewTest() = runComposeUiTest {
        val content = ""
        val input = "1235"
        setContent {
            UnlockView {

            }
        }

        onNodeWithText(content).assertIsDisplayed()
        onNodeWithTag("EnterPIN").assertExists()
        onNodeWithTag("EnterPIN").performTextInput(input)
        onNodeWithTag("EnterPIN").assert(hasText(input))
        if (platform().hasDoneButton)
            onNodeWithText(i18n(S.done)).assertIsDisplayed()
    }
}