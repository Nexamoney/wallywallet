package ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AssetListItemView
import info.bitcoinunlimited.www.wally.ui.AssetView
import org.nexa.libnexakotlin.*
import kotlin.test.Test

class AssetScreenTest
{
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun displayAssetViewTest() = runComposeUiTest {
        // https://explorer.nexa.org/token/nexa:tptlgmqhvmwqppajq7kduxenwt5ljzcccln8ysn9wdzde540vcqqqcra40x0x
        val groupIdMock = GroupId("nexa:tptlgmqhvmwqppajq7kduxenwt5ljzcccln8ysn9wdzde540vcqqqcra40x0x")
        val assetInfoMock = AssetInfo(groupIdMock)
        assetInfoMock.tokenInfo = TokenDesc("MOCK")

        // This causes the test to run indefinetively
        setContent {
            Box(Modifier.padding(4.dp, 1.dp).fillMaxWidth()
            ) {
                AssetView(assetInfoMock, Modifier.padding(0.dp, 2.dp).fillMaxSize())
            }
        }

        onNodeWithText(i18n(S.TokenUnsigned)).assertIsDisplayed()
    }
}