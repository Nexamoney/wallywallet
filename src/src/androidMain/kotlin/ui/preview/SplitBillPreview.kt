package info.bitcoinunlimited.www.wally.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import info.bitcoinunlimited.www.wally.ui.SplitBillScreen
import info.bitcoinunlimited.www.wally.ui.theme.WallyPageBase


@Composable
@Preview
fun SplitBillPreview()
{
    val fakes = setUpPreview(accounts = 0)
    Box(modifier = WallyPageBase) {
        SplitBillScreen(fakes.nav)
    }
}
