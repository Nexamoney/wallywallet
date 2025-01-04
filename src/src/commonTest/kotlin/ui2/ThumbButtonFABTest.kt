package ui2

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui2.ThumbButton
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ThumbButtonFABTest
{
    @Test
    fun thumbButtonTest() = runComposeUiTest {
        val res = S.imageQr
        var isClicked = false

        setContent {
            ThumbButton(
              icon = Icons.Outlined.DocumentScanner,
              textRes = res,
              mod = Modifier.clickable {
                  isClicked = true
              }
            )
        }

        onNodeWithText(i18n(res)).assertIsDisplayed()
        onNodeWithContentDescription(i18n(res)).assertIsDisplayed()
        onNodeWithContentDescription(i18n(res)).performClick()
        assertTrue { isClicked }
    }

    @Test
    fun thumbButtonFABTest() = runComposeUiTest {

        // TODO: Test for gallery on some platforms
    }
}