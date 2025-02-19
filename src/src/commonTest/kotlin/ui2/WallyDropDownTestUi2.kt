package ui2

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui2.WallyDropDownUi2
import info.bitcoinunlimited.www.wally.ui2.supportedBlockchains
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WallyDropDownTestUi2:WallyUiTestBase()
{
    @Test
    fun wallyDropdownMenuTest() = runComposeUiTest {
        val blockchains = supportedBlockchains
        val selectedBlockChain = mutableStateOf(blockchains.entries.first().toPair())
        val secondBlockchain = blockchains.entries.elementAt(1).toPair()
        val thirdBlockchain = blockchains.entries.elementAt(2).toPair()

        setContent {
            WallyDropDownUi2<ChainSelector>(
              selected =selectedBlockChain.value,
              options = blockchains,
              onSelect = {
                  selectedBlockChain.value = it
              }
            )
        }

        onNodeWithTag("DropdownMenuItemSelectedUi2").assertIsDisplayed().assertTextContains(blockchains.entries.first().key)
        onNodeWithTag("DropdownMenuItemSelectedUi2").performClick()
        onNodeWithText(secondBlockchain.first).assertIsDisplayed()
        onNodeWithText(thirdBlockchain.first).assertIsDisplayed()
        onNodeWithText(secondBlockchain.first).performClick()
        onNodeWithTag("DropdownMenuItemSelectedUi2").assertIsDisplayed().assertTextContains(secondBlockchain.first)
        onNodeWithText(secondBlockchain.first).performClick()
        onNodeWithText(thirdBlockchain.first).assertIsDisplayed()
        onNodeWithText(thirdBlockchain.first).performClick()
        onNodeWithTag("DropdownMenuItemSelectedUi2").assertIsDisplayed().assertTextContains(thirdBlockchain.first)
    }
}