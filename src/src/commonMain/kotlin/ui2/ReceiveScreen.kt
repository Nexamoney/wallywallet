package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.displayNotice
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.setTextClipboard
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.currentReceiveShared
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple
import info.bitcoinunlimited.www.wally.uiv2.AccountPill
import info.bitcoinunlimited.www.wally.uiv2.IconTextButtonUi2
import info.bitcoinunlimited.www.wally.wallyApp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

@Composable
fun ReceiveScreen()
{
    val selectedAccountState = selectedAccountUi2.collectAsState()
    val addressState = currentReceiveShared.collectAsState()
    val selectedAccount = selectedAccountState.value
    val address = addressState.value.second

    // Select the first available account if none are available
    if (selectedAccount == null)
        wallyApp?.accounts?.values?.first()?.let {
            setSelectedAccount(it)
        }

    // If the selected account changes, we need to update the receiving address
    LaunchedEffect(selectedAccount) {
        selectedAccount?.onUpdatedReceiveInfo { address ->
            currentReceiveShared.value = Pair(selectedAccount.name, address)
        }
    }

    Column (
      modifier = Modifier.fillMaxSize(),
    ) {
        ReceiveScreenContent(address, Modifier.weight(1f))
        // Row with buttons at the bottom
        Row(
          modifier = Modifier.fillMaxWidth()
            .wrapContentHeight()
            .background(Color.White)
            .padding(2.dp),
          horizontalArrangement = Arrangement.Center
        ) {
            IconTextButtonUi2(
              icon = Icons.Outlined.ContentCopy,
              modifier = Modifier.weight(1f),
              description = "Copy address",
              color = wallyPurple,
            ) {
                setTextClipboard(address)
                displayNotice(i18n(S.copiedToClipboard))
            }
            IconTextButtonUi2(
              icon = Icons.AutoMirrored.Outlined.ArrowBack,
              modifier = Modifier.weight(1f),
              description = "Back",
              color = wallyPurple,
            ) {
                nav.back()
            }
        }
    }
}

@Composable
fun ReceiveScreenContent(address: String, modifier: Modifier = Modifier)
{
    val qrcodePainter = rememberQrCodePainter(address)
    Surface(
      modifier = modifier.fillMaxWidth(),
      color = Color.White
    ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            AccountPill(buttonsEnabled = false)
            Spacer(modifier = Modifier.height(32.dp))
            Image(
              painter = qrcodePainter,
              contentDescription = "QR Code",
              modifier = Modifier
                .fillMaxWidth(0.7f) // Dynamically adjusts size to the screen width
                .aspectRatio(1f) // Keeps the image square
                .background(Color.White)
                .clickable { setTextClipboard(address) }
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
              text = "Your nexa address", // TODO: Move to string resource
              style = MaterialTheme.typography.headlineSmall
            )
            Text(
              text = address,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(0.8f).clickable { setTextClipboard(address) }
            )
        }
    }
}