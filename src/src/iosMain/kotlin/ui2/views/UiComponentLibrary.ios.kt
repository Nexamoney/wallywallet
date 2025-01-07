package info.bitcoinunlimited.www.wally.ui2.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.layout.ContentScale
import info.bitcoinunlimited.www.wally.getResourceFile
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.viewmodels.MusicView
import info.bitcoinunlimited.www.wally.ui2.isSoftKeyboardShowing
import info.bitcoinunlimited.www.wally.ui2.theme.BaseBkg
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.UnimplementedException
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode

private val LogIt = GetLog("wally.componentlibrary.ios")

/** Provide a view for this piece of media.  If mediaData is non-null, use it as the media file contents.
 * However, still provide mediaUri (or at least dummy.ext) so that we can determine the media type from the file name within the Uri.
 * This composable is "unique" in that rather than providing a callback for contents, it provides a callback that allows you to wrap the final
 * media view.  This callback includes information about the piece of media being shown, so that you can create a custom wrapper based on the media.
 *
 * Your custom wrapper MUST call the passed composable to actually render the media.  You may pass a custom modifier.  If you pass null,
 * Modifier.fillMaxSize().background(Color.Transparent) is used.
 */

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MpMediaView(mediaImage: ImageBitmap?, mediaData: ByteArray?, mediaUri: String?, autoplay: Boolean, hideMusicView: Boolean, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit):Boolean
{
    // LogIt.info( "MpMediaView(${mediaData?.size} bytes, $mediaUri)")
    val mu = mediaUri
    if (mu == null) return false
    val name = mu.lowercase()

    if (mediaImage != null)
    {
        wrapper(MediaInfo(mediaImage.width, mediaImage.height, false)) { mod ->
            val m = mod ?: Modifier
              .fillMaxSize()
              .background(Color.Transparent)
            Image(mediaImage, null, m, contentScale = ContentScale.Fit)
        }
        return true
    }
    else if (name.endsWith(".svg", true))
    {
        // TODO iOS SVG rendering
        return false
    }
    else if (name.endsWith(".jpg", true) ||
      name.endsWith(".jpeg", true) ||
      name.endsWith(".png", true) ||
      name.endsWith(".webp", true) ||
      name.endsWith(".gif", true) ||
      name.endsWith(".heic", true) ||
      name.endsWith(".heif", true)
    )
    {
        var im by remember { mutableStateOf<UIImage?>(null) }
        im = mediaData?.usePinned { pinned ->
            val b = NSData.create(bytes = pinned.addressOf(0), length = pinned.get().size.toULong())
            try
            {
                UIImage(b)
            }
            catch (e: NullPointerException)
            {
                // Happens if the data cannot be parsed into an image
                return false
            }
        } ?: run {
            val tmp = resolveLocalFilename(mu)
            if (tmp!=null) UIImage(tmp.toString()) else UIImage()
        }

        val tim = im
        if (tim != null)
        {
            val (width, height) = tim.size.useContents {
                val w = this.width.toInt()
                val h = this.height.toInt()
                if (h == 0) Pair(100,100)  // if image is bad, just return something that will work
                else Pair(this.width.toInt(), this.height.toInt())
            }

            wrapper(MediaInfo(width, height, false, true))
            {
                val m = it ?: Modifier.fillMaxSize().background(Color.Transparent)
                // LogIt.info("MpMediaView wrapper render")
                UIKitView(modifier = m,
                  factory = {
                      val imField = UIImageView(CGRectMake(0.0, 0.0, 0.0, 0.0))
                      imField.image = tim
                      imField.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                      imField
                  },
                  update = {
                      it.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                      it.image = tim
                  },
                  onRelease = {
                      im = null
                  }
                )
            }
        }
        return true
    }
    else if (name.endsWith(".mp4", true) ||
      name.endsWith(".webm", true) ||
      name.endsWith(".3gp", true) ||
      name.endsWith(".mkv", true))
    {
        val tmp = resolveLocalFilename(mu)
        if (tmp!=null)
        {
            val furl = "file://" + tmp.toString()
            //val cutname = "file:" + if (mu.startsWith("file://")) mu.drop(7) else mu
            LogIt.info("VideoView $furl")
            VideoView(furl, autoplay, wrapper)
            return true
        }
        else return false
    }
    else if (name.endsWith(".alac", true) ||
      name.endsWith(".flac", true) ||
      name.endsWith(".wma", true) ||
      name.endsWith(".ogg", true))
    {
        wrapper(MediaInfo(200, 200, true, true)){
            ResImageView("icons/media_not_supported.xml", modifier = Modifier.background(BaseBkg), null)
        }
    }
    // Supported audio file formats for iOS:
    // https://developer.apple.com/library/archive/documentation/MusicAudio/Conceptual/CoreAudioOverview/SupportedAudioFormatsMacOSX/SupportedAudioFormatsMacOSX.html
    else if (name.endsWith(".aac", true) ||
      name.endsWith(".adts", true) ||
      name.endsWith(".ac3", true) ||
      name.endsWith(".aif", true) ||
      name.endsWith(".aiff", true) ||
      name.endsWith(".aifc", true) ||
      name.endsWith(".caf", true) ||
      name.endsWith(".mp4", true) ||
      name.endsWith(".m4a", true) ||
      name.endsWith(".snd", true) ||
      name.endsWith(".au", true) ||
      name.endsWith(".sd2", true) ||
      name.endsWith(".mp3", true) ||
      name.endsWith(".wav", true))
    {
        val tmp = resolveLocalFilename(mu)
        if (tmp!=null && !hideMusicView)
        {
            val furl = "file://" + tmp.toString()
            MusicView(furl, wrapper)
        }
        return true
    }
    return false
}

@OptIn(ExperimentalForeignApi::class)
actual fun MpIcon(mediaUri: String, widthPx: Int, heightPx: Int): ImageBitmap
{
    val name = mediaUri.lowercase()

    val bytes = try
    {
        getResourceFile(mediaUri).readByteArray()
    }
    catch (e: Exception)
    {
        null
    }

    //if (name.endsWith(".svg", true)) // Note SVG appears to only be supported via a resource file.
    if (
      name.endsWith(".jpg", true) ||
      name.endsWith(".jpeg", true) ||
      name.endsWith(".png", true) ||
      name.endsWith(".webp", true) ||
      name.endsWith(".gif", true) ||
      name.endsWith(".heic", true) ||
      name.endsWith(".heif", true)
    )
    {
        // LogIt.info("Media icon $name")

        val im = bytes?.usePinned { pinned ->
            val b = NSData.create(bytes = pinned.addressOf(0), length = pinned.get().size.toULong())
            UIImage(b)
        }
          ?: run {
              val path = mediaUri.toPath()
              // If mediaUri doesn't exist, program will crash
              if (!FileSystem.SYSTEM.exists(path)) UIImage()
              else UIImage(mediaUri)
          }

        return im.toImageBitmap()
    }
    throw UnimplementedException("other icon formats")
}

fun resolveLocalFilename(filename: String): Path?
{
    var path = filename.toPath()
    if (!FileSystem.SYSTEM.exists(path))
    {
        // LogIt.info("MpMediaView File does not exist $path ($mu)")
        val cutname = if (filename.startsWith("file://")) filename.drop(7) else filename
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        for (d in dirs)
        {
            val dsa = d.toString().toPath() / cutname
            // LogIt.info("MpMediaView trying $ds $dsa")
            if (FileSystem.SYSTEM.exists(dsa))
            {
                // LogIt.info("MpMediaView Found at $dsa")
                return dsa
            }
            else
            {
                // LogIt.info("MpMediaView File does not exist $path ($mu)")
            }
        }
        return null
    }
    else return path
}

@OptIn(ExperimentalForeignApi::class)
@Composable fun VideoView(url: String, autoplay: Boolean = false, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit)
{
    var avPlayer by remember { mutableStateOf<AVPlayerItem?>(null) }
    var avPlayerViewController by remember { mutableStateOf<AVPlayerViewController?>(null) }
    var uiView by remember { mutableStateOf<UIView?>(null) }
    var looper by remember { mutableStateOf<AVPlayerLooper?>(null) }
    var width by remember { mutableStateOf(200) }
    var height by remember { mutableStateOf(200) }

    LaunchedEffect(Unit) {
        avPlayer = withContext(Dispatchers.Main) {
            val tmp = AVPlayerItem(uRL = NSURL(string = url))
            tmp
        }
        avPlayerViewController = withContext(Dispatchers.Main) {
            val tmp = AVPlayerViewController().apply {
                val qp = AVQueuePlayer(avPlayer)
                player = qp
                looper = AVPlayerLooper(qp, avPlayer!!, CMTimeRangeMake(CMTimeMake(0L,0), CMTimeMake(1000000000L,0)))
                showsPlaybackControls = false // true
                entersFullScreenWhenPlaybackBegins = false
                exitsFullScreenWhenPlaybackEnds = false
                videoGravity = AVLayerVideoGravityResize
                contentOverlayView?.autoresizesSubviews = true
                if(autoplay)
                    player?.play()  // get going
            }
            tmp
        }
        uiView = withContext(Dispatchers.Main) {
            avPlayerViewController?.view
        }
    }

    if (avPlayer != null && uiView != null)
    {
        wrapper(MediaInfo(width, height, true, true))
        {
            val m = it ?: Modifier.fillMaxSize()
            UIKitView(
              modifier = m,
              factory = {
                  uiView!!
              },
              interactive = true,
              onRelease = {
                  avPlayerViewController?.player = null
                  avPlayer = null
                  avPlayerViewController = null
                  looper = null
                  uiView = null
              }
            )
        }
    }
}

actual fun NativeSplash(start: Boolean): Boolean
{
    return false
}

actual fun NativeTitle(title: String)
{
}
actual fun UxInTextEntry(boolean: Boolean)
{
    isSoftKeyboardShowing.value = boolean
}