package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest: WallyUiTestBase()
{
    @Test
    fun blockchainSelectorsDisplayedTest() = runComposeUiTest {
        // devMode is a process-wide global; reset it so test order doesn't matter.
        devMode = false
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

        // Leave the global the way we found it so we don't leak `devMode = true`
        // into tests that assume it starts off (e.g. settingsScreen_devModeSwitch_togglesOn).
        devMode = false
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

        onNodeWithText(i18n(S.CurrencySettings)).assertIsDisplayed()
        onNodeWithText(i18n(S.GeneralSettings)).assertIsDisplayed()
        onNodeWithText(i18n(S.BlockchainSettings)).assertIsDisplayed()
    }

    // ======================================================================
    // SettingsScreen — switch display
    // ======================================================================

    @Test
    fun settingsScreen_devModeSwitch_togglesOn() = runComposeUiTest {
        // devMode is a process-wide global. Force it off here so this test is
        // deterministic regardless of whether a prior test left it true.
        devMode = false
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
        // devMode is a process-wide global; reset it so test order doesn't matter.
        devMode = false
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
        // devMode is a process-wide global; reset it so test order doesn't matter.
        devMode = false
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

        for (code in FIAT_CURRENCIES)
        {
            onNodeWithTag("FiatCurrency_$code").assertIsDisplayed()
        }
    }

    /** selecting from the dropdown calls SetLocalCurrency, which updates the accounts and makes
     * requests to both hosts. Disable price data to prevent those requests. Restore the shared
     * globals and accounts after the test, since SetLocalCurrency clears fiatPerCoin anyway*/
    private fun withoutTouchingTheNetwork(body: () -> Unit)
    {
        val priorAccess = allowAccessPriceData
        val priorLocal = localCurrency
        val priorCode = fiatCurrencyCode
        val app = wallyApp
        val priorRates = app?.let { a -> a.accountLock.lock { a.accounts.values.toList() } }
          ?.map { it to it.fiatPerCoin } ?: listOf()
        allowAccessPriceData = false
        try
        {
            body()
        }
        finally
        {
            allowAccessPriceData = priorAccess
            localCurrency = priorLocal
            fiatCurrencyCode = priorCode
            priorRates.forEach { (act, rate) -> act.fiatPerCoin = rate }
        }
    }

    @Test
    fun localCurrency_showsTheDeviceDefaultWithoutSavingIt() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent { LocalCurrency(prefs) }
        settle()

        onNodeWithTag("SelectedFiatCurrency").assertTextEquals(deviceFiatCurrency())
        assertEquals(null, prefs.getString(LOCAL_CURRENCY_PREF, null))  // the startup read saves it, not the screen
    }

    @Test
    fun localCurrency_showsTheSavedCurrency() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(LOCAL_CURRENCY_PREF, "JPY").commit()
        setContent { LocalCurrency(prefs) }
        settle()

        onNodeWithTag("SelectedFiatCurrency").assertTextEquals("JPY")
    }

    @Test
    fun localCurrency_selectEurPersistsToPrefs() = runComposeUiTest {
        withoutTouchingTheNetwork {
            val prefs = FakeSharedPreferences()
            setContent { LocalCurrency(prefs) }
            settle()

            onNodeWithTag("FiatCurrencyDropdown").performClick()
            settle()
            onNodeWithTag("FiatCurrency_EUR").performClick()
            settle()

            assertEquals("EUR", prefs.getString(LOCAL_CURRENCY_PREF, ""))
            assertEquals("EUR", localCurrency)
            assertEquals("EUR", fiatCurrencyCode)
        }
    }

    @Test
    fun localCurrency_selectJpyPersistsToPrefs() = runComposeUiTest {
        withoutTouchingTheNetwork {
            val prefs = FakeSharedPreferences()
            setContent { LocalCurrency(prefs) }
            settle()

            onNodeWithTag("FiatCurrencyDropdown").performClick()
            settle()
            onNodeWithTag("FiatCurrency_JPY").performClick()
            settle()

            assertEquals("JPY", prefs.getString(LOCAL_CURRENCY_PREF, ""))
            assertEquals("JPY", localCurrency)
            assertEquals("JPY", fiatCurrencyCode)
        }
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

    /** A label too wide for the row must not starve the amount entry or the currency code (gitlab #651). */
    @Test
    fun confirmAbove_labelTooWideForRow_doesNotStarveEntryOrCurrencyCode() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent {
            Box(Modifier.width(140.dp)) { ConfirmAbove(prefs) }
        }
        settle()

        // width vs height comparison is independent of the platform's font metrics
        val code = chainToCurrencyCode[ChainSelector.NEXA]!!
        val codeBounds = onNodeWithText(code).getUnclippedBoundsInRoot()
        val codeWidth = codeBounds.right - codeBounds.left
        val codeHeight = codeBounds.bottom - codeBounds.top
        assertTrue(
          codeWidth > codeHeight,
          "currency code '$code' wrapped vertically: $codeWidth x $codeHeight"
        )

        onNodeWithTag("ConfirmAboveEntry").assertWidthIsAtLeast(32.dp)
    }

    /** The dropdown belongs at the right edge of the row like every other settings row (gitlab #651). */
    @Test
    fun localCurrency_spreadsLabelAndDropdownAcrossTheRow() = runComposeUiTest {
        val prefs = FakeSharedPreferences()
        setContent {
            Box(Modifier.width(300.dp)) { LocalCurrency(prefs) }
        }
        settle()

        val dropdown = onNodeWithTag("FiatCurrencyDropdown").getUnclippedBoundsInRoot()
        assertTrue(
          dropdown.right > 250.dp,
          "fiat dropdown did not reach the end of the row: right edge at ${dropdown.right} of 300.dp"
        )
    }
}
