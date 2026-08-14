package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModelImpl
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import org.nexa.libnexakotlin.CurrencyDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * System level test of the "confirm above" preference: what [ConfirmAbove] writes in the settings
 * screen has to decide whether [SendScreenViewModelImpl.onSendButtonClicked] stops at the confirm
 * step or sends straight away.  Both screens are driven through [NavigationRoot], so the preference
 * travels the same path it does for a user: settings screen -> preference db -> send screen.
 */
@OptIn(ExperimentalTestApi::class)
class ConfirmAbovePreferenceTest: WallyUiTestBase()
{
    val sendAmount = "100000"
    val priorConfirmAbove = wallyApp!!.preferenceDB.getString(CONFIRM_ABOVE_PREF, "0") ?: "0"
    val priorSelectedAccount = wallyApp!!.focusedAccount.value
    val priorSelectedAccountName = wallyApp!!.preferenceDB.getString(SELECTED_ACCOUNT_NAME_PREF, "") ?: ""

    @AfterTest
    fun restoreGlobals()
    {
        // nav, the focused account and the preference db all outlive the test.  Leaving nav on Send
        // makes the next test class that composes NavigationRoot render SendScreen against this
        // mock account, whose wallet is torn down by then -- an NPE in checkForBlockchainConnection.
        nav.switch(ScreenId.Home)
        wallyApp!!.focusedAccount.value = priorSelectedAccount
        with(wallyApp!!.preferenceDB.edit())
        {
            putString(CONFIRM_ABOVE_PREF, priorConfirmAbove)
            putString(SELECTED_ACCOUNT_NAME_PREF, priorSelectedAccountName)
            commit()
        }
        clearAlerts()
    }

    @Test
    fun confirmScreenIsDisplayedAboveTheLimit()
    {
        // Android draws the platform's title bar, which has no settings gear to click
        if (platform().hasNativeTitleBar) return

        sendFromHomeScreen("10000") { viewModel ->
            waitForCatching { onNodeWithTag("SendBottomButtonConfirm").isDisplayed() }
            onNodeWithTag("SendBottomButtonConfirm").assertIsDisplayed()
            onAllNodesWithText(i18n(S.confirmSend)).onFirst().assertIsDisplayed()
            assertTrue(viewModel.uiState.value.isConfirming)
        }
    }

    @Test
    fun confirmScreenIsNotDisplayedBelowTheLimit()
    {
        if (platform().hasNativeTitleBar) return

        // Same amount as above, only the limit moves: below it the send is attempted immediately.
        // That send fails on the mock account's wallet, which does not matter here -- the point is
        // that the confirm step was never shown.
        sendFromHomeScreen("1000000") { viewModel ->
            onNodeWithTag("SendBottomButtonConfirm").assertDoesNotExist()
            assertTrue(onAllNodesWithText(i18n(S.confirmSend)).fetchSemanticsNodes().isEmpty())
            assertFalse(viewModel.uiState.value.isConfirming)
        }
    }

    /**
     * Sets [confirmAboveLimit] in the settings screen, navigates back home, and sends [sendAmount]
     * to the account's own address.  [verify] runs right after the send button is clicked.
     */
    private fun sendFromHomeScreen(confirmAboveLimit: String, verify: ComposeUiTest.(SendScreenViewModel) -> Unit)
    {
        // The real view model refuses to send more than the wallet holds, so the wallet needs a
        // balance covering [sendAmount] (mock accounts use 100 finest units per coin).
        val account = mockAccount(initialBalance = BigDecimal.parseString("1000000.00"), walletBalance = 100_000_000L)
        assignAccountsGuiSlots()
        setSelectedAccount(account)

        runComposeUiTest(testTimeout = 3.minutes) {
            val accountFlow = wallyApp!!.focusedAccount
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }
            val unlock = UnlockViewModel(accountFlow)
            val sendViewModel = SendScreenViewModelImpl(account, unlock)
            val apvm = AccountPillViewModelFake(
              accountFlow,
              balance = BalanceViewModelImpl(accountFlow),
              sync = SyncViewModelFake(),
            )

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(
                      Modifier,
                      WindowInsets(0, 0, 0, 0),
                      apvm,
                      AssetViewModel(),
                      AccountUiDataViewModel(),
                      unlock,
                      sendScreenViewModel = sendViewModel,
                    )
                }
            }
            settle()
            nav.switch(ScreenId.Home)
            settle()

            // ----- Navigate to settings with the gear icon in the title bar -----
            waitForCatching { onNodeWithContentDescription("Settings", useUnmergedTree = true).isDisplayed() }
            onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
            settle()

            // ----- Set the confirm above limit -----
            waitForCatching { onNodeWithTag("ConfirmAboveEntry").isDisplayed() }
            onNodeWithTag("ConfirmAboveEntry").performTextClearance()
            onNodeWithTag("ConfirmAboveEntry").performTextInput(confirmAboveLimit)
            settle()
            // Stored formatted ("10000.00"), so compare the quantity rather than the text
            val storedLimit = wallyApp!!.preferenceDB.getString(CONFIRM_ABOVE_PREF, "0") ?: "0"
            assertEquals(0, CurrencyDecimal(storedLimit).compareTo(CurrencyDecimal(confirmAboveLimit)))

            // ----- Navigate back to the home screen -----
            onNodeWithTag("backButton").performClick()
            settle()

            // ----- Navigate to the send screen from the account pill -----
            waitForCatching { onNodeWithTag("SendButton").isDisplayed() }
            onNodeWithTag("SendButton").performClick()
            settle()

            // ----- Fill in the destination and the amount -----
            val ownAddress = account.currentReceive!!.address.toString()
            waitForCatching { onNodeWithTag("sendToAddress").isDisplayed() }
            onNodeWithTag("sendToAddress").requestFocus()
            onNodeWithTag("sendToAddress").performTextInput(ownAddress)
            onNodeWithTag("amountToSendInput").requestFocus()
            onNodeWithTag("amountToSendInput").performTextInput(sendAmount)
            settle()

            // ----- Send -----
            onNodeWithTag("SendBottomButtonSend").assertIsDisplayed()
            onNodeWithTag("SendBottomButtonSend").performClick()
            settle()

            verify(sendViewModel)
        }
    }
}
