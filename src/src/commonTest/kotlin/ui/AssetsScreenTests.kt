package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AssetListItemView
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
}