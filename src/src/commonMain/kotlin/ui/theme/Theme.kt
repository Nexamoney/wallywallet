package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.SHOW_TRICKLEPAY_PREF

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

val WallyPageBase = Modifier.fillMaxSize().background(BaseBkg)

val WallyRoundedButtonOutline = BorderStroke(1.dp, WallyBorder)

val WallyBoringButtonOutline = BorderStroke(0.5.dp, WallyBoringButtonShadow)


/** This is the basic Wally "fancy" button */
@Composable
fun WallyRoundedButton(onClick: () -> Unit, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), content: @Composable() (RowScope.() -> Unit))
{
    OutlinedButton(
      onClick = onClick,
      // Change button appearance based on current screen
      enabled = enabled,
      shape = RoundedCornerShape(50),
      contentPadding = PaddingValues(2.dp, 2.dp),
      border = WallyRoundedButtonOutline,
      colors = ButtonDefaults.buttonColors(
        disabledContainerColor = WallyButtonBackgroundDisabled,
        disabledContentColor = WallyButtonForeground,
        containerColor = WallyButtonBackground,
        contentColor = WallyButtonForeground),
      modifier = Modifier.width(IntrinsicSize.Max).padding(0.dp).defaultMinSize(1.dp, 1.dp),
      interactionSource = interactionSource,
      content = content
    )
}

@Composable
fun WallyRoundedTextButton(textRes: Int, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyRoundedButton(onClick, enabled, interactionSource) { WallyButtonText(i18n(textRes))}
}
@Composable
fun WallyRoundedTextButton(text: String, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyRoundedButton(onClick, enabled, interactionSource) { WallyButtonText(text)}
}

/** This is a button meant to look very very button-like */
@Composable
fun WallyBoringButton(onClick: () -> Unit, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), content: @Composable() (RowScope.() -> Unit))
{
    OutlinedButton(
      onClick = onClick,
      // Change button appearance based on current screen
      enabled = enabled,
      shape = RoundedCornerShape(10),
      contentPadding = PaddingValues(2.dp, 2.dp),
      border = WallyBoringButtonOutline,
      colors = ButtonDefaults.buttonColors(
        disabledContainerColor = WallyButtonBackgroundDisabled,
        disabledContentColor = WallyButtonForeground,
        containerColor = WallyButtonBackground,
        contentColor = WallyButtonForeground),
      modifier = Modifier.width(IntrinsicSize.Max).padding(0.dp).defaultMinSize(1.dp, 1.dp),
      interactionSource = interactionSource,
      content = content
    )
}

@Composable
fun WallyBoringTextButton(textRes: Int, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { WallyButtonText(i18n(textRes))}
}
@Composable
fun WallyBoringTextButton(text: String, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { WallyButtonText(text)}
}


@Composable fun WallyButtonText(text: String)
{
    val tmp = TextStyle.Default.copy(lineHeight = 0.em,
      lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Bold)
    Text(text = text, modifier = Modifier.padding(0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally,true),
      style = tmp, textAlign = TextAlign.Center, softWrap = false, maxLines = 1 )
}

@Composable fun WallyBoldText(textRes: Int)
{
    val textstyle = TextStyle.Default.copy(lineHeight = 0.em, lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Bold)
    var s = i18n(textRes)
    s = s.replace("\\n", "\n")

    Text(text = s, modifier = Modifier.padding(0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally,false),
      style = textstyle, textAlign = TextAlign.Center, softWrap = true)
}

@Composable fun WallySwitch(isChecked: MutableState<Boolean>, onCheckedChange: (Boolean) -> Unit)
{
    Switch(
      checked = isChecked.value,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
      colors = SwitchDefaults.colors(
        checkedBorderColor = Color.Transparent,
        uncheckedBorderColor = Color.Transparent,
      )
    )
}

@Composable fun WallySwitch(isChecked: MutableState<Boolean>, textRes: Int, onCheckedChange: (Boolean) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        WallySwitch(isChecked, onCheckedChange)
        Text(
          text = i18n(textRes),
        )
    }
}


//val WallyTextStyle = LocalTextStyle.current.copy()

