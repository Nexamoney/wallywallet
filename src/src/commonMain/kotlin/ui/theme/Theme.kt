package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
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
import info.bitcoinunlimited.www.wally.ui2.softKeyboardBar
import info.bitcoinunlimited.www.wally.ui2.theme.*
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallyBoringButtonOutline
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallyModalOutline
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallyRoundedButtonOutline
import info.bitcoinunlimited.www.wally.ui2.views.*
import org.nexa.libnexakotlin.CURRENCY_1
import org.nexa.libnexakotlin.CurrencyDecimal
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.exceptionHandler

private val LogIt = GetLog("wally.theme")

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

/*
@Composable fun FittedTextIn()
{
    // see https://stackoverflow.com/questions/63971569/androidautosizetexttype-in-jetpack-compose
    var rtextStyle by remember { mutableStateOf(textStyle) }
    var drawIt by remember { mutableStateOf(false) }
    Text(text = text, style = textStyle, color = color ?: Color.Unspecified, modifier = Modifier.padding(0.dp).fillMaxWidth().drawWithContent { if (drawIt) drawContent() }. then(modifier), textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
      onTextLayout = {
          textLayoutResult ->
          if (textLayoutResult.didOverflowWidth)
              rtextStyle = rtextStyle.copy(fontSize = rtextStyle.fontSize * 0.9)
          else drawIt = true
      })
}
 */





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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text(text = i18n(descriptionTextRes), style = textStyle)
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
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
        StringInputTextField(labelRes, text, onChange = onChange)
    }
}

/**
 *  Input field that returns a string and is described and labelled by a res Int for internationalization
 */
@Composable
fun AddressInputField(descriptionRes: Int, labelRes: Int, text: String, style: TextStyle = TextStyle(), modifier: Modifier = Modifier, onChange: (String) -> Unit)
{
    Row(
      modifier = Modifier.fillMaxWidth().then(modifier),
      verticalAlignment = Alignment.CenterVertically
    ) {
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
fun StringInputTextField(labelRes: Int, text: String, modifier: Modifier = Modifier, onChange: (String) -> Unit)
{
    val ia = remember { MutableInteractionSource() }
    val localFocusManager = LocalFocusManager.current
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
    OutlinedTextField(
      value = text,
      onValueChange = onChange,
      label = { Text(i18n(labelRes)) },
      interactionSource = ia,
      modifier = Modifier.fillMaxWidth().then(modifier),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
      keyboardActions = KeyboardActions(onNext = {
          localFocusManager.moveFocus(FocusDirection.Down)
      }),
      minLines = 2,
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
      ),
      textStyle = TextStyle(fontSize = 14.sp),
    )
}

/**
 * Input field that returns a String
 */
@Composable
fun AddressInputTextField(labelRes: Int, text: String, onChange: (String) -> Unit)
{
    val ia = remember { MutableInteractionSource() }
    val localFocusManager = LocalFocusManager.current
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
    OutlinedTextField(
      value = text,
      onValueChange = onChange,
      label = { Text(i18n(labelRes)) },
      singleLine = true,
      interactionSource = ia,
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Next),
      keyboardActions = KeyboardActions(onNext = {
          localFocusManager.moveFocus(FocusDirection.Down)
      }),
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