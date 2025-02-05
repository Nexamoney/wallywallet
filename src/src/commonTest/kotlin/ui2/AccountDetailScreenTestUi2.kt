package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
class AccountDetailScreenTestUi2:WallyUiTestBase()
{
    @Test
    fun accountDetailScreenTest()
    {
        val cs = ChainSelector.NEXA
        wallyApp!!.openAllAccounts()
        val account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            /*
                Set selected account to populate the UI
            */
            setSelectedAccount(account)

            val accountStatsViewModel = AccountStatisticsViewModelFake(account)

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    AccountDetailScreenUi2(accountStatsViewModel)
                }
            }
            settle()

            onNodeWithText(i18n(S.AutomaticNewAddress)).assertIsDisplayed()

            /**
             * Open change pin View and click cancel button to close it.
             */
            onNodeWithText(i18n(S.AccountStatistics)).isDisplayed()
            onNodeWithText(i18n(S.SetChangePin)).performClick()
            settle()
            onNodeWithText(i18n(S.PinHidesAccount)).isDisplayed()
            onNodeWithText(i18n(S.cancel)).performClick()
            settle()
        }
    }
}