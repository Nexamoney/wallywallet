package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.NewAccountScreenUi2
import info.bitcoinunlimited.www.wally.ui2.NewAccountState
import info.bitcoinunlimited.www.wally.ui2.newAccountState
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

val TESTWALLET = "newAccountScreenTest"

fun setupTest(cs: ChainSelector, selectAccount: Boolean = true): Account
{
    wallyApp = CommonApp()
    wallyApp!!.onCreate()
    wallyApp!!.openAllAccounts()
    lateinit var account: Account
    runBlocking(Dispatchers.IO) {
        account = wallyApp!!.newAccount(TESTWALLET, 0U, "", cs)!!
    }
    if (selectAccount) setSelectedAccount(account)
    return account
}

@OptIn(ExperimentalTestApi::class)
class NewAccountScreenTestUi2
{

    @BeforeTest
    fun setUp() {
        if (platform().target == KotlinTarget.JVM)
            Dispatchers.setMain(StandardTestDispatcher())
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @AfterTest
    fun clean()
    {
        if (platform().target == KotlinTarget.JVM)
            Dispatchers.resetMain()
    }

    /** Test opening the new account screen */
    @Test
    fun newAccountScreenTest() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
        val account = setupTest(cs)

        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }
    }

    /** Test creating a Nexa account */
    @Test
    fun selectBlockchainAndCreateAccount() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
        val account = setupTest(cs)

        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
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

        onNodeWithText(i18n(S.createNewAccount)).assertExists()
        onNodeWithText(i18n(S.createNewAccount)).performClick()
        assertTrue { newAccountState.value == NewAccountState() }
    }

    /** Test changing the new account's name */
    @Test
    fun enterNameAndCreateAccount() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
        val account = setupTest(cs, false)
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }

        onNodeWithTag("AccountNameInput").assertExists()
        onNodeWithTag("AccountNameInput").performTextInput("account")

        onNodeWithText(i18n(S.createNewAccount)).assertExists()
        onNodeWithText(i18n(S.createNewAccount)).performClick()
        assertTrue { newAccountState.value == NewAccountState() }
    }

    /** Test preventing an overly long name */
    @Test
    fun tooLongAccountName() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
        val account = setupTest(cs, false)
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }

        onNodeWithTag("AccountNameInput").assertExists()
        onNodeWithTag("AccountNameInput").performTextInput("longaccountname")
    }

    /** Test specifying an account unlock PIN */
    @Test
    fun enterPinAndCreateAccount() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
        val account = setupTest(cs, false)
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }

        onNodeWithTag("NewAccountPinInput").assertExists()
        onNodeWithTag("NewAccountPinInput").performTextInput("1234")

        onNodeWithText(i18n(S.createNewAccount)).assertExists()
        onNodeWithText(i18n(S.createNewAccount)).performClick()

        assertTrue { newAccountState.value == NewAccountState() }
    }

    /** Test specifying a short PIN */
    @Test
    fun enterTooShortPin() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
        val account = setupTest(cs, false)
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }

        onNodeWithTag("NewAccountPinInput").assertExists()
        onNodeWithTag("NewAccountPinInput").performTextInput("12")
    }

    /** Test specifying an overly long PIN */
    @Test
    fun enterTooLongPin() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
        val account = setupTest(cs, false)
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }

        onNodeWithTag("NewAccountPinInput").assertExists()
        onNodeWithTag("NewAccountPinInput").performTextInput("1234567890")
    }

    @Test
    fun recoverAccountFromMnemonic() = runComposeUiTest {
        // TODO: figure out a way to assert previous tests before implementing this one...
    }
}