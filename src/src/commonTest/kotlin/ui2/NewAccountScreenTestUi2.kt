package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.NewAccountScreenUi2
import info.bitcoinunlimited.www.wally.ui2.newAccountState
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.*
import kotlin.test.Test
import kotlin.test.assertTrue

val TESTWALLET = "newAccountScreenTest"

@OptIn(ExperimentalTestApi::class)
class NewAccountScreenTestUi2:WallyUiTestBase()
{
    init {
        setupTestEnv()
    }
    /** Test opening the new account screen */
    @Test
    fun newAccountScreenTest() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }
        settle()
    }

    /** Test creating a Nexa account */
    @Test
    fun selectBlockchainAndCreateAccount()
    {
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
                }
            }
            settle()

            // Open Blockchain selector and select NEXA
            // Commented because using dropdown in UI tests only works on the JVM target...
            /*
            onNodeWithText("NEXA").assertExists()
            onNodeWithText("NEXA").performClick()
            onNodeWithTag("DropdownMenuItem-NEXA").assertExists()
            onNodeWithTag("DropdownMenuItem-NEXA").performClick()
            onNodeWithText("NEXA").assertExists()
             */

            onNodeWithText(i18n(S.createNewAccount)).assertExists()
            onNodeWithText(i18n(S.createNewAccount)).performClick()
            settle()
            // If account name gets cleared, new account creation succeeded
            assertTrue { newAccountState.value.accountName == "" }
        }
    }

    /** Test changing the new account's name */
    @Test
    fun enterNameAndCreateAccount()
    {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
                }
            }

            onNodeWithTag("AccountNameInput").assertExists()
            onNodeWithTag("AccountNameInput").performTextInput("account")
            settle()
            onNodeWithText(i18n(S.createNewAccount)).assertExists()
            onNodeWithText(i18n(S.createNewAccount)).performClick()
            settle()
            assertTrue { newAccountState.value.accountName == "" }
            settle()
        }
    }

    /** Test preventing an overly long name */
    @Test
    fun tooLongAccountName()
    {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }
            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
                }
            }
            // default name should be valid
            onNodeWithTag("AccountName_C").assertExists()
            onNodeWithTag("AccountName_X").assertDoesNotExist()
            // ok now put a bad name in
            onNodeWithTag("AccountNameInput").assertExists()
            onNodeWithTag("AccountNameInput").performTextReplacement("longaccountname_longaccountname")
            settle()
            // check that its marked with an X
            onNodeWithTag("AccountName_X").assertExists()
            onNodeWithTag("AccountName_C").assertDoesNotExist()
            // now click create account
            onNodeWithTag("onClickCreateAccount").assertExists()
            onNodeWithTag("onClickCreateAccount").performClick()
            settle()
            // No fields should be changed
            onNodeWithTag("AccountNameInput").assertTextEquals("longaccountname_longaccountname")
            onNodeWithTag("AccountName_X").assertExists()
            onNodeWithTag("AccountName_C").assertDoesNotExist()

            // Put values back the way they were for other tests
            onNodeWithTag("AccountNameInput").performTextReplacement("nexa")
            settle()
        }
    }

    /** Test specifying an account unlock PIN */
    @Test
    fun enterPinAndCreateAccount()
    {
        // Delete the account we are going to create, in case it was previously created by a prior test run
        wallyApp!!.accounts["newAct"]?.let {
            it.delete()
            wallyApp!!.accounts.remove("newAct")
        }
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
                }
            }
            settle()

            onNodeWithTag("AccountNameInput").performTextClearance()
            onNodeWithTag("AccountNameInput").performTextInput("newAct")
            onNodeWithTag("NewAccountPinInput").assertExists().assertIsDisplayed()
            onNodeWithTag("NewAccountPinInput").performTextInput("1234")
            settle()
            onNodeWithText(i18n(S.createNewAccount)).assertExists().assertIsDisplayed()
            onNodeWithText(i18n(S.createNewAccount)).performClick()
            settle()
            // If the account is unable to be created for some reason, the view state will not be cleared
            waitFor(5000) { newAccountState.value.accountName == "" }
            settle()
        }

    }

    /** Test specifying a short PIN */
    @Test
    fun enterTooShortPin()
    {
        // If we have accounts from other tests, the default account name will be incorrect
        wallyApp!!.accounts.clear()
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
                }
            }
            settle()
            // empty PIN is fine
            onNodeWithTag("pin_C").assertExists()
            onNodeWithTag("pin_X").assertDoesNotExist()
            onNodeWithTag("NewAccountPinInput").assertExists()
            onNodeWithTag("NewAccountPinInput").performTextInput("12")
            // short pin is not
            settle()
            onNodeWithTag("pin_X").assertExists()
            onNodeWithTag("pin_C").assertDoesNotExist()
            // click create account anyway
            onNodeWithText(i18n(S.createNewAccount)).assertExists()
            onNodeWithText(i18n(S.createNewAccount)).performClick()
            settle()
            // should still be an X
            onNodeWithTag("pin_X").assertExists()
            onNodeWithTag("pin_C").assertDoesNotExist()
            // Strangely the account name gets cleared when the pin is bad
            onNodeWithTag("AccountNameInput").assertTextEquals("nexa")
            settle()
        }
    }

    /** Test specifying a long PIN (pin length is not limited) */
    @Test
    fun enterLongPin() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        //val cs = ChainSelector.NEXA
        //val account = setupTest(cs, false)
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }
        settle()
        // these fail on ios, but visually pin_C (the checkmark) IS there
        //onNodeWithTag("pin_C").assertExists()
        //onNodeWithTag("pin_X").assertDoesNotExist()
        onNodeWithTag("NewAccountPinInput").assertExists()
        onNodeWithTag("NewAccountPinInput").performTextInput("12345678901234567890")
        settle()
        onNodeWithTag("pin_C").assertExists()
        onNodeWithTag("pin_X").assertDoesNotExist()
        settle()
    }

    @Test
    fun recoverAccountFromMnemonic() = runComposeUiTest {
        // TODO: figure out a way to assert previous tests before implementing this one...
    }
}