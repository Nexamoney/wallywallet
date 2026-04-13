package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AssetListItemView
import info.bitcoinunlimited.www.wally.ui.AssetScreen
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import org.nexa.assets.AssetInfo
import org.nexa.assets.AssetPerAccount
import org.nexa.assets.NexaNFTv2
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AssetsScreenTests:WallyUiTestBase()
{
    @Test
    fun assetListItemViewTest() = runComposeUiTest {
        val assetPerAccount = assetPerAccountFaker()
        val assetAmount = assetPerAccount.groupInfo.tokenAmount
        val title = assetPerAccount.assetInfo.nft?.title ?: throw Exception("missing title")
        val series = assetPerAccount.assetInfo.nft?.series ?: throw Exception("missing series")
        setContent {
            AssetListItemView(assetPerAccount)
        }

        onNodeWithTag("AssetListItemView").assertIsDisplayed()
        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(series).assertIsDisplayed()
        onNodeWithText(assetAmount.toString()).assertIsDisplayed()
    }

    @Test
    fun assetListAndDetailScreen() = runComposeUiTest {
        data class AssetSpec(val name: String, val title: String, val series: String, val author: String, val amount: Long, val seed: Int, val license: String? = null, val info: String = "")

        val mockLicense = "This asset is licensed under the Mock Public License v1.0. All rights reserved by the original author."
        val mockInfo = "This NFT represents a unique piece of digital art depicting a lunar rock sample collected during the Apollo missions."
        val specs = listOf(
          AssetSpec("SolarCoin", "Solar Panel #42", "Green Energy", "EcoMinter", 500L, 0),
          AssetSpec("LunarToken", "Moon Rock #7", "Space Rocks", "AstroForge", 1L, 100, license = mockLicense, info = mockInfo),
          AssetSpec("OceanCert", "Deep Reef #3", "Marine Life", "AquaVault", 75L, 200),
          AssetSpec("PixelArt", "Sunset Vista", "Landscapes", "NeonBrush", 10L, 300),
          AssetSpec("ForestNFT", "Ancient Oak #1", "Old Growth", "TimberDAO", 3L, 400),
        )

        val assets = specs.map { assetPerAccountFaker(it.name, it.title, it.series, it.author, it.amount, it.seed, it.license, it.info) }
        val assetMap = assets.associate { it.groupInfo.groupId to it }
        val account = mockAccount(initialAssets = assetMap)
        setSelectedAccount(account)

        setContent {
            AssetScreen(account) {}
        }

        // Verify all assets are displayed in the list
        val firstTitle = specs.first().title
        waitForCatching { onNodeWithText(firstTitle).assertExists(); true }
        for (spec in specs) {
            onNodeWithText(spec.title).assertExists()
            onNodeWithText(spec.series).assertExists()
        }

        // Click the 3rd asset (sorted by title) to enter detail view
        // Sort order: "Ancient Oak #1", "Deep Reef #3", "Moon Rock #7", "Solar Panel #42", "Sunset Vista"
        val detailSpec = specs.sortedBy { it.title }[2]
        onNodeWithText(detailSpec.title).performClick()
        settle()

        // Verify the detail view shows the asset's name and author
        waitForCatching { onNodeWithText(i18n(S.copyId)).isDisplayed() }
        onNodeWithText(detailSpec.name).assertIsDisplayed()
        onNodeWithText(detailSpec.author).assertIsDisplayed()
        onNodeWithText(detailSpec.series).assertIsDisplayed()

        // Verify action buttons are displayed
        onNodeWithText(i18n(S.copyId)).assertIsDisplayed()
        onNodeWithText(i18n(S.Send)).assertIsDisplayed()
        onNodeWithText(i18n(S.Sell)).assertIsDisplayed()
        onNodeWithText(i18n(S.openInBrowser)).assertIsDisplayed()

        // The appuri is set so the Invoke button should be visible
        onNodeWithText(i18n(S.AssetApplication)).assertIsDisplayed()

        // Click the Info tab and verify the info text is displayed
        onNodeWithText(i18n(S.NftInfo)).performClick()
        settle()
        waitForCatching { onNodeWithText(mockInfo, substring = true).assertExists(); true }
        onNodeWithText(mockInfo, substring = true).assertIsDisplayed()

        // Click the License tab and verify the license text is displayed
        onNodeWithText(i18n(S.NftLegal)).performClick()
        settle()
        waitForCatching { onNodeWithText(mockLicense, substring = true).assertExists(); true }
        onNodeWithText(mockLicense, substring = true).assertIsDisplayed()
    }
}