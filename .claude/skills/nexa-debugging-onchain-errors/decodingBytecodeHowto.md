# Decoding bytecode to debug an on-chain failure

When a contract spend is rejected on-chain (e.g. `mandatory-script-verify-flag-failed` /
`OP_EQUALVERIFY`) and you need to see *which* instruction failed, the workflow is: get the scripts,
disassemble them, map the opcodes back to the contract logic, and replay through the script VM.
This is the debugging-skill view; the detailed lookup tables live in the NPL and scriptmachine
skills — this file is the procedure that ties them together.

## 1. Get the two transactions

You need the **funding (parent)** tx and the **spending (child)** tx as hex. Fetch them from
whatever source you have:

- a node you operate: `rpc.getrawtransaction(txid)` (`nexa-rpc-node-client`),
- a light client: `electrum.getTx(txid)` (`nexa-electrum-monitoring`),
- or straight from the callback/log that carried the failing tx.

## 2. Disassemble the scripts

A compiled script is a `SatoshiScript`; print its assembly rather than reading raw hex:

```kotlin
println(script.toAsm(" "))     // opcode mnemonics + push data
```

To split a spending input into its three template-spend scripts (satisfier / constraint /
template), use `parseTemplateSpend` (`nexa-script-machine-testing` Pattern 7), or just let the two-tx
VM constructor parse them for you (next step).

## 3. Map opcodes back to the contract

Recognize the constructs in the ASM using the forward and reverse maps:

- **NPL DSL → opcodes:** `nexa-npl-smart-contracts/opcodesDecoded.md` (e.g. `hash160()` → `OP_HASH160`,
  `equalVerify` → `OP_EQUALVERIFY`, the `OP_PARSE` field-number tells, `OP_PUSH_TX_STATE` group
  queries).
- **Full DSL surface / OP_PARSE field numbers:** `nexa-npl-smart-contracts/dslReference.md` §7/§9.
- A bare `OP_EQUALVERIFY` that fails is almost always a mismatch between the two values just pushed
  — e.g. a revealed preimage hashed differently than the committed hash, or visible args in the
  wrong order. A failed `OP_CHECKLOCKTIMEVERIFY` is a finality issue (`nexa-locktime-cltv`).

## 4. Replay through the script VM to see the exact failure

This is the decisive step — the VM runs the **same** consensus engine as the node, so it reproduces
the on-chain result and tells you the exact failing opcode + stack:

```kotlin
Initialize()
val sm = ScriptMachine(parentTxHex, childTxHex)   // auto-detects the spend; parses the 3 scripts
sm.next(false)
while (sm.step()) { /* optionally print sm.getState() each step */ }
println("result: ${sm.scriptErr}")                 // contains "failed" on a verify failure
println("stack at failure: ${sm.getState()}")
sm.delete()
```

See `nexa-script-machine-testing` (Pattern 3/5 and `opcodeStepThrough.md`) for stepping, breakpoints,
and reading the stack-item strings (`stackItemFormat.md`). Remember the VM checks **script
validity** only — it does **not** model fees, mempool policy, or chain MTP, so a clean VM run does
not explain a `REJECT_INSUFFICIENTFEE` or a not-yet-final CLTV spend (`errorCodeReference.md`,
`nexa-locktime-cltv`).

## When you only have the locking script (no spend yet)

If you just want to confirm a funded contract output is the template you expect, disassemble its
locking script and read the template fields — `parseTemplate(0)` recovers the template hash, args
hash, and visible args (`nexa-identity-and-addresses/addressTypesTable.md`). Compare the template hash
against the one `compileAndPrintTemplate.kt` printed for your contract.

## Related

- `nexa-script-machine-testing` — replay/step a spend; `opcodeStepThrough.md`, `stackItemFormat.md`.
- `nexa-npl-smart-contracts` — `opcodesDecoded.md` (opcode map), `dslReference.md` (OP_PARSE fields).
- `errorCodeReference.md` — what the on-chain rejection code/message means.
- `SKILL.md` — the symptom→cause triage table that routes you here.