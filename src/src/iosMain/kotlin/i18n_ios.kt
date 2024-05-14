package info.bitcoinunlimited.www.wally

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import org.nexa.libnexakotlin.decodeUtf8
import platform.Foundation.*

var LocaleStrings = arrayOf<String>()

/** Convert this number to a locale-based string */
actual fun i18n(id: Int): String
{
    if (id > LocaleStrings.size - 1) return("STR$id")
    else return LocaleStrings[id]
}

actual fun setLocale():Boolean
{
    // we want whatever the phone is set to, not where the user is currently located, so system locale.
    var locale = NSLocale.autoupdatingCurrentLocale
    //var locale = NSLocale.systemLocale()
    if (locale.languageCode == null)
    {
        locale = NSLocale.currentLocale()
    }
    println("LANGUAGE: ${locale.languageCode}, COUNTRY: ${locale.countryCode}")
    return setLocale(locale.languageCode, locale.countryCode ?: "")
}

fun provideLocaleFilesData(data:ByteArray)
{
    setLocaleStringsFrom(data)
}
@OptIn(ExperimentalForeignApi::class)
actual fun setLocale(language: String, country: String):Boolean
{
    val data = try
      {
          val url = NSBundle.mainBundle.URLForResource("strings_${language}_$country", "bin")
          if (url == null) throw NotUriException()
          val data = NSData.create(url!!)
          if (data == null) throw NotUriException()
          data.bytes?.readBytes(data.length.toInt()) ?: throw NotUriException()
      }
    catch(e: Exception)
    {
        try
        {
            val url = NSBundle.mainBundle.URLForResource("strings_$language", "bin")
            if (url == null) throw NotUriException()
            val data = NSData.create(url!!)
            if (data == null) throw NotUriException()
            data.bytes?.readBytes(data.length.toInt()) ?: throw NotUriException()
        }
        catch (e: Exception)
        {
            null
        }
    }

    if (data == null || data.size == 0) return false
    val ret = setLocaleStringsFrom(data)
    data.drop(data.size) // Try to clean up most of the memory even if the mem allocator does not
    return ret
}

fun setLocaleStringsFrom(strs: ByteArray): Boolean
{
    val chopSpots = mutableListOf<Int>(0)
    strs.forEachIndexed { index, byte -> if (byte==0.toByte()) chopSpots.add(index+1)  }

    val strings = Array<String>(chopSpots.size-1, { i: Int ->
        val ba = strs.sliceArray(chopSpots[i] until chopSpots[i + 1] - 1)
        ba.decodeUtf8().replace("\\n","\n").replace("\\'","\'").replace("\\\"","\"")

    })
    chopSpots.clear()
    LocaleStrings = strings
    return true
}