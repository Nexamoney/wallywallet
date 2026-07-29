---
name: nexa-wallet-lifecycle-and-chain
description: "Creates, restores, opens, encrypts, backs up, and connects a Nexa wallet via libnexakotlin, the wallet/chain bootstrap an app runs at startup before showing a balance, deriving an address, or building a transaction. Use when creating or recovering a wallet, opening the wallet file on launch, managing seed words, encrypting/locking the wallet, getting balance or a receive address, connecting to the network or a full node, or fixing an always-zero balance. Triggers: Bip44Wallet, CommonWallet, newWallet/recoverWallet/openWallet, WalletDatabase, generateBip39SecretWords, balance, syncedHeight, connectBlockchain, ElectrumClient, initializeLibNexa, WallyEnterpriseWallet, signMessage/signData (sign with a wallet key), pause/resume, rediscover. Not for building/signing/broadcasting txs (nexa-transaction-construction) or address-type choice (nexa-identity-and-addresses)."
---

# Nexa wallet lifecycle and chain connection (libnexakotlin)

## When to use this skill

Trigger when a developer needs to **create, restore, open, encrypt, back up, or connect a Nexa
wallet** — i.e. the libnexakotlin wallet/chain bootstrap that almost every Nexa application runs at
startup, before it can show a balance, derive an address, or build a transaction. Concretely
trigger on:

- Keywords: `Bip44Wallet`, `CommonWallet`, `newWallet`, `recoverWallet`, `openWallet`,
  `openDisconnectedWallet`, `openCreateDisconnectedWallet`, `openWalletDB`, `WalletDatabase`,
  `WalletStartup` / `NEW_WALLET`, recovery phrase / seed words / mnemonic, `recoverySecret`,
  `generateBip39SecretWords`, `wallet.encrypt`, `unlock` / `lock` / `lockedState`, `getNewAddress`,
  `destinationFor`, `COMMON_IDENTITY_SEED`, `balance` / `balanceConfirmed` / `balanceUnconfirmed`,
  `syncedHeight` / `synced(...)`, `blockchainFor`, `connectBlockchain`, `GetBlockchain`,
  `addBlockchain`, `Blockchain`, `getTip`, `curHeight`, `CnxnMgr` / `net`, `exclusiveNodes`,
  `ElectrumClient`, SPV, `initializeLibNexa`, `SetLogFile`, `WallyEnterpriseWallet`, `wew`,
  `org.wallywallet:wew`, named signing wallets, multi-account wallet, wallet daemon,
  enterprise wallet, "Bip44Wallet vs WallyEnterpriseWallet", `signMessage` / `signData` /
  `signHash` / `verifySigForData` / `verifyMessage` (sign or verify a message with a wallet key),
  `pause` / `resume` (background the wallet), `rediscover` (full rescan), `cleanReserved` /
  `cleanUnconfirmed`, `statistics()`, `fastForward` / fast sync (catch a far-behind wallet up
  without a block scan), `getCurrentDestination` (current receive address without rotating),
  `sync(maxWait)` / `chainstate` (blocking startup sync gate), `forEachUtxo` / `filterInputs` /
  `Spendable.reserved` (enumerate spendable UTXOs), `GetCnxnMgr` / `GetBlockchain` with
  `start = false` (pin nodes before connecting), `getElectrum` / `returnElectrum` (the pooled
  electrum channel), `getElectrumServerCandidate`, `deleteWalletFile` (corrupt-file recovery),
  `p2pCnxns` ("am I connected?" — gate sends on peer connectivity, chain-status indicator),
  `dataDirectory` (relocate wallet/data files on the JVM).
- Tasks: "create a new Nexa wallet", "restore a wallet from a recovery phrase", "open the wallet
  file at startup", "back up the seed words", "encrypt the wallet with a passphrase", "get the
  wallet balance / a receive address", "connect to the Nexa network / my own full node", "is the
  wallet synced yet", "the wallet opens but the balance is always zero".
- Files: a server's `main()` / `Application.kt` startup block; any client model code that opens a
  wallet on launch.

**Negative triggers** — do NOT use this skill for:
- Building/signing/broadcasting a transaction from an open wallet — use
  `nexa-transaction-construction` (`send`, `txCompleter`, `setOnWalletChange`).
- The P2PKH-identity vs rotating-P2PKT-payout *address* distinction once you have a wallet — use
  `nexa-identity-and-addresses`.
- The Wally **external** wallet over the TDPP/nexid protocol (a separate device signing for your
  app) — use `nexa-wallet-connection`. This skill is about a wallet your code **holds the keys to**.
- Talking to a full node over JSON-RPC — use `nexa-rpc-node-client`. That is a different channel from
  the SPV/electrum connection this skill's wallet uses.
- Server-side management of *several named* signing wallets — that `org.wallywallet:wew` pattern is
  in `nexa-ktor-server-integration`; it sits on top of the single-wallet lifecycle here.

## Mental model

A Nexa wallet in libnexakotlin is a **`Bip44Wallet`** (a `CommonWallet`): an HD, BIP44-derived
key set, backed by a per-wallet on-disk database, that watches a `Blockchain` for its UTXOs. Three
things have to come together before a wallet is usable:

1. **The library is initialized.** `initializeLibNexa()` (and a log-file destination via
   `SetLogFile`) must run once at process start, before any wallet or chain object is created.
2. **A chain connection exists.** A `Blockchain` object tracks headers and relays transactions
   over a **connection manager** (`net`, a `CnxnMgr`) — by default an **SPV/electrum** connection
   to public seeder nodes (or to a node you pin). This is *not* the JSON-RPC path in
   `nexa-rpc-node-client`; it is libnexakotlin's own light-client network layer.
3. **A wallet is attached to that chain.** The wallet derives keys, scans the chain for outputs it
   can spend, and exposes `balance` / `getNewAddress()` / `send(...)`.

The high-level `init.kt` helpers do all three for you in one call — you rarely construct
`Blockchain` or `Bip44Wallet` directly. The whole bootstrap is **libnexakotlin** (multiplatform);
none of it requires libnexaapp. (libnexaapp's `initBlockchain(...)` is a thin server-side wrapper
that records the same chain into its own globals — see `nexa-ktor-server-integration`.)

**The single most important distinction is the three ways a wallet comes into being**, because
they differ in *whether the chain is scanned for history*:

| Helper | Secret | Chain history scan | Use for |
| --- | --- | --- | --- |
| `newWallet(name, cs)` | freshly generated BIP39 phrase | none (`addBlockchain(bc, -1, -1)`) — brand-new keys can't have old tx | a first-run wallet |
| `recoverWallet(name, phrase, cs)` | the recovery phrase you pass | full (scans from the chain checkpoint) | restoring an existing wallet's funds |
| `openWallet(name)` | read from the existing wallet file | resumes from where the file left off | every subsequent launch |

Getting this wrong is the classic "restored wallet shows zero balance" bug: opening a recovered
key set with `newWallet`/`openWallet` semantics skips the historical scan, so old UTXOs never
appear. **A recovery must go through `recoverWallet`.**

A wallet is **chain-bound**: its keys, addresses, and the `ChainSelector` are baked into the file.
Don't open a testnet wallet against mainnet (or vice versa); keep one file per chain.

### Which chain do I develop on? Default to testnet

Every wallet — and everything built on it — runs on one of three chains, chosen by its
`ChainSelector`. **While developing, default to testnet (`ChainSelector.NEXATESTNET`).** Reach for
regtest only for the specific capability it adds; use mainnet only in production.

| Chain | `ChainSelector` | What it is | Develop here when |
| --- | --- | --- | --- |
| **Testnet** | `NEXATESTNET` | a shared, public chain with real block timing; coins are free (from a faucet) and worthless | **the default** — the closest safe analog to production, no real funds at risk |
| **Regtest** | `NEXAREGTEST` | a private, single-node chain **you** run and fully control | you need to **control block production** — force-mine blocks on demand, get instant/deterministic confirmations, test reorgs (`invalidateblock`), or drive a time-/confirmation-gated path that can't wait for real blocks (see `nexa-rpc-node-client`) |
| **Mainnet** | `NEXA` | production; real, irreversible funds | releasing — not during development |

The one question that decides testnet vs regtest: **does the app need to force-mine blocks (or
otherwise control the chain)?** If no, stay on testnet. If yes — deterministic confirmations,
reorg tests, sub-minute contract-timeout tests — use regtest.

In code the choice is normally a single **`DEFAULT_CHAIN` constant** (see
`nexa-ktor-server-integration`), so flipping chains looks like a one-line edit. But the chain is
more than a constant — switching it means **three coordinated changes**:

1. **`DEFAULT_CHAIN`.** Route every `ChainSelector` through this one constant (never hardcode
   `ChainSelector.NEXA`/`NEXATESTNET` at a call site) so this really is one edit.
2. **The local node must be on the same chain.** A node running testnet cannot serve regtest, and
   vice versa. When you switch the app's chain you must also switch the node it connects to — restart
   your local node in the matching mode (its `nexa.conf` chain) and update the RPC/electrum
   host/port/credentials if they differ per chain. If you instead rely on public SPV seeders, those
   are chain-specific too, so the same rule applies: the network you connect to must match
   `DEFAULT_CHAIN`.
3. **A chain-matched wallet file.** The file embeds its `ChainSelector`; keep one per chain
   (`myTestnetWallet`, `myRegtestWallet`, `myMainnetWallet`) — see the anti-pattern below.

Finally, there are **two layers** at which you can operate wallets: the multiplatform `Bip44Wallet`
**primitive** documented here, and the JVM `WallyEnterpriseWallet` (`org.wallywallet:wew`)
**management runtime** that orchestrates a registry of *named* `Bip44Wallet` accounts on a server.
The latter is built *on top of* the former — Pattern 8 covers which to choose for which scenario.

## Setup and versions

You need `libnexakotlin` (all the types here) and a platform SQL driver for its wallet database
(wired in per target). Pin per `nexa-project-setup`. The wallet/chain layer is multiplatform, so
this works on JVM, Android, iOS, and WASM clients as well as servers.

Imports:

```kotlin
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.newWallet
import org.nexa.libnexakotlin.recoverWallet
import org.nexa.libnexakotlin.openWallet
import org.nexa.libnexakotlin.openDisconnectedWallet
import org.nexa.libnexakotlin.blockchainFor
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.SetLogFile
```

`initializeLibNexa(variant: String? = "")` is an `expect` function with a per-platform
implementation; call it (and `SetLogFile(path)` — to a path **outside** a Compose/Wasm dev-watched
project dir, see `nexa-ktor-server-integration`) once before creating any wallet.

## Core patterns

### Pattern 1: First run — create a brand-new wallet

`newWallet` generates a fresh BIP39 recovery phrase, persists the wallet to its own database file,
and connects it to the chain (auto-creating the chain connection if one isn't open yet):

```kotlin
initializeLibNexa()
SetLogFile(logPathOutsideProjectDir())

// name → display name, log name, and the wallet's filename. cs → which chain.
val wallet: Bip44Wallet = newWallet("myTestnetWallet", ChainSelector.NEXATESTNET)

// Show the recovery phrase ONCE so the user can back it up (see Security considerations):
val phrase: String = wallet.recoverySecret      // space-separated BIP39 words
```

`newWallet(name, bc: Blockchain)` is the overload to use when you already built the `Blockchain`
yourself (Pattern 5) and want the wallet attached to that specific instance.

### Pattern 2: Restore a wallet from a recovery phrase

The phrase is the only input. `recoverWallet` attaches the wallet so it **scans the chain from the
checkpoint for historical transactions** — this is what makes prior funds reappear:

```kotlin
val restored = recoverWallet("myTestnetWallet", userEnteredPhrase.trim(), ChainSelector.NEXATESTNET)
// Funds populate as the SPV scan catches up — gate UI on `synced(...)` (Pattern 4), not on the
// instant return of this call.
```

Validate the phrase before calling (correct word count, single-space separated); the helper assumes
a well-formed recovery phrase. (If you need to construct from words at a lower level, the
`Bip44Wallet(wdb, name, cs, secretWordList, maxAddr)` constructor underlies this helper, and
`generateBip39SecretWords(generateEntropy(...))` is what `newWallet` uses to make a fresh one.)

### Pattern 3: Subsequent launches — open the existing wallet

```kotlin
val wallet = try {
    openWallet("myTestnetWallet")          // chain is read from the wallet file; auto-connects
} catch (e: org.nexa.libnexakotlin.DataMissingException) {
    newWallet("myTestnetWallet", ChainSelector.NEXATESTNET)   // first run
}
```

`openWallet` reads the wallet's own `ChainSelector` from the file, so you don't pass one. The
open-or-create idiom above is the normal startup shape (it mirrors what a server's `main()` does).

Two adjacent facts: after opening, a previously-`pause()`d wallet needs `wallet.resume()` before it
processes blocks again (Pattern 7c). And for a **corrupt** wallet file (`openWallet` throws
something other than not-found), `deleteWalletFile(walletName, underlyingFileName, chainSelector)`
removes the damaged database so a fresh `newWallet`/`recoverWallet` can take its place — this is
**destructive**: only appropriate when the recovery phrase is safely backed up (restore via
`recoverWallet`) or the wallet's funds are expendable. A server that auto-recreates its wallet this
way should stop and surface the new wallet's receive address rather than continuing as if funded.

### Pattern 4: Receive address, identity, balance, and sync state

Once open, a wallet exposes the basics directly:

```kotlin
val receiveAddr = wallet.getNewAddress()          // a fresh P2PKT receive address — ROTATES per call
// (getnewaddress() is a documented alias — kept for classic bitcoin-RPC capitalization compatibility)

// The CURRENT receive destination WITHOUT rotating — re-reading it returns the same address
// until it gets used. Use for "show my receive address again" so redisplays don't burn
// addresses; use getNewAddress() when you deliberately want a fresh one:
val current = wallet.getCurrentDestination().address

// The wallet's STABLE identity destination (does not rotate) — same one used for server signing
// identity in nexa-ktor-server-integration:
val identityAddr = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address

val total       = wallet.balance              // Long, satoshi
val confirmed   = wallet.balanceConfirmed     // only mined funds
val unconfirmed = wallet.balanceUnconfirmed   // 0-conf in mempool

// Sync progress — an SPV wallet is not usable for spends until it has caught up:
val atHeight = wallet.syncedHeight
val ready    = wallet.synced()                // synced(-1) ≈ "synced to ~now"; pass a height/epoch-ms to test a point

// Blocking form for a startup gate: polls synced() every 500 ms until true or maxWait ms elapse.
// Loop it with a progress line so a long catch-up is visible in the logs:
while (!wallet.sync(2000)) {
    val chst = (wallet as CommonWallet).chainstate   // syncedDate (epoch s), syncedHeight, syncedHash — null if disconnected
    log("syncing: at ${chst?.syncedHeight} (${chst?.syncedDate}), chain at ${wallet.blockchain.curHeight}")
}
```

`getNewAddress()` returning a *rotating* P2PKT address and `destinationFor(COMMON_IDENTITY_SEED)`
returning a *stable* identity is exactly the payout-vs-identity split documented in
`nexa-identity-and-addresses` — applied here to a wallet your code controls.

To react to incoming funds / confirmations, hook `wallet.setOnWalletChange { w, txs -> … }` — its
`TransactionHistory` shape and the 0-conf/finality rules live in `nexa-transaction-construction`
Pattern 4. (`balance` is cached; flush with `(w as CommonWallet).clearCachedBalances()` in that
callback, as that skill shows.)

#### Purge stale wallet state at boot (`cleanReserved` + `cleanUnconfirmed`)

A long-lived wallet (a dev/server wallet reused across many runs) accumulates cruft that wedges
funding. Two purges, run at server boot (and in IT self-heal), keep it spending only good UTXOs —
the shared rationale is "nothing is genuinely in-flight at boot, so anything reserved/unconfirmed is
stale":

```kotlin
wallet.cleanReserved()      // release UTXOs reserved by a build that never broadcast
wallet.cleanUnconfirmed()   // drop stale 0-conf txs the node never accepted
```

- **`cleanReserved()`** frees inputs an interrupted `txCompleter`/`send` marked reserved but never
  spent, so they don't look permanently unavailable.
- **`cleanUnconfirmed()`** is the fix for a specific, high-severity wedge: on testnet (infrequent
  blocks; tests that don't wait for confirmation) the wallet builds up UNCONFIRMED txs the node
  never accepted, then funds new txs from **phantom 0-conf change** whose parents the node lacks. The
  node then defers **every** relay — even a genesis spending confirmed coins — with **reject code 73
  "please wait for wallet sync"**, and nothing reaches the mempool. It looks like a node outage but
  the node is healthy (confirm via RPC `getblockchaininfo`: `blocks == headers`,
  `initialblockdownload == false`). Purging the stale 0-conf set restores funding from confirmed
  UTXOs only. This WALLET-STATE wedge (affects *all* txs) is distinct from the single covenant-spend
  transient code-73 in `nexa-transaction-construction`/`nexa-rpc-node-client` (cleared by broadcasting
  that one spend via RPC, not by purging). See `nexa-debugging-onchain-errors` for the code-73 split.

Two related tools worth knowing alongside the purges: **`rediscover(forgetAddresses = false,
noPrehistory = false, forgetHistory = false)`** is the dev/debug "full rescan" — it forgets the
wallet's transaction and blockchain state and asynchronously redoes the search for wallet txs (the
doc-comment on `cleanUnconfirmed` notes that anything it forgot which really is on-chain comes back
via rediscover or on confirmation). And **`wallet.getTx(txIdem): TransactionHistory?`** looks up a
single wallet-history entry by transaction idem — handy for re-checking one tx's `confirmedHeight`
on demand instead of only reacting to `setOnWalletChange` callbacks.

#### Enumerating spendable UTXOs (`forEachUtxo` / `filterInputs`, and `reserved`)

Beyond the balance summaries, a wallet exposes its spendable set directly — the basis for
inventory checks like "how many usable mint-authority UTXOs do I hold" (`nexa-tokens-and-groups`
Pattern 9):

```kotlin
// Visit every UTXO; return true from the lambda to stop early:
wallet.forEachUtxo { sp: Spendable ->
    val gi = sp.groupInfo()                    // GroupInfo? — token/authority data, null if ungrouped
    val usable = sp.reserved == 0L             // reserved != 0 ⇒ claimed by an in-flight txCompleter build
    /* count/inspect */ ; false
}

// Or collect: filterInputs(minAmt, minConfirms = 0, filter) returns MutableList<Spendable>.
// It iterates until the filter's summed returns reach minAmt; the filter maps each candidate to
// the amount to count it as (return 0 to exclude). With NO filter only ungrouped, uncontracted
// coins are offered and satoshis are counted; WITH a filter you see everything (incl. token and
// authority UTXOs) and must exclude what you don't want:
val spendables = wallet.filterInputs(Long.MAX_VALUE) { 1 }   // "all spendables, counted as 1 each"
```

The `Spendable.reserved` field matters when counting by hand: UTXOs reserved by a
not-yet-broadcast `txCompleter` build are unavailable to new builds (that's what boot-time
`cleanReserved()` clears), so a `forEachUtxo` availability count should skip `reserved != 0L`
entries. (`filterInputs` applies the unspent/unreserved/confirmation checks itself before calling
your filter.)

#### Fast-forward sync: `Bip44Wallet.fastForward` (skip the block scan)

When a wallet has been offline long enough that SPV catch-up is painful (days of blocks), there
is a second sync strategy: **`fastForward(displayFastForwardInfo: (String?) -> Unit):
Objectify<Boolean>`** (libnexakotlin's `fastforward.kt`). Instead of scanning blocks, it asks a
Rostrum/electrum server for the history of the wallet's derivation paths (receive, change, and
identity paths) and **injects the discovered transactions directly**, jumping `syncedHeight` to
the tip:

```kotlin
val aborter = wallet.fastForward { progress ->
    // progress strings: "start", running counts, then "finished"
    syncStatusFlow.value = progress
}
// to cancel a fast-forward in flight:
aborter.obj = true
```

Trade-offs and usage notes:

- **Trust:** the injected history comes from the electrum server rather than SPV-verified block
  scanning — the same untrusted-server caveat as `nexa-electrum-monitoring`. Fine for a wallet
  UI catching up; corroborate independently before treating large sums as settled.
- **When to offer it:** the Wally wallet's heuristic is "synced date more than ~a day behind
  now" — recent releases both show a manual fast-sync affordance and trigger it automatically
  when eligible. A wallet freshly restored *with a full scan in progress* is better left to
  `recoverWallet`'s scan unless the user opts in.
- The returned `Objectify<Boolean>` is the abort handle (set it `true` to stop); a non-null
  in-flight handle is also your "fast-forward is running" signal.

### Pattern 5: Connect the chain explicitly (and pin to your own node)

`newWallet`/`openWallet`/`recoverWallet` auto-connect the chain. When you want control over the
connection — most commonly to point the SPV link at **your own trusted full node** instead of
public seeders — build the `Blockchain` first with `blockchainFor` and pass it to the wallet
helper:

```kotlin
val bc = blockchainFor(ChainSelector.NEXATESTNET) {
    // `this` is the Blockchain; `net` is its connection manager (CnxnMgr):
    net.exclusiveNodes(setOf("127.0.0.1:<your node's electrum port>"))   // only talk to this node
}
val wallet = newWallet("myTestnetWallet", bc)      // or recoverWallet(name, phrase, bc)

val tip       = bc.getTip()        // iBlockHeader? — .time (block timestamp), .height
val curHeight = bc.curHeight       // Long — current best-known height
```

`blockchainFor(cs)` (a synonym for `connectBlockchain`) is **idempotent per chain**: it reuses an
already-connected `Blockchain` for that `ChainSelector`, runs your initializer, and ensures the
connection is started. `net.exclusiveNodes(null)` clears the pin and returns to public seeders;
`net.stop()` tears the connection down. `bc.getTip()?.time` is the wallet-side estimate of chain
time used by the locktime pre-flight check in `nexa-locktime-cltv`. (The default public
electrum/seeder lists the connection falls back to live in libnexakotlin's `init.kt` —
`nexaElectrum` / `nexaTestnetElectrum`, atomic lists of `IpPort` an app can inspect or replace if
it wants different defaults than `exclusiveNodes` pinning.)

**Alternative pattern, preferred when the pin must be in place before ANY connection is attempted:**
build the pieces unstarted, configure, then start. `blockchainFor`'s initializer runs on an
already-starting connection, so a server that must never contact public seeders (it depends on one
trusted node — e.g. for the tx-validation service in `nexa-transaction-construction` Pattern 6b)
uses the two lower-level factories with `start = false`:

```kotlin
val cm = GetCnxnMgr(DEFAULT_CHAIN, start = false)          // connection manager, not yet connecting
val bc = GetBlockchain(DEFAULT_CHAIN, cm, start = false)   // chain object on that manager
cm.exclusiveNodes(setOf(trustedNodeHostPort))              // pin BEFORE anything dials out
cm.start(); bc.start()
```

Two more connection-manager knobs worth knowing on this path:

- **The electrum-channel pool.** `net.getElectrum()` checks an `ElectrumClient` out of the
  connection manager's pool and `net.returnElectrum(ec)` returns it — the same channel the
  `{ bc.net.getElectrum() }` supplier idiom hands to token-metadata lookups. For a long-running
  query loop, recycle on failure: on `ElectrumRequestTimeout`, `ec.close(reason)`, return it, and
  check out a fresh one rather than hammering the dead connection.
- **Overriding electrum server selection.** A `MultiNodeCnxnMgr` exposes
  `getElectrumServerCandidate: ((ChainSelector, exclusiveNodes, preferredNodes) -> IpPort)?` — set
  it to force which electrum endpoint the pool connects to (e.g. your pinned full node's electrum
  port, when `exclusiveNodes` alone pins only the P2P side):

  ```kotlin
  (bc.net as MultiNodeCnxnMgr).getElectrumServerCandidate = { chain, _, _ ->
      IpPort(trustedNodeHost, DefaultElectrumTCP[chain] ?: DEFAULT_NEXA_TCP_ELECTRUM_PORT)
  }
  ```

#### Connectivity checks and a live chain-status indicator (`net.p2pCnxns`)

The connection manager exposes its live P2P connections as `net.p2pCnxns: List<P2pClient>`. Two
uses every server-side wallet benefits from:

- **Gate sends on connectivity.** A `wallet.send(...)` attempted while the wallet has no P2P peer
  can't relay; checking first turns a confusing stall into a clear error:

  ```kotlin
  if (wallet.blockchain.net.p2pCnxns.isEmpty())
      return respond("error: not connected to the blockchain")
  val tx = wallet.send(amount payTo addr, minConfirms = 0)
  ```

- **Publish a chain-status indicator.** A small poll loop into a `@Serializable` status flow gives
  the UI a live "connected / chain / tip height" badge (`MutableStateFlow` skips equal values, so
  the poll only emits over the wire when something actually changes — see
  `nexa-server-state-and-flows`):

  ```kotlin
  CoroutineScope(Dispatchers.Default).launch {
      while (true) {
          chainStatus.value = ChainStatus(
              connected = bc.net.p2pCnxns.isNotEmpty(),
              chain = DEFAULT_CHAIN.uriScheme,           // or your own display name mapping
              height = bc.curHeight)
          delay(5000)
      }
  }
  ```

Each `P2pClient` in the list also carries per-peer diagnostics (`logName`, `aveLatency`,
`bytesSent`/`bytesReceived`), useful for an admin/debug view of who the wallet is actually
talking to.

#### Relocating wallet/data files: the JVM `dataDirectory` global

On the JVM, libnexakotlin's file access (wallet databases, the log file, other library files)
resolves **relative to the process working directory** by default. The top-level
`var dataDirectory: String?` (JVM-only, in libnexakotlin) changes that: when set, **all file
opens are prefixed with it**, acting as the library's "current directory". Set it early — before
opening/creating any wallet — and note it is a plain string prefix, so **it must end with `/`**
(`"/opt/data/myapp/"`, not `"/opt/data/myapp"`, or the prefix concatenates into the filename).
This is the clean way for a deployed service to keep its wallet files in a data directory
instead of wherever the process happens to start.

### Pattern 6: Encrypt the wallet and lock/unlock it

A wallet starts unencrypted. `encrypt(passphrase)` switches it to encrypted-at-rest (or rekeys an
already-encrypted one); after that the private keys and recovery phrase are only available while
unlocked:

```kotlin
wallet.encrypt(userPassphrase)        // encrypt at rest (re-saves the wallet)

wallet.lock()                         // forget the decrypted secret
val ok: Boolean? = wallet.unlock(userPassphrase)   // true on success; wallet usable again
val locked: Boolean? = wallet.lockedState()        // true = locked, false = unlocked, null = not encrypted

// recoverySecret THROWS (WalletLockedException) while encrypted+locked — unlock first to show it.
```

### Pattern 7: A disconnected (offline / trusted-provider) wallet

For signing messages, deriving addresses, or apps that get their chain data from a trusted provider
rather than running SPV, open a wallet that is **not** attached to a live chain. You still pass the
chain so addresses derive in the right space:

```kotlin
val signer = openDisconnectedWallet("signingWallet")                 // open existing, no chain connection
// or, open-or-create:
val signer2 = openCreateDisconnectedWallet("signingWallet", ChainSelector.NEXA)
```

A disconnected wallet has no `balance`/sync and cannot broadcast on its own, but it can derive
addresses and sign — useful for a server identity key or an air-gapped flow.

### Pattern 7b: Sign and verify messages/data with the wallet

A wallet signs two distinct kinds of things, and the two APIs are **not interchangeable**:

```kotlin
// 1. Human-readable message signing (Bitcoin-style "Signed Message" scheme).
//    This is the scheme nexid login signatures and token-description-document signatures use.
//    NOT usable inside contracts.
val sigB64: String = wallet.signMessage("I authorize X", addr = null)   // null addr ⇒ the COMMON identity
val ok = Wallet.verifyMessage("I authorize X", PayAddress(addrStr), sigB64)   // static; also (ByteArray, …) forms

// 2. Blockchain-compatible DATA signing (SHA256 of the message, then Schnorr).
//    This produces exactly the signature an NPL contract's checkDataSigVerify(sig, msg, pubkey)
//    verifies — the oracle / pre-authorization primitive.
val dataSig: ByteArray = wallet.signData(messageBytes, addr = null)     // or signHash(hash) if pre-hashed
val valid = wallet.verifySigForData(messageBytes, dataSig, addr)        // wallet-side check
// To verify a NON-wallet signature against a raw pubkey (e.g. server-side against an oracle key):
//   libnexa.verifySignedHashSchnorr(hash32, pubkey, sig64) / verifySignedDataSchnorr(...)
```

The library's own doc-comment states the split outright: *"This signed message is not usable
inside contracts (instead use signData). It is for traditional human-readable message signing by a
coin holder."* So: **`signMessage`** for login/identity/document signatures (the
`nexa-wallet-connection` nexid flow and the TDD signature in `nexa-tokens-and-groups`);
**`signData`/`signHash`** to produce what `checkDataSigVerify` in an NPL rule consumes
(`nexa-npl-smart-contracts`). All of these sign with a specific address's key (`addr`), defaulting
to the wallet's common identity when `addr = null`, and throw `WalletAddressMissingException` /
`WalletIncompatibleAddress` if the wallet doesn't hold that key.

### Pattern 7c: Pause and resume wallet processing

`CommonWallet.pause(maxWait: Long = 30000): Boolean` stops the wallet's blockchain-processing
thread (returning true once it has actually paused), and `resume()` restarts it. Use them when the
app goes to the background (mobile), before bulk local surgery on wallet state, or any time you
need the wallet quiescent without tearing down the chain connection (`rediscover` uses the same
mechanism internally). A recent release wired an abort signal through the wallet's blocking network
operations, so `pause()` now interrupts an in-flight sync promptly instead of waiting for the
current network call to finish — pausing on app-background is cheap. Remember to `resume()`;
a paused wallet processes no incoming blocks/tx (balances and `setOnWalletChange` go quiet).

### Pattern 8: Choosing your wallet layer — raw `Bip44Wallet` vs `WallyEnterpriseWallet`

There are **two ways to operate wallets in the Nexa stack**, and they are *layered*, not
alternatives:

- **The `Bip44Wallet` primitive (libnexakotlin)** — everything in Patterns 1–7. You construct/open a
  wallet object in **your** process and drive it programmatically. It is **multiplatform** (JVM,
  Android, iOS, Wasm) and has no opinion about how your app is structured.
- **`WallyEnterpriseWallet` (`org.wallywallet:wew`, GitLab Maven project `15615113`)** — a **JVM
  wallet-management runtime** that holds a registry of *named* accounts (`accounts: Map<String,
  Wallet>`, each entry a libnexakotlin `Bip44Wallet`) plus a registry of named full-node RPC
  connections (`nodes: Map<String, NexaRpc>`), and runs a scriptable command engine behind a choice
  of front-ends.

The key relationship: **WEW is not a different *kind* of wallet — its accounts *are*
`Bip44Wallet`s.** Choosing WEW does not change the cryptography, the recovery phrase, the encryption,
or the identity model; everything in this skill still applies underneath. You are choosing whether to
hand the *operational layer* (multi-account orchestration, lifecycle, a CLI/daemon, live scripting)
to WEW or to build it yourself around the primitive.

`WallyEnterpriseWallet.run(shell, walName?, cs?)` selects the front-end via the `CliType` enum:
`Console` (interactive REPL), `Graphical` (GUI), `Fifo` (a named-pipe `in`/`out` command channel you
drive from another process — a controllable wallet **daemon**), or `None` (embedded/headless). It
also owns blockchain connections and stops/saves every account on shutdown.

**Prefer the raw `Bip44Wallet` primitive when:**

- You are building a **client / UI app** — Compose Multiplatform, Android, iOS, or a Wasm frontend.
  WEW is a JVM server/desktop runtime; the wallet primitive is the multiplatform piece.
- Your application **owns the process and orchestration** and the wallet is one embedded component
  among many you already manage.
- You need **one, or a few hand-managed, wallets** with bespoke lifecycle/UX and minimal extra
  dependencies. The `init.kt` helpers (`newWallet`/`openWallet`/`recoverWallet`) are all you need.

**Prefer `WallyEnterpriseWallet` when:**

- A **server / back-office operates several long-lived signing wallets** under distinct roles
  (e.g. an oracle key, a facilitator key, a fee/treasury key) and you want a ready-made named-account
  registry with start/stop/save-all lifecycle instead of hand-rolling a `Map<String, Bip44Wallet>`.
- You want a **controllable wallet service/daemon**: the `Fifo` front-end exposes a command pipe for
  another process to drive; `Console` gives an operator REPL.
- You want a **live scripting console** to operate wallets — issue tokens, build/spend contracts,
  inspect state — without recompiling, and/or WEW's plugin surface.
- You are managing **wallets and named node-RPC connections together** as one operational unit.

| Scenario | Prefer |
| --- | --- |
| KMP / Compose / mobile / Wasm client wallet | raw `Bip44Wallet` (WEW is JVM server/desktop) |
| App server where the wallet is one component you orchestrate | raw `Bip44Wallet` via the `init.kt` helpers |
| A single signing wallet on a server | raw `openWallet`/`newWallet` (WEW runtime is overhead here) |
| Several named signing roles operated as a service | `WallyEnterpriseWallet` |
| A controllable wallet daemon (FIFO) or operator console/scripting | `WallyEnterpriseWallet` |
| Wallets **and** named full-node RPC connections managed together | `WallyEnterpriseWallet` |

The concrete server wiring for the WEW path (`WallyEnterpriseWallet.run(CliType.Fifo)` on a thread,
`accounts[name] = openWallet(name)`, `destinationFor(COMMON_IDENTITY_SEED)` for a stable signing
identity) is in `nexa-ktor-server-integration` § "Server-side signing wallets." Because WEW accounts are
`Bip44Wallet`s, you can also seed a WEW account from any wallet you created with Patterns 1–3 here.

## Common mistakes and anti-patterns

### Restoring a wallet with `newWallet`/`openWallet` instead of `recoverWallet`

**Wrong**:
```kotlin
// User pasted their recovery phrase, but we created a fresh wallet and ignored it:
val w = newWallet("restored", ChainSelector.NEXA)   // brand-new keys; user's funds are NOT here
```
*Even if you later inject the phrase, `newWallet` attaches the chain with **no historical scan**
(`addBlockchain(bc, -1, -1)`), so pre-existing UTXOs never load — the wallet looks empty.*

**Right**: route a recovery through `recoverWallet`, which scans from the checkpoint:
```kotlin
val w = recoverWallet("restored", userPhrase.trim(), ChainSelector.NEXA)   // scans for old tx
```

### Treating the helper's return as "synced and ready"

**Wrong**:
```kotlin
val w = openWallet("myWallet")
showSpendableBalance(w.balance)      // balance is still 0 — SPV hasn't caught up
```
*Opening/creating a wallet returns immediately; the SPV scan runs in the background. `balance` and
the UTXO set fill in over time.*

**Right**: gate spend UI / "ready" state on `w.synced()` (and refresh on `setOnWalletChange`):
```kotlin
if (w.synced()) enableSpending() else showSyncingIndicator(w.syncedHeight, w.blockchain.curHeight)
```

### Reusing one wallet file across chains

**Wrong**: opening `"myWallet"` against both `NEXA` and `NEXATESTNET`. *The file embeds its
`ChainSelector`; addresses and derivations are chain-specific.* **Right**: one file per chain
(`myMainnetWallet`, `myTestnetWallet`, `myRegtestWallet`) — the same rule
`nexa-ktor-server-integration` states for the server wallet.

### Calling wallet/chain APIs before `initializeLibNexa()`

**Wrong**: constructing a wallet or calling `blockchainFor` at top-level before the library is
initialized. *Platform primitives (crypto, DB driver, native hashing) aren't wired yet.* **Right**:
`initializeLibNexa()` (and `SetLogFile`) are the **first** lines of your startup, before any wallet
or chain object exists.

### Logging or hard-coding the recovery phrase

**Wrong**: `LogIt.info("seed: ${wallet.recoverySecret}")`, or persisting the phrase in plaintext
config. *The recovery phrase is the wallet — anyone who reads it controls all funds, on any
device.* **Right**: surface `recoverySecret` only to the user, once, for backup; never log it,
never transmit it, and `encrypt(...)` the wallet so it isn't on disk in the clear.

### Assuming the SPV `net` connection is the JSON-RPC node connection

**Wrong**: expecting `wallet.blockchain.net` to be the same thing as a `NexaRpc` client, or that
pinning `exclusiveNodes` gives you RPC methods. *The wallet's `net` is libnexakotlin's SPV/electrum
light-client transport; `org.nexa:nexarpc` is a separate, authenticated JSON-RPC channel to a node
you own.* **Right**: use SPV (`exclusiveNodes` points it at an electrum endpoint) for the wallet,
and `nexa-rpc-node-client` when you specifically need node RPCs.

### Reading `balance` in a hot loop expecting it to be live

`balance` is cached and updated on wallet changes, not recomputed per read. Drive UI off
`setOnWalletChange` (flushing with `clearCachedBalances()`), not by polling `balance` on a timer —
see `nexa-transaction-construction` Pattern 4.

### Reaching for `WallyEnterpriseWallet` in a client app, or treating it as a different wallet type

**Wrong**: pulling `org.wallywallet:wew` into a Compose Multiplatform / mobile / Wasm client to "get
a wallet," or assuming WEW replaces the lifecycle/encryption/recovery knowledge in this skill. *WEW
is a **JVM** management runtime, not a multiplatform wallet primitive, and its accounts are ordinary
libnexakotlin `Bip44Wallet`s — it adds an operational layer, it does not change how a wallet is
created, restored, encrypted, or signed.*

**Right**: use the multiplatform `Bip44Wallet` primitive directly in client/UI apps (Patterns 1–7).
Reach for WEW only on a JVM server/back-office that benefits from its multi-account runtime, daemon
front-ends, or scripting (Pattern 8) — and even then the per-wallet rules here still govern each
account.

## Security considerations

- **The recovery phrase (`recoverySecret`) is the entire wallet.** It regenerates every private key
  on any device, bypassing on-disk encryption. Show it only to the user for backup, never log or
  transmit it, and expect `recoverySecret` to throw while the wallet is encrypted+locked — that is
  a feature, not a bug to work around.
- **Encrypt wallets that hold value.** An unencrypted wallet file is spendable by anyone who copies
  it. `encrypt(passphrase)` protects the keys at rest; keep the wallet `lock()`ed when idle so the
  decrypted secret isn't resident in memory longer than needed.
- **Don't commit wallet database files** (`*.db`) or any seed material to source control — the same
  `.gitignore` rule `nexa-project-setup` states. One leaked wallet file (unencrypted) is one drained
  wallet.
- **A pinned `exclusiveNodes` node is trusted for chain data.** An SPV wallet asks its nodes what
  the chain looks like; a malicious node can withhold transactions or lie about confirmations
  (though it cannot forge spends of your keys). Pin to a node you actually control, and apply the
  confirmation-depth rules in `nexa-transaction-construction` before treating funds as final.
- **A disconnected wallet cannot detect double-spends or confirmations** (it has no chain view).
  Use it for signing/derivation only; never make settlement decisions from a wallet that isn't
  syncing a chain.
- **Chain isolation is a safety boundary.** A wallet built for testnet cannot accidentally spend on
  mainnet (different `ChainSelector`, different serialization), which is exactly why you keep
  separate files and never copy keys across chains.

## Related skills and references

- `nexa-transaction-construction` — once a wallet is open and synced: `send(...)`, `txCompleter`, and
  the `setOnWalletChange` / `TransactionHistory` / 0-conf-finality model for watching funds.
- `nexa-identity-and-addresses` — the rotating-`getNewAddress` (P2PKT payout) vs
  stable-`destinationFor(COMMON_IDENTITY_SEED)` (identity) split, in depth.
- `nexa-ktor-server-integration` — wrapping this lifecycle in a server (`openWallet`/`newWallet` in
  `main()`, libnexaapp's `initBlockchain`, and the `org.wallywallet:wew` multi-named-wallet pattern
  that sits on top of single-wallet creation here).
- `nexa-wallet-connection` — the *opposite* model: an external Wally wallet that holds its own keys
  and signs for your app over TDPP/nexid, rather than a wallet your code controls.
- `nexa-rpc-node-client` — the authenticated JSON-RPC channel to a full node you operate, distinct
  from the SPV/electrum connection the wallet here uses.
- `nexa-electrum-monitoring` — the wallet follows its *own* keys via SPV; when you need to query or
  watch on-chain state the wallet doesn't own (arbitrary addresses, contract outpoints, token
  state), the `ElectrumClient` is the direct light-client tool.
- `nexa-capd-messaging` — the connected `Blockchain` this skill builds (`chain.net`) is also what
  CAPD off-chain messaging broadcasts and receives over (it needs a P2P peer to receive).
- `nexa-locktime-cltv` — uses `wallet.blockchain.getTip()?.time` for the locktime pre-flight check.
- `nexa-project-setup` — pinning libnexakotlin and registering its GitLab Maven repo; the platform
  SQL driver the wallet database needs.

### Supporting files in this folder

- `walletStartupTemplate.kt` — a drop-in open-or-create startup block (open → fall back to
  `newWallet`, plus the `recoverWallet` restore path and sync-gating boilerplate), encoding the
  new-doesn't-scan vs recover-scans distinction.
- `recoveryFlow.md` — a checklist for a safe create → show-phrase → confirm-backup → encrypt flow,
  the lock/unlock day-to-day, and the restore-with-scan path through `recoverWallet`.