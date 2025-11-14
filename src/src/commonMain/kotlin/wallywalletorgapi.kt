package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import com.ionspin.kotlin.bignum.decimal.*

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.descriptors.*

import kotlin.time.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

private val LogIt = GetLog("BU.wally.orgapi")

val WALLY_WALLET_ORG_HOST = "www.wallywallet.org"

val POLL_INTERVAL = 60000
val POLL_RETRY_INTERVAL = 10000

val DAILY_POLL_INTERVAL = 60*5  // every 5 minutes
val TWO_BD = CurrencyDecimal(2)

private val jsonParser: Json = Json { isLenient = true; ignoreUnknownKeys = true }  // nonstrict mode ignores extra fields

object BigDecimalSerializer: JsonTransformingSerializer<BigDecimal>(tSerializer = object:KSerializer<BigDecimal> {
    override fun deserialize(decoder: Decoder): BigDecimal = decoder.decodeString().toBigDecimal()
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
})
{
    override fun transformDeserialize(element: JsonElement): JsonElement
    {
        var s = element.toString()
        if (s[0] == '"') s = s.drop(1).dropLast(1)
        return JsonPrimitive(value = s)
    }
}

@Serializable
data class WallyWalletOrgApiCurPrice(@Serializable(with = BigDecimalSerializer::class) val Bid: BigDecimal,
                       @Serializable(with = BigDecimalSerializer::class) val Ask: BigDecimal,
                       @Serializable(with = BigDecimalSerializer::class) val Last: BigDecimal)

@Serializable
data class WallyWalletOrgApiDailyPrice(val price: Array<@Serializable(with = BigDecimalSerializer::class) BigDecimal>)


val nexaPricePollSync = org.nexa.threads.Mutex()

/** polledAt is the last successful poll.  lastTried is the last polling attempt */
class PricePoll(val polledAt: Long, val price: BigDecimal?, val triedAt: Long)

@OptIn(ExperimentalTime::class)
val lastNexaPricePoll = mutableMapOf<String, PricePoll>()

@OptIn(ExperimentalTime::class)
val lastNexaHistoryPoll = mutableMapOf<String, Pair<Long, Array<BigDecimal>>>()

@OptIn(ExperimentalTime::class)
fun NexDaily(fiat: String): Array<BigDecimal>?
{
    if (!allowAccessPriceData) return null
    //return arrayOf("0.00001380","0.00001385","0.00001400","0.00001450","0.00001500","0.00001600","0.00001550","0.00001450" ).map { BigDecimal(it)}.toTypedArray()

    val prior = lastNexaHistoryPoll[fiat]

    // Time to refresh this data
    if ((prior == null) || (millinow() - prior.first < DAILY_POLL_INTERVAL)) launch {
        val client = HttpClient()
        val data = try
        {
            client.get("http://$WALLY_WALLET_ORG_HOST/_api/v0/day/nex/usdt").bodyAsText()
        }
        catch (e: Exception)
        {
            LogIt.info("NexDaily: Error retrieving price: " + e.message)
            return@launch
        }
        finally
        {
            client.close()
        }
        LogIt.info(sourceLoc() + " " + data)
        val obj = jsonParser.decodeFromString(WallyWalletOrgApiDailyPrice.serializer(), data)
        lastNexaHistoryPoll[fiat] = Pair(millinow(), obj.price)
    }

    if (prior == null) return null
    else return prior.second
}

// Load the price and update all accounts (or other objects) that need it
@OptIn(ExperimentalTime::class)
fun UpdateNexaXchgRates(fiat: String)
{
    if (fiat != "USD") return
    val now = millinow()
    // Grab the last
    val prior = nexaPricePollSync.lock { lastNexaPricePoll[fiat] }

    if ((prior == null) || ((now - prior.polledAt > POLL_INTERVAL)&&(now - prior.triedAt > POLL_RETRY_INTERVAL)))
    {
        // Update the last poll attempt time so we don't retry too soon
        nexaPricePollSync.lock {
            lastNexaPricePoll[fiat] = prior?.let { PricePoll(it.polledAt, it.price, now ) } ?: PricePoll(0, null, millinow())
        }
        later {
            val data = try
            {
                val route = "http://$WALLY_WALLET_ORG_HOST/_api/v0/now/nex/usdt"
                LogIt.info(sourceLoc() + ": Loading exchange rate for: $fiatCurrencyCode from: $route")
                Url(route).readText(10000, 20000, 10000)
            }
            catch (e: Exception)
            {
                LogIt.info("Error retrieving price: " + e.message)
                return@later
            }
            if (data.startsWith("<!DOCTYPE HTML"))
            {
                LogIt.info("Error retrieving price, page is html not json (likely site offline)")
                return@later
            }

            try
            {
                val obj = jsonParser.decodeFromString(WallyWalletOrgApiCurPrice.serializer(), data)
                val v = (obj.Bid + obj.Ask) / TWO_BD
                val p = PricePoll(now, v, now)
                nexaPricePollSync.lock {
                    lastNexaPricePoll[fiat] = p
                }
                // Update all interested accounts with this exchange rate
                wallyApp?.accounts?.values?.forEach { act ->
                    if (act.chain.chainSelector == ChainSelector.NEXA)
                    {
                        act.fiatPerCoin = CurrencyDecimal(v)
                    }
                }
            }
            catch (e: Exception)
            {
                LogIt.info("Error retrieving price: " + e.message)
                return@later
            }
        }
    }
}


/*  This variant will load the price multiple times if multiple entities need it
@OptIn(ExperimentalTime::class)
fun NexInFiat(fiat: String, setter: (BigDecimal) -> Unit)
{
    if ((!allowAccessPriceData)||(backgroundOnly)) return
    // only usdt pair supported right now
    if (fiat != "USD") return
    val prior = nexaPricePollSync.lock { lastNexaPricePoll[fiat] }
    if (prior != null)
    {
        if (prior.first.elapsedNow().inWholeMilliseconds < POLL_INTERVAL)
        {
            setter(prior.second)
            return
        }
    }

    later {
        val data = try
        {
            val route = "http://$WALLY_WALLET_ORG_HOST/_api/v0/now/nex/usdt"
            LogIt.info(sourceLoc() + ": Loading exchange rate for: $fiatCurrencyCode from: $route")
            Url(route).readText(10000, 20000, 10000)
        }
        catch (e: Exception)
        {
            LogIt.info("Error retrieving price: " + e.message)
            return@later
        }

        if (data.startsWith("<!DOCTYPE HTML"))
        {
            LogIt.info("Error retrieving price, page is html not json (likely site offline)")
            return@later
        }

        try
        {
            val parser = Json { isLenient = true; ignoreUnknownKeys = true }
            val obj = parser.decodeFromString(WallyWalletOrgApiCurPrice.serializer(), data)
            val v = (obj.Bid + obj.Ask) / CurrencyDecimal(2)
            nexaPricePollSync.lock {
                lastNexaPricePoll[fiat] = Pair(Monotonic.markNow(), v)
            }
            setter(v)
        }
        catch (e: Exception)
        {
            LogIt.info("Error retrieving price: " + e.message)
            return@later
        }
    }
}
*/
