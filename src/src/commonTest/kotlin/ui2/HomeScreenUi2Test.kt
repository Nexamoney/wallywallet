package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.AssetViewModelFake
import info.bitcoinunlimited.www.wally.ui2.BalanceViewModelFake
import info.bitcoinunlimited.www.wally.ui2.HomeScreenUi2
import info.bitcoinunlimited.www.wally.ui2.SyncViewModelFake
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModelFake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HomeScreenUi2Test
{
    @BeforeTest
    fun setUp() {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @Test
    fun homeScreenTest() = runComposeUiTest {
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

        val assetViewModel = AssetViewModelFake()
        val balanceViewModel = BalanceViewModelFake()
        val syncViewModelFake = SyncViewModelFake()
        val accountUiDataViewModel = AccountUiDataViewModelFake()

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                HomeScreenUi2(false, assetViewModel, balanceViewModel, syncViewModelFake, accountUiDataViewModel)
            }
        }

        onNodeWithText(account.name).assertIsDisplayed()
        // TODO: Click tabrowitem and verify
    }
}