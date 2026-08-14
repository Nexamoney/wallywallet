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

/**
 * Toggle the "enable screen" settings switches and verify that the rest of the app follows:
 * the screen's bottom bar button appears and navigates to that screen.
 */
@OptIn(ExperimentalTestApi::class)
class EnableScreensTest: WallyUiTestBase(false)
{
    /** Both screens start disabled, as they are on a fresh install. */
    private fun disableBothScreens()
    {
        showIdentityPref.value = false
        showTricklePayPref.value = false
        menuItems.value = permanentMenuItems
        moreMenuItems.value = setOf()
    }

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
    fun enableIdentityScreenTest()
    {
        disableBothScreens()
        val account = wallyApp!!.newAccount("enblIdent", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                setSelectedAccount(account)
                showNavigationRoot()

                // Identity is off, so it has no bottom bar button
                onNodeWithTag("IdentityButton").assertDoesNotExist()

                // Reach the settings screen through the more menu
                waitForCatching { onNodeWithTag("MoreMenuButton").isDisplayed() }
                onNodeWithTag("MoreMenuButton").performClick()
                settle()
                waitForCatching { onNodeWithText(i18n(S.title_activity_settings)).isDisplayed() }
                onNodeWithText(i18n(S.title_activity_settings)).performClick()
                settle()
                assertEquals(ScreenId.Settings, nav.currentScreen.value)

                onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.enableIdentityMenu)))
                onNodeWithTag("IdentitySwitch").assertIsOff()
                onNodeWithTag("IdentitySwitch").performClick()
                settle()
                onNodeWithTag("IdentitySwitch").assertIsOn()

                // Enabling it puts identity in the bottom bar, and it navigates to the identity screen
                waitForCatching { onNodeWithTag("IdentityButton").isDisplayed() }
                onNodeWithTag("IdentityButton").performClick()
                settle()

                assertEquals(ScreenId.Identity, nav.currentScreen.value)
                waitForCatching { onNodeWithText(i18n(S.IdentityRegistrations)).isDisplayed() }
                onNodeWithText(i18n(S.IdentityRegistrations)).assertIsDisplayed()
            }
        }
        finally
        {
            disableBothScreens()
            nav.switch(ScreenId.Home)
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun enableServicesScreenTest()
    {
        disableBothScreens()
        wallyApp!!.tpDomains.clear()
        val account = wallyApp!!.newAccount("enblSvc", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                setSelectedAccount(account)
                showNavigationRoot()

                onNodeWithTag("TricklePayRegistrationsButton").assertDoesNotExist()

                // Android draws the title bar natively, so its settings icon is only reachable via the more menu
                if (platform().hasNativeTitleBar)
                {
                    onNodeWithTag("MoreMenuButton").performClick()
                    settle()
                    waitForCatching { onNodeWithText(i18n(S.title_activity_settings)).isDisplayed() }
                    onNodeWithText(i18n(S.title_activity_settings)).performClick()
                }
                else
                {
                    waitForCatching { onNodeWithContentDescription("Settings").isDisplayed() }
                    onNodeWithContentDescription("Settings").performClick()
                }
                settle()
                assertEquals(ScreenId.Settings, nav.currentScreen.value)

                onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.EnableServices)))
                onNodeWithTag("ServicesSwitch").assertIsOff()
                onNodeWithTag("ServicesSwitch").performClick()
                settle()
                onNodeWithTag("ServicesSwitch").assertIsOn()

                waitForCatching { onNodeWithTag("TricklePayRegistrationsButton").isDisplayed() }
                onNodeWithTag("TricklePayRegistrationsButton").performClick()
                settle()

                assertEquals(ScreenId.TricklePayRegistrations, nav.currentScreen.value)
                waitForCatching { onNodeWithText(i18n(S.NoServicesRegistered)).isDisplayed() }
                onNodeWithText(i18n(S.NoServicesRegistered)).assertIsDisplayed()
            }
        }
        finally
        {
            disableBothScreens()
            nav.switch(ScreenId.Home)
            wallyApp!!.deleteAccount(account)
        }
    }
}
