package ui

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui2.NewAccountState
import info.bitcoinunlimited.www.wally.ui2.ScreenNav
import info.bitcoinunlimited.www.wally.ui2.newAccountState
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import ui2.WallyUiTestBase
import ui2.settle
import ui2.setupTestEnv
import ui2.waitForCatching
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@kotlin.ExperimentalUnsignedTypes
class NewAccountScreenTest: WallyUiTestBase(false)
{
    val cs = ChainSelector.NEXA

    @Test
    fun selectBlockchainAndCreateAccount() = runComposeUiTest {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        setContent {
            NewAccountScreen(accountGuiSlots.collectAsState(), false, ScreenNav())
        }

        // Open Blockchain selector and select NEXA
        // Commented because using dropdown in UI tests only works on the JVM target...
        /*
        onNodeWithText("NEXA").assertExists()
        onNodeWithText("NEXA").performClick()
        onNodeWithTag("DropdownMenuItem-NEXA").assertExists()
        onNodeWithTag("DropdownMenuItem-NEXA").performClick()
        onNodeWithText("NEXA").assertExists()
         */

        waitForCatching {  onNodeWithText(i18n(S.createNewAccount)).isDisplayed() }
        onNodeWithText(i18n(S.createNewAccount)).performClick()
        assertTrue { newAccountState.value == NewAccountState() }
    }

    @Test
    fun enterNameAndCreateAccount() = runComposeUiTest {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        setContent {
            NewAccountScreen(accountGuiSlots.collectAsState(), false, ScreenNav())
        }

        waitForCatching {  onNodeWithTag("AccountNameInput").isDisplayed() }
        onNodeWithTag("AccountNameInput").performTextInput("account")

        onNodeWithText(i18n(S.createNewAccount)).assertExists()
        onNodeWithText(i18n(S.createNewAccount)).performClick()
        assertTrue { newAccountState.value == NewAccountState() }
    }

    @Test
    fun tooLongAccountName() = runComposeUiTest {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        setContent {
            NewAccountScreen(accountGuiSlots.collectAsState(), false, ScreenNav())
        }

        waitForCatching { onNodeWithTag("AccountNameInput").isDisplayed() }
        onNodeWithTag("AccountNameInput").performTextInput("longaccountname")
    }

    @Test
    fun enterPinAndCreateAccount() = runComposeUiTest {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        setContent {
            NewAccountScreen(accountGuiSlots.collectAsState(), false, ScreenNav())
        }
        settle()
        waitForCatching { onNodeWithTag("NewAccountPinInput").isDisplayed() }
        onNodeWithTag("NewAccountPinInput").performTextInput("1234")

        onNodeWithText(i18n(S.createNewAccount)).assertExists()
        onNodeWithText(i18n(S.createNewAccount)).performClick()

        assertTrue { newAccountState.value == NewAccountState() }
    }

    @Test
    fun enterTooShortPin() = runComposeUiTest {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        setContent {
            NewAccountScreen(accountGuiSlots.collectAsState(), false, ScreenNav())
        }
        settle()

        waitForCatching {  onNodeWithTag("NewAccountPinInput").isDisplayed() }
        onNodeWithTag("NewAccountPinInput").performTextInput("12")
    }

    @Test
    fun enterTooLongShortPin() = runComposeUiTest {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        setContent {
            NewAccountScreen(accountGuiSlots.collectAsState(), false, ScreenNav())
        }

        waitForCatching { onNodeWithTag("NewAccountPinInput").isDisplayed() }
        onNodeWithTag("NewAccountPinInput").performTextInput("1234567890")
    }

    @Test
    fun recoverAccountFromMnemonic() = runComposeUiTest {
        // TODO: figure out a way to assert previous tests before implementing this one...
    }
}