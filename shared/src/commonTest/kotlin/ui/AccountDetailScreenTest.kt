package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.util.fastJoinToString
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AccountDetailScreen
import info.bitcoinunlimited.www.wally.ui.AccountStatisticsViewModelFake
import info.bitcoinunlimited.www.wally.ui.VisualOverflowKey
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModelFake
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.UnsecuredSecret
import org.nexa.libnexakotlin.decodeUtf8
import org.nexa.libnexakotlin.encodeUtf8
import org.nexa.libnexakotlin.englishWordList
import org.nexa.libnexakotlin.generateBip39Seed
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AccountDetailScreenTest:WallyUiTestBase()
{
    @Test
    fun viewPinTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("viewPinTest", 0U, "", cs)!!
        try
        {

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
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun setAndChangePinTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("setAndChangePinTest", 0U, "", cs)!!
        try
        {

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
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun viewRecoveryPhraseAndCopyToClipBoardTestMockAccount()
    {
        secureViewEnabled = false

        listOf(ChainSelector.NEXATESTNET, ChainSelector.NEXA).forEach { cs ->
            val account = mockAccount(chainSelector = cs)
            try
            {
            val mnemonic = account.getRecoveryPhrase()
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

            }
            finally
            {
                wallyApp!!.deleteAccount(account)
            }
        }
    }

    @Test
    fun viewBiggestRecoveryPhrase()
    {
        var longestWord = "mushroom"
        // We don't really need to do this every time since the longest word was calced as "mushroom".  But maybe font changes or we want to
        // support non-english words lists in the future.
        androidx.compose.ui.test.v2.runComposeUiTest {
            val words = englishWordList

            setContent {
                Column {
                    words.forEach { word ->
                        BasicText(
                          text = word,
                          modifier = Modifier.testTag(word)
                        )
                    }
                }
            }

            longestWord = words.maxBy { word ->
                val ret = onNodeWithTag(word)
                  .fetchSemanticsNode().size.width
                // println("$word: $ret")
                ret
            }
        }
        println("Longest rendered word: $longestWord")

        secureViewEnabled = false

        listOf(ChainSelector.NEXATESTNET).forEach { cs ->
            val account = mockAccount(chainSelector = cs)
            account.wallet.secretWords = UnsecuredSecret(List(12) { longestWord }.joinToString(" ").encodeUtf8())
            account.wallet.secret = UnsecuredSecret(generateBip39Seed(account.wallet.secretWords.getSecret().decodeUtf8(), ""))
            try
            {
                val mnemonic = account.getRecoveryPhrase()
                val mnemonicFormatted = mnemonic.split(" ").chunked(4).fastJoinToString("\n") { it.fastJoinToString(" ") }

                androidx.compose.ui.test.v2.runComposeUiTest {
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
                    settle()
                    onNodeWithText(i18n(S.recoveryPhrase)).assertIsDisplayed()

                    val node = onNodeWithTag("recoveryPhraseWords", true)

                    // Check that the box we display it in is not overflowing its parent
                    val semnode = node.fetchSemanticsNode()
                    val root = onRoot().fetchSemanticsNode()
                    val bounds = semnode.boundsInRoot
                    val rootBounds = root.boundsInRoot
                    assertTrue(bounds.left >= rootBounds.left &&
                      bounds.top >= rootBounds.top &&
                      bounds.right <= rootBounds.right &&
                      bounds.bottom <= rootBounds.bottom)

                    // This is really the important one for text display.
                    // Check that we didn't mark it as having visual overflow during text layout.
                    node.assert(SemanticsMatcher.expectValue(VisualOverflowKey, false))
                }
            }
            finally
            {
                wallyApp!!.deleteAccount(account)
            }
        }
    }

    @Test
    fun viewRecoveryPhraseAndCopyToClipBoardTestRealAccount()
    {
        /**
         * Disabled for iOS because this test is failing a lot for the iosSimulatorArm64Test target
         * Check out the logs here: https://gitlab.com/wallywallet/wallet/-/work_items/560
         */
        if (platform().target == KotlinTarget.iOS) return
        secureViewEnabled = false

        listOf(ChainSelector.NEXATESTNET, ChainSelector.NEXA).forEach { cs ->
            val account = wallyApp!!.newAccount("viewRecoveryPhraseAndCopyToClipBoardTest", 0U, "", cs)!!
            try
            {
            val mnemonic = account.getRecoveryPhrase()
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

            }
            finally
            {
                wallyApp!!.deleteAccount(account)
                // Deletion is async; the next loop iteration recreates the same name, so wait for
                // Phase B to release the wallet files before reusing it.
                awaitDeletionsComplete()
            }
        }
    }
}