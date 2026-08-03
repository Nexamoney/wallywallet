package ui

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.SendScreenNavParams
import info.bitcoinunlimited.www.wally.ui.nav
import org.nexa.libnexakotlin.*
import org.nexa.threads.millisleep
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppTest : WallyUiTestBase()
{
    @AfterTest
    fun cleanup()
    {
        wallyApp = null
    }

    // --- LongPollInfo.url tests ---

    @Test
    fun longPollInfoUrlWithCookie()
    {
        val info = LongPollInfo("https", "example.com:8080", "session123")
        val url = info.url()
        assertEquals("https://example.com:8080/_lp?cookie=session123", url)
    }

    @Test
    fun longPollInfoUrlWithoutCookie()
    {
        val info = LongPollInfo("http", "localhost:3000", null)
        val url = info.url()
        assertEquals("http://localhost:3000/_lp", url)
    }

    @Test
    fun longPollInfoUrlRoute()
    {
        val info = LongPollInfo("https", "example.com", null)
        assertEquals("https://example.com/_lp", info.urlRoute())
    }

    // --- lockAccounts / unlockAccounts tests ---

    @Test
    fun lockAccountsSetsAllPinEnteredFalse()
    {
        val app = wallyApp!!
        val account = app.newAccount("lockTest", 0U, "1234", ChainSelector.NEXA)!!
        account.pinEntered = true
        assertTrue(account.pinEntered)

        app.lockAccounts()

        assertFalse(account.pinEntered)
        app.deleteAccount(account)
    }

    @Test
    fun unlockAccountsWithCorrectPinReturnsCount()
    {
        val app = wallyApp!!
        val account = mockAccount()
        app.accounts[account.name] = account
        // mockAccount has no PIN (encodedPin is null), so submitAccountPin returns 0
        // Need a real account with a PIN for this test
        val realAccount = app.newAccount("unlockReal", 0U, "5678", ChainSelector.NEXA)!!
        realAccount.pinEntered = false

        val unlocked = app.unlockAccounts("5678")
        assertTrue(unlocked > 0)
        assertTrue(realAccount.pinEntered)
        app.deleteAccount(realAccount)
    }

    @Test
    fun unlockAccountsWithWrongPinReturnsZero()
    {
        val app = wallyApp!!
        val realAccount = app.newAccount("unlockWrong", 0U, "9999", ChainSelector.NEXA)!!
        realAccount.pinEntered = false

        val unlocked = app.unlockAccounts("0000")
        assertEquals(0, unlocked)
        assertFalse(realAccount.pinEntered)
        app.deleteAccount(realAccount)
    }

    // --- visibleAccountNames tests ---

    @Test
    fun visibleAccountNamesReturnsOnlyVisible()
    {
        val app = wallyApp!!
        val account = mockAccount()
        app.accounts[account.name] = account

        val names = app.visibleAccountNames()
        for (name in names)
        {
            val a = app.accounts[name]
            assertNotNull(a)
            assertTrue(a.visible)
        }
    }

    @Test
    fun visibleAccountNamesEmptyWhenNoAccounts()
    {
        val app = wallyApp!!
        val saved = app.accounts.values.toList()
        for (a in saved) app.deleteAccount(a)

        val names = app.visibleAccountNames()
        assertEquals(0, names.size)
    }

    // --- accountsFor tests ---

    @Test
    fun accountsForReturnsOnlyMatchingChain()
    {
        val app = wallyApp!!
        val account = mockAccount()
        app.accounts[account.name] = account

        val result = app.accountsFor(account.wallet.chainSelector)
        for (a in result)
        {
            assertEquals(account.wallet.chainSelector, a.wallet.chainSelector)
        }
    }

    @Test
    fun accountsForEmptyChainReturnsEmptyList()
    {
        val app = wallyApp!!
        val result = app.accountsFor(ChainSelector.BCHREGTEST)
        assertTrue(result.isEmpty())
    }

    // --- preferredVisibleAccountOrNull tests ---

    @Test
    fun preferredVisibleAccountOrNullReturnsAccount()
    {
        val app = wallyApp!!
        val account = mockAccount()
        app.accounts[account.name] = account

        val result = app.preferredVisibleAccountOrNull()
        assertNotNull(result)
        assertTrue(result.visible)
    }

    @Test
    fun preferredVisibleAccountOrNullReturnsNullWhenEmpty()
    {
        val app = wallyApp!!
        val saved = app.accounts.values.toList()
        for (a in saved) app.deleteAccount(a)

        val result = app.preferredVisibleAccountOrNull()
        assertNull(result)
    }

    @Test
    fun preferredVisibleAccountOrNullReturnsFocusedFirst()
    {
        val app = wallyApp!!
        val account = mockAccount()
        app.accounts[account.name] = account
        app.focusedAccount.value = account

        val result = app.preferredVisibleAccountOrNull()
        assertEquals(account, result)

        app.focusedAccount.value = null
    }

    // --- handlePaste tests ---

    @Test
    fun handlePasteEmptyStringReturnsTrue()
    {
        // Empty string means "just open wally" — returns true
        val result = wallyApp!!.handlePaste("")
        assertTrue(result)
    }

    @Test
    fun handlePasteValidNexaAddressNavigatesToSend()
    {
        val result = wallyApp!!.handlePaste("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        assertTrue(result)
        assertEquals(ScreenId.Send, nav.currentScreen.value)
        val params = nav.curData.value as SendScreenNavParams
        assertTrue(params.toAddress.contains("nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"))
        assertNull(params.amount)
    }

    @Test
    fun handlePasteNexaAddressWithoutPrefixNavigatesToSend()
    {
        val result = wallyApp!!.handlePaste("nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        assertTrue(result)
        assertEquals(ScreenId.Send, nav.currentScreen.value)
        val params = nav.curData.value as SendScreenNavParams
        assertTrue(params.toAddress.contains("nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"))
        assertNull(params.amount)
    }

    @Test
    fun handlePasteUnknownSchemeReturnsFalse()
    {
        val result = wallyApp!!.handlePaste("ftp://example.com/file.txt")
        assertFalse(result)
    }

    @Test
    fun handlePasteMalformedInputReturnsFalse()
    {
        val result = wallyApp!!.handlePaste("not_a_valid_anything_at_all_xyz")
        assertFalse(result)
    }

    @Test
    fun handlePasteHttpWrapperUnwrapsToNexa()
    {
        // HTTP wrapper: http://fake.com/nexa/address -> nexa:address
        val result = wallyApp!!.handlePaste("http://fake.com/nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        assertTrue(result)
    }

    @Test
    fun handlePasteNexaAddressWithAmountNavigatesToSend()
    {
        val result = wallyApp!!.handlePaste("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw?amount=1.5")
        assertTrue(result)
        assertEquals(ScreenId.Send, nav.currentScreen.value)
        val params = nav.curData.value as SendScreenNavParams
        assertTrue(params.toAddress.contains("nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"))
        assertEquals(BigDecimal.parseString("1.5"), params.amount)
    }

    @Test
    fun handlePasteNexaAddressWithInvalidAmountReturnsFalse()
    {
        // BadAmountException path — invalid amount triggers displayError and returns false
        val result = wallyApp!!.handlePaste("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw?amount=notanumber")
        assertFalse(result)
    }

    // --- postThen tests ---

    @Test
    fun postThenUnreachableServerDoesNotThrow()
    {
        val app = wallyApp!!
        var nextCalled = false
        // Unreachable URL — postThen should catch IOException and not throw
        app.postThen("http://127.0.0.1:1/unreachable", {}) { nextCalled = true }
        // postThen runs async via later — give it time to attempt and fail
        millisleep(3000U)
        // next should NOT be called since the request fails
        assertFalse(nextCalled)
    }

    @Test
    fun postThenNullNextDoesNotThrow()
    {
        val app = wallyApp!!
        // null next callback — should not crash
        app.postThen("http://127.0.0.1:1/unreachable", {}, null)
        millisleep(3000U)
        // Just verifying no exception is thrown
    }

    @Test
    fun postConvenienceCallsPostThen()
    {
        val app = wallyApp!!
        // post() is a convenience wrapper for postThen with null next
        app.post("http://127.0.0.1:1/unreachable") {}
        millisleep(3000U)
        // Just verifying no exception is thrown
    }

    // --- AccessHandler tests ---

    @Test
    fun activeToReturnsNullWhenNoPolls()
    {
        val handler = wallyApp!!.accessHandler
        assertNull(handler.activeTo("example.com"))
    }

    @Test
    fun startLongPollingMakesHostActive()
    {
        val handler = wallyApp!!.accessHandler
        handler.startLongPolling("http", "testhost.com:8080", "cookie123")
        // Give the async later{} a moment to register
        millisleep(500U)
        val lpi = handler.activeTo("testhost.com:8080")
        assertNotNull(lpi)
        assertTrue(lpi.active)
        assertEquals("cookie123", lpi.cookie)
        assertEquals("http", lpi.proto)
        // Clean up: deactivate
        lpi.active = false
        handler.done = true
    }

    @Test
    fun startLongPollingReplacesExisting()
    {
        val handler = wallyApp!!.accessHandler
        handler.startLongPolling("http", "replace.com:9090", "old")
        millisleep(500U)
        // Replace with new cookie
        handler.startLongPolling("https", "replace.com:9090", "new")
        millisleep(500U)
        val lpi = handler.activeTo("replace.com:9090")
        assertNotNull(lpi)
        assertEquals("new", lpi.cookie)
        assertEquals("https", lpi.proto)
        // Clean up
        lpi.active = false
        handler.done = true
    }

    @Test
    fun activeToMatchesHostWithPort()
    {
        val handler = wallyApp!!.accessHandler
        handler.startLongPolling("http", "porthost.com:1234", null)
        millisleep(500U)
        // Exact match with port
        assertNotNull(handler.activeTo("porthost.com:1234"))
        // Different port should not match exact, but host-only search should find it
        assertNotNull(handler.activeTo("porthost.com:9999"))
        // Clean up
        handler.activeTo("porthost.com:1234")?.active = false
        handler.done = true
    }

    @Test
    fun activeToMatchesHostWithoutPort()
    {
        val handler = wallyApp!!.accessHandler
        handler.startLongPolling("http", "noport.com:5555", null)
        millisleep(500U)
        // Host-only search (no colon) should match
        assertNotNull(handler.activeTo("noport.com"))
        // Clean up
        handler.activeTo("noport.com:5555")?.active = false
        handler.done = true
    }

    @Test
    fun activeToReturnsNullForUnknownHost()
    {
        val handler = wallyApp!!.accessHandler
        handler.startLongPolling("http", "known.com:80", null)
        millisleep(500U)
        assertNull(handler.activeTo("unknown.com:80"))
        // Clean up
        handler.activeTo("known.com:80")?.active = false
        handler.done = true
    }

    @Test
    fun activeToReturnsNullForInactiveConnection()
    {
        val handler = wallyApp!!.accessHandler
        handler.startLongPolling("http", "inactive.com:80", null)
        millisleep(500U)
        // Deactivate it
        handler.activeTo("inactive.com:80")?.active = false
        millisleep(500U)
        // Should return null since it's no longer active
        assertNull(handler.activeTo("inactive.com:80"))
        handler.done = true
    }

    // --- backgroundSync tests ---

    @Test
    fun backgroundSyncCallsCompletionWhenBackgroundOnlyFalse()
    {
        // When backgroundOnly is false, backgroundSync returns early from the sync loop
        // and still calls completion()
        backgroundOnly = false
        var completionCalled = false
        backgroundSync { completionCalled = true }
        assertTrue(completionCalled)
    }

    @Test
    fun backgroundSyncResetsBackgroundStopFlag()
    {
        backgroundOnly = false
        backgroundStop = true
        backgroundSync {}
        // backgroundSync sets backgroundStop = false at start
        assertFalse(backgroundStop)
    }

    @Test
    fun backgroundSyncCompletesEvenWithNoAccounts()
    {
        // Delete all accounts so the sync loop is skipped entirely
        val app = wallyApp!!
        val saved = app.accounts.values.toList()
        for (a in saved) app.deleteAccount(a)

        backgroundOnly = false
        var completionCalled = false
        backgroundSync { completionCalled = true }
        assertTrue(completionCalled)
    }
}
