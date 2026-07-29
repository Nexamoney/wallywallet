---
name: nexa-npl-smart-contracts
description: "Writes, compiles, and spends Nexa smart contracts using NPL (Nexa Programming Language) and the script-template VM. Use when authoring a contract() DSL block, compiling NPL to bytecode, constructing P2T/template locking scripts, building satisfier scripts to spend a contract UTXO, or debugging contract failures like OP_EQUALVERIFY or 'Cannot find state transition'. Triggers: NPL, org.nexa.npl, contract { face { } }, ruleWithPublicArgs, NBytes/NInt/NSig/NPubKey, verify(... eq ...), getOutputArgsHash, checkLockTimeVerify, SatoshiScript.Type.TEMPLATE, P2T, Pay2TemplateDestination, addSpecificTransitions, DynamicStackTransform, templateArgs/universal contract, getPrevoutVisibleArg, enforcer/follower (batch covenant spends, tx too large to relay), scriptlet/OP_EXEC (holder-supplied script as an arg), compile stack/size diagnostics. Not for general tx construction (nexa-transaction-construction), CLTV nSequence rules (nexa-locktime-cltv), or P2PKT address validation (nexa-identity-and-addresses)."
---

# Nexa NPL smart contracts (script-template VM)

## When to use this skill

Trigger when a developer is writing, compiling, or spending a NEXA smart contract.
Concretely trigger on:

- Keywords: NPL, Nexa Programming Language, `org.nexa.npl`, `Nexa(...)`,
  `contract(...) { face { ... } }`, `ruleWithPublicArgs`, `holderPublicArgs`,
  `holderArgs`, `spenderArgs`, `NBytes`, `NInt`, `NSig`, `NPubKey`, `script { ... }`,
  `verify(... eq ...)`, `getOutputArgsHash`, `outputValueN`, `checkLockTimeVerify`,
  `hash160()`, `checkSigVerify`, `checkDataSigVerify`, `SatoshiScript.Type.TEMPLATE`, `P2T`,
  `P2PKT_ID`, `Pay2TemplateDestination`, `argsHash`, `templateHash`, satisfier, locking
  script, `Cannot find state transition`, `addSpecificTransitions`,
  `addWarriorContractTransitions`, `DynamicStackTransform`,
  `DynamicStackTransformRegistry.register`, `stackX`, `stackScripts.bin`,
  `initNpl`, `NexaContract`, `NplScript`, `templateArgs`, universal contract / constant
  template hash, `getPrevoutVisibleArg`, enforcer/follower, `StackXformDiag`, contract/tx
  too large to relay, scriptlet / `OP_EXEC` / `OP.EXEC` (a holder-supplied script arg the
  template executes; "let the holder use multisig instead of one key").
- Tasks: "write a smart contract for NEXA that does X", "how do I spend a contract
  UTXO", "compile my NPL contract to bytecode", "the contract rejects my spend with
  OP_EQUALVERIFY", "what goes in the satisfier script", "create a P2T output", "atomic
  swap on Nexa", "my NPL contract won't compile / `Cannot find state transition`", "how do
  I add a stack transition / register a transformer", "verify a signature / oracle in a
  contract", "make the contract's script hash the same for every instance (universal
  contract)", "a tx spending many covenant UTXOs is too big / over the relay limit",
  "read another input's contract args from inside a rule", "how much stack / script size
  does my contract use when compiled".
- Files: `ContractTest.kt`, `contract.kt`, `contractTemplate.kt`, `contractTx.kt` —
  any file authoring an NPL DSL block or constructing a P2T locking script.

**Negative triggers** — do NOT use this skill for:
- General tx construction (inputs/outputs, `txFor`) — use `nexa-transaction-construction`.
- The `nSequence < FINAL` rule needed when your contract uses `checkLockTimeVerify` —
  use `nexa-locktime-cltv` (this skill points to it but doesn't re-explain).
- Validating that the seller's payout address is P2PKT before locking funds — use
  `nexa-identity-and-addresses`.
- Broadcasting the eventual claim tx — once you have an `iTransaction`,
  `nexa-transaction-construction` covers broadcast.

## Mental model

NEXA smart contracts run in the **script-template VM** — a successor to Bitcoin Script
that adds template-based code reuse. A contract is just a constraint script that locks
a UTXO. To spend the UTXO, a *satisfier script* must provide the data and rules that
make the constraint evaluate to true.

NPL is a Kotlin DSL that lets you write contract logic declaratively and compile it
to template bytecode. Think of NPL as Solidity for NEXA — except instead of deploying
to a global state machine, every contract instance is just a UTXO with the compiled
template baked into its locking script.

**NSL (the language inside `script { }`) is dependency-based, not sequential.** Bindings are
read-only — "assigning" creates a *new* binding — and the compiler determines execution order
from data dependencies, not from the order you wrote the statements (two independent
statements may compile in either order; the compiler even lays each rule out twice — as
written and dataflow-weight-sorted — and keeps the shorter script). Two practical
consequences: (a) never rely on statement order for anything other than data flow; (b) when a
value is used more than once (a struct field like `oracleMsg.priceAinB`, an introspection
read), **bind it to a `val` once and reuse the binding** rather than re-extracting it —
repeated extraction bloats the script and deepens the stack rearrangements the compiler must
solve (see `stateTransitions.md` on designing rules that avoid `Cannot find state
transition`).

Three args slots on a contract:

| Slot | Set by | Visible? | Used for |
| --- | --- | --- | --- |
| **`holderArgs`** (hidden args) | Output creator at funding time. | Hashed into `argsHash`, not visible on-chain. | Per-instance secrets the creator commits to without revealing. Usually empty for app-style contracts. |
| **`holderPublicArgs`** (visible args) | Output creator at funding time. | Concatenated into the locking script, visible on-chain. | Parameters every observer can read: prices, addresses, hashes, timeouts. |
| **`spenderArgs`** | Spender at spend time. | Pushed by the satisfier. Visible after broadcast. | Witnesses: signatures, revealed secrets, refund-direction selectors. |

There is a fourth slot, **`templateArgs`** (the `null` first list in every rule declaration
below): compile-time constants **baked into the template bytecode itself**. `NC*` constants
(`NCInt`/`NCBytes`/…) used inside a script body are likewise compiled in. The tradeoff this
slot controls is **universality**: anything baked into the template changes the template
*hash*, so a contract with per-instance data in `templateArgs` gets a different script hash
per instance, while a contract that keeps per-instance data in the holder slots (hidden or
visible) compiles to **one constant template hash for every instance** — a "universal"
contract, recognizable on-chain as a contract *type* and reusable without recompiling. Rule
of thumb: `templateArgs`/`NC*` constants only for values genuinely shared by every instance
(an oracle ticker, a fee percentage, a protocol constant); per-instance data (parties,
amounts, deadlines) belongs in holder args.

A contract `face` has one or more **rules**. Each
rule is a separate way to spend the UTXO with its own script body and `spenderArgs`
list. The spender picks which rule to invoke by pushing the **rule index** as the
**last** element of the spender args.

> **The compiled `ruleIdx` is the rule's position in ALPHABETICAL order by name — NOT
> declaration order.** NPL sorts rules by name at compile time, so rules declared
> `Sale, Cancel, Expire` compile to `Cancel(0), Expire(1), Sale(2)`. A wrong index does
> **not** error — it silently runs a *different* rule (e.g. selecting 0 runs `Cancel`, which
> hits an `OP_CHECKSIGVERIFY` that fails; selecting 2 runs `Sale`'s fee math), so the failure
> surfaces far from the cause. Pin your `ruleIdx` constants to the **sorted** rule names and
> let the `scriptmachine` replay tests (see `nexa-script-machine-testing`) be the guard.

There are two rule-declaration forms, differing only in whether they expose a *visible*
(`holderPublicArgs`) slot:

- `ruleWithPublicArgs(name, templateArgs, holderArgs, holderPublicArgs, spenderArgs) { script { … } }`
  — the form with on-chain visible args. `fullRule(...)` is an exact alias for it.
- `rule(name, templateArgs, holderArgs, spenderArgs) { … }` — the shortcut with **no**
  `holderPublicArgs` slot (its block is the script body directly, not a `RuleBuilder`).
  Use it for contracts whose only holder commitments are hidden (hashed into `argsHash`).

Don't confuse the two argument lists: `rule`'s fourth positional list is `spenderArgs`,
whereas `ruleWithPublicArgs`'s fourth is `holderPublicArgs` and `spenderArgs` is fifth.

The output's locking script is a P2T (Pay-to-Template) script:

```
[OP_PUSHFALSE | groupInfo] [templateHash | wellKnownId] [argsHash] [visible args...]
```

`P2T(template, hiddenArgs, visibleArgs).lockingScript()` from `Pay2TemplateDestination`
builds this for you. `argsHash = hash160(hiddenArgs serialized)`.

The satisfier the spender provides looks like:

```
PUSH(templateBytecode) PUSH(hiddenArgsBytes) <declared spenderArgs as PUSHes> PUSH(ruleIdx)
```

The VM looks up the template by hash, verifies `hash160(hiddenArgs) == argsHash`,
selects the rule by ruleIdx, and runs the rule's script with the spender args on the
stack and the visible args available as named values.

The satisfier (unlocking script) **must be push-only** — every element is a data push
(opcodes `0x60` or lower); it may push *scripts* as data (the template bytecode, hidden args)
but may not contain executable opcodes. This is a consensus rule, not a convention, so build
satisfiers only out of `OP.push(...)` and the small-constant pushes — never embed logic in the
satisfier. (See the script-template execution model in the Nexa spec:
`https://spec.nexa.org/addresses/scriptTemplates/`.)

Mechanically (per the spec's template execution model): the locking script's **hidden args are
parsed and pushed onto the *alt* stack** first, then the **visible args** are pushed onto the alt
stack in order — so once on the alt stack, hidden and visible args are indistinguishable, which is
why their *combined* order must match the template's expectations and why a holder can move a
parameter between the hidden and visible slots without changing the template. The satisfier's
spender args go on the **main** stack. The hash committing the hidden args may be a `hash160`
(20 bytes) *or* a `hash256` (32 bytes) — the size disambiguates; libnexakotlin's P2PKT/P2T
builders use `hash160`.

**Critical differences from Ethereum / Solidity**:
- No global state. A contract is a single UTXO; spending it destroys it. Multi-step
  protocols thread state through a chain of UTXOs.
- No "deployment". The contract's template bytecode is just embedded in the locking
  script of any UTXO using it (or rather, only its hash — the bytecode is in the
  satisfier at spend time, since it's needed for execution).
- No gas. Script execution is bounded by opcode limits per tx, not by metered runtime.
- No accounts. Spenders are identified by their signing keys or by knowledge of
  secrets, exactly as in Bitcoin Script.
- Outputs are also constrained from the inside: the contract can `verify
  (getOutputArgsHash(0.nx) == sellerHash)` to force the spending tx to pay a specific
  recipient. This is the foundation of atomic swaps, escrow, and forced settlement.

## Setup and versions

You need three Nexa artifacts:
- `org.nexa:npl` — the NPL DSL.
- `org.nexa:scriptmachine` — the VM runtime; needed in tests to compile contracts.
- `org.nexa:libnexakotlin` — `SatoshiScript`, `P2T`, `Pay2TemplateDestination`.

Pin exact versions per `nexa-project-setup`. The NPL POM declares which `scriptmachine` and
`libnexakotlin` it expects — keep your pins compatible with that. The `org.nexa.npl` DSL surface
drifts fastest of all the Nexa libraries, so verify any DSL signature shown below against the
resolved jar before relying on it — see `nexa-project-setup` § "Verifying API signatures before
relying on them".

In the server module's dependencies:
```kotlin
implementation(libs.nexa.npl)
implementation(libs.nexa.scriptmachine)     // only the test runtime really needs this, but
                                             // declaring it on main is harmless
implementation(libs.nexa.libnexakotlin)
```

Standard NPL imports:
```kotlin
import org.nexa.npl.NBytes
import org.nexa.npl.NInt
import org.nexa.npl.NSig
import org.nexa.npl.NPubKey
import org.nexa.npl.Nexa
import org.nexa.npl.nx
import org.nexa.npl.addSpecificTransitions
import org.nexa.npl.addWarriorContractTransitions
import org.nexa.npl.initRefactor
import org.nexa.npl.loadCalcStackX
import org.nexa.npl.stackX
```

**Project entry points.** `Nexa("name") { … }` (used in Pattern 1) is the general builder —
its block can declare `group`s, `contract`s, and project-level `face`s. Two shortcuts skip
the nesting for the common single-thing cases: `NexaContract { … }` (one unnamed contract;
its block is a `ContractBuilder`) and `NplScript(templateArgs, hiddenArgs, visibleArgs,
satisfierArgs) { … }` (one rule with one script body). All three return an `NPL` you then
`.compile()`. The full DSL surface — every typed-arg class, operator, hashing/signature/
introspection function, and the high-level `nsllib` helpers — is catalogued in
`dslReference.md` in this folder.

## Core patterns

### Pattern 1: Define the contract DSL (one-time)

A complete, runnable secret-reveal contract:

```kotlin
val secretRevealContract = Nexa("SecretRevealContract") {
    val TimeoutDeadline = 1800.nx     // 30 minutes, in seconds; .nx makes it a script literal

    // Visible args (in declaration order -- ORDER MATTERS).
    val buyerHash = NBytes("buyerHash")
    val sellerHash = NBytes("sellerHash")
    val purchaseTime = NInt("purchaseTime")
    val purchaseAmt = NInt("purchaseAmt")
    val secretHash = NBytes("secretHash")

    // Spender args.
    val revealedSecretBytes = NBytes("revealedSecretBytes")

    contract("SecretRevealContract") {
        face {
            // Rule 0: Reveal -- seller proves they know the secret and gets paid.
            ruleWithPublicArgs(
                name = "Reveal",
                templateArgs = null,
                holderArgs = listOf(),                              // no hidden args
                holderPublicArgs = listOf(buyerHash, sellerHash, purchaseTime, purchaseAmt, secretHash),
                spenderArgs = listOf(revealedSecretBytes)
            ) {
                script {
                    // hash160(secret) must match the committed secretHash
                    val revealedHash = revealedSecretBytes.hash160()
                    verify(revealedHash eq secretHash)

                    // output 0 must pay sellerHash for exactly purchaseAmt
                    val parsedSellerHash = getOutputArgsHash(0.nx)
                    verify(sellerHash eq parsedSellerHash)
                    val parsedOutputAmount = outputValueN(0.nx)
                    verify(purchaseAmt eq parsedOutputAmount)
                }
            }
            // Rule 1: TimeoutRefund -- buyer reclaims if seller didn't reveal.
            ruleWithPublicArgs(
                name = "TimeoutRefund",
                templateArgs = null,
                holderArgs = listOf(),
                holderPublicArgs = listOf(buyerHash, sellerHash, purchaseTime, purchaseAmt, secretHash),
                spenderArgs = listOf()                              // no spender args
            ) {
                script {
                    checkLockTimeVerify(purchaseTime + TimeoutDeadline)
                    val parsedBuyerHash = getOutputArgsHash(0.nx)
                    verify(buyerHash eq parsedBuyerHash)
                    val parsedOutputAmount = outputValueN(0.nx)
                    verify(purchaseAmt eq parsedOutputAmount)
                }
            }
        }
    }
}
```

**Signature and oracle checks.** The secret-reveal example above authorizes a spend by
*knowledge* (the revealed secret), so it needs no signatures. Contracts that authorize by
*key* instead use `checkSigVerify(sig, pubkey)` (a transaction signature) or — the oracle
primitive — `checkDataSigVerify(sig, msg, pubkey)` (a signature over an **arbitrary message**:
an off-chain oracle signs a fact and the contract checks it against the oracle's committed key,
the basis of bet/prediction and insurance contracts). Declare the `NSig` in `spenderArgs` and
the `NPubKey` as a visible arg or constant; full signatures are in `dslReference.md` §4.
On the producing side, the signature `checkDataSigVerify` consumes is what libnexakotlin's
`wallet.signData(messageBytes, addr)` emits (SHA256 of the message, then Schnorr) — **not**
`wallet.signMessage(...)`, whose Bitcoin-style "Signed Message" wrapping is for nexid/TDD
signatures and is not usable inside contracts. See `nexa-wallet-lifecycle-and-chain`
("Sign and verify messages/data with the wallet").

> **`checkSigVerify` (TX sig) and `checkDataSigVerify` (message/data sig) are not
> interchangeable, and the choice constrains your whole flow.** A `checkSigVerify` rule
> authorizes with a **transaction signature** (ALL/ALL) over the *actual spend tx* — so the
> signer must sign a tx that already exists. That is fundamentally incompatible with an
> **offline / one-scan** flow where a device signs a **message** ahead of time over some
> committed data (e.g. an id set), because at signing time the server-built spend tx does not
> yet exist. If a covenant's spend rule uses `checkSigVerify`, the key-holder must be
> **online and interactive** to sign the assembled tx; a pre-authorized offline message
> signature cannot satisfy it. To make an offline/pre-authorized spend work, the rule must
> instead verify a `checkDataSigVerify` over data the holder *can* commit to in advance (a
> redemption-service sig + holder data-sig pre-authorization), i.e. a covenant redesign — not
> a client change. See `nexa-wallet-connection` (message signing via `signMessage` /
> `verifySignedHashSchnorr`) for the message-sig side. Also: a rule that pins an **absolute**
> output (`getOutputArgsHash(0.nx)`, literal 0) admits only **one** covenant input per tx, so
> such spends cannot be trustlessly batched.

### Pattern 2: Compile to bytecode, ONCE, in a test

NPL compilation requires `scriptmachine.Initialize()` and is slow enough that you don't
want to run it at server startup. Compile in a test, print the bytecode, paste it into
production source as a `const val`.

```kotlin
class ContractTests {
    init {
        org.nexa.scriptmachine.Initialize()
        loadCalcStackX()
        addSpecificTransitions(stackX)
        addWarriorContractTransitions(stackX)
        initRefactor()
    }

    @Test
    fun getContractHashesAndBytecode() {
        secretRevealContract.compile()
        val iface = secretRevealContract.contract("SecretRevealContract")!!.interfaces[0]
        iface.compile()
        val bytecodeHex = iface.compiled!!.toHex()
        val templateHashHex = iface.compiled!!.scriptHash160().toHex()
        println("Secret Contract Bytecode: $bytecodeHex")
        println("Secret Contract template hash: $templateHashHex")
    }
}
```

The init scaffold above is the **maximal** form (correct for delegation/"warrior"-style
contracts); simpler contracts can use fewer calls, and `initNpl()` is the minimal one-call
convenience. Which tier each contract needs, what `loadCalcStackX()`'s `stackScripts.bin` cache
does, and how all of this ties into the `Cannot find state transition` error are covered in
`stateTransitions.md` in this folder. You must always call `org.nexa.scriptmachine.Initialize()`
first regardless.

Run with:
```bash
./gradlew :server:test --tests ContractTests.getContractHashesAndBytecode -i
```

Then paste the bytecode into production source:

```kotlin
const val SECRET_CONTRACT_BYTECODE_HEX =
    "54950801000000240000007c7f77547f75818f65a96c8800545100e66c6c6c537a6b6b517a..."
const val SECRET_CONTRACT_TEMPLATE_HASH_HEX = "a598d898f30b264df9830ebbb63d151632ed7e44"
const val SECRET_TIMEOUT_SECONDS = 1800L
const val SECRET_RULE_REVEAL = 0L
const val SECRET_RULE_TIMEOUT_REFUND = 1L

fun secretContractTemplate(): SatoshiScript =
    SatoshiScript(DEFAULT_CHAIN, SECRET_CONTRACT_BYTECODE_HEX, SatoshiScript.Type.TEMPLATE)
```

The template hash constant is a **sanity check**: when you regenerate after editing the
DSL, the new hash must equal what your code expects (or you've forgotten to update one
of the two constants). If the hash matches and the bytecode doesn't, your hex is stale.

The same `org.nexa.scriptmachine.Initialize()` that compiles the DSL also drives the script
**VM**: once you have the compiled template and have built a funding + spend tx (Patterns 4–7),
write a **test** that replays that spend through the VM and asserts it executes cleanly — that is
how you catch a wrong satisfier layout, a stale bytecode constant, or a swapped visible-arg order
as a precise local failure (the exact failing opcode + stack) instead of an opaque on-chain
`mandatory-script-verify-flag-failed`. Like the compile step, this is a test-source-set activity
(`src/test`), not something you run in production. See `nexa-script-machine-testing`.

### When compilation fails: `Cannot find state transition`

You will eventually hit `java.lang.IllegalStateException: Cannot find state transition` (printed
with a `begin⇒end` stack descriptor). It is unlike an ordinary compiler error: NPL compiles each
rule by emitting opcodes that realize a *stack rearrangement* (a "state transition") between steps,
and this error means it could neither find that rearrangement in its precomputed table (`stackX`)
nor synthesize one — most often because the rule moves *many* stack items at once (lots of visible
args, or deep delegation logic).
The fix is to **supply the missing piece yourself — from your own project, no NPL source edit
required**: both the transition table (`stackX`) and the transformer registry are public, so after
the init scaffold you can hard-code the printed transition with `stackX.add(...)` or register a
`DynamicStackTransform` via `DynamicStackTransformRegistry.register(...)`, then compile (editing
NPL's own `addSpecificTransitions` / `addWarriorContractTransitions` is only how you'd *upstream*
a transition; delete a stale `stackScripts.bin` cache either way). The full mechanism, both fixes
with code, and the operational gotchas are in **`stateTransitions.md`** in this folder (terse API
in `dslReference.md` §10).

### Pattern 3: Build the visible args helper

```kotlin
/** ORDER MATTERS -- must exactly match the DSL's `holderPublicArgs` declaration order. */
fun secretVisibleArgs(
    buyerHash: ByteArray,
    sellerHash: ByteArray,
    purchaseTimeEpochSec: Long,
    purchaseAmtSatoshis: Long,
    secretHash: ByteArray,
): SatoshiScript = NexaArgs(buyerHash, sellerHash, purchaseTimeEpochSec, purchaseAmtSatoshis, secretHash)
```

`NexaArgs(...)` is a small helper most NPL projects roll themselves — it compiles each arg
into the right script PUSH opcode based on its Kotlin type:

```kotlin
fun NexaArgs(vararg args: Any?, chainSelector: ChainSelector = DEFAULT_CHAIN): SatoshiScript {
    val ret = SatoshiScript(chainSelector)
    for (a in args) {
        if (a == null) continue
        when (a) {
            is List<*>     -> if (a.isNotEmpty()) ret.add(NexaArgs(*(a.toTypedArray()), chainSelector = chainSelector).flatten())
            is Boolean     -> if (a) ret.add(OP.PUSHTRUE) else ret.add(OP.PUSHFALSE)
            is ByteArray   -> ret.add(OP.push(a))
            is Int         -> if (a == -1) ret.add(OP.CNEG1) else ret.add(OP.push(a))
            is UInt        -> ret.add(OP.push(a.toLong()))
            is Long        -> if (a == -1L) ret.add(OP.CNEG1) else ret.add(OP.push(a))
            is ULong       -> ret.add(OP.push(a.toLong()))
            else -> throw IllegalArgumentException("Cannot push ${a::class.simpleName}: $a")
        }
    }
    return ret
}
```

**Structured visible args:** when a visible arg is a fixed-layout record rather than a flat
value, NPL offers `PackedStructure` with `PBytes(n)` / `PInt(n)` property delegates instead
of hand-packing via `NexaArgs`. For example, packing an oracle price point as a fixed-offset
blob:

```kotlin
class PriceDataPoint(...) : PackedStructure(name, _nsl) {
    val tickerA: NBytes by PBytes(4)
    val tickerB: NBytes by PBytes(4)
    val epochSeconds: NInt by PInt(8)
    val priceAinB:    NInt by PInt(8)
}
```

Reach for `PackedStructure` when a contract reads a multi-field blob at fixed offsets; reach
for `NexaArgs` for the ordinary positional-args case.

### Pattern 4: Build a P2T locking script for the contract output

```kotlin
import org.nexa.libnexakotlin.Pay2TemplateDestination
import org.nexa.libnexakotlin.SatoshiScript

class P2T(tmpl: SatoshiScript, args: SatoshiScript, visArgs: SatoshiScript? = null)
    : Pay2TemplateDestination(tmpl.chainSelector) {
    init {
        template = tmpl
        constraint = args
        visibleConstraint = visArgs ?: SatoshiScript(tmpl.chainSelector)
    }
}

fun buildContractFundingTxHex(amountSatoshis: Long, visibleArgs: SatoshiScript): String {
    val template = secretContractTemplate()
    val hiddenArgs = NexaArgs(chainSelector = DEFAULT_CHAIN)            // empty
    val lockingScript = P2T(template, hiddenArgs, visibleArgs).lockingScript()

    val tx = txFor(DEFAULT_CHAIN)
    val out = txOutputFor(DEFAULT_CHAIN)
    out.amount = amountSatoshis
    out.script = lockingScript
    tx.add(out)
    return tx.toHex()
}
```

This is a partial tx — output only. Push it to the buyer's wallet via the TDPP `/tx`
flow (see `nexa-wallet-connection`).

**Alternative, preferred when you don't want to hand-roll the `P2T` class:** libnexakotlin ships a
companion builder that produces the same locking script in one call —

```kotlin
val lockingScript = SatoshiScript.p2t(
    DEFAULT_CHAIN,
    templateScriptHash = template.scriptHash160(),
    constraintArgsHash = hiddenArgs.scriptHash160(),   // null ⇒ OP_PUSHFALSE (no hidden args)
    constraintPublicArgs = visibleArgs,                 // the holderPublicArgs script
    grpId = null, tokenAmt = null)                       // set both to lock TOKENS into the contract
```

This is the same `SatoshiScript.p2t(...)` builder the script-VM test in `nexa-script-machine-testing`
Pattern 2 uses to construct a prevout, so a contract output you fund and a UTXO you replay through
the VM are built by the *identical* code path. Its `grpId` / `tokenAmt` parameters are the missing
link between this skill and `nexa-tokens-and-groups`: passing a group id and quantity builds a
**token-bearing contract output** (a covenant that holds a native token) directly, without a
separate `grouped(...)` step — the library-level counterpart to
`Contract.groupedConstraint(...)`.

### Pattern 5: Spend the contract — build the Spendable + satisfier

```kotlin
private fun contractSpendableAndSatisfier(
    visibleArgs: SatoshiScript,
    fundingTxid: Hash256, fundingVout: Int,
    fundedValue: Long,
    ruleIdx: Long,
    spenderArgs: SatoshiScript,
): Pair<Spendable, SatoshiScript> {
    val template = secretContractTemplate()
    val hiddenArgs = NexaArgs(chainSelector = DEFAULT_CHAIN)
    val locking = P2T(template, hiddenArgs, visibleArgs).lockingScript()

    val spendable = Spendable(DEFAULT_CHAIN).apply {
        amount = fundedValue
        outpoint = NexaTxOutpoint(fundingTxid, fundingVout)
        priorOutScript = locking                          // rebuild from same args
    }

    // Satisfier layout:
    //   PUSH(template bytes), [PUSH(hiddenArgs bytes) — ONLY if non-empty], <spender args>, PUSH(ruleIdx)
    val tail = NexaArgs(ruleIdx, chainSelector = DEFAULT_CHAIN)
    val satisfier = SatoshiScript(DEFAULT_CHAIN, SatoshiScript.Type.SATOSCRIPT,
        OP.push(template.flatten()))
    // Do NOT push an empty hidden-args placeholder. When there are no hidden args the locking
    // script committed OP_PUSHFALSE and the constraint section is ZERO bytes; a stray
    // OP.push(ByteArray(0)) leaves an unconsumed `BYTES 0 false 0` on the stack — see below.
    val hiddenBytes = hiddenArgs.toByteArray()
    if (hiddenBytes.isNotEmpty()) satisfier.add(OP.push(hiddenBytes))
    satisfier.add(spenderArgs.toByteArray())
    satisfier.add(tail.toByteArray())

    return Pair(spendable, satisfier)
}
```

> **Never push an empty hidden-args placeholder.** The unlocking script is
> `template ++ constraint(hidden args) ++ satisfier` concatenated. When a covenant has **no**
> hidden args, the locking script commits `OP_PUSHFALSE` and the constraint section must be
> **zero bytes**. Pushing `OP.push(ByteArray(0))` as a placeholder leaves a stray
> `BYTES 0 false 0` that no rule ever consumes → the script runs to `scriptErr = No error(0)`
> **but leaves the main stack not clean**, which consensus rejects. This is invisible to
> replays of rules that fail an early `verify` (they never reach the end) — only a **valid**
> spend replay through the script VM catches it (`nexa-script-machine-testing` asserts the main
> stack is empty for exactly this reason). Push the hidden-args element only when
> `hiddenArgs.toByteArray()` is non-empty.

### Pattern 6: Building the seller's claim tx (Rule 0: Reveal)

```kotlin
fun buildClaimTx(
    visibleArgs: SatoshiScript,
    fundingTxid: Hash256, fundingVout: Int,
    fundedSatoshis: Long,
    payoutSatoshis: Long,                          // MUST equal purchaseAmt
    sellerAddress: String,
    revealedSecretBytes: ByteArray,
): iTransaction {
    val spenderArgs = NexaArgs(revealedSecretBytes, chainSelector = DEFAULT_CHAIN)
    val (spendable, satisfier) = contractSpendableAndSatisfier(
        visibleArgs, fundingTxid, fundingVout, fundedSatoshis, SECRET_RULE_REVEAL, spenderArgs)

    val tx = txFor(DEFAULT_CHAIN)
    val input = NexaTxInput(spendable)
    input.script = satisfier
    tx.add(input)

    val out = txOutputFor(DEFAULT_CHAIN)
    out.amount = payoutSatoshis
    out.script = PayAddress(sellerAddress).lockingScript()
    tx.add(out)
    return tx
}

// Broadcast (no signature needed; contract validates by hash):
nexaWallet.blockchain.net.broadcastTransaction(claimTx.toByteArray())
```

### Pattern 7: Building the buyer's refund tx (Rule 1: TimeoutRefund)

```kotlin
fun buildRefundTx(
    visibleArgs: SatoshiScript,
    fundingTxid: Hash256, fundingVout: Int,
    fundedSatoshis: Long,
    payoutSatoshis: Long,
    buyerAddress: String,
    refundableAtEpochSec: Long,
): iTransaction {
    val spenderArgs = NexaArgs(chainSelector = DEFAULT_CHAIN)        // empty for TimeoutRefund
    val (spendable, satisfier) = contractSpendableAndSatisfier(
        visibleArgs, fundingTxid, fundingVout, fundedSatoshis, SECRET_RULE_TIMEOUT_REFUND, spenderArgs)

    val tx = txFor(DEFAULT_CHAIN)
    tx.lockTime = refundableAtEpochSec
    val input = NexaTxInput(spendable)
    input.script = satisfier
    input.sequence = 0xfffffffeL                                       // see nexa-locktime-cltv
    tx.add(input)

    val out = txOutputFor(DEFAULT_CHAIN)
    out.amount = payoutSatoshis
    out.script = PayAddress(buyerAddress).lockingScript()
    tx.add(out)
    return tx
}
```

### Pattern 8: Overfund the contract so claim/refund txs have a fee budget

Contracts that verify `outputValueN(0.nx) eq purchaseAmt` lock the output amount
exactly. The spending tx has no room to subtract a network fee from that amount,
leading to `mempool min fee not met` rejections.

Solution: **overfund** the contract at funding time. The output is `purchaseAmt +
FEE_BUFFER`; the spending tx outputs exactly `purchaseAmt`; the buffer becomes the fee.

```kotlin
const val CONTRACT_FEE_BUFFER_SATOSHIS = 1000L    // 10 whole NEXA — generous

// At funding (in your buy handler):
val priceSatoshis = listing.priceNexa.nexa        // purchaseAmt, baked into visible args
val fundedSatoshis = priceSatoshis + CONTRACT_FEE_BUFFER_SATOSHIS

// Visible args still use priceSatoshis -- the contract validates outputs against
// purchaseAmt, not against the funded amount.
val visibleArgs = secretVisibleArgs(buyerHash, sellerHash, purchaseTimeSec,
    priceSatoshis, listing.secretHash)
val partialTxHex = buildContractFundingTxHex(fundedSatoshis, visibleArgs)

// When the funding tx callback arrives, capture the ACTUAL funded amount from the
// on-chain output (the wallet may have nudged the amount), not what we computed:
val actualFunded = tx.outputs[vout].amount
listing.fundedSatoshis = actualFunded
```

Then at claim/refund time:
- input amount = `listing.fundedSatoshis` (what's on-chain)
- output amount = `listing.priceNexa.nexa` (purchaseAmt, what the contract demands)
- fee = `fundedSatoshis - purchaseAmt`

**Size the buffer to the spend tx, not to a one-size-fits-all constant.** Network fees are
proportional to the *serialized size* of the spending transaction (sat-per-byte), so a
buffer that clears the mempool for a one-input/one-output reveal can be too small for a
covenant spend that adds several outputs or carries a large satisfier (revealed secrets,
many visible args). When unsure, overfund more generously — the excess simply becomes
fee — and confirm the spend actually clears rather than assuming a fixed number always
works. A buffer that is *too small* surfaces at spend time as `mempool min fee not met`
(see `nexa-debugging-onchain-errors`), which is unfixable without re-funding a new contract
output.

### Pattern 9: Reading another output's *individual* visible args via OP_PARSE

`getOutputArgsHash(0.nx)` (Pattern 1) gives you the **combined** args hash of an output —
enough to assert "this output pays exactly this recipient" when you already know the full
arg set and can recompute its hash. But sometimes a contract must read an **individual**
visible arg out of a *sibling* output (or out of the prevout it is spending) without
knowing the rest — e.g. a delegation contract that checks "the new contract Bob is
creating carries *Alice's* hash in visible-arg slot 0, whatever Bob put in the other
slots." That is what NEXA's `OP_PARSE` is for, and NPL exposes it two ways.

These read from an output's **canonical parsed form**, whose field numbering is fixed (it
matches the on-chain `OP_PARSE` "canonical output form" in the Nexa spec —
`https://spec.nexa.org/script/op-codes/op_parse/`; the NPL accessors live in NPL's
`opParseHelpers.kt` / `nsl.kt`):

| Field | Meaning | NPL accessor |
| --- | --- | --- |
| 0 | groupId — `OP_0` if the output is ungrouped (native NEXA) | `getOutputGroupId(outIdx)` |
| 1 | groupAmount (BIN2NUM'd). **For an ungrouped or *fenced* output this is the output's native-NEXA amount, not 0** (the canonical form normalizes NEXA to look like any token; field 0 = `OP_0` is what distinguishes it). An authority output's amount field is `OP_0`. | `getOutputGroupAmount(outIdx)` |
| 2 | group authority flags (raw bytes; sign-magnitude); `OP_0` if the output claims no authority or is ungrouped | `getOutputGroupAuthority(outIdx)` and variants |
| 3 | template hash — **the value as it appears in the output**: a well-known-template *number* stays a number, a full hash stays a hash; `OP_PARSE` does **not** convert between them, so compare against the form your contract expects | `getOutputContractId(outIdx)` / `parseOutputTemplateHash(outIdx)` |
| 4 | args hash (all hidden+visible args, hashed) | `getOutputArgsHash(outIdx)` / `parseOutputArgsHash(outIdx)` |
| 5, 6, 7 | **reserved** — always parse as `OP_0` (this is why the visible args start at field 8, not 5) | — |
| 8, 9, 10, … | individual **visible** args (holderPublicArgs), in declaration order | `getOutputVisibleArg(outIdx, n)` / `parseOutputArg(outIdx, 8+n)`; prevout side: `getPrevoutVisibleArg(inIdx, n)` / `getPrevoutVisibleArgAsInt(inIdx, n)` |

The group fields (0/1/2) are the basis of native-token contracts; their accessors and the
same-group covenant pattern are covered in `nexa-tokens-and-groups`.

The last argument of the underlying `OP_PARSE` call selects the **parse operation** (data
source): **`OUTPUT_DATA` (0)** — read an output of *this* tx; **`PREVOUT_DATA` (1)** — read the
UTXO (prevout) being spent at an input; **`INPUT_DATA` (2)** — read an *unlocking* script, whose
canonical form is `0` = template bytecode, `1` = hidden-args bytecode, `2…` = satisfier pushes;
**`BYTECODE_DATA` (3)** — parse an arbitrary serialized script you already have on the stack (e.g.
the template bytecode you pulled with `INPUT_DATA`). `OUTPUT_DATA`/`PREVOUT_DATA` use the canonical
*output* field numbering in the table above; the NPL `getOutput*`/`getPrevout*` accessors wrap the
first two. Any parse-operation value other than 0–3 fails the script. NPL wraps the output/prevout
variants:

```kotlin
script {
    // Combined args-hash check (you know all the args, so recompute and compare):
    verify(getOutputArgsHash(0.nx) eq expectedArgsHash)

    // Individual visible-arg check — read slot 0 of output 0 (e.g. Alice's hash),
    // WITHOUT needing to know the other visible args Bob chose:
    val aliceHashOnOutput = getOutputVisibleArg(0.nx, 0.nx)     // 0-based visible-arg index
    verify(aliceHashOnOutput eq aliceHash)

    // Same idea against the prevout (the UTXO this rule is spending):
    val argsHashBeingSpent = parsePrevoutArgsHash(0.nx)         // input 0, variant 1

    // Individual visible args of a prevout — including a SIBLING input's prevout (added to NPL
    // in a recent release): read what another contract input in this same tx was committed to.
    val siblingCommit = getPrevoutVisibleArg(otherInputIdx, 0.nx)      // bytes form
    val siblingDeadline = getPrevoutVisibleArgAsInt(otherInputIdx, 2.nx) // BIN2NUM'd form
    verify(siblingCommit eq myUserHash)
}
```

The prevout visible-arg accessors unlock **cross-input coordination**: a rule can verify that
*another input being spent in the same transaction* is an instance of an expected contract
(`getPrevoutContractId(otherIdx) eq expectedTemplateHash`) *and* that it was parameterized with
the values this rule cares about (`getPrevoutVisibleArg(otherIdx, n) eq …`). That pair of checks
is the basis of the enforcer/follower pattern (Pattern 11), where many lightweight inputs
delegate their whole-tx validation to one full-validation input.

Two accessor families exist (pick by ergonomics, not correctness): the **NSL members** like
`getOutputVisibleArg` take a **0-based** visible-arg index — the library adds the `+8` field offset
for you; the **top-level helpers** in `opParseHelpers.kt` like `parseOutputArg` take the **raw
canonical field number**, so the first visible arg is field **8**, not 0. Mixing the two index bases
is a classic bug — see the dedicated anti-pattern below, and the full accessor list in
`dslReference.md` §9.

This is the script-side counterpart of the server-side `extractAcceptorAddrFromTx` /
`tmpl.rest` reads in `nexa-identity-and-addresses` — both pull the same positional visible
args, one from inside the VM, the other from a parsed tx in Kotlin.

> Use the combined `getOutputArgsHash` check when your contract (or its operator) knows the
> complete arg set and just needs to bind the output to a known recipient; use the individual
> `getOutputVisibleArg` / `parseOutputArg` reads when the contract must validate *one* party's
> parameter while leaving the counterparty free to choose the rest — the foundation of the
> open-offer delegation pattern.

### Pattern 10: Threading state through a UTXO chain (`verifySameContract` / `verifySameGroup`)

Multi-step protocols on a UTXO chain (a baton passed forward, a token vault that splits and
continues, an escrow that re-locks a remainder) need a rule to assert "the output I'm
creating continues the *same* contract (and/or the same token group) I'm spending." NPL
provides two convenience pairs for exactly this:

```kotlin
script {
    // "the output at destIdx must re-lock under the same template (contract) as the UTXO
    //  I'm spending at this input":
    verifySameContract(destIdx)                 // compares getOutputContractId(destIdx)
                                                //   eq getPrevoutContractId(thisIndex())
    // explicit-input form when you aren't spending at thisIndex():
    verifySameContract(inputIdx, destIdx)

    // "the output at destIdx must carry the same group (token) as the UTXO I'm spending":
    verifySameGroup(destIdx)                    // getOutputGroupId(destIdx) eq getPrevoutGroupId(thisIndex())
    verifySameGroup(inputIdx, destIdx)
}
```

`verifySameContract` compares **template (contract) hashes** — it is field-3 (`OP_PARSE`
contract id) on both sides — so it pins continuation to the same compiled contract without
caring about the visible args. `verifySameGroup` pins the field-0 group id. They are
independent: a token-bearing covenant that must stay both the same contract *and* the same
group calls both. Use these instead of hand-writing the `getPrevout… eq getOutput…`
comparisons; they read more clearly and are harder to get backwards. The token side is
developed further in `nexa-tokens-and-groups` (Pattern 6, the same-group covenant).

**Higher-level introspection helpers (`nsllib`).** Above the raw `getOutput*`/`getPrevout*`
accessors, NPL ships composed helpers for the recurring "where am I / where must value go"
checks, callable inside any `script { }`: `thisIndex()`/`thisGroup()`/`thisTemplateHash()`/
`thisArgsHash()`/`thisInputUtxoHash()` (facts about the running input, no hardcoded index),
`mustSpendGroupToP2pkt(gid, addr)` (force a token group to exactly one P2PKT output, with an
`argsCheck` overload to constrain its args hash), `forGroupedOutputs(N, gid) { outIdx -> … }`
(apply a check to each grouped output — **unrolls** N times since the VM has no loops, and fails
if there are more than N, so size N to the real maximum), and `groupsIn(...)` / `groupsOut(...)`
(assert each named group is present among the tx's inputs / outputs). Reach for these before
hand-rolling the equivalent `countOutputsByGroup` / `groupedOutputN` /
`constraintScriptForOutputN` sequence; full signatures are in `dslReference.md` §8.

### Pattern 11: The enforcer/follower split — validating a many-input covenant tx once

A covenant that re-checks the **whole output set on every input** does not scale: a tx that
spends N such UTXOs carries N copies of the full validation script (satisfier + template per
input), and the serialized size grows quadratically-ish with N — a 64-input settlement built
this way can run to hundreds of KB, past the network's relay-policy size limit (on the order of
100 KB serialized). The fix is to run the full validation **once per transaction, not once per
input**, by splitting the contract in two:

- **The enforcer** — one dedicated, *ungrouped dust-value* UTXO ("bond") locked under an
  enforce-only contract whose rule(s) validate the **entire output set** (every output's
  contract id, group, amount, visible args). Its visible args commit to the batch's parameters
  (the parties' hashes, a deadline, a batch id). Only this one input carries the heavy script.
- **The followers** — every real (value/token-bearing) UTXO is locked under a lightweight
  contract whose spend rule does **no output checking at all**. It only proves a matching
  enforcer is present in the same tx, taking the enforcer's input index as a spender arg:

```kotlin
// follower rule body — three checks, no output introspection:
script {
    checkSigVerify(operatorSig, OperatorPubkey)                       // who may move the batch
    verify(getPrevoutContractId(enforcerIdx) eq EnforcerTemplateHash) // a real enforcer input exists
    verify(getPrevoutVisibleArg(enforcerIdx, 0.nx) eq myUserHash)     // …committed to MY params
    verify(getPrevoutVisibleArgAsInt(enforcerIdx, 2.nx) eq myBatchId)
}
```

Why this is sound: the enforcer constrains every output slot, and (for token followers) Nexa's
consensus group-conservation then forces each token into one of those validated slots — so the
followers inherit the enforcer's guarantees without inspecting anything themselves. Three
load-bearing rules when you build one of these:

- **Every rule of the enforcer contract must fully enforce the output set.** A follower pins
  only the enforcer's *template hash* (field 3), and that hash is the same **whichever rule**
  spends the bond. One partial or no-op rule in the enforcer's face lets an attacker spend the
  bond through it while every follower still passes — outputs unconstrained, funds stolen.
  Consequence: an enforcer bond typically has **no refund/timeout rule** (a refund rule doesn't
  validate outputs). That's acceptable because the bond is dust; put the refund path on the
  followers instead.
- **Input order is part of the protocol.** The enforcer typically pins output *i* to input *i*
  (and followers may range-check their own `thisIndex()`), so the tx builder must place cards/
  assets and the bond at agreed indices, and the followers' `enforcerIdx` spender arg must point
  at the bond's actual index.
- **The enforcer bond is created by the operator at batch setup** (an ordinary dust output
  paying the enforcer's P2T locking script) and is throwaway — only dust is at risk if the
  batch is abandoned.

NPL's published test sources contain a complete worked example of this pattern (a two-party
batch escrow of 64 token UTXOs settled in one tx, with delegation and resolution enforcers).
This is also the answer to the note in Pattern 1's `checkSigVerify` blockquote that
absolute-output covenants "cannot be trustlessly batched": the enforcer is the one place that
pins outputs, so followers *can* be batched arbitrarily.

### Pattern 12: Scriptlets — a holder-supplied script executed with OP_EXEC

The args slots don't have to hold plain data. A **scriptlet** is a small script pushed *as data*
into the hidden or visible args, which the template later executes with the `OP_EXEC` opcode
(`https://spec.nexa.org/addresses/scriptTemplates/` § Scriptlets). This is how a template author
lets the **holder** contribute *programmatic* constraints instead of only data: the classic
template shape hardcodes "holder spends with a single key" —

```
IF   authorSpendConstraints()
ELSE authorConstraintsOnHolderSpend()  <holderSig> <holderPubkey> CHECKSIGVERIFY  ENDIF
```

— which forces every holder onto a single pubkey lock. The scriptlet form replaces the
`CHECKSIGVERIFY` tail with `<holderScriptlet from the locking-script args> EXEC`, so each holder
funds instances with whatever lock they want (multisig, timelocked, another covenant) without
changing the template hash.

The `OP_EXEC` execution rules that shape scriptlet design
(`https://spec.nexa.org/script/op-codes/op_exec/`):

- Stack layout is `code param1…paramN N_Params M_Returns EXEC`: N params are moved into the
  subscript, it runs, then M results are copied back to the main stack.
- The subscript runs on an **isolated stack** — it can neither read nor modify the caller's
  main/alt stacks beyond the params it was handed. This isolation is deliberate: satisfier- and
  constraint-supplied code is antagonistic to the template, and must not be able to reach around
  it. A zero-length scriptlet is valid (does nothing).
- Subscript operations **count toward all consensus limits** (op count, sigchecks, stack), and
  recursion is capped: at most `MAX_EXEC_DEPTH = 3` deep and `MAX_OP_EXEC = 20` total calls per
  script. Both caps may be *raised* in future — never design a contract that relies on hitting
  them to fail.

libnexakotlin exposes the opcode as `OP.EXEC` (`0xed`; `OP.JUMP` is the related `0x65`) for
raw-script construction, and the `scriptmachine` resource counters report per-run usage
(`opExecsExecuted` / `opExecRecursionDepth` — `nexa-script-machine-testing` Pattern 6). The NPL
DSL surface documented in this corpus has no dedicated scriptlet builder; a template that EXECs
a holder arg is currently authored at the raw-script level (or verify the current `org.nexa:npl`
artifact for newer support before hand-rolling).

Two adjacent execution-model facts from the same spec page, useful when a big contract brushes
against limits: the **template script is not pushed onto the VM stack** during execution, so its
size is not subject to per-stack-item limits; and the hidden args (serialized as one push in the
satisfier) may **collectively** exceed stack limits once re-split — though each individual arg
must still fit.

## Common mistakes and anti-patterns

### Pushing an implicit `outputIdx` in spender args

**Wrong** (copied from a reference contract that DID declare outputIdx as a spender arg):
```kotlin
val tail = NexaArgs(0L /* outputIdx */, ruleIdx, chainSelector = DEFAULT_CHAIN)
```
*If your DSL hardcodes `getOutputArgsHash(0.nx)` (literal 0, not a variable from
spenderArgs), there is no outputIdx slot. The extra push shifts the stack by one, and
some other comparison consumes `outputIdx=0` instead of the value it expected →
`OP_EQUALVERIFY` failure.*

**Right**: only push what your DSL declared:

```kotlin
// DSL: spenderArgs = listOf(revealedSecretBytes)
val tail = NexaArgs(ruleIdx, chainSelector = DEFAULT_CHAIN)
```

Pattern: the satisfier's tail args are **exactly** the declared `spenderArgs` (in order)
followed by **ruleIdx**, and nothing else.

> **Concrete counter-example:** plenty of real contracts *do* declare `outputIdx` as a
> spender arg — precisely because the rule lets the spender choose which output it
> constrains (`getOutputArgsHash(outputIdx)` rather than a literal `0.nx`). A typical
> delegation contract has three rules, each with its own spender-arg list:
>
> | Rule | ruleIdx | spenderArgs (in order, before ruleIdx) |
> | --- | --- | --- |
> | DelegateToBob | 0 | `bobHash, outputIdx` |
> | ReclaimForAlice (cancel, facilitator sig) | 1 | `outputIdx, facilitatorSig` |
> | ReturnExpiredToAlice (locktime refund) | 2 | `outputIdx` |
>
> So `outputIdx` is not "always wrong" — it is wrong only when *your* DSL hardcodes
> `getOutputArgsHash(0.nx)`. The invariant is unchanged: the satisfier tail must be exactly the
> spenderArgs your DSL declared for that rule, in order, then ruleIdx. In code, build one
> `NexaArgs(... , ruleIdx)` and append it after the template + hiddenArgs pushes:
> `satisfier.add(NexaArgs(bobHash, outputIdx, ruleIdx).toByteArray())`.
>
> The ruleIdx values in the table above follow **alphabetical** rule-name order (which for
> `DelegateToBob`/`ReclaimForAlice`/`ReturnExpiredToAlice` happens to coincide with how they
> were declared) — recall the compiler sorts rules by name, so always derive these constants
> from the sorted names, not the order you wrote them (see the rule-index note near the top).

### Bytecode constant out of sync with DSL

**Wrong**: You change the DSL (e.g. fix a bug where TimeoutRefund's `sellerHash eq
parsedBuyerHash` should be `buyerHash eq parsedBuyerHash`), but forget to regenerate
the bytecode hex. The compiled contract on-chain is still buggy; your code is now also
checking arguments based on a different layout than the bytecode expects → mysterious
`OP_EQUALVERIFY` failures.

**Right**: after any DSL edit, regenerate the bytecode AND template hash. Compare the
new template hash to the constant. If they differ, the DSL changed; update both
constants. If they match, the DSL is semantically identical (or you forgot to save
the file).

### Spender-arg order disagreeing with declaration order

**Wrong**:
```kotlin
// DSL: holderPublicArgs = listOf(buyerHash, sellerHash, purchaseTime, purchaseAmt, secretHash)
fun visibleArgs(...) = NexaArgs(sellerHash, buyerHash, ...)   // swapped first two
```
*Visible args are read positionally inside the script (NPL compiles each named arg to a
specific stack-position reference). Swapped order → contract reads `buyerHash` where
you put `sellerHash` and vice versa → output-destination check fails (or worse, lets
the wrong party claim).*

**Right**: match `holderPublicArgs`/`spenderArgs` order **exactly**:
```kotlin
fun visibleArgs(buyerH, sellerH, time, amt, hash) =
    NexaArgs(buyerH, sellerH, time, amt, hash)
```

### Confusing the two OP_PARSE accessor families' index bases

The NSL member `getOutputVisibleArg(outIdx, n)` takes a **0-based visible-arg index** (it adds
the `+8` canonical-field offset internally). The top-level `parseOutputArg(outIdx, field)`
takes the **raw canonical field number**, where the first visible arg is field **8**. Mixing
the conventions reads the wrong field and the eventual `verify(... eq ...)` fails with
`OP_EQUALVERIFY`.

**Wrong** — passing a raw field number to the 0-based accessor (reads field 16, not the first visible arg):
```kotlin
val firstVisible = getOutputVisibleArg(0.nx, 8.nx)   // 8 + 8 = field 16 → garbage
```
**Wrong** — passing a 0-based index to the raw-field helper (reads field 0 = groupId):
```kotlin
val firstVisible = parseOutputArg(0.nx, 0.nx)        // field 0 = groupId, not visible arg 0
```

**Right**:
```kotlin
val firstVisible = getOutputVisibleArg(0.nx, 0.nx)   // 0-based: first visible arg
// or, with the raw-field helper:
val firstVisible = parseOutputArg(0.nx, 8.nx)        // field 8: first visible arg
```

### Compiling NPL at server startup

**Wrong**:
```kotlin
fun main() {
    org.nexa.scriptmachine.Initialize()                  // 200-2000ms startup hit
    secretRevealContract.compile()                        // pulls scriptmachine into hot path
    // ...
}
```

**Right**: hardcode the compiled bytecode as a constant. Run the compile-and-print test
once. Re-run it when the DSL changes.

### Storing `purchaseTime` in milliseconds

**Wrong**:
```kotlin
val purchaseTimeMs = System.currentTimeMillis()       // ~1.7e12 -- doesn't fit script-VM int
```

**Right**: epoch **seconds** (~1.7e9). Fits comfortably in a 32-bit signed int, which is
what NEXA's `OP_CHECKLOCKTIMEVERIFY` expects for timestamp interpretation (values >=
500_000_000 = timestamp, < 500_000_000 = block height).

```kotlin
val purchaseTimeSec = epochMilliSeconds() / 1000L
```

### Forgetting that `getOutputArgsHash(0.nx)` only works on template outputs

**Wrong**: spending the contract to a P2PKH address.
```kotlin
out.script = PayAddress(p2pkhRecipient).lockingScript()
```
*The contract's `getOutputArgsHash(0.nx)` returns null (or fails) for a non-template
output, so the comparison `sellerHash eq parsedSellerHash` fails.*

**Right**: recipient must be P2PKT. See `nexa-identity-and-addresses` for how to
validate that an address is P2PKT before locking funds against it.

### Building the visible args at spend time differently than at fund time

**Wrong**: storing `buyerAddress` at fund time but extracting `buyerHash` from a fresh
lookup at spend time, where the address has rotated.

**Right**: store the *exact P2PKT address used to compute the visible args at fund
time*. Pin it to the contract record. Use that same address (and thus same argsHash)
when rebuilding visible args for the spend.

```kotlin
class ContractRecord(
    val buyerRefundAddress: String,                  // P2PKT, snapshot at buy time
    /* ... */
)
// ... at spend ...
val buyerHash = extractArgsHash(record.buyerRefundAddress)   // SAME bytes as at fund time
```

### Trying to use `Hash256(stringHex)` for a raw 32-byte hex without reversal

If you parse a txid from an external source (explorer URL, RPC), most NEXA tooling
accepts the hex in standard (non-reversed) order. `Hash256(hex)` constructor matches
that. Don't reverse the bytes unless you have evidence the source is reversed.

### Pushing a negative Long as a literal

`OP.push(-1L)` is invalid in raw form; NPL uses `OP.CNEG1` for the constant -1 and
forbids other negative pushes in script literals. `NexaArgs` handles -1 specially:

```kotlin
is Long -> if (a == -1L) ret.add(OP.CNEG1) else ret.add(OP.push(a))
```

If you need a negative number other than -1, encode it via two's-complement byte
operations or restructure the contract logic.

## Security considerations

- **Anyone who learns the spender args can spend the contract.** A secret-reveal
  contract has no signature check; the secret IS the key. Once you broadcast the claim
  tx, the secret is on-chain forever and anyone watching the mempool can use it. This
  is correct behavior for secret-reveal — but be aware that **spender-args are not
  private**.

- **`hash160(x)` is `RIPEMD160(SHA256(x))`** — a 20-byte hash. Collision-resistant in
  practice but smaller than SHA256. Long enough for commit-reveal but not large enough
  to be a key on its own. The spec's guidance for the template/args hashes (either
  hash160 or hash256 is legal — the length disambiguates): prefer hash160 for size,
  **unless the hash preimage is constructed by multiple parties** — a multi-party
  preimage lets a malicious co-author grind for a 20-byte collision (Wagner's birthday
  attack), so use hash256 there (`https://spec.nexa.org/addresses/scriptTemplates/`).

- **`getOutputArgsHash` lets the contract dictate the spender's output script.** This is
  the foundation of "forced settlement" patterns: an atomic-swap contract verifies that
  the spending tx pays the right counterparty. Don't break this property — don't write
  rules that take `sellerHash` from `spenderArgs` (would let the spender redirect
  funds).

- **A contract output's locking script is fully public.** Visible args (price, hashes,
  addresses, deadlines) are all observable by anyone watching the chain. Treat
  `holderPublicArgs` as a public commitment. For anything you need to keep private,
  use `holderArgs` (hidden args) — but those are committed via `argsHash` and must be
  revealed to spend.

- **The fee buffer pattern leaks information.** Overfunding by exactly 1000 sat reveals
  to chain observers that this is a contract claim from this app, not a regular
  payment. Acceptable for most apps; if you want to obscure, randomize the buffer in a
  small range.

- **Re-spend after broadcast.** Contract UTXOs are spendable until they are spent. If
  two parties both have valid satisfiers (e.g., both know the secret), whoever
  broadcasts first wins. Build front-running resistance into your protocols (e.g., the
  contract forces the output to a specific recipient so an attacker stealing the
  secret cannot redirect funds).

- **`checkLockTimeVerify` is necessary but not sufficient for refunds.** The
  input's `nSequence` must also be `< 0xFFFFFFFF`. See `nexa-locktime-cltv`. Forgetting
  this gives `Locktime requirement not satisfied` rejections that look unrelated.

- **Don't add `OP_RETURN` outputs to a contract spend.** Some VM versions count
  unspendable output amounts against the input value, breaking your fee math.

- **Contract spends are NOT atomic across multiple inputs by default.** If your tx
  spends two contract UTXOs, both must individually validate. If one rule allows
  spending and the other doesn't, the whole tx is rejected.

- **If other contracts pin your contract's template hash, every rule you add is part of their
  security.** A follower/delegator that checks `getPrevoutContractId(...) eq YourTemplateHash`
  (Pattern 11) trusts *all* of your rules equally — the template hash doesn't say which rule
  spent the UTXO. Adding a convenience rule (a refund, an admin escape hatch) that skips the
  validation the pinning contracts rely on silently breaks them. Either make every rule fully
  enforce, or split the lenient path into a separate contract with a different hash.

## Related skills and references

- `nexa-locktime-cltv` — required reading for any contract using `checkLockTimeVerify`.
  The `nSequence` and MTP gotchas live there.
- `nexa-transaction-construction` — how the `iTransaction` you build here becomes a
  broadcast on-chain; also its **read-only inputs** pattern (reference a UTXO's
  data/args in this tx without spending it — the prevout accessors in Pattern 9 can
  read a read-only input's UTXO, enabling shared oracle/state UTXOs many txs read in
  parallel).
- `nexa-identity-and-addresses` — how to make sure your `sellerAddress` is actually
  P2PKT so `getOutputArgsHash` works on the output.
- `nexa-tokens-and-groups` — the group/token slice of this DSL: the field-0/1/2 group
  accessors, `verifySameGroup`, mint/authority, and same-group token covenants.
- `nexa-script-machine-testing` — write a test that runs a built contract spend through the real
  script VM (`org.nexa:scriptmachine`, test source set) to confirm the reveal/refund/covenant rule
  executes correctly; step/inspect a failing satisfier instruction-by-instruction.
- `nexa-capd-messaging` — for multi-party contracts (delegation, escrow, atomic swaps) where the
  participants must exchange partial transactions / signatures off-chain without a direct
  connection, CAPD's `CapdProtocolCommunication` is the coordination channel libnexakotlin builds
  its own contract-coordination flows on.
- `nexa-electrum-monitoring` — watch a deployed contract UTXO from a light client to detect when (and
  by which tx) it is spent, without running a node.
- `nexa-debugging-onchain-errors` — diagnostic table for the script-verify failures
  you'll encounter while iterating on NPL.

### Supporting files in this folder

- `dslReference.md` — catalogue of the NPL DSL surface (typed-arg classes `NBytes`/`NInt`/
  `NSig`/`NPubKey`/…, the `.nx` constants, comparison/arithmetic/bitwise operators,
  hashing + signature ops, control flow, split/data ops, the full input/output/prevout/group
  introspection family, the OP_PARSE helper families, `PackedStructure`, the high-level
  `nsllib` helpers, and the compilation/state-transition internals), with signatures.
- `compileAndPrintTemplate.kt` — drop-in test scaffold (the compiler init scaffold +
  `compile()` → bytecode hex + template `hash160`) the user copies once and parameterizes
  for their own contract.
- `stateTransitions.md` — the compile init-scaffold tiers, the `stackScripts.bin` cache, the
  full `Cannot find state transition` mechanism + the two fixes (registered from your own
  project), how to *design rules that avoid the error*, and the compile-time stack/script-size
  diagnostics (the on-demand companion to Pattern 2 and the "When compilation fails" section).
- `opcodesDecoded.md` — partial guide to disassembling compiled NPL bytecode (`toAsm`) and
  recognizing the DSL constructs behind the opcodes (hashing, comparison, signatures/CLTV,
  the `OP_PARSE`/`OP_PUSH_TX_STATE` introspection families).

### Supporting files in this folder (to be created)

- `examples/` — full example projects: atomic swap, multi-sig escrow, oracle bet,
  HTLC. With both the DSL and the generated bytecode + satisfier construction.