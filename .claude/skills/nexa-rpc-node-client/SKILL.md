---
name: nexa-rpc-node-client
description: "Talks to a Nexa full node you operate over its JSON-RPC interface from Kotlin (nexarpc). Use when broadcasting or looking up transactions through your node, reading chain or mempool state, driving the node's own wallet, issuing tokens with node tooling, signing/verifying messages with node keys, mining regtest blocks in tests, reading node statistics, or fixing RPC Unauthorized errors. Triggers: nexarpc, org.nexa.nexarpc, NexaRpc, NexaRpcFactory, JvmNexaRpc, getblockcount, getrawtransaction, sendrawtransaction, enqueuerawtransaction, listunspent, getpeerinfo, generate, tokenNew/tokenMint, signmessage, getstat, capd. Not for light-client queries (nexa-electrum-monitoring); this is owner/node control."
---

# Nexa full-node JSON-RPC client (nexarpc)

## When to use this skill

Trigger when a developer is talking to a Nexa **full node** they operate, over its
JSON-RPC interface, from Kotlin — broadcasting/looking up transactions through the node,
reading chain or mempool state, driving the node's own wallet, issuing tokens with the
node's token tooling, signing/verifying messages with node keys, mining regtest blocks in
tests, or reading node statistics. Concretely trigger on:

- Keywords: `nexarpc`, `org.nexa.nexarpc`, `NexaRpc`, `NexaRpcFactory`, `JvmNexaRpc`,
  `NexaRpcException`, `nexa-cli`, `nexa.conf`, `server=1`, JSON-RPC to a Nexa node, RPC
  username/password, `calls`/`callje`, `getblockcount`, `getbestblockhash`, `getblock`,
  `getrawtransaction`, `sendrawtransaction`, `enqueuerawtransaction`, `gettransactiondetails`,
  `gettransaction`, `getutxo`/`gettxout`, `listunspent`, `gettxpoolinfo`, `getrawtxpool`,
  `getpeerinfo`, `getwalletinfo`, `getbalance`, `getnewaddress`, `sendtoaddress`, `generate`,
  `invalidateblock`, `evicttransaction`, `abandontransaction`, `getstat`, `getstatlist`,
  `getstatInt`/`getstatIntRange`/`getstatDouble`/`getstatDoubleRange`, `token new`/`tokenNew`,
  `tokenMint`/`tokenMelt`/`tokenSend`/`tokenMintage`/`tokenAuthorityCreate`, `tokenBalance`,
  `signdata`, `signmessage`, `verifymessage`, `capd`, `HashId`, the suspend `_`-prefixed
  variants (`_getblockcount`, `_sendrawtransaction`, …), RPC port (18332 / 7229).
- Tasks: "broadcast a tx through my own node", "read the chain tip / block height from a
  node", "look up a raw transaction by id", "check the node wallet balance", "mine regtest
  blocks in a test", "issue a token on regtest", "read node statistics / monitor a node",
  "call an RPC the library doesn't wrap yet", "the RPC returns Unauthorized".

**Negative triggers** — do NOT use this skill for:
- Building/signing the transaction you intend to broadcast — use
  `nexa-transaction-construction` (this skill only *submits* the finished hex through a node).
- The SPV / P2P broadcast path (`net.broadcastTransaction`, blockchain monitoring,
  `setOnWalletChange`) — that is libnexakotlin's own connection manager, a different channel
  from talking to a node over RPC; use `nexa-transaction-construction`.
- The wallet-protocol (Wally/TDPP) connection — use `nexa-wallet-connection`. The Wally wallet
  is unrelated to the node's RPC port.
- Token *semantics* (groups, authorities, covenants) — use `nexa-tokens-and-groups`; this skill
  only covers the node's token-issuance RPCs.

## Mental model

`nexarpc` (`org.nexa:nexarpc`, Kotlin package `org.nexa.nexarpc`) is a thin, typed
JSON-RPC client for a Nexa **full node you control**. Every call maps to a command you could
also run via `nexa-cli`; the library just does it programmatically and deserializes the JSON
reply into Kotlin data classes. It is especially common in **test code** (spin up a regtest
node, mine blocks, fund and broadcast, assert) but is equally usable from a server that runs
its own trusted node.

**Chain note:** regtest is not the default *development* chain — testnet is (see
`nexa-wallet-lifecycle-and-chain`, "Which chain do I develop on?"). Regtest is what you reach for
here specifically when a test or workflow needs to **control block production** — force-mining
(`generate`), deterministic confirmations, or reorgs (`invalidateblock`). Whichever chain your app
targets, the node you point this client at must be running that **same** chain (a testnet node
can't serve regtest and vice versa), and its RPC host/port/credentials come from that node's
`nexa.conf`.

Four facts shape how you use it:

1. **It requires a node you own.** Full-node RPC is owner-only: you must run the node, set
   `server=1` in `nexa.conf`, and authenticate with the RPC username/password from that file.
   If `nexa-cli` works against your node, this library will too. It is *not* a way to reach a
   public/third-party node.

2. **It is a node connection, not a wallet/SPV connection.** This is a different channel from
   libnexakotlin's blockchain/`net` layer (the P2P/SPV path that `nexa-transaction-construction`
   uses for `net.broadcastTransaction` and `setOnWalletChange`). The RPC client talks to the
   node's *own* wallet and chain index over authenticated HTTP. A server can use either or
   both; don't conflate them.

3. **The interface is blocking.** Every method on the `NexaRpc` interface returns a plain
   value (it runs the underlying suspend call to completion internally). There is no callback
   or `Flow`. Calling these from a coroutine/Ktor handler thread blocks that thread — wrap in
   `withContext(Dispatchers.IO)` (see anti-patterns). Each call also opens and closes a fresh
   HTTP connection (`Connection: close`), so the client is convenient and stateless, not a
   high-throughput pooled connection. Under the hood each blocking method is a
   `runBlocking { ... }` wrapper around a public `suspend` twin on the `JvmNexaRpc`
   implementation (`_getblockcount()`, `_sendrawtransaction(...)`, `_calls(...)`, …) —
   coroutine-native code can call those directly instead (see the alternative under the
   blocking anti-pattern).

4. **Errors surface as `NexaRpcException`.** Any RPC whose JSON reply carries an `error`
   object (or a null `result`) throws `NexaRpcException(message, code)`, where `code` is the
   node's JSON-RPC error code (and `message` its text). A bad username/password throws
   `NexaRpcException("Unauthorized (bad rpc username/password)", 401)`. Catch this one type to
   handle all RPC failures. Two failure shapes bypass it and propagate raw, so a robust caller
   handles them separately: **transport failures** (node down/unreachable — e.g.
   `java.net.ConnectException` on JVM) and **reply-shape drift** (a kotlinx
   `SerializationException` when the node's reply is missing a field the client's data class
   expects; unknown *extra* fields are ignored, so only a large node↔artifact version gap
   triggers this — update the artifact to match your node).

**Typed methods vs. the escape hatch.** Common RPCs have typed wrappers (`getblockcount(): Long`,
`getblock(...): BlockInfo`, …). For any RPC without a wrapper, two generic calls take the method
name plus a `List<String>?` of params: `calls(name, params): String` (raw JSON text) and
`callje(name, params): JsonElement` (parsed JSON you traverse yourself).

**`HashId` reverses for display.** The library's `HashId` type stores hash bytes in one order
and renders them reversed: `HashId(hex)` reverses the input on construction, and `toHex()` /
`toString()` return the **bitcoin-standard display hex** (the reversed form an explorer shows).
`equals` compares the raw bytes by content. So a `HashId.toHex()` is already the explorer/CLI
form — do not reverse it again. (This is the opposite convention from libnexakotlin's `Hash256`,
whose `toHex()` is *not* display-reversed for txids — see `nexa-transaction-construction`. The two
hash types live in different libraries; keep their conventions straight.)

## Setup and versions

You need the `nexarpc` artifact:

```kotlin
// gradle/libs.versions.toml  (look up the current version in the GitLab Maven registry)
[libraries]
nexa-rpc = { module = "org.nexa:nexarpc", version.ref = "nexa_nexarpc" }
```

Its GitLab Maven repository must be registered in `settings.gradle.kts` (project `38119368`;
see `nexa-project-setup` for the full repositories block):

```kotlin
maven { url = uri("https://gitlab.com/api/v4/projects/38119368/packages/maven") }  // nexarpc
```

Because it is most often used to drive a node from tests, it is frequently a
`testImplementation` rather than a main dependency:

```kotlin
testImplementation(libs.nexa.rpc)   // or implementation(...) if a server uses it at runtime
```

Imports:

```kotlin
import org.nexa.nexarpc.NexaRpc
import org.nexa.nexarpc.NexaRpcFactory
import org.nexa.nexarpc.NexaRpcException
import org.nexa.nexarpc.HashId
import com.ionspin.kotlin.bignum.decimal.BigDecimal   // getbalance/sendtoaddress use BigDecimal
```

The library brings the Ktor client and kotlinx-serialization in transitively. Note: the
current package is `org.nexa.nexarpc` and the coordinate is `org.nexa:nexarpc`; older releases
and some older docs reference a `Nexa.NexaRpc` package / `Nexa:NexaRpc` coordinate — use the
current `org.nexa.*` forms.

Two setup facts worth knowing:

- **The artifact is Kotlin Multiplatform** (JVM, Android, iOS/macOS, Linux, Windows targets).
  The implementation class is named `JvmNexaRpc` for historical reasons but lives in common
  code — you are not restricted to the JVM.
- **`org.nexa.nexarpc` exports its own top-level `String.fromHex()` / `ByteArray.toHex()`
  extensions.** libnexakotlin defines identically-named extensions, so a file that
  wildcard-imports both packages gets an ambiguity error on `toHex()`/`fromHex()` — resolve it
  with explicit imports (or import one side qualified).

## Core patterns

### Pattern 1: Create the client

```kotlin
// Defaults target a local regtest node: http://127.0.0.1:18332/ , user/pwd "regtest"/"regtest".
val rpc: NexaRpc = NexaRpcFactory.create(
    url = "http://127.0.0.1:18332/",
    username = NEXA_RPC_USER,        // from your node's nexa.conf
    password = NEXA_RPC_PASSWORD)
```

The `url`, `username`, and `password` come from the node's config. The client holds no
persistent connection; you can create one and reuse it, or create per-call — each RPC opens
its own short-lived HTTP connection regardless.

**Which port?** The RPC port is per-chain and comes from the node's `nexa.conf` (`rpcport`).
`18332` (the factory default) is the regtest convention; the library's own test suite reaches a
**testnet** node on port `7229`. Whatever chain your node runs, read the actual port from its
config rather than assuming — a "connection refused" from a correct host is very often a
port/chain mismatch.

### Pattern 2: Read chain / mempool state

```kotlin
val height: Long      = rpc.getblockcount()
val tipHash: HashId   = rpc.getbestblockhash()
val tip               = rpc.getblock(tipHash)         // BlockInfo: height, time, mediantime, txid/txidem lists, …
val blockAt0          = rpc.getblock(0L)              // by height (overloaded)

val poolHashes        = rpc.getrawtxpool()            // List<HashId> of unconfirmed txs
val poolInfo          = rpc.gettxpoolinfo()           // TxPoolInfo: size, bytes, txpoolminfee, tps, …
```

`BlockInfo` carries both `time` (the block's own timestamp) and `mediantime` (the
median-time-past). The latter is the consensus clock that CLTV / mempool finality gate on —
reading `rpc.getblock(rpc.getbestblockhash()).mediantime` from your own node is a direct way to
check MTP (see `nexa-locktime-cltv`, which otherwise approximates it via tip time).

`BlockInfo` also links the chain: `previousblockhash` (added in a recent release; `null` at
genesis), `ancestorhash`, and `nextblockhash` let a test walk backwards/forwards from the tip —
handy for asserting what `invalidateblock` rolled back, without re-fetching by height.

### Pattern 3: Broadcast a finished transaction through the node

Build and sign the tx elsewhere (`nexa-transaction-construction`); this skill submits the hex.
Two methods differ in *when the node checks validity*:

```kotlin
// sendrawtransaction: validate first, throw NexaRpcException if invalid, then enqueue. Returns the txid(em).
val txid: HashId = rpc.sendrawtransaction(txHex)        // also an overload taking ByteArray
// enqueuerawtransaction: enqueue without waiting for full verification (faster, weaker guarantee).
val txid2: HashId = rpc.enqueuerawtransaction(txHex)    // also a ByteArray overload
```

Prefer `sendrawtransaction` when you want a hard yes/no on acceptance; use
`enqueuerawtransaction` when you've already validated and just want to relay quickly.

Re-broadcasting a tx the node already has throws with `txn-already-in-mempool` (still
unconfirmed) or `already in block chain` (mined). These are **not** real failures — idempotent
retry / auto-claim loops hit them constantly. Fold them into success (see
`nexa-transaction-construction` § "broadcast via Nexa RPC" for the full idempotent wrapper, and
`nexa-debugging-onchain-errors`).

`sendrawtransaction` is also the **reliable broadcast path for a covenant spend of a 0-conf
output** — it submits straight to the node's mempool, bypassing the bloom-filter gating that
rejects the same spend over the wallet's P2P net with `NexaRpcException(73, "please wait for wallet
sync")`. Because it throws `NexaRpcException(message, code)`, you can branch on `e.code` to retry
the transient and fail fast on the rest. The rejection codes you'll actually see:

| `e.code` | meaning | fix |
| --- | --- | --- |
| **73** | "please wait for wallet sync" — the node's bloom view doesn't yet include the input, OR (worse) a stale 0-conf wallet backlog is deferring *every* tx | transient: retry (also wait for the **parent** to reach the node's mempool first); if it's every tx, `wallet.cleanUnconfirmed()` — see `nexa-transaction-construction`/`nexa-wallet-lifecycle-and-chain` |
| **66** | "mempool min fee not met" — a self-funded covenant spend paid a flat fee too small for its size | size the fee: `fee ≥ ceil(txSize · 1.01)` — see `nexa-transaction-construction` |
| **16** | "inconsistent input value" — a signed input's serialized `amount` ≠ the real UTXO value | keep each input's `amount` = the true UTXO value; bias fee-preseed via `inamt` in the URL only — see `nexa-transaction-construction` |

None of 73/66/16 is a *script* error (those are code 16 with a `mandatory-script-verify-flag`
message vs the "inconsistent input value" text above — read `e.message`, not just `e.code`). A
wrong `server.rpc` user/pass never reaches any of these — it surfaces as
`java.net.ConnectException: Connection refused` from `JvmNexaRpc`, not an auth error.

### Pattern 4: Fetch and inspect a transaction

Three lookups with different shapes — pick by what you need:

```kotlin
// Raw serialized bytes (parse with libnexakotlin if you need the structured tx):
val rawBytes: ByteArray = rpc.getrawtransaction(txid)         // String or HashId overloads

// Node-decoded details for ANY tx the node knows (in pool or chain):
val details = rpc.gettransactiondetails(txid)                // TransactionDetails: vin/vout, fee, size, blockhash, in_txpool, …

// Wallet-centric view — ONLY works for a tx in the node's own wallet:
val walletTx = rpc.gettransaction(txid)                      // TransactionInfo: confirmations, details[], hex, …

// A single UTXO by outpoint (wraps the node's gettxout):
val utxo = rpc.getutxo(outpointString)                       // Txout: value, confirmations, scriptPubKey
```

`gettransactiondetails` is the right choice for an arbitrary on-chain tx; `gettransaction`
throws unless the tx belongs to the node's wallet.

One value-math caveat on `getutxo`: its `Txout.value` is a lossy `Double` and `Txout` has **no
exact `satoshi` field** (unlike `Unspent` from `listunspent`). If you need the exact amount of
an arbitrary outpoint, fetch the raw tx and parse it with libnexakotlin. `Txout.scriptPubKey`
does expose `scriptHash`/`argsHash`/`addresses`, which pairs with the argsHash-reading patterns
in `nexa-identity-and-addresses`.

### Pattern 5: Call an RPC the library doesn't wrap

Not every node RPC has a typed method. Use the escape hatch with the method name and a
`List<String>?` of string params; you parse the result yourself:

```kotlin
val rawJson: String = rpc.calls("getchaintips")              // raw JSON text
val parsed = rpc.callje("getchaintips")                      // kotlinx JsonElement to traverse
val asm = rpc.callje("decodescript", listOf(scriptHex))
```

This is also the cleanest way to confirm a new RPC's exact shape before writing a typed
wrapper. (If you add a wrapper, the library welcomes the contribution — the existing typed
methods are the template.)

### Pattern 6: Node statistics (monitoring)

The node exposes a statistics subsystem. `getstatlist()` enumerates the available metric names
(e.g. `net/send/total`, `net/recv/total`, `txpool/size`, `mining/blocks`); `getstat(...)`
fetches one:

```kotlin
val names: List<String> = rpc.getstatlist()
// series ∈ "total" | "now" | "all" | "sec10" | "min5" | "hourly" | "daily" | "monthly"
val raw = rpc.getstat("net/send/total", series = "now")      // List<Map<String, JsonElement>> (raw, format varies per stat)
val hourly = rpc.getstat("net/send/total", "hourly", count = "5")
val verbose = rpc.getstat("txpool/size", "now", verbose = true)   // verbose prepends a "-v" arg
```

Because the raw return is heterogeneous JSON (a stat may be a scalar, an array, or
`{min,val,max}` range objects), typed convenience helpers flatten the common shapes for you;
they skip values that don't fit (e.g. an Int too large), so treat their output as best-effort:

```kotlin
val ints:    List<Int>           = rpc.getstatInt("net/send/total", "now")
val doubles: List<Double>        = rpc.getstatDouble("blockValidationTime", "now")
val ranges:  List<NexaRpc.IntRange>    = rpc.getstatIntRange("txpool/size", "now")     // each .min/.`val`/.max
val dranges: List<NexaRpc.DoubleRange> = rpc.getstatDoubleRange(...)
```

`getstat()` with no statistic name throws (the node requires one) — `NexaRpcException` with
code `-1`. An unknown statistic name throws with a non-zero code. (The typed `getstat*` helpers
are a relatively recent addition; older releases expose only the raw `getstat`/`getstatlist`.)

### Pattern 7: Drive the node's wallet

```kotlin
val bal: BigDecimal = rpc.getbalance()                       // whole-NEXA BigDecimal (see unit anti-pattern)
val info            = rpc.getwalletinfo()                    // WalletInfo: syncheight, txcount, …
val addr: String    = rpc.getnewaddress()                    // new P2PKT receive address
val oldStyle        = rpc.getnewaddress("p2pkh")             // address-type argument
val sentTxid        = rpc.sendtoaddress(addr, BigDecimal.parseString("1000.23"))
val unspent         = rpc.listunspent()                      // List<Unspent> — read .satoshi (Long), NOT .amount (Double)
```

### Pattern 8: Issue tokens with the node's token tooling

The node's `token` command is exposed as typed methods. This is the programmatic form of the
`token new …` genesis recipe described in `nexa-tokens-and-groups` Pattern 7:

```kotlin
// Genesis: returns (groupIdentifier, genesisTxid). descHash is the SHA-256 of the
// token-description document (mind the trailing-newline trap in nexa-tokens-and-groups).
val (groupId, genesisTx) = rpc.tokenNew(
    address = issuerAddr, tokenTicker = "TST", tokenName = "Test token",
    descUrl = "https://example.org/TSTdesc", descHash = docSha256Hex, decimals = 2)

val mintTx  = rpc.tokenMint(groupId, issuerAddr, quantity = 100_000)
val meltTx  = rpc.tokenMelt(groupId, quantity = 10_000)
val (mintage, decimals) = rpc.tokenMintage(groupId)          // outstanding supply (smallest unit) + decimals string
val sendTx  = rpc.tokenSend(groupId, recipientAddr, quantity = 5)
val authTx  = rpc.tokenAuthorityCreate(groupId, addr2, authFlags = listOf("MINT", "MELT"))
val (bal, balDecimals) = rpc.tokenBalance(groupId)           // node wallet's balance of this group
val (addrBal, _) = rpc.tokenBalance(groupId, issuerAddr)     // narrowed to one address
```

(`tokenBalance` had a copy-paste bug before a mid-2026 fix — it issued the node's `melt` verb
and errored instead of returning a balance. If it misbehaves, update your nexarpc artifact.)

These are convenient for issuance and for test setup, but they operate on the *node's* wallet
and keys. For app-controlled minting (authority UTXOs you hold, mint-on-demand half-txs), build
the tx yourself — see `nexa-tokens-and-groups` Pattern 9.

### Pattern 9: Sign / verify messages with node keys

```kotlin
val sig = rpc.signMessage(addr, message)
val ok  = rpc.verifyMessage(addr, sig, message)              // Boolean
val dataSigHex = rpc.signData(addr, format = "string", message = msg)     // hex (64-byte sig for "string")
val verbose    = rpc.signDataVerbose(addr, "string", msg)    // SignData: msghash, signature, pubkey, pubkeyhash
```

### Pattern 10: Regtest helpers for tests

`generate` and `invalidateblock` only work on regtest/testnet and make deterministic tests
possible:

```kotlin
val mined: List<HashId> = rpc.generate(1)                    // mine N blocks; returns their hashes
// ... fund, broadcast, assert ...
rpc.invalidateblock(mined[0])                                // roll a block back
rpc.evicttransaction(txid)                                   // drop a tx from the pool (may be re-relayed)
rpc.abandontransaction(txid)                                 // mark an in-wallet, unconfirmed tx abandoned so its inputs can be respent
```

A typical regtest test arc: `generate(1)` to get spendable coins → `listunspent()` /
`getbalance()` → `sendtoaddress(...)` → `getrawtxpool()` to see it pending → `generate(1)` to
confirm → assert via `getblockcount()` / `gettransactiondetails(...)`.

## Common mistakes and anti-patterns

### Calling the blocking RPC methods directly on a coroutine dispatcher

**Wrong**:
```kotlin
get("/api/height") {
    val h = rpc.getblockcount()        // blocks this Ktor worker thread on network I/O
    call.respond(h)
}
```
*The `NexaRpc` interface methods run their HTTP call to completion synchronously. Invoking them
on a coroutine/event-loop thread ties that thread up for the whole round trip, starving other
requests.*

**Right**: push the blocking call onto the IO dispatcher.
```kotlin
get("/api/height") {
    val h = withContext(Dispatchers.IO) { rpc.getblockcount() }
    call.respond(h)
}
```

Alternative pattern, preferred when your call site is already coroutine-native: hold the client
as `JvmNexaRpc` and use its public `suspend` `_`-prefixed twins directly (each blocking interface
method is just `runBlocking` around one of these):
```kotlin
val rpc = JvmNexaRpc(url, user, pwd)      // or: NexaRpcFactory.create(...) as JvmNexaRpc
get("/api/height") {
    call.respond(rpc._getblockcount())    // suspends; no thread blocked, no runBlocking
}
```
Never call the *blocking* form inside `runBlocking` on an event-loop thread — that is the same
trap with an extra layer.

### Reversing a `HashId.toHex()` for an explorer or CLI

**Wrong**:
```kotlin
val displayHex = rpc.getbestblockhash().hash.reversed().toByteArray().toHex()   // double-reversed
```
*`HashId.toHex()` already returns the bitcoin-standard, display-reversed form. Reversing again
gives the wrong string.*

**Right**:
```kotlin
val displayHex = rpc.getbestblockhash().toHex()    // or .toString() — both are display form
```

(Conversely, don't assume libnexakotlin's `Hash256.toHex()` matches: that one is *not*
display-reversed for txids. The two hash types come from different libraries.)

### Using a `HashId` as a `HashMap`/`HashSet` key

**Wrong**:
```kotlin
val seen = HashSet<HashId>()
seen.add(rpc.sendrawtransaction(txHex))
val alreadySent = txid in seen      // false negatives: equal HashIds hash to different buckets
```
*`HashId` overrides `equals` (content comparison of the hash bytes) but not `hashCode`, so two
equal ids get different identity hash codes. `List.contains` and `==` work; hashed containers
don't.*

**Right**: key by the display hex.
```kotlin
val seen = HashSet<String>()
seen.add(rpc.sendrawtransaction(txHex).toHex())
```

### Using `gettransaction` for a tx that isn't in the node's wallet

**Wrong**:
```kotlin
val info = rpc.gettransaction(someArbitraryTxid)   // throws NexaRpcException — not a wallet tx
```

**Right**: for any on-chain or in-pool tx, use `gettransactiondetails` (decoded) or
`getrawtransaction` (raw bytes). Reserve `gettransaction` for the node wallet's own txs.

### Reading `Unspent.amount` (Double) instead of `Unspent.satoshi` (Long)

**Wrong**:
```kotlin
val value = rpc.listunspent().first().amount    // Double whole-NEXA — lossy, floating point
```
*The `amount` field is a non-exact decimal fraction. The library's own type comment says not to
use it.*

**Right**:
```kotlin
val sats = rpc.listunspent().first().satoshi    // Long, exact, smallest unit
```

The same satoshi-vs-whole-NEXA split from `nexa-transaction-construction` applies; here the
whole-NEXA fields are lossy `Double`s, so prefer the `satoshi` `Long` wherever both exist.
(`getbalance`/`sendtoaddress` use `BigDecimal`, which is exact, for whole-NEXA amounts.)

### Catching the wrong exception type

**Wrong**: catching `IOException` / a Ktor exception and assuming that covers RPC errors.
*A node that returns a JSON-RPC `error` object completes the HTTP request fine — the failure
surfaces as `NexaRpcException`, not a transport error.*

**Right**: catch `NexaRpcException` (it carries the node's `code` and `message`); inspect
`e.code` to distinguish causes (`401` = bad credentials, `-1` = missing required arg, other
node codes per the RPC).

### Treating "already in mempool / block" as a broadcast failure

**Wrong**: marking an operation failed when `sendrawtransaction` throws on a re-broadcast.
**Right**: fold `txn-already-in-mempool` and `already in block chain` into success — see
`nexa-transaction-construction` and `nexa-debugging-onchain-errors`.

### Expecting a persistent / pooled connection for high throughput

Each call opens and closes its own HTTP connection (`Connection: close`). For a tight loop of
thousands of calls this is the bottleneck, not the node. Batch what you can (e.g. one
`getrawtxpool` then iterate), and don't architect a hot path around per-item RPCs.

### Hardcoding the regtest defaults / committing RPC credentials

**Wrong**: shipping `NexaRpcFactory.create()` (which defaults to `regtest`/`regtest` on
`127.0.0.1:18332`) or pasting real credentials into source.
**Right**: read `url`/`username`/`password` from config (the same config that holds your
node's `nexa.conf` values), and keep credentials out of source control.

## Security considerations

- **RPC credentials are full control of the node and its wallet.** Anyone with the RPC
  username/password can move the node wallet's funds, mint tokens with its keys, and invalidate
  blocks. Never commit them, never log them, and treat them like a private key.

- **The RPC port is owner-only — never expose it to the internet.** Bind the node's RPC to
  localhost (or a trusted private interface) and reach a remote node through an SSH tunnel or a
  TLS-terminating proxy. The library authenticates with HTTP Basic over whatever transport you
  point it at; plain `http://` sends the credentials in the clear, so only use it on a trusted
  local link.

- **A 401 is a credentials problem, surfaced as `NexaRpcException(code = 401)`.** Don't retry it
  in a loop — fix the username/password. Avoid logging the response on auth failures since the
  request carried the Basic-auth header.

- **Don't log raw tx hex, signatures, or `signData` output.** They contain spendable / replayable
  material. The node-signing methods return real signatures over real keys.

- **The node's wallet is shared mutable state.** `sendtoaddress`, `tokenMint`/`tokenMelt`,
  `abandontransaction`, and `generate`/`invalidateblock` change node-global state. Two processes
  (or a test and a server) pointing at the same node can interfere; isolate test nodes (a
  dedicated regtest instance) from anything that matters.

- **`generate` and `invalidateblock` are regtest/testnet powers.** They have no effect (or are
  rejected) on mainnet; never write production logic that depends on being able to mine or roll
  back blocks.

## Related skills and references

- `nexa-transaction-construction` — build and sign the tx you submit via `sendrawtransaction` /
  `enqueuerawtransaction`; the idempotent "already in mempool/block" broadcast wrapper; the
  alternative SPV/P2P broadcast path (`net.broadcastTransaction`) and how it differs from RPC.
- `nexa-tokens-and-groups` — token semantics behind `tokenNew`/`tokenMint`/… ; Pattern 7's
  `token new` genesis recipe and the description-document SHA-256 the `descHash` argument commits.
- `nexa-locktime-cltv` — `BlockInfo.mediantime` from `getblock` is a direct read of the MTP that
  CLTV/mempool finality gate on.
- `nexa-debugging-onchain-errors` — decoding the `code:`/`reason:` in a rejection, and the benign
  `txn-already-in-mempool` / `already in block chain` re-broadcast results.
- `nexa-wallet-lifecycle-and-chain` — the **other** chain channel: a libnexakotlin wallet's SPV/electrum
  connection (`blockchainFor`/`exclusiveNodes`/`getTip`), distinct from this authenticated JSON-RPC
  client to a node you own. A server may use both; don't conflate `wallet.blockchain.net` with a
  `NexaRpc`.
- `nexa-electrum-monitoring` — the **no-node-required** read/monitor alternative: `ElectrumClient`
  queries arbitrary chain state against public (untrusted) electrum servers, where this skill needs
  a node you operate. Use electrum for trustless-ish light-client reads, RPC for node control.
- `nexa-project-setup` — the GitLab Maven repositories block (project `38119368` for nexarpc) and
  version-pinning approach.
- `nexa-script-machine-testing` — the complementary *offline* test path: a test that verifies a
  spend's script logic in the local script VM. Use the VM for fast script-validity checks and a
  regtest node (this skill) for full end-to-end testing (fees, mempool, confirmations); both are
  test-time tools, not part of the production send path.

### Supporting files in this folder

- `rpcMethodReference.md` — the full typed-method surface (signatures + return data-class fields),
  grouped by area, with the `HashId`/`NexaRpcException` conventions and the `calls`/`callje`
  escape hatch.
- `regtestHarness.kt` — a drop-in test fixture that creates a regtest client, mines starter
  coins, and exposes fund/broadcast/confirm/reorg + exact-MTP helpers.