package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.swipeUp
import info.bitcoinunlimited.www.wally.ui.BlockchainSource
import info.bitcoinunlimited.www.wally.ui.ConfirmAbove
import info.bitcoinunlimited.www.wally.ui.LocalCurrency
import info.bitcoinunlimited.www.wally.ui.SettingsScreen
import info.bitcoinunlimited.www.wally.ui.experimentalUI
import info.bitcoinunlimited.www.wally.ui.showIdentityPref
import info.bitcoinunlimited.www.wally.ui.showTricklePayPref
import info.bitcoinunlimited.www.wally.ui.soundEnabled
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.chainToCurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest: WallyUiTestBase()
{
    @Test
    fun blockchainSelectorsDisplayedTest() = runComposeUiTest {
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

    // ======================================================================
    // SettingsScreen — sections and layout
    // ======================================================================

    @Test
    fun settingsScreen_displaysAllSections() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { SettingsScreen(prefs) }
        settle()

        onNodeWithText("Currency Settings").assertIsDisplayed()
        onNodeWithText(i18n(S.GeneralSettings)).assertIsDisplayed()
        onNodeWithText(i18n(S.BlockchainSettings)).assertIsDisplayed()
    }

    // ======================================================================
    // SettingsScreen — switch display
    // ======================================================================

    @Test
    fun settingsScreen_devModeSwitch_togglesOn() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { SettingsScreen(prefs) }
        settle()

        onNodeWithText(i18n(S.enableDeveloperView)).assertIsDisplayed()
        onNodeWithTag("DevModeSwitch").performClick()
        settle()
        onNodeWithTag("DevModeSwitch").assertIsOn()

        // Toggle back off to clean up
        onNodeWithTag("DevModeSwitch").performClick()
        settle()
    }

    @Test
    fun settingsScreen_experimentalUxSwitch_togglesOn() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        experimentalUI.value = false
        setContent { SettingsScreen(prefs) }
        settle()

        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.enableExperimentalUx)))
        onNodeWithText(i18n(S.enableExperimentalUx)).assertIsDisplayed()
        onNodeWithTag("ExperimentalUxSwitch").assertIsOff()
        onNodeWithTag("ExperimentalUxSwitch").performClick()
        settle()
        onNodeWithTag("ExperimentalUxSwitch").assertIsOn()

        experimentalUI.value = false
    }

    @Test
    fun settingsScreen_soundSwitch_togglesOff() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        soundEnabled.value = true
        setContent { SettingsScreen(prefs) }
        settle()

        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.enableSound)))
        onNodeWithText(i18n(S.enableSound)).assertIsDisplayed()
        onNodeWithTag("SoundSwitch").assertIsOn()
        onNodeWithTag("SoundSwitch").performClick()
        settle()
        onNodeWithTag("SoundSwitch").assertIsOff()

        soundEnabled.value = true
    }

    @Test
    fun settingsScreen_displaysAccessPriceDataSwitch() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { SettingsScreen(prefs) }
        settle()
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.AccessPriceData)))
        onNodeWithText(i18n(S.AccessPriceData)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_identitySwitch_togglesOn() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        showIdentityPref.value = false
        setContent { SettingsScreen(prefs) }
        settle()

        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.enableIdentityMenu)))
        onNodeWithText(i18n(S.enableIdentityMenu)).assertIsDisplayed()
        onNodeWithTag("IdentitySwitch").assertIsOff()
        onNodeWithTag("IdentitySwitch").performClick()
        settle()
        onNodeWithTag("IdentitySwitch").assertIsOn()

        showIdentityPref.value = false
    }

    @Test
    fun settingsScreen_servicesSwitch_togglesOn() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        showTricklePayPref.value = false
        setContent { SettingsScreen(prefs) }
        settle()

        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.EnableServices)))
        onNodeWithText(i18n(S.EnableServices)).assertIsDisplayed()
        onNodeWithTag("ServicesSwitch").assertIsOff()
        onNodeWithTag("ServicesSwitch").performClick()
        settle()
        onNodeWithTag("ServicesSwitch").assertIsOn()

        showTricklePayPref.value = false
    }

    @Test
    fun settingsScreen_displaysNexaBlockchainSource() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { SettingsScreen(prefs) }
        settle()
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasTestTag("BlockchainSelectors"))
        onNodeWithText("Nexa").assertIsDisplayed()
    }

    // ======================================================================
    // Dev mode — conditional UI
    // ======================================================================

    @Test
    fun settingsScreen_devModeToggle_showsTNexaAndRNexaRows() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { SettingsScreen(prefs) }
        settle()

        // Before toggling dev mode, testnet/regtest rows should not exist
        onNodeWithText("TNexa").assertDoesNotExist()
        onNodeWithText("RNexa").assertDoesNotExist()

        // Toggle dev mode on via the switch
        onNodeWithTag("DevModeSwitch").performClick()
        settle()

        // Now TNexa and RNexa rows should appear
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText("TNexa"))
        onNodeWithText("TNexa").assertIsDisplayed()
        onNodeWithText("RNexa").assertIsDisplayed()

        // Toggle dev mode off — rows should disappear
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.enableDeveloperView)))
        onNodeWithTag("DevModeSwitch").performClick()
        settle()
        onNodeWithText("TNexa").assertDoesNotExist()
        onNodeWithText("RNexa").assertDoesNotExist()
    }

    @Test
    fun settingsScreen_devModeToggle_showsDevButtons() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { SettingsScreen(prefs) }
        settle()

        // Dev buttons absent before toggle
        onNodeWithText("Log Info").assertDoesNotExist()
        onNodeWithText("Reload Assets").assertDoesNotExist()
        onNodeWithText("Close P2P").assertDoesNotExist()
        onNodeWithText("Delete Headers").assertDoesNotExist()

        // Toggle dev mode on
        onNodeWithTag("DevModeSwitch").performClick()
        settle()

        // Dev buttons should now appear
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText("Log Info"))
        onNodeWithText("Log Info").assertIsDisplayed()
        onNodeWithText("Reload Assets").assertIsDisplayed()
        onNodeWithText("Close P2P").assertIsDisplayed()
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText("Delete Headers"))
        onNodeWithText("Delete Headers").assertIsDisplayed()

        // Toggle dev mode off — buttons disappear
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.enableDeveloperView)))
        onNodeWithTag("DevModeSwitch").performClick()
        settle()
        onNodeWithText("Log Info").assertDoesNotExist()
    }

    // ======================================================================
    // LocalCurrency — dropdown selection
    // ======================================================================

    @Test
    fun localCurrency_dropdownShowsAllCurrencies() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { LocalCurrency(prefs) }
        settle()

        onNodeWithTag("FiatCurrencyDropdown").performClick()
        settle()

        for (code in listOf("BRL", "CAD", "CNY", "EUR", "GBP", "JPY", "RUB", "USD", "XAU"))
        {
            onNodeWithTag("FiatCurrency_$code").assertIsDisplayed()
        }
    }

    @Test
    fun localCurrency_selectEurPersistsToPrefs() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { LocalCurrency(prefs) }
        settle()

        onNodeWithTag("FiatCurrencyDropdown").performClick()
        settle()
        onNodeWithTag("FiatCurrency_EUR").performClick()
        settle()

        assertEquals("EUR", prefs.getString(LOCAL_CURRENCY_PREF, ""))
    }

    @Test
    fun localCurrency_selectJpyPersistsToPrefs() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { LocalCurrency(prefs) }
        settle()

        onNodeWithTag("FiatCurrencyDropdown").performClick()
        settle()
        onNodeWithTag("FiatCurrency_JPY").performClick()
        settle()

        assertEquals("JPY", prefs.getString(LOCAL_CURRENCY_PREF, ""))
    }

    // ======================================================================
    // ConfirmAbove — additional tests
    // ======================================================================

    @Test
    fun confirmAbove_emptyInputDefaultsToZero() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { ConfirmAbove(prefs) }
        settle()

        onNodeWithTag("ConfirmAboveEntry").performTextClearance()
        onNodeWithTag("ConfirmAboveEntry").performTextInput("")
        settle()

        val stored = prefs.getString(CONFIRM_ABOVE_PREF, "unset") ?: "unset"
        assertEquals("0.00", stored)
    }

    // ======================================================================
    // BlockchainSource — standalone composable
    // ======================================================================

    @Test
    fun blockchainSource_displaysNexaChainName() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent {
            BlockchainSource(
                ChainSelector.NEXA, prefs,
                androidx.compose.ui.layout.HorizontalAlignmentLine { old, new -> maxOf(old, new) }
            )
        }
        settle()

        onNodeWithText("Nexa").assertIsDisplayed()
        onNodeWithText(i18n(S.only)).assertIsDisplayed()
        onNodeWithText(i18n(S.prefer)).assertIsDisplayed()
    }

    @Test
    fun blockchainSource_displaysTestnetChainName() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent {
            BlockchainSource(
                ChainSelector.NEXATESTNET, prefs,
                androidx.compose.ui.layout.HorizontalAlignmentLine { old, new -> maxOf(old, new) }
            )
        }
        settle()

        onNodeWithText("TNexa").assertIsDisplayed()
    }
}
