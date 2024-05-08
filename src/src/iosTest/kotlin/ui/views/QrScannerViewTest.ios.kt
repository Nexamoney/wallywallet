package ui.views

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui.views.QrScannerDialog
import kotlin.test.Test

class QrScannerViewTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun qrScannerDialogTest() = runComposeUiTest {
        setContent {
            QrScannerDialog(
                onDismiss = {

                },
                onScan = {

                }
            )
        }

        onNodeWithTag("QrScannerView").isDisplayed()
    }
}