# CORS: dev vs production

The `install(CORS)` block in `applicationModuleTemplate.kt` / `SKILL.md` uses `anyHost()` so the
Wasm frontend and mobile testing "just work" during development. That configuration is **not safe
for production** — it lets any website's JavaScript make credentialed requests to your server.
This file shows the locked-down production form and the reasoning. CORS is a Ktor feature, not a
Nexa one; the Nexa-specific parts are only the headers libnexaapp needs.

## Development (permissive — what the skill shows)

```kotlin
install(CORS) {
    listOf(HttpMethod.Options, HttpMethod.Get, HttpMethod.Post, HttpMethod.Put,
           HttpMethod.Delete, HttpMethod.Patch).forEach { allowMethod(it) }
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)              // REQUIRED for JSON POSTs
    allowHeader(SESSION_HEADER_COOKIE_NAME)           // libnexaapp session cookie ("NexaAppSess")
    exposeHeader(SESSION_HEADER_COOKIE_NAME)
    allowCredentials = true
    anyHost()                                          // <-- DEV ONLY
}
```

Fine on a LAN while iterating. **Never ship it.**

## Production (locked down)

```kotlin
install(CORS) {
    listOf(HttpMethod.Options, HttpMethod.Get, HttpMethod.Post).forEach { allowMethod(it) }
    // ^ only the methods you actually serve — drop PUT/DELETE/PATCH if your API doesn't use them

    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
    allowHeader(SESSION_HEADER_COOKIE_NAME)
    exposeHeader(SESSION_HEADER_COOKIE_NAME)

    // EXACT origins only — scheme + host (+ port if non-default). No wildcard, no anyHost().
    allowHost("app.example.com", schemes = listOf("https"))
    // add each additional front-end origin explicitly:
    // allowHost("admin.example.com", schemes = listOf("https"))

    allowCredentials = true        // requires specific origins above — a wildcard origin with
                                   // credentials is rejected by browsers anyway
}
```

Key rules:
- **List exact origins** with `allowHost(host, schemes = [...])`; never `anyHost()` /
  `allowOrigins { true }` in production.
- **`allowCredentials = true` is incompatible with a wildcard origin** — browsers refuse
  `Access-Control-Allow-Origin: *` together with credentials, so you must enumerate origins.
- **Only allow the methods/headers you use.** Keep `ContentType` (JSON POSTs need it) and the
  libnexaapp session cookie header; drop the rest.
- Put the server behind TLS; only enable `ForwardedHeaders`/`XForwardedHeaders` when you are
  actually behind a trusted reverse proxy (otherwise a client can spoof its origin IP).

## The WebSocket caveat (flowConnector)

CORS preflight is a **browser fetch/XHR** mechanism — browsers do **not** run the CORS
preflight on a WebSocket handshake the way they do on `fetch`. So the `/api/client/ws`
flowConnector upgrade is **not** protected by the CORS block above. If you need to restrict who can
open the WebSocket, the server must **inspect the `Origin` header on the upgrade request itself**
and reject disallowed origins — don't assume the CORS plugin gates it. Treat "configure CORS" and
"origin-check the WS upgrade" as two separate tasks.

## Related

- `applicationModuleTemplate.kt` — where this `install(CORS)` block lives.
- `SKILL.md` — the `anyHost()` "tighten in production" note and the `ContentType`-required gotcha.
- `nexa-server-state-and-flows` — the `/api/client/ws` WebSocket this caveat concerns.