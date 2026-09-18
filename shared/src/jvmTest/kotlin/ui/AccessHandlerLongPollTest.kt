package ui

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.SendScreenNavParams
import info.bitcoinunlimited.www.wally.ui.nav
import org.nexa.threads.millisleep
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val TEST_ADDRESS = "nexa:nqtsq5g53fjq76u7y5kfuw8xqj3s7almzsqsjgypt2gs8ne2"

/** A loopback server standing in for the wallet-connect server's /_lp route.
 * [reply] is called with the exchange and the 0-based index of this request. */
private class FakeLpServer(val reply: (HttpExchange, Int) -> Unit)
{
    val requests = CopyOnWriteArrayList<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val exec = Executors.newSingleThreadExecutor()
    val hostPort = "127.0.0.1:" + server.address.port

    init
    {
        server.createContext("/") { exchange ->
            val idx = requests.size
            requests.add(exchange.requestURI.toString())
            reply(exchange, idx)
            exchange.close()
        }
        server.executor = exec
        server.start()
    }

    fun stop()
    {
        server.stop(0)
        exec.shutdownNow()
    }
}

private fun HttpExchange.respondWith(status: Int, body: String)
{
    val bytes = body.encodeToByteArray()
    if (bytes.isEmpty()) sendResponseHeaders(status, -1)
    else
    {
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }
}

private fun waitUntil(timeoutMs: Long = 20000, cond: () -> Boolean): Boolean
{
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline)
    {
        if (cond()) return true
        millisleep(50U)
    }
    return cond()
}

class AccessHandlerLongPollTest : WallyUiTestBase()
{
    private val servers = mutableListOf<FakeLpServer>()

    // Leave wallyApp itself alone: recreating it here would hand the account-dependent test
    // classes that run next an app with no accounts.
    @AfterTest
    fun cleanup()
    {
        val handler = wallyApp?.accessHandler
        for (s in servers)
        {
            handler?.activeTo(s.hostPort)?.active = false
            s.stop()
        }
        servers.clear()
    }

    private fun startPoll(server: FakeLpServer, cookie: String? = "cookie123"): LongPollInfo
    {
        servers.add(server)
        val handler = wallyApp!!.accessHandler
        handler.startLongPolling("http", server.hostPort, cookie)
        return handler.activeTo(server.hostPort)!!
    }

    @Test
    fun longPollSendsCookieAndIncrementingIndex()
    {
        val server = FakeLpServer { ex, _ -> ex.respondWith(200, "") }
        val lpInfo = startPoll(server)
        assertTrue(waitUntil { server.requests.size >= 2 }, "polled ${server.requests.size} times")
        lpInfo.active = false
        assertEquals("/_lp?cookie=cookie123&i=0", server.requests[0])
        assertEquals("/_lp?cookie=cookie123&i=1", server.requests[1])
        assertTrue(lpInfo.lastPing > 0)
    }

    @Test
    fun longPollWithoutCookieAppendsIndexToThePath()
    {
        // No cookie means no '?', so the index lands on the path instead of the query: /_lp&i=0
        val server = FakeLpServer { ex, _ -> ex.respondWith(200, "") }
        val lpInfo = startPoll(server, null)
        assertTrue(waitUntil { server.requests.isNotEmpty() })
        lpInfo.active = false
        assertEquals("/_lp&i=0", server.requests[0])
    }

    @Test
    fun longPollAcceptKeepsConnectionActive()
    {
        alerts.clear()
        val server = FakeLpServer { ex, _ -> ex.respondWith(200, "A") }
        val lpInfo = startPoll(server)
        assertTrue(waitUntil { server.requests.size >= 2 }, "polled ${server.requests.size} times")
        assertSame(lpInfo, wallyApp!!.accessHandler.activeTo(server.hostPort))
        assertTrue(lpInfo.lastPing > 0)
        assertTrue(alerts.any { it.msg == i18n(S.connectionEstablished) })
        lpInfo.active = false
    }

    @Test
    fun longPollQuitResponseEndsPolling()
    {
        alerts.clear()
        val server = FakeLpServer { ex, _ -> ex.respondWith(200, "Q") }
        val lpInfo = startPoll(server)
        val handler = wallyApp!!.accessHandler
        assertTrue(waitUntil { handler.activeTo(server.hostPort) !== lpInfo }, "long poll did not end")
        val polls = server.requests.size
        millisleep(1500U)
        assertEquals(polls, server.requests.size)
        assertTrue(alerts.any { it.msg == i18n(S.connectionClosed) })
    }

    @Test
    fun longPollServiceUnavailableEndsPolling()
    {
        val server = FakeLpServer { ex, _ -> ex.respondWith(503, "<html><body>Service Unavailable</body></html>") }
        val lpInfo = startPoll(server)
        val handler = wallyApp!!.accessHandler
        assertTrue(waitUntil { handler.activeTo(server.hostPort) !== lpInfo }, "long poll did not end")
    }

    @Test
    fun longPollNotFoundEndsPollingAndAsksForNewQr()
    {
        alerts.clear()
        val server = FakeLpServer { ex, _ -> ex.respondWith(404, "not found") }
        val lpInfo = startPoll(server)
        val handler = wallyApp!!.accessHandler
        assertTrue(waitUntil { handler.activeTo(server.hostPort) !== lpInfo }, "long poll did not end")
        assertTrue(alerts.any { it.msg == i18n(S.refreshQR) })
    }

    @Test
    fun longPollBadRequestEndsPolling()
    {
        val server = FakeLpServer { ex, _ -> ex.respondWith(400, "bad request") }
        val lpInfo = startPoll(server)
        val handler = wallyApp!!.accessHandler
        assertTrue(waitUntil { handler.activeTo(server.hostPort) !== lpInfo }, "long poll did not end")
    }

    @Test
    fun longPollPayloadIsHandledAsAPaste()
    {
        val screen = nav.currentScreen.value
        val data = nav.curData.value
        try
        {
            val server = FakeLpServer { ex, i -> ex.respondWith(200, if (i == 0) TEST_ADDRESS else "Q") }
            val lpInfo = startPoll(server)
            assertTrue(waitUntil { (nav.curData.value as? SendScreenNavParams)?.toAddress?.contains(TEST_ADDRESS.substringAfter(":")) == true },
              "did not navigate to Send with the pasted address")
            assertEquals(ScreenId.Send, nav.currentScreen.value)
            assertTrue(lpInfo.lastPing > 0)
        }
        finally  // a Send screen left in nav.curData is cast to their own params by the screens that run next
        {
            nav.switch(screen, data = data)
        }
    }

    @Test
    fun longPollConnectionErrorKeepsRetrying()
    {
        val handler = wallyApp!!.accessHandler
        handler.startLongPolling("http", "127.0.0.1:1", "cookie123")
        val lpInfo = handler.activeTo("127.0.0.1:1")!!
        millisleep(4000U)
        assertSame(lpInfo, handler.activeTo("127.0.0.1:1"))
        lpInfo.active = false
    }
}
