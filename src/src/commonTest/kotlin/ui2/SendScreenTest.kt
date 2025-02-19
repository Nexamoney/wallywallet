package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.AssetListItemEditable
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui2.ConfirmSend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import info.bitcoinunlimited.www.wally.ui2.WallyNumericInputFieldAsset
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SendScreenTest:WallyUiTestBase()
{
    val cs = ChainSelector.NEXA

    @Test
    fun sendScreenContentTest()
    {
        wallyApp!!.openAllAccounts()
        val account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore = ViewModelStore()
            }

            setSelectedAccount(account) // Set selected account to populate the UI
            val sendScreenViewModel = SendScreenViewModelFake(account)

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    SendScreenContent(sendScreenViewModel, SendScreenNavParams())
                }
            }
            settle()

            // Input a mock address into to the text input field and assert that it is displayed
            val toAddress = "nexa:nqtsq5g55t9699mcue00frjqql5275r3et45c3dqtxzfz8ru"
            onNodeWithTag("sendToAddress").assertIsDisplayed()
            onNodeWithTag("sendToAddress").assertIsFocused()
            onNodeWithTag("sendToAddress").performTextInput(toAddress)
            settle()
            onNodeWithTag("sendToAddress").assertTextContains(toAddress)

            // Input a note into the text input field and assert that is it displayed
            val note = "To: Mom. Merry Xmas."
            onNodeWithTag("noteInput").assertIsDisplayed()
            onNodeWithTag("noteInput").requestFocus()
            onNodeWithTag("noteInput").assertIsFocused()
            onNodeWithTag("noteInput").performTextInput(note)
            settle()
            onNodeWithTag("noteInput").assertTextContains(note)

            // Input an amount to send into the text input field and assert that it is displayed
            val amount = "1337"
            onNodeWithTag("amountToSendInput").assertIsDisplayed()
            onNodeWithTag("amountToSendInput").requestFocus()
            settle()
            onNodeWithTag("amountToSendInput").assertIsFocused()
            onNodeWithTag("amountToSendInput").performTextInput(amount)
            onNodeWithTag("amountToSendInput").assertTextContains(amount)

            // TODO: Figure out how to clear focus or hide the soft keyboard and click the send button
            onNodeWithTag("SendScreenContentColumn").performClick()
            settle()
            // onNodeWithText("Send").assertIsDisplayed()
            // onNodeWithText("Cancel").assertIsDisplayed()

            val uiState = sendScreenViewModel.uiState.value
            println(uiState)
            settle()
        }
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun sendBottomButtonsTest()
    {
        val account = wallyApp!!.newAccount("sendBottomButtonsTest", 0U, "", cs)!!
        runComposeUiTest {
            val viewModel = SendScreenViewModelFake(account)

            setContent {
                SendBottomButtons(Modifier, viewModel)
            }
            settle()

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
            settle()
        }
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun assetListItemEditableTest() = runComposeUiTest {
        // Mock asset per account
        val groupIdData = ByteArray(520, { it.toByte() })
        val groupId = GroupId(ChainSelector.NEXA, groupIdData)
        val assetInfo = AssetInfo(groupId)
        val title = "title"
        val series = "series"
        assetInfo.nft = NexaNFTv2("niftyVer", title, series, "author", listOf(), "appUri","info")
        val assetAmount = 600L
        val groupInfo = GroupInfo(groupId, assetAmount)
        val assetPerAccount = AssetPerAccount(groupInfo, assetInfo, null)
        setContent {
            AssetListItemEditable(assetPerAccount, true)
        }
        settle()

        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(series).assertIsDisplayed()
        settle()
    }

    @Test
    fun wallyNumericInputFieldAssetTest() = runComposeUiTest {
        // Mock asset per account
        val groupIdData = ByteArray(520, { it.toByte() })
        val groupId = GroupId(ChainSelector.NEXA, groupIdData)
        val assetInfo = AssetInfo(groupId)
        val title = "title"
        val series = "series"
        assetInfo.nft = NexaNFTv2("niftyVer", title, series, "author", listOf(), "appUri","info")
        val assetAmount = 600L
        val groupInfo = GroupInfo(groupId, assetAmount)
        val assetPerAccount = AssetPerAccount(groupInfo, assetInfo, null)

        var quantity by mutableStateOf(assetPerAccount.editableAmount?.toPlainString() ?: "600")
        val placeholder = "placeholder string"
        val readOnly = false

        setContent {
            WallyNumericInputFieldAsset(quantity, "label",placeholder, isReadOnly = readOnly, onValueChange =  {
                quantity = it
                if (it.isEmpty())
                    assetPerAccount.editableAmount = BigDecimal.ZERO
                else
                    assetPerAccount.editableAmount = assetPerAccount.tokenDecimalFromString(it)
            })
        }
        settle()
        // Updates the amount in the text field
        val inputTag = "WallyNumericInputFieldAsset"
        onNodeWithTag(inputTag).assertIsDisplayed()
        onNodeWithTag(inputTag).requestFocus()
        onNodeWithTag(inputTag).assertIsFocused()
        onNodeWithTag(inputTag).performTextClearance()
        onNodeWithTag(inputTag).performTextInput("100")
        onNodeWithTag(inputTag).assertTextContains("100")
        assertEquals(quantity, "100")
        settle()
    }

    @Test
    fun confirmSendTest()
    {
        setupApp()
        val account = wallyApp!!.newAccount("testAcc", 0U, "", cs)!!
        runComposeUiTest {
            setSelectedAccount(account)
            val viewModel = SendScreenViewModelFake(account)

            setContent {
                ConfirmSend(viewModel)
            }
            settle()

            onNodeWithText(i18n(S.confirmSend)).assertIsDisplayed()

            // TODO: Verify that uiState values such as toAddress is displayed:
            // val toAddress = viewModel.uiState.value.toAddress
            // onNodeWithText(toAddress).assertIsDisplayed()

            // TODO: mock assetsToSend and verify that it is displayed
            val assetsToSend = viewModel.assetsToSend.value.size
            if (assetsToSend > 0)
                onNodeWithText(assetsToSend.toString()).assertExists()
            settle()
        }
        wallyApp!!.deleteAccount(account)
    }
}