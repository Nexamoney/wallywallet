package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AssetListItemView
import info.bitcoinunlimited.www.wally.ui.AssetScreen
import info.bitcoinunlimited.www.wally.ui.AssetView
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import org.nexa.assets.AssetInfo
import org.nexa.assets.AssetPerAccount
import org.nexa.assets.NexaNFTv2
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AssetsScreenTests:WallyUiTestBase()
{
    // devMode is a process-wide global and the settings screen persists it to a preference store
    // that outlives the test run, so put both back the way we found them.
    private val devModeWas = wallyApp!!.preferenceDB.getBoolean(DEV_MODE_PREF, false)

    @AfterTest
    fun resetDevMode()
    {
        devMode = devModeWas
        wallyApp!!.preferenceDB.edit().putBoolean(DEV_MODE_PREF, devModeWas).commit()
    }

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
    fun assetViewDisplaysNameAndNftDetails() = runComposeUiTest {
        val asset = createAssetInfo("TestToken", "https://test.dev")
        asset.nft = NexaNFTv2("v1", "Sunset Photo", "Nature Series", "Alice", listOf(), "appUri", "info")
        setContent {
            AssetView(asset, 1L)
        }
        settle()
        onNodeWithText("TestToken").assertIsDisplayed()
        onNodeWithText("Nature Series").assertIsDisplayed()
        onNodeWithText("Alice").assertIsDisplayed()
    }

    @Test
    fun assetViewHidesQuantityWhenOne() = runComposeUiTest {
        val asset = createAssetInfo("UniqueNFT", "https://test.dev")
        asset.nft = NexaNFTv2("v1", "One of One", "Singles", "Carol", listOf(), "appUri", "info")
        setContent {
            AssetView(asset, 1L)
        }
        settle()
        onNodeWithText("UniqueNFT").assertIsDisplayed()
        // quantity text uses S.QuantityWithValue which contains "Quantity"
        onAllNodesWithText(i18n(S.QuantityWithValue).substringBefore("%"), substring = true)
            .fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun assetViewShowsUntitledWhenNameBlank() = runComposeUiTest {
        val groupIdData = ByteArray(520) { it.toByte() }
        val groupId = GroupId(ChainSelector.NEXA, groupIdData)
        val asset = AssetInfo(groupId)
        asset.name = ""
        setContent {
            AssetView(asset, 1L)
        }
        settle()
        onNodeWithText(i18n(S.Untitled)).assertIsDisplayed()
    }

    @Test
    fun assetViewShowsLoadingWhenNameNull() = runComposeUiTest {
        val groupIdData = ByteArray(520) { it.toByte() }
        val groupId = GroupId(ChainSelector.NEXA, groupIdData)
        val asset = AssetInfo(groupId)
        // name defaults to null
        setContent {
            AssetView(asset, 1L)
        }
        settle()
        onNodeWithText(i18n(S.loading)).assertIsDisplayed()
    }

    @Test
    fun assetViewWithoutNftHidesAuthorAndSeries() = runComposeUiTest {
        val asset = createAssetInfo("PlainToken", "https://test.dev")
        asset.nft = null
        setContent {
            AssetView(asset, 10L)
        }
        settle()
        onNodeWithText("PlainToken").assertIsDisplayed()
        // ByAuthor text should not appear without NFT data
        onNodeWithText(i18n(S.ByAuthor), substring = true).assertDoesNotExist()
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

    @Test
    fun assetListShowsGroupIdsAfterEnablingDeveloperMode() = runComposeUiTest {
        devMode = false

        val specs = listOf(
          "SolarCoin" to "Solar Panel #42",
          "LunarToken" to "Moon Rock #7",
          "OceanCert" to "Deep Reef #3",
          "PixelArt" to "Sunset Vista",
        )
        val assets = specs.mapIndexed { i, (name, title) ->
            val groupId = GroupId(ChainSelector.NEXA, ByteArray(32) { ((it + 16 * i) % 256).toByte() })
            val assetInfo = AssetInfo(groupId)
            assetInfo.name = name
            assetInfo.nft = NexaNFTv2("niftyVer", title, "series", "author", listOf(), "appUri", "info")
            AssetPerAccount(GroupInfo(groupId, 10L), assetInfo, null)
        }
        val groupIds = assets.map { it.groupInfo.groupId.toStringNoPrefix() }
        val account = mockAccount(initialAssets = assets.associateBy { it.groupInfo.groupId })
        setSelectedAccount(account)

        val viewModelStoreOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        val assetViewModel = AssetViewModel()
        val accountUiDataViewModel = AccountUiDataViewModel()
        val pill = AccountPill(wallyApp!!.focusedAccount)
        val unlock = UnlockViewModel(wallyApp!!.focusedAccount)

        // nav is a global: switch before composing so the first composition is the asset list and
        // not the splash screen, or whatever screen a previously run test left behind.
        nav.switch(ScreenId.Assets)
        setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                NavigationRoot(Modifier, WindowInsets(0, 0, 0, 0), pill, assetViewModel, accountUiDataViewModel, unlock)
            }
        }
        settle()

        // All four assets are listed, but their group ids are developer-only info
        waitForCatching { onNodeWithText(specs.first().second).assertExists(); true }
        for ((_, title) in specs) onNodeWithText(title).assertExists()
        for (gid in groupIds) onNodeWithText(gid).assertDoesNotExist()

        // Android draws the gear in its native title bar, which is outside of the compose tree
        if (platform().hasNativeTitleBar) nav.go(ScreenId.Settings)
        else onNodeWithContentDescription("Settings").performClick()
        settle()

        waitForCatching { onNodeWithTag("SettingsScreenScrollable").isDisplayed() }
        onNodeWithTag("SettingsScreenScrollable").performScrollToNode(hasText(i18n(S.enableDeveloperView)))
        onNodeWithTag("DevModeSwitch").performClick()
        settle()
        onNodeWithTag("DevModeSwitch").assertIsOn()
        // The switch persists DEV_MODE_PREF from Dispatchers.IO. Wait for that write, otherwise it
        // can land after resetDevMode() and leave developer mode on for the tests that follow.
        waitForCatching { wallyApp!!.preferenceDB.getBoolean(DEV_MODE_PREF, false) }

        // Back to the assets list through the bottom navigation bar
        onNodeWithTag("AssetsButton").performClick()
        settle()

        waitForCatching { onNodeWithText(groupIds.first()).assertExists(); true }
        for (gid in groupIds) onNodeWithText(gid).assertIsDisplayed()
    }
}