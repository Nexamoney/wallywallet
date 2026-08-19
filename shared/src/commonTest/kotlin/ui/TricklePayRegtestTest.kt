package ui

import androidx.compose.ui.test.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
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
}
