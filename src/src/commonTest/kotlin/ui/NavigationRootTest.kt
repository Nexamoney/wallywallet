package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.SendScreenViewModelFake
import info.bitcoinunlimited.www.wally.ui.SyncViewModelFake
import info.bitcoinunlimited.www.wally.ui.assignAccountsGuiSlots
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModelImpl
import info.bitcoinunlimited.www.wally.ui.views.NativeSplash
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.nexa.libnexakotlin.*
import org.nexa.threads.millisleep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.nexa.threads.millinow
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

/**
 * Account deletion is two-phase and asynchronous: deleteAccount() returns immediately after the
 * synchronous Phase A, while Phase B (wallet teardown + file delete) runs on libNexaJobPool. Tests
 * that reuse a deleted account name, or that tear wallyApp down, must wait for Phase B to finish --
 * otherwise the leaked job races the next test's DB (manifesting as SQLITE_READONLY_DBMOVED /
 * "no such table" / a re-create returning null). This polls the same persisted "pendingDeletions"
 * list that finalizeAccountDeletion clears in its finally, so it is the real completion signal
 * rather than an assumption that delete is synchronous.
 */
fun awaitDeletionsComplete(timeoutMs: Long = 10_000)
{
    val start = millinow()
    while (millinow() - start < timeoutMs)
    {
        val pending = kvpDb?.getOrNull("pendingDeletions")?.decodeUtf8().orEmpty()
        if (pending.split(",").none { it.isNotEmpty() }) return
        millisleep(20U)
    }
    LogIt.warning("awaitDeletionsComplete timed out; pending deletions did not clear")
}

@OptIn(ExperimentalUnsignedTypes::class)
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
        // CI reuses the workspace, so a previous session can leave a half-finished deletion in the
        // persisted pendingDeletions. openAllAccounts kicks off the resume of that deletion on a
        // background job; wait for it to actually finish (delete the old wallet + clear the flag)
        // before any test runs, otherwise that resume races a test recreating the same account name.
        awaitDeletionsComplete()
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
        // Account deletion is async (Phase B on libNexaJobPool). Several tests reuse the same
        // account name across methods, so wait for any in-flight deletion to finish before the next
        // test recreates that name -- otherwise newAccount() sees it still pending and returns null.
        // (The JVM-only pool drain below doesn't cover iOS, and never waited on a *running* job.)
        awaitDeletionsComplete()

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

        // LeakCanary memory leak detector
        // Every Android pixel5Check and connectedDebugAndroidTest run triggers a heap analysis after each passing test
        // via WallyUiTestBase.testDone() and fails the test on any retained watched instance. JVM and iOS runs stay no-ops.
        // Expected cost: ~5–10s per test on the emulator.
        LeakAssertions.assertNoLeaks(this::class.simpleName ?: "test")
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
        androidx.compose.ui.test.v2.runComposeUiTest {
            val ap = AccountPill(wallyApp!!.focusedAccount)
            val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
            setContent {
                NavigationRoot(Modifier, WindowInsets(0,0,0,0), ap, unlock = unlock)
            }
            // On Android, Compose state writes have to be dispatched to the UI
            // thread — `runOnIdle` does that, avoiding "Detected multithreaded
            // access to SnapshotStateObserver" from the test worker.
            runOnIdle { nav.switch(ScreenId.Home) }
            onNodeWithTag("AccountPillAccountName").isDisplayed()
            runOnIdle { unlock.triggerUnlockDialog(true) { println("Unlock attempted") } }
            onNodeWithTag("EnterPIN").isDisplayed()
            onNodeWithTag("EnterPIN").performTextInput("1111")
            onNodeWithTag("EnterPIN").multiplatformImeAction()
        }
    }
    @Test fun navRootTest()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            val assetViewModel = AssetViewModelFake()
            val accountUiDataViewModel = AccountUiDataViewModelFake()
            val apvm = AccountPill(wallyApp!!.focusedAccount)
            val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(Modifier, WindowInsets(0,0,0,0), apvm, assetViewModel, accountUiDataViewModel, unlock)
                }
            }

            val nativeSplash = NativeSplash(true)
            // This is not visible because the splash screen is showing on some targets
            if (nativeSplash)
                onNodeWithTag("RootScaffold").assertIsNotDisplayed()
            settle()
        }
    }

    @Test
    fun deleteSecondAccountFromAccountDetail()
    {
        // Start from a clean slate so that "account number two" is unambiguous.
        val existing = wallyApp!!.accounts.values.toList()
        for (a in existing)
        {
            LogIt.info(sourceLoc() + ": Pre-clean: deleting account ${a.name}")
            wallyApp!!.deleteAccount(a)
        }

        // Names are crafted so alphabetical ordering (used by orderedAccounts) matches
        // creation order, making "account number two" deterministic across platforms.
        val mainnet1 = wallyApp!!.newAccount("delAct1main", 0U, "", ChainSelector.NEXA)!!
        val mainnet2 = wallyApp!!.newAccount("delAct2main", 0U, "", ChainSelector.NEXA)!!
        val testnet1 = wallyApp!!.newAccount("delAct3test", 0U, "", ChainSelector.NEXATESTNET)!!
        val testnet2 = wallyApp!!.newAccount("delAct4test", 0U, "", ChainSelector.NEXATESTNET)!!

        // orderedAccounts returns a ListifyMap which implements List<Account> directly.
        val ordered = wallyApp!!.orderedAccounts(false)
        assertEquals(4, ordered.size)
        val accountTwo = ordered[1]
        assertEquals(mainnet2.name, accountTwo.name)

        val wInsets = WindowInsets(0, 0, 0, 0)

        androidx.compose.ui.test.v2.runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }
            val assetViewModel = AssetViewModel()
            val accountUiDataViewModel = AccountUiDataViewModel()
            val pill = AccountPill(wallyApp!!.focusedAccount)
            val unlock = UnlockViewModel(wallyApp!!.focusedAccount)

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(Modifier, wInsets, pill, assetViewModel, accountUiDataViewModel, unlock)
                }
            }
            assignAccountsGuiSlots()
            nav.switch(ScreenId.Home)
            settle()

            // Click the second account in the list to focus it.
            waitForCatching {
                onNode(hasTestTag("CarouselAccountName") and hasText(accountTwo.name), useUnmergedTree = true).isDisplayed()
            }
            onNode(hasTestTag("CarouselAccountName") and hasText(accountTwo.name), useUnmergedTree = true).performClick()
            settle()

            waitForCatching { onNodeWithTag("AccountPillAccountName").isDisplayed() }
            onNodeWithTag("AccountPillAccountName").assertTextEquals(accountTwo.name)

            // Tap the gear icon to navigate to AccountDetails.
            waitForCatching { onNodeWithTag("AccountDetailsButton").isDisplayed() }
            onNodeWithTag("AccountDetailsButton").performClick()
            settle()

            // Trigger the delete-account confirmation card.
            waitForCatching { onNodeWithText(i18n(S.deleteWalletAccount)).isDisplayed() }
            onNodeWithText(i18n(S.deleteWalletAccount)).performScrollTo()
            onNodeWithText(i18n(S.deleteWalletAccount)).performClick()
            settle()

            // Confirm the deletion.
            waitForCatching { onNodeWithText(i18n(S.accept)).isDisplayed() }
            onNodeWithText(i18n(S.accept)).performClick()
            settle()

            // Deletion is dispatched via tlater{}, so wait until wallyApp reflects the change.
            val deletedName = accountTwo.name
            waitForCatching<Boolean>(10000, { "Account $deletedName was not removed from wallyApp.accounts" }) {
                !wallyApp!!.accounts.containsKey(deletedName)
            }

            // After accept, AccountDetailScreen calls nav.back(), so we should be back on Home.
            // Verify in the UI that the deleted account's carousel row is gone, and the other
            // three accounts are still rendered.
            assignAccountsGuiSlots()
            settle()
            waitForCatching<Boolean>(10000, { "Carousel still shows deleted account $deletedName" }) {
                onAllNodes(hasTestTag("CarouselAccountName") and hasText(deletedName), useUnmergedTree = true)
                  .fetchSemanticsNodes().isEmpty()
            }
            onNode(hasTestTag("CarouselAccountName") and hasText(deletedName), useUnmergedTree = true).assertDoesNotExist()
            waitForCatching<Boolean> { onNode(hasTestTag("CarouselAccountName") and hasText(mainnet1.name), useUnmergedTree = true).isDisplayed() }
            waitForCatching<Boolean> { onNode(hasTestTag("CarouselAccountName") and hasText(testnet1.name), useUnmergedTree = true).isDisplayed() }
            waitForCatching<Boolean> { onNode(hasTestTag("CarouselAccountName") and hasText(testnet2.name), useUnmergedTree = true).isDisplayed() }
        }

        // The 2nd account is gone, the others remain.
        assertFalse(wallyApp!!.accounts.containsKey(mainnet2.name))
        assertTrue(wallyApp!!.accounts.containsKey(mainnet1.name))
        assertTrue(wallyApp!!.accounts.containsKey(testnet1.name))
        assertTrue(wallyApp!!.accounts.containsKey(testnet2.name))
        assertEquals(3, wallyApp!!.orderedAccounts(false).size)
        // accountTwo should not be present in the ordered list either.
        assertFalse(wallyApp!!.orderedAccounts(false).any { it.name == mainnet2.name })

        // Cleanup the remaining accounts so we leave a clean state for other tests.
        wallyApp!!.deleteAccount(mainnet1)
        wallyApp!!.deleteAccount(testnet1)
        wallyApp!!.deleteAccount(testnet2)
    }
}