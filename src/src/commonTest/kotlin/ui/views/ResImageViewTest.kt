package ui.views

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.ui2.views.ResImageView
import ui2.waitForCatching
import kotlin.test.Test

class ResImageViewTest
{
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun resImageViewTest() = runComposeUiTest {
        setContent {
            ResImageView("icons/fastforward.png", modifier = Modifier.size(26.dp))
        }

        waitForCatching { onNodeWithTag("res_image").isDisplayed() }
        onNodeWithTag("res_image").assertWidthIsAtLeast(26.dp)
        onNodeWithTag("res_image").assertHeightIsAtLeast(26.dp)
    }
}