package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.SyncViewModelFake
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui2.views.AccountPillViewModelFake
import info.bitcoinunlimited.www.wally.ui2.views.BalanceViewModelFake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests

class AccountPillTest:WallyUiTestBase()
{
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun accountPillHeaderTest()
    {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore = ViewModelStore()
        }

        /*
            Start the app
         */
        val cs = ChainSelector.NEXA
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
        val balanceViewModel = BalanceViewModelFake()
        val syncViewModel = SyncViewModelFake()
        balanceViewModel.balance.value = "99.0"
        balanceViewModel.fiatBalance.value = "5555"
        val balance = balanceViewModel.balance.value

        val pill = AccountPillViewModelFake(MutableStateFlow(account), balanceViewModel, syncViewModel)

        composeTestRule.setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                pill.draw(false)
            }
        }

        /*
            Check if initial values are displayed
         */
        composeTestRule.onNodeWithText(accountName).assertIsDisplayed()
        composeTestRule.onNodeWithText(currencyCode).assertIsDisplayed()
        composeTestRule.onNodeWithText(balance).assertIsDisplayed()

        /*
            Update balance view model to trigger an UI update and verify the result
         */
        val balance2 = "100.1"
        balanceViewModel.balance.value = balance2
        composeTestRule.onNodeWithText(balance2).assertIsDisplayed()
        val fiatBalance = "55555"
        balanceViewModel.fiatBalance.value = fiatBalance
        composeTestRule.onNodeWithTag("AccountPillFiatBalance").assertTextEquals(fiatBalance)
        composeTestRule.onNodeWithTag("AccountPillFiatCurrencyCode").assertIsDisplayed()
        wallyApp!!.deleteAccount(account)
    }
}