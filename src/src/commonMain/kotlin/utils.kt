package info.bitcoinunlimited.www.wally

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.ktor.http.*
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*
import com.eygraber.uri.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
//import java.io.InputStream

private val LogIt = GetLog("BU.wally.utils")

class ImageContainer
{
    var defp: (@Composable ()->Painter)? = null
    var iv:ImageVector? = null
    var ib:ImageBitmap? = null
    var painter:Painter? = null

    constructor(dp:(@Composable ()->Painter)) { defp = dp }
    constructor(imv:ImageVector) { iv = imv}
    constructor(imb:ImageBitmap) { ib = imb}
    constructor(pt: Painter) { painter = pt}

    @Composable
    fun image(contentDescription: String?=null,
        modifier: Modifier = Modifier,
        alignment: Alignment = Alignment.Center,
        contentScale: ContentScale = ContentScale.Fit,
        alpha: Float = DefaultAlpha,
        colorFilter: ColorFilter? = null)
    {
        defp?.let { Image(it(), contentDescription, modifier, alignment, contentScale, alpha, colorFilter ); return }
        iv?.let { Image(it, contentDescription, modifier, alignment, contentScale, alpha, colorFilter ); return }
        ib?.let { Image(it, contentDescription, modifier, alignment, contentScale, alpha, colorFilter ); return }
        painter?.let { Image(it, contentDescription, modifier, alignment, contentScale, alpha, colorFilter); return }
    }


    @Composable
    fun icon(contentDescription: String?=null,
      modifier: Modifier = Modifier,
      tint: Color = LocalContentColor.current)
    {
        defp?.let { Icon(it(), contentDescription, modifier, tint ); return }
        iv?.let { Icon(it, contentDescription, modifier, tint); return }
        ib?.let { return Icon(it, contentDescription, modifier, tint); return }
        painter?.let { return Icon(it, contentDescription, modifier, tint); return }
    }
}


/** dig through text looking for addresses */
fun scanForFirstAddress(s: String):PayAddress?
{
    val words = s.split(" ",",","!",".","@","#","$","%","^","&","*","(",")","{","}","[","]","\\","|","/",">","<",";","\'","\"","~","+","=","-","_","`","~","?") // None of these characters are allowed in addresses so split the text on them
    for (w in words)
    {
        if (w.length > 32)  // cashaddr type addresses are pretty long
        {
            try
            {
                val pa = PayAddress(w)
                return pa
            }
            catch (e: Exception)
            {
                // not an address
            }
        }
    }
    return null
}

/** Convert a ChainSelector to its currency code at 100M/1000 units */
val chainToDisplayCurrencyCode: Map<ChainSelector, String> = mapOf(
  ChainSelector.NEXATESTNET to "tNEX", ChainSelector.NEXAREGTEST to "rNEX", ChainSelector.NEXA to "NEX",
  ChainSelector.BCH to "uBCH", ChainSelector.BCHTESTNET to "tuBCH", ChainSelector.BCHREGTEST to "ruBCH"
)

val ChainSelector.currencyDecimals: Int
  get()
{
    return when (this)
    {
        ChainSelector.BCHTESTNET, ChainSelector.BCHREGTEST, ChainSelector.BCH -> uBchDecimals
        ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> NexaDecimals
    }
}

fun isCashAddrScheme(s: String): Boolean
{
    val chain = uriToChain[s.lowercase()]
    return chain != null
}

// This extension function is used to convert List to a single value, considering only the first element.
fun List<String>.first(): String = this[0]

// you can install your own coroutine threads here and this common code will use that instead of GlobalScope
var notInUIscope: CoroutineScope? = null

/** Do whatever you pass but not within the user interface context, asynchronously */
fun later(fn: suspend () -> Unit): Unit
{
    (notInUIscope ?: GlobalScope).launch {
        try
        {
            fn()
        } catch (e: Exception) // Uncaught exceptions will end the app
        {
            LogIt.info(sourceLoc() + ": General exception handler (should be caught earlier!)")
            handleThreadException(e)
        }
    }
}

/** load this Uri (blocking).
 * @return The Uri body contents as a string, and the status code.  If status code 301 or 302 is returned (forwarding) then return the forwarding Uri in the first parameter.
 */
fun Uri.loadTextAndStatus(timeoutInMs: Number): Pair<String,Int>
{
    var err:Int = 0
    val client = HttpClient() {
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutInMs.toLong()
            // connectTimeoutMillis  // time to connect
            // socketTimeoutMillis   // time between 2 data packets
        }
    }

    return runBlocking {
        val resp = client.get(this.toString())
        val status = resp.status.value
        if ((status == 301) or (status == 302))  // Handle URL forwarding (often switching from http to https)
        {
            Pair(resp.request.headers.get("Location") ?: "", status)
        }
        else Pair(resp.bodyAsText(), status)
    }
}

/*
fun Uri.accessWithError(): Pair<String,Int>
{
    var err:Int = 0
    val client = HttpClient() {
        HttpResponseValidator {
            validateResponse {
                err = it.status.value
            }
        }
    }
    return Pair(runBlocking {
        val resp = client.get(this.toString())
        val rbody = resp.status.value


        resp.bodyAsText()
    }, err)
}

 */

suspend fun Uri.coaccess(): String
{
    val client = HttpClient()
    return client.get(this.toString()).bodyAsText()
}

fun Uri.body(): String
{
    val suri = toString()
    var start = suri.indexOf(':')
    var end = suri.indexOf('?')
    if (end == -1) end = suri.length
    return suri.slice(start + 1 .. end - 1)
}

/** Return the parameters in this Url.  If a parameter name is repeated, return only the first instance.
 */
fun Uri.queryMap(): Map<String, String>
{
    // painful, but we need to trick the standard code into seeing this as a "hierarchial" URI so that it will parse parameters.
    val suri = toString()
    val index = suri.indexOf('?')
    if (index == -1) return mapOf()
    val fakeHeirarchicalUri = "http://a.b/_" + suri.drop(index)
    // To decode the parameters, we drop our scheme (blockchain identifier) and replace with http, so the standard Url parser will do the job for us.
    val u = Uri.parse(fakeHeirarchicalUri)

    // The parameters property of Ktor's Url class already provides parsed and decoded query parameters.
    val parameters = mutableMapOf<String, String>()
    val keys = u.getQueryParameterNames()
    keys.forEach { name ->
        val values = u.getQueryParameters(name)
        if (values.size > 0)
            parameters[name] = values[0]
        else
            parameters[name] = ""
    }
    return parameters
}

// Add functions to ktor URL that is like Android Uri
// fun Uri.getQueryParameter(param: String): String? = parameters.get(param)

/** Converts an encoded URL to a raw string */
expect fun String.urlDecode():String

/** Converts a string to a string that can be put into a URL */
expect fun String.urlEncode():String

/** Get the clipboard.  Platforms that have a clipboard history should return that history, with the primary clip in index 0 */
expect fun getTextClipboard(): List<String>

/** Sets the clipboard, potentially asynchronously. */
expect fun setTextClipboard(msg: String)


/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
expect fun isUiThread(): Boolean

/** Access a file from the resource area */
// expect fun readResourceFile(filename: String): InputStream