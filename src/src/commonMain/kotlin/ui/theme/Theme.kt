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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.views.ResImageView

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
      modifier = Modifier.padding(0.dp).defaultMinSize(1.dp, 1.dp),
      interactionSource = interactionSource,
      content = content
    )
}

@Composable
fun WallyImageButton(resPath: String, enabled: Boolean=true, modifier: Modifier, onClick: () -> Unit)
{
    WallyRoundedButton(onClick, enabled) { ResImageView("icons/plus.xml",
      modifier = modifier.clickable { onClick() }
        )  }
}
@Composable
fun WallyBoringTextButton(textRes: Int, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { WallyButtonText(i18n(textRes))}
}
@Composable
fun WallyBoringTextButton(text: String, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { WallyButtonText(text)}
}

@Composable
fun WallyBoringLargeTextButton(textRes: Int, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { WallyLargeButtonText(i18n(textRes))}
}

@Composable
fun WallyBoringLargeIconButton(iconRes: String, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    // chosen height is comparable to the large text button
    WallyBoringButton(onClick, enabled, interactionSource) { ResImageView(iconRes, Modifier.wrapContentWidth().height(32.dp).defaultMinSize(32.dp, 32.dp).clickable { onClick() }.then(modifier)) }
}

@Composable
fun WallyBoringMediumTextButton(textRes: Int, enabled: Boolean=true, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { WallyMediumButtonText(i18n(textRes))}
}

@Composable
fun WallyBoringIconButton(iconRes: String, modifier: Modifier = Modifier, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { ResImageView(iconRes, modifier.clickable { onClick() }) }
}

@Composable
fun WallyBoringIconButton(icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean=true, description:String? = null, interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { Image(icon,description,modifier.clickable { onClick() }) }
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
    var s = i18n(textRes)

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

@Composable fun WallySwitch(isChecked: Boolean,  modifier: Modifier, onCheckedChange: (Boolean) -> Unit)
{
    Switch(
      checked = isChecked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.defaultMinSize(1.dp,1.dp).padding(0.dp).wrapContentHeight().then(modifier),
      colors = SwitchDefaults.colors(
        checkedBorderColor = Color.Transparent,
        uncheckedBorderColor = Color.Transparent,
      )
    )
}

@Composable fun WallySwitch(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) = WallySwitch(isChecked, Modifier, onCheckedChange)

@Composable fun WallySwitch(isChecked: Boolean, textRes: Int,  modifier: Modifier, onCheckedChange: (Boolean) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.defaultMinSize(1.dp, 1.dp).wrapContentHeight()
    ) {
        //WallySwitch(isChecked, modifier, onCheckedChange)
        Text(text = i18n(textRes))
    }
}
@Composable fun WallySwitch(isChecked: Boolean, textRes: Int, onCheckedChange: (Boolean) -> Unit) = WallySwitch(isChecked, textRes,  Modifier, onCheckedChange)

@Composable fun WallyError(message: String)
{
    Text(
      text = message,
      color = Color.Red
    )
}

@Composable fun WallyEmphasisBox(modifier: Modifier = Modifier, content: @Composable () -> Unit)
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

//val WallyTextStyle = LocalTextStyle.current.copy()


/**
 * Displays a notice with the given text
 */
@Composable
fun NoticeText(noticeText: String, modifier: Modifier)
{
    Text(text = noticeText,
            style = LocalTextStyle.current.copy(
                color = Color.Black,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .background(color = Color.Green).then(modifier)
                .wrapContentWidth(align = Alignment.CenterHorizontally)
        )
}

@Composable
fun ErrorText(errorText: String, modifier: Modifier)
{
   Text(text = errorText,
          style = LocalTextStyle.current.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold
          ),
          modifier = Modifier
            .background(color = Color.Red).then(modifier)
            .wrapContentWidth(align = Alignment.CenterHorizontally)
        )
}

@Composable fun FontScale(amt: Double): TextUnit
{
    return LocalTextStyle.current.fontSize.times(amt)
}

@Composable fun FontScaleStyle(amt: Double): TextStyle
{
    return LocalTextStyle.current.copy(
      fontSize = LocalTextStyle.current.fontSize.times(amt)
    )
}

@Composable fun WallyDropdownItemFontStyle(): TextStyle
{
    return LocalTextStyle.current.copy(
      fontSize = LocalTextStyle.current.fontSize.times(1.5)
    )
}

/* Styling for the text of page titles */
@Composable
fun TitleText(textRes: Int, modifier: Modifier) = TitleText(i18n(textRes), modifier)

@Composable
fun TitleText(text: String, modifier: Modifier)
{
    Text(
      text = text,
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

@Composable
fun WallySectionTextStyle(): TextStyle = LocalTextStyle.current.copy(
        color = Color.Black,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = LocalTextStyle.current.fontSize.times(1.25)
      )

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
      style = WallySectionTextStyle()
    )
}

/* Styling for the text of titles that appear within a page */
@Composable
fun CenteredSectionText(text: String, modifier: Modifier = Modifier)
{
    Box(Modifier.fillMaxWidth())
    {
        Text(text = text, modifier = modifier.padding(0.dp).fillMaxWidth().align(Alignment.Center),
          //.background(Color.Red),  // for layout debugging
          style = WallySectionTextStyle()
        )
    }
}


/** Standard Wally text entry field.*/
@Composable
fun WallyTextEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, onValueChange: ((String) -> Unit)? = null) = WallyDataEntry(value, modifier, textStyle, null, onValueChange)

/** Standard Wally text entry field.*/
@Composable
fun WallyDecimalEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, onValueChange: ((String) -> Unit)? = null) =
  WallyDataEntry(value, modifier, textStyle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), onValueChange)


/** Standard Wally data entry field.
 */
@Composable
fun WallyDataEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, keyboardOptions: KeyboardOptions?=null, onValueChange: ((String) -> Unit)? = null)
{
    val ts2 = LocalTextStyle.current.copy(fontSize = LocalTextStyle.current.fontSize.times(1.25))
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
        onValueChange ?: { },
        textStyle = ts,
        interactionSource = ia,
        modifier = modifier,
        keyboardOptions = keyboardOptions ?: KeyboardOptions.Default,
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
fun WallyIncognitoTextEntry(value: String, modifier: Modifier, onValueChange: (String) -> Unit)
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
    Row(
      modifier = Modifier.fillMaxWidth().then(modifier),
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(descriptionRes), style = style)
        Spacer(modifier = Modifier.width(8.dp))
        StringInputTextField(labelRes, text, onChange)
    }
}

/**
 *  Input field that accepts decimal numbers and is described and labelled by a res Int for internationalization
 */
@Composable
fun DecimalInputField(descriptionRes: Int, labelRes: Int, text: String, style: TextStyle = TextStyle(), modifier: Modifier = Modifier, onChange: (String) -> Unit)
{
    Row(
      modifier = Modifier.fillMaxWidth().then(modifier),
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(descriptionRes), style = style)
        Spacer(modifier = Modifier.width(8.dp))
        TextField(
            value = text,
            onValueChange = onChange,
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringInputTextField(labelRes: Int, text: String, onChange: (String) -> Unit)
{
    TextField(
      value = text,
      onValueChange = onChange,
      label = { Text(i18n(labelRes)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),

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