---
name: nexa-debugging-onchain-errors
description: "Diagnoses Nexa failures by mapping symptom to root cause: rejected transactions, failing contract spends, wallets that won't connect, and build/runtime errors in org.nexa.* packages. Use when a tx is rejected and the cause is unclear, when decoding a specific error string, or when debugging a broadcast or contract reveal. Triggers: mandatory-script-verify-flag-failed, OP_EQUALVERIFY, Locktime requirement not satisfied, mempool min fee not met, please wait for wallet sync (code 73), inconsistent input value (code 16), Could not extract argsHash from address, NoSuchMethodError org.nexa.libnexakotlin.PlatformKt, Kotlin/Wasm stdlib mismatch, unknown session, txn-already-in-mempool, UnsatisfiedLinkError/libnexa.so, Cannot find state transition. Routes to the specific skill for each root cause."
---

# Nexa debugging: symptom → cause for on-chain failures

## When to use this skill

Trigger when a developer is debugging a tx that the network rejects, a contract spend
that fails, a wallet that won't connect, or a build/runtime error in `org.nexa.*`
packages. Concretely trigger on:

- Specific error strings: `mandatory-script-verify-flag-failed`, `OP_EQUALVERIFY`,
  `Locktime requirement not satisfied`, `mempool min fee not met`,
  `Script failed an OP_EQUALVERIFY operation`, `Could not extract argsHash from
  address`, `NoSuchMethodError: 'long org.nexa.libnexakotlin.PlatformKt`,
  `The version of the Kotlin/Wasm standard library`, `IllegalStateException: Symbol
  for Any not found`, `WebSocketException`, `Already resumed, but proposed with
  update CompletedExceptionally`, `unknown session`, `txn-already-in-mempool`,
  `already in block chain` (benign RPC re-broadcast results, not failures),
  `UnsatisfiedLinkError` / `libnexa.so` / `--enable-javacashlib` (the script-VM native
  library failed to load), `Cannot find state transition` (the NPL compiler can't realize a
  stack rearrangement).
- Tasks: "the tx rejected and I can't tell why", "what does this error mean",
  "debugging a NEXA broadcast", "why does my contract reveal fail with
  OP_EQUALVERIFY", "my NPL contract won't compile / cannot find state transition".

**Negative triggers** — do NOT use this skill for:
- Initial setup before any tx has been built — use `nexa-project-setup`.
- Routine code patterns when nothing is failing — use the relevant
  pattern-focused skill.

## Mental model

NEXA errors come from three layers, and pinning the right layer first saves hours:

1. **Build-time / load-time errors** — Kotlin compiler, JVM class loader. Symptoms:
   `NoSuchMethodError`, "stdlib differs from compiler", `IllegalStateException` at
   library init. Almost always a version-pin mismatch. (One build-time error is *not* a
   version mismatch: `Cannot find state transition` is the NPL compiler reporting a stack
   rearrangement it can't realize — see `nexa-npl-smart-contracts`.)
2. **Application-runtime errors** — your Ktor server is up but something inside
   business logic fails: a session is null, a CORS preflight blocks a request, a
   coroutine swallows an exception. Symptoms: 401/403/Conflict from your own routes,
   silent UI hangs, missing wallet callback.
3. **Network errors from the chain** — the tx is built and broadcast but rejected.
   Symptoms: `code: 16`/`code: 66`/`code: 73` rejections in the server log, broadcast exception
   in user code. The error string is the most reliable signal — the table below maps
   each error string to the actual root cause. Note two non-script code-16/73 cases the naive
   reader mistakes for script bugs or node outages: `inconsistent input value` (code 16, a
   wrong wire-serialized input amount) and `please wait for wallet sync` (code 73, bloom-gating
   or a stale 0-conf wallet wedge) — both decoded below.

For each on-chain error, the cause is rarely "the script is wrong" in the obvious
sense. It's almost always one of: (a) a satisfier-layout mismatch with the contract
DSL; (b) an `nSequence` / `nLockTime` interaction; (c) a stale contract bytecode
constant; (d) wrong address type as an output destination; (e) zero fee budget.

## Setup and versions

No special setup — this skill applies during diagnosis at any phase.

## Core patterns

### How to read a NEXA rejection log

A typical rejection log line:

```
chain_nexatest_cnxns_processor@(p2p.kt:1862)nexatest@127.0.0.1:7230:
Received reject of tx, code: 16, reason: mandatory-script-verify-flag-failed (Locktime requirement not satisfied)
```

Decode in order:

1. **`code: 16`** = consensus failure (script verification or finality) — this is
   `REJECT_INVALID` (`0x10`). The mempool-policy codes are a distinct range
   (`https://spec.nexa.org/network/messages/reject/`): **`64`** = `REJECT_NONSTANDARD`
   (`0x40`, non-standard or non-final per BIP68), **`65`** = `REJECT_DUST` (`0x41`, an output is
   too small), **`66`** = `REJECT_INSUFFICIENTFEE` (`0x42`, fee too low to relay — this is what
   `mempool min fee not met` maps to). `01` = `REJECT_MALFORMED` (couldn't even deserialize).
   So a `16` means "the tx itself is invalid (often the script)"; a `64`/`65`/`66` means "consensus
   would accept it, but mempool policy won't relay it." The parenthesized `reason` string is
   advisory and may change — match on the numeric code, not the text.
2. **`reason:`** is the network-level rejection category. Look for keywords:
   - `mandatory-script-verify-flag-failed` → consensus rule, script's fault
   - `mempool min fee not met` → tx accepted by consensus, rejected by mempool
     policy
3. The parenthesized suffix (e.g. `(Locktime requirement not satisfied)`,
   `(Script failed an OP_EQUALVERIFY operation)`) is the **actual diagnosable
   information**.

### Inspecting your tx with the explorer

After `broadcastTransaction` succeeds (or even after a server-side broadcast that the
*network* later rejects), the explorer is the ground truth.

```kotlin
val explorerUrl = when (DEFAULT_CHAIN) {
    ChainSelector.NEXATESTNET -> "https://testnet-explorer.nexa.org"
    else -> "https://explorer.nexa.org"
} + "/tx/${tx.idem.toHex()}"
println(explorerUrl)
```

For a rejected tx, the explorer typically shows "not found" — useful negative
confirmation. Check the funding UTXO instead: is it still unspent? Is its locking
script the template hash you expect?

### Reproducing a script-verify failure locally in the script VM

A `mandatory-script-verify-flag-failed` rejection from the network gives you one line. The
`org.nexa:scriptmachine` library — the JNI binding to the node's *actual* script VM — lets you
replay the same spend offline and watch it fail at the exact opcode, with the stack visible. This
turns "the contract rejected my reveal" into "instruction 14, `OP_EQUALVERIFY`, top of stack was
`X` not `Y`."

```kotlin
org.nexa.scriptmachine.Initialize()                         // once; auto-extracts+loads the bundled native VM
val sm = org.nexa.scriptmachine.ScriptMachine(fundingTxHex, spendTxHex)   // auto-detects the spend
sm.next(false)                                              // load, ready to step
var ok = true
do { ok = sm.step() } while (ok)
println("result: ${sm.scriptErr}  state: ${sm.getState()}") // failing-opcode position + stacks
// a clean spend ends "completed" with an empty main stack; a failed verify's scriptErr contains "failed"
```

This is the same VM the consensus rules use, so a clean run here predicts script-verify acceptance
(it does **not** check fees or MTP — those stay mempool/runtime concerns). Full patterns —
single-input replay, breakpoints, resource-limit checks, and a test-shaped pass/fail helper — are in
`nexa-script-machine-testing`.

### Decoding `mandatory-script-verify-flag-failed (Script failed an OP_EQUALVERIFY operation)`

This always means a `verify(a eq b)` in the contract evaluated false. Investigate in
this order:

1. **Stale bytecode constant.** Did you edit `ContractTest.kt`'s DSL recently?
   Re-run `getContractHashesAndBytecode`. Compare the new template hash to your
   `SECRET_CONTRACT_TEMPLATE_HASH_HEX`. If different, your bytecode is stale; paste
   the new one.
2. **Satisfier args mismatch.** Are you pushing an `outputIdx` that the DSL never
   declared? Are you missing a declared spender arg? See
   `nexa-npl-smart-contracts` for the canonical satisfier layout.
3. **Visible-args order mismatch.** Did you swap the order of `buyerHash` and
   `sellerHash` in your `NexaArgs(...)` call?
4. **Wrong address type.** Is the output's locking script actually a P2PKT? If you
   built `PayAddress(identityAddr).lockingScript()` from a P2PKH identity, the
   contract's `getOutputArgsHash(0)` returns garbage.
5. **Amount/unit confusion.** Did you set `out.amount = priceNexa` (whole NEXA)
   instead of `out.amount = priceNexa.nexa` (satoshi)?

The fastest way to pin down *which* `verify` failed is to stop guessing and **replay the spend
locally in the script VM** — it reports the exact failing opcode, position, and stack state
instead of the one-line network rejection. See "Reproducing a script-verify failure locally"
below.

### Decoding `Locktime requirement not satisfied`

Two distinct sub-causes:

- **Immediate** (the error fires the moment you broadcast, no waiting): your
  `input.sequence` is `0xFFFFFFFF` (the libnexakotlin default). Tx is "final", so
  `nLockTime` is ignored, so CLTV fails. **Fix:** set
  `input.sequence = 0xfffffffeL`. See `nexa-locktime-cltv`.
- **Persistent** (you've waited and it still fails): MTP hasn't caught up to your
  `nLockTime`. Tip time can be ahead of MTP by an hour or more on testnets.
  **Fix:** wait longer. Or use a larger contract timeout. See `nexa-locktime-cltv`.

To tell which: was the waiting time longer than ~3 hours? If yes, it's the sequence
issue (or some other bug). If no, just keep waiting.

### Decoding `mempool min fee not met` (reject code 66)

Your tx's fee is below the relay floor. Two distinct sub-causes:

- **Zero/near-zero fee budget on a constrained contract output.** For a contract spend where the
  script verifies `output[0].amount == purchaseAmt`, the contract makes it impossible to subtract a
  fee from that exact amount. **Fix:** overfund the contract output at funding time
  (`purchaseAmt + buffer`); the buffer becomes the fee at spend time.

  ```kotlin
  const val CONTRACT_FEE_BUFFER_SATOSHIS = 1000L
  val fundedSatoshis = priceSatoshis + CONTRACT_FEE_BUFFER_SATOSHIS
  ```

- **A self-funded covenant spend paid a *flat* fee too small for its size.** A covenant SPEND that
  funds its own fee from the input UTXO (no wallet `txCompleter` computing fees) must pay
  `fee ≥ ceil(txSize · MinFeeSatPerByte)` with **`MinFeeSatPerByte = 1.01`**. A fixed hop fee (e.g.
  a flat 500 sat on a ~700-byte spend) underpays and is rejected 66. **Fix:** sign once to realize
  the full satisfier, measure `tx.BCHserialize(NETWORK).size`, then
  `fee = ceil(size · DesiredFeeSatPerByte)` (1.1), set `out.amount = in − fee`, re-sign. A terminal
  spend that dumps its whole overfund buffer to fee sidesteps this; a continuing spend that keeps a
  buffer must size the fee. See `nexa-transaction-construction`.

See `nexa-npl-smart-contracts` for the funding-buffer pattern.

### Decoding reject `code 73 "please wait for wallet sync"`

Not a script error (script failures are code 16). The node is deferring relay. Two very different
causes — tell them apart before "fixing" the wrong one:

- **Transient, ONE tx** — a covenant SPEND of a *just-created (0-conf)* output relayed over the
  wallet's P2P net (`wallet.blockchain.net.broadcastTransaction`): the node's bloom-filter view of
  the SPV wallet doesn't yet include the fresh output. **Fix:** broadcast via node RPC
  `sendrawtransaction` (bypasses bloom gating) and wait for the **parent** to reach the node's
  mempool first. See `nexa-transaction-construction` / `nexa-rpc-node-client`.
- **Wedged, EVERY tx** — a long-lived dev/server wallet accumulated stale UNCONFIRMED txs the node
  never accepted, and now funds new txs from phantom 0-conf change whose parents the node lacks, so
  it defers *every* relay (even a genesis spending confirmed coins). Looks like a node outage; the
  node is fine (RPC `getblockchaininfo`: `blocks == headers`, `initialblockdownload == false`).
  **Fix:** `wallet.cleanUnconfirmed()` so the wallet spends only CONFIRMED UTXOs; call it at server
  boot and in IT self-heal. See `nexa-wallet-lifecycle-and-chain`.

### Decoding reject `code 16 "inconsistent input value"`

This is a code-16 rejection whose text is `inconsistent input value` (NOT
`mandatory-script-verify-flag-failed` — so it is *not* a script bug). A Nexa tx **serializes each
input's spent `amount` on the wire, and the node validates it against the real UTXO value** (the
BIP143-style sighash also commits to it). You set an input's `spendable.amount` to something other
than the true prevout value — the node does **not** silently read the true amount from the chain; it
rejects your declared one. Most often this comes from trying to pre-seed extra fee by *lowering* a
covenant input's amount. **Fix:** keep each input's `amount` = the true UTXO value; to pre-seed fee,
bias `inamt` in the TDPP URL string only (rewrite `inamt=<real>` → `inamt=<real−pad>`), or pass
`txCompleter(..., inputAmount = real − pad)`. See `nexa-transaction-construction`.

### Decoding `NoSuchMethodError: 'long org.nexa.libnexakotlin.PlatformKt.<name>()'`

Library version mismatch. The compiled `libnexaapp` bytecode you have was built
against a version of `libnexakotlin` that named this function differently (or in a
different class). Common renames:

- `millinow()` ↔ `epochMilliSeconds()` (the function was renamed in a libnexakotlin release;
  pin the `libnexakotlin` your `libnexaapp`'s POM declares)

**Fix:** check the POM of your `libnexaapp` artifact for its declared
`libnexakotlin` version, and pin your `nexa_libnexakotlin` to match exactly. See
`nexa-project-setup` § "Verifying version compatibility".

### Decoding `The version of the Kotlin/Wasm standard library (X) differs from the version of the compiler (Y)`

Your `kotlin = "Y"` in `libs.versions.toml` doesn't match the stdlib version some
transitive dep brought in. **Fix:** bump `kotlin = "X"` to match. See
`nexa-project-setup`.

### Decoding `IllegalStateException: Symbol for Any not found`

On WASM builds only. Caused by `mavenLocal()` being listed *above* `mavenCentral()`
in `settings.gradle.kts`, so a stale local Kotlin stdlib shadows the published one.
**Fix:** move `mavenLocal()` below `mavenCentral()`. See `nexa-project-setup`.

### Decoding `unknown session` from libnexaapp routes

The wallet's POST/GET to `/_lp`, `/_share`, etc. carries a `cookie=<id>` query param.
That id must already exist in the server-side `sessionHandler`. If the server
restarted between the time the QR was generated and the wallet scan, the session id
is gone. **Fix:** regenerate the QR (refresh the page) so the wallet picks up a
fresh session id.

### Decoding `Could not extract argsHash from address: ...`

You called `extractArgsHash(addr)` on a non-P2PKT address. Almost certainly:

- `addr` is `session.identity.value` (a P2PKH from the login signature) — use
  `session.userNexaAddress.value` instead.
- `addr` is empty/null — the wallet hasn't shared an address yet, retry after
  reconnect.

See `nexa-identity-and-addresses`.

### Decoding `WebSocketException: {"target":{},"type":"error","isTrusted":true}` on the wasm client

Generic browser WebSocket error. Most common cause: server-side an exception was
thrown inside the WebSocket handler (e.g., from `NexaAppSession.<init>` calling
`millinow()` and crashing), causing the upgrade to fail before completion.
**Fix:** look at server logs for an earlier exception, often the real cause.

### Decoding `Already resumed, but proposed with update CompletedExceptionally`

Ktor Wasm WebSocket client surfaced an error in `kotlinx.coroutines.CancellableContinuationImpl`.
Usually a downstream symptom of the server-side error above. Look at the server log for
the upstream exception.

### Decoding browser-client API call that "hangs forever"

Two equally common causes:

1. **CORS preflight blocked.** Your POST sends `Content-Type: application/json`; CORS
   doesn't list `ContentType` in allowed headers. Browser blocks the actual POST. The
   fetch promise rejects with a generic TypeError. If your client code doesn't
   try/catch around the launch, the rejection is swallowed and the UI stays in
   "Loading…". **Fix:** add `allowHeader(HttpHeaders.ContentType)` server-side, AND
   wrap client API calls in try/catch so errors surface.

2. **Server crashed mid-request.** Stuck connection. Restart server, check logs.

### Decoding RPC broadcast `txn-already-in-mempool` / `already in block chain`

These are **not** failures. When you broadcast through the node's JSON-RPC
`sendrawtransaction` (rather than `net.broadcastTransaction`), re-submitting a tx the node
already has throws with `txn-already-in-mempool` (still
unconfirmed) or `already in block chain` (already mined). Idempotent retry / auto-claim loops
hit this constantly. **Fix:** treat both as success and recover the txid locally:

```kotlin
if (msg.contains("txn-already-in-mempool", true) || msg.contains("already in block chain", true))
    return BroadcastResult(success = true, txid = extractTxidAndOutpoint(txHex).first)
```

A genuine rejection (bad script, low fee, non-final) carries a different reason string — use
the `code:` / `reason:` decoder at the top of this skill. See `nexa-transaction-construction`
for the full RPC broadcast helper, and `nexa-rpc-node-client` for the JSON-RPC client itself.

### Decoding `NexaRpcException: Unauthorized (bad rpc username/password)` (code 401)

Thrown by the `org.nexa:nexarpc` client when the node rejects the RPC credentials. The node
needs `server=1` in its `nexa.conf`, and the `username`/`password` you passed to
`NexaRpcFactory.create(...)` must match the `rpcuser`/`rpcpassword` (or `rpcauth`) in that file.
Don't retry this in a loop — fix the credentials or the node config. Note that the RPC client
throws `NexaRpcException` (carrying the node's `code`/`message`) for *all* RPC failures, not an
`IOException`; catch that type. See `nexa-rpc-node-client`.

## Common mistakes and anti-patterns

The full list of symptom → fix:

| Symptom | Most likely root cause | Skill                       |
| --- | --- |-----------------------------|
| `NoSuchMethodError org.nexa.libnexakotlin.PlatformKt.<X>()` | `libnexakotlin` ≠ what `libnexaapp` was built against | `nexa-project-setup`          |
| `The version of the Kotlin/Wasm standard library (X) differs from compiler (Y)` | Bump `kotlin = X` in `libs.versions.toml` | `nexa-project-setup`          |
| `IllegalStateException: Symbol for Any not found` (WASM build) | `mavenLocal()` listed above `mavenCentral()` *with a conflicting local stdlib snapshot to shadow* | `nexa-project-setup`          |
| AGP-version incompatibility from Android Studio | Bump or downgrade `agp` to match AS | `nexa-project-setup`          |
| Wallet QR displays but never connects | `EXTERNAL_URL` is wrong/stale; not reachable from Wally's network; server bound to `localhost` | `nexa-wallet-connection`      |
| Login QR blank/empty | libnexaapp's `createQrSvg` doesn't XML-escape `&` in `onclick` SVG attr | `nexa-wallet-connection`      |
| Wallet hits `/_lp` and gets 404 / "unknown session" | Server restarted; QR was generated against a stale session id. Re-fetch QR. | `nexa-wallet-connection`      |
| Wallet broadcasts tx but server state never advances | `/tx` callback not registered (GET, not POST), or wrong path | `nexa-wallet-connection`      |
| `Could not extract argsHash from address: ...` | Address is P2PKH (identity), not P2PKT (receive) | `nexa-identity-and-addresses`  |
| "Listing thinks I'm not the seller" after a spend | Ownership check keyed off rotating receive address instead of stable identity | `nexa-identity-and-addresses`  |
| Browser API call hangs in "Loading…" forever | CORS preflight blocked (missing `allowHeader(ContentType)`), OR coroutine swallowed an exception | `nexa-ktor-server-integration` |
| `WebSocketException: {"target":{},"type":"error","isTrusted":true}` | Server-side exception in WS handler before upgrade — usually a NoSuchMethodError | `nexa-project-setup`          |
| `Already resumed, but proposed with update CompletedExceptionally` | Downstream of WS exception above | `nexa-ktor-server-integration` |
| `mempool min fee not met` (code 66) | Contract verifies output==purchaseAmt; no fee budget → overfund at funding. OR a self-funded covenant spend paid a flat fee below `ceil(size·1.01)` → size the fee | `nexa-npl-smart-contracts` / `nexa-transaction-construction` |
| `code: 73` "please wait for wallet sync" on ONE covenant spend of a 0-conf output | Node's bloom view of the SPV wallet lacks the fresh output → broadcast via RPC `sendrawtransaction`, wait for the parent in mempool first | `nexa-transaction-construction` / `nexa-rpc-node-client` |
| `code: 73` "please wait for wallet sync" on EVERY tx (even confirmed-coin spends) | Stale 0-conf backlog: wallet funds from phantom change the node lacks. Node is fine (`getblockchaininfo`). `wallet.cleanUnconfirmed()` | `nexa-wallet-lifecycle-and-chain` |
| `code: 16` `inconsistent input value` (NOT a script-verify message) | An input's serialized `amount` ≠ the true UTXO value (often from lowering a covenant input to pre-seed fee). Keep the true amount; bias `inamt` in the URL only | `nexa-transaction-construction` |
| `Locktime requirement not satisfied` immediately after broadcast | `input.sequence == 0xffffffff` (SEQUENCE_FINAL); set to `0xfffffffeL` | `nexa-locktime-cltv`          |
| `Locktime requirement not satisfied` after long wait | MTP hasn't caught up to `nLockTime`. Wait or pick a longer contract timeout. | `nexa-locktime-cltv`          |
| `mandatory-script-verify-flag-failed (Script failed an OP_EQUALVERIFY operation)` on reveal | Bytecode constant stale; satisfier args layout wrong; visible-args order wrong | `nexa-npl-smart-contracts`     |
| `mandatory-script-verify-flag-failed` on refund only (reveal works) | Old buggy contract bytecode; user edited DSL but didn't regenerate constant | `nexa-npl-smart-contracts`     |
| Per-session view shows the previous wallet's data after switching wallets | `onWalletConnected` doesn't recompute views; views filtered by rotating field | `nexa-server-state-and-flows`   |
| `IllegalArgumentException: Registered duplicate name: <name>` (client) / `FlowConnector flow named <name> already exists` (server) | Two flow registrations with the same name | `nexa-server-state-and-flows`   |
| Flow update never reaches client | Type isn't `@Serializable`; CBOR encoding silently throws inside the connector | `nexa-server-state-and-flows`   |
| `CborDecodingException: Input contains N unprocessed bytes left after decoding a value` | Possible kotlinx-serialization / CBOR version interaction; see the CBOR caveat | `nexa-project-setup`          |
| `WalletNotSupportedException: This denotes a token type, not an address` | Tried to pay to / build a locking script from a `PayAddressType.GROUP` (token-type) address. Pay to a P2PKT/TEMPLATE address and attach the group to its script. | `nexa-tokens-and-groups`       |
| Token sent but recipient shows zero / "no token received" | Token quantity put on `out.amount` (that's native sat) instead of attached to the script via `ofGroup`/`grouped`; output ended up ungrouped | `nexa-tokens-and-groups`       |
| Token-covenant spend rejected with `OP_EQUALVERIFY` on a group check | Output's group id doesn't match the prevout group (missing/incorrect `verifySameGroup`/`getOutputGroupId eq`), or authority bits read as a number instead of raw bytes | `nexa-tokens-and-groups`       |
| Mint fails / "wallet cannot access tokens" / not-enough-token-balance when minting | `txCompleter` found no spendable MINT authority — forgot `USE_GROUP_AUTHORITIES`, or the authority pool is exhausted by concurrent mints. Set the flag; pre-split a pool of authorities and retry | `nexa-tokens-and-groups`       |
| Incoming payment credited/shipped, then "disappears" or double-spends | Acted on the first (0-conf) `setOnWalletChange` sighting; tx was replaced, never mined, or re-orged. Gate irreversible actions on `confirmedHeight >= 0` (deeper for high value); a later callback with `confirmedHeight == Long.MIN_VALUE` is the explicit "this 0-conf tx is being dropped as invalid" signal | `nexa-transaction-construction` |
| Wallet owns a token but it never shows up in the user's asset/portfolio list | The `/assets` proof failed verification, the output was ungrouped, or its group is **fenced** (`GroupId.isFenced()` → skipped). Check the proof against your issued challenge and that you read the group with `script.groupInfo(amount)` | `nexa-tokens-and-groups` |
| Token/NFT name, ticker, icon, or decimals come back empty (`getTokenGenesisInfo` finds nothing) | The id is a **subgroup** (typical NFT) — a subgroup has no genesis of its own; the metadata lives on the parent. Query `gid.parentGroup()`, or use `getTokenInfo(...)` / libnexaapp's `AssetManager` which handle the hop | `nexa-tokens-and-groups` |
| NFT artwork serves as 0 bytes / only the collection icon shows, though the `AssetInfo` loaded (`loadState == COMPLETED`) | `iconBytes`/`publicMediaBytes` are **null for media over ~20 KB** (flushed to the asset cache on disk) — read `ai.iconUri` / `publicMediaCache` back via `assetManager.loadCardFile(ref)` instead of the byte fields | `nexa-tokens-and-groups` |
| Built-in `/assets` verification never completes / NPEs, or `AssetManager` lookups fail on the server | libnexaapp's `blockchain` global was never set — call `initBlockchain(bc, assetDir, cacheDir)` before starting Ktor (the handler verifies proofs via `blockchain!!.net.getNode()`) | `nexa-ktor-server-integration` |
| `import org.nexa.libnexakotlin.TDPP_FLAG_*` won't resolve / unresolved reference | Stale libnexakotlin pin or a typo — the six `TDPP_FLAG_*` constants ARE top-level exports of `org.nexa.libnexakotlin` (`NOFUND=1, NOPOST=2, NOSHUFFLE=4, PARTIAL=8, FUND_GROUPS=16, HIDE_ASSET_DETAILS=32`); the values are also protocol-fixed, so a literal works too | `nexa-wallet-connection` |
| `/tx` callback advances state twice for one operation | The wallet GETs `/tx` more than once (it does so even when it broadcasts). Guard the continuation with a per-session "already processing/done" flag | `nexa-wallet-connection` |
| `/tx` callback arrives with NO `tx` parameter (only `cookie` + `resultcode=300`) | Not malformed — the **user rejected** the request in the wallet. Cancel the pending operation for that cookie | `nexa-wallet-connection` |
| Wallet shows the user a "transaction failed" warning although the tx broadcast fine | Your `/tx` callback's response body contained `error`/`invalid`/`rejected` (case-insensitive substring) — the wallet parses the body. Keep success bodies neutral (`ok`) | `nexa-wallet-connection` |
| Wallet keeps telling the user to "refresh the QR" | Its `/_lp` long-poll got HTTP 400/404 — the session id is gone (server restart, expiry). Also happens on auto-reconnect after a wallet-app restart when the old session died; serve a fresh QR | `nexa-wallet-connection` |
| `/assets` reply arrives but every entry's `proof` is null though you sent `chalby` | The challenge didn't decode to 8–64 bytes — the wallet enumerates but silently omits proofs. Fix the challenge size | `nexa-tokens-and-groups` |
| `NexaRpcException: Unauthorized (bad rpc username/password)` (code 401) | Node RPC credentials wrong, or `server=1` missing from `nexa.conf`. Fix config/creds; don't retry | `nexa-rpc-node-client` |
| `NexaRpcException` from a node call you can't place / RPC has no typed method | Use `calls(name, params)` / `callje(name, params)` to inspect the raw reply; catch `NexaRpcException` (not `IOException`) and read `e.code` | `nexa-rpc-node-client` |
| `UnsatisfiedLinkError` / "Likely libnexa.so was NOT configured with --enable-javacashlib" when a test starts | The script-VM native library failed to load. It ships bundled in the `libnexakotlin-jvm` jar and is auto-extracted to `<working dir>/lib/` by `Initialize()` — so check: platform not covered by the bundled builds, unwritable working dir, `Initialize()` never called, or a self-supplied build (via `initializeLibNexa(variant)`) compiled without `--enable-javacashlib` | `nexa-script-machine-testing` |
| Contract spend rejected on-chain but you can't tell which `verify` failed | Replay the funding+spend tx through the script VM (`ScriptMachine(parentHex, childHex)` + `step()`); it reports the exact failing opcode + stack | `nexa-script-machine-testing` |
| `ScriptMachineException: No spend` / "These transaction are not related by a spend" constructing a two-tx replay | The two txs you passed aren't connected — wrong pair, the child spends a different funding tx, or the outpoints don't match. Verify the child's input outpoints against the parent's outputs | `nexa-script-machine-testing` |
| `ScriptException: The hidden args script … must contain only data push instructions` constructing a replay | Satisfier layout wrong: missing hidden-args push (prevout commits to an argsHash but the input doesn't push one, so the first satisfier arg is misread as the constraint), or the hidden-args push contains executable opcodes | `nexa-script-machine-testing` / `nexa-npl-smart-contracts` |
| `Prevout template hash / input template mismatch` in `scriptErr` after constructing a replay | The template script pushed in the input doesn't hash to the prevout's committed template hash — stale/regenerated bytecode or the wrong contract version. In `tolerant` mode (default) the run continues with your script anyway; treat the message as the failure | `nexa-script-machine-testing` / `nexa-npl-smart-contracts` |
| `IllegalStateException: Cannot find state transition` (printed `((…), (…))⇒((…), (…))`) when compiling an NPL contract | The compiler needs a stack rearrangement that isn't in `stackX` and no dynamic generator produced it — usually a deep/unusual permutation, or the init scaffold didn't register the needed tier, or a stale `stackScripts.bin` cache. Register the printed transition **from your own project** via `stackX.add(...)` (or `DynamicStackTransformRegistry.register(...)` for a recurring shape — no NPL source edit needed), ensure that code runs before `compile()`, and delete the stale cache | `nexa-npl-smart-contracts` |
| A tx spending many covenant/contract UTXOs is rejected as too large / won't relay (serialized size ballooned) | Every input carries the full validation script (satisfier + template each), so size grows with input count past the relay-policy limit (~100 KB order). Restructure with the enforcer/follower split: one dust "enforcer" input validates the whole output set; each real input only proves the enforcer is present | `nexa-npl-smart-contracts` |
| `sendTxVal` callback never fires / reply is the empty string | No txval-capable node reachable on the P2P connection (or the request timed out) — pin a trusted node with `exclusiveNodes` and always bound the wait with your own timeout; empty ≠ valid | `nexa-transaction-construction` |
| `SerializationException` parsing a `sendTxVal` reply | The reply can be the **plain text** `transaction already in mempool` (not JSON) — check for it before `parseTxValReply`; it means success | `nexa-transaction-construction` |
| Contract-spend watcher never fires though the outpoint was spent | The watcher treated `ElectrumNotFound` as the spend signal. A spent outpoint RETURNS from `getUtxo` with `status == "spent"` (+ the spending tx in `spent`); the exception only means the outpoint is unknown to the server | `nexa-electrum-monitoring` |
| `import ConnectWalletButton` (unqualified) stops resolving after a libnexaapp bump — or the qualified `org.nexa.libnexaapp.compose.ConnectWalletButton` import fails on an older one | The high-level components (`ConnectWalletButton`/`LightModeToggle`/`NexaInputField`, plus the newer `LoadAssetsButton`) moved from the root package into `org.nexa.libnexaapp.compose` in a libnexaapp update — match the import style to your artifact | `nexa-compose-ui-design` |
| The previous wallet's tokens/NFTs still render after disconnecting / connecting a different wallet | Client-held asset state: `walletOwnsAssetHandler` deliveries accumulate in your own store and nothing clears it — dedupe by `outpointHash`, clear on `walletConnected == false` and before re-requests; server-side, clear app session fields in BOTH `disconnectWallet()` and `handleAbandoned()` overrides | `nexa-tokens-and-groups` / `nexa-wallet-connection` |
| After a server-side disconnect the wallet can never reconnect (every `/_lp` answered `Q`) until the user fetches a fresh QR | Working as designed: `disconnectWallet()`/`handleAbandoned()` set the session's `allowWalletConnection = false`, and only generating a new connect/login URI re-enables it — re-serve the QR rather than fighting the gate | `nexa-wallet-connection` |
| A `/tx` callback arrives with `resultcode` 201 / 202 / 203 / 204 (not 200 or 300) | Not a rejection: 201 = wallet filled the tx but signatures are still missing (continue the multi-signer flow), 202 = tx unmodified, 203 = created but not currently final (locktime/MTP — `nexa-locktime-cltv`), 204 = wallet's chain connection is down so it couldn't post (consider broadcasting the returned tx yourself) | `nexa-wallet-connection` |
| A tx with a hand-built read-only input is rejected, or its referenced UTXO "doesn't work" as read-only | Read-only inputs (type byte 1) must have `amount = 0` and `sequence = 0`, the tx needs ≥1 normal input, and the referenced UTXO must be **confirmed** (a same-block/unconfirmed output can't be read read-only). A signed read-only input's satisfier must be valid; an empty one is legal but proves/activates nothing | `nexa-transaction-construction` |

### Diagnostic workflow

When an on-chain failure happens:

```
1. Capture the full reject line from server logs (`code: N, reason: <category> (<detail>)`).
2. Open the table above and find the matching row.
3. Apply the fix.
4. Rebuild the FAILING tx from scratch (don't try to retry the cached one).
5. If reveal/claim was the failure: make sure the contract BYTECODE matches the DSL.
6. If refund was the failure: verify input.sequence AND MTP.
7. If broadcasting your own tx: print the tx hex and inspect with the explorer's tx-decode UI.
```

When a build-time failure happens:

```
1. Read the first line of the stack trace — that's the class that failed to link.
2. If `org.nexa.libnexakotlin.*KT` — version mismatch with libnexaapp.
3. If `org.jetbrains.kotlin.*` — Kotlin compiler version drift.
4. If `org.gradle.api.*` — AGP / Gradle version mismatch.
5. Cross-check the failing artifact's POM against your `libs.versions.toml`.
```

When a UI hangs (no error, no progress):

```
1. Open browser DevTools → Network tab.  Is the API call shown?
   - No: client never made the request (CORS preflight blocked, or client never reached
     this code).  Check console for CORS errors.
   - Yes, pending: server didn't respond.  Check server logs.
   - Yes, 4xx/5xx: server rejected.  Read the response body.
2. If a CORS error: add `allowHeader(HttpHeaders.ContentType)` and `exposeHeader(...)` as needed.
3. Check that client-side API helpers are wrapped in try/catch so errors surface.
```

## Security considerations

- **Don't log raw tx hex or signed messages in production.** They contain spendable
  authority and on-chain commitments. Development-only.
- **Don't log session cookies.** Sessions are credentials.
- **Don't log wallet identity addresses unless you've decided they're public.** In
  many apps the identity address is a stable user identifier whose disclosure
  enables tracking.
- **When a tx is rejected, do not display the full tx hex to the user.** It may
  contain partial signatures or the kind of in-flight state that's useful to an
  attacker who can replay/modify.

## Related skills and references

- Every other skill in this corpus — this skill is the index of "when things go
  wrong, which other skill explains what was happening?"

### Supporting files in this folder

- `errorCodeReference.md` — the P2P reject codes (`REJECT_MALFORMED`…`REJECT_INSUFFICIENTFEE`), the
  broadcast result strings (idempotency), and the `NexaRpcException` code space, mapped to causes.
- `decodingBytecodeHowto.md` — the procedure for debugging an on-chain failure: fetch the txs,
  `toAsm` disassemble, map opcodes back to the DSL, and replay through the script VM.
- `runbookBrokenBuild.md` — step-by-step triage when the project no longer compiles after a
  dependency bump (resolution vs compile vs test-time link errors).