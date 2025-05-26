package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.*
import info.bitcoinunlimited.www.wally.ui.views.FontScale
import org.nexa.libnexakotlin.ChainSelector

@Composable
fun wallyTileHeader(col: Color = Color.White) = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)

val wallyLightColors = lightColorScheme(
  background = wallyBeige, // Change to Color.White when fixing colors.
)

@Deprecated("Use theme or set font size directly in Text Composable")
val defaultFontSize = 16.sp // Fallback size

var WallyAssetRowColors = arrayOf(Color(0x4Ff5f8ff), Color(0x4Fd0d0ef))

val WallyPageBase = Modifier.fillMaxSize().background(BaseBkg)

val WallyRoundedButtonOutline = BorderStroke(1.dp, WallyBorder)

val WallyBoringButtonOutline = BorderStroke(0.5.dp, WallyBoringButtonShadow)

val WallyModalOutline = BorderStroke(2.dp, WallyBorder)

@Composable
expect fun WallyTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    typography: Typography = Typography(
    bodyLarge = MaterialTheme.typography.bodyLarge,
    headlineSmall = TextStyle(
      color = wallyPurple,
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold
    ),
    headlineMedium = TextStyle(
      color = wallyPurple,
      fontSize = 28.sp
    ),
    headlineLarge = TextStyle(
      color = wallyPurple,
      fontSize = 32.sp
    ),
    titleLarge = TextStyle(
      color = wallyPurple,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
      lineHeight = 28.sp,
      letterSpacing = 0.sp
    ),
    labelSmall = MaterialTheme.typography.labelSmall.copy(
      color = wallyPurple
    )
  ),
    lightColors: ColorScheme = wallyLightColors,
    content: @Composable () -> Unit
)

// Get this theme's icon for a specific blockchain
fun getAccountIconResPath(chainSelector: ChainSelector?): String
{
    if (chainSelector == null)  return "icons/nexa_icon.png"  // TODO, should never happen for a real account but maybe a blank icon would be better?
    return when(chainSelector)
    {
        ChainSelector.NEXA -> "icons/nexa_icon.png"
        ChainSelector.NEXATESTNET -> "icons/nexatest_icon.png"
        ChainSelector.NEXAREGTEST -> "icons/nexareg_icon.png"
        ChainSelector.BCH -> "icons/bitcoin_cash_token.xml"
        ChainSelector.BCHTESTNET -> "icons/bitcoin_cash_token.xml"
        ChainSelector.BCHREGTEST -> "icons/bitcoin_cash_token.xml"
    }
}

// TODO: Implement dark mode
val DarkColorPalette = darkColorScheme(
  primary = colorPrimary,
  inversePrimary = colorPrimaryDark,
  secondary = colorAccent,
  background = colorTitleBackground
)

val LightColorPalette = lightColorScheme(
  primary = colorPrimary,
  inversePrimary = colorPrimaryDark,
  secondary = colorAccent,
  background = colorTitleBackground

  // Other default colors to override
  //
  // background = Color.White,
  // surface = Color.White,
  // onPrimary = Color.White,
  // onSecondary = Color.Black,
  // onBackground = Color.Black,
  // onSurface = Color.Black,
)

@Composable fun WallyTextStyle(fontScale: Double=1.0, fw: FontWeight = FontWeight.Normal) = TextStyle.Default.copy(lineHeight = 0.em, fontSize = FontScale(fontScale),
  lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = fw)
