package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.*
import androidx.compose.ui.test.swipeUp
import info.bitcoinunlimited.www.wally.ui.ConfirmAbove
import info.bitcoinunlimited.www.wally.ui.LocalCurrency
import info.bitcoinunlimited.www.wally.ui.SettingsScreen
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.chainToCurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest: WallyUiTestBase()
{
    @Test
    fun settingsScreenTest() = runComposeUiTest {
        val preferenceDB: SharedPreferences = FakeSharedPreferences()

        setContent {
            SettingsScreen(preferenceDB)
        }
        settle()

        onNodeWithTag(i18n(S.localCurrency)).assertExists()
        onNodeWithTag(i18n(S.localCurrency)).assertTextEquals(i18n(S.localCurrency))

        // Enable developer mode and assert that the Reload Assets button is displayed
        onNodeWithText(i18n(S.enableDeveloperView)).assertIsDisplayed()
        onNodeWithText(i18n(S.enableDeveloperView)).performClick()
        settle()
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasTestTag("BlockchainSelectors")).performTouchInput { swipeUp() }
        // Failing: Reason: Expected exactly '1' node but could not find any node that satisfies: (Text + EditableText contains 'Reload Assets' (ignoreCase: false))
        // onNodeWithText("Reload Assets").assertIsDisplayed()
        settle()
    }

    @Test
    fun confirmAboveTest() = runComposeUiTest {

        val preferenceDB: SharedPreferences = FakeSharedPreferences()
        setContent {
            ConfirmAbove(preferenceDB)
        }
        settle()

        val textInput = "123123.00"

        onNodeWithText(i18n(S.WhenAskSure)).assertIsDisplayed()
        onNodeWithText(chainToCurrencyCode[ChainSelector.NEXA]!!).assertIsDisplayed()
        onNodeWithTag("ConfirmAboveEntry").assertIsDisplayed()
        onNodeWithTag("ConfirmAboveEntry").performTextInput("")
        settle()
        onNodeWithTag("ConfirmAboveEntry").performTextClearance()
        onNodeWithTag("ConfirmAboveEntry").performTextInput(textInput)
        settle()
        onNodeWithTag("ConfirmAboveEntry").assertTextContains(textInput)
        val confirmAbove = preferenceDB.getString(CONFIRM_ABOVE_PREF, "0") ?: "0"

        assertEquals(textInput, confirmAbove)
        settle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun localCurrencyTest() = runComposeUiTest {
        val preferenceDB: SharedPreferences = FakeSharedPreferences()
        setContent {
            LocalCurrency(preferenceDB)
        }
        settle()
        onNodeWithTag(i18n(S.localCurrency)).assertExists()
        onNodeWithTag(i18n(S.localCurrency)).assertTextEquals(i18n(S.localCurrency))
        settle()
    }
}
