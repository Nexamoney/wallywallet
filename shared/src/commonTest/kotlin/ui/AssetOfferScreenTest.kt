package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.AssetOfferScreen
import info.bitcoinunlimited.www.wally.ui.AssetOfferViewModelFake
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import org.nexa.libnexakotlin.rem
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AssetOfferScreenTest: WallyUiTestBase()
{
    @Test
    fun assetOfferScreenTest() = runComposeUiTest {
        val offer = assetOfferFaker()
        val viewModel = AssetOfferViewModelFake(offer)
        val asset = offer.asset
        val nft = asset.nft
        val assetName = asset.nameObservable
        val name = (if ((nft != null) && (nft.title.length > 0)) nft.title else assetName.value) ?: ""
        val buyText = if (offer.assetQty == 1L && offer.uniqueAsset)
            i18n(S.scanToBuyOne) % mapOf("name" to name)
        // Display the amount if the offer is for a fungible token/asset
        else
            i18n(S.scanToBuySeveral) % mapOf("amount" to  asset.tokenDecimalFromFinestUnit(offer.assetQty).toPlainString(), "name" to name)

        setContent {
            AssetOfferScreen(ScreenNav(), offer, viewModel)
        }

        onNodeWithText(buyText).assertIsDisplayed()
        onNodeWithText(i18n(S.nexaOfferAmount) % mapOf("amount" to offer.priceFormatted)).assertIsDisplayed()
        onNodeWithTag("offerQrCode").assertIsDisplayed()

        assertTrue(true)
    }
}