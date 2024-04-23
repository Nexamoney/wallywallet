package info.bitcoinunlimited.www.wally

import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import androidx.compose.ui.window.ComposeUIViewController
import info.bitcoinunlimited.www.wally.ui.BACKGROUND_SYNC_PREF
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import kotlinx.cinterop.ExperimentalForeignApi
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.initializeLibNexa
import platform.CoreGraphics.CGColorRef
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

private val LogIt = GetLog("BU.wally.iosMain.MainViewController")
val accounts = mutableMapOf<String, Bip44Wallet>()

fun OnAppStartup()
{
    initializeLibNexa()
    setLocale()
    wallyApp = CommonApp()
    wallyApp!!.onCreate()
}
val nav = ScreenNav()

@OptIn(ExperimentalForeignApi::class, InternalComposeApi::class)
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
        val currentRootScreen = remember { mutableStateOf(ScreenId.Splash) }
        nav.reset(currentRootScreen)
        NavigationRoot(nav)
    })
    v = view

    // Wrong selector
    //    val dc = NSNotificationCenter.defaultCenter
    //    dc.addObserver(dc, selector = NSSelectorFromString("keyboardWillShow:"), name = UIKeyboardWillShowNotification, null)
    return view
}

fun iosBackgroundSync(completion: () -> Unit)
{
    val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
    val allowBackgroundSync = preferenceDB.getBoolean(BACKGROUND_SYNC_PREF, true)
    if (allowBackgroundSync)
        backgroundSync(completion)
    else
        completion()
}

fun iosCancelBackgroundSync()
{
    cancelBackgroundSync()
}
