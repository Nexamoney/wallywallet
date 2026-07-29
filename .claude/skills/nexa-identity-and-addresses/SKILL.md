---
name: nexa-identity-and-addresses
description: "Explains Nexa address types and identity, P2PKT (rotating payout) vs P2PKH (stable identity), and how to choose which address to send funds to, identify users, check ownership, or fix argsHash failures. Use when sending a payment to the logged-in user, gating access by identity, validating that an address is P2PKT before locking funds to it, or debugging 'Could not extract argsHash from address'. Triggers: PayAddress, extractArgsHash, argsHash, P2PKT, P2PKH, lockingScript, parseTemplate, p2pktAddressFromHash, session.identity, userNexaAddress, nexa: and nexatest: prefixes. Not for building the tx (nexa-transaction-construction), how userNexaAddress gets populated (nexa-wallet-connection), or contract output verification (nexa-npl-smart-contracts)."
---

# Nexa identity and addresses (P2PKT vs P2PKH)

## When to use this skill

Trigger when a developer is choosing which address to send funds to, identifying users,
checking who owns a record, or troubleshooting "argsHash" failures. Concretely trigger on:

- Keywords: `PayAddress`, `extractArgsHash`, `argsHash`, `P2PKT`, `P2PKH`,
  `lockingScript`, `parseTemplate`, `p2pktAddressFromHash`, `session.identity`,
  `userNexaAddress`, `nexatest:`, `nexa:`.
- Errors: `Could not extract argsHash from address: ...`, `address not P2PKT`,
  `lockingScript: type != Type.TEMPLATE`.
- Tasks: "send a payment to the logged-in user", "use the user's identity to gate
  access to this listing", "validate that an address is a P2PKT before locking funds
  to it", "buyer paid the wrong address", "address keeps changing".

**Negative triggers** — do NOT use this skill for:
- Building the transaction itself once you have the address → `nexa-transaction-construction`.
- Wallet connection / how `userNexaAddress` *gets populated* → `nexa-wallet-connection`.
- NPL contract output verification → `nexa-npl-smart-contracts` (this skill explains
  the input, that one explains the script side).

## Mental model

NEXA's standard address format is **P2PKT — Pay to Public-Key Template**. A P2PKT
locking script is the script-template VM equivalent of "anyone who can sign with key K
and produces this exact constraint script can spend this output." The constraint script
is identified by a 20-byte `argsHash` that is embedded directly in the P2PKT output
script. When a contract or any other on-chain logic wants to verify "this output goes to
person X", it extracts that `argsHash` from the output's script and compares.

Wally's **identity address** — the one you get back from the `nexid` login flow at
`session.identity.value` — is **NOT a P2PKT address**. It's a P2PKH (pay-to-pubkey-hash),
which is the older Bitcoin-style format. Same key pair, different output layout.

**You can tell the two apart at a glance by the character after the `nexa:`/`nexatest:`
prefix**, because the cashaddr version byte determines the first payload character
(`https://spec.nexa.org/addresses/cashaddr/`):

| Starts with | cashaddr version byte | Address kind |
| --- | --- | --- |
| `q…` (e.g. `nexa:qrz…`) | 0 | **P2PKH** — the legacy/identity format (`session.identity`) |
| `n…` (e.g. `nexa:nqt…`) | 152 | **Pay to Script Template (P2ST)** — the modern native format; the public-key instance is P2PKT (your rotating payout/receive address), and contract outputs are the P2CAT/P2CT script-template forms |
| `t…` (e.g. `nexatest:t…`) | 88 | **GROUP** — a native-token *type* identifier, not a payable destination (see `nexa-tokens-and-groups`) |

So a `nexa:q…` string is an identity (P2PKH) and `extractArgsHash` will reject it; a `nexa:n…`
string is a script-template address you *can* pull an argsHash from. (The spec's umbrella term for
the `n…` family is **Pay to Script Template / P2ST**; "P2PKT" is the public-key-template instance
this skill centers on, and "P2CAT"/"P2CT" are the contract-with-args / contract-no-args forms an
NPL contract output uses.) This character cue is a cheap first-pass classifier — still parse with
`PayAddress(str).lockingScript().parseTemplate(0)` to be certain.

So a single connected wallet user has *two* on-chain identifiers:

| Slot | Type | Stability | Use it for | Cannot be used for |
| --- | --- | --- | --- | --- |
| `session.identity.value` | P2PKH | **Stable** (one per signing key, set at login) | Authentication, ownership ("who created this listing?"), audit trail | Contract output destinations, anywhere `extractArgsHash` is called |
| `session.userNexaAddress.value` | P2PKT | **Rotates** after spends | Actual on-chain destinations (pay this address), contract output `sellerHash`/`buyerHash` args | Stable user identification across sessions |

A library-vs-app distinction worth internalizing: **`session.identity` is a real
libnexaapp field** — `NexaAppSession` declares `val identity = MutableStateFlow<String?>(…)`,
populated by the `nexid` login callback. **`session.userNexaAddress` is not a library
field** — it is an app-level `MutableStateFlow<String>` you declare on your own `AppSession`
subclass and populate from the wallet's `/_share` POST (see `nexa-wallet-connection`). A bare
`NexaAppSession` has no `userNexaAddress`; the name is a convention these skills use, not an
API. (libnexaapp deliberately leaves the payout/receive address to the app, since which
field holds it — and how it's validated — is application policy.)

This split has no analogue in Ethereum (where an EOA is a single address that does
everything) or single-key Bitcoin wallets. It catches everyone the first time. Common
symptoms of mixing them up:

- "Could not extract argsHash from address" — you tried `extractArgsHash` on a P2PKH
  identity. P2PKH scripts have no template structure, so `parseTemplate(0)` returns null.
- A naïve payment URI `nexa:<identity>?amount=N` "works" because Wally is forgiving about
  destination address types, but the on-chain output is a P2PKH spend, which most NEXA
  smart contracts cannot validate with `getOutputArgsHash`.
- A listing's "owner" gets a wrong identity check after the user does a spend that
  rotates `userNexaAddress` — because the app keyed ownership off the rotating address
  instead of the stable identity.

The rule: **identity for who, payout for where.** Always capture both at the moment of
record creation, store them both, and never substitute one for the other.

## Setup and versions

You need `libnexakotlin` (provides `PayAddress`, `lockingScript`, `parseTemplate`,
`P2PKT_ID`) and `libnexaapp` (provides `NexaAppSession.identity`, `userNexaAddress`). Pin
exact versions per `nexa-project-setup`.

Address-related types you'll import from libnexakotlin:

```kotlin
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.PayAddressType
import org.nexa.libnexakotlin.P2PKT_ID
import org.nexa.libnexakotlin.SatoshiScript
import org.nexa.libnexakotlin.OP
import org.nexa.libnexakotlin.scriptDataFrom
```

## Core patterns

### Extracting the argsHash from a P2PKT address

```kotlin
import org.nexa.libnexakotlin.PayAddress

/** Pulls the 20-byte argsHash that a NEXA contract reads via getOutputArgsHash().
 *  Throws if [addressString] is not a P2PKT-style address. */
fun extractArgsHash(addressString: String): ByteArray {
    val pa = PayAddress(addressString)
    val lockingScript = pa.lockingScript()
    val tmpl = lockingScript.parseTemplate(0)            // 0 = no native amount, just structure
    return tmpl?.argsHash
        ?: throw IllegalArgumentException(
            "Address is not P2PKT (type=${pa.type}): $addressString")
}
```

### Reconstructing a P2PKT address from its argsHash

```kotlin
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.PayAddressType
import org.nexa.libnexakotlin.P2PKT_ID
import org.nexa.libnexakotlin.SatoshiScript
import org.nexa.libnexakotlin.OP

@OptIn(ExperimentalUnsignedTypes::class)
fun p2pktAddressFromHash(argsHash: ByteArray, chain: org.nexa.libnexakotlin.ChainSelector): String {
    val p2pktScript = SatoshiScript(chain, SatoshiScript.Type.TEMPLATE,
        OP.PUSHFALSE, P2PKT_ID, OP.push(argsHash))
    val payAddr = PayAddress(chain, PayAddressType.TEMPLATE, p2pktScript.asSerializedByteArray())
    return payAddr.toString()
}
```

Useful for: deriving a recipient's address from the `argsHash` you see in a contract's
visible args, or showing the buyer's refund address based on what's encoded on-chain.

### Reading a P2PKT argsHash out of an on-chain contract output

The visible args baked into a funded contract output are readable straight from the chain:
parse the output's template and pull the pushed args from `tmpl.rest` via `scriptDataFrom(...)`.
Useful when you need to recover a counterparty's pay-address from a funded contract tx
(e.g. after losing a server-side DB copy of the deal):

```kotlin
fun extractAcceptorAddrFromTx(acceptTx: iTransaction, creatorAddress: String): String? {
    val contractOutput = acceptTx.outputs[0]
    val tmpl = contractOutput.script.parseTemplate(contractOutput.amount) ?: return null
    val argDatas = tmpl.rest.map { scriptDataFrom(it) }     // visible args, in declaration order
    val holderAHash = argDatas[0] ?: return null
    val holderBHash = argDatas[1] ?: return null
    val creatorHash = extractArgsHash(creatorAddress)
    val acceptorHash = when {                               // the holder hash that ISN'T the creator
        holderAHash.contentEquals(creatorHash) -> holderBHash
        holderBHash.contentEquals(creatorHash) -> holderAHash
        else -> return null
    }
    return p2pktAddressFromHash(acceptorHash, contractOutput.script.chainSelector)
}
```

Two details worth noting: (1) here `parseTemplate` is passed the output **amount**
(`contractOutput.amount`), not `0` — the native amount participates in parsing a funded
output, whereas `extractArgsHash` above passes `0` because it only needs the address's
structural template; (2) `tmpl.rest` holds the visible args in the exact order your NPL
`holderPublicArgs` declared them, so positional reads must match that declaration order (see
`nexa-npl-smart-contracts`). Compare hashes with `contentEquals`, never `==`.

This Kotlin-side `tmpl.rest` read has an exact in-VM counterpart: a contract rule can read the
same positional visible args during script execution via `getOutputVisibleArg(outIdx, n)` /
`parseOutputArg(outIdx, 8+n)` (OP_PARSE fields 8+). See `nexa-npl-smart-contracts` Pattern 9 —
the field numbering and 0-based-vs-raw-field nuance live there. The two paths read identical
bytes in identical order, so a value you validate server-side here will match what the
contract checks on-chain.

### Defining the dual-address helpers on the server

```kotlin
/** Stable login identity (signature-verified).  Use ONLY for auth/ownership checks. */
fun walletIdentityOf(session: AppSession): String? =
    session.identity.value?.takeIf { it.isNotBlank() }

/** Current P2PKT receive address (rotates).  Use ONLY for on-chain destinations.
 *  Capture this at record-creation time and pin it to the record. */
fun walletPayoutAddressOf(session: AppSession): String? =
    session.userNexaAddress.value.takeIf { it.isNotBlank() }
```

### A record that captures both

```kotlin
class ListingRecord(
    val id: String,
    /** Stable login identity -- this is what 'is this MY listing?' compares against. */
    val sellerIdentity: String,
    /** P2PKT receive address, snapshot at create time.  This is what the contract pays
     *  out to.  Does not move even if the seller's wallet rotates addresses later. */
    val sellerPayoutAddress: String,
    val priceNexa: Long,
    // ...
) {
    @Volatile var buyerIdentity: String? = null
    @Volatile var buyerRefundAddress: String? = null    // also captured at buy time
    // ...
}
```

### Validating addresses up front in route handlers

```kotlin
post("/api/listings/create") {
    val session = sessionHandler?.findSession(call) as? AppSession
        ?: return@post call.respond(HttpStatusCode.Unauthorized, "no session")

    val sellerIdentity = walletIdentityOf(session)
        ?: return@post call.respond(HttpStatusCode.Unauthorized, "log in with your wallet first")

    val sellerPayout = walletPayoutAddressOf(session)
        ?: return@post call.respond(HttpStatusCode.Conflict,
            "no payout address shared -- reconnect your wallet so it can /share a receive address")

    // Verify it's actually a P2PKT, because the contract requires it.  Fail loudly here
    // so the user gets a clear error instead of an opaque on-chain script-verify failure.
    try { extractArgsHash(sellerPayout) }
    catch (e: Throwable) {
        return@post call.respond(HttpStatusCode.BadRequest,
            "shared address is not P2PKT (got: $sellerPayout, type unrecognized)")
    }

    // ... create the record using both sellerIdentity and sellerPayout ...
}
```

### Filtering "my activity" by identity, not payout address

```kotlin
fun refreshMySalesView(session: AppSession) {
    val identity = walletIdentityOf(session) ?: return
    val mine = listings.values.filter { it.sellerIdentity == identity }
    session.mySales.value = MySalesPage(mine.map { it.toView() })
}
```

This is correct **even after `userNexaAddress` rotates**, because we keyed off the stable
identity.

## Common mistakes and anti-patterns

### Using identity as the on-chain destination

**Wrong**:
```kotlin
val recipientAddr = session.identity.value!!
val argsHash = extractArgsHash(recipientAddr)   // throws: not P2PKT
```

**Right**:
```kotlin
val recipientAddr = session.userNexaAddress.value
require(recipientAddr.isNotBlank()) { "no payout address shared" }
val argsHash = extractArgsHash(recipientAddr)   // works
```

### Using payout address for ownership checks

**Wrong**:
```kotlin
if (listing.sellerAddress != walletPayoutAddressOf(session)) {
    return@post call.respond(HttpStatusCode.Forbidden, "not the seller")
}
// One spend later, sellerPayoutAddress rotates and the real seller is locked out.
```

**Right**:
```kotlin
if (listing.sellerIdentity != walletIdentityOf(session)) {
    return@post call.respond(HttpStatusCode.Forbidden, "not the seller")
}
```

### Treating `PayAddress(str).lockingScript()` as the way to get the argsHash

**Wrong**:
```kotlin
val argsHash = PayAddress(addr).lockingScript().toByteArray().sliceArray(...)  // brittle, wrong
```

**Right**: parse the script template properly. `parseTemplate(0)` is the public API.

```kotlin
val tmpl = PayAddress(addr).lockingScript().parseTemplate(0)
    ?: throw IllegalArgumentException("not a P2PKT address: $addr")
val argsHash = tmpl.argsHash ?: throw IllegalArgumentException("template has no argsHash")
```

### Comparing wallet identities case-insensitively or with whitespace

NEXA addresses are case-sensitive within their cashaddr alphabet. Don't trim, don't
lowercase, don't normalize — compare verbatim:

```kotlin
// Right
if (listing.sellerIdentity == identity) { ... }
```

### Assuming `session.identity.value` is non-null after a "connected" event

It can be null in three cases:
1. The session used `connectSvg` (no identity) instead of `loginSvg`.
2. The user disconnected the wallet — `disconnectWallet()` resets identity to null.
3. The session just became active and login is still in flight.

Always null-check before using identity in any decision.

### Letting the user submit their own "payout address" as a form field

**Wrong**: route accepts `payoutAddress` from the request body.
```kotlin
data class CreateListingRequest(val title: String, val priceNexa: Long, val payoutAddress: String)
```
*Allows a malicious user to point payouts at someone else, OR at a malformed address that
silently black-holes funds.*

**Right**: only ever read the payout address from `session.userNexaAddress.value`, which
was populated by the wallet's own `/_share` POST.

### Looking up a session by argsHash

**Wrong**: keying session lookup by `extractArgsHash(session.userNexaAddress.value)`.
The argsHash rotates with the address. Use the browser session cookie or `identity` for
durable identity.

## Security considerations

- **Validate every external address with `PayAddress(str)` early.** The constructor
  throws on malformed cashaddr strings (`PayAddressBlankException` for empty, and other
  exceptions for bad checksums). Doing this once at ingestion is much safer than
  discovering a malformed address inside a tx-construction code path.
- **Treat `userNexaAddress.value` as untrusted input** until you parse it through
  `PayAddress(str).lockingScript().parseTemplate(0)`. Wally is usually trustworthy but
  the API contract gives the wallet a free-form string and you should not assume it's
  well-formed.
- **Never expose `session.identity.value` to other users** unless your app explicitly
  treats identity as public (e.g., a marketplace where sellers are identified by their
  identity hash). On the wire it can serve as a stable user id; in the UI it's an
  address that links to the user's signing key, which is often privacy-sensitive.
- **The reconstructed-from-argsHash address** (`p2pktAddressFromHash`) gives a *valid*
  address for the constraint script, but if the original address had additional script
  metadata (a different `groupInfo`, hidden-args, etc.), the reconstruction will not be
  identical. Use it for display, not for authentication.
- **An argsHash collision** between two different scripts is computationally infeasible
  (it's a hash160), so treating argsHash equality as "same recipient" is safe in
  practice.

## Related skills and references

- `nexa-wallet-lifecycle-and-chain` — the same payout-vs-identity split for a wallet your *own* code
  holds: `getNewAddress()` (rotating P2PKT payout) vs `destinationFor(COMMON_IDENTITY_SEED)`
  (stable identity), as opposed to the external-Wally `session.*` fields this skill centers on.
- `nexa-wallet-connection` — how `session.identity` and `session.userNexaAddress` get
  populated in the first place.
- `nexa-transaction-construction` — how to build outputs targeting a P2PKT address
  (`PayAddress(addr).lockingScript()`).
- `nexa-npl-smart-contracts` — how `getOutputArgsHash(0.nx)` on the script side relates
  to `extractArgsHash` on the server side (they return the same 20 bytes).
- `nexa-tokens-and-groups` — a *third* address-type concept: `PayAddressType.GROUP` denotes a
  native-token *type*, not a payable destination (calling `lockingScript()` on it throws).
  You still pay tokens to a P2PKT/TEMPLATE address; the group rides on that address's script.

### Supporting files in this folder

- `addressTypesTable.md` — full table of `PayAddressType` enum values, what each one's
  `lockingScript()` produces, and which ones support `parseTemplate` / `groupedLockingScript`
  (with the `ScriptTemplate` fields recovered from a P2PKT output).
- `validateHelpers.kt` — drop-in `requireP2PKT(addr)` / `requireP2PKH(addr)` /
  `requireLooksLikeNexaAddress(addr)` guards built on the real `PayAddress` API (these are
  app-level helpers, not library functions).