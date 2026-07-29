/*
 * addressWatcherTemplate.kt — a drop-in monitor for arbitrary scripts/outpoints you do NOT own,
 * built on libnexakotlin's ElectrumClient.
 *
 * The electrum client has NO per-address subscription — the only push is block headers
 * (subscribeHeaders). So this watcher does an initial poll, then re-polls on every new block.
 * Use it to watch a contract's UTXO, a counterparty's address, or any script your own wallet
 * doesn't hold the keys to. For your OWN wallet's funds, use the wallet's setOnWalletChange
 * (nexaWalletLifecycleAndChain) instead — don't reimplement wallet tracking on electrum.
 *
 * See electrumMethodReference.md for every method/field used here, and SKILL.md for the trust
 * model (untrusted server; query reveals interest; validate fetched txs).
 */

import org.nexa.libnexakotlin.ElectrumClient
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.SatoshiScript
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.ElectrumNotFound

/**
 * Watches a set of scripts (for incoming/spending activity) and a set of outpoints (for the
 * moment they are spent — the contract-spend signal). Polls once at start, then on each new block.
 */
class AddressWatcher(
    private val electrum: ElectrumClient,
    /** Number of confirmations before a sighting is treated as settled (value-dependent; see SKILL). */
    private val confirmationsForFinal: Int = 1,
) {
    // last-seen history length per watched script, so we only fire on *new* activity.
    private val scriptHistorySeen = mutableMapOf<String, Int>()
    private val watchedScripts = mutableListOf<Pair<SatoshiScript, (txid: Hash256, height: Int) -> Unit>>()
    // outpoints we're waiting to see spent: "outpointHex" -> callback.
    private val watchedOutpoints = mutableMapOf<String, () -> Unit>()

    /** Watch a script; `onActivity(txid, height)` fires for each newly-seen tx touching it. */
    fun watchScript(script: SatoshiScript, onActivity: (txid: Hash256, height: Int) -> Unit) {
        watchedScripts.add(script to onActivity)
    }

    /** Watch an outpoint (e.g. a contract UTXO); `onSpent()` fires once it's no longer unspent. */
    fun watchOutpointSpent(outpointHex: String, onSpent: () -> Unit) {
        watchedOutpoints[outpointHex] = onSpent
    }

    /** Start: poll once now, then re-poll on every new block header. */
    fun start() {
        poll()                                          // initial poll — don't wait for the next block
        electrum.subscribeHeaders { _ -> poll() }       // the ONLY push electrum gives us
    }

    fun stop() = electrum.unsubscribeHeaders()

    private fun poll() {
        val tipHeight = runCatching { electrum.getTip().second }.getOrNull()

        // 1) Script activity: compare current history length to what we last saw.
        for ((script, cb) in watchedScripts) {
            val key = script.scriptHash().toHex()
            val history = runCatching { electrum.getHistory(script) }.getOrNull() ?: continue
            val seen = scriptHistorySeen[key] ?: 0
            if (history.size > seen) {
                // newest entries first or last depending on server; iterate the new tail
                for (i in seen until history.size) {
                    val (height, txid) = history[i]
                    cb(txid, height)                    // height <= 0 means unconfirmed (mempool)
                }
                scriptHistorySeen[key] = history.size
            }
        }

        // 2) Outpoint spends: a SPENT outpoint returns status == "spent" (with the spending tx in
        //    .spent); ElectrumNotFound means the server doesn't know the outpoint at all —
        //    it never fires for a genuinely spent outpoint (see SKILL.md Pattern 3).
        val spent = mutableListOf<String>()
        for ((outpointHex, onSpent) in watchedOutpoints) {
            try {
                val utxo = electrum.getUtxo(outpointHex)
                if (utxo.status == "spent") {           // consumed — utxo.spent has the spending tx
                    onSpent()
                    spent.add(outpointHex)
                } else if (utxo.height > 0 && tipHeight != null &&
                    (tipHeight - utxo.height + 1) >= confirmationsForFinal) {
                    // still unspent and deep enough — your code may treat the funding as final here
                }
            } catch (e: ElectrumNotFound) {
                // outpoint unknown to the server: never existed / bad hex / wrong chain — a
                // watch-list bug, not a spend. Drop it so we don't re-query forever.
                spent.add(outpointHex)
            }
            // let other electrum errors (timeout, connect) propagate / be retried next block
        }
        spent.forEach { watchedOutpoints.remove(it) }   // stop watching once fired
    }
}

/*
 * Usage:
 *   val electrum = ElectrumClient(ChainSelector.NEXA, electrumServerHost)   // useSSL=true → SSL port
 *   val watcher = AddressWatcher(electrum, confirmationsForFinal = 2)
 *   watcher.watchOutpointSpent(contractUtxoOutpointHex) { onContractSpent() }
 *   watcher.watchScript(payoutScript) { txid, h -> creditOnceConfirmed(txid, h) }
 *   watcher.start()
 *
 * Trust note: one untrusted server can lie by omission (hide a tx). For anything you settle on,
 * cross-check a second server or your own node, and validate any tx you fetch before acting
 * (SKILL.md Security).
 */