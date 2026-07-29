# Token metadata: the token-description document (TDD) and genesis commitment

The wire/format detail behind a group's human-facing metadata: the off-chain token-description
document (TDD), how the genesis transaction commits to it on-chain, the signature canonicalization,
and the reserved-ticker rules. Read `SKILL.md` Pattern 7 first for the genesis/definition DSL and
the key facts (metadata is off-chain; `decimal_places` is on-chain; identify tokens by group id) —
this file is the exact-format companion you need when actually issuing a token and authoring its
metadata. Grounded in the Nexa spec (`https://spec.nexa.org/tokens/tokenDescription/`,
`/tokens/nft/`).

## Off-chain token metadata (the token-description document)

A group's human-facing metadata — ticker, name, summary, icon — lives in an off-chain
**token-description document (TDD)** referenced from the genesis, *not* on-chain (the on-chain
genesis OP_RETURN carries `decimal_places` and the document's hash; see below). The document is a
JSON array of two elements: a metadata object followed by a base64 signature over it, e.g.

```json
[
  { "ticker": "EXMPL", "name": "Example Token", "summary": "…", "icon": "/td/example.svg" },
  "IHd…base64-signature…="
]
```

The signature lets a client confirm the document was issued by the genesis signer. NFTs (single
units) typically omit `decimal_places`.

The Nexa spec (`https://spec.nexa.org/tokens/tokenDescription/`) defines the *richer* TDD
dictionary — `ticker` (≤6 chars, required), `name`, `summary`, `description`, `legal` (inline
contract text, never a link), `creator`, `contact`, `icon`, `category` — and is strict about the
signature: it is the `signmessage`-style signature (the same `"Bitcoin Signed Message:\n"` +
double-SHA256 + base64 scheme the nexid login uses) over the **exact bytes of the dictionary, brace
to brace** — *do not* JSON-parse and re-serialize before checking it, or whitespace changes break
the hash and signature.

> **Where `decimal_places` actually lives.** Per the spec, `decimal_places` is **not** a field of
> the TDD JSON dictionary — it is committed on-chain in the genesis OP_RETURN (see below), and that
> is what `TokenGenesisInfo.decimal_places` reflects. (Some apps may *also* echo it into their
> served TDD, but the authoritative source is the genesis record.) If absent, decimals default to
> **0**; clients should support 0–18. Identify a token only by its group id, never by ticker/name —
> tickers are not unique, and the spec recommends clients refuse to display reserved tickers (NEX,
> KEX, MEX, ISO-4217 currency codes, exchange symbols) to avoid impersonation.

## How the document binds to the group at genesis

The genesis transaction that creates the group commits to the metadata in an **OP_RETURN output**
whose first push is the type tag `88888888` (the token-description record), followed by
individually-pushed fields in this order: `<ticker> <name> <URI> <SHA-256 of the TDD dictionary>
[decimal_places]` (a field may be `OP_FALSE` for "empty"; `decimal_places` is encoded as a script
number). A client fetches the document at the URI and re-hashes its exact bytes against that
committed SHA-256 to verify it has not been tampered with. The genesis input signature **should
cover both the OP_RETURN and the token-authority output** (sign the whole tx with ALL/ALL) so a
third party cannot malleate the metadata before the tx confirms. (An NFT/SFT data file is committed
separately in a second OP_RETURN tagged `88888889`, carrying the work's title, the
**double-SHA256 of the .zip**, and its URL — see `https://spec.nexa.org/tokens/nft/`.) Token genesis
is typically performed with the full node's token tooling rather than from app code — e.g. a
`token new <issuerAddress> <ticker> <name> <documentUrl> <sha256OfDocument>` node-CLI call, which
returns the new `groupIdentifier`, the genesis `transaction`, and a `tokenDescriptorSigningAddress`
— the address you must `signmessage`-sign the TDD with (it is the address the genesis authority was
locked to, so its signature proves the same entity created both the group and the document). The same
genesis is available programmatically through the `org.nexa:nexarpc` client as
`rpc.tokenNew(address, tokenTicker, tokenName, descUrl, descHash, decimals)` (returning
`(groupIdentifier, genesisTxid)`) — convenient for scripted issuance and tests; see
`nexa-rpc-node-client`. A frequent operational trap: editors append a trailing newline to the JSON,
so the SHA-256 you pass at genesis must be of the **exact bytes** served later (strip the trailing
newline before hashing, or the on-chain commitment won't match the served file and clients will
reject the metadata). The two-element `[doc, sig]` array above is the *served* form; the genesis
commitment is over the metadata object's bytes.

## The libnexakotlin types and helpers for this format (`token.kt`)

You rarely need to hand-parse or hand-build these documents — libnexakotlin ships the typed
surface:

- **`TokenDesc`** — the typed TDD dictionary (`ticker` required; `name`/`summary`/`description`/
  `legal`/`creator`/`category`/`contact`/`icon`, NFT extensions `nftId`/`nftUrl`), plus derived
  fields filled at decode time: `signedSlice` (the exact brace-to-brace bytes), `tddHash` (their
  SHA-256 — compare to the genesis `document_hash`), `tddSig`, `pubkey` (recovered from the
  signature), and `genesisInfo: TokenGenesisInfo?`.
- **`decodeTokenDescDoc(s)`** parses the served `[dict, "sig"]` array, slices the exact dictionary
  bytes, hashes them, and recovers the signer pubkey. `TokenDesc.signedBy(address): Boolean?`
  answers "did this address sign it?" (works for P2PKH and P2PKT genesis addresses; `null` =
  undeterminable).
- **`TokenDesc.makeTokenDescriptionDoc(wallet, genesisAddr)`** produces the fully-signed served
  form (the wallet must hold `genesisAddr`'s key) — the programmatic alternative to hand-signing.
- **`GroupDescriptor(ticker, name, docUri, doc, decimals)`** builds the genesis side:
  `buildGenesisData()` emits the `88888888` genesis OP_RETURN script exactly as specified above,
  and `buildTokenDescriptionDoc/File(wallet, genesisAddr, …)` writes the signed TDD. (NPL declares
  a `GroupDescriptor` with the same shape for its genesis DSL — see
  `nexa-npl-smart-contracts/dslReference.md` §1.)
- **`getTokenInfo(grpId, getEc, cnxnMgr)`** is the end-to-end read path (genesis info from a
  token-info-capable node or electrum → fetch TDD at `document_url` → decode + signature check) —
  see `SKILL.md` Pattern 7, including the **subgroup→`parentGroup()`** rule.

## Related

- `SKILL.md` Pattern 7 — the genesis/definition DSL (`group("…") { mint(...) }`) and the key facts
  this file gives the exact format for.
- `nexa-rpc-node-client` — the node token tooling (`tokenNew` / `token new`) that performs genesis.
- `nexa-electrum-monitoring` — `getTokenGenesisInfo(groupId)` reads back the committed
  `decimal_places`/ticker/name from a light client (call it on the **parent** group for a subgroup
  NFT).