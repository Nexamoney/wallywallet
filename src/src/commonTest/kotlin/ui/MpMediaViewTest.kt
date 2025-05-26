package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.ui.views.MpMediaView
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MpMediaViewTest:WallyUiTestBase()
{
    @Test
    fun mpMediaViewTest() = runComposeUiTest {

        val verbosity = 1

        // Simply render MpMediaView to see if it crashes when displaying media.
        setContent {
            MpMediaView(null, null, null, hideMusicView = true) { mi, draw ->
                var m = Modifier.background(Color.Transparent).testTag("mpMediaViewTest")
                m = if (verbosity > 0) m.size(64.dp, 64.dp) else m.size(26.dp, 26.dp)
                draw(m)
            }
        }

        // MpMediaView returns quite early and does not render when mediaUri is empty
        onNodeWithTag("mpMediaViewTest").assertDoesNotExist()
    }
}