package info.bitcoinunlimited.www.wally

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.zxing.integration.android.IntentIntegrator
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import kotlinx.coroutines.delay
import org.nexa.libnexakotlin.launch
import android.content.pm.ActivityInfo
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentResult
import org.nexa.libnexakotlin.logThreadException
import org.nexa.libnexakotlin.sourceLoc

fun SetTitle(title: String)
{
    val ca = currentActivity
    if (ca != null)
    {
        ca.setTitle(title)
    }
}

/*
actual fun ScanQrCode(scanDone: (String)->Unit): Boolean
{
    val ca = currentActivity
    (ca as ComposeActivity).scanQrCode(scanDone)
    return true
}
 */

class ComposeActivity: CommonActivity()
{
    var nav = ScreenNav()

    var dynOrStaticOrientation: Int = -1  // Used to remember the screen orientation when temporarily disabling int
    var scanDoneFn: ((String)->Unit)? = null

    /* Not needed unless we decide we like the xzing QR scanner better
    fun scanQrCode(scanDone: (String)->Unit)
    {
        scanDoneFn = scanDone
        val v = IntentIntegrator(this)
        dynOrStaticOrientation = requestedOrientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
        v.setPrompt(i18n(R.string.scanPaymentQRcode)).setBeepEnabled(false).setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name).setOrientationLocked(true).setCameraId(0).initiateScan()

    }
     */

    /** this handles the result of variety of launched subactivities including:
     * a QR code scan.  We want to accept QR codes of any different format and "do what I mean" based on the QR code's contents
     * an image selection (presumably its a QR code)
     * an identity or trickle pay activity completion
     * */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        // QR code scanning
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null)
        {
            if (result.contents != null)
            {
                laterUI {
                    val QRstring = result.contents.toString()
                    displayNotice(i18n(R.string.scanSuccess), "QR text: " + QRstring, 2000)
                    try
                    {
                        scanDoneFn?.invoke(QRstring)
                    }
                    catch (e: Exception)  // I can't handle it as plain text
                    {
                        logThreadException(e)
                        // LogIt.info(sourceLoc() + ": QR contents invalid: " + QRstring)
                        displayError(R.string.badAddress, QRstring)
                    }

                }
                return
            }
            else
            {
                displayError(R.string.scanFailed)
            }
        }
        else
        {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed()
            {
                if (nav?.back() == null) finish()
            }

        })

        // Wait for accounts to be loaded before we show the screen
        laterUI {
            while(!coinsCreated) delay(250)
            setContent {
                val currentRootScreen = remember { mutableStateOf(ScreenId.Home) }
                nav.reset(currentRootScreen)
                SetTitle(nav.title())
                NavigationRoot(nav)
            }
        }
    }
}

