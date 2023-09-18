package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// TODO: Implement dark mode
val DarkColorPalette = darkColorScheme(
  primary = colorPrimary,
  inversePrimary = colorPrimaryDark,
  secondary = colorAccent
)

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
