package info.bitcoinunlimited.www.wally

/** The fiat currencies the local currency setting offers */
val FIAT_CURRENCIES = listOf("BRL", "CAD", "CNY", "EUR", "GBP", "INR", "JPY", "RUB", "USD", "XAU")

/** Used whenever the device does not report a locale, or reports one we have no currency for */
const val FALLBACK_FIAT_CURRENCY = "USD"

private val EUROZONE = setOf("AT", "BE", "HR", "CY", "EE", "FI", "FR", "DE", "GR", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PT", "SK", "SI", "ES")

private val countryCurrency: Map<String, String> = mapOf(
  "BR" to "BRL", "CA" to "CAD", "CN" to "CNY", "GB" to "GBP", "IN" to "INR", "JP" to "JPY", "RU" to "RUB", "US" to "USD"
) + EUROZONE.associateWith { "EUR" }

private val languageCurrency: Map<String, String> = mapOf(
  "ja" to "JPY", "zh" to "CNY", "ru" to "RUB", "pt" to "BRL", "en" to "USD",
  "de" to "EUR", "fr" to "EUR", "it" to "EUR", "nl" to "EUR", "el" to "EUR", "fi" to "EUR",
  "et" to "EUR", "lv" to "EUR", "lt" to "EUR", "sk" to "EUR", "sl" to "EUR", "ga" to "EUR", "mt" to "EUR"
)

/** These languages are spoken across several of the currencies we offer, so let the country decide */
private val multiCurrencyLanguages = setOf("en", "fr", "pt")

/** The currency for a device locale, always one of [FIAT_CURRENCIES].
 * Outside [multiCurrencyLanguages] the language wins over the country on purpose: with so few currencies on
 * offer the language is the better guess, so de-CH and it-CH give EUR and zh-TW and zh-HK give CNY. */
fun localeFiatCurrency(language: String?, country: String?): String
{
    val lang = (language ?: "").lowercase().substringBefore('-').substringBefore('_')
    val ctry = (country ?: "").uppercase().substringBefore('-').substringBefore('_')
    if (lang in multiCurrencyLanguages) return countryCurrency[ctry] ?: languageCurrency[lang] ?: FALLBACK_FIAT_CURRENCY
    return languageCurrency[lang] ?: countryCurrency[ctry] ?: FALLBACK_FIAT_CURRENCY
}

/** The currency this device's locale asks for, always one of [FIAT_CURRENCIES] */
fun deviceFiatCurrency(): String
{
    val locale = deviceLocale() ?: return FALLBACK_FIAT_CURRENCY
    return localeFiatCurrency(locale.first, locale.second)
}

/** The chosen currency, or the one this device's locale asks for if none is chosen.  Never writes, so it is safe in composition. */
fun chosenFiatCurrency(preferenceDB: SharedPreferences): String
{
    val chosen = preferenceDB.getString(LOCAL_CURRENCY_PREF, null)
    return if (chosen.isNullOrEmpty()) deviceFiatCurrency() else chosen
}

/** The chosen currency.  Until the user chooses one we save the currency this device's locale asks for.
 * This commits to disk, so call it at startup, not from the UI thread. */
fun defaultFiatCurrency(preferenceDB: SharedPreferences): String
{
    val chosen = preferenceDB.getString(LOCAL_CURRENCY_PREF, null)
    if (!chosen.isNullOrEmpty()) return chosen
    val currency = deviceFiatCurrency()
    preferenceDB.edit().putString(LOCAL_CURRENCY_PREF, currency).commit()
    return currency
}
