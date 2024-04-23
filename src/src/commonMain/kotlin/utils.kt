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
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.cio.*
import kotlinx.datetime.LocalDateTime
import okio.BufferedSource
import okio.utf8Size

private val LogIt = GetLog("BU.wally.utils")

/** Gets the ktor http client for this platform */
expect fun GetHttpClient(timeoutInMs: Number):HttpClient

/** Get a image from the file system (probably a QR code) and get a wally command string from it */
expect fun ImageQrCode(imageParsed: (String?)->Unit): Boolean

expect fun stackTraceWithout(skipFirst: MutableSet<String>, ignoreFiles: MutableSet<String>?=null): String

// For platforms that ktor can't do https on (ios)
// expect fun loadhttps(url: String):ByteArray

// Note this code might not work (to properly use the i18n number characters) because DecimalFormat might force the use of US ones
private var numberGroupingSeparator: String? = null
val NumberGroupingSeparator:String
    get()
    {
        // This code assumes that the language's number grouping separator will be between the hundreds and thousands
        return numberGroupingSeparator ?: run {

            val mode = DecimalMode(4L, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO, 0L)  //!< tell the system details about how we want bigdecimal math handled
            val test = DecimalFormat("#,###").format(BigDecimal.fromInt(1111, mode))
            val r:String = test.replace("1","")
            numberGroupingSeparator = r
            r
        }

    }

// Note this code might not work (to properly use the i18n number characters) because NexaFormat might force the use of US ones
private var numberDecimalCharacter: String? = null
val NumberDecimalCharacter:String
    get()
    {
        // This code assumes that the language's number grouping separator will be between the hundreds and thousands
        return  numberDecimalCharacter?: run {
            val test = DecimalFormat("#.#").format(BigDecimal.fromFloat(1.1f))
            val r:String = test.replace("1","")
            numberDecimalCharacter = r
            r
        }
    }



data class PlatformCharacteristics(
  /** Does this platform support QR code scanning */
  val hasQrScanner: Boolean,
  val hasGallery: Boolean,
  /** Is this primarily a mouse device (or is it touch).  WRT laptops with touch: This affects the UX styles so the platform should guess based on
   * what people mostly use on that platform, and then the UX can allow a persistent override. */
  val usesMouse: Boolean,
  /** True if the frame (or other non-compose) portion this application is capable of displaying alerts */
  val hasAlert: Boolean,
  /** True if the frame (or other non-compose) portion this application or device has a back button */
  val hasBack: Boolean,
  /** True if the frame (or other non-compose) portion this application or device has a title bar */
  val hasNativeTitleBar: Boolean,
  /** True if this platform is typically space constrained (phones), verses tablets or desktops.  This will drive large UI changes like
   * collapsing subsections of the UX */
  val spaceConstrained: Boolean,
  /** True if this platform is landscape (wider than it is tall).  This will drive large UI layout changes.
   * If you don't want to the app to dynamically change when rotating your device, just return the same value regardless of orientation
   * (and as part of your native set-up, turn off rotating) */
  val landscape: Boolean,
  /** Return true if the platform supports the concept of "sharing" (setting to true adds a share button in various places in the UX) */
  val hasShare: Boolean,
  /** Return true if the platform supports the concept of background syncronization **/
  val supportsBackgroundSync: Boolean
)

/** Return details about this platform */
expect fun platform(): PlatformCharacteristics

/** Actually share this text using the platform's share functionality */
expect fun platformShare(textToShare: String)

expect fun platformRam():Long?


class ApplicationState(val runState:RunState)
{
    enum class RunState
    {
        ACTIVE, INACTIVE, BACKGROUND
    }

}
expect fun applicationState(): ApplicationState


/** Initiate a platform-level notification message.  Note that these messages visually disrupt the user's potentially unrelated task
 * and may play a sound, so this must be used sparingly.
 */
expect fun platformNotification(message:String, title: String? = null, onclickUrl:String? = null, severity: AlertLevel = AlertLevel.NOTICE)

expect fun assetManagerStorage(): AssetManagerStorage


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
    fun icon(contentDescription: String?=null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
    {
        defp?.let { Icon(it(), contentDescription, modifier, tint ); return }
        iv?.let { Icon(it, contentDescription, modifier, tint); return }
        ib?.let { Icon(it, contentDescription, modifier, tint); return }
        painter?.let { Icon(it, contentDescription, modifier, tint); return }
    }
}

fun String.onlyDigits(): Boolean
{
    for (ch in this)
    {
        if (!ch.isDigit())
        {
            return false
        }
    }
    return true
}
fun String.onlyDecimal(): Boolean
{
    for (ch in this)
    {
        if (!(ch.isDigit() || ch == ',' || ch == '.'))
        {
            return false
        }
    }
    return true
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


/**
 * compute this^e.  This is not currently a high performing implementation
 */
fun Long.exp(e: Long): Long
{
    val mul = this
    var ret = 1L
    for (i in rangeTo(e))
    {
        ret = ret*mul
    }
    return ret
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

fun dbgAssertGuiThread()
{
    if (!isUiThread())
    {
        val tname = "" // TODO this thread's name
        LogIt.warning("ASSERT GUI operations in thread " + tname)
        val e = AssertException("Executing GUI operations in thread " + tname)
        LogIt.warning(e.stackTraceToString())
        throw e
    }
}

fun dbgAssertNotGuiThread()
{
    if (isUiThread())
    {
        val tname = "" // TODO this thread's name
        LogIt.warning("ASSERT blocking operations in GUI thread " + tname)
        val e = AssertException("Executing blocking operations in GUI thread " + tname)
        LogIt.warning(e.stackTraceToString())
        throw e
    }
}


/** load this Uri (blocking).
 * @return The Uri body contents as a string, and the status code.  If status code 301 or 302 is returned (forwarding) then return the forwarding Uri in the first parameter.
 */
fun com.eygraber.uri.Uri.loadTextAndStatus(timeoutInMs: Number): Pair<String,Int>
{
    val client = HttpClient() {
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutInMs.toLong()
            // connectTimeoutMillis  // time to connect
            // socketTimeoutMillis   // time between 2 data packets
        }
    }
    var access = this.toString()

    return runBlocking {
        var tries = 0
        while (tries < 10)
        {
            tries++
            val resp = client.get(access)
            val status = resp.status.value
            if ((status == 301) or (status == 302))  // Handle URL forwarding (often switching from http to https)
            {
                val newLoc = resp.request.headers.get("Location")
                if (newLoc != null) access = newLoc
                else throw CannotLoadException(access)
            }
            else return@runBlocking Pair(resp.bodyAsText(), status)
        }
        throw CannotLoadException(access)
    }
}


/** This helper function reads the contents of the URL.  This duplicates the API of other URL classes */
fun io.ktor.http.Url.readPostBytes(jsonBody: String, timeoutInMs: Number = 10000, maxReadSize: Number = 250000000): ByteArray
{
    val client = HttpClient() {
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutInMs.toLong()
            // connectTimeoutMillis  // time to connect
            // socketTimeoutMillis   // time between 2 data packets
        }
    }

    var url:io.ktor.http.Url = this
    return runBlocking {
        var tries = 0
        while (tries < 10)
        {
            tries = tries + 1
            val resp: HttpResponse = client.post(url) {
                // Configure request parameters exposed by HttpRequestBuilder
                setBody(jsonBody)
            }
            val status = resp.status.value
            if ((status == 301) or (status == 302))  // Handle URL forwarding (often switching from http to https)
            {
                val newLoc = resp.request.headers.get("Location")
                if (newLoc != null) url = io.ktor.http.Url(newLoc)
                else throw CannotLoadException(url.toString())
            }
            else return@runBlocking resp.bodyAsChannel().toByteArray(maxReadSize.toInt())
        }
        throw CannotLoadException(url.toString())
    }
}

fun io.ktor.http.Url.resolve(relativeUrl: io.ktor.http.Url): io.ktor.http.Url
{
    val urb = URLBuilder(this).takeFrom(relativeUrl)
    return urb.build()
}

fun io.ktor.http.Url.resolve(relativeUrl: String): io.ktor.http.Url
{
    val urb = URLBuilder(this).takeFrom(relativeUrl)
    return urb.build()
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

suspend fun com.eygraber.uri.Uri.coaccess(): String
{
    val client = HttpClient()
    return client.get(this.toString()).bodyAsText()
}

fun com.eygraber.uri.Uri.body(): String
{
    val suri = toString()
    var start = suri.indexOf(':')
    var end = suri.indexOf('?')
    if (end == -1) end = suri.length
    return suri.slice(start + 1 .. end - 1)
}

/** Return the parameters in this Url.  If a parameter name is repeated, return only the first instance.
 */
fun com.eygraber.uri.Uri.queryMap(): Map<String, String>
{
    // painful, but we need to trick the standard code into seeing this as a "hierarchial" URI so that it will parse parameters.
    val suri = toString()
    val index = suri.indexOf('?')
    if (index == -1) return mapOf()
    val fakeHeirarchicalUri = "http://a.b/_" + suri.drop(index)
    // To decode the parameters, we drop our scheme (blockchain identifier) and replace with http, so the standard Url parser will do the job for us.
    val u = com.eygraber.uri.Uri.parse(fakeHeirarchicalUri)

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
// in libnexakotlin expect fun String.urlDecode():String

/** Converts a string to a string that can be put into a URL */
// in libnexakotlin expect fun String.urlEncode():String

/** Get the clipboard.  Platforms that have a clipboard history should return that history, with the primary clip in index 0 */
expect fun getTextClipboard(): List<String>

/** Sets the clipboard, potentially asynchronously. */
expect fun setTextClipboard(msg: String)

/** Display an alert in the native manner (if it exists, see @platform()).  If there is no native manner, just return */
expect fun displayAlert(alert: Alert)

/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
expect fun isUiThread(): Boolean

/** Access a file from the resource area */
// expect fun readResourceFile(filename: String): InputStream

@Composable expect fun isImeVisible(): Boolean

/** Split into set splits a list of items, separate by comma or space, into a set of individual items
 * This defines the standard way in the UX to specify a list of items, so must be used for EVERY field that
 * asks for an item list.
 */
fun String.splitIntoSet():Set<String>
{
    return split(","," ").map({it.trim()}).filter({(it.isNotEmpty())&&(it.isNotBlank())}).toSet()
}

fun formatLocalDateTime(ldt: LocalDateTime): String {
    val year = ldt.year.toString()
    val month = ldt.monthNumber.toString().padStart(2, '0')
    val day = ldt.dayOfMonth.toString().padStart(2, '0')
    val hour = ldt.hour.toString().padStart(2, '0')
    val minute = ldt.minute.toString().padStart(2, '0')
    val second = ldt.second.toString().padStart(2, '0')

    // Format: YYYY-MM-DD HH:MM:SS
    return "$year-$month-$day $hour:$minute:$second"
}

expect fun getResourceFile(name: String): BufferedSource