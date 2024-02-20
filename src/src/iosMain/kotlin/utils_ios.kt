package info.bitcoinunlimited.www.wally

import androidx.compose.runtime.Composable
import info.bitcoinunlimited.www.wally.ui.softKeyboardBar
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.BufferedSource
import okio.FileNotFoundException
import okio.buffer
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.resource
import org.nexa.libnexakotlin.GetLog
import platform.Foundation.*
import platform.UIKit.*

private val LogIt = GetLog("BU.wally.utils_ios")

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData
{
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray
{
    val len = length.toInt()
    val ba = ByteArray(len)
    ba.usePinned {
        this.getBytes(it.addressOf(0))
    }
    return ba
}


@OptIn(ExperimentalForeignApi::class)
actual fun inflateRfc1951(compressedBytes: ByteArray, expectedfinalSize: Long): ByteArray
{
    val nsd = compressedBytes.toNSData()
    val dec = nsd.decompressedDataUsingAlgorithm(NSDataCompressionAlgorithmZlib, null)
    return dec?.toByteArray() ?: byteArrayOf()
}

actual fun stackTraceWithout(skipFirst: MutableSet<String>, ignoreFiles: MutableSet<String>?): String
{
    skipFirst.add("stackTraceWithout")
    skipFirst.add("stackTraceWithout\$default")
    val igf = ignoreFiles ?: defaultIgnoreFiles
    val st = Exception().stackTraceToString()
    //while (st.isNotEmpty() && skipFirst.contains(st.first().methodName)) st.removeAt(0)
    //st.removeAll { igf.contains(it.fileName) }
    return st //.toTypedArray()
}

actual fun GetHttpClient(timeoutInMs: Number): HttpClient = HttpClient(CIO)
{
    install(HttpTimeout) { requestTimeoutMillis = timeoutInMs.toLong() }
}

/** Get the clipboard.  Platforms that have a clipboard history should return that history, with the primary clip in index 0 */
actual fun getTextClipboard(): List<String>
{
    val clips = UIPasteboard.generalPasteboard.strings
    if (clips == null) return emptyList()
    return clips.map { it.toString() }
}

/** Sets the clipboard, potentially asynchronously. */
actual fun setTextClipboard(msg: String)
{
    UIPasteboard.generalPasteboard.string = msg
}


/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
actual fun isUiThread(): Boolean
{
    return NSThread.isMainThread
}

/** Get a QR code from an image (request file from user), and call the scanDone function when finished.  Returns false if QR scanning is not available */
actual fun ImageQrCode(imageParsed: (String?)->Unit): Boolean
{
    return false
}

/** No banner in iOS so no native way to display alerts */
actual fun displayAlert(alert: Alert) {}

/** Actually share this text using the platform's share functionality */
actual fun platformShare(textToShare: String)
{
    val activityController = UIActivityViewController(
      activityItems = listOf(textToShare),
      applicationActivities = null,
    )
    val window = UIApplication.sharedApplication.windows().first() as UIWindow?
    activityController.popoverPresentationController()?.sourceView =
      window
    window?.rootViewController?.presentViewController(
      activityController as UIViewController,
      animated = true,
      completion = null,
    )

}

val iosPlatformCharacteristics = PlatformCharacteristics(
  hasQrScanner = true,
  hasGallery = false,
  usesMouse = false,
  hasAlert = false,
  hasBack = false,
  hasNativeTitleBar = false,
  spaceConstrained = true,
  landscape = false,
  hasShare = true
  )
actual fun platform(): PlatformCharacteristics = iosPlatformCharacteristics

actual fun platformNotification(message:String, title: String?, onclickUrl:String?, severity: AlertLevel)
{
    // TODO issue an ios notification
}

@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
actual fun getResourceFile(name: String): BufferedSource
{
    /* uses the old URLForResource APIs
    val dotSpot = name.lastIndexOf('.')
    val ext = if (dotSpot == -1) null else name.takeLast(name.length-dotSpot-1)
    val base = if (dotSpot == -1) name else name.take(dotSpot)

    println("$base $ext")
    val dirSpot = base.lastIndexOf('/')
    val fname = if (dirSpot == -1) base else base.takeLast(base.length-dirSpot-1)
    val subdir = if (dirSpot == -1) null else base.take(dirSpot)

    println("RESOURCE: $subdir $fname  $ext")

    var url = NSBundle.mainBundle.URLForResource(base, ext, subdir)
    println("$url ${url?.absoluteURL}")
    if (url == null) url = NSBundle.mainBundle.URLForResource(base, ext)
    if ((url == null)&&(subdir != null))
    {
        //var tmp = NSBundle.bundleWithURL(NSURL("/icons"))
        //println("bwURL: $tmp")
        var tmp = NSBundle.bundleWithPath(subdir)
        println("bwPath: $tmp")
        if (tmp!=null)
            url = tmp.URLForResource(base, ext)
    }
    println("$url ${url?.absoluteURL}")

     */

    val ba = try
        {
            val res = resource(name)
            runBlocking { res.readBytes() }
        }
        catch(e: Exception)
        {
            val dirSpot = name.lastIndexOf('/')
            val fname = if (dirSpot == -1) name else name.takeLast(name.length-dirSpot-1)
            val subdir = if (dirSpot == -1) null else name.take(dirSpot)
            try
            {
                val res = resource(fname)
                runBlocking { res.readBytes() }
            }
            catch (e: Exception)
            {
                throw FileNotFoundException(name)
            }
        }
    val buf = Buffer()
    buf.write(ba)
    return buf
}

@Composable
actual fun isImeVisible(): Boolean
{
    // This is a "cheat"; this is set by text entry fields if they are in focus.  so we assume on ios that if this is set
    // then the softkeyboard is up.
    if (softKeyboardBar != null) return true
    return false
}
