package info.bitcoinunlimited.www.wally

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import kotlinx.cinterop.*
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
