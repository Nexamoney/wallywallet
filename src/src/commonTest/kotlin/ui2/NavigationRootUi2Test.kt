package ui2

import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.NativeSplash
import info.bitcoinunlimited.www.wally.ui2.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui2.NavigationRootUi2
import info.bitcoinunlimited.www.wally.uiv2.AssetsViewModelFake
import info.bitcoinunlimited.www.wally.uiv2.BalanceViewModelFake
import info.bitcoinunlimited.www.wally.uiv2.SyncViewModelFake
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NavigationRootUi2Test
{
    @BeforeTest
    fun setUp() {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @Test
    fun navRootTest() = runComposeUiTest {
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

        val assetViewModel = AssetsViewModelFake()
        val balanceViewModel = BalanceViewModelFake()
        val syncViewModel = SyncViewModelFake()
        val accountUiDataViewModel = AccountUiDataViewModelFake()

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NavigationRootUi2(Modifier, assetViewModel, balanceViewModel, syncViewModel, accountUiDataViewModel)
            }
        }

        val nativeSplash = NativeSplash(true)
        // This is not visible because the splash screen is showing on some targets
        if (nativeSplash)
            onNodeWithTag("RootScaffold").assertIsNotDisplayed()

    }
}