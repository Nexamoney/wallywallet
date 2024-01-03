package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults.DecorationBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.NORMAL_NOTICE_DISPLAY_TIME
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.setTextClipboard
import info.bitcoinunlimited.www.wally.ui.CONFIRM_ABOVE_PREF
import info.bitcoinunlimited.www.wally.ui.SHOW_TRICKLEPAY_PREF
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.nexa.libnexakotlin.*

// https://stackoverflow.com/questions/65893939/how-to-convert-textunit-to-dp-in-jetpack-compose
val Int.dpTextUnit: TextUnit
    @Composable
    get() = with(LocalDensity.current) { this@dpTextUnit.dp.toSp() }


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

val WallyModalOutline = BorderStroke(2.dp, WallyBorder)

/** This is the basic Wally "fancy" button */
@Composable
fun WallyRoundedButton(onClick: () -> Unit, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), content: @Composable() (RowScope.() -> Unit))
{
    // BUG this button pads out vertically
    OutlinedButton(
      onClick = onClick,
      // Change button appearance based on current screen
      enabled = enabled,
      shape = RoundedCornerShape(40),
      contentPadding = PaddingValues(3.dp, 3.dp),
      border = WallyRoundedButtonOutline,
      colors = ButtonDefaults.buttonColors(
        disabledContainerColor = WallyButtonBackgroundDisabled,
        disabledContentColor = WallyButtonForeground,
        containerColor = WallyButtonBackground,
        contentColor = WallyButtonForeground),
      modifier = Modifier.width(IntrinsicSize.Max).padding(0.dp).defaultMinSize(1.dp, 1.dp),  // height(IntrinsicSize.Min)
        // .background(Color.Magenta),
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

@Composable
fun WallyBoringLargeTextButton(textRes: Int, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { WallyLargeButtonText(i18n(textRes))}
}

@Composable
fun WallyImageButton(resPath: String, enabled: Boolean=true, modifier: Modifier, onClick: () -> Unit)
{
    WallyRoundedButton(onClick, enabled) { ResImageView("icons/plus.xml",
      modifier = modifier.clickable { onClick() }
        )  }
}

@Composable fun WallyLargeButtonText(text: String)
{
    val tmp = TextStyle.Default.copy(lineHeight = 0.em, fontSize = 24.dpTextUnit, // TextStyle.Default.fontSize, //*2,
      lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Bold)
    Text(text = text, modifier = Modifier.padding(0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally,true),
      style = tmp, textAlign = TextAlign.Center, softWrap = false, maxLines = 1 )
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

@Composable fun WallySwitchRow(isChecked: Boolean, textRes: Int, onCheckedChange: (Boolean) -> Unit)
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

@Composable fun WallySwitch(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit)
{
    Switch(
      checked = isChecked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
      colors = SwitchDefaults.colors(
        checkedBorderColor = Color.Transparent,
        uncheckedBorderColor = Color.Transparent,
      )
    )
}

@Composable fun WallySwitch(isChecked: Boolean, textRes: Int, onCheckedChange: (Boolean) -> Unit)
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

@Composable fun WallyError(message: String)
{
    Text(
      text = message,
      color = Color.Red
    )
}


//val WallyTextStyle = LocalTextStyle.current.copy()


/**
 * Displays a notice with the given text
 */
@Composable
fun NoticeText(noticeText: String)
{
    Box(
        modifier = Modifier
            .background(color = Color.Green)
            .fillMaxWidth()
            .padding(16.dp)
            .wrapContentWidth(align = Alignment.CenterHorizontally)
    ) {
        Text(
            text = noticeText,
            style = LocalTextStyle.current.copy(
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        )
    }
}


/* Styling for the text of page titles */
@Composable
fun TitleText(textRes: Int, modifier: Modifier)
{
    Text(
      text = i18n(textRes),
      modifier = modifier,
        //.background(Color.Red),  // for layout debugging
      style = LocalTextStyle.current.copy(
        color = Color.Black,
        textAlign = TextAlign.Center,  // To make this actually work, you need to pass a modifier where the space given to the title is greedy using .weight()
        fontWeight = FontWeight.Bold,
        fontSize = LocalTextStyle.current.fontSize.times(1.5)
      )
    )
}

/* Styling for the text of titles that appear within a page */
@Composable
fun SectionText(textRes: Int, modifier: Modifier = Modifier) = SectionText(i18n(textRes), modifier)

/* Styling for the text of titles that appear within a page */
@Composable
fun SectionText(text: String, modifier: Modifier = Modifier)
{
    Text(
      text = text,
      modifier = modifier.padding(0.dp),
      //.background(Color.Red),  // for layout debugging
      style = LocalTextStyle.current.copy(
        color = Color.Black,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = LocalTextStyle.current.fontSize.times(1.25)
      )
    )
}


/** Standard Wally text entry field.
 */
@Composable
fun WallyTextEntry(value: String,  onValueChange: (String) -> Unit, modifier: Modifier, textStyle: TextStyle? = null)
{
    val ts2 = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize.times(1.25))
    val ts = ts2.merge(textStyle)
    val scope = rememberCoroutineScope()
    val interact = remember { object: HoverInteraction, InteractionSource
    {
        override val interactions: Flow<Interaction>
            get() = TODO("Not yet implemented")

    } }

    val bkgColor = remember { Animatable(BaseBkg) }
    val ia = remember { MutableInteractionSource() }

    LaunchedEffect(ia) {
        ia.interactions.collect {
            when(it) {
                // Hover for mouse platforms, Focus for touch platforms
                is HoverInteraction.Enter, is FocusInteraction.Focus -> {
                    scope.launch {
                        bkgColor.animateTo(SelectedBkg, animationSpec = tween(500))
                        }
                }
                is HoverInteraction.Exit, is FocusInteraction.Unfocus -> {
                    scope.launch {
                        bkgColor.animateTo(BaseBkg, animationSpec = tween(500))
                    }
                }

            }
        }
    }

    BasicTextField(
        value,
        onValueChange,
        textStyle = ts,
      interactionSource = ia,
        modifier = modifier,
      decorationBox = { tf ->
          Box(Modifier.hoverable(ia, true)
          .background(bkgColor.value)
          .drawBehind {
              val strokeWidthPx = 1.dp.toPx()
              val verticalOffset = size.height - 2.sp.toPx()
            drawLine(
              color = Color.Black,
                strokeWidth = strokeWidthPx,
                start = Offset(0f, verticalOffset),
                end = Offset(size.width, verticalOffset))
          })
          {
              Box(Modifier.padding(0.dp,0.dp, 0.dp, 2.dp)) {
              tf()
              }
          }
      }
    )
}

@Composable
fun WallyIncognitoTextEntry(value: String,  onValueChange: (String) -> Unit, modifier: Modifier)
{
    BasicTextField(
      value,
      onValueChange,
      modifier = modifier
    )
}


@Composable
fun QrCode(qrText: String, modifier: Modifier)
{
    var displayCopiedNotice by remember { mutableStateOf(false) }
    val qrcodePainter = rememberQrCodePainter(qrText)

    //Box(modifier = Modifier.padding(16.dp).background(color = Color.White)) {
    Image(painter = qrcodePainter,
              contentDescription = null,
              modifier = modifier.padding(16.dp))
}