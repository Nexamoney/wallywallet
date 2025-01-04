package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.NewAccountScreen
import info.bitcoinunlimited.www.wally.ui.NewAccountState
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.newAccountState
import info.bitcoinunlimited.www.wally.ui2.NewAccountScreenUi2
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

@OptIn(ExperimentalTestApi::class)
class NewAccountScreenTestUi2
{

    @BeforeTest
    fun setUp() {
        // jvm only
        if (platform().usesMouse)
            Dispatchers.setMain(StandardTestDispatcher())
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @AfterTest
    fun clean()
    {
        // jvm only
        if (platform().usesMouse)
            Dispatchers.resetMain()
    }

    @Test
    fun newAccountScreenTest() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("NewAccountScreenTest", 0U, "", cs)!!
        }

        /*
            Set selected account to populate the UI
        */
        setSelectedAccount(account)

        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NewAccountScreenUi2(accountGuiSlots.collectAsState(), false)
            }
        }
    }

    @Test
    fun selectBlockchainAndCreateAccount() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!
        }

        /*
            Set selected account to populate the UI
        */
        setSelectedAccount(account)

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

    @Test
    fun enterNameAndCreateAccount() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!
        }

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

    @Test
    fun tooLongAccountName() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!
        }

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

    @Test
    fun enterPinAndCreateAccount() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!
        }

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

    @Test
    fun enterTooShortPin() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!
        }

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

    @Test
    fun enterTooLongShortPin() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!
        }

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