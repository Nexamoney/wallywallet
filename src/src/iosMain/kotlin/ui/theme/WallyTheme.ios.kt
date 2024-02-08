package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.toNSData
import io.ktor.http.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import okio.Path.Companion.toPath
import org.nexa.libnexakotlin.GetLog
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewControllerDelegateProtocol
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRange
import platform.CoreMedia.CMTimeRangeMake
import platform.UIKit.*

private val LogIt = GetLog("wally.theme.ios")

@Composable
actual fun WallyTheme(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  content: @Composable () -> Unit
) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette,
      typography = Typography(),
      shapes = Shapes(),
      content = content
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable actual fun MpMediaView(mediaData: ByteArray?, mediaUri: String?, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit):Boolean
{
    println("MpMediaView($mediaData, $mediaUri, $wrapper)")

    val mu = mediaUri
    if (mu == null) return false

    //val url = Url(mu)
    val name = mu.lowercase()

    if (name.endsWith(".svg", true))
    {

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
        // var mediaBytes by remember { mutableStateOf(mediaData) }
        var im by remember { mutableStateOf<UIImage?>(null) }
        // val tmpname = name?.takeLast(20) ?: ""
        // var message by remember { mutableStateOf(tmpname) }

        im = mediaData?.usePinned { pinned ->
                    val b = NSData.create(bytes = pinned.addressOf(0), length = pinned.get().size.toULong())
                    UIImage(b)
                }
          ?: run {
              val path = mediaUri.toPath()
              // If mediaUri doesn't exist, program will crash
              if (!FileSystem.SYSTEM.exists(path)) UIImage()
              else UIImage(mediaUri)
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
                UIKitView(
                      modifier = m,
                      factory = {
                          /*
                          val textField = object : UITextField(CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                              @ObjCAction
                              fun editingChanged() {
                                  message = text ?: ""
                              }
                          }
                          textField.font = UIFont.systemFontOfSize(6.0)
                          if (tim != null) textField.background = tim
                          textField
                          */

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
    }
    else if (name.endsWith(".mp4", true) ||
      name.endsWith(".webm", true) ||
      name.endsWith(".3gp", true) ||
      name.endsWith(".mkv", true))
    {
        VideoView(mediaUri, wrapper)
    }

    return false
}

@OptIn(ExperimentalForeignApi::class)
@Composable fun VideoView(url: String, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit)
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
