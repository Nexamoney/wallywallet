package ui2

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.uiv2.AssetListItemViewUi2
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalUnsignedTypes::class)
class AssetsScreenUi2Tests
{
    @Test
    fun assetListItemViewTest() = runComposeUiTest {
        val groupIdData = ByteArray(520, { it.toByte() })
        val groupId = GroupId(ChainSelector.NEXA, groupIdData)
        val assetInfo = AssetInfo(groupId)
        val title = "title"
        val series = "series"
        assetInfo.nft = NexaNFTv2("niftyVer", title, series, "author", listOf(), "appUri","info")
        val assetAmount = 2L
        val groupInfo = GroupInfo(groupId, assetAmount)
        val assetPerAccount = AssetPerAccount(groupInfo, assetInfo, null)
        setContent {
            AssetListItemViewUi2(assetPerAccount)
        }

        onNodeWithTag("AssetListItemViewUi2").assertIsDisplayed()
        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(series).assertIsDisplayed()
        onNodeWithText(assetAmount.toString()).assertIsDisplayed()
    }
}