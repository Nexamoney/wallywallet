package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import io.ktor.http.*
import org.nexa.libnexakotlin.UnimplementedException
import java.io.ByteArrayInputStream
import java.io.FileInputStream

import javax.imageio.ImageIO
//import javafx.scene.media.Media
//import javafx.scene.media.MediaPlayer
//import javafx.scene.media.MediaView


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


@Composable actual fun MpMediaView(mediaData: ByteArray?, mediaUri: String?, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit): Boolean
{
    val bytes = mediaData

    val mu = mediaUri
    if (mu == null) return false

    val url = Url(mu)
    val name = mu.lowercase()

    if (name.endsWith(".svg", true))
    {
        /*
        val svg = if (bytes == null)
        {
            if (url.protocol == null)
            {
                SVG.getFromInputStream(FileInputStream(name))
            }
            else throw UnimplementedException("non-local data in NFT")
        }
        else
        {
            SVG.getFromInputStream(ByteArrayInputStream(bytes))
        }


        // This doesn't make sense because SVG is generally scalable and might be given to us with crazy dimensions
        // val bitmap = Bitmap.createBitmap(svg.documentWidth.toInt(), svg.documentHeight.toInt(), Bitmap.Config.ARGB_8888)

        val bitmap = Bitmap.createBitmap((1000*svg.documentAspectRatio).toInt(), 1000, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        svg.renderToCanvas(canvas)
        val im: ImageBitmap = bitmap.asImageBitmap()
        wrapper(MediaInfo(im.width,im.height,false)) { mod ->
            val m = mod ?: Modifier.fillMaxSize().background(Color.Transparent)
            Image(im, null, m, contentScale = ContentScale.Fit)
        }

         */
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
        val bitmap = if (bytes == null)
        {
            val URL = url.toURI().toURL()
            if ((URL.protocol == null)||(URL.protocol == "file"))
            {
                ImageIO.read(url.toURI().toURL())
            }
            else throw UnimplementedException("non-local data in NFT")
        }
        else
        {
            ImageIO.read(ByteArrayInputStream(bytes))
        }

        val im: ImageBitmap = bitmap.toComposeImageBitmap()
        wrapper(MediaInfo(im.width,im.height,false)) { mod ->
            val m = mod ?: Modifier.fillMaxSize().background(Color.Transparent)
            Image(im, null, m, contentScale = ContentScale.Fit)
        }
    }
    else
    {
        val mi = MediaInfo(200, 200, false, false)
        wrapper(mi) { mod ->
            val m = mod ?: Modifier.fillMaxSize().background(Color.Transparent)
            ResImageView("icons/media_not_supported.xml", modifier = m)
        }
        return false
    }

        /*
        val context = LocalContext.current

        if (name.endsWith(".mp4", true) ||
          name.endsWith(".webm", true) ||
          name.endsWith(".3gp", true) ||
          name.endsWith(".mkv", true))
        {
            LogIt.info("Video URI: ${url}")

            // val tmp = ProgressiveMediaSource.Factory(ByteArrayDataSourceFactory(ByteArrayDataSource(mediaData!!, Url(mediaUri))))
            val mediaItem = MediaItem.Builder().setUri(url.toString()).build()

            val exoPlayer = remember(context, mediaItem) {
                ExoPlayer.Builder(context)
                  // .setMediaSourceFactory(tmp)
                  .build()
                  .also { exoPlayer ->
                      exoPlayer.setMediaItem(mediaItem)
                      exoPlayer.prepare()
                      exoPlayer.playWhenReady = true
                      exoPlayer.repeatMode = REPEAT_MODE_ALL
                  }
            }

            val fmt = exoPlayer.videoFormat
            val mi = if (fmt != null)
            {
                if (fmt.rotationDegrees == 90 || fmt.rotationDegrees == 270)
                {
                    MediaInfo(fmt.height, fmt.width, true)
                }
                else MediaInfo(fmt.width, fmt.height, true)
            }
            else MediaInfo(200,200, true)  // No idea so pick something not crazy

            wrapper(mi) { mod ->
                val m = mod ?: Modifier.fillMaxSize().background(Color.Transparent)
                DisposableEffect(
                  AndroidView(factory = {
                      PlayerView(context).apply {
                          player = exoPlayer
                          useController = true
                          // artworkDisplayMode = ARTWORK_DISPLAY_MODE_FIT
                          controllerAutoShow = true
                      }
                  },
                    modifier = m)
                ) {
                    onDispose { exoPlayer.release() }
                }
            }
        }
        else
        {
            throw UnimplementedException("unsupported video format $name")
        }

         */
    return true
}