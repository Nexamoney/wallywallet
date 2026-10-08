package ui

import androidx.compose.ui.test.*
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import org.nexa.threads.millinow
import org.nexa.threads.millisleep
import kotlin.test.assertNull
import info.bitcoinunlimited.www.wally.ui.SpecialTxPermScreen
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*
import org.nexa.nexarpc.NexaRpc
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val LogIt = GetLog("BU.wally.tpRegtest")

@OptIn(ExperimentalTestApi::class)
class TricklePayRegtestTest : WallyUiTestBase()
{
    val cs = ChainSelector.NEXAREGTEST

    /** Connect to the regtest node, or null (-> skip) when unreachable. */
    private fun rpcOrNull(): NexaRpc? =
        try { getNexaRpc() }
        catch (e: Throwable)
        {
            LogIt.warning("regtest node unavailable -- skipping trickle pay regtest: ${e.message}")
            null
        }

    /** Full-fidelity repro of issue #661: a tdpp proposal whose foreign input (546 sat, empty script)
     * cannot be signed by this wallet.  The wallet funds and signs its own input, txSanityCheck throws,
     * and the permission screen must show the friendly error instead of the raw dump. */
    @Test
    fun unsignedForeignInputProposalShowsFriendlyError()
    {
        val rpc = rpcOrNull() ?: return
        cleanupAccounts("tpRegtest", null)
        val account = wallyApp!!.newAccount("tpRegtest", 0U, "", cs)!!
        try
        {
            account.chain.net.exclusiveNodes(setOf(REGTEST_IP))
            val addr = account.wallet.getNewDestination().address!!
            // The malicious outputs total 250546 sat, so give the wallet 3000 NEXA (300000 sat) to fund them
            rpc.sendtoaddress(addr.toString(), BigDecimal.fromInt(3000))
            rpc.generate(1)
            waitFor(30000, { "Cannot connect to ${cs.name} network!  Run a local full node." }) {
                account.wallet.balance >= 300000L
            }

            // The offer: one foreign 546-sat input with an EMPTY script, outputs the wallet does not own
            val tx = NexaTransaction(cs)
            val foreignSp = Spendable(cs, NexaTxOutpoint(Hash256(Random.nextBytes(32))), 546)
            tx._inputs.add(NexaTxInput(cs, foreignSp, SatoshiScript(cs), 0xffffffff))
            val junkDest1 = Pay2PubKeyTemplateDestination(cs, UnsecuredSecret(ByteArray(32) { 7 }), 1)
            val junkDest2 = Pay2PubKeyTemplateDestination(cs, UnsecuredSecret(ByteArray(32) { 9 }), 2)
            tx._outputs.add(NexaTxOutput(cs, 200546L, junkDest1.lockingScript()))
            tx._outputs.add(NexaTxOutput(cs, 50000L, junkDest2.lockingScript()))

            val tp = TricklePaySession(wallyApp!!.tpDomains)
            tp.pill.account.value = account
            tp.chainSelector = cs
            tp.host = "niftyart.cash"
            tp.topic = "sell-offer"
            tp.proposedTx = tx

            val analysis = tp.analyzeCompleteAndSignTx(tx, 546L, null)
            tp.proposalAnalysis.value = analysis

            // The wallet funded and signed its own input and hit the real txSanityCheck
            assertNotNull(analysis.completionException)
            assertTrue(analysis.completionException!!.message!!.contains("not fully signed"),
              "unexpected completion error: ${analysis.completionException}")
            assertEquals(listOf(0), analysis.foreignUnsignedInputs)

            runComposeUiTest {
                val unlock = UnlockViewModel(MutableStateFlow(account))
                setContent {
                    SpecialTxPermScreen(tp, unlock)
                }
                settle()

                onNodeWithText(i18n(S.TpProposalMissingSignature)).assertIsDisplayed()
                onNodeWithText(i18n(S.Okay)).assertIsDisplayed()
                require(onAllNodesWithText("Unsigned input indexes", substring = true).fetchSemanticsNodes().isEmpty()) { "Raw completion exception leaked to the screen" }
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    private fun waitUntil(timeoutMs: Long, desc: String, cond: () -> Boolean)
    {
        val start = millinow()
        while (millinow() - start < timeoutMs)
        {
            if (cond()) return
            millisleep(300U)
        }
        error("timed out after ${timeoutMs}ms waiting for: $desc")
    }

    /** A /tx request from [host] paying [amount] satoshis to [payee] (someone who is not this wallet) */
    private fun requestSession(flags: Int, amount: Long, host: String, topic: String, payee: PayAddress): TricklePaySession
    {
        val tx = txFor(cs)
        tx.add(NexaTxOutput(cs, amount, payee.outputScript()))
        val session = TricklePaySession(wallyApp!!.tpDomains)
        session.chainSelector = cs
        session.handleTxAutopay(Uri.parse("https://$host/tx?host=$host&port=8080&tx=${tx.toHex()}&flags=$flags&inamt=0&chain=${chainToURI[cs]}&topic=$topic"))
        assertNotNull(session.proposalAnalysis.value)
        return session
    }

    /** Work item #691, end to end: a request the wallet cannot fund recovers by itself when the coins arrive while the permission screen
     * is showing, and a later request from the same site can reuse the coins an earlier partial transaction left reserved, once the
     * node confirms they are unspent. */
    @Test
    fun insufficientProposalRecoversWhenFundsArriveAndReservedCoinsCanBeReused()
    {
        val rpc = rpcOrNull() ?: return
        val payee = PayAddress(rpc.getnewaddress())
        cleanupAccounts("tpRecover", null)
        val account = wallyApp!!.newAccount("tpRecover", 0U, "", cs)!!
        try
        {
            account.chain.net.exclusiveNodes(setOf(REGTEST_IP))
            waitUntil(60_000, "wallet connected") { account.chain.net.p2pCnxns.size > 0 }
            waitUntil(60_000, "wallet synced") { account.wallet.syncedHeight >= rpc.getblockcount() }
            setSelectedAccount(account)
            val unlock = UnlockViewModel(MutableStateFlow(account))

            // The empty wallet cannot fund 2500 NEXA
            val first = requestSession(0, 250_000L, "game.example.com", "new-game", payee)
            assertTrue(first.proposalAnalysis.value!!.isInsufficientFunds, "expected insufficient funds: ${first.proposalAnalysis.value!!.completionException}")
            runComposeUiTest {
                setContent { SpecialTxPermScreen(first, unlock) }
                settle()
                onNodeWithText(i18n(S.TpInsufficientWillRetry)).assertIsDisplayed()

                // Funds arrive while the screen is showing: the watcher it started re-checks and the proposal becomes acceptable
                rpc.sendtoaddress(account.wallet.getNewAddress().toString(), BigDecimal.fromInt(3000))
                rpc.generate(1)
                waitUntil(90_000, "proposal re-checked and completable") { first.proposalAnalysis.value?.completionException == null }
                settle()
                onNodeWithText(i18n(S.accept)).assertIsDisplayed()
            }
            first.stopWatching()
            first.releaseIfUndecided()  // what leaving the screen does
            var reserved = 0
            account.wallet.forEachUtxo { if (it.isUnspent && it.reserved != 0L) reserved++; false }
            assertEquals(0, reserved)

            // A partial transaction handed to the game keeps the (only) coin reserved
            val partial = requestSession(TDPP_FLAG_PARTIAL or TDPP_FLAG_NOPOST, 100_000L, "game.example.com", "new-game", payee)
            assertNull(partial.proposalAnalysis.value!!.completionException)
            partial.acceptSpecialTx()
            assertEquals(1, TdppPartialLedger.candidates(account.wallet, "game.example.com", "new-game").size)

            // The next request from the same game cannot be funded, but the trial shows the reserved coin would do it
            val next = requestSession(0, 100_000L, "game.example.com", "new-game", payee)
            assertTrue(next.proposalAnalysis.value!!.isInsufficientFunds)
            assertTrue(next.proposalAnalysis.value!!.reservedCoinsWouldHelp)
            assertTrue(TdppPartialLedger.stillReserved(account.wallet, TdppPartialLedger.candidates(account.wallet, "game.example.com", "new-game")).isNotEmpty())

            // Using it: the node confirms the coin is unspent, the reservation is released and the request completes
            runComposeUiTest {
                setContent { SpecialTxPermScreen(next, unlock) }
                settle()
                onNodeWithTag("SpecialTxUseReserved").performScrollTo().performClick()
                waitUntil(60_000, "retry with reserved coins completed") { next.proposalAnalysis.value?.completionException == null }
                settle()
                onNodeWithTag("SpecialTxUsingReserved").assertExists()
                onNodeWithText(i18n(S.accept)).assertIsDisplayed()
            }
            assertTrue(next.usingReservedCoins.value)
            assertEquals(0, TdppPartialLedger.candidates(account.wallet, "game.example.com", "new-game").size)
            next.stopWatching()
            next.releaseIfUndecided()
        }
        finally
        {
            TdppPartialLedger.clear()
            wallyApp!!.deleteAccount(account)
        }
    }
}
