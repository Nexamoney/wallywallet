# flowConnector wire protocol

A wire-level description of the libnexaapp `flowConnector` binding/update messages, for debugging at
the WebSocket-frame level. Read `SKILL.md` first for the application model (global vs per-session
flows, `connectFlows`/`aConnectFlows`). Grounded in libnexaapp `flowConnectorShared.kt`,
`flowConnector.kt` (client), and `flowConnectorServer.kt` (server); those declarations are
authoritative.

## Transport

- One WebSocket per client at **`/api/client/ws`**. The session is identified by the
  **`NexaAppSess`** cookie (`SESSION_HEADER_COOKIE_NAME`), so the server can route per-session flows
  to the right client.
- Payloads are CBOR-serialized values; malformed inbound frames are caught and dropped (the server
  doesn't die on a bad frame).

## Message types (first byte)

Every frame begins with a one-byte message type:

| Constant | Byte | Meaning |
| --- | --- | --- |
| `FLOW_UPDATE2` | `0` | a value update for a bound flow (carries the flow's numeric id + the serialized value) |
| `FLOW_CONTROL_REQUEST` | `1` | client → server: request to install/bind a named flow |
| `FLOW_CONTROL_BINDING` | `2` | server → client: the binding result (the numeric id assigned to a name) |
| `NOTIFICATION_MSG` | `3` | an out-of-band app message (the `sendAppMessage` / `setAppMessageHandler` channel) |

## The bind handshake

A name (a `String` like `"walletConnected"`) is bound to a compact **numeric id** once, then all
updates reference the id rather than re-sending the name:

1. **Request** — when you `connectFlows("name", …)`, the client sends a `FLOW_CONTROL_REQUEST`
   carrying a `FlowConnectorRequest`:

   ```kotlin
   data class FlowConnectorRequest(val install: Boolean, val direction: Byte, val scope: Byte, val name: String)
   ```

   `direction` is a `FlowDirection`; `scope` is a `FlowScope` (below). `install = true` adds the
   binding.

2. **Binding** — the server replies with `FLOW_CONTROL_BINDING` carrying a `FlowConnectorBinding`:

   ```kotlin
   data class FlowConnectorBinding(val known: Boolean, val id: Int, val name: String)
   ```

   `known = true` and an `id` mean the name is now bound to that integer id for this connection.

3. **Updates** — thereafter each value change is a `FLOW_UPDATE2` frame referencing the numeric
   `id` plus the CBOR-serialized new value. The receiving side writes it into the bound
   `MutableStateFlow`.

`NOTIFICATION_MSG` frames are independent of any flow binding — they carry a `msgId: Byte` + raw
`ByteArray` for the `sendAppMessage`/`setAppMessageHandler` side channel.

## Direction and scope

```kotlin
enum class FlowDirection(val v: Byte) { BIDIRECTIONAL(0), TOCLIENT(1), TOSERVER(2) }
enum class FlowScope(val v: Byte)     { GLOBAL(0), PER_SESSION(1), PER_CONNECTION(2) }
```

- **Direction** — who may push updates. `TOCLIENT` (the default) is server→client only; `TOSERVER`
  is client→server; `BIDIRECTIONAL` both ways.
- **Scope** — how many flow instances exist. `GLOBAL` = one shared value for all clients;
  `PER_SESSION` = one per session (keyed by the `NexaAppSess` cookie); `PER_CONNECTION` = one per
  WebSocket.

A mismatch — e.g. expecting client writes on a `TOCLIENT` flow — means the update is simply not
propagated in that direction; check the `FlowDirection` you registered with.

## Duplicate-name errors (verified throw messages)

Registering the same flow name twice throws, with different messages per side — useful when
triaging a startup crash:

- **server:** `"FlowConnector flow named <name> already exists"`
- **client:** `"Registered duplicate name: <name>"`

Register each name **once** at startup (e.g. via `registerLibNexaAppFlows()` and your own
registration block), not per-request.

## Related

- `SKILL.md` — the application-level usage (`connectFlows`/`aConnectFlows`, global vs per-session,
  `setupServerConnection`, `flowConnector.start`).
- `examples/perSessionViews.kt` — per-session reactive views built on this protocol.
- `nexa-ktor-server-integration` — installs the `/api/client/ws` route (`installWalletRoutes`).