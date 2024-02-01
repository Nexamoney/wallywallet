package info.bitcoinunlimited.www.wally

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import org.nexa.libnexakotlin.*
import java.net.URLDecoder
import java.net.URLEncoder

import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Density
import info.bitcoinunlimited.www.wally.ui.theme.colorError
import info.bitcoinunlimited.www.wally.ui.theme.colorNotice
import info.bitcoinunlimited.www.wally.ui.theme.colorWarning
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import okio.utf8Size
import java.io.InputStream
import java.util.zip.Inflater

actual fun inflateRfc1951(compressedBytes: ByteArray, expectedfinalSize: Long): ByteArray
{
    val inf = Inflater(true)  // true means do not wrap in the gzip header

    inf.setInput(compressedBytes)
    val ba = ByteArray(expectedfinalSize.toInt())
    val sz = inf.inflate(ba)
    if (sz != expectedfinalSize.toInt()) throw Exception("inflate wrong size")
    inf.end()
    return ba
}

actual fun stackTraceWithout(skipFirst: MutableSet<String>, ignoreFiles: MutableSet<String>?): String
{
    skipFirst.add("stackTraceWithout")
    skipFirst.add("stackTraceWithout\$default")
    val igf = ignoreFiles ?: defaultIgnoreFiles
    val st = Exception().stackTrace.toMutableList()
    while (st.isNotEmpty() && skipFirst.contains(st.first().methodName)) st.removeAt(0)
    st.removeAll { igf.contains(it.fileName) }
    val sb = StringBuilder()
    st.forEach { sb.append(it.toString()).append("\n") }
    return st.toString()
}

/** Gets the ktor http client for this platform */
actual fun GetHttpClient(timeoutInMs: Number): HttpClient = HttpClient(Android) {
    install(HttpTimeout) { requestTimeoutMillis = timeoutInMs.toLong() } // Long timeout because we don't expect a response right away; its a long poll
}

/** Get the clipboard.  Platforms that have a clipboard history should return that history, with the primary clip in index 0 */
actual fun getTextClipboard(): List<String>
{
    val c = (appContext() as? android.content.Context) ?: return listOf()
    var myClipboard = c.getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
    var clip = myClipboard.getPrimaryClip()
    val ret = mutableListOf<String>()
    if (clip != null)
    {
        for (i in 0 until clip.itemCount)
        {
            val item = clip?.getItemAt(i)
            item?.text?.toString()?.let { ret.add(it)}
        }
    }
    return ret
}

/** Sets the clipboard, potentially asynchronously. */
actual fun setTextClipboard(msg: String)
{
    val c = (appContext() as? android.content.Context) ?: return
    var clipboard = c.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    var clip = ClipData.newPlainText("text", msg)
    clipboard.setPrimaryClip(clip)
}

/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
actual fun isUiThread(): Boolean
{
    val tname = Thread.currentThread().name
    return (tname == "main")
}

actual fun displayAlert(alert: Alert)
{
    val act = currentActivity
    if (act != null)
    {
        act.displayAlert(alert)
    }

}

fun AlertLevel.color(): Color
{
    return when
    {
        level >= AlertLevel.EXCEPTION.level -> colorError
        level >= AlertLevel.ERROR.level -> colorError
        level >= AlertLevel.WARN.level -> colorWarning
        level >= AlertLevel.NOTICE.level -> colorNotice
        else -> Color.White
    }
}

val androidPlatformCharacteristics = PlatformCharacteristics(
  hasQrScanner = true,
  hasGallery = true,
  usesMouse = false,
  hasAlert = true,
  hasBack = true,
  hasNativeTitleBar = true,
  spaceConstrained = true,
  landscape = false,
  hasShare = true)

actual fun platform(): PlatformCharacteristics = androidPlatformCharacteristics

actual fun platformShare(textToShare: String)
{
    val act = currentActivity as? ComposeActivity
    if (act != null)
    {
        act.share(textToShare)
    }
}

