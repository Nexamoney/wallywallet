// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally
import bitcoinunlimited.libbitcoincash.sourceLoc
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonConfiguration
import java.math.BigDecimal

import java.net.URL

import java.util.logging.Logger
import kotlin.time.*

private val LogIt = Logger.getLogger("bitcoinunlimited.bitcoincom")

val POLL_INTERVAL = 30000

@Serializable
data class BchUsdBitcoinCom(val price: Int, val stamp: Long)


@Serializable
data class HistItemBitcoinCom(val price: Int)
@Serializable
data class HistBitcoinCom(val lookup: HistItemBitcoinCom)

@UseExperimental(ExperimentalTime::class)
val lastPoll = mutableMapOf<String, Pair<ClockMark,BigDecimal>>()

@UseExperimental(ExperimentalTime::class)
fun MbchInFiat(fiat: String, setter: (BigDecimal)-> Unit)
{
    val prior = lastPoll[fiat]
    if (prior != null)
    {
        if (prior.first.elapsedNow().inMilliseconds < POLL_INTERVAL)
        {
            setter(prior.second)
            return
        }
    }
    // TODO periodic update
    GlobalScope.launch {
        val data = try { URL("https://index-api.bitcoin.com/api/v0/cash/price/" + fiat).readText() }
        catch(e: java.io.FileNotFoundException)
        {
            return@launch
        }
        LogIt.info(sourceLoc() + " " + data)
        val parser: Json = Json(JsonConfiguration(strictMode = false))  // nonstrict mode ignores extra fields
        val obj = parser.parse(BchUsdBitcoinCom.serializer(), data)
        LogIt.info(sourceLoc() + " " + obj.toString())
        // TODO verify recent timestamp
        val v = obj.price.toBigDecimal().setScale(16) / 100000.toBigDecimal().setScale(16) // bitcoin.com price is in cents per BCH.  We want "dollars" per MBCH (thousandth of a BCH)
        lastPoll[fiat] = Pair(MonoClock.markNow(),v)
        setter(v)
    }

}

/** Return the approximate price of mBCH at the time provided in seconds since the epoch */
fun historicalMbchInFiat(fiat: String, timeStamp: Long): BigDecimal
{
    // see https://index.bitcoin.com/
    val spec = "https://index-api.bitcoin.com/api/v0/cash/lookup?time=" + timeStamp.toString()
    val data = try { URL(spec).readText() }
    catch(e: java.io.FileNotFoundException)
    {
        return BigDecimal(-1)
    }
    val parser: Json = Json(JsonConfiguration(strictMode = false))  // nonstrict mode ignores extra fields

    LogIt.info(sourceLoc() + " " + data)

    val obj = parser.parse(HistBitcoinCom.serializer(), data)
    LogIt.info(sourceLoc() + " " + obj.toString())

    // TODO verify timestamp
    val v = obj.lookup.price.toBigDecimal().setScale(16) / 100000.toBigDecimal().setScale(16) // bitcoin.com price is in cents per BCH.  We want "dollars" per MBCH (thousandth of a BCH)
    return v
}