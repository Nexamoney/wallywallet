package ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AccountDetailScreen
import info.bitcoinunlimited.www.wally.ui.AccountStatisticsViewModelFake
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModelFake
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AccountDetailScreenTest:WallyUiTestBase()
{
    @Test
    fun accountDetailScreenTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!

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
        wallyApp!!.deleteAccount(account)
    }
}