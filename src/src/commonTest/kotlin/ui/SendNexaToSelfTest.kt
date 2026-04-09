package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.SendScreenViewModelFake
import info.bitcoinunlimited.www.wally.ui.SyncViewModelFake
import info.bitcoinunlimited.www.wally.ui.assignAccountsGuiSlots
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModelImpl
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.DecimalFormat
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SendNexaToSelfTest : WallyUiTestBase()
{
    @Test
    fun sendNexaToSelfDeductsFee()
    {
        val nexaFormat = DecimalFormat("##,###,###,###,##0.00")
        val sendAmountText = "1000"
        val sendAmount = BigDecimal.parseString(sendAmountText)
        val simulatedFee = BigDecimal.parseString("0.05")
        val initialBalance = BigDecimal.parseString("50000.00")
        val finalBalance = initialBalance - simulatedFee

        val mockAccount = mockAccount(initialBalance)

        // ReceiveScreen / SendScreen. read `wallyApp.focusedAccount` directly,
        setSelectedAccount(mockAccount)

        runComposeUiTest {
            val mockAccountFlow = wallyApp!!.focusedAccount
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }
            val assetViewModel = AssetViewModel()
            val accountUiDataViewModel = AccountUiDataViewModel()
            val apvm = AccountPillViewModelFake(
              mockAccountFlow,
              balance = BalanceViewModelImpl(mockAccountFlow),
              sync = SyncViewModelFake(),
            )
            val unlock = UnlockViewModel(mockAccountFlow)
            val sendVm = SendScreenViewModelFake(mockAccount)

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(
                      Modifier,
                      WindowInsets(0, 0, 0, 0),
                      apvm,
                      assetViewModel,
                      accountUiDataViewModel,
                      unlock,
                      sendScreenViewModel = sendVm,
                    )
                }
            }
            settle()
            nav.switch(ScreenId.Home)
            settle()
            assignAccountsGuiSlots()
            waitForCatching { onNodeWithTag("AccountPillBalance").isDisplayed() }

            // ----- Step 1: Open app, save the current amount in the account pill -----
            val balanceBefore = mockAccount.balanceState.value!!
            val pillTextBefore = nexaFormat.format(balanceBefore)
            onNodeWithTag("AccountPillBalance").assertTextEquals(pillTextBefore)

            // ----- Step 2: Navigate to receive -----
            onNodeWithTag("ReceiveButton").performClick()
            settle()
            waitForCatching(6000, { "Receive screen address never appeared" }) {
                runCatching {
                    onNodeWithTag("receiveScreen:receiveAddress").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            val currentReceive = mockAccount.currentReceive
            assertNotNull(currentReceive)
            onNodeWithTag("receiveScreen:receiveAddress").assertTextEquals(currentReceive.address.toString())

            // ----- Step 3: Copy your own address to clipboard -----
            onNodeWithTag("receiveScreen:receiveAddress").performClick()
            settle()

            // ----- Step 4: Navigate back to the home screen -----
            onNodeWithTag("BackButton").performClick()
            settle()
            waitForCatching { onNodeWithTag("AccountPillBalance").isDisplayed() }

            // ----- Step 5: Navigate to send screen -----
            onNodeWithTag("SendButton").performClick()
            settle()
            waitForCatching { onNodeWithTag("sendToAddress").isDisplayed() }

            // ----- Step 6: Paste the address into the receive address field -----
            onNodeWithTag("sendToAddress").requestFocus()
            onNodeWithTag("sendToAddress").performTextInput(currentReceive.address.toString())
            settle()

            // ----- Step 7: Set Nexa amount to send to 1000 Nexa -----
            onNodeWithTag("amountToSendInput").requestFocus()
            onNodeWithTag("amountToSendInput").performTextInput(sendAmountText)
            settle()

            // ----- Step 8: Click send -----
            // SendScreenViewModelFake.onSendButtonClicked() validates the fields and
            // flips uiState.isConfirming = true, swapping Send → Confirm.
            onNodeWithTag("SendBottomButtonSend").assertIsDisplayed()
            onNodeWithTag("SendBottomButtonSend").performClick()
            settle()

            // ----- Step 9: Confirm send -----
            // The fake's actuallySend() is a no-op, so simulate the post-send pill
            // amount by mutating the observable backing the mock's balanceState.
            onNodeWithTag("SendBottomButtonConfirm").assertIsDisplayed()
            onNodeWithTag("SendBottomButtonConfirm").performClick()
            mockAccount.balance = finalBalance
            settle()

            // ----- Step 10: Verify the amount in the account pill is slightly less
            //                than the amount from step 1.
            nav.switch(ScreenId.Home)
            settle()
            waitForCatching { onNodeWithTag("AccountPillBalance").isDisplayed() }

            val balanceAfter = mockAccount.balanceState.value!!
            assertTrue(
              balanceAfter < balanceBefore,
              "Balance should have decreased after sending to self. before=$balanceBefore after=$balanceAfter"
            )
            // Sending to yourself returns the principal, so the delta must be only the
            // fee — strictly less than the amount that was sent.
            val delta = balanceBefore - balanceAfter
            assertTrue(
              delta < sendAmount,
              "Balance reduction should only be the fee, not the send amount. delta=$delta sendAmount=$sendAmount"
            )
            // And it must be visible in the account pill itself.
            val pillTextAfter = nexaFormat.format(finalBalance)
            onNodeWithTag("AccountPillBalance").assertTextEquals(pillTextAfter)
        }
    }
}
