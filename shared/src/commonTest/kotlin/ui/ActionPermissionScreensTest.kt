package ui

import androidx.compose.ui.test.*
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.SpecialTxPermScreen
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import info.bitcoinunlimited.www.wally.wallyApp
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.assets.AssetInfo
import org.nexa.libnexakotlin.*
import kotlin.random.Random
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.sourceLoc
import kotlin.test.Test

private val LogIt = GetLog("BU.wally.perm")

@OptIn(ExperimentalTestApi::class)
class ActionPermissionScreensTest : WallyUiTestBase()
{
    val cs = ChainSelector.NEXA

    @Test
    fun specialTxPermScreenBuyEightAssetsTest()
    {
        val account = mockAccount()

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
    }

    @Test
    fun specialTxPermScreenUnsignedProposalShowsFriendlyError()
    {
        val account = mockAccount()

        runComposeUiTest {
            val tp = TricklePaySession(wallyApp!!.tpDomains)
            tp.host = "niftyart.cash"
            tp.topic = "sell-offer"

            val mockTx = mock<iTransaction>()
            every { mockTx.toHex() } returns "deadbeef"
            every { mockTx.chainSelector } returns cs
            tp.proposedTx = mockTx

            // The raw txSanityCheck message carries the full tx hex after the newline
            val rawMsg = "Wallet nexa tx is not fully signed.  Unsigned input indexes: [0]  Total inputs: 2  Outputs: 4  Fee: 597\n000200a52adeadbeef0123456789abcdef"

            val analysis = TxAnalysisResults(
                account = account,
                receivingSats = 0L,
                sendingSats = 250546L,
                receivingTokenTypes = 0L,
                sendingTokenTypes = 0L,
                imSpendingTokenTypes = 0L,
                otherInputSatoshis = 546L,
                myInputSatoshis = 250000L,
                myInputTokenInfo = emptyMap(),
                sendingTokenInfo = emptyMap(),
                receivingTokenInfo = emptyMap(),
                myNetTokenInfo = emptyMap(),
                assetViewModel = AssetViewModel(false),
                completionException = WalletException(rawMsg),
                foreignUnsignedInputs = listOf(0)
            )
            tp.proposalAnalysis.value = analysis
            tp.pill.account.value = account

            val unlock = UnlockViewModel(MutableStateFlow(account))

            setContent {
                SpecialTxPermScreen(tp, unlock)
            }

            // onNodeWithText also guards against the error rendering more than once
            onNodeWithText(i18n(S.TpProposalMissingSignature)).assertIsDisplayed()
            onNodeWithText(i18n(S.CannotCompleteTransaction)).assertIsDisplayed()

            // The raw exception text (and the tx hex dump after its newline) must not reach the screen
            require(onAllNodesWithText("Unsigned input indexes", substring = true).fetchSemanticsNodes().isEmpty()) { "Raw completion exception leaked to the screen" }
            require(onAllNodesWithText("000200a52adeadbeef", substring = true).fetchSemanticsNodes().isEmpty()) { "Raw tx hex leaked to the screen" }

            // An uncompletable proposal is acknowledged with Okay; accept and deny are not offered
            onNodeWithText(i18n(S.Okay)).assertIsDisplayed()
            require(onAllNodesWithText(i18n(S.accept)).fetchSemanticsNodes().isEmpty()) { "Accept button should not render for an uncompletable proposal" }
            require(onAllNodesWithText(i18n(S.deny)).fetchSemanticsNodes().isEmpty()) { "Deny button should be replaced by Okay for an uncompletable proposal" }
        }
    }

    @Test
    fun specialTxPermScreenCompletionErrorDropsHexDump()
    {
        val account = mockAccount()

        runComposeUiTest {
            val tp = TricklePaySession(wallyApp!!.tpDomains)
            tp.host = "testmerchant.example.com"
            tp.topic = "buy-assets"

            val mockTx = mock<iTransaction>()
            every { mockTx.toHex() } returns "deadbeef"
            every { mockTx.chainSelector } returns cs
            tp.proposedTx = mockTx

            val analysis = TxAnalysisResults(
                account = account,
                receivingSats = 0L,
                sendingSats = 1000L,
                receivingTokenTypes = 0L,
                sendingTokenTypes = 0L,
                imSpendingTokenTypes = 0L,
                otherInputSatoshis = null,
                myInputSatoshis = 1000L,
                myInputTokenInfo = emptyMap(),
                sendingTokenInfo = emptyMap(),
                receivingTokenInfo = emptyMap(),
                myNetTokenInfo = emptyMap(),
                assetViewModel = AssetViewModel(false),
                completionException = WalletException("Some failure reason\n00aabbccddeeff")
            )
            tp.proposalAnalysis.value = analysis
            tp.pill.account.value = account

            val unlock = UnlockViewModel(MutableStateFlow(account))

            setContent {
                SpecialTxPermScreen(tp, unlock)
            }

            onNodeWithText("Some failure reason").assertIsDisplayed()
            require(onAllNodesWithText("00aabbccddeeff", substring = true).fetchSemanticsNodes().isEmpty()) { "Tx hex after the newline leaked to the screen" }
        }
    }

    @Test
    fun sendToPermScreenTest()
    {
        val account = wallyApp!!.newAccount("sendto", 0U, "", cs)!!
        try
        {
            runComposeUiTest {
                val tp = TricklePaySession(wallyApp!!.tpDomains)
                setContent {
                    SendToPermScreen( tp, ScreenNav())
                }
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun assetInfoPermScreenTest()
    {
        val account = wallyApp!!.newAccount("assetInfo", 0U, "", cs)!!
        try
        {
            waitFor(15000, { "Cannot connect to ${cs.name} network!  Run a local full node."}) {
                account.chain.net.p2pCnxns.size > 0
            }
            runComposeUiTest {
                val tp = TricklePaySession(wallyApp!!.tpDomains)
                setContent {
                    AssetInfoPermScreen(account, tp, ScreenNav())
                }
                /**
                 * Assert text is displayed and click "deny"
                 */
                onNodeWithText(i18n(S.TpAssetRequestFrom)).assertIsDisplayed()
                onNodeWithText(i18n(S.TpHandledByAccount)).assertIsDisplayed()
                onNodeWithText(i18n(S.TpAssetInfoNotXfer)).assertIsDisplayed()
                onNodeWithText(i18n(S.accept)).assertIsDisplayed()
                onNodeWithText(i18n(S.deny)).assertIsDisplayed()
                onNodeWithText(i18n(S.deny)).performClick()
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun identityPermScreenUriNullTest()
    {
        val account = AccountImpl("identityPerm", chainSelector = cs)
        try
        {
            runComposeUiTest {
                setSelectedAccount(account)
                val nav = ScreenNav()
                val sess = IdentitySession(null)
                setContent {
                    IdentityPermScreen(sess, nav)
                }
                LogIt.info("Identity perm screen up")
                settle()
                LogIt.info("Identity perm test done")
            }
        }
        finally
        {
            account.delete()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun acceptAndDenyDisplayedWith40Assets() {
        val account = try
        {
            wallyApp!!.newAccount("nexaTest1", 0U, "", cs)!!
        }
        catch (e: Exception)
        {
            println(sourceLoc() + ": ERROR creating nexaTest1: $e")
            throw e
        }
        try
        {
            val unlockViewModel = UnlockViewModel(MutableStateFlow(account))
            val tricklePaySession = tricklePaySessionFaker(account)


            runComposeUiTest {
                setContent {
                    SpecialTxPermScreen(tricklePaySession, unlockViewModel)
                }
                settle()

                onNodeWithText(i18n(S.deny)).assertIsDisplayed()
                onNodeWithText(i18n(S.accept)).assertIsDisplayed()
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }
}
