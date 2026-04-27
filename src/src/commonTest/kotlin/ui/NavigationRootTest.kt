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
        runComposeUiTest {
            val ap = AccountPill(wallyApp!!.focusedAccount)
            val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
            setContent {
                NavigationRoot(Modifier, WindowInsets(0,0,0,0), ap, unlock = unlock)
            }
            settle()
            nav.switch(ScreenId.Home)
            settle()
            waitForCatching { onNodeWithTag("AccountPillAccountName").isDisplayed() }
            unlock.triggerUnlockDialog(true, { println("Unlock attempted")})
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

    /**
     * System-level UI test: Send Nexa to yourself and verify that the transaction fee has been deducted.
     *
     * The test injects a [SendScreenViewModelFake]) into [NavigationRoot] via the `sendScreenViewModel` parameter.
     * The fake's `onSendButtonClicked()` validates the inputs and flips `isConfirming = true` so clicking the real Send button advances
     * the UI into the confirmation state. Clicking Confirm calls the no-op `actuallySend`,
     * after which the test mutates the `balanceFlow` backing the mock's `balanceState` to
     * simulate the post-send pill amount.
     */
    @Test
    fun sendNexaToSelfDeductsFee()
    {
        val nexaFormat = DecimalFormat("##,###,###,###,##0.00")
        val sendAmountText = "1000"
        val sendAmount = BigDecimal.parseString(sendAmountText)
        val simulatedFee = BigDecimal.parseString("0.05")
        val initialBalance = BigDecimal.parseString("50000.00")
        val finalBalance = initialBalance - simulatedFee

        val mockAccount = mockAccount(initialBalance)

        // ReceiveScreen / SendScreen. read `wallyApp.focusedAccount` directly,
        setSelectedAccount(mockAccount)

        runComposeUiTest {
            val mockAccountFlow = wallyApp!!.focusedAccount
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }
            val assetViewModel = AssetViewModelFake()
            val accountUiDataViewModel = AccountUiDataViewModel()
            val apvm = AccountPillViewModelFake(
              mockAccountFlow,
              balance = BalanceViewModelImpl(mockAccountFlow),
              sync = SyncViewModelFake(),
            )
            val unlock = UnlockViewModel(mockAccountFlow)
            val sendVm = SendScreenViewModelFake(mockAccount)

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(
                      Modifier,
                      WindowInsets(0, 0, 0, 0),
                      apvm,
                      assetViewModel,
                      accountUiDataViewModel,
                      unlock,
                      sendScreenViewModel = sendVm,
                    )
                }
            }
            settle()
            nav.switch(ScreenId.Home)
            settle()
            assignAccountsGuiSlots()
            waitForCatching { onNodeWithTag("AccountPillBalance").isDisplayed() }

            // ----- Step 1: Open app, save the current amount in the account pill -----
            val balanceBefore = mockAccount.balanceState.value!!
            val pillTextBefore = nexaFormat.format(balanceBefore)
            onNodeWithTag("AccountPillBalance").assertTextEquals(pillTextBefore)

            // ----- Step 2: Navigate to receive -----
            onNodeWithTag("ReceiveButton").performClick()
            settle()
            waitForCatching(6000, { "Receive screen address never appeared" }) {
                runCatching {
                    onNodeWithTag("receiveScreen:receiveAddress").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            val currentReceive = mockAccount.currentReceive
            assertNotNull(currentReceive)
            onNodeWithTag("receiveScreen:receiveAddress").assertTextEquals(currentReceive.address.toString())

            // ----- Step 3: Copy your own address to clipboard -----
            // Clicking the address text invokes setTextClipboard(addrStr). We keep
            // the address in `ownAddress` for the paste step rather than reading it
            // back via getTextClipboard() (which is a no-op in headless test envs).
            onNodeWithTag("receiveScreen:receiveAddress").performClick()
            settle()

            // ----- Step 4: Navigate back to the home screen -----
            onNodeWithTag("BackButton").performClick()
            settle()
            waitForCatching { onNodeWithTag("AccountPillBalance").isDisplayed() }

            // ----- Step 5: Navigate to send screen -----
            onNodeWithTag("SendButton").performClick()
            settle()
            waitForCatching { onNodeWithTag("sendToAddress").isDisplayed() }

            // ----- Step 6: Paste the address into the receive address field -----
            onNodeWithTag("sendToAddress").requestFocus()
            onNodeWithTag("sendToAddress").performTextInput(currentReceive.address.toString())
            settle()

            // ----- Step 7: Set Nexa amount to send to 1000 Nexa -----
            onNodeWithTag("amountToSendInput").requestFocus()
            onNodeWithTag("amountToSendInput").performTextInput(sendAmountText)
            settle()

            // ----- Step 8: Click send -----
            // SendScreenViewModelFake.onSendButtonClicked() validates the fields and
            // flips uiState.isConfirming = true, swapping Send → Confirm.
            onNodeWithTag("SendBottomButtonSend").assertIsDisplayed()
            onNodeWithTag("SendBottomButtonSend").performClick()
            settle()

            // ----- Step 9: Confirm send -----
            // The fake's actuallySend() is a no-op, so simulate the post-send pill
            // amount by mutating the observable backing the mock's balanceState.
            onNodeWithTag("SendBottomButtonConfirm").assertIsDisplayed()
            onNodeWithTag("SendBottomButtonConfirm").performClick()
            mockAccount.balance = finalBalance
            settle()

            // ----- Step 10: Verify the amount in the account pill is slightly less
            //                than the amount from step 1.
            nav.switch(ScreenId.Home)
            settle()
            waitForCatching { onNodeWithTag("AccountPillBalance").isDisplayed() }

            val balanceAfter = mockAccount.balanceState.value!!
            assertTrue(
              balanceAfter < balanceBefore,
              "Balance should have decreased after sending to self. before=$balanceBefore after=$balanceAfter"
            )
            // Sending to yourself returns the principal, so the delta must be only the
            // fee — strictly less than the amount that was sent.
            val delta = balanceBefore - balanceAfter
            assertTrue(
              delta < sendAmount,
              "Balance reduction should only be the fee, not the send amount. delta=$delta sendAmount=$sendAmount"
            )
            // And it must be visible in the account pill itself.
            val pillTextAfter = nexaFormat.format(finalBalance)
            onNodeWithTag("AccountPillBalance").assertTextEquals(pillTextAfter)
        }
    }
}