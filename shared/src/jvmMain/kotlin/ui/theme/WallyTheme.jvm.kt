package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

@Composable
actual fun WallyTheme(
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