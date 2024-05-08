package ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui.theme.FontScale
import info.bitcoinunlimited.www.wally.ui.theme.FontScaleStyle
import info.bitcoinunlimited.www.wally.ui.theme.WallyRoundedButton
import info.bitcoinunlimited.www.wally.ui.theme.WallySectionTextStyle
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ThemeTest
{
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

    @Test
    fun wallySelectionTextStyleTest() = runComposeUiTest {
        setContent {
            Text(
              text = "text",
              modifier = Modifier,
              style = WallySectionTextStyle()
            )
        }
    }

    @Test
    fun wallyFontScaleTest() = runComposeUiTest {
        setContent {
            Text(
              text = "text",
              modifier = Modifier,
              fontSize = FontScale(0.75)
            )
        }
    }

    @Test
    fun wallyFontFontScaleStyleTest() = runComposeUiTest {
        setContent {
            Text(
              text = "text",
              modifier = Modifier,
              style = FontScaleStyle(2.0)
            )
        }
    }

}