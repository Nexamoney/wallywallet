package ui2

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.SendBottomButtons
import info.bitcoinunlimited.www.wally.ui2.SendScreenUi
import info.bitcoinunlimited.www.wally.ui2.SendScreenViewModelImpl
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SendScreenTestUi2:WallyUiTestBase()
{
    @Test
    fun sendBottomButtonsTest()
    {
        wallyApp!!.openAllAccounts()
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("itemvie", 0U, "", cs)!!

        runComposeUiTest {
            val viewModel = SendScreenViewModelImpl(account)
            setContent {
                SendBottomButtons(Modifier, viewModel)
            }
            settle()
            onNodeWithText(i18n(S.confirmSend)).assertDoesNotExist()
            onNodeWithText(i18n(S.Send)).assertIsDisplayed()
            onNodeWithText(i18n(S.SendCancel)).assertIsDisplayed()

            onNodeWithText(i18n(S.Send)).performClick()
            settle()
            onNodeWithText(i18n(S.SendCancel)).performClick()
            settle()
            assertEquals(viewModel.uiState.value.note, SendScreenUi().note)
            assertEquals(viewModel.uiState.value.amount, SendScreenUi().amount)
            assertEquals(viewModel.uiState.value.toAddress, SendScreenUi().toAddress)
            assertEquals(viewModel.uiState.value.amountFinal, SendScreenUi().amountFinal)
            assertEquals(viewModel.uiState.value.currencyCode, SendScreenUi().currencyCode)
            assertEquals(viewModel.uiState.value.fiatAmount, SendScreenUi().fiatAmount)
            assertEquals(viewModel.uiState.value.isConfirming, SendScreenUi().isConfirming)
            assertEquals(viewModel.uiState.value.toAddressFinal, SendScreenUi().toAddressFinal)
            settle()
        }
        wallyApp!!.deleteAccount(account)
    }
}