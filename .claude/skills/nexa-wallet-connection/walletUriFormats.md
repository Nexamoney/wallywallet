# Wallet URI formats (TDPP / nexid)

A consolidated reference for the URI shapes the Wally wallet accepts, the libnexaapp builders that
produce them, and the `flags` bitfield. Read `SKILL.md` first for the protocol semantics (the
per-`op` callback shapes, the nexid login-signature format, request signing, Trickle Pay) — this
file is the format lookup. Grounded in the DPP spec (`https://spec.nexa.org/dpp/`) and libnexaapp
`tdpp.kt`; flag bit values cross-checked against the spec.

## Two envelopes for the same request

Every push has two equivalent forms; `uriVariant` on the builders selects between them:

| Form | Shape | When |
| --- | --- | --- |
| **Universal/deep link** (default) | `https://w.nexa.org/tdpp/<requestingDomain>/<op>?…` | works on devices without the raw scheme handler registered; the web layer bridges to the wallet |
| **Raw scheme** | `tdpp://<host>/<op>?…` (or `nexid://<host>/_identity?…`) | when the wallet's URI scheme is registered on the device |

The deep-link host for Nexa is `w.nexa.org`. `tx.createTdppUrl(...)`
(`nexa-transaction-construction`) builds the `tx` push and lets you pass `applinkDomain = null` for
the raw `tdpp://` form.

> **`sendPaymentUri`'s `uriVariant` polarity is inverted** relative to the other builders: its
> default is the raw BIP21 form, not the universal link. The others default to the universal link.

## Operations (`op`)

The DPP path segment after the domain selects the operation. The wallet POSTs/GETs your callback
per op (see `SKILL.md` "What the wallet sends back on each callback" for the reply shapes):

| `op` | Purpose | Wallet → your callback |
| --- | --- | --- |
| `reg` | register a Trickle Pay domain (hands-free pay) | POST JSON `{op,addr,sig,cookie}` |
| `sendto` | request a payment to an address | POST JSON `{resultCode,txid,txidem,tx,error}` |
| `tx` | push a (partial) tx for the wallet to complete | GET your `/tx` callback with the final hex |
| `assets` | prove the wallet holds a token/NFT | POST JSON `{assets:[{outpointHash,amt,prevout,proof}]}` |
| `share` | share the wallet's address | POST the plain-text address |
| `login` (nexid) | authenticate an identity | GET your callback with the signed challenge (`sig=`, or `ctxsig=` — a signed challenge tx; servers must accept either per the spec) |
| `jsonpay` | complete a BitPay-style JSON-payment-protocol dialog (`?uri=<url>`) | per that protocol; `resultcode=200` on completion |

Rejections call back too: a denied request produces a best-effort callback with
`resultcode=300` and no payload (a `/tx` GET with no `tx` parameter is a user rejection, not a
malformed request); `resultcode=200` marks acceptance where sent. See `SKILL.md` § "What the
wallet sends back on each callback".

## libnexaapp builders (server side)

Verified signatures from `tdpp.kt` (all return the URI `String`):

```kotlin
fun connectWalletUri(id: String, serverPrefix: String? = null, uriVariant: Boolean = false): String
fun loginWalletUri(session: NexaAppSession, connectWallet: Boolean = true, proto: String? = null,
                   requiredInfo: ESet<NexId>? = null, optionalInfo: ESet<NexId>? = null,
                   uriVariant: Boolean = false): String
fun requestAssetsUri(filter: ByteArray, assetChallenge: ByteArray? = null, serverPrefix: String? = null,
                     uriVariant: Boolean = false, sessId: String): String
fun requestAssetsUri(filterHex: String, assetChallengeHex: String? = null, serverPrefix: String? = null,
                     uriVariant: Boolean = false, sessId: String): String
// BIP21 payment. NOTE inverted polarity: default is the raw `nexa:<addr>?amount=<qty>` form
// (qty is whole NEXA, a BigDecimal), uriVariant=true is the universal-link form.
fun sendPaymentUri(address: PayAddress, quantity: BigDecimal, label: String? = null,
                   message: String? = null, uriVariant: Boolean = false): String
```

(The partial-tx push URI is built with
`iTransaction.createTdppUrl(requestingDomain, tdppFlags, applinkDomain)` —
`nexa-transaction-construction`.) The library provides **no** `/tx` *callback route* — that's app
code; only the push *URI* has a builder.

`loginWalletUri`'s `requiredInfo`/`optionalInfo` select **identity-info fields** to request from
the wallet — they emit `<field>=<level>` query params (level `m`/`r`/`o` = mandatory/recommended/
optional; the default URI's `hdl=m` is "handle, mandatory"). The field vocabulary is
libnexakotlin's `nexidParams`: `attest`, `ava` (avatar), `billing`, `dob`, `email`, `hdl`
(handle), `realname`, `ph` (phone), `postal`, `sm` (social media). See `SKILL.md` § "What
`hdl=m` means".

## The `tx` `flags` bitfield

A TDPP wire bitfield both ends agree on.
**Importable from libnexakotlin**: all six are documented top-level `const val`s in
`org.nexa.libnexakotlin` (`utils.kt`, common code) — `import org.nexa.libnexakotlin.TDPP_FLAG_NOFUND`
etc. Bit values (cross-checked against the
DPP spec):

| Flag | Bit / value | Effect |
| --- | --- | --- |
| `NOFUND` | `1` | don't add native inputs; `inamt` required when clear |
| `NOPOST` | `2` | sign but don't broadcast — return via `/tx` |
| `NOSHUFFLE` | `4` | preserve input/output order (offer/swap protocols) |
| `PARTIAL` | `8` | multi-party / incomplete tx |
| `FUND_GROUPS` | `16` | also contribute token inputs |
| `HIDE_ASSET_DETAILS` | `32` | approval-UI presentation hint |

Canonical partial-offer combination: `NOFUND|NOPOST|NOSHUFFLE|PARTIAL` = **15** (the half-tx swap
idiom, `nexa-transaction-construction`). The optional `&reason=<url-encoded>` `tx` param is shown on
the wallet's approval screen.

## Request signing (canonicalization)

For a signed request (Trickle Pay / `reg`), the canonical string to sign is, per the spec:

1. **omit** the `sig` parameter,
2. **sort** the remaining params alphanumerically by key (keys must not repeat),
3. **URL-encode** the values — *form* encoding per the spec (space is `+`, not `%20`),
4. join and sign; the signature is Nexa-standard **base64** (standard alphabet; a
   future-proof decoder retries a failed decode against the RFC 4648 base64url alphabet).

A receiver re-canonicalizes the same way before verifying. (`SKILL.md` "Signing your TDPP
requests" has the full rule and the nexid login-signature format
`<host><portString>_nexid_<op>_<challenge>`.)

## Push vs route path asymmetry

The wallet is *pushed* URIs with paths `/lp`, `/share`, `/identity`; the server *routes* are
`/_lp`, `/_share` (app), `/_identity` — and it is the **wallet itself** that maps op → callback
path: it hardcodes `/_lp` for the long-poll and `/_share` for the share callback, while the nexid
callback simply reuses **the path carried in the nexid URI** (`loginWalletUri` puts `/_identity`
there). The other callbacks (`/sendto`, `/address`, `/assets`, `/tx`) keep the op's own path, no
underscore. (The universal-link/deep-link layer plays no part in this mapping — it only unwraps
`http(s)://<any-host>/<scheme>/<rest>` into `<scheme>://<rest>`; any host works, `w.nexa.org` is
convention, not mechanism.) Both the cheat sheet (`/lp`) and the route list (`/_lp`) in `SKILL.md`
are correct; don't "fix" either.

## Additional per-op request parameters (wallet-verified)

- **`/sendto`** — numbered destination pairs `amt0`/`addr0`, `amt1`/`addr1`, … with each `amtN`
  in **satoshis** (finest unit; unlike BIP21 `?amount=`, which is whole NEXA). `chain=` is
  mandatory; numbering must start at 0 and increment, and N is **not** a promise of the tx
  output index (use the `/tx` op when exact output positions matter). All addresses must
  be on one chain. Insecure (`http` + no valid `sig`) sendto requests are rejected outright.
- **`/address`** — `blockchain=<chain-uri>` (defaults to mainnet; note the spelling — not
  `chain=`), `unique=true` for a fresh address; default returns the wallet's stable per-domain
  "main pay address".
- **`/reg`** — `topic`, `addr` (your signing address, pinned to the domain), `uoa`, and limit
  pairs `maxper`/`descper`, `maxday`/`descday`, `maxweek`/`descweek`, `maxmonth`/`descmonth`
  (finest-unit amounts). Requires a valid `sig`. Re-registration takes the larger of existing vs
  requested limits.
- **`/tx`** — `tx=<hex>` or `tx64=<base64url>` (the compact form for large partial txs);
  `inamt` (required unless `NOFUND`), `flags`, `reason`, `cookie`, `chain`.
- **All ops** — `cookie` (correlation, echoed back), `topic` (domain sub-key), `rproto` (reply
  protocol; **defaults to `http`** when absent on a raw `tdpp://` push — set `rproto=https` on
  TLS servers), `rpath` (reply-path override: a bare path or `//host/path`), `reason`
  (approval-screen text), `sig`+`addr` (request signature).
- **`/assets`** — `chalby` must decode to **8–64 bytes**, or the wallet enumerates with no
  proofs (null `proof` fields).

## Related

- `SKILL.md` — per-op callback reply shapes, nexid signature verification, Trickle Pay, security.
- `flowchart.md` — the end-to-end login + buy + sign message sequence.
- `qrRouteTemplate.kt` — serving a connect/login QR (with the onclick XML-escape).
- `nexa-transaction-construction` — `createTdppUrl` and the half-tx swap offer.