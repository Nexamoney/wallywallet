package info.bitcoinunlimited.www.wally.ui2.themeUi2

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

@Composable
actual fun WallyThemeUi2(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  typography: Typography,
  lightColors: ColorScheme,
  content: @Composable () -> Unit
) {
    MaterialTheme(
      colorScheme = lightColors,
      typography = typography,
      shapes = Shapes(),
      content = content
    )
}