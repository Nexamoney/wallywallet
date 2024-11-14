package info.bitcoinunlimited.www.wally

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.plugins.*
import okio.BufferedSource
import okio.FileNotFoundException
import okio.buffer
import okio.source
import org.jetbrains.skia.*
import org.xml.sax.InputSource
import java.awt.Toolkit
import java.awt.datatransfer.*
import java.io.File
import java.io.InputStream
import java.util.zip.Inflater
import org.nexa.libnexakotlin.Objectify
import java.awt.Desktop
import java.net.URI
import kotlin.math.abs


// on android this fails with couldn't find "libskiko-android-arm64.so", see https://github.com/JetBrains/skiko/issues/531
fun scaleUsingSurface(image: Image, width: Int, height: Int): Image
{
    val surface = Surface.makeRasterN32Premul(width, height)
    val canvas = surface.canvas
    val paint = Paint().apply {
        this.isAntiAlias = true
        this.isDither = true
        // this.filterQuality = FilterQuality.HIGH
    }

    canvas.drawImageRect(image, Rect(0f, 0f, width.toFloat(), height.toFloat()), paint)
    val im = surface.makeImageSnapshot()
    return im
}

actual fun makeImageBitmap(imageBytes: ByteArray, width: Int, height: Int,scaleMode: ScaleMode): ImageBitmap?
{
    try
    {
        val imIn = Image.makeFromEncoded(imageBytes)
        var newWidth = width
        var newHeight = height
        if ((scaleMode != ScaleMode.DISTORT) && (imIn.height != 0))
        {
            var ratio = imIn.width.toFloat() / imIn.height.toFloat()

            if (scaleMode == ScaleMode.INSIDE)
            {
                if (ratio < 1.0) newWidth = (height * ratio).toInt()
                else newHeight = (width / ratio).toInt()
            }
            if (scaleMode == ScaleMode.COVER)
            {
                if (ratio < 1.0) newHeight = (width / ratio).toInt()
                else newWidth = (width * ratio).toInt()
            }
        }
        val im = scaleUsingSurface(imIn, newWidth, newHeight)
        return im.toComposeImageBitmap()
    }
    catch (e: IllegalArgumentException)  // imageBytes can't be decoded
    {
        return null
    }
}

// the platform release did not exist so no possibility of old accounts
actual fun convertOldAccounts(): Boolean
{
    return false
}
actual fun applicationState(): ApplicationState
{
    return ApplicationState(ApplicationState.RunState.ACTIVE)
}

actual fun platformRam():Long?
{
    val mem = Runtime.getRuntime().maxMemory()
    return mem
}


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
actual fun GetHttpClient(timeoutInMs: Number): HttpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
    install(HttpTimeout) { requestTimeoutMillis = timeoutInMs.toLong() }
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

actual fun getResourceFile(name: String): BufferedSource
{
    return readResourceFile(name).source().buffer()
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

val jvmPlatformCharacteristics = PlatformCharacteristics(
  hasQrScanner = false,
  hasGallery = false,
  usesMouse = true,
  hasAlert = false,
  hasBack = false,
  hasNativeTitleBar = false,
  spaceConstrained = false,
  landscape = true,
  hasShare = true,
  supportsBackgroundSync = false,
  0.dp)

actual fun displayAlert(alert: Alert)
{}

actual fun platform(): PlatformCharacteristics = jvmPlatformCharacteristics

actual fun ImageQrCode(imageParsed: (String?)->Unit): Boolean
{
    return false
}

/** Actually share this text using the platform's share functionality */
actual fun platformShare(textToShare: String)
{
    return
}

/** Initiate a platform-level notification message.  Note that these messages visually disrupt the user's potentially unrelated task
 * and may play a sound, so this must be used sparingly.
 */
actual fun platformNotification(message:String, title: String?, onclickUrl:String?, severity: AlertLevel)
{
    when (severity)
    {
        AlertLevel.CLEAR -> {}
        AlertLevel.SUCCESS ->
        {
            if (title != null) displaySuccess(title, message)
            else displaySuccess(message)
        }
        AlertLevel.NOTICE ->
        {
            if (title != null) displayNotice(title, message)
            else displayNotice(message)
        }
        AlertLevel.WARN ->
        {
            if (title != null) displayWarning(title, message)
            else displayWarning(message)
        }
        AlertLevel.ERROR, AlertLevel.EXCEPTION ->
        {
            if (title != null) displayError(title, message)
            else displayError(message)
        }
    }
    // TODO actually use platform level notifications
}


// No IME (soft keyboard on desktop)
@Composable actual fun isImeVisible(): Boolean = false

@Composable actual fun getImeHeight(): Dp = 0.dp

actual fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            println("Desktop is not supported on this system")
        }
    } catch (e: Exception) {
        println("Failed to open URL: $url. Error: ${e.message}")
    }
}