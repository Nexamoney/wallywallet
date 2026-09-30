package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AssetListItemViewOld
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.TxHistoryScreen
import info.bitcoinunlimited.www.wally.ui.txHistoryAccount
import info.bitcoinunlimited.www.wally.ui.txHistoryInfo
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.TransactionHistory
import org.nexa.libnexakotlin.txFor
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class TxHistoryScreenTest: WallyUiTestBase(false)
{
    /**
     * An account with no transactions: the screen's async calcTxHistoryInfo produces an all-null
     * array, so the "no activity" message replaces the list.
     */
    @Test
    fun emptyTxHistoryScreenTest() = runComposeUiTest {
        val account = mockAccount(chainSelector = ChainSelector.NEXA)
        try
        {
            setContent {
                TxHistoryScreen(account, ScreenNav())
            }

            waitForCatching { onNodeWithText(i18n(S.NoAccountActivity)).assertIsDisplayed() }
            onNodeWithTag("TxHistoryList").assertDoesNotExist()
        }
        finally
        {
            txHistoryInfo.value = null
            txHistoryAccount.value = null
            account.wallet.close()
        }
    }

    /**
     * Renders TxHistoryScreen with 100 mock transactions injected into txHistoryInfo, and verifies
     * that every 25th transaction's note displays after scrolling the list to it.
     */
    @Test
    fun txHistoryScreenWith100MockedTransactions() = runComposeUiTest(testTimeout = 2.minutes) {
        val cs = ChainSelector.NEXA
        val account = mockAccount(chainSelector = cs)

        // Newest first, the order calcTxHistoryInfo fills the array in. Dates must be after
        // Jan 1 2020 (1577836800000) or the screen logs them instead of rendering a date.
        val mockTxes = Array(100) { i ->
            val txh = TransactionHistory(cs, txFor(cs))
            txh.date = 2_000_000_000_000L - i * 86_400_000L
            // Alternate receive/send so both arrow branches render
            if (i % 2 == 0)
            {
                txh.incomingAmt = (i + 1) * 100L
                txh.outgoingAmt = 0L
            }
            else
            {
                txh.incomingAmt = 0L
                txh.outgoingAmt = (i + 1) * 100L
            }
            txh.note = "mock-note-$i"
            MutableStateFlow<TransactionHistory?>(txh)
        }

        // Injecting the state directly bypasses calcTxHistoryInfo, which would read the wallet DB
        txHistoryAccount.value = account
        txHistoryInfo.value = mockTxes

        try
        {
            setContent {
                TxHistoryScreen(account, ScreenNav())
            }

            waitForCatching { onNodeWithText("mock-note-0").assertIsDisplayed() }

            for (i in 10 until 100 step 25)
            {
                val noteText = "mock-note-$i"
                waitForCatching {
                    onNodeWithTag("TxHistoryList").performScrollToNode(hasText(noteText))
                    onNodeWithText(noteText).assertIsDisplayed()
                }
            }
        }
        finally
        {
            txHistoryInfo.value = null
            txHistoryAccount.value = null
            account.wallet.close()
        }
    }

    @Test
    fun assetRowShowsExplorerButton() = runComposeUiTest {
        val asset = createAssetPerAccount(createAssetInfo("explorerAsset", null), 5L)

        setContent {
            AssetListItemViewOld(asset, 0, false)
        }
        settle()

        onNodeWithTag("AssetExplorerButton").assertIsDisplayed().assertHasClickAction()
    }
}
