package info.bitcoinunlimited.www.wally.ui2.themeUi2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.theme.WallySwitch

val colorPrimaryUi2 = Color(0xFFD0A6FF)
val colorPrimaryDarkUi2 = Color(0xFF5B276B)
val wallyPurple = Color(0xFF735092)
val wallyPurple2 = Color(0xFF5F3C7E)
val wallyPurpleLight = Color(0xFFDDDAF3)
val wallyPurpleExtraLight = Color(0xFFF9F8FF)
val wallyBeige = Color(0xFFFFFCF0D9)
val wallyBeige2 = Color(0xFFF9F3E6)

val samsungKeyBoardGray= Color(0xFFf4f6f8)

val wallyLightColors = lightColorScheme(
  background = wallyBeige, // Change to Color.White when fixing colors.
)

@Composable
expect fun WallyThemeUi2(
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

@Composable fun WallySwitchRowUi2(isChecked: Boolean, textRes: Int, onCheckedChange: (Boolean) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        WallySwitch(isChecked, onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Text(
          text = i18n(textRes),
        )
    }
}