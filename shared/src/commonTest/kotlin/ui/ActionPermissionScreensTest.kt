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
import androidx.compose.ui.test.v2.runComposeUiTest
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
import com.eygraber.uri.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SENDTO_ADDRESS = "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"

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

    /** A proposal spending 123456 finest units (1,234.56 NEXA, but native DecimalFormat omits the grouping separator) */
    private fun sendToSession(whenDone: ((String, String, Boolean?) -> Unit)? = null): TricklePaySession
    {
        val tp = TricklePaySession(wallyApp!!.tpDomains, whenDone)
        tp.proposalUrl = Uri.parse("https://shop.example.com/sendto?cookie=k1")
        tp.host = "shop.example.com"
        tp.port = 443
        tp.cookie = "k1"
        tp.topic = "checkout"
        tp.reason = "Coffee and a bun"
        tp.askReasons.add("over the automatic limit")
        tp.proposedDestinations = listOf(Pair(PayAddress(SENDTO_ADDRESS), 123456L))
        return tp
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

    /** A hand-built session (no originalTx, so nothing automatic runs) whose analysis failed with [exception] */
    private fun failedSession(account: Account, exception: Exception?, reservedCoinsWouldHelp: Boolean = false, reservedCoinsIncludeTokens: Boolean = false): TricklePaySession
    {
        val tp = TricklePaySession(wallyApp!!.tpDomains)
        tp.host = "game.example.com"
        tp.topic = "new-game"
        val mockTx = mock<iTransaction>()
        every { mockTx.toHex() } returns "deadbeef"
        every { mockTx.chainSelector } returns cs
        tp.proposedTx = mockTx
        tp.proposalAnalysis.value = TxAnalysisResults(
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
            completionException = exception,
            reservedCoinsWouldHelp = reservedCoinsWouldHelp,
            reservedCoinsIncludeTokens = reservedCoinsIncludeTokens
        )
        tp.pill.account.value = account
        return tp
    }

    @Test
    fun specialTxPermScreenInsufficientFundsShowsFriendlyTextAndRetryHint()
    {
        val account = mockAccount()

        runComposeUiTest {
            // The library's message is the raw i18n text with the tx hex after a newline
            val tp = failedSession(account, WalletNotEnoughBalanceException("send more than balance\n000200a52adeadbeef"))
            setContent { SpecialTxPermScreen(tp, UnlockViewModel(MutableStateFlow(account))) }

            onNodeWithText(i18n(S.CannotCompleteTransaction)).assertIsDisplayed()
            onNodeWithText(i18n(S.insufficentBalance)).assertIsDisplayed()
            onNodeWithText(i18n(S.TpInsufficientWillRetry)).performScrollTo().assertIsDisplayed()
            require(onAllNodesWithText("000200a52adeadbeef", substring = true).fetchSemanticsNodes().isEmpty()) { "Raw tx hex leaked to the screen" }
            // While the wallet keeps re-checking, the one button is honest about what it does: it denies the request (not an "Okay")
            onNodeWithText(i18n(S.deny)).assertIsDisplayed()
            require(onAllNodesWithText(i18n(S.Okay)).fetchSemanticsNodes().isEmpty()) { "an underfunded proposal is denied, not acknowledged" }
            require(onAllNodesWithText(i18n(S.accept)).fetchSemanticsNodes().isEmpty()) { "Accept button should not render for an uncompletable proposal" }
            // The trial did not pass, so no offer to use reserved coins
            require(onAllNodesWithTag("SpecialTxUseReserved").fetchSemanticsNodes().isEmpty()) { "Use reserved coins must only be offered when it would help" }
        }
    }

    @Test
    fun specialTxPermScreenTokenShortfallShowsTokenText()
    {
        val account = mockAccount()

        runComposeUiTest {
            val tp = failedSession(account, WalletNotEnoughTokenBalanceException("not enough tokens\n0002deadbeef"))
            setContent { SpecialTxPermScreen(tp, UnlockViewModel(MutableStateFlow(account))) }
            onNodeWithText(i18n(S.insufficentTokenBalance)).assertIsDisplayed()
            onNodeWithText(i18n(S.TpInsufficientWillRetry)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun specialTxPermScreenFlipsToAcceptWhenAnalysisSucceeds()
    {
        val account = mockAccount()

        runComposeUiTest {
            val tp = failedSession(account, WalletNotEnoughBalanceException("send more than balance"))
            setContent { SpecialTxPermScreen(tp, UnlockViewModel(MutableStateFlow(account))) }
            onNodeWithText(i18n(S.CannotCompleteTransaction)).assertIsDisplayed()
            require(onAllNodesWithText(i18n(S.accept)).fetchSemanticsNodes().isEmpty())

            // Funds arrived and the re-analysis succeeded: the error must go away and Accept/Deny must appear
            tp.proposalAnalysis.value = tp.proposalAnalysis.value!!.copy(completionException = null)
            waitForIdle()
            require(onAllNodesWithText(i18n(S.CannotCompleteTransaction)).fetchSemanticsNodes().isEmpty()) { "Error must clear once the proposal can be completed" }
            onNodeWithText(i18n(S.accept)).assertIsDisplayed()
            onNodeWithText(i18n(S.deny)).assertIsDisplayed()
        }
    }

    @Test
    fun specialTxPermScreenHidesAcceptWhileRechecking()
    {
        val account = mockAccount()

        runComposeUiTest {
            val tp = failedSession(account, null)
            setContent { SpecialTxPermScreen(tp, UnlockViewModel(MutableStateFlow(account))) }
            onNodeWithText(i18n(S.accept)).assertIsDisplayed()
            require(onAllNodesWithTag("SpecialTxAnalyzing").fetchSemanticsNodes().isEmpty())

            tp.analyzing.value = true
            waitForIdle()
            onNodeWithTag("SpecialTxAnalyzing").assertIsDisplayed()
            require(onAllNodesWithText(i18n(S.accept)).fetchSemanticsNodes().isEmpty()) { "Accept must be hidden while the proposal is being re-checked" }

            tp.analyzing.value = false
            waitForIdle()
            onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        }
    }

    @Test
    fun specialTxPermScreenOffersReservedCoinsWhenTheyWouldHelp()
    {
        val account = mockAccount()

        runComposeUiTest {
            val tp = failedSession(account, WalletNotEnoughBalanceException("send more than balance"), reservedCoinsWouldHelp = true)
            setContent { SpecialTxPermScreen(tp, UnlockViewModel(MutableStateFlow(account))) }
            onNodeWithText(i18n(S.TpReservedCoinsExplain) % mapOf("host" to "game.example.com")).performScrollTo().assertIsDisplayed()
            onNodeWithTag("SpecialTxUseReserved").performScrollTo().assertIsDisplayed()
            onNodeWithText(i18n(S.TpUseReservedCoins)).assertIsDisplayed()
            onNodeWithText(i18n(S.deny)).assertIsDisplayed()
            require(onAllNodesWithText(i18n(S.accept)).fetchSemanticsNodes().isEmpty())
            // Tapping it on a hand-built session (no request to retry) must be harmless
            onNodeWithTag("SpecialTxUseReserved").performClick()
            waitForIdle()
            onNodeWithText(i18n(S.deny)).assertIsDisplayed()
        }
    }

    @Test
    fun specialTxPermScreenOffersReservedTokensWhenTheReservedCoinsCarryTokens()
    {
        val account = mockAccount()

        runComposeUiTest {
            val tp = failedSession(account, WalletNotEnoughTokenBalanceException("not enough tokens"), reservedCoinsWouldHelp = true, reservedCoinsIncludeTokens = true)
            setContent { SpecialTxPermScreen(tp, UnlockViewModel(MutableStateFlow(account))) }
            onNodeWithTag("SpecialTxUseReserved").performScrollTo().assertIsDisplayed()
            onNodeWithText(i18n(S.TpUseReservedTokens)).assertIsDisplayed()
            require(onAllNodesWithText(i18n(S.TpUseReservedCoins)).fetchSemanticsNodes().isEmpty()) { "reserved tokens must not be offered as coins" }
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
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            val tp = sendToSession()
            tp.pill.account.value = account
            setContent {
                SendToPermScreen(tp, ScreenNav())
            }
            settle()

            onNodeWithText(i18n(S.TpSendToTitle)).assertIsDisplayed()
            // The merchant is identified by authority plus topic so the user can see who is asking
            onNodeWithText("shop.example.com:checkout").assertIsDisplayed()
            onNodeWithText(i18n(S.TpInAccount)).assertIsDisplayed()
            onNodeWithText(account.nameAndChain).assertIsDisplayed()
            onNodeWithText(i18n(S.TpSpends)).assertIsDisplayed()
            onNodeWithText(account.cryptoFormat.format(account.fromFinestUnit(123456L)) + " " + account.currencyCode).assertIsDisplayed()
            onNodeWithText("Coffee and a bun").assertIsDisplayed()
            onNodeWithText(i18n(S.TpWhyNotAuto)).assertIsDisplayed()
            onNodeWithText("over the automatic limit").assertIsDisplayed()
            onNodeWithText(i18n(S.accept)).assertIsDisplayed()
            onNodeWithText(i18n(S.deny)).assertIsDisplayed()
        }
    }

    @Test
    fun sendToPermScreenNoProposalTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            // Nothing has been proposed, so there is nothing to approve and the screen draws nothing
            val tp = TricklePaySession(wallyApp!!.tpDomains)
            tp.pill.account.value = account
            setContent {
                SendToPermScreen(tp, ScreenNav())
            }
            settle()
            onNodeWithTag("ActionPermissionScreensColumn").assertDoesNotExist()
        }
    }

    @Test
    fun sendToPermScreenNoDestinationsTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            // A proposal url without destinations is equally unapprovable
            val tp = TricklePaySession(wallyApp!!.tpDomains)
            tp.proposalUrl = Uri.parse("https://shop.example.com/sendto?cookie=k1")
            tp.pill.account.value = account
            setContent {
                SendToPermScreen(tp, ScreenNav())
            }
            settle()
            onNodeWithTag("ActionPermissionScreensColumn").assertDoesNotExist()
        }
    }

    @Test
    fun sendToPermScreenDenyTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            var replyUrl: String? = null
            val tp = sendToSession { url, _, _ -> replyUrl = url }
            tp.pill.account.value = account
            val screenNav = ScreenNav()
            screenNav.go(ScreenId.SendToPerm)
            setContent {
                SendToPermScreen(tp, screenNav)
            }
            settle()

            val before = alerts.size
            onNodeWithText(i18n(S.deny)).performClick()
            settle()

            // A denial is reported back to the merchant rather than silently dropped
            val url = assertNotNull(replyUrl)
            assertTrue(url.startsWith("https://shop.example.com:443/sendto?cookie=k1"), url)
            assertTrue(url.contains("resultcode=300"), url)
            assertAlerted(before, i18n(S.TpSendRequestDenied), AlertLevel.NOTICE)
            assertEquals(ScreenId.Splash, screenNav.currentScreen.value)
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
                onNodeWithText(i18n(S.TpAssetRequestFrom).uppercase()).assertIsDisplayed()
                onNodeWithText(i18n(S.TpAssetInfoAsk)).assertIsDisplayed()
                onNodeWithText(i18n(S.sharing)).assertIsDisplayed()
                onNodeWithText(i18n(S.TpAssetsYouOwn)).assertIsDisplayed()
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
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            val screenNav = ScreenNav()
            screenNav.go(ScreenId.IdentityOp)
            val before = alerts.size
            val sess = IdentitySession(null)
            setContent {
                IdentityPermScreen(sess, screenNav)
            }
            settle()

            // Without a uri there is nothing to consent to, so the screen refuses and pops itself
            assertAlerted(before, i18n(S.badQR))
            assertEquals(ScreenId.Splash, screenNav.currentScreen.value)
            onNodeWithText(i18n(S.yes)).assertDoesNotExist()
        }
    }

    @Test
    fun identityPermScreenLoginTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            val sess = IdentitySession(Uri.parse("nexid://perm.example.com/?op=login&chal=c1"))
            setContent {
                IdentityPermScreen(sess, ScreenNav())
            }
            settle()

            onNodeWithText(i18n(S.provideLoginQuestion)).assertIsDisplayed()
            // The user must be able to see which service is asking and which account answers
            onNodeWithText("perm.example.com").assertIsDisplayed()
            onNodeWithText(i18n(S.TpInAccount)).assertIsDisplayed()
            // The name appears both in the account pill and under the "in account" heading
            assertTrue(onAllNodesWithText(account.name).fetchSemanticsNodes().isNotEmpty())
            onNodeWithText(i18n(S.yes)).assertIsDisplayed()
            onNodeWithText(i18n(S.no)).assertIsDisplayed()
        }
    }

    @Test
    fun identityPermScreenSignTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            val sess = IdentitySession(Uri.parse("nexid://signperm.example.com/?op=sign&sign=Marzipan"))
            setContent {
                IdentityPermScreen(sess, ScreenNav())
            }
            settle()

            onNodeWithText(i18n(S.signDataQuestion)).assertIsDisplayed()
            // Never sign something the user has not been shown
            onNodeWithText(i18n(S.textToSign)).assertIsDisplayed()
            onNodeWithText("Marzipan").assertIsDisplayed()
            onNodeWithText(i18n(S.yes)).assertIsDisplayed()
        }
    }

    @Test
    fun identityPermScreenSignBinaryTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            val sess = IdentitySession(Uri.parse("nexid://signhex.example.com/?op=sign&signhex=68656c6c6f"))
            setContent {
                IdentityPermScreen(sess, ScreenNav())
            }
            settle()

            onNodeWithText(i18n(S.binaryToSign)).assertIsDisplayed()
            onNodeWithText("68656c6c6f").assertIsDisplayed()
        }
    }

    @Test
    fun identityPermScreenSignWithoutDataTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            val before = alerts.size
            val sess = IdentitySession(Uri.parse("nexid://nodata.example.com/?op=sign"))
            setContent {
                IdentityPermScreen(sess, ScreenNav())
            }
            settle()
            assertAlerted(before, i18n(S.nothingToSign))
        }
    }

    @Test
    fun identityPermScreenInvalidOpTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            val screenNav = ScreenNav()
            screenNav.go(ScreenId.IdentityOp)
            val before = alerts.size
            val sess = IdentitySession(Uri.parse("nexid://bogus.example.com/?op=teleport"))
            setContent {
                IdentityPermScreen(sess, screenNav)
            }
            settle()

            assertAlerted(before, i18n(S.badQR))
            assertEquals(ScreenId.Splash, screenNav.currentScreen.value)
        }
    }

    @Test
    fun identityPermScreenNoCandidateAccountsTest()
    {
        val account = mockAccount()
        setSelectedAccount(account)
        runComposeUiTest {
            val screenNav = ScreenNav()
            screenNav.go(ScreenId.IdentityOp)
            val before = alerts.size
            val sess = IdentitySession(Uri.parse("nexid://nocand.example.com/?op=login&chal=c1"))
            // An empty list means no account can serve this request; null would mean "any of them"
            sess.candidateAccounts = ListifyMap(emptyMap(), { true }, Comparator { a: String, b: String -> a.compareTo(b) })
            setContent {
                IdentityPermScreen(sess, screenNav)
            }
            settle()

            assertAlerted(before, i18n(S.NoAccounts))
            assertEquals(ScreenId.Splash, screenNav.currentScreen.value)
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
