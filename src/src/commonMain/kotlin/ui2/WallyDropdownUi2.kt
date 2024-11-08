package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui.theme.FontScale
import info.bitcoinunlimited.www.wally.ui.theme.WallyModalOutline
import info.bitcoinunlimited.www.wally.ui.theme.colorCredit
import info.bitcoinunlimited.www.wally.ui.theme.colorDebit
import org.nexa.libnexakotlin.GetLog

private val LogIt = GetLog("BU.wally.dropdown.ui2")


@Composable
fun <T> WallyDropDownUi2(
  selected: Pair<String, T>,
  options: Map<String, T>,
  onSelect: (Pair<String, T>) -> Unit,
  usesMouse: Boolean = platform().usesMouse
)
{
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun DialogMenu()
    {
        Dialog(onDismissRequest = { expanded = false }) {
            LazyColumn (
              modifier = Modifier.background(color = Color.White, shape = RoundedCornerShape(32.dp)).border(
                  WallyModalOutline, RoundedCornerShape(32.dp)).padding(16.dp)
            ) {
                itemsIndexed(options.keys.toList()) {_, key ->
                    Row(modifier = Modifier.padding(2.dp).clickable {
                        expanded = false
                        val value = options[key] ?: return@clickable
                        onSelect(key to value)
                    }) {
                        val fontWeight: FontWeight = if (selected.first == key) FontWeight.Bold else FontWeight.Normal
                        val color = if (selected.first == key) MaterialTheme.colorScheme.primary.copy(alpha = 1.0f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 1.0f)
                        Text(
                          fontWeight = fontWeight,
                          color = color,
                          fontSize = FontScale(1.3),
                          text = key
                        )
                    }
                }
                options.forEach {
                }
            }
        }
    }

    @Composable
    fun DropDownMenu()
    {
        DropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
        ) {
            options.forEach {
                DropdownMenuItem(
                  onClick = {
                      onSelect(it.toPair())
                      expanded = false
                  },
                  text = { Text(text = it.key) },
                  modifier = Modifier.testTag("DropdownMenuItem-${it.key}")
                )
            }
        }
    }

    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, tint = colorCredit ,contentDescription = "Check or not check")
        Spacer(Modifier.width(8.dp))
        Text(i18n(S.Blockchain))
        Spacer(Modifier.width(8.dp))
        Box {
            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                  text = selected.first,
                  modifier = Modifier.clickable(onClick = { expanded = true })
                )
                IconButton(onClick = {expanded = true}) {
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }

            if (usesMouse)
                DropDownMenu()
            else if(!usesMouse && expanded)
                DialogMenu()
        }
    }
}
