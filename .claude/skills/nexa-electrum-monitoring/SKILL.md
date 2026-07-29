---
name: nexa-electrum-monitoring
description: "Queries and monitors arbitrary on-chain Nexa state (any address, script, outpoint, transaction, or token) via libnexakotlin's ElectrumClient, without running a full node. Use when watching an address for payments, checking whether an outpoint or contract UTXO is spent, listing UTXOs, looking up a transaction from a light client, reacting to new blocks, reading a token's on-chain metadata, or broadcasting through an electrum server. Triggers: ElectrumClient, getTx/getUtxo/getHistory/listUnspent/getBalance, subscribeHeaders, getTokenGenesisInfo, scriptHash, ElectrumNotFound. Not for the wallet's own keys (use nexa-wallet-lifecycle-and-chain), node control (nexa-rpc-node-client), or off-chain messaging (nexa-capd-messaging)."
---

# Nexa electrum client (on-chain queries & monitoring)

## When to use this skill

Trigger when a developer needs to **query or monitor arbitrary on-chain state** — any address,
script, outpoint, transaction, or token — *without* running a full node and *without* being limited
to a wallet's own keys. libnexakotlin's `ElectrumClient` speaks the electrum protocol to a Nexa
electrum server (public seeders, or one you run) and is the most direct way to watch transactions,
check whether a specific outpoint has been spent, list a UTXO set, or read a token's on-chain
metadata. Concretely trigger on:

- Keywords: `ElectrumClient`, electrum, `getTx` / `getTxDetails` / `getTxAt`, `getUtxo` /
  `getBchUtxo`, `getHistory`, `listUnspent`, `getBalance`, `getFirstUse`, `subscribeHeaders` /
  `unsubscribeHeaders`, `sendTx`, `getTokenGenesisInfo` / `getTokenBalance` / `getTokenUnspent` /
  `getTokenHistory`, `getTip` / `getHeader` / `getHeadersFor`, `scriptHash`, outpoint monitoring,
  watch an address, `GetUtxoResult.status` (`"unspent"`/`"spent"`) / `GetUtxoSpentInfo`,
  "is this offer's input still unspent", `ElectrumNotFound` / `ElectrumRequestTimeout` /
  `ElectrumException`, `DEFAULT_NEXA_SSL_ELECTRUM_PORT`.
- Tasks: "watch an address for incoming payments", "check whether this contract UTXO has been
  spent", "look up a transaction by id from a light client", "list the UTXOs at an address",
  "monitor outpoints / detect when a counterparty spends", "react when a new block arrives", "read a
  token's decimals/ticker from chain", "broadcast a tx through an electrum server", "query the chain
  without running a node".

**Negative triggers** — do NOT use this skill for:
- Your own wallet's balance/UTXOs/incoming payments — the wallet already tracks its own keys via
  SPV; use `nexa-wallet-lifecycle-and-chain` (`balance`, `setOnWalletChange`). Reach for electrum to
  watch **arbitrary** scripts/outpoints the wallet doesn't own.
- Owner-only node operations (mining regtest, node wallet, statistics, `enqueuerawtransaction`) —
  use `nexa-rpc-node-client`. Electrum is a light-client read/broadcast protocol, not node control.
- Off-chain peer messaging — use `nexa-capd-messaging`.

## Mental model

`ElectrumClient` is a **light-client query channel** to a Nexa electrum server. Unlike the SPV
wallet (which only sees its own keys) and unlike `nexarpc` (which needs a node *you* operate), the
electrum client can ask about **any** address, script, outpoint, transaction, or token on the
chain, talking to **public** electrum servers (or your own). It is the natural tool for
"watch something I don't own": a contract UTXO, a counterparty's address, a token's supply.

Three facts shape how you use it:

1. **Scripts are addressed by their hash.** Most lookups key off a **script hash** (the electrum
   `scripthash`), with convenience overloads that accept a `PayAddress`, a `SatoshiScript`, a
   `Hash256` script hash, or the hex form. `getHistory(addr.lockingScript())`, `listUnspent(addr)`,
   and `getBalance(addr)` all resolve to the same underlying script-hash query.

2. **The only *push* subscription is new blocks.** `subscribeHeaders { header -> … }` calls you back
   on every new tip. There is **no per-address push** in this client — so monitoring an
   address/outpoint means: subscribe to headers, and on each new block (and once up front) **re-poll**
   `getHistory` / `getUtxo` / `listUnspent` for the things you care about. (You can also just poll on
   a timer; the header subscription simply tells you *when* it's worth re-polling.)

3. **The server is untrusted.** A light client asks a server what the chain looks like; a malicious
   or buggy server can withhold a transaction or lie about confirmations (it cannot forge spends of
   your keys). The library deliberately does **not** surface the server's raw error strings to higher
   layers (to avoid showing untrusted text to users) — it maps failures onto typed exceptions
   (`ElectrumNotFound`, `ElectrumRequestTimeout`, `ElectrumIncorrectRequest`, …). Verify anything
   important against more than one server or your own node, and apply confirmation-depth rules
   (`nexa-transaction-construction`).

Calls are **blocking** with a per-call `timeoutInMs` (each throws `ElectrumRequestTimeout` if the
server doesn't answer in time); several also have an async callback overload returning a request id.
Drive the blocking forms off a background dispatcher, like any network I/O.

## Setup and versions

You need `libnexakotlin` (the client and its types). Pin per `nexa-project-setup`. The client is
multiplatform.

```kotlin
import org.nexa.libnexakotlin.ElectrumClient
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.Spendable
import org.nexa.libnexakotlin.ElectrumNotFound
import org.nexa.libnexakotlin.ElectrumRequestTimeout
```

The constructor is
`ElectrumClient(chainSelector, host, port = DEFAULT_NEXA_SSL_ELECTRUM_PORT, logName, autostart = true, useSSL = true, …)`.
The default ports are stable network constants (named in the library): SSL **20002** / TCP **20001**
for mainnet, SSL **30002** / TCP **30001** for testnet, TCP **30403** for regtest — match `useSSL`
to the port you pass. With `autostart = true` the client connects on construction.

## Core patterns

### Pattern 1: Connect to an electrum server

```kotlin
// Public/self-hosted Nexa electrum server over SSL (default port + useSSL):
val ec = ElectrumClient(ChainSelector.NEXATESTNET, electrumServerHost)    // autostart connects

// Plaintext TCP (e.g. a local regtest server) — match port and useSSL:
val reg = ElectrumClient(ChainSelector.NEXAREGTEST, "127.0.0.1",
    port = 30403, useSSL = false)
```

### Pattern 2: Look up a transaction

```kotlin
val tx = ec.getTx(Hash256(txidHex))            // parsed iTransaction (also a String overload)
val details = ec.getTxDetails(txidHex)         // raw JsonElement (node-decoded view, traverse yourself)
// async, non-blocking form returns a request id and calls back:
ec.getTx(txidHex) { tx, err -> if (err == null) handle(tx!!) }
```

### Pattern 3: Check an outpoint — has this UTXO been spent? (contract / escrow monitoring)

The highest-value monitoring primitive: given an outpoint, ask the chain its current status. This is
how you watch a contract output you funded and detect the moment a counterparty (or you) spends it:

```kotlin
val r = ec.getUtxo(outpoint)        // outpoint: iTxOutpoint, or a NexaTxInput, or the hex form
// GetUtxoResult: amount (Long sat), height (Int), scripthash, status, spent (GetUtxoSpentInfo),
//                group (String?) + group_quantity (Long?) when the output carries a token.
if (r.group != null) { /* this UTXO holds a token — see nexa-tokens-and-groups */ }
when (r.status) {
    "unspent" -> { /* still there; r.height is its confirmation height */ }
    "spent"   -> { /* consumed — r.spent tells WHERE: spending tx_hash, tx_pos, height */ }
}
```

**A spent outpoint still returns a result** — `status` is `"spent"` and `spent`
(`GetUtxoSpentInfo(height, tx_hash, tx_pos)`) identifies the spending transaction; for an
unspent outpoint `status` is `"unspent"` and the `spent` sub-fields are null. `ElectrumNotFound`
is thrown only for an outpoint the server does not know at all (the tx/output never existed —
usually a bad hex or wrong chain), because the underlying `blockchain.utxo.get` errors only in
that case. So the spend-detection branch is `r.status == "spent"`, **not** the exception. For
contract-spend detection, polling `getUtxo` on each new block (Pattern 6) tells you exactly when
— and by which tx — your funded output is consumed. (Each overload also takes a trailing
`timeoutInMs`.)

### Pattern 4: Watch an address / script

```kotlin
// All tx that touched a script, as (height, txid) pairs (height 0 = unconfirmed/mempool):
val history: Array<Pair<Int, Hash256>> = ec.getHistory(addr.lockingScript())

// The spendable UTXO set at an address, ready to build inputs from:
val utxos: List<Spendable> = ec.listUnspent(addr)        // addr: PayAddress (also a PayDestination overload)

// Confirmed/unconfirmed balance summary:
val bal = ec.getBalance(addr)                            // BalanceResult(confirmed, unconfirmed)

// When did this script FIRST appear on chain (block_height/block_hash/tx_hash, or not-found)?
val first = ec.getFirstUse(addr.lockingScript())        // FirstUseResult; throws ElectrumNotFound if unused
```

For precise value math prefer `listUnspent(...)` and sum the `Spendable.amount` (Long sat) rather
than reading `BalanceResult` summary fields.

### Pattern 5: Read token (group) state from chain

The electrum server exposes the token indexes — including the genesis metadata that
`nexa-tokens-and-groups` describes as off-chain (the server resolves and serves it):

```kotlin
val gi = ec.getTokenGenesisInfo(groupId)                // TokenGenesisInfo: ticker, name, decimals, doc url/hash, …
val tb = ec.getTokenBalance(addr)                       // TokenGetBalanceResult(confirmed: Map<gid,qty>, unconfirmed, cursor)
val tu = ec.getTokenUnspent(addrStr, tokenId = null, cursor = null)   // token UTXOs (paged via cursor)
val th = ec.getTokenHistory(groupId, cursor = null)     // (height, txid) pairs for a group, paged
```

`getTokenGenesisInfo(...).decimal_places` is the authoritative way for a light client to learn how
to scale a raw token quantity for display (the anti-pattern `nexa-tokens-and-groups` warns about).

**Subgroup caveat:** called with a **subgroup** id (the usual shape of an NFT — see
`nexa-tokens-and-groups`), `getTokenGenesisInfo` resolves nothing, because a subgroup has no genesis
of its own — the name/ticker/icon/decimals live on the **parent** group. Query
`gid.parentGroup()` instead. For app-level "give me this token's display metadata," prefer the
higher-level `getTokenInfo(grpId, getEc, cnxnMgr)` in libnexakotlin's `token.kt` (fetches and
signature-checks the token-description document too — `nexa-tokens-and-groups` Pattern 7); when a
wallet/SPV connection is already up, supply its own electrum channel (`{ bc.net.getElectrum() }`)
rather than constructing a second standalone client to a guessed port.

### Pattern 6: Monitor — subscribe to blocks, re-poll your watch list

There is no address-push, so the monitoring loop is "on each new block, re-query what you care
about":

```kotlin
val watchedOutpoints = listOf(contractUtxo)
ec.subscribeHeaders { tip ->
    // called on every new tip (only one header subscription at a time)
    for (op in watchedOutpoints) {
        try {
            val u = ec.getUtxo(op)
            if (u.status == "spent") onContractSpent(op, u.spent)   // u.spent: the spending tx
            // else "unspent": still there
        } catch (e: ElectrumNotFound) {
            // outpoint UNKNOWN to the server (never existed / bad hex) — a bug in the watch
            // list, not the spend signal; see Pattern 3
        }
    }
    for (addr in watchedAddresses) reconcile(addr, ec.getHistory(addr.lockingScript()))
}
// ... later:
ec.unsubscribeHeaders()
```

Do an initial poll up front (don't wait for the first block), and remember mempool (0-conf) activity
shows up in `getHistory` at height 0 — apply the finality rules in `nexa-transaction-construction`
before acting on it.

A high-value instance of this loop: **invalidating stored partial-tx offers** (an order book / "for
sale" table whose rows each hold a half-signed tx — `nexa-transaction-construction` Pattern 6). An
offer dies the moment its maker spends any input out from under it, so a background sweep walks the
stored offers and checks each input's outpoint via `getUtxo` — any input whose `status != "unspent"`
means the offer can never complete; delist it. Two operational notes from running such a sweep
against public servers: rate-limit it (a short sleep between outpoint queries, a longer one between
full sweeps), and on `ElectrumRequestTimeout` recycle the connection rather than retrying on the dead
one — when using a connection manager's pooled channel, `close(...)` the timed-out client,
`net.returnElectrum(ec)` it, and `net.getElectrum()` a fresh one (see
`nexa-wallet-lifecycle-and-chain` on the pool).

### Pattern 7: Broadcast and chain tip

```kotlin
val txid = ec.sendTx(tx.toByteArray())                  // broadcast via the electrum server; returns txid hex
val (tipHeader, _) = ec.getTip()                        // current tip header (+ a Long); also getHeader(height)
```

`sendTx` is a third broadcast path alongside the wallet's SPV `net.broadcastTransaction` and the
node RPC `sendrawtransaction` (`nexa-transaction-construction` / `nexa-rpc-node-client`).

### Pattern 8: Escape hatch for un-wrapped electrum methods

```kotlin
val raw: String? = ec.call("blockchain.relayfee", null, timeoutInMs)     // raw JSON string
ec.subscribe("blockchain.headers.subscribe", null) { json -> /* … */ }    // generic subscription
```

## Common mistakes and anti-patterns

### Using electrum to track your own wallet's funds

**Wrong**: polling `getBalance(myWalletAddress)` / `listUnspent(myWalletAddress)` to drive your own
wallet UI. *The wallet already follows its own keys via SPV and fires `setOnWalletChange`;
re-querying electrum for your own addresses duplicates that, misses rotation, and trusts an external
server for state you already have locally.*

**Right**: use the wallet's own `balance` / `setOnWalletChange` (`nexa-wallet-lifecycle-and-chain` /
`nexa-transaction-construction`). Reserve electrum for scripts/outpoints the wallet does **not** own
(contracts, counterparties, third-party addresses).

### Expecting a per-address push subscription

**Wrong**: assuming there's an "on payment to this address" callback. *The only push subscription is
`subscribeHeaders` (new blocks); there is no address subscription in this client, and only one
header subscription at a time is allowed.*

**Right**: subscribe to headers and **re-poll** `getHistory`/`getUtxo`/`listUnspent` for your watch
list on each block (Pattern 6), plus an initial poll.

### Trusting a single public server's answer as ground truth

**Wrong**: treating a `getUtxo`/`getHistory` reply from one public server as authoritative for a
high-value decision. *A light-client server can withhold or misreport; the library even hides the
server's raw error text so it isn't shown to users.*

**Right**: cross-check critical results against another server or your own node (`nexa-rpc-node-client`),
and require confirmation depth before acting (`nexa-transaction-construction`).

### Mismatching `useSSL` and the port

**Wrong**: `ElectrumClient(cs, host, port = 20002, useSSL = false)` (SSL port, plaintext mode) or the
inverse. *The connection silently fails to establish or times out.* **Right**: SSL ports (…002) with
`useSSL = true`; TCP ports (…001 / 30403) with `useSSL = false`.

### Blocking the event loop on a synchronous call

**Wrong**: calling `ec.getTx(...)` / `ec.listUnspent(...)` directly on a UI or Ktor request thread.
*They block on network I/O until reply or `timeoutInMs`.* **Right**: run them on a background
dispatcher (e.g. `withContext(Dispatchers.IO)`), or use the async callback overloads where provided.

### Reading a `not found` as an error rather than a state

`getFirstUse` throws `ElectrumNotFound` for "script never used" — often the *answer* you're
monitoring for (the address is fresh), not a failure; catch it explicitly and branch rather than
treating it like a timeout. For `getUtxo` the exception means "outpoint unknown to the server"
(never existed / bad hex) — a **spent** outpoint returns normally with `status == "spent"` (see
Pattern 3), so don't use the exception as the spend signal.

## Security considerations

- **The electrum server is untrusted infrastructure.** It can omit transactions, delay them, or
  misreport confirmation height; it cannot forge spends of keys it doesn't hold. Don't make
  irreversible decisions from a single server's word — corroborate and require confirmations.
- **Querying reveals your interest.** Asking a public server for an address's history/UTXOs tells
  that server you care about that address (and links your queries by connection). For
  privacy-sensitive monitoring, run your own electrum server or rotate servers, and don't batch
  unrelated watched addresses over one connection if linkage matters.
- **Use SSL for anything over an untrusted network.** Plaintext TCP is fine for a local/regtest
  server; over the internet use the SSL ports so queries and broadcasts aren't observable in transit.
- **Validate transactions you fetch before trusting their contents.** A server hands you bytes;
  parse them with libnexakotlin and check that a returned tx actually matches the txid/outpoint you
  asked about, rather than assuming the server returned the right object.
- **`sendTx` through a public server exposes your raw tx to that server first.** That's inherent to
  light-client broadcast; if the tx's mere existence is sensitive before it's mined, broadcast
  through your own node or P2P path instead.

## Related skills and references

- `nexa-wallet-lifecycle-and-chain` — the SPV wallet uses electrum internally for *its own* keys; this
  skill is the general-purpose client for *arbitrary* on-chain state. Both connect to electrum
  servers; the wallet path is key-scoped, this one is not.
- `nexa-transaction-construction` — `sendTx` is an alternative broadcast path, and outpoint monitoring
  (Pattern 3/6) is how you detect when a funded contract UTXO is spent or a pushed offer is taken;
  apply its 0-conf/confirmation-depth finality rules to anything electrum reports.
- `nexa-rpc-node-client` — the owner-operated full-node alternative: authenticated JSON-RPC with node
  control (mining, node wallet, stats). Use electrum for trustless-ish public queries, RPC for a
  node you run.
- `nexa-tokens-and-groups` — `getTokenGenesisInfo` (decimals/ticker), `getTokenBalance`,
  `getTokenUnspent`, `getTokenHistory` are the on-chain token reads behind that skill's metadata and
  ownership discussion.
- `nexa-capd-messaging` — coordinate a multi-party tx off-chain via CAPD, then watch the chain here for
  the resulting broadcast and the counterparty's UTXO spends.

### Supporting files in this folder

- `electrumMethodReference.md` — the full method surface (construction + per-chain port constants,
  tx lookups, `getUtxo`/UTXO checks, address/script watching, token reads, headers/monitoring,
  broadcast, the `call`/`subscribe` escape hatch) with result data-class fields and the exception set.
- `addressWatcherTemplate.kt` — a drop-in "watch these scripts/outpoints" monitor built on
  `subscribeHeaders` + re-poll, with the initial-poll and confirmation-depth handling wired in.