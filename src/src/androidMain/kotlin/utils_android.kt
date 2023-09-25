package info.bitcoinunlimited.www.wally

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import org.nexa.libnexakotlin.*
import java.net.URLDecoder
import java.net.URLEncoder

import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import java.io.InputStream

/** Converts an encoded URL to a raw string */
actual fun String.urlDecode():String
{
    return URLDecoder.decode(this,"utf-8")
}

actual fun String.urlEncode():String
{
    return URLEncoder.encode(this, "utf-8")
}

/** Get the clipboard.  Platforms that have a clipboard history should return that history, with the primary clip in index 0 */
actual fun getTextClipboard(): List<String>
{
    val c = androidContext ?: return listOf()
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
    val c = androidContext ?: return
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

/** Access a file from the resource area */
actual fun readResourceFile(filename: String): InputStream
{
    TODO()
}

@Composable
actual fun loadImage(filename: String): ImageContainer?
{
    val res = when(filename)
    {
        "ic_faucet_drip.xml" -> R.drawable.ic_faucet_drip
        else -> null
    }

    if (res == null) return null
    return ImageContainer(painterResource(id = res))
}