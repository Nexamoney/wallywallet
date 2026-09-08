import info.bitcoinunlimited.www.wally.*
import org.nexa.libnexakotlin.*
import kotlin.test.*

class FiatFormatTest
{
    private val THOUSAND_TNEX_IN_BRL = CurrencyDecimal("0.0026718933458")

    @Test fun givenAThousandTestnetCoinsInBrl_whenFormattedForMainnet_thenItRoundsToZero()
    {
        // given
        val amount = THOUSAND_TNEX_IN_BRL

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXA)

        // then
        assertEquals("0.00", formatted)
    }

    @Test fun givenAnAmountFarBelowACent_whenFormattedForMainnet_thenItRoundsToZero()
    {
        // given
        val amount = CurrencyDecimal("0.000000514")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXA)

        // then
        assertEquals("0.00", formatted)
    }

    @Test fun givenACentAmount_whenFormattedForMainnet_thenItIsUnchanged()
    {
        // given
        val amount = CurrencyDecimal("2.67")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXA)

        // then
        assertEquals("2.67", formatted)
    }

    @Test fun givenAThousandsAmount_whenFormattedForMainnet_thenItIsGroupedWithTwoDecimals()
    {
        // given
        val amount = CurrencyDecimal("1234.5")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXA)

        // then
        val grouped = if (platform().target == KotlinTarget.iOS) "1234.50" else "1,234.50"
        assertEquals(grouped, formatted)
    }

    @Test fun givenAThousandTestnetCoinsInBrl_whenFormattedForTestnet_thenSignificantDigitsAreKept()
    {
        // given the amount that started this.  2 decimals flatten it to "0.00"
        val amount = THOUSAND_TNEX_IN_BRL

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXATESTNET)

        // then
        assertEquals("0.002672", formatted)
    }

    @Test fun givenAnAmountFarBelowACent_whenFormattedForTestnet_thenSignificantDigitsAreKept()
    {
        // given
        val amount = CurrencyDecimal("0.000000514")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXATESTNET)

        // then
        assertEquals("0.000000514", formatted)
    }

    @Test fun givenAThousandTestnetCoinsInBrl_whenFormattedForRegtest_thenItReadsLikeTestnet()
    {
        // given
        val amount = THOUSAND_TNEX_IN_BRL

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXAREGTEST)

        // then same as testnet
        assertEquals("0.002672", formatted)
    }

    @Test fun givenHalfACent_whenFormattedForTestnet_thenItSurvivesTheRoundingTie()
    {
        // given
        val amount = CurrencyDecimal("0.005")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXATESTNET)

        // then
        assertEquals("0.005", formatted)
    }

    @Test fun givenExactlyOneCent_whenFormattedForTestnet_thenItShowsTwoDecimals()
    {
        // given
        val amount = CurrencyDecimal("0.01")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXATESTNET)

        // then
        assertEquals("0.01", formatted)
    }

    @Test fun givenACentAmount_whenFormattedForTestnet_thenItIsUnchanged()
    {
        // given
        val amount = CurrencyDecimal("2.67")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXATESTNET)

        // then
        assertEquals("2.67", formatted)
    }

    @Test fun givenAThousandsAmount_whenFormattedForTestnet_thenItIsGroupedWithTwoDecimals()
    {
        // given
        val amount = CurrencyDecimal("1234.5")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXATESTNET)

        // then, same as mainnet
        val grouped = if (platform().target == KotlinTarget.iOS) "1234.50" else "1,234.50"
        assertEquals(grouped, formatted)
    }

    @Test fun givenZero_whenFormattedForTestnet_thenItReadsAsPlainZero()
    {
        // given
        val amount = CURRENCY_ZERO

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXATESTNET)

        // then
        assertEquals("0.00", formatted)
    }

    @Test fun givenZero_whenFormattedForMainnet_thenItReadsAsPlainZero()
    {
        // given
        val amount = CURRENCY_ZERO

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXA)

        // then
        assertEquals("0.00", formatted)
    }

    @Test fun givenAnAmountShorterThanTheDigitCap_whenFormattedForTestnet_thenThePaddingIsTrimmed()
    {
        // given
        val amount = CurrencyDecimal("0.004")

        // when
        val formatted = formatFiatAmount(amount, ChainSelector.NEXATESTNET)

        // then
        assertEquals("0.004", formatted)
    }
}
