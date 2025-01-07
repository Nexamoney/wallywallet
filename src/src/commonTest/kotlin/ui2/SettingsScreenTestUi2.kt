package ui2

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ConfirmAbove
import info.bitcoinunlimited.www.wally.ui2.LocalCurrency
import info.bitcoinunlimited.www.wally.wallyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.chainToCurrencyCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTestUi2 {

    @BeforeTest
    fun setup()
    {
        // jvm only
        if (platform().usesMouse)
            Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun clean()
    {
        // jvm only
        if (platform().usesMouse)
            Dispatchers.resetMain()
    }

    @Test
    fun confirmAboveTest() = runComposeUiTest {
        wallyApp = CommonApp()

        val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
        setContent {
            ConfirmAbove(preferenceDB)
        }

        val textInput = "123123"

        onNodeWithText(i18n(S.WhenAskSure)).assertIsDisplayed()
        onNodeWithText(chainToCurrencyCode[ChainSelector.NEXA]!!).assertIsDisplayed()
        onNodeWithTag("ConfirmAboveEntry").assertIsDisplayed()
        onNodeWithTag("ConfirmAboveEntry").performTextInput("")
        onNodeWithTag("ConfirmAboveEntry").performTextClearance()
        onNodeWithTag("ConfirmAboveEntry").performTextInput(textInput)
        onNodeWithTag("ConfirmAboveEntry").assertTextContains(textInput)
        val confirmAbove = preferenceDB.getString(CONFIRM_ABOVE_PREF, "0") ?: "0"
        assertEquals(textInput, confirmAbove)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun localCurrencyTest() = runComposeUiTest {
        val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
        setContent {
            LocalCurrency(preferenceDB)
        }

        onNodeWithTag(i18n(S.localCurrency)).assertExists()
        onNodeWithTag(i18n(S.localCurrency)).assertTextEquals(i18n(S.localCurrency))
    }
}
