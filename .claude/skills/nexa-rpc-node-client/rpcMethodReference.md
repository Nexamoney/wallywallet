# NexaRpc method reference

The full typed `org.nexa.nexarpc.NexaRpc` surface — methods, their return types, the escape hatch,
and the exception/`HashId` conventions. This is the lookup table behind `SKILL.md`; read it first
for the mental model (owner-only node connection, blocking calls, fresh HTTP request per call).
Grounded in nexarpc `NexaRpc.kt` / `JvmNexaRpc.kt`; the declarations there are authoritative.

## Factory and connection

```kotlin
object NexaRpcFactory {
    fun create(url: String = "http://127.0.0.1:18332/",
               username: String = "regtest",
               password: String = "regtest"): NexaRpc
}
```

The defaults target a local **regtest** node — override all three for any other node. Each call
opens a fresh `Connection: close` HTTP request and runs to completion (blocking); there is no
connection pool. All RPC-level failures throw `NexaRpcException` (below).

**Suspend variants.** Every blocking interface method is a `runBlocking { ... }` wrapper around a
public `suspend` twin on the `JvmNexaRpc` implementation class, named with a leading underscore:
`_getblockcount()`, `_sendrawtransaction(txHex)`, `_calls(name, params)`, `_getstat(...)`, etc.
Coroutine code (a Ktor handler, a `Flow` pipeline) can hold the client as `JvmNexaRpc` and call
these directly instead of pushing the blocking form onto `Dispatchers.IO` — see the alternative
pattern in `SKILL.md`. (Despite the `Jvm` prefix, the class lives in common code; the library
publishes JVM, Android, iOS/macOS, Linux, and Windows targets.)

## Chain / mempool reads

```kotlin
fun getblockcount(): Long
fun getbestblockhash(): HashId
fun getblock(hash: HashId): BlockInfo
fun getblock(height: Long): BlockInfo
fun getrawtxpool(): List<HashId>
fun gettxpoolinfo(): TxPoolInfo
fun getpeerinfo(): List<PeerInfo>
fun bch_getmempoolinfo(): BchMemPoolInfo
```

`BlockInfo` carries `mediantime: Long` — the exact median-time-past for CLTV decisions
(`nexa-locktime-cltv`), alongside `time`, `height`, `merkleroot`, `txid`/`txidem` lists, etc.
It also links the chain for walking/reorg checks: `previousblockhash: HashId?` (added in a
recent release; `null` at the genesis block), `ancestorhash`, `nextblockhash: HashId?`, plus
`confirmations`, `status`, and `onMainChain`.

Use the `calls`/`callje` escape hatch only for node RPCs with no typed wrapper.

## Node statistics

```kotlin
fun getstat(statistic: String? = null, series: String? = null,
            count: String? = null, verbose: Boolean = false): List<Map<String, JsonElement>>
fun getstatlist(): List<String>
fun getstatInt(statistic: String? = null, series: String? = null,
               count: String? = null, verbose: Boolean = false): List<Int>
fun getstatIntRange(...same params...): List<NexaRpc.IntRange>       // IntRange(min, `val`, max)
fun getstatDouble(...same params...): List<Double>
fun getstatDoubleRange(...same params...): List<NexaRpc.DoubleRange> // DoubleRange(min, `val`, max)
```

`series` ∈ `"total" | "now" | "all" | "sec10" | "min5" | "hourly" | "daily" | "monthly"`; `count`
is a stringified sample count; `verbose = true` prepends a `-v` argument. Calling `getstat()` with
no statistic name throws (`NexaRpcException`, code `-1`). The typed helpers flatten the
heterogeneous JSON best-effort and *skip* values that don't fit the target type (e.g. an `Int`
overflow) — see `SKILL.md` Pattern 6.

## Transactions

```kotlin
fun sendrawtransaction(txHex: String): HashId       // validates; throws on rejection
fun sendrawtransaction(tx: ByteArray): HashId
fun enqueuerawtransaction(txHex: String): HashId    // relays without full verification (faster, weaker)
fun enqueuerawtransaction(tx: ByteArray): HashId
fun getrawtransaction(hash: String): ByteArray
fun getrawtransaction(hash: HashId): ByteArray
fun gettransactiondetails(hash: String): TransactionDetails   // works for any tx
fun gettransactiondetails(hash: HashId): TransactionDetails
fun gettransaction(hash: String): TransactionInfo             // WALLET tx only (wallet's view)
fun gettransaction(hash: HashId): TransactionInfo
fun getutxo(hash: String): Txout
fun getutxo(hash: HashId): Txout
fun evicttransaction(hash: HashId): Long
fun abandontransaction(hash: HashId)
```

`gettransactiondetails` is the general lookup (`in_txpool`/`in_orphanpool`, full `vin`/`vout`);
`gettransaction` returns the **wallet's** view (`TransactionInfo`) and is only meaningful for a tx
the node's wallet is involved in — calling it on an arbitrary tx is an anti-pattern (`SKILL.md`).
On the wire, `gettransactiondetails` is issued as the node's verbose `getrawtransaction <hash>
true` form — which is why it works for any tx the node knows, wallet or not.

`getutxo` (the node's `gettxout`) returns `Txout(outpoint, bestblock, confirmations, value,
scriptPubKey)`. Note **`Txout.value` is a lossy `Double`** and `Txout` has no exact `satoshi`
twin — for exact value math fetch the raw tx and parse it, or use `listunspent` (whose `Unspent`
does carry `satoshi`). `Txout.scriptPubKey` (`ScriptPubKey`) exposes `asm`/`hex`/`type` plus
`scriptHash`, `argsHash`, and `addresses` — the same argsHash `nexa-identity-and-addresses` shows
how to read.

## Wallet operations

```kotlin
fun listunspent(): List<Unspent>
fun getbalance(): BigDecimal
fun getwalletinfo(): WalletInfo
fun getnewaddress(addrType: String? = null): String
fun sendtoaddress(addr: String, amt: BigDecimal): HashId
```

```kotlin
@Serializable data class Unspent(
    val outpoint: String, val txid: String, val txidem: String, val vout: Long,
    val address: String, val scriptPubKey: String, val scriptType: String,
    val satoshi: Long,        // <-- use THIS for value math (exact)
    val amount: Double,       // <-- lossy decimal NEXA; do NOT use for math
    val confirmations: Long, val spendable: Boolean)
```

> **Read `Unspent.satoshi` (`Long`), not `Unspent.amount` (`Double`)** for any value math — the
> `Double` is a lossy display value (the "reading lossy `Unspent.amount`" anti-pattern in `SKILL.md`).

## Token issuance RPCs

```kotlin
fun tokenNew(address: String? = null, tokenTicker: String? = null, tokenName: String? = null,
             descUrl: String? = null, descHash: String? = null, decimals: Int? = null): Pair<String, HashId>
fun tokenMint(groupId: String, address: String, quantity: Int): HashId
fun tokenMelt(groupId: String, quantity: Int): HashId
fun tokenSend(groupId: String, address: String, quantity: Int): HashId
fun tokenBalance(groupId: String, address: String? = null): Pair<Long, String>   // (balance_satoshis, decimals)
fun tokenMintage(groupId: String): Pair<Long, String>
fun tokenAuthorityCreate(groupId: String, address: String, authFlags: List<String>): HashId
```

`tokenNew` is the programmatic form of the node-CLI `token new …` genesis recipe in
`nexa-tokens-and-groups` Pattern 7 (it commits the description-document hash via `descHash`).

`tokenBalance(groupId, address?)` returns `(balance in the finest unit, decimals string)`; the
optional `address` narrows the query. **API-evolution note:** releases before a mid-2026 fix had a
copy-paste bug that issued the node's `token melt` verb instead of `token balance` (the call
errored instead of returning a balance) and dropped the `address` argument — if `tokenBalance`
misbehaves on an older artifact, update it.

## Message signing

```kotlin
fun signMessage(address: String, message: String): String
fun verifyMessage(address: String, signature: String, message: String): Boolean
fun signData(address: String, format: String, message: String): String
fun signDataVerbose(address: String, format: String, message: String): SignData
```

## CAPD (node-side)

```kotlin
fun capdList(): List<HashId>
fun capdClear()
fun capdGet(msg: HashId): CapdMsg
fun capdSend(msg: ByteArray): HashId
fun capdInfo(): CapdInfo
```

(For the app-facing CAPD conversation layer, see `nexa-capd-messaging` — these are the node's raw
message-pool RPCs.)

## Regtest

```kotlin
fun generate(qty: Int): List<HashId>           // mine qty blocks (regtest)
fun invalidateblock(hash: HashId)              // roll back to before this block (regtest reorg testing)
```

## Escape hatch (un-wrapped RPCs)

```kotlin
fun calls(rpcName: String, params: List<String>? = null): String         // raw string result
fun callje(rpcName: String, params: List<String>? = null): JsonElement   // parsed JSON result
```

Use these for any node RPC without a typed method above.

## Exception and HashId conventions

```kotlin
open class NexaRpcException(msg: String, val code: Long) : Exception(msg)
```

**Catch `NexaRpcException`, not `IOException`** — every RPC-level failure (bad params, auth, node
error) surfaces as `NexaRpcException` with the node's `code`. Two failure shapes bypass it and
propagate raw: **transport failures** (node unreachable → e.g. `java.net.ConnectException` on JVM)
and **reply-shape drift** (a kotlinx `SerializationException` when the node's JSON reply is missing
a field the client's data class requires — the client sets `ignoreUnknownKeys`, so *new* node
fields are harmless, but a node much older/newer than your artifact can drop or rename one).

```kotlin
class HashId(val hash: ByteArray = ByteArray(32))   // stored internal order; display is reversed
// HashId(hex) reverses on construct; HashId.toHex()/toString() reverses again → "bitcoin standard" hex
```

`HashId.toHex()` already produces the display (reversed) hex — **don't reverse it again** yourself
(the "double-reversing a `HashId.toHex()`" anti-pattern in `SKILL.md`). `HashId.equals` compares
hash bytes by content, but the class does **not** override `hashCode` — don't use a `HashId` as a
`HashMap`/`HashSet` key (equal ids land in different buckets); key by `toHex()` instead
(`List.contains`/`==` are fine).

## Related

- `SKILL.md` — the connection model, anti-patterns, and security (RPC creds = full node control).
- `regtestHarness.kt` — a drop-in regtest fixture (create client, mine starter coins, fund/confirm).
- `nexa-transaction-construction` — building the tx you `sendrawtransaction`.
- `nexa-locktime-cltv` — `getblock(...).mediantime` is the exact MTP source.