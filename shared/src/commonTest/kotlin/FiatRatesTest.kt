import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import kotlinx.serialization.json.Json
import org.nexa.libnexakotlin.*
import kotlin.test.*

private val SAMPLE_RESPONSE = """
{"result":"success","provider":"https://www.exchangerate-api.com","time_last_update_unix":1787097751,
"base_code":"USD","rates":{"USD":1,"BRL":5.207284,"CAD":1.38884,"CNY":6.756911,"EUR":0.863866,
"GBP":0.738843,"JPY":159.590794,"RUB":85.040648,"IDR":17864.679126}}
"""

class FiatRatesTest
{
    private val parser = Json { isLenient = true; ignoreUnknownKeys = true }

    private val USD_PER_NEXA = CurrencyDecimal("0.000000514734645")

    private fun sampleRates(): Map<String, BigDecimal> =
      parser.decodeFromString(FiatRatesApiResponse.serializer(), SAMPLE_RESPONSE).rates

    @AfterTest fun forgetRates()
    {
        fiatRates.install(null)
    }

    @Test fun givenAProviderResponse_whenItIsDecoded_thenTheEnvelopeAndEveryRateLandExact()
    {
        // given succeed response

        // when
        val obj = parser.decodeFromString(FiatRatesApiResponse.serializer(), SAMPLE_RESPONSE)

        // then
        assertEquals("success", obj.result)
        assertEquals("USD", obj.baseCode)
        assertEquals(9, obj.rates.size)
        assertEquals(0, obj.rates["USD"]!!.compareTo(CurrencyDecimal("1")))
        assertEquals(0, obj.rates["EUR"]!!.compareTo(CurrencyDecimal("0.863866")))
        assertEquals(0, obj.rates["IDR"]!!.compareTo(CurrencyDecimal("17864.679126")))
    }

    @Test fun givenNoRateTable_whenUsdIsConverted_thenItResolvesToOne()
    {
        // given no table

        // when
        val rate = fiatRates.usdTo("USD")

        // then
        assertEquals(0, rate!!.compareTo(CurrencyDecimal("1")))
    }

    @Test fun givenNoRateTable_whenAnotherCurrencyIsConverted_thenItIsUnavailable()
    {
        // given no table

        // when
        val rate = fiatRates.usdTo("EUR")

        // then
        assertNull(rate)
    }

    @Test fun givenAnInstalledTable_whenItsCurrenciesAreConverted_thenTheyResolveAtTheProviderRate()
    {
        // given
        fiatRates.install(sampleRates())

        // when
        val eur = fiatRates.usdTo("EUR")
        val jpy = fiatRates.usdTo("JPY")

        // then
        assertEquals(0, eur!!.compareTo(CurrencyDecimal("0.863866")))
        assertEquals(0, jpy!!.compareTo(CurrencyDecimal("159.590794")))
    }

    @Test fun givenATableWithoutMetals_whenAMetalIsConverted_thenItIsUnavailable()
    {
        // given a table without metals in it
        fiatRates.install(sampleRates())

        // when
        val rate = fiatRates.usdTo("XAU")

        // then
        assertNull(rate)
    }

    @Test fun givenAnInstalledTable_whenItIsDropped_thenItsCurrenciesAreUnavailableAgain()
    {
        // given
        fiatRates.install(sampleRates())

        // when
        fiatRates.install(null)

        // then
        assertNull(fiatRates.usdTo("EUR"))
        assertEquals(0, fiatRates.usdTo("USD")!!.compareTo(CurrencyDecimal("1")))
    }

    @Test fun givenARateTable_whenACoinPriceIsConverted_thenItIsTheUsdPriceTimesTheRate()
    {
        // given
        fiatRates.install(sampleRates())

        // when
        val brl = nexaFiatPerCoin("BRL", USD_PER_NEXA)

        // then
        assertEquals(0, brl.compareTo(CurrencyDecimal("0.0000026803694812")))
    }

    @Test fun givenAHighRateCurrency_whenACoinPriceIsConverted_thenTheRateIsReallyApplied()
    {
        // given
        fiatRates.install(sampleRates())

        // when
        val jpy = nexaFiatPerCoin("JPY", USD_PER_NEXA)

        // then
        assertEquals(0, jpy.compareTo(CurrencyDecimal("0.0000821469106949")))
        assertTrue(jpy > USD_PER_NEXA, "a 159 JPY/USD rate has to move the number, not pass it through")
    }

    @Test fun givenATableWithoutMetals_whenACoinPriceIsConvertedToAMetal_thenTheRateIsUnavailable()
    {
        // given
        fiatRates.install(sampleRates())

        // when
        val xau = nexaFiatPerCoin("XAU", USD_PER_NEXA)

        // then
        assertEquals(0, xau.compareTo(CURRENCY_NEG1))
    }
}
