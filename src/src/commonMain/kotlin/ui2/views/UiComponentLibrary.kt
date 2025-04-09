package info.bitcoinunlimited.www.wally.ui2.views

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.SyncViewModel
import info.bitcoinunlimited.www.wally.ui2.SyncViewModelImpl
import info.bitcoinunlimited.www.wally.ui2.softKeyboardBar
import info.bitcoinunlimited.www.wally.ui2.theme.*
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.CURRENCY_1
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.CurrencyDecimal
import org.nexa.libnexakotlin.exceptionHandler


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

@Deprecated("Use theme to set font scale or set directly into the Text composable")
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

@Deprecated("Use theme to set font scale or set directly into the Text composable")
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

@Composable fun WallyTextStyle(fontScale: Double=1.0, fw: FontWeight = FontWeight.Normal, col:Color = Color.Unspecified) = TextStyle.Default.copy(color= col, lineHeight = 0.em, fontSize = FontScale(fontScale),
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
              textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.95)
          else drawIt = true
      })
}

@Composable fun CenteredFittedWithinSpaceText(text: String, startingFontScale: Double=1.0, fontWeight: FontWeight = FontWeight.Normal, fontColor: Color = Color.Unspecified, modifier: Modifier = Modifier)
{
    // see https://stackoverflow.com/questions/63971569/androidautosizetexttype-in-jetpack-compose
    val tmp = WallyTextStyle(startingFontScale, fontWeight, col = fontColor)
    var textStyle by remember { mutableStateOf(tmp) }
    var drawIt by remember { mutableStateOf(false) }
    Text(text = text, style = textStyle, modifier = modifier.padding(0.dp).drawWithContent { if (drawIt) drawContent() }. then(modifier), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
      onTextLayout = {
          textLayoutResult ->
          if (textLayoutResult.didOverflowWidth)
              textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.95)
          else drawIt = true
      })
}

@Composable fun FittedText(text: String, textStyle: TextStyle?=null, color: Color? = null, fontWeight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier)
{
    // see https://stackoverflow.com/questions/63971569/androidautosizetexttype-in-jetpack-compose
    val tmp = textStyle ?: WallyTextStyle(1.0, fontWeight)
    var rtextStyle by remember { mutableStateOf(tmp) }
    var drawIt by remember { mutableStateOf(false) }
    Text(text = text, style = rtextStyle, color = color ?: Color.Unspecified, modifier = Modifier.padding(0.dp).fillMaxWidth().drawWithContent { if (drawIt) drawContent() }. then(modifier), textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
      onTextLayout = {
          textLayoutResult ->
          if (textLayoutResult.didOverflowWidth)
              rtextStyle = rtextStyle.copy(fontSize = rtextStyle.fontSize * 0.95)
          else drawIt = true
      })
}

/** This fits multiple pieces of text into a single line
 * @param adjCount Provide the number of individual adjustable pieces of text
 * @param minFontSize Provide the smallest font size you are willing to see
 * @param textStyle Provide the initial text style
 * @param aLine Provide your composable for the line.  You must use the passed Modifier and TextStyle for every Text component that should be adjusted.  And onTextLayout must call the passed function
 *
 * This function currently has an issue where recompositions with different content keep the smaller font size of old content
*/
@Composable fun FittedText(adjCount: Int, minFontSize: TextUnit, textStyle: TextStyle?=null, aLine: @Composable (Modifier, TextStyle, (TextLayoutResult) -> Unit)->Unit)
{
    val tmp = textStyle ?: WallyTextStyle(1.0)
    val rtextStyle = remember { mutableStateOf(tmp) } // MutableStateFlow(tmp)
    var drawIt by remember { mutableStateOf(0) }
    val mod = Modifier.drawWithContent { if (drawIt>=adjCount) drawContent() }
    // rtextStyle.collectAsState().value
    aLine(mod, rtextStyle.value, { tlr ->
       // println("width ${tlr.didOverflowWidth} ${tlr.lineCount}")
        val ts = rtextStyle.value
       if ((tlr.didOverflowWidth || tlr.lineCount>1) && ts.fontSize > minFontSize)
       {
           rtextStyle.value = ts.copy(fontSize = ts.fontSize * 0.95)
           drawIt = 0
       }
       else drawIt+=1
    } )
}

/** Sets the title (at the native/platform level) if needed */
expect fun NativeTitle(title: String)

/** Sets/removes the native splashscreen, returning True if the platform HAS a native splashscreen */
expect fun NativeSplash(start: Boolean): Boolean

/** Call this when text entry has the focus -- this works around issues on some platforms in gaining knowledge of what the
 * system UX is doing.
 */
expect fun UxInTextEntry(boolean: Boolean)

data class MediaInfo(val width: Int, val height: Int,
  /** Is this a video? */
  val video:Boolean,
  /** If false, this platform cannot display this media.  A "can't display" icon is automatically substituted, or the wrapper can choose its
   * own error display by not calling the child composable. */
  val displayable: Boolean = true)


/** Provide a view for this piece of media.  If mediaData is non-null, use it as the media file contents.
 * However, still provide mediaUri (or at least dummy.ext) so that we can determine the media type from the file name within the Uri.
 * This composable is "unique" in that rather than providing a callback for contents, it provides a callback that allows you to wrap the final
 * media view.  This callback includes information about the piece of media being shown, so that you can create a custom wrapper based on the media.
 *
 * Your custom wrapper MUST call the passed composable to actually render the media.  You may pass a custom modifier.  If you pass null,
 * Modifier.fillMaxSize().background(Color.Transparent) is used.
 */
@Composable expect fun MpMediaView(mediaImage: ImageBitmap?, mediaData: ByteArray?, mediaUri: String?, autoplay: Boolean = false, hideMusicView: Boolean = false, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit):Boolean


expect fun MpIcon(mediaUri: String, widthPx: Int, heightPx: Int): ImageBitmap

@Composable fun WallyBoldText(textRes: Int)
{
    val textstyle = TextStyle.Default.copy(lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both), fontWeight = FontWeight.Bold)
    val s = i18n(textRes)

    Text(text = s, modifier = Modifier.padding(0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally,false),
      style = textstyle, textAlign = TextAlign.Center, softWrap = true)
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
fun WallyBoringLargeTextButton(textRes: Int, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { WallyLargeButtonText(i18n(textRes))}
}

@Composable
fun WallyBoringIconButton(iconRes: String, modifier: Modifier = Modifier, enabled: Boolean=true,  interactionSource: MutableInteractionSource = MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { ResImageView(iconRes, modifier.clickable { onClick() }) }
}

@Composable
fun WallyBoringIconButton(icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean=true, description:String? = null, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { Image(icon,description,modifier.clickable { onClick() }) }
}


@Composable
fun WallySmallTextButton(textRes: Int, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), selected: Boolean = false, onClick: () -> Unit)
{
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    val tmp = TextStyle.Default.copy(
      lineHeight = 0.em,
      fontSize = FontScale(0.75),
      lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
      fontWeight = fontWeight
    )

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
    { Text(i18n(textRes), Modifier.padding(2.dp), style = tmp)}
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

/** Creates a horizontal row of evenly spaced objects that you add.  Meant to be used to provide a consistent look for
 * a row of buttons. */
@Composable fun WallyButtonRow(modifier: Modifier = Modifier, content: @Composable() (RowScope.() -> Unit))
{
    Row(modifier = Modifier.fillMaxWidth().then(modifier), horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically, content)
}

@Composable
fun WallyBoringLargeIconButton(iconRes: String, enabled: Boolean= true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    // chosen height is comparable to the large text button
    WallyBoringButton(onClick, enabled, modifier, interactionSource)
    {
        if (iconRes.endsWith(".xml") || iconRes.endsWith(".png"))
            ResImageView(iconRes, Modifier.wrapContentWidth().height(32.dp).defaultMinSize(32.dp, 32.dp).clickable { onClick() })
        else
        {
            val imbytes = getResourceFile(iconRes).readByteArray()
            MpMediaView(null, imbytes, iconRes) { mediaInfo, drawer ->
                drawer(Modifier.wrapContentWidth().height(32.dp).defaultMinSize(32.dp, 32.dp).clickable { onClick() })
            }
        }
    }
}

@Composable
fun WallyBoringMediumTextButton(textRes: Int, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, modifier, interactionSource) { WallyMediumButtonText(i18n(textRes))}
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
fun CenteredSectionText(text: Int, modifier: Modifier = Modifier) = CenteredSectionText(i18n(text), modifier)


/** Standard Wally data entry field. */
@Composable
fun WallyDataEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, keyboardOptions: KeyboardOptions?=null, bkgCol: Color? = null, onValueChange: ((String) -> Unit)? = null)
{
    val ts2 = FontScaleStyle(1.25)
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
                        scope.launch(exceptionHandler) {
                            bkgColor.animateTo(bkgCol ?: SelectedBkg, animationSpec = tween(500))
                        }
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        scope.launch(exceptionHandler) {
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
    val adjustedFontSize = FontScale(1.25)
    val ts2 = LocalTextStyle.current.copy(fontSize = adjustedFontSize)
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
      modifier = modifier,
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
      textStyle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), bkgCol,
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
fun WallyTextEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, bkgCol: Color? = null, keyboardOptions: KeyboardOptions? = null, onValueChange: ((String) -> Unit)? = null) = WallyDataEntry(value, modifier, textStyle, keyboardOptions, bkgCol, onValueChange)

@Composable fun WallyError(message: String)
{
    Text(
      text = message,
      color = Color.Red
    )
}

@Composable
fun WallyRoundedTextButton(textRes: Int, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyRoundedButton(onClick, enabled, interactionSource) { WallyButtonText(i18n(textRes)) }
}
@Composable
fun WallyRoundedTextButton(text: String, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyRoundedButton(onClick, enabled, interactionSource) { WallyButtonText(text) }
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

@Composable
fun WallySectionTextStyle(): TextStyle {
    val currentStyle = LocalTextStyle.current

    val fontSize = FontScale(1.25)

    return currentStyle.copy(
      color = Color.Black,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
      fontSize = fontSize
    )
}

/** Standard Wally text entry field.*/
@Composable
fun WallyOutLineDecimalEntryTFV(tfv: MutableState<TextFieldValue>, modifier: Modifier = Modifier, textStyle: TextStyle? = null, bkgCol: Color? = null, suffix: @Composable() (() -> Unit)? = null, label: String, onValueChange: ((String) -> String) = { it })
{
    val focusManager = LocalFocusManager.current
    WallyOutlineDataEntry(tfv, modifier.onKeyEvent {
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
                            CurrencyDecimal(tfv.value.text)
                        }
                        catch (e: ArithmeticException)
                        {
                            if ((tfv.value.text.length == 0) || (tfv.value.text == "all")) CURRENCY_1
                            else return@WallyRoundedTextButton
                        }
                        catch (e: NumberFormatException)
                        {
                            if ((tfv.value.text.length == 0) || (tfv.value.text == "all")) CURRENCY_1
                            else return@WallyRoundedTextButton
                        }
                        amt *= BigDecimal.fromInt(1000)
                        val tmp = onValueChange.invoke(amt.toPlainString())
                        tfv.value = TextFieldValue(tmp, selection = TextRange(tmp.length))
                    }
                    WallyRoundedTextButton(S.million) {
                        var amt = try
                        {
                            CurrencyDecimal(tfv.value.text)
                        }
                        catch (e: ArithmeticException)
                        {
                            if ((tfv.value.text.length == 0) || (tfv.value.text == "all")) CURRENCY_1
                            else return@WallyRoundedTextButton
                        }
                        catch (e: NumberFormatException)
                        {
                            if ((tfv.value.text.length == 0) || (tfv.value.text == "all")) CURRENCY_1
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
      textStyle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), bkgCol,
      {
          if (it.text.onlyDecimal())  // Only allow characters to be entered that are part of decimal numbers
          {
              val tmp = onValueChange.invoke(it.text)
              tfv.value = TextFieldValue(tmp, selection = it.selection)
          }
      }, suffix = suffix, label = label
    )
}

/** Standard Wally text entry field.*/
@Composable
fun WallyOutLineDecimalEntry(value: MutableState<String>, modifier: Modifier = Modifier, textStyle: TextStyle? = null, bkgCol: Color? = null, suffix: @Composable() (() -> Unit)? = null, label: String, onValueChange: ((String) -> String) = { it })
{
    val tfv = remember { mutableStateOf(TextFieldValue(value.value)) }
    val focusManager = LocalFocusManager.current
    WallyOutlineDataEntry(tfv, modifier.onKeyEvent {
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
      textStyle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), bkgCol,
      {
          if (it.text.onlyDecimal())  // Only allow characters to be entered that are part of decimal numbers
          {
              val tmp = onValueChange.invoke(it.text)
              tfv.value = TextFieldValue(tmp, selection = it.selection)
          }
      }, suffix = suffix, label = label
    )
}

/** Standard Wally data entry field. */
@Composable
fun WallyOutlineDataEntry(value: MutableState<TextFieldValue>, modifier: Modifier = Modifier, textStyle: TextStyle? = null, keyboardOptions: KeyboardOptions?=null, bkgCol: Color? = null, onValueChange: ((TextFieldValue) -> Unit)? = null, suffix: @Composable() (() -> Unit)?, label: String)
{
    val adjustedFontSize = FontScale(1.25)
    val ts2 = LocalTextStyle.current.copy(fontSize = adjustedFontSize)
    val ts = ts2.merge(textStyle)
    val scope = rememberCoroutineScope()
    val bkgColor = remember { Animatable(BaseBkg) }
    val ia = remember { MutableInteractionSource() }
    val localFocusManager = LocalFocusManager.current

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

    OutlinedTextField(
      value = value.value,
      onValueChange = onValueChange ?: { },
      label = { Text(label) },
      textStyle = ts,
      interactionSource = ia,
      modifier = modifier,
      keyboardOptions = keyboardOptions ?: KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(
        onDone = { localFocusManager.clearFocus() }
      ),
      suffix = suffix
    )
}


@Composable
fun QrCode(qrText: String, modifier: Modifier)
{
    val qrcodePainter = rememberQrCodePainter(qrText)
    Image(painter = qrcodePainter, contentDescription = null, modifier = modifier.padding(16.dp))
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

/* Styling for the text of page titles */
@Composable
fun TitleText(textRes: Int, modifier: Modifier = Modifier) = TitleText(i18n(textRes), modifier)

@Composable
fun TitleText(text: String, modifier: Modifier = Modifier)
{
    Text(
      text = text,
      modifier = modifier,
      //.background(Color.Red),  // for layout debugging
      style = LocalTextStyle.current.copy(
        color = colorTitleForeground,
        textAlign = TextAlign.Center,  // To make this actually work, you need to pass a modifier where the space given to the title is greedy using .weight()
        fontWeight = FontWeight.Bold,
        fontSize = FontScale(1.5)
      )
    )
}

@Composable
fun BlockchainIcon(label: String, value: String, chain: ChainSelector?)
{
    if (chain != null)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
                ResImageView(getAccountIconResPath(chain), Modifier.size(32.dp), "Blockchain icon")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  color = wallyPurple2
                )
            }
            Spacer(Modifier.width(24.dp))
            Text(
              text = value,
              style = MaterialTheme.typography.bodyMedium
            )
        }
}

@Composable
fun Syncing(syncColor: Color = Color.White, syncViewModel: SyncViewModel = viewModel { SyncViewModelImpl() })
{
    val isSynced = syncViewModel.isSynced.collectAsState().value
    val infiniteTransition = rememberInfiniteTransition()
    val syncingText = "Syncing" // TODO: Move to string resource
    val syncedText = "Synced" // TODO: Move to string resource

    val animation by infiniteTransition.animateFloat(
      initialValue = 360f,
      targetValue = 0f,
      animationSpec = infiniteRepeatable(
        animation = tween(1000, easing = LinearEasing), // 1 second for full rotation
        repeatMode = RepeatMode.Restart
      )
    )

    Row {
        if (isSynced)
            Text(text = syncedText, style = MaterialTheme.typography.labelLarge.copy(
              color = syncColor,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            ))
        else
            Text(text = syncingText, style = MaterialTheme.typography.labelLarge.copy(
              color = syncColor,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            ))
        Spacer(modifier = Modifier.width(4.dp))
        if (isSynced)
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = syncedText,
              tint = syncColor,
              modifier = Modifier.size(18.dp)
            )
        else
            Icon(
              imageVector = Icons.Default.Sync,
              contentDescription = syncingText,
              tint = syncColor,
              modifier = Modifier
                .size(18.dp)
                .rotate(animation)
            )
    }
}

/** Wally standard icon text button */
@Composable
fun IconTextButtonUi2(
  icon: ImageVector,
  modifier: Modifier = Modifier,
  description: String = "",
  color: Color = Color.White,
  rotateIcon: Boolean = false,
  onClick: () -> Unit
)
{
    val iconModifier = if (rotateIcon)
        Modifier.graphicsLayer(
          rotationZ = 90f // Rotate the icon 90 degrees
        )
    else
        Modifier

    Column(
      modifier = modifier.wrapContentWidth().wrapContentHeight().padding(
        top = 8.dp,
        bottom = 8.dp,
        start = 2.dp,
        end = 2.dp
      ),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
          modifier = iconModifier.wrapContentWidth().wrapContentHeight().clickable {
              onClick()
          },
          imageVector = icon,
          contentDescription = description,
          tint = color,
        )
        Box(
          modifier = Modifier.wrapContentWidth().wrapContentHeight().clickable {
              onClick()
          },
          contentAlignment = Alignment.Center
        ) {
            Text(
              modifier = Modifier.clickable {
                  onClick()
              },
              style = MaterialTheme.typography.labelSmall.copy(
                color = color
              ),
              text = description,
            )
        }
    }
}

@Composable
fun ConnectionWarning()
{
    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
      )
    ) {
        Row(
          modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
              imageVector = Icons.Default.WifiOff,
              contentDescription = "Connection Warning",
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier
                .padding(end = 8.dp)
                .size(24.dp)
            )
            Text(
              text = i18n(S.connectionWarning),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}