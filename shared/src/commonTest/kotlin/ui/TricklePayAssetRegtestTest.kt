package ui

import androidx.compose.ui.test.ExperimentalTestApi
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import org.nexa.libnexakotlin.*
import org.nexa.nexarpc.NexaRpc
import org.nexa.threads.millinow
import org.nexa.threads.millisleep
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val LogIt = GetLog("BU.wally.tpAssetRegtest")

/**
 * Issue #623: the TDPP `/assets` query must offer quantity-bearing token UTXOs only -- a group
 * authority (mint/melt/rescript) is not transferable value and must never be handed to a
 * requesting site (the fix in MR !700).
 *
 * The group filter (`af`) only constrains the *script prefix*, and an authority output carries the
 * same group id prefix as a quantity output, so nothing but [GroupInfo.isAuthority] separates them.
 * That makes this only testable against real grouped UTXOs, which is what a **Nexa regtest full
 * node** provides here: the node issues a group, sends tokens to the wallet, and mints an authority
 * into the very same wallet (same harness [SystemTests] uses: [getNexaRpc] / [REGTEST_IP]).
 *
 * Requires a Nexa regtest node reachable at [REGTEST_IP]:`NexaRegtestRpcPort`
 * (rpc user/password `regtest`/`regtest`). When none is running the tests log and **skip**
 * (return) rather than fail, so the default offline suite stays green.
 */
@OptIn(ExperimentalTestApi::class)
class TricklePayAssetRegtestTest : WallyUiTestBase(openAllAccounts = false)
{
    private val cs = ChainSelector.NEXAREGTEST
    private val createdAccounts = mutableListOf<String>()

    /** Connect to the regtest node, or null (-> skip) when unreachable. */
    private fun rpcOrNull(): NexaRpc? =
        try { getNexaRpc() }
        catch (e: Throwable)
        {
            LogIt.warning("regtest node unavailable -- skipping trickle pay asset regtest: ${e.message}")
            null
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

    private fun newFundedAccount(rpc: NexaRpc, name: String, fundNexa: Int): Account
    {
        cleanupAccounts(name, 3)
        val account = wallyApp!!.newAccount(name, 0U, "", cs) ?: error("could not create regtest account $name")
        createdAccounts += name
        account.chain.net.exclusiveNodes(setOf(REGTEST_IP))
        // Fund only once the SPV wallet is connected and caught up, otherwise the funding block
        // can arrive before the wallet's bloom filter is on the node and the tx is never seen
        waitUntil(60_000, "wallet $name connected") { account.chain.net.p2pCnxns.size > 0 }
        waitUntil(60_000, "wallet $name synced") { account.wallet.syncedHeight >= rpc.getblockcount() }
        rpc.sendtoaddress(account.wallet.getNewAddress().toString(), BigDecimal.fromInt(fundNexa))
        rpc.generate(1)
        waitUntil(60_000, "account $name funded") { account.wallet.balanceConfirmed > 0L }
        return account
    }

    @AfterTest
    fun cleanupCreatedAccounts()
    {
        wallyApp!!.focusedAccount.value = null
        for (name in createdAccounts)
        {
            wallyApp!!.accounts[name]?.let { runCatching { wallyApp!!.deleteAccount(it) } }
        }
        createdAccounts.clear()
    }

    /** The account's unspent outputs in [groupId], split into (quantity outputs, authority outputs) */
    private fun groupUtxos(account: Account, groupId: GroupId): Pair<List<Spendable>, List<Spendable>>
    {
        val quantity = mutableListOf<Spendable>()
        val authority = mutableListOf<Spendable>()
        account.wallet.forEachUtxo { sp ->
            val gi = sp.groupInfo()
            if ((gi != null) && (gi.groupId == groupId))
            {
                if (gi.isAuthority()) authority.add(sp) else quantity.add(sp)
            }
            false
        }
        return Pair(quantity, authority)
    }

    /** An asset filter selecting every output of [groupId], as a site would send it in `af` */
    private fun assetFilterHex(groupId: GroupId): String =
        SatoshiScript(cs, SatoshiScript.Type.TEMPLATE, OP.push(groupId.toByteArray()), OP.TMPL_DATA).toHex()

    /** The group carried by a returned entry's serialized prevout */
    private fun replyGroupInfo(entry: TricklePayAssetInfo): GroupInfo
    {
        val out = NexaTxOutput(cs, BCHserialized(entry.prevout.fromHex(), SerializationType.NETWORK))
        return out.script.groupInfo(out.amount) ?: error("returned asset ${entry.outpointHash} is not a token")
    }

    /** Run the production `/assets` request against [account], returning what the wallet offers */
    private fun requestAssets(account: Account, host: String, filterHex: String): List<TricklePayAssetInfo>
    {
        setSelectedAccount(account)
        val tp = TricklePaySession(wallyApp!!.tpDomains)
        val uri = Uri.parse("tdpp://$host/assets?chain=${chainToURI[cs]}&af=$filterHex")
        val action = tp.handleAssetInfoRequest(uri)
        assertNotEquals(TdppAction.DENY, action, "the wallet denied the asset query")
        assertEquals(account, tp.pill.account.value, "the query must be served by ${account.name}")
        return assertNotNull(tp.assetInfoList, "the asset query must produce a list").assets
    }

    /**
     * A wallet holding both quantity outputs and a mint authority for the same group offers only
     * the quantity outputs, and a wallet holding only quantity outputs still matches as before.
     */
    @Test
    fun assetInfoOffersTokensNotAuthorities()
    {
        val rpc = rpcOrNull() ?: return
        val holder = newFundedAccount(rpc, "tpAssetHolder", 2000)

        // The node issues the group and keeps the genesis authorities
        val nodeAddr = rpc.getnewaddress()
        val (groupIdStr, _) = rpc.tokenNew(address = nodeAddr, tokenTicker = "AFT", tokenName = "AssetFilterTest")
        rpc.tokenMint(groupIdStr, nodeAddr, 1000)
        rpc.generate(1)
        val groupId = GroupId(groupIdStr)
        val filterHex = assetFilterHex(groupId)

        val qty = 25L
        rpc.tokenSend(groupIdStr, holder.wallet.getNewAddress().toString(), qty.toInt())
        rpc.generate(1)
        waitUntil(60_000, "holder received the tokens") { groupUtxos(holder, groupId).first.isNotEmpty() }

        // Regression: a wallet holding only regular asset tokens matches as before
        val tokensOnly = requestAssets(holder, "assetfilter.regtest", filterHex)
        assertEquals(1, tokensOnly.size, "the holder's single token UTXO must be offered")
        assertEquals(qty, replyGroupInfo(tokensOnly[0]).tokenAmount)

        // Now mint a mint-authority for the same group into the same wallet
        rpc.tokenAuthorityCreate(groupIdStr, holder.wallet.getNewAddress().toString(), listOf("mint"))
        rpc.generate(1)
        waitUntil(60_000, "holder received the authority") { groupUtxos(holder, groupId).second.isNotEmpty() }

        // The authority's script does satisfy the asset filter -- only the isAuthority() guard
        // keeps it out of the reply, so without that guard this wallet would offer it.
        val authority = groupUtxos(holder, groupId).second.first()
        val asSent = SatoshiScript(cs, SatoshiScript.Type.SATOSCRIPT, filterHex.fromHex())
        assertNotNull(authority.priorOutScript.matches(asSent, true), "the asset filter must match the authority script")

        val withAuthority = requestAssets(holder, "assetfilter.regtest", filterHex)
        assertEquals(tokensOnly.map { it.outpointHash }.toSet(), withAuthority.map { it.outpointHash }.toSet(),
          "holding an authority must not change what the wallet offers")
        for (entry in withAuthority) assertFalse(replyGroupInfo(entry).isAuthority(), "offered ${entry.outpointHash} is an authority")
    }

    /** A wallet holding only an authority for the requested group offers nothing. */
    @Test
    fun assetInfoIgnoresAuthorityOnlyWallet()
    {
        val rpc = rpcOrNull() ?: return
        val account = newFundedAccount(rpc, "tpAssetAuthority", 2000)

        val nodeAddr = rpc.getnewaddress()
        val (groupIdStr, _) = rpc.tokenNew(address = nodeAddr, tokenTicker = "AOT", tokenName = "AuthorityOnlyTest")
        rpc.generate(1)
        val groupId = GroupId(groupIdStr)

        rpc.tokenAuthorityCreate(groupIdStr, account.wallet.getNewAddress().toString(), listOf("mint", "melt"))
        rpc.generate(1)
        waitUntil(60_000, "wallet received the authority") { groupUtxos(account, groupId).second.isNotEmpty() }
        assertTrue(groupUtxos(account, groupId).first.isEmpty(), "this wallet must hold no quantity outputs")

        val matches = requestAssets(account, "authorityonly.regtest", assetFilterHex(groupId))
        assertEquals(0, matches.size, "an authority-only wallet must offer nothing: $matches")
    }
}
