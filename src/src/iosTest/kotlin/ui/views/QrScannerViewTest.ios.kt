package ui.views

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui2.views.CodeType
import info.bitcoinunlimited.www.wally.ui2.views.ScannerWithPermissions
import kotlin.test.Test

class QrScannerViewTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun qrScannerDialogTest() = runComposeUiTest {
        setContent {
            ScannerWithPermissions(onScanned = { println(it); true }, types = listOf(CodeType.QR))
        }
    }
}