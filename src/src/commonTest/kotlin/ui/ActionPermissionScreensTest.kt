package ui

import androidx.compose.ui.test.*
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.assets.AssetInfo
import org.nexa.libnexakotlin.*
import kotlin.random.Random
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ActionPermissionScreensTest : WallyUiTestBase()
{
    val cs = ChainSelector.NEXA

    @Test
    fun specialTxPermScreenBuyEightAssetsTest()
    {
        // TODO: Replace with mocked account when https://gitlab.com/wallywallet/wallet/-/merge_requests/611 is merged
        val account = wallyApp!!.newAccount("specialtxtest", 0U, "", cs)!!

        runComposeUiTest {
            val tp = TricklePaySession(wallyApp!!.tpDomains)
            tp.host = "testmerchant.example.com"
            tp.topic = "buy-assets"

            // Set up a mock proposed transaction
            val mockTx = mock<iTransaction>()
            every { mockTx.toHex() } returns "deadbeef"
            every { mockTx.chainSelector } returns cs
            tp.proposedTx = mockTx

            // Create 8 named assets
            val assetNames = listOf("AlphaToken", "BetaCoin", "GammaNFT", "DeltaAsset",
                "EpsilonToken", "ZetaCoin", "EtaNFT", "ThetaAsset")

            val groupIds = assetNames.mapIndexed { i, _ ->
                GroupId(cs, ByteArray(520) { (it + i).toByte() })
            }

            val rng = Random(42)
            val assetAmounts = assetNames.map { rng.nextInt(1, 11).toLong() }

            val receivingTokenInfo = mutableMapOf<GroupId, Long>()
            val myNetTokenInfo = mutableMapOf<GroupId, Long>()
            val assetInfoList = mutableListOf<AssetInfo>()
            val amountsMap = mutableMapOf<GroupId, Long>()

            for ((i, gid) in groupIds.withIndex())
            {
                val amt = assetAmounts[i]
                receivingTokenInfo[gid] = amt
                myNetTokenInfo[gid] = amt
                amountsMap[gid] = amt
                val ai = AssetInfo(gid)
                ai.name = assetNames[i]
                assetInfoList.add(ai)
            }

            val avm = AssetViewModel(false)
            avm.assets.value = assetInfoList
            avm.amounts.value = amountsMap

            val analysis = TxAnalysisResults(
                account = account,
                receivingSats = 0L,
                sendingSats = 1000000L,
                receivingTokenTypes = 8L,
                sendingTokenTypes = 0L,
                imSpendingTokenTypes = 0L,
                otherInputSatoshis = null,
                myInputSatoshis = 1000000L,
                myInputTokenInfo = emptyMap(),
                sendingTokenInfo = emptyMap(),
                receivingTokenInfo = receivingTokenInfo,
                myNetTokenInfo = myNetTokenInfo,
                assetViewModel = avm,
                completionException = null
            )
            tp.proposalAnalysis.value = analysis
            tp.pill.account.value = account

            val unlock = UnlockViewModel(MutableStateFlow(account))

            setContent {
                SpecialTxPermScreen(tp, unlock)
            }

            // Verify title is displayed
            onNodeWithText(i18n(S.SpecialTpTransactionFrom)).assertIsDisplayed()

            // Verify host and topic are displayed
            onNodeWithText("testmerchant.example.com").assertIsDisplayed()
            onNodeWithText("buy-assets").assertIsDisplayed()

            // Verify the receiving section is displayed with 8 assets
            onNodeWithText(i18n(S.receiving)).assertIsDisplayed()
            onNodeWithText("8").assertIsDisplayed()

            // Verify each asset name and amount is displayed in the asset list
            for ((i, name) in assetNames.withIndex())
            {
                onNodeWithText(name).assertIsDisplayed()
                val amtStr = assetAmounts[i].toString()
                val nodes = onAllNodesWithText(amtStr).fetchSemanticsNodes()
                require(nodes.isNotEmpty()) { "No nodes found with amount: $amtStr for asset: $name" }
            }

            // Verify accept and deny buttons are displayed
            onNodeWithText(i18n(S.accept)).assertIsDisplayed()
            onNodeWithText(i18n(S.deny)).assertIsDisplayed()
        }
        wallyApp!!.deleteAccount(account)
    }
}
