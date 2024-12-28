package ui2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyDropdownMenu
import info.bitcoinunlimited.www.wally.ui2.WallyDropDownUi2
import info.bitcoinunlimited.www.wally.ui2.supportedBlockchains
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WallyDropDownTestUi2
{
    @Test
    fun wallyDropdownMenuTest() = runComposeUiTest {
        val blockchains = supportedBlockchains.filter { true || it.value.isMainNet }
        val secondBlockchain = blockchains.entries.elementAt(1).toPair()
        val thirdBlockchain = blockchains.entries.elementAt(2).toPair()
        val itemsMock = listOf("one", "two", "three", "five", "balloon")
        var selectedIndexMock by mutableStateOf(0)
        setContent {
            WallyDropDownUi2<ChainSelector>(
              selected = blockchains.entries.first().toPair(),
              options = blockchains,
              onSelect = {

              }
            )
        }

        onNodeWithTag("DropdownMenuItemSelectedUi2").assertIsDisplayed().assertTextContains(blockchains.entries.first().key)

        /*

        onNodeWithTag("DropdownMenuItem-${blockchains.entries.first().key}").performClick()
        onNodeWithText(secondBlockchain.first).performClick()
        onNodeWithTag("DropdownMenuItem-${secondBlockchain.first}").assertIsDisplayed().assertTextContains(secondBlockchain.first)
        onNodeWithTag("DropdownMenuItem-${secondBlockchain.first}").performClick()
        onNodeWithText(thirdBlockchain.first).performClick()
        onNodeWithTag("DropdownMenuItem-${thirdBlockchain.first}").assertIsDisplayed().assertTextContains(thirdBlockchain.first)
         */
    }
}