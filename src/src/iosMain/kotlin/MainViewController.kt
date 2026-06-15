package info.bitcoinunlimited.www.wally

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.UiRoot
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.handleThreadException
import org.nexa.libnexakotlin.laterJob
import org.nexa.threads.millisleep
import org.jetbrains.skia.FontMgr
import platform.UIKit.UIViewController

private val LogIt = GetLog("BU.wally.iosMain.MainViewController")
val accounts = mutableMapOf<String, Bip44Wallet>()


@Throws(Throwable::class, Exception::class, NullPointerException::class, RuntimeException::class)
fun OnAppStartup()
{
    initializeLibNexa()
    setLocale()
    // TODO discover if we are running in test mode and set this appropriately
    // This is not that necessary for ios tests now, because the tests only run in
    // a simulation environment, so we do not need to isolate real and test states as we should
    // in a real wallet.
    wallyApp = CommonApp(false)
    wallyApp!!.onCreate()
    LogIt.info("Wally APP startup")

}

// Build the Skia FontMgr.default global (its constructor enumerates CoreText fonts) off the main
// thread, so Compose's first scene commit reuses it instead of initializing it on the UI thread and
// tripping the launch watchdog (#652). skiko is a commonMain dep, so this is the same FontMgr that
// Compose's FontCache reads.
fun prewarmFontMgr()
{
    try
    {
        val n = FontMgr.default.familiesCount
        LogIt.info("prewarmFontMgr: $n font families")
    }
    catch (e: Throwable)
    {
        LogIt.info("prewarmFontMgr failed: ${e.message}")
    }
}

fun MainViewController(): UIViewController
{
    backgroundOnly = false  // This function is called to instantiate the UI, so we must not be in background mode
    val unlock = UnlockViewModel(wallyApp!!.focusedAccount)

    val view = ComposeUIViewController({},
      {
        UiRoot(
          // Add padding to allow .ignoresSafeArea in iOSApp.swift
          Modifier.fillMaxSize().padding(WindowInsets.systemBars.asPaddingValues()),
          WindowInsets(0,0,0,0),
          unlock
        )
    })
    // Wrong selector
    //    val dc = NSNotificationCenter.defaultCenter
    //    dc.addObserver(dc, selector = NSSelectorFromString("keyboardWillShow:"), name = UIKeyboardWillShowNotification, null)

    return view
}

@Throws(Throwable::class, Exception::class, NullPointerException::class, RuntimeException::class)
fun iosBackgroundSync(completion: () -> Unit)
{
    val preferenceDB: SharedPreferences = getSharedPreferences(PREFERENCE_FILE_NAME, PREF_MODE_PRIVATE)
    val allowBackgroundSync = preferenceDB.getBoolean(BACKGROUND_SYNC_PREF, true)
    if (allowBackgroundSync)
        backgroundSync(completion)
    else
        completion()
}

@Throws(Throwable::class, Exception::class, NullPointerException::class, RuntimeException::class)
fun iosCancelBackgroundSync()
{
    try
    {
        cancelBackgroundSync()
    }
    catch(e: Exception)
    {
        println(e.stackTraceToString())
        handleThreadException(e, "Unexpected exception cancelling background sync")
    }
}

@Throws(Throwable::class, Exception::class, NullPointerException::class, RuntimeException::class)
fun onQrCodeScannedWithDefaultCameraApp(qr: String)
{
    LogIt.info("onQrCodeScannedWithDefaultCameraApp: $qr")

    laterJob {
        while(true)
        {
            val w = wallyApp
            if (w != null)
            {
                w.runWhenReady {
                    w.handlePaste(qr)
                }
                break
            }
            millisleep(200U)
        }

    }
}
