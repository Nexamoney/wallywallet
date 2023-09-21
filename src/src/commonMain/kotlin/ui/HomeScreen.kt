package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyRoundedButton

@Composable
fun HomeScreen()
{
    var isSending by remember { mutableStateOf(false) }

    Box(modifier = WallyPageBase) {
        Column {
            Text("HomeScreen")
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