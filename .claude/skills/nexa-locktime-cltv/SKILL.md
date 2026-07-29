---
name: nexa-locktime-cltv
description: "Builds and debugs Nexa transactions with absolute time-based spending constraints using OP_CHECKLOCKTIMEVERIFY (CLTV): timeout refunds, vesting, escrow with deadlines, and HTLCs. Use when letting a buyer reclaim funds after a timeout, sending a tx only valid after a timestamp, or debugging a refund tx that never gets mined. Triggers: OP_CHECKLOCKTIMEVERIFY, CLTV, checkLockTimeVerify, nLockTime, tx.lockTime, input.sequence, nSequence, SEQUENCE_FINAL, 0xfffffffe, median time past / MTP, 'Locktime requirement not satisfied', non-final. Covers absolute-timestamp CLTV only, not block-height locktime, BIP68 relative locktime, or OP_CHECKSEQUENCEVERIFY; not general tx construction (nexa-transaction-construction)."
---

# Nexa CHECKLOCKTIMEVERIFY and timed transactions

## When to use this skill

Trigger when a developer is writing or debugging a NEXA transaction that uses time-based
spending constraints — timeout refunds, vesting, escrow with deadlines, hashed
timelocked contracts (HTLCs). Concretely trigger on:

- Keywords: `OP_CHECKLOCKTIMEVERIFY`, CLTV, `checkLockTimeVerify`, `nLockTime`,
  `tx.lockTime`, `input.sequence`, `nSequence`, `SEQUENCE_FINAL`, `0xfffffffe`,
  median time past, MTP, time-locked transaction, timeout refund, HTLC, relative
  locktime.
- Errors: `Locktime requirement not satisfied` (immediate OR persistent), `non-final`,
  tx accepted from script verification but rejected from mempool.
- Tasks: "let the buyer reclaim after timeout", "send a transaction that's only valid
  after timestamp X", "the refund tx never gets mined", "we waited overnight and the
  refund still won't broadcast".

**Negative triggers** — do NOT use this skill for:
- Block-height locktime, sequence-based BIP68 relative locktime, or `OP_CHECKSEQUENCEVERIFY`
  (related but distinct semantics; this skill covers absolute timestamp CLTV only).
- General tx construction without locktime — use `nexa-transaction-construction`.
- Designing the contract logic that uses CLTV — use `nexa-npl-smart-contracts`.

## Mental model

`OP_CHECKLOCKTIMEVERIFY` (CLTV) is NEXA's absolute timestamp/block-height locktime
opcode, inherited semantically from Bitcoin's BIP65. A contract that calls
`checkLockTimeVerify(T)` requires the spending tx to satisfy:

```
tx.nLockTime >= T
```

That is the script-side rule. Easy to get right.

The traps are on the *consensus* and *mempool* sides:

### Trap 1 — `nSequence < 0xFFFFFFFF` is mandatory

For a tx to honor its `nLockTime` field at all, at least one input must have
`nSequence < 0xFFFFFFFF` (the `SEQUENCE_FINAL` sentinel). If every input has
`nSequence == 0xFFFFFFFF`, the tx is "final" — its `nLockTime` is ignored by consensus
and CLTV in any input script will fail with `Locktime requirement not satisfied`,
regardless of how far past the deadline you are.

`libnexakotlin`'s `NexaTxInput.sequence` **defaults to `0xFFFFFFFF`**. You must set it
explicitly:

```kotlin
input.sequence = 0xfffffffeL          // non-final, locktime honored, no BIP68 effects
```

libnexakotlin's own field declaration corroborates this exactly:
`var sequence: Long = 0xffffffff //!< enable locktime if not 0xffffffff` (in
`nexaTransaction.kt` / `bchTransaction.kt`). The field type is `Long`; in Kotlin the literal
`0xFFFFFFFE` already exceeds `Int` range, so `0xFFFFFFFE` and `0xfffffffeL` are identical —
either is correct.

The conventional value is `0xfffffffe`, exactly one below `SEQUENCE_FINAL`. This is
also conventionally below the BIP68-relative-locktime activation threshold, so it
doesn't trigger relative-locktime semantics by accident.

### Trap 2 — Mempool requires `nLockTime <= medianTimePast`

A tx with `nLockTime > MTP` (median time of the last 11 blocks) is non-final from the
mempool's perspective and gets rejected. You can satisfy the contract's
`checkLockTimeVerify(T)` only by setting `tx.nLockTime >= T`, but then the mempool
won't accept the tx until `MTP >= T`.

**MTP lags wall-clock by 30 minutes to several hours**, even more on low-activity
testnets where blocks are infrequent. A "30-minute timeout" contract often takes 2+
hours of wall-clock time before the refund tx will actually broadcast.

Approximation in your server: `tipTime >= MTP` always, so you can pre-flight check
`tipTime >= requiredLockTime` to give a clearer error than the mempool will:

```kotlin
val tipTime = nexaWallet?.blockchain?.getTip()?.time ?: 0L
if (tipTime < refundableAt) {
    // refund definitely can't broadcast yet -- tipTime is an upper bound on MTP
}
```

If you operate your own full node, you can read MTP **directly** rather than approximating it
with tip time: the `org.nexa:nexarpc` client's `getblock(...)` returns a `BlockInfo` whose
`mediantime` field *is* the median-time-past. `rpc.getblock(rpc.getbestblockhash()).mediantime`
gives the exact MTP the mempool will gate against. See `nexa-rpc-node-client`.

### Trap 3 — Heights vs timestamps in the same field

`nLockTime` (and the value compared against in CLTV) has a magic threshold:

- Values **< 500,000,000** are interpreted as **block heights**.
- Values **>= 500,000,000** are interpreted as **epoch seconds**.

`epochMilliSeconds() / 1000L` (~1.7e9) is comfortably in the timestamp range.
`tx.lockTime = 30L` would be interpreted as "block height 30" — almost certainly not
what you want. If your application reasons in time, never let an `nLockTime` value
slip below 500e6.

The opcode spec adds a subtlety beyond "pick the right range": `OP_CHECKLOCKTIMEVERIFY` **fails
outright if the contract's CLTV value and the tx's `nLockTime` are on *opposite sides* of the
500,000,000 threshold** (one a height, the other a timestamp), and also fails if the top stack item
is negative or the stack is empty (`https://spec.nexa.org/script/op-codes/` → Locktime, and BIP65).
So a contract whose `checkLockTimeVerify(T)` uses a *timestamp* can only ever be satisfied by a tx
whose `nLockTime` is also a timestamp — you cannot mix the two kinds. Keep both sides in the same
domain (this skill assumes timestamps throughout).

## Setup and versions

You need `libnexakotlin` (provides `tx.lockTime` and `NexaTxInput.sequence`) and `npl`
(provides `checkLockTimeVerify(NInt + NInt)`). Pin exact versions per `nexa-project-setup`.

Required field setters on `NexaTransaction` / `NexaTxInput`:

```kotlin
import org.nexa.libnexakotlin.NexaTxInput
import org.nexa.libnexakotlin.txFor

val tx = txFor(DEFAULT_CHAIN)
tx.lockTime = futureEpochSeconds    // Long
val input = NexaTxInput(spendable)
input.sequence = 0xfffffffeL        // Long; < 0xFFFFFFFF enables locktime
```

## Core patterns

### Pattern: A complete refund tx that respects CLTV

```kotlin
fun buildRefundTx(
    visibleArgs: SatoshiScript,
    fundingTxid: Hash256, fundingVout: Int,
    fundedSatoshis: Long, payoutSatoshis: Long,
    buyerAddress: String,
    refundableAtEpochSec: Long,
): iTransaction {
    val (spendable, satisfier) = contractSpendableAndSatisfier(
        visibleArgs, fundingTxid, fundingVout, fundedSatoshis,
        SECRET_RULE_TIMEOUT_REFUND, NexaArgs(chainSelector = DEFAULT_CHAIN))

    val tx = txFor(DEFAULT_CHAIN)
    tx.lockTime = refundableAtEpochSec        // satisfy checkLockTimeVerify
    val input = NexaTxInput(spendable)
    input.script = satisfier
    input.sequence = 0xfffffffeL              // < FINAL -- REQUIRED for locktime
    tx.add(input)

    val out = txOutputFor(DEFAULT_CHAIN)
    out.amount = payoutSatoshis
    out.script = PayAddress(buyerAddress).lockingScript()
    tx.add(out)
    return tx
}
```

### Pattern: Pre-flight MTP check before attempting refund

```kotlin
post("/api/.../refund/{id}") {
    val listing = lookupListing(id) ?: return@post call.respond(HttpStatusCode.NotFound)
    val refundableAt = listing.purchaseTimeEpochSec + TIMEOUT_SECONDS

    val nowSec = epochMilliSeconds() / 1000L
    if (nowSec < refundableAt) {
        call.respond(HttpStatusCode.Conflict,
            "refund not yet available by wall-clock; available in ${refundableAt - nowSec} seconds")
        return@post
    }

    val tipTime = nexaWallet?.blockchain?.getTip()?.time ?: 0L
    if (tipTime > 0L && tipTime < refundableAt) {
        val deltaMin = (refundableAt - tipTime + 59) / 60
        call.respond(HttpStatusCode.Conflict,
            "tip time ($tipTime) is still below contract deadline ($refundableAt). " +
            "Wait ~$deltaMin more minute(s).  Note: median-time-past may lag tip by " +
            "an additional hour or more on testnets.")
        return@post
    }

    val refundTx = buildRefundTx(/* ... */)
    try {
        broadcastTx(refundTx)
    } catch (e: Throwable) {
        val hint = if ((e.message ?: "").contains("Locktime", ignoreCase = true))
            " (MTP hasn't caught up to the contract deadline; wait longer and retry)"
        else ""
        call.respond(HttpStatusCode.InternalServerError, "broadcast failed: ${e.message}$hint")
        return@post
    }
    call.respondText("ok")
}
```

### Pattern: Pick a contract timeout that respects MTP realism

Don't pick `30 minutes` and expect refunds to be usable in 30 minutes. On a quiet
testnet, MTP can lag by 1-2 hours. Realistic guidelines:

| Use case | Suggested timeout |
| --- | --- |
| Production mainnet (active) | 1+ hour |
| Production mainnet (cautious) | 6 hours |
| Public testnet (active) | 2+ hours |
| Public testnet (quiet) | 4-12 hours |
| Regtest with auto-mining | seconds (mine on demand) |

In NPL, the timeout is a literal `Long.nx` in the DSL — bumping it changes the
template hash, so all existing UTXOs locked under the old timeout retain the old
behavior.

Production settlement contracts on public networks typically use timeouts measured in hours
to days, far longer than a 30-minute teaching example — e.g. 48 hours for a refund window
on a slow oracle-priced settlement, 3 days for an event whose resolution may stall, 24
hours for a single-party claim window. Treat those as sensible order-of-magnitude defaults
for real contracts, not the sub-hour values that only work on auto-mining regtest.

### Pattern: Validating an `nLockTime` value before assigning

```kotlin
fun setLockTimeFromEpochSec(tx: iTransaction, epochSec: Long) {
    require(epochSec >= 500_000_000L) {
        "nLockTime $epochSec would be interpreted as a block height, not a timestamp"
    }
    require(epochSec < 0xFFFF_FFFFL) {
        "nLockTime $epochSec exceeds 32-bit unsigned range"
    }
    tx.lockTime = epochSec
}
```

## Common mistakes and anti-patterns

### Leaving `input.sequence` at the default

**Wrong**:
```kotlin
val input = NexaTxInput(spendable)
input.script = satisfier
tx.add(input)                                  // input.sequence == 0xffffffff (default)
tx.lockTime = futureTime
```
*Mempool: `Locktime requirement not satisfied`. The tx is "final" because all inputs
are final, so `nLockTime` is ignored by consensus and the script's CLTV opcode
fails. Looks like a script bug — it's a sequence bug.*

**Right**:
```kotlin
input.sequence = 0xfffffffeL
```

### Setting `tx.lockTime` to a current epoch and immediately broadcasting

**Wrong**:
```kotlin
tx.lockTime = epochMilliSeconds() / 1000L      // "now"
broadcastTx(tx)
```
*Mempool MTP is always behind "now" by some amount. The tx is rejected for ~30 min to
a few hours until MTP catches up to "now".*

**Right**: only build/broadcast the locktime tx when MTP (or tipTime as a safe
proxy) is already past the lockTime you want. If you're using the contract for a
deadline, set `tx.lockTime = deadline` and only attempt broadcast after the deadline
plus MTP-lag margin has elapsed.

### Trying to outsmart MTP by setting `nLockTime` to `MTP - 1`

**Wrong**:
```kotlin
tx.lockTime = (nexaWallet.blockchain.getTip().time) - 60   // "safely below tip"
```
*Then the contract's `checkLockTimeVerify(deadline)` fails because
`tx.lockTime < deadline`. Contract gates on the deadline, not on MTP.*

**Right**: set `tx.lockTime = deadline`. Wait for MTP to reach the deadline. There's
no shortcut.

### Using milliseconds in the locktime value

**Wrong**:
```kotlin
tx.lockTime = System.currentTimeMillis()      // ~1.7e12 -- overflows 32-bit unsigned
```

**Right**:
```kotlin
tx.lockTime = System.currentTimeMillis() / 1000L
```

`nLockTime` and the CLTV stack value are 32-bit unsigned. Milliseconds blow that out
to nonsense.

### Using `nLockTime` that's a block-height when you meant a timestamp

**Wrong**:
```kotlin
tx.lockTime = 1000L          // intended "1000 seconds from now" -- interpreted as height 1000
```

**Right**: timestamps must be >= 500_000_000. Add the desired delay to current epoch:

```kotlin
tx.lockTime = epochMilliSeconds() / 1000L + 1000L     // now + 1000 sec
```

### Comparing the contract's deadline to wall-clock time and assuming refund will work

**Wrong**: a buyer sees "30 minutes since funding" and tries the refund button. Server
constructs the tx with `nLockTime = fundingTime + 1800`, broadcasts. **Rejected** —
MTP hasn't caught up.

**Right**: gate the UI "Refund" button on tipTime as the *minimum* prerequisite and
educate the user that MTP may further delay acceptance. Surface the actual error
message from the broadcast to the UI so the user knows to retry later.

### Using BIP68 relative locktime where you wanted absolute timestamps

`input.sequence` values >= 0x80000000 are reserved; values 0..0x7FFFFFFF with bit 22
set encode BIP68 relative locktime. Setting `input.sequence = 0xfffffffe` avoids
both meanings and just non-finalizes the input.

If you intentionally want relative locktime semantics, that's a different opcode
(`OP_CHECKSEQUENCEVERIFY`) and a different sequence-value encoding (consult libnexa
docs separately).

### Picking a contract timeout that's too short for the network

**Wrong**: 5-minute timeout in the contract DSL, deployed to testnet, expecting users
to refund 5 minutes after funding. They can't — MTP lags more than that.

**Right**: timeout should be at least `expected MTP lag + UX buffer`. For testnet, 2
hours is a reasonable minimum. For mainnet, 1 hour is usually fine.

If you can't change the contract (already deployed), accept that "30-minute" timeout
means "spendable about 2 hours later in wall-clock terms" and document accordingly.

## Security considerations

- **Don't set `nLockTime` further in the future than the script demands.** A tx with
  `nLockTime` further out than necessary stays non-final in the mempool longer than
  needed, increasing the window where a competing tx can spend the UTXO. Set
  `nLockTime` to exactly the contract's required value, not a "safe margin" beyond.

- **The locktime field is malleable across miners.** A miner sees your tx but doesn't
  mine it immediately; the tx remains in mempool waiting for MTP to catch up. Don't
  rely on locktime as a primary security mechanism for high-value scenarios — combine
  with off-chain agreement or oracle-based settlement when stakes are high.

- **An attacker watching the mempool can race your refund.** If your contract is
  spendable two different ways (e.g., reveal OR refund), and you broadcast the refund,
  another party who knows the secret can race to broadcast their claim tx first. The
  one mined first wins. In a contract where both rules end up paying the same party,
  this is benign; otherwise design accordingly.

- **MTP can be manipulated by miners within narrow bounds.** A miner can choose to mine
  with a slightly past timestamp (down to MTP) to slightly advance MTP, or with a
  near-current timestamp. This means MTP advancement isn't strictly monotonic on
  short scales. Don't write logic that depends on the precise instant MTP crosses a
  threshold.

- **Don't store the timeout duration as a server-side variable that can be edited at
  runtime.** It's baked into the contract bytecode. Changing it without recompiling
  the contract creates a server-side belief that diverges from the on-chain reality —
  refund attempts will fail in confusing ways.

## Related skills and references

- `nexa-npl-smart-contracts` — defining `checkLockTimeVerify(...)` in the DSL.
- `nexa-transaction-construction` — wider tx construction context.
- `nexa-tokens-and-groups` — when the timed output carries a native token (e.g. a vesting or
  time-locked token vault), the CLTV mechanics here are unchanged but the output must also
  preserve its group; see that skill for the same-group covenant.
- `nexa-rpc-node-client` — reading the exact MTP (`BlockInfo.mediantime`) and tip from a full node
  you operate, instead of approximating with wallet tip time.
- `nexa-wallet-lifecycle-and-chain` — where `nexaWallet.blockchain.getTip()?.time` (the wallet-tip
  proxy used in the pre-flight check above) comes from: the wallet's SPV chain connection.
- `nexa-script-machine-testing` — the script VM evaluates `checkLockTimeVerify`'s stack comparison, so
  you can replay a refund spend offline to confirm the *script* side is right; but the VM does not
  know the chain MTP, so a clean VM run still doesn't prove the tx is final (Trap 2 applies at
  broadcast time).
- `nexa-debugging-onchain-errors` — `Locktime requirement not satisfied` decoder.

### Supporting files in this folder

- `cltvCheatsheet.md` — one-page reference: the two-part `nSequence < 0xFFFFFFFF` + reached-deadline
  rule, the `nLockTime` height-vs-timestamp threshold (`500_000_000`), MTP lag, and timeout sizing.
- `mtpMonitor.kt` — drop-in helper that estimates current MTP (median of the last 11 header times
  via electrum `getHeadersFor`, or `tipTime` as an optimistic upper bound; node-operator exact MTP
  noted), with `timestampDeadlineReached`.