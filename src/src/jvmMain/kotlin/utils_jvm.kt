package info.bitcoinunlimited.www.wally

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import okio.FileNotFoundException
import org.xml.sax.InputSource
import java.awt.Toolkit
import java.awt.datatransfer.*
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder

actual fun GetHttpClient(timeoutInMs: Number): HttpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
    install(HttpTimeout) { requestTimeoutMillis = timeoutInMs.toLong() }
}

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
    val c: Clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
    try
    {
        val s: String = c.getData(DataFlavor.stringFlavor) as String
        return listOf(s)
    }
    catch (e:Exception)
    {
        return emptyList()
    }
}

/** Sets the clipboard, potentially asynchronously. */
actual fun setTextClipboard(msg: String)
{
    val c: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val sel = StringSelection(msg)
    c.setContents(sel, sel)
}


/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
actual fun isUiThread(): Boolean
{
    // TODO this assumption (ui thread is main) may not be right
    if (Thread.currentThread().name == "main") return true
    else return false
}

/** Access a file from the resource area */
fun readResourceFile(filename: String): InputStream
{
    val nothing = Objectify<Int>(0)

    val loadTries = listOf<()->InputStream> (
      { nothing::class.java.getClassLoader().getResourceAsStream(filename) },
      { nothing::class.java.getClassLoader().getResourceAsStream("icons/" + filename) },
      { File(filename).inputStream() },
    )
    for (i in loadTries)
    {
        try
        {
            val ins = i()
            return ins
        }
        catch (e:Exception)
        {}
    }
    throw FileNotFoundException()
}


fun loadImageBmp(file: File): ImageBitmap
{
    val strm = file.inputStream().buffered()
    return loadImageBitmap(strm)
}

fun loadSvgToPainter(file: File, density: Density): Painter
{
    val strm = file.inputStream().buffered()
    return loadSvgPainter(strm, density)
}

fun loadXmlImageVector(file: File, density: Density): ImageVector =
  file.inputStream().buffered().use { androidx.compose.ui.res.loadXmlImageVector(InputSource(it), density) }

@Composable
fun loadIcon(ins: InputStream): ImageVector?
{
    val density = LocalDensity.current
    return ins.buffered().use { androidx.compose.ui.res.loadXmlImageVector(InputSource(it), density) }
}
