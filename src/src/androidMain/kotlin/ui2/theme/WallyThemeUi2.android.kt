package info.bitcoinunlimited.www.wally.ui2.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import info.bitcoinunlimited.www.wally.ui.theme.DarkColorPalette
import info.bitcoinunlimited.www.wally.ui2.theme.Shapes

@Composable
actual fun WallyThemeUi2(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  typography: Typography,
  lightColors: ColorScheme,
  content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color is only supported on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorPalette
        else -> lightColors
    }

    // If not in Android Studio's preview then update also the system bars
    val view = LocalView.current
    if (!view.isInEditMode)
    {
        val activity = view.context as? Activity
        SideEffect {
            activity?.window?.apply {
                statusBarColor = colorTitleBackground.toArgb()
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
      typography = typography,
      shapes = Shapes,
      content = content
    )
}