package ui.views

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui2.views.UnlockTile
import info.bitcoinunlimited.www.wally.ui2.views.unlockTileSize
import ui2.WallyUiTestBase
import ui2.waitForCatching
import kotlin.test.Test

class UnlockViewTest:WallyUiTestBase()
{
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unlockViewTest() = runComposeUiTest {
        val content = i18n(S.EnterPIN)
        val input = "1235"
        setContent {
            UnlockTile()
        }
        unlockTileSize.value = 300

        waitForCatching { onNodeWithText(content).isDisplayed() }
        onNodeWithTag("EnterPIN").assertExists()
        onNodeWithTag("EnterPIN").performTextInput(input)
        onNodeWithTag("EnterPIN").assert(hasText(input))
        if (platform().hasDoneButton)
            onNodeWithTag("UnlockTileAccept").assertIsDisplayed()
    }
}