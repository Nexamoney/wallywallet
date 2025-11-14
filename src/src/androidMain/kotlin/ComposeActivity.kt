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
import android.net.Uri
import android.os.Build
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.theme.BaseBkg
import info.bitcoinunlimited.www.wally.ui.theme.colorTitleBackground
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.laterJob
import org.nexa.libnexakotlin.logThreadException
import org.nexa.libnexakotlin.rem
import org.nexa.libnexakotlin.runningTheTests
import org.nexa.threads.millisleep
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

private val LogIt = GetLog("BU.wally.ComposeActivity")

actual fun ImageQrCode(imageParsed: (String?)->Unit): Boolean
{
    val ca = currentActivity
    (ca as ComposeActivity?)?.ImageQrCode(imageParsed)
    return true
}

private lateinit var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>

var lastHandledIntent = ""

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
    fun onReadMediaPermissionGrantedLegacy(doit: () -> Unit): Boolean
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Do nothing, this is not legacy
            return false
        }
        else // otherwise we have to ask for access to any external storage files to access the gallery
        {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                doit()
            else
            {
                doOnMediaReadPerms = doit
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_MEDIA_IMAGES_RESULT)
                return true
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

        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            // Handle fallback for older Android versions
            onReadMediaPermissionGrantedLegacy {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(intent, IMAGE_RESULT)
            }
        }
         */
    }

    /** this handles the result of variety of launched subactivities including:
     * a QR code scan.  We want to accept QR codes of any different format and "do what I mean" based on the QR code's contents
     * an image selection (presumably its a QR code)
     * an identity or trickle pay activity completion
     * This is a legacy callback for Android 12 and below.
     * */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)
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
                                LogIt.info("QR code not found in image: ${e.message}")
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
        inflater.inflate(R.menu.options_menu, menu)

        val settingsItem = menu.findItem(R.id.settings)
        settingsItem.setOnMenuItemClickListener {
            // Clicking this settings icon while in settings screen was causing the back button to navigate to settings...
            if(nav.currentScreen.value != ScreenId.Settings)
                nav.go(ScreenId.Settings)
            true
        }
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

        return super.onCreateOptionsMenu(menu)
    }

    override fun onResume()
    {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        enableEdgeToEdge()
        // WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        window.statusBarColor = colorTitleBackground.toArgb()
        window.navigationBarColor = colorTitleBackground.toArgb()
        val decorView: View = getWindow().getDecorView()
        decorView.setBackgroundColor(BaseBkg.value.toInt())
        backgroundOnly = false

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed()
            {
                try
                {
                    if (nav.back() == null) finish()
                    else setTitle(nav.title())
                }
                catch (e: NullPointerException)
                {
                    logThreadException(e, "NPE when handling back button")
                }
            }
        })

        (decorView.findViewById(android.R.id.content) as View).setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30)
            {
                val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                LogIt.info("Insets: systembars top=${system.top}, bottom=${system.bottom}")
                val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                LogIt.info("Insets: statusbars top=${status.top}, bottom=${status.bottom}")
                val capt = insets.getInsets(WindowInsetsCompat.Type.captionBar())
                LogIt.info("Insets: captionbars top=${capt.top}, bottom=${capt.bottom}")
                // We sanity check the system insets to 30 dp just in case something crazy is going on
                androidPlatformCharacteristics.bottomSystemBarOverlap = min(60.dp, pxToDp(system.bottom))
            }
            insets
        }

        // Monitor the content view and the action bar (top bar) view to see if the action bar overlaps the content.
        // If so, set a global mutable state flow that is used to pad the content so it always appears below the action bar.
        val actionBar = findViewById<View>(actionBarId)
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnGlobalLayoutListener {
            // The content is behind the action bar, so pad it
            if (content.top < actionBar.bottom)
            {
                behindTitleBarPadding.value = TopAppBarDefaults.TopAppBarExpandedHeight
            }
            else
            {
                behindTitleBarPadding.value = 0.dp
            }
        }

        // If the UI is opened, register background sync work.  But we don't want to reregister the background work whenever the background work
        // itself is launched, so this code can't be in the app class.
        // This starts up every 15 min
        if (!runningTheTests)
        {
            val bkgSync = PeriodicWorkRequestBuilder<BackgroundSync>(BACKGROUND_PERIOD_MSEC.milliseconds.toJavaDuration()).build()
            bkgSync.let { WorkManager.getInstance(this).enqueueUniquePeriodicWork("WallySync", ExistingPeriodicWorkPolicy.UPDATE, it) }
            // This will start up a few seconds after the app is closed, but only once (once it reports its finished)
            val bkgSyncOnce = OneTimeWorkRequestBuilder<BackgroundSync>().build()
            WorkManager.getInstance(this).enqueueUniqueWork("WallySyncOnce", ExistingWorkPolicy.REPLACE, bkgSyncOnce)
        }

        var actionb:Int? = null
        val intentDataStr = intent.data.toString()  // Save the intent data string in case the app clears it later
        // Grab the intent if we haven't already marked it as handled
        val tmp = com.eygraber.uri.Uri.parseOrNull(intent.toUri(0))
        val intentUri = if (intentDataStr != lastHandledIntent) tmp else null

        LogIt.info("Launched by referrer:  $referrer")
        val caller = callingActivity
        LogIt.info("Launched by activity:  $caller")
        val callerPkg = callingPackage
        val intentReturnsData = if (caller == null) false
        else // if the caller launched with the intention of getting data back, caller should be non-null
        {
            LogIt.info("Launched by a specific caller:  $caller $callerPkg\nfor data so data will be returned in the intent rather than posted.")
            true
        }
        var returnToLaunchingApp = true  // by default if an app launched us, we'll assume we want to go back to it
        if (referrer != null)
        {
            // The user probably just popped up the camera to do a scan; they don't want to go back there.
            if (referrer.toString().contains("camera")) returnToLaunchingApp = false
        }

        LogIt.info("Launched by intent URI: ${intent.toUri(0)}  intent: $intent")
        LogIt.info("Last handled: ${lastHandledIntent}")
        initializeGraphicsResources()
        setContent {
            val scheme = intentUri?.scheme?.lowercase()

            // If there's an incoming intent, go handle it don't pop the app up normally
            if ((scheme == TDPP_URI_SCHEME) || (scheme == IDENTITY_URI_SCHEME))
            {
                // There is no reason for a local app to ask for the clipboard other then to hide its own clipboard use
                if (intentUri.getQueryParameter("info")?.lowercase() == "clipboard")
                {
                    lastHandledIntent = intentDataStr // intent.data.toString()
                    setIntent(null)  // done handling this
                    setResult(Activity.RESULT_CANCELED, intent)
                    if (returnToLaunchingApp) finish()
                }
                else
                {
                    // Push in the end
                    nav.reset(ScreenId.None)
                    // And make the back button finish the activity
                    nav.onDepart {
                        if (it == ScreenNav.Direction.LEAVING)
                        {
                            if (intent != null)  // Even though this is seen as not nullable, we see null object references in the play console errors
                            {
                                lastHandledIntent = intentDataStr // intent.data.toString()
                                setResult(Activity.RESULT_CANCELED, intent)
                            }
                            setIntent(null)  // done handling this
                            if (returnToLaunchingApp) finish()
                        }
                    }
                    // Now handle the request (which should set the screen to something)
                    wallyApp!!.handlePaste(intent.toUri(0), !intentReturnsData) { uriBack: String, data: String, worked: Boolean? ->
                        lastHandledIntent = intentDataStr // intent.data.toString()
                        setIntent(null)  // done handling this
                        val result = Intent()
                        result.putExtra("body", data)  // This data would be in the http POST body which is why its called body
                        result.data = android.net.Uri.parse(uriBack)
                        nav.reset(ScreenId.Home)  // get rid of our cancel onDepart
                        if (worked == false) setResult(Activity.RESULT_CANCELED, result) else setResult(Activity.RESULT_OK, result)
                        if (returnToLaunchingApp) finish()
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
                        lastHandledIntent = intentDataStr //intent.data.toString()
                        setIntent(null)  // done handling this
                        setResult(Activity.RESULT_CANCELED, Intent())
                        if (returnToLaunchingApp) finish()
                    }
                }
                wallyApp!!.handlePaste(intent.toUri(0), !intentReturnsData) { uriBack:String, data:String, worked:Boolean? ->
                    // TODO what to reply with with just a successful money send
                    lastHandledIntent = intentDataStr // intent.data.toString()
                    setIntent(null)  // done handling this
                    val result = Intent()
                    result.putExtra("body", data)  // This data would be in the http POST body which is why its called body
                    result.data = android.net.Uri.parse(uriBack)
                    nav.reset(ScreenId.Home)  // get rid of our cancel onDepart
                    if (worked == false) setResult(Activity.RESULT_CANCELED, result) else setResult(Activity.RESULT_OK, result)
                    if (returnToLaunchingApp) finish()
                }
            }
            else if ((scheme == "http") || (scheme == "https"))  // Deep link
            {
                // Push in the end
                nav.reset(ScreenId.None)
                // And make the back button finish the activity
                nav.onDepart {
                    if (it == ScreenNav.Direction.LEAVING)
                    {
                        lastHandledIntent = intentDataStr // intent?.data?.toString()
                        setIntent(null)  // done handling this
                        nav.reset(ScreenId.Home)
                        setResult(Activity.RESULT_CANCELED, intent)
                        if (returnToLaunchingApp) finish()
                    }
                }
                // We need to open the accounts, etc before we can process a paste intent so add this to the whenReady queue.
                wallyApp!!.runWhenReady {
                    // Now handle the request (which should set the screen to something)
                    wallyApp!!.handlePaste(intent.toUri(0),!intentReturnsData) { uriBack: String, data: String, worked: Boolean? ->
                        lastHandledIntent = intentDataStr //intent.data.toString()
                        setIntent(null)  // done handling this
                        val result = Intent()
                        result.putExtra("body", data)  // This data would be in the http POST body which is why its called body
                        result.data = android.net.Uri.parse(uriBack)
                        nav.reset(ScreenId.Home)  // get rid of our cancel onDepart
                        if (worked == false) setResult(Activity.RESULT_CANCELED, result) else setResult(Activity.RESULT_OK, result)
                        if (returnToLaunchingApp) finish()
                    }
                }
            }

            setTitle(nav.title())
            // Note that modern versions of android place the app view behind the system "insets". Old ones do not.
            // DONT MESS WITH THIS CODE unless you are ready to test multiple android versions!
            val insets = ViewCompat.getRootWindowInsets(LocalView.current)
            if (actionb == null)
            {
                val sysInsets = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                actionb = if (android.os.Build.VERSION.SDK_INT < 35) 0 else sysInsets.top
            }
            val navInsets = insets!!.getInsets(WindowInsetsCompat.Type.navigationBars())
            val navBottom = if (android.os.Build.VERSION.SDK_INT < 35) 0 else navInsets.bottom
            val systemPadding = WindowInsets(0.dp, pxToDp(actionb ?: 0), 0.dp, pxToDp(navBottom)) // pxToDp(sysInsets.bottom))

            UiRoot(Modifier, systemPadding)

            LaunchedEffect(Unit) {
                invalidateOptionsMenu()
            }
        }

        // Initialize the launcher
        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // LogIt.info(sourceLoc() + ": Parse QR from image: " + im)
            if (uri != null)
            {
                val resolver = applicationContext.contentResolver
                //resolver.openFileDescriptor(im, "r").use { pfd ->
                resolver.openInputStream(uri).use { s ->
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
            else
            {
                LogIt.info("No image selected")
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
        LogIt.info("Sharing a string of ${text.length} ")
        val sendIntent = if (text.length > 4*1024)
        {
            val file = File(cacheDir, "shared.txt")
            file.writeText(text)
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        else
        {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }
}

