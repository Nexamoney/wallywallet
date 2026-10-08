package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import org.nexa.threads.Mutex

private val LogIt = GetLog("BU.wally.tpledger")

/** Remembers the coins that partial (not yet completable) TDPP transactions handed to a server are holding reserved, so that they
 * can be identified later.
 *
 * libnexakotlin reserves a UTXO when it puts it into a transaction but has no record of *which* transaction reserved it, so when a
 * server abandons a partial transaction (an opponent cancels a game, for example) the wallet has no way to tell that those coins are
 * free again.  This ledger is the missing bookkeeping: it lets a later request from the same site offer to "use reserved coins" by
 * releasing exactly the reservations that an earlier request from that site left behind.  Only the coins the wallet's completer
 * reserved for the transaction are recorded; inputs the request itself named are never reserved by the completer and may be held
 * by some other flow, so they are not the ledger's to release.
 *
 * The ledger is RAM only, which matches the lifetime of [Spendable.reserved] (reservations are not persisted, so a restart clears
 * both).
 */
object TdppPartialLedger
{
    class Entry(val walletName: String, val host: String, val topic: String, val idem: Hash256, val outpoints: Set<iTxOutpoint>)

    private val lock = Mutex("tdppLedger")
    private val entries = mutableListOf<Entry>()

    private fun topicKey(topic: String?): String = topic ?: ""

    /** Record that the partial transaction [tx], handed to [host]/[topic], is holding [outpoints] reserved in [wallet] */
    fun record(wallet: Wallet, host: String?, topic: String?, tx: iTransaction, outpoints: Set<iTxOutpoint>)
    {
        if (host == null) return
        val held = outpoints.filter { op -> wallet.getTxo(op)?.let { it.isUnspent && it.reserved != 0L } == true }.toSet()
        if (held.isEmpty()) return
        val entry = Entry(wallet.name, host, topicKey(topic), tx.idem, held)
        lock.lock { entries.add(entry) }
        LogIt.info("recorded partial TDPP tx ${tx.idem.toHex()} for $host:${topicKey(topic)} holding ${held.size} coins")
    }

    /** Entries from [host]/[topic] whose partial transaction is still holding at least one unspent, reserved coin in [wallet].
     * Entries that no longer hold anything (the coins were spent or released, or the transaction was broadcast) are forgotten. */
    fun candidates(wallet: Wallet, host: String?, topic: String?): List<Entry>
    {
        if (host == null) return listOf()
        val tk = topicKey(topic)
        return lock.lock {
            val stale = mutableListOf<Entry>()
            val ret = mutableListOf<Entry>()
            for (e in entries)
            {
                if (e.walletName != wallet.name) continue
                val holding = wallet.getTx(e.idem) == null && e.outpoints.any { op -> wallet.getTxo(op)?.let { it.isUnspent && it.reserved != 0L } == true }
                if (!holding) stale.add(e)
                else if (e.host == host && e.topic == tk) ret.add(e)
            }
            entries.removeAll(stale)
            ret
        }
    }

    /** The coins that [entries] are still holding reserved in [wallet] */
    fun stillReserved(wallet: Wallet, entries: List<Entry>): List<Spendable>
    {
        val seen = mutableSetOf<iTxOutpoint>()
        val ret = mutableListOf<Spendable>()
        for (e in entries)
            for (op in e.outpoints)
            {
                if (!seen.add(op)) continue
                val sp = wallet.getTxo(op) ?: continue
                if (sp.isUnspent && sp.reserved != 0L) ret.add(sp)
            }
        return ret
    }

    private fun giveBack(wallet: Wallet, outpoints: Collection<iTxOutpoint>): Int
    {
        // Through abortTransaction on a synthetic transaction holding only these inputs, so that no transaction history is touched
        val synthetic = txFor(wallet.chainSelector)
        for (op in outpoints) wallet.getTxo(op)?.let { if (it.reserved != 0L) synthetic.add(txInputFor(it), SPENDABLE_UNRESERVED) }
        if (synthetic.inputs.isNotEmpty()) wallet.abortTransaction(synthetic)
        return synthetic.inputs.size
    }

    /** Release the reservations on exactly [outpoints] (which must belong to [entries]) and forget those entries */
    fun release(wallet: Wallet, entries: List<Entry>, outpoints: Set<iTxOutpoint>)
    {
        val n = giveBack(wallet, outpoints)
        lock.lock { this.entries.removeAll(entries) }
        LogIt.info("released $n coins reserved by ${entries.size} abandoned partial TDPP transactions")
    }

    /** The server reported it never stored [tx]: release the coins recorded for it and forget it */
    fun releaseFor(wallet: Wallet, tx: iTransaction)
    {
        val mine = lock.lock { entries.filter { it.walletName == wallet.name && it.idem == tx.idem } }
        if (mine.isEmpty()) return
        release(wallet, mine, mine.flatMap { it.outpoints }.toSet())
    }

    fun removeForWallet(walletName: String)
    {
        lock.lock { entries.removeAll { it.walletName == walletName } }
    }

    fun size(): Int = lock.lock { entries.size }

    fun clear()
    {
        lock.lock { entries.clear() }
    }
}
