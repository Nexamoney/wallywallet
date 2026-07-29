/*
 * broadcast-tx.kt — a broadcast wrapper with sensible retry + error categorization.
 *
 * Two broadcast paths (SKILL.md Pattern 3 + its RPC alternative):
 *   - SPV/connection-manager:  blockchain.net.broadcastTransaction(tx.toByteArray())
 *   - your own full node:      nexarpc sendrawtransaction(txHex)  (see nexaRpcNodeClient)
 *
 * The crucial rule both share: "already in mempool / already in block chain" is SUCCESS, not
 * failure — any retry/auto-claim loop WILL re-broadcast the same tx, and treating the duplicate as
 * an error makes it spin (SKILL.md "Treating an 'already in mempool / block' broadcast result as a
 * failure"). Grounded in libnexakotlin cnxnmgr.kt broadcastTransaction(ByteArray).
 */

import org.nexa.libnexakotlin.Blockchain
import org.nexa.libnexakotlin.iTransaction

/** Categorized outcome so callers don't string-match error messages themselves. */
sealed class BroadcastOutcome {
    data class Accepted(val alreadyKnown: Boolean) : BroadcastOutcome()   // in mempool or a block now
    data class FeeTooLow(val message: String) : BroadcastOutcome()        // unfixable without re-funding
    data class Rejected(val message: String) : BroadcastOutcome()         // invalid / double-spent / other
    data class TransientError(val message: String) : BroadcastOutcome()   // connectivity — worth retrying
}

private fun categorize(message: String): BroadcastOutcome = when {
    message.contains("txn-already-in-mempool", true) ||
    message.contains("already in block chain", true) ||
    message.contains("already known", true)                  -> BroadcastOutcome.Accepted(alreadyKnown = true)
    message.contains("mempool min fee not met", true) ||
    message.contains("min relay fee", true) ||
    message.contains("insufficient priority", true)          -> BroadcastOutcome.FeeTooLow(message)
    message.contains("missing inputs", true) ||
    message.contains("txn-mempool-conflict", true) ||
    message.contains("already spent", true) ||
    message.contains("mandatory-script-verify", true)        -> BroadcastOutcome.Rejected(message)
    message.contains("timeout", true) ||
    message.contains("no available connections", true) ||
    message.contains("connect", true)                        -> BroadcastOutcome.TransientError(message)
    else                                                     -> BroadcastOutcome.Rejected(message)
}

/**
 * Broadcast via the SPV connection manager, retrying only TRANSIENT (connectivity) errors.
 * Returns the categorized outcome; "already known" maps to Accepted.
 *
 * @param maxAttempts total tries for transient failures (with a caller-supplied backoff)
 * @param backoff     invoked between transient retries (e.g. { Thread.sleep(500L * it) }); the Int
 *                    is the attempt number. Kept injectable so this file has no threading deps.
 */
fun broadcastWithRetry(
    blockchain: Blockchain,
    tx: iTransaction,
    maxAttempts: Int = 3,
    backoff: (attempt: Int) -> Unit = {},
): BroadcastOutcome {
    val bytes = tx.toByteArray()
    var attempt = 0
    while (true) {
        attempt++
        val outcome = try {
            blockchain.net.broadcastTransaction(bytes)       // throws on rejection
            BroadcastOutcome.Accepted(alreadyKnown = false)
        } catch (e: Exception) {
            categorize(e.message ?: "unknown broadcast error")
        }
        when (outcome) {
            is BroadcastOutcome.TransientError -> {
                if (attempt >= maxAttempts) return outcome
                backoff(attempt)                              // and loop to retry
            }
            else -> return outcome                            // Accepted / FeeTooLow / Rejected are terminal
        }
    }
}

/*
 * Notes:
 * - FeeTooLow is terminal here on purpose: a tx that underpays can't be fixed by re-broadcasting
 *   the same bytes — you must rebuild with a larger fee (re-fund). Surface it distinctly so the
 *   caller rebuilds rather than retries. See the fee-sizing caveat in SKILL.md / nexaNplSmartContracts.
 * - For the full-node path, swap the try-body for nexaRpc.sendrawtransaction(tx.toHex()) and reuse
 *   categorize(...). sendrawtransaction validates and throws on rejection; enqueuerawtransaction
 *   relays without full verification (faster, weaker). See nexaRpcNodeClient.
 * - Acceptance is NOT finality. A 0-conf tx can still be reorged/double-spent — gate irreversible
 *   actions on confirmation depth (SKILL.md "Treating a 0-confirmation incoming payment as final").
 */