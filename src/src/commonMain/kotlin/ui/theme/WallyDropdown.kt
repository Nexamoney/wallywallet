package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import org.nexa.libnexakotlin.GetLog

private val LogIt = GetLog("BU.wally.dropdown")

enum class WallyDropdownStyle
{
    Outlined,
    Field,
    Succinct
}

@Composable
fun <T> WallyDropdownMenu(
  modalModifier: Modifier = Modifier,
  modifier: Modifier = Modifier.fillMaxWidth(),
  enabled: Boolean = true,
  label: String,
  notSetLabel: String? = null,
  items: List<T>,
  selectedIndex: Int = -1,
  onItemSelected: (index: Int, item: T) -> Unit,
  selectedItemToString: (T) -> String = { it.toString() },
  style: WallyDropdownStyle = WallyDropdownStyle.Succinct,
  itemDivider: (@Composable () -> Unit)? = null,
  drawItem: @Composable (T, Boolean, Boolean, () -> Unit) -> Unit = { item, selected, itemEnabled, onClick ->
      WallyDropdownMenuItem(
        text = item.toString(),
        selected = selected,
        enabled = itemEnabled
      )
  },
) {
    var expanded by remember { mutableStateOf(false) }

    var topOffset = 0.dp
    Box(modifier = modifier) {
        if (style == WallyDropdownStyle.Outlined)
        {
            topOffset = 8.dp
            OutlinedTextField(
              label = { Text(label) },
              value = items.getOrNull(selectedIndex)?.let { selectedItemToString(it) } ?: "",
              enabled = enabled,
              modifier = modifier,
              trailingIcon = {
                  val icon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Filled.ArrowDropDown
                  Icon(icon, "")
              },
              onValueChange = { },
              readOnly = true,
              /*  Should work, but does not get anything but focus events
          interactionSource = remember { MutableInteractionSource() }.also { interactionSource ->
              LogIt.info("i source")
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect {
                    LogIt.info("interaction source")
                    if (it is PressInteraction.Release) {
                        LogIt.info("interaction source CLICKED")
                        expanded = true
                    }
                }
            }
        } */
            )
        }
        else if (style == WallyDropdownStyle.Field)
            TextField(
              label = { Text(label) },
              value = items.getOrNull(selectedIndex)?.let { selectedItemToString(it) } ?: "",
              enabled = enabled,
              modifier = Modifier.background(BaseBkg).then(modifier),
              trailingIcon = {
                  val icon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Filled.ArrowDropDown
                  Icon(icon, "")
              },
              colors = TextFieldDefaults.colors(focusedContainerColor = BrightBkg, unfocusedContainerColor = BaseBkg),
              onValueChange = { },
              readOnly = true,
              shape = Shapes.extraSmall,
            )
        else // succinct
        {
            Row {
                Text(items.getOrNull(selectedIndex)?.toString() ?: label)
                val icon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Filled.ArrowDropDown
                Icon(icon, "")
            }
        }


        // Transparent clickable surface on top of OutlinedTextField
        Surface(
              modifier = modalModifier
                .matchParentSize()
                .padding(top = topOffset)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(enabled = enabled) { expanded = true },
              color = Color.Transparent,
          ) {}

    }


    if (expanded)
    {
        Dialog(onDismissRequest = { expanded = false }) {
            WallyTheme(false, false) {  // TODO dark mode
                val surfShape = RoundedCornerShape(32.dp)
                Surface(
                  shape = surfShape,
                  modifier = Modifier.border(WallyModalOutline, surfShape)  // background(ModalBkg) BUG: loses the shape
                ) {
                    val listState = rememberLazyListState()
                    if (selectedIndex > -1) {
                        LaunchedEffect("ScrollToSelected") {
                            listState.scrollToItem(index = selectedIndex)
                        }
                    }
                    var maxElementWidth by remember { mutableStateOf(0) }

                    LazyColumn(modifier = Modifier.defaultMinSize(1.dp).width( if (maxElementWidth == 0) 500.dp else max(50.dp, maxElementWidth.dp)),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      state = listState) {
                        if (notSetLabel != null) {
                            item {
                                WallyDropdownMenuItem(
                                  text = notSetLabel,
                                  selected = false,
                                  enabled = false,
                                )
                            }
                        }

                        itemsIndexed(items) { index, item ->
                            val selectedItem = index == selectedIndex

                            val contentColor =  when {
                                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                selectedItem -> MaterialTheme.colorScheme.primary.copy(alpha = 1.0f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 1.0f)
                            }

                            CompositionLocalProvider(LocalContentColor provides contentColor) {

                                Column(modifier = Modifier.fillMaxWidth().background(BrightBkg)
                                  .clickable(enabled) {
                                      onItemSelected(index, item)
                                      expanded = false
                                  }
                                  , horizontalAlignment = Alignment.CenterHorizontally)
                                {
                                    Box(modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min).onSizeChanged {
                                        //val Int.toDp get() = (this / LocalContext.current.resources.getSystem().displayMetrics.density).toInt()
                                        if (it.width > maxElementWidth) maxElementWidth = it.width
                                    })
                                    {
                                        drawItem(
                                          item,
                                          selectedItem,
                                          true
                                        ) {
                                            onItemSelected(index, item)
                                            expanded = false
                                        }
                                    }
                                }
                            }

                            if (index < items.lastIndex)
                            {
                                if (itemDivider != null) itemDivider()
                                    //Divider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WallyDropdownMenuItem(
  text: String,
  selected: Boolean,
  enabled: Boolean,
) {
        Box(modifier = Modifier
          .padding(3.dp)) {
            Text(
              text = text,
              style = MaterialTheme.typography.titleSmall,
              modifier = Modifier.align(Alignment.Center),
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Visible
            )
        }

}