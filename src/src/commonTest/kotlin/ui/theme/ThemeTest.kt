package ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyRoundedButton
import kotlin.test.Test

class ThemeTest
{

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun wallyRoundedButtonTest() = runComposeUiTest {
        setContent {
            var count by remember { mutableStateOf(0) }
            Text(text = count.toString())
            WallyRoundedButton({
                count++
            }) {
                Text("Button")
            }
        }

        onNodeWithText("0").assertIsDisplayed()
        onNodeWithText("Button").assertIsDisplayed()
        onNodeWithText("Button").performClick()
        onNodeWithText("1").assertIsDisplayed()
        onNodeWithText("Button").performClick()
        onNodeWithText("Button").performClick()
        onNodeWithText("3").assertIsDisplayed()
    }
}