package info.bitcoinunlimited.www.wally

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import kotlinx.coroutines.delay
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.theme.BaseBkg
import info.bitcoinunlimited.www.wally.ui2.UiRoot
import info.bitcoinunlimited.www.wally.ui2.newUI
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.rem
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

private val LogIt = GetLog("BU.wally.ComposeActivity")

actual fun ImageQrCode(imageParsed: (String?)->Unit): Boolean
{
    val ca = currentActivity
    (ca as ComposeActivity).ImageQrCode(imageParsed)
    return true
}

class ComposeActivity: CommonActivity()
{
    // var dynOrStaticOrientation: Int = -1  // Used to remember the screen orientation when temporarily disabling int
    // var scanDoneFn: ((String)->Unit)? = null
    var imageParsedFn: ((String?)->Unit)? = null
    /** Do this once we get file read permissions */
    var doOnMediaReadPerms: (() -> Unit)? = null
    /** Do this once we get file read permissions */
    var doOnFileReadPerms: (() -> Unit)? = null

    override fun splash(shown: Boolean)
    {
        if (shown)
        {

        }
        else
        {
            val v = findViewById<View>(android.R.id.content).getRootView()
            v.setBackgroundResource(0)
            v.background = ColorDrawable(ContextCompat.getColor(applicationContext, R.color.titleBackground))
        }
    }

    // call this with a function to execute whenever that function needs file read permissions
    fun onReadMediaPermissionGranted(doit: () -> Unit): Boolean
    {
        // In later versions of android we can ask for a less intrusive permission
        if (android.os.Build.VERSION.SDK_INT >= 33)
        {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED)
                doit()
            else
            {
                doOnMediaReadPerms = doit
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), READ_MEDIA_IMAGES_RESULT)
            }
        }
        else // otherwise we have to ask for access to any external storage files to access the gallery
        {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                doit()
            else
            {
                doOnMediaReadPerms = doit
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_MEDIA_IMAGES_RESULT)
            }
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
                }
            }
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

        // New UI
        if (newUI.value)
        {
            inflater.inflate(R.menu.options_menu_ui2, menu)

            val settingsItem = menu.findItem(R.id.settings)
            settingsItem.setOnMenuItemClickListener {
                nav.go(ScreenId.Settings)
                true
            }
            val shareItemUi2 = menu.findItem(R.id.menu_item_share_ui2)
            shareItemUi2.setOnMenuItemClickListener {
                onShareButton()
                true
            }
            val unlockItem = menu.findItem(R.id.unlock_ui2)
            unlockItem.setOnMenuItemClickListener {
                triggerUnlockDialog()
                true
            }
        }
        // Old UI
        else
        {
            inflater.inflate(R.menu.options_menu, menu)
            // Locate MenuItem with ShareActionProvider
            val shareItem = menu.findItem(R.id.menu_item_share)
            shareItem.setOnMenuItemClickListener {
                onShareButton()
                true
            }
            val unlockItem = menu.findItem(R.id.unlock)
            unlockItem.setOnMenuItemClickListener {
                triggerUnlockDialog()
                true
            }
            initializeHelpOption(menu)
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onResume()
    {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        val decorView: View = getWindow().getDecorView()
        decorView.setBackgroundColor(BaseBkg.value.toInt())
        initializeGraphicsResources()
        backgroundOnly = false

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed()
            {
                if (nav.back() == null) finish()
                else setTitle(nav.title())
            }
        })

        (decorView.findViewById(android.R.id.content) as View).setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30)
            {
                //TODO: insets.getInsets(WindowInsetsCompat.Type.ime())
                val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                // We sanity check the system insets to 30 dp just in case something crazy is going on
                androidPlatformCharacteristics.bottomSystemBarOverlap = min(30.dp, pxToDp(system.bottom))
            }
            insets
        }

        // If the UI is opened, register background sync work.  But we don't want to reregister the background work whenever the background work
        // itself is launched, so this code can't be in the app class.
        // This starts up every 15 min
        val bkgSync = PeriodicWorkRequestBuilder<BackgroundSync>(BACKGROUND_PERIOD_MSEC.milliseconds.toJavaDuration()).build()
        bkgSync.let { WorkManager.getInstance(this).enqueueUniquePeriodicWork("WallySync", ExistingPeriodicWorkPolicy.UPDATE, it) }
        // This will start up a few seconds after the app is closed, but only once (once it reports its finished)
        val bkgSyncOnce = OneTimeWorkRequestBuilder<BackgroundSync>().build()
        WorkManager.getInstance(this).enqueueUniqueWork("WallySyncOnce", ExistingWorkPolicy.REPLACE, bkgSyncOnce)

        var actionb:Int? = null
        val intentUri = com.eygraber.uri.Uri.parseOrNull(intent.toUri(0))
        LogIt.info("Launched by intent URI: ${intent.toUri(0)}  intent: $intent")
        setContent {
            val newUi = newUI.collectAsState().value
            val scheme = intentUri?.scheme?.lowercase()

            // If there's an incoming intent, go handle it don't pop the app up normally
            if ((scheme == TDPP_URI_SCHEME) || (scheme == IDENTITY_URI_SCHEME))
            {
                // There is no reason for a local app to ask for the clipboard other then to hide its own clipboard use
                if (intentUri.getQueryParameter("info")?.lowercase() == "clipboard")
                {
                    setResult(Activity.RESULT_CANCELED, intent)
                    finish()
                }
                else
                {
                    // Push in the end
                    nav.reset(ScreenId.None)
                    // And make the back button finish the activity
                    nav.onDepart {
                        if (it == ScreenNav.Direction.LEAVING)
                        {
                            setResult(Activity.RESULT_CANCELED, intent)
                            finish()
                        }
                    }
                    // Now handle the request (which should set the screen to something)
                    wallyApp!!.handlePaste(intent.toUri(0)) { uriBack: String, data: String, worked: Boolean? ->
                        val result = Intent()
                        result.putExtra("body", data)  // This data would be in the http POST body which is why its called body
                        result.data = android.net.Uri.parse(uriBack)
                        nav.reset(ScreenId.Home)  // get rid of our cancel onDepart
                        if (worked == false) setResult(Activity.RESULT_CANCELED, result) else setResult(Activity.RESULT_OK, result)
                        finish()
                    }
                }
            }
            // If a monetary transfer was requested
            if ((scheme == "nexa") || (scheme == "nexatest") || (scheme == "nexareg"))
            {
                nav.reset(ScreenId.None)
                // And make the back button finish the activity
                nav.onDepart {
                    if (it == ScreenNav.Direction.LEAVING)
                    {
                        setResult(Activity.RESULT_CANCELED, Intent())
                        finish()
                    }
                }
                wallyApp!!.handlePaste(intent.toUri(0)) { uriBack:String, data:String, worked:Boolean? ->
                    // TODO what to reply with with just a successful money send
                    val result = Intent()
                    result.putExtra("body", data)  // This data would be in the http POST body which is why its called body
                    result.data = android.net.Uri.parse(uriBack)
                    nav.reset(ScreenId.Home)  // get rid of our cancel onDepart
                    if (worked == false) setResult(Activity.RESULT_CANCELED, result) else setResult(Activity.RESULT_OK, result)
                    finish()
                }
            }

            setTitle(nav.title())
            // Note that modern versions of android place the app view behind the system "insets". Old ones do not.
            // DONT MESS WITH THIS CODE unless you are ready to test multiple android versions!
            if (actionb == null)
            {
                val insets = ViewCompat.getRootWindowInsets(LocalView.current)
                val sysInsets = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                actionb = if (android.os.Build.VERSION.SDK_INT < 35) 0 else sysInsets.top
            }
            val systemPadding = Modifier.padding(0.dp, pxToDp(actionb ?: 0), 0.dp, 0.dp) // pxToDp(sysInsets.bottom))
            UiRoot(systemPadding)

            LaunchedEffect(newUi) {
                invalidateOptionsMenu()
            }
        }

    }

    // If the title bar is touched, show all the errors and warnings the app has generated
    // unless we are already in that screen.
    override fun onTitleBarTouched()
    {
        /*
        if (nav.currentScreen.value == ScreenId.Logs)
        {
            nav.back()
        }
        else
        {
            nav.go(ScreenId.Logs)
        }
         */
    }


    override fun onDestroy()
    {
        backgroundOnly = true
        super.onDestroy()
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

