package info.bitcoinunlimited.www.wally

import platform.Foundation.NSThread
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

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