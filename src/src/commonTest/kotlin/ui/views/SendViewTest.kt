package ui.views

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.views.SendView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import ui2.WallyUiTestBase
import ui2.settle
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalUnsignedTypes::class)
class SendViewTest:WallyUiTestBase()
{
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sendViewTest() = runComposeUiTest {
        val selectedAccountNameMock = "selectedAccountName"
        val selectedAccountNamesMock = listOf(selectedAccountNameMock)
        val toAddressMock = "toAddress"
        val noteMock = mutableStateOf("note")
        val sendQuantityMock = mutableStateOf("sendQuantity")
        val cs = ChainSelector.NEXA
        lateinit var account: Account

        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("itemvie", 0U, "", cs)!!
        }
        setContent {
            val currenciesMock: MutableState<List<String>> = mutableStateOf(listOf())
            SendView(
              selectedAccountName = selectedAccountNameMock,
              accountNames = selectedAccountNamesMock,
              toAddress = toAddressMock,
              note = noteMock,
              sendQuantity = sendQuantityMock,
              paymentInProgress = null,
              approximatelyText = "",
              xchgRateText = "",
              currencies = currenciesMock,
              setSendQuantity = {},
              setToAddress = {},
              onCancel = {},
              onPaymentInProgress = {},
              updateSendBasedOnPaymentInProgress = {},
              onApproximatelyText = {},
              checkSendQuantity = {_, _ -> },
              onSendSuccess = {},
              onAccountNameSelected = {},
              account = account
            )
        }

        /**
         * Check is basic UI elements are displayed
         */
        onNodeWithText(i18n(S.fromAccountColon)).isDisplayed()
        onNodeWithText(i18n(S.sendToAddressHint)).isDisplayed()
        onNodeWithText(i18n(S.Amount)).isDisplayed()

        /**
         * Click note button and check for UI changes
         */
        onNodeWithTag("noteButtonSendView").isDisplayed()
        onNodeWithTag("noteButtonSendView").performClick()
        settle()
        onNodeWithText(i18n(S.editSendNoteHint)).isDisplayed()
        settle()
    }
}