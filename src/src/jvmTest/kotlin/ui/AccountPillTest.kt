package ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.FiatFormat
import org.nexa.threads.millisleep
import kotlin.test.Test
import info.bitcoinunlimited.www.wally.ui.theme.WallyTheme

@OptIn(ExperimentalTestApi::class)
class AccountPillTest:WallyUiTestBase()
{
    @Test
    fun accountPillHeaderTest()
    {
        val cs = ChainSelector.NEXA
        wallyApp!!.openAllAccounts()
        val account = wallyApp!!.newAccount("accountPillHeaderTest", 0U, "", cs)!!

        runComposeUiTest {
            try
            {
                val viewModelStoreOwner = object : ViewModelStoreOwner
                {
                    override val viewModelStore = ViewModelStore()
                }

                //Set selected account to populate the UI
                setSelectedAccount(account)
                val accountName = account.name
                val currencyCode = account.currencyCode

                val ap = AccountPill(account)
                setContent {
                    WallyTheme {
                        CompositionLocalProvider(
                          LocalViewModelStoreOwner provides viewModelStoreOwner
                        ) {
                            ap.AccountPillHeader()
                        }
                    }
                }
                settle()

                //Check if initial values are displayed

                onNodeWithText(accountName).assertIsDisplayed()
                onNodeWithText(currencyCode).assertIsDisplayed()

                //The initial value for both viewModel.balance and viewModel.fiatBalance is the same ("Loading...").
                //Therefore we can't use onNodeWithText because it crashes when it finds more than one node with the same text.

                val balance = ap.balance.balance.value
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


                //Update balance view model to trigger an UI update and verify the result
                val balance2 = "100.1"
                account.fiatPerCoin = BigDecimal.fromInt(5)
                ap.balance.balance.value = balance2
                settle()
                onNodeWithText(balance2).assertIsDisplayed()
                settle()
                millisleep(1000UL)
                println("Fiat per coin: ${account.fiatPerCoin} balance: ${account.balance}")
                // We cannot trick the fiat balance because it is auto-updated based on the real account balance.
                val fiatBalance2 = FiatFormat.format(account.fiatPerCoin * account.balance)
                onNodeWithTag("AccountPillFiatBalance").assertTextEquals(fiatBalance2).assertIsDisplayed()
                onNodeWithTag("AccountPillFiatCurrencyCode").assertIsDisplayed()
                settle()
            }
            catch (e: Exception)
            {
                println("${e.message}")
                println("EXCEPTION: $e")
                throw e
            }
        }
        wallyApp!!.deleteAccount(account)
    }
}