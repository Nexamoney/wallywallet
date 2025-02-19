package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.views.*
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.threads.millisleep
import kotlin.test.Test

class TestTimeoutException(what: String): Exception(what)
fun<T> waitFor(timeout: Int = 10000, lazyErrorMsg: (()->String)? = null, checkIt: ()->T?):T
{
    var count = timeout
    var ret:T? = checkIt()
    while(ret == null || ret == false)
    {
        millisleep(100U)
        count-=100
        if (count < 0 )
        {
            val msg = lazyErrorMsg?.invoke()
            if (msg != null) println(msg)
            throw TestTimeoutException("Timeout waiting for predicate: $msg")
        }
        ret = checkIt()
    }
    return ret
}


internal fun setupTest()
{
    if (wallyApp == null)
    {
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
    }
}

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
        setupTest()
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
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun testMultipleAccountsInCarouselAndAccountPill()
    {
        val cs = ChainSelector.NEXA
        setupTest()
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
        wallyApp!!.deleteAccount(account2)
        wallyApp!!.deleteAccount(account1)
    }
    @Test
    fun testNavigationToReceiveScreenWithTwoAccounts()
    {
        setupTest()
        // Create a normal account
        val normalAccount = wallyApp!!.newAccount("nexaAccount", 0U, "", ChainSelector.NEXA)!!
        // Create a testnet account
        val testnetAccount = wallyApp!!.newAccount("nexaTestnetAccount", 0U, "", ChainSelector.NEXATESTNET)!!

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }


            // Initialize ViewModels
            val assetViewModel = AssetViewModel()
            lateinit var balanceViewModel: BalanceViewModel
            val accountUiDataViewModel = AccountUiDataViewModel()

            /*
                Set content to NavigationRootUi2 (the root composable that handles navigation)
             */
            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRootUi2(Modifier, Modifier,
                      assetViewModel = assetViewModel,
                      accountUiDataViewModel = accountUiDataViewModel
                    )
                }
            }

            // List of accounts to verify
            val accounts = listOf(normalAccount, testnetAccount)

            // Iterate through each account and verify the address
            for (account in accounts)
            {
                // Select the account
                setSelectedAccount(account)
                assignAccountsGuiSlots()
                balanceViewModel = BalanceViewModelImpl(account)

                balanceViewModel.observeBalance(account)
                balanceViewModel.setFiatBalance(account)

                // Navigate to the Home Screen
                nav.switch(ScreenId.Home)
                settle()

                // Verify that the Home Screen is displayed
                onNodeWithTag("AccountPillAccountName").assertTextEquals(account.name)

                // Simulate clicking the "Receive" button to navigate to the Receive Screen
                onNodeWithTag("ReceiveButton").performClick()
                settle()
                val expectedAddress = account.wallet.getCurrentDestination().address.toString()
                println("Expected Address ${expectedAddress}")
                // Verify that the Receive Screen is displayed
                // Receive addresses need to be installed into connected nodes' bloom filters before they are
                // shown, so showing this QR code can actually take a lot of time
                waitFor(6000,{"QR code not displayed"}) {
                    var result = false
                    try
                    {
                        onNodeWithTag("qrcode").assertExists()
                        //onNodeWithText(expectedAddress).assertExists()
                        //onNodeWithText(expectedAddress).assertIsDisplayed()
                        result = true
                    }
                    catch(e:AssertionError) { }
                    result
                }
                onNodeWithTag("qrcode").assertIsDisplayed()
                // Check if the address displayed on the Receive Screen matches the expected address
                onNodeWithTag("receiveScreen:receiveAddress").assertIsDisplayed()
                // check that the address is correct
                onNodeWithTag("receiveScreen:receiveAddress").assertTextEquals(expectedAddress)
                //onNodeWithText(expectedAddress).assertIsDisplayed()
                // Navigate back to the Home Screen
                onNodeWithTag("BackButton").performClick()
                settle()
            }
        }
        wallyApp!!.deleteAccount(normalAccount)
        wallyApp!!.deleteAccount(testnetAccount)
    }
}