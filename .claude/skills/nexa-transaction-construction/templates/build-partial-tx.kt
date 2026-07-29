/*
 * build-partial-tx.kt — drop-in builders for the three transaction-building flavors a Nexa app
 * uses (SKILL.md "Mental model"):
 *   1. wallet does everything (BIP21 URI push)         — buildPaymentUri
 *   2. server builds a partial tx, the wallet completes — buildPartialTxOffer / buildHalfTxSwapOffer
 *   3. server fully builds (+ later broadcasts)         — buildFullySpecifiedTx
 *
 * Grounded in libnexakotlin simpleapi.kt / ichain.kt / iWallet.kt. Amounts are satoshi Longs
 * (see simpleapi-cheatsheet.md). Broadcasting is a separate concern — see broadcast-tx.kt.
 */

import org.nexa.libnexakotlin.*
import org.nexa.libnexakotlin.simpleapi.*

/* ----- Flavor 1: wallet does everything (a plain payment) ---------------------------------- */

/** A BIP21 URI the wallet funds/signs/broadcasts itself. Best for ordinary payments. */
fun buildPaymentUri(toAddress: String, amountSats: Long, label: String? = null): String {
    val base = "nexa:$toAddress?amount=$amountSats"
    return if (label != null) "$base&label=$label" else base
}

/* ----- Flavor 2: server builds outputs, the wallet completes (funds + signs) --------------- */

/**
 * Build an OUTPUTS-ONLY transaction and produce the tdpp push URI for the wallet to complete.
 * Use for contract funding where the output shape is non-trivial. The wallet adds inputs, signs,
 * broadcasts, and GETs your /tx callback with the final hex.
 *
 * createTdppUrl auto-derives `inamt` from the (zero, here) inputs and emits chain/inamt/flags/tx;
 * it takes NO cookie param, so append your correlation cookie to the returned string yourself.
 */
fun buildPartialTxOffer(chain: ChainSelector, outputs: List<iTxOutput>, requestingDomain: String,
                        correlationCookie: String): String {
    val tx = NexaTransaction(chain)
    outputs.forEach { tx.add(it) }                      // outputs only — no inputs
    val pushUri = tx.createTdppUrl(requestingDomain = requestingDomain)   // applinkDomain defaults to w.nexa.org
    return "$pushUri&cookie=$correlationCookie"
}

/**
 * The "half tx" swap-offer idiom (order books / atomic asset trades): build a tx carrying BOTH
 * the seller's token input AND the payment-demand output, PARTIAL-fund and PARTIAL-sign the
 * seller's side, then push for the counterparty's wallet to complete. Requires the seller's wallet
 * to fund/sign its side via txCompleter (two PARTIAL passes), so this is a method on the wallet.
 *
 * Flags: NOFUND|NOPOST|NOSHUFFLE|PARTIAL is the canonical push combination for the completing
 * wallet (see SKILL.md "The 'half tx' swap-offer idiom" and the TDPP flags table in
 * nexaWalletConnection). The completer itself uses TxCompletionFlags below.
 */
fun CommonWallet.buildHalfTxSwapOffer(
    tokenInputOutput: iTxOutput,         // the token being offered (added as an output to bind it)
    paymentDemandOutput: iTxOutput,      // what the seller demands in return
    minConfirms: Int,
    requestingDomain: String,
    correlationCookie: String,
): String {
    val tx = NexaTransaction(chainSelector)
    tx.add(tokenInputOutput)
    tx.add(paymentDemandOutput)
    // Phase 1: pull in MY inputs to fund my side (may add my own token change):
    txCompleter(tx, minConfirms, TxCompletionFlags.PARTIAL or TxCompletionFlags.FUND_GROUPS,
                changeAddress = getNewAddress())
    // (reorder/replace outputs into the final offer shape your protocol requires here)
    // Phase 2: sign my inputs and finalize my output scripts:
    txCompleter(tx, minConfirms,
                TxCompletionFlags.PARTIAL or TxCompletionFlags.SIGN or TxCompletionFlags.BIND_OUTPUT_PARAMETERS,
                changeAddress = getNewAddress())
    // Push for the counterparty to complete (NOFUND|NOPOST|NOSHUFFLE|PARTIAL = 15 on the wire):
    val pushUri = tx.createTdppUrl(requestingDomain = requestingDomain, tdppFlags = 15L)
    return "$pushUri&cookie=$correlationCookie"
}

/* ----- Flavor 3: server fully builds (and later broadcasts) -------------------------------- */

/**
 * Fully construct a tx the server can broadcast itself — only valid when no input needs the
 * user's signature (e.g. spending a script-template contract that needs only revealed args).
 * For a wallet-funded send, prefer wallet.send(...) or the txCompleter form below.
 */
fun buildFullySpecifiedTx(chain: ChainSelector, inputs: List<iTxInput>, outputs: List<iTxOutput>): NexaTransaction {
    val tx = NexaTransaction(chain)
    inputs.forEach { tx.add(it) }
    outputs.forEach { tx.add(it) }
    return tx
}

/**
 * The general completion engine: supply OUTPUTS, txCompleter adds inputs, computes change/fee,
 * and (with SIGN) signs. This is the same engine the wallet runs on a /tx push.
 *
 * txCompleter(tx, minConfirms, flags, inputAmount?, adjustableOutput?, destinationAddress?,
 *             changeAddress?, sigHashTypeOverride?, contractId?)
 *
 * Common flag combos (TxCompletionFlags):
 *   normal send:  FUND_NATIVE or SIGN
 *   token send:   FUND_GROUPS or FUND_NATIVE or SIGN
 *   sweep:        FUND_NATIVE or SPEND_ALL_NATIVE or DEDUCT_FEE_FROM_OUTPUT or SIGN
 *   mint:         FUND_NATIVE or FUND_GROUPS or SIGN or USE_GROUP_AUTHORITIES (or MUST_MINT to force issuance)
 * `adjustableOutput` is the output index DEDUCT_FEE_FROM_OUTPUT acts on. `inputAmount` may be
 * negative on a PARTIAL fund pass to pre-seed extra fee. The completer does NOT reorder in/outputs.
 */
fun CommonWallet.completeNormalSend(outputs: List<iTxOutput>, minConfirms: Int): iTransaction {
    val tx = NexaTransaction(chainSelector)
    outputs.forEach { tx.add(it) }
    txCompleter(tx, minConfirms,
                TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.FUND_GROUPS or TxCompletionFlags.SIGN,
                changeAddress = getNewAddress())
    return tx
}