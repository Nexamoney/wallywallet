package ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.CommonApp
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.REG_TEST_ONLY
import info.bitcoinunlimited.www.wally.ui.theme.FontScale
import info.bitcoinunlimited.www.wally.ui.theme.FontScaleStyle
import info.bitcoinunlimited.www.wally.ui.theme.WallyRoundedButton
import info.bitcoinunlimited.www.wally.ui.theme.WallySectionTextStyle
import kotlin.test.Test
import kotlin.test.assertTrue

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
    fun wallySmallTextButtonTest() = runComposeUiTest {
        var clicked by mutableStateOf(false)
        setContent {
            WallySmallTextButton(S.ButtonTextHere, true, onClick = {
                clicked = true
            })
        }

        onNodeWithText(i18n(S.ButtonTextHere)).isDisplayed()
        onNodeWithText(i18n(S.ButtonTextHere)).performClick()
        assertTrue(clicked)
    }

    @Test
    fun wallyBoringButtonTest() = runComposeUiTest {
        var clicked by mutableStateOf(false)
        val text = i18n(S.ButtonTextHere)
        setContent {
            WallyBoringButton( enabled = true, onClick = {
                clicked = true
            }) {
                Text(text)
            }
        }

        onNodeWithText(text).isDisplayed()
        onNodeWithText(text).performClick()
        assertTrue(clicked)
    }

    @Test fun noticeTextTest() = runComposeUiTest {
        val text = i18n(S.ButtonTextHere)
        setContent {
            NoticeText(text, Modifier)
        }
        onNodeWithText(text).isDisplayed()
    }

    @Test
    fun wallyDropdownItemFontStyleTest() = runComposeUiTest {
        setContent {
            Text(
              text = "text",
              modifier = Modifier,
              style = WallyDropdownItemFontStyle()
            )
        }
    }

    @Test
    fun titleTextTest() = runComposeUiTest {
        val text = i18n(S.ButtonTextHere)
        setContent {
            TitleText(text, Modifier.fillMaxSize())
        }
        onNodeWithText(text).isDisplayed()
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

    @Test
    fun wallyDecimalEntryTest() = runComposeUiTest {
        val valueMock = mutableStateOf("0")
        wallyApp = CommonApp()

        setContent {
            WallyDecimalEntry(valueMock)
        }
        onNodeWithText(valueMock.value).assertIsDisplayed()
        onNodeWithTag("WallyDataEntryTextField").assertIsDisplayed()
        onNodeWithTag("WallyDataEntryTextField").performTextInput("0.1")
    }
}