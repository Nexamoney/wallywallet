package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.triggerUnlockDialog
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.NativeSplash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.nexa.libnexakotlin.*
import org.nexa.threads.millisleep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
private val LogIt = GetLog("BU.wally.navRootTest")

// Changing this value to several seconds (3000) makes the tests proceed at a human visible pace
var testSlowdown = 0 // runCatching { System.getProperty("testSlowdown").toInt() }.getOrDefault(0)
// Delay between tests to let async stuff finish.  NOTE: JVM tests WILL NOT RELIABLY WORK unless this delay is greater than about a half second
var afterTestDelay = 500 // runCatching { System.getProperty("testSlowdown").toInt() }.getOrDefault(0)
// Wait for a max of this time for the job pool to clear.  Its possible that periodic or long running jobs might make the pool never clear...
// But if it takes longer than this for ephemeral jobs to finish we have a problem anyway.
var maxPoolWait = 500

// Reset WallyApp for every test under JVM?
var jvmResetWallyApp = false

internal fun setupTestEnv(openAllAccounts:Boolean = true)
{
    LogIt.info(sourceLoc() + ": Setting up TEST environment")
    // On some platforms, wallyApp is set up by the platform-specific app instantiation.
    // This happens before any test code is given the chance to run.
    // In other platforms or contexts, the platform-specific app appears to not be instantiated (during tests).
    if (wallyApp == null)
    {
        LogIt.info(sourceLoc() + ": initializing libnexa")
        initializeLibNexa()
        BLOCKCHAIN_LOGGING = true
        LogIt.info(sourceLoc() + ": creating wallyApp")
        wallyApp = CommonApp(true)
        LogIt.info(sourceLoc() + ": wallyApp.onCreate")
        wallyApp!!.onCreate()
    }
    if (openAllAccounts)
    {
        LogIt.info(sourceLoc() + ": opening all accounts")
        wallyApp!!.openAllAccounts()
        LogIt.info(sourceLoc() + ": holding blockchain refs")
    }
    // HACK: In these tests, wallets are created and deleted rapidly.  This can cause the blockchains to be started up and shutdown
    // rapidly as well.  This means connecting and disconnecting from nodes repeatedly, which gets IPs banned.
    // So force the blockchains to remain open even if all wallets are deleted from it
    for(b in blockchains.values)
    {
        b.attachedWallets++
    }
    LogIt.info(sourceLoc() + ": setupTestEnv complete")
}

//val sched = TestCoroutineScheduler()
//val testDispatcher = StandardTestDispatcher(sched, "testDispatcher")
var installedTestDispatcher = 0
open class WallyUiTestBase(openAllAccounts: Boolean = true)
{
    var sched = TestCoroutineScheduler()
    var testDispatcher = StandardTestDispatcher(sched)

    // You only need to do this once
    init {
        setupTestEnv(openAllAccounts)
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
                // println("Installing test dispatcher")
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
            // println("settle scheduler")
            sched.advanceTimeBy(1000)
            sched.runCurrent()
            val poolWaitStart = millinow()
            while ((millinow() - poolWaitStart < maxPoolWait) && (libNexaJobPool.jobs.size != 0) && (libNexaJobPool.availableThreads.value!= libNexaJobPool.allThreads.size)) millisleep(50U)
        }

        millisleep(afterTestDelay.toULong())

        if (platform().target == KotlinTarget.JVM)
        {
            installedTestDispatcher--
            if (installedTestDispatcher == 0)
            {
                // println("Removing test dispatcher")
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
    while ((millinow()-poolWaitStart < ui.maxPoolWait) && (libNexaJobPool.jobs.size != 0) && (libNexaJobPool.availableThreads.value != libNexaJobPool.allThreads.size))
    {
        LogIt.info("Settling")
        millisleep(1000U)
    }

    if (scope!=null)
    {
        LogIt.info("advanceTimeBy")
        scope.testScheduler.advanceTimeBy(1000)
        scope.testScheduler.runCurrent()
    }
    else
    {
        // The above does waitForIdle with a timeout
        // LogIt.info("waitForIdle")
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
class NavigationRootTest: WallyUiTestBase()
{
    @Test fun unlockTest()
    {
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner { override val viewModelStore: ViewModelStore = ViewModelStore() }
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                    NavigationRoot(Modifier, WindowInsets(0,0,0,0))
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
            val apvm = AccountPill(wallyApp!!.focusedAccount)
            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(Modifier, WindowInsets(0,0,0,0), apvm, assetViewModel, accountUiDataViewModel)
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