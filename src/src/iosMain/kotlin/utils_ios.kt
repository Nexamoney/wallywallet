package info.bitcoinunlimited.www.wally

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.BufferedSource
import okio.FileNotFoundException
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.skia.*
import org.nexa.libnexakotlin.GetLog
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.*
import wpw.src.generated.resources.Res
import kotlin.math.pow
import org.nexa.libnexakotlin.millinow

private val LogIt = GetLog("BU.wally.utils_ios")


actual fun notify(title: String?, content: String, onlyIfBackground: Boolean): Int
{
    return -1
}
/* Remove the notification returned by notify */
actual fun denotify(id: Int): Boolean
{
    return true
}

// on android this fails with couldn't find "libskiko-android-arm64.so", see https://github.com/JetBrains/skiko/issues/531
fun scaleUsingSurface(image: Image, width: Int, height: Int): Image
{
    val surface = Surface.makeRasterN32Premul(width, height)
    val canvas = surface.canvas
    val paint = Paint()
    canvas.drawImageRect(image, Rect(0f, 0f, width.toFloat(), height.toFloat()), paint)
    val im = surface.makeImageSnapshot()
    return im
}

actual fun makeImageBitmap(imageBytes: ByteArray, width: Int, height: Int,scaleMode: ScaleMode): ImageBitmap?
{
    try
    {
        val imIn = Image.makeFromEncoded(imageBytes)
        var newWidth = width
        var newHeight = height
        if ((scaleMode != ScaleMode.DISTORT) && (imIn.height != 0))
        {
            var ratio = imIn.width.toFloat() / imIn.height.toFloat()

            if (scaleMode == ScaleMode.INSIDE)
            {
                if (ratio < 1.0) newWidth = (height * ratio).toInt()
                else newHeight = (width / ratio).toInt()
            }
            if (scaleMode == ScaleMode.COVER)
            {
                if (ratio < 1.0) newHeight = (width / ratio).toInt()
                else newWidth = (width * ratio).toInt()
            }
        }
        val im = scaleUsingSurface(imIn, newWidth, newHeight)
        return im.toComposeImageBitmap()
    }
    catch (e: IllegalArgumentException)  // imageBytes can't be decoded
    {
        return null
    }
}

// the platform release did not exist so no possibility of old accounts
actual fun convertOldAccounts(): Boolean
{
    return false
}
actual fun applicationState(): ApplicationState
{
    val state = UIApplication.sharedApplication.applicationState
    val cvt = if (state == UIApplicationState.UIApplicationStateBackground) ApplicationState.RunState.BACKGROUND
    else if (state == UIApplicationState.UIApplicationStateActive) ApplicationState.RunState.ACTIVE
    else ApplicationState.RunState.INACTIVE
    return ApplicationState(cvt)
}

actual fun platformRam():Long?
{
    val mem = NSProcessInfo.processInfo.physicalMemory
    return mem.toLong()
}

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData
{
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray
{
    val len = length.toInt()
    val ba = ByteArray(len)
    ba.usePinned {
        this.getBytes(it.addressOf(0))
    }
    return ba
}


@OptIn(ExperimentalForeignApi::class)
actual fun inflateRfc1951(compressedBytes: ByteArray, expectedfinalSize: Long): ByteArray
{
    val nsd = compressedBytes.toNSData()
    val dec = nsd.decompressedDataUsingAlgorithm(NSDataCompressionAlgorithmZlib, null)
    return dec?.toByteArray() ?: byteArrayOf()
}

actual fun stackTraceWithout(skipFirst: MutableSet<String>, ignoreFiles: MutableSet<String>?): String
{
    skipFirst.add("stackTraceWithout")
    skipFirst.add("stackTraceWithout\$default")
    val igf = ignoreFiles ?: defaultIgnoreFiles
    val st = Exception().stackTraceToString()
    //while (st.isNotEmpty() && skipFirst.contains(st.first().methodName)) st.removeAt(0)
    //st.removeAll { igf.contains(it.fileName) }
    return st //.toTypedArray()
}

actual fun GetHttpClient(timeoutInMs: Number): HttpClient = HttpClient(CIO)
{
    install(HttpTimeout) { requestTimeoutMillis = timeoutInMs.toLong() }
}

/** Get the clipboard.  Platforms that have a clipboard history should return that history, with the primary clip in index 0 */
actual fun getTextClipboard(): List<String>
{
    val clips = UIPasteboard.generalPasteboard.strings
    if (clips == null) return emptyList()
    return clips.map { it.toString() }
}

/** Sets the clipboard, potentially asynchronously. */
actual fun setTextClipboard(msg: String)
{
    UIPasteboard.generalPasteboard.string = msg
}


/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
actual fun isUiThread(): Boolean
{
    return NSThread.isMainThread
}

/** Get a QR code from an image (request file from user), and call the scanDone function when finished.  Returns false if QR scanning is not available */
actual fun ImageQrCode(imageParsed: (String?)->Unit): Boolean
{
    return false
}

/** No banner in iOS so no native way to display alerts */
actual fun displayAlert(alert: Alert) {}

/** Actually share this text using the platform's share functionality */
actual fun platformShare(textToShare: String)
{
    val activityController = UIActivityViewController(
      activityItems = listOf(textToShare),
      applicationActivities = null,
    )
    val window = UIApplication.sharedApplication.windows().first() as UIWindow?
    activityController.popoverPresentationController()?.sourceView =
      window
    window?.rootViewController?.presentViewController(
      activityController as UIViewController,
      animated = true,
      completion = null,
    )

}

val iosPlatformCharacteristics = PlatformCharacteristics(
  target = KotlinTarget.iOS,
  hasQrScanner = true,
  hasGallery = false,
  usesMouse = false,
  hasAlert = false,
  hasBack = false,
  hasNativeTitleBar = false,
  spaceConstrained = true,
  landscape = false,
  hasShare = true,
  supportsBackgroundSync = true,
  bottomSystemBarOverlap = 0.dp,
  hasLinkToNiftyArt = false,
  hasDoneButton = true
  )
actual fun platform(): PlatformCharacteristics = iosPlatformCharacteristics

actual fun platformNotification(message:String, title: String?, onclickUrl:String?, severity: AlertLevel)
{
    // TODO issue an ios notification
}

@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
actual fun getResourceFile(name: String): BufferedSource
{
    /* uses the old URLForResource APIs
    val dotSpot = name.lastIndexOf('.')
    val ext = if (dotSpot == -1) null else name.takeLast(name.length-dotSpot-1)
    val base = if (dotSpot == -1) name else name.take(dotSpot)

    println("$base $ext")
    val dirSpot = base.lastIndexOf('/')
    val fname = if (dirSpot == -1) base else base.takeLast(base.length-dirSpot-1)
    val subdir = if (dirSpot == -1) null else base.take(dirSpot)

    println("RESOURCE: $subdir $fname  $ext")

    var url = NSBundle.mainBundle.URLForResource(base, ext, subdir)
    println("$url ${url?.absoluteURL}")
    if (url == null) url = NSBundle.mainBundle.URLForResource(base, ext)
    if ((url == null)&&(subdir != null))
    {
        //var tmp = NSBundle.bundleWithURL(NSURL("/icons"))
        //println("bwURL: $tmp")
        var tmp = NSBundle.bundleWithPath(subdir)
        println("bwPath: $tmp")
        if (tmp!=null)
            url = tmp.URLForResource(base, ext)
    }
    println("$url ${url?.absoluteURL}")

     */

    val ba = try
        {
            runBlocking { Res.readBytes(name) }
        }
        catch(e: Exception)
        {
            val dirSpot = name.lastIndexOf('/')
            val fname = if (dirSpot == -1) name else name.takeLast(name.length-dirSpot-1)
            val subdir = if (dirSpot == -1) null else name.take(dirSpot)
            try
            {
                runBlocking { Res.readBytes(fname) }
            }
            catch (e: Exception)
            {
                throw FileNotFoundException(name)
            }
        }
    val buf = Buffer()
    buf.write(ba)
    return buf
}

@Composable
actual fun isImeVisible(): Boolean
{
    return false
}

@Composable actual fun getImeHeight(): Dp
{
    // This isn't needed right now because we don't put the number bar above the IME
    return 0.dp
}

actual fun openUrl(url: String) {
    val nsUrl = NSURL(string = url)
    if (nsUrl != null) {
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>()) { success ->
            if (!success) {
                println("Failed to open URL: $url")
            }
        }
    } else {
        println("Invalid URL: $url")
    }
}

// from https://slack-chats.kotlinlang.org/t/12086405/hi-all-how-to-convert-ios-uiimage-to-compose-imagebitmap-in-
@OptIn(ExperimentalForeignApi::class)
internal fun UIImage.toSkiaImage(): Image?
{
    val imageRef = CGImageCreateCopyWithColorSpace(this.CGImage, CGColorSpaceCreateDeviceRGB()) ?: return null

    val width = CGImageGetWidth(imageRef).toInt()
    val height = CGImageGetHeight(imageRef).toInt()

    val bytesPerRow = CGImageGetBytesPerRow(imageRef)
    val data = CGDataProviderCopyData(CGImageGetDataProvider(imageRef))
    val bytePointer = CFDataGetBytePtr(data)
    val length = CFDataGetLength(data)
    val alphaInfo = CGImageGetAlphaInfo(imageRef)

    val alphaType = when (alphaInfo) {
        CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst, CGImageAlphaInfo.kCGImageAlphaPremultipliedLast -> ColorAlphaType.PREMUL
        CGImageAlphaInfo.kCGImageAlphaFirst, CGImageAlphaInfo.kCGImageAlphaLast -> ColorAlphaType.UNPREMUL
        CGImageAlphaInfo.kCGImageAlphaNone, CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst, CGImageAlphaInfo.kCGImageAlphaNoneSkipLast -> ColorAlphaType.OPAQUE
        else -> ColorAlphaType.UNKNOWN
    }

    val byteArray = ByteArray(length.toInt()) { index ->
        bytePointer!![index].toByte()
    }
    CFRelease(data)
    CFRelease(imageRef)

    return Image.makeRaster(
      imageInfo = ImageInfo(width = width, height = height, colorType = ColorType.RGBA_8888, alphaType = alphaType),
      bytes = byteArray,
      rowBytes = bytesPerRow.toInt(),
    )
}

@OptIn(ExperimentalForeignApi::class)
fun UIImage.toImageBitmap(): ImageBitmap {
    return this.toSkiaImage()!!.toComposeImageBitmap()
    //todo <https://github.com/touchlab/DroidconKotlin/blob/fe5b7e8bb6cdf5d00eeaf7ee13f1f96b71857e8f/shared-ui/src/iosMain/kotlin/co/touchlab/droidcon/ui/util/ToSkiaImage.kt>
    /*
    val pngRepresentation = UIImagePNGRepresentation(this)!!
    val byteArray = ByteArray(pngRepresentation.length.toInt()).apply {
        usePinned {
            memcpy(it.addressOf(0), pngRepresentation.bytes, pngRepresentation.length)
        }
    }
    return org.jetbrains.skia.Image.makeFromEncoded(byteArray).toComposeImageBitmap()

     */
}

internal fun systemVersionMoreOrEqualThan(version: String): Boolean {
    val systemVersion = UIDevice.currentDevice.systemVersion
    return version.versionToNumber() >= systemVersion.versionToNumber()
}

private fun String.versionToNumber() = split(".")
  .map { it.toInt() }
  .take(3)
  .run {
      val appendedList = toMutableList()
      repeat(3 - size) { appendedList.add(0) }
      println(appendedList)
      appendedList.foldIndexed(0) { index, acc, current ->
          acc + current * 1000.0.pow(2 - index).toInt()
      }
  }

class AppStoreInAppReviewInitParams(val appStoreId: String)

class AppStoreInAppReviewManager(private val params: AppStoreInAppReviewInitParams) : InAppReviewDelegate {

    // In iOS the app can ask for a review maximally three times a year
    override fun requestInAppReview(): Flow<ReviewCode> = flow {
        if (systemVersionMoreOrEqualThan("14.0")) {
            val scene = UIApplication.sharedApplication.connectedScenes.map { it as UIWindowScene }
              .first { it.activationState == UISceneActivationStateForegroundActive }
            SKStoreReviewController.requestReviewInScene(scene)
        } else {
            SKStoreReviewController.requestReview()
        }
        emit(ReviewCode.NO_ERROR)
    }

    override fun requestInMarketReview() = flow {
        val url = NSURL(string = "https://apps.apple.com/app/${params.appStoreId}?action=write-review")
        UIApplication.sharedApplication.openURL(url)
        emit(ReviewCode.NO_ERROR)
    }
}

actual fun getReviewManager(): InAppReviewDelegate? = AppStoreInAppReviewManager(AppStoreInAppReviewInitParams("id6469619075"))

// Requests in-app review and waits one month to ask again if no review is given.
actual fun requestInAppReview()
{
    val now = millinow()/1000
    val lastReviewRequest = wallyApp?.preferenceDB?.getString(LAST_REVIEW_TIMESTAMP, "0")?.toLong() ?: now
    val oneWeekInSeconds = 30 * 24 * 60 * 60

    if ((now - lastReviewRequest) >= oneWeekInSeconds)
    {
        val scene = UIApplication.sharedApplication.connectedScenes.map { it as UIWindowScene }
          .first { it.activationState == UISceneActivationStateForegroundActive }
        SKStoreReviewController.requestReviewInScene(scene)
        wallyApp?.run { preferenceDB.edit().putString(LAST_REVIEW_TIMESTAMP, now.toString()).commit() }
    }
}
