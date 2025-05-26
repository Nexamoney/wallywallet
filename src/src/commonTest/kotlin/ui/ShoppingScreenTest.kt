package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui.ShoppingScreen
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ShoppingScreenTest
{
    @Test
    fun shoppingScreenTest() = runComposeUiTest {
        setContent {
            ShoppingScreen()
        }

        onNodeWithText(i18n(S.ShoppingWarning)).isDisplayed()
        if (platform().hasLinkToNiftyArt)
            onNodeWithText(i18n(S.NFTs)).isDisplayed()
        onNodeWithText(i18n(S.ExplainBitmart)).isDisplayed()
        onNodeWithText(i18n(S.ExplainMexc)).isDisplayed()
    }
}