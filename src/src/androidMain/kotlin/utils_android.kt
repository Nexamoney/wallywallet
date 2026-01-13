package info.bitcoinunlimited.www.wally

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import info.bitcoinunlimited.www.wally.old.convertOldAccounts
import info.bitcoinunlimited.www.wally.ui.theme.colorError
import info.bitcoinunlimited.www.wally.ui.theme.colorNotice
import info.bitcoinunlimited.www.wally.ui.theme.colorWarning
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import okio.*
import org.nexa.libnexakotlin.*
import java.io.File
import java.io.InputStream
import java.util.zip.Inflater

/*
actual fun scaleTo(imageBytes: ByteArray, width: Int, height: Int, outFormat: EncodedImageFormat): ByteArray?
{
    val bmp = BitmapFactory.decodeByteArray(imageBytes,0, imageBytes.size)
    val bitmap = Bitmap.createScaledBitmap(bmp, width, height, true)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.

}
 */

var notificationId = -1
actual fun notify(title: String?, content: String, onlyIfBackground: Boolean): Int
{
    val t = title ?: i18n(S.app_long_name)
    val app = wallyAndroidApp
    if (app != null)
    {
        if (!app.visible())
        {
            val intent = Intent(wallyAndroidApp, ComposeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            notificationId = app.notify(intent, t, content, currentActivity as AppCompatActivity, overwrite = notificationId)
        }
    }
    return notificationId
}

actual fun denotify(id: Int): Boolean
{
    if (id == -1) return false
    wallyAndroidApp?.denotify(id)
    if (notificationId == id) notificationId = -1
    return true
}

actual fun makeImageBitmap(imageBytes: ByteArray, width: Int, height: Int, scaleMode: ScaleMode): ImageBitmap?
{
    val imIn = BitmapFactory.decodeByteArray(imageBytes,0, imageBytes.size)
    if (imIn == null) return null
    var newWidth = width
    var newHeight = height
    if ((scaleMode != ScaleMode.DISTORT)&&(imIn.height != 0))
    {
        var ratio = imIn.width.toFloat()/imIn.height.toFloat()

        if (scaleMode == ScaleMode.INSIDE)
        {
            if (ratio < 1.0) newWidth = (height*ratio).toInt()
            else newHeight = (width/ratio).toInt()
        }
        if (scaleMode == ScaleMode.COVER)
        {
            if (ratio < 1.0) newHeight = (width/ratio).toInt()
            else newWidth = (width*ratio).toInt()
        }
    }
    val sbmp = Bitmap.createScaledBitmap(imIn, newWidth, newHeight, true)
    return sbmp.asImageBitmap()
}
/* logic to handle scaling if we have to provide the scale factor
    var scale:Float = 1.0
    if (scaleMode != ScaleMode.DISTORT)
    {
        var sx = width.toFloat()/imIn.width.toFloat()
        var sy = height.toFloat()/imIn.height.toFloat()
        var minIsX = (abs(1.0-sx) < abs(1.0-sy))
        scale = if (scaleMode == ScaleMode.INSIDE)
        {
            // choose the minimum scale of the 2
            if (minIsX) sx else sy
        }
        else
        {
            if (minIsX) sy else sx
        }
    }
 */

actual fun convertOldAccounts(): Boolean
{
    return convertOldAccounts(wallyAndroidApp!!)
}

actual fun platformRam():Long?
{
    val appContext:Context? = wallyAndroidApp as? Context //  currentActivity?.applicationContext
    if (appContext!=null)
      {
          val svc = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
          val memInfo = ActivityManager.MemoryInfo()
          svc.getMemoryInfo(memInfo)
          return memInfo.totalMem
      }
    return null
}

actual fun applicationState(): ApplicationState
{
    return ApplicationState(ApplicationState.RunState.ACTIVE)
}

actual fun inflateRfc1951(compressedBytes: ByteArray, expectedfinalSize: Long): ByteArray
{
    val inf = Inflater(true)  // true means do not wrap in the gzip header

    inf.setInput(compressedBytes)
    val ba = ByteArray(expectedfinalSize.toInt())
    val sz = inf.inflate(ba)
    if (sz != expectedfinalSize.toInt()) throw Exception("inflate wrong size")
    inf.end()
    return ba
}

actual fun stackTraceWithout(skipFirst: MutableSet<String>, ignoreFiles: MutableSet<String>?): String
{
    skipFirst.add("stackTraceWithout")
    skipFirst.add("stackTraceWithout\$default")
    val igf = ignoreFiles ?: defaultIgnoreFiles
    val st = Exception().stackTrace.toMutableList()
    while (st.isNotEmpty() && skipFirst.contains(st.first().methodName)) st.removeAt(0)
    st.removeAll { igf.contains(it.fileName) }
    val sb = StringBuilder()
    st.forEach { sb.append(it.toString()).append("\n") }
    return st.toString()
}

/** Gets the ktor http client for this platform */
actual fun GetHttpClient(timeoutInMs: Number): HttpClient = HttpClient(Android) {
    install(HttpTimeout) { requestTimeoutMillis = timeoutInMs.toLong() } // Long timeout because we don't expect a response right away; its a long poll
}

actual fun PlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Android, block)


/** Get the clipboard.  Platforms that have a clipboard history should return that history, with the primary clip in index 0 */
actual fun getTextClipboard(): List<String>
{
    val c = (appContext() as? android.content.Context) ?: return listOf()
    var myClipboard = c.getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
    var clip = myClipboard.getPrimaryClip()
    val ret = mutableListOf<String>()
    if (clip != null)
    {
        for (i in 0 until clip.itemCount)
        {
            val item = clip?.getItemAt(i)
            item?.text?.toString()?.let { ret.add(it)}
        }
    }
    return ret
}

/** Sets the clipboard, potentially asynchronously. */
actual fun setTextClipboard(msg: String)
{
    val c = (appContext() as? android.content.Context) ?: return
    var clipboard = c.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    var clip = ClipData.newPlainText("text", msg)
    clipboard.setPrimaryClip(clip)
}

/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
actual fun isUiThread(): Boolean
{
    val tname = Thread.currentThread().name
    return (tname == "main")
}

actual fun displayAlert(alert: Alert)
{
    val act = currentActivity
    if (act != null)
    {
        act.displayAlert(alert)
    }

}

fun AlertLevel.color(): Color
{
    return when
    {
        level >= AlertLevel.EXCEPTION.level -> colorError
        level >= AlertLevel.ERROR.level -> colorError
        level >= AlertLevel.WARN.level -> colorWarning
        level >= AlertLevel.NOTICE.level -> colorNotice
        else -> Color.White
    }
}


val androidPlatformCharacteristics = PlatformCharacteristics(
  target = KotlinTarget.Android,
  hasQrScanner = true,
  hasGallery = true,
  usesMouse = false,
  hasAlert = true,
  hasBack = true,
  hasNativeTitleBar = true,
  spaceConstrained = true,
  landscape = false,
  hasShare = true,
  supportsBackgroundSync = true,
  bottomSystemBarOverlap = if (android.os.Build.VERSION.SDK_INT < 33) 0.dp else 10.dp, // This is overwritten when the view is created and we can check the insets
  hasLinkToNiftyArt = true,
  hasDoneButton = false
)

actual fun platform(): PlatformCharacteristics = androidPlatformCharacteristics

actual fun platformShare(textToShare: String)
{
    val act = currentActivity as? ComposeActivity
    if (act != null)
    {
        act.share(textToShare)
    }
}


/** Access a file from the resource area */
fun readResourceFile(filename: String): InputStream
{
    val nothing = Objectify<Int>(0)

    val loadTries = listOf<()->InputStream> (
      { nothing::class.java.getClassLoader().getResourceAsStream(filename) },
      { File(filename).inputStream() },
    )
    for (i in loadTries)
    {
        try
        {
            val ins = i()
            return ins
        }
        catch (e:Exception)
        {}
    }
    throw FileNotFoundException()
}

actual fun getResourceFile(name: String): BufferedSource
{
    val androidContext = (appContext() as android.content.Context)!!
    var id = androidContext.resources.getIdentifier(name, "raw", androidContext.packageName)
    if (id != 0)
    {
        val f = androidContext.resources.openRawResource(id)
        return f.source().buffer()
    }
    else
    {
        return readResourceFile(name).source().buffer()  // throws FileNotFoundException(name
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable actual fun isImeVisible(): Boolean
{
    val act = currentActivity as? ComposeActivity
    if (act != null) return act.isKeyboardShown()
    return WindowInsets.isImeVisible  // Does not seem to work, but is the "compose" API
}

// Note that changing AndroidManifest.xml android:windowSoftInputMode="stateHidden|adjustPan" to something else will break this
@Composable actual fun getImeHeight(): Dp
{
    val view = LocalView.current
    val insets = ViewCompat.getRootWindowInsets(view)
    var imeHeight = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
    val systemnavbar = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    if (android.os.Build.VERSION.SDK_INT < 35) imeHeight -= systemnavbar
    val density = LocalDensity.current.density
    val height = imeHeight / density
    return if (height < 0)
        0.dp
    else
        (imeHeight / density).dp
}

actual fun openUrl(url: String) {
    val appContext:Context? = wallyAndroidApp as? Context //  currentActivity?.applicationContext
    if (appContext!=null)
    {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        appContext.startActivity(intent)
    }
}

// Not adding Android yet because it requires Google Play services
// See reference implementation/library here: https://github.com/jQrgen/kmp-app-review
actual fun getReviewManager(): InAppReviewDelegate? = null

actual fun requestInAppReview() {}
