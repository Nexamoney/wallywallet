/*
 * qrRouteTemplate.kt — a drop-in Ktor route that serves a CLICKABLE connect/login QR for the Wally
 * wallet, applying the XML-escape workaround needed to keep the QR's onclick valid.
 *
 * THE WORKAROUND: libnexaapp's createQrSvg(...) interpolates the `onclick` string into the SVG XML
 * WITHOUT escaping it (verified in qr.kt). A TDPP/nexid URI contains `&` (e.g. ?a=1&b=2), and a raw
 * `&` is illegal in an XML attribute value — it breaks the SVG and the QR stops being clickable. So
 * when you pass a URI as the onclick navigation target, escape `&` -> `&amp;` first. (The QR image
 * data itself encodes the RAW, unescaped URI — only the onclick attribute needs escaping.)
 *
 * Grounded in libnexaapp tdpp.kt (loginWalletUri/connectWalletUri) and sharedBackend qr.kt
 * (createQrSvg). See walletUriFormats.md for the URI shapes and SKILL.md for the protocol.
 */

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import org.nexa.libnexaapp.* // loginWalletUri, connectWalletUri, NexaAppSession, sessionHandler
import org.nexa.sharedBackend.createQrSvg   // adjust import to the module that exposes createQrSvg

/** Escape the characters that are illegal/ambiguous inside an XML attribute value. */
private fun xmlAttrEscape(s: String): String = s
    .replace("&", "&amp;")     // MUST be first
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

/**
 * Build a clickable QR SVG for a TDPP/nexid URI.
 * - The QR pixels encode the RAW uri (so a phone camera scans the real link).
 * - The onclick navigates THIS browser to the same uri, with the uri XML-escaped for the attribute.
 */
fun clickableWalletQr(uri: String, sizePx: Int = 320, cssClasses: String? = "wallet-qr"): String {
    val onclick = "window.location.href='${xmlAttrEscape(uri)}'"   // uri escaped for the XML attribute
    return createQrSvg(
        qrData = uri,             // RAW uri in the QR image
        maxSzInPix = sizePx,
        classes = cssClasses,
        onclick = onclick,        // createQrSvg does NOT escape this — we already did
    )
}

/**
 * Install GET routes that serve connect and login QRs for the caller's session.
 * (These are APP routes that complement libnexaapp's installWalletRoutes; mount under your app.)
 */
fun Routing.installWalletQrRoutes(externalUrl: String) {

    // A "connect wallet" QR.
    get("/qr/connect") {
        val (_, session) = sessionHandler!!.findCreateSession(call)
        val uri = connectWalletUri(session.id, serverPrefix = externalUrl)   // builds the tdpp connect URI
        call.respondText(clickableWalletQr(uri), ContentType.Image.SVG)
    }

    // A "login with nexid" QR. loginWalletUri binds a single-use challenge to the session.
    get("/qr/login") {
        val (_, session) = sessionHandler!!.findCreateSession(call)
        val uri = loginWalletUri(session, connectWallet = true)              // builds the nexid login URI
        call.respondText(clickableWalletQr(uri), ContentType.Image.SVG)
    }
}

/*
 * Notes:
 * - Only the onclick attribute is escaped; the scannable QR encodes the raw URI. Escaping the QR
 *   data would make phones scan a broken link.
 * - libnexaapp also serves connect/login QRs at /api/wallet/connectSvg and /api/wallet/loginSvg via
 *   installWalletRoutes — use those if you don't need a custom clickable wrapper. This template is
 *   for when you want the click-to-open behavior and must apply the escape yourself.
 * - The login challenge is single-use and session-bound (replay protection — SKILL.md Security).
 */