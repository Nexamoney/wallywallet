package info.bitcoinunlimited.www.wally.ui2.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.displayError

@Composable
actual fun QrScannerView(
  modifier: Modifier,
  onQrCodeScanned: (String) -> Unit
) {
    displayError(S.NotImplemented)
}