---
name: nexa-ktor-server-integration
description: "Wires a Ktor server to host a Nexa application: CORS for browser/wasmJs clients, route registration ordering, server-side wallet setup at startup, and the loadConfigFile/startercfg pattern. Use when setting up a Nexa Ktor server, configuring CORS for the browser client, deciding what goes in startercfg.json, fixing CORS preflight failures, or locating where to install wallet routes. Triggers: installWalletRoutes, Application.module, embeddedServer, startercfg.json, loadConfigFile, serverRootDir, EXTERNAL_URL, ServerConfig, AppConfig, WallyEnterpriseWallet, CORS for Wally clients, serving Compose Multiplatform Wasm from Ktor. Not for the wallet-side protocol (nexa-wallet-connection) or browser-server reactive state (nexa-server-state-and-flows)."
---

# Nexa server integration with Ktor

## When to use this skill

Trigger when a developer is wiring a Ktor server to host a Nexa application —
configuring CORS for browser clients, ordering route registrations, setting up the
server-side wallet at startup, or handling the loadConfigFile / startercfg pattern.
Concretely trigger on:

- Keywords: `installWalletRoutes`, `Application.module`, `embeddedServer`, Ktor server
  setup for Nexa, `startercfg.json`, `loadConfigFile`, `serverRootDir`, CORS for Wally
  / wasmJs clients, `host = "0.0.0.0"`, `EXTERNAL_URL`, `ServerConfig`, `AppConfig`,
  `app.cfg` (properties-style server config), `WallyEnterpriseWallet` / `wew` /
  oracle + facilitator server signing wallets,
  serving Compose Multiplatform Wasm app from Ktor.
- Tasks: "set up a Nexa Ktor server", "configure CORS for the browser client", "what
  goes in startercfg.json", "the server starts but the wallet can't reach it",
  "preflight CORS fails on my POST", "where do I install the wallet routes".
- Errors: CORS-related preflight failures in the browser console, "no session"
  responses from libnexaapp routes, ".../api/wallet/loginSvg" returns 404, server
  starts but no flowConnector connections.

**Negative triggers** — do NOT use this skill for:
- The wallet-side protocol (URI shapes, callbacks) — use `nexa-wallet-connection`.
- The browser-server reactive state — use `nexa-server-state-and-flows`.
- Tx construction, NPL — use the dedicated skills.

## Mental model

A Nexa Ktor server has three classes of HTTP routes that get installed:

1. **libnexaapp's wallet routes** (installed by `installWalletRoutes`):
   `/api/session`, `/api/client/ws` (WebSocket), the `/api/wallet/*` family
   (`connectSvg`, `connectEmbedSvg`, `connectText`, `loginSvg`, `embedSvg`, `svg`, `tdpp`,
   `assets`, `disconnect`, `logout`), `/api/asset/image`, and the wallet-facing
   `/_lp` (GET long-poll), `/_identity` (POST), and `/assets` (POST). Don't re-implement these.
   The built-in `/assets` handler is a complete implementation (proof verification against the
   trusted node, `session.assets`, client notification — `nexa-tokens-and-groups` Pattern 8);
   `/api/asset/image`, by contrast, is registered but does not currently respond with image
   bytes, so serve NFT/asset media from your own route (pattern below). Four of the
   `/api/wallet/*` routes are **browser-facing triggers** the frontend can call directly —
   `tdpp?msg=` (push any URI to the connected wallet), `assets?filter=` (kick off the whole
   asset-ownership round trip, fresh challenge included), `connectText` (the connect URI as
   text), and `disconnect` — documented in `nexa-wallet-connection` § "the built-in
   `/api/wallet/*` trigger routes".

   The real signature is
   `fun Routing.installWalletRoutes(externalUrl: String, session_handler: SessionHandler? = null, walletRoutes: WalletRoutes? = null)`.
   It **assigns the handler you pass to the global `var sessionHandler`** (in libnexaapp's
   `session.kt`); if you pass `null` it creates a default. That global is what your own route
   handlers read via `sessionHandler!!.findSession(call)` / `sessionHandler?.findSession(call)`.
   The optional third `walletRoutes` lets you override the wallet route set; you rarely need it.
   Note `installWalletRoutes` does **not** register `/tx` or `/_share` — those are yours.

2. **Wallet callbacks YOU register**: `/_share`, `/address` (address sharing — libnexaapp
   provides the `tdpp://host/share` push but expects you to write the handler), and
   `/tx` (TDPP partial-tx callback, GET).

3. **Your application routes**: typically under `/api/<feature>/...`. JSON or CBOR
   request/response bodies.

There's also a **WebSocket endpoint at `/api/client/ws`** that handles the
`flowConnector` traffic between server and browser. `installWalletRoutes` installs it.

**libnexaapp's wallet routes are a convenience, not a requirement.** The wallet protocol is plain
HTTP, and production Nexa servers exist that register every wallet-facing route themselves — their
own session store, their own `/_lp` long-poll handler, their own `/assets` verification — with no
libnexaapp dependency at all (typical when the frontend isn't Kotlin, so libnexaapp's client half
is unusable anyway — see `nexa-server-state-and-flows`). The wire contracts to implement against
are in `nexa-wallet-connection` ("Implementing the wallet-facing routes without libnexaapp"). This
skill's patterns assume the libnexaapp path; mix-and-match is fine (e.g. your own routes but
libnexakotlin's wallet/chain bootstrap unchanged).

The server hosts the **Compose Multiplatform Wasm front-end** from its `frontend/` (or
`composeApp/build/dist/wasmJs/...`) directory as static files. The `/devprodconfig.js`
endpoint is special: it serves a tiny JS file that tells the wasm bundle where to find
the server API in production (when the same Ktor instance serves both the static files
AND the API).

CORS is the source of most "API call hangs" bugs in browser clients. The default Ktor
CORS plugin blocks any POST with `Content-Type: application/json` unless you add
`allowHeader(HttpHeaders.ContentType)`. Custom response headers (e.g., `X-Login-Link`)
are invisible to browser JS unless you `exposeHeader` them.

## Setup and versions

Pin exact versions for `ktor`, `libnexaapp`, `kotlinx-coroutines`, `logback`, and
`kotlinx-serialization`, but look up the current published version of each in its
registry — see `nexa-project-setup` for the relationships that matter and the per-library
GitLab Maven URLs.

Required Ktor plugins:

```kotlin
implementation(libs.ktor.server.core)
implementation(libs.ktor.server.netty)
implementation(libs.ktor.server.content.negotiation)
implementation(libs.ktor.serialization.kotlinx.json)
implementation(libs.ktor.serialization.kotlinx.cbor)
implementation(libs.ktor.server.cors)
implementation(libs.ktor.server.forwarded.header)
implementation(libs.ktor.server.default.headers)
implementation(libs.ktor.server.host.common)
implementation(libs.ktor.server.status.pages)
implementation(libs.ktor.server.sessions)
implementation(libs.ktor.server.webjars)
implementation(libs.ktor.server.websockets)
```

## Core patterns

### A complete server entry point

```kotlin
@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package myapp

import io.ktor.http.*
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.files
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.nexa.libnexaapp.shared.SESSION_HEADER_COOKIE_NAME
import org.nexa.libnexaapp.*
import org.nexa.libnexakotlin.*
import com.ionspin.kotlin.bignum.serialization.kotlinx.bigdecimal.bigDecimalHumanReadableSerializerModule

const val API_VERSION = "1.0"
var EXTERNAL_URL = "http://YOUR_LAN_IP:8001"        // overridden by startercfg.json
// Develop on testnet by default. Switch to NEXAREGTEST only if you need to force-mine blocks /
// deterministic confirmations; NEXA for production. Whichever you pick, the node this server
// connects to (below) must be running that SAME chain. See nexa-wallet-lifecycle-and-chain
// "Which chain do I develop on?".
var DEFAULT_CHAIN = ChainSelector.NEXATESTNET

var nexaWallet: Bip44Wallet? = null

fun main() {
    loadConfigFile("startercfg.json")                  // optionally overrides EXTERNAL_URL, port, etc.

    org.nexa.libnexakotlin.SetLogFile("server.log")    // log OUTSIDE the project dir
    initializeLibNexa()
    org.nexa.libnexaapp.shared.log = { msg, loc, _ ->
        println(if (loc != null) "$loc: $msg" else msg)
    }

    val bc = blockchainFor(DEFAULT_CHAIN) {
        // Pin to a local node if you have one; otherwise omit (public seeders take over)
        APP_CONFIG.server.trustedFullNode?.takeIf { it.isNotBlank() }?.let {
            net.exclusiveNodes(setOf(it))
        }
    }
    org.nexa.libnexaapp.initBlockchain(bc, ASSET_DIR_FILE, CACHE_DIR_FILE)

    // Open or create the server's wallet (chain-bound by file).
    val walletFile = APP_CONFIG.server.walletFile ?: "serverTestnetWallet"
    nexaWallet = try { openWallet(walletFile) }
        catch (e: kotlinx.io.files.FileNotFoundException) {
            newWallet(walletFile, DEFAULT_CHAIN)
        }

    nexaWallet?.setOnWalletChange { w, txs ->
        (w as CommonWallet).clearCachedBalances()
        serverBalance.value = w.balance
        // see nexa-transaction-construction for handling txs
    }
    serverBalance.value = nexaWallet?.balance

    // Register flowConnector flows BEFORE starting Ktor.
    health.connectFlows("health")
    serverBalance.connectFlows("serverBalance")
    publicListings.connectFlows("listings")
    flowConnector.setNewSessionCallback { session ->
        if (session is AppSession) {
            session.mySales.connectFlows("mySales", session)
        }
    }

    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0",
        module = Application::module).start(wait = true)
}

fun Application.module() {
    install(WebSockets) { /* defaults */ }
    install(ForwardedHeaders)         // safe ONLY behind a trusted reverse proxy
    install(XForwardedHeaders)        // same
    install(DefaultHeaders)
    install(ContentNegotiation) {
        cbor()                         // for flowConnector and libnexaapp internals
        json(Json {
            isLenient = true
            serializersModule = bigDecimalHumanReadableSerializerModule
        })
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)               // REQUIRED for JSON POSTs
        allowHeader(SESSION_HEADER_COOKIE_NAME)
        exposeHeader(SESSION_HEADER_COOKIE_NAME)
        exposeHeader("X-Login-Link")                       // any custom headers you set
        allowOrigins { true }
        anyHost()                                          // tighten in production
        allowCredentials = true
    }

    routing {
        installWalletRoutes(EXTERNAL_URL, AppSessionHandler)

        // YOUR address-share callback (libnexaapp doesn't install this)
        post("/_share") {
            val s = sessionHandler!!.findSession(call) as? AppSession
            s?.userNexaAddress?.value = call.receiveText()
            call.respondText("ok")
        }
        post("/address") {
            val s = sessionHandler!!.findSession(call) as? AppSession
            s?.userNexaAddress?.value = call.receiveText()
            call.respondText("ok")
        }

        // YOUR /tx callback for the TDPP partial-tx flow (Wally hits this, GET not POST)
        get("/tx") {
            val cookie = call.request.queryParameters["cookie"]
            val txHex = call.request.queryParameters["tx"]
            // ... advance state ...
            call.respondText("ok")
        }

        // YOUR application routes
        installMyAppRoutes()

        // Serve the Compose Wasm front-end as static files
        get("/devprodconfig.js") {
            call.respondText("""
                export let SERVER_API_URL = "$EXTERNAL_URL";
                console.log("Server API:", SERVER_API_URL);
            """.trimIndent(),
                contentType = ContentType.Text.JavaScript,
                status = HttpStatusCode.OK)
        }
        get("/") {
            call.response.notCacheable()
            call.respondFile(java.io.File("$serverRootDir/frontend/index.html"))
        }
        static("/") {
            files("$serverRootDir/frontend")
            resources("static")
        }
    }
}

fun Routing.installMyAppRoutes() {
    get("/api/myapp/health") { call.respond(health.value) }
    // ... etc ...
}
```

### The startercfg.json pattern

Keep environment-specific values out of source. `loadConfigFile` reads them at boot:

```json
{
  "server": {
    "externalUrl": "http://192.168.1.137:8001",
    "listenPort": 8001,
    "walletFile": "starterTestnetWallet",
    "trustedFullNode": null
  }
}
```

The config OVERRIDES the in-code defaults. `externalUrl` is the single most important
field — Wally embeds it in the URIs we push, so it must be reachable from Wally's
network. **Use your LAN IP for mobile testing, not `localhost`.**

A deployment-oriented config commonly also carries a `dataDir`: libnexakotlin's file access
(wallet databases, log files) is working-directory-relative by default, and setting its JVM
global `dataDirectory` (trailing `/` required) from such a field relocates it all to a proper
data directory (e.g. under `/opt/...` for a systemd service) — see
`nexa-wallet-lifecycle-and-chain` § "Relocating wallet/data files".

`loadConfigFile`/`startercfg.json` may come from a libnexaapp starter template; if they
don't resolve against your `libnexaapp` version, the equivalent is to roll your own config
loader and call it as the first line of `main()` (pattern below).

**Where the library boundary actually is:** libnexaapp ships **no** config-file loader — there
is no `loadConfigFile`, `ServerConfig`, or `AppConfig` in the library. The only server-setup
the library provides in this area is `initBlockchain(chain, assetDir, cacheDir)` (which records
the chain/blockchain/asset-dir/cache-dir into libnexaapp's `serverCfg` globals) plus the global
`var sessionHandler`. So **both** config styles shown here — a `startercfg.json` loader and the
`AppConfig`/`app.cfg` loader below — are entirely **your** code; pick or invent whichever, and
treat the JSON/`loadConfigFile` example as illustrative app code, not a library call.

### The libnexaapp server globals (`initBlockchain` and what it sets)

The one piece of server-wide state libnexaapp *does* own is in its `serverCfg.kt`
(package `org.nexa.libnexaapp`): calling
`initBlockchain(chain: Blockchain, assetDir: File, cacheDir: File)` — as the complete entry
point above does — records four top-level globals your route handlers (and libnexaapp's own)
can read afterwards:

```kotlin
var chainSelector: ChainSelector   // = chain.chainSelector
var blockchain: Blockchain?        // the server's chain connection
var assetDataDir: File?            // permanent asset storage (NFT zips, token descriptions)
var cacheDataDir: File?            // cache storage (extracted media)
```

Call it **before** starting Ktor. Two libnexaapp subsystems silently depend on it: the built-in
`POST /assets` proof-verification handler (it reaches the trusted node via
`blockchain!!.net.getNode()` — NPE/failure if unset), and the `org.nexa.assets.assetManager`
singleton (token metadata + NFT media caching under those two directories). See
`nexa-tokens-and-groups` Patterns 8/8b for both. When a route needs an electrum channel (e.g.
for `assetManager.getTokenDesc`), reuse the connection's own — `{ blockchain!!.net.getElectrum() }`
— rather than constructing a standalone `ElectrumClient`.

To serve asset/NFT media bytes your route resolved (per `nexa-tokens-and-groups` Pattern 8b):

```kotlin
get("/api/nft/media") {
    val gid = call.parameters["group"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val media = resolveNftArtwork(GroupId(gid))          // your fn wrapping assetManager (Pattern 8b)
        ?: return@get call.respond(HttpStatusCode.NotFound)
    call.respondBytes(media.bytes, media.contentType)    // content type from the resolved filename
}
```

### Alternative config pattern: a properties-style `app.cfg`

A common alternative is to load a flat properties-style `app.cfg` through a hand-rolled
`AppConfig` object, called as the first line of `main()`:

```kotlin
fun main() {
    AppConfig.load("app.cfg")                       // must be first
    DEFAULT_CHAIN = if (AppConfig.isMainnet) ChainSelector.NEXA else ChainSelector.NEXATESTNET
    initializeLibNexa()
    SERVER_HOST = EXTERNAL_URL.removePrefix("http://").removePrefix("https://")
    // ... blockchain + DB init, then register global flows, then embeddedServer(...).start()
}

// EXTERNAL_URL etc. are computed properties backed by AppConfig:
val EXTERNAL_URL: String get() = AppConfig.externalUrl
val API_VERSION:  String get() = AppConfig.apiVersion
```

`app.cfg` is a flat `key = value` file (NOT JSON), git-ignored, created from a template.
Representative keys:

```properties
external_url        = http://192.168.1.137:7995
default_chain       = NEXATESTNET            # dev default; NEXAREGTEST for block control, NEXA for mainnet
exclusive_node      = 127.0.0.1              # must be a node running the SAME chain as default_chain
nexa_rpc_host       = http://127.0.0.1
nexa_rpc_port       = 18332                  # RPC port is per-chain: 18332 = regtest convention;
                                             # testnet nodes conventionally use 7229 (the nexarpc
                                             # library's own tests do) — read rpcport from the
                                             # node's nexa.conf and match default_chain above
nexa_rpc_user       = <user>
nexa_rpc_password   = <pass>
db_url              = jdbc:postgresql://localhost:5432/<dbname>
signing_wallet_name = <wallet>
asset_dir           = assets
cache_dir           = cache
```

Choose whichever config style your project already uses; the important invariant is the same
either way — **`external_url` must be a LAN-reachable address for the wallet, not `localhost`.**

### Server-side signing wallets: single `openWallet` vs. multiple named wallets

The complete entry point above opens one `nexaWallet` via `openWallet`/`newWallet` — the raw
libnexakotlin wallet primitive, which is all a single-signing-wallet server needs (and the only
option for a multiplatform client). A server that must *sign* under **several roles** (oracle
attestations, fee collection, treasury payouts) can instead adopt the `org.wallywallet:wew` library
(GitLab Maven project `15615113`): a **JVM wallet-management runtime** whose
`WallyEnterpriseWallet.accounts[name]` is a registry of named wallets — and crucially, **each
account is a libnexakotlin `Bip44Wallet`**, so all the create/restore/open/encrypt/sign mechanics in
`nexa-wallet-lifecycle-and-chain` still govern each one. WEW adds the *operational* layer around them:
the named-account registry, a parallel `nodes` registry of full-node RPC connections, blockchain
lifecycle (it stops/saves every account on shutdown), and a scriptable command engine behind a
chosen front-end (`CliType.Console` / `Graphical` / `Fifo` / `None`). The `Fifo` mode in particular
turns it into a controllable wallet **daemon** driven over a named pipe. **When to pick the raw
primitive vs. the WEW runtime is laid out in `nexa-wallet-lifecycle-and-chain` Pattern 8.** The wiring
for the WEW path:

```kotlin
import org.wallywallet.wew.WallyEnterpriseWallet
import org.nexa.libnexakotlin.Bip44Wallet

fun loadWallets() {
    thread { WallyEnterpriseWallet.run(WallyEnterpriseWallet.CliType.Fifo) }
    WallyEnterpriseWallet.accounts[ORACLE_WALLET_NAME]      = openWallet(ORACLE_WALLET_NAME)
    WallyEnterpriseWallet.accounts[FACILITATOR_WALLET_NAME] = openWallet(FACILITATOR_WALLET_NAME)

    // The wallet's "common identity" destination is its stable signing identity:
    val oracleDest = oracleWallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
    ORACLE_SERVER_IDENTITY_ADDRESS = oracleDest.address          // stable identity (signing)
    ORACLE_RECV_ADDR = oracleWallet.getNewAddress().toString()   // rotating receive address
}
```

Two things worth carrying away: (1) `WallyEnterpriseWallet.accounts[name]` is a registry of
named wallets, so a server can hold an oracle key and a separate fee key under independent
names; (2) the *stable* server identity is
`wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address`, while
`wallet.getNewAddress()` is a rotating receive address — the same identity-vs-payout split
described in `nexa-identity-and-addresses`, applied to the server's own wallet.

### Registering your own session handler

You almost always want a session class with your own per-session fields. Subclass
`NexaAppSession` and provide a `BasicSessionHandler` factory:

```kotlin
class AppSession(id: String) : NexaAppSession(id) {
    val userNexaAddress = MutableStateFlow("")
    val mySales = MutableStateFlow(MySalesPage())
    // ... your fields ...

    override fun onWalletConnected() {
        // ask for an address; recompute per-session views
        pushToWallet("tdpp://${EXTERNAL_URL.removePrefix("http://").removePrefix("https://")}" +
            "/share?info=address&chain=${chainToURI[DEFAULT_CHAIN]}&cookie=$id&rproto=http")
        refreshMySessionViews(this)
    }
}

object AppSessionHandler : BasicSessionHandler<AppSession>({ AppSession(it) }) {
    override fun event(event: String, session: NexaAppSession) {
        // optional: react to "connected" / "disconnected" events
    }
}
```

Pass this to `installWalletRoutes(EXTERNAL_URL, AppSessionHandler)`.

### Receiving a JSON body on a POST route

```kotlin
@Serializable
data class CreateListingRequest(val title: String, val priceNexa: Long)

routing {
    post("/api/listings/create") {
        val session = sessionHandler?.findSession(call) as? AppSession
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "no session")
        val req = call.receive<CreateListingRequest>()
        if (req.title.isBlank() || req.priceNexa <= 0) {
            return@post call.respond(HttpStatusCode.BadRequest, "invalid")
        }
        // ... do work, then ...
        call.respond(HttpStatusCode.OK, mapOf("id" to id))
    }
}
```

Browser client sends:

```kotlin
postToServer("/api/listings/create",
    Json.encodeToString(CreateListingRequest("My secret", 100L)),
    ContentType.Application.Json) { resp ->
    // resp.bodyAsText(), resp.status, etc.
}
```

The CORS block must include `allowHeader(HttpHeaders.ContentType)` for this to work in
the browser; otherwise the preflight OPTIONS is rejected and the actual POST never
fires.

### Serving the Wasm frontend from the same Ktor instance

```kotlin
// build.gradle.kts (server module):
distributions {
    main {
        contents {
            from(findFrontend().path) {       // composeApp/build/dist/wasmJs/{development,production}Executable
                into("frontend")
            }
        }
    }
}

// In Application.kt's routing block:
get("/") { call.respondFile(java.io.File("$serverRootDir/frontend/index.html")) }
static("/") {
    files("$serverRootDir/frontend")
    resources("static")
}
```

For development with hot reload, the wasm bundle typically runs from
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun` on a different port (e.g. 8080).
The server's `/devprodconfig.js` route is used in production builds where everything is
served from one port.

The `/devprodconfig.js` indirection is **optional** — one valid approach when a single Ktor
instance serves both the Wasm bundle and the API. Alternatively, the client can hardcode or
otherwise configure its `SERVER_URL_API` directly, in which case you don't need this route at
all. Pick the indirection when you want a single deployed artifact to work in multiple
environments without recompiling the frontend.

## Common mistakes and anti-patterns

### Forgetting `allowHeader(HttpHeaders.ContentType)` in CORS

**Wrong**:
```kotlin
install(CORS) {
    allowMethod(HttpMethod.Post)
    allowHeader(SESSION_HEADER_COOKIE_NAME)
    // ContentType missing
}
```
*Browser sends `Content-Type: application/json` on the POST → preflight OPTIONS hits
the server → server replies with allowed headers list lacking `Content-Type` →
browser blocks the actual POST. The fetch promise rejects with a generic TypeError;
your UI hangs forever if you don't handle exceptions.*

**Right**:
```kotlin
install(CORS) {
    allowMethod(HttpMethod.Post)
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
    allowHeader(SESSION_HEADER_COOKIE_NAME)
    // ...
}
```

### Not exposing custom response headers

**Wrong**: setting `call.response.header("X-Login-Link", url)` and expecting browser JS
to read it. Without `exposeHeader("X-Login-Link")` in CORS, the browser hides the
header from JS.

**Right**: every custom response header you set must be in `exposeHeader(...)`.

### Binding to `localhost` / `127.0.0.1`

**Wrong**:
```kotlin
embeddedServer(Netty, port = 8001).start(wait = true)   // defaults to localhost on some setups
```

**Right**: explicit `host = "0.0.0.0"`. The server must be reachable from outside the
local machine (Wally is on a phone).

### Stale `externalUrl` in startercfg.json

**Wrong**: `externalUrl: "http://192.168.2.11:8001"` while the machine's actual LAN IP
is `192.168.1.137`. The QR code embeds an unreachable URL → wallet says "connecting"
forever.

**Right**: keep `externalUrl` in sync with your current LAN IP. For mobile dev, a
quick `ifconfig | grep inet` to verify is worth its weight in time saved.

### Registering wallet routes AFTER your own routes

**Wrong**:
```kotlin
routing {
    installMyAppRoutes()
    installWalletRoutes(EXTERNAL_URL, AppSessionHandler)
}
```
*Usually still works, but if your own routes happen to match libnexaapp's paths (e.g.
`/api/wallet/...`), the order determines which handler wins. Convention is libnexaapp
first.*

**Right**:
```kotlin
routing {
    installWalletRoutes(EXTERNAL_URL, AppSessionHandler)
    // then any /api/<feature>/... routes
    installMyAppRoutes()
    // static last so it doesn't shadow API routes
    static("/") { files("$serverRootDir/frontend") }
}
```

### Calling `connectFlows("name")` after the server has started

**Wrong**:
```kotlin
embeddedServer(Netty, port = 8001).start(wait = true)
publicListings.connectFlows("listings")     // unreachable: start(wait=true) never returns
```

**Right**: register all global flows before `start(wait = true)`.

### Putting `ForwardedHeaders` plugin behind an untrusted edge

`install(ForwardedHeaders)` and `XForwardedHeaders` trust the `X-Forwarded-*` headers
from incoming requests. Behind a reverse proxy you control, that's correct. If you
expose the server directly to the internet, *don't* install them — a client can spoof
their apparent IP / scheme.

### Reusing wallet files across chains

**Wrong**: `walletFile = "myWallet"` while testing both mainnet and testnet — the
wallet file is chain-bound; opening a mainnet wallet on testnet won't work cleanly
(wrong derivations, mixed addresses).

**Right**: separate files per chain: `myMainnetWallet`, `myTestnetWallet`,
`myRegtestWallet`.

### Logging the wallet file inside the project directory

**Wrong**:
```kotlin
org.nexa.libnexakotlin.SetLogFile("./logs/wallet.log")     // inside project dir
```
*Compose Multiplatform Wasm dev server watches the project dir and triggers a full
recompile every time the log file changes. Boots into a recompile loop.*

**Right**: write logs to an OS-level temp dir or somewhere outside the project tree:

```kotlin
org.nexa.libnexakotlin.SetLogFile(java.io.File(System.getProperty("user.home"), ".myapp/wallet.log").path)
```

### Not handling missing session in route handlers

**Wrong**:
```kotlin
post("/api/listings/create") {
    val session = sessionHandler!!.findSession(call) as AppSession   // NPE if no session header
    /* ... */
}
```

**Right**:
```kotlin
val session = sessionHandler?.findSession(call) as? AppSession
    ?: return@post call.respond(HttpStatusCode.Unauthorized, "no session")
```

`findSession` (not `findCreateSession`) returns null instead of throwing when no
session cookie is present.

## Security considerations

- **Pin CORS origins in production.** `anyHost()` and `allowOrigins { true }` are
  fine for development but let any web origin script your server when allowing
  credentials. Replace with `allowHost("yourdomain.com", schemes = listOf("https"))`.

- **Don't install `ForwardedHeaders` without a trusted edge.** A direct internet-facing
  Ktor server with `ForwardedHeaders` lets any client claim any source IP for log
  purposes.

- **Disable `anyHost()` for `Access-Control-Allow-Credentials: true`** in production.
  The combination is unsafe — any origin can make authenticated requests on the user's
  behalf.

- **The Compose Wasm static directory is served verbatim.** Don't put anything secret
  in there. The `serverRootDir/frontend` directory becomes browser-readable.

- **HTTP, not HTTPS, in startercfg.json is fine for LAN dev, NOT prod.** Production
  servers must use TLS (`https://`). The wallet's signing material and session cookies
  travel over this connection.

- **`/devprodconfig.js`** is served as text/javascript. If you template anything into
  it (e.g., from configuration), HTML-escape carefully — a malicious config could
  inject JS into every visitor's page.

- **`installWalletRoutes` registers everything libnexaapp needs at the wallet
  protocol level**. Don't try to limit access to those routes with custom auth — Wally
  is the consumer, and locking it out breaks the protocol.

## Related skills and references

- `nexa-wallet-lifecycle-and-chain` — what `openWallet`/`newWallet`/`recoverWallet`,
  `destinationFor(COMMON_IDENTITY_SEED)`, and the chain connection actually do; the
  libnexakotlin wallet bootstrap this server's `main()` calls. The `org.wallywallet:wew`
  multi-named-wallet pattern above sits on top of that single-wallet lifecycle.
- `nexa-wallet-connection` — what `installWalletRoutes` and the `/_share`, `/tx`
  callbacks are doing.
- `nexa-server-state-and-flows` — the `flowConnector` setup that pairs with
  `install(WebSockets)`.
- `nexa-project-setup` — the gradle dependency block this skill assumes.

### Supporting files in this folder

- `applicationModuleTemplate.kt` — drop-in `Application.module()` with CORS, content negotiation
  (CBOR + JSON), websockets, `installWalletRoutes`, the app `/_share` and `/tx` callbacks, route
  ordering, and frontend serving.
- `startercfg.json.template` — full config schema with per-field documentation (note: config
  loading is app code; libnexaapp ships no loader).
- `corsProdVsDev.md` — the permissive dev config vs a locked-down production config, plus the
  WebSocket-origin caveat (CORS preflight doesn't gate the WS upgrade).