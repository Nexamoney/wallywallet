package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SendScreenTest
{
    @BeforeTest
    fun setUp() {
        // JVM only or this fails android
        if (platform().usesMouse)
            Dispatchers.setMain(UnconfinedTestDispatcher())
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @AfterTest
    fun tearDown() {
        // JVM only or this fails android
        if (platform().usesMouse)
            Dispatchers.resetMain()
    }

    @Test
    fun sendScreenContentTest() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore = ViewModelStore()
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
        val balanceViewModel = BalanceViewModelFake()

        /*
            Set selected account to populate the UI
         */
        setSelectedAccount(account)
        val sendScreenViewModel = SendScreenViewModelFake(account)
        val syncViewModel = SyncViewModelFake()

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                SendScreenContent(sendScreenViewModel, balanceViewModel, syncViewModel, SendScreenNavParams())
            }
        }

        // Input a mock address into to the text input field and assert that it is displayed
        val toAddress = "nexa:nqtsq5g55t9699mcue00frjqql5275r3et45c3dqtxzfz8ru"
        onNodeWithTag("sendToAddress").assertIsDisplayed()
        onNodeWithTag("sendToAddress").assertIsFocused()
        onNodeWithTag("sendToAddress").performTextInput(toAddress)
        onNodeWithTag("sendToAddress").assertTextContains(toAddress)

        // Input a note into the text input field and assert that is it displayed
        val note = "To: Mom. Merry Xmas."
        onNodeWithTag("noteInput").assertIsDisplayed()
        onNodeWithTag("noteInput").requestFocus()
        onNodeWithTag("noteInput").assertIsFocused()
        onNodeWithTag("noteInput").performTextInput(note)
        onNodeWithTag("noteInput").assertTextContains(note)

        // Input an amount to send into the text input field and assert that it is displayed
        val amount = "1337"
        onNodeWithTag("amountToSendInput").assertIsDisplayed()
        onNodeWithTag("amountToSendInput").requestFocus()
        onNodeWithTag("amountToSendInput").assertIsFocused()
        onNodeWithTag("amountToSendInput").performTextInput(amount)
        onNodeWithTag("amountToSendInput").assertTextContains(amount)

        // TODO: Figure out how to clear focus or hide the soft keyboard and click the send button
        onNodeWithTag("SendScreenContentColumn").performClick()
        // onNodeWithText("Send").assertIsDisplayed()
        // onNodeWithText("Cancel").assertIsDisplayed()

        val uiState = sendScreenViewModel.uiState.value
        println(uiState)
    }

    @Test
    fun sendBottomButtonsTest() = runComposeUiTest {
        initializeLibNexa()
        val cs = ChainSelector.NEXA
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("itemvie", 0U, "", cs)!!
        }

        val viewModel = SendScreenViewModelFake(account)

        setContent {
            SendBottomButtons(Modifier, viewModel)
        }

        onNodeWithText(i18n(S.confirmSend)).assertDoesNotExist()
        onNodeWithText(i18n(S.Send)).assertIsDisplayed()
        onNodeWithText(i18n(S.SendCancel)).assertIsDisplayed()

        onNodeWithText(i18n(S.Send)).performClick()
        onNodeWithText(i18n(S.SendCancel)).performClick()

        assertEquals(viewModel.uiState.value.note, SendScreenUi().note)
        assertEquals(viewModel.uiState.value.amount, SendScreenUi().amount)
        assertEquals(viewModel.uiState.value.toAddress, SendScreenUi().toAddress)
        assertEquals(viewModel.uiState.value.amountFinal, SendScreenUi().amountFinal)
        assertEquals(viewModel.uiState.value.currencyCode, SendScreenUi().currencyCode)
        assertEquals(viewModel.uiState.value.fiatAmount, SendScreenUi().fiatAmount)
        assertEquals(viewModel.uiState.value.isConfirming, SendScreenUi().isConfirming)
        assertEquals(viewModel.uiState.value.toAddressFinal, SendScreenUi().toAddressFinal)
    }
}