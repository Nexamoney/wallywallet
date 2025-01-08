package ui2

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.SendBottomButtons
import info.bitcoinunlimited.www.wally.ui2.SendScreenUi
import info.bitcoinunlimited.www.wally.ui2.SendScreenViewModelImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SendScreenTestUi2
{
    @BeforeTest
    fun init()
    {
        // jvm only
        if (platform().usesMouse)
            Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun after()
    {
        // jvm only
        if (platform().usesMouse)
            Dispatchers.resetMain()
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

        val viewModel = SendScreenViewModelImpl(account)

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