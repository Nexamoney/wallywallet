package ui

import com.eygraber.uri.Uri
import info.bitcoinunlimited.www.wally.ui.identityErrorReply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the nexid error Response (spec: error field on the op reply, e.g. "unsupported_sigalg",
 * "user_rejected") construction used by IdentitySession.sendErrorReply
 */
class IdentityErrorReplyTest
{
    @Test
    fun signErrorRidesTheReplyGet()
    {
        val u = Uri.parse("nexid://example.com/_identity?op=sign&sign=hello&sigalg=foo&cookie=ABC&proto=https")
        val (req, body) = identityErrorReply(u, "sign", "ABC", "unsupported_sigalg")!!
        assertEquals("https://example.com/_identity?op=sign&error=unsupported_sigalg&cookie=ABC", req)
        assertNull(body)  // sign errors are a GET, not a POST
    }

    @Test
    fun loginErrorDefaultsToHttpAndKeepsNonDefaultPort()
    {
        // no proto parameter: the nexid scheme is not a protocol, so the reply falls back to http
        // (same workaround as the success replies)
        val u = Uri.parse("nexid://example.com:8001/callback?op=login&chal=x&cookie=C1")
        val (req, body) = identityErrorReply(u, "login", "C1", "user_rejected")!!
        assertEquals("http://example.com:8001/callback?op=login&error=user_rejected&cookie=C1", req)
        assertNull(body)
    }

    @Test
    fun regErrorRidesThePostJsonBody()
    {
        val u = Uri.parse("nexid://example.com/_identity?op=reg&chal=x&cookie=C2&proto=http")
        val (req, body) = identityErrorReply(u, "reg", "C2", "user_rejected")!!
        assertEquals("http://example.com/_identity?cookie=C2", req)
        assertEquals("""{"op":"reg","error":"user_rejected","cookie":"C2"}""", body)
    }

    @Test
    fun infoErrorWithoutCookieOmitsIt()
    {
        val u = Uri.parse("nexid://example.com/_identity?op=info&chal=x&proto=http")
        val (req, body) = identityErrorReply(u, "info", null, "user_rejected")!!
        assertEquals("http://example.com/_identity", req)
        assertEquals("""{"op":"info","error":"user_rejected"}""", body)
    }

    @Test
    fun suppressedWhenReplyFalse()
    {
        val u = Uri.parse("nexid://example.com/_identity?op=sign&sign=hello&reply=false&cookie=ABC")
        assertNull(identityErrorReply(u, "sign", "ABC", "user_rejected"))
    }

    @Test
    fun suppressedForClipboardOnlyHost()
    {
        val u = Uri.parse("nexid://_/?op=sign&sign=hello")
        assertNull(identityErrorReply(u, "sign", null, "user_rejected"))
    }

    @Test
    fun defaultPortsAreElided()
    {
        val u = Uri.parse("nexid://example.com:443/cb?op=sign&sign=hello&proto=https&cookie=Z")
        val (req, _) = identityErrorReply(u, "sign", "Z", "unsupported_sigalg")!!
        assertTrue(req.startsWith("https://example.com/cb?"), "default port must be elided: $req")
    }
}
