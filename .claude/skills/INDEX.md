# Nexa skills corpus — index

A corpus of skills for building Nexa blockchain applications in Kotlin / Kotlin
Multiplatform (libnexakotlin + libnexaapp + NPL, with the Wally wallet). Each skill is a
`SKILL.md` in its own folder and follows the same fixed section structure: *When to use this
skill, Mental model, Setup and versions, Core patterns, Common mistakes and anti-patterns,
Security considerations, Related skills and references.*

When refining, see `CHANGELOG.md` for the running history of edit passes (read its most recent
"Notes for the next agent" before starting).

## Skills

| Skill                                                               | Use it for |
|---------------------------------------------------------------------| --- |
| [nexa-project-setup](nexa-project-setup/SKILL.md)                       | Gradle/version pinning, the GitLab Maven repos, libnexakotlin↔libnexaapp compatibility traps, and **build-time** errors in `org.nexa.*`. |
| [nexa-wallet-lifecycle-and-chain](nexa-wallet-lifecycle-and-chain/SKILL.md) | The libnexakotlin wallet bootstrap a Nexa app runs at startup: **create / restore-from-recovery-phrase / open / encrypt** a `Bip44Wallet` (`newWallet`/`recoverWallet`/`openWallet`/`openDisconnectedWallet`), connect the SPV/electrum chain (`blockchainFor`/`exclusiveNodes`/`getTip`), read `getNewAddress`/`getCurrentDestination`/`balance`/`synced`, **sign/verify messages and data with wallet keys** (`signMessage` vs `signData`), pause/resume/maintain the wallet (`pause`/`resume`, `cleanReserved`/`cleanUnconfirmed`, `rediscover`), **fast-forward a far-behind wallet** (`fastForward` derivation-path sync), gate startup on `sync(maxWait)`/`chainstate`, enumerate spendable UTXOs (`forEachUtxo`/`filterInputs`/`reserved`), the connection-manager knobs (`GetCnxnMgr`/`GetBlockchain` with `start=false` to pin nodes before connecting; the `getElectrum`/`returnElectrum` pool; `getElectrumServerCandidate`), **connectivity checks** (`net.p2pCnxns` — gate sends on peers, live chain-status indicator), and relocating wallet/data files on the JVM (`dataDirectory`). The keys-you-control counterpart to the external-Wally model. |
| [nexa-ktor-server-integration](nexa-ktor-server-integration/SKILL.md)     | Wiring the Ktor server: CORS, route ordering, `installWalletRoutes`, config loading (`app.cfg`/`AppConfig` vs `startercfg.json`), the `initBlockchain` server globals (`blockchain`/`assetDataDir`/`cacheDataDir`), server signing wallets, serving the Wasm frontend, and serving asset/NFT media bytes. |
| [nexa-wallet-connection](nexa-wallet-connection/SKILL.md)               | Connecting the Wally wallet over the TDPP/nexid URI-push + HTTP-callback protocol; login/connect QR generation; `/_share` and `/tx` callbacks (incl. the `resultcode=300` rejection pings and the wallet-parsed `/tx` response-body contract); the `/_lp` long-poll wire protocol (`A`/`Q`/URI bodies, wallet auto-reconnect); the TDPP `flags` bitfield (`TDPP_FLAG_*`, exported by libnexakotlin), nexid login-signature format + ops (login/reg/info/sign incl. `signhex`), the wallet's multi-account (host, topic)→account domain binding, request-signing canonicalization, `/sendto` (`amtN` in satoshis), the full DPP result-code vocabulary (`201` missing-sig / `203` not-final / `204` cannot-post, and late-reply semantics), the nexid `ctxsig` challenge-tx reply form, Trickle Pay domain registration / hands-free pay, **implementing the wallet-facing routes without libnexaapp** (hand-rolled `/_lp`, wallet-silent-vs-disconnected, QR-alphanumeric cookies), QR-scan-only request delivery (no long-poll needed), the **browser-facing `/api/wallet/*` trigger routes** (`tdpp?msg=` generic push, `assets?filter=` one-call asset round trip, `connectText`, `disconnect`), and the disconnect mechanics (`allowWalletConnection` reconnect gate; clear app session state in both `disconnectWallet()` and `handleAbandoned()`). |
| [nexa-identity-and-addresses](nexa-identity-and-addresses/SKILL.md)       | The P2PKH **identity** (stable, auth) vs rotating P2PKT **payout** address split; `extractArgsHash`, reconstructing/reading addresses from on-chain args. |
| [nexa-transaction-construction](nexa-transaction-construction/SKILL.md) | Building/signing/broadcasting NEXA txs; sat vs whole-NEXA units; the three build flavors; tx completion via `txCompleter`/`TxCompletionFlags` (fund/sign/partial-offer) and the `iTransaction.createTdppUrl` push builder; **validating a returned/partial tx against a trusted node without broadcasting** (`sendTxVal`/`parseTxValReply`) and verifying/selectively signing a returned proposal (`script.parameterized()`, `signInput`); `OP.TMPL_SCRIPT` parameterized outputs and store-and-serve sell offers; broadcasting via `net.broadcastTransaction` or RPC `sendrawtransaction`; **read-only inputs** (`NexaTxInput.type` — reference/read a UTXO without spending it; shared oracle/state UTXOs, ownership proofs); per-chain explorer links (`ChainSelector.explorer`); confirmation/finality (0-conf, reorgs). |
| [nexa-npl-smart-contracts](nexa-npl-smart-contracts/SKILL.md)             | Writing/compiling/spending NPL script-template contracts; the DSL→bytecode workflow; the dependency-based (read-only-bindings) execution model; visible/hidden/spender args + `templateArgs` and the universal-contract (constant-template-hash) tradeoff; satisfier layout; P2T outputs; signature/oracle checks (`checkSigVerify`/`checkDataSigVerify`); cross-input reads (`getPrevoutVisibleArg`) and the **enforcer/follower** pattern for many-input covenant txs (relay-size limits); **scriptlets** (a holder-supplied script arg the template runs via `OP_EXEC` — holder-chosen locks without changing the template hash); **how compilation works and how to fix `Cannot find state transition`** (registering a transition/`DynamicStackTransform` from your own project) plus compile-time stack/size diagnostics; the full DSL surface in `dslReference.md`. |
| [nexa-tokens-and-groups](nexa-tokens-and-groups/SKILL.md)                 | Nexa native tokens (**groups**): `GroupId`/`GroupInfo`/`GroupAuthorityFlags`; content-addressed subgroup NFT ids; building token outputs (`ofGroup`/`payTo`); reading a token off a tx; the contract-side `getOutputGroup*`/`verifySameGroup` introspection; same-group covenants; **minting by spending a MINT authority** (`USE_GROUP_AUTHORITIES`/`NO_BATON_AUTHORITIES`, mint-on-demand half-tx, authority pools, `chunkTokenInto` token-UTXO pools, reusing a BATON authority without consuming it via a read-only input); the off-chain token-description document and its genesis SHA-256 commitment; **resolving display metadata** (`getTokenInfo`/`TokenDesc`, the subgroup→`parentGroup()` rule); proving wallet token/NFT ownership via the TDPP `/assets` flow — including libnexaapp's **built-in `/assets` verification** (`checkAssetChallenge`, `session.assets`/`OwnedAssetInfo`, the `WALLET_HAS_ASSET` client notification, the one-call browser trigger `GET /api/wallet/assets`, and the client-side dedupe/clear-on-disconnect hygiene) — and **displaying an NFT's own artwork** via `AssetManager` (`getNftFile`/`track`+`load`, the NFT-zip `cardf`/`info.json` layout, the large-media-flushed-to-disk rule + `loadCardFile`). |
| [nexa-locktime-cltv](nexa-locktime-cltv/SKILL.md)                       | `OP_CHECKLOCKTIMEVERIFY`, the mandatory `nSequence < 0xFFFFFFFF` rule, MTP lag, timestamp-vs-height threshold, and picking realistic contract timeouts. |
| [nexa-server-state-and-flows](nexa-server-state-and-flows/SKILL.md)         | libnexaapp's `flowConnector` reactive state over WebSocket — global vs per-session flows, `connectFlows`/`aConnectFlows`, serialization constraints, functional-flow last-value replay, the notification/app-message frames on the same socket (`sendNotification`/`walletOwnsAssetHandler`/`setAppMessageHandler`), the client HTTP helper family (`setupServerConnection`, `getFromServer`/`asGetFromServer`/`postToServer`, `sessionId`/`coScope`), and the hand-rolled WebSocket channel a **non-Kotlin (Vue/React/JS) frontend** uses instead (per-session socket lists, serialized sends, ping/pong). |
| [nexa-compose-ui-design](nexa-compose-ui-design/SKILL.md)                   | Building a clean, branded, responsive **front end** with libnexaapp's Compose Multiplatform design library (`org.nexa.libnexaapp:compose`): the `DesignScheme` + global `design` flow theming model, deriving palettes with the color utilities, dark/light mode, `NexaApp`/`appDim` responsiveness, the themed components (`BasicButton`/`IconTextButton`/`LightModeToggle`/`ConnectWalletButton`/`LoadAssetsButton`/`NexaInputField` — incl. the root-package→`compose`-package migration, the inverted `getNexaExchangeRate`↔`exchangeRate` direction, and `supplementalButtonText`), `launchApplink` ("open in wallet" on the same device), `vsash`/`hsash` split panes (incl. weighted panes), rendering **runtime-fetched image bytes** (`decodeToImageBitmap`; `makeImageBitmap` is JVM-only), and the design editor. Also the UI-quality fundamentals on this foundation-based stack: **accessibility** (you own semantics/roles/touch-targets since the components aren't Material), **Compose mechanics & performance** (recomposition, state stability, list keys, side effects), and **loading/refresh UX**. |
| [nexa-rpc-node-client](nexa-rpc-node-client/SKILL.md)                     | The `org.nexa:nexarpc` JSON-RPC client to a **full node you operate**: `NexaRpcFactory`/`NexaRpc`, blocking calls + `NexaRpcException` (and `JvmNexaRpc`'s suspend `_`-prefixed variants for coroutine code), chain/mempool/wallet reads, `sendrawtransaction`/`enqueuerawtransaction`, `getrawtransaction`/`gettransactiondetails`, the `calls`/`callje` escape hatch, node statistics (`getstat*`), the token-issuance RPCs (incl. `tokenBalance`), and regtest `generate`/`invalidateblock` for tests. |
| [nexa-electrum-monitoring](nexa-electrum-monitoring/SKILL.md)           | libnexakotlin's `ElectrumClient`: light-client queries/monitoring of **arbitrary** on-chain state (any address/script/outpoint/tx/token, no node required) — `getTx`/`getUtxo` (a spent outpoint returns `status "spent"` + the spending tx; `ElectrumNotFound` = unknown outpoint)/`getHistory`/`listUnspent`/`getBalance`/`getFirstUse`, `getTokenGenesisInfo`/token reads, `subscribeHeaders`+re-poll monitoring (incl. invalidating stored partial-tx offers), `sendTx`, and the untrusted-server light-client trust model. |
| [nexa-capd-messaging](nexa-capd-messaging/SKILL.md)                     | Nexa's **CAPD** off-chain P2P message bus (a feature unique to Nexa): `CapdMsg`/`CapdQuery`, PoW anti-spam (`solve`/`setPowTargetHarderThanPriority`/`CapdTooDifficult`), expiration/rescind, `chain.net.broadcastMsg`, the encrypted conversation channel `CapdProtocolCommunication` (`send`/`receive`, incl. `prefixSize`/`receive(filter)` sub-channels) for multi-party coordination (swaps, rendezvous), and the **built-in M-of-N multisig wallet contract** whose formation runs over CAPD (`MultisigWalletContract`/`handleContractFormationInvitation`). |
| [nexa-script-machine-testing](nexa-script-machine-testing/SKILL.md)       | Local script-VM execution & testing via `org.nexa:scriptmachine` (the JNI binding to the full node's actual script machine): `Initialize()`+`libnexa` (bundled in libnexakotlin-jvm, auto-extracted), `eval` bare opcodes, replay a real spend (`ScriptMachine(parentHex, childHex)` / `ScriptMachine(tx, inputIdx, utxo)` / `analyze2Tx`), assert a clean run (`scriptErr == "No error(0)"` + empty main stack), decode the constructor-time diagnostics for malformed template spends (missing hidden-args push, template-hash mismatch, "No spend"), step/breakpoint/inspect stacks (`getState`), seed synthetic stacks (`loadStacks`/`replaceStacks`), validate every input of a **multi-input** spend via the two-phase init, and check resource budgets (`getResources`/`setLimits`) — a **test-source-set** check (`testImplementation`, like NPL compilation) that a contract or complex tx executes correctly while you develop it; not a production send-path step. |
| [nexa-debugging-onchain-errors](nexa-debugging-onchain-errors/SKILL.md)   | The triage index: symptom→cause→owning-skill table for build-time, app-runtime, and on-chain (broadcast/script-verify) failures. |

## How the skills relate

- **Start here for a new project:** `nexa-project-setup` → `nexa-ktor-server-integration`.
- **A wallet your own code holds the keys to (create/restore/open/connect at startup):**
  `nexa-wallet-lifecycle-and-chain` → `nexa-transaction-construction` (to spend) /
  `nexa-identity-and-addresses` (its address types). This is distinct from the external-Wally model
  below.
- **Letting a user act with their own external Wally wallet:** `nexa-wallet-connection` →
  `nexa-identity-and-addresses` → `nexa-transaction-construction`.
- **Contract logic:** `nexa-npl-smart-contracts` (+ `nexa-locktime-cltv` for any timed rule).
- **Native tokens:** `nexa-tokens-and-groups` (+ `nexa-npl-smart-contracts` for the contract DSL
  it builds on).
- **Live UI state:** `nexa-server-state-and-flows`.
- **The front-end UI itself (look, theme, layout):** `nexa-compose-ui-design` — the Compose
  design library used to render screens; pairs with `nexa-server-state-and-flows` (the state it
  renders) and `nexa-wallet-connection` (the protocol behind its `ConnectWalletButton`).
- **Talking to your own full node (broadcast, chain/mempool reads, tests):** `nexa-rpc-node-client`
  (pairs with `nexa-transaction-construction` for the tx you submit).
- **Querying/monitoring arbitrary on-chain state without a node (watch an address, check whether an
  outpoint is spent, read token state):** `nexa-electrum-monitoring`. Use it for scripts/outpoints your
  wallet doesn't own; use the wallet's own `setOnWalletChange` for your own keys.
- **Off-chain peer-to-peer coordination over the Nexa network (no direct connection between
  parties):** `nexa-capd-messaging` — negotiate a multi-party tx, then build it with
  `nexa-transaction-construction` and watch for it with `nexa-electrum-monitoring`.
- **Which chain to develop on:** default to **testnet**; the app's chain is a single
  `DEFAULT_CHAIN` constant. Reach for **regtest** only when you need to control block production
  (force-mine, deterministic confirmations, reorg tests); **mainnet** is production only. The full
  rule — including that your local node must run the same chain you select — is in
  `nexa-wallet-lifecycle-and-chain` ("Which chain do I develop on?").
- **Testing (in your test suite) that a contract / complex tx actually executes:**
  `nexa-script-machine-testing` (replay the spend through the real script VM offline, from the test
  source set; pairs with `nexa-npl-smart-contracts` + `nexa-transaction-construction`). For full
  end-to-end testing (fees, mempool, confirmations), testnet is the default; use the regtest path in
  `nexa-rpc-node-client` when a test needs to force-mine blocks or deterministic confirmations.
- **When something breaks:** `nexa-debugging-onchain-errors` routes you to the owning skill.

## Where to find canonical sources

If you need to confirm an API, a version pin, or a specific behavior beyond what these
skills capture, go to the libraries themselves. All Nexa libraries are published to GitLab
Maven registries (not Maven Central); look up the current version of each in its registry
rather than trusting a number copied here.

- **libnexakotlin** (`org.nexa:libnexakotlin`) — chain primitives: `SatoshiScript`,
  `PayAddress`, `Bip44Wallet`, tx types, hashing/serialization. Multiplatform.
  GitLab Maven project `48545045`.
- **libnexaapp** (`org.nexa.libnexaapp:app` / `:compose` / `:server`) — server-side Ktor
  wallet sessions, the TDPP/nexid wallet protocol, the `flowConnector` reactive layer, and
  Compose UI helpers. GitLab Maven project `73565187`.
- **npl** (`org.nexa:npl`, Kotlin package `org.nexa.npl`) — the NPL DSL and `OP_PARSE`
  helpers. GitLab Maven project `82390523`. Its published test sources are the best
  worked-example reference for the compile scaffold and the typed DSL calling conventions.
- **scriptmachine** (`org.nexa:scriptmachine`, Kotlin package `org.nexa.scriptmachine`) — the
  script-template VM runtime NPL compiles against, and a JNI binding to the full node's actual
  script machine (`libnexa.so`) usable to **execute/debug** scripts and replay tx spends locally
  (see `nexa-script-machine-testing`). GitLab Maven project `46299034`. (Its README still shows the
  pre-migration `Nexa:NexaScriptMachine` / `import Nexa.ScriptMachine.*` forms — trust the
  `org.nexa.*` package declaration.)
- **nexarpc** (`org.nexa:nexarpc`) — JSON-RPC client for a Nexa full node. GitLab Maven
  project `38119368`.
- **mpthreads** (`org.nexa:mpthreads`) — multiplatform threading primitives. GitLab Maven
  project `48544966`.
- **wew** (`org.wallywallet:wew`) — the Wally Enterprise Wallet library, for servers that
  manage one or more named signing wallets. GitLab Maven project `15615113`.

For **protocol / consensus / wire-format facts** — the opcode set and their semantics, the
script-template (P2T/P2PKT/P2CAT) locking-script layout, `OP_PARSE` / `OP_PUSH_TX_STATE`
introspection field numbering, address cashaddr version bytes, the group-tokenization and
token-description-document rules, the Challenge Transaction (asset-ownership proof) format, sighash
types, CLTV/CSV, CAPD, and the P2P reject codes — the authoritative source is the **Nexa
specification at `https://spec.nexa.org`** (these skills cite the relevant pages inline). Where a
library API and the spec describe the same on-chain behavior, the spec is canonical for *what the
chain does* and the library source is canonical for *the Kotlin signature that does it*.

For transitive version pins, the published `.pom` of each artifact is authoritative — the
`nexa-project-setup` skill shows how to read one from the local Gradle cache. A running
build that compiles against the published artifacts is the strongest oracle of all; this
documentation is necessarily a step behind any of them.