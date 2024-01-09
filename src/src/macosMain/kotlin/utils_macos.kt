package info.bitcoinunlimited.www.wally

import io.ktor.client.*
import io.ktor.client.plugins.*
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardType
import platform.AppKit.NSPasteboardTypeString
import platform.Foundation.NSData

//import platform.UIKit.UIApplication

actual fun GetHttpClient(timeoutInMs: Number): HttpClient = HttpClient(io.ktor.client.engine.cio.CIO)
{
    install(HttpTimeout) { requestTimeoutMillis = timeoutInMs.toLong() }
}

/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
actual fun isUiThread(): Boolean
{
    TODO("Not yet implemented")
    //return UIApplication.sharedApplication.isMainThread
}

/** Converts an encoded URL to a raw string */
actual fun String.urlDecode(): String {
    TODO("Not yet implemented")
}

/** Converts a string to a string that can be put into a URL */
actual fun String.urlEncode(): String
{
    TODO("Not yet implemented")
}

/**
ß * Get the clipboard for MacOs
 * */
actual fun getTextClipboard(): List<String> {
    val generalPB = NSPasteboard.generalPasteboard

    return generalPB.types?.mapNotNull { it as? String }?.filter { type ->
        // Check if the type corresponds to a string type
        type == NSPasteboardTypeString
    }?.mapNotNull { stringType ->
        // Retrieve the string value for the string type
        generalPB.stringForType(stringType)
    }.orEmpty() // Return an empty list if there are no string types
}

/** Sets the clipboard, potentially asynchronously. */
actual fun setTextClipboard(msg: String)
{
    val pasteboard = NSPasteboard.generalPasteboard()
    pasteboard.clearContents()
    pasteboard.setString(msg, NSPasteboardTypeString)
}

val macosPlatformCharacteristics = PlatformCharacteristics(hasQrScanner = false, usesMouse = true)
actual fun platform(): PlatformCharacteristics = macosPlatformCharacteristics