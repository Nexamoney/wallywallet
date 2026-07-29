/*
 * mtpMonitor.kt — estimate the chain's current median-time-past (MTP) for deciding whether a
 * timestamp `nLockTime` deadline has been reached.
 *
 * Why this exists: a timestamp CLTV becomes spendable only once MTP passes it, and MTP is the
 * MEDIAN OF THE LAST 11 BLOCK TIMESTAMPS — it trails wall-clock (see cltvCheatsheet.md). There is
 * no single libnexakotlin "getMedianTimePast" call, so this computes it from block-header times.
 *
 * Three sources, most-exact first:
 *   1. node operator:   nexaRpc.getblock(...).mediantime          (exact; see nexaRpcNodeClient)
 *   2. light client:    ElectrumClient.getHeadersFor(...) + median (this file's primary helper)
 *   3. quick bound:     Blockchain.getTip()?.time                  (an UPPER bound on MTP; optimistic)
 *
 * iBlockHeader.time is a Long (epoch seconds). LOCKTIME_THRESHOLD = 500_000_000 splits height vs
 * timestamp locktimes.
 */

import org.nexa.libnexakotlin.Blockchain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.ElectrumClient
import org.nexa.libnexakotlin.iBlockHeader

const val LOCKTIME_THRESHOLD = 500_000_000L      // >= this is a timestamp; below is a block height

/**
 * Exact MTP via a light client: median of the last 11 block-header timestamps ending at the tip.
 * Use this when you don't operate a node. Returns null if headers can't be fetched.
 */
fun estimateMtpViaElectrum(electrum: ElectrumClient): Long? {
    val (_, tipHeight) = runCatching { electrum.getTip() }.getOrNull() ?: return null
    val start = maxOf(0, tipHeight.toInt() - 10)                      // last 11 blocks: tip-10 .. tip
    val headers: List<iBlockHeader> =
        runCatching { electrum.getHeadersFor(electrum.chainSelector, start, 11) }.getOrNull() ?: return null
    if (headers.isEmpty()) return null
    return medianTime(headers)
}

/** The consensus MTP definition: the median of the (up to 11) header timestamps. */
fun medianTime(headers: List<iBlockHeader>): Long {
    val times = headers.map { it.time }.sorted()
    return times[times.size / 2]                                     // median (odd count → middle element)
}

/**
 * A quick UPPER BOUND on MTP: the tip block's own timestamp. MTP <= tip.time always (MTP lags),
 * so this is OPTIMISTIC for finality decisions:
 *   - tip.time < deadline  ⇒  MTP < deadline too  ⇒  definitely NOT final yet (safe to act on).
 *   - tip.time >= deadline ⇒  MTP MIGHT still be behind  ⇒  do NOT conclude "final"; check real MTP.
 * Use it only as a cheap "is it even close yet?" gate, never as the finality signal.
 */
fun tipTimeUpperBound(blockchain: Blockchain): Long? = blockchain.getTip()?.time

/**
 * Decide whether a TIMESTAMP locktime deadline has been reached, given an MTP value.
 * (For a HEIGHT locktime — deadline < LOCKTIME_THRESHOLD — compare blockchain.curHeight instead.)
 */
fun timestampDeadlineReached(deadlineEpochSeconds: Long, currentMtp: Long): Boolean {
    require(deadlineEpochSeconds >= LOCKTIME_THRESHOLD) {
        "deadline $deadlineEpochSeconds looks like a block height, not a timestamp; compare curHeight"
    }
    return currentMtp >= deadlineEpochSeconds
}

/*
 * Usage:
 *
 *   // Light client (no node):
 *   val mtp = estimateMtpViaElectrum(electrum)
 *   val canRefund = mtp != null && timestampDeadlineReached(refundDeadline, mtp)
 *
 *   // Node operator — exact MTP (see nexaRpcNodeClient):
 *   //   val mtp = nexaRpc.getblock(nexaRpc.getbestblockhash()).mediantime
 *
 *   // Quick optimistic gate only:
 *   val notYet = (tipTimeUpperBound(blockchain) ?: 0) < refundDeadline   // true ⇒ definitely not final
 *
 * Remember the OTHER half of the rule: the spend's input must set nSequence < 0xFFFFFFFF or the
 * locktime is ignored entirely (cltvCheatsheet.md / nexaTransactionConstruction).
 */