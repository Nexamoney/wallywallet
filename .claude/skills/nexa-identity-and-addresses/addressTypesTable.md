# PayAddressType reference

The full `PayAddressType` enum, what each type's `lockingScript()` produces, and which types
support `parseTemplate` / `groupedLockingScript`. This is the lookup table behind `SKILL.md`'s
P2PKH-identity vs P2PKT-payout distinction; read the skill first for *why* you split identity from
payout. Grounded in libnexakotlin `libnexa.kt` (the enum) and `payAddress.kt` (the behavior).

## The enum

```kotlin
enum class PayAddressType(val v: Byte) {
    NONE(255.toByte()),
    P2PUBKEY(2),
    P2PKH(0),
    P2SH(1),
    GROUP(11),
    TEMPLATE(19),   // generalized pay-to-script-template
    P2PKT(19);      // pay-to-pub-key-template — SAME byte value (19) as TEMPLATE
}
```

`PayAddressType.isValid()` returns true only for the byte values above.
**`P2PKT` and `TEMPLATE` share byte `19`** — P2PKT is the well-known pay-to-pub-key-template
specialization of the generalized template type, so the two are the same on the wire and behave
identically in `lockingScript()` / `parseTemplate()`.

## What `lockingScript()` produces per type

`PayAddress.lockingScript(): SatoshiScript` switches on `type`:

| Type | `lockingScript()` result |
| --- | --- |
| `P2PKH` | `SatoshiScript.p2pkh(data)` = `OP_DUP OP_HASH160 <20-byte hash> OP_EQUALVERIFY OP_CHECKSIG` |
| `P2SH` | `SatoshiScript.p2sh(data)` = `OP_HASH160 <20-byte hash> OP_EQUAL` |
| `TEMPLATE` / `P2PKT` | a `Type.TEMPLATE` script built from the address `data` (the serialized template) |
| `NONE` | **throws** `WalletNotSupportedException` ("Cannot create payment unconstrained by an address") |
| `P2PUBKEY` | **throws** `WalletNotSupportedException` ("Pay to public key outputs not supported") |
| `GROUP` | **throws** `WalletNotSupportedException` ("This denotes a token type, not an address") |

So only **P2PKH, P2SH, TEMPLATE/P2PKT** are payable destinations. `NONE`/`P2PUBKEY` are not
supported as outputs, and **`GROUP` is not an address at all** — it denotes a token *type* (a
`GroupId`), which is the "third concept" `SKILL.md` warns about: don't try to pay *to* a `GROUP`
PayAddress; carry the token group as a `GroupId` on a template output instead (see
`nexa-tokens-and-groups`).

## Which types support `parseTemplate`

`SatoshiScript.parseTemplate(nativeAmount: Long): ScriptTemplate?` returns **null** unless the
script is `Type.TEMPLATE` (the first line is `if (type != Type.TEMPLATE) return null`). So:

| Type | `parseTemplate` | `groupedLockingScript(grp, qty)` |
| --- | --- | --- |
| `TEMPLATE` / `P2PKT` | ✅ returns a `ScriptTemplate` | ✅ rebuilds the template output bearing the group |
| `P2PKH`, `P2SH`, `P2PUBKEY`, `NONE`, `GROUP` | ✗ (null / not a template) | ✗ **throws** `WalletNotSupportedException` |

`ScriptTemplate` (what a successful `parseTemplate` yields):

```kotlin
data class ScriptTemplate(
    var groupInfo: GroupInfo?,      // token group + amount, or null for native coin
    val templateHash: ByteArray?,   // the template script hash (or null if a well-known id is used)
    val wellKnownId: Long?,         // e.g. the P2PKT well-known id (or null if a templateHash is used)
    val argsHash: ByteArray?,       // hash of the hidden/constraint args (null if none)
    val rest: List<ByteArray>)      // the visible constraint args (holderPublicArgs), in order
```

This is how you recover the **args hash** (the identity/payout commitment) and the **visible
args** from an on-chain P2PKT output — the basis of the `extractArgsHash` recovery pattern in
`SKILL.md`. The `nativeAmount` argument only affects how `groupInfo` is computed; pass `0` when you
just want the template structure.

## The identity vs payout split, in these terms

- **Identity (auth):** a stable **P2PKH** address — the signature-verified login identity. Use it
  only for authentication/ownership checks. `parseTemplate` does **not** apply (it's not a
  template).
- **Payout (rotating):** a **P2PKT** template address. `parseTemplate` *does* apply, so a contract
  can read its args hash on-chain, and `groupedLockingScript` can re-issue it bearing a token
  group.
- **GROUP:** neither — a token type. See `nexa-tokens-and-groups`.

## Constructing / parsing addresses

```kotlin
PayAddress(address: String)             // parse from a cashaddr string (throws on bad/blank input)
PayAddress(stream: BCHserialized)       // deserialize
payAddress.toString(): String           // encode back to cashaddr (throws if type invalid / bad data)
payAddress.type                          // the PayAddressType
payAddress.chainSelector                 // == blockchain
payAddress.contentEquals(other)          // compares chain + data (ignores type — types can be specializations)
```

Parse failures throw `PayAddressDecodeException` / `PayAddressBlankException` (both
`PayAddressException`). See `validateHelpers.kt` for drop-in guards.

> There is **no** library `p2pktAddressFromHash` / `requireP2PKT` function — those are app-level
> helpers. `SKILL.md` defines its own; `validateHelpers.kt` provides drop-in versions built on the
> `PayAddress` API above.

## Related

- `SKILL.md` — the identity/payout model, `extractArgsHash`, reading addresses from on-chain args.
- `validateHelpers.kt` — `requireP2PKT` / `requireLooksLikeNexaAddress` guards.
- `nexa-tokens-and-groups` — the `GROUP` token-type concept and `groupedLockingScript`.