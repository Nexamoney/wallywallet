package info.bitcoinunlimited.www.wally

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import org.nexa.libnexakotlin.GetLog
import platform.Foundation.NSThread
import platform.UIKit.UIPasteboard

private val LogIt = GetLog("BU.wally.utils_ios")

actual fun GetHttpClient(timeoutInMs: Number): HttpClient = HttpClient(CIO)
{
    install(HttpTimeout) { requestTimeoutMillis = timeoutInMs.toLong() }
}
/** Converts an encoded URL to a raw string */
actual fun String.urlDecode():String
{
    TODO()
}

/** Converts a string to a string that can be put into a URL */
actual fun String.urlEncode():String
{
    TODO()
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

    /*
    val split = filename.lastIndexOf('.')
    val name = filename.take(split)
    val ext = filename.drop(split+1)
    LogIt.info("name/ext: $name $ext")
    val url = NSBundle.mainBundle.URLForResource(name, ext)
    if (url == null) throw NotUriException()
    val data = NSData.create(url!!)
    if (data == null) throw NotUriException()
    //val bytes = data.bytes?.readBytes(data.length.toInt()) ?: throw NotUriException()

    // Works but how to get it into a compose image?
    // val uiIm = UIImage.imageWithData(data)

    //loadXmlImageVector
}
     */

val iosPlatformCharacteristics = PlatformCharacteristics(hasQrScanner = true, usesMouse = false)
actual fun platform(): PlatformCharacteristics = iosPlatformCharacteristics