# TDPP/nexid message sequence: connect → login → buy → sign

A sequence walkthrough of a full cycle — browser, your server, and the Wally wallet — showing every
message. Read `SKILL.md` for each message's contents and `walletUriFormats.md` for the URI shapes;
this ties them into one timeline. Route paths are the verified libnexaapp set (`installWalletRoutes`);
the wallet *pushes* to `/lp`,`/share`,`/identity` while the server *routes* are `/_lp`,`/_share`,
`/_identity` (the deep-link layer bridges them — see `walletUriFormats.md`).

## Actors

- **Browser** — your web frontend (or app UI).
- **Server** — your Ktor server (libnexaapp routes + your app routes).
- **Wallet** — the user's Wally wallet (the protocol counterparty).

## 1. Connect / login (nexid)

```
Browser            Server                          Wallet
  |                  |                               |
  |-- GET page ----->|                               |
  |   open WS /api/client/ws  (flowConnector)        |
  |<== WS bound =====|                               |
  |                  |                               |
  |-- GET /api/wallet/loginSvg ------------------->  |   (server builds loginWalletUri via tdpp.kt,
  |<-- SVG QR (encodes nexid login URI) ----------   |    binds a single-use challenge to the session)
  |                  |                               |
  |   user scans QR with Wally  ------------------>  |
  |                  |   wallet signs the challenge  |
  |                  |   string <host><port>_nexid_login_<challenge>
  |                  |<-- GET/POST /_identity {addr,sig,cookie} --|
  |                  |   server verifies sig vs addr,|
  |                  |   sets session.identity        |
  |                  |-- (optionally) wallet GETs /_lp long-poll for follow-ups -->
  |<== flowConnector pushes "walletConnected"=true (FLOW_UPDATE2) ==|
  |   UI updates to "connected as <identity>"        |
```

Key points: the QR encodes a `loginWalletUri(session, …)`; the challenge is **single-use** and
**bound to the session** (replay protection — `SKILL.md` Security). The server learns the result on
its `/_identity` callback, and the browser learns it reactively over the flowConnector WebSocket
(`nexa-server-state-and-flows`), not by polling.

## 2. Share a payout address (optional)

```
Browser            Server                          Wallet
  |-- "use my wallet to pay" ----->|                 |
  |                  |-- push /share URI (deep link) ------------->|
  |                  |<-- POST /_share  <plain-text address> ------|
  |                  |   server stores session.userNexaAddress     |
```

`/_share` returns a plain-text address. Note `userNexaAddress` is an **app-level** field you
declare on your `AppSession` and populate here — not a library API (`SKILL.md`).

## 3. Buy (request a payment)

```
Browser            Server                          Wallet
  |-- "buy item X" ->|                               |
  |                  |-- push /sendto URI (address, amount, cookie) ----->|
  |                  |                               |  user approves in Wally;
  |                  |                               |  wallet funds+signs+broadcasts
  |                  |<-- POST /sendto JSON {resultCode,txid,txidem,tx,error} --|
  |                  |   server correlates by cookie, records the sale          |
  |<== flowConnector pushes order state ("paid") ===|                 |
```

For a Trickle-Pay-registered domain within limits, an in-limit `sendto` **auto-pays without a
prompt** (`SKILL.md` Trickle Pay); otherwise the wallet prompts. Token sends always prompt.

## 4. Sign / complete a server-built (partial) tx

```
Browser            Server                          Wallet
  |-- "complete contract" ->|                        |
  |             server builds outputs, makes a tx push URI:        |
  |             tx.createTdppUrl(requestingDomain, tdppFlags) + &cookie=ID
  |                  |-- push /tx URI (tx=<hex>, inamt, flags, cookie) ----->|
  |                  |                               |  wallet runs txCompleter per the flags
  |                  |                               |  (NOPOST ⇒ return; else broadcast)
  |                  |<-- GET /tx?tx=<finalHex>&cookie=ID ----------|
  |                  |   server validates the returned tx against    |
  |                  |   the original offer, then broadcasts (if NOPOST) |
  |<== flowConnector pushes "settled" ==============|                 |
```

Idempotency: the wallet may GET `/tx` **more than once** — guard the state-advancing continuation
with a per-session "already processing/done" flag and return a benign response on the duplicate
(`SKILL.md`). Correlate the round trip by the `cookie` you appended (`createTdppUrl` emits no
cookie itself — `nexa-transaction-construction`).

## The two channels, summarized

- **Wallet ⇄ Server:** HTTP callbacks (`/_identity`, `/_share`, `/sendto`, `/tx`, `/assets`) +
  the wallet's `/_lp` long-poll for server→wallet pushes.
- **Server ⇄ Browser:** the flowConnector WebSocket (`/api/client/ws`) — the browser sees state
  changes reactively (`nexa-server-state-and-flows`), never by polling the wallet directly.

## Related

- `SKILL.md` — message contents, nexid signature, callback reply shapes, Trickle Pay, security.
- `walletUriFormats.md` — the URI shapes and `flags` for each push.
- `nexa-server-state-and-flows` — the browser-facing reactive channel shown above.
- `nexa-transaction-construction` — building the `/tx` partial-tx offer.