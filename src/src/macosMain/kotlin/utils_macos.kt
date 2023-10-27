package info.bitcoinunlimited.www.wally

import io.ktor.client.*
import io.ktor.client.plugins.*
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

/** Get the clipboard.  Platforms that have a clipboard history should return that history, with the primary clip in index 0 */
actual fun getTextClipboard(): List<String>
{
    TODO("Not yet implemented")
}

/** Sets the clipboard, potentially asynchronously. */
actual fun setTextClipboard(msg: String)
{
}