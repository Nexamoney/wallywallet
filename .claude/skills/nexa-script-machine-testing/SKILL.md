---
name: nexa-script-machine-testing
description: "Runs, simulates, debugs, and tests Nexa script execution locally by replaying a transaction spend through the actual script VM (scriptmachine), a development/test-time activity in src/test, not the production send path. Use to confirm a contract or complex tx validates before broadcasting, unit-test opcode logic, or step through a failing satisfier/constraint to see which instruction and stack state broke. Triggers: org.nexa.scriptmachine, ScriptMachine, eval/next/step/cont, mainStackAt/altStackAt, getState, MachineState, setBreakpoint, analyze2Tx, parseTemplateSpend, ScriptMachineResources, setLimits, ScriptMachineDump, BIN2BIGNUM, POST_UPGRADE_MANDATORY_SCRIPT_VERIFY_FLAGS, libnexa.so, UnsatisfiedLinkError, script debugger/simulator."
---

# Nexa script-VM execution & testing (scriptmachine)

## When to use this skill

Trigger when a developer wants to **run, simulate, debug, or test Nexa script execution
locally** — replaying a transaction spend through the actual script virtual machine to confirm a
contract or complex transaction validates, unit-testing opcode logic, or stepping through a
failing satisfier/constraint/template to see exactly which instruction and stack state broke.
This is a **development / test-time** activity: it lives in your test source set (`src/test`) and
runs as part of your test suite while you iterate on a contract — it is *not* something you wire
into your production send path. Concretely trigger on:

- Keywords: `org.nexa:scriptmachine`, `org.nexa.scriptmachine`, `ScriptMachine`, `Initialize()`
  (the scriptmachine one), `eval`, `next`, `step`, `cont`, `mainStackAt`, `altStackAt`,
  `getState`, `MachineState`, `setBreakpoint`, `analyze2Tx`, `ScriptMachineEnvironment`,
  `parseTemplateSpend`, `SpecifiedScriptTemplate`, `getResources`, `ScriptMachineResources`,
  `setLimits`, `resetResourceUse`, `swapStacks`, `SpecialOperation` / `ALT_STACK_LOADED`,
  `clone`/`copy`, `dump`/`fromDump`/`ScriptMachineDump`, `setRegister`/`getRegister`, BMD /
  `setBMD` / `BIN2BIGNUM`, `loadStacks`/`replaceStacks`/`getBinaryStack`, `tolerant`,
  `POST_UPGRADE_MANDATORY_SCRIPT_VERIFY_FLAGS`,
  `libnexa.so`, `UnsatisfiedLinkError`, "script debugger", "script simulator",
  "No spend / These transaction are not related by a spend", "The hidden args script … must
  contain only data push instructions", "Prevout template hash / input template mismatch",
  "validate every input of a multi-input spend", "measure a script's runtime stack depth /
  resource usage", two-phase init / `initialize(true)`.
- Tasks: "test that my contract spend actually executes before I broadcast it", "why does my
  satisfier fail with OP_EQUALVERIFY — let me reproduce it locally", "step through a Nexa
  script", "simulate a transaction's script verification", "replay a parent/child tx spend in
  the VM", "check my contract stays within the script resource limits", "unit-test an NPL
  contract's spend path", "inspect the stack after each opcode".
- Files: `*Test.kt` / `ContractTest.kt` that build a funding tx + a spend tx and want to assert
  the spend validates; any test harness that exercises a contract's reveal/refund/covenant rules.

**Negative triggers** — do NOT use this skill for:
- *Compiling* an NPL DSL contract to bytecode — that also calls `org.nexa.scriptmachine.Initialize()`,
  but the compile workflow (`.compile()`, scaffold, template hash) lives in `nexa-npl-smart-contracts`
  Pattern 2. This skill is about *executing/verifying* scripts, not generating them.
- Validating a finished tx by *broadcasting it to a real node* (regtest mine-and-assert) — use
  `nexa-rpc-node-client`. The script VM checks script validity offline; it does not model mempool
  policy, fees, or chain state.
- Building/signing the transaction whose scripts you want to run — use
  `nexa-transaction-construction` (and `nexa-npl-smart-contracts` for the satisfier layout).

## Mental model

`scriptmachine` (`org.nexa:scriptmachine`, Kotlin package `org.nexa.scriptmachine`) is a thin
Kotlin wrapper over a **JNI binding to the Nexa full node's actual script virtual machine**. It
is not a reimplementation of the VM — it runs the *same* C++ consensus engine the node uses,
loaded from the native shared library `libnexa.so`. That is the entire value proposition: a
script that executes cleanly here will execute the same way on-chain, so a **test** can verify a
contract spend offline — at development time, before you ever rely on that spend path in
production — without standing up a node or broadcasting anything. Treat it exactly like the NPL
compile step (`nexa-npl-smart-contracts` Pattern 2): a test-classpath tool you run while building and
iterating on a contract, not code you invoke from your live `main`/server at send time.

A Nexa template spend is three scripts that interact (`SpecifiedScriptTemplate`):

| Script | Where it comes from | Role |
| --- | --- | --- |
| **template** | the compiled contract bytecode (pushed in the satisfier, or well-known like P2PKT) | the constraint logic that must succeed |
| **constraint** | the holder's hidden args + the visible args on the locking script | per-instance parameters fed to the template |
| **satisfier** | the spender's input script (witnesses: sigs, revealed secrets, ruleIdx) | the spender's solution |

The VM executes them in order — constraint, then satisfier (its results are moved onto the alt
stack), then template — and the spend is **valid iff the run finishes with no error and a clean
main stack**. Concretely, the two success signals you assert on are:

- `status` / `scriptErr` == `"No error(0)"` (an error leaves a message; a failed `verify`
  surfaces a string containing `"failed"`; a step-loop that reaches the end leaves `"completed"`).
- `mainStackAt(0) == ""` — the template consumed everything. A leftover truthy value, or any
  error, means consensus would reject the spend.

Two facts that shape everything:

1. **It is a native-backed, JVM-only library.** `Initialize()` `System.load`s `libnexa`
   (`libnexa.dylib` on macOS / `libnexa.so` on Linux — the node's cashlib, built with
   `--enable-javacashlib`); it throws `UnsatisfiedLinkError` if that library cannot be loaded.
   **Where the native VM actually comes from:** the `scriptmachine` jar itself contains only
   `.class` files, but you do **not** normally supply `libnexa` yourself. `Initialize()` simply
   calls libnexakotlin's `initializeLibNexa()` with the default variant, and that default path
   **extracts a bundled `libnexa` from the `libnexakotlin-jvm` jar's `nativeLibs/` resources**
   (per-platform builds: `.so`, `.dylib`, `.dll`, plus arm/musl variants) into a `lib/` folder
   under the process working directory, `System.load`s it, and falls back to the
   `libnexa_musl.so` variant if the first load fails. The external-search path (working dir →
   parent dir → `java.library.path`) only runs when a caller passes a non-empty *variant name*
   to `initializeLibNexa(...)` — which scriptmachine's no-arg `Initialize()` never does. So an
   `UnsatisfiedLinkError` at test start usually means the bundled builds don't match your
   platform (an architecture the bundle doesn't cover), the working directory isn't writable
   (extraction target is `<working dir>/lib/`), or — if you *are* wiring in your own build via
   `initializeLibNexa(variant)` — a `libnexa` compiled without `--enable-javacashlib`. The
   loaded library is what defines the consensus rules the VM runs, so keep your libnexakotlin
   dependency current. (The NPL compile step's `Initialize()` — `nexa-npl-smart-contracts`
   Pattern 2 — performs the same native load, so compile and replay share the one `libnexa`.)

   Because it is a native binding,
   it lives on the **server/test classpath only** — never on a Wasm/multiplatform client (same
   keep-JVM-only-libs-off-Wasm rule as `org.nexa:npl`, see `nexa-project-setup`).

2. **What it checks and what it does not.** The VM runs the scripts under the mandatory
   script-verify flags (`POST_UPGRADE_MANDATORY_SCRIPT_VERIFY_FLAGS`). It evaluates script logic —
   including introspection opcodes (`getOutput*`/`getPrevout*`/OP_PARSE) and `OP_CHECKLOCKTIMEVERIFY`'s
   stack comparison — *when you give it transaction context*. It does **not** model mempool policy
   (a clean run does not prove the fee is sufficient → see `mempool min fee not met` in
   `nexa-transaction-construction`/`nexa-npl-smart-contracts`) and it does **not** know the chain's
   median-time-past (a CLTV spend can pass the VM yet still be non-final until MTP catches up →
   see `nexa-locktime-cltv`). "VM says OK" rules out *script* bugs; the runtime rules still apply.

Three ways to instantiate the VM, matching three testing needs:

- **Context-free** (`ScriptMachine()`): run bare opcodes with no transaction. Introspection and
  signature opcodes cannot work (no tx context). For unit-testing arithmetic/stack/opcode logic.
- **Single-input** (`ScriptMachine(tx, inputIdx, utxo, advance)`): verify one input of a tx spends
  a given UTXO. The template/constraint/satisfier are parsed out of the input script (+ the UTXO).
- **Two-transaction** (`ScriptMachine(parentHex, childHex)`): hand it two related transactions; it
  finds the input that spends an output and sets up that spend automatically (`analyze2Tx` does the
  same discovery and hands you the pieces). This is the closest thing to "did this real spend
  validate?"

## Setup and versions

You need the `scriptmachine` artifact (and the `libnexakotlin` types it operates on; it also pulls
`mpthreads` transitively):

```kotlin
// gradle/libs.versions.toml  (look up the current version in the GitLab Maven registry)
[libraries]
nexa-scriptmachine = { module = "org.nexa:scriptmachine", version.ref = "nexa_scriptmachine" }
```

Register its GitLab Maven repository in `settings.gradle.kts` (project `46299034`; see
`nexa-project-setup` for the full repositories block):

```kotlin
maven { url = uri("https://gitlab.com/api/v4/projects/46299034/packages/maven") }  // scriptmachine
```

Declare it as a **`testImplementation`** and keep all `ScriptMachine` code in the **test source
set** (`src/test`) — the same placement NPL contract tests already use for `.compile()`. VM
execution is a development/test-time check, so it does not belong on your `main` classpath or in
your production send path:

```kotlin
testImplementation(libs.nexa.scriptmachine)   // test source set only
```

(The rare exception is a dedicated *script-debugger / block-explorer* tool whose whole purpose is
inspecting scripts at runtime; an ordinary app that builds and broadcasts transactions does not
need it on `main`.)

Imports:

```kotlin
import org.nexa.scriptmachine.ScriptMachine
import org.nexa.scriptmachine.Initialize          // top-level fun, NOT ScriptMachine.Initialize()
import org.nexa.scriptmachine.analyze2Tx
import org.nexa.scriptmachine.ScriptMachineStack
import com.ionspin.kotlin.bignum.integer.BigInteger   // the bignum type loadStacks/registers use
import org.nexa.libnexakotlin.OP
import org.nexa.libnexakotlin.SatoshiScript
import org.nexa.libnexakotlin.NexaTransaction
import org.nexa.libnexakotlin.ChainSelector
```

> **Stale-doc note (API evolution).** Older releases and the package's own README/Module docs
> still show the pre-migration coordinate and package — `("Nexa","NexaScriptMachine","…")` and
> `import Nexa.ScriptMachine.*`, with a `ScriptMachine.Initialize()` companion call. The current
> library declares `package org.nexa.scriptmachine`, publishes as `org.nexa:scriptmachine`, and
> exposes a **top-level** `fun Initialize()`. Use the `org.nexa.*` forms (this matches the
> `org.nexa.scriptmachine.Initialize()` call `nexa-npl-smart-contracts` already uses, and mirrors the
> same `Nexa.*` → `org.nexa.*` migration recorded for `nexarpc` and `npl`). The package docs'
> worked example also calls a `sm.goScript(false)` that no longer exists — the current name is
> `next(false)` (Pattern 8) — and says `libnexa.so` "MUST be in your path or working directory",
> which the bundled-extraction behavior (Mental model) has superseded. Trust the package
> declaration over the README.

`Initialize()` must be called **once** before constructing any `ScriptMachine` (a JUnit
`@BeforeTest` / `@BeforeAll` is the natural home). It loads the native library — by default by
**extracting the copy bundled inside the `libnexakotlin-jvm` jar** into `<working dir>/lib/` and
`System.load`ing it (see Mental model), so the test process needs a writable working directory
but no manually-installed `libnexa`. If the load fails, it prints a hint that `libnexa.so` was
not built with `--enable-javacashlib` and rethrows the `UnsatisfiedLinkError`.

Two environment facts worth knowing: recent releases of the library are compiled with a
**Java 21 toolchain**, so run your tests on a JDK ≥ 21 (an older JDK fails with
`UnsupportedClassVersionError` before any VM code runs); and the artifact is a plain Kotlin/JVM
jar — the tx types it operates on come from `libnexakotlin`, which your project already declares.

## Core patterns

### Pattern 1: Run bare opcodes (no transaction context)

For unit-testing pure script logic — arithmetic, stack ops, bignum/BMD behavior. Top of stack is
index 0; `mainStackAt` returns `"<TYPE> <len> <hex>h <decimal>"` (e.g. `"BYTES 1 03h 3"`), or
`""` for an empty slot.

```kotlin
Initialize()                               // once, before any ScriptMachine
val sm = ScriptMachine()                   // context-free
val ranToEnd = sm.eval(OP.push(1), OP.push(2), OP.ADD)
check(ranToEnd)                            // true = executed to the end (NOT "succeeded" — see anti-patterns)
check(sm.status == "No error(0)")          // this is the success signal
check(sm.mainStackAt(0) == "BYTES 1 03h 3")
sm.delete()                                // release the native VM handle
```

`eval` has a vararg-`OP` form (above) and a `SatoshiScript` form; pass `run = false` to load a
script and pause before the first instruction so you can `step()`/`cont()` it (Pattern 5).

### Pattern 2: Verify a single input's spend against its UTXO

The most direct "does this contract spend execute?" test. Build the spending tx and the prevout
(the contract's P2T UTXO) exactly as in `nexa-npl-smart-contracts` / `nexa-transaction-construction`,
then run input `inputIdx` against the UTXO. `advance = true` runs the satisfier+constraint during
construction and leaves the template ready; one `next()` runs the template to completion.

```kotlin
val cs = ChainSelector.NEXAREGTEST

// The prevout: a P2T (pay-to-template) output. SatoshiScript.p2t(chain, templateHash160,
// constraintHash160, visibleArgsScript) is libnexakotlin's builder (the in-VM analogue of the
// Pay2TemplateDestination / P2T helper in nexa-npl-smart-contracts).
val utxo = NexaTxOutput(cs, fundedSatoshis,
    SatoshiScript.p2t(cs, template.scriptHash160(), constraint.scriptHash160(), visibleArgs))

// spendTx is your fully-built spend (input.script = satisfier; output(s) the contract demands).
val sm = ScriptMachine(spendTx, /*inputIdx*/ 0, utxo, /*advance*/ true)
sm.next()                                  // runs the remaining (template) script to completion
check(sm.scriptErr == "No error(0)") { "spend failed: ${sm.scriptErr}\n${sm.getState()}" }
check(sm.mainStackAt(0) == "")             // clean main stack ⇒ a valid spend
sm.delete()
```

If the script *uses introspection only against the UTXO you pass* (no reads of other prevouts),
this is sufficient. If a rule reads sibling prevouts/outputs it doesn't have here, prefer the
two-transaction form (Pattern 3), which supplies the whole tx as context.

### Pattern 3: Replay a real parent→child spend from two transactions

Hand the VM the funding (parent) tx and the spending (child) tx as hex (or `NexaTransaction`); it
finds the input that spends one of the parent's outputs and sets up that spend, parsing
satisfier/constraint/template for you. This is the canonical end-to-end "did this spend validate?"
check and what you run in a test right after constructing both txs.

```kotlin
val sm = ScriptMachine(parentTxHex, childTxHex)        // auto-detects the spend dependency
println("template:   " + sm.template!!.toAsm(" "))     // inspect what it parsed out
println("constraint: " + sm.constraint!!.toAsm(" "))
println("satisfier:  " + sm.satisfier!!.toAsm(" "))

sm.next(false)                                         // load, ready to step
var ok = true
var instr = 0
do { ok = sm.step(); if (ok) instr++ } while (ok)      // step to completion
println("result: ${sm.scriptErr} in $instr instructions")
// A clean spend ends with scriptErr "completed" and an empty main stack; a failed verify leaves
// a scriptErr that contains "failed".
check(!(sm.scriptErr ?: "").contains("failed"))
check(sm.mainStackAt(0) == "")
sm.delete()
```

To debug *what the contract logic would do under a different template*, pass an override script as
a third argument (`ScriptMachine(parentHex, childHex, myAltTemplate)`) — the VM runs your script in
place of the one in the transactions. Useful for "would this fix make the spend pass?" without
recompiling and re-funding.

`analyze2Tx(listOf(childTx, parentTx))` is the discovery step on its own: it returns a
`ScriptMachineEnvironment(utxo, utxoIdx, spender, tx)` for the first spend dependency it finds
(or `null`), which you can feed into your own harness. The two-tx constructor uses the same logic
and tolerates the txs in either order.

Three behaviors of the two-tx constructor worth knowing:

- It sets up **only the first spend dependency it finds** (it prints
  `Processing output N being spent by input M` to stdout so you can see which one it picked). If
  the child spends *several* of the parent's outputs, the others are not validated by this run —
  loop over inputs with the two-phase init (Pattern 10) to cover them all.
- If the two transactions are **not related by a spend at all**, the constructor throws a
  `ScriptMachineException` (`"No spend" / "These transaction are not related by a spend"`) —
  usually a sign you passed the wrong pair, the child spends a different funding tx, or an
  outpoint doesn't match. The exception: when you also pass an **override script**, unrelated txs
  don't throw — the VM assumes the second tx is the spending context and the first tx's output 0
  is the prevout, so you can exercise a template against transactions that don't yet connect.
- The hex/byte forms take an optional trailing `chainSelector` (default `ChainSelector.NEXA`).
  The VM's consensus rules currently don't differ across Nexa mainnet/testnet/regtest, so the
  default works for replaying txs from any of them; pass your chain if you want the parsed
  scripts/addresses to carry it.

### Pattern 4: Assert the success/failure of a spend (test-shaped)

Wrap Pattern 2/3 in a reusable test helper so contract tests read cleanly:

```kotlin
/** Runs the connected spend through the VM and returns null on success or the failure detail. */
fun spendError(parentTxHex: String, childTxHex: String): String? {
    val sm = ScriptMachine(parentTxHex, childTxHex)
    try {
        sm.next(false)
        var ok = true
        do { ok = sm.step() } while (ok)
        val err = sm.scriptErr ?: ""
        if (err.contains("failed", ignoreCase = true)) return "$err\n${sm.getState()}"
        if (sm.mainStackAt(0) != "")  return "main stack not clean: ${sm.getState().mainstack}"
        return null
    } finally {
        sm.delete()                              // always release, even on assertion failure
    }
}

@Test fun revealRuleValidates()  { assertNull(spendError(fundingHex, claimHex)) }
@Test fun refundRuleValidates()  { assertNull(spendError(fundingHex, refundHex)) }
```

This is the testing capability the corpus's contract/token skills assume but don't provide: you
catch a wrong satisfier layout, a stale bytecode constant, a swapped visible-arg order, or a
broken covenant check **as a precise local failure** (the failing opcode + stack via `getState()`),
instead of an opaque `mandatory-script-verify-flag-failed` from the network after a real broadcast.

> **Some consensus rejections happen in the `ScriptMachine` *constructor*, before any script
> runs — and they escape the `try` above.** The two-tx / single-input constructors call the
> native `createTemplateContext`, which enforces **group (token) conservation** while setting up
> the spend. A child output carrying a *different* group than the token input is an illegal melt
> and the **constructor throws** (`grp-invalid-melt Group input exceeds output, but no melt
> permission`) — note this is thrown at `ScriptMachine(parentTxHex, childTxHex)` on line above,
> which sits **outside** the `try`, so it propagates as a raw exception rather than a returned
> failure string. If you want `spendError` to report ctor-time rejections too, move the
> construction inside the `try`. A practical consequence: a covenant's own `verifySameGroup`
> check (see `nexa-tokens-and-groups`) is genuine defense-in-depth but **cannot be isolated in a
> replay test built with these constructors** — you can't build a conservation-valid spend that
> also swaps the group, because conservation rejects the swap first, before the covenant script
> ever executes.
> (The two-phase init in Pattern 10 does **not** skip that check — it reaches the same native
> `createTemplateContext` — but it fixes the *false* rejections: the tx-based constructors
> fabricate zero-value placeholder coins for every input other than the one under test, so a
> genuinely balanced **multi-input** grouped spend looks like an illegal melt input-by-input and
> gets rejected at construction. The two-phase init hands the VM the *real* prevout array, so
> balanced spends initialize cleanly and conservation is checked against the truth. The upshot:
> **no** `ScriptMachine` path runs a conservation-*violating* spend's scripts, so a
> `verifySameGroup` bypass cannot be exercised in isolation.)

**Constructor-time diagnostics for malformed template spends.** A recent release taught the
tx-based constructors to diagnose *badly constructed* inputs instead of failing opaquely. The
messages you'll meet, and what each means:

| Error | What it means |
| --- | --- |
| `The hidden args script, provided as the second push in the unlocking script must contain only data push instructions…` (a `ScriptException`) | The satisfier layout is wrong: either your "hidden args" push contains non-push opcodes, or — the hint in the message's second sentence — **you forgot the hidden-args push entirely**, so your first satisfier arg is being misread as the constraint script. Fix the input-script layout (`nexa-npl-smart-contracts` satisfier layout). |
| `Unlocking script (in the input) is invalid. It must have at least 1 push…` / `First instruction of the input script was … it must be a push instruction (of the template script).` | A non-well-known template spend must push the actual template script first; your input script doesn't start with that push. |
| `Prevout template hash / input template mismatch. Prevout template hash is …, actual provided template script hash is … or …` | The template script pushed in the input hashes (hash160 and hash256 both tried) to something other than what the prevout committed to — stale bytecode or wrong contract. In the default `tolerant` mode this lands in `scriptErr` and execution proceeds with your script anyway (see the tolerant anti-pattern); `tolerant = false` makes it throw. |
| `Constraint instruction N is not a push! It is …` (appended to `scriptErr`) | A pre-check before the native call: your constraint/hidden-args script contains an executable opcode where only data pushes are legal. |

The parse rule behind the first row: the constructors consume a hidden-args (constraint) push
from the input script **only when the prevout's locking script commits to an `argsHash`**. A
template with no hidden args expects the input layout `[template push][satisfier args…]` — no
empty constraint push — and a template *with* hidden args expects
`[template push][hidden-args push][satisfier args…]`. Get this wrong in either direction and the
scripts are mis-split, which these diagnostics now catch at construction.

### Pattern 5: Step, breakpoint, and inspect a failing spend

When `spendError` reports a failure, drill in. `step()` runs one instruction; `cont()` runs to the
next breakpoint, completion, or error; `pos` is the byte offset; `getState()` snapshots the machine.

```kotlin
val sm = ScriptMachine(parentHex, childHex)
sm.next(false)                               // ready to step the template
sm.setBreakpoint(/*byte offset*/ 12)         // stop when execution reaches offset 12
sm.cont()                                    // run up to the breakpoint
println(sm.getState())                       // MachineState(scriptType,pos,status,bmd,mainstack,altstack)
println("top of stack: ${sm.mainStackAt(0)}, alt: ${sm.altStackAt(0)}")
sm.step()                                     // single-step from here
sm.clearBreakpoint(12)
sm.cont()
sm.delete()
```

Notes from the VM internals worth knowing:
- In `getState()`/`MachineState`, the `mainstack`/`altstack` lists are ordered **bottom-first** —
  the *end* of the list is the top of the stack. (`mainStackAt(0)` is the top; the list is the
  reverse of indexed access.) Don't mix the two conventions.
- A breakpoint is implemented by overwriting the byte with an illegal opcode; the VM transparently
  restores it around `step()`/`cont()`. You can also breakpoint "by hand" with
  `modify(offset, byteArrayOf(-1))` and put the real opcode back (`modify` also has a
  `modify(offset, OP)` overload), but `setBreakpoint`/`clearBreakpoint` manage that for you.
- `cont(relativePos)` moves the program counter by `relativePos` bytes before continuing, and
  `pos` is **settable** — after patching an instruction with `modify`, `cont(-1)` re-runs from
  the replaced instruction. `clearStatus()` resets an error state (`status` returns to an
  `Initialized…` message) so you can keep driving the same machine after an expected failure.
- `clone()` / `copy()` fork a machine mid-execution (e.g. to explore both branches of a rule), and
  `dump()` / `ScriptMachine().fromDump(dump)` serialize machine state to inspect later or after the
  original is `delete()`d.

### Pattern 6: Confirm a complex contract stays within the resource budget

A contract can be *logically* correct yet exceed consensus limits on ops, stack size, or signature
checks. After a clean run, read the resources used; or set a limit and prove the script stays under
it. This matters most for "complex" contracts (many rules, large satisfiers, loops via OP_EXEC).

```kotlin
val sm = ScriptMachine(spendTx, 0, utxo, true)
sm.next()
check(sm.scriptErr == "No error(0)")
val r = sm.getResources()                    // ScriptMachineResources
println("ops=${r.instructionsExecuted} sigchecks=${r.sigsChecked} " +
        "maxStackBytes=${r.maxStackBytes} maxStackItems=${r.maxStackItems} " +
        "opExecs=${r.opExecsExecuted} opExecDepth=${r.opExecRecursionDepth}")
sm.delete()

// Or prove it fails when a limit is too tight (e.g. to find the real high-water mark):
val sm2 = ScriptMachine(spendTx, 0, utxo, true)
sm2.setLimits(maxStackUse = 3)               // pass -1 (default) to leave a limit unchanged
sm2.next()
check(sm2.scriptErr!!.contains("Stack total length limit exceeded"))
sm2.delete()
```

`instructionsExecuted` excludes pushes of the small constants `OP_0..OP_16`. Most resource counters
persist across successive `eval`s on the same machine (instruction count resets per `eval`); call
`resetResourceUse()` to zero them.

### Pattern 7: Parse the three scripts out of an input yourself

If you only need to *see* how an input decomposes (not run it), the `parseTemplateSpend` extension
splits an input script into its `SpecifiedScriptTemplate(satisfier, constraint, template)`:

```kotlin
val parts = childTx.inputs[0].script.parseTemplateSpend(parentTx.outputs[vout] as NexaTxOutput)
println("template ASM: " + parts.template.toAsm(" "))
```

Pass `null` for the txo when you don't have the prevout (it then guesses whether a constraint-args
push is present). With the prevout it uses the locking script's template metadata (well-known id,
args hash, visible args) to split precisely.

### Pattern 8: Driving the full three-script spend with `next()`

`next(runNow = true)` is the high-level driver for a machine loaded with all three scripts. It does
**not** return a plain pass/fail; it returns a `Triple<String?, String, SpecialOperation>`:

- **`.first`** — which script it just prepared/ran: `"constraint"`, `"satisfier"`, `"template"`,
  `"all scripts completed"` when every script is done, or `"step"` when it single-stepped one
  instruction (see below).
- **`.second`** — that script's status string (`"No error(0)"`, an error message, …) when it
  ran a whole script; `"ok"`/`"error"` when it single-stepped.
- **`.third`** — a `SpecialOperation`: `NONE`, `ALT_STACK_LOADED` (the satisfier's results were
  swapped onto the alt stack before the template runs — this happens automatically between the
  satisfier and template phases), or `ALL_DONE`.

How many `next()` calls a spend takes depends on **how the constructor set the machine up**, which
is the practical thing to get right:

- The **two-tx** (`ScriptMachine(parentHex, childHex)`) and **single-input `advance = true`**
  (`ScriptMachine(tx, idx, utxo, true)`) constructors **pre-run the satisfier and constraint during
  construction** and leave the template staged — so a single `next()` (or one `step()`-loop) drives
  just the template to completion. This is what Patterns 2–4 use.
- The **no-context triple-script** constructor (`ScriptMachine(satisfier, constraint, template)` or
  its byte-array form) and the single-input **`advance = false`** form run **nothing** up front, so
  you call `next()` once per script, in execution order, to walk the whole spend:

```kotlin
val sm = ScriptMachine(spendTx, /*inputIdx*/ 0, /*utxo*/ null, /*advance*/ false)
var r = sm.next()                          // runs the constraint script
check(r.first == "constraint" && r.second == "No error(0)")
r = sm.next()                              // runs the satisfier; its results move to the alt stack
check(r.first == "satisfier" && r.third == ScriptMachine.SpecialOperation.ALT_STACK_LOADED)
r = sm.next()                              // runs the template
check(r.first == "template" && r.second == "No error(0)")
check(sm.mainStackAt(0) == "")             // clean main stack ⇒ valid spend
sm.delete()
```

To single-step instead of running each script whole, call `next(false)` to stage a script, after
which subsequent `next()` calls single-step it (returning `.first == "step"`) — the step-loop form
in Pattern 3 is the common shorthand for this. Either way, the terminal signal is the same: keep
calling `next()` until it reports `evaling == "all scripts completed"` (equivalently `.third ==
ALL_DONE`); `getState().scriptType` surfaces that same `evaling` string for an assertion or log line.

### Pattern 9: Snapshot, fork, and serialize machine state (`clone` / `copy` / `dump`)

Three related tools let you capture a machine mid-execution — useful for exploring both branches of
a rule, or for saving a failing state to inspect later:

```kotlin
// clone(): fork the LIVE native VM at the current instruction. Each clone owns its own native
// handle — delete() every one. Continuing each fork explores divergent paths from a shared point.
val fork = sm.clone()
fork.cont()
check(fork.pos == sm.pos)                  // both reach the same end position here
fork.delete()

// copy(): rebuild an equivalent machine in pure Kotlin (re-loads the stacks + re-seats the current
// script; no native clone). Preserves stack contents, including bignums.
val twin = sm.copy()
check(twin.mainStackAt(0) == sm.mainStackAt(0))

// dump()/fromDump(): serialize state to a @Serializable ScriptMachineDump (main/alt stacks as
// strings, bmd, pos, and a handle-zeroed ScriptMachine), then rebuild a working machine from it —
// even after the original has been delete()d.
val saved = sm.dump()
sm.delete()
val restored = ScriptMachine().fromDump(saved)
check(restored.mainStackAt(0) != "")       // restored uses its own fresh native handle
```

`dump()` is the one to persist to a file or log (its embedded `ScriptMachine` has a zeroed native
handle, since `getPos`/`getBMD` need a live handle and can't run on a deleted machine); `clone()` is
the cheapest in-memory branch; `copy()` is the handle-independent equivalent when you don't want to
share the native VM. Same as everywhere else, `delete()` the originals and the clones when done.

`ScriptMachineDump` (and `ScriptMachine` itself, minus the native handle) are kotlinx-
`@Serializable` — the library ships custom serializers that Base64-wrap the embedded
tx/coins/scripts — so persisting to disk is just `Json.encodeToString(sm.dump())` written to a
file, and `ScriptMachine().fromDump(Json.decodeFromString(text))` on the way back.

**Seeding a machine with a synthetic stack.** The primitives under `copy()` are public and
useful on their own for unit-testing a template *fragment* against an arbitrary stack state —
no satisfier or tx required:

```kotlin
val sm = ScriptMachine()
sm.loadStacks(listOf(3, byteArrayOf(0x01), BigInteger.parseString("12345678901234567890")),
              listOf<Any>())                 // main stack (bottom-first), alt stack
sm.eval(fragmentUnderTest)                   // run just the opcodes you care about
val typed = sm.getBinaryStack(ScriptMachineStack.MAINSTACK)  // ByteArray/BigInteger, bottom-first
```

`loadStacks(stack, alt)` accepts `Int`/`Long`/`ByteArray`/`BigInteger` items (bignums are pushed
in sign-magnitude with a `BIN2BIGNUM`, so the machine state matches a real run) and *appends* to
whatever is on the stacks; `replaceStacks(stack, alt)` overwrites the stacks while preserving the
machine's script/position state — handy for re-testing one instruction against many stack
shapes. `getBinaryStack(whichStack)` is the typed read-back (see `stackItemFormat.md`).

### Pattern 10: Validate every input of a multi-input spend (two-phase init)

The tx-based constructors set up **one input's** spend and also apply chain-level checks (the
group-conservation rejection in Pattern 4's note) during construction. For a spend with many
contract inputs — a batch settlement, an enforcer/follower tx (`nexa-npl-smart-contracts`
Pattern 11) — build the machine in two phases instead: construct empty, seat the parsed scripts
and tx context yourself, then `initialize(true)`. Loop it over every input:

```kotlin
fun freshMachine(tx: NexaTransaction, coins: Array<iTxOutput>, i: Int): ScriptMachine {
    val sm = ScriptMachine()
    val parsed = tx.inputs[i].script.parseTemplateSpend(coins[i] as NexaTxOutput)  // Pattern 7
    sm.template = parsed.template; sm.constraint = parsed.constraint; sm.satisfier = parsed.satisfier
    sm.inputIdx = i; sm.tx = tx; sm.coins = coins    // full-tx context: introspection reads all prevouts
    sm.initialize(true)
    return sm
}

// validate every input (throwing on the first failure):
val coins: Array<iTxOutput> = Array(utxos.size) { utxos[it] }
for (i in tx.inputs.indices) {
    val sm = freshMachine(tx, coins, i)
    try {
        sm.next(true)
        check(sm.status == "No error(0)") { "input $i failed: ${sm.status}\n${sm.getState()}" }
    } finally { sm.delete() }
}
```

Because `coins` carries **all** the prevouts, cross-input introspection
(`getPrevoutContractId(otherIdx)`, `getPrevoutVisibleArg(otherIdx, n)`) resolves correctly for
every input — which the single-utxo constructor cannot offer. Two caveats:
(a) the group-conservation check still runs here — `initialize(true)` reaches the same native
`createTemplateContext` as the constructors (see the constructor-time note in Pattern 4) — but it now
sees the **real** prevouts instead of the constructors' fabricated placeholders, so a balanced
multi-input grouped spend initializes cleanly and an actually-unbalanced one still throws; the
check is only as truthful as the `coins` array you seat; (b) to also measure a spend's **runtime stack
high-water** (worth doing for large batch contracts), step each input with `next(false)` until
`ALL_DONE`, tracking `getState().mainstack.size + altstack.size` per step, and read
`getResources().maxStackItems` at the end. NPL's published test sources ship a contract-agnostic
harness for exactly this (`ScriptDiagnostics.kt`: `validateSpend`, `measureRuntimeStackDepth`,
plus static per-rule size/opcode analyzers) — a ready-made model to copy.

## Common mistakes and anti-patterns

### Treating `eval()`'s Boolean return as pass/fail

**Wrong**:
```kotlin
val passed = sm.eval(myScript)              // misread: "did the script SUCCEED?"
if (passed) approveSpend()
```
*`eval`/`step`/`cont` return whether execution **ran to the end / advanced**, which is different
from whether the script **succeeded**. A script that fails a `verify` can still "run" up to the
failing instruction.*

**Right**: judge success by `status`/`scriptErr` and a clean stack.
```kotlin
sm.eval(myScript)
check(sm.status == "No error(0)" && sm.mainStackAt(0) == "")
```

### Forgetting `Initialize()`, or expecting a pure-Kotlin VM

**Wrong**: constructing a `ScriptMachine` without calling `Initialize()` first, or deploying the
test on a machine where the native library can't load.
*You get an `UnsatisfiedLinkError` (the native methods aren't bound). This is the single most
common "the tests won't even start" failure.*

**Right**: call `org.nexa.scriptmachine.Initialize()` once before any machine (a `@BeforeTest` /
`@BeforeAll`). The native VM ships **inside the `libnexakotlin-jvm` jar** and is auto-extracted
to `<working dir>/lib/` on first load (Mental model), so a plain JVM CI image works as long as
its platform matches one of the bundled builds and the working directory is writable — you do
not install `libnexa.so` separately. If you deliberately load your own node build via
`initializeLibNexa(variant)`, it must be compiled with `--enable-javacashlib`. See the symptom
row in `nexa-debugging-onchain-errors`.

### Not calling `delete()` — leaking native handles

**Wrong**: creating many `ScriptMachine`s in a loop/test suite and relying on the garbage collector.
*Each machine owns a native VM handle. Leaning on finalization can pile up native memory over a
large test run.*

**Right**: `delete()` each machine when done (a `try { … } finally { sm.delete() }`, as in Pattern
4). After `delete()` you can `initialize(...)` to reuse the object.

### Running the VM inline in production / the live send path

**Wrong**: calling `ScriptMachine(...)` from your server's `main` or from the route that builds
and broadcasts a tx, to "double-check" every spend at runtime.
*This pulls the native `libnexa.so` runtime into your production classpath and hot path, adds the
`Initialize()` native-load cost and per-spend VM execution to live requests, and gives a false
sense of safety (the VM still doesn't model fees/MTP/mempool). It mirrors the
`nexa-npl-smart-contracts` "compiling NPL at server startup" anti-pattern.*

**Right**: keep VM verification in the **test source set**. You write a test (Pattern 4) that
builds the funding + spend txs and asserts the spend executes cleanly while you develop and
iterate on the contract; production code just builds and broadcasts the spend it already trusts
(having tested it). The VM is a `testImplementation`, not a runtime dependency of a normal app.

### Using the context-free machine for a script that needs the transaction

**Wrong**:
```kotlin
val sm = ScriptMachine()                    // no tx context
sm.eval(contractTemplateThatReadsOutputs)   // getOutputArgsHash / CHECKSIG have nothing to read
```
*Introspection opcodes (`getOutput*`/`getPrevout*`/OP_PARSE) and signature checks need the spending
transaction (and the coins). Without context they fail or read garbage.*

**Right**: use a tx-based constructor (`ScriptMachine(tx, inputIdx, utxo, …)` or
`ScriptMachine(parentHex, childHex)`) so the VM has the tx and prevouts to introspect.

### Reading the stack from the wrong end

`mainStackAt(0)` is the **top** of the stack; higher indices go deeper. In `getState().mainstack`
the list is ordered bottom-first, so the **last** element is the top. Mixing the two (e.g. asserting
`getState().mainstack[0]` is the result) reads the bottom instead of the top.

### Expecting the VM to catch fee or locktime-finality problems

**Wrong**: "the script VM said the spend is valid, so it will broadcast."
*The VM verifies *script* validity only. It does not check that the fee clears the mempool
(`mempool min fee not met`) or that the chain's median-time-past has reached your `nLockTime` (a
CLTV spend passes the VM's stack comparison but is still non-final until MTP catches up).*

**Right**: treat a clean VM run as "no script bug," then still apply the fee buffer rule
(`nexa-npl-smart-contracts` Pattern 8 / `nexa-transaction-construction`) and the `nSequence`/MTP rules
(`nexa-locktime-cltv`) before and after broadcasting.

### Omitting (or bloating) the hidden-args push when the prevout commits to an argsHash

**Wrong**: building the spending input as `[template push][satisfier args…]` when the contract
was funded *with* hidden args (the locking script carries an `argsHash`) — or pushing a "hidden
args" blob that contains executable opcodes.
*The VM mis-splits the scripts: your first satisfier arg gets consumed as the constraint script.
A recent release diagnoses this at construction with `The hidden args script, provided as the
second push in the unlocking script must contain only data push instructions… This can also
happen if you forget to include a hidden args script…` — before that diagnostic, it surfaced as
a baffling downstream `OP_EQUALVERIFY`-style failure.*

**Right**: match the input layout to the prevout: `argsHash` present ⇒
`[template push][hidden-args push][satisfier args…]` (the hidden-args push is data-push-only);
no `argsHash` ⇒ no constraint push at all. See the parse-rule note under Pattern 4 and the
satisfier layout in `nexa-npl-smart-contracts`.

### Assuming a failure throws, not realizing `tolerant` mode swallows it

By default `ScriptMachine.tolerant == true`: non-fatal problems (e.g. the provided template script
not matching the prevout's template hash) set `scriptErr` and let execution continue rather than
throwing. So you must **inspect `scriptErr`/`status`**, not rely on a `try/catch`. Set
`tolerant = false` if you want such inconsistencies to throw `ScriptMachineException` instead.

### Trusting the README's coordinate/package

**Wrong**: `implementation("Nexa","NexaScriptMachine","…")` / `import Nexa.ScriptMachine.*` /
`ScriptMachine.Initialize()` copied from the package README.
*Those are the pre-migration forms.*

**Right**: `org.nexa:scriptmachine` (project `46299034`), `import org.nexa.scriptmachine.*`,
top-level `Initialize()`.

## Security considerations

- **A clean VM run is necessary, not sufficient, for safe settlement.** It proves the *scripts*
  validate; it says nothing about fees, mempool acceptance, median-time-past finality, or
  double-spend / front-running races. Don't gate an irreversible action (shipping, revealing a
  secret, crediting a withdrawable balance) on "the VM accepted it" — apply the confirmation-depth
  and finality rules in `nexa-transaction-construction` and `nexa-locktime-cltv`.
- **The VM executes native code with full process privileges.** `Initialize()` loads `libnexa.so`
  via JNI; a tampered or untrusted shared library is arbitrary code execution in your JVM. Use a
  known-good build of the node's cashlib (the one shipped with the artifact, or one you built from
  the upstream full node), and don't load a `libnexa.so` of unknown provenance.
- **Don't auto-broadcast from a debugging/replay pipeline.** This tool is for *inspecting* a tx; a
  harness that feeds arbitrary externally-supplied tx hex into the VM and then broadcasts whatever
  validated is a footgun. Keep simulation and broadcast as deliberate, separate steps.
- **Test against realistic resource limits, not just the defaults.** A contract that passes with the
  VM's generous default limits could exceed the consensus budget for ops/stack/sigchecks on a busy
  chain. Use `setLimits(...)` (Pattern 6) to confirm headroom before relying on a complex
  multi-rule contract in production.
- **Don't log raw tx hex, satisfier scripts, or signed material in production.** A spend's satisfier
  reveals secrets/signatures the moment it's serialized; the debugging prints in the patterns above
  are development-only.

## Related skills and references

- `nexa-npl-smart-contracts` — writes and compiles the contract whose spend you verify here; uses the
  *same* `org.nexa.scriptmachine.Initialize()` for the compile step (Pattern 2). After compiling and
  building a spend, run it through this skill before broadcasting.
- `nexa-transaction-construction` — builds and signs the parent (funding) and child (spend)
  transactions you feed to `ScriptMachine(parentHex, childHex)`; also the fee-budget rule the VM
  doesn't enforce.
- `nexa-tokens-and-groups` — group/authority covenants (`verifySameGroup`, mint authority spends) are
  exactly the "complex contract transactions" worth replaying in the VM to confirm the group/output
  introspection checks pass before broadcasting.
- `nexa-locktime-cltv` — the CLTV stack comparison runs in the VM, but `nSequence` finality and MTP
  are runtime concerns the VM does not model; verify both there.
- `nexa-rpc-node-client` — the complementary, *on-chain* way to test: broadcast to a regtest node and
  `generate` blocks. Use the VM for fast, offline script-logic verification; use regtest for full
  end-to-end (fees, mempool, confirmation) testing.
- `nexa-debugging-onchain-errors` — when the network rejects a spend with
  `mandatory-script-verify-flag-failed` / `OP_EQUALVERIFY`, reproduce it locally here to see the
  exact failing opcode and stack; also the `UnsatisfiedLinkError` / `libnexa.so` setup symptom.

### Supporting files in this folder

- `contractSpendTestHarness.kt` — a drop-in JUnit base class: `Initialize()` once in `@BeforeAll`,
  a `spendError(...)` / `assertSpendValidates(...)` / `assertSpendFails(…, expecting)` set built on
  Pattern 3/4 (two-tx and single-input), and a `dumpState(sm)` debug helper.
- `stackItemFormat.md` — the `getStackItemText` string grammar (`<TYPE> <len> <hex>h <decimal>`,
  the `BYTES 0 false 0` empty case vs the `""` absent-slot case, `BIGNUM` sign-magnitude rendering
  and BMD interaction) for parsing stack contents in assertions.
- `opcodeStepThrough.md` — worked walkthrough of stepping a reveal-vs-refund spend script-by-script
  and instruction-by-instruction, reading the stack at each step.