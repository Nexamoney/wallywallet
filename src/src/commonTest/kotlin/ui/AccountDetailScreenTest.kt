package ui

import androidx.compose.ui.test.*
import androidx.compose.ui.util.fastJoinToString
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AccountDetailScreen
import info.bitcoinunlimited.www.wally.ui.AccountStatisticsViewModelFake
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModelFake
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.decodeUtf8
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AccountDetailScreenTest:WallyUiTestBase()
{
    @Test
    fun viewPinTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!

        runComposeUiTest {
            /*
                Set selected account to populate the UI
            */
            setSelectedAccount(account)

            val ap = AccountPillViewModelFake(MutableStateFlow(account))
            val accountStatsViewModel = AccountStatisticsViewModelFake(account)

            setContent {
                AccountDetailScreen(accountStatsViewModel, ap)
            }
            settle()

            onNodeWithText(i18n(S.AutomaticNewAddress)).assertIsDisplayed()

            /**
             * Open change pin View and click cancel button to close it.
             */
            waitForCatching { onNodeWithText(i18n(S.AccountStatistics)).isDisplayed() }
            onNodeWithText(i18n(S.SetChangePin)).performClick()
            settle()
            waitForCatching { onNodeWithText(i18n(S.PinHidesAccount)).isDisplayed() }
            onNodeWithText(i18n(S.cancel)).performClick()
            settle()
        }
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun setAndChangePinTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!

        runComposeUiTest {
            /*
                Set selected account to populate the UI
            */
            setSelectedAccount(account)

            val ap = AccountPillViewModelFake(MutableStateFlow(account))
            val accountStatsViewModel = AccountStatisticsViewModelFake(account)

            setContent {
                AccountDetailScreen(accountStatsViewModel, ap)
            }
            settle()

            /**
             * Set pin to 777
             */
            waitForCatching { onNodeWithText(i18n(S.AccountStatistics)).isDisplayed() }
            onNodeWithText(i18n(S.SetChangePin)).performClick()
            settle()
            onNodeWithText(i18n(S.PinHidesAccount)).isDisplayed()
            onNodeWithText(i18n(S.EnterPINorBlankToRemove)).requestFocus()
            onNodeWithText(i18n(S.EnterPINorBlankToRemove)).performTextInput("7777")
            onNodeWithText(i18n(S.accept)).performClick()

            /**
             * Change pin to 8888
             */
            onNodeWithText(i18n(S.SetChangePin)).performClick()
            onNodeWithText(i18n(S.CurrentPin)).assertIsDisplayed()
            onNodeWithText(i18n(S.NewPin)).assertIsDisplayed()
            onNodeWithTag("CurrentPinCheckIcon").assertDoesNotExist()
            onNodeWithText(i18n(S.EnterPIN)).requestFocus()
            onNodeWithText(i18n(S.EnterPIN)).performTextInput("7777")

            onNodeWithText(i18n(S.EnterPINorBlankToRemove)).requestFocus()
            onNodeWithText(i18n(S.EnterPINorBlankToRemove)).performTextInput("8888")
            settle()
            onNodeWithTag("CurrentPinCheckIcon").assertIsDisplayed()
            onNodeWithText(i18n(S.accept)).performClick()
            onNodeWithText(i18n(S.SetChangePin)).assertIsDisplayed()
        }
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun viewRecoveryPhraseAndCopyToClipBoardTest()
    {
        secureViewEnabled = false

        listOf(ChainSelector.NEXATESTNET, ChainSelector.NEXA).forEach { cs ->
            val account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!
            val mnemonic = account.wallet.secretWords.getSecret().decodeUtf8()
            val mnemonicFormatted = mnemonic.split(" ").chunked(4).fastJoinToString("\n") { it.fastJoinToString(" ") }

            runComposeUiTest {
                // Set selected account to populate the UI
                setSelectedAccount(account)

                val ap = AccountPillViewModelFake(MutableStateFlow(account))
                val accountStatsViewModel = AccountStatisticsViewModelFake(account)

                setContent {
                    AccountDetailScreen(accountStatsViewModel, ap)
                }
                settle()

                onNodeWithText(i18n(S.ViewRecoveryPhrase)).assertIsDisplayed()
                onNodeWithText(i18n(S.ViewRecoveryPhrase)).performClick()
                onNodeWithText(i18n(S.recoveryPhrase)).assertIsDisplayed()
                onNodeWithText(mnemonicFormatted).assertIsDisplayed()
                onNodeWithText(mnemonicFormatted).performClick()
                if (devMode || (cs != ChainSelector.NEXA))
                {
                    onNodeWithText(i18n(S.PastingRecoveryPhraseIsBadIdea)).performScrollTo()
                    onNodeWithText(i18n(S.PastingRecoveryPhraseIsBadIdea)).assertIsDisplayed()
                }
                else
                {
                    // The code actually changes the color of this text when the recovery phrase is clicked.
                    // However testing that is onerous, so let us be content with verifying that it is displayed.
                    onNodeWithText(i18n(S.recoveryWarning)).performScrollTo()
                    onNodeWithText(i18n(S.recoveryWarning)).assertIsDisplayed()
                }
                onNodeWithText(i18n(S.WroteRecoveryPhraseDown)).performClick()
                onNodeWithText(i18n(S.ViewRecoveryPhrase)).assertIsDisplayed()
            }

            wallyApp!!.deleteAccount(account)
        }
    }
}