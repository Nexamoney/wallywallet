package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.ui.theme.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource


val testDropDown = listOf("big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "this_is_a_test_of_a_long_string",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  )

@OptIn(ExperimentalResourceApi::class)
@Composable fun ImagesFromSharedResources()
{
    Row {
        Image(
          painterResource("icons/check.xml"),
          null,
          modifier = Modifier.size(40.dp),
        )
        Image(
          painterResource("icons/faucet_drip.xml"),
          null,
          modifier = Modifier.size(40.dp),
        )
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun HomeScreen()
{
    var isSending by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("any") }

    Box(modifier = WallyPageBase) {
        Column {
            Text("HomeScreen")

            ImagesFromSharedResources()

            //Row() {  // bug leaves a big gap
            Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Text("Drop boxes: ")
                var selectedIndex by remember { mutableStateOf(-1) }
                WallyDropdownMenu(
                  modifier = Modifier.width(IntrinsicSize.Min),
                  label = "Succinct",
                  items = testDropDown,
                  selectedIndex = selectedIndex,
                  style = WallyDropdownStyle.Succinct,
                  onItemSelected = { index, _ -> selectedIndex = index },
                )

                Text(", ")
                var selectedIndex2 by remember { mutableStateOf(-1) }
                WallyDropdownMenu(
                  modifier = Modifier.width(IntrinsicSize.Min).weight(1f),
                  label = "Field",
                  items = testDropDown,
                  selectedIndex = selectedIndex2,
                  style = WallyDropdownStyle.Field,
                  onItemSelected = { index, _ -> selectedIndex2 = index },
                )

                Text(", and ")
                var selectedIndex3 by remember { mutableStateOf(-1) }
                WallyDropdownMenu(
                  modifier = Modifier.width(IntrinsicSize.Min).weight(1f),
                  label = "Outlined",
                  items = testDropDown,
                  selectedIndex = selectedIndex3,
                  style = WallyDropdownStyle.Outlined,
                  onItemSelected = { index, _ -> selectedIndex3 = index },
                )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                  text = selected,
                  modifier = Modifier.clickable(onClick = { expanded = true })
                )
                IconButton(onClick = {expanded = true}) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "")
                }
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false },
              modifier = Modifier.background(Color.Magenta)
            ) {
                testDropDown.forEachIndexed { _, s ->
                    DropdownMenuItem(
                      onClick = {
                          expanded = false
                          selected = s
                      },
                      text = { Text(text = s) }
                    )
                }
            }
            WallyDivider()
            if(isSending)
            {
                SendFormView(
                  onComplete = { isSending = false }
                )
            }
            else if(!isSending)
            {
                WallyRoundedTextButton(S.Send) { isSending = true }
                WallyDivider()
                ReceiveView()
                WallyDivider()
            }
            AccountListView()
            WallyDivider()
            QrCodeScannerView()
        }
    }
}

@Composable
fun SendFormView(onComplete: () -> Unit)
{
    Text("Send from account:")
    Text("To:...")
    Text("Amount..")
    Row {
        WallyRoundedTextButton(S.Send) {
            // TODO: Send...
            // TODO: Display success/failure AlertDialog
            onComplete()
        }
        Spacer(Modifier.width(10.dp))
        WallyBoringTextButton(S.SendCancel) {
            // TODO: Clear text in "To" field
            // TODO: Clear quantity in "Amount" field
            onComplete()
        }
    }
}

@Composable
fun ReceiveView()
{
    Text("ReceiveView")
}

@Composable
fun AccountListView()
{
    Text("AccountListView")
}

@Composable
fun QrCodeScannerView()
{
    var isDialogOpen by remember { mutableStateOf(false) }
    WallyRoundedTextButton("Scan QR code") {
          isDialogOpen = true
          // TODO: Implement QR-code scanner
      }

    if (isDialogOpen) {
        AlertDialog(
          onDismissRequest = { },
          confirmButton = {
              Button(onClick = { isDialogOpen = false }) {
                  Text("OK")
              }
          },
          title = { Text("Qr scanner") },
          text = { Text("...not implemented") },
        )
    }
}