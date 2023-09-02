package info.bitcoinunlimited.www.wally

import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*
import java.net.URL
import java.net.URLDecoder
import java.util.LinkedHashMap

private val LogIt = GetLog("BU.wally.utils")

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


// see https://stackoverflow.com/questions/13592236/parse-a-uri-string-into-name-value-collection
fun URL.queryMap(): Map<String, String>
{
    val query_pairs = LinkedHashMap<String, String>()
    val query = this.getQuery()
    if (query == null) return mapOf()
    val pairs = query.split("&")
    for (pair in pairs)
    {
        val idx = pair.indexOf("=")
        query_pairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
    }
    return query_pairs
}

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

/** execute the passed code block directly if not in the UI thread, otherwise defer it */
fun notInUI(fn: () -> Unit)
{
    val tname = Thread.currentThread().name
    if (tname == "main")  // main is the UI thread so need to launch this
    {
        (notInUIscope ?: GlobalScope).launch {
            try
            {
                fn()
            }
            catch (e: Exception)
            {
                LogIt.warning("Exception in notInUI: " + e.toString())
            }
        }
    }
    else // otherwise just call it
    {
        try
        {
            fn()
        }
        catch (e: Exception)
        {
            LogIt.warning("Exception in notInUI: " + e.toString())
        }
    }
}

fun <RET> syncNotInUI(fn: () -> RET): RET
{
    val tname = Thread.currentThread().name
    if (tname == "main")
    {
        val ret = runBlocking(Dispatchers.IO) {
            fn()
        }
        return ret
    }
    else
    {
        return fn()
    }
}

