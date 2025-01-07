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
import info.bitcoinunlimited.www.wally.ui2.theme.Shapes
import info.bitcoinunlimited.www.wally.ui2.views.ResImageView
import io.ktor.http.*
import org.nexa.libnexakotlin.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.min

private val LogIt = GetLog("wally.theme.android")

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
