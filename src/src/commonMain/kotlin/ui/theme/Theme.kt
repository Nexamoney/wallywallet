package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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

@Composable
fun WallyBoringLargeTextButton(textRes: Int, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { WallyLargeButtonText(i18n(textRes))}
}

@Composable
fun WallyBoringLargeIconButton(iconRes: String, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { ResImageView(iconRes, Modifier.clickable { onClick() }) }
}

@Composable
fun WallyBoringIconButton(iconRes: String, modifier: Modifier, enabled: Boolean=true,  interactionSource: MutableInteractionSource= MutableInteractionSource(), onClick: () -> Unit)
{
    WallyBoringButton(onClick, enabled, interactionSource) { ResImageView(iconRes, modifier.clickable { onClick() }) }
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

/**
 * Dropdown for selecting a string from a list
 */
@Composable
fun SelectStringDropDown(
  selected: String,
  options: List<String>,
  expanded: Boolean,
  onSelect: (String) -> Unit,
  onExpand: (Boolean) -> Unit
)
{
    Column {
        Box {
            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                  text = selected,
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
  onSelect: (String) -> Unit,
  onExpand: (Boolean) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
)
{
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = i18n(descriptionTextRes))
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                Row(
                  verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                      text = selected,
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
fun StringInputField(descriptionRes: Int, labelRes: Int, text: String, onChange: (String) -> Unit)
{
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(descriptionRes))
        StringInputTextField(labelRes, text, onChange)
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
      modifier = Modifier.wrapContentWidth(),
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