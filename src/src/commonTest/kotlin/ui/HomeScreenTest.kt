package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.*
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.sourceLoc
import org.nexa.threads.millisleep
import kotlin.test.Test

fun SemanticsNodeInteraction.multiplatformImeAction()
{
    // There is no IME action on iOS
    // TODO create a specific platform() "hasSoftKeyboardAction" parameter
    // TODO is there some other way to "ok" or "dismiss" the IME on iOS?
    if (platform().target == KotlinTarget.iOS) return
    else this.performImeAction()
}

class TestTimeoutException(what: String): Exception(what)
fun<T> waitFor(timeout: Int = 10000, lazyErrorMsg: (()->String)? = null, checkIt: ()->T?):T
{
    var count = timeout
    var ret:T? = checkIt()
    while(ret == null || ret as? Boolean == false)
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
fun<Boolean> waitFor1(timeout: Int = 10000, lazyErrorMsg: (()->String)? = null, checkIt: () -> Boolean):Boolean
{
    var count = timeout
    var ret: Boolean? = checkIt()
    while (ret == null || ret as? Boolean == false)
    {
        millisleep(100U)
        count -= 100
        if (count < 0)
        {
            val msg = lazyErrorMsg?.invoke()
            if (msg != null) println(msg)
            throw TestTimeoutException("Timeout waiting for predicate: $msg")
        }
        ret = checkIt()
    }
    return ret
}
/** Wait for the predicate, retrying if any exception happens */
fun<T> waitForCatching(timeout: Int = 100000, lazyErrorMsg: (()->String)? = null, checkIt: ()->T?):T
{
    var count = timeout
    var ret:T? = try { checkIt() } catch(e:Throwable) { null }
    while(ret == null || ret as? Boolean == false)
    {
        millisleep(100U)
        count-=100
        if (count < 0 )
        {
            val msg = lazyErrorMsg?.invoke()
            if (msg != null) println(msg)
            throw TestTimeoutException("Timeout waiting for predicate: $msg")
        }
        try
        {
            ret = checkIt()
        }
        catch(_:Throwable)
        {}
    }
    return ret
}


@OptIn(ExperimentalTestApi::class)
class HomeScreenTest: WallyUiTestBase()
{
    init
    {
        setupTestEnv()
    }
    @Test
    fun homeScreenTest() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }

        val cs = ChainSelector.NEXA
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
                val apvm = AccountPill(wallyApp!!.focusedAccount)
                HomeScreen(false, apvm, assetViewModel, accountUiDataViewModel)
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
        // Create two accounts
        val account1 = try
        {
            wallyApp!!.newAccount("nexaTest1", 0U, "", cs)!!
        }
        catch (e: Exception)
        {
            println(sourceLoc() + ": ERROR creating nexaTest1: $e")
            throw e
        }

        val account2 = try
        {
            wallyApp!!.newAccount("nexaTest2", 0U, "", cs)!!
        }
        catch (e: Exception)
        {
            println(sourceLoc() + ": ERROR creating nexaTest2: $e")
            throw e
        }

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
            val apvm = AccountPill(wallyApp!!.focusedAccount)

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    HomeScreen(
                      isShowingRecoveryWarning = false,
                      apvm,
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
        // Create a normal account
        val normalAccount = wallyApp!!.newAccount("nexaAccount", 0U, "", ChainSelector.NEXA)!!
        // Create a testnet account
        val testnetAccount = wallyApp!!.newAccount("nexaTestnetAccount", 0U, "", ChainSelector.NEXATESTNET)!!
        val wInsets = WindowInsets(0,0,0,0)

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            // Initialize ViewModels
            val assetViewModel = AssetViewModel()
            lateinit var balanceViewModel: BalanceViewModel
            val accountUiDataViewModel = AccountUiDataViewModel()

            /*
                Set content to NavigationRoot (the root composable that handles navigation)
             */
            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(Modifier, wInsets,
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
                waitForCatching(6000,{"QR code not displayed"}) {
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