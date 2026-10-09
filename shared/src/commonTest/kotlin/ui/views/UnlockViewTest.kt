package ui.views

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.views.UnlockTile
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import info.bitcoinunlimited.www.wally.wallyApp
import ui.WallyUiTestBase
import ui.waitForCatching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnlockViewTest:WallyUiTestBase()
{
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unlockViewTest() = runComposeUiTest {
        val content = i18n(S.EnterPIN)
        val input = "1235"
        val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
        setContent {
            UnlockTile(unlock)
        }
        unlock.unlockTileSize.value = 300

        waitForCatching { onNodeWithText(content).isDisplayed() }
        onNodeWithTag("EnterPIN").assertExists()
        onNodeWithTag("EnterPIN").performTextInput(input)
        onNodeWithTag("EnterPIN").assert(hasText(input))
        if (platform().hasDoneButton)
            onNodeWithTag("UnlockTileAccept").assertIsDisplayed()
        println("unlockViewTest complete")
    }

    /** Issue #393: the PIN request belongs to the screen that raised it, so backing out of that screen drops it. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unlockTileDismissedByBackTest() = runComposeUiTest {
        val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
        setContent {
            UnlockTile(unlock)
        }
        runOnIdle { nav.switch(ScreenId.Home) }
        runOnIdle { nav.go(ScreenId.AccountDetails) }
        runOnIdle { unlock.triggerUnlockDialog(true) { println("Unlock attempted") } }

        // The tile stays up as long as we are on the screen that asked for the PIN
        waitForCatching(20000, { "PIN tile never appeared" }) {
            waitForIdle()
            unlock.unlockTileSize.value > 0
        }
        onNodeWithTag("EnterPIN", useUnmergedTree = true).assertExists()
        onNodeWithTag("EnterPIN", useUnmergedTree = true).performTextInput("12")

        runOnIdle { nav.back() }

        waitForCatching(20000, { "PIN tile survived the back button" }) {
            waitForIdle()
            unlock.unlockTileSize.value == 0
        }
        onNodeWithTag("EnterPIN", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("", unlock.pin.value)
        assertNull(unlock.unlockThen)

        // Leave the navigation where the rest of the suite expects to find it
        runOnIdle { nav.switch(ScreenId.Home) }
        println("unlockTileDismissedByBackTest complete")
    }
}
