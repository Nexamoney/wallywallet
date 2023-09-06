package info.bitcoinunlimited.www.wally

import io.ktor.http.*
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*
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

/** Return the parameters in this Url.  If a parameter name is repeated, return only the first instance.
 */
fun Url.queryMap(): Map<String, String>
{
    // The parameters property of Ktor's Url class already provides parsed and decoded query parameters.
    val parameters = mutableMapOf<String, String>()
    this.parameters.forEach { name, values ->
        if (values.size > 0)
            parameters[name] = values[0]
        else
            parameters[name] = ""
    }
    return parameters
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
