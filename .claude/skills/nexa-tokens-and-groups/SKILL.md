---
name: nexa-tokens-and-groups
description: "Works with Nexa native tokens (called groups) at any layer: token ids, building outputs that carry token amounts, reading token balances off a transaction, or writing NPL contracts that inspect or constrain an output's group. Use when issuing or transferring a token, minting an NFT (including mint-on-demand), splitting a token balance in a contract, building token vaults/covenants, checking mint authorities, showing which tokens a wallet owns, or displaying an NFT's artwork/media. Triggers: group, GroupId, ofToken, GroupInfo, GroupAuthorityFlags, MINT/MELT/BATON, getOutputGroupId/getOutputGroupAmount, verifySameGroup, groupedConstraint, subgroupData, nativeCoinGroupId, PayAddressType.GROUP, the TDPP /assets flow, AssetManager/AssetInfo/getNftFile/OwnedAssetInfo, NFT zip/cardf/NexaNFTv2. See nexa-npl-smart-contracts for contract authoring and nexa-transaction-construction for sending."
---

# Nexa native tokens and groups (GROUP tokenization)

## When to use this skill

Trigger when a developer is working with Nexa's **native tokens** — Nexa calls them
**groups** — at any layer: representing a token id, building an output that carries a
token amount, reading a token balance off a transaction, or writing an NPL contract that
inspects or constrains the group of an output it touches. Concretely trigger on:

- Keywords: group, groupId, `GroupId`, native token, NEXA token, fungible token, NFT/SFT
  on Nexa, `ofGroup`, `ofToken`, `GroupInfo`, `GroupAuthorityFlags`, `MINT`, `MELT`,
  `BATON`, `RESCRIPT`, `SUBGROUP`, authority output, `getOutputGroupId`,
  `getOutputGroupAmount`, `getPrevoutGroupId`, `verifySameGroup`, `countInputsByGroup`,
  `countOutputsByGroup`, `groupedOutputN`, `groupedConstraint`, `groupedLockingScript`,
  `mint(...)`, token genesis, subgroup, `subgroupData`, content-addressed NFT,
  `USE_GROUP_AUTHORITIES`, `NO_BATON_AUTHORITIES`, `MUST_MINT`, mint authority pool, mint-on-demand,
  reuse an authority without consuming it / BATON via read-only input,
  `SatoshiScript.grouped`, `nativeCoinGroupId`, `isFenced`, `isCovenanted`, covenanted group,
  `getTokenAmountOrAuthority`, `getTokenInfo`, `TokenDesc`, "token name/icon shows empty for an
  NFT" / subgroup metadata / `parentGroup()`, `chunkTokenInto`,
  `PayAddressType.GROUP`, token covenant, "amount of group", `AssetManager`, `AssetInfo`,
  `getNftFile`, `track`/`load`, `loadCardFile`, `OwnedAssetInfo`, `walletOwnsAssetHandler`,
  `NexaNFTv2`, `makeNftyZip`, `cardf`/card-front, "NFT artwork/image/media".
- Tasks: "issue a token on Nexa", "transfer a token", "write a contract that only lets a
  token move to the same group", "mint an NFT", "mint an NFT only when the buyer pays",
  "split a token balance in a contract",
  "read how many tokens an output holds", "what address do I send a token to", "make a
  token vault / covenant", "check an output's mint authority", "show which NFTs/tokens a
  connected wallet owns", "verify wallet asset ownership", "the TDPP `/assets` flow",
  "token-description document / decimals / ticker metadata", "display the NFT's own
  artwork (not just the collection icon)", "serve NFT media from my server".

**Negative triggers** — do NOT use this skill for:
- Plain native-NEXA payments with no token involved — use `nexa-transaction-construction`.
- The P2PKT-vs-P2PKH **identity** split (a different "two address types" concept) — use
  `nexa-identity-and-addresses`. (That skill is about *who*; this one is about *which token*.)
- General NPL contract structure (rules, satisfiers, visible args) — use
  `nexa-npl-smart-contracts`. This skill covers only the *group/token* slice of that DSL.
- Time-locked token releases (the CLTV mechanics are the same regardless of token) — use
  `nexa-locktime-cltv`.

## Mental model

Nexa has **native tokenization built into the UTXO/script layer** — there is no token
*contract* in the Ethereum sense and no separate token ledger. A token is a **group**: a
group id (a byte string, minimum 32 bytes) that an output's locking script declares it
carries, together with a token quantity. Spending and consensus enforce token
conservation per group the same way they enforce native-coin conservation.

The single most important structural fact:

> **A token amount is NOT the output's `amount` field.** `NexaTxOutput.amount` is always
> the native NEXA (satoshi) value. The *token* group id and *token* quantity live **inside
> the output's locking script**, decoded via `script.groupInfo(amount)` into a `GroupInfo`.
> Every grouped output therefore carries *both* a tiny native-NEXA value (typically the
> dust minimum, to satisfy native-coin rules) *and* a token quantity.

On the wire this is literal: a grouped TEMPLATE locking script **begins** with a push of the
group id followed by a 2-, 4-, or 8-byte little-endian quantity push, then the template-hash
and args fields; an ungrouped output starts with `OP_0` instead (which is why the group can
never hide inside conditional code — it must come first). Worked byte-level breakdowns of a
grouped vs ungrouped locking script are in the spec's script-template examples
(`https://spec.nexa.org/addresses/scriptTemplates/`, "How Tokens are Stored Inside a UTXO") —
useful when eyeballing raw hex.

Native NEXA itself is, conceptually, just the **zero group** — a `GroupId` of 32 zero
bytes (`nativeCoinGroupId(chain)`). But in practice an ordinary native-NEXA output carries
**no group at all**: `script.groupInfo(amount)` returns `null` for an ungrouped output.
Treat "native NEXA" as "no group on the script," and reserve the zero-group id for the
rare API that wants an explicit native-coin group handle.

A group can have **authority outputs**. An authority output carries, instead of a positive
token quantity, a set of **authority flags** (`MINT`, `MELT`, `BATON`, `RESCRIPT`,
`SUBGROUP`) that grant the power to create tokens, destroy tokens, delegate authority to a
child output, etc. Authority is encoded as a *negative* sign-magnitude integer in the
group-quantity slot, which is why the in-script accessors return it as raw bytes, not a
number (see Pattern 5).

Two more facts that catch people:

- **Token decimals are off-chain.** The on-chain token quantity is an integer in the
  token's smallest unit. How many decimal places to *display* is carried in the token's
  genesis/description metadata (`TokenGenesisInfo.decimal_places`), not enforced by
  consensus. Native NEXA's own 2-decimal convention (1 NEXA = 100 sat, see
  `nexa-transaction-construction`) is just the special case of this for the coin.
- **A group address is not a payable address.** `PayAddressType.GROUP` *denotes a token
  type*; you cannot pay to it. You pay tokens to an ordinary `TEMPLATE`/`P2PKT` address and
  attach the group + quantity to that address's locking script (see Pattern 2).

## Setup and versions

Native tokens span three libraries; pin exact versions per `nexa-project-setup`. Verify the
group/authority accessor signatures against the resolved jar before relying on them — see
`nexa-project-setup` § "Verifying API signatures before relying on them".

- **`libnexakotlin`** — the on-chain/server-side group types: `GroupId`, `GroupInfo`,
  `GroupAuthorityFlags`, the `script.grouped(...)` / `script.groupInfo(...)` helpers, and
  the `ofGroup` / `ofToken` / `payTo` simpleapi extensions.
- **`npl`** — the contract-side group introspection (`getOutputGroupId`, `verifySameGroup`,
  …) used inside a `script { }` rule body, plus the genesis/definition DSL (`group("…") {
  mint(...) }`).
- **`org.nexa.libnexaapp:server`** — the app-level asset layer (Patterns 8/8b): the built-in
  `/assets` proof verification, `OwnedAssetInfo`, and the `org.nexa.assets` package
  (`AssetManager`, `AssetInfo`, the NFT zip tooling in `nftTools.kt`). Server/JVM only.

Server/test-side imports from libnexakotlin:

```kotlin
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import org.nexa.libnexakotlin.GroupAuthorityFlags
import org.nexa.libnexakotlin.nativeCoinGroupId
import org.nexa.libnexakotlin.simpleapi.ofGroup     // and ofToken, payTo
import org.nexa.libnexakotlin.simpleapi.ofToken
import org.nexa.libnexakotlin.simpleapi.payTo
```

NPL contract-side group introspection is exposed as member functions on the NSL receiver
inside a `script { }` block (no extra import beyond the standard NPL DSL imports in
`nexa-npl-smart-contracts`).

## Core patterns

### Pattern 1: Represent a group id

```kotlin
// From a hex string OR a "nexa:...group..." address string (the constructor accepts both):
val gid = GroupId("a1b2c3...")                       // hex
val gidFromAddr = GroupId("nexatest:tnq...")         // group address form

// From raw bytes on a known chain:
val gid2 = GroupId(DEFAULT_CHAIN, groupIdBytes)      // groupIdBytes is >= 32 bytes

// Render back to the canonical group-address string:
val asAddress = gid.toString()                       // libnexa.groupIdToAddr(...)

// Native NEXA as an explicit group handle (rarely needed; usually "native == no group"):
val nativeGid = nativeCoinGroupId(DEFAULT_CHAIN)     // 32 zero bytes
```

`GroupId` is a `data class(blockchain: ChainSelector, data: ByteArray)`, but it **hand-overrides
`equals`/`hashCode` to compare `data` by `contentEquals`** — so `gid == other` is safe here,
unlike a naïve `ByteArray`-holding data class (whose auto-generated `equals` would compare the
array by reference). Use `==` (or `gid.data.contentEquals(other.data)` directly); **never**
`===`. Subgroups are supported (`gid.isSubgroup()`, `gid.parentGroup()`,
`gid.subgroup(childBytes)`, `gid.subgroupData()`), which is how NFTs/SFTs are minted as
children of a parent group.

A common and robust way to give each NFT a **deterministic, content-addressed** group id is
to subgroup the parent by a hash of the asset itself:

```kotlin
// nftGroup is reproducible from the file alone — no DB needed to recover it:
val nftGroup = parentGroupId.subgroup(hash256(assetBytes))     // libnexakotlin's hash256
// later, recover the committed payload (here, the asset hash) from the subgroup id:
val committed = nftGroup.subgroupData()                        // == hash256(assetBytes)
```

This is exactly what the Nexa NFT spec mandates for a content-defined NFT: *"the subgroup
identifier MUST be the double SHA256 of the NFT .zip file"* (`https://spec.nexa.org/tokens/nft/`),
and libnexakotlin's `hash256` is that double SHA256 — committing the file's double-SHA256 into the
group id is what lets a holder later prove a particular data file *is* this NFT (re-hash the file,
compare to `subgroupData()`). This means two uploads of the same bytes collide on the same group id
(natural dedupe), and the group id ↔ asset binding survives a lost database. A subgroup id is recognizable on chain:
`gid.isSubgroup()` is true and `gi.isSubgroup()` is exposed on a read-back `GroupInfo`. A
practical "is this a real NFT (vs an authority or a system group)?" predicate combines three
library checks — non-authority, subgroup, and not fenced:

```kotlin
fun GroupInfo?.looksLikeNft(): Boolean =
    this != null && !isAuthority() && isSubgroup() && !groupId.isFenced()
```

**What `isFenced()` / `isCovenanted()` actually test.** Both are flag bits baked into the **last
byte of the 32-byte base group id** (so they travel with the id and survive serialization), read
directly off `GroupId` without any chain lookup:

- `gid.isFenced()` — the group holds **native crypto rather than tokens** (bit 1 of that byte).
  This is exactly why the NFT predicate above and the `/assets` enumeration in Pattern 8 *skip*
  fenced groups: they are not ordinary token groups. (`isHoldingNative()` is the deprecated alias
  for the same bit — prefer `isFenced()`.)
- `gid.isCovenanted()` — the group is **covenanted**: consensus requires every grouped output's
  locking script to **equal the input script** it was spent from (bit 0 of that byte). This is a
  *native* covenant primitive — the group id itself forces script continuity, independent of any
  NPL contract template. If you read back a `GroupInfo` whose `groupId.isCovenanted()` is true,
  every spend must re-lock under the same script; don't design a transfer that changes the output
  script, or consensus rejects it.

### Pattern 2: Build an output that carries a token amount

The simpleapi `ofGroup`/`ofToken` extensions pair a quantity with a group id, and `payTo`
turns that pair into an output. `ofGroup` and `ofToken` are identical — pick whichever
reads better.

```kotlin
// 1000 token units of `gid`, sent to a P2PKT address (the output's native amount is set to
// the chain dust minimum automatically):
val out: iTxOutput = (1000L ofGroup gid) payTo recipientP2PKTAddress

// payTo also accepts a PayAddress, a PayDestination, or a raw SatoshiScript:
val out2 = (1000L ofToken gid) payTo PayAddress(recipientAddr)
val out3 = (1000L ofGroup gid) payTo contractLockingScript     // pay tokens INTO a contract
```

Under the hood this attaches the group to the locking script via
`SatoshiScript.grouped(chain, gid, tokenAmt)` and sets the native `amount` to dust. If you
are hand-building a script, the equivalents are:

```kotlin
// On a fresh template script:
val s = SatoshiScript.grouped(DEFAULT_CHAIN, gid, tokenAmt)        // companion form
// Attach a group to an existing P2PKT/TEMPLATE locking script:
val grouped = existingTemplateScript.grouped(gid, tokenAmt)
// Or from a PayAddress (TEMPLATE/P2PKT only):
val locking = PayAddress(recipientAddr).groupedLockingScript(gid, tokenAmt)
```

Key point reiterated: the token output still needs a native-NEXA value (dust) on
`out.amount`, and the *transaction* still needs a native-NEXA fee budget on top of the
token flows. Tokens and native coin are conserved independently in the same tx.

To actually *fund and sign* a token send from your own wallet, build the token output(s) as
above and let `CommonWallet.txCompleter(tx, minConfirms, TxCompletionFlags.FUND_GROUPS or
TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.SIGN)` pull in the matching token inputs
(adding token change if it over-pulls), fund the native side, and sign — see
`nexa-transaction-construction` § "Completing a transaction" for the full flag set and the
partial-tx (offer) idiom that `FUND_GROUPS` underpins. `USE_GROUP_AUTHORITIES` additionally lets
the completer spend an authority UTXO when one is needed.

### Pattern 3: Read the token (group) off a transaction output, server-side

```kotlin
fun readGroup(output: NexaTxOutput): GroupInfo? = output.script.groupInfo(output.amount)

// e.g. when an incoming-payment callback (see nexa-transaction-construction Pattern 4) fires:
val gi = readGroup(output)
if (gi == null) {
    // ungrouped: this is a plain native-NEXA receive of `output.amount` sat
} else if (gi.isAuthority()) {
    // an authority UTXO for gi.groupId — gi.authorityFlags tells you which powers
} else {
    // gi.tokenAmount units of token gi.groupId (in the token's smallest unit)
}
```

`GroupInfo` is `data class(groupId: GroupId, tokenAmount: Long, authorityFlags: ULong = 0)`.
`isAuthority()` is `(authorityFlags and GroupAuthorityFlags.AUTHORITY) != 0`. For an
authority UTXO, `tokenAmount` is not a token count — use `authorityFlags`. The helper
`gi.getTokenAmountOrAuthority(): Long` returns the unified on-chain quantity slot: the token
amount for an ordinary output, or the authority flags **as a negative value** for an authority
output (mirroring the sign-magnitude encoding consensus uses) — convenient when you need the one
field that distinguishes the two without branching first. `gi.isSubgroup()` delegates to
`groupId.isSubgroup()`.

### Pattern 4: Inspect an output's / prevout's group inside an NPL contract

These extend the OP_PARSE field table from `nexa-npl-smart-contracts` Pattern 9. The group
fields of an output's canonical parsed form are:

| Field | Meaning | NPL accessor (NSL member) |
| --- | --- | --- |
| 0 | groupId (raw bytes, not numeric); `OP_0` if the output is ungrouped (native NEXA) | `getOutputGroupId(outIdx)` / `getPrevoutGroupId(inIdx)` |
| 1 | groupAmount (BIN2NUM'd to a number for you). **Note the spec normalization: for an *ungrouped* or *fenced* output this field returns the output's native-NEXA amount, not 0** — so it is only a *token* quantity when field 0 is a real group id. An authority output's amount field is `OP_0`. | `getOutputGroupAmount(outIdx)` / `getPrevoutGroupAmount(inIdx)` |
| 2 | authority flags (raw bytes; sign-magnitude); `OP_0` when the output claims no authority or is ungrouped | `getOutputGroupAuthority(outIdx)` and variants |

The same OUTPUT_DATA(variant 0) / PREVOUT_DATA(variant 1) selector from `nexa-npl-smart-contracts`
Pattern 9 applies: `getOutput*` reads an output of the tx being built, `getPrevout*` reads the
UTXO being spent at an input. (Field numbering is the `OP_PARSE` canonical output form in the Nexa
spec: `https://spec.nexa.org/script/op-codes/op_parse/`. Because field 1 reports native value for
ungrouped/fenced outputs, always pair an amount check with a `getOutputGroupId(...) eq myGroup`
check — see the covenant anti-pattern below — so a spender can't satisfy a quantity check on an
ungrouped output.)

```kotlin
script {
    // "the output continues my group": bind output destIdx to the SAME group I'm spending
    val myGroup = getPrevoutGroupId(thisIndex())
    verify(getOutputGroupId(destIdx) eq myGroup)

    // read the numeric token quantity of an output (already BIN2NUM'd):
    val movedQty = getOutputGroupAmount(destIdx)
    verify(movedQty eq 1.nx)

    // convenience: both id and amount in one call
    val (gid, amt) = getOutputGroupData(destIdx)
}
```

Higher-level helpers for whole-tx group reasoning (full signatures in
`groupIntrospectionReference.md`): `verifySameGroup(outputIdx)` / `verifySameGroup(inputIdx,
outputIdx)` — the token-covenant building block, equivalent to the manual `getPrevoutGroupId …
eq getOutputGroupId …` above (its contract-template analogue `verifySameContract(...)` is in
`nexa-npl-smart-contracts`); `countInputsByGroup(gid)` / `countOutputsByGroup(gid)` — group
in/output counts; `groupedOutputN(gid, n)` — the output index of the n-th (0-based) output
carrying `gid`, fed into the field accessors above; `groupIdOf("name")` — resolve a named
genesis-DSL group to its runtime `NGroupId`.

These whole-tx group helpers compile to a **different on-chain opcode than the per-output
`getOutput*` reads above**. The field accessors use `OP_PARSE` (which extracts fields from *one*
output/prevout you name); the count/enumerate helpers use **`OP_PUSH_TX_STATE`** (`0xea`), which
the VM answers from *synthesized* whole-transaction state — `GROUP_INCOMING_COUNT` /
`GROUP_OUTGOING_COUNT` for the counts and `GROUP_NTH_INPUT` / `GROUP_NTH_OUTPUT` for "the index of
the n-th grouped in/output" (counts *include* authority in/outputs). Two consequences worth knowing:
the count specifiers return **0 for a group not present in the tx**, but `groupedOutputN`/`*NthInput`
**fail the script** if the requested n-th does not exist — so guard an enumerate with a count first.
(Spec: `https://spec.nexa.org/script/op-codes/op_push_tx_state/`.)

### Pattern 5: Authority flags

`GroupAuthorityFlags` (a libnexakotlin companion object) defines the bit constants:

```kotlin
GroupAuthorityFlags.AUTHORITY  // 0x8000000000000000UL — MUST be set for any authority
GroupAuthorityFlags.MINT       // 0x4000000000000000UL — create new tokens of this group
GroupAuthorityFlags.MELT       // 0x2000000000000000UL — destroy tokens of this group
GroupAuthorityFlags.BATON      // 0x1000000000000000UL — delegate authority to a child output
GroupAuthorityFlags.RESCRIPT   // 0x0800000000000000UL
GroupAuthorityFlags.SUBGROUP   // 0x0400000000000000UL — create subgroups (NFTs/SFTs)
GroupAuthorityFlags.ALL_AUTHORITIES   // all of the above OR'd together
```

`GroupAuthorityFlags.toString(bits)` renders set flags as a comma-separated string and
returns `""` when `AUTHORITY` is not set. Compose flags with `or`:

```kotlin
val mintAndMelt = GroupAuthorityFlags.AUTHORITY or
                  GroupAuthorityFlags.MINT or GroupAuthorityFlags.MELT
```

To actually *create* an authority output, reuse the same `groupedLockingScript(gid, x: Long)`
overload that builds a token output — it does **double duty**: pass a positive token
**quantity** for a normal token output, or pass the **authority-flags bitset** (`.toLong()`)
for an authority output. The negative sign-magnitude value the flags encode to is what tells
consensus "this is an authority, not a quantity":

```kotlin
// A spendable MINT+SUBGROUP authority output (dust native value, like any grouped output):
val authFlags = (GroupAuthorityFlags.AUTHORITY or GroupAuthorityFlags.SUBGROUP or
                 GroupAuthorityFlags.MINT).toLong()
val authScript = dest.groupedLockingScript(gid, authFlags)        // same call as a token output
val authOut = NexaTxOutput(chain, dust(chain), authScript)
```

In a contract, authority bits come back as **raw bytes** (field 2), because the Nexa VM
encodes integers in sign-magnitude and an authority is a negative 8-byte value — reading it
as a number is error-prone. Compare the bytes, or use the dedicated accessor variant that
extracts the bits for you:

```kotlin
script {
    val authBits = getOutputGroupAuthority(0.nx)   // raw authority bytes of output 0
    // (variants exist: getOutputGroupAuthorityCanonical / …Bits / …Bytes — pick per how you
    //  want the bytes shaped for comparison)
}
```

### Pattern 6: A token-transfer covenant (split-and-continue)

A common requirement: a UTXO holding `N` tokens may be spent only if exactly one unit moves
to a designated recipient and the remaining `N-1` units return to the original holder,
*and the group is preserved on both outputs*. This is the canonical "same-group covenant":

```kotlin
script {
    val myGroup  = getPrevoutGroupId(thisIndex())
    val myAmount = getPrevoutGroupAmount(thisIndex())

    // exactly 1 unit of this group goes to the peer:
    verify(getOutputGroupId(destIdx) eq myGroup)
    verify(parseOutputArgsHash(destIdx) eq peerUserHash)     // pays the right recipient
    verify(getOutputGroupAmount(destIdx) eq 1.nx)

    // remainder (only when myAmount > 1) returns to me, same group:
    if_(myAmount gt 1.nx, {
        verify(getOutputGroupId(restDestIdx) eq myGroup)
        verify(parseOutputArgsHash(restDestIdx) eq myUserHash)
        verify(getOutputGroupAmount(restDestIdx) eq (myAmount - 1.nx))
    })
}
```

The `getOutputGroupId(...) eq myGroup` checks are what stop a spender from "laundering" the
constraint onto a *different* (e.g. worthless) group while keeping the rest of the script
happy. Pair them with `parseOutputArgsHash` / `getOutputVisibleArg` recipient checks (see
`nexa-npl-smart-contracts` Pattern 9) so both *which token* and *to whom* are constrained.

### Pattern 7: Declaring a token group (genesis/definition DSL)

NPL's `Nexa("…") { … }` block can declare a **group** as a sibling of `contract(...)`. A
group block sets genesis flags, optionally attaches a controlling contract (`face { rule
{ … } }`), and can `mint(...)` initial supply, grant `authority(...)`, declare a
`subgroup(...)`, or attach NFT `media(...)`:

```kotlin
val token = Nexa("MyToken") {
    group("g1") {
        flags = DefaultGroupFlags u GroupFlag.COVENANT    // genesis flags
        face {
            // a rule whose script is the covenant every spend of this group must satisfy;
            // declare its visible/spender args exactly as for a contract rule (see
            // nexa-npl-smart-contracts for the rule/ruleWithPublicArgs arg conventions):
            rule(/* name, templateArgs, … */) {
                script { /* getPrevoutGroupId/getOutputGroupId checks, etc. */ }
            }
        }
        mint(1_000_000UL, issuerAddress)                  // initial supply to an address
        authority(GroupAuthorityFlags.AUTHORITY or GroupAuthorityFlags.MINT) { /* … */ }
    }
}
```

`mint(...)` has overloads taking a quantity plus an address (`String`/`PayAddress`) or a
nested `ContractBuilder` block; `authority(flags) { … }` declares an authority output the
genesis grants. The library turns this declaration into the actual genesis transaction and
group id — consult the `npl` source for the full genesis-construction surface if you need
to drive it directly. `Contract.groupedConstraint(gid, grpQty, vararg holderArgs)` produces
the grouped output (constraint) script for a contract-controlled group when you build txs by
hand.

**Token metadata.** A group's human-facing metadata (ticker, name, summary, icon, …) lives in an
off-chain **token-description document (TDD)** referenced from the genesis. `decimal_places` is the
exception: it is committed **on-chain** in the genesis OP_RETURN (not in the TDD), which is what
`TokenGenesisInfo.decimal_places` reflects (defaults to **0** if absent; support 0–18). Always
identify a token by its **group id**, never by ticker/name — tickers are not unique, and reserved
tickers (NEX, KEX, MEX, ISO-4217 / exchange symbols) should be refused. Genesis is typically
performed with the node's token tooling (`token new` / `rpc.tokenNew(...)`, see
`nexa-rpc-node-client`). The exact TDD JSON shape and its `signmessage` signature canonicalization,
the genesis OP_RETURN byte layout (the `88888888` token-description tag + field order; `88888889`
for an NFT data file), and the trailing-newline hashing trap are in `tokenMetadataReference.md` in
this folder.

**Resolving metadata for display — and the subgroup rule.** libnexakotlin ships a first-class
resolver, `getTokenInfo(grpId, getEc: () -> ElectrumClient, cnxnMgr: CnxnMgr?): TokenDesc`
(`token.kt`): it asks any connected P2P node that `supportsTokenInfo()` first, falls back to
electrum `getTokenGenesisInfo`, fetches the TDD from the genesis `document_url`, checks the TDD
signature against the genesis address (`TokenDesc.signedBy`), and returns a `TokenDesc` (the typed
TDD: `ticker`/`name`/`summary`/`icon`/`nftId`/`nftUrl`/… plus `genesisInfo`). Pass the SPV
connection's own electrum supplier — `{ bc.net.getElectrum() }` — rather than constructing a
standalone `ElectrumClient` for this; the already-connected node is the one known to serve token
info. Two rules that save real debugging time:

- **A subgroup (NFT) has no genesis of its own.** Its name/ticker/icon/decimals live on the
  **parent** group's genesis, so metadata lookups on a subgroup id resolve nothing — hop to
  `gid.parentGroup()` first (`getTokenGenesisInfo(subgroupId)` comes back empty for the same
  reason; see `nexa-electrum-monitoring`).
- **Verify the TDD against the chain before trusting it.** `TokenDesc.tddHash` is the SHA-256 of
  the served dictionary; compare it to the genesis `document_hash` commitment (this is the concrete
  mitigation for the "metadata is advisory and spoofable" warning below). Server apps on libnexaapp
  can use its higher-level `AssetManager.getTokenDesc(chain, groupId, getEc)` (in `org.nexa.assets`,
  the `:server` artifact), which does the parent hop, the hash verification, and disk caching in
  one call.

### Pattern 8: Proving wallet token ownership via the TDPP `/assets` flow

To show a connected user which tokens/NFTs they hold (a portfolio, a "list my NFT for sale"
screen), you ask the wallet to enumerate its owned grouped UTXOs and *prove* it controls each
one — the wallet, not your server, holds the keys. This is the TDPP `/assets` round trip (the
wallet-side callback shape is in `nexa-wallet-connection` § "What the wallet sends back on each
callback").

**Request.** Push `tdpp://<host>/assets?chain=<chain>&af=<filterHex>&chalby=<challengeHex>&cookie=<sessId>`
(or build it with `requestAssetsUri(...)`, see `nexa-wallet-connection`). Two parameters carry the
real intent:

- `af` is an **asset filter**: a script-template *pattern* the wallet matches owned outputs
  against. A pattern of two data placeholders matches **any grouped output** (i.e. "every token
  you hold"):

  ```kotlin
  val filter = SatoshiScript(chain, SatoshiScript.Type.TEMPLATE, OP.TMPL_DATA, OP.TMPL_DATA)
  val af = filter.toHex()
  ```

  Narrow the pattern (constrain the group or template bytes) if you only want a specific group.
  libnexaapp ships this exact match-any-grouped-output pattern ready-made as the top-level
  `ALL_ASSET_FILTER` (`tdpp.kt`) — pass it straight to `requestAssetsUri(ALL_ASSET_FILTER, …)`.
- `chalby` is a **per-session ownership challenge** — fresh, unpredictable bytes you generate and
  remember (single-use, like the nexid login challenge). The wallet binds its proofs to it.
  (`requestAssetsUri`'s `assetChallenge` parameter is optional — pass `null` and no proof is
  requested at all; the wallet then just enumerates, which is fine for a low-stakes display list
  but proves nothing.) **Size it 8–64 bytes**: outside that range the wallet still enumerates
  but attaches no proofs (null `proof` on every entry) — a too-short challenge silently degrades
  the flow to unproven.

Four wallet-side behaviors that shape what you get back:

- **Enumeration covers ONE account.** Wally is multi-account, and a registered domain is bound
  to the account the user consented with — the reply lists that account's grouped UTXOs only,
  not the whole wallet's. A user whose NFTs live in a different account looks empty to your app
  until they re-register from that account.
- **Authority UTXOs are never returned** — only quantity-bearing token outputs, so you won't
  see (and can't be spoofed by) MINT/MELT authorities in an asset list.
- The per-domain **asset-info policy** (ACCEPT/ASK/DENY) applies: a domain the user set to
  auto-accept answers without a prompt; DENY replies with a `resultcode=300` rejection callback.
- Building a proof does **not** reserve the UTXO — proving ownership never blocks the user from
  spending the asset a moment later, so re-verify on-chain state at settlement time rather than
  trusting a stale proof.

One use of verified holdings beyond display: **token-gated authorization**. A session whose
verified asset set contains a designated group id (an "admin token", a membership NFT) can be
granted elevated capabilities — ownership of the token *is* the credential, with no account system
needed. Gate on the *proof-verified* holdings (this pattern), never on an unverified claim, and
remember the proof is a snapshot: re-run the `/assets` round trip (or re-check the outpoint
on-chain) before honoring the credential for anything destructive.

**Response handling.** The wallet POSTs back a JSON list of entries, each with an
`outpointHash`, the serialized `prevout`, and a `proof`. For each entry, server-side:

```kotlin
// 1. Deserialize the claimed prevout (a serialized output) on your chain:
val prevout = NexaTxOutput(chain, BCHserialized(entry.prevout.fromHex(), SerializationType.NETWORK))

// 2. Read its group; skip ungrouped and "fenced" (restricted/system) groups:
val gi = prevout.script.groupInfo(prevout.amount) ?: return@forEach        // not a token
if (gi.groupId.isFenced()) return@forEach                                   // skip fenced groups

// 3. Verify the ownership PROOF against the challenge you issued (see below) AND that the
//    outpoint is real/unspent on the network — only then count it as owned.
if (!verifyOwnershipProof(gi, entry, issuedChallenge)) return@forEach

// 4. Key owned holdings by GroupId, accumulating quantity across multiple UTXOs of the same
//    group (a fungible balance held in several outputs sums; an NFT is just quantity 1):
val amount = prevout.groupInfo()?.tokenAmount ?: 1L
ownedByGroup.merge(gi.groupId, amount) { a, b -> a + b }
```

**What the proof is, and why it's safe.** The wallet's `proof` is a **Challenge Transaction**
(`https://spec.nexa.org/transactions/challengeTransaction/`): a **signed but deliberately invalid**
transaction that can never be mined. The spec's "obvious and minimal" invalidity mechanism is an
**nVersion with the high bit set (`> 127`, e.g. `0x80`)** — Nexa consensus only allows specific
nVersion values, so any MSB-set version is invalid forever (your verifier must *not* reject on the
MSB; it validates everything else as normal). The challenge tx spends the claimed UTXO and has a
**single `OP_RETURN` output** whose **first push is the challenger's identity (your server host /
FQDN)** and whose **second push is the challenge** you issued. The wallet doesn't sign your raw
challenge bytes directly: it interleaves a **random byte before every byte** of your challenge (so a
16-byte signed blob comes from your 8 bytes — your issued bytes are the *odd-indexed* ones), which
breaks up the signed content so it can't be lifted into another context.

Your verification: (1) confirm the nVersion MSB is set; (2) confirm there is one `OP_RETURN` output
and its **first push matches your host** — this is the critical anti-spoof check (it stops site A
from relaying site B's challenge as its own to impersonate you to B); (3) extract the challenge from
the second push and confirm it equals the single-use challenge you issued for this session; (4)
**verify the UTXO actually existed on-chain** (a merkle/SPV proof, or a lookup against your own node
/ electrum) — script validity *alone is not enough*, because an attacker can fabricate a UTXO that
never existed, so you must independently confirm the outpoint is real and unspent. Because the proof
commits to your host and a single-use challenge and is never broadcast, it cannot be replayed
against another site or reused later, and the wallet proves control without spending or exposing
keys. (The `chalby=` param means "I'm sending only the challenge *bytes* — you build the full
challenge tx"; the alternative `chaltx=` form sends the whole challenge transaction for the wallet
to sign.)

(You do not have to hand-roll any of this: libnexaapp ships the wire types
(`TricklePayAssetList`/`TricklePayAssetInfo` in `tdpp.kt`), a proof-verification helper
(`checkAssetChallenge`), and a complete built-in `POST /assets` handler installed by
`installWalletRoutes` — see "The built-in server side of this flow" below. The steps above are
the *custom/override* path. Either way, do **not** trust a bare `outpointHash` as proof of
ownership — a wallet can claim any outpoint; only a valid challenge proof establishes control.)

#### The built-in server side of this flow (libnexaapp)

`installWalletRoutes` registers the wallet-facing `POST /assets` callback route itself, and its
handler (`handleAssets` in libnexaapp's `routeController.kt`) already performs the whole response
side of Pattern 8: it parses the `TricklePayAssetList` JSON, deserializes each claimed prevout,
reads its group (`script.groupInfo(amount)`), skips ungrouped and fenced entries, and verifies
each proof via `checkAssetChallenge(gi, chainSelector, assetInfo, p2pnode) { isValid -> … }` —
which relays the proof tx to the **trusted P2P node** for validation (a tx-validation network
message) and confirms the proof's first input validly spends the claimed outpoint with the
claimed constraint script. For each asset that passes, it:

- records it on the browser session: `session.assets[groupId] = OwnedAssetInfo(script, outpoint,
  satoshis, prevout, file, cacheDir, ai)` — `NexaAppSession.assets` is a
  `MutableMap<GroupId, OwnedAssetInfo>`, and the `ai: AssetInfo` field is already hooked into the
  `AssetManager` artwork pipeline (the next pattern);
- notifies every connected browser tab with a `WALLET_HAS_ASSET` notification carrying a
  CBOR-encoded `TricklePayBinaryAssetInfo(groupId, amount, outpointHash, prevout, …)`. On the
  client, set `flowConnector.walletOwnsAssetHandler = { assetInfo -> … }` (a var on the client
  `FlowConnector`) to receive each verified asset as it arrives.

So the minimal app-side wiring is: push `requestAssetsUri(ALL_ASSET_FILTER, challenge, sessId =
id)`, then read `session.assets` (server) or `walletOwnsAssetHandler` (client). Implement your own
handler (the steps above) only when you need custom behavior — and note the built-in handler
requires the libnexaapp `blockchain` global to be set (`initBlockchain`, see
`nexa-ktor-server-integration`), since proof verification goes through `blockchain.net.getNode()`.

Even simpler: the **browser can trigger the whole round trip itself** — `installWalletRoutes`
also registers the browser-facing `GET /api/wallet/assets?filter=<hex>`, which generates a fresh
random 8-byte challenge per call, builds the `requestAssetsUri`, and pushes it to the session's
wallet (see `nexa-wallet-connection` § "the built-in `/api/wallet/*` trigger routes"). A "load my
assets" button is then a single client-side GET with no custom server route at all.

**Client-side hygiene for the assets you accumulate.** The `walletOwnsAssetHandler` deliveries
land in whatever store *you* keep (a state list of `TricklePayBinaryAssetInfo`), and that store
has three sharp edges: (1) **dedupe by `outpointHash`** — re-running the flow re-delivers assets
you already hold; (2) **clear the store before a re-request** (a "reload assets" button that only
appends accumulates duplicates); (3) **clear it when `walletConnected` flips false** — the server
does not re-push assets on reconnect, and a *different* wallet connecting on the same browser
session would otherwise keep rendering the previous wallet's tokens/NFTs (clear any per-group
metadata/media caches keyed off those assets at the same time). This is the client-side
counterpart of the server-side "clear your app session fields in both disconnect paths" rule in
`nexa-wallet-connection`.

Two caveats on the built-in path:

- **Challenge binding is not checked for you.** `checkAssetChallenge` validates that the proof is
  a valid signed spend of the claimed outpoint (via the node) and that script + outpoint match
  the claim — but it does not compare the proof's `OP_RETURN` host/challenge commitments against
  the `chalby` challenge you issued. If your threat model includes proof replay across sites or
  sessions (Pattern 8's verification steps 2–3), enforce the host + single-use-challenge checks
  in your own handler (override the route or verify out of band).
- The client-side helper `getWalletAssets(filter)` in libnexaapp's `serverAccess.kt` is an
  unimplemented stub (`TODO()`) — don't call it; trigger the flow by having your server push the
  `requestAssetsUri` URI instead.

### Pattern 8b: Displaying an NFT's artwork (libnexaapp's `AssetManager`)

The collection **icon** from the TDD (Pattern 7) is shared by every NFT in a group — it is *not*
the per-NFT image. An NFT's own artwork lives inside the **NFT `.zip` data file** whose
double-SHA256 equals the subgroup id (Pattern 1). libnexaapp's `org.nexa.assets` subsystem
(shipped in the **`:server` artifact**, not the client library) turns a group id into those
bytes. It needs the `blockchain` global (`initBlockchain`, see `nexa-ktor-server-integration`)
and offers one singleton, `val assetManager = AssetManager()`.

The zip layout (both what `extractNftData` parses and what the spec's NFT format defines):
entries starting `cardf` = **card front** (the displayed artwork), `cardb` = card back,
`public` = public media (full-resolution work), `owner` = owner-only media, and `info.json` =
the NFT metadata, decoded into `NexaNFTv2(niftyVer, title, series, author, keywords, appuri,
category, info, bindata, data, license)` and exposed as `ai.nft`.

```kotlin
import org.nexa.assets.assetManager
import org.nexa.libnexaapp.blockchain          // set by initBlockchain

fun nftArtwork(gid: GroupId): Pair<ByteArray, String>? {   // bytes + a name to derive content-type from
    val bc = blockchain ?: return null
    val getEc = { bc.net.getElectrum() }                   // the SPV connection's own electrum channel
    val ai = assetManager.track(gid, getEc)                // AssetInfo (cached if already tracked)
    ai.load(bc, assetManager, getEc)                       // TDD → fetch + hash-verify zip → extractNftData
    if (ai.loadState != AssetLoadState.COMPLETED) return null
    // Large media is flushed to DISK, not kept in memory (see below) — try memory, then the cached file:
    ai.iconBytes?.let { return it to (ai.iconUri?.toString() ?: "cardf") }
    for (ref in listOfNotNull(ai.iconUri?.toString(), ai.publicMediaCache, ai.publicMediaUri?.toString())) {
        runCatching { assetManager.loadCardFile(ref) }.getOrNull()?.let { (name, bytes) ->
            if (bytes.isNotEmpty()) return bytes to name
        }
    }
    return null
}
```

The pieces, and the gotcha that costs the most time:

- `assetManager.track(gid, getEc)` returns the `AssetInfo` for a group (creating and caching it);
  with `getEc = null` the load is deferred to a background job (`assetOf(gid)` is the deferred
  convenience form). `ai.load(chain, am, getEc)` runs the full pipeline: token description
  (`getTokenDesc`, Pattern 7 — parent-group hop and TDD hash-verify included), then
  `getNftFile(td, gid)` — which finds the `.zip` locally, at the TDD's `nftUrl`, or at the
  well-known public location, **hash-verifies it against `gid.subgroupData()`** (the
  content-addressing rule from Pattern 1), and stores it — then `extractNftData` walks the zip.
  `ai.loadState` progresses `UNLOADED → LOADED_GENESIS_INFO → LOADED_TOKEN_DESC → COMPLETED`.
- **The in-memory byte fields are null for large media.** Media larger than
  `MAX_UNCACHED_FILE_SIZE` (a top-level `var`, default ~20 KB) is flushed to the cache directory
  and the in-memory field (`iconBytes`, `publicMediaBytes`, …) is set **null** — real artwork is
  almost always over the threshold. The displayed artwork is the **card front**, and its bytes
  live at `ai.iconUri` (a `file://` reference into the asset cache); `publicMediaCache` /
  `publicMediaUri` are the full-media equivalents. Read them back with
  `assetManager.loadCardFile(ref)` — it strips the `file://` prefix and resolves against the
  cache directory, returning `(resolvedName, bytes)`. There is no in-library
  bitmap accessor (the `ImageBitmap`-returning accessors like `publicMediaImage()` are
  deliberately absent so the assets layer carries no Compose dependency) — you serve the raw
  bytes and decode client-side (`nexa-compose-ui-design` covers decoding).
- The storage layout under the asset/cache dirs you passed to `initBlockchain`: `<gid>.td`
  (cached token description), `<gid>.ai` (serialized `AssetInfo`), `<hash>.zip` (the verified NFT
  data file), plus cached media files named `<gid>_cardf.<ext>` etc.
- To **serve** the artwork from a Ktor route, `call.respondBytes(bytes, contentType)` — derive
  the content type from the resolved file name's extension. Note that `installWalletRoutes` also
  registers a `GET /api/asset/image` route, but its handler does not respond with the image
  bytes — register your own media route rather than relying on it.

**Creating** the zip is also in the library (`org.nexa.assets`, `nftTools.kt`): `makeNftyZip`
builds a spec-conformant NFT zip from an `NFTCreationData` (main data file + card media +
`NexaNFTv2` metadata), `checkNftyZip` validates one, and `generateCardFile` renders card media.
Since the zip's double-SHA256 *is* the subgroup id, build the zip first, then derive the NFT's
group id from it (Pattern 1) and mint (Pattern 9).

### Pattern 9: Minting tokens/NFTs by spending a MINT authority

Issuing supply means **spending a UTXO that carries the MINT authority** (Pattern 5) and
creating a grouped output with the new quantity. You don't hand-wire the authority input —
`txCompleter` pulls it in for you when you set `USE_GROUP_AUTHORITIES`, the same way
`FUND_GROUPS` pulls in ordinary token inputs (see `nexa-transaction-construction` Pattern 6 for the
full `TxCompletionFlags` set). A free, self-funded mint to a known destination is the simplest
case:

```kotlin
val mint = txFor(chain)
// One unit of the (sub)group, minted straight to dest:
mint.add(NexaTxOutput(chain, dust(chain), dest.groupedLockingScript(gid, nftQty)))
wallet.txCompleter(mint, 0,
    TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.FUND_GROUPS or
    TxCompletionFlags.SIGN or
    TxCompletionFlags.USE_GROUP_AUTHORITIES or TxCompletionFlags.NO_BATON_AUTHORITIES)
wallet.send(mint)
```

Two mint-specific flags beyond the normal fund/sign set:

- **`USE_GROUP_AUTHORITIES`** — permits the completer to spend an authority UTXO (without it, the
  completer treats authorities as untouchable and the mint fails to find a usable input).
- **`NO_BATON_AUTHORITIES`** — keep the completer from consuming a **BATON** authority for this
  spend. You almost always want this: the baton is the master "create more authorities" right, and
  burning it into a routine mint is hard to undo. Spend a plain MINT authority and preserve the
  baton.
- **`MUST_MINT`** (optional, defensive) — forbid the completer from satisfying the grouped output
  out of token UTXOs you already hold; the quantity *must* come from spending a mint authority. Set
  it when "issue new supply" must not silently degrade into "move existing tokens" (e.g. a paid
  mint where moving an existing NFT instead of minting a fresh one would be a correctness bug).

#### Minting as a half-tx the buyer funds (mint-on-demand / paid mint)

A marketplace that mints an NFT only when someone pays builds a **half transaction**: the issuer
signs the mint contingent on the buyer supplying the NEXA fee and the NFT destination. The idiom
chains two `txCompleter` passes around an output swap so the issuer never signs the NFT output
itself:

```kotlin
val mint = txFor(chain)
val flags = TxCompletionFlags.FUND_GROUPS or TxCompletionFlags.USE_GROUP_AUTHORITIES or
            TxCompletionFlags.NO_BATON_AUTHORITIES

// 1. Add a *template-spendable* grouped output so the completer knows an NFT is being minted
//    here and pulls in a matching mint-authority input. Appending OP.TMPL_SCRIPT marks it as a
//    slot the completing (buyer's) wallet may fill in:
val nft = NexaTxOutput(chain, dust(chain),
    SatoshiScript.grouped(chain, gid, nftQty) + OP.TMPL_SCRIPT)
mint.add(nft)
wallet.txCompleter(mint, 0, flags)          // pulls the authority input; does NOT fund native / sign yet

// 2. Remove the NFT output (the issuer must NOT sign it — the buyer's wallet finalizes it):
mint.outputs.removeAt(0)
// 3. Add the fee/payment output the buyer's wallet must satisfy, then PARTIAL-sign the issuer's
//    inputs (the authority) so the signature is valid only if the fee output survives:
mint.add(NexaTxOutput(chain, fee, feeDest.lockingScript()))
wallet.txCompleter(mint, 0, flags or TxCompletionFlags.SIGN or TxCompletionFlags.PARTIAL)
// 4. Re-add the NFT output so the counterparty knows what to mint, then push the half-tx
//    (see nexa-transaction-construction Pattern 6's half-tx idiom and nexa-wallet-connection's /tx flow):
mint.add(nft)
```

This is the token-issuance instance of the general half-tx swap-offer idiom in
`nexa-transaction-construction` Pattern 6. When you push this half-tx to the paying wallet, the
default `flags=0` means the wallet funds the native side — so the push URI **must carry
`inamt=` the sum of the half-tx's existing input amounts** (`mint.inputTotal`, or sum the inputs
yourself); a missing `inamt` with `NOFUND` clear makes the wallet reject the push as a bad link
(`nexa-wallet-connection`). And when the completed tx comes back on your `/tx` callback, verify it
against the proposal and validate it via the trusted node before recording the mint —
`nexa-transaction-construction` Patterns 6b/6c.

#### Keep a pool of spare authorities for concurrency

A single MINT authority UTXO can only be spent once per tx, so concurrent mints contend for it.
The robust pattern is to **pre-split one authority into many** ahead of demand: build a tx with N
identical authority outputs (`groupedLockingScript(gid, authFlagsAsLong)`, Pattern 5), fund/sign
with `USE_GROUP_AUTHORITIES`, and broadcast — then mint against the pool, topping it back up when
it runs low. When the completer can't find a free authority it raises a
not-enough-token-balance error; catch it, split more authorities, and retry the mint once.

The concrete pool-management loop, with the pieces named:

- **Count what's actually available.** Walk the wallet's UTXOs (`wallet.forEachUtxo` — see
  `nexa-wallet-lifecycle-and-chain`) counting entries whose `groupInfo()` is an authority of the
  **parent** group with the `MINT` bit set and **`reserved == 0L`** (a reserved authority is
  already claimed by an in-flight build), and **excluding the BATON** from the count (it is not a
  pool worker). For subgroup-NFT minting the authorities live on the *parent* group
  (`gid.parentGroup()`), which is why the count keys off the parent.
- **Top up below a threshold, split generously.** When the available count drops below a small
  floor (a handful), broadcast one split tx with many authority outputs (dozens to ~100 —
  authority outputs are dust-cheap, and each extra output saves a future contention stall). Check
  at startup and before each mint; the split tx is `send(…, sync = true)` so the pool exists
  before the mint that needed it.
- **Catch the named exceptions.** The completer signals "no usable authority" as
  **`WalletNotEnoughTokenBalanceException`**; a wallet holding *no* authority for the group at all
  surfaces as **`WalletAuthorityException`**. Catch the former around `txCompleter`, split, retry
  the completion once; treat the latter as a configuration/ownership problem (there is nothing to
  split from), not a retry.
- **A completion that found nothing still "succeeds."** After a mint's `txCompleter` pass, check
  `tx.inputs.size > 0` — a completer that couldn't pull any input can leave the tx empty rather
  than throwing, and an empty-input mint half-tx pushed to a wallet fails confusingly later.

For pooling ordinary **token** UTXOs (not authorities) — e.g. keeping N single-unit SFT outputs
ready so concurrent vends don't contend — libnexakotlin ships the helper
`Wallet.chunkTokenInto(gid, payAddress, numUtxos, tokenAmt = 1): List<iTransaction>`
(`walletMgmt.kt`): it counts existing UTXOs of `gid` that already hold exactly `tokenAmt`, splits
larger token UTXOs into more until `numUtxos` exist (funding/signing/broadcasting via
`txCompleter` internally), and returns whatever txs it created (possibly none). Idempotent by
construction, so it's safe to call on a schedule as the top-up.

#### Reusing a BATON authority without consuming it (read-only inputs)

Consensus offers an alternative to spending (and re-creating) an authority on every use: a
**read-only input** (`https://spec.nexa.org/script/read-only-inputs/`, and the pattern in
`nexa-transaction-construction`). A group authority referenced by a read-only input grants its
powers to the transaction **if and only if** the authority carries the **BATON** flag and the
read-only input has a valid, non-empty satisfier — and because the input is not spent, the same
authority UTXO survives to authorize the next mint too, sidestepping the consume-and-recreate
churn the authority-pool pattern above manages. Two design cautions from the spec: sign such an
input with an `…/ALL`-output sighash so the signature pins exactly how the authority's powers
are used in this tx; and a contract that *offers* a baton read-only to the public must require
at least a trivial non-empty satisfier (an empty one is legal but grants no powers). Note the
libnexakotlin completer does not build read-only inputs for you (`USE_GROUP_AUTHORITIES` spends
them) — this is a hand-built-input technique. Ordinary grouped assets on a read-only input are
*not* counted for conservation or introspection; only the BATON-authority case activates.

#### Melting is NOT symmetric with minting — `txCompleter` ignores melt inputs

You might expect a melt-and-remint tx (destroy a batch of tokens, mint replacements) to have
`txCompleter` handle the MELT half the way it handles the MINT half. **It does not.** In
libnexakotlin's completer (`wallet.kt`), a token **input** whose group has no matching
output (a melt) hits the input-tally loop and is **skipped** —
`val d = groupData[cgdata.groupId] ?: continue // either tx is melting or won't validate (not our
problem)`. So the completer never pulls a MELT authority for you and never even tracks the melt;
**you must arrange the melt authority yourself.** There is **no `MUST_MELT` flag** —
`TxCompletionFlags` has only the MINT-side `MUST_MINT`.

The MINT half, by contrast, *is* the normal proven path: a fresh-subgroup **output** with no
matching input is unbalanced, so the completer's mint branch pulls **one** authority (via the
parent + `SUBGROUP` fallback), and `tx.hasAuthority` short-circuits the rest so only **one**
authority is consumed. The trick that makes a melt-remint work in a single tx: that one pulled
authority carries `MINT | MELT | SUBGROUP`, so it **also** authorizes every token melt at
consensus — no separate melt-authority wiring is needed. Recipe for a batch melt-remint sweep:

```kotlin
// Add the token NFT inputs EXPLICITLY — the completer will NOT add melt inputs for you:
forEachUtxo { spendable -> sweep.add(txInputFor(spendable)) }
// ...add the replacement (stub) outputs, then:
wallet.txCompleter(sweep, 0,
    TxCompletionFlags.FUND_GROUPS or TxCompletionFlags.USE_GROUP_AUTHORITIES or
    TxCompletionFlags.MUST_MINT or TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.SIGN)
// One MINT|MELT|SUBGROUP authority melts every token input AND mints every stub output.
```

> **Do NOT set `NO_BATON_AUTHORITIES` on the melt-remint sweep** (testnet-confirmed). Even though
> Pattern 9 above tells you to set it for a *routine mint*, a sweep that relies on finding an
> authority will fail `STRf024 ... no applicable authorities` if a prior baton-less `mintTokens`
> (which does NOT set `NO_BATON`) has already **drained the authority pool** — the log shows
> `consuming authority without creating child authority because baton not set` once per worker.
> Omitting `NO_BATON_AUTHORITIES` lets the completer use any `MINT|MELT|SUBGROUP` authority — a
> free pool worker if one exists, else the genesis **baton master** (always custody-held; the
> completer auto-preserves it by emitting a baton child). The sweep is then self-sufficient and
> immune to minting having emptied the pool. Trade-off: the sweep touches the baton, so if you
> need the baton kept cold, provision a dedicated melt/mint authority pool instead. **Corollary:**
> a baton-less `mintTokens` will happily eat pool workers when a pool exists — pre-mint before
> splitting the pool, or size the pool for both the mint draw and the sweep draw.

### Putting the token amount in `output.amount`

**Wrong**:
```kotlin
val out = txOutputFor(DEFAULT_CHAIN)
out.amount = 1000L                       // this is 1000 SAT of native NEXA, not 1000 tokens
out.script = PayAddress(addr).lockingScript()   // ungrouped — no token at all
```
*You sent 10 native NEXA (1000 sat) and zero tokens. `script.groupInfo()` returns null.*

**Right**: attach the group to the script; the native `amount` is dust.
```kotlin
val out = (1000L ofGroup gid) payTo addr        // 1000 token units; amount auto-set to dust
```

### Trying to pay to a `PayAddressType.GROUP` address

**Wrong**:
```kotlin
val groupAddr = gid.toString()                  // "nexa:...group..."
val out = 5L.nexa payTo groupAddr               // throws / WalletNotSupportedException
```
*A group address denotes a token *type*, not a destination — `lockingScript()` on a GROUP
address throws "This denotes a token type, not an address".*

**Right**: the **group id** identifies the token; you still pay to an ordinary
`P2PKT`/`TEMPLATE` recipient address and attach the group to that address's script:
```kotlin
val out = (5L ofGroup gid) payTo recipientP2PKTAddress
```

### Forgetting the native-NEXA (dust) value and fee on a token tx

**Wrong**: building a token-only tx with `amount = 0` outputs and no fee budget.
*Rejected — every output still needs at least dust native value, and the tx still needs a
native-NEXA fee. Tokens do not pay fees.*

**Right**: let `ofGroup … payTo` set dust for you, and fund the native side (inputs/fee)
exactly as for a normal payment (`nexa-transaction-construction`). For a *contract* that
locks the token amount, overfund the native side the same way Pattern 8 of
`nexa-npl-smart-contracts` overfunds for fees.

### Treating the on-chain token quantity as the display amount

**Wrong**: showing `gi.tokenAmount` directly as "tokens" in the UI.
*The on-chain quantity is in the token's smallest unit. A token with
`decimal_places = 2` and `tokenAmount = 1000` is **10.00** tokens, not 1000.*

**Right**: fetch the token's `decimal_places` from its genesis/description metadata and
scale for display only. Never scale before doing on-chain math — contracts and conservation
operate on the raw integer quantity.

### Comparing native NEXA against the zero-group instead of "no group"

**Wrong**:
```kotlin
if (gi.groupId == nativeCoinGroupId(chain)) { /* native */ }   // ungrouped outputs give gi == null first
```
*Ordinary native outputs return `groupInfo() == null`; you'll NPE or mis-branch before ever
reaching the zero-group comparison.*

**Right**: branch on null first.
```kotlin
val gi = output.script.groupInfo(output.amount)
if (gi == null) { /* native NEXA */ } else { /* token of gi.groupId */ }
```

### Reading authority flags as a number in-script

**Wrong**: parsing field 2 as an integer and comparing numerically. The VM's sign-magnitude
encoding makes an authority a *negative* 8-byte value, and a naive `BIN2NUM` /comparison
misreads it.

**Right**: read authority as raw bytes (`getOutputGroupAuthority` and its variants) and
compare bytes, or build the expected flag bytes the same way and `eq` them.

### Letting a covenant forget `verifySameGroup`

**Wrong**: a token-vault rule that checks the *amount* and *recipient* of its outputs but
not their **group**. A spender can satisfy the amount/recipient checks while substituting a
different (worthless) group id, draining the real token.

**Right**: every covenant that constrains a token output must also bind its group —
`verify(getOutputGroupId(idx) eq myGroup)` or `verifySameGroup(idx)`.

> Note that `verifySameGroup` is genuine **defense-in-depth, not the primary guard** against a
> group swap: Nexa enforces per-group **conservation at the consensus layer before any script
> runs** — a spend whose output carries a different group than a token input is an illegal melt
> and is rejected (`grp-invalid-melt Group input exceeds output, but no melt permission`). In the
> script VM this rejection is raised in the `ScriptMachine` **constructor**
> (`createTemplateContext`), before the covenant executes, which is why you **cannot isolate a
> `verifySameGroup` bypass in a replay test built with the tx-based constructors** (see
> `nexa-script-machine-testing`): you can't construct a conservation-valid spend that also swaps
> the group.
> (The two-phase init documented there — `ScriptMachine()` + seat the scripts/tx +
> `initialize(true)` — does **not** skip that check; it reaches the same native
> `createTemplateContext`. What it fixes is *false* rejections: the tx-based constructors fake
> every other prevout as a zero-value placeholder, so balanced **multi-input** grouped spends
> look like illegal melts at construction, while the two-phase init sees the real prevout array.
> No `ScriptMachine` path executes a conservation-violating spend's scripts, so a
> `verifySameGroup` bypass cannot be exercised in isolation.) Keep the check anyway — it defends
> the cases conservation alone doesn't cover (e.g. binding a *specific* group among several, or a
> same-group-but-wrong-recipient path).

### Reading `iconBytes`/`publicMediaBytes` and getting null (or a 0-byte response) for a loaded NFT

**Wrong**: after `ai.load(...)` completes (`loadState == COMPLETED`), serving `ai.publicMediaBytes`
(or `ai.iconBytes`) directly as the artwork response.
*Both fields are null whenever the media exceeds the in-memory threshold
(`MAX_UNCACHED_FILE_SIZE`, ~20 KB default) — which real artwork almost always does. The bytes were
flushed to the asset cache on disk; your endpoint returns 0 bytes and the UI falls back to the
collection icon.*

**Right**: treat the byte fields as a small-media fast path only; the durable references are
`ai.iconUri` (card front) and `ai.publicMediaCache`/`ai.publicMediaUri` (full media). Resolve them
with `assetManager.loadCardFile(ref)` (Pattern 8b).

### Hardcoding a group id across chains

A `GroupId` carries its `ChainSelector`. A mainnet group id is not a testnet group id; the
genesis tx that created it lives on one chain. Reconstruct group ids on `DEFAULT_CHAIN` and
don't copy a mainnet group string into a testnet build (and vice versa).

## Security considerations

- **Authority outputs are the keys to the mint.** A UTXO carrying `MINT` can create
  unlimited tokens; one carrying `MELT` can destroy them; `BATON` can pass authority to a
  new output. If a contract governs a group, its rules must constrain *where authority is
  allowed to flow* (typically: an authority may only continue to an output of the same
  contract/group via `verifySameContract` + `verifySameGroup`), or an attacker who can
  spend the authority UTXO can mint at will. Treat losing an authority UTXO as catastrophic.
  When minting, set `NO_BATON_AUTHORITIES` so a routine mint spends a plain MINT authority and
  never consumes the **BATON** (the master right to create more authorities) — burning the baton
  into an ordinary issuance is hard to recover from. Keep a *pool* of spendable MINT authorities
  (Pattern 9) so concurrent mints don't contend for a single UTXO and stall.

- **Group-id collision is infeasible** (group ids are at least 32 bytes), so treating
  group-id equality as "same token" is safe. But equality of a *display ticker/name* is
  **not** — names live in off-chain metadata and are not unique. Always identify a token by
  its group id, never by its ticker or name.

- **Decimals are advisory metadata, not consensus.** A malicious or buggy token-description
  document can claim any `decimal_places`. Don't make value decisions ("this is worth X")
  from token metadata you didn't validate; consensus only guarantees the raw integer
  quantity and conservation per group.

- **Token and native value are conserved separately.** A tx can be perfectly valid for
  tokens while stealing native NEXA (or vice versa). When you validate an incoming tx,
  check *both* the group/quantity (`groupInfo`) *and* the native `amount`, not just one.

- **A `GROUP` address can be confused for a payable address by careless code.** Validate
  destination addresses as `P2PKT`/`TEMPLATE` before locking funds to them (see
  `nexa-identity-and-addresses`); a group string slipped in where a recipient was expected
  will throw at best and misroute at worst.

- **Subgroups inherit trust from the parent group.** Anyone with `SUBGROUP` authority on a
  parent can mint child groups (NFTs/SFTs) under it. If your app trusts "any token under
  parent G", remember that trust is exactly as strong as control of G's subgroup authority.

## Related skills and references

- `nexa-npl-smart-contracts` — the rest of the NPL contract DSL; Pattern 9 there is the
  OP_PARSE field table this skill extends with the group fields (0/1/2). The
  `verifySameContract` baton/state-threading helper is documented there.
- `nexa-transaction-construction` — building and broadcasting the tx that carries your token
  outputs; the native-NEXA unit system and fee rules that still apply to token txs.
- `nexa-identity-and-addresses` — `PayAddress`/`PayAddressType`, validating that a recipient
  is `P2PKT`/`TEMPLATE` (vs the un-payable `GROUP` type) before sending tokens to it.
- `nexa-wallet-connection` — the wallet side of Pattern 8: the `tdpp://host/assets` push, the
  `af`/`chalby` parameters, and the JSON callback shape the wallet returns; also the TDPP
  `flags` bitfield used when pushing a token (offer) tx.
- `nexa-ktor-server-integration` — `initBlockchain` and the libnexaapp server globals that
  Patterns 8/8b's built-in `/assets` handler and `AssetManager` depend on, and serving media
  bytes from a route.
- `nexa-compose-ui-design` — decoding the artwork bytes Pattern 8b serves into an `ImageBitmap`
  on the Compose Multiplatform client (`decodeToImageBitmap`, and the SVG path).
- `nexa-rpc-node-client` — the node's token-issuance RPCs (`tokenNew`/`tokenMint`/`tokenMelt`/
  `tokenSend`/`tokenMintage`/`tokenAuthorityCreate`) for issuing and managing a group with a
  full node you operate, as the scripted/test counterpart to the app-controlled minting here.
- `nexa-electrum-monitoring` — read a group's on-chain token state from a light client:
  `getTokenGenesisInfo(groupId)` (the `decimal_places`/ticker/name the display-amount discussion
  needs), `getTokenBalance`/`getTokenUnspent`/`getTokenHistory`, and `getUtxo` (whose `group` /
  `group_quantity` fields reveal whether an outpoint carries a token).
- `nexa-script-machine-testing` — write a test that replays a group/authority covenant spend
  (same-group check, mint authority spend) through the real script VM to confirm the
  `verifySameGroup` / group-introspection rules pass; these covenants are exactly the "complex"
  spends worth verifying offline (in the test source set) while you develop them.
- `nexa-debugging-onchain-errors` — symptom→cause table when a token tx is rejected.
- The **Token Secrets** protocol sketch (`https://spec.nexa.org/tokensecret/`) — the spec's
  design for atomically transferring a token *and* an associated decryption secret in one tx
  (ECDH over a half-transaction, the secret's pubkey committed in the mint/group id). Relevant
  when a token is meant to track knowledge of a secret (encrypted-content NFTs); note the spec's
  own caveats (honest-issuer assumption; a past holder keeps the key).

### Supporting files in this folder

- `groupIntrospectionReference.md` — the full NSL group-accessor surface
  (`getOutputGroup*` / `getPrevoutGroup*` / the five authority variants / `countByGroup` /
  `groupedOutputN` / `verifySameGroup`) with exact signatures and the canonical field each reads.
- `tokenMetadataReference.md` — the token-description document (TDD) JSON shape + signature
  canonicalization, and the genesis OP_RETURN byte layout (`88888888`/`88888889` tags) — the
  on-demand wire-format detail behind Pattern 7.

### Supporting files in this folder (to be created)

- `examples/` — full worked examples: a simple fungible-token transfer, a same-group
  covenant vault, an authority/baton delegation chain, and a subgroup-minted NFT.