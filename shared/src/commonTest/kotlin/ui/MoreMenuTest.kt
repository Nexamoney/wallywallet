package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.assignAccountsGuiSlots
import info.bitcoinunlimited.www.wally.ui.menuItems
import info.bitcoinunlimited.www.wally.ui.moreMenuItems
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.permanentMenuItems
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.showIdentityPref
import info.bitcoinunlimited.www.wally.ui.showTricklePayPref
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue #680: picking a screen in the more menu must close the more menu. */
@OptIn(ExperimentalTestApi::class)
class MoreMenuTest: WallyUiTestBase(false)
{
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.showNavigationRoot()
    {
        val viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        val pill = AccountPill(wallyApp!!.focusedAccount)
        val unlock = UnlockViewModel(wallyApp!!.focusedAccount)

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                NavigationRoot(Modifier, WindowInsets(0, 0, 0, 0), pill, AssetViewModelFake(), AccountUiDataViewModelFake(), unlock)
            }
        }
        assignAccountsGuiSlots()
        nav.switch(ScreenId.Home)
        settle()
    }

    @Test
    fun moreMenuClosesAfterNavigatingTest()
    {
        showIdentityPref.value = false
        showTricklePayPref.value = false
        menuItems.value = permanentMenuItems
        moreMenuItems.value = setOf()
        val account = wallyApp!!.newAccount("moreMenu680", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                setSelectedAccount(account)
                showNavigationRoot()

                waitForCatching { onNodeWithTag("MoreMenuButton").isDisplayed() }
                onNodeWithTag("MoreMenuButton").performClick()
                settle()
                waitForCatching { onNodeWithTag("MoreMenuSheet").isDisplayed() }

                onNodeWithText(i18n(S.title_activity_settings)).performClick()
                settle()

                assertEquals(ScreenId.Settings, nav.currentScreen.value)
                onNodeWithTag("MoreMenuSheet").assertIsNotDisplayed()
            }
        }
        finally
        {
            showIdentityPref.value = false
            showTricklePayPref.value = false
            nav.switch(ScreenId.Home)
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun navSwitchClosesMoreMenuTest()
    {
        showIdentityPref.value = false
        showTricklePayPref.value = false
        menuItems.value = permanentMenuItems
        moreMenuItems.value = setOf()
        val account = wallyApp!!.newAccount("moreMenuNav680", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                setSelectedAccount(account)
                showNavigationRoot()

                waitForCatching { onNodeWithTag("MoreMenuButton").isDisplayed() }
                onNodeWithTag("MoreMenuButton").performClick()
                settle()
                waitForCatching { onNodeWithTag("MoreMenuSheet").isDisplayed() }

                nav.switch(ScreenId.Settings)
                settle()

                onNodeWithTag("MoreMenuSheet").assertIsNotDisplayed()
            }
        }
        finally
        {
            showIdentityPref.value = false
            showTricklePayPref.value = false
            nav.switch(ScreenId.Home)
            wallyApp!!.deleteAccount(account)
        }
    }
}
