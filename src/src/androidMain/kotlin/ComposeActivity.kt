package info.bitcoinunlimited.www.wally

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.delay
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.zxing.integration.android.IntentResult
import info.bitcoinunlimited.www.wally.ui.*
import org.nexa.libnexakotlin.logThreadException
import org.nexa.libnexakotlin.rem

fun SetTitle(title: String)
{
    val ca = currentActivity
    if (ca != null)
    {
        ca.setTitle(title)
    }
}

actual fun ImageQrCode(imageParsed: (String?)->Unit): Boolean
{
    val ca = currentActivity
    (ca as ComposeActivity).ImageQrCode(imageParsed)
    return true
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
    var imageParsedFn: ((String?)->Unit)? = null
    /** Do this once we get file read permissions */
    var doOnMediaReadPerms: (() -> Unit)? = null
    /** Do this once we get file read permissions */
    var doOnFileReadPerms: (() -> Unit)? = null

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

    // call this with a function to execute whenever that function needs file read permissions
    fun onReadMediaPermissionGranted(doit: () -> Unit): Boolean
    {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            doit()
        else
        {
            doOnMediaReadPerms = doit
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), READ_MEDIA_IMAGES_RESULT)
        }
        return false
    }

    override fun onSoftKeyboard(shown: Boolean)
    {
        isSoftKeyboardShowing.value = shown
    }

    fun ImageQrCode(imageParsed: (String?) -> Unit)
    {
        imageParsedFn = imageParsed
        onReadMediaPermissionGranted {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, IMAGE_RESULT)
        }
    }

    /** this handles the result of variety of launched subactivities including:
     * a QR code scan.  We want to accept QR codes of any different format and "do what I mean" based on the QR code's contents
     * an image selection (presumably its a QR code)
     * an identity or trickle pay activity completion
     * */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        // Gallery Image selection (presumably a QR code)
        if (requestCode == IMAGE_RESULT)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                var im = data?.data
                // LogIt.info(sourceLoc() + ": Parse QR from image: " + im)
                if (im != null)
                {
                    val resolver = applicationContext.contentResolver
                    //resolver.openFileDescriptor(im, "r").use { pfd ->
                    resolver.openInputStream(im).use { s ->
                        if (s == null)  displayError(S.badImageQR)
                        else
                        {
                            try
                            {
                                val qrdata = readQRcode(s)
                                displayNotice(R.string.goodQR, qrdata)
                                imageParsedFn?.let { it(qrdata) }
                            }
                            catch (e: com.google.zxing.NotFoundException)
                            {
                                displayError(R.string.badImageQR, R.string.badImageQRhelp)
                            }
                            catch(e: Exception)
                            {
                                displayException(R.string.badImageQR, e)
                            }
                        }
                    }


                    /*

                    val path = URIPathHelper().getPath(this, im)
                    if (path != null)
                    {
                        try
                        {
                            val qrdata = readQRcode(path)
                            displayNotice(R.string.goodQR, qrdata)
                            imageParsedFn?.let { it(qrdata)}
                        }
                        catch (e: com.google.zxing.NotFoundException)
                        {
                            displayError(R.string.badImageQR, R.string.badImageQRhelp)
                        }
                        catch(e: Exception)
                        {
                            displayException(R.string.badImageQR, e)
                        }
                    }
                     */
                }
            }
        }


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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode)
        {
            READ_FILES_PERMISSION_RESULT ->
            {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                {
                    doOnFileReadPerms?.invoke()
                }
                else
                {
                    laterUI {
                        delay(1000)
                        displayError(R.string.NoPermission, i18n(R.string.NoPermissionDetails) % mapOf("perm" to "Read external storage"))
                    }
                }
                return
            }
            READ_MEDIA_IMAGES_RESULT ->
            {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                {
                    doOnMediaReadPerms?.invoke()
                }
                else
                {
                    laterUI {
                        delay(1000)
                        displayError(R.string.NoPermission, i18n(R.string.NoPermissionDetails) % mapOf("perm" to "Read media images"))
                    }
                }
                return
            }
            // Add other 'when' lines to check for other permissions this app might request.
            else ->
            {
                // Ignore all other requests.
            }
        }

    }

    /** Inflate the options menu */
    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        val inflater: MenuInflater = menuInflater

        inflater.inflate(R.menu.options_menu, menu);

        // Locate MenuItem with ShareActionProvider
        val shareItem = menu.findItem(R.id.menu_item_share)
        shareItem.setOnMenuItemClickListener {
            onShareButton()
            true
        }

        //val settingsItem = menu.findItem(R.id.settings)
        //settingsItem.setOnMenuItemClickListener {
        //    nav.go(ScreenId.Settings)
        //    true
        //}

        val unlockItem = menu.findItem(R.id.unlock)
        unlockItem.setOnMenuItemClickListener {
            triggerUnlockDialog()
            true
        }

        //val item4 = menu.findItem(R.id.compose)
        //item4.intent = Intent(this, MainActivity::class.java)

        initializeHelpOption(menu)
        return super.onCreateOptionsMenu(menu)
    }


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed()
            {
                if (nav.back() == null) finish()
            }

        })

        initializeGraphicsResources()

        // Wait for accounts to be loaded before we show the screen
        laterUI {
            //while(!coinsCreated) delay(250)
            setContent {
                val currentRootScreen = remember { mutableStateOf(ScreenId.Home) }
                nav.reset(currentRootScreen)
                SetTitle(nav.title())
                NavigationRoot(nav)
            }
        }
    }

    fun share(text:String)
    {
        val receiveAddrSendIntent: Intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(receiveAddrSendIntent, null)
        startActivity(shareIntent)
    }
}

