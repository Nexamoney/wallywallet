package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import info.bitcoinunlimited.www.wally.uiv2.AccountPillHeader
import info.bitcoinunlimited.www.wally.uiv2.BalanceViewModel
import info.bitcoinunlimited.www.wally.uiv2.BalanceViewModelImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AccountPillTest
{
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }


    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun accountPillHeaderTest() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("accountPillHeaderTest", 0U, "", cs)!!
        }

        /*
            Set selected account to populate the UI
         */
        setSelectedAccount(account)
        val accountName = account.name
        val currencyCode = account.currencyCode
        val balanceViewModel = BalanceViewModelImpl()
        val balance = balanceViewModel.balance.value

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                AccountPillHeader(balanceViewModel)
            }
        }

        /*
            Check if initial values are displayed
         */
        onNodeWithText(accountName).assertIsDisplayed()
        onNodeWithText(currencyCode).assertIsDisplayed()
        /*
            The initial value for both viewModel.balance and viewModel.fiatBalance is the same ("Loading...").
            Therefore we can't use onNodeWithText because it crashes when it finds more than one node with the same text.
         */
        onAllNodesWithText(balance).apply {
            fetchSemanticsNodes().let { nodes ->
                require(nodes.isNotEmpty()) { "No nodes found with text: $balance" }

                // Check if any node is visible (has LayoutInfo and not marked hidden)
                val isAnyNodeVisible = nodes.any { node ->
                    node.config.contains(SemanticsProperties.Text) &&
                      node.boundsInRoot.height > 0 && node.boundsInRoot.width > 0
                }

                check(isAnyNodeVisible) { "None of the nodes with text '$balance' are displayed" }
            }
        }

        /*
            Update balance view model to trigger an UI update and verify the result
         */
        val balance2 = "100.1"
        balanceViewModel.balance.value = balance2
        onNodeWithText(balance2).assertIsDisplayed()
        val fiatBalance2 = "555"
        balanceViewModel.fiatBalance.value = fiatBalance2
        onNodeWithText(fiatBalance2).assertIsDisplayed()
    }
}