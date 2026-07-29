/*
 * capdConversationTemplate.kt — a drop-in two-party conversation harness over CAPD's encrypted
 * channel (libnexakotlin CapdProtocolCommunication), with re-send-until-acked and expiry handling.
 *
 * CAPD is an ephemeral, PoW-rate-limited, best-effort broadcast bus — NOT durable or guaranteed
 * delivery (SKILL.md). So application messages that must arrive are re-sent until the peer acks.
 * The channel itself is end-to-end encrypted from a shared convoSecret; this harness adds a thin
 * frame (sender id + message id + type) so each side can ignore its own broadcasts and ack.
 *
 * Grounded in libnexakotlin protocolCommunication.kt (CapdProtocolCommunication: start/stop/send/
 * receive) and capd.kt. Receiving requires a P2P peer — start() calls chain.net.getp2p(), so an
 * electrum/SPV-only connection can broadcast but not receive (SKILL.md).
 */

import org.nexa.libnexakotlin.Blockchain
import org.nexa.libnexakotlin.CapdProtocolCommunication
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * @param chain        a connected Blockchain whose net can supply a P2P peer (getp2p()).
 * @param convoSecret  the shared secret BOTH parties derive the conversation id + AES key from.
 *                     Exchange it out-of-band (e.g. from a key agreement); anyone with it can read.
 * @param selfId       a short unique id for this side, so we ignore our own broadcast echoes.
 * @param coCtxt       coroutine context for the channel + receive loop.
 */
class CapdConversation(
    chain: Blockchain,
    convoSecret: ByteArray,
    private val selfId: String,
    private val coCtxt: CoroutineContext,
) {
    private val channel = CapdProtocolCommunication(chain, convoSecret, coCtxt)
    private val scope = CoroutineScope(coCtxt + SupervisorJob())

    // message ids we've already delivered to the app, to dedup CAPD's at-least-once / replays.
    private val deliveredIds = mutableSetOf<String>()
    // outstanding sends awaiting an ack: messageId -> CompletableDeferred completed when acked.
    private val pendingAcks = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** Frame: "selfId|msgId|type|payload". type is "MSG" or "ACK"; payload is the app bytes (hex-free here). */
    private fun frame(msgId: String, type: String, payload: ByteArray) =
        "$selfId|$msgId|$type|".encodeToByteArray() + payload

    private data class Parsed(val sender: String, val msgId: String, val type: String, val payload: ByteArray)
    private fun parse(bytes: ByteArray): Parsed? {
        val s = bytes.decodeToString()
        val head = s.split("|", limit = 4)
        if (head.size < 4) return null
        val prefixLen = head[0].length + head[1].length + head[2].length + 3   // 3 separators
        return Parsed(head[0], head[1], head[2], bytes.copyOfRange(prefixLen, bytes.size))
    }

    /** Start the channel and the receive loop. Suspends only to obtain a P2P peer. */
    suspend fun start(onMessage: (payload: ByteArray, sentAtEpochSeconds: Long) -> Unit) {
        channel.start()                                    // needs a P2P peer (getp2p)
        scope.launch {
            while (isActive) {
                // channel.receive() already drops self-declared-expired messages and decrypts.
                val (bytes, sentAt) = runCatching { channel.receive() }.getOrNull() ?: continue
                val p = parse(bytes) ?: continue
                if (p.sender == selfId) continue           // ignore our own broadcast echo
                when (p.type) {
                    "ACK" -> pendingAcks.remove(p.msgId)?.complete(Unit)
                    "MSG" -> {
                        // ack first (idempotent), then deliver once
                        runCatching { channel.send(frame(p.msgId, "ACK", ByteArray(0))) }
                        if (deliveredIds.add(p.msgId)) onMessage(p.payload, sentAt)
                    }
                }
            }
        }
    }

    /**
     * Send `payload`, re-broadcasting until the peer acks or attempts run out. Each send solves a
     * fresh PoW (CapdProtocolCommunication.send → setPowTargetHarderThanPriority + solve), so don't
     * set the interval too tight. Throws if never acked within the budget.
     *
     * @param msgId           a unique id you generate (e.g. a counter + selfId); used for ack matching + dedup.
     * @param resendInterval  delay between re-broadcasts (ms).
     * @param maxAttempts     how many times to (re)broadcast before giving up.
     */
    suspend fun sendReliable(payload: ByteArray, msgId: String,
                             resendInterval: Long = 15_000L, maxAttempts: Int = 8) {
        val acked = CompletableDeferred<Unit>()
        pendingAcks[msgId] = acked
        try {
            repeat(maxAttempts) {
                runCatching { channel.send(frame(msgId, "MSG", payload)) }   // encrypt + solve PoW + broadcastMsg
                val got = withTimeoutOrNull(resendInterval) { acked.await() }
                if (got != null) return                       // peer acked
            }
            throw IllegalStateException("CAPD message $msgId not acked after $maxAttempts attempts")
        } finally {
            pendingAcks.remove(msgId)
        }
    }

    /** Fire-and-forget (no ack): one encrypted broadcast. Fine for idempotent/repeated state. */
    suspend fun sendBestEffort(payload: ByteArray, msgId: String) {
        runCatching { channel.send(frame(msgId, "MSG", payload)) }
    }

    fun stop() {
        channel.stop()
        scope.cancel()
    }
}

/*
 * Notes:
 * - PoW: each send solves a proof-of-work sized by the relay priority. If a target is too hard the
 *   underlying CapdMsg.solve throws CapdTooDifficult (work above CapdSolvableCutoff) — don't loop
 *   forever on an unsolvable target (SKILL.md anti-patterns).
 * - Expiry: CAPD messages self-expire (CapdMsg.expiration); the channel's receive loop already
 *   discards messages past their declared lifetime, so an ack that never comes within the message
 *   lifetime is handled by the resend budget above, not by stale delivery.
 * - Confidentiality/auth: the channel encrypts with a key derived from convoSecret, but CAPD has no
 *   built-in identity — PoW is anti-spam, not authentication. If you need to know WHO sent a
 *   message, sign the payload (SKILL.md Security). The selfId frame here is a convenience, not a
 *   trust boundary.
 * - Receiving needs a P2P peer; broadcast-only works on any connection. See SKILL.md.
 */