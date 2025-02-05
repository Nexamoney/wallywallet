package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.BalanceViewModel
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui2.views.uiData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.*
import org.nexa.threads.platformName


@OptIn(ExperimentalTestApi::class)
class HomeScreenUi2Test:WallyUiTestBase()
{
    @Test
    fun homeScreenTest() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        val account = wallyApp!!.newAccount("nexaTest", 0U, "", cs)!!

        // Set selected account to populate the UI
        setSelectedAccount(account)
        assignAccountsGuiSlots()

        lateinit var balanceViewModel: BalanceViewModel
        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                val assetViewModel = AssetViewModel()
                balanceViewModel = BalanceViewModelImpl(account)
                val accountUiDataViewModel = AccountUiDataViewModel()
                HomeScreenUi2(false, assetViewModel, accountUiDataViewModel)
            }
        }
        settle()
        balanceViewModel.observeBalance(account)
        balanceViewModel.setFiatBalance(account)
        settle()

        // Verify that the account name is displayed in the account carousel
        val accountName = account.name
        onNodeWithTag("AccountPillAccountName").assertTextEquals(accountName)
        settle()
        onNode(hasTestTag("CarouselAccountName") and hasText("nexaTest"), useUnmergedTree = true).assertTextEquals("nexaTest")
        val expectedCurrencyCode = account.uiData().currencyCode
        onNodeWithTag("AccountPillCurrencyCode").assertTextEquals(expectedCurrencyCode)

        // Verify the balance in the account pill
        val expectedBalance = account.format(account.balanceState.value)
        onNodeWithTag("AccountPillBalance").assertTextEquals(expectedBalance)

        // Verify the fiat currency code and balance (if applicable)
        val expectedFiatBalance = balanceViewModel.fiatBalance.value
        if (expectedFiatBalance.isNotEmpty())
        {
            onNodeWithTag("AccountPillFiatBalance").assertTextEquals(expectedFiatBalance)
        }
        // TODO: Click tabrowitem and verify
    }

    @Test
    fun testMultipleAccountsInCarouselAndAccountPill()
    {
        val cs = ChainSelector.NEXA
        wallyApp!!.openAllAccounts()

        // Create two accounts
        val account1 = wallyApp!!.newAccount("nexaTest1", 0U, "", cs)!!
        val account2 = wallyApp!!.newAccount("nexaTest2", 0U, "", cs)!!

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            /*
                Set the first account as selected initially
             */
            setSelectedAccount(account1)
            assignAccountsGuiSlots()
            val assetViewModel = AssetViewModel()
            val accountUiDataViewModel = AccountUiDataViewModel()

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    HomeScreenUi2(
                      isShowingRecoveryWarning = false,
                      assetViewModel = assetViewModel,
                      accountUiDataViewModel = accountUiDataViewModel
                    )
                }
            }
            settle()

            // Verify that both accounts are visible in the carousel
            onNode(hasTestTag("CarouselAccountName") and hasText("nexaTest1"), useUnmergedTree = true).assertTextEquals("nexaTest1")
            val expectedBalance1 = account1.format(account1.balanceState.value)
            onNode(hasTestTag("AccountCarouselBalance_nexaTest1"), useUnmergedTree = true).assertTextEquals(expectedBalance1)
            onNode(hasTestTag("CarouselAccountName") and hasText("nexaTest2"), useUnmergedTree = true).assertTextEquals("nexaTest2")
            val expectedBalance2 = account2.format(account1.balanceState.value)
            onNode(hasTestTag("AccountCarouselBalance_nexaTest2"), useUnmergedTree = true).assertTextEquals(expectedBalance2)

            // Click on the second account in the carousel
            onNode(hasTestTag("CarouselAccountName") and hasText("nexaTest2"), useUnmergedTree = true).performClick()
            settle()

            // Verify that the second account's name is displayed in the account pill
            onNodeWithTag("AccountPillAccountName").assertTextEquals("nexaTest2")
            onNodeWithTag("AccountPillBalance").assertTextEquals(expectedBalance2)
            settle()
        }
    }
}