package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.softKeyboardBar
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import org.nexa.libnexakotlin.CURRENCY_1
import org.nexa.libnexakotlin.CurrencyDecimal
import org.nexa.libnexakotlin.GetLog

private val LogIt = GetLog("wally.theme")

val defaultFontSize = 16.sp // Fallback size

// https://stackoverflow.com/questions/65893939/how-to-convert-textunit-to-dp-in-jetpack-compose
val Int.dpTextUnit: TextUnit
    @Composable
    get() = with(LocalDensity.current) { this@dpTextUnit.dp.toSp() }


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

var WallyAssetRowColors = arrayOf(Color(0x4Ff5f8ff), Color(0x4Fd0d0ef))

val WallyPageBase = Modifier.fillMaxSize().background(BaseBkg)

val WallyRoundedButtonOutline = BorderStroke(1.dp, WallyBorder)

val WallyBoringButtonOutline = BorderStroke(0.5.dp, WallyBoringButtonShadow)

val WallyModalOutline = BorderStroke(2.dp, WallyBorder)

/** This is the basic Wally "fancy" button */
@Composable
fun WallyRoundedButton(onClick: () -> Unit, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), content: @Composable() RowScope.() -> Unit)
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
fun WallySmallTextButton(textRes: Int, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    val tmp = TextStyle.Default.copy(lineHeight = 0.em, fontSize = FontScale(0.75),
      lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Normal)
    OutlinedButton(
      onClick = onClick,
      // Change button appearance based on current screen
      enabled = enabled,
      shape = RoundedCornerShape(50),
      contentPadding = PaddingValues(1.dp, 1.dp),
      border = WallyRoundedButtonOutline,
      colors = ButtonDefaults.buttonColors(
        disabledContainerColor = WallyButtonBackgroundDisabled,
        disabledContentColor = WallyButtonForeground,
        containerColor = WallyButtonBackground,
        contentColor = WallyButtonForeground),
      modifier = Modifier.width(IntrinsicSize.Max).padding(0.dp).defaultMinSize(1.dp, 1.dp),
      interactionSource = interactionSource
    )
    { Text(i18n(textRes), Modifier.padding(0.dp), style = tmp)}
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
fun WallyBoringButton(onClick: () -> Unit, enabled: Boolean=true,  modifier: Modifier = Modifier, interactionSource: MutableInteractionSource = MutableInteractionSource(), content: @Composable() RowScope.() -> Unit)
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
      modifier = Modifier.padding(0.dp).defaultMinSize(1.dp, 1.dp).then(modifier),
      interactionSource = interactionSource,
      content = content
    )
}

@Composable
fun WallyImageButton(resPath: String, enabled: Boolean=true, modifier: Modifier, onClick: () -> Unit)
{
    WallyRoundedButton(onClick, enabled) { ResImageView(resPath, modifier = modifier.clickable { onClick() })  }
}
@Composable
fun WallyBoringTextButton(textRes: Int, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { WallyButtonText(i18n(textRes))}
}
@Composable
fun WallyBoringTextButton(text: String, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { WallyButtonText(text)}
}

@Composable
fun WallyBoringLargeTextButton(textRes: Int, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { WallyLargeButtonText(i18n(textRes))}
}

@Composable
fun WallyBoringLargeIconButton(iconRes: String, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    // chosen height is comparable to the large text button
    WallyBoringButton(onClick, enabled, modifier, interactionSource)
    {
        if (iconRes.endsWith(".xml") || iconRes.endsWith(".png"))
            ResImageView(iconRes, Modifier.wrapContentWidth().height(32.dp).defaultMinSize(32.dp, 32.dp).clickable { onClick() }.then(modifier))
        else
        {
            val imbytes = getResourceFile(iconRes).readByteArray()
            MpMediaView(null, imbytes, iconRes) { mediaInfo, drawer ->
                drawer(Modifier.wrapContentWidth().height(32.dp).defaultMinSize(32.dp, 32.dp).clickable { onClick() }.then(modifier))
            }
        }
    }
}

@Composable
fun WallyBoringMediumTextButton(textRes: Int, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { WallyMediumButtonText(i18n(textRes))}
}

@Composable
fun WallyBoringIconButton(iconRes: String, modifier: Modifier = Modifier, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { ResImageView(iconRes, modifier.clickable { onClick() }) }
}

@Composable
fun WallyBoringIconButton(icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean=true, description:String? = null, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { Image(icon,description,modifier.clickable { onClick() }) }
}

@Composable fun WallyLargeButtonText(text: String)
{
    val tmp = TextStyle.Default.copy(lineHeight = 0.em, fontSize = FontScale(1.5),
      lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Bold)
    Text(text = text, modifier = Modifier.padding(0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally,true),
      style = tmp, textAlign = TextAlign.Center, softWrap = false, maxLines = 1 )
}


@Composable fun WallyMediumButtonText(text: String)
{
    val tmp = TextStyle.Default.copy(lineHeight = 0.em, fontSize = FontScale(1.25),
      lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Bold)
    Text(text = text, modifier = Modifier.padding(0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally,true),
      style = tmp, textAlign = TextAlign.Center, softWrap = false, maxLines = 1 )
}

@Composable fun WallyMediumButtonText(text: Int) = WallyMediumButtonText(i18n(text))



@Composable fun WallyButtonText(text: String)
{
    val tmp = TextStyle.Default.copy(lineHeight = 0.em,
      lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Bold)
    Text(text = text, modifier = Modifier.padding(0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally,true),
      style = tmp, textAlign = TextAlign.Center, softWrap = false, maxLines = 1 )
}


@Composable fun WallyBoldText(textRes: Int)
{
    val textstyle = TextStyle.Default.copy(lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Bold)
    val s = i18n(textRes)

    Text(text = s, modifier = Modifier.padding(0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally,false),
      style = textstyle, textAlign = TextAlign.Center, softWrap = true)
}

@Composable fun WallySwitch(isChecked: MutableState<Boolean>, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit)
{
    Switch(
      checked = isChecked.value,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.then(modifier),  // graphicsLayer(scaleX = 0.7f, scaleY = 0.7f)
      colors = SwitchDefaults.colors(
        checkedBorderColor = Color.Transparent,
        uncheckedBorderColor = Color.Transparent,
      )
    )
}

@Composable fun WallySwitch(isChecked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit)
{
    Switch(
      checked = isChecked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.then(modifier),
      colors = SwitchDefaults.colors(
        checkedBorderColor = Color.Transparent,
        uncheckedBorderColor = Color.Transparent,
      )
    )
}

@Composable fun WallySwitch(isChecked: MutableState<Boolean>, onCheckedChange: (Boolean) -> Unit) = WallySwitch(isChecked, Modifier, onCheckedChange)

@Composable fun WallySwitch(isChecked: MutableState<Boolean>, textRes: Int, modifier: Modifier, onCheckedChange: (Boolean) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        WallySwitch(isChecked, onCheckedChange)
        Text(
          text = i18n(textRes),
          modifier = Modifier.padding(4.dp, 0.dp, 0.dp, 0.dp).then(modifier)
        )
    }
}

@Composable fun WallySwitch(isChecked: MutableState<Boolean>, textRes: Int, onCheckedChange: (Boolean) -> Unit) = WallySwitch(isChecked, textRes, Modifier, onCheckedChange)

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

@Composable fun WallySwitch(isChecked: Boolean, enabled: Boolean = true, modifier: Modifier=Modifier, onCheckedChange: (Boolean) -> Unit)
{
    Switch(
      checked = isChecked,
      enabled = enabled,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.defaultMinSize(1.dp,1.dp).padding(0.dp).wrapContentHeight().then(modifier),
      colors = SwitchDefaults.colors(
        checkedBorderColor = Color.Transparent,
        uncheckedBorderColor = Color.Transparent,
      )
    )
}

@Composable fun WallySwitch(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) = WallySwitch(isChecked, true, Modifier, onCheckedChange)

@Composable fun WallySwitch(isChecked: Boolean, textRes: Int, enabled: Boolean = true, modifier: Modifier=Modifier, onCheckedChange: (Boolean) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.defaultMinSize(1.dp, 1.dp).wrapContentHeight()
    ) {
        WallySwitch(isChecked, enabled, modifier, onCheckedChange)
        Text(text = i18n(textRes))
    }
}

@Composable fun WallySwitch(isChecked: Boolean, text: String, enabled: Boolean = true, modifier: Modifier=Modifier, onCheckedChange: (Boolean) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.defaultMinSize(1.dp, 1.dp).wrapContentHeight()
    ) {
        WallySwitch(isChecked, enabled, modifier, onCheckedChange)
        Text(text = text)
    }
}

@Composable fun WallySwitch(isChecked: Boolean, textRes: Int, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) = WallySwitch(isChecked, textRes, enabled,  Modifier, onCheckedChange)

@Composable fun WallyError(message: String)
{
    Text(
      text = message,
      color = Color.Red
    )
}

@Composable fun WallyBrightEmphasisBox(modifier: Modifier = Modifier, content: @Composable () -> Unit)
{
    val surfShape = RoundedCornerShape(20.dp)
    Surface(
      shape = surfShape,
      contentColor = BrightBkg,
      modifier = Modifier.border(WallyModalOutline, surfShape).then(modifier)
    ) {
        Box(Modifier.wrapContentSize().padding(8.dp)) { content() }
    }
}

@Composable fun WallyEmphasisBox(modifier: Modifier = Modifier, bkgCol: Color = Color.Transparent, content: @Composable () -> Unit)
{
    val surfShape = RoundedCornerShape(20.dp)
    Surface(
      shape = surfShape,
      contentColor = bkgCol,
      modifier = Modifier.border(WallyModalOutline, surfShape).then(modifier)
    ) {
        Box(Modifier.wrapContentSize().padding(0.dp)) { content() }
    }
}


/**
 * Displays a notice with the given text
 */
@Composable
fun NoticeText(noticeText: String, modifier: Modifier)
{
    Text(text = noticeText,
            style = LocalTextStyle.current.copy(
                color = Color.Black,
                fontWeight = FontWeight.Normal
            ),
            modifier = modifier.wrapContentWidth(align = Alignment.CenterHorizontally)
        )
}

@Composable
fun ErrorText(errorText: String, modifier: Modifier)
{
   Text(text = errorText,
          style = LocalTextStyle.current.copy(
            color = Color.White,
            fontWeight = FontWeight.Normal
          ),
          modifier = modifier.wrapContentWidth(align = Alignment.CenterHorizontally)
        )
}

@Composable
fun WarningText(errorText: String, modifier: Modifier)
{
    Text(text = errorText,
      style = LocalTextStyle.current.copy(
        color = Color.White,
        fontWeight = FontWeight.Normal
      ),
      modifier = modifier.wrapContentWidth(align = Alignment.CenterHorizontally)
    )
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

@Composable fun WallyDropdownItemFontStyle(): TextStyle
{
    val currentStyle = LocalTextStyle.current
    val fontSize = if (currentStyle.fontSize.isUnspecified) {
        defaultFontSize
    } else {
        currentStyle.fontSize
    }

    return LocalTextStyle.current.copy(
      fontSize = fontSize.times(1.5)
    )
}

/* Styling for the text of page titles */
@Composable
fun TitleText(textRes: Int, modifier: Modifier = Modifier) = TitleText(i18n(textRes), modifier)

@Composable
fun TitleText(text: String, modifier: Modifier = Modifier)
{
    val currentStyle = LocalTextStyle.current
    val fontSize = if (currentStyle.fontSize.isUnspecified) {
        defaultFontSize
    } else {
        currentStyle.fontSize
    }

    Text(
      text = text,
      modifier = modifier,
      //.background(Color.Red),  // for layout debugging
      style = LocalTextStyle.current.copy(
        color = colorTitleForeground,
        textAlign = TextAlign.Center,  // To make this actually work, you need to pass a modifier where the space given to the title is greedy using .weight()
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.times(1.5)
      )
    )
}

@Composable
fun WallySectionTextStyle(): TextStyle {
    val currentStyle = LocalTextStyle.current

    val fontSize = if (currentStyle.fontSize.isUnspecified) {
        defaultFontSize * 1.25
    } else {
        currentStyle.fontSize
    }

    return currentStyle.copy(
      color = Color.Black,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
      fontSize = fontSize
    )
}

/* Styling for the text of titles that appear within a page */
@Composable
fun SectionText(textRes: Int, modifier: Modifier? = null) = SectionText(i18n(textRes), modifier)

/* Styling for the text of titles that appear within a page */
@Composable
fun SectionText(text: String, modifier: Modifier? = null)
{
    val focusManager = LocalFocusManager.current
    val mod = modifier ?: Modifier.padding(0.dp).clickable {
        focusManager.clearFocus()
    }
    Text(
      text = text,
      modifier = mod,
      //.background(Color.Red),  // for layout debugging
      style = WallySectionTextStyle()
    )
}

/* Styling for the text of titles that appear within a page */
@Composable
fun CenteredSectionText(text: Int, modifier: Modifier = Modifier) =
  CenteredSectionText(i18n(text), modifier)

/* Styling for the text of titles that appear within a page */
@Composable
fun CenteredSectionText(text: String, modifier: Modifier = Modifier)
{
    val focusManager = LocalFocusManager.current
    Box(Modifier.fillMaxWidth())
    {
        Text(text = text, modifier = Modifier.padding(0.dp).fillMaxWidth().align(Alignment.Center).clickable {
            focusManager.clearFocus()
        }.then(modifier),
          //.background(Color.Red),  // for layout debugging
          style = WallySectionTextStyle(),
          textAlign = TextAlign.Center,
        )
    }
}

/* Styling for the text of titles that appear within a page */
@Composable
fun CenteredText(text: String, modifier: Modifier = Modifier)
{
    Text(text = text, modifier = Modifier.padding(0.dp).fillMaxWidth().then(modifier), textAlign = TextAlign.Center)
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

/** Creates a horizontal row of evenly spaced objects that you add.  Meant to be used to provide a consistent look for
 * a row of buttons. */
@Composable fun WallyButtonRow(modifier: Modifier = Modifier, content: @Composable() (RowScope.() -> Unit))
{
    Row(modifier = Modifier.fillMaxWidth().then(modifier), horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically, content)
}

/** Standard Wally text entry field.*/
@Composable
fun WallyTextEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, bkgCol: Color? = null, keyboardOptions: KeyboardOptions? = null, onValueChange: ((String) -> Unit)? = null) = WallyDataEntry(value, modifier, textStyle, keyboardOptions, bkgCol, onValueChange)

/** Standard Wally text entry field.*/
@Composable
fun WallyDecimalEntry(value: MutableState<String>, modifier: Modifier = Modifier, textStyle: TextStyle? = null, bkgCol: Color? = null, onValueChange: ((String) -> String) = { it })
{
    val tfv = remember { mutableStateOf(TextFieldValue(value.value)) }
    val focusManager = LocalFocusManager.current
    WallyDataEntry(tfv, modifier.onKeyEvent {
        if ((it.key == Key.Enter) || (it.key == Key.NumPadEnter))
        {
            focusManager.moveFocus(FocusDirection.Next)
            true
        }
        else false// Do not accept this key
    }.onFocusChanged {
        if (it.isFocused)
        {
            softKeyboardBar.value = { modifier ->
                // imePadding() is not needed; the BottomStart is already just above the IME
                Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                    WallyRoundedTextButton(S.sendAll) {
                        val tmp = onValueChange.invoke("all")
                        tfv.value = TextFieldValue(tmp, selection = TextRange(tmp.length))
                    }
                    WallyRoundedTextButton(S.thousand) {
                        var amt = try
                        {
                            CurrencyDecimal(value.value)
                        }
                        catch (e: ArithmeticException)
                        {
                            if ((value.value.length == 0) || (value.value == "all")) CURRENCY_1
                            else return@WallyRoundedTextButton
                        }
                        catch (e: NumberFormatException)
                        {
                            if ((value.value.length == 0) || (value.value == "all")) CURRENCY_1
                            else return@WallyRoundedTextButton
                        }
                        amt *= BigDecimal.fromInt(1000)
                        val tmp = onValueChange.invoke(amt.toPlainString())
                        tfv.value = TextFieldValue(tmp, selection = TextRange(tmp.length))
                    }
                    WallyRoundedTextButton(S.million) {
                        var amt = try
                        {
                            CurrencyDecimal(value.value)
                        }
                        catch (e: ArithmeticException)
                        {
                            if ((value.value.length == 0) || (value.value == "all")) CURRENCY_1
                            else return@WallyRoundedTextButton
                        }
                        catch (e: NumberFormatException)
                        {
                            if ((value.value.length == 0) || (value.value == "all")) CURRENCY_1
                            else return@WallyRoundedTextButton
                        }
                        amt *= BigDecimal.fromInt(1000000)
                        val tmp = onValueChange.invoke(amt.toPlainString())
                        tfv.value = TextFieldValue(tmp, selection = TextRange(tmp.length))
                    }
                    WallyRoundedTextButton(S.clear) {
                        val tmp = onValueChange.invoke("")
                        tfv.value = TextFieldValue(tmp, selection = TextRange(tmp.length))
                    }
                }
            }
        }
        else
        {
            softKeyboardBar.value = null
        }
    },
      textStyle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), bkgCol,
      {
          if (it.text.onlyDecimal())  // Only allow characters to be entered that are part of decimal numbers
          {
              val tmp = onValueChange.invoke(it.text)
              tfv.value = TextFieldValue(tmp, selection = it.selection)
          }
      }
    )
}

/** Standard Wally text entry field.*/
@Composable
fun WallyDigitEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, bkgCol: Color? = null, onValueChange: ((String) -> String) = { it })
{
    val tfv = remember { mutableStateOf(TextFieldValue(value)) }
    val focusManager = LocalFocusManager.current
    WallyDataEntry(tfv, modifier.onKeyEvent {
        if ((it.key == Key.Enter) || (it.key == Key.NumPadEnter))
        {
            focusManager.moveFocus(FocusDirection.Next)
            true
        }
        else false// Do not accept this key
    },
      textStyle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), bkgCol,
      {
          if (it.text.onlyDigits())  // Only allow characters to be entered that digits
          {
              val tmp = onValueChange.invoke(it.text)
              tfv.value = TextFieldValue(tmp, selection = it.selection)
          }
      }
    )
}

/** Standard Wally data entry field. */
@Composable
fun WallyDataEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, keyboardOptions: KeyboardOptions?=null, bkgCol: Color? = null, onValueChange: ((String) -> Unit)? = null)
{
    val ts2 = LocalTextStyle.current.copy(fontSize = LocalTextStyle.current.fontSize.times(1.25))
    val ts = ts2.merge(textStyle)
    val scope = rememberCoroutineScope()
    val bkgColor = remember { Animatable(BaseBkg) }
    val ia = remember { MutableInteractionSource() }
    // Track whenever we are inside a data entry field, because the soft keyboard will appear & we want to modify the screen based on soft
    // keyboard state
    LaunchedEffect(ia) {
        var entries=0
        try
        {
            ia.interactions.collect {
                //LogIt.info("WallyDataEntry interaction: $it")
                when (it)
                {
                    // Hover for mouse platforms, Focus for touch platforms
                    is HoverInteraction.Enter, is FocusInteraction.Focus ->
                    {
                        scope.launch {
                            bkgColor.animateTo(bkgCol ?: SelectedBkg, animationSpec = tween(500))
                        }
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        scope.launch {
                            bkgColor.animateTo(bkgCol ?: BaseBkg, animationSpec = tween(500))
                        }
                        entries--
                        if (entries==0) UxInTextEntry(false)
                    }
                }
            }
        }
        catch(e: CancellationException)
        {
            // LogIt.info("WallyDataEntry cancelled $entries")
            if (entries>0) UxInTextEntry(false)
        }
    }

    BasicTextField(
        value,
        onValueChange ?: { },
        textStyle = ts,
        interactionSource = ia,
        modifier = modifier,
        keyboardOptions = keyboardOptions ?: KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { tf ->
          Box(Modifier.hoverable(ia, true)
          .background(bkgCol ?: bkgColor.value)
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

/** Standard Wally data entry field. */
@Composable
fun WallyDataEntry(value: MutableState<TextFieldValue>, modifier: Modifier = Modifier, textStyle: TextStyle? = null, keyboardOptions: KeyboardOptions?=null, bkgCol: Color? = null, onValueChange: ((TextFieldValue) -> Unit)? = null)
{
    val currentStyle = LocalTextStyle.current

    val fontSize = if (currentStyle.fontSize.isUnspecified) {
        defaultFontSize * 1.25
    } else {
        currentStyle.fontSize
    }

    val ts2 = LocalTextStyle.current.copy(fontSize = fontSize.times(1.25))
    val ts = ts2.merge(textStyle)
    val scope = rememberCoroutineScope()
    val bkgColor = remember { Animatable(BaseBkg) }
    val ia = remember { MutableInteractionSource() }

    LaunchedEffect(ia)
    {
        var entries=0
        try
        {
            ia.interactions.collect {
                when (it)
                {
                    // Hover for mouse platforms, Focus for touch platforms
                    is HoverInteraction.Enter, is FocusInteraction.Focus ->
                    {
                        scope.launch {
                            bkgColor.animateTo(bkgCol ?: SelectedBkg, animationSpec = tween(500))
                        }
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        scope.launch {
                            bkgColor.animateTo(bkgCol ?: BaseBkg, animationSpec = tween(500))
                        }
                        entries--
                        if(entries==0) UxInTextEntry(false)
                    }

                }
            }
        }
        catch(e: CancellationException)
        {
            // LogIt.info("WallyDataEntry cancelled $entries")
            if (entries>0) UxInTextEntry(false)
        }
    }

    BasicTextField(
      value.value,
      onValueChange ?: { },
      textStyle = ts,
      interactionSource = ia,
      modifier = modifier.testTag("WallyDataEntryTextField"),
      keyboardOptions = keyboardOptions ?: KeyboardOptions.Default,
      decorationBox = { tf ->
          Box(Modifier.hoverable(ia, true)
            .background(bkgCol ?: bkgColor.value)
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
fun WallyIncognitoTextEntry(value: String, modifier: Modifier, onValueChange: (String) -> Unit)
{
    val ia = remember { MutableInteractionSource() }
    // Track whenever we are inside a data entry field, because the soft keyboard will appear & we want to modify the screen based on soft
    // keyboard state
    LaunchedEffect(ia) {
        var entries=0
        try
        {
            ia.interactions.collect {
                //LogIt.info("WallyDataEntry interaction: $it")
                when (it)
                {
                    // Hover for mouse platforms, Focus for touch platforms
                    is HoverInteraction.Enter, is FocusInteraction.Focus ->
                    {
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        entries--
                        if (entries==0) UxInTextEntry(false)
                    }
                }
            }
        }
        catch(e: CancellationException)
        {
            // LogIt.info("WallyDataEntry cancelled $entries")
            if (entries>0) UxInTextEntry(false)
        }
    }

    BasicTextField(
      value,
      onValueChange,
      modifier = modifier,
      interactionSource = ia
    )
}


@Composable
fun QrCode(qrText: String, modifier: Modifier)
{
    val qrcodePainter = rememberQrCodePainter(qrText)
    Image(painter = qrcodePainter, contentDescription = null, modifier = modifier.padding(16.dp))
}

/**
 * Dropdown for selecting a string from a list
 */
@Composable
fun SelectStringDropDown(
  selected: String,
  options: List<String>,
  expanded: Boolean,
  onSelect: (String) -> Unit,
  onExpand: (Boolean) -> Unit,
  modifier: Modifier
)
{
    Column {
        Box {
            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                  text = selected,
                  modifier = modifier.clickable(onClick = { onExpand(true) })
                )
                IconButton(onClick = { onExpand(true) }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { onExpand(false) },
            ) {
                options.forEach { currency ->
                    DropdownMenuItem(
                      onClick = {
                          onSelect(currency)
                          onExpand(false)
                      },
                      text = { Text(text = currency) }
                    )
                }
            }
        }
    }
}

/**
 * Select a string from a list of strings. Takes a descriptionTextRes to display an internationalized string from resources
 */
@Composable
fun SelectStringDropdownRes(
  descriptionTextRes: Int,
  selected: String,
  options: List<String>,
  expanded: Boolean,
  textStyle: TextStyle,
  onSelect: (String) -> Unit,
  onExpand: (Boolean) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
)
{
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = i18n(descriptionTextRes), style = textStyle)
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                Row(
                  verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                      text = selected,
                      style = textStyle,
                      modifier = Modifier.clickable(onClick = { onExpand(true) })
                    )
                    IconButton(onClick = { onExpand(true) }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                  expanded = expanded,
                  onDismissRequest = { onExpand(false) },
                ) {
                    options.forEach { name ->
                        DropdownMenuItem(
                          onClick = {
                              onSelect(name)
                              onExpand(false)
                          },
                          text = { Text(text = name) }
                        )
                    }
                }
            }
        }
    }
}



/**
 *  Input field that returns a string and is described and labelled by a res Int for internationalization
 */
@Composable
fun StringInputField(descriptionRes: Int, labelRes: Int, text: String, style: TextStyle = TextStyle(), modifier: Modifier = Modifier, onChange: (String) -> Unit)
{
    val focusManager = LocalFocusManager.current
    Row(
      modifier = Modifier.fillMaxWidth().then(modifier),
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(descriptionRes), style = style, modifier = Modifier.clickable {
            focusManager.clearFocus()
        })
        Spacer(modifier = Modifier.width(8.dp))
        StringInputTextField(labelRes, text, onChange)
    }
}

/**
 *  Input field that returns a string and is described and labelled by a res Int for internationalization
 */
@Composable
fun AddressInputField(descriptionRes: Int, labelRes: Int, text: String, style: TextStyle = TextStyle(), modifier: Modifier = Modifier, onChange: (String) -> Unit)
{
    val focusManager = LocalFocusManager.current
    Row(
      modifier = Modifier.fillMaxWidth().then(modifier),
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(descriptionRes), style = style, modifier = Modifier.clickable {
            focusManager.clearFocus()
        })
        Spacer(modifier = Modifier.width(8.dp))
        AddressInputTextField(labelRes, text, onChange)
    }
}

/**
 *  Input field that accepts decimal numbers and is described and labelled by a res Int for internationalization
 */
@Composable
fun DecimalInputField(descriptionRes: Int, labelRes: Int, text: String, style: TextStyle = TextStyle(), modifier: Modifier = Modifier, onChange: (String) -> Unit)
{
    val ia = remember { MutableInteractionSource() }
    // Track whenever we are inside a data entry field, because the soft keyboard will appear & we want to modify the screen based on soft
    // keyboard state
    LaunchedEffect(ia) {
        var entries=0
        try
        {
            ia.interactions.collect {
                //LogIt.info("WallyDataEntry interaction: $it")
                when (it)
                {
                    // Hover for mouse platforms, Focus for touch platforms
                    is HoverInteraction.Enter, is FocusInteraction.Focus ->
                    {
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        entries--
                        if (entries==0) UxInTextEntry(false)
                    }
                }
            }
        }
        catch(e: CancellationException)
        {
            // LogIt.info("WallyDataEntry cancelled $entries")
            if (entries>0) UxInTextEntry(false)
        }
    }

    Row(
      modifier = Modifier.fillMaxWidth().then(modifier),
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(descriptionRes), style = style)
        Spacer(modifier = Modifier.width(8.dp))
        TextField(
            value = text,
            onValueChange = onChange,
            interactionSource = ia,
            label = { Text(i18n(labelRes)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().wrapContentWidth(),

            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
            textStyle = TextStyle(fontSize = 12.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            )
    )
    }
}

/**
 * Input field that returns a String
 */
@Composable
fun StringInputTextField(labelRes: Int, text: String, onChange: (String) -> Unit)
{
    val ia = remember { MutableInteractionSource() }
    // Track whenever we are inside a data entry field, because the soft keyboard will appear & we want to modify the screen based on soft
    // keyboard state
    LaunchedEffect(ia) {
        var entries=0
        try
        {
            ia.interactions.collect {
                //LogIt.info("WallyDataEntry interaction: $it")
                when (it)
                {
                    // Hover for mouse platforms, Focus for touch platforms
                    is HoverInteraction.Enter, is FocusInteraction.Focus ->
                    {
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        entries--
                        if (entries==0) UxInTextEntry(false)
                    }
                }
            }
        }
        catch(e: CancellationException)
        {
            // LogIt.info("WallyDataEntry cancelled $entries")
            if (entries>0) UxInTextEntry(false)
        }
    }
    TextField(
      value = text,
      onValueChange = onChange,
      label = { Text(i18n(labelRes)) },
      singleLine = true,
      interactionSource = ia,
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),

      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
      ),
      textStyle = TextStyle(fontSize = 12.sp),
    )
}

/**
 * Input field that returns a String
 */
@Composable
fun AddressInputTextField(labelRes: Int, text: String, onChange: (String) -> Unit)
{
    val ia = remember { MutableInteractionSource() }
    // Track whenever we are inside a data entry field, because the soft keyboard will appear & we want to modify the screen based on soft
    // keyboard state
    LaunchedEffect(ia) {
        var entries=0
        try
        {
            ia.interactions.collect {
                //LogIt.info("WallyDataEntry interaction: $it")
                when (it)
                {
                    // Hover for mouse platforms, Focus for touch platforms
                    is HoverInteraction.Enter, is FocusInteraction.Focus ->
                    {
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        entries--
                        if (entries==0) UxInTextEntry(false)
                    }
                }
            }
        }
        catch(e: CancellationException)
        {
            // LogIt.info("WallyDataEntry cancelled $entries")
            if (entries>0) UxInTextEntry(false)
        }
    }
    TextField(
      value = text,
      onValueChange = onChange,
      label = { Text(i18n(labelRes)) },
      singleLine = true,
      interactionSource = ia,
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
      ),
      textStyle = TextStyle(fontSize = 12.sp),
    )
}

/**
 * Input field that returns a Kotlin Long
 */
@Composable
fun LongInputField(descriptionRes: Int, labelRes: Int, amount: Long, onChange: (Long) -> Unit)
{
    val ia = remember { MutableInteractionSource() }
    // Track whenever we are inside a data entry field, because the soft keyboard will appear & we want to modify the screen based on soft
    // keyboard state
    LaunchedEffect(ia) {
        var entries=0
        try
        {
            ia.interactions.collect {
                //LogIt.info("WallyDataEntry interaction: $it")
                when (it)
                {
                    // Hover for mouse platforms, Focus for touch platforms
                    is HoverInteraction.Enter, is FocusInteraction.Focus ->
                    {
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        entries--
                        if (entries==0) UxInTextEntry(false)
                    }
                }
            }
        }
        catch(e: CancellationException)
        {
            // LogIt.info("WallyDataEntry cancelled $entries")
            if (entries>0) UxInTextEntry(false)
        }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(descriptionRes))
        TextField(
          value =
          if (amount == 0L)
              ""
          else
              amount.toString(),
          onValueChange = {
              if (it.isEmpty() || it == "0")
                  onChange(0L)
              else
                  onChange(it.toLongOrNull() ?: amount)
          },
          label = { Text(i18n(labelRes)) },
          singleLine = true,
          interactionSource = ia,
          modifier = Modifier.width(180.dp),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
          ),
          textStyle = TextStyle(fontSize = 12.sp),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}


/**
 * Displays a confirm/dismiss dialog to users with optional confirm/dismiss button text and description
 */
@Composable fun ConfirmDismissNoteDialog(
  amount: BigDecimal,
  assets: List<AssetInfo>,
  displayed: Boolean,
  titleRes: Int,
  text: String,
  note: String,
  dismissRes: Int,
  confirmRes: Int,
  onDismiss: () -> Unit,
  onConfirm: (amount: BigDecimal) -> Unit
) {
    if (displayed) {
        AlertDialog(
          title = { Text(i18n(titleRes)) },
          text = {
              Column {
                  Text(text)
                  Text(note)
              }
          },
          confirmButton = {
              Button(onClick = { onConfirm(amount) }) {
                  Text(i18n(confirmRes))
              }
          },
          dismissButton = {
              Button(onClick = onDismiss) {
                  Text(i18n(dismissRes))
              }
          },
          onDismissRequest = onDismiss,
        )
    }
}