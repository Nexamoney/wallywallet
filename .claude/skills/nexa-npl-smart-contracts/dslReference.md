# NPL DSL reference

A catalogue of the `org.nexa.npl` DSL surface — the functions, operators, and types you use
to *define* a contract and to write the logic *inside* a rule's `script { }` block. Read the
parent `SKILL.md` first for the mental model (P2T outputs, the three args slots, satisfier
layout) and the compile workflow; this file is the lookup table behind it.

Signatures are grounded in the library source (`npl.kt`, `nsl.kt`, `nsltypes.kt`,
`nsltypesint.kt`, `nslstruct.kt`, `nsllib.kt`, `opParseHelpers.kt`, `nslxlat.kt`). The DSL
iterates, so when an exact signature matters, the declaration in those files is authoritative
over this summary.

---

## 1. Project / contract definition (npl.kt)

You build an `NPL` project object, then `.compile()` it and read the bytecode off its
interfaces.

| Entry point | Purpose |
| --- | --- |
| `Nexa(name: String, initializer: NplBuilder.() -> Unit): NPL` | General builder. Its block can declare `group`s, `contract`s, and project-level `face`s. |
| `NexaContract(block: ContractBuilder.() -> Unit): NPL` | Shortcut for one unnamed contract (no groups). |
| `NplScript(templateArgs, hiddenArgs, visibleArgs, satisfierArgs, code: NSL.() -> Unit): NPL` | Shortcut for a single rule with one script body. All four arg lists are `List<NBinding>? = null`. |
| `NexaExtend(npl: NPL, initializer: NplExtensionBuilder.() -> Unit): NPL` | Extend an already-defined project. |

**Builders (inside the blocks above):**

- `NplBuilder`: `contract(name) { … }`, `face(name) { … }`, `group(name, flags) { … }`,
  `groupOfNexa(name, flags) { … }`, `address(name) { … }`, `import(module: NPL)`.
- `ContractBuilder`: `face { … }`, `implement(face) { … }`, and the rule shortcuts
  `rule(name, templateArgs, holderArgs, spenderArgs) { … }` / `rule { … }`.
- `InterfaceBuilder` (the `face { }` body): declares the rules —
  - `ruleWithPublicArgs(name, templateArgs, holderArgs, holderPublicArgs, spenderArgs) { script { … } }`
    — the form **with** an on-chain visible-args (`holderPublicArgs`) slot.
  - `fullRule(...)` — exact alias of `ruleWithPublicArgs`.
  - `rule(name, templateArgs, holderArgs, spenderArgs) { … }` — shortcut with **no**
    `holderPublicArgs` slot; its block is the script body directly.
- `RuleBuilder` (the `ruleWithPublicArgs`/`fullRule` body): `script { … }`, plus
  `templateArgs(...)`, `hiddenArgs(...)`, `visibleArgs(...)`, `satisfierArgs(...)`.

> Argument-order trap: `rule`'s fourth positional list is `spenderArgs`; in
> `ruleWithPublicArgs` the fourth is `holderPublicArgs` and `spenderArgs` is fifth.

**Interface abstraction.** A project-level `face("name") { … }` declares an interface: a set of
rule *signatures* (name + arg lists), each optionally carrying a **default implementation** (a
rule declared with a body). Another project can `import(thatNpl)` and a contract can
`implement("name") { … }`, supplying bodies for the interface's rules; a rule left
unimplemented that has **no default** fails the compile with an `NplException`. Rules with the
same arg *types* may coexist in one face as long as their arg *names* differ.

**Compiling and reading bytecode:**

- `NPL.compile()` — compiles every group/contract/interface in the project.
- `Contract(val name: String?, val interfaces: List<Interface>)` — `.interfaces[0]` is the
  usual single interface; `Contract.compiled` (alias for the template script) and
  `Contract.face(name)` / `Contract.findRule(name)` are also available.
- `Interface.compile(onlyIfNeeded = false): SatoshiScript`, `Interface.compiled`,
  `Interface.contractId` (first 8 bytes of `hashId`), `Interface.rule(name)`.
- The template hash is `iface.compiled!!.scriptHash160()`; the bytecode hex is
  `iface.compiled!!.toHex()`. (See `SKILL.md` Pattern 2.)

**Token / group genesis (npl.kt) —** these belong primarily to `nexa-tokens-and-groups`; named
here because they live in this library:

- `GroupDescriptor { ticker; name; docUri; doc; decimals }` with `buildGenesisData(): SatoshiScript`
  (the genesis `OP_RETURN`) and `buildTokenDescriptionDoc(wallet, genesisAddr, outputFile)`.
- `Wallet.createGroup(groupFlags, genesisAuthorityFlags, opRet?, genesisAddress?): Triple<iTransaction, PayAddress, GroupId>`,
  `Wallet.createSubgroup(...)`, `Wallet.createSubgroups(...)`.
- `GroupBuilder` (inside `group(name) { }`): `descriptor { }`, `authority(flags, …)`,
  `mint(qty, …)` (several overloads), `subgroup(name) { }`, `media(fileOrDir) { }`.
- `Group.create(wallet)` / `Group.deploy(wallet)`, `NPL.deploy(wallet, …)`,
  `NPL.groupIdOf(name): GroupId`.

**Runtime init:** `initNpl()` = `loadCalcStackX()` + `initRefactor()` (minimal tier). For the
full scaffold and the cache/transition story see `SKILL.md` Pattern 2 and the
"How NPL compiles a rule" section.

---

## 2. Typed argument classes (nsltypes.kt, nsltypesint.kt)

Every contract value is one of these `NBinding` subclasses. A `name` ties the binding to a
declared arg; constants (the `NC*` forms) carry a literal value.

| Type | Represents | Notable members |
| --- | --- | --- |
| `NBytes(name?)` | byte string | `+` (concat), `size()`, `reverse()`, `toInt(dest?)`, hashing (§4), `or`/`xor`/`and`, `split(loc)`, `eq`/`neq`/`equalVerify`, `verify()` |
| `NCBytes(v: ByteArray, size?, name?)` | constant bytes | constant form of `NBytes` |
| `NInt(name?)` | signed script int (LE sign-magnitude) | arithmetic (§3), comparison (§3), bitwise (§3), `inc()`/`dec()`, `toBytes(size, dest?)`, `verify()` |
| `NCInt(v: Long, name?)` | constant int | constant form of `NInt` |
| `NUInt(name?)` / `NCUInt(v: ULong, name?)` | unsigned script int | same operator families as `NInt`/`NCInt` |
| `NBool(name?)` / `NCBool(v: Boolean, name?)` | boolean (derives from `NInt`) | `eq`/`neq`, `and`/`or`/`xor` |
| `NSig(name?)` | transaction/data signature | use as a `spenderArgs` element; consumed by `checkSigVerify`/`checkDataSigVerify` |
| `NPubKey(name?)` | public key | the key a signature is checked against |
| `NGroupId(name?)` / `NCGroupId(gid: GroupId)` | token group id | feeds the `countInputsByGroup`/`groupedOutputN`/`verifySameGroup` family |
| `NScript(name?, nsl?)` | script bytecode | `templateAndArgsHash(): Pair<NBytes,NBytes>`, `splitPush(): Pair<NBytes,NScript>` |
| `NAddress(name?)` / `NCAddress(address: PayDestination)` | address | `.argsHash: NBytes`, `.pubKey: NPubKey` |

### Constants — the `.nx` extension

`.nx` lifts a Kotlin literal into an NPL constant:

| Kotlin | `.nx` result |
| --- | --- |
| `Int` / `Long` | `NCInt` |
| `UInt` / `ULong` | `NCUInt` (`ULong.nxb` instead gives `NCBytes` of the 8 LE bytes) |
| `Boolean` | `NCBool` |
| `ByteArray` | `NCBytes` |
| `String` | `NCBytes` of the UTF-8 bytes (no null terminator) |

Index/field arguments to introspection functions are passed as `.nx` ints, e.g.
`getOutputArgsHash(0.nx)`, `getOutputVisibleArg(0.nx, 2.nx)`.

---

## 3. Operators

**Comparison** (return `NBool`): `eq`, `neq`. Numeric types (`NInt`/`NUInt`) additionally have
`lt`, `lte`, `gt`, `gte`. Each has overloads for the matching N-type and for raw Kotlin
`Int`/`Long`/`UInt`/`ULong` right-operands. `NBytes.eq`/`neq` compare bytewise;
`NBytes.equalVerify` / `NInt`-`NBinding.numEqualVerify` fail the script on mismatch (cheaper
than `verify(a eq b)` when you only need the assert).

**Arithmetic** on `NInt`/`NUInt` (return the same type): `+`, `-`, `*`, `/`, `%` (`plus`,
`minus`, `times`, `div`, `rem`), each overloaded for N-type and raw Kotlin right-operands;
plus `inc()`/`dec()`. `NBytes + NBytes` is concatenation. Helpers `min(a, b)` / `max(a, b)`
on `NInt` (in `nsl.kt`).

**Bitwise**: `or`, `xor`, `and` on `NInt`, `NUInt`, and `NBytes`. (`shr`/`shl` exist on the
int types but are currently disabled — do not rely on them.) `NBool` has logical
`and`/`or`/`xor`.

---

## 4. Hashing and signatures

**Hashing** (methods on `NBytes`, return `NBytes`): `hash160()` (= RIPEMD160(SHA256(x)),
20 bytes), `hash256()` (double SHA256), `sha256()`, `ripemd160()`, `sha1()`.

**Signatures** (methods in `NSL`):

- `checkSigVerify(sig: NSig, pubkey: NPubKey)` — verify a transaction signature; fails the
  script if invalid. Authorize a spend by *key*.
- `checkDataSigVerify(sig: NSig, msg: NBytes, pubkey: NPubKey)` — verify a signature over an
  **arbitrary message** (not the spending tx). The oracle primitive: an off-chain oracle
  signs a fact and the contract checks it against the oracle's committed `NPubKey`. Foundation
  of price-feed / bet-settlement / insurance contracts.

Idiom: declare the `NSig` in `spenderArgs` (the spender supplies it at spend time) and the
`NPubKey` as a visible arg or constant (the holder commits to which key/oracle is trusted).

---

## 5. Control flow

- `verify(b)` — overloaded for `NBytes` / `NBool` / `NInt`; fails the script if the value is
  zero/false/empty. `verifyFalse(b)` is the inverse. `fail()` aborts unconditionally.
- `if_(condition: NBinding, then: NSL.() -> Unit, else_: (NSL.() -> Unit)? = null): List<NBinding>`
  — conditional execution. The VM has **no loops**; unroll with Kotlin `for` at DSL-build time
  (see `forGroupedOutputs` in §8).
- `checkLockTimeVerify(locktime: NInt)` — CLTV; **also** requires the spending input's
  `nSequence < 0xFFFFFFFF`. See `nexa-locktime-cltv`.
- `checkSequenceVerify(seq: NInt)` — relative locktime (CSV).
- `result(vararg bindings)` — declare the rule's output bindings.

---

## 6. Data manipulation

- `split(data: NBytes, location: NInt): Pair<NBytes, NBytes>` — split at byte offset
  (OP.SPLIT). Typed variants: `splitInto(data, location, prefix, suffix)`,
  `splitPrefixInto(...)`, `splitSuffixInto(...)`.
- `splitInt(data, location, pfxName?, suffixName?): Pair<NInt, NBytes>` and
  `splitLeSignMagInt(...)` — split off a leading little-endian sign-magnitude integer.
- `NBytes.toInt(dest?)` (OP.BIN2NUM) ↔ `NInt.toBytes(size, dest?)` (OP.NUM2BIN).
- `NBytes.size()` (OP.SIZE), `NBytes.reverse()` (OP.REVERSE).

---

## 7. Transaction / output / prevout introspection (nsl.kt)

These let a rule read and constrain the *spending transaction* from inside the VM — the basis
of forced-settlement, atomic-swap, and covenant patterns. Indices are `NInt` (`.nx`).

**Counts and the running input:**

- `thisIndex(): NInt` — index of the input the running rule is spending (OP.INPUTINDEX).
- `inputCount(): NInt`, `outputCount(): NInt`.

**Per-input reads:**

- `inputUtxoHash(idx): NBytes` (OP.OUTPOINTTXHASH)
- `inputConstraintScript(idx): NScript` (OP.UTXOBYTECODE — the locking script of the prevout)
- `inputSatisfierScript(idx): NScript` (OP.INPUTBYTECODE)

**Per-output reads:**

- `outputValueN(idx): NInt` (OP.OUTPUTVALUE — native sats on the output)
- `constraintScriptForOutputN(idx): NScript` (OP.OUTPUTBYTECODE)

**Parsed output fields** (canonical OP_PARSE form; see §9 for the field numbering):

- `getOutputGroupId(idx)`, `getOutputGroupAmount(idx)`, `getOutputGroupData(idx): Pair<NBytes, NInt>`
- `getOutputContractId(idx)` (field 3, template hash), `getOutputArgsHash(idx)` (field 4)
- `getOutputVisibleArg(idx, argIdx): NBytes` — `argIdx` is **0-based** over the visible args
  (the +8 field offset is added for you); `getOutputVisibleArgAsInt(idx, argIdx): NInt`;
  `getOutputVisibleArgsAsInt(idx, argIdx, numArgs): List<NInt>` (reads several at once).
- Group authority (field 2) variants — all return `NBytes`, differing only in how they
  normalize the VM's sign-magnitude authority bytes for comparison:
  `getOutputGroupAuthorityCanonical`, `getOutputGroupAuthorityBits`,
  `getOutputGroupAuthority`, `getOutputGroupAuthorityBytes`,
  `getOutputGroupAuthorityManualParseBytecode`. Pick one and use it on **both** sides of any
  comparison. (Detail in `nexa-tokens-and-groups`.)

**Prevout (the UTXO being spent) reads:** `getPrevoutGroupId(inputIdx)`,
`getPrevoutGroupAmount(inputIdx)`, `getPrevoutContractId(inputIdx)`,
`getPrevoutArgsHash(inputIdx)`; and — added in a recent release — the individual visible-arg
reads `getPrevoutVisibleArg(inputIdx, argIdx): NBytes` / `getPrevoutVisibleArgAsInt(inputIdx,
argIdx): NInt` (0-based `argIdx`, the +8 field offset added for you — the prevout analogues of
`getOutputVisibleArg`/`getOutputVisibleArgAsInt`). Reading a *sibling* input's prevout args is
the basis of cross-input coordination (`SKILL.md` Patterns 9 and 11).

**Group introspection / state-threading:**

- `groupIdOf(name): NGroupId` — the id of a named group declared in the project.
- `countInputsByGroup(gid): NInt`, `countOutputsByGroup(gid): NInt`,
  `groupedOutputN(gid, n: NInt): NInt` (the output index of the n-th output bearing `gid`).
- `verifySameContract(outputIdx)` / `verifySameContract(inputIdx, outputIdx)` — assert the
  output continues the same template (field-3 contract id) as the spent input.
- `verifySameGroup(outputIdx)` / `verifySameGroup(inputIdx, outputIdx)` — same, for the
  field-0 group id. Independent of `verifySameContract`. (See `SKILL.md` Pattern 10.)

---

## 8. High-level helpers (nsllib.kt)

Composed over §7 for the recurring "where am I / where must value go" checks:

- `thisGroup(): NGroupId`, `thisTemplateHash(): NBytes`, `thisArgsHash(): NBytes`,
  `thisInputUtxoHash(): NBytes`, `thisTemplateAndArgsHash(): Pair<NBytes, NBytes>` — facts
  about the running input, no hardcoded index.
- `mustSpendGroupToP2pkt(gid, addr: NAddress)` and the
  `mustSpendGroupToP2pkt(gid, argsCheck: (NBytes) -> Unit)` overload — assert a group is paid
  to exactly one P2PKT output, optionally constraining its args hash.
  `mustSpendLowQuantityGroupToP2pkt(...)` is the efficient form when the group quantity is a
  2-byte push.
- `forGroupedOutputs(N: Int, gid, doit: (NInt) -> Unit)` — apply a check to each grouped
  output up to N; **unrolls** N times (no VM loops) and fails if there are more than N grouped
  outputs, so set N to the contract's real maximum.
- `groupsIn(vararg gids)` / `groupsOut(vararg gids)` — assert each group is present among the
  tx's inputs / outputs.

Reach for these before hand-rolling the equivalent `countOutputsByGroup` / `groupedOutputN` /
`constraintScriptForOutputN` sequence.

---

## 9. OP_PARSE helper families (opParseHelpers.kt + nsl.kt)

A spending output's canonical parsed form has fixed field numbers:

| Field | Meaning |
| --- | --- |
| 0 | groupId |
| 1 | groupAmount |
| 2 | group authority flags (sign-magnitude bytes) |
| 3 | template (contract) hash |
| 4 | args hash (all hidden + visible args, hashed) |
| 8, 9, 10, … | individual visible args (holderPublicArgs), in declaration order |

The last OP_PARSE selector picks the data source: **variant 0 = OUTPUT_DATA** (an output of
this tx), **variant 1 = PREVOUT_DATA** (the UTXO being spent at an input). Two accessor
families wrap these:

- **NSL members** (`getOutputVisibleArg`, `getOutputArgsHash`, `getOutputContractId`,
  `getPrevoutArgsHash`, `getPrevoutVisibleArg`/`getPrevoutVisibleArgAsInt`, …): the visible-arg
  index is **0-based** — the library adds the +8 field offset for you.
- **Top-level helpers** in `opParseHelpers.kt` — `parseOutputArgsHash(outputIdx)` (field 4,
  variant 0), `parsePrevoutArgsHash(inputIdx)` (field 4, variant 1),
  `parseOutputTemplateHash(outputIdx)` (field 3, variant 0),
  `parseOutputArg(outputIdx, fieldIdx)` and `parseOutputArgInt(outputIdx, fieldIdx)` — these
  take the **raw canonical field number**, so the first visible arg is field **8**, not 0.

Mixing the two index conventions is a classic bug; see the "Confusing the two OP_PARSE
accessor families' index bases" anti-pattern in `SKILL.md`.

---

## 10. Compilation internals (nslxlat.kt, dynstackx.kt)

You normally don't touch these, but you must when a contract fails to compile with
`Cannot find state transition` (full workflow in `SKILL.md` → "How NPL compiles a rule").

- `stackX: StateTransitions` — the global table mapping a `StateTransition` (`begin` ⇒ `end`
  stack `StateDescriptor`s) to the shortest opcode `Clause` that achieves it. Populated by
  `calcStackX()` (bounded BFS over short opcode sequences) plus the hand-registered
  `addDropRollPatterns` / `addSpecificTransitions` / `addWarriorContractTransitions`.
- `loadCalcStackX()` — load `stackX` from the `stackScripts.bin` cache, recomputing via
  `calcStackX()` only on a miss. Delete the cache to force a recompute after editing the
  transition functions.
- `StateTransitions.add(begin, end, vararg op)` registers a hand-derived transition;
  `StateTransitions.check(begin, end, vararg op)` (run when the `DoubleCheckTransitions` flag
  is set) executes the opcodes in the real script VM and asserts they produce `end`.
- `DynamicStackTransform { val name; fun tryGenerate(xSpec): Clause? }` — the interface for
  algorithmic generators; instances are registered in `DynamicStackTransformRegistry`, which
  validates each candidate and keeps the shortest. **`DynamicStackTransformRegistry.register(generator)`
  is public** — an application project can register its own transformer at startup (after the
  init scaffold, before `compile()`); no NPL source edit needed. A validated generated
  transition is cached back into `stackX`. The built-in roster includes targeted generators
  (`SimpleRoll`, `RotationTransform`, `RecursiveDecompositionTransform`, …) and, in a recent
  release, `StructuredDecompositionTransform` (bottom-segment→top rearrangements around a large
  untouched middle segment; supersedes the brute-force `UniversalStackTransform` for those shapes).
- `StackXformDiag` (`nslc.kt`) — gated compile-time instrumentation (abstract-stack high-water
  per rule, program-order vs weight-sorted pass depths); see `stateTransitions.md` for how to
  use it with the published test sources' `ScriptDiagnostics` harness.

The `StateDescriptor` printed in the error is `(mainStackLabels, altStackLabels)`; the end of
each list is the **top** of that stack, equal numbers mean equal values. That printout is
exactly what you paste into a new `stackX.add(...)` (or upstream `addSpecificTransitions`)
entry as the `begin`/`end`. Position labels are `Int`s (deep stacks >255 items compile, since a
recent release); the `SD(ByteArray, ByteArray)` convenience constructor reads bytes as
*unsigned* labels (0–255) — use the `StateDescriptor(IntArray, IntArray)` form beyond that.