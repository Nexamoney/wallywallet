/*
 * regtestHarness.kt — a drop-in test fixture for end-to-end testing against a local regtest node
 * via org.nexa:nexarpc. Mines starter coins, then exposes fund/broadcast/confirm helpers.
 *
 * This is the COMPLEMENT to nexaScriptMachineTesting: the script VM checks script validity offline;
 * regtest exercises the real thing — fees, mempool policy, and confirmations. Use both (VM for fast
 * script-logic checks while developing; regtest for full end-to-end).
 *
 * Reach for regtest specifically when a test needs to CONTROL block production — force-mining
 * (generate), deterministic confirmations, or reorgs (invalidateblock). For ordinary development
 * that doesn't need that control, testnet is the default chain (see nexa-wallet-lifecycle-and-chain,
 * "Which chain do I develop on?").
 *
 * Prerequisite: a nexad running in regtest mode reachable at the URL below (default
 * http://127.0.0.1:18332/, user/pwd regtest/regtest). Declare nexarpc as testImplementation.
 * See rpcMethodReference.md for every call used here, and SKILL.md for the security model
 * (RPC creds = full node control; never point this at a non-regtest node).
 */

import org.nexa.nexarpc.NexaRpc
import org.nexa.nexarpc.NexaRpcFactory
import org.nexa.nexarpc.NexaRpcException
import org.nexa.nexarpc.HashId

class RegtestHarness(
    url: String = "http://127.0.0.1:18332/",
    user: String = "regtest",
    password: String = "regtest",
) {
    val rpc: NexaRpc = NexaRpcFactory.create(url, user, password)

    /** Mine `blocks` blocks. On a fresh regtest, mine >100 to mature a coinbase into spendable coin. */
    fun mine(blocks: Int): List<HashId> = rpc.generate(blocks)

    /**
     * Ensure the node wallet has spendable balance, mining coinbase-maturity blocks if needed.
     * Returns the spendable balance after mining.
     */
    fun ensureFunds(minimumBalanceSats: Long = 1, maturityBlocks: Int = 101): Long {
        if (rpc.listunspent().sumOf { it.satoshi } < minimumBalanceSats) {
            mine(maturityBlocks)                       // mature a coinbase so it becomes spendable
        }
        // Use .satoshi (Long), never .amount (lossy Double) — see rpcMethodReference.md.
        return rpc.listunspent().filter { it.spendable }.sumOf { it.satoshi }
    }

    /** A fresh address from the node wallet (handy as a funding source / sink in tests). */
    fun newAddress(): String = rpc.getnewaddress()

    /**
     * Broadcast a raw tx (hex), folding "already known" into success so retries don't spuriously
     * fail. Returns the txid, null if the node already had the tx (no id is returned in that
     * reply — track it at send time if you need it), or rethrows a genuine rejection.
     */
    fun broadcast(txHex: String): HashId? = try {
        rpc.sendrawtransaction(txHex)
    } catch (e: NexaRpcException) {
        val m = e.message ?: ""
        if (m.contains("already-in-mempool", true) || m.contains("already in block", true)) {
            null                                      // already accepted earlier in this test — success
        } else throw e
    }

    /** Mine `n` blocks to confirm a just-broadcast tx, then return its confirmation count. */
    fun confirm(txid: HashId, blocks: Int = 1): Long {
        mine(blocks)
        return rpc.gettransactiondetails(txid).let { details ->
            // gettransactiondetails works for any tx; in_txpool=false after it's mined into a block
            if (details.in_txpool) 0L else blocks.toLong()
        }
    }

    /** Roll back to before `blockHash` — for testing reorg / 0-conf-invalidation handling. */
    fun reorgBefore(blockHash: HashId) = rpc.invalidateblock(blockHash)

    /** Exact median-time-past at the tip — for testing CLTV/timeout spends (see nexaLocktimeCltv). */
    fun tipMedianTimePast(): Long = rpc.getblock(rpc.getbestblockhash()).mediantime
}

/*
 * Usage (JUnit):
 *
 *   val h = RegtestHarness()
 *   h.ensureFunds()                                  // mine maturity blocks if the wallet is empty
 *   val txid = h.broadcast(myTxHex)                  // send your built tx (null = node already had it)
 *   checkNotNull(txid)                               // first broadcast in a test should be fresh
 *   assert(h.confirm(txid, blocks = 1) >= 1)         // mine + check it confirmed
 *
 * SECURITY: regtest only. `generate`/`invalidateblock` and the default regtest/regtest creds are
 * powers/credentials that must never touch testnet or mainnet. See SKILL.md.
 */