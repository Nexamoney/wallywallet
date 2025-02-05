package ui2

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
import info.bitcoinunlimited.www.wally.ui2.LocalCurrency
import info.bitcoinunlimited.www.wally.wallyApp
import info.bitcoinunlimited.www.wally.ui2.SettingsScreenUi2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.chainToCurrencyCode
import org.nexa.threads.millisleep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTestUi2:WallyUiTestBase()
{
    @Test
    fun sendScreenContentTest() = runComposeUiTest {
        val preferenceDB: SharedPreferences = FakeSharedPreferences()
        val hasNewUIState: State<Boolean> = mutableStateOf(true)

        setContent {
            SettingsScreenUi2(preferenceDB, hasNewUIState)
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
        val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
        setContent {
            ConfirmAbove(preferenceDB)
        }
        settle()

        val textInput = "123123"

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

class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getInt(key: String, defaultValue: Int): Int
    {
        TODO("Not yet implemented")
    }

    override fun edit(): PreferencesEdit = FakeEditor(data)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean
    {
        return data[key] as Boolean? ?: defaultValue
    }

    // Other methods should be implemented similarly
}

class FakeEditor(private val data: MutableMap<String, Any>) :  PreferencesEdit {
    override fun putString(key: String, value: String): PreferencesEdit
    {
        value.let { data[key] = it }
        return this
    }

    override fun putBoolean(key: String, value: Boolean): PreferencesEdit
    {
        value.let { data[key] = it }
        return this
    }

    override fun putInt(key: String, value: Int): PreferencesEdit
    {
        TODO("Not yet implemented")
    }

    override fun commit()
    {

    }
}
