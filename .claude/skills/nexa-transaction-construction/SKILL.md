---
name: nexa-transaction-construction
description: "Builds, signs, and broadcasts Nexa transactions in Kotlin, including constructing payment URIs and assembling partial transactions for the wallet to co-sign. Use when paying X to Y, broadcasting signed tx hex, letting the wallet add inputs and sign, computing amounts in satoshis, watching for incoming payments, parsing broadcasted tx hex, validating a returned or partial tx against a trusted node before accepting it, adding a read-only input that references a UTXO without spending it, or deciding when a payment is final/safe to credit (0-conf, confirmations, reorgs). Triggers: txFor, txOutputFor, NexaTxInput/Output, Spendable, NexaTxOutpoint, tx.toHex, broadcastTransaction, Hash256, nexa:addr?amount=, partial transaction, setOnWalletChange, sendrawtransaction, txn-already-in-mempool, txCompleter, TxCompletionFlags, createTdppUrl, confirmedHeight, TransactionHistory, abortTransaction, removeOnWalletChange, relatedTo, sendTxVal/parseTxValReply, OP.TMPL_SCRIPT parameterized outputs, signInput, stored sell offers."
---

# Nexa transaction construction and broadcast

## When to use this skill

Trigger when a developer is building, signing, or broadcasting a NEXA transaction in
Kotlin, constructing payment URIs, or assembling partial transactions for the wallet to
co-sign. Concretely trigger on:

- Keywords: `txFor`, `txOutputFor`, `NexaTxInput`, `NexaTxOutput`, `Spendable`,
  `NexaTxOutpoint`, `tx.toHex`, `broadcastTransaction`, `Hash256`, `.nexa`, `.sat`,
  `.mexa`, `nexa:addr?amount=`, partial transaction, `inamt`, `setOnWalletChange`,
  `NexaRpcFactory`, `sendrawtransaction`, broadcasting tx hex via Nexa RPC,
  `txn-already-in-mempool` handling, half-tx swap offer, order book / atomic asset trade,
  `TDPP_FLAG_NOSHUFFLE`, the `txProposal`/`txContinuation` correlation pattern,
  `txCompleter`, `TxCompletionFlags`, `adjustableOutput`, `MUST_MINT`, `createTdppUrl`,
  `confirmedHeight`, `TransactionHistory`, `abortTransaction` (release a built tx's reserved
  UTXOs), `removeOnWalletChange` / observer handles, `relatedTo` (annotating wallet history),
  `sendTxVal` / `parseTxValReply` / `TxValReply` (validate a tx via a trusted node without
  broadcasting), `OP.TMPL_SCRIPT` / parameterized outputs / `script.parameterized()`,
  `signInput` (sign one input), `inputTotal` / `outputTotal`, "verify the tx the wallet returned",
  store/serve a persisted sell offer, read-only inputs (`NexaTxInput.type`, reference/read a UTXO
  without spending it, shared oracle/state UTXO, prove ownership without spending).
- Tasks: "build a NEXA transaction that pays X to Y", "broadcast a signed transaction",
  "let the wallet add inputs and sign", "calculate the right amount in satoshis",
  "watch for incoming payments to the server's wallet", "parse a broadcasted tx hex",
  "when is a payment final / safe to credit", "how many confirmations before I ship",
  "handle 0-conf / reorg / unconfirmed receives".
- Code in: `contractTx.kt`, `Application.kt`'s `nexaWallet.setOnWalletChange { ... }`,
  any route that pushes `tdpp://host/tx?tx=<hex>` to a wallet.

**Negative triggers** — do NOT use this skill for:
- NPL/contract scripts going *into* a tx output — use `nexa-npl-smart-contracts`.
- The wallet protocol that delivers the partial tx — use `nexa-wallet-connection`.
- `OP_CHECKLOCKTIMEVERIFY`-specific gotchas (`nSequence`, MTP) — use
  `nexa-locktime-cltv`.

## Mental model

NEXA transactions look like Bitcoin transactions at first glance but are NOT
serialization-compatible. The library types are `NexaTransaction`, `NexaTxInput`,
`NexaTxOutput`, `NexaTxOutpoint` — all in `org.nexa.libnexakotlin`. Use the chain-aware
factory functions (`txFor(chain)`, `txOutputFor(chain)`) so the code stays
chain-agnostic.

There are **two unit systems** and they will trip you up:

- **Satoshi** (internal): a single `Long`. The smallest divisible unit.
- **Whole NEXA** (user-facing, BIP21 URIs): `1 NEXA = 100 sat`.

The convenience extensions in `org.nexa.libnexakotlin.simpleapi`:

```kotlin
1.nexa    // → 100 (Long, in sat units)
1.sat     // → 1 (Long)
1.mexa    // → 100_000_000 (1 mega-NEXA = 10^8 sat)
```

When you build a `NexaTxOutput.amount`, it's **always satoshi** (Long). When you build a
`nexa:<addr>?amount=N` URI, `N` is **whole NEXA** (no decimal divisor, but the wallet
interprets it that way).

There are three flavors of transaction building in a typical NEXA app:

1. **Wallet does everything**: push a `nexa:<addr>?amount=N` URI to Wally, it picks
   inputs, signs, broadcasts. Server doesn't see the tx until it lands on-chain. Best
   for ordinary payments.
2. **Server constructs a partial tx, wallet completes it**: server builds outputs only
   (no inputs), sends the hex inside a `tdpp://host/tx?tx=<hex>&inamt=0&cookie=ID` URI.
   Wally adds funding inputs, signs them, broadcasts, then GETs back to `/tx?tx=<finalHex>&cookie=ID`.
   Best for contract funding where the output shape is non-trivial.
3. **Server constructs AND broadcasts the whole tx**: only safe when no input requires
   the user's signature (e.g., spending a script-template contract that only needs
   secret-revealing arguments, not a sig). Broadcasts via
   `nexaWallet.blockchain.net.broadcastTransaction(tx.toByteArray())`.

For incoming payments to the server's own wallet, hook `setOnWalletChange` —
libnexakotlin's `CommonWallet` fires this whenever the wallet's UTXO set changes,
giving you the new `TransactionHistory` entries directly.

## Setup and versions

You need `libnexakotlin` (tx types, broadcast) and `libnexaapp` (wallet-push + `/tx`
callback patterns). Pin exact versions per `nexa-project-setup`. The `txCompleter` /
`TxCompletionFlags` surface is among the most likely to drift — verify it against the resolved jar
before relying on it (`nexa-project-setup` § "Verifying API signatures before relying on them").

Imports:

```kotlin
import org.nexa.libnexakotlin.txFor
import org.nexa.libnexakotlin.txOutputFor
import org.nexa.libnexakotlin.iTransaction
import org.nexa.libnexakotlin.NexaTxInput
import org.nexa.libnexakotlin.NexaTxOutpoint
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.Spendable
import org.nexa.libnexakotlin.BCHserialized
import org.nexa.libnexakotlin.SerializationType
import org.nexa.libnexakotlin.fromHex
import org.nexa.libnexakotlin.toHex
import org.nexa.libnexakotlin.simpleapi.nexa
import org.nexa.libnexakotlin.simpleapi.payTo
```

## Core patterns

### Pattern 1: "Just have the wallet pay" — BIP21 URI push

```kotlin
// Send 1000 whole NEXA to recipientAddr.  Wally adds inputs, signs, broadcasts.
val uri = "$recipientP2PKTAddress?amount=$priceInWholeNexa"
session.pushToWallet(uri)
// No further action needed.  The tx appears on-chain when Wally finishes; you can
// optionally watch for it via blockchain monitoring.
```

Notes:
- `recipientP2PKTAddress` is a `nexatest:nqt...` (or `nexa:nqt...`) string. Don't include
  `tdpp://` prefix — BIP21 URIs use the bare address.
- `priceInWholeNexa` is the unit a user expects (1000 NEXA), not satoshi.
- No callback. If you need confirmation, watch the destination address on-chain.
- The wallet also honors BIP21 `label` and `message` (it joins them into the payment note it
  shows/stores), and a sub-satoshi `amount` is **rounded up** (the recipient is overpaid by the
  fraction) rather than rejected — don't emit more decimals than 1/100 NEXA.

### Pattern 2: Server-constructed partial tx + wallet completion

```kotlin
import org.nexa.libnexakotlin.txFor
import org.nexa.libnexakotlin.txOutputFor

/** Build a partial tx with a single output of [amountSatoshis] paying to [lockingScript].
 *  Returns hex.  Wallet will add inputs/change/signatures and broadcast. */
fun buildPartialFundingTxHex(amountSatoshis: Long, lockingScript: org.nexa.libnexakotlin.SatoshiScript): String {
    val tx = txFor(DEFAULT_CHAIN)
    val out = txOutputFor(DEFAULT_CHAIN)
    out.amount = amountSatoshis
    out.script = lockingScript
    tx.add(out)
    return tx.toHex()
}

// In your route handler:
val partialTxHex = buildPartialFundingTxHex(priceInWholeNexa.nexa, contractLockingScript)
val tdppUrl = "tdpp://${tdppHost()}/tx?chain=nexatest&inamt=0&flags=0" +
              "&tx=$partialTxHex&cookie=$correlationId"
session.pushToWallet(tdppUrl)
// Wally calls back: GET /tx?tx=<finalHex>&cookie=<correlationId>
// Register that route to advance state.  See nexa-wallet-connection.
```

Notes:
- You can build the `tdpp://host/tx?...` string by hand (as above) or with libnexakotlin's
  `iTransaction.createTdppUrl(...)`, which assembles it from a (partial) transaction — see
  Pattern 6's partial-offer idiom.
- `inamt` is the satoshis the *requester* already supplied via inputs already present in the
  partial tx; the wallet passes it to its `txCompleter` so it knows how much more to fund.
  `inamt=0` therefore means "I provided no input value — you fund the entire output."
- `flags=0` is normal broadcast. `flags` is a TDPP **wire-protocol** bitfield the **wallet**
  interprets.
  The `TDPP_FLAG_*` constants are top-level `const val`s exported by libnexakotlin
  (`org.nexa.libnexakotlin`, in its `utils.kt`) — import them rather than re-defining.
  The full per-bit table (integer
  values `NOFUND=1, NOPOST=2, NOSHUFFLE=4, PARTIAL=8, FUND_GROUPS=16, HIDE_ASSET_DETAILS=32`) is
  in `nexa-wallet-connection` § "The TDPP transaction `flags` bitfield".

  In particular `TDPP_FLAG_NOPOST` (`2`) is the "sign but don't broadcast — return the completed
  tx to me" bit, and `TDPP_FLAG_NOSHUFFLE` (`4`) tells the wallet to keep output order stable.
- `cookie` is your correlation id — pick anything that lets you look up the operation
  on the callback side.

### Pattern 3: Server fully constructs + broadcasts (no wallet involvement)

Only valid when the input doesn't need a user signature — for example, spending a
script-template contract whose locking script accepts the args you can compute on the
server (like a secret-reveal contract).

```kotlin
fun buildClaimTx(visibleArgs: SatoshiScript, fundingTxid: Hash256, fundingVout: Int,
    fundedSatoshis: Long, payoutSatoshis: Long, recipientAddress: String,
    spenderArgs: SatoshiScript): iTransaction
{
    // ... build the spendable + satisfier (see nexa-npl-smart-contracts) ...

    val tx = txFor(DEFAULT_CHAIN)
    val input = NexaTxInput(spendable)
    input.script = satisfier
    input.sequence = 0xfffffffeL          // see nexa-locktime-cltv for why this matters
    tx.add(input)

    val out = txOutputFor(DEFAULT_CHAIN)
    out.amount = payoutSatoshis
    out.script = PayAddress(recipientAddress).lockingScript()
    tx.add(out)
    return tx
}

fun broadcastTx(tx: iTransaction) {
    val net = nexaWallet?.blockchain?.net
        ?: throw IllegalStateException("server wallet not initialized")
    net.broadcastTransaction(tx.toByteArray())
}
```

`broadcastTransaction(tx: ByteArray)` on the connection manager (`blockchain.net`) lives in
libnexakotlin's `cnxnmgr.kt`.

#### Alternative pattern, preferred when you run your own full node: broadcast via Nexa RPC

When the server already talks RPC to a trusted node, broadcasting the **tx hex** through the
node's JSON-RPC `sendrawtransaction` (using `org.nexa:nexarpc`) is the natural choice, and it
makes the "already known" cases easy to treat as success:

```kotlin
import org.nexa.nexarpc.NexaRpcFactory

fun broadcastTransaction(txHex: String): BroadcastResult {
    return try {
        val nexaRpc = NexaRpcFactory.create("$NEXA_RPC_HOST:$NEXA_RPC_PORT/", NEXA_RPC_USER, NEXA_RPC_PASSWORD)
        val txid = nexaRpc.sendrawtransaction(txHex).toHex()
        BroadcastResult(success = true, txid = txid)
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        // Idempotency: a tx already in the mempool or a block is NOT a failure.
        if (msg.contains("txn-already-in-mempool", ignoreCase = true) ||
            msg.contains("already in block chain", ignoreCase = true)) {
            return BroadcastResult(success = true, txid = extractTxidAndOutpoint(txHex).first)
        }
        BroadcastResult(success = false, errorMessage = msg)
    }
}
```

Two takeaways:
- `sendrawtransaction` takes the **hex string** (`tx.toHex()`) or a `ByteArray` (there are
  overloads for both), and returns the txid as a nexarpc `HashId`.
- Auto-claim / retry tasks WILL re-broadcast the same tx; `txn-already-in-mempool` and
  `already in block chain` must be folded into "success", or your retry loop will report
  spurious failures and may keep re-trying a tx that is already settling.

> **Broadcasting a covenant SPEND of a just-created (0-conf) output — use RPC, not the wallet's
> P2P net.** Relaying a spend of a freshly-created 0-conf covenant output through
> `wallet.blockchain.net.broadcastTransaction` is rejected with **reject code 73 "please wait for
> wallet sync"**: the node's **bloom-filter view of the SPV wallet** doesn't yet include the new
> covenant output, so it gates the relay. This is *not* a script error (script failures are code
> 16). Submit via the node's RPC `sendrawtransaction` instead — a direct mempool submit that
> bypasses bloom gating, returns synchronously, and throws `NexaRpcException(msg, code)` so you can
> **retry the transient 73 and fail-fast on other codes**. Also wait for the **parent** tx to be
> in the node's mempool before you broadcast the child that spends its 0-conf output (broadcast the
> child too soon and the node doesn't yet know the input); via RPC the same
> covenant spend is accepted immediately with no code-73. (This transient is distinct from the
> **wallet-state** code-73 wedge in `nexa-wallet-lifecycle-and-chain` — a stale 0-conf backlog
> that makes the node defer *every* tx until `cleanUnconfirmed()`; that one is not fixed by
> switching to RPC.) It does require correct `server.rpc` creds — a wrong rpc user/pass surfaces as
> `java.net.ConnectException: Connection refused` from `JvmNexaRpc`, not an auth error.

`sendrawtransaction` validates the tx and throws on rejection; the node also offers
`enqueuerawtransaction`, which relays without waiting for full verification (faster, weaker
guarantee). The full nexarpc client surface — connecting, error handling, chain/mempool reads,
and the `calls`/`callje` escape hatch for un-wrapped RPCs — is documented in
`nexa-rpc-node-client`.

### Pattern 4: Watching for incoming payments to the server's wallet

```kotlin
nexaWallet?.setOnWalletChange { wallet, txs ->
    (wallet as CommonWallet).clearCachedBalances()    // libnexakotlin caches balances; flush
    serverBalance.value = wallet.balance

    if (txs == null) return@setOnWalletChange
    for (txh in txs) {
        for (idx in txh.incomingIdxes) {              // which outputs are receives
            val output = txh.tx.outputs[idx.toInt()]
            val recipientAddr = output.script.address?.toString() ?: continue
            val amount = output.amount
            handleIncomingPayment(recipientAddr, amount, txh.tx.idem)
        }
    }
}
```

`TransactionHistory` is the libnexakotlin wrapper that carries:
- `tx: iTransaction` — the full tx
- `incomingIdxes: MutableList<Long>` — which output indexes are receives to this wallet
- `outgoingIdxes`, `spentTxos` — for tx that spent our UTXOs
- `confirmedHeight` (`-1` = unconfirmed; `Long.MIN_VALUE` = being removed/invalid; see below)
- `confirmedHash: Hash256?` — the confirming block's hash, or null while unconfirmed
- `incomingAmt` / `outgoingAmt` (Long, sat) and `date` (epoch **milliseconds**)

`confirmedHeight` is a three-state signal, not a boolean:

- `>= 0` — the height of the block that confirmed it.
- `-1` — still unconfirmed (mempool only).
- `Long.MIN_VALUE` — the tx is being **removed from the unconfirmed list** because it is
  (probably) invalid: it was double-spent, conflicted out, or otherwise dropped. This is the
  transition that fires when a 0-conf receive you saw earlier turns out *not* to be money — gate
  on `confirmedHeight >= 0` and treat the `Long.MIN_VALUE` case as an explicit "reverse the
  pending credit" signal, never as just "still waiting."

(To re-check one transaction on demand — e.g. a route that reports an order's settlement state —
`wallet.getTx(txIdem): TransactionHistory?` looks the entry up by idem instead of waiting for the
next callback.)

Three facts about the observer machinery worth knowing:

- **`setOnWalletChange` registers, it doesn't replace: it returns an `Int` handle**, and
  `wallet.removeOnWalletChange(handle)` deregisters just that observer. So you can keep a
  long-lived "update the balance" observer *and* add a temporary per-operation one (e.g. "watch
  for this specific payment to land, then deregister") without them clobbering each other.
- **Most invocations pass `txs = null`** — the non-null tx list arrives from only a few code
  paths. A robust per-operation observer therefore doesn't parse `txs`; it re-checks its target
  by `wallet.getTx(idem)` on every callback and reads `confirmedHeight` from that (the pattern
  the Wally wallet itself uses to confirm a submitted tx: success once `getTx` shows the tx at
  `confirmedHeight == -1` or above, failure on `Long.MIN_VALUE`, plus its own timeout since a tx
  that never arrives fires no callback).
- **`TransactionHistory.relatedTo`** is a `MutableMap<String, ByteArray>` for your own
  annotations on a wallet-history entry (an order id, a protocol tag) — persisted with the
  wallet, so a later callback or restart can recognize "this tx belongs to operation X".

`setOnWalletChange` fires on the *first* (unconfirmed) sighting as well as on later
state changes, so the same incoming tx will surface here while it is still 0-conf, and again if
its `confirmedHeight` later flips to a real height or to `Long.MIN_VALUE`. An
unconfirmed receive can be double-spent or simply never mined, and even a confirmed one
can be undone by a chain reorganization that orphans its block. Gate anything irreversible
(shipping goods, revealing a secret, crediting a withdrawable balance) on a confirmation
depth you pick for the value at stake, and re-check `confirmedHeight` as subsequent
`setOnWalletChange` callbacks fire rather than acting on the first sighting.

### Pattern 5: Parse a hex-encoded tx that arrived on a callback

```kotlin
fun parseTxHex(txHex: String): iTransaction =
    txFor(DEFAULT_CHAIN, BCHserialized(txHex.fromHex(), SerializationType.NETWORK))

// In your /tx callback:
get("/tx") {
    val cookie = call.request.queryParameters["cookie"]
    val txHex = call.request.queryParameters["tx"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val tx = parseTxHex(txHex)
    val txid = tx.idem                  // Hash256 -- standard NEXA tx id
    // Find the contract output by expected amount + locking script type, not by index --
    // the wallet may have added other outputs (change, etc.).
    val vout = tx.outputs.indexOfFirst { it.amount == expectedFundedAmount }
    if (vout < 0) { /* not our funding */ }
    val actualAmount = tx.outputs[vout].amount  // capture what's actually on-chain
    // ... advance state, store txid + vout ...
}
```

### Building an explorer URL from a txid

```kotlin
fun explorerTxUrl(txid: Hash256): String {
    val base = when (DEFAULT_CHAIN) {
        org.nexa.libnexakotlin.ChainSelector.NEXATESTNET -> "https://testnet-explorer.nexa.org"
        else -> "https://explorer.nexa.org"
    }
    return "$base/tx/${txid.toHex()}"
}
```

The standard NEXA tx id (`tx.idem.toHex()`) is what explorers want — no byte reversal
needed (unlike Bitcoin's display-reversed convention for blocks/txs).

Alternative pattern, preferred when you don't want to maintain the URL table yourself:
libnexakotlin's `ChainSelector` has a member `explorer(s: String): String` that maps the chain to
its canonical explorer base and appends your path:

```kotlin
val explorerUrl = DEFAULT_CHAIN.explorer("/tx/${txid.toHex()}")   // per-chain explorer base + path
```

The path must start with `/` (it `require`s that, and rejects suspicious characters like `://`
or `@` so untrusted input can't turn the result into a link to another host); `NEXAREGTEST` maps
to a localhost base. Adjacent `ChainSelector` members worth knowing while you're there:
`uriScheme` (the address/URI prefix, e.g. `nexa`/`nexatest` — the property form of the
`chainToURI[...]` lookup used elsewhere in these skills), `currencyCode`, and `isMainNet`.

**NEXA has two distinct transaction identifiers — use `idem` for app-level tracking**
(`https://spec.nexa.org/transactions/transactionIdentifier/`):

- **`idem`** ("identical-effect") is the hash of the transaction **without the input signature
  scripts**. Because the satisfier bytes are excluded, it is **malleation-stable** — any party can
  re-sign or re-malleate the unlocking scripts and the `idem` stays the same. It is what
  **outpoints reference** (so a child tx can be built/signed before its parent), and what explorers
  and wallets index by ("did this credit/debit appear?"). Use `tx.idem` for correlation, storage,
  explorer links, and "did my payment land."
- **`id`** is the hash of **all** the bytes, signatures included. Two malleated variants of the same
  tx share an `idem` but differ in `id`. The block merkle tree commits the `id` (so a block names
  exactly which variant it mined), and network/relay protocols use it. You rarely need it at the app
  layer.

The wallet callbacks reflect both: a `/sendto` reply carries `{txid, txidem, …}` (the `txid` is the
`id`, `txidem` is the `idem`); the token-genesis `TokenGenesisInfo` carries both `txid` and `txidem`.

### simpleapi sugar for paying

When you ARE using the server's wallet to send (not a contract spend), the simpleapi
extension functions are much shorter:

```kotlin
import org.nexa.libnexakotlin.simpleapi.nexa
import org.nexa.libnexakotlin.simpleapi.payTo

// Send 1001 NEXA to recipientAddr from the server's wallet
val tx = nexaWallet.send(1001.nexa payTo recipientAddr, minConfirms = 0)
// tx is built, signed, and broadcast by .send()
println("Sent: ${tx.idem.toHex()}")
```

`1001.nexa payTo addr` constructs a `NexaTxOutput`. `.send(output)` lets the wallet pick
inputs, sign, broadcast in one call. This is the right pattern for things like rewards,
faucets, or auto-payouts from the server.

`send` is a whole overload family on the wallet, all returning the signed `iTransaction`:
`send(amountSatoshis, destAddress /* PayAddress, String, or a SatoshiScript */,
deductFeeFromAmount = false, sync = false, note = null, minConfirms = 0)` — set
**`deductFeeFromAmount = true`** to take the fee out of the sent amount instead of adding it on
top (the "send exactly what's left" case); **`sync = true`** blocks until the tx has actually been
handed to nodes; `note` attaches a private local annotation to the wallet history. Multi-recipient:
`send(listOf(addr1 to sats1, addr2 to sats2))`. Token convenience:
`send(amountTokens, destAddress, groupId)` builds-and-sends one token output (the manual
equivalent is Pattern 6's `txCompleter` with `FUND_GROUPS`). And `send(tx)` broadcasts a tx you
completed yourself.

### Pattern 6: Completing a transaction — `txCompleter` and `TxCompletionFlags`

`.send(...)` is a one-shot convenience. When you need finer control — funding a tx with both
native and token inputs, signing only your own inputs, sweeping a balance, or building a
*partial* tx for someone else to finish — drop to `CommonWallet.txCompleter(...)`. This is the
**same completion engine the Wally wallet runs when it receives a `tdpp://host/tx` push**:
you hand it a transaction that already has the OUTPUTS you want, and it adds inputs, computes
change and fee, signs, and finalizes the output scripts according to the `TxCompletionFlags`
you pass (from `org.nexa.libnexakotlin`):

| `TxCompletionFlags` | What it does when set |
| --- | --- |
| `FUND_NATIVE` | Add native-NEXA inputs to cover the outputs + fee. |
| `FUND_GROUPS` | Add token (group) inputs to cover token outputs (and add token change if it over-pulls). |
| `SIGN` | Sign the inputs this wallet controls. |
| `BIND_OUTPUT_PARAMETERS` | Resolve/finalize template + group parameters on the outputs so the locking scripts are concrete. |
| `PARTIAL` | Complete only *your* part — don't require the tx to fully fund or balance; leave it for other signers. The basis of partial-tx offers. |
| `SPEND_ALL_NATIVE` | Sweep: spend the entire native balance. |
| `DEDUCT_FEE_FROM_OUTPUT` | Subtract the fee from a specific output (pass its index) instead of from change — required when sweeping, since there is no change output. |
| `USE_GROUP_AUTHORITIES` | Allow spending an authority UTXO if one is present — needed to mint (see `nexa-tokens-and-groups` Pattern 9). |
| `NO_BATON_AUTHORITIES` | When spending authorities, do **not** consume a BATON authority. Pair with `USE_GROUP_AUTHORITIES` on a mint so a routine issuance spends a plain MINT authority and preserves the master baton. |
| `MUST_MINT` | Do **not** satisfy the grouped outputs from existing token UTXOs — the tx *must* create the token quantity by spending a mint authority. Use it to guarantee a mint actually issues new supply rather than quietly moving tokens you already hold (see `nexa-tokens-and-groups` Pattern 9). |

The flag bits are stable named constants in libnexakotlin's `TxCompletionFlags` (you `or` them
together into the `flags: Int` argument); reference them by name rather than by numeric value.

A normal "send native + tokens, fund, sign, broadcast" from the server's own wallet:

```kotlin
val tx = txFor(cs)
// native output:
txOutputFor(cs).apply { amount = atomAmt; script = sendAddress.lockingScript() }.also(tx::add)
// token output — native amount is dust; the token rides on the script (see nexa-tokens-and-groups):
txOutputFor(cs).apply { amount = dust(cs); script = sendAddress.groupedLockingScript(gid, tokQty) }.also(tx::add)

val cflags = TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.FUND_GROUPS or TxCompletionFlags.SIGN
wallet.txCompleter(tx, /*minConfirms*/ 0, cflags)     // funds, signs, binds output params
wallet.send(tx)                                        // broadcast (or push/hand off elsewhere)
```

The completer has more knobs than the common case needs: its **full signature**, the named tail
args (notably **`adjustableOutput`** — the output a sweep's `DEDUCT_FEE_FROM_OUTPUT` acts on — and a
**negative `inputAmount`** to seed extra fee on a `PARTIAL` pass), the lower-level
`signInput(tx, idx, sigHashType)`, and the **sighash-type model** that makes the half-tx offer below
cryptographically safe (the offerer signs *this input + the payment output only*, so the
counterparty can add funding/change without invalidating the signature) are all in
`txCompletionReference.md` in this folder. The completer **does not reorder** the inputs/outputs you
hand it — output shuffling is the wallet's separate `NOSHUFFLE`-gated behavior.

`txOutputFor` has an overload per output shape: `txOutputFor(chain)` (blank — set `.amount`
and `.script` yourself), `txOutputFor(amount, payAddress)` (native), and
`txOutputFor(address, tokenQty, groupId)` (token). `dust(chain)` is the per-chain dust minimum
to put on a token output's native `amount`.

**Abandoning a completed-but-unbroadcast tx: `wallet.abortTransaction(tx)`.** `txCompleter`
*reserves* the UTXOs it pulls in, so they won't be double-selected by a concurrent build. If you
then decide not to broadcast (the user rejected the proposal, a counterparty never completed the
offer, validation failed), release those reservations with `abortTransaction(tx)` — otherwise the
inputs stay unavailable until the next boot-time `cleanReserved()` sweep
(`nexa-wallet-lifecycle-and-chain`). This is the targeted, per-tx counterpart of that boot purge:
the Wally wallet calls it whenever the user declines a TDPP tx proposal it had already completed.

#### Building a partial-tx offer the wallet (or a counterparty) will complete

A partial tx funds and signs only YOUR side; another party finishes it. The idiom is **two
`txCompleter` passes — fund first, then sign + bind — with `PARTIAL` set throughout** so the tx
is not required to balance in between:

```kotlin
val tx = txFor(cs)
tx.add(txOutputFor(myAddress, offerQty, gid))   // e.g. the token I'm offering

// Phase 1: pull in MY inputs to fund my side (may add my own token change):
txCompleter(tx, minConfirms, TxCompletionFlags.PARTIAL or TxCompletionFlags.FUND_GROUPS, changeAddress = myAddress)

// (reorder/replace outputs into the final shape your protocol requires here)

// Phase 2: sign my inputs and finalize my output scripts:
txCompleter(tx, minConfirms, TxCompletionFlags.PARTIAL or TxCompletionFlags.SIGN or TxCompletionFlags.BIND_OUTPUT_PARAMETERS, changeAddress = myAddress)

// Build the push URI from the (partial) tx and push it to the wallet to complete:
val pushUri = tx.createTdppUrl(
    requestingDomain = myDomain,    // the site notified of completion ("" if none)
    tdppFlags = NOFUND or NOPOST or NOSHUFFLE or PARTIAL,
    applinkDomain = "w.nexa.org")   // pass null for a raw `tdpp://` URI instead of the applink
session.pushToWallet("$pushUri&cookie=$correlationId")   // append your own correlation cookie
```

`createTdppUrl` takes no host or cookie parameter: it **auto-derives `inamt`** by summing the
existing inputs' amounts and emits the `chain`/`inamt`/`flags`/`tx` query params, producing the
`https://<applinkDomain>/tdpp/<requestingDomain>/tx?…` applink by default (or a raw
`tdpp://<requestingDomain>/tx?…` URI when `applinkDomain = null`). Because it emits no `cookie`,
**append your correlation cookie to the returned string yourself** if you need to match the `/tx`
callback to an operation (the manual builder in Pattern 2 adds `&cookie=` directly).

When the wallet receives that push, it runs `txCompleter` on its side with the flags decoded
from the URI's `flags` parameter (`NOFUND` → clear `FUND_NATIVE`, `PARTIAL` → `PARTIAL`,
`FUND_GROUPS` → `FUND_GROUPS`) and the `inamt` value as the amount you already supplied — see
`nexa-wallet-connection` § "The TDPP transaction `flags` bitfield" for the wire-flag mapping.

> **To pre-seed extra fee, bias `inamt` in the URL string ONLY — never lower an input's
> `spendable.amount`.** The wallet consumes `inamt` as txCompleter's `inputAmount` (the documented
> fee-preseed knob: `inAmt = inputAmount ?: foldInputs`); under-reporting it makes the completing
> wallet pull `pad` extra sat of funding, and that surplus becomes real fee. It is tempting to
> achieve this by lowering the covenant input's `spendable.amount` before `createTdppUrl` (since
> the URL derives `inamt` by summing input amounts) — **do not.** A Nexa tx **serializes each
> input's spent amount on the wire, and the node validates it against the real UTXO value** (and
> the BIP143-style sighash commits to it). A wrong input amount is **reject code 16 "inconsistent
> input value"**, not silently corrected from the chain. Keep each input's `amount` = the TRUE
> UTXO value, and bias only the URL string after building it: rewrite `inamt=<real>` →
> `inamt=<real−pad>`. (To simulate the effect in a test, call
> `txCompleter(tx, …, inputAmount = real − pad)` directly.)

##### The "half tx" swap-offer idiom (order books / atomic asset trades)

A token-for-NEXA trade (a marketplace "sell" listing, an order-book offer) is a single tx with
*both* sides: an input spending the token being sold **and** an output demanding the payment.
The seller's server builds and PARTIAL-signs this **half tx** — the token input + the
payment-demand output, with the token-change output if selling part of a balance — then pushes
it for the buyer's wallet to *complete* (add the payment funding, sign, return). The flag
combination is `TDPP_FLAG_NOFUND or TDPP_FLAG_NOPOST or TDPP_FLAG_NOSHUFFLE or TDPP_FLAG_PARTIAL`:

- **NOFUND** — the offer already carries the seller's input; the *completing* wallet funds the
  payment side, the offer side is not re-funded.
- **NOPOST** — the wallet returns the completed tx to your `/tx` callback instead of
  broadcasting, so the server (acting as matcher/escrow) can validate it against the original
  offer before broadcasting itself.
- **NOSHUFFLE** — output order is preserved, so the matching logic can rely on the offer's
  output indexes lining up with what it proposed (this is the canonical reason `NOSHUFFLE`
  exists).
- **PARTIAL** — multi-party; the tx is not expected to balance until the counterparty completes
  it.

The server keeps the correlation across the round trip: stash the original proposal and a
**continuation callback** keyed by the `cookie` you put in the push URI. When the wallet GETs
`/tx?tx=<completedHex>&cookie=<id>`, look up the stashed proposal, verify the returned tx still
contains *your* original inputs/outputs unchanged (match by outpoint and by the demanded
payment output — never trust the wallet to have left the rest alone), then broadcast. Guard the
continuation against the wallet's duplicate `/tx` GET (see the idempotency note in
`nexa-wallet-connection`).

For combining the counterparty's signatures into **your own retained copy** of the offer (rather
than trusting the returned tx wholesale), libnexakotlin provides
`iTransaction.mergeUnlockingScripts(other: iTransaction)`: it copies input unlocking scripts from
`other` into any of your inputs whose script is still empty — but **only if `other.idem == idem`**
(a returned tx whose idem differs is a different spend and is silently ignored — check idem
equality yourself first if you want to report that), and it skips any input whose script in
`other` begins with `OP_RETURN` (a communications-data slot, not a finished satisfier). Because
`idem` excludes unlocking scripts, the counterparty adding their signatures does not change it —
which is exactly what makes this merge well-defined.

##### Parameterized outputs: `OP.TMPL_SCRIPT` placeholders the completing wallet fills in

A partial tx can carry outputs whose **destination is deliberately left open** for whichever wallet
completes it. The placeholder opcode is `OP.TMPL_SCRIPT` (`0xf2`, rendered `<script>` in ASM;
`OP.TMPL_DATA`/`OP.TMPL_PUBKEYHASH` are the finer-grained variants) in the output's TEMPLATE
script, and the completer's `BIND_OUTPUT_PARAMETERS` step is what resolves it into a concrete
locking script from the completing wallet's own keys:

```kotlin
// "pay <amount> native NEXA to WHOEVER completes this tx" (e.g. an offer's payment demand):
NexaTxOutput(chain, amount, SatoshiScript(chain, SatoshiScript.Type.TEMPLATE, OP.PUSHFALSE, OP.TMPL_SCRIPT))
// "deliver <qty> of token gid to whoever completes" (e.g. the asset side the buyer receives):
NexaTxOutput(chain, dust(chain), SatoshiScript.grouped(chain, gid, qty) + OP.TMPL_SCRIPT)
```

`SatoshiScript.parameterized(): Boolean` tests whether a script still contains such placeholders —
useful on the verification side (below): a parameterized proposal output legitimately comes back
*changed* (the wallet bound it), so exact-match checks must skip it. (The mint-on-demand half-tx in
`nexa-tokens-and-groups` Pattern 9 uses the grouped form for exactly this reason.)

#### Pattern 6b: Validate a returned or partial tx against a trusted node — `sendTxVal`

When a completed (or still-partial) tx comes back from a counterparty — a wallet's `/tx` callback,
an offer taken from an order book — you want to know it is valid **before** broadcasting or
accepting it, and for a *partial* tx broadcasting isn't even possible yet. libnexakotlin exposes
the node's transaction-validation P2P service for exactly this:

```kotlin
val p2pnode = wallet.blockchain.req.net.getp2p()          // a P2P client to the trusted node (suspend)
p2pnode.sendTxVal(tx) { reply: String -> … }              // async; reply is the node's validation JSON

// A synchronous wrapper with a timeout is the usual shape (the callback never fires if no
// txval-capable node is reachable, so ALWAYS bound the wait):
suspend fun validate(tx: iTransaction, node: P2pClient): String =
    withTimeoutOrNull(5000L) {
        suspendCancellableCoroutine { cont -> node.sendTxVal(tx) { if (cont.isActive) cont.resume(it) } }
    } ?: ""
```

Parsing the reply — three shapes to handle, in this order:

1. **Empty string** — your timeout fired (node unreachable / txval not offered). Treat as
   "cannot validate", not as valid.
2. **The plain, non-JSON text `transaction already in mempool`** — the tx is already known
   (typically the wallet broadcast it before your callback ran). This is success; check for it
   *before* JSON-parsing, which would otherwise throw.
3. **JSON** — `parseTxValReply(reply): TxValReply` (libnexakotlin `p2p.kt`):

```kotlin
data class TxValReply(val txid: String, val isValid: Boolean, val isMineable: Boolean,
    val isFutureMineable: Boolean, val isStandard: Boolean,
    val metadata: TxValMetadata,                 // size, txfee, txfeeneeded
    val errors: List<String>,                    // tx-level errors
    val inputs_flags: TxValInputsFlags,          // per-input validity under standard flags
    val inputs_mandatoryFlags: TxValInputsFlags) // per-input validity under mandatory (consensus) flags
// TxValInputsFlags(isValid, inputs: List<TxValInputs>); TxValInputs(isValid, metadata, errors)
// TxValInputs.metadata: amount, constraint, constraintType, outpoint, satisfier, sequence, spentAmount
```

How to judge a **partial** tx: filter the tx-level `errors` for ones that don't apply yet —
`min fee not met` is expected (the completing wallet funds the fee later) — then require every
input in `inputs_mandatoryFlags` to be `isValid`. Per-input `errors` strings worth branching on:
`input-does-not-exist` / `inputs-are-missing` (the input's outpoint is gone — probably spent; the
offer is dead) and `inconsistent input value` (a declared input amount ≠ the real UTXO value —
the same fault as the reject-16 in Pattern 3's note). The per-input `metadata.constraint` and
`metadata.outpoint` echo what the node saw, so you can also confirm *which* UTXO each input spends
(libnexaapp's built-in `/assets` proof verification rides this same message — see
`nexa-tokens-and-groups` Pattern 8).

Requirements: a **trusted node** reachable over the wallet's P2P connection that offers the
tx-validation service (pin it with `exclusiveNodes` — see `nexa-wallet-lifecycle-and-chain`
Pattern 5); validation trusts that node's view, so this is an owner-node tool, not a trustless
check.

#### Pattern 6c: Verifying a returned proposal, and selectively signing your side

When the wallet's `/tx` callback returns a tx built from your proposal (Pattern 2 /
`nexa-wallet-connection`), don't trust it wholesale. The robust continuation does three things:

```kotlin
// 1. Every output you proposed must survive — except parameterized ones (the wallet binds those):
for (pout in txProposal.outputs) {
    if (pout.script.parameterized()) continue          // placeholder output — wallet legitimately filled it
    if (txReturned.outputs.none { it == pout }) return reject("outputs were modified")
}

// 2. If your inputs are unsigned in the returned tx, sign ONLY the inputs YOU proposed —
//    match by outpoint, restore your own Spendable (the returned tx doesn't carry your
//    priorOutScript), then sign that index. Never sign an input you didn't propose: a malicious
//    counterparty could include a UTXO you happen to own and trick you into signing it away.
for (pin in txProposal.inputs) {
    for ((idx, rin) in txReturned.inputs.withIndex()) {
        if (rin.script == SatoshiScript(chain) && pin.spendable.outpoint == rin.spendable.outpoint) {
            rin.spendable = pin.spendable              // your amount + priorOutScript back in place
            signInput(txReturned, idx.toLong(), byteArrayOf())   // top-level fn; empty = default sighash
            break
        }
    }
}

// 3. Validate the whole tx against the trusted node (Pattern 6b) BEFORE broadcasting / recording it.
```

`signInput(tx, idx, sigHashType, serializedTx = null): Boolean` is a top-level libnexakotlin
function (`wallet.kt`) — the per-input signer under `txCompleter`'s `SIGN`, usable directly when
you need index-level control like this.

##### Store-and-serve offers (a marketplace "for sale" table)

The half-tx offer above doesn't have to be pushed to a counterparty immediately — a marketplace
persists it and serves it to *any* future buyer:

- **List:** the seller's wallet PARTIAL-signs the half tx (its asset input + the payment-demand and
  fee outputs, pushed with `NOFUND or NOPOST or NOSHUFFLE or PARTIAL`); your `/tx` continuation
  verifies it (Pattern 6c), validates it (Pattern 6b), **adds the buyer-side parameterized outputs**
  (the grouped `+ OP.TMPL_SCRIPT` outputs that will deliver the asset to whoever completes), and
  persists the tx (`tx.BCHserialize(SerializationType.DISK)` bytes are the natural storage form).
- **Buy:** rehydrate (`txFor(chain, BCHserialized(bytes, SerializationType.DISK))`), re-annotate the
  inputs — a deserialized tx's `spendable.amount` is `-1` and `priorOutScript` empty, so look each
  prevout up (`blockchain.req.getTx(outpointTxHash)`, or electrum) and fill them in — then push to
  the buyer's wallet with **`flags=0` and `inamt=` the sum of the (re-annotated) input amounts**:
  the buyer's wallet funds the payment side, binds the parameterized outputs to its own keys, signs,
  and broadcasts. (`iTransaction.inputTotal` / `outputTotal` are the library's summing properties —
  both throw on a negative amount, i.e. on undiscovered prevouts, which makes forgetting the
  re-annotation step loud.)
- **Invalidate:** an offer dies when the seller spends its input elsewhere — sweep stored offers
  and check each input's outpoint via electrum `getUtxo` (`status != "unspent"` ⇒ delist); see
  `nexa-electrum-monitoring` Pattern 6.
- **Sizing note:** keep one asset input per stored offer. The seller signs once, up front, and the
  signature must stay valid however the buyer extends the tx — the sighash coverages that make that
  work are per-input (see `txCompletionReference.md`), and the single-input offer is the shape whose
  coverage is well-trodden.

### Pattern 7: Read-only inputs — reference a UTXO without spending it

Nexa transactions can include inputs that are **read, not spent**
(`https://spec.nexa.org/script/read-only-inputs/`). A read-only input brings a UTXO's data into
the transaction — contracts in the same tx can introspect its script, visible args, amount, and
group via the prevout accessors (`nexa-npl-smart-contracts` Pattern 9) — while the UTXO itself
survives, so **many transactions can read the same UTXO in parallel** (versus the
destroy-and-recreate "import and rewrite" pattern, which serializes all readers).

The wire rules, per the spec:

- An input carries a **type byte**: `0` = normal UTXO spend, `1` = READONLY. (libnexakotlin's
  `NexaTxInput` exposes this as `var type: Byte = 0`, serialized ahead of the outpoint — the
  wallet/`txCompleter` machinery only builds ordinary spends, so a read-only input is one you
  construct and set by hand.)
- A read-only input's **`amount` and `sequence` must be 0**, and its value is excluded from the
  tx's input total (it contributes no funds — the tx still needs at least one normal input,
  and a tx of *only* read-only inputs is invalid).
- Its satisfier script may be **empty** (pure data read) **or a valid satisfier** — and if
  non-empty it must validate normally even though nothing is spent. A valid signed read-only
  input is therefore an on-chain **proof of ownership** of the UTXO without consuming it
  (dividend/reward-distribution flows); a contract relying on that proof must introspect that
  the read-only input's satisfier length is non-zero, since an empty satisfier is also legal.
- **Grouped assets on a read-only input are not counted** for conservation or introspection.
  The one exception: a **group authority with the BATON flag** whose read-only input carries a
  valid non-empty satisfier grants its authority powers to the tx **without being consumed** —
  see `nexa-tokens-and-groups` Pattern 9.
- **The referenced UTXO must be confirmed.** Block validation runs read-only checks before
  outputs/inputs ("ROTOTI"), so a UTXO created in a block cannot be used read-only in that same
  block, and unconfirmed outputs can't be read-only referenced from the txpool. Wait for the
  producing tx to confirm before building read-only readers against it.

When signing a read-only input (the ownership-proof or baton case), the spec recommends an
`…/ALL`-output sighash so the signature strictly pins how the transaction uses the proven
powers. The classic use case for the whole feature is a shared **oracle/state UTXO**: one party
maintains a data-bearing UTXO, and any number of contracts read it per tx without racing to
destroy and recreate it.

## Common mistakes and anti-patterns

### Mixing satoshi and whole-NEXA units

**Wrong**:
```kotlin
out.amount = 1000                          // 1000 sat = 10 whole NEXA, probably not what you meant
```
or
```kotlin
val uri = "$addr?amount=${10.nexa}"        // "?amount=1000" -- but BIP21 expects whole NEXA
```

**Right**:
```kotlin
val priceInWholeNexa = 1000L
out.amount = priceInWholeNexa.nexa         // 100,000 sat = 1000 whole NEXA ✓
val uri = "$addr?amount=$priceInWholeNexa" // "?amount=1000" -- correct: whole NEXA ✓
```

Mnemonic: **`.amount` is sat, `?amount=` is whole NEXA.**

### Forgetting to set `input.sequence` on a tx with `nLockTime`

**Wrong**:
```kotlin
tx.lockTime = futureTimestamp
val input = NexaTxInput(spendable)
input.script = satisfier
tx.add(input)                              // input.sequence defaults to 0xffffffff = FINAL → locktime ignored
```
*The mempool rejects with `Locktime requirement not satisfied` even when timestamps are
well past the deadline. See `nexa-locktime-cltv` for the full story.*

**Right**:
```kotlin
input.sequence = 0xfffffffeL               // < SEQUENCE_FINAL → enables locktime
```

### Looking up an output by index alone

**Wrong**:
```kotlin
val output = tx.outputs[0]                 // ASSUMES our output is at index 0
```
*By default Wally may add change outputs and reorder inputs/outputs for privacy, so in an
ordinary funding push you cannot assume your output's index. `TDPP_FLAG_NOSHUFFLE` (`4`) asks
the wallet to preserve input/output order (offer/swap protocols set it so they can rely on
positions), but even with it set the wallet still **adds** funding/change outputs — so your
output's absolute index is still not guaranteed unless your protocol fully fixes the layout.
Identifying the output by content is the robust rule regardless.*

**Right**: identify the output by content (amount + script type/argsHash):

```kotlin
val expected = listing.priceNexa.nexa + CONTRACT_FEE_BUFFER_SATOSHIS
val vout = tx.outputs.indexOfFirst { out ->
    out.amount == expected /* && out.script matches our P2T template */
}
```

### Not capturing the on-chain amount from the wallet callback

**Wrong**:
```kotlin
listing.fundedSatoshis = expectedAmount   // what we asked for
```
*The wallet may add a few sat for a more precise fee, or you may have computed
`expected` slightly off. Use the actual on-chain output amount.*

**Right**:
```kotlin
listing.fundedSatoshis = tx.outputs[vout].amount
```

### Building outputs against a stale chain selector

**Wrong**:
```kotlin
val tx = txFor(ChainSelector.NEXA)        // hardcoded mainnet — fails silently on testnet
```

**Right**: always route through a single `DEFAULT_CHAIN` constant.

```kotlin
val tx = txFor(DEFAULT_CHAIN)
```

### Constructing `Spendable` without `priorOutScript`

**Wrong**:
```kotlin
val spendable = Spendable(DEFAULT_CHAIN).apply {
    amount = value
    outpoint = NexaTxOutpoint(fundingTxid, fundingVout)
}
```
*The signer / contract spender doesn't know what locking script we're satisfying, so it
can't compute sighashes or do template lookup.*

**Right**:
```kotlin
val spendable = Spendable(DEFAULT_CHAIN).apply {
    amount = value
    outpoint = NexaTxOutpoint(fundingTxid, fundingVout)
    priorOutScript = lockingScript        // exact script of the UTXO being spent
}
```

### Not flushing the wallet's cached balance after `setOnWalletChange`

**Wrong**:
```kotlin
nexaWallet?.setOnWalletChange { w, txs ->
    serverBalance.value = w.balance       // stale — w.balance is cached
}
```

**Right**:
```kotlin
nexaWallet?.setOnWalletChange { w, txs ->
    (w as CommonWallet).clearCachedBalances()
    serverBalance.value = w.balance
}
```

(The libnexakotlin maintainer's comment in the source notes this is a TODO that should
go away in a future version.)

### Treating a 0-confirmation incoming payment as final

**Wrong**:
```kotlin
nexaWallet?.setOnWalletChange { w, txs ->
    for (txh in txs.orEmpty()) for (idx in txh.incomingIdxes)
        creditUserAndShip(txh.tx, idx)        // fires the instant the tx hits the mempool
}
```
*`setOnWalletChange` fires on every UTXO-set change, including the first *unconfirmed*
sighting (`confirmedHeight == -1`). A 0-conf tx can be replaced or simply never mined, and
a confirmed one can be re-orged out of its block. Crediting or shipping on 0-conf is a
double-spend invitation.*

**Right**: show "pending" on first sighting if you like, but gate the irreversible step on
confirmation depth — at minimum `txh.confirmedHeight >= 0`, and for higher value
`tipHeight - txh.confirmedHeight >= N` for an `N` you choose for the amount at stake. Also handle
the negative-removal case: a later `setOnWalletChange` with `txh.confirmedHeight == Long.MIN_VALUE`
means that pending tx is being dropped as invalid (double-spent/conflicted) — reverse any
"pending" UI or provisional credit you showed for it.

### Using `tx.idem.toString()` for explorer URLs

**Wrong**: assuming `.toString()` and `.toHex()` are the same on Hash256.

**Right**: always use `.toHex()` for explorer URLs and log lines.

```kotlin
val explorerUrl = "https://testnet-explorer.nexa.org/tx/${txid.toHex()}"
```

`Hash256.toString()` may include type info or surrounding formatting depending on the
libnexakotlin version.

### Trying to broadcast a tx that has no inputs

**Wrong**:
```kotlin
val tx = txFor(DEFAULT_CHAIN)
tx.add(out)                                // output only, no input
broadcastTx(tx)                            // mempool rejects: no inputs / invalid tx
```

**Right**: partial txs with only outputs are for *handing to the wallet* via the TDPP
`/tx` flow — never for direct broadcast.

### Treating an "already in mempool / block" broadcast result as a failure

**Wrong** (when broadcasting via RPC `sendrawtransaction`, or any retry/auto-claim loop):
```kotlin
val txid = nexaRpc.sendrawtransaction(txHex).toHex()   // throws if node already has this tx
// caller marks the operation FAILED on the exception
```
*A node that already accepted the tx rejects a re-broadcast with `txn-already-in-mempool`
(or `already in block chain` once mined). Treating that as failure makes idempotent retry
loops thrash and can flip a perfectly-settling tx to a "failed" state in your DB.*

**Right**: fold the "already known" rejections into success and recover the txid locally:
```kotlin
if (msg.contains("txn-already-in-mempool", true) || msg.contains("already in block chain", true))
    return BroadcastResult(success = true, txid = extractTxidAndOutpoint(txHex).first)
```

### Paying a flat fee on a self-funded covenant spend (reject code 66)

A covenant SPEND that funds its own fee from the input UTXO — no wallet `txCompleter` computing
fees for you — must pay a **size-based** fee, not a fixed buffer. The floor is
`fee ≥ ceil(txSize · MinFeeSatPerByte)` where **`MinFeeSatPerByte = 1.01`** (libnexakotlin
`blockchainConst.kt`; the node enforces the same). A fee that's too small is rejected by the node
with `NexaRpcException(66, "mempool min fee not met")` — **not** a script error.

**Wrong**: a flat hop fee that ignores the tx's serialized size.
```kotlin
val fee = 500L                                  // ~700-byte spend needs ~770 sat → reject 66
out.amount = in - fee
```

**Right**: measure the signed tx's wire size and price the fee from it. The wire size is invariant
to the output *amount* value and to the fixed-length Schnorr sig, so:
```kotlin
// 1. build the output with a placeholder amount, 2. sign once to realize the full satisfier,
// 3. measure, 4. set the real amount, 5. re-sign (ALL/ALL fixes size, so fee is unchanged):
val size = tx.BCHserialize(SerializationType.NETWORK).size
val fee  = ceil(size * DesiredFeeSatPerByte).toLong()   // DesiredFeeSatPerByte = 1.1
out.amount = inputValue - fee
// re-sign; size/fee are unchanged by the re-sign
```
A **terminal** spend (e.g. a cancel that keeps no buffer) can sidestep this by dumping the whole
overfund buffer to fee — incidentally always ≥ the floor. But a **continuing** spend that
preserves a buffer (transfer/list) must compute the fee. If a wallet is in hand,
`CommonWallet.minFeeForSize(size)` is the same `ceil(size · 1.01)`.

### Letting a stale 0-conf backlog wedge the wallet (every tx → code 73)

**Wrong**: a long-lived dev/server wallet that never purges unconfirmed txs. On testnet (infrequent
blocks; ITs that don't wait for confirmation) it accumulates UNCONFIRMED txs the node never
accepted, then funds new txs from **phantom 0-conf change** whose parents the node doesn't have. The
node then defers **every** relay — even a genesis spending confirmed coins — with **reject code 73
"please wait for wallet sync"**, and nothing reaches the mempool. It looks like a node outage but the
node is fine (verify with RPC `getblockchaininfo`: `blocks == headers`, `initialblockdownload = false`).

**Right**: purge the stale 0-conf set so the wallet spends only CONFIRMED UTXOs.
```kotlin
wallet.cleanUnconfirmed()        // drops stale 0-conf txs (alongside the existing cleanReserved())
```
Call it in server boot and in IT self-heal blocks — same "nothing genuinely in-flight at boot"
rationale as `cleanReserved()`. This WALLET-STATE wedge (all txs affected, cleared by purging
unconfirmed) is distinct from the single-output covenant-spend transient-73 (cleared by RPC
broadcast; see Pattern 3). See `nexa-wallet-lifecycle-and-chain` and `nexa-debugging-onchain-errors`.

## Security considerations

- **Capture the on-chain values, don't trust your local computations.** When the
  wallet's `/tx` callback arrives, parse the tx hex and read the actual amounts/scripts
  out of it. A wallet bug or man-in-the-middle could have altered the broadcast tx;
  recording your *expectation* instead of the *observation* leaves you vulnerable to
  paying out against an output that doesn't exist.

- **Validate addresses at the API boundary, not just inside builders.** A user-submitted
  `recipientAddress` string can be malformed. `PayAddress(str)` throws on bad cashaddr;
  let it.

- **Fees are not optional in NEXA.** A tx with `inputSum == outputSum` (no fee budget)
  is rejected with `mempool min fee not met`. For contract spends where outputs are
  constrained by visible args, build a fee buffer into the *funding* tx (overfund the
  contract output). See `nexa-npl-smart-contracts`. The required fee scales with the tx's
  serialized size (sat-per-byte), so size the buffer to the actual spend — a constant that
  works for a small one-input/one-output spend can underpay a larger multi-output one.

- **0-conf and reorgs are a settlement-finality concern, not just a UX one.** An
  unconfirmed receive is not money in the bank — it can be double-spent before mining, and
  even a mined tx can be orphaned by a reorganization. Treat `confirmedHeight == -1` as
  "pending" and require a value-appropriate confirmation depth before anything
  irreversible. This is separate from the on-chain-amount-capture rule above: capturing the
  observed amount protects against *what* was paid; confirmation depth protects against
  *whether it stays* paid.

- **Don't broadcast an unsigned tx.** The libnexakotlin API will happily call
  `broadcastTransaction` on a tx with empty satisfier scripts; the network rejects it,
  but you also leak the partial state to whoever sees the network broadcast. Only call
  `broadcastTx` after the input satisfier is fully populated.

- **`tx.toByteArray()` serializes in network format.** Don't confuse with
  `tx.BCHserialize(SerializationType.DISK).toByteArray()` which is the on-disk format —
  the network broadcast wants the network format.

- **Replay protection**: NEXA has chain-specific tx serialization (`ChainSelector`
  determines the format), so a tx built for testnet cannot replay on mainnet and vice
  versa. This is why you must `txFor(DEFAULT_CHAIN)` correctly.

- **Server-broadcast spends of contract UTXOs reveal whatever you put in the spender
  args on-chain forever.** For a secret-reveal contract, this is the point — the secret
  becomes public when the seller claims. Be aware that the spender args are also
  visible in the broadcast tx hex, so anything you put there (e.g., a secret) is public
  the instant you broadcast.

## Related skills and references

- `nexa-wallet-lifecycle-and-chain` — where the `wallet` / `nexaWallet` in these patterns comes from:
  creating/restoring/opening a `Bip44Wallet`, connecting its SPV chain, and the `synced()` /
  `balance` state that must be ready before you `send`/`txCompleter`/broadcast.
- `nexa-npl-smart-contracts` — how to construct the locking script that goes into
  `out.script` for a contract-funding output, and the satisfier for spending it.
- `nexa-tokens-and-groups` — when the output carries a native token (group): how to attach a
  group + quantity to `out.script` (`ofGroup`/`payTo`, `SatoshiScript.grouped`) and why the
  token amount is not `out.amount`.
- `nexa-locktime-cltv` — the input-sequence and locktime-value rules for any tx that
  uses `OP_CHECKLOCKTIMEVERIFY`.
- `nexa-wallet-connection` — how the partial-tx hex gets to the wallet and how the
  callback comes back.
- `nexa-identity-and-addresses` — how to validate `recipientAddress` strings before
  putting them in an output.
- `nexa-rpc-node-client` — the full `org.nexa:nexarpc` JSON-RPC client when you broadcast and
  look up txs through a node you operate (`sendrawtransaction`/`enqueuerawtransaction`,
  `getrawtransaction`/`gettransactiondetails`), as opposed to the SPV/P2P path here.
- `nexa-electrum-monitoring` — the read/watch side: detect when a funded contract UTXO is spent or a
  pushed offer is taken (`getUtxo`/`getHistory` on each new block), and an alternative `sendTx`
  broadcast path — for scripts/outpoints your own wallet doesn't track.
- `nexa-capd-messaging` — coordinate a multi-party / partial-tx offer (the half-tx swap idiom above)
  off-chain when the parties have no direct connection, before broadcasting the assembled tx.
- `nexa-script-machine-testing` — a test-time check (test source set, not the live send path): replay
  the parent (funding) + child (spend) tx through the real script VM
  (`ScriptMachine(parentHex, childHex)`) and confirm it executes cleanly while developing the
  spend. The VM checks script validity offline; it does **not** model the fee or 0-conf/MTP rules
  above, so a clean VM run still needs those runtime checks.

### Supporting files in this folder

- `simpleapi-cheatsheet.md` — every `.nexa` / `.sat` / `.mexa` / `payTo` / `ofGroup` / `ofToken` /
  `txOutputFor` / `dust` extension with a one-line example each.
- `txCompletionReference.md` — the full `txCompleter(...)` signature, its named tail args
  (`adjustableOutput`, negative `inputAmount`, `signInput`), and the sighash-type model behind the
  half-tx offer (the on-demand detail for Pattern 6).
- `templates/build-partial-tx.kt` — drop-in builders for the three build flavors (BIP21 push,
  partial-tx offer + `createTdppUrl`, full-construct + `txCompleter`), including the half-tx swap
  offer and the `TxCompletionFlags` combos.
- `templates/broadcast-tx.kt` — wrapper around `broadcastTransaction` with retry and outcome
  categorization (Accepted/already-known, FeeTooLow, Rejected, TransientError).