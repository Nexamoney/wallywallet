# `txCompleter` reference — full signature, named args, and the sighash-type model

The exact `CommonWallet.txCompleter(...)` signature, the named tail arguments that give you
fine-grained control over completion, and the NEXA sighash-type model that makes partial / half-tx
offers cryptographically safe. Read `SKILL.md` Pattern 6 first for the `TxCompletionFlags` table,
the common "fund + sign + broadcast" example, and the half-tx swap-offer idiom — this file is the
on-demand detail behind them. Grounded in libnexakotlin; verify the signature against the resolved
jar (`nexa-project-setup` § "Verifying API signatures") before relying on it.

## Full signature

```kotlin
fun txCompleter(
    tx: iTransaction, minConfirms: Int, flags: Int,
    inputAmount: Long? = null,           // assume existing inputs already supply this many sat
    adjustableOutput: Int? = null,       // index of the output adjusted for fee/surplus
    destinationAddress: PayAddress? = null,  // address for substituted/bound output templates
    changeAddress: PayAddress? = null,   // where native + token change goes
    sigHashTypeOverride: ByteArray? = null,
    contractId: ByteArray? = null)
```

`flags` is an `or` of `TxCompletionFlags` (the table is in `SKILL.md` Pattern 6).

## The named tail args

The named tail args are how you control completion precisely; two are worth internalizing:

- **`adjustableOutput`** is the output index that `DEDUCT_FEE_FROM_OUTPUT` (and surplus
  handling) acts on — so a sweep is `SPEND_ALL_NATIVE or DEDUCT_FEE_FROM_OUTPUT` with
  `adjustableOutput = <index of the output to take the fee from>` (e.g. the last output). Passing
  `null` here means "error rather than silently adjust an output," which is the safe default when
  no output is meant to flex.
- **`inputAmount`** declares how much coin the inputs *already present* supply, so the completer
  won't go look them up. It can be **negative**: passing a negative `inputAmount` on a `PARTIAL`
  fund pass tells the completer to pull in extra native coin beyond the outputs — the standard way
  to seed enough fee in an early phase so a *later* completion phase (yours or the counterparty's)
  can finish without underpaying. The completer **does not reorder** the inputs/outputs you give
  it (output shuffling, when it happens, is the wallet's separate `NOSHUFFLE`-gated behavior, not
  the completer's).

For signing a single input by hand (below the `PARTIAL`/`SIGN` flag level), libnexakotlin also
exposes the lower-level `signInput(tx, idx: Long, sigHashType: ByteArray): Boolean`.

## The sighash-type model — why half-tx offers stay valid when the counterparty completes them

A NEXA signature is a 64-byte Schnorr signature followed by the **sighash-type bytes**, which
select *which parts of the tx the signature commits to*
(`https://spec.nexa.org/transactions/sighashtype/`). The default — the single byte `0` — means
**sign all inputs and all outputs** (the most common payment; an empty sighash *is* `0`, saving a
byte). The flag byte's upper 4 bits choose the **input** coverage and the lower 4 bits the
**output** coverage:

- inputs: `0` = all inputs (locked: none can be added/removed/modified); `1` = first N inputs (a
  subsequent byte gives N — lets *other parties add inputs*); `2` = **this input only** (others can
  add/remove/modify the rest).
- outputs: `0` = all outputs; `1` = first N outputs (lets others *append* outputs); `2` = **two
  specified outputs N, M** (e.g. "what I receive" + "my change"; everything else stays open).

This is the cryptographic basis of the partial-tx / half-tx offer idiom (`SKILL.md` Pattern 6):
the offerer signs their input(s) with a sighash that covers **this input** plus the **specific
output(s) demanding payment**, but *not* all outputs/inputs — so the counterparty's wallet can add
its own funding inputs and a change output without invalidating the offerer's signature. Conversely,
a "no outputs" sighash is dangerous (whoever holds the tx can redirect all the value), and a
`2`-input sighash with N=0 (no inputs) can be replayed against another prevout locked by the same
key — so choose the narrowest coverage that still pins what must not change. The
`sigHashTypeOverride` arg above and `signInput`'s `sigHashType` take exactly these bytes.

You rarely need to assemble the sighash bytes by hand — `iTransaction` carries two builders that
produce the common partial-tx coverages from the tx's current shape:

```kotlin
// Commit to everything currently in the tx, but allow inputs and/or outputs to be APPENDED:
val sh1: ByteArray = tx.appendableSighash(extendInputs = true, extendOutputs = true)
// Commit to the first N inputs / M outputs (Int.MAX_VALUE = "all"):
val sh2: ByteArray = tx.firstnSighash(numInputs = 1, numOutputs = 2)
```

`appendableSighash()` is the natural choice for the offer side of a half tx ("what I've built is
fixed; you may add your funding/change"), fed to `sigHashTypeOverride` or `signInput`.

This is also what the TDPP wire flag `partial` (bit 3) asks the *receiving wallet* to do: per the
DPP spec, a wallet completing a `partial`-flagged push signs with an extendable coverage so
later parties can add inputs/outputs without invalidating its signatures — the wallet-side
mirror of the model above (`nexa-wallet-connection` § "The TDPP transaction `flags` bitfield").

## Related

- `SKILL.md` Pattern 6 — the `TxCompletionFlags` table, the common fund/sign/broadcast example,
  and the half-tx swap-offer idiom this file's sighash model underpins.
- `nexa-tokens-and-groups` — the token-side completion flags (`FUND_GROUPS`,
  `USE_GROUP_AUTHORITIES`, `NO_BATON_AUTHORITIES`, `MUST_MINT`).
- `nexa-wallet-connection` — the wallet runs this same completer when it receives a `tdpp://…/tx`
  push; the TDPP `flags` bitfield maps onto these `TxCompletionFlags`.