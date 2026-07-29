# Decoding compiled NPL bytecode against the DSL

When you have a compiled template (the bytecode `compileAndPrintTemplate.kt` prints, or a script
pulled off-chain) and want to read it back to the DSL constructs that produced it, this is the
map. It is **partial** — a recognition guide for the common constructs, not an exhaustive
byte-for-byte spec. The compiler is free to choose opcode sequences (and the state-transition
machinery may insert stack-shuffling ops), so treat this as "what each construct *emits at its
core*," and use it to recognize the shape in the disassembly.

Opcode names below are `org.nexa.libnexakotlin.OP` members and are grounded in the NPL source
(`nsl.kt`, `nsltypes.kt`); when in doubt the `exec(...)` call in those files is authoritative.

## Step 1 — disassemble to ASM

Don't read raw hex. A compiled rule is a `SatoshiScript`; print its assembly:

```kotlin
val script = iface.compiled!!          // from compileAndPrintTemplate.kt
println(script.toAsm(" "))             // space-separated opcode mnemonics + push data
```

`toAsm(" ")` is the same call the script-VM patterns use to inspect a parsed template/constraint/
satisfier (`nexa-script-machine-testing` Pattern 3). Pushes appear as their hex bytes; opcodes as
their mnemonics (`OP_HASH160`, `OP_PARSE`, `OP_CHECKLOCKTIMEVERIFY`, …). To watch the script
*execute* and see the stack between opcodes, replay it through the VM and step
(`nexa-script-machine-testing/opcodeStepThrough.md`).

## Step 2 — recognize the constructs

### Hashing (`NBytes` methods)

| DSL | Emits |
| --- | --- |
| `x.hash160()` | `OP_HASH160` |
| `x.hash256()` | `OP_HASH256` |
| `x.sha256()` | `OP_SHA256` |
| `x.ripemd160()` | `OP_RIPEMD160` |

### Comparison and assertion

| DSL | Emits (core) |
| --- | --- |
| `a eq b` | `OP_EQUAL` (leaves a boolean) |
| `a neq b` | `OP_EQUAL OP_NOT` |
| `a equalVerify b` | `OP_EQUALVERIFY` (asserts, leaves nothing) |
| `verify(boolExpr)` | a `…OP_VERIFY` tail; `verify(NInt)` compiles as `OP_NOTEQUAL0 OP_NOT OP_VERIFY` |
| `verifyFalse(x)` | `OP_PUSHFALSE OP_VERIFY` pattern |

Note the cost difference: `a equalVerify b` is one opcode (`OP_EQUALVERIFY`); `verify(a eq b)` is
`OP_EQUAL` then a verify — so seeing a bare `OP_EQUALVERIFY` in the ASM usually means the source
used the `equalVerify` form.

### Signatures and locktime

| DSL | Emits |
| --- | --- |
| `checkSigVerify(sig, pubkey)` | `OP_CHECKSIGVERIFY` |
| `checkDataSigVerify(sig, msg, pubkey)` | `OP_CHECKDATASIGVERIFY` |
| `checkLockTimeVerify(t)` | `OP_CHECKLOCKTIMEVERIFY` (the locktime value is left on the stack) |
| `checkSequenceVerify(s)` | `OP_CHECKSEQUENCEVERIFY` |

`OP_CHECKLOCKTIMEVERIFY` in a rule is the tell-tale of a timeout/refund path — and a reminder the
spending input must set `nSequence < 0xFFFFFFFF` (see `nexa-locktime-cltv`).

### Data ops

| DSL | Emits |
| --- | --- |
| `split(data, loc)` | `OP_SPLIT` |
| `x.size()` | `OP_SIZE` |
| `x.reverse()` | `OP_REVERSE` |
| `x.toInt()` | `OP_BIN2NUM` |
| `n.toBytes(size)` | `OP_NUM2BIN` |
| `a + b` (`NBytes`) | `OP_CAT` |
| `a or/xor/and b` | `OP_OR` / `OP_XOR` / `OP_AND` |

### Introspection — the `OP_PARSE` family

The introspection accessors compile to `OP_PARSE` (often preceded by `OP_OUTPUTBYTECODE` /
`OP_UTXOBYTECODE` and followed by a small fix-up). The four values pushed before `OP_PARSE` are
`(index, fieldStart, fieldCount, source)` where `source` is `0` for an output of this tx and `1`
for a prevout. So in the ASM, **the constant before `OP_PARSE` tells you the field**:

| DSL | Core emission | Field |
| --- | --- | --- |
| `getOutputGroupId(i)` / `getOutputGroupData(i)` | `… 0 … OP_PARSE` (`getOutputGroupData` adds `OP_BIN2NUM`) | 0 (+1) |
| `getOutputGroupAmount(i)` | `… 1 … OP_PARSE` | 1 |
| `getOutputGroupAuthority*(i)` | `… 2 … OP_PARSE` (+ variant-specific normalization) | 2 |
| `getOutputContractId(i)` | `… 3 … OP_PARSE` | 3 |
| `getOutputArgsHash(i)` | `… 4 … OP_PARSE` | 4 |
| `getOutputVisibleArg(i, a)` | field `a + 8` → `… OP_PARSE` | 8+ |
| prevout variants (`getPrevout*`) | identical, but `source = 1` | same fields |

The group **count/locate** helpers compile to `OP_PUSH_TX_STATE` instead of `OP_PARSE`:

| DSL | Core emission |
| --- | --- |
| `countInputsByGroup(gid)` | `… OP_CAT OP_PUSH_TX_STATE` (state code `GROUP_INCOMING_COUNT`) |
| `countOutputsByGroup(gid)` | `… OP_CAT OP_PUSH_TX_STATE` (`GROUP_OUTGOING_COUNT`) |
| `groupedOutputN(gid, n)` | `OP_C2 OP_NUM2BIN OP_SWAP OP_CAT OP_CAT OP_PUSH_TX_STATE` (`GROUP_NTH_OUTPUT`) |
| `outputValueN(i)` | `OP_OUTPUTVALUE` |

(So a run of `…OP_PUSH_TX_STATE` is a group-count/locate query, while `…OP_PARSE` is a field read.)

## Step 3 — what you *won't* be able to read back cleanly

- **Stack shuffling.** The compiler inserts `OP_PICK`/`OP_ROLL`/`OP_DUP`/`OP_DROP`/`OP_SWAP`
  sequences to arrange operands; these come from the state-transition table
  (`addSpecificTransitions` / `calcStackX`), not from a single DSL call, so don't try to map every
  one to source. They're plumbing between the meaningful ops above.
- **Arg layout.** Hidden/visible/spender args become pushes and `args-hash` checks; the
  *positions* are determined by the compile, so identify args by the operations performed on them,
  not by absolute stack slot (the same lesson as the "look up an output by index alone"
  anti-pattern in `nexa-transaction-construction`).
- **Exact constant encodings.** Numbers are LE sign-magnitude; a small constant may appear as
  `OP_C0..OP_C16` rather than a push. See `nexa-script-machine-testing/stackItemFormat.md` for how the
  VM renders these.

## Related

- `compileAndPrintTemplate.kt` — produces the bytecode/ASM you decode here.
- `dslReference.md` — the forward direction: the full DSL surface and the OP_PARSE field table.
- `nexa-script-machine-testing/opcodeStepThrough.md` — step the disassembly through the VM to see the
  stack effect of each opcode (the surest way to confirm what a sequence does).