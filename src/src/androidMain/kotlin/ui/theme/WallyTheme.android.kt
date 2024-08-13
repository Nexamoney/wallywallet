package info.bitcoinunlimited.www.wally.ui.theme


import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.media3.ui.PlayerView
import com.caverock.androidsvg.SVG
import info.bitcoinunlimited.www.wally.currentActivity
import info.bitcoinunlimited.www.wally.getResourceFile
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import io.ktor.http.*
import org.nexa.libnexakotlin.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.min

private val LogIt = GetLog("wally.theme.android")

actual fun UxInTextEntry(boolean: Boolean)
{
    // nothing to do; we can learn about the soft keyboard from the os
}

actual fun NativeSplash(start: Boolean): Boolean
{
    return false
    //currentActivity!!.splash(start)
    //return true
}

actual fun NativeTitle(title: String)
{
    val ca = currentActivity
    if (ca != null)
    {
        ca.setTitle(title)
    }
}

@OptIn(UnstableApi::class)
class ByteArrayDataSourceFactory(val bads: ByteArrayDataSource):DataSource.Factory
{
    override fun createDataSource(): DataSource
    {
        return bads
    }
}

class FileIdentifier(protected val bytes: ByteArray, protected val offset: Int)
{
    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (other is FileIdentifier && this.bytes contentEquals other.bytes) return true
        if (other is ByteArray && this.bytes contentEquals other.sliceArray(IntRange(offset, offset+bytes.size-1))) return true
        return false
    }
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = bytes.contentToString()
}
private fun maj(offset:Int, vararg bytes: UByte) = FileIdentifier(bytes.asByteArray(), offset)

val fileMagicNums:Map<FileIdentifier, String> = mapOf<FileIdentifier, String>(
  maj(0,0x42U,0x4dU) to ".bmp",
  maj(0, 0x47U,0x49U,0x46U,0x38U) to ".gif",
  maj(0, 0xffU, 0xd8U, 0xffU, 0xe0U) to ".jpg",
  maj(0, 0x89U, 0x50U, 0x4eU, 0x47U) to ".png",
  maj(0, 0x52U, 0x49U, 0x46U, 0x46U) to ".webp",
  maj(4, 0x66U, 0x74U, 0x79U, 0x70U, 0x68U, 0x65U) to ".heic",
  maj(4,0x66U, 0x74U, 0x79U, 0x70U, 0x69U, 0x73U, 0x6FU, 0x6DU) to ".mp4",
  maj(4,0x66U, 0x74U, 0x79U, 0x70U, 0x4DU, 0x53U, 0x4EU, 0x56U) to ".mp4",
  maj(0,0x1AU, 0x45U, 0xDFU, 0xA3U) to ".webm",  // and .mkv

  maj(0, 0xFFU, 0xFBU) to ".mp3",
  maj(0, 0xFFU, 0xF3U) to ".mp3",
  maj(0, 0xFFU, 0xF2U) to ".mp3",
  maj(0, 0x49U, 0x44U, 0x33U) to ".mp3",
  maj(0,0x4FU, 0x67U, 0x67U, 0x53U) to ".ogg"
)

fun mediaType(contents:ByteArray): String?
{
    for ((k,v) in fileMagicNums)
    {
        if (k.equals(contents)) return v
    }
    return null
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



actual fun MpIcon(mediaUri: String, widthPx: Int, heightPx: Int): ImageBitmap
{
    val bytes = try
    {
        getResourceFile(mediaUri)
    }
    catch (e: Exception)
    {
        null
    }
    val name = mediaUri.lowercase()
    val url = Url(mediaUri)

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
            SVG.getFromInputStream(bytes.inputStream())
        }

        val winAR:Float = widthPx.toFloat()/heightPx.toFloat()

        // window is wider than the doc
        val bitmap =  if (winAR > svg.documentAspectRatio)
            Bitmap.createBitmap((heightPx * svg.documentAspectRatio).toInt(), heightPx, Bitmap.Config.ARGB_8888)
        else  // windows is narrower than the doc
            Bitmap.createBitmap(widthPx, (widthPx/svg.documentAspectRatio).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        svg.setDocumentWidth(bitmap.width.toFloat())
        svg.setDocumentHeight(bitmap.height.toFloat())
        svg.renderToCanvas(canvas)
        val im: ImageBitmap = bitmap.asImageBitmap()
        return im
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
            else throw CannotLoadException("non-local data load: " + mediaUri)
        }
        else
        {
            BitmapFactory.decodeStream(bytes.inputStream())
        }

        val im: ImageBitmap = bitmap.asImageBitmap()
        return im
    }

    throw UnimplementedException("video icons")
}

@OptIn(UnstableApi::class) @Composable actual fun MpMediaView(mediaImage: ImageBitmap?, mediaData: ByteArray?, mediaUri: String?, autoplay: Boolean, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit): Boolean
{
    // Its cached
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

    val name = mediaUri ?: ""
    val url = Url(name)
    val lcasename = name.lowercase()
    var fileExt: String? = if (lcasename.contains(".")) ("." + lcasename.split(".").last()) else null
    var mediaExt: String? = if (mediaData != null) mediaType(mediaData) else null

    val ext = mediaExt ?: fileExt ?: return false

    if (ext.endsWith(".svg", true))
    {
        val svg = if (mediaData == null)
        {
            if (url.protocol == null || url.protocol.name == "file" || url.toString().contains("://localhost"))
            {
                SVG.getFromInputStream(FileInputStream(name))
            }
            else throw UnimplementedException("non-local data in NFT")
        }
        else
        {
            SVG.getFromInputStream(ByteArrayInputStream(mediaData))
        }


        // This doesn't make sense because SVG is generally scalable and might be given to us with crazy dimensions
        // val bitmap = Bitmap.createBitmap(svg.documentWidth.toInt(), svg.documentHeight.toInt(), Bitmap.Config.ARGB_8888)
        val width = (512 * svg.documentAspectRatio).toInt()
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        svg.setDocumentHeight(height.toFloat())  // This tells the renderer to scale the SVG to fit the whole canvas
        svg.setDocumentWidth(width.toFloat())
        svg.renderToCanvas(canvas)
        val im: ImageBitmap = bitmap.asImageBitmap()
        wrapper(MediaInfo(im.width, im.height, false)) { mod ->
            val m = mod ?: Modifier
              .fillMaxSize()
              .background(Color.Transparent)
            Image(im, null, m, contentScale = ContentScale.Fit)
        }
    }
    else if (ext.endsWith(".jpg", true) ||
      ext.endsWith(".jpeg", true) ||
      ext.endsWith(".png", true) ||
      ext.endsWith(".webp", true) ||
      ext.endsWith(".gif", true) ||
      ext.endsWith(".heic", true) ||
      ext.endsWith(".heif", true)
    )
    {
        val bitmap = if (mediaData == null)
        {
            if (url.protocol == null || url.protocol.name == "file" || url.toString().contains("://localhost"))
            {
                var localname = if (name.startsWith("file:///")) name.drop(7) else name
                var tmp = BitmapFactory.decodeFile(localname)
                if (tmp == null)  // given the troubles with android directory prefixes, leave this code in until we see stability
                {
                    val justname = name.replaceBeforeLast(File.separator, "").drop(1)
                    val dir = androidContext!!.filesDir
                    if (tmp == null) tmp = BitmapFactory.decodeFile(justname)
                    if (tmp == null) tmp = BitmapFactory.decodeFile(dir.absolutePath + File.separator + justname)
                    if (tmp == null) tmp = BitmapFactory.decodeFile("/data/data/info.bitcoinunlimited.www.wally/files" + File.separator + justname)
                }
                tmp
            }
            else return false //throw UnimplementedException("non-local data in NFT: protocol: ${url.protocol} url is $url, name is $name")
        }
        else
        {
            // BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
            BitmapFactory.decodeByteArray(mediaData,0,mediaData.size)
        }
        if (bitmap == null) return false

        val im: ImageBitmap = bitmap.asImageBitmap()
        wrapper(MediaInfo(im.width, im.height, false)) { mod ->
            val m = mod ?: Modifier
              .fillMaxSize()
              .background(Color.Transparent)
            Image(im, null, m, contentScale = ContentScale.Fit)
        }
    }
    else if (ext.endsWith(".mp4", true) ||
      ext.endsWith(".webm", true) ||
      ext.endsWith(".3gp", true) ||
      ext.endsWith(".mkv", true))
    {

        LogIt.info(sourceLoc()+": Video URI: $name (url: ${url})")
        val context = LocalContext.current
        val mediaItem = MediaItem.Builder().setUri(name).build()

        val exoPlayer = remember(context, mediaItem) {
            ExoPlayer.Builder(context)
              // .setMediaSourceFactory(tmp)
              .build()
              .also { exoPlayer ->
                  exoPlayer.setMediaItem(mediaItem)
                  exoPlayer.prepare()
                  if(autoplay) exoPlayer.playWhenReady = true
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
            val m = mod ?: Modifier
              .fillMaxSize()
              .background(Color.Transparent)
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
    else if (ext.endsWith(".mp3", true) ||
      ext.endsWith(".ogg", true) ||
      ext.endsWith(".aac", true) ||
      ext.endsWith(".aiff", true) ||
      ext.endsWith(".wma", true) ||
      ext.endsWith(".flac", true) ||
      ext.endsWith(".alac", true) ||
      ext.endsWith(".wav", true))
    {
        val mi = MediaInfo(200, 200, false, false)
        wrapper(mi) { mod ->
            val m = mod ?: Modifier
              .fillMaxSize()
              .background(Color.Transparent)
            ResImageView("icons/note.png", modifier = m)
        }
        return false
    }
    else  // Media is not displayable
    {
        val mi = MediaInfo(200, 200, false, false)
        wrapper(mi) { mod ->
            val m = mod ?: Modifier
              .fillMaxSize()
              .background(Color.Transparent)
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
                    statusBarColor = colorScheme.inversePrimary.toArgb()
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
