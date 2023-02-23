package info.bitcoinunlimited.www.wally

import java.math.BigDecimal
import java.net.URL
import java.util.logging.Logger

import bitcoinunlimited.libbitcoincash.launch
import bitcoinunlimited.libbitcoincash.sourceLoc

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.descriptors.*

import kotlinx.coroutines.*
import kotlin.time.*
import kotlin.time.TimeSource.Monotonic

private val LogIt = Logger.getLogger("BU.wally.orgapi")

val WALLY_WALLET_ORG_HOST = "www.wallywallet.org"
//val WALLY_WALLET_ORG_HOST = "192.168.1.5:7996"


val DAILY_POLL_INTERVAL = 60*5  // every 5 minutes

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


@OptIn(ExperimentalTime::class)
val lastNexaPricePoll = mutableMapOf<String, Pair<TimeMark, BigDecimal>>()

@OptIn(ExperimentalTime::class)
val lastNexaHistoryPoll = mutableMapOf<String, Pair<TimeMark, Array<BigDecimal>>>()

@OptIn(ExperimentalTime::class)
fun NexDaily(fiat: String): Array<BigDecimal>?
{
    if (!allowAccessPriceData) return null
    //return arrayOf("0.00001380","0.00001385","0.00001400","0.00001450","0.00001500","0.00001600","0.00001550","0.00001450" ).map { BigDecimal(it)}.toTypedArray()

    val prior = lastNexaHistoryPoll[fiat]

    // Time to refresh this data
    if ((prior == null) || (prior.first.elapsedNow().inWholeMilliseconds < DAILY_POLL_INTERVAL)) launch()
    {
        val data = try
        {
            URL("http://$WALLY_WALLET_ORG_HOST/_api/v0/day/nex/usdt").readText()
        }
        catch (e: java.io.FileNotFoundException)
        {
            return@launch
        }
        catch (e: Exception)
        {
            LogIt.info("Error retrieving price: " + e.message)
            return@launch
        }
        LogIt.info(sourceLoc() + " " + data)
        val parser: Json = Json { isLenient = true; ignoreUnknownKeys = true }  // nonstrict mode ignores extra fields
        val obj = parser.decodeFromString(WallyWalletOrgApiDailyPrice.serializer(), data)
        lastNexaHistoryPoll[fiat] = Pair(Monotonic.markNow(), obj.price)
    }

    if (prior == null) return null
    else return prior.second
}



@OptIn(ExperimentalTime::class)
fun NexInFiat(fiat: String, setter: (BigDecimal) -> Unit)
{
    if (!allowAccessPriceData) return
    // only usdt pair supported right now
    if (fiat != "USD") return

    val prior = lastNexaPricePoll[fiat]
    if (prior != null)
    {
        if (prior.first.elapsedNow().inWholeMilliseconds < POLL_INTERVAL)
        {
            setter(prior.second)
            return
        }
    }

    // TODO periodic update
    launch {
        val data = try
        {
            URL("http://$WALLY_WALLET_ORG_HOST/_api/v0/now/nex/usdt").readText()
        }
        catch (e: java.io.FileNotFoundException)
        {
            return@launch
        }
        catch (e: Exception)
        {
            LogIt.info("Error retrieving price: " + e.message)
            return@launch
        }
        LogIt.info(sourceLoc() + " " + data)
        val parser: Json = Json { isLenient = true; ignoreUnknownKeys = true }  // nonstrict mode ignores extra fields
        val obj = parser.decodeFromString(WallyWalletOrgApiCurPrice.serializer(), data)
        LogIt.info(sourceLoc() + " " + obj.toString())
        // Average the bid and ask prices
        val v = (obj.Bid + obj.Ask)/BigDecimal(2)
        lastNexaPricePoll[fiat] = Pair(Monotonic.markNow(), v)
        setter(v)
    }
}