package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import com.ionspin.kotlin.bignum.decimal.*

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import io.ktor.http.*
import org.nexa.threads.millinow

private val LogIt = GetLog("BU.wally.fiatrates")

private val jsonParser: Json = Json { isLenient = true; ignoreUnknownKeys = true }  // nonstrict mode ignores extra fields

//The one USD -> fiat table the whole app shares
val fiatRates = FiatRates()

@Serializable
data class FiatRatesApiResponse(
  val result: String = "",
  @SerialName("base_code") val baseCode: String = "",
  val rates: Map<String, @Serializable(with = BigDecimalSerializer::class) BigDecimal> = mapOf()
)

/** USD -> fiat table + its polling.  Use the shared [fiatRates], don't make another one. */
class FiatRates
{
    private val sync = org.nexa.threads.Mutex()

    private var rates: Map<String, BigDecimal>? = null  // null until the first successful fetch
    private var polledAt = 0L  // last success
    private var triedAt = 0L   // last attempt, success or not

    /** How many [fiat] one USD buys.  null means no rate: don't fall back to the USD number. */
    fun usdTo(fiat: String): BigDecimal?
    {
        if (fiat == BASE_CURRENCY) return CURRENCY_1
        return sync.lock { rates?.get(fiat) }
    }

    /** Refresh if stale. [whenUpdated] runs on success only. */
    fun update(whenUpdated: (() -> Unit)? = null)
    {
        if (!allowAccessPriceData)
        {
            LogIt.info(sourceLoc() + ": Not loading fiat rates, price data access is turned off in settings")
            return
        }
        val now = millinow()

        // claim the attempt inside the lock so two threads can't both poll
        val skip: String? = sync.lock {
            val stale = (rates == null) || (now - polledAt > POLL_INTERVAL)
            val backoff = if (rates == null) FIRST_FETCH_RETRY_INTERVAL else RETRY_INTERVAL
            val mayRetry = now - triedAt > backoff
            when
            {
                !stale -> "already have " + (rates?.size ?: 0) + " rates, fetched " + ((now - polledAt)/1000) + "s ago"
                !mayRetry -> "last attempt was " + ((now - triedAt)/1000) + "s ago, waiting before retrying"
                else ->
                {
                    triedAt = now
                    null
                }
            }
        }
        if (skip != null)
        {
            LogIt.info(sourceLoc() + ": Not reloading fiat rates, " + skip)
            return
        }

        later {
            val data = try
            {
                LogIt.info(sourceLoc() + ": Loading fiat exchange rates from: $SOURCE_URL")
                Url(SOURCE_URL).readText(10000, 20000, MAX_READ)
            }
            catch (e: Exception)
            {
                LogIt.info("Error retrieving fiat rates: " + e.message)
                return@later
            }

            if (data.startsWith("<!DOCTYPE"))
            {
                LogIt.info("Error retrieving fiat rates, page is html not json (likely site offline)")
                return@later
            }

            val obj = try
            {
                jsonParser.decodeFromString(FiatRatesApiResponse.serializer(), data)
            }
            catch (e: Exception)
            {
                LogIt.info("Error parsing fiat rates: " + e.message)
                return@later
            }

            if ((obj.result != "success") || obj.rates.isEmpty())
            {
                LogIt.info("Error retrieving fiat rates, provider reported: " + obj.result)
                return@later
            }

            if (obj.baseCode != BASE_CURRENCY)
            {
                LogIt.info("Error retrieving fiat rates, unexpected base currency: " + obj.baseCode)
                return@later
            }

            install(obj.rates)
            LogIt.info(sourceLoc() + ": Loaded " + obj.rates.size + " fiat exchange rates, base " + obj.baseCode
              + ", " + localCurrency + "=" + (obj.rates[localCurrency]?.toPlainString() ?: "NOT CARRIED BY PROVIDER"))
            whenUpdated?.invoke()
        }
    }

    internal fun install(newRates: Map<String, BigDecimal>?)
    {
        sync.lock {
            rates = newRates
            polledAt = if (newRates == null) 0L else millinow()
            if (newRates == null) triedAt = 0L
        }
    }

    companion object
    {
        // one request returns every currency, so changing the local currency never needs another fetch
        const val SOURCE_URL = "https://open.er-api.com/v6/latest/USD"

        const val ATTRIBUTION_TEXT = "Rates By Exchange Rate API"
        const val ATTRIBUTION_URL = "https://www.exchangerate-api.com"

        // coin prices are quoted in USD, so everything converts through it
        const val BASE_CURRENCY = "USD"

        // the free tier only recalculates daily
        const val POLL_INTERVAL = 24*60*60*1000L
        const val RETRY_INTERVAL = 60*60*1000L
        // until the first table lands. same as the coin price poll retry
        const val FIRST_FETCH_RETRY_INTERVAL = 10*1000L
        const val MAX_READ = 200000
    }
}
