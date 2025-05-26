package info.bitcoinunlimited.www.wally

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import androidx.compose.ui.window.ComposeUIViewController
import info.bitcoinunlimited.www.wally.ui.BACKGROUND_SYNC_PREF
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.UiRoot
import info.bitcoinunlimited.www.wally.ui.nav
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.handleThreadException
import platform.UIKit.UIColor
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

fun MainViewController(): UIViewController
{
    backgroundOnly = false  // This function is called to instantiate the UI, so we must not be in background mode
    var v: UIViewController?=null
    // Trying to change the background to purple; does not work.
    val view = ComposeUIViewController({
        val bkgCol =  UIColor.colorWithRed(0x72/256.0, 0x50/256.0, 0x92/256.0, 1.0)
        this.delegate = object: ComposeUIViewControllerDelegate {
            override fun viewDidLoad() {
                super.viewDidLoad()
                v?.view?.backgroundColor = bkgCol
                LogIt.info("VIEW ${v?.view}")
            }
        }
    },
      {
        nav.reset(ScreenId.Splash)
        UiRoot(
          // Add padding to allow .ignoresSafeArea in iOSApp.swift
          Modifier.fillMaxSize().padding(WindowInsets.systemBars.asPaddingValues()),
          WindowInsets(0,0,0,0)
        )
    })
    v = view

    // Wrong selector
    //    val dc = NSNotificationCenter.defaultCenter
    //    dc.addObserver(dc, selector = NSSelectorFromString("keyboardWillShow:"), name = UIKeyboardWillShowNotification, null)

    return view
}

@Throws(Throwable::class, Exception::class, NullPointerException::class, RuntimeException::class)
fun iosBackgroundSync(completion: () -> Unit)
{
    val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
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

    wallyApp?.handlePaste(qr)
}
