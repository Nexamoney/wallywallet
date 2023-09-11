package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen()
{
    var isSending by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            Text("HomeScreen")
            Divider()
            if(isSending)
            {
                SendFormView(
                  onComplete = { isSending = false }
                )
            }
            else if(!isSending)
            {
                Button(onClick = { isSending = true }) {
                    Text("Send")
                }
                Divider()
                ReceiveView()
                Divider()
            }
            AccountListView()
            Divider()
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
        Button(onClick = { onComplete() }) {
            Text("Send")
            // TODO: Send...
            // TODO: Display success/failure AlertDialog
        }
        Button(onClick = {
            // TODO: Clear text in "To" field
            // TODO: Clear quantity in "Amount" field
            onComplete()
        }) {
            Text("Cancel")
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
    Button(
      onClick = {
          isDialogOpen = true
          // TODO: Implement QR-code scanner
      }
    ) {
        Text("Scan QR code")
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