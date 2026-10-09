package ui.views

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui.views.offThreadDecode
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/** What offThreadDecode last returned to the composition, and how many times the decoder was called */
private class DecodeProbe
{
    @Volatile var image: ImageBitmap? = null
    @Volatile var pending = true
    @Volatile var calls = 0
}

@OptIn(ExperimentalTestApi::class)
class OffThreadDecodeTest
{
    private val bmp = ImageBitmap(1, 1)

    /** Compose offThreadDecode with a decoder that answers null for the first [nullsFirst] calls (forever if negative),
     * then [bmp], and wait until it reports that it is no longer pending */
    private fun ComposeUiTest.decode(retry: Boolean, nullsFirst: Int, throws: Boolean = false): DecodeProbe
    {
        val probe = DecodeProbe()
        setContent {
            val (im, pending) = offThreadDecode("key", retry = retry) {
                val n = probe.calls++
                if (throws) throw IllegalStateException("undecodable")
                if (nullsFirst < 0 || n < nullsFirst) null else bmp
            }
            probe.image = im
            probe.pending = pending
        }
        // retry backs off 150+300+600+1200+2400 ms, so leave room for all of it
        waitUntil(timeoutMillis = 15000) { !probe.pending }
        return probe
    }

    @Test
    fun decodeLandsInComposition() = runComposeUiTest {
        val probe = decode(retry = false, nullsFirst = 0)
        assertSame(bmp, probe.image)
        assertEquals(1, probe.calls)
    }

    @Test
    fun nullIsFinalWithoutRetry() = runComposeUiTest {
        val probe = decode(retry = false, nullsFirst = -1)
        assertNull(probe.image)
        assertFalse(probe.pending)
        assertEquals(1, probe.calls)
    }

    @Test
    fun retryPicksUpALateFile() = runComposeUiTest {
        // the file another thread is writing turns up on the third probe
        val probe = decode(retry = true, nullsFirst = 2)
        assertSame(bmp, probe.image)
        assertEquals(3, probe.calls)
    }

    @Test
    fun retryGivesUpAfterSixAttempts() = runComposeUiTest {
        val probe = decode(retry = true, nullsFirst = -1)
        assertNull(probe.image)
        assertFalse(probe.pending)
        assertEquals(6, probe.calls)
    }

    @Test
    fun decoderExceptionIsTreatedAsNoImage() = runComposeUiTest {
        val probe = decode(retry = false, nullsFirst = 0, throws = true)
        assertNull(probe.image)
        assertFalse(probe.pending)
        assertEquals(1, probe.calls)
    }
}
