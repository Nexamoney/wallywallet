package info.bitcoinunlimited.www.wally

import java.awt.Toolkit
import java.awt.datatransfer.*
import java.net.URLDecoder
import java.net.URLEncoder

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
    val c: Clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
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