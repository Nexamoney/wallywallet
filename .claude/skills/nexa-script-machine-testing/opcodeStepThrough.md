# Stepping a reveal-vs-refund spend, rule by rule

A worked walkthrough of stepping a two-rule contract (a hash-lock that can be **claimed** by
revealing a secret, or **refunded** after a timeout) through the script VM, watching the stack
change. The goal is to show *how* to drive and read the VM at instruction granularity so you can
locate exactly where a real spend diverges from what you expected.

This uses the step/inspect API from `SKILL.md` Pattern 5 and the `next()` driver from Pattern 8.
For the string format printed at each step, see `stackItemFormat.md`.

## The contract, in spend terms

Such a contract compiles to a **template** with two rules and is funded into a P2T output. A spend
provides a **satisfier** (the spender's witnesses) selecting and satisfying one rule:

- **Claim rule** — satisfier supplies the preimage and a signature; the template hashes the
  preimage, compares it to the locked hash, and checks the signature.
- **Refund rule** — satisfier supplies the refunder's signature; the template enforces
  `OP_CHECKLOCKTIMEVERIFY` against the spend's `nLockTime` and checks the signature.

All three scripts run in order: **constraint** (the per-instance args baked into the locking
script), then **satisfier** (the spender's solution — its results are moved onto the **alt stack**,
signalled by `ALT_STACK_LOADED`), then **template** (the rule logic, which consumes the alt-stack
witnesses and must finish with a clean main stack).

## Driving it script-by-script

With the two-tx constructor (or single-input `advance = true`), the satisfier and constraint are
**pre-run during construction** and the template is staged, so a single `next()` drives the
template. To watch all three phases explicitly, use the no-context triple-script form or
`advance = false`, which runs nothing up front:

```kotlin
Initialize()
// advance = false: nothing runs during construction, so we can next() each script in turn
val sm = ScriptMachine(spendTx, /*inputIdx*/ 0, /*utxo*/ contractUtxo, /*advance*/ false)

var r = sm.next()                                  // runs the CONSTRAINT script
check(r.first == "constraint" && r.second == "No error(0)")
println("after constraint: ${sm.getState().mainstack}")

r = sm.next()                                      // runs the SATISFIER; results move to alt stack
check(r.first == "satisfier")
check(r.third == ScriptMachine.SpecialOperation.ALT_STACK_LOADED)
println("after satisfier:  main=${sm.getState().mainstack} alt=${sm.getState().altstack}")

r = sm.next()                                      // runs the TEMPLATE (the rule logic)
check(r.first == "template" && r.second == "No error(0)")
check(sm.mainStackAt(0) == "")                     // clean main stack ⇒ the rule was satisfied
sm.delete()
```

Reading the phase prints tells you **which script** broke: a `verify` that fails inside the
template surfaces a `scriptErr` containing `"failed"`, while a malformed satisfier fails in the
satisfier phase.

## Stepping the template instruction by instruction

When the template phase fails (or you just want to see each opcode's effect), stage it and
single-step, snapshotting the stack after each instruction. `step()` returns `true` while it
advances and `false` at the end/error; `pos` is the current byte offset.

```kotlin
val sm = ScriptMachine(parentTxHex, childTxHex)    // two-tx replay: satisfier+constraint pre-run
sm.next(false)                                     // stage the template, ready to step
println("template ASM: ${sm.template!!.toAsm(" ")}")   // see the opcodes you're about to walk

var i = 0
while (sm.step()) {
    val st = sm.getState()
    // st.mainstack/altstack are BOTTOM-first; reverse for top-first reading (see stackItemFormat.md)
    println("step ${i++}: pos=${st.pos} top=${sm.mainStackAt(0)} main(top→)=${st.mainstack.asReversed()}")
}
println("result: ${sm.scriptErr}")                 // "completed" + empty main stack ⇒ valid
sm.delete()
```

A typical claim-rule trace reads as: the rule index/selector is consumed, the revealed preimage is
hashed (`OP_HASH256`/`OP_HASH160`), the result is compared to the committed hash
(`OP_EQUALVERIFY` — this is the instruction that fails if the preimage is wrong), then the
signature is checked (`OP_CHECKSIGVERIFY`). For the refund rule you instead see
`OP_CHECKLOCKTIMEVERIFY` compare the spend's `nLockTime` against the encoded timeout (it fails if
the timeout hasn't been reached *in the script's view* — note the VM does not know the chain's
median-time-past; see `nexa-locktime-cltv`).

## Pinpointing a failure with a breakpoint

If you know roughly where the rule should fail, breakpoint that byte offset and inspect just before
and after, rather than printing every step:

```kotlin
val sm = ScriptMachine(parentTxHex, childTxHex)
sm.next(false)
sm.setBreakpoint(/*byte offset of the suspect opcode*/ 12)
sm.cont()                                          // run up to the breakpoint
println("at breakpoint: top=${sm.mainStackAt(0)}, next-deeper=${sm.mainStackAt(1)}")
sm.step()                                          // execute the suspect opcode
println("after: ${sm.scriptErr ?: "ok"}  top=${sm.mainStackAt(0)}")
sm.clearBreakpoint(12)
sm.delete()
```

This is the fast way to answer "did `OP_EQUALVERIFY` see the two values I expected on top of the
stack?" — the most common reason a reveal rule rejects (preimage encoded differently than the hash
was committed to, or the two operands in the wrong order).

## Comparing claim vs refund as forks of one state

To explore both rules from a shared point (e.g. up to the rule-selector), `clone()` the live
machine and continue each fork independently — each clone owns its own native handle, so
`delete()` every one:

```kotlin
sm.next(false)
// ... step up to the branch point ...
val claimFork  = sm.clone()
val refundFork = sm.clone()
claimFork.cont();  println("claim path:  ${claimFork.scriptErr}")
refundFork.cont(); println("refund path: ${refundFork.scriptErr}")
claimFork.delete(); refundFork.delete()
```

## Reading the printed stacks correctly

Every value printed above follows the `stackItemFormat.md` grammar (`BYTES 1 03h 3`,
`BYTES 0 false 0` for an empty item, `BIGNUM ...`). Two reversal traps:

- `mainStackAt(0)` is the **top**; `getState().mainstack` is **bottom-first** (its last element is
  the top). The traces above call `.asReversed()` so both read top-first.
- A clean, satisfied spend ends with `mainStackAt(0) == ""` (nothing left) and a `scriptErr` of
  `"No error(0)"` (whole-script run) or `"completed"` (step-loop reached the end). Anything else —
  a leftover value, or a `scriptErr` containing `"failed"` — means consensus would reject it.