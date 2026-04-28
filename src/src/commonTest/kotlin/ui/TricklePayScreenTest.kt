package ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.KotlinTarget
import info.bitcoinunlimited.www.wally.NumberGroupingSeparator
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.TdppDomain
import info.bitcoinunlimited.www.wally.TricklePaySession
import info.bitcoinunlimited.www.wally.devMode
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui.AutopayCard
import info.bitcoinunlimited.www.wally.ui.ConfigureTricklePayScreen
import info.bitcoinunlimited.www.wally.ui.TricklePayDomainView
import info.bitcoinunlimited.www.wally.ui.TricklePayRegistrationsScreen
import info.bitcoinunlimited.www.wally.ui.groupedCurrencyDecimal
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TricklePayScreenTest: WallyUiTestBase()
{
    /** Fresh TdppDomain with sensible defaults for tests. `maxper=-1` etc.
     *  means "not set" (displayed as empty strings via the fmtMax helper). */
    private fun makeDomain(
      domain: String = "example.com",
      topic: String = "shopping",
      maxper: Long = -1,
      maxday: Long = -1,
      maxweek: Long = -1,
      maxmonth: Long = -1,
      automaticEnabled: Boolean = false,
    ) = TdppDomain(
      domain, topic,
      "addr", "NEX",
      maxper, maxday, maxweek, maxmonth,
      "per", "day", "week", "month",
      automaticEnabled,
      "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw",
      "nexa:nqtsq5g55t9699mcue00frjqql5275r3et45c3dqtxzfz8ru",
      "nexa:nqtsq5g5skc5xfw7dzu2jw7hktf3tg053drevxx94yx44gjc",
    )

    // Restore devMode after each test in case something flipped it.
    @AfterTest fun resetDevMode() { devMode = false }

    // ======================================================================
    // groupedCurrencyDecimal — pure helper, no Compose needed.
    // ======================================================================

    /** groupedCurrencyDecimal relies on NumberGroupingSeparator, which on iOS
     *  goes through libnexakotlin's native DecimalFormat — that currently
     *  throws IndexOutOfBoundsException on the "#,###" pattern (see
     *  platformNative.kt:63). These tests are gated off iOS until that's fixed
     *  upstream. */
    @Test
    fun groupedCurrencyDecimal_parsesPlainNumber()
    {
        if (platform().target == KotlinTarget.iOS) return
        // Use compareTo — CurrencyDecimal uses a fixed scale (lots of trailing
        // zeros) so toPlainString() doesn't equal "1234".
        assertEquals(0, groupedCurrencyDecimal("1234").compareTo(BigDecimal.fromInt(1234)))
    }

    @Test
    fun groupedCurrencyDecimal_stripsGroupingSeparator()
    {
        if (platform().target == KotlinTarget.iOS) return
        // "1,234,567" form with locale-appropriate separator should parse to
        // the same value as the un-separated form.
        val sep = NumberGroupingSeparator
        val grouped = "1${sep}234${sep}567"
        assertEquals(0, groupedCurrencyDecimal(grouped).compareTo(groupedCurrencyDecimal("1234567")))
    }

    @Test
    fun groupedCurrencyDecimal_parsesDecimal()
    {
        if (platform().target == KotlinTarget.iOS) return
        assertEquals(0, groupedCurrencyDecimal("12.34").compareTo(groupedCurrencyDecimal("12.34")))
        // Sanity: 12.34 is not equal to 1234 at scale.
        assertTrue(groupedCurrencyDecimal("12.34").compareTo(BigDecimal.fromInt(1234)) != 0)
    }

    // ======================================================================
    // Existing coverage: TricklePayDomainView
    // ======================================================================

    @Test
    fun tricklePayDomainViewTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("testaccountitemview", 0U, "", cs)!!
        runComposeUiTest {
            val td = makeDomain(domain = "domain", topic = "topic", maxper = 2, maxday = 3, maxweek = 4)
            setContent { TricklePayDomainView(td, Modifier, account) }
            check(waitForCatching { onNodeWithTag("TricklePayDomainViewDomainName").isDisplayed() })
        }
        wallyApp!!.deleteAccount(account)
    }

    // ======================================================================
    // AutopayCard — standalone, no session/account dependency.
    // ======================================================================

    @Test
    fun autopayCard_rendersLabelsAndInstructions() = runComposeUiTest {
        setContent {
            AutopayCard(
              autoPayEnabled = true,
              maxPerRequest = "", maxPerDay = "", maxPerWeek = "", maxPerMonth = "",
              onAutoPayToggled = {},
              onMaxPerRequestChanged = {}, onMaxPerDayChanged = {},
              onMaxPerWeekChanged = {}, onMaxPerMonthChanged = {},
            )
        }
        settle()
        onNodeWithText(i18n(S.AutoPay)).assertIsDisplayed()
        onNodeWithText(i18n(S.AutoPayInstructions)).assertIsDisplayed()
        onNodeWithText(i18n(S.LimitedAutoApproval)).assertIsDisplayed()
        onNodeWithText(i18n(S.AutoPayMaxPerRequest)).assertIsDisplayed()
        onNodeWithText(i18n(S.AutoPayMaxPerDay)).assertIsDisplayed()
        onNodeWithText(i18n(S.AutoPayMaxPerWeek)).assertIsDisplayed()
        onNodeWithText(i18n(S.AutoPayMaxPerMonth)).assertIsDisplayed()
    }

    @Test
    fun autopayCard_hasFourNumericInputs() = runComposeUiTest {
        setContent {
            AutopayCard(
              autoPayEnabled = true,
              maxPerRequest = "100", maxPerDay = "200", maxPerWeek = "300", maxPerMonth = "400",
              onAutoPayToggled = {},
              onMaxPerRequestChanged = {}, onMaxPerDayChanged = {},
              onMaxPerWeekChanged = {}, onMaxPerMonthChanged = {},
            )
        }
        settle()
        // All four WallyNumericInputFieldBalance calls share the same tag
        // ("amountToSendInput") — count them instead of matching by tag.
        assertEquals(4, onAllNodesWithTag("amountToSendInput").fetchSemanticsNodes().size)
        // Each distinct amount string is visible somewhere in the card.
        onNodeWithText("100").assertIsDisplayed()
        onNodeWithText("200").assertIsDisplayed()
        onNodeWithText("300").assertIsDisplayed()
        onNodeWithText("400").assertIsDisplayed()
    }

    @Test
    fun autopayCard_toggleSwitch_invokesCallback() = runComposeUiTest {
        var toggled: Boolean? = null
        setContent {
            AutopayCard(
              autoPayEnabled = false,
              maxPerRequest = "", maxPerDay = "", maxPerWeek = "", maxPerMonth = "",
              onAutoPayToggled = { toggled = it },
              onMaxPerRequestChanged = {}, onMaxPerDayChanged = {},
              onMaxPerWeekChanged = {}, onMaxPerMonthChanged = {},
            )
        }
        settle()
        // The Switch rendered by WallySwitchRow is the only toggleable node in the card.
        onNode(isToggleable()).performClick()
        settle()
        assertEquals(true, toggled)
    }

    /** The four numeric inputs in AutopayCard share the same testTag, so we
     *  distinguish them by their tree-order index: 0=request, 1=day, 2=week,
     *  3=month. Typing into input `targetIndex` must fire *only* the matching
     *  callback — the others stay null. */
    private fun assertOnlyCallbackFires(
      targetIndex: Int,
      typed: String,
      verify: (req: String?, day: String?, week: String?, month: String?) -> Unit,
    ) = runComposeUiTest {
        var req: String? = null
        var day: String? = null
        var week: String? = null
        var month: String? = null
        setContent {
            AutopayCard(
              autoPayEnabled = true,
              maxPerRequest = "", maxPerDay = "", maxPerWeek = "", maxPerMonth = "",
              onAutoPayToggled = {},
              onMaxPerRequestChanged = { req = it },
              onMaxPerDayChanged = { day = it },
              onMaxPerWeekChanged = { week = it },
              onMaxPerMonthChanged = { month = it },
            )
        }
        settle()
        onAllNodesWithTag("amountToSendInput")[targetIndex].performTextInput(typed)
        settle()
        verify(req, day, week, month)
    }

    @Test
    fun autopayCard_maxPerRequestInput_invokesCallback() =
      assertOnlyCallbackFires(targetIndex = 0, typed = "42") { req, day, week, month ->
          assertEquals("42", req)
          assertNull(day); assertNull(week); assertNull(month)
      }

    @Test
    fun autopayCard_maxPerDayInput_invokesCallback() =
      assertOnlyCallbackFires(targetIndex = 1, typed = "100") { req, day, week, month ->
          assertEquals("100", day)
          assertNull(req); assertNull(week); assertNull(month)
      }

    @Test
    fun autopayCard_maxPerWeekInput_invokesCallback() =
      assertOnlyCallbackFires(targetIndex = 2, typed = "500") { req, day, week, month ->
          assertEquals("500", week)
          assertNull(req); assertNull(day); assertNull(month)
      }

    @Test
    fun autopayCard_maxPerMonthInput_invokesCallback() =
      assertOnlyCallbackFires(targetIndex = 3, typed = "2000") { req, day, week, month ->
          assertEquals("2000", month)
          assertNull(req); assertNull(day); assertNull(week)
      }

    // ======================================================================
    // TricklePayRegistrationsScreen
    // ======================================================================

    @Test
    fun tricklePayRegistrationsScreen_empty_showsNoServicesMessage() = runComposeUiTest {
        wallyApp!!.tpDomains.clear()
        setContent { TricklePayRegistrationsScreen() }
        settle()
        onNodeWithText(i18n(S.NoServicesRegistered)).assertIsDisplayed()
    }

    @Test
    fun tricklePayRegistrationsScreen_withDomains_listsThem() = runComposeUiTest {
        wallyApp!!.tpDomains.clear()
        wallyApp!!.tpDomains.insert(makeDomain("alpha.example", "topic-alpha"))
        wallyApp!!.tpDomains.insert(makeDomain("beta.example", "topic-beta"))

        setContent { TricklePayRegistrationsScreen() }
        settle()

        // ListItem merges its headlineContent + supportingContent into one
        // semantics node, and the headline uses the map key (domain/topic),
        // so match by substring.
        onNodeWithText("alpha.example", substring = true).assertIsDisplayed()
        onNodeWithText("beta.example", substring = true).assertIsDisplayed()
        onNodeWithText("topic-alpha", substring = true).assertIsDisplayed()
        onNodeWithText("topic-beta", substring = true).assertIsDisplayed()
        onNodeWithText(i18n(S.NoServicesRegistered)).assertDoesNotExist()

        wallyApp!!.tpDomains.clear()
    }

    @Test
    fun tricklePayRegistrationsScreen_devMode_showsRemoveAllButton() = runComposeUiTest {
        wallyApp!!.tpDomains.clear()
        devMode = true
        setContent { TricklePayRegistrationsScreen() }
        settle()
        onNodeWithText(i18n(S.removeAll)).assertIsDisplayed()
    }

    @Test
    fun tricklePayRegistrationsScreen_nonDevMode_hidesRemoveAllButton() = runComposeUiTest {
        wallyApp!!.tpDomains.clear()
        devMode = false
        setContent { TricklePayRegistrationsScreen() }
        settle()
        onNodeWithText(i18n(S.removeAll)).assertDoesNotExist()
    }

    // ======================================================================
    // ConfigureTricklePayScreen
    // ======================================================================

    /** Configure flow for a *new* domain (fromSess.newDomain == true) exposes
     *  the "Accept or reject service" header plus Accept/Reject buttons.
     *  We anchor on the unique header text plus S.reject (which never appears
     *  as an iOS numeric-keyboard label, unlike S.done). i18n(S.accept) yields
     *  two matches — once in the WallyOptionsCard's ACCEPT option row, once on
     *  the button. */
    @Test
    fun configureTricklePayScreen_newDomain_showsAcceptRejectButtons()
    {
        val account = wallyApp!!.newAccount("tpConfigNew", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                val toDomain = makeDomain("new.example", "onboarding")
                val fromSess = TricklePaySession(wallyApp!!.tpDomains).apply {
                    newDomain = true
                    domain = null
                }
                setContent { ConfigureTricklePayScreen(fromSess, toDomain, account) }
                settle()

                onNodeWithText(i18n(S.AcceptOrRejectService)).assertIsDisplayed()
                onNodeWithText(i18n(S.ConfigureService)).assertDoesNotExist()
                onAllNodesWithText(i18n(S.accept)).assertCountEquals(2)
                onNodeWithText(i18n(S.reject)).assertIsDisplayed()
                // S.remove is unique (not in card, not an iOS keyboard label)
                // and must not appear in the new-domain flow.
                onNodeWithText(i18n(S.remove)).assertDoesNotExist()
                // Note: we can't assert `S.done doesn't exist` here — on iOS,
                // WallyNumericInputFieldBalance with hasIosDoneButton=true
                // renders a "Done" keyboard accessory per input (4 of them),
                // so that text is always present on iOS. The absence of the
                // actual Done button is implied by the header + remove check.
                onNodeWithTag("TricklePayDomainViewDomainName").assertExists()
            }
        }
        finally { wallyApp!!.deleteAccount(account) }
    }

    /** Configure flow for an *existing* domain (newDomain == false) exposes
     *  the "Configure service" header plus Done/Remove buttons. */
    @Test
    fun configureTricklePayScreen_existingDomain_showsDoneRemoveButtons()
    {
        val account = wallyApp!!.newAccount("tpConfigEdit", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                val toDomain = makeDomain("edit.example", "billing")
                val fromSess = TricklePaySession(wallyApp!!.tpDomains).apply {
                    newDomain = false
                    editDomain = true
                    domain = toDomain
                }
                setContent { ConfigureTricklePayScreen(fromSess, toDomain, account) }
                settle()

                onNodeWithText(i18n(S.ConfigureService)).assertIsDisplayed()
                onNodeWithText(i18n(S.AcceptOrRejectService)).assertDoesNotExist()
                onNodeWithText(i18n(S.remove)).assertIsDisplayed()
                // i18n(S.done) also appears as the iOS numeric-keyboard "Done"
                // accessory on each of the 4 numeric inputs, so the count is
                // 5 on iOS and 1 on JVM/Android. "Present at all" is enough.
                assertTrue(
                  onAllNodesWithText(i18n(S.done)).fetchSemanticsNodes().isNotEmpty(),
                  "Done button should be visible in the edit flow",
                )
                // i18n(S.accept) appears only as the ACCEPT option in the card
                // (not as a button) in this flow. S.reject is unique to the
                // new-domain flow and must not appear here.
                onAllNodesWithText(i18n(S.accept)).assertCountEquals(1)
                onNodeWithText(i18n(S.reject)).assertDoesNotExist()
            }
        }
        finally { wallyApp!!.deleteAccount(account) }
    }

    /** Clicking Accept on a new domain invokes whenDone(url, "ok", true)
     *  and inserts the domain into tpDomains. */
    @Test
    fun configureTricklePayScreen_newDomain_acceptInvokesWhenDoneAndInserts()
    {
        val account = wallyApp!!.newAccount("tpConfigAccept", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                val toDomain = makeDomain("accept.example", "topic")
                wallyApp!!.tpDomains.remove(toDomain)  // ensure clean slate

                var captured: Triple<String, String, Boolean?>? = null
                val fromSess = TricklePaySession(wallyApp!!.tpDomains, whenDone = { url, msg, ok ->
                    captured = Triple(url, msg, ok)
                }).apply {
                    newDomain = true
                    domain = null
                }
                setContent { ConfigureTricklePayScreen(fromSess, toDomain, account) }
                settle()
                // The S.accept text matches both the card's ACCEPT option and
                // the button. The button composes after the card, so it's the
                // second (last) node in tree order.
                val accepts = onAllNodesWithText(i18n(S.accept)).apply { assertCountEquals(2) }
                accepts[1].performClick()
                settle()

                assertEquals("ok", captured?.second)
                assertEquals(true, captured?.third)
                val key = toDomain.domain + "/" + toDomain.topic
                assertTrue(wallyApp!!.tpDomains.domains.value.containsKey(key),
                  "Accepted domain should be inserted into tpDomains")
                // whenDone's URL is fromSess.proposalUrl.toString(); with
                // proposalUrl == null, that's "null".
                assertEquals("null", captured?.first)
            }
        }
        finally { wallyApp!!.deleteAccount(account) }
    }

    /** Clicking Reject on a new domain invokes whenDone(_, "", false) and
     *  does NOT insert the domain. */
    @Test
    fun configureTricklePayScreen_newDomain_rejectInvokesWhenDoneWithFalse()
    {
        val account = wallyApp!!.newAccount("tpConfigReject", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                val toDomain = makeDomain("reject.example", "topic")
                wallyApp!!.tpDomains.remove(toDomain)

                var captured: Triple<String, String, Boolean?>? = null
                val fromSess = TricklePaySession(wallyApp!!.tpDomains, whenDone = { url, msg, ok ->
                    captured = Triple(url, msg, ok)
                }).apply {
                    newDomain = true
                    domain = null
                }
                setContent { ConfigureTricklePayScreen(fromSess, toDomain, account) }
                settle()
                // S.reject only appears on the button — no collision with the card.
                onNodeWithText(i18n(S.reject)).performClick()
                settle()

                assertEquals("", captured?.second)
                assertEquals(false, captured?.third)
                val key = toDomain.domain + "/" + toDomain.topic
                assertFalse(wallyApp!!.tpDomains.domains.value.containsKey(key),
                  "Rejected domain must not be inserted into tpDomains")
            }
        }
        finally { wallyApp!!.deleteAccount(account) }
    }
}
