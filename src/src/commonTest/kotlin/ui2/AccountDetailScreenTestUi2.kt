package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui2.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import info.bitcoinunlimited.www.wally.uiv2.AccountDetailScreenUi2
import info.bitcoinunlimited.www.wally.uiv2.AccountStatisticsViewModelFake
import info.bitcoinunlimited.www.wally.uiv2.BalanceViewModelFake
import info.bitcoinunlimited.www.wally.uiv2.SyncViewModelFake
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
class AccountDetailScreenTestUi2
{
    @BeforeTest
    fun setUp() {
        // Jvm
        if (platform().usesMouse)
            Dispatchers.setMain(StandardTestDispatcher())
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @AfterTest
    fun tearDown() {
        // Jvm
        if (platform().usesMouse)
            Dispatchers.resetMain()
    }

    @Test
    fun accountDetailScreenTest() = runComposeUiTest {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
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
            account = wallyApp!!.newAccount("sendScreenContentTest", 0U, "", cs)!!
        }

        /*
            Set selected account to populate the UI
        */
        setSelectedAccount(account)

        val accountStatsViewModel = AccountStatisticsViewModelFake()
        val balanceViewModel = BalanceViewModelFake()
        val syncViewModel = SyncViewModelFake()
        val accountUiDataViewModel = AccountUiDataViewModelFake()

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                AccountDetailScreenUi2(account, accountStatsViewModel, balanceViewModel, syncViewModel, accountUiDataViewModel)
            }
        }

        onNodeWithText(i18n(S.AutomaticNewAddress)).assertIsDisplayed()

        /**
         * Open change pin View and click cancel button to close it.
         */
        onNodeWithText(i18n(S.AccountStatistics)).isDisplayed()
        onNodeWithText(i18n(S.SetChangePin)).performClick()
        onNodeWithText(i18n(S.PinHidesAccount)).isDisplayed()
        onNodeWithText(i18n(S.cancel)).performClick()
    }
}