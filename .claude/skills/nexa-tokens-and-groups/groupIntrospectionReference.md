# Group introspection reference (NSL group accessors)

The full `org.nexa.npl` NSL surface for reading and constraining a transaction's **group**
(token) fields from inside a contract rule. This is the group-specific companion to
`nexa-npl-smart-contracts/dslReference.md` §7/§9 — it lists every group accessor with its exact
signature and the canonical OP_PARSE field it reads. Read the parent `SKILL.md` for the token
mental model (the amount lives in the script, authority outputs, fenced/covenanted groups) first.

Signatures are grounded in the NPL library source (`nsl.kt`). When an exact signature matters,
the declaration there is authoritative over this summary.

## The canonical parsed-output field numbers

Every output (and prevout) has a fixed-field canonical form that OP_PARSE indexes:

| Field | Meaning |
| --- | --- |
| 0 | groupId |
| 1 | groupAmount |
| 2 | group authority flags (sign-magnitude bytes — see "Authority variants" below) |
| 3 | template (contract) hash |
| 4 | args hash |
| 8, 9, … | individual visible args (holderPublicArgs), in declaration order |

The OP_PARSE selector's last argument picks the data **source**: `0` = OUTPUT_DATA (an output of
this tx), `1` = PREVOUT_DATA (the UTXO being spent at an input). The accessors below bake in the
right field number and source for you.

## Reading a group id / amount

All indices are `NInt` (pass a literal with `.nx`, e.g. `0.nx`).

| Accessor | Reads | Returns |
| --- | --- | --- |
| `getOutputGroupId(outputIdx)` | output field 0 | `NBytes` (the 32-byte group id, or empty for an ungrouped output) |
| `getOutputGroupAmount(outputIdx)` | output field 1 | `NInt` (token quantity) |
| `getOutputGroupData(outputIdx)` | output fields 0+1 in one parse | `Pair<NBytes, NInt>` = (groupId, groupAmount) |
| `getPrevoutGroupId(inputIdx)` | prevout field 0 | `NBytes` |
| `getPrevoutGroupAmount(inputIdx)` | prevout field 1 | `NInt` |

`getOutputGroupData` is the efficient choice when you need both the id and the amount of the same
output — it parses fields 0–1 together rather than running two separate parses.

## Authority flags (field 2) — five variants, pick one and be consistent

The authority bits live in field 2, but the Nexa VM encodes them as a **sign-magnitude** number,
and an authority output's flags always read as a *negative* 8-byte integer. So "the authority
flags" can be surfaced in several byte shapes; the five accessors differ **only** in how they
normalize those bytes for comparison. They all take `(outputIdx: NInt)` and return `NBytes`:

| Variant | What it produces |
| --- | --- |
| `getOutputGroupAuthorityCanonical(outputIdx)` | the raw field-2 parse (canonical sign-magnitude bytes) |
| `getOutputGroupAuthority(outputIdx)` | parses field 2 directly off the output bytecode (`OUTPUTBYTECODE`) |
| `getOutputGroupAuthorityBytes(outputIdx)` | the field-2 value normalized to a fixed **8-byte** form (`NUM2BIN 8`) |
| `getOutputGroupAuthorityBits(outputIdx)` | the flags reduced to a comparable **bit array** (reverses, masks the sign bit, inverts) — use when you want to test individual capability bits |
| `getOutputGroupAuthorityManualParseBytecode(outputIdx)` | the same field-2 bytes extracted by manual byte-slicing of the output script rather than `OP_PARSE` |

> **Rule: use the same variant on both sides of any comparison.** Mixing (e.g. comparing a
> `Canonical` result against a `Bytes` result) compares different byte encodings of the same flags
> and will mismatch. Pick one variant for a given contract and use it everywhere you read or
> compare authority flags.

The flag bit meanings themselves (MINT / MELT / BATON / RESCRIPT / SUBGROUP / …) are
`GroupAuthorityFlags` in libnexakotlin — see `SKILL.md` "The authority flags".

## Contract/template and args hashes (for covenant threading)

These aren't group-specific but are how a token covenant keeps the same template/group across a
UTXO chain:

| Accessor | Reads |
| --- | --- |
| `getOutputContractId(outputIdx)` | output field 3 (template hash) |
| `getOutputArgsHash(outputIdx)` | output field 4 (args hash) |
| `getPrevoutContractId(inputIdx)` | prevout field 3 |
| `getPrevoutArgsHash(inputIdx)` | prevout field 4 |

## Counting and locating grouped inputs/outputs

These use the VM's `PUSH_TX_STATE` group queries, not OP_PARSE:

| Accessor | Returns |
| --- | --- |
| `countInputsByGroup(gid: NGroupId)` | `NInt` — how many inputs of `gid` the tx spends (`GROUP_INCOMING_COUNT`) |
| `countOutputsByGroup(gid: NGroupId)` | `NInt` — how many outputs of `gid` the tx creates (`GROUP_OUTGOING_COUNT`) |
| `groupedOutputN(gid: NGroupId, n: NInt)` | `NInt` — the **output index** of the n-th (0-based) output bearing `gid` (`GROUP_NTH_OUTPUT`); feed it into the field accessors above |

Typical use: `groupedOutputN(gid, 0.nx)` to find the first output of a group, then
`getOutputArgsHash(that)` / `outputValueN(that)` to constrain where the token went and how much.

## Same-group / same-contract assertions (state threading)

These wrap the reads above into a single covenant check. From `nsl.kt`, their bodies are exactly:

| Helper | Equivalent to |
| --- | --- |
| `verifySameGroup(outputIdx)` | `verify(getPrevoutGroupId(thisIndex()) eq getOutputGroupId(outputIdx))` — the output continues **this** input's group |
| `verifySameGroup(inputIdx, outputIdx)` | `verify(getPrevoutGroupId(inputIdx) eq getOutputGroupId(outputIdx))` — across an explicit input |
| `verifySameContract(outputIdx)` | `verify(getPrevoutContractId(thisIndex()) eq getOutputContractId(outputIdx))` — the output continues **this** input's template |
| `verifySameContract(inputIdx, outputIdx)` | same, across an explicit input |

`verifySameGroup` compares field-0 group ids; `verifySameContract` compares field-3 template
hashes. They are **independent** — a covenant that must preserve both the group *and* the contract
calls both (see `nexa-npl-smart-contracts` Pattern 10).

## High-level helpers (prefer these to hand-rolling)

From `nsllib.kt`, composed over the accessors above — reach for them before writing a raw
`countOutputsByGroup` / `groupedOutputN` / `getOutputArgsHash` sequence:

- `thisGroup(): NGroupId` — this input's group, no hardcoded index.
- `groupsIn(vararg gids)` / `groupsOut(vararg gids)` — assert each group is present among the tx's
  inputs / outputs.
- `mustSpendGroupToP2pkt(gid, addr)` / `mustSpendGroupToP2pkt(gid) { argsHash -> … }` — assert a
  group is paid to exactly one P2PKT output (optionally constraining its args hash).
  `mustSpendLowQuantityGroupToP2pkt(...)` is the efficient form for a 2-byte quantity.
- `forGroupedOutputs(N: Int, gid) { outIdx -> … }` — apply a check to each grouped output up to N.
  It **unrolls** N times (the VM has no loops) and fails if there are more than N grouped outputs,
  so set N to the contract's real maximum.

## Related

- `nexa-npl-smart-contracts/dslReference.md` — the full DSL surface; §7 (introspection) and §9
  (OP_PARSE field numbering + the two accessor index conventions) are the broader context for this
  table.
- `SKILL.md` (this folder) — token mental model, `GroupId`/`GroupInfo`/`GroupAuthorityFlags`,
  building token outputs, and the same-group covenant pattern.