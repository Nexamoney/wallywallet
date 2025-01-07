package ui.views

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui2.views.UnlockView
import kotlin.test.Test

class UnlockViewTest
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
        onNodeWithTag(i18n(S.EnterPIN)).assertExists()
        onNodeWithTag(i18n(S.EnterPIN)).performTextInput(input)
        onNodeWithTag(i18n(S.EnterPIN)).assert(hasText(input))
    }
}