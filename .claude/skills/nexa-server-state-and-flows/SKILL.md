---
name: nexa-server-state-and-flows
description: "Implements reactive server-to-browser state with libnexaapp's flowConnector, pushing live updates to connected clients without manual polling. Use when streaming server changes to the browser (or vice versa), building a global catalogue every client sees, a per-user view following wallet identity, or UI that updates when a tx confirms. Triggers: flowConnector, connectFlows/aConnectFlows, MutableStateFlow, setNewSessionCallback, FlowConnector, flowConnector.register/set/aset, WebSocket client-server state, sendNotification/NotificationDataType/walletOwnsAssetHandler, setAppMessageHandler/sendAppMessage. Not for the wallet-server long-poll /_lp channel (nexa-wallet-connection), plain HTTP request/response (use Ktor), or persistence (flowConnector is in-memory only)."
---

# Reactive state with libnexaapp's flowConnector

## When to use this skill

Trigger when a developer needs server state to flow to a connected browser without
manual polling, or vice versa. Concretely trigger on:

- Keywords: `flowConnector`, `connectFlows`, `aConnectFlows`, `MutableStateFlow`,
  `setNewSessionCallback`, `FlowConnector`, `WebSocket` (in the context of
  client↔server state), reactive UI state from a NEXA server.
- Tasks: "push live updates to the browser when X changes on the server", "global
  catalogue that every connected client sees", "per-user view that follows wallet
  identity", "make the UI update when a tx confirms", "wallet status indicator".
- Code touching: `flowConnector.register`, `flowConnector.set`, `flowConnector.aset`,
  `aConnectFlows`, `walletConnected.aConnectFlows("walletConnected")`,
  `sendNotification` / `NotificationDataType` / `walletOwnsAssetHandler` /
  `unsolicitedAppSpecificDataHandler`, `setAppMessageHandler` / `sendAppMessage`,
  the client `flowConnector.connected` socket-health flow, the client HTTP helpers
  (`setupServerConnection`, `getFromServer` / `aGetFromServer` / `asGetFromServer` /
  `postToServer`, `sessionId`, `coScope`).

**Negative triggers** — do NOT use this skill for:
- Wallet↔server long-poll (`/_lp`) — that's a separate channel; use
  `nexa-wallet-connection`.
- HTTP request/response patterns — use plain Ktor.
- Database synchronization — `flowConnector` is in-memory only; persistence is your
  problem.

(For a **non-Kotlin frontend** — Vue/React/JS — `flowConnector` is unavailable on the client side;
this skill still applies for the "When the frontend isn't Kotlin" pattern below, which covers the
hand-rolled WebSocket channel such apps use instead.)

## Mental model

`flowConnector` is libnexaapp's React-style state propagation built on a single
WebSocket between each browser tab and the server. It lets you mirror server-side
`MutableStateFlow<T>` values onto client-side `MutableStateFlow<T>` of the same type.
Changes flow automatically — set a value on one side, the other side observes it.

It is **completely independent** from the wallet long-poll protocol. The WebSocket is
between the *browser* and the server. The wallet doesn't see it.

There are three scopes for a flow:

1. **Global** — one server-side `MutableStateFlow`, broadcast to every connected
   browser. Use for catalogues, system health, public state.
2. **Per-session** — one server-side `MutableStateFlow` per `NexaAppSession`, broadcast
   only to that session's browser tabs. Use for "my activity", per-user views.
3. **Per-socket** — one per WebSocket connection. Rarely needed; most use cases want
   per-session so multiple tabs in the same browser see the same view.

Updates are CBOR-serialized over the WebSocket. The values you put in
`MutableStateFlow<T>` must be `@Serializable` (kotlinx.serialization) on both sides, and
**the type must exist in the `shared` module** so client and server agree on the schema.

You can also flow in the other direction (client → server) using
`FlowDirection.TOSERVER` or `FlowDirection.BIDIRECTIONAL`. Most apps only use the
default `TOCLIENT`.

The mental shortcut: **treat `flowConnector` like Redux/Recoil that magically syncs
across the network for you**. The cost is a tiny bit of plumbing at registration time;
the benefit is you never write `GET /api/foo/refresh` endpoints again.

## Setup and versions

You need `libnexaapp` (provides `flowConnector` on both server and client sides, and
`connectFlows` / `aConnectFlows` extensions). Pin per `nexa-project-setup`.

Required imports on the server:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexaapp.flowConnector       // the global server-side singleton
import org.nexa.libnexaapp.connectFlows        // extension on MutableStateFlow
```

Required imports on the client (composeApp/commonMain):

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexaapp.client.flowConnector      // global client-side singleton
import org.nexa.libnexaapp.client.aConnectFlows      // extension on MutableStateFlow
import org.nexa.libnexaapp.client.registerLibNexaAppFlows
import org.nexa.libnexaapp.client.setupServerConnection
```

Required WebSocket and ContentNegotiation:

```kotlin
install(WebSockets) { /* defaults are fine */ }
install(ContentNegotiation) {
    cbor()
    json(/* ... */)
}
```

The wallet routes' WebSocket endpoint is installed by `installWalletRoutes` and is at
`/api/client/ws`. The client's `flowConnector.start(baseUrl)` connects to it.

## Core patterns

### A complete server-side wiring

```kotlin
// shared/src/commonMain/kotlin/apimodels.kt
@Serializable
data class Health(val connected: Boolean, val database: String, val apiVersion: String)

@Serializable
data class ListingsPage(val listings: List<Listing> = emptyList())

@Serializable
data class MySalesPage(val sales: List<MySaleView> = emptyList())

// server-side, top-level
val health = MutableStateFlow(Health(true, "UP", "1.0"))
val publicListings = MutableStateFlow(ListingsPage())

class AppSession(id: String) : NexaAppSession(id) {
    val mySales = MutableStateFlow(MySalesPage())            // per-session
}

fun main() {
    // ... server setup ...

    // Register globals BEFORE starting Ktor so initial-state push has them ready:
    health.connectFlows("health")
    publicListings.connectFlows("listings")

    // Per-session flows are registered in the new-session callback:
    flowConnector.setNewSessionCallback { session ->
        if (session is AppSession) {
            session.mySales.connectFlows("mySales", session)
            // ... any other per-session flows ...
            refreshMySalesView(session)            // seed an initial value
        }
    }

    embeddedServer(Netty, port = 8001, host = "0.0.0.0", module = Application::module).start(wait = true)
}
```

### A complete client-side wiring (commonMain)

```kotlin
// composeApp/src/commonMain/kotlin/model.kt
val health = MutableStateFlow(Health(false, "", ""))
val publicListings = MutableStateFlow(ListingsPage())
val mySales = MutableStateFlow(MySalesPage())

// composeApp/src/commonMain/kotlin/serverProtocol.kt
fun ConnectCommonFlows() {
    registerLibNexaAppFlows()                              // walletConnected, etc.
    health.aConnectFlows("health")
    publicListings.aConnectFlows("listings")
    mySales.aConnectFlows("mySales")
}

fun SetupApp() {
    ConnectCommonFlows()
    setupServerConnection(SERVER_URL_API) {
        // optional Ktor client config
    }
    coScope.launch { flowConnector.start(SERVER_URL_API) }
}

@Composable
fun App() {
    NexaApp {
        val listings = publicListings.collectAsState().value
        val sales = mySales.collectAsState().value
        // ... render the lists; they update automatically as the server sets new values
    }
}
```

### Pushing an update from the server

You don't need a special API. Just set the value:

```kotlin
publicListings.value = ListingsPage(newSnapshot)
```

The connector observes the change and broadcasts a CBOR-encoded update to every
subscribed client.

One property worth exploiting: `MutableStateFlow` **skips assignments equal to the current
value**, so a server-side poll loop that recomputes a status object every few seconds and assigns
it unconditionally only emits over the wire when something actually changed — a cheap way to run
a live status indicator (chain height, peer connectivity) without hand-rolled change detection.
(Make the flowed type a `data class` so equality is structural.)

### Pushing a per-session update

```kotlin
fun refreshMySalesView(session: AppSession) {
    val identity = session.identity.value ?: return
    val mine = listings.values.filter { it.sellerIdentity == identity }
    session.mySales.value = MySalesPage(mine.map { it.toView() })
}

// When a listing changes, refresh the affected sessions:
fun refreshAllSessionViews() {
    sessionHandler?.forEachSession { s ->
        if (s is AppSession) refreshMySalesView(s)
    }
}
```

### Reacting to wallet-switch (re-filter per-session views)

Per-session views typically filter by `session.identity.value`. When the user
disconnects and connects a different wallet, identity changes — your views must
recompute. Override `onWalletConnected`:

```kotlin
class AppSession(id: String) : NexaAppSession(id) {
    val mySales = MutableStateFlow(MySalesPage())

    override fun onWalletConnected() {
        super.onWalletConnected()
        refreshMySalesView(this)            // re-target views to the new identity
    }
}
```

### Functional (push-only) flows

If you don't want to wire a `MutableStateFlow` but just want to *push* a value to a
session occasionally:

```kotlin
// Server, in setNewSessionCallback:
flowConnector.register("notice", session)                 // no MSF, just a named channel

// Anytime later, on the server:
flowConnector.aset("notice", "you have a new buyer!", session)
```

Note: client side must still register a `MutableStateFlow<String>` (or whatever) under
the same name via `aConnectFlows`. The server-side `register(name, session)` (no MSF)
just declares "I will push values here later."

Since a recent release, the server-side connector also **caches the last value pushed to a
functional flow** (per session, and a separate global slot) and **replays it to a newly-binding
client**. So a browser tab that connects *after* your last `aset(...)` still receives that value
at bind time instead of showing nothing until the next push — this is how libnexaapp's own
`walletConnected` state survives a page reload. Consequence: don't treat a functional-flow push
as strictly ephemeral/one-shot; a late-joining tab in the same session will see the last one
again. If a value must be acted on exactly once, carry an id in the payload and dedupe on the
client.

### Beyond flows: notifications and app messages on the same WebSocket

The flow connector's WebSocket also carries two lower-level channels you can use without
defining a named flow:

- **Notifications (server → client, typed):** on the server, a client socket exposes
  `sendNotification(type: Byte, data: ByteArray)`; the type space is
  `NotificationDataType` (`org.nexa.libnexaapp.shared`) — `WALLET_HAS_ASSET` is used by
  libnexaapp's own `/assets` verification flow (each verified asset arrives as a CBOR
  `TricklePayBinaryAssetInfo`; see `nexa-tokens-and-groups` Pattern 8), and values from
  `APP_SPECIFIC` up are yours. On the client, set
  `flowConnector.walletOwnsAssetHandler = { assetInfo -> … }` for the former and
  `flowConnector.unsolicitedAppSpecificDataHandler = { bytes -> … }` for the latter.
- **App messages (your own binary frames):** `flowConnector.setAppMessageHandler { msgType,
  data -> … }` receives frames whose first byte is a message id you choose — it **must be
  ≥ 0x80** (all values below are reserved for flow-connector messages); send with the
  connector's `sendAppMessage`. Recommended over opening a second WebSocket, because the
  library's connection state then stays accurate.

Related: the client `FlowConnector` exposes `connected: MutableStateFlow<Boolean>` — whether
the underlying WebSocket is currently up — which is the right source for a "server reachable"
indicator (distinct from `walletConnected`, which is about the *wallet* long-poll).

One hygiene rule for notification-fed client state: whatever store your
`walletOwnsAssetHandler` (or app-message handler) accumulates into is **yours to reset** — the
server does not re-push past notifications on reconnect, and nothing clears the store when the
wallet disconnects. Watch `walletConnected` and clear the accumulated state (and any caches
keyed off it) when it flips false — the full asset-list version of this rule is in
`nexa-tokens-and-groups` Pattern 8.

### The client's HTTP helpers (`org.nexa.libnexaapp.client`, `serverAccess.kt`)

The snippets in these skills call `getFromServer`/`postToServer` as if they were obvious — they
are libnexaapp's client-side HTTP layer, and they share the session identity with the
flowConnector socket (a `SessionHeaderPlugin` attaches the session id header to every request, so
your Ktor routes' `findSession(call)` sees the same session as the WebSocket). The surface:

```kotlin
// One-time setup (before any call; also before flowConnector.start — the skills' SetupApp shows this):
setupServerConnection(serverPrefix: String? = null, oneSessionPerBrowser: Boolean = true,
                      extraConfig: HttpClientConfig<*>.() -> Unit): HttpClient
// customServerConnection(...) is the variant that returns a configured client without installing
// it as the global session client; genericHttpClient(...) skips the session plumbing entirely.

// GETs against the configured server prefix:
suspend fun getFromServer(cmd: String): String?                       // body as text, null on failure
suspend fun getFromServer(cmd: String, sess: HttpClient? = ..., handler: suspend (HttpResponse) -> Unit)
fun aGetFromServer(cmd: String, handler: (HttpResponse) -> Unit)      // fire-and-forget, non-suspend caller
fun asGetFromServer(cmd: String, handler: suspend (HttpResponse) -> Unit)  // same, suspend handler (binary bodies: resp.body<ByteArray>())
fun asGet(urlString: String, handler: suspend (HttpResponse) -> Unit) // absolute URL (not server-relative)

// POSTs (text or streamed):
suspend fun <T> postToServer(cmd: String, contents: String, ctype: ContentType = ContentType.Text.Plain,
                             sess: HttpClient? = ..., handler: suspend (HttpResponse) -> T?): T?
```

Adjacent globals: `coScope` (the client's `MainScope` the skills launch from), `sessionId` (the
browser session id string; `newSessionId()` regenerates), `sessionHttpClient` (the installed
client), and the `timeoutInMs` / `maxReadSize` knobs. Confirm exact shapes against the resolved
artifact (`nexa-project-setup` § "Verifying API signatures") — this family is client convenience
API and can grow. (The similarly-named `getWalletAssets(filter)` in the same file is an
unimplemented stub — see `nexa-tokens-and-groups`; trigger the asset flow via
`GET /api/wallet/assets` instead.)

### When the frontend isn't Kotlin (Vue/React/plain JS)

`flowConnector`'s client half is a **Kotlin (KMP) library** — a JavaScript/TypeScript frontend
(Vue, React, plain browser JS) cannot bind it, so a Nexa app with a non-Kotlin frontend hand-rolls
the browser↔server channel instead: a plain WebSocket the client opens with its session id, and a
small JSON message envelope (a `messageType` + payload) both sides agree on. The wallet protocol is
unaffected — TDPP/nexid is plain HTTP with no client-library dependency (`nexa-wallet-connection`),
so only this browser-sync layer changes. Ground rules learned from production hand-rolled versions:

- **A session has *many* sockets** (one per tab). Keep a per-session connection list, add/remove on
  socket open/close, and only treat the session's browser as "gone" when the list is empty —
  don't let one tab's close event log out the others.
- **Serialize writes per socket** (a send mutex or single writer coroutine) and snapshot the
  connection list before broadcasting, so a concurrent connect/disconnect doesn't race the loop.
- **Keepalive belongs to you** — a ping/pong text frame on an interval, and an idle timeout on the
  server's frame loop, replace the liveness the library layer would otherwise provide.
- Push wallet-state transitions (connected / disconnected / *silent*) to every tab as explicit
  messages; the browser can't see the wallet long-poll, so the server is its only source.

If you control both ends and both are Kotlin, prefer `flowConnector` — the above is what it does
for you (plus typed CBOR state sync that a hand-rolled JSON channel gives up).

## Common mistakes and anti-patterns

### Forgetting to register globals BEFORE `start(wait=true)`

**Wrong**:
```kotlin
embeddedServer(Netty, port = 8001, /* ... */).start(wait = true)
health.connectFlows("health")           // unreachable after start(wait=true)
```

**Right**: register all globals first, then start the engine. `connectFlows` is a
non-suspending registration call and must happen before clients connect.

### Putting non-serializable types in a flowed `MutableStateFlow`

**Wrong**:
```kotlin
data class Listing(val id: String, val script: SatoshiScript)   // SatoshiScript is not @Serializable
val flow = MutableStateFlow(Listing("a", SatoshiScript(...)))
flow.connectFlows("listings")
```
*At first update, CBOR encoding throws `SerializationException`. The flow stalls and
clients silently stop receiving.*

**Right**: keep the wire model in `shared/`, with only `@Serializable` fields:

```kotlin
// shared/src/commonMain/kotlin/apimodels.kt
@Serializable
data class Listing(val id: String, val scriptHex: String)   // hex string, not raw script
```

### Filtering per-session views by a rotating field

**Wrong**:
```kotlin
session.mySales.value = MySalesPage(
    listings.values.filter { it.sellerAddress == session.userNexaAddress.value }
)
```
*`userNexaAddress` rotates; views break the moment the user spends from their wallet.*

**Right**: filter by `session.identity.value` (the stable login identity). See
`nexa-identity-and-addresses`.

### Registering per-session flows globally

**Wrong**:
```kotlin
val mySales = MutableStateFlow(MySalesPage())        // top-level singleton
mySales.connectFlows("mySales")                       // global → every client sees same value
```

**Right**: per-session state belongs on the session object, registered per-session:

```kotlin
class AppSession(id: String) : NexaAppSession(id) {
    val mySales = MutableStateFlow(MySalesPage())
}
flowConnector.setNewSessionCallback { session ->
    if (session is AppSession) {
        session.mySales.connectFlows("mySales", session)     // 2nd arg = session scope
    }
}
```

### Calling the client-side `aConnectFlows` before `setupServerConnection`

**Wrong**:
```kotlin
fun App() {
    mySales.aConnectFlows("mySales")                  // before HttpClient is configured
    SetupApp()
}
```

**Right**: setup first, then register:
```kotlin
fun App() {
    SetupApp()                                         // calls ConnectCommonFlows internally
}

fun SetupApp() {
    ConnectCommonFlows()                               // registers all aConnectFlows
    setupServerConnection(SERVER_URL_API) { /* ... */ }
    coScope.launch { flowConnector.start(SERVER_URL_API) }
}
```

### Registering two different MutableStateFlows under the same name

**Wrong**:
```kotlin
health.connectFlows("status")
serverBalance.connectFlows("status")                  // throws IllegalArgumentException
```
*Server-side, a duplicate name throws `IllegalArgumentException("FlowConnector flow named
status already exists")`; the client-side `aConnectFlows`/register path throws
`IllegalArgumentException("Registered duplicate name: status")`. Either way it's a coding
error — names are the wire identifiers and must be unique on each side.*

**Right**: use distinct names. The name is the wire identifier; clients on the other
side must agree.

### Mutating the value in-place

**Wrong**:
```kotlin
mySales.value.sales.add(newSale)                      // adds to the existing list reference
mySales.value = mySales.value                          // assigning the same reference -- no change emitted
```
*MutableStateFlow only fires emissions on reference change (or value-equality change),
so mutating-in-place doesn't propagate.*

**Right**: always assign a new object:

```kotlin
mySales.value = MySalesPage(mySales.value.sales + newSale)
```

### Holding `flowConnector` references across module boundaries

The `flowConnector` global is module-scoped: the libnexaapp **server** `flowConnector`
lives at `org.nexa.libnexaapp.flowConnector`, and the **client** one lives at
`org.nexa.libnexaapp.client.flowConnector`. Importing the wrong one in shared code
silently fails. Always check the package on the import line.

## Security considerations

- **Per-session flows are scoped by session id, not by identity.** If two browsers share
  the same session cookie (e.g., via cookie theft), they see the same per-session view.
  Treat per-session data as session-scoped, not user-scoped, for sensitive material.
- **Don't put secrets in global flows.** `connectFlows("name")` without a session
  parameter broadcasts to every connected client. Anything you put there is effectively
  public to all logged-in users.
- **The WebSocket carries unencrypted CBOR over `ws://`** when your server is HTTP.
  Use HTTPS in production so the WebSocket upgrades to `wss://`.
- **CBOR deserialization is permissive on unknown fields** by default, but throws on
  type mismatches. A misbehaving client could send a malformed flow update (when
  `flowDirection = TOSERVER` or `BIDIRECTIONAL`). The setter wraps decoding in
  try/catch and silently drops; you don't need extra validation, but you also don't
  get a notification of the malformed update.
- **The CBOR encoder used** (`Cbor { serializersModule = ... }`) does not currently
  enable any polymorphism. If your flowed type is a sealed class or has polymorphic
  fields, you'll need to register the serializers module appropriately.

## Related skills and references

- `nexa-wallet-connection` — the wallet long-poll is a completely separate channel.
  `flowConnector` is for browser↔server; the wallet protocol is for wallet↔server.
- `nexa-identity-and-addresses` — most per-session filters key off `session.identity`,
  which this skill assumes you've populated.
- `nexa-ktor-server-integration` — Ktor `install(WebSockets)` is required; CORS rules
  apply to the WebSocket upgrade too.
- `nexa-transaction-construction` — to drive a flow off on-chain activity ("update the UI
  when a tx confirms"), the server-side signal comes from `setOnWalletChange` there; set
  your flow's `MutableStateFlow.value` from that callback (and respect its 0-conf /
  confirmation-depth caveat before treating a payment as settled).

### Supporting files in this folder

- `flowBindingProtocol.md` — wire-level description of the binding/update messages (the message-type
  bytes, the request→binding→update handshake, `FlowDirection`/`FlowScope`, the verified
  duplicate-name throw messages), for debugging at the WebSocket frame level.
- `examples/perSessionViews.kt` — drop-in helpers (`refreshAllSessionViews()`,
  `refreshOnIdentityChange()`, global vs per-session `connectFlows` setup).