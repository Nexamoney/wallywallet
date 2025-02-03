package ui2

import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui2.views.NativeSplash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import org.nexa.libnexakotlin.*
import org.nexa.threads.millisleep
import kotlin.test.BeforeTest
import kotlin.test.Test

// Changing this value to several seconds (3000) makes the tests proceed at a human visible pace
var testSlowdown = 0 // runCatching { System.getProperty("testSlowdown").toInt() }.getOrDefault(0)
// Wait for a max of this time for the job pool to clear.  Its possible that periodic or long running jobs might make the pool never clear...
// But if it takes longer than this for ephemeral jobs to finish we have a problem anyway.
var maxPoolWait = 500

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.settle(scope: TestScope? = null)
{
    val poolWaitStart = millinow()
    while ((millinow()-poolWaitStart < ui2.maxPoolWait) && (libNexaJobPool.jobs.size != 0) && (libNexaJobPool.availableThreads != libNexaJobPool.allThreads.size)) millisleep(50U)

    if (scope!=null)
    {
        scope.testScheduler.advanceTimeBy(1000)
        scope.testScheduler.runCurrent()
    }
    else
    {
        // The above does waitForIdle with a timeout
        waitForIdle()
        //catch (e: TimeoutCancellationException)
        //{
        //    println("Warning: coroutine waitForIdle() never became idle.  However, we are seeing this without any actual busy resources, so continuing")
        //}
    }
    if (testSlowdown != 0)
    {
        val leftover = testSlowdown - (millinow() - poolWaitStart)
        if (leftover>0) millisleep(leftover.toULong())
    }
}

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

        val assetViewModel = AssetViewModelFake()
        val balanceViewModel = BalanceViewModelFake()
        val syncViewModel = SyncViewModelFake()
        val accountUiDataViewModel = AccountUiDataViewModelFake()
        val walletViewModel = WalletViewModelFake()

        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NavigationRootUi2(Modifier, Modifier, assetViewModel, balanceViewModel, syncViewModel, accountUiDataViewModel, walletViewModel)
            }
        }

        val nativeSplash = NativeSplash(true)
        // This is not visible because the splash screen is showing on some targets
        if (nativeSplash)
            onNodeWithTag("RootScaffold").assertIsNotDisplayed()

    }
}