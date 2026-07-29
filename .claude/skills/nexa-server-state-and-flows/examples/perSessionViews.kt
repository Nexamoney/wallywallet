/*
 * perSessionViews.kt — drop-in helpers for per-session reactive views over libnexaapp's
 * flowConnector (server side). Shows the common MutableStateFlow(initialValue) + connectFlows
 * setup, refreshing all sessions, and refreshing a session when its identity changes.
 *
 * Grounded in libnexaapp flowConnectorServer.kt (connectFlows(name, session, …), set(...),
 * register(name, session)) and session.kt (NexaAppSession.identity: MutableStateFlow<String?>).
 * See flowBindingProtocol.md for the wire protocol and SKILL.md for the model.
 */

package example

import org.nexa.libnexaapp.*
import org.nexa.libnexaapp.flowConnector
import kotlinx.coroutines.flow.MutableStateFlow

/* ----- A GLOBAL flow: one value shared by every client ------------------------------------- */

/**
 * Declare a global flow once at startup. Every connected client mirrors this value.
 * (registerLibNexaAppFlows() already registers the built-in "walletConnected" global flow.)
 */
object GlobalViews {
    // initial value is required — MutableStateFlow has no empty state
    val serverStatus = MutableStateFlow("starting")

    fun register() {
        // GLOBAL + TOCLIENT (server → client). Call ONCE — a duplicate name throws
        // "FlowConnector flow named serverStatus already exists" (see flowBindingProtocol.md).
        serverStatus.connectFlows("serverStatus", FlowDirection.TOCLIENT)
    }

    fun update(status: String) { serverStatus.value = status }   // pushes to all clients
}

/* ----- PER-SESSION views: one value per session -------------------------------------------- */

/**
 * A per-session view: each session sees its own value. Hold the flow on your AppSession subclass
 * so its lifetime matches the session, and connect it for that session.
 *
 * class MyAppSession(id: String) : NexaAppSession(id) {
 *     val cartTotal = MutableStateFlow(0L)
 * }
 */
fun <T> bindPerSession(name: String, session: NexaAppSession, flow: MutableStateFlow<T>) {
    // PER_SESSION scope is implied by passing the session; TOCLIENT pushes server→that client.
    flow.connectFlows(name, session, FlowDirection.TOCLIENT)
}

/** Push a new value to one session's view by name. */
inline fun <reified T> setSessionView(name: String, value: T, session: NexaAppSession) {
    flowConnector.aset(name, value, session)     // aset = non-suspend; use set(...) from a coroutine
}

/* ----- Refresh helpers --------------------------------------------------------------------- */

/**
 * Recompute and push a per-session view for EVERY active session. Use after a change that affects
 * all users (e.g. a price update, a new round). `compute` derives the per-session value.
 */
inline fun <reified T> refreshAllSessionViews(name: String, crossinline compute: (NexaAppSession) -> T) {
    sessionHandler?.forEachSession { session ->
        flowConnector.aset(name, compute(session), session)
    }
}

/**
 * Wire a per-session view to recompute whenever that session's wallet identity changes. Call once
 * per session (e.g. from your SessionHandler's new-session hook / onWalletConnected). The session's
 * `identity` is a real libnexaapp MutableStateFlow<String?> on NexaAppSession.
 *
 * Collecting the identity flow requires a coroutine scope tied to the session's lifetime; pass one.
 */
fun <T> refreshOnIdentityChange(
    name: String,
    session: NexaAppSession,
    scope: kotlinx.coroutines.CoroutineScope,
    compute: (identity: String?) -> T,
) {
    scope.launch {
        session.identity.collect { id ->                 // fires on each identity change (login/logout)
            flowConnector.aset(name, compute(id), session)
        }
    }
}

/*
 * Usage at startup:
 *   GlobalViews.register()                               // global flows: once
 *   // per session (in your session factory / onWalletConnected):
 *   bindPerSession("cartTotal", session, (session as MyAppSession).cartTotal)
 *   refreshOnIdentityChange("greeting", session, sessionScope) { id -> "hello ${id ?: "guest"}" }
 *
 * Reminder: register each flow NAME once. Per-session flows are distinguished by the session, not
 * by re-registering the name. See flowBindingProtocol.md (FlowScope.PER_SESSION).
 */