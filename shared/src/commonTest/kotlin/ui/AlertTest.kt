package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.Alert
import info.bitcoinunlimited.www.wally.AlertLevel
import info.bitcoinunlimited.www.wally.ERROR_DISPLAY_TIME
import info.bitcoinunlimited.www.wally.NORMAL_NOTICE_DISPLAY_TIME
import info.bitcoinunlimited.www.wally.NOTICE_DISPLAY_TIME
import info.bitcoinunlimited.www.wally.alertChannel
import info.bitcoinunlimited.www.wally.alerts
import info.bitcoinunlimited.www.wally.clearAlerts
import info.bitcoinunlimited.www.wally.clearScreenAlerts
import info.bitcoinunlimited.www.wally.displayError
import info.bitcoinunlimited.www.wally.displayNotice
import info.bitcoinunlimited.www.wally.displaySuccess
import info.bitcoinunlimited.www.wally.displayUnexpectedException
import info.bitcoinunlimited.www.wally.displayWarning
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.wallyApp
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AlertTest : WallyUiTestBase()
{
    // ======================================================================
    // AlertLevel.longevity
    // ======================================================================

    @Test fun longevity_clear()
    {
        assertEquals(0, AlertLevel.CLEAR.longevity())
    }

    @Test fun longevity_success()
    {
        assertEquals(NORMAL_NOTICE_DISPLAY_TIME, AlertLevel.SUCCESS.longevity())
    }

    @Test fun longevity_notice()
    {
        assertEquals(NORMAL_NOTICE_DISPLAY_TIME, AlertLevel.NOTICE.longevity())
    }

    @Test fun longevity_warn()
    {
        assertEquals(NOTICE_DISPLAY_TIME, AlertLevel.WARN.longevity())
    }

    @Test fun longevity_error()
    {
        assertEquals(ERROR_DISPLAY_TIME, AlertLevel.ERROR.longevity())
    }

    @Test fun longevity_exception()
    {
        assertEquals(ERROR_DISPLAY_TIME, AlertLevel.EXCEPTION.longevity())
    }

    // ======================================================================
    // AlertLevel ordering
    // ======================================================================

    @Test fun alertLevel_ordering()
    {
        assertTrue(AlertLevel.CLEAR.level < AlertLevel.SUCCESS.level)
        assertTrue(AlertLevel.SUCCESS.level < AlertLevel.NOTICE.level)
        assertTrue(AlertLevel.NOTICE.level < AlertLevel.WARN.level)
        assertTrue(AlertLevel.WARN.level < AlertLevel.ERROR.level)
        assertTrue(AlertLevel.ERROR.level < AlertLevel.EXCEPTION.level)
    }

    // ======================================================================
    // Alert data class
    // ======================================================================

    @Test fun alert_dataClass_fields()
    {
        val a = Alert("summary", "details", AlertLevel.ERROR, "trace", 2, 5000L, 12345L)
        assertEquals("summary", a.msg)
        assertEquals("details", a.details)
        assertEquals(AlertLevel.ERROR, a.level)
        assertEquals("trace", a.trace)
        assertEquals(2, a.persistAcrossScreens)
        assertEquals(5000L, a.longevity)
        assertEquals(12345L, a.date)
    }

    @Test fun alert_defaultValues()
    {
        val a = Alert("msg", null, AlertLevel.NOTICE)
        assertNull(a.details)
        assertNotNull(a.trace)
        assertEquals(1, a.persistAcrossScreens)
        assertNull(a.longevity)
        assertTrue(a.date > 0)
    }

    // ======================================================================
    // displayX — verified via the shared `alerts` list. Each displayX appends
    // to `alerts` synchronously before launching the channel send, so this is
    // race-free. We deliberately do *not* observe alertChannel: it is a
    // single-consumer rendezvous channel, and any live NavigationRoot in the
    // test suite is also a consumer, which makes a direct channel.receive()
    // flaky on Android.
    //
    // These tests are gated on !hasNativeTitleBar: on Android, displayX
    // routes through currentActivity.displayAlert instead of populating
    // `alerts` + alertChannel, and currentActivity is null in these tests.
    // ======================================================================

    @Test fun displaySuccess_appendsToAlerts()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displaySuccess("Success!", "detail")
        assertEquals(before + 1, alerts.size)
        val a = alerts[before]
        assertEquals("Success!", a.msg)
        assertEquals("detail", a.details)
        assertEquals(AlertLevel.SUCCESS, a.level)
        assertNull(a.trace)
        assertEquals(NORMAL_NOTICE_DISPLAY_TIME, a.longevity)
    }

    @Test fun displaySuccess_noDetails()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displaySuccess("ok")
        val a = alerts[before]
        assertEquals("ok", a.msg)
        assertNull(a.details)
        assertEquals(AlertLevel.SUCCESS, a.level)
    }

    @Test fun displayNotice_appendsToAlerts()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displayNotice("Note", "info")
        assertEquals(before + 1, alerts.size)
        val a = alerts[before]
        assertEquals("Note", a.msg)
        assertEquals("info", a.details)
        assertEquals(AlertLevel.NOTICE, a.level)
        assertNull(a.trace)
        assertEquals(NOTICE_DISPLAY_TIME, a.longevity)
    }

    @Test fun displayNotice_persistAcrossScreens()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displayNotice("Note", persistAcrossScreens = 3)
        assertEquals(3, alerts[before].persistAcrossScreens)
    }

    @Test fun displayWarning_appendsToAlerts()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displayWarning("Warn!", "caution")
        assertEquals(before + 1, alerts.size)
        val a = alerts[before]
        assertEquals("Warn!", a.msg)
        assertEquals("caution", a.details)
        assertEquals(AlertLevel.WARN, a.level)
        assertNull(a.trace)
        assertEquals(NORMAL_NOTICE_DISPLAY_TIME, a.longevity)
    }

    @Test fun displayError_appendsToAlerts()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displayError("Error!", "bad thing")
        assertEquals(before + 1, alerts.size)
        val a = alerts[before]
        assertEquals("Error!", a.msg)
        assertEquals("bad thing", a.details)
        assertEquals(AlertLevel.ERROR, a.level)
        assertNull(a.trace)
        assertEquals(ERROR_DISPLAY_TIME, a.longevity)
    }

    @Test fun displayError_noDetails()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displayError("Error!")
        val a = alerts[before]
        assertEquals("Error!", a.msg)
        assertNull(a.details)
    }

    @Test fun displayUnexpectedException_appendsToAlerts()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displayUnexpectedException(RuntimeException("test crash"))
        assertEquals(before + 1, alerts.size)
        val a = alerts[before]
        assertEquals("test crash", a.msg)
        assertNotNull(a.details)
        assertEquals(AlertLevel.EXCEPTION, a.level)
        assertNotNull(a.trace)
        assertTrue(a.trace!!.contains("RuntimeException"))
        assertEquals(0, a.persistAcrossScreens)
        assertEquals(ERROR_DISPLAY_TIME, a.longevity)
    }

    @Test fun displayUnexpectedException_noMessage()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displayUnexpectedException(RuntimeException())
        // Falls back to e.toString() when e.message is null.
        assertTrue(alerts[before].msg.contains("RuntimeException"))
    }

    @Test fun alertsList_accumulates()
    {
        if (platform().hasNativeTitleBar) return
        val before = alerts.size
        displaySuccess("one")
        displayWarning("two")
        displayError("three")
        assertEquals(before + 3, alerts.size)
        assertEquals("one", alerts[before].msg)
        assertEquals("two", alerts[before + 1].msg)
        assertEquals("three", alerts[before + 2].msg)
    }

    // ======================================================================
    // clearAlerts / clearScreenAlerts — only observable via the UI, since they
    // neither mutate `alerts` nor expose any other state. We stand up a
    // NavigationRoot, trigger an error banner, call the clear function, then
    // assert on the banner text.
    //
    // Also gated on !hasNativeTitleBar: on Android both displayError and
    // clearAlerts go to the native title bar, which these Compose tests do
    // not render into.
    // ======================================================================

    /** The displayX tests above leave pending sends on the rendezvous
     *  alertChannel (they don't subscribe). If we don't drain them, the fresh
     *  NavigationRoot's LaunchedEffect receives those stale alerts first and
     *  ends up with the wrong banner state — an older "Error!" can sit on top
     *  of our warning and hide it, since TopBar renders only the top-level
     *  banner. */
    private fun drainAlertChannel()
    {
        while (alertChannel.tryReceive().isSuccess) { }
    }

    @Test fun clearAlerts_clearsErrorBanner()
    {
        if (platform().hasNativeTitleBar) return
        drainAlertChannel()
        runComposeUiTest {
            val pill = AccountPill(wallyApp!!.focusedAccount)
            val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
            setContent { NavigationRoot(Modifier, WindowInsets(0, 0, 0, 0), pill, unlock = unlock) }
            settle()
            nav.switch(ScreenId.Home)
            settle()

            val msg = "AlertTest_clearAlerts_boom"
            displayError(msg)
            waitUntil(timeoutMillis = 5000L) { onAllNodesWithText(msg).fetchSemanticsNodes().isNotEmpty() }

            clearAlerts()
            waitUntil(timeoutMillis = 5000L) { onAllNodesWithText(msg).fetchSemanticsNodes().isEmpty() }
        }
    }

    /** Verifies that the maxLevel parameter is respected: clearAlerts(WARN)
     *  clears a WARN-level banner. The positive signal is the banner actually
     *  disappearing, not a settle(). We can't usefully assert the symmetric
     *  property ("leaves ERROR alone") in the same Compose tree, because the
     *  TopBar renders only the highest-level banner, so WARN state would be
     *  invisible behind an ERROR and there'd be nothing to observe either way. */
    @Test fun clearAlerts_customMaxLevel_clearsWarningBanner()
    {
        if (platform().hasNativeTitleBar) return
        drainAlertChannel()
        runComposeUiTest {
            val pill = AccountPill(wallyApp!!.focusedAccount)
            val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
            setContent { NavigationRoot(Modifier, WindowInsets(0, 0, 0, 0), pill, unlock = unlock) }
            settle()
            nav.switch(ScreenId.Home)
            settle()

            val msg = "AlertTest_clearAlerts_maxWarn"
            displayWarning(msg)
            waitUntil(timeoutMillis = 5000L) { onAllNodesWithText(msg).fetchSemanticsNodes().isNotEmpty() }

            clearAlerts(AlertLevel.WARN)
            waitUntil(timeoutMillis = 5000L) { onAllNodesWithText(msg).fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test fun clearScreenAlerts_clearsErrorBanner()
    {
        if (platform().hasNativeTitleBar) return
        drainAlertChannel()
        runComposeUiTest {
            val pill = AccountPill(wallyApp!!.focusedAccount)
            val unlock = UnlockViewModel(wallyApp!!.focusedAccount)
            setContent { NavigationRoot(Modifier, WindowInsets(0, 0, 0, 0), pill, unlock = unlock) }
            settle()
            nav.switch(ScreenId.Home)
            settle()

            val msg = "AlertTest_clearScreenAlerts_boom"
            displayError(msg)
            waitUntil(timeoutMillis = 5000L) { onAllNodesWithText(msg).fetchSemanticsNodes().isNotEmpty() }

            clearScreenAlerts()
            waitUntil(timeoutMillis = 5000L) { onAllNodesWithText(msg).fetchSemanticsNodes().isEmpty() }
        }
    }
}
