import info.bitcoinunlimited.www.wally.*
import ui.FakeSharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalCurrencyTest
{
    @Test fun languageChoosesTheCurrency()
    {
        assertEquals("JPY", localeFiatCurrency("ja", "JP"))
        assertEquals("JPY", localeFiatCurrency("ja", ""))
        assertEquals("CNY", localeFiatCurrency("zh", ""))
        assertEquals("CNY", localeFiatCurrency("zh-Hans", "HK"))
        assertEquals("RUB", localeFiatCurrency("ru", ""))
        assertEquals("BRL", localeFiatCurrency("pt", ""))
        assertEquals("EUR", localeFiatCurrency("de", ""))
    }

    @Test fun countryDecidesForLanguagesWithSeveralCurrencies()
    {
        assertEquals("USD", localeFiatCurrency("en", "US"))
        assertEquals("GBP", localeFiatCurrency("en", "GB"))
        assertEquals("CAD", localeFiatCurrency("en", "CA"))
        assertEquals("CAD", localeFiatCurrency("fr", "CA"))
        assertEquals("EUR", localeFiatCurrency("fr", "FR"))
        assertEquals("EUR", localeFiatCurrency("pt", "PT"))
        assertEquals("EUR", localeFiatCurrency("es", "ES"))
    }

    @Test fun unsupportedLocalesFallBackToUsd()
    {
        assertEquals("USD", localeFiatCurrency("nb", "NO"))  // no NOK to offer
        assertEquals("USD", localeFiatCurrency("es", "MX"))  // no MXN to offer
        assertEquals("USD", localeFiatCurrency("en", "AU"))
        assertEquals("USD", localeFiatCurrency(null, null))
        assertEquals("USD", localeFiatCurrency("", ""))
    }

    @Test fun theCountryIsUsedWhenTheLanguageIsUnknown()
    {
        assertEquals("JPY", localeFiatCurrency("xx", "JP"))
        assertEquals("EUR", localeFiatCurrency("xx", "IT"))
    }

    @Test fun everyLocaleMapsToACurrencyWeOffer()
    {
        for (language in listOf("ja", "zh", "ru", "pt", "en", "de", "fr", "es", "nb", "xx", ""))
            for (country in listOf("JP", "CN", "RU", "BR", "US", "GB", "CA", "DE", "NO", "XX", ""))
                assertTrue(localeFiatCurrency(language, country) in FIAT_CURRENCIES)
    }

    @Test fun theDeviceDefaultIsACurrencyWeOffer()
    {
        assertTrue(deviceFiatCurrency() in FIAT_CURRENCIES)
    }

    @Test fun theDeviceDefaultIsSavedUntilTheUserChooses()
    {
        val prefs = FakeSharedPreferences()
        assertEquals(null, prefs.getString(LOCAL_CURRENCY_PREF, null))

        assertEquals(deviceFiatCurrency(), defaultFiatCurrency(prefs))
        assertEquals(deviceFiatCurrency(), prefs.getString(LOCAL_CURRENCY_PREF, null))
    }

    @Test fun readingTheChoiceNeverSavesIt()
    {
        val prefs = FakeSharedPreferences()
        assertEquals(deviceFiatCurrency(), chosenFiatCurrency(prefs))
        assertEquals(null, prefs.getString(LOCAL_CURRENCY_PREF, null))

        prefs.edit().putString(LOCAL_CURRENCY_PREF, "").commit()
        assertEquals(deviceFiatCurrency(), chosenFiatCurrency(prefs))

        prefs.edit().putString(LOCAL_CURRENCY_PREF, "JPY").commit()
        assertEquals("JPY", chosenFiatCurrency(prefs))
    }

    @Test fun languageBeatsCountryOutsideTheMultiCurrencyLanguages()
    {
        assertEquals("EUR", localeFiatCurrency("de", "CH"))
        assertEquals("CNY", localeFiatCurrency("zh", "TW"))
        assertEquals("INR", localeFiatCurrency("en", "IN"))
    }

    @Test fun aChosenCurrencyIsLeftAlone()
    {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(LOCAL_CURRENCY_PREF, "JPY").commit()

        assertEquals("JPY", defaultFiatCurrency(prefs))
    }

    @Test fun anEmptyPreferenceCountsAsUnchosen()
    {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(LOCAL_CURRENCY_PREF, "").commit()

        assertEquals(deviceFiatCurrency(), defaultFiatCurrency(prefs))
        assertEquals(deviceFiatCurrency(), prefs.getString(LOCAL_CURRENCY_PREF, null))
    }
}
