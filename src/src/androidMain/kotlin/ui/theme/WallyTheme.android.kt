package info.bitcoinunlimited.www.wally.ui.theme


import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.VideoView
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
import androidx.media3.common.Player.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerView.ARTWORK_DISPLAY_MODE_FIT
import com.caverock.androidsvg.SVG
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import io.ktor.http.*
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.UnimplementedException
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.min

private val LogIt = GetLog("wally.theme.android")

@OptIn(UnstableApi::class)
class ByteArrayDataSourceFactory(val bads: ByteArrayDataSource):DataSource.Factory
{
    override fun createDataSource(): DataSource
    {
        return bads
    }
}

// TODO this ByteArrayDataSource is broken somehow (media does not play)
@OptIn(UnstableApi::class)
class ByteArrayDataSource(val data:ByteArray, val url: Url): DataSource
{
    var curPos = 0
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int
    {
        if (curPos >= data.size) return C.RESULT_END_OF_INPUT
        val end = min(data.size - 1, curPos + length)
        try
        {
            data.copyInto(buffer, offset, curPos, end)
            val amt = end - curPos
            curPos += amt
            return amt
        }
        catch(e:ArrayIndexOutOfBoundsException)
        {
            data.copyInto(buffer, offset, curPos, end)
            throw e
        }
    }

    override fun addTransferListener(transferListener: TransferListener)
    {
    }

    override fun open(dataSpec: DataSpec): Long
    {
        if (dataSpec.position > data.size) throw(DataSourceException(ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE))
        curPos = dataSpec.position.toInt()
        return (data.size - curPos).toLong()
    }

    override fun getUri(): Uri?
    {
        return Uri.parse(url.toString())
    }

    override fun close()
    {
        curPos = 0

    }
}


@OptIn(UnstableApi::class) @Composable actual fun MpMediaView(mediaData: ByteArray?, mediaUri: String?, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit): Boolean
{
    val bytes = mediaData

    val mu = mediaUri
    if (mu == null) return false

    val url = Url(mu)
    val name = mu.lowercase()

    if (name.endsWith(".svg", true))
    {
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

        val bitmap = Bitmap.createBitmap((1000 * svg.documentAspectRatio).toInt(), 1000, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        svg.renderToCanvas(canvas)
        val im: ImageBitmap = bitmap.asImageBitmap()
        wrapper(MediaInfo(im.width, im.height, false)) { mod ->
            val m = mod ?: Modifier.fillMaxSize().background(Color.Transparent)
            Image(im, null, m, contentScale = ContentScale.Fit)
        }
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
            if (url.protocol == null)
            {
                BitmapFactory.decodeFile(name)
            }
            else throw UnimplementedException("non-local data in NFT")
        }
        else
        {
            BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
        }

        val im: ImageBitmap = bitmap.asImageBitmap()
        wrapper(MediaInfo(im.width, im.height, false)) { mod ->
            val m = mod ?: Modifier.fillMaxSize().background(Color.Transparent)
            Image(im, null, m, contentScale = ContentScale.Fit)
        }
    }
    else if (name.endsWith(".mp4", true) ||
      name.endsWith(".webm", true) ||
      name.endsWith(".3gp", true) ||
      name.endsWith(".mkv", true))
    {

        LogIt.info("Video URI: ${url}")
        val context = LocalContext.current
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
        else MediaInfo(200, 200, true)  // No idea so pick something not crazy

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
    else  // Media is not displayable
    {
        val mi = MediaInfo(200, 200, false, false)
        wrapper(mi) { mod ->
            val m = mod ?: Modifier.fillMaxSize().background(Color.Transparent)
            ResImageView("icons/media_not_supported.xml", modifier = m)
        }
        return false
    }
    return true
}


@Composable
actual fun WallyTheme(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color is only supported on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorPalette
        else -> LightColorPalette
    }

        // If not in Android Studio's preview then update also the system bars
        val view = LocalView.current
        if (!view.isInEditMode)
        {
            val activity = view.context as? Activity
            SideEffect {
                activity?.window?.apply {
                    statusBarColor = colorScheme.primary.toArgb()
                    WindowCompat
                      .getInsetsController(this, view).apply {
                          isAppearanceLightStatusBars = darkTheme
                          isAppearanceLightNavigationBars = darkTheme
                      }
                }
            }
        }


    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = Shapes,
      content = content
    )
}
