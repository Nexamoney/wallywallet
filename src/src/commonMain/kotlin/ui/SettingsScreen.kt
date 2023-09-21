package info.bitcoinunlimited.www.wally.ui
import info.bitcoinunlimited.www.wally.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.S

@Composable
fun SettingsScreen()
{
    Column(
      modifier = Modifier
        .fillMaxWidth(),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.SpaceEvenly
    ) {
        LocalCurrency()

        AccessPriceData()
        Identity()
        TricklePay()
        Assets()
        DevMode()
        AreYouSureAmt()

        WallyDivider()
        Text(text  = i18n(S.BlockchainSettings), fontSize = 24.sp)

        NexaOption("Nexa")
        NexaOption("NexaTest")
        NexaOption("NexaReg")
        NexaOption("BCH")
    }
}

@Composable
fun LocalCurrency()
{
    var expanded by remember { mutableStateOf(false) }
    val items = listOf("A", "B", "C", "D", "E", "F")
    val disabledValue = "B"
    var selectedIndex by remember { mutableStateOf(0) }
    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
          text = "Local Currency: ",
          fontSize = 18.sp
        )
        Text(items[selectedIndex],modifier = Modifier.fillMaxWidth().clickable(onClick = { expanded = true }).background(
          Color.Gray))
        DropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
          modifier = Modifier.fillMaxWidth().background(
            Color.Red)
        ) {
            items.forEachIndexed { index, s ->
                DropdownMenuItem(onClick = {
                    selectedIndex = index
                    expanded = false
                }, text = {
                    val disabledText = if (s == disabledValue)
                    {
                        " (Disabled)"
                    }
                    else
                    {
                        ""
                    }
                    Text(text = s + disabledText)
                })
            }
        }
    }
}

@Composable
fun AccessPriceData()
{
    val isChecked = remember { mutableStateOf(false) }  // initial value is true

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = isChecked.value, onCheckedChange = { isChecked.value = it })
        Text(
          text = "Access Price Data",
          fontSize = 18.sp
        )
    }
}

@Composable
fun Identity()
{
    val isChecked = remember { mutableStateOf(false) }  // initial value is true
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = isChecked.value, onCheckedChange = { isChecked.value = it })
        Text(
          text = "Enable Identity Menu",
          fontSize = 18.sp
        )
    }
}

@Composable
fun TricklePay()
{
    val isChecked = remember { mutableStateOf(false) }  // initial value is true

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = isChecked.value, onCheckedChange = { isChecked.value = it })
        Text(
          text = "Enable Trickle Pay Menu",
          fontSize = 18.sp
        )
    }
}

@Composable
fun Assets()
{
    val isChecked = remember { mutableStateOf(false) }  // initial value is true

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = isChecked.value, onCheckedChange = { isChecked.value = it })
        Text(
          text = "Enable Assets Menu",
          fontSize = 18.sp
        )
    }
}

@Composable
fun DevMode()
{
    val isChecked = remember { mutableStateOf(false) }  // initial value is true

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = isChecked.value, onCheckedChange = { isChecked.value = it })
        Text(
          text = "Enable Developer View",
          fontSize = 18.sp
        )
    }
}

@Composable
fun AreYouSureAmt()
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
          value = "",
          onValueChange = {},
          modifier = Modifier.width(160.dp)
        )
        Text(
          text = "When to ask 'Are you sure?'",
          fontSize = 16.sp
        )
    }
}

@Composable
fun NexaOption(name: String)
{
    val isChecked = remember { mutableStateOf(false) }  // initial value is true
    val isChecked2 = remember { mutableStateOf(false) }  // initial value is true
    var textState by remember { mutableStateOf(TextFieldValue()) }

    Column {
        Row {
            Text(
              text = name,
              fontSize = 18.sp
            )
            TextField(
              value = textState,
              onValueChange = { textState = it },
              label = { Text("Enter something") },
              keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done
              ),
              keyboardActions = KeyboardActions(
                onDone = {
                    // Handle 'Done' key press
                }
              ),
              modifier = Modifier.fillMaxWidth()
            )
        }
        Row {
            Switch(checked = isChecked.value, onCheckedChange = { isChecked.value = it })


            Text(
              text = "only",
              fontSize = 16.sp
            )

            Switch(checked = isChecked2.value, onCheckedChange = { isChecked2.value = it })

            Text(
              text = "prefer",
              fontSize = 16.sp
            )
        }
    }
}
