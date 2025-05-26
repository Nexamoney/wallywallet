package ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui.WallyDropDown
import info.bitcoinunlimited.www.wally.ui.supportedBlockchains
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WallyDropDownTest:WallyUiTestBase()
{
    @Test
    fun wallyDropdownMenuTest() = runComposeUiTest {
        val blockchains = supportedBlockchains
        val selectedBlockChain = mutableStateOf(blockchains.entries.first().toPair())
        val secondBlockchain = blockchains.entries.elementAt(1).toPair()
        val thirdBlockchain = blockchains.entries.elementAt(2).toPair()

        setContent {
            WallyDropDown<ChainSelector>(
              selected =selectedBlockChain.value,
              options = blockchains,
              onSelect = {
                  selectedBlockChain.value = it
              }
            )
        }

        onNodeWithTag("DropdownMenuItemSelected").assertIsDisplayed().assertTextContains(blockchains.entries.first().key)
        onNodeWithTag("DropdownMenuItemSelected").performClick()
        onNodeWithText(secondBlockchain.first).assertIsDisplayed()
        onNodeWithText(thirdBlockchain.first).assertIsDisplayed()
        onNodeWithText(secondBlockchain.first).performClick()
        onNodeWithTag("DropdownMenuItemSelected").assertIsDisplayed().assertTextContains(secondBlockchain.first)
        onNodeWithText(secondBlockchain.first).performClick()
        onNodeWithText(thirdBlockchain.first).assertIsDisplayed()
        onNodeWithText(thirdBlockchain.first).performClick()
        onNodeWithTag("DropdownMenuItemSelected").assertIsDisplayed().assertTextContains(thirdBlockchain.first)
    }
}