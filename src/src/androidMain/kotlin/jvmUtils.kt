package info.bitcoinunlimited.www.wally

import kotlinx.coroutines.*
import org.nexa.libnexakotlin.GetLog

private val LogIt = GetLog("BU.wally.jvmUtils")


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

