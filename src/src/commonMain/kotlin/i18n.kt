package info.bitcoinunlimited.www.wally

/** Convert this number to a locale-based string */
expect fun i18n(id: Int): String

/** Set the current locale to the reported region on this device
 * @return false if that locale is unsupported, or the device has no API to get the locale */
expect fun setLocale():Boolean

/** Set the current locale (pass the region code) */
expect fun setLocale(language: String, country: String): Boolean
