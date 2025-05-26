package ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.views.UnlockTile

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
    setUpPreview(0, ScreenId.Home, "en", "us")
    UnlockTile()
}