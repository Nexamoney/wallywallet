package info.bitcoinunlimited.www.wally

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSBundle
import org.nexa.libnexakotlin.decodeUtf8
import platform.Foundation.NSData
import platform.Foundation.create

var LocaleStrings = listOf<String>()

/** Convert this number to a locale-based string */
actual fun i18n(id: Int): String
{
    if (id > LocaleStrings.size - 1) return("STR$id")
    else return LocaleStrings[id].replace("\\n","\n")
}


actual fun setLocale():Boolean
{
    //val locale = Locale.getDefault()
    //return setLocale(locale.language, locale.country)
    return false
}

fun provideLocaleFilesData(data:ByteArray)
{
    setLocaleStringsFrom(data)
}
@OptIn(ExperimentalForeignApi::class)
actual fun setLocale(language: String, country: String):Boolean
{

    //val nothing = Objectify<Int>(0)
    val loadTries = listOf<() -> ByteArray>(
      {
          val url = NSBundle.mainBundle.URLForResource("strings_$language", "bin")
          if (url == null) throw NotUriException()
          val data = NSData.create(url!!)
          if (data == null) throw NotUriException()
          data.bytes?.readBytes(data.length.toInt()) ?: throw NotUriException()
      }
      // { nothing::class.java.getClassLoader().getResourceAsStream("strings_${language}_${country}.bin").readBytes() },
      //  { nothing::class.java.getClassLoader().getResourceAsStream("strings_${language}.bin").readBytes() },
      // { File("strings_${language}_${country}.bin").readBytes() },
      //  { File("strings_${language}.bin").readBytes() }
    )

    var strs = byteArrayOf()
    for (i in loadTries)
    {
        try
        {
            strs = i()
            break
        }
        catch (e: Exception)
        {
        }
    }
    if (strs.size == 0) return false
    return setLocaleStringsFrom(strs)
}

fun setLocaleStringsFrom(strs: ByteArray): Boolean
{
    val chopSpots = mutableListOf<Int>(0)
    strs.forEachIndexed { index, byte -> if (byte==0.toByte()) chopSpots.add(index+1)  }

    val strings = mutableListOf<String>()
    for (i in 0 until chopSpots.size-1)
    {
        val ba = strs.sliceArray(chopSpots[i] until chopSpots[i+1]-1)
        strings.add(ba.decodeUtf8())
    }
    LocaleStrings = strings
    return true
}