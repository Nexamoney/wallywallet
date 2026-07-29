---
name: nexa-wallet-connection
description: "Connects a Kotlin/KMP app to the Wally Nexa wallet via the TDPP/nexid protocol: generating wallet-connect QR codes, handling the wallet long-poll, and wiring sign-in identity. Use when letting a user connect their Nexa wallet, logging in with Nexa identity, running an asset-ownership challenge, or debugging a QR that won't connect or a pushed payment request the wallet never sees. Triggers: Wally, TDPP, tdpp://, nexid://, /_lp, /_identity, /_share, pushToWallet, connectWallet, loginWalletUri, NexaAppSession, SessionHandler, /assets, /tx, TDPP_FLAG_*, resultcode=300, resultcode 201/203/204, ctxsig, jsonpay, unknown session, tx64, rproto, rpath, /sendto amt0/addr0, long-poll A/Q protocol, topic, wallet multi-account/domain binding. Stops at producing a tdpp:// URL. Not for choosing which address to use (nexa-identity-and-addresses) or building the tx to push (nexa-transaction-construction)."
---

# Nexa wallet connection (Wally / TDPP protocol)

## When to use this skill

Trigger when a developer is connecting a Kotlin/KMP app to the Wally NEXA wallet,
generating wallet-connect QR codes, handling wallet long-poll, or wiring up sign-in
identity. Concretely trigger on:

- Keywords: Wally, TDPP, tdpp://, nexid, nexid://, wallet long poll, `/_lp`,
  `/_identity`, `/_share`, `pushToWallet`, `connectWallet`, `loginWalletUri`,
  `NexaAppSession`, `SessionHandler`, "connect wallet QR", `/assets`, `/tx`,
  `TDPP_FLAG_*`, `NOSHUFFLE`, asset-ownership challenge, "what tokens does the wallet hold",
  `resultcode=300` / "wallet rejected the request", the finer DPP result codes (`201` missing
  sig / `203` not final / `204` cannot post), `ctxsig` (challenge-transaction login reply),
  `jsonpay`, "unknown session", `tx64`, `rproto`, `rpath`,
  `/sendto` (`amt0`/`addr0`), `topic` / per-domain registration, "wallet shows an error but
  the tx went through", the `A`/`Q` long-poll protocol, `/api/wallet/tdpp` / `/api/wallet/assets`
  / `/api/wallet/connectText` (browser-facing trigger routes), `allowWalletConnection`
  ("the wallet keeps getting disconnected after I disconnect it once").
- Tasks: "let the user connect their NEXA wallet", "log in with Nexa identity",
  "the wallet QR scans but won't connect", "QR code is blank", "wallet doesn't see
  the payment request I pushed", "implement the wallet routes without libnexaapp /
  in a non-Kotlin server", "send a TDPP request by QR only (no connection)",
  "the wallet stopped polling — is the user gone?".
- Files touched: any server-side Ktor route registration involving `installWalletRoutes`;
  any client-side Compose component that fetches `/api/wallet/*` endpoints.

**Negative triggers** — do NOT use this skill for:
- Choosing what address to *use* once connected — that's `nexa-identity-and-addresses`.
- Constructing transactions to push to the wallet — that's `nexa-transaction-construction`.
  This skill stops at "you have a `tdpp://` URL to push"; the other skill is how to build
  that URL.
- CORS / Ktor plumbing beyond the wallet protocol — that's `nexa-ktor-server-integration`.

## Mental model

Wally is a **separate process on a separate device** (typically a phone). It talks to
your server over plain HTTP. There is no WebSocket between Wally and your server. The
session model is the user's *browser* session — Wally is just a peripheral that the
browser-session uses to sign things.

The protocol has two channels:

1. **Server → Wallet (push)**: your server enqueues a URI on a per-session channel.
   Wally is long-polling `/_lp` and receives the URI. It then *acts* on the URI:
   navigating the user through approval, signing whatever it asks for, then doing one
   more callback to your server with the result.

2. **Wallet → Server (callback)**: Wally makes a fresh HTTP request (GET or POST) to a
   well-known route. The URI we pushed in step 1 contains a `cookie=<id>` query parameter
   that Wally echoes back in the callback so your server can correlate.

The URIs use two custom schemes that the OS routes to Wally:

- `tdpp://host/path?params` — Trickle-DPP protocol. Used for general wallet actions
  (long-poll, share an address, send a payment, complete a partial tx).
- `nexid://host/path?params` — Nexa Identity (`nexid`) flow. Used only for the
  signature-based login challenge.

Wally needs to be able to reach your server's `host:port`. **The `host` in every URI
must be a network address Wally can reach** — typically your machine's LAN IP, not
`localhost`, not `127.0.0.1` (unless Wally is on the same device).

A common confusion vs other chains: there is no "WalletConnect", no "Sign in with
Ethereum", no off-chain JSON-RPC bridge. The wallet protocol is purely URI push + HTTP
callback. If you understand BIP21 (`bitcoin:addr?amount=N`) you understand the simple
case; the TDPP additions just let the wallet call back when it's done.

## Setup and versions

You need `libnexaapp` (provides `installWalletRoutes`, sessions, push helpers, QR generation).
Pin to the current published version — see `nexa-project-setup` for the registry URL and pinning
rules.

Server-side Ktor must have these installed:

```kotlin
install(WebSockets) { /* for client-side flowConnector, not wallet-side */ }
install(CORS) { /* see nexa-ktor-server-integration */ }
install(ContentNegotiation) { cbor(); json(...) }
```

In your `routing { }` block, libnexaapp installs the wallet routes:

```kotlin
routing {
    installWalletRoutes(EXTERNAL_URL, MyAppSessionHandler)
    // ...your own routes
}
```

`installWalletRoutes` registers everything Wally needs **except** the `/tx` callback —
that one you register yourself.

## Core patterns

### A complete `NexaAppSession` subclass

Holds per-browser-session state. The framework constructs one per browser tab.

```kotlin
package myapp

import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexaapp.BasicSessionHandler
import org.nexa.libnexaapp.ExternalUrl
import org.nexa.libnexaapp.NexaAppSession
import org.nexa.libnexakotlin.chainToURI

class AppSession(id: String) : NexaAppSession(id) {
    val userNexaAddress = MutableStateFlow("")   // populated by /_share POST

    override fun onWalletConnected() {
        // Wally just completed the long-poll handshake.  Ask it to share a receive
        // address so we know where to send the user funds later.
        val myproto = ExternalUrl.substringBefore(':')        // "http" or "https"
        val req = ExternalUrl
            .replace("https", "tdpp")
            .replace("http", "tdpp") +
            "/share?info=address&chain=${chainToURI[DEFAULT_CHAIN]}&cookie=$id&rproto=$myproto"
        pushToWallet(req)
    }
}

object AppSessionHandler : BasicSessionHandler<AppSession>({ AppSession(it) }) {
    override fun event(event: String, session: NexaAppSession) { /* connect/disconnect events */ }
}
```

### Wire the wallet's address-share POST

`installWalletRoutes` does NOT register a handler for the address share callback because
you choose the field. Add it yourself:

```kotlin
routing {
    installWalletRoutes(EXTERNAL_URL, AppSessionHandler)

    post("/_share") {
        val session = sessionHandler!!.findSession(call) as? AppSession
        if (session != null) session.userNexaAddress.value = call.receiveText()
        call.respondText("ok")
    }
    post("/address") {                          // some Wally builds POST here instead
        val session = sessionHandler!!.findSession(call) as? AppSession
        if (session != null) session.userNexaAddress.value = call.receiveText()
        call.respondText("ok")
    }
}
```

**Harden the handler:** the body Wally POSTs is an untrusted free-form string. A
production-ready `/_share` handler validates the address prefix before storing it and
correlates the session by the `cookie` query parameter:

```kotlin
post("/_share") {
    val cookie = call.parameters["cookie"]
        ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing cookie parameter")
    val sharedData = call.receiveText().trim()
    if (!isValidNexaPrefix(sharedData))                       // must start with nexa:/nexatest:
        return@post call.respond(HttpStatusCode.BadRequest, "Address must start with nexa: or nexatest:")
    val sess = AppSessionHandler.findSession(cookie) as? AppSession   // cookie param, not header
        ?: return@post call.respond(HttpStatusCode.NotFound, "Unknown session or request")
    sess.userNexaAddress.value = sharedData         // your app-defined per-session field
    call.respondText("OK")
}
```

`userNexaAddress` here is the *app-defined* `MutableStateFlow<String>` on your `AppSession`
subclass (see the `AppSession` example above), not a libnexaapp method — `NexaAppSession`
exposes `identity`, `pushToWallet`, `onWalletConnected`, `disconnectWallet`, and
`identityChallenge`, but no address setter, so the payout/receive address is something you set
on your own session field.

Note `findSession(call)` and `findSession(cookie)` are interchangeable here: libnexaapp's
`BasicSessionHandler.findSession(call)` falls back to `call.request.queryParameters["cookie"]`
when no session header is present (the fallback lives in libnexaapp's `session.kt`), which is
exactly the case for a wallet callback — the wallet is not the browser and carries the id only
as the `cookie` query param. Prefix validation here is the cheap first half of the
`nexa-identity-and-addresses` rule "treat `userNexaAddress` as untrusted until parsed."

### The `/tx` callback (you MUST register this yourself)

For any `tdpp://host/tx?...` URI you push to the wallet, Wally finishes by hitting:

```
GET http://host/tx?tx=<finalSignedTxHex>&cookie=<yourCookie>
```

Register it at the root path of your server (not under `/api/...`):

```kotlin
routing {
    // ... installWalletRoutes, /_share, etc ...

    get("/tx") {
        val cookie = call.request.queryParameters["cookie"]
        val txHex = call.request.queryParameters["tx"]
        if (cookie.isNullOrBlank() || txHex.isNullOrBlank()) {
            call.respondText("missing tx/cookie", status = HttpStatusCode.BadRequest)
            return@get
        }
        val tx = txFor(DEFAULT_CHAIN, BCHserialized(txHex.fromHex(), SerializationType.NETWORK))
        val txid = tx.idem
        // Use `cookie` to look up which logical operation completed and advance state.
        // tx.outputs[i] now reflects what the wallet ACTUALLY broadcast (after adding
        // its own inputs/change), so capture amounts/scripts from this tx, not from
        // what you originally constructed.
        call.respondText("ok")
    }
}
```

### Prefer login (`/api/wallet/loginSvg`) over plain connect

`installWalletRoutes` exposes two QR endpoints:

- `GET /api/wallet/connectSvg?sizePx=400` — plain connection, no identity. Wally just
  starts long-polling. `session.identity.value` stays null.
- `GET /api/wallet/loginSvg?sizePx=400` — login flow. Wally signs a server-issued
  challenge, POSTs `(addr, sig)` to `/_identity`. **AND** opens long-poll (because the
  URI is appended with `&connect`).

Always use `loginSvg` for any app that cares who the user is. The identity address it
gives you in `session.identity.value` is signature-verified — only the holder of the
private key behind that pubkey hash could have produced the signature.

### Working around libnexaapp's QR XML-escape bug

`createQrSvg(...)` injects the URI verbatim into an SVG `onclick="window.open('$uri')"`
attribute. The login URI contains multiple `&` characters from query params (e.g.
`...&op=reg&cookie=ABC&hdl=m...`), which makes the SVG invalid XML and Skia's parser
silently returns blank.

**Workaround**: register a custom QR route that omits `onclick` and returns the
launchable URL in a response header:

```kotlin
import org.nexa.libnexaapp.createQrSvg
import org.nexa.libnexaapp.loginWalletUri
import org.nexa.libnexaapp.notCacheable
import org.nexa.libnexaapp.shared.SESSION_HEADER_COOKIE_NAME

routing {
    get("/api/secrets/loginSvg") {
        val (id, session) = sessionHandler!!.findCreateSession(call)
        val sz = call.parameters["sizePx"]?.toIntOrNull() ?: 400
        val col = call.parameters["col"] ?: "black"
        val loginUrl = loginWalletUri(session, connectWallet = true)
        val qr = createQrSvg(loginUrl, sz, col, null, forceSize = true, onclick = null)
        call.response.notCacheable()
        call.response.header(SESSION_HEADER_COOKIE_NAME, id)
        call.response.header("X-Login-Link", loginUrl)
        call.respondText(qr, contentType = ContentType.Image.SVG, status = HttpStatusCode.OK)
    }
}
```

Client side reads `X-Login-Link` from the response so a "click here to open in wallet"
button still works:

```kotlin
import io.ktor.client.statement.bodyAsText
import org.nexa.libnexaapp.client.getFromServer

fun fetchLoginQr() {
    coScope.launch {
        getFromServer("/api/secrets/loginSvg?sizePx=400") { resp ->
            val link = resp.headers["X-Login-Link"]
            connectWalletLink.value = link
            connectWalletQr.value = svgFromString(resp.bodyAsText())
        }
    }
}
```

(`svgFromString` here is an app-level helper — parse/render the SVG string with your UI stack's
SVG facility; it is not a libnexaapp export. The library's `SvgImage` composable covers *bundled*
SVG resources, not runtime strings — see `nexa-compose-ui-design` Pattern 7.)

Make sure your CORS block has `exposeHeader("X-Login-Link")` or the browser won't let
JS read the header.

#### Alternative pattern, when you still want a clickable QR

You don't have to drop `onclick` to fix the blank-QR bug — you can keep a clickable QR by
XML-escaping the `&` characters *inside* the onclick handler:

```kotlin
val nexidUri = "nexid://$serverHost/identity?op=reg&cookie=$id&chal=$challenge&connect"
val qr = createQrSvg(nexidUri, sizePx, onclick = "window.open('${nexidUri.replace("&", "&amp;")}')")
```

This is valid because `createQrSvg`'s full signature (in libnexaapp's `qr.kt`) is:

```kotlin
fun createQrSvg(qrData: String, maxSzInPix: Int, oneCol: String? = null,
                classes: String? = null, forceSize: Boolean = true, onclick: String? = null): String
```

so both the `onclick = null` workaround above and this `&amp;`-escaped `onclick` are
legitimate. Pick the escape approach when a "tap to open in wallet" affordance directly on
the QR is worth keeping; pick `onclick = null` + the `X-Login-Link` header when the client
renders its own button.

**However the client styles the QR, keep it scannable:** a QR must render as **dark modules on a
light background** — an inverted/"reverse" QR may happen to scan on one phone and fail on many
others — and it needs its **quiet zone** (the empty light border around the modules; keep real
padding there, don't crop to the modules). Theme-proof both by drawing the QR with the design
scheme's `alwaysDark` on an `alwaysLight` card rather than themed body colors
(`nexa-compose-ui-design`).

#### What `loginWalletUri` emits

`loginWalletUri(session, connectWallet = true)` (libnexaapp's `tdpp.kt`) produces a path of
`/_identity?chal=<challenge>&op=reg&cookie=<id>&hdl=m&proto=<http|https>...&connect`, and
libnexaapp registers the matching `POST /_identity` callback. One nuance not in the cheat
sheet below: by default `loginWalletUri` wraps that into an
**`https://w.nexa.org/<rewritten-host>/_identity?...` universal link** (so the QR works even
on devices without the `nexid://` scheme handler registered); pass `uriVariant = true` to get
a raw `nexid://<host>/_identity?...` instead. Some apps bypass this helper and hand-build a
raw `nexid://host/identity?...` URI with their own `POST /identity` handler — a valid custom
variant, not the default.

**What `hdl=m` means — requesting identity-info fields.** A nexid `reg`/`login` URI can ask the
wallet to share fields of the user's stored identity profile alongside the address+signature. Each
field is a query parameter whose key is the field name and whose value is the **requirement
level**: `m` = mandatory, `r` = recommended, `o` = optional (`x` = not used). So the default
`hdl=m` asks for the user's handle/username as a mandatory item. The field-name vocabulary is
libnexakotlin's `nexidParams` set (`identity.kt`): `attest` (attestations), `ava` (avatar URL),
`billing` (billing address), `dob` (birthday), `email`, `hdl` (handle/username), `realname`, `ph`
(phone), `postal` (postal address), `sm` (social media). On the wallet side these live in a
per-identity `IdentityInfo` record, and the user grants per-domain permission per field — the
wallet checks the granted permissions against the request's requirement levels (a mandatory field
the user declines makes the permissions not satisfy the request), so mark fields `m` only when
your app genuinely can't proceed without them. `loginWalletUri`'s `requiredInfo`/`optionalInfo`
parameters are how you select these without hand-building the query string (see
`walletUriFormats.md`).

How the granted fields come back, and the wallet-side failure modes:

- Granted fields arrive as **additional top-level keys in the `op=reg`/`op=info` POST JSON
  body**, alongside `op`/`addr`/`sig`/`cookie` — e.g. `{"op":"reg","addr":…,"sig":…,
  "cookie":…,"hdl":"alice","email":"a@example.org"}`. A custom `/_identity`-style handler
  should read them from the body, not from query parameters.
- A **mandatory (`m`) field the user has not granted** blocks the whole login on the wallet
  side — you never receive a callback. A granted field with no stored value routes the user
  into the wallet's identity editor first, so the callback can arrive noticeably later than
  the scan.
- On a **repeat `op=reg`**, requesting the same or a narrower field set than already granted
  gives the user a one-tap confirm; requesting new or escalated fields routes them through the
  full permission screen. Keep your requested set stable unless you actually need more.

### The push-to-wallet URI cheat sheet

| Action | URI shape |
| --- | --- |
| Plain connect | `tdpp://host/lp?cookie=ID&rproto=http` |
| Login (identity + connect) | `nexid://host/_identity?chal=X&op=reg&cookie=ID&hdl=m&proto=http&connect` |
| Ask for a receive address | `tdpp://host/share?info=address&chain=nexatest&cookie=ID&rproto=http` |
| Ask for assets/NFTs | `tdpp://host/assets?chain=nexatest&af=<filter>&chalby=<chal>&cookie=ID` |
| Send a simple payment | `nexatest:nqtsq5g...?amount=1000` (whole NEXA, not satoshi) |
| Request a payment (TDPP, signed) | `tdpp://host/sendto?amt0=<sats>&addr0=<addr>&amt1=…&addr1=…&cookie=ID&addr=<signer>&sig=<sig>` |
| Ask for a stable per-domain address | `tdpp://host/address?blockchain=nexatest&cookie=ID` (`&unique=true` for a fresh one) |
| Sign + broadcast a partial tx | `tdpp://host/tx?chain=nexatest&inamt=0&flags=0&tx=<partialHex>&cookie=ID` |

For the `/tx` flow: `inamt=0` tells Wally to fund the entire output from its own UTXOs.
`flags=0` is normal broadcast. The `flags` value is a **TDPP wire-protocol** bitfield the
wallet (Wally) interprets.
The bit values are fixed by the protocol, and libnexakotlin exports them as top-level
constants — `import org.nexa.libnexakotlin.TDPP_FLAG_NOFUND` etc. (see the per-bit table in
"The TDPP transaction `flags` bitfield" below). The per-bit semantics are documented there.
Prefer the imports; defining your own matching `const val`s is harmless since the wire values
are protocol-fixed.

Two of those rows deserve their units and semantics spelled out:

- **`/sendto` amounts are satoshis, not whole NEXA.** The TDPP payment-request op takes numbered
  pairs `amt0`/`addr0`, `amt1`/`addr1`, … (all on one chain; `chain=` is mandatory per the spec,
  and the numbering must start at 0 and increment — but N does **not** promise the transaction
  output index; a protocol that needs exact output positions uses the `/tx` op instead), and each
  `amtN` is in the **finest unit** — the opposite convention from the BIP21 `?amount=` row above
  it. This is the op that
  participates in Trickle Pay auto-pay, and an *insecure* one (no HTTPS, no valid signature) is
  rejected outright. The wallet replies with the `{resultCode, txid, txidem, tx, error}` JSON
  (200 = paid, 300 = rejected).
- **`/address` returns a *stable* per-domain address by default.** The wallet pins a "main pay
  address" to each registered (host, topic) and returns the same one on every `/address` request
  unless you pass `unique=true` (fresh address per request). Contrast with
  `/share?info=address`, which returns the account's *current* receive address — a value that
  rotates as it gets used. Pick `/address` when you want one durable address to associate with
  the user; pick `/share` (or `unique=true`) when address reuse is the thing you're avoiding.
  Its chain parameter is spelled `blockchain=` (not `chain=`) and defaults to mainnet.

Two smaller spec surfaces round out the op set: every request may carry **`rpath`** alongside
`rproto` (a reply-path override — a bare path or `//host/path`, letting callbacks land somewhere
other than the request path), and the spec defines a **`/jsonpay`** op
(`tdpp://host/jsonpay?uri=<url>`) that hands the wallet a BitPay-style JSON-payment-protocol
dialog to complete — niche, but recognize them when reading wallet traffic
(`https://spec.nexa.org/dpp/`).

### libnexaapp's own URI builders (prefer these over hand-building strings)

libnexaapp ships the push-URI builders in its `tdpp.kt`, so you rarely need to assemble these
strings by hand: `connectWalletUri`, `loginWalletUri`, `sendPaymentUri`, and `requestAssetsUri`.
Each takes a `uriVariant` flag that flips between a **universal-link** form
(`https://w.nexa.org/<host>/…` — works even on devices with no `tdpp://`/`nexid://` scheme handler)
and a **raw-scheme** form. Mind the polarity: `connectWalletUri`/`loginWalletUri`/`requestAssetsUri`
default to the universal link, but **`sendPaymentUri` is inverted** (its default is the raw BIP21
`<addr>?amount=<qty>`). The full verified signatures are in `walletUriFormats.md`.

The partial-tx `/tx` push URI is **not** built by those helpers — use libnexakotlin's
`iTransaction.createTdppUrl(requestingDomain = "", tdppFlags: Long = 0L, applinkDomain: String? = "w.nexa.org")`.
It **auto-derives `inamt`** from the partial tx's existing inputs, builds the
`https://w.nexa.org/tdpp/<requestingDomain>/tx?…` applink by default (pass `applinkDomain = null` for
a raw `tdpp://…/tx?…` URI), and emits **no `cookie`** — so append your own correlation
`&cookie=<id>` to the returned string (see `nexa-transaction-construction` § "Building a partial-tx
offer"). The `/tx` **callback route** is still **yours** (libnexaapp does not register it; see the
`/tx` callback pattern above), as is the address-share push (`tdpp://<host>/share?info=address…`,
typically sent from `onWalletConnected` via `pushToWallet`, with the matching `/_share` callback).

### Driving pushes from the browser: the built-in `/api/wallet/*` trigger routes

Beyond the QR endpoints, `installWalletRoutes` registers several **browser-facing** GET routes
that let the *client* drive wallet pushes without you writing any server code (each resolves the
caller's session from the session header, so they are for your web frontend, not for the wallet):

| Route | What it does |
| --- | --- |
| `GET /api/wallet/tdpp?msg=<url-encoded URI>` | the generic relay: pushes the given URI to the session's connected wallet via `pushToWallet` — any `tdpp://`/`nexid://`/BIP21 payload the frontend assembled |
| `GET /api/wallet/assets?filter=<hex>` | triggers the whole `/assets` ownership round trip: generates a **fresh random 8-byte challenge** per call, builds `requestAssetsUri(filter, challenge, …, sessId)`, and pushes it. The `filter` is the `af` script-template pattern (hex) passed straight through | 
| `GET /api/wallet/connectText` | returns the session's **connect URI as text** (the universal-link form; `?uri` selects the raw `tdpp://` form) — for a frontend that renders its own QR/link instead of fetching `connectSvg` |
| `GET /api/wallet/disconnect` | server-side wallet disconnect for this session (see the `allowWalletConnection` note below) |

So the minimal client wiring for the token-portfolio flow (`nexa-tokens-and-groups` Pattern 8) is
one GET to `/api/wallet/assets` — the challenge, URI building, push, proof verification, and
`WALLET_HAS_ASSET` notifications are all built in. Since the challenge is generated per request
and verified by the built-in handler, your app never touches it on this path.

### The TDPP transaction `flags` bitfield (what the wallet does with each bit)

The `flags` integer on a `tdpp://host/tx?...&flags=N` push is read and acted on by the
**wallet** (Wally), which maps each bit onto its transaction-completion behavior. The bit
values are part of the **TDPP wire protocol** and are stable.
The named `TDPP_FLAG_*` constants **are importable from libnexakotlin** — they are top-level
`const val`s in `org.nexa.libnexakotlin` (declared in its `utils.kt`, with KDoc stating each
bit's meaning). Import them rather than re-defining; both ends of the protocol agree on the
integer bit values below.

| Constant (`org.nexa.libnexakotlin`) | Bit value | Effect when the bit is set |
| --- | --- | --- |
| `TDPP_FLAG_NOFUND` | `1` | Wallet does **not** add native-NEXA funding inputs; it only signs what's present. When this bit is *clear*, the wallet funds the native side and the `inamt` parameter is **required** (the wallet rejects the request without it). |
| `TDPP_FLAG_NOPOST` | `2` | Wallet signs/completes but does **not** broadcast — it hands the completed tx back to your `/tx` callback for you to broadcast. When *clear*, the wallet broadcasts the fully-signed tx itself. |
| `TDPP_FLAG_NOSHUFFLE` | `4` | Wallet **preserves the existing input/output order** instead of shuffling them for privacy. Set this when a multi-party / offer protocol relies on **stable output positions** (e.g. a half-signed swap offer the counterparty must complete at known indexes — see `nexa-transaction-construction` § "Building a partial-tx offer"). When *clear*, the wallet may reorder inputs/outputs. |
| `TDPP_FLAG_PARTIAL` | `8` | The tx is a **partial / multi-party** tx; the wallet signs its part but treats the result as incomplete (more signers expected). Per the DPP spec, this bit also directs the wallet's **sighash choice**: it signs with an extendable coverage so additional inputs/outputs can be added by later parties *without invalidating its signatures* (the sighash model in `nexa-transaction-construction`/`txCompletionReference.md`). |
| `TDPP_FLAG_FUND_GROUPS` | `16` | Wallet also contributes **token (group) inputs**, not just native NEXA — needed when the tx must be funded on both the native and a token side (see `nexa-tokens-and-groups`). |
| `TDPP_FLAG_HIDE_ASSET_DETAILS` | `32` | Asks the wallet to **suppress detailed asset/token info** in its approval UI (a presentation hint, not a funding/signing behavior). |

Combine bits with `or`. `flags=0` is the ordinary case: fund native, sign, broadcast,
single-party. A common **partial-offer** combination is
`NOFUND or NOPOST or NOSHUFFLE or PARTIAL` (`= 1+2+4+8 = 15`): the offerer's side is already
funded and signed, the wallet must *not* broadcast (it returns the completed tx so the server /
counterparty can finalize or match it), order is preserved so the protocol can rely on output
positions, and the tx is multi-party. The `/tx` push URI also accepts an optional
`&reason=<url-encoded text>` parameter — a short human-readable description the wallet shows the
user on the approval screen.

Three wallet-side facts about how the bits are consumed (useful when a push behaves
unexpectedly):

- **How the wallet maps wire flags to its completer:** it starts from
  `FUND_NATIVE or SIGN or BIND_OUTPUT_PARAMETERS`, then `NOFUND` *clears* `FUND_NATIVE`,
  `PARTIAL` adds `PARTIAL`, and `FUND_GROUPS` adds `FUND_GROUPS` (the same
  `TxCompletionFlags` engine documented in `nexa-transaction-construction` Pattern 6). `NOPOST`
  and `HIDE_ASSET_DETAILS` never reach the completer — `NOPOST` gates the broadcast step and
  `HIDE_ASSET_DETAILS` only condenses the approval UI's per-asset detail.
- **`NOSHUFFLE` is a guarantee, not a toggle you can observe:** current Wally builds do not
  reorder inputs/outputs even without it, so an offer protocol can *appear* to work with the bit
  clear. Set it anyway whenever positions matter — order preservation is only promised to
  requests that ask for it, and wallet-side shuffling may appear at any time.
- **The tx payload can be sent base64url instead of hex:** the wallet accepts `tx64=<base64url>`
  as an alternative to `tx=<hex>` on the `/tx` push. Hex doubles the byte length, so `tx64`
  keeps large partial-tx pushes (and their QR codes) roughly 33% smaller. (`createTdppUrl`
  emits the `tx=` hex form; use `tx64` when hand-building a push for a big tx.)

**Idempotency note:** even when the wallet broadcasts (NOPOST clear), it *also*
GETs your `/tx?tx=<finalHex>&cookie=<id>` callback afterward — and it may GET it **more than
once**. So your `/tx` handler runs whether or not the wallet broadcast, and possibly twice for
one operation. Make it idempotent: a robust handler keeps a per-session "tx continuation already
processing / already completed" flag, and on a duplicate hit returns a benign response (e.g.
"already processing") **without re-running** the state-advancing continuation. Combine that with
the "already in mempool" broadcast handling in `nexa-transaction-construction`.

Before *acting* on the returned tx (broadcasting it yourself, recording a sale), verify it against
your original proposal and — when a trusted node is available — validate it without broadcasting:
`nexa-transaction-construction` Patterns 6b (`sendTxVal`) and 6c (output/selective-signing checks).

**Your callback's response body is user-facing signal — the wallet parses it.** After hitting
your `/tx` callback, Wally classifies the response body to decide what to show the user:

- the exact body `unknown session` → the wallet tells the user the session is gone (re-scan the
  QR). Use this exact string for a dead/unknown cookie — it is the same convention libnexaapp's
  own routes use.
- a body containing `invalid`, `error`, or `rejected` (case-insensitive substring match) → the
  wallet shows the user a **transaction-failed warning with your body text**, even if the tx
  actually broadcast fine. Keep success bodies free of these tokens (a plain `ok` is ideal), and
  when you *do* reject a returned tx, include one of them so the user sees the failure.
- anything else → treated as success/advisory. For a **completed** tx the wallet's definitive
  success signal is observing the tx reach the mempool via its own wallet (it waits up to ~60 s
  before showing a network-timeout warning); for a **partial** tx your response is the only
  signal the user gets, so make it meaningful.

Respond promptly: the wallet's callback requests run with short timeouts (a few seconds). Do
slow work (broadcast retries, DB writes) after responding, not before.

### Verifying the nexid login signature yourself

libnexaapp's built-in `/_identity` handler validates the login signature for you, but a server
doing custom identity handling (or cross-checking out of band) needs the **exact** message the
wallet signs. For the `nexid` identity flow the wallet signs this deterministic challenge string
with the identity key and returns `(addr, sig)`:

```
<host><portString>_nexid_<op>_<challenge>
```

- `<host>` is the URI host; `<portString>` is `":"+port` **except it is empty for the default
  ports 80 and 443** (a frequent verification bug — re-derive it exactly the same way).
- `<op>` is `login`, `reg`, or `info` (whichever your push URI requested).
- `<challenge>` is the `chal` query parameter you issued — must be unique and unpredictable per
  session (see Security considerations).

`sig` is base64. The signing address is the wallet's identity destination —
`destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)` for the shared identity, or a *per-domain*
identity keyed by `host+path` when the user chooses a site-specific identity. Verify with
libnexakotlin's message verification: `verifyMessage(message.toByteArray(),
PayAddress(addr).data, base64Decode(sig))`.

How the wallet returns the result depends on `op`:

- `op=login` → a **GET** callback to `<proto>://<host>/<path>?op=login&addr=<addr>&sig=<sig>&cookie=<id>`.
  **A bare `op=login` only works for a returning user**: the wallet offers only accounts that
  already hold an identity registration for your host, and errors ("register first") when none
  does. This is why `loginWalletUri` emits `op=reg`, which behaves as an upsert — it registers on
  first contact and logs in thereafter. Hand-build an `op=login` URI only for flows where the
  user is guaranteed to have registered already.
- `op=reg` / `op=info` → a **POST** callback with JSON body `{"op":…,"addr":…,"sig":…,"cookie":…}`
  to `<proto>://<host>/<path>?cookie=<id>`. (`loginWalletUri` emits `op=reg`, so its callback is
  this POST form — consistent with libnexaapp's `POST /_identity`.) `op=reg` is also the moment
  the wallet **binds your (host, topic) to the consenting account** for all later TDPP requests;
  `op=info` deliberately never creates a registration, so it's the right op for a one-off
  info request from an untrusted context.
- `op=sign` → arbitrary-message signing: the wallet signs the **raw** `sign=<message>` parameter
  (not the `_nexid_` challenge format) and GETs back `?op=sign&addr=<addr>&sig=<sig>&cookie=<id>`;
  honors `reply=false` to suppress the callback (clipboard-only — the wallet puts a JSON
  `{"message":…,"address":…,"signature":…}` on the clipboard either way). Two extra request
  parameters: `signhex=<hex>` signs **binary** data instead of `sign=`'s text, and `addr=<addr>`
  asks the wallet to sign with a specific wallet address's key rather than the identity default.

Two spec-level facts a **custom** identity handler should honor (the nexid spec —
`https://spec.nexa.org/nexid/` — defines them; libnexaapp's built-in `/_identity` handler covers
only the first form):

- **The reply may carry `ctxsig=` instead of `sig=`.** A wallet may answer a login/reg with a
  hex-encoded **signed Challenge Transaction** (`ctxsig`, built per
  `https://spec.nexa.org/transactions/challengeTransaction/` with challenger id
  `<host><portString>_nexid_<op>_`) rather than the plain message signature — the spec requires
  servers to accept **either** form, because some address types have no plain-signature form.
  libnexaapp's built-in handler verifies only the plain-`sig` form today, so a handler that must
  be spec-complete (or that meets a wallet answering in challenge-tx form) needs to verify the
  challenge tx itself — same verification steps as the `/assets` proof (`nexa-tokens-and-groups`
  Pattern 8), with the nexid challenger-id string as the committed host.
- **Reply with the spec's login-response codes, and don't lock out early.** The defined server
  responses are `200 "login accepted"`, `200 "bad signature"` (note: 200 with an error text),
  `301/302` redirects (wallets follow them — e.g. http→https), `404 "unknown session"` /
  `"unknown operation"`, and `401 "unknown identity"`. On `401`, the spec says to keep the login
  offer alive for at least **~33 attempts**: a wallet restoring from a recovery phrase probes
  several candidate identities against your host, so invalidating the challenge after a couple of
  failures breaks wallet recovery. (Signatures arrive base64-encoded in the standard alphabet;
  future-proof decoders retry a failed decode against the RFC 4648 base64url alphabet.)

> **A `op=sign` message signature is NOT a transaction signature — it cannot satisfy a covenant
> spend rule that uses `checkSigVerify`.** `op=sign` produces a Schnorr signature over an
> arbitrary *message* (verify with `verifySignedHashSchnorr`), which is perfect for a data-sig /
> pre-authorization the holder commits to **offline**, before any tx exists. But an NPL covenant
> `checkSigVerify` rule (see `nexa-npl-smart-contracts`) demands a signature over the **actual
> spend transaction** (ALL/ALL) — a tx the server assembles later. A device that signed a message
> offline has not signed that tx, so a self-custody covenant redeemed via `checkSigVerify`
> requires the holder to be **online and interactive** to sign the assembled tx (push it to them
> via the `/tx` flow). An offline one-scan / pre-authorized flow needs the covenant rule to verify
> a `checkDataSigVerify` over committed data instead — a contract redesign, not a wallet change.

Two protocol gotchas worth pinning:

- **Always set `proto=http` or `proto=https` in the nexid URI.** `nexid` is a URI *scheme*, not a
  transport, so the wallet can't infer how to call you back; absent `proto` it falls back to
  `http` (a documented wallet workaround), which silently breaks against an HTTPS server.
- **Append `&connect`** to a login/reg URI to also open the long-poll push channel on success;
  without it the user authenticates but no push channel opens. This is what
  `loginWalletUri(..., connectWallet = true)` does.

### Signing your TDPP requests (Trickle Pay domains & hands-free pay)

The wallet treats a `tdpp://` request as **secure** if it arrives over `https` *or* carries a
valid `sig`. Insecure `sendto` payment requests are rejected outright, and domain registration
(`/reg`) requires a good signature. To sign a request, append `addr=<your signing address>` and
`sig=<signature>`; the wallet verifies by:

1. dropping the `sig` parameter,
2. sorting the remaining query parameters **alphabetically by key**,
3. form-encoding each value and joining them as `k=v&k=v…`,
4. rebuilding `<scheme>://<authority><path>?<sortedQuery>`,
5. checking `verifyMessage(thatString, PayAddress(addr).data, sig)`.

Reproduce that canonicalization **exactly** when you produce `sig` (alphabetical key order,
form-encoding, `sig` excluded) or verification fails.

Why this matters: the wallet supports **Trickle Pay**. A user can `reg`-ister your domain via a
signed `tdpp://host/reg?...&addr=…&sig=…` URI and grant per-payment / per-day / per-week /
per-month spending limits, each with an `ACCEPT` / `ASK` / `DENY` policy. A *signed* `sendto`
request within those limits is then paid **automatically, without prompting the user** (token
sends always prompt regardless). This is the mechanism behind recurring / streaming /
micro-payment UX: unsigned or over-limit requests fall back to an interactive prompt or denial.
So if your app wants hands-free payments, you must serve over HTTPS or sign your requests, **and**
the user must have registered your domain with a limit — there is no way to force auto-pay from
the server side alone.

More of the registration model, from the wallet side:

- **A "domain" is keyed by (host, `topic`).** Every TDPP URI may carry a `topic` query
  parameter; the wallet keeps a separate registration (limits, policies, signing address, pay
  address) per `(host, topic)` pair, so one server can hold several independent grants
  ("example.com/subscriptions" vs "example.com/tips").
- **The `/reg` URI's parameters:** `topic`, `addr` (your signing address — pinned; later
  requests must verify against it), `uoa` (unit of account), and the limit/description pairs
  `maxper`/`descper`, `maxday`/`descday`, `maxweek`/`descweek`, `maxmonth`/`descmonth`. Limit
  amounts are in the **finest unit** (satoshis for NEXA). Re-registering merges by taking the
  larger of the existing and requested limits — a repeat `/reg` can raise but not silently
  lower what the user granted.
- **Each registration binds to ONE wallet account.** Wally is multi-account; the account the
  user consents with (at nexid `op=reg`, or on the first address share) is recorded on the
  domain, and every later request for that (host, topic) — payout addresses, `/assets`
  enumeration, tx funding — is served **from that account only**. Consequences: an `/assets`
  reply lists one account's holdings, not the whole wallet, and a user who moved funds to
  another account looks "empty" to your app until they re-register with the other account.
- **Auto-pay covers `/tx` completions too, not just `sendto`.** An in-limit, auto-enabled
  domain can have a pushed partial-tx completion run with no prompt — with the same guards:
  any tx that would **spend the wallet's tokens always prompts**, whatever the limits.
- **Enforcement today is the per-payment cap.** The registration schema carries the
  day/week/month limits (and per-domain policies for asset/balance queries), but as of recent
  Wally releases the enforced auto-pay gates are the per-payment `maxper`, the master
  auto-enable switch, and the token-spend rule above. Don't design a product on the assumption
  that the periodic caps clamp cumulative spending yet.
- **`rproto` has the same default-`http` footgun as nexid's `proto`.** The wallet replies over
  `rproto` if present, else the pushed URI's scheme when it isn't `tdpp://`, else **`http`**. A
  raw `tdpp://` push to an HTTPS-only server must carry `rproto=https` or every callback will
  hit port 80 in the clear and fail.

### What the wallet sends back on each callback

The concrete reply shapes (so your route handlers parse the right thing):

| Wallet push (`tdpp://host/…`) | Callback the wallet makes to your server |
| --- | --- |
| `/share?info=address` | **POST** the address string (plain text) to `/_share?cookie=ID` |
| `/share?info=clipboard` | **POST** the clipboard text (plain text) to `/_share?cookie=ID` |
| `/address?...` | **POST** the address string (plain text) to `/address?cookie=ID` |
| `/sendto?...` | **POST** JSON `{resultCode, txid, txidem, tx, error}` to `/sendto?cookie=ID` |
| `/assets?...` | **POST** JSON `{assets:[{outpointHash, amt, prevout, proof}]}` to `/assets?cookie=ID` |
| `/tx?...` | **GET** `/tx?tx=<finalHex>&cookie=ID` (note: GET, not POST) |
| `/lp?...` | no callback — this opens the wallet's long-poll channel |

**Rejections also call back — with `resultcode=300` and no payload.** When the user (or an
auto-deny policy) rejects a request, the wallet makes a best-effort callback so your server can
stop waiting: `GET /tx?cookie=ID&resultcode=300` (no `tx` parameter), `POST
/sendto?cookie=ID&resultcode=300` with a JSON body whose `resultCode` is `300`, and the
`/address` / `/assets` equivalents with `resultcode=300`. `resultcode=200` accompanies
acceptances on the ops that send it (e.g. the `/address` reply). So a `/tx` GET **without a `tx`
parameter is a user rejection, not a malformed request** — handle it by cancelling the pending
operation rather than just answering 400. (These are best-effort: a network failure or force-quit
can still leave you with no callback at all, so keep your own timeout.)

**The full DPP result-code vocabulary (beyond 200/300).** The spec (`https://spec.nexa.org/dpp/`)
defines more granular result codes a wallet may send, worth recognizing so a non-200 isn't
misread as a rejection:

- `/tx` replies: `200` = tx completed; `201` = **tx filled out but missing signatures** (the
  wallet did its part but can't complete — multisig or another party must still sign; continue
  your partial-tx flow rather than failing); `202` = tx unmodified; `203` = **tx created but not
  currently final** (e.g. a locktime the chain hasn't reached — see `nexa-locktime-cltv`); `204` =
  the wallet's chain connection is down so it **couldn't post** (the tx may still be returned —
  consider broadcasting it yourself).
- Any op: `300` = user reject; `301` = request signature failed; `302` = registration requires a
  pubkey (`/reg` without `addr` on a non-HTTPS channel); `303` = unsupported request type;
  `304` = insufficient balance.

**Replies are optional and can be very late.** DPP is a human-in-the-loop protocol: the spec is
explicit that a reply may arrive **days** after the request (the user parked it in the wallet's
notifications), and that no-reply is a normal outcome, not an error. So (a) keep your own
timeout for UX, but (b) make a very-late callback benign — either resolvable (if the operation
can still complete) or answered with the exact body `unknown session` so the wallet tells the
user cleanly; don't let a stale cookie crash a handler that assumed callbacks come quickly.

**Callback-path mapping (who adds the underscore).** The wallet maps each push op to its
callback path itself: the long-poll goes to **`/_lp`**, the share callback to **`/_share`**, and
the nexid identity callback to **whatever path the nexid URI carried** (`loginWalletUri` uses
`/_identity`, so that's where its callback lands). The other callbacks (`/sendto`, `/address`,
`/assets`, `/tx`) use the op's own path with no underscore. This is why the push cheat sheet
shows `/share` while your route table shows `/_share` — both are correct.

For `/assets`, each entry's `proof` (when you supplied a `chalby=` challenge) is a **Challenge
Transaction** (`https://spec.nexa.org/transactions/challengeTransaction/`): a signed but
deliberately-invalid (nVersion high bit set, `> 127`) un-broadcast tx that spends the claimed UTXO
and carries a single `OP_RETURN` whose first push is **your server host** (the anti-spoof check —
reject a proof whose host isn't yours) and second push is your interleaved challenge. Verifying it
proves the wallet actually controls the asset rather than just claiming the outpoint — but you must
**also** confirm the UTXO really existed on-chain (merkle/SPV proof or your own node), since script
validity alone can be faked. `chalby=` sends only the challenge *bytes* (the wallet builds the full
challenge tx); the `chaltx=` variant sends the whole challenge tx to be signed. **The `chalby`
challenge must decode to 8–64 bytes** — outside that range the wallet still enumerates but
silently attaches **no proof** (each entry's `proof` is null), so a too-short challenge looks like
a wallet bug when it's a request bug. The `af=` filter is
a script-template pattern (one or more `af` params); only grouped (token) UTXOs matching it are
returned — and the wallet **never returns authority UTXOs**, only quantity-bearing outputs. The full server-side consumption of this callback — deserializing each prevout, reading
its group, skipping fenced groups, verifying the proof against your challenge, and accumulating
holdings by group id — is `nexa-tokens-and-groups` Pattern 8. Note that the `POST /assets` route
this callback lands on is one of the routes `installWalletRoutes` registers, and its **built-in
handler already does that consumption** (node-verified proofs → `session.assets` → a
`WALLET_HAS_ASSET` notification to the browser) — see the "built-in server side" subsection of
that same pattern before writing your own.

### The long-poll wire protocol (what `/_lp` actually speaks)

The server→wallet push channel is a plain HTTP long-poll with a tiny text protocol — knowing it
makes wallet-connection bugs debuggable with curl and matters if you implement either side
yourself. The wallet issues `GET <proto>://<host:port>/_lp?cookie=<id>&i=<count>` in a loop
(`i` counts polls from 0) and interprets the response:

| Server response | Wallet behavior |
| --- | --- |
| body `A` | connection accepted (only meaningful on the first poll, `i=0`) — shows "connected" |
| body `Q` | server-requested disconnect — the wallet stops polling (this is what `session.disconnectWallet()` pushes) |
| empty body | no activity — the wallet immediately re-polls |
| any other body | treated as a pushed URI: **exactly what `pushToWallet(uri)` delivers.** The wallet handles it as if the user had scanned/pasted it — a `tdpp://` op, a `nexid://` request, or a bare BIP21 payment URI (which pre-fills its Send screen) |
| HTTP 400 / 404 | fatal — the wallet stops polling and tells the user to re-scan a fresh QR (this is what a dead session looks like from the wallet side) |

Two wallet behaviors that shape server expectations:

- **The wallet persists its active long-polls and reconnects on app restart** (within ~30
  minutes of the last successful poll). So your server may see `/_lp` requests arrive with an
  old cookie after the wallet app was killed and relaunched — if the session still exists the
  connection silently resumes; if it's gone, respond 404 and the user is prompted to re-scan.
  Pair this with the server-side session-abandonment grace period (below): between them, neither
  a browser refresh nor a brief wallet-app restart drops the connection.
- **One long-poll per host:port.** Connecting again to the same server replaces the previous
  poll rather than adding a second channel, so a re-scan is always safe.

### Implementing the wallet-facing routes without libnexaapp

Everything the wallet speaks is plain HTTP, so a server that can't (or doesn't want to) use
libnexaapp — different session model, different framework, non-Kotlin stack — can implement the
wallet side directly against the wire facts in this skill. The load-bearing pieces, verified
end-to-end in production:

- **`GET /_lp?cookie=<id>&i=<count>`** — reply `A` (as a body) to the first poll (`i=0`); on later
  polls, **hold the request a few seconds** (~5 s is the working cadence) waiting for a queued push
  URI, then respond with the URI if one arrived or an **empty body** if not (the wallet immediately
  re-polls); reply `Q` to command a disconnect; reply HTTP 400 (no cookie) / 404 (unknown session)
  to make the wallet stop and tell the user to re-scan. A per-session unbounded queue (`Channel`)
  that `pushToWallet` feeds and the poll handler drains is the natural implementation.
- **Wallet-alive is a heuristic, and "silent" ≠ "disconnected."** Track the last successful poll;
  if it's older than the hold time plus a tolerance (~10–15 s total), mark the wallet *silent* —
  but do **not** log the user out or clear identity/assets: a phone wallet suspends in the
  background and resumes polling later (within its ~30-min auto-reconnect window). Reserve the
  disconnect treatment for an explicit `Q`/disconnect action or session expiry, and surface
  "wallet not currently reachable" to the browser as its own state.
- **Make session cookies QR-friendly.** The cookie rides inside QR-encoded URIs; restricting its
  alphabet to `A–Z0–9` keeps the QR in alphanumeric mode (lowercase forces byte mode, which makes
  the code denser for the same length).
- The callback routes (`/tx`, `/assets`, `/address`, the share POST) and their body/response
  contracts are exactly as documented in this skill — none of them require libnexaapp types.

(When you *are* on libnexaapp, prefer `installWalletRoutes` — this section exists because the
protocol itself is server-framework-agnostic.)

### A request can be delivered by QR scan alone — no connection required

The long-poll push channel is a convenience, not a prerequisite. Any `tdpp://` request — including
a `/tx` push whose `tx=<hex>` embeds an entire partial transaction — can be delivered by having
the user **scan a QR of the URI directly** (or tap its universal-link form). The wallet acts on a
scanned/pasted URI exactly as on a pushed one, and its callbacks still arrive keyed by the
`cookie` you embedded, so a stateless flow (a chat-bot offer, a printed payment request, a kiosk)
works with no `/lp` session at all. Mind QR density for big embedded txs: the cookie-alphabet note
above and the `tx64=` base64url form both shrink the code.

One durable-identity trick this enables: `/address` (with `unique` absent/false) returns the
wallet's **stable per-(host, topic) address**, so a connectionless flow can "register" a user by
pushing one `/address` QR and keying them by the returned address thereafter — a lightweight
alternative to the nexid login when all you need is a stable payment identity.

### Disconnecting and switching wallets

`session.disconnectWallet()` clears `identity.value` and cancels the long-poll channel.
The next `loginSvg`/`connectSvg` fetch starts a fresh handshake.

Two mechanics behind that, worth knowing:

- **`allowWalletConnection` is the reconnect gate.** `NexaAppSession` carries
  `allowWalletConnection: Boolean` (initially true). Both `disconnectWallet()` and
  `handleAbandoned()` set it **false**, and the `/_lp` handler answers any long-poll on a
  disallowed session with `Q` (disconnect) — which is what stops the wallet's ~30-minute
  auto-reconnect from silently re-attaching after a deliberate disconnect. Generating a fresh
  connect/login URI (the QR routes, `connectText`, or the `connectWallet(...)` helpers) sets it
  back to true. So "disconnect, then reconnect" always goes through a fresh QR/URI fetch by
  design, not by accident.
- **The library clears only its own state — clear yours in BOTH disconnect paths.** The base
  `disconnectWallet()`/`handleAbandoned()` reset `identity`, the long-poll channel, and the
  library's session fields, but they know nothing about *your* `AppSession` additions
  (`userNexaAddress`, cached per-wallet views). A stale payout address lingering across a wallet
  swap on the same session is a classic bug. Override **both** (they are distinct paths — user
  action vs browser abandonment), call `super`, then clear your fields; writing `""`/null into a
  flowed field also pushes the cleared value to connected clients:

  ```kotlin
  override fun disconnectWallet() { super.disconnectWallet(); userNexaAddress.value = "" }
  override fun handleAbandoned()  { super.handleAbandoned();  userNexaAddress.value = "" }
  ```

  (The client-side counterpart — clearing wallet-asset lists the browser accumulated — is in
  `nexa-tokens-and-groups` Pattern 8.)

The server also disconnects the wallet when the session is **abandoned** — i.e. when the
wallet's long-poll arrives and the session has zero connected browser tabs. Since a recent
release there is a **grace period**: the long-poll handler waits ~5 seconds for a browser to
reconnect before calling the session's `handleAbandoned()` (which disconnects the wallet and
pushes `walletConnected = false` to any client that rebinds). The practical effect: a browser
**page refresh no longer drops the wallet connection** — the tab's WebSocket comes back within
the grace window and the wallet stays connected. Don't design UX around the older
instant-disconnect behavior, and don't treat a refresh as a wallet-switch event.

If the user switches wallets in the same browser tab, the session id stays the same but
`session.identity.value` and `session.userNexaAddress.value` change. Anything you keyed
by the old identity must be re-filtered. Override `onWalletConnected()` to re-trigger
your per-session view refreshes:

```kotlin
override fun onWalletConnected() {
    super.onWalletConnected()
    pushToWallet(buildShareAddressUri())
    refreshMySessionViews(this)         // your function — recompute mySales, myPurchases
}
```

## Common mistakes and anti-patterns

### Pushing a URI with `localhost` host

**Wrong**:
```kotlin
val req = "tdpp://localhost:8001/lp?cookie=$id"  // Wally is on the phone, not the laptop
```
*Wally's HTTP request to `localhost:8001` resolves to the phone itself and fails. User
sees a spinner that never resolves.*

**Right**:
```kotlin
// EXTERNAL_URL in startercfg.json is "http://192.168.1.137:8001" -- your LAN IP
val req = ExternalUrl.replace("http", "tdpp") + "/lp?cookie=$id"
```

Always source the host from a server-startup config (`EXTERNAL_URL`), and verify the
config reflects the *current* LAN IP. `127.0.0.1` is also wrong for the same reason
unless Wally happens to run on the same machine.

### Using `connectSvg` instead of `loginSvg`

**Wrong**: relying on `connectSvg` and then trying to read `session.identity.value`.
`identity.value` will be null — `connectSvg` doesn't run the signature flow.

**Right**: use `loginSvg` (or your custom-escape version of it) for any app that needs
to know *who* the user is. The login URI has `&connect` appended so it ALSO opens the
long-poll channel — there's no reason to use `connectSvg` instead.

### Trying to register `/_tx` (POST) for the wallet's tx callback

**Wrong**:
```kotlin
post("/_tx") { /* ... */ }  // libnexaapp doesn't document this route, but it's tempting
```
*Wally never hits this. The TDPP `/tx` callback is a **GET** to root path `/tx`.*

**Right**:
```kotlin
get("/tx") {
    val cookie = call.request.queryParameters["cookie"]
    val txHex = call.request.queryParameters["tx"]
    // ...
}
```

### Forgetting to populate `userNexaAddress`

**Wrong**: After login, immediately trying to put `session.identity.value` into a tx
as the payment destination. Identity addresses are P2PKH-style and not valid as
contract output destinations. *See `nexa-identity-and-addresses`.*

**Right**: In `onWalletConnected()`, push `tdpp://host/share?info=address...`. Wally
POSTs back to `/_share`, and your handler stores the value in
`session.userNexaAddress.value`. THAT is the P2PKT address you use for payments.

### Embedding `onclick` in `createQrSvg` for login URLs

**Wrong**: directly using `installWalletRoutes`' built-in `/api/wallet/loginSvg` route.
The SVG is malformed because `&` is not XML-escaped. QR renders blank in the browser.

**Right**: register your own QR route that calls `createQrSvg(..., onclick = null)`
and exposes the URL via response header. See the "QR XML-escape bug" pattern above.

### Pushing a URI without `cookie=<id>`

**Wrong**:
```kotlin
session.pushToWallet("tdpp://$host/tx?chain=nexatest&inamt=0&flags=0&tx=$partialTxHex")
```
*Wally signs and broadcasts, but you have no way to correlate the eventual `/tx` callback
to any of your application objects.*

**Right**: every TDPP URI you push must include `cookie=<your-correlation-id>`. Echoed
back in the callback. Use a stable id (database row id, UUID, listing id, etc.).

### Returning an error-flavored body from a successful `/tx` callback

**Wrong**:
```kotlin
get("/tx") {
    // ... tx handled fine ...
    call.respondText("no error occurred")     // contains "error" → wallet shows a FAILURE warning
}
```
*The wallet substring-matches your response body: `invalid`, `error`, or `rejected`
(case-insensitive) makes it tell the user the transaction failed — even though everything worked.
Conversely, a rejection body without any of those tokens reads as success to the wallet.*

**Right**: keep success bodies neutral (`ok`), use one of the tokens deliberately when you reject
the returned tx, and answer a dead cookie with the exact body `unknown session`. See "Your
callback's response body is user-facing signal" above.

### Treating a `/tx` callback without a `tx` parameter as a malformed request

**Wrong**: `if (txHex.isNullOrBlank()) respond(BadRequest)` and nothing else — the pending
operation waits forever.
*A `/tx` GET carrying `cookie` and `resultcode=300` but no `tx` is the wallet reporting that the
user REJECTED the request.* **Right**: on `resultcode=300`, cancel/expire the pending operation
keyed by the cookie (and still keep your own timeout — the rejection ping is best-effort).

### Pushing a raw `tdpp://` URI to an HTTPS server without `rproto=https`

**Wrong**: `session.pushToWallet("tdpp://$host/share?info=address&cookie=$id")` on a TLS-only
server. *Absent `rproto`, a raw `tdpp://` push defaults the wallet's callbacks to plain `http` —
they hit port 80 and fail (or leak).* **Right**: append `rproto=https` (the libnexaapp builders
and the `ExternalUrl`-derived pattern above do this for you); same footgun class as the nexid
`proto` parameter.

### Calling `findSession(call)` when no session cookie was sent

`findSession` returns `null` (not throws) when the request has no session header. Callers
must check:

```kotlin
val session = sessionHandler?.findSession(call) as? AppSession
if (session == null) {
    call.respond(HttpStatusCode.Unauthorized, "no session")
    return@post
}
```

## Security considerations

- **Identity is signature-verified, but addresses shared via `/share` are NOT.** Anyone
  controlling the wallet at long-poll time can submit any address string they want via
  `/_share`. If you're going to send funds to that address, verify it parses as a
  `PayAddress` and ideally extract its argsHash so a malformed string doesn't quietly
  become a black-hole payment target. See `nexa-identity-and-addresses`.
- **Replay**: `cookie` values you embed in pushed URIs are visible to the user. Don't
  use them to authenticate sensitive operations on their own; the underlying session
  cookie is the authoritative auth.
- **The login challenge (`session.identityChallenge`)** must be unique per session and
  unpredictable. `BasicSessionHandler` uses a cryptographic random by default — don't
  override this.
- **Bind the nexid challenge to the session and treat it as single-use.** The wallet signs
  `<host><port>_nexid_<op>_<challenge>` (see "Verifying the nexid login signature yourself"), so
  if you reuse a `chal` or accept a stale one, a replayed `(addr, sig)` re-authenticates. Issue a
  fresh challenge per login attempt, verify the signature against *that* challenge only, and
  expire it after use — which is exactly what the per-session `identityChallenge` gives you, so
  don't weaken it by accepting client-supplied challenges.
- **For `reg` (domain registration / Trickle Pay), check that `addr` is the address you expect.**
  A signed `reg` proves control of *some* identity key; your auto-pay limits are only as safe as
  binding them to the right signing address. The wallet stores the domain's signing `addr` and
  later rejects requests whose signature doesn't match it — mirror that on your side.
- **Don't ever log `tdpp://` or `nexid://` URIs in production logs.** They contain
  session cookies and signed material. Logging at info-level for development is fine.
- **The `host` portion of pushed URIs reveals your server's external address** to the
  wallet (and via the QR, to anyone the user shows the QR to). Don't include sensitive
  intranet hostnames.

## Related skills and references

- `nexa-identity-and-addresses` — what to *do* with `session.identity.value` vs
  `session.userNexaAddress.value` once you have them.
- `nexa-server-state-and-flows` — how to push per-session state changes to the connected
  browser (the WebSocket channel, separate from the wallet long-poll).
- `nexa-ktor-server-integration` — full CORS block, route registration order, where to
  call `installWalletRoutes` from.
- `nexa-transaction-construction` — how to build the partial-tx hex you put in the
  `tdpp://host/tx?tx=<hex>` URI.

### Supporting files in this folder

- `walletUriFormats.md` — consolidated reference for the `tdpp://` / `nexid://` URI shapes, the
  libnexaapp builders, the per-`op` list, the `flags` bitfield (with bit values), and request-signing
  canonicalization.
- `qrRouteTemplate.kt` — drop-in route handler that serves a clickable connect/login QR, applying
  the XML-escape workaround `createQrSvg` requires for the `onclick` URI.
- `flowchart.md` — message sequence: browser → server → wallet → server across a complete
  connect/login + buy + sign cycle.