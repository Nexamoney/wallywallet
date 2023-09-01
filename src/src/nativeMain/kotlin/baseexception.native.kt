package info.bitcoinunlimited.www.wally

/** Convert this number to a locale-based string */
actual fun i18n(id: Int): String
{
    return "Msg$id"
}