# ElectrumClient method reference

The full `org.nexa.libnexakotlin.ElectrumClient` surface — construction, the query/monitor
methods, the result data classes, and the exception set. This is the lookup table behind
`SKILL.md`; read it first for the mental model (light-client queries of *arbitrary* on-chain
state; untrusted-server trust model; no per-address push). Signatures are grounded in
libnexakotlin `electrumclient.kt` and `token.kt`; the declaration there is authoritative if this
drifts.

Every method takes a trailing `timeoutInMs: Int = requestTimeout`, omitted below for brevity.

## Construction and ports

```kotlin
class ElectrumClient(
    val chainSelector: ChainSelector,
    name: String,                                   // server host
    port: Int = DEFAULT_NEXA_SSL_ELECTRUM_PORT,
    logName: String = "$name:$port",
    autostart: Boolean = true,
    useSSL: Boolean = true,
    connectTimeoutMs: Long = JsonRpc.CONNECT_TIMEOUT,
    accessTimeoutMs: Long = JsonRpc.ACCESS_TIMEOUT)
```

Default port constants (pick the one matching `useSSL` and your chain):

| Constant | Value |
| --- | --- |
| `DEFAULT_NEXA_TCP_ELECTRUM_PORT` | 20001 |
| `DEFAULT_NEXA_SSL_ELECTRUM_PORT` | 20002 |
| `DEFAULT_NEXATEST_TCP_ELECTRUM_PORT` | 30001 |
| `DEFAULT_NEXATEST_SSL_ELECTRUM_PORT` | 30002 |
| `DEFAULT_NEXAREG_TCP_ELECTRUM_PORT` | 30403 |

There are also `DefaultElectrumTCP` / `DefaultElectrumSSL` maps keyed by `ChainSelector` (note
regtest has no SSL port — the SSL map holds `-1`). **The port must match the `useSSL` flag** — a
TCP port with `useSSL = true` (or vice versa) is the most common connection failure.

## Transaction lookups

| Method | Returns |
| --- | --- |
| `getTx(txHash: Hash256)` / `getTx(txHash: String)` | `iTransaction` |
| `getTx(txHash: String, timeoutInMs, cb: (iTransaction?, ErrorCodeReply?) -> Unit): Int` | async; result via callback |
| `getTxDetails(txHash: String)` | `JsonElement` (the server's verbose JSON) |
| `getTxAt(height: Int, idx: Int, blockMerkleRoot: Hash256? = null)` | `iTransaction` (tx by block position) |
| `getTxHashAt(height: Int, idx: Int, blockMerkleRoot: Hash256? = null)` | `Hash256` |

## Outpoint / UTXO checks

```kotlin
fun getUtxo(outpointHex: String): GetUtxoResult
fun getUtxo(inp: iTxOutpoint): GetUtxoResult
fun getUtxo(inp: NexaTxInput): GetUtxoResult
```

`GetUtxoResult`:

```kotlin
data class GetUtxoResult(
    val amount: Long, val height: Int, val scripthash: String, val status: String,
    val spent: GetUtxoSpentInfo,                 // nullable sub-fields; see below
    val group: String? = null, val group_quantity: Long? = null)

data class GetUtxoSpentInfo(val height: Int?, val tx_hash: String?, val tx_pos: Int?)
```

`status` is `"unspent"` or `"spent"` — **a spent outpoint returns a normal result**, with `spent`'s
fields populated (the spending tx's hash/position/height); for an unspent outpoint they are null.
A token UTXO carries `group`/`group_quantity`. `ElectrumNotFound` is thrown only when the server
does not know the tx/output at all (the underlying `blockchain.utxo.get` errors only in that case).
The contract-spend-detection primitive is therefore `result.status == "spent"`, not the exception —
see `SKILL.md` Pattern 3.

## Address / script watching

| Method | Returns |
| --- | --- |
| `getHistory(script: SatoshiScript)` / `(scriptHash: Hash256)` / `(scriptHash: String)` | `Array<Pair<Int, Hash256>>` (height, txid) |
| `getHistory(scriptHash: String, timeoutInMs, cb: (Array<Pair<Int,Hash256>>?, ErrorCodeReply?) -> Unit): Int` | async |
| `listUnspent(destination: PayDestination)` | `List<Spendable>` |
| `getBalance(address: PayAddress)` | `BalanceResult` |
| `getFirstUse(script: SatoshiScript)` / `(scriptHash: Hash256)` / `(scriptHash: String)` | `FirstUseResult` |

```kotlin
data class BalanceResult(val confirmed: Int = 0, val unconfirmed: Int = 0)
data class FirstUseResult(val block_hash: String? = null, val block_height: Int? = null, val tx_hash: String? = null)
```

> **`BalanceResult`'s fields are `Int`, not `Long`.** For precise value math, prefer
> `listUnspent(...)` and sum the `Spendable.amount` (`Long`) values rather than relying on the
> summary `BalanceResult` (see `SKILL.md` Security notes).

## Token reads

| Method | Returns |
| --- | --- |
| `getTokenGenesisInfo(tokenType: GroupId)` / `(tokenType: String)` | `TokenGenesisInfo` |
| `getTokenBalance(address: PayAddress)` / `(address: String)` | `TokenGetBalanceResult` |
| `getTokenUnspent(address: String, tokenId: String? = null, cursor: String? = null)` | `TokenListUnspentResult` |
| `getTokenHistory(tokenId: GroupId, cursor: String? = null)` / `(tokenId: String, …)` | `Array<Pair<Int, Hash256>>` |

```kotlin
data class TokenGenesisInfo(
    val document_hash: String?, val document_url: String?, val height: Long,
    val name: String?, val ticker: String?, val token_id_hex: String,
    val txid: String, val txidem: String,
    val decimal_places: Int? = null, val op_return: String? = null) : BCHserializable

data class TokenGetBalanceResult(val confirmed: Map<String, Long>, val unconfirmed: Map<String, Long>, val cursor: String?)
data class TokenListUnspentResult(val unspent: Array<UnspentInfo>, val cursor: String?)
data class UnspentInfo(val group: String, val height: Long, val outpoint_hash: String,
    val token_amount: Long, val token_id_hex: String, val tx_pos: Long, val value: Long, val tx_hash: String)
```

`getTokenGenesisInfo` is the light-client way to fetch the off-chain `decimal_places` / `ticker` /
`name` and the token-description-document URL+hash (`nexa-tokens-and-groups` Pattern 7) — the server
resolves and serves it. Token balances/unspents are **cursor-paged** (`cursor` in/out).

## Block headers and monitoring

The electrum client has **no per-address subscription** — the only push is block headers, so all
address/outpoint monitoring is re-poll-on-new-block:

```kotlin
fun subscribeHeaders(callback: (iBlockHeader) -> Unit)   // fires on each new chain tip
fun unsubscribeHeaders()
fun getTip(): Pair<iBlockHeader, Long>                   // current tip header + height
fun getHeader(height: Int): ByteArray
fun getHeadersFor(cs: ChainSelector, height: Int, count: Int): List<iBlockHeader>
```

Pattern: `subscribeHeaders { … }` to learn when a block arrives, then re-poll the
`getUtxo`/`getHistory`/`listUnspent` of the scripts/outpoints you care about. See
`addressWatcherTemplate.kt` for a drop-in version.

## Broadcast

```kotlin
fun sendTx(serializedTx: ByteArray): String              // returns the txid; throws on rejection
```

## Escape hatch (raw electrum methods)

For any electrum RPC without a typed wrapper (inherited from the `JsonRpc` parent):

```kotlin
fun call(method: String, params: List<Any?>?, timeoutInMs: Int): String?
fun call(method: String, params: List<Any?>?, timeoutInMs: Int, response: (String?) -> Unit): Int  // async
fun subscribe(method: String, params: List<Any?>? = null, response: (String?) -> Unit)
```

## Exceptions

All extend `ElectrumException : NetException`:

| Exception | Meaning |
| --- | --- |
| `ElectrumNotFound(msg)` | the queried tx/outpoint/script isn't known to the server — **a state, not a bug** (a tx/outpoint that never existed; a never-used script from `getFirstUse`). Note a *spent* outpoint does NOT raise this from `getUtxo` — it returns `status == "spent"` (see above) |
| `ElectrumConnectError(node, err)` | couldn't connect (often an SSL/port mismatch) |
| `ElectrumRequestTimeout()` | the request timed out |
| `ElectrumIncorrectReply(what)` / `ElectrumIncorrectRequest(what)` | malformed reply / request |
| `ElectrumShutdown()` | intentional shutdown |

Catch `ElectrumNotFound` specifically where "not found" is an expected answer (a fresh address from
`getFirstUse`; a watch-list entry that never existed); let the others propagate as genuine
connection/protocol errors.

## Related

- `SKILL.md` — the trust model, the headers-only monitoring pattern, and when to use this vs the
  SPV wallet (`nexa-wallet-lifecycle-and-chain`) vs a node you operate (`nexa-rpc-node-client`).
- `addressWatcherTemplate.kt` — a drop-in monitor built on `subscribeHeaders` + re-poll.