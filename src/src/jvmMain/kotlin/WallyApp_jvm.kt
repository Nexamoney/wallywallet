package info.bitcoinunlimited.www.wally

import androidx.compose.foundation.layout.WindowInsets
import org.nexa.libnexakotlin.*
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.rememberWindowState
import info.bitcoinunlimited.www.wally.ui2.ScreenId
import info.bitcoinunlimited.www.wally.ui2.views.loadingAnimation
import info.bitcoinunlimited.www.wally.ui2.UiRoot
import info.bitcoinunlimited.www.wally.ui2.nav
import java.io.File

private val LogIt = GetLog("BU.wally.IdentityActivity")

fun loadTextResource(resFile: String):String?
{
    val nothing = Objectify<Int>(0)
    val loadTries = listOf<() -> ByteArray>(
      { nothing::class.java.getClassLoader().getResourceAsStream(resFile).readBytes() },
      { File(resFile).readBytes() }
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
    if (strs.size == 0) return null
    else return strs.decodeUtf8()
}


fun initializeGraphicsResources()
{
    loadingAnimation = loadTextResource("loading_animation.json")
}


object WallyJvmApp
{
    fun runningTest(): Boolean
    {
        return try
        {
            Class.forName("kotlin.test.Test")
            true
        }
        catch (e: ClassNotFoundException)
        {
            false
        }
        catch (e: Exception)
        {
            false
        }
    }

    @JvmStatic
    fun main(args: Array<String>)
    {
        initializeLibNexa()
        initializeGraphicsResources()
        setLocale()
        LogIt.warning("Starting Wally Enterprise Wallet")
        wallyApp = CommonApp(runningTest())
        wallyApp!!.onCreate()
        guiNewPanel()
    }

    var topWindow = mutableStateOf(i18n(S.app_name))
}


fun guiNewPanel()
{
    backgroundOnly = false
    application(true)
    {
        var isOpen by remember { mutableStateOf(true) }
        nav.reset(ScreenId.Splash)

        if (isOpen)
        {
            val w = Window(
              onCloseRequest = { isOpen = false },
              title = nav.title(),
              state = rememberWindowState(width = (5 * 160).dp, height = (7 * 160).dp)
            )
            {
                UiRoot(Modifier,  WindowInsets(0,0,0,0))
            }
        }
    }
}
