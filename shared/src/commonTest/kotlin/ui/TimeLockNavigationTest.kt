package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.wallyApp
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.assignAccountsGuiSlots
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.requestedHomeTab
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import org.nexa.libnexakotlin.GetLog
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val LogIt = GetLog("BU.wally.timeLockNavTest")

/**
 * System-level navigation tests for the time-lock feature: they drive the real
 * [NavigationRoot] (which builds the real [TimeLockViewModel] /
 * [repositories.TimeLockContractRepository] for the focused account) and verify
 * routing into and out of the Time Lock Vault screen, plus the Contracts-tab entry
 * point on Home.
 *
 * The preamble mirrors the working `NavigationRootTest.unlockTest`: after
 * `nav.switch(Home)` we wait for the account pill to render, which both gets
 * past the native splash and confirms the root content is live before we
 * navigate further.
 */
@OptIn(ExperimentalTestApi::class)
class TimeLockNavigationTest : WallyUiTestBase()
{
    private fun ComposeUiTest.mountNavigationRoot()
    {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        val assetViewModel = AssetViewModelFake()
        val accountUiDataViewModel = AccountUiDataViewModelFake()
        val pill = AccountPill(wallyApp!!.focusedAccount)
        val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                NavigationRoot(Modifier, WindowInsets(0, 0, 0, 0), pill, assetViewModel, accountUiDataViewModel, unlock)
            }
        }
    }

    /** Switch to Home (on [tab], if given) and wait until the splash is gone and Home has rendered. */
    private fun ComposeUiTest.showHome(tab: Int? = null)
    {
        requestedHomeTab = tab
        mountNavigationRoot()
        settle()
        nav.switch(ScreenId.Home)
        settle()
        assignAccountsGuiSlots()
        settle()
        waitForCatching { onNodeWithTag("AccountPillAccountName").isDisplayed() }
    }

    @Test
    fun clickingTimeLockVaultRowRoutesToTimeLock()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            // Home on the Contracts tab → tap the Time Lock Vault row. The row's onClick is
            // wired to nav.go(ScreenId.TimeLock); assert the router actually moved
            // there (system-level routing, independent of screen-render timing).
            showHome(tab = 2)
            waitForCatching { onAllNodesWithText(i18n(S.tlvTitle)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText(i18n(S.tlvTitle)).performClick()
            settle()
            waitForCatching { nav.currentScreen.value == ScreenId.TimeLock }
            assertEquals(ScreenId.TimeLock, nav.currentScreen.value)
        }
    }

    /**
     * The header's New vault pushes the create child screen as the "NEW"
     * TimeLock sub-state, and BACK pops it straight back to the master list —
     * exercising the real [TimeLockScreen] wrapper wiring end to end.
     */
    @Test
    fun newVaultOpensCreateChildAndBackReturns()
    {
        // The TimeLock screen requires a focused account (withAccount bounces
        // to the previous screen otherwise), so make one for the test.
        val account = wallyApp!!.newAccount("tlNavCreate", 0U, "", org.nexa.libnexakotlin.ChainSelector.NEXATESTNET)!!
        wallyApp!!.focusedAccount.value = account
        try
        {
            androidx.compose.ui.test.v2.runComposeUiTest {
                showHome()
                nav.go(ScreenId.TimeLock)
                settle()
                waitForCatching { nav.currentScreen.value == ScreenId.TimeLock }
                // Both the account pill's contract line and the first-run header offer New vault.
                waitForCatching { onAllNodesWithText(i18n(S.tlvNewVault)).fetchSemanticsNodes().isNotEmpty() }
                onAllNodesWithText(i18n(S.tlvNewVault)).onFirst().performClick()
                settle()
                assertContentEquals(byteArrayOf(0x4E, 0x45, 0x57), nav.currentSubState.value) // "NEW"
                waitForCatching { onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).isDisplayed() }

                nav.back()
                settle()
                assertNull(nav.currentSubState.value)
                assertEquals(ScreenId.TimeLock, nav.currentScreen.value)
            }
        }
        finally
        {
            wallyApp!!.focusedAccount.value = null
            runCatching { wallyApp!!.deleteAccount(account) }
        }
    }

    @Test
    fun backFromTimeLockReturnsHome()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            showHome()
            nav.go(ScreenId.TimeLock)
            settle()
            waitForCatching { nav.currentScreen.value == ScreenId.TimeLock }

            nav.back()
            settle()
            // Router popped back to Home.
            waitForCatching { nav.currentScreen.value == ScreenId.Home }
            assertEquals(ScreenId.Home, nav.currentScreen.value)
        }
    }

    @Test
    fun contractsTabListsTimeLockVault()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            // Home on the Contracts tab (3rd tab); the Time Lock Vault summary row is there.
            showHome(tab = 2)
            waitForCatching { onNodeWithText(i18n(S.tlvTitle)).isDisplayed() }
            onNodeWithText(i18n(S.tlvTitle)).assertIsDisplayed()
        }
    }

    /**
     * Regression for the master–detail back stack: [ScreenNav.back] must
     * restore the popped entry's sub-state and data — the TimeLock screen
     * pushes its detail level beneath the txs level, so clearing instead of
     * restoring skipped the detail on BACK and left a dead stack entry.
     */
    @Test
    fun backRestoresSubStateOfPoppedEntry()
    {
        val n = ScreenNav()
        n.reset(ScreenId.Home)
        n.go(ScreenId.TimeLock)                                  // master
        n.go(ScreenId.TimeLock, byteArrayOf(0x44), "vault-1")    // detail
        n.go(ScreenId.TimeLock, byteArrayOf(0x54), "vault-1")    // txs
        n.back()
        assertContentEquals(byteArrayOf(0x44), n.currentSubState.value)
        assertEquals("vault-1", n.curData.value)
        assertEquals(ScreenId.TimeLock, n.currentScreen.value)
        n.back()
        assertNull(n.currentSubState.value)
        assertNull(n.curData.value)
        assertEquals(ScreenId.TimeLock, n.currentScreen.value)
        n.back()
        assertEquals(ScreenId.Home, n.currentScreen.value)
    }
}
