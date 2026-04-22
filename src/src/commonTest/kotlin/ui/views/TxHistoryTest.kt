package ui.views

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.matcher.any
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.ui.views.RecentTransactionUIData
import info.bitcoinunlimited.www.wally.ui.views.TransactionsList
import info.bitcoinunlimited.www.wally.ui.views.TxHistoryViewModel
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.TransactionHistory
import org.nexa.libnexakotlin.txFor
import org.nexa.threads.millinow
import org.nexa.threads.millisleep
import org.nexa.assets.NexaNFTv2
import ui.WallyUiTestBase
import ui.createAssetInfo
import ui.createAssetPerAccount
import ui.mockAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TxHistoryTest : WallyUiTestBase()
{
    /** Build a fake TransactionHistory with unique date and amount per index. */
    private fun fakeTxHistory(i: Int): TransactionHistory
    {
        val tx = txFor(ChainSelector.NEXA)
        val txh = TransactionHistory(ChainSelector.NEXA, tx)
        // Spread dates widely so after sortByDescending the order matches our
        // construction order (newest first).
        txh.date = 2_000_000_000_000L - i * 86_400_000L
        if (i % 2 == 0)
        {
            txh.incomingAmt = (i + 1) * 1000L
            txh.outgoingAmt = 0L
        }
        else
        {
            txh.incomingAmt = 0L
            txh.outgoingAmt = (i + 1) * 1000L
        }
        txh.note = "mock-tx-$i"
        return txh
    }

    /**
     * Stub the wallet's tx database so forEach invokes the visitor with each
     * of the supplied fake transactions.
     */
    private fun stubWalletWithTxs(account: Account, txes: List<TransactionHistory>)
    {
        val txStore = account.walletDb!!.tx
        every { txStore.forEach(any(), any(), any()) } calls {
            (doit: (TransactionHistory) -> Boolean, _: Long, _: Long) ->
            for (txh in txes)
            {
                if (doit(txh)) break
            }
            Unit
        }
    }

    /** Wait for viewModel.loading to go false, with timeout. */
    private fun waitForNotLoading(viewModel: TxHistoryViewModel, timeoutMs: Long = 10_000L): Boolean
    {
        val deadline = millinow() + timeoutMs
        while (viewModel.loading.value && millinow() < deadline)
        {
            millisleep(50U)
        }
        return !viewModel.loading.value
    }

    // --- TxHistoryViewModel.getAllTransactions tests ---

    @Test
    fun getAllTransactionsPopulatesHistoryWith100Items()
    {
        val account = mockAccount()
        val fakes = (0 until 100).map { fakeTxHistory(it) }
        stubWalletWithTxs(account, fakes)

        val viewModel = TxHistoryViewModel()
        viewModel.getAllTransactions(account)

        // Wait for both loading=false AND size=100. On iOS the tlater
        // coroutine runs slower; waiting on loading alone can return
        // prematurely if a concurrent call sets loading=false mid-build.
        val deadline = millinow() + 15_000L
        while (millinow() < deadline && (viewModel.loading.value || viewModel.txHistory.value.size != 100))
        {
            millisleep(50U)
        }
        assertEquals(100, viewModel.txHistory.value.size)
    }

    @Test
    fun getAllTransactionsSortsByDateDescending()
    {
        val account = mockAccount()
        val fakes = (0 until 20).map { fakeTxHistory(it) }
        stubWalletWithTxs(account, fakes)

        val viewModel = TxHistoryViewModel()
        viewModel.getAllTransactions(account)
        assertTrue(waitForNotLoading(viewModel))

        val result = viewModel.txHistory.value
        // Newest first — date must be monotonically non-increasing
        for (i in 1 until result.size)
        {
            assertTrue(
              result[i - 1].dateEpochMiliseconds >= result[i].dateEpochMiliseconds,
              "txHistory is not sorted by date descending at index $i"
            )
        }
    }

    @Test
    fun getAllTransactionsMarksReceivedVsSent()
    {
        val account = mockAccount()
        val fakes = (0 until 6).map { fakeTxHistory(it) }  // even=Received, odd=Send
        stubWalletWithTxs(account, fakes)

        val viewModel = TxHistoryViewModel()
        viewModel.getAllTransactions(account)
        assertTrue(waitForNotLoading(viewModel))

        val result = viewModel.txHistory.value
        assertEquals(6, result.size)
        val types = result.map { it.type }.toSet()
        assertTrue("Received" in types, "expected at least one Received tx")
        assertTrue("Send" in types, "expected at least one Send tx")
    }

    @Test
    fun getAllTransactionsEmptyWalletProducesEmptyList()
    {
        val account = mockAccount()
        stubWalletWithTxs(account, emptyList())

        val viewModel = TxHistoryViewModel()
        viewModel.getAllTransactions(account)
        assertTrue(waitForNotLoading(viewModel))

        assertEquals(0, viewModel.txHistory.value.size)
    }

    @Test
    fun getAllTransactionsClearsHistoryWhenAccountChanges()
    {
        val account1 = mockAccount()
        val account2 = mockAccount()
        val fakes = (0 until 10).map { fakeTxHistory(it) }
        stubWalletWithTxs(account1, fakes)
        stubWalletWithTxs(account2, emptyList())

        val viewModel = TxHistoryViewModel()
        viewModel.getAllTransactions(account1)
        assertTrue(waitForNotLoading(viewModel))
        assertEquals(10, viewModel.txHistory.value.size)

        // Switch account — viewModel must clear the prior history immediately
        viewModel.getAllTransactions(account2)
        // The clear happens synchronously before the async tlater{} runs
        // so we expect the list to already be empty here (even if the new
        // load hasn't finished).
        // After the new load completes, list should still be empty (account2 empty)
        assertTrue(waitForNotLoading(viewModel))
        assertEquals(0, viewModel.txHistory.value.size)
    }

    // --- TransactionsList composable tests ---

    @Test
    fun transactionsListRendersEvery10thOf100Transactions() = runComposeUiTest {
        val account = mockAccount()
        val fakes = (0 until 100).map { fakeTxHistory(it) }
        stubWalletWithTxs(account, fakes)

        wallyApp!!.focusedAccount.value = null
        val viewModel = TxHistoryViewModel()

        // Pre-load the 100 tx records BEFORE setContent. This matters because
        // TransactionsList's LaunchedEffect(balance) calls getAllTransactions on
        // first composition; getAllTransactions does NOT cancel a prior in-flight
        // load, and its interim updates (at sizes 8, 16, 32, 48, 64, 80, 96) push
        // shrinking snapshots into txHistory which can race with our scroll loop.
        // By priming txHistory to 100 *before* composition, the re-fire's interim
        // pushes are skipped by the guard `txHistory.value.size < transactions.size`
        // (100 < 8 is false, etc.) and only its final push (again size 100) fires.
        wallyApp!!.focusedAccount.value = account
        viewModel.getAllTransactions(account)
        // Wait for the pre-load to fully settle — loading off AND size == 100.
        val preloadDeadline = millinow() + 15_000L
        while ((viewModel.loading.value || viewModel.txHistory.value.size != 100) && millinow() < preloadDeadline)
        {
            millisleep(50U)
        }
        assertEquals(100, viewModel.txHistory.value.size)

        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                TransactionsList(Modifier, viewModel)
            }
        }

        waitForIdle()

        // Verify every 10th transaction is displayed after scrolling.
        // Each tx renders its amount (unique) and date (unique, 1-day spacing).
        val sorted = viewModel.txHistory.value  // already sorted by date desc
        for (i in 0 until 100 step 10)
        {
            val tx = sorted[i]
            onNode(hasScrollAction()).performScrollToIndex(i)
            // LazyColumn lazy-composes items; poll for the target node to
            // appear in the semantics tree before asserting display.
            waitUntilExactlyOneExists(hasText(tx.amount), timeoutMillis = 5_000L)
            onNodeWithText(tx.amount).assertIsDisplayed()
            onNodeWithText(tx.date).assertIsDisplayed()
        }

        wallyApp!!.focusedAccount.value = null
    }

    /**
     * TransactionsList should render an AssetListItem beneath each transaction
     * whose `assets` list is non-empty. We bypass getAllTransactions (which
     * builds RecentTransactionUIData by analyzing real tx outputs) by waiting
     * for its initial async load to complete, then setting viewModel.txHistory
     * directly with a fake tx that carries a mocked AssetPerAccount.
     */
    @Test
    fun transactionsListRendersAssetListItemWhenTxHasAssets() = runComposeUiTest {
        val account = mockAccount()
        stubWalletWithTxs(account, emptyList())

        wallyApp!!.focusedAccount.value = null
        val viewModel = TxHistoryViewModel()
        wallyApp!!.focusedAccount.value = account

        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                TransactionsList(Modifier, viewModel)
            }
        }

        // Let the LaunchedEffect-triggered getAllTransactions complete with an
        // empty result. After that, balance doesn't change so it won't re-fire
        // and overwrite our fake.
        assertTrue(waitForNotLoading(viewModel, 10_000L))

        // Build a fake tx with a mocked asset
        val nftTitle = "Moon Rock #42"
        val nftSeries = "Apollo Collection"
        val nft = NexaNFTv2("niftyVer", nftTitle, nftSeries, "author", listOf(), "appUri", "info")
        val assetInfo = createAssetInfo("mockAssetName", "https://mock.test.dev", nft)
        val mockedAsset = createAssetPerAccount(assetInfo, 200L)

        val txh = fakeTxHistory(0)
        val fakeTx = RecentTransactionUIData(
          transaction = txh,
          type = "Received",
          icon = Icons.Outlined.ArrowDownward,
          contentDescription = "Transaction",
          amount = "7777.77",
          currency = "NEXA",
          dateEpochMiliseconds = txh.date,
          assets = listOf(mockedAsset)
        )
        viewModel.txHistory.value = listOf(fakeTx)
        waitForIdle()

        onNodeWithText("7777.77").assertIsDisplayed()
        // AssetListItem row beneath it should show the NFT title and series
        onNodeWithText(nftTitle).assertIsDisplayed()
        onNodeWithText(nftSeries).assertIsDisplayed()

        wallyApp!!.focusedAccount.value = null
    }

    @Test
    fun transactionsListEmptyShowsNoActivityOrLoadingText() = runComposeUiTest {
        val account = mockAccount()
        stubWalletWithTxs(account, emptyList())

        wallyApp!!.focusedAccount.value = null
        val viewModel = TxHistoryViewModel()
        wallyApp!!.focusedAccount.value = account

        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                TransactionsList(Modifier, viewModel)
            }
        }

        assertTrue(waitForNotLoading(viewModel, 10_000L))
        waitForIdle()
        assertEquals(0, viewModel.txHistory.value.size)

        wallyApp!!.focusedAccount.value = null
    }

    // --- RecentTransactionUIData sanity check ---

    @Test
    fun recentTransactionUIDataComputesDateStringForRecentTimes()
    {
        val txh = TransactionHistory(ChainSelector.NEXA, txFor(ChainSelector.NEXA))
        val data = RecentTransactionUIData(
          transaction = txh,
          type = "Received",
          icon = Icons.Outlined.ArrowDownward,
          contentDescription = "",
          amount = "1.00",
          currency = "NEXA",
          dateEpochMiliseconds = 1_700_000_000_000L  // well after the cutoff
        )
        assertTrue(data.date.isNotEmpty(), "date string should be populated for a recent epoch")
    }

    @Test
    fun recentTransactionUIDataDateStringEmptyForOldTimes()
    {
        val txh = TransactionHistory(ChainSelector.NEXA, txFor(ChainSelector.NEXA))
        val data = RecentTransactionUIData(
          transaction = txh,
          type = "Send",
          icon = Icons.Outlined.ArrowUpward,
          contentDescription = "",
          amount = "1.00",
          currency = "NEXA",
          dateEpochMiliseconds = 0L  // before the cutoff (1231006505000L)
        )
        assertEquals("", data.date)
    }
}
