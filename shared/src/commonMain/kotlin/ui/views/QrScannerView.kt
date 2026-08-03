package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Displays a confirm/dismiss dialog to users with optional confirm/dismiss button text and description
 */
@Composable fun QrScannerDialog(
  onDismiss: () -> Unit,
  onScan: (String) -> Unit
)
{
    Dialog(
      onDismissRequest = onDismiss,
      content = {
          QrScannerView(Modifier.height(300.dp).testTag("QrScannerView"), onScan)
      },
    )
}

@Composable
expect fun QrScannerView(modifier: Modifier, onQrCodeScanned: (String) -> Unit)