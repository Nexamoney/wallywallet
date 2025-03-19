package info.bitcoinunlimited.www.wally.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import info.bitcoinunlimited.www.wally.ui2.ScreenId
import info.bitcoinunlimited.www.wally.ui2.views.UnlockTile

/*
@Preview
@Composable
fun UnlockViewPreview()
{
    setUpPreview(0,ScreenId.Home, "en", "us")
    UnlockView()
}
 */

@Preview
@Composable
fun UnlockViewInlinePreview()
{
    setUpPreview(0,ScreenId.Home, "en", "us")
    UnlockTile()
}