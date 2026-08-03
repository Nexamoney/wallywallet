package ui.views

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui.views.WallyDropdownMenu
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WallyDropDownTest
{
    @Test
    fun wallyDropdownMenuTest() = runComposeUiTest {
        val itemsMock = listOf("one", "two", "three", "five", "balloon")
        val labelMock = ""
        var selectedIndexMock by mutableStateOf(0)
        setContent {
            WallyDropdownMenu(
              selectedIndex = selectedIndexMock,
              label = labelMock,
              items = itemsMock,
              onItemSelected = { index, _ ->
                  selectedIndexMock = index
              }
            )
        }

        onNodeWithTag("WallyDropdownMenuItemSelected").assertIsDisplayed().assertTextContains(itemsMock.first())
        onNodeWithTag("WallyDropdownMenuItemSelected").performClick()
        onNodeWithText(itemsMock[2]).performClick()
        onNodeWithTag("WallyDropdownMenuItemSelected").assertIsDisplayed().assertTextContains(itemsMock[2])
        onNodeWithTag("WallyDropdownMenuItemSelected").performClick()
        onNodeWithText(itemsMock[3]).performClick()
        onNodeWithTag("WallyDropdownMenuItemSelected").assertIsDisplayed().assertTextContains(itemsMock[3])
    }
}