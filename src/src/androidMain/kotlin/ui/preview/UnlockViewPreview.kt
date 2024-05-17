package info.bitcoinunlimited.www.wally.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import info.bitcoinunlimited.www.wally.setLocale
import info.bitcoinunlimited.www.wally.ui.UnlockView
import androidx.compose.ui.platform.LocalContext
import info.bitcoinunlimited.www.wally.ui.ScreenId

@OptIn(ExperimentalStdlibApi::class)
@Preview
@Composable
fun UnlockViewPreview()
{
    setUpPreview(0,ScreenId.Home, "en", "us")
    UnlockView() {
    }
}