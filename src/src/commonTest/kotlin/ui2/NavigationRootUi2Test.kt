package ui2

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui2.views.AssetViewModelFake
import info.bitcoinunlimited.www.wally.ui2.views.NativeSplash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.nexa.libnexakotlin.*
import org.nexa.threads.millisleep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

// Changing this value to several seconds (3000) makes the tests proceed at a human visible pace
var testSlowdown = 0 // runCatching { System.getProperty("testSlowdown").toInt() }.getOrDefault(0)
// Delay between tests to let async stuff finish.  NOTE: JVM tests WILL NOT RELIABLY WORK unless this delay is greater than about a half second
var afterTestDelay = 500 // runCatching { System.getProperty("testSlowdown").toInt() }.getOrDefault(0)
// Wait for a max of this time for the job pool to clear.  Its possible that periodic or long running jobs might make the pool never clear...
// But if it takes longer than this for ephemeral jobs to finish we have a problem anyway.
var maxPoolWait = 500

// Reset WallyApp for every test under JVM?
var jvmResetWallyApp = false

internal fun setupTestEnv()
{
    initializeLibNexa()
    runningTheTests = true
    TEST_PREF = "test_"
    forTestingDoNotAutoCreateWallets = true
    dbPrefix = "test_"
    if (wallyApp == null)
    {
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
    }
}

//val sched = TestCoroutineScheduler()
//val testDispatcher = StandardTestDispatcher(sched, "testDispatcher")
var installedTestDispatcher = 0
open class WallyUiTestBase
{
    var sched = TestCoroutineScheduler()
    var testDispatcher = StandardTestDispatcher(sched)

    // You only need to do this once
    init {
        setupTestEnv()
    }

    @BeforeTest
    fun testSetup()
    {
        // Solves the error: Module with the Main dispatcher had failed to initialize. For tests Dispatchers.setMain from kotlinx-coroutines-test module can be used
        // On Android this code ends up running UI drawing in multiple threads which is disallowed.
        if (platform().target == KotlinTarget.JVM)
        {
            if (installedTestDispatcher==0)
            {
                sched = TestCoroutineScheduler()
                testDispatcher = StandardTestDispatcher(sched)
                println("Installing test dispatcher")
                Dispatchers.setMain(testDispatcher)
                assert(wallyApp!=null)
            }
            installedTestDispatcher++
        }
    }

    @AfterTest
    fun testDone()
    {
        if (platform().target == KotlinTarget.JVM)
        {
            println("settle scheduler")
            sched.advanceTimeBy(1000)
            sched.runCurrent()
            val poolWaitStart = millinow()
            while ((millinow() - poolWaitStart < maxPoolWait) && (libNexaJobPool.jobs.size != 0) && (libNexaJobPool.availableThreads != libNexaJobPool.allThreads.size)) millisleep(50U)
        }

        millisleep(afterTestDelay.toULong())

        if (platform().target == KotlinTarget.JVM)
        {
            installedTestDispatcher--
            if (installedTestDispatcher == 0)
            {
                println("Removing test dispatcher")
                Dispatchers.resetMain()
                if (jvmResetWallyApp) wallyApp = null
            }
        }
    }
}


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
class NavigationRootUi2Test:WallyUiTestBase()
{
    @Test fun unlockTest()
    {
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner { override val viewModelStore: ViewModelStore = ViewModelStore() }
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    NavigationRootUi2(Modifier, WindowInsets(0,0,0,0))
                }
            }
            settle()
            nav.switch(ScreenId.Home)
            settle()
            waitForCatching { onNodeWithTag("AccountPillAccountName").isDisplayed() }
            triggerUnlockDialog(true, { println("Unlock attempted")})
            settle()
            waitForCatching { onNodeWithTag("EnterPIN").isDisplayed() }
            onNodeWithTag("EnterPIN").performTextInput("1111")
            settle()
            onNodeWithTag("EnterPIN").multiplatformImeAction()
        }
    }
    @Test fun navRootTest()
    {
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }


            val assetViewModel = AssetViewModelFake()
            val accountUiDataViewModel = AccountUiDataViewModelFake()

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRootUi2(Modifier, WindowInsets(0,0,0,0), assetViewModel, accountUiDataViewModel)
                }
            }

            val nativeSplash = NativeSplash(true)
            // This is not visible because the splash screen is showing on some targets
            if (nativeSplash)
                onNodeWithTag("RootScaffold").assertIsNotDisplayed()

            settle()
        }
    }
}