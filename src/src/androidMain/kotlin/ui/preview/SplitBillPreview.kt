package info.bitcoinunlimited.www.wally.previews

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import info.bitcoinunlimited.www.wally.ui2.SplitBillScreen
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallyPageBase


@Composable
@Preview
fun SplitBillPreview()
{
    val fakes = setUpPreview(accounts = 0)
    Box(modifier = WallyPageBase) {
        SplitBillScreen()
    }
}
