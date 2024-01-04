package info.bitcoinunlimited.www.wally

import android.content.res.Resources
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.decodeUtf8
import org.nexa.libnexakotlin.appContext

private val LogIt = GetLog("BU.wally.i18n")
var LocaleStrings = listOf<String>()


// Lookup strings in strings.xml
/** Convert this number to a locale-based string.
 * First we will use common translations then Android-specific */
actual fun i18n(id: Int): String
{
    if (id == -1) return ""
    if (id < LocaleStrings.size)
    {
        return LocaleStrings[id].replace("\\n","\n")
    }

    try
    {
        if (appResources == null) LogIt.error("appResources not loaded")
        val s = appResources?.getString(id)
        if (s != null) return s
    }
    catch (e: Resources.NotFoundException)
    {
    }

    LogIt.error("Missing strings.xml translation for " + id.toString() + "(0x" + id.toString(16))
    return "STR" + id.toString()
}

actual fun setLocale():Boolean
{
    val locales = (appContext() as android.content.Context)!!.resources.configuration.locales
    for (idx in 0 until locales.size())
    {
        val loc = locales[idx]
        LogIt.info("Locale: ${loc.language} ${loc.country}")
        if (setLocale(loc.language, loc.country)) return true
    }
    return false
}

actual fun setLocale(language: String, country: String):Boolean
{
    val androidContext = (appContext() as android.content.Context)!!
    var id = androidContext.resources.getIdentifier("strings_${language}", "raw", androidContext.packageName)
    if (id == 0) id = androidContext.resources.getIdentifier("strings_${language}_${country}", "raw", androidContext.packageName)
    val strs = androidContext.resources.openRawResource(id).readBytes()

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