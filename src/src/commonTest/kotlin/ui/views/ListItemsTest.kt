package ui.views

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.ui.views.AssetCarouselItem
import info.bitcoinunlimited.www.wally.ui.views.AssetListItem
import info.bitcoinunlimited.www.wally.ui.views.RecentTransactionListItem
import org.nexa.assets.NexaNFTv2
import ui.WallyUiTestBase
import ui.createAssetInfo
import ui.createAssetPerAccount
import ui.createRecentTransactionUIData
import ui.settle
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ListItemsTest : WallyUiTestBase()
{
    private fun createViewModelStoreOwner() = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Test
    fun assetListItemShowsNftTitleAndSeries() = runComposeUiTest {
        val title = "My NFT Title"
        val series = "Gold Series"
        val txType = "Received"
        val docUrl = "https://example.com"
        val tokenAmount = 200L
        val nft = NexaNFTv2("niftyVer", title, series, "author", listOf(), "appUri", "info")
        val assetInfo = createAssetInfo("assetName", docUrl, nft)
        val asset = createAssetPerAccount(assetInfo, tokenAmount)
        val tx = createRecentTransactionUIData(txType)

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides createViewModelStoreOwner()) {
                AssetListItem(asset, tx)
            }
        }
        settle()

        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(series).assertIsDisplayed()
        onNodeWithText(txType).assertIsDisplayed()
        onNodeWithText(tokenAmount.toString()).assertIsDisplayed()
        onNodeWithText("example.com").assertIsDisplayed()
    }

    @Test
    fun assetListItemShowsAssetNameWhenNoNft() = runComposeUiTest {
        val assetName = "mockAssetName"
        val txType = "Send"
        val docUrl = "https://mock.pages.dev"
        val assetInfo = createAssetInfo(assetName, docUrl, null)
        val asset = createAssetPerAccount(assetInfo, 50L)
        val tx = createRecentTransactionUIData(txType)

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides createViewModelStoreOwner()) {
                AssetListItem(asset, tx)
            }
        }
        settle()

        onNodeWithText(assetName).assertIsDisplayed()
        onNodeWithText(txType).assertIsDisplayed()
        onNodeWithText("50").assertIsDisplayed()
        onNodeWithText("mock.pages.dev").assertIsDisplayed()
    }

    @Test
    fun assetListItemHidesQuantityForUniqueToken() = runComposeUiTest {
        val title = "Unique Art"
        val series = "Limited"
        val nft = NexaNFTv2("niftyVer", title, series, "author", listOf(), "appUri", "info")
        val assetInfo = createAssetInfo("uniqueAsset", null, nft)
        val asset = createAssetPerAccount(assetInfo, 1L)
        val tx = createRecentTransactionUIData()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides createViewModelStoreOwner()) {
                AssetListItem(asset, tx)
            }
        }
        settle()

        onNodeWithText(title).assertIsDisplayed()
        // Unique tokens (amount == 1) should not show quantity
        onNodeWithText("1").assertDoesNotExist()
    }

    @Test
    fun recentTransactionListItemShowsDetails() = runComposeUiTest {
        val txType = "Received"
        val amount = "42.50"
        val date = "2026-01-15"
        val tx = createRecentTransactionUIData(type = txType, amount = amount, date = date)

        setContent {
            RecentTransactionListItem(tx)
        }
        settle()

        onNodeWithText(txType).assertIsDisplayed()
        onNodeWithText(amount).assertIsDisplayed()
        onNodeWithText(date).assertIsDisplayed()
    }

    @Test
    fun recentTransactionListItemShowsSendType() = runComposeUiTest {
        val txType = "Send"
        val amount = "1000.00"
        val tx = createRecentTransactionUIData(type = txType, amount = amount)

        setContent {
            RecentTransactionListItem(tx)
        }
        settle()

        onNodeWithText(txType).assertIsDisplayed()
        onNodeWithText(amount).assertIsDisplayed()
    }

    @Test
    fun assetCarouselItemRendersWithoutOverlay() = runComposeUiTest {
        val assetName = "CarouselAsset"
        val assetInfo = createAssetInfo(assetName, null)

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides createViewModelStoreOwner()) {
                AssetCarouselItem(assetInfo, hasNameOverLay = false)
            }
        }
        settle()

        // Without overlay, the asset name text should not be shown
        onNodeWithText(assetName).assertDoesNotExist()
    }

    @Test
    fun assetCarouselItemRendersWithNameOverlay() = runComposeUiTest {
        val title = "My Carousel NFT"
        val nft = NexaNFTv2("niftyVer", title, "series", "author", listOf(), "appUri", "info")
        val assetInfo = createAssetInfo("fallbackName", null, nft)

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides createViewModelStoreOwner()) {
                AssetCarouselItem(assetInfo, hasNameOverLay = true)
            }
        }
        settle()

        // With overlay enabled, the NFT title should exist in the tree
        onNodeWithText(title).assertExists()
    }

    @Test
    fun assetCarouselItemOverlayFallsBackToAssetName() = runComposeUiTest {
        val assetName = "FallbackAssetName"
        val assetInfo = createAssetInfo(assetName, null)

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides createViewModelStoreOwner()) {
                AssetCarouselItem(assetInfo, hasNameOverLay = true)
            }
        }
        settle()

        // Without NFT, overlay should fall back to asset name
        onNodeWithText(assetName).assertExists()
    }
}
