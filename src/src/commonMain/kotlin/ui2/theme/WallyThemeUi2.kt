package info.bitcoinunlimited.www.wally.ui2.themeUi2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.theme.WallySwitch
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import info.bitcoinunlimited.www.wally.ui.theme.defaultFontSize

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

@Composable fun FontScale(amt: Double): TextUnit
{
    val currentStyle = LocalTextStyle.current
    val fontSize = if (currentStyle.fontSize.isUnspecified) {
        defaultFontSize
    } else {
        currentStyle.fontSize
    }

    return fontSize.times(amt)
}

@Composable fun FontScaleStyle(amt: Double): TextStyle
{
    val currentStyle = LocalTextStyle.current
    val fontSize = if (currentStyle.fontSize.isUnspecified) {
        defaultFontSize
    } else {
        currentStyle.fontSize
    }

    return LocalTextStyle.current.copy(
      fontSize = fontSize.times(amt)
    )
}

@Composable fun WallyTextStyle(fontScale: Double=1.0, fw: FontWeight = FontWeight.Normal) = TextStyle.Default.copy(lineHeight = 0.em, fontSize = FontScale(fontScale),
  lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = fw)

/* Shortcut to center text */
@Composable
fun CenteredText(text: String, modifier: Modifier = Modifier)
{
    Text(text = text, modifier = Modifier.padding(0.dp).fillMaxWidth().then(modifier), textAlign = TextAlign.Center)
}

/* Shortcut to center text */
@Composable
fun CenteredText(text: String, textStyle: TextStyle, modifier: Modifier = Modifier)
{
    Text(text = text, modifier = Modifier.padding(0.dp).fillMaxWidth().then(modifier), textAlign = TextAlign.Center, style = textStyle)
}


@Composable fun CenteredFittedText(text: Int, startingFontScale: Double=1.0, fontWeight: FontWeight = FontWeight.Normal, color: Color? = null, modifier: Modifier = Modifier) =
  CenteredFittedText(i18n(text), startingFontScale, fontWeight, color, modifier)

@Composable fun CenteredFittedText(text: String, startingFontScale: Double=1.0, fontWeight: FontWeight = FontWeight.Normal, color: Color? = null, modifier: Modifier = Modifier)
{
    // see https://stackoverflow.com/questions/63971569/androidautosizetexttype-in-jetpack-compose
    val tmp = WallyTextStyle(startingFontScale, fontWeight)
    var textStyle by remember { mutableStateOf(tmp) }
    var drawIt by remember { mutableStateOf(false) }
    Text(text = text, style = textStyle, color = color ?: Color.Unspecified, modifier = Modifier.padding(0.dp).fillMaxWidth().drawWithContent { if (drawIt) drawContent() }. then(modifier), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
      onTextLayout = {
          textLayoutResult ->
        if (textLayoutResult.didOverflowWidth)
            textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.9)
        else drawIt = true
      })
}

@Composable fun CenteredFittedWithinSpaceText(text: String, startingFontScale: Double=1.0, fontWeight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier)
{
    // see https://stackoverflow.com/questions/63971569/androidautosizetexttype-in-jetpack-compose
    val tmp = WallyTextStyle(startingFontScale, fontWeight)
    var textStyle by remember { mutableStateOf(tmp) }
    var drawIt by remember { mutableStateOf(false) }
    Text(text = text, style = textStyle, modifier = modifier.padding(0.dp).drawWithContent { if (drawIt) drawContent() }. then(modifier), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
      onTextLayout = {
          textLayoutResult ->
        if (textLayoutResult.didOverflowWidth)
            textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.9)
        else drawIt = true
      })
}

@Composable fun FittedText(text: String, textStyle:TextStyle?=null, color: Color? = null, fontWeight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier)
{
    // see https://stackoverflow.com/questions/63971569/androidautosizetexttype-in-jetpack-compose
    val tmp = textStyle ?: WallyTextStyle(1.0, fontWeight)
    var rtextStyle by remember { mutableStateOf(tmp) }
    var drawIt by remember { mutableStateOf(false) }
    Text(text = text, style = rtextStyle, color = color ?: Color.Unspecified, modifier = Modifier.padding(0.dp).fillMaxWidth().drawWithContent { if (drawIt) drawContent() }. then(modifier), textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
      onTextLayout = {
          textLayoutResult ->
          if (textLayoutResult.didOverflowWidth)
              rtextStyle = rtextStyle.copy(fontSize = rtextStyle.fontSize * 0.9)
          else drawIt = true
      })
}
