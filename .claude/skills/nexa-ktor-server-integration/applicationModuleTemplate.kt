/*
 * applicationModuleTemplate.kt — a drop-in Ktor Application.module() for a Nexa app server, with
 * CORS, content negotiation (CBOR + JSON), WebSockets, libnexaapp wallet routes, the app callbacks
 * libnexaapp does NOT install (/_share, /tx), and Wasm frontend serving — in the right order.
 *
 * Grounded in libnexaapp routes.kt (installWalletRoutes), session.kt (NexaAppSession /
 * BasicSessionHandler / sessionHandler), and flowConnectorShared.kt (SESSION_HEADER_COOKIE_NAME).
 * See SKILL.md for the full discussion; corsProdVsDev.md to lock down CORS for production.
 *
 * Replace EXTERNAL_URL / SERVER_PORT / serverRootDir with your config values (see
 * startercfg.json.template) — do NOT hardcode them; externalUrl in particular must be reachable
 * from the wallet's network (your LAN IP for mobile testing, not localhost).
 */

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.websocket.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.nexa.libnexaapp.*   // installWalletRoutes, NexaAppSession, BasicSessionHandler, sessionHandler, SESSION_HEADER_COOKIE_NAME

// --- your app's session type: extend NexaAppSession with app-level fields ---
class AppSession(id: String) : NexaAppSession(id) {
    // app-level field (NOT a library API) — populated from your /_share callback:
    val userNexaAddress = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    // The library clears only ITS OWN state on disconnect — clear your app fields in BOTH
    // disconnect paths (explicit disconnect AND browser abandonment), or a stale address
    // lingers when a different wallet connects on the same session (nexa-wallet-connection).
    override fun disconnectWallet() { super.disconnectWallet(); userNexaAddress.value = null }
    override fun handleAbandoned()  { super.handleAbandoned();  userNexaAddress.value = null }
}
val appSessionHandler = BasicSessionHandler { id -> AppSession(id) }

fun startServer(externalUrl: String, port: Int) {
    // Register flowConnector flows BEFORE starting Ktor (see nexaServerStateAndFlows).
    registerLibNexaAppFlows()
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(externalUrl) }.start(wait = true)
}

fun Application.module(externalUrl: String) {
    install(WebSockets)                       // required for the flowConnector /api/client/ws channel
    install(ForwardedHeaders)                 // ONLY safe behind a trusted reverse proxy
    install(XForwardedHeaders)
    install(DefaultHeaders)
    install(ContentNegotiation) {
        cbor()                                // flowConnector + libnexaapp internals use CBOR
        json(Json { isLenient = true })       // your JSON APIs
    }
    install(CORS) {
        listOf(HttpMethod.Options, HttpMethod.Get, HttpMethod.Post, HttpMethod.Put,
               HttpMethod.Delete, HttpMethod.Patch).forEach { allowMethod(it) }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)              // REQUIRED for JSON POSTs
        allowHeader(SESSION_HEADER_COOKIE_NAME)
        exposeHeader(SESSION_HEADER_COOKIE_NAME)
        allowCredentials = true
        anyHost()                                          // DEV ONLY — lock down for prod (corsProdVsDev.md)
    }

    routing {
        // 1) libnexaapp wallet routes (installs /api/wallet/*, /api/client/ws, /_lp, /_identity,
        //    /assets, /api/asset/image). It also assigns the handler to the global `sessionHandler`.
        installWalletRoutes(externalUrl, appSessionHandler)

        // 2) Callbacks libnexaapp does NOT install — these are YOUR app code:
        post("/_share") {                                  // wallet shares its address here
            (sessionHandler!!.findSession(call) as? AppSession)?.userNexaAddress?.value = call.receiveText()
            call.respondText("ok")
        }
        get("/tx") {                                       // TDPP partial-tx completion (GET, not POST)
            val cookie = call.request.queryParameters["cookie"]
            val txHex  = call.request.queryParameters["tx"]
            // No `tx` param + resultcode=300 = the USER REJECTED the request — cancel the pending
            // operation for this cookie instead of treating it as malformed.
            // Guard against duplicate GETs: the wallet may hit /tx more than once (idempotency —
            // SKILL.md / nexaWalletConnection). Advance state only once per cookie.
            // The wallet PARSES this body: keep success bodies free of "error"/"invalid"/"rejected"
            // (those tokens make it show the user a failure); reply exactly "unknown session" for a
            // dead cookie. See nexa-wallet-connection.
            call.respondText("ok")
        }

        // 3) your application's own API routes
        // installMyAppRoutes()

        // 4) frontend serving LAST (the catch-all static handler must not shadow API routes above)
        staticFiles("/", java.io.File("frontend"))         // serve the Wasm/Compose frontend
    }
}