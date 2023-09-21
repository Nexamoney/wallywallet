package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier

// TODO: Implement dark mode
val DarkColorPalette = darkColorScheme(
  primary = colorPrimary,
  inversePrimary = colorPrimaryDark,
  secondary = colorAccent
)

val WallyPageBase = Modifier.fillMaxSize().background(BaseBkg)

val LightColorPalette = lightColorScheme(
    primary = colorPrimary,
    inversePrimary = colorPrimaryDark,
    secondary = colorAccent
    // background = defaultListHighlight

    // Other default colors to override
    //
    // background = Color.White,
    // surface = Color.White,
    // onPrimary = Color.White,
    // onSecondary = Color.Black,
    // onBackground = Color.Black,
    // onSurface = Color.Black,
)
