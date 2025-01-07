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
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.displayNotice
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.setTextClipboard
import info.bitcoinunlimited.www.wally.*import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui2.views.CenteredText
import info.bitcoinunlimited.www.wally.wallyApp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.nexa.libnexakotlin.PayDestination
import org.nexa.libnexakotlin.chainToURI
import org.nexa.libnexakotlin.rem

@Composable
fun ReceiveScreen()
{
    val selectedAccountState = selectedAccountUi2.collectAsState()
    val selectedAccount = selectedAccountState.value
    // Select the first available account if none are available
    if (selectedAccount == null)
        wallyApp?.accounts?.values?.first()?.let {
            setSelectedAccount(it)
        }


    if (selectedAccount == null)
    {
        displayErrorAndGoBack(S.NoAccounts)
        return
    }
    val address = selectedAccount.wallet.getCurrentDestination()

    // If the selected account changes, we need to update the receiving address
    LaunchedEffect(selectedAccount) {
        selectedAccount.onUpdatedReceiveInfo { address ->
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
              description = i18n(S.CopyAddress),
              color = wallyPurple,
            ) {
                setTextClipboard(address.address.toString())
                displayNotice(i18n(S.copiedToClipboard))
            }
            IconTextButtonUi2(
              icon = Icons.AutoMirrored.Outlined.ArrowBack,
              modifier = Modifier.weight(1f),
              description = i18n(S.Back),
              color = wallyPurple,
            ) {
                nav.back()
            }
        }
    }
}

@Composable
fun ReceiveScreenContent(
  address: PayDestination,
  modifier: Modifier = Modifier,
  balanceViewModel: BalanceViewModel = viewModel { BalanceViewModelImpl() },
  syncViewModel: SyncViewModel = viewModel { SyncViewModelImpl() },
  accountUiDataViewModel: AccountUiDataViewModel = viewModel { AccountUiDataViewModel() },
)
{
    val addrStr = address.address.toString()
    val qrcodePainter = rememberQrCodePainter(addrStr)
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
            AccountPill(buttonsEnabled = false, balanceViewModel, syncViewModel, accountUiDataViewModel)
            Spacer(modifier = Modifier.height(32.dp))
            Image(
              painter = qrcodePainter,
              contentDescription = "QR Code",
              modifier = Modifier
                .fillMaxWidth(0.7f) // Dynamically adjusts size to the screen width
                .aspectRatio(1f) // Keeps the image square
                .background(Color.White)
                .clickable { setTextClipboard(addrStr) }
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
              text = i18n(S.YourAddress) % mapOf("blockchain" to (chainToURI[address.chainSelector] ?: "")),
              style = MaterialTheme.typography.headlineSmall
            )
            Text(
              text = addrStr,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(0.8f).clickable { setTextClipboard(addrStr) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (devMode)
            {
                // Dev mode so don't need i18n
                CenteredText(text = "Providing address ${address.index}", textStyle = MaterialTheme.typography.bodySmall)
            }
        }
    }
}