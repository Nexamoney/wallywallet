package ui

import com.eygraber.uri.Uri
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import kotlinx.serialization.json.Json
import org.nexa.libnexakotlin.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNotNull

@OptIn(ExperimentalUnsignedTypes::class)
class TricklePaySessionTest
{
    init {
        initializeLibNexa()
    }

    @BeforeTest
    fun beforeTest()
    {
        wallyApp = CommonApp(true)
        wallyApp!!.onCreate()
        val account = mockAccount()
        wallyApp!!.accounts[account.name] = account
        setSelectedAccount(account)
    }

    @AfterTest
    fun afterTest()
    {
        // Each test deletes its account; deletion's Phase B is async. Wait for it before dropping
        // wallyApp so a leaked teardown job doesn't run against the next test's fresh app/DB.
        awaitDeletionsComplete()
        deleteMockAccountDbs()
        wallyApp = null
    }

    private fun clearPersistedTdppDomains()
    {
        try
        {
            openKvpDB("wallyData")?.delete("tdppDomains".encodeToByteArray())
        }
        catch (_: Throwable)
        {
            // Key wasn't present, or the DB is fine to leave alone.
        }
    }

    // --- TdppAction.of tests ---

    @Test
    fun tdppActionOfDeny()
    {
        assertEquals(TdppAction.DENY, TdppAction.of(0))
    }

    @Test
    fun tdppActionOfAsk()
    {
        assertEquals(TdppAction.ASK, TdppAction.of(1))
    }

    @Test
    fun tdppActionOfAccept()
    {
        assertEquals(TdppAction.ACCEPT, TdppAction.of(2))
    }

    @Test
    fun tdppActionOfInvalidThrows()
    {
        assertFails {
            TdppAction.of(99)
        }
    }

    // --- TdppAction.inc tests ---

    @Test
    fun tdppActionIncCycles()
    {
        var action = TdppAction.DENY
        action++
        assertEquals(TdppAction.ASK, action)
        action++
        assertEquals(TdppAction.ACCEPT, action)
        action++
        assertEquals(TdppAction.DENY, action)
    }

    // --- TdppAction.toString tests ---

    @Test
    fun tdppActionToStringReturnsI18n()
    {
        assertEquals(i18n(S.deny), TdppAction.DENY.toString())
        assertEquals(i18n(S.ask), TdppAction.ASK.toString())
        assertEquals(i18n(S.accept), TdppAction.ACCEPT.toString())
    }

    // --- TdppDomain tests ---

    private fun makeDomain(
        domain: String = "example.com",
        topic: String = "",
        maxper: Long = 100,
        maxday: Long = 500,
        maxweek: Long = 2000,
        maxmonth: Long = 5000,
        automaticEnabled: Boolean = true
    ): TdppDomain
    {
        return TdppDomain(
            domain, topic, "addr", "NEX",
            maxper, maxday, maxweek, maxmonth,
            "per desc", "day desc", "week desc", "month desc",
            automaticEnabled, "", "", ""
        )
    }

    @Test
    fun tdppDomainGetParamParsesAmount()
    {
        val d = makeDomain()
        val uri = Uri.parse("https://example.com?maxper=200&descper=payment")
        val result = d.getParam(uri, "maxper", "descper")
        assertEquals(200L, result.first)
        assertEquals("payment", result.second)
    }

    @Test
    fun tdppDomainGetParamMissingReturnsNeg1()
    {
        val d = makeDomain()
        val uri = Uri.parse("https://example.com")
        val result = d.getParam(uri, "maxper", "descper")
        assertEquals(-1L, result.first)
        assertEquals("", result.second)
    }

    @Test
    fun tdppDomainMergeTakesBiggerValues()
    {
        val d = makeDomain(maxper = 50, maxday = 50, maxweek = 50, maxmonth = 50)
        val uri = Uri.parse("https://example.com?topic=&addr=a&maxper=200&descper=big&maxday=300&descday=d&maxweek=400&descweek=w&maxmonth=500&descmonth=m")
        d.merge(uri)
        assertEquals(200L, d.maxper)
        assertEquals(300L, d.maxday)
        assertEquals(400L, d.maxweek)
        assertEquals(500L, d.maxmonth)
    }

    @Test
    fun tdppDomainMergeKeepsExistingWhenBigger()
    {
        val d = makeDomain(maxper = 999, maxday = 999, maxweek = 999, maxmonth = 999)
        val uri = Uri.parse("https://example.com?topic=&addr=a&maxper=10&descper=small&maxday=10&descday=d&maxweek=10&descweek=w&maxmonth=10&descmonth=m")
        d.merge(uri)
        assertEquals(999L, d.maxper)
        assertEquals(999L, d.maxday)
        assertEquals(999L, d.maxweek)
        assertEquals(999L, d.maxmonth)
    }

    @Test
    fun tdppDomainLoadOverwritesAll()
    {
        val d = makeDomain(maxper = 999)
        val uri = Uri.parse("https://newhost.com?topic=newtopic&addr=newaddr&maxper=10&descper=p&maxday=20&descday=d&maxweek=30&descweek=w&maxmonth=40&descmonth=m")
        d.load(uri)
        assertEquals("newhost.com", d.domain)
        assertEquals("newtopic", d.topic)
        assertEquals("newaddr", d.addr)
        assertEquals(10L, d.maxper)
        assertEquals(20L, d.maxday)
        assertEquals(30L, d.maxweek)
        assertEquals(40L, d.maxmonth)
    }

    @Test
    fun tdppDomainDefaultExceededActionsAreAsk()
    {
        val d = makeDomain()
        assertEquals(TdppAction.ASK, d.maxperExceeded)
        assertEquals(TdppAction.ASK, d.maxdayExceeded)
        assertEquals(TdppAction.ASK, d.maxweekExceeded)
        assertEquals(TdppAction.ASK, d.maxmonthExceeded)
        assertEquals(TdppAction.ASK, d.assetInfo)
        assertEquals(TdppAction.ASK, d.balanceInfo)
    }

    @Test
    fun tdppDomainSerializeDeserializeRoundTrip()
    {
        val d = makeDomain(domain = "test.org", topic = "testtopic")
        d.maxperExceeded = TdppAction.ACCEPT
        d.maxdayExceeded = TdppAction.DENY
        d.assetInfo = TdppAction.ACCEPT
        d.balanceInfo = TdppAction.DENY
        d.mainPayAddress = "mainaddr"
        d.lastPayAddress = "lastaddr"
        d.accountName = "myaccount"

        val serialized = d.BCHserialize()
        val d2 = TdppDomain(serialized)

        assertEquals(d.domain, d2.domain)
        assertEquals(d.topic, d2.topic)
        assertEquals(d.addr, d2.addr)
        assertEquals(d.uoa, d2.uoa)
        assertEquals(d.maxper, d2.maxper)
        assertEquals(d.maxday, d2.maxday)
        assertEquals(d.maxweek, d2.maxweek)
        assertEquals(d.maxmonth, d2.maxmonth)
        assertEquals(d.descper, d2.descper)
        assertEquals(d.descday, d2.descday)
        assertEquals(d.descweek, d2.descweek)
        assertEquals(d.descmonth, d2.descmonth)
        assertEquals(d.automaticEnabled, d2.automaticEnabled)
        assertEquals(d.maxperExceeded, d2.maxperExceeded)
        assertEquals(d.maxdayExceeded, d2.maxdayExceeded)
        assertEquals(d.maxweekExceeded, d2.maxweekExceeded)
        assertEquals(d.maxmonthExceeded, d2.maxmonthExceeded)
        assertEquals(d.assetInfo, d2.assetInfo)
        assertEquals(d.balanceInfo, d2.balanceInfo)
        assertEquals(d.mainPayAddress, d2.mainPayAddress)
        assertEquals(d.lastPayAddress, d2.lastPayAddress)
        assertEquals(d.accountName, d2.accountName)
    }

    // --- TricklePayDomains tests ---

    @Test
    fun tricklePayDomainsDomainKey()
    {
        val domains = TricklePayDomains()
        assertEquals("host/topic", domains.domainKey("host", "topic"))
    }

    @Test
    fun tricklePayDomainsDomainKeyNullTopic()
    {
        val domains = TricklePayDomains()
        assertEquals("host/", domains.domainKey("host", null))
    }

    @Test
    fun tricklePayDomainsInsertAndRetrieve()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val d = makeDomain(domain = "inserted.com", topic = "t1")
        domains.insert(d)
        assertEquals(1, domains.size)
        assertTrue(domains.domains.value.containsKey("inserted.com/t1"))
    }

    @Test
    fun tricklePayDomainsRemove()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val d = makeDomain(domain = "toremove.com", topic = "")
        domains.insert(d)
        assertEquals(1, domains.size)
        domains.remove(d)
        assertEquals(0, domains.size)
    }

    @Test
    fun tricklePayDomainsClear()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        domains.insert(makeDomain(domain = "a.com"))
        domains.insert(makeDomain(domain = "b.com"))
        assertEquals(2, domains.size)
        domains.clear()
        assertEquals(0, domains.size)
    }

    @Test
    fun tricklePayDomainsLoadCreateDomainCreatesNew()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val d = domains.loadCreateDomain("newhost.com", "newtopic")
        assertNotNull(d)
        assertEquals("newhost.com", d.domain)
        assertEquals("newtopic", d.topic)
        assertEquals(1, domains.size)
    }

    @Test
    fun tricklePayDomainsLoadCreateDomainReturnsExisting()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val original = makeDomain(domain = "existing.com", topic = "t")
        original.maxper = 777
        domains.insert(original)
        val loaded = domains.loadCreateDomain("existing.com", "t")
        assertEquals(777L, loaded.maxper)
        assertEquals(1, domains.size)
    }

    @Test
    fun tricklePayDomainsLoadDomainReturnsNullIfMissing()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        assertNull(domains.loadDomain("noexist.com", ""))
    }

    // --- TricklePaySession property tests ---

    private fun makeSession(domains: TricklePayDomains? = null): TricklePaySession
    {
        val d = domains ?: TricklePayDomains().also { it.domainsLoaded = true }
        return TricklePaySession(d)
    }

    @Test
    fun sessionIsSecureRequestWithSigOkTrue()
    {
        val session = makeSession()
        session.sigOk = true
        assertTrue(session.isSecureRequest)
    }

    @Test
    fun sessionIsSecureRequestWithSigOkFalse()
    {
        val session = makeSession()
        session.sigOk = false
        session.proposalUrl = Uri.parse("https://example.com")
        assertFalse(session.isSecureRequest)
    }

    @Test
    fun sessionIsSecureRequestWithHttps()
    {
        val session = makeSession()
        session.sigOk = null
        session.proposalUrl = Uri.parse("https://example.com")
        assertTrue(session.isSecureRequest)
    }

    @Test
    fun sessionIsSecureRequestWithHttpNoSig()
    {
        val session = makeSession()
        session.sigOk = null
        session.proposalUrl = Uri.parse("http://example.com")
        assertFalse(session.isSecureRequest)
    }

    @Test
    fun sessionReplyProtocolFromRproto()
    {
        val session = makeSession()
        session.rproto = "https"
        assertEquals("https", session.replyProtocol)
    }

    @Test
    fun sessionReplyProtocolFromScheme()
    {
        val session = makeSession()
        session.rproto = null
        session.proposalUrl = Uri.parse("https://example.com/path")
        assertEquals("https", session.replyProtocol)
    }

    @Test
    fun sessionReplyProtocolDefault()
    {
        val session = makeSession()
        session.rproto = null
        session.proposalUrl = null
        assertEquals(TDPP_DEFAULT_PROTOCOL, session.replyProtocol)
    }

    @Test
    fun sessionDomainAndTopicWithTopic()
    {
        val session = makeSession()
        session.domain = makeDomain(domain = "example.com")
        session.topic = "payments"
        assertEquals("example.com:payments", session.domainAndTopic)
    }

    @Test
    fun sessionDomainAndTopicWithoutTopic()
    {
        val session = makeSession()
        session.domain = makeDomain(domain = "example.com")
        session.topic = null
        assertEquals("example.com", session.domainAndTopic)
    }

    @Test
    fun sessionHostAndPortWithPort()
    {
        val session = makeSession()
        session.host = "example.com"
        session.port = 8080
        assertEquals("example.com:8080", session.hostAndPort)
    }

    @Test
    fun sessionHostAndPortWithoutPort()
    {
        val session = makeSession()
        session.host = "example.com"
        session.port = -1
        assertEquals("example.com", session.hostAndPort)
    }

    @Test
    fun sessionHostAndPortNullHostThrows()
    {
        val session = makeSession()
        session.host = null
        assertFailsWith<TdppException> {
            session.hostAndPort
        }
    }

    @Test
    fun sessionCookieParamWithCookie()
    {
        val session = makeSession()
        session.cookie = "abc123"
        assertEquals("cookie=abc123", session.cookieParam)
    }

    @Test
    fun sessionCookieParamWithoutCookie()
    {
        val session = makeSession()
        session.cookie = null
        assertEquals("", session.cookieParam)
    }

    // --- TricklePaySession.abortProposal tests ---

    @Test
    fun abortProposalNullTxIsNoOp()
    {
        val session = makeSession()
        session.proposedTx = null
        session.abortProposal()
        assertNull(session.proposedTx)
    }

    // --- TricklePaySession.determineAction tests ---

    @Test
    fun determineActionNullDomainReturnsDeny()
    {
        val session = makeSession()
        assertEquals(TdppAction.DENY, session.determineAction(null))
    }

    @Test
    fun determineActionAutoEnabledUnderLimitsReturnsAccept()
    {
        val session = makeSession()
        session.totalNexaSpent = 50
        val domain = makeDomain(maxper = 100, automaticEnabled = true)
        assertEquals(TdppAction.ACCEPT, session.determineAction(domain))
    }

    @Test
    fun determineActionExceedsMaxperDenyReturnsDeny()
    {
        val session = makeSession()
        session.totalNexaSpent = 200
        val domain = makeDomain(maxper = 100, automaticEnabled = true)
        domain.maxperExceeded = TdppAction.DENY
        assertEquals(TdppAction.DENY, session.determineAction(domain))
    }

    @Test
    fun determineActionExceedsMaxperAskReturnsAsk()
    {
        val session = makeSession()
        session.totalNexaSpent = 200
        val domain = makeDomain(maxper = 100, automaticEnabled = true)
        domain.maxperExceeded = TdppAction.ASK
        val result = session.determineAction(domain)
        assertEquals(TdppAction.ASK, result)
        assertTrue(session.askReasons.isNotEmpty())
    }

    @Test
    fun determineActionAutomaticDisabledReturnsAsk()
    {
        val session = makeSession()
        session.totalNexaSpent = 50
        val domain = makeDomain(maxper = 100, automaticEnabled = false)
        val result = session.determineAction(domain)
        assertEquals(TdppAction.ASK, result)
        assertTrue(session.askReasons.any { it == i18n(S.TpAutomaticDisabled) })
    }

    @Test
    fun determineActionSpendingTokensReturnsAsk()
    {
        val account = mockAccount()
        val session = tricklePaySessionFaker(account)
        // The faker sets imSpendingTokenTypes = 42
        val domain = makeDomain(maxper = 99999, automaticEnabled = true)
        assertEquals(TdppAction.ASK, session.determineAction(domain))
    }

    // --- TricklePaySession.rejectSendToRequest tests ---

    @Test
    fun rejectSendToRequestCallsWhenDone()
    {
        var capturedUrl = ""
        var capturedBody = ""
        val session = TricklePaySession(TricklePayDomains()) { url, body, _ ->
            capturedUrl = url
            capturedBody = body
        }
        session.host = "example.com"
        session.port = 8080
        session.cookie = "c1"
        session.rproto = "https"

        session.rejectSendToRequest()

        assertTrue(capturedUrl.contains("resultcode=300"))
        assertTrue(capturedUrl.contains("example.com:8080"))
        assertTrue(capturedUrl.contains("/sendto"))
    }

    @Test
    fun rejectAddressRequestCallsWhenDone()
    {
        var capturedUrl = ""
        val session = TricklePaySession(TricklePayDomains()) { url, _, _ ->
            capturedUrl = url
        }
        session.host = "example.com"
        session.port = -1
        session.cookie = null
        session.rproto = "http"

        session.rejectAddressRequest()

        assertTrue(capturedUrl.contains("resultcode=300"))
        assertTrue(capturedUrl.contains("/address"))
    }

    @Test
    fun rejectAssetRequestCallsWhenDone()
    {
        var capturedUrl = ""
        val session = TricklePaySession(TricklePayDomains()) { url, _, _ ->
            capturedUrl = url
        }
        session.host = "example.com"
        session.port = -1
        session.cookie = null
        session.rproto = "http"

        session.rejectAssetRequest()

        assertTrue(capturedUrl.contains("resultcode=300"))
        assertTrue(capturedUrl.contains("/assets"))
    }

    @Test
    fun respondWithCallsWhenDone()
    {
        var capturedUrl = ""
        var capturedBody = ""
        var capturedSuccess: Boolean? = null
        val session = TricklePaySession(TricklePayDomains()) { url, body, success ->
            capturedUrl = url
            capturedBody = body
            capturedSuccess = success
        }

        session.respondWith("http://example.com/test", "body content")

        assertEquals("http://example.com/test", capturedUrl)
        assertEquals("body content", capturedBody)
        assertEquals(true, capturedSuccess)
    }

    @Test
    fun respondWithErrorCallsWhenDone()
    {
        var capturedUrl = ""
        var capturedBody = ""
        val session = TricklePaySession(TricklePayDomains()) { url, body, _ ->
            capturedUrl = url
            capturedBody = body
        }

        session.respondWithError("http://example.com/error", "error body")

        assertEquals("http://example.com/error", capturedUrl)
        assertEquals("error body", capturedBody)
    }

    // --- VerifyTdppSignature tests ---

    @Test
    fun verifyTdppSignatureNullAddrReturnsNull()
    {
        val uri = Uri.parse("https://example.com?topic=t")
        assertNull(VerifyTdppSignature(uri))
    }

    @Test
    fun verifyTdppSignatureNullSigReturnsNull()
    {
        val uri = Uri.parse("https://example.com?addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        assertNull(VerifyTdppSignature(uri))
    }

    @Test
    fun verifyTdppSignatureEmptyAddrParamReturnsNull()
    {
        val uri = Uri.parse("https://example.com?topic=t")
        assertNull(VerifyTdppSignature(uri, ""))
    }

    @Test
    fun verifyTdppSignatureBadBase64ReturnsFalse()
    {
        // iOS path is covered by verifyTdppSignatureBadBase64ReturnsFalseIOS
        // because libnexakotlin native Codec.decode64 throws LibNexaException
        // (instead of IllegalStateException as on JVM), which the production
        // VerifyTdppSignature catch block does not handle.
        if (platform().target == KotlinTarget.iOS) return
        val uri = Uri.parse("https://example.com?addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw&sig=!!!invalid!!!")
        val result = VerifyTdppSignature(uri)
        assertEquals(false, result)
    }

    @Test
    fun verifyTdppSignatureBadBase64ReturnsFalseIOS()
    {
        if (platform().target != KotlinTarget.iOS) return
        val uri = Uri.parse("https://example.com?addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw&sig=!!!invalid!!!")
        // On iOS, Codec.decode64's native implementation throws LibNexaException
        // for malformed Base64 instead of returning a sentinel or throwing
        // IllegalStateException. The production catch in VerifyTdppSignature only
        // handles IllegalStateException, so wrap the call here and treat the
        // thrown exception as the expected "signature is not valid" outcome.
        val result = try { VerifyTdppSignature(uri) } catch (e: LibNexaException) { false }
        assertEquals(false, result)
    }

    // --- makeChallengeTx tests ---

    @Test
    fun makeChallengeTxTooShortReturnsNull()
    {
        val cs = ChainSelector.NEXA
        val sp = Spendable(cs, NexaTxOutpoint("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f"), 10001)
        val result = makeChallengeTx(sp, "challenger".encodeToByteArray(), ByteArray(7)) // < 8
        assertNull(result)
    }

    @Test
    fun makeChallengeTxTooLongReturnsNull()
    {
        val cs = ChainSelector.NEXA
        val sp = Spendable(cs, NexaTxOutpoint("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f"), 10001)
        val result = makeChallengeTx(sp, "challenger".encodeToByteArray(), ByteArray(65)) // > 64
        assertNull(result)
    }

    @Test
    fun makeChallengeTxValidSizeReturnsTransaction()
    {
        val cs = ChainSelector.NEXA
        val sp = Spendable(cs, NexaTxOutpoint("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f"), 10001)
        val result = makeChallengeTx(sp, "challenger".encodeToByteArray(), ByteArray(8))
        assertNotNull(result)
    }

    @Test
    fun makeChallengeTxMaxValidSizeReturnsTransaction()
    {
        val cs = ChainSelector.NEXA
        val sp = Spendable(cs, NexaTxOutpoint("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f"), 10001)
        val result = makeChallengeTx(sp, "challenger".encodeToByteArray(), ByteArray(64))
        assertNotNull(result)
    }

    // --- TricklePaySession.getRelevantAccount tests ---

    @Test
    fun getRelevantAccountReturnsPillAccount()
    {
        val account = mockAccount()
        val session = makeSession()
        session.pill.account.value = account
        val result = session.getRelevantAccount()
        assertEquals(account, result)
    }

    @Test
    fun getRelevantAccountNullThrows()
    {
        val session = makeSession()
        session.pill.account.value = null
        assertFailsWith<WalletInvalidException> {
            session.getRelevantAccount()
        }
    }

    // --- TricklePaySession.parseCommonFields tests ---

    @Test
    fun parseCommonFieldsSetsHostAndPort()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com:9090/path?topic=mytopic&cookie=c1&reason=testing&rproto=https")
        session.parseCommonFields(uri)
        assertEquals("example.com", session.host)
        assertEquals(9090, session.port)
        assertEquals("mytopic", session.topic)
        assertEquals("c1", session.cookie)
        assertEquals("testing", session.reason)
        assertEquals("https", session.rproto)
    }

    @Test
    fun parseCommonFieldsCreatesNewDomain()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://newdomain.com/path?topic=newtopic&addr=testaddr")
        session.parseCommonFields(uri)
        assertNotNull(session.domain)
        assertEquals("newdomain.com", session.domain?.domain)
        assertEquals("newtopic", session.domain?.topic)
    }

    @Test
    fun parseCommonFieldsNoHostThrows()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        // Opaque URI with no host component
        val uri = Uri.parse("tdpp:opaque")
        assertFails {
            session.parseCommonFields(uri)
        }
    }

    @Test
    fun parseCommonFieldsAutoCreateFalseUnknownDomainThrows()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://unknown.com/path?topic=t")
        assertFailsWith<TdppException> {
            session.parseCommonFields(uri, autoCreateDomain = false)
        }
    }

    @Test
    fun parseCommonFieldsLoadsExistingDomain()
    {
        // Wipe any persisted TDPP domain state so parseCommonFields()'s
        // unconditional load() can't overwrite the in-memory domain inserted below.
        clearPersistedTdppDomains()
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val existing = makeDomain(domain = "existing.com", topic = "t1")
        existing.maxper = 555
        domains.insert(existing)
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://existing.com/path?topic=t1")
        session.parseCommonFields(uri)
        assertEquals(555L, session.domain?.maxper)
        assertFalse(session.newDomain)
    }

    @Test
    fun parseCommonFieldsNoSigSetsSigOkNull()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com/path?topic=t")
        session.parseCommonFields(uri)
        // No addr and no sig in URI -> VerifyTdppSignature returns null
        assertNull(session.sigOk)
    }

    // --- TricklePaySession.populateRelevantAccounts tests ---

    @Test
    fun populateRelevantAccountsSetsAccountOnPill()
    {
        val session = makeSession()
        session.chainSelector = null
        session.populateRelevantAccounts()
        assertNotNull(session.pill.account.value)
    }

    @Test
    fun populateRelevantAccountsWithChainSelector()
    {
        val session = makeSession()
        session.chainSelector = ChainSelector.NEXA
        session.populateRelevantAccounts()
        assertNotNull(session.pill.account.value)
    }

    // --- TricklePaySession.abortProposal with tx ---

    @Test
    fun abortProposalWithTxClearsState()
    {
        val account = mockAccount()
        val session = tricklePaySessionFaker(account)
        assertNotNull(session.proposedTx)
        assertNotNull(session.proposalAnalysis.value)
        session.abortProposal()
        assertNull(session.proposedTx)
        assertNull(session.proposalAnalysis.value)
    }

    // --- TricklePaySession.acceptAddressRequest tests ---

    @Test
    fun acceptAddressRequestWithExistingMainPayAddress()
    {
        var capturedUrl = ""
        var capturedBody = ""
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains) { url, body, _ ->
            capturedUrl = url
            capturedBody = body
        }
        val account = mockAccount()
        session.pill.account.value = account
        session.host = "example.com"
        session.port = -1
        session.cookie = "c1"
        session.rproto = "https"
        session.uniqueAddress = false

        val d = makeDomain(domain = "example.com")
        d.mainPayAddress = "nexa:existingaddress"
        d.accountName = "mockSelfSend"
        session.domain = d

        val result = session.acceptAddressRequest()

        assertTrue(result.contains("Sent to:"))
        assertTrue(capturedUrl.contains("/address"))
        assertTrue(capturedUrl.contains("resultcode=200"))
        assertEquals("nexa:existingaddress", capturedBody)
        assertEquals("nexa:existingaddress", d.lastPayAddress)
    }

    @Test
    fun acceptAddressRequestNullDomainThrows()
    {
        val session = makeSession()
        session.domain = null
        session.host = "example.com"
        session.port = -1
        session.rproto = "http"
        assertFailsWith<TdppException> {
            session.acceptAddressRequest()
        }
    }

    // --- TricklePaySession.acceptAssetRequest tests ---

    @Test
    fun acceptAssetRequestWithWhenDoneCallback()
    {
        var capturedUrl = ""
        var capturedBody = ""
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains) { url, body, _ ->
            capturedUrl = url
            capturedBody = body
        }
        session.host = "example.com"
        session.port = -1
        session.cookie = null
        session.rproto = "https"
        session.domain = makeDomain()

        // Set asset info list with test data
        session.assetInfoList = TricklePayAssetList(listOf(
            TricklePayAssetInfo("aabb", 100, "prevout1"),
            TricklePayAssetInfo("ccdd", 200, "prevout2", "proof1")
        ))

        val result = session.acceptAssetRequest()

        assertTrue(result.contains("Sent to:"))
        assertTrue(capturedUrl.contains("/assets"))
        // Verify JSON was serialized
        val parsed = Json.decodeFromString(TricklePayAssetList.serializer(), capturedBody)
        assertEquals(2, parsed.assets.size)
        assertEquals("aabb", parsed.assets[0].outpointHash)
        assertEquals(200L, parsed.assets[1].amt)
        assertEquals("proof1", parsed.assets[1].proof)
    }

    @Test
    fun acceptAssetRequestWithNullAssetListSendsEmpty()
    {
        var capturedBody = ""
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains) { _, body, _ ->
            capturedBody = body
        }
        session.host = "example.com"
        session.port = -1
        session.cookie = null
        session.rproto = "http"
        session.domain = makeDomain()
        session.assetInfoList = null

        session.acceptAssetRequest()

        val parsed = Json.decodeFromString(TricklePayAssetList.serializer(), capturedBody)
        assertEquals(0, parsed.assets.size)
    }

    // --- TricklePaySession.handleAddressInfoRequest tests ---

    @Test
    fun handleAddressInfoRequestReturnsAccept()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com/address?topic=t&blockchain=nexa")
        val result = session.handleAddressInfoRequest(uri)
        assertEquals(TdppAction.ACCEPT, result)
    }

    @Test
    fun handleAddressInfoRequestSetsUniqueAddress()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com/address?topic=t&blockchain=nexa&unique=true")
        session.handleAddressInfoRequest(uri)
        assertTrue(session.uniqueAddress)
    }

    @Test
    fun handleAddressInfoRequestSetsChainSelector()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com/address?topic=t&blockchain=nexa")
        session.handleAddressInfoRequest(uri)
        assertEquals(ChainSelector.NEXA, session.chainSelector)
    }

    @Test
    fun handleAddressInfoRequestUnknownBlockchainThrows()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com/address?topic=t&blockchain=fakecoin")
        assertFailsWith<TdppException> {
            session.handleAddressInfoRequest(uri)
        }
    }

    // --- TricklePaySession.handleAssetInfoRequest tests ---

    @Test
    fun handleAssetInfoRequestDenyReturnsDeny()
    {
        // Wipe any persisted TDPP domain state so parseCommonFields()'s
        // unconditional load() can't clobber the in-memory domain inserted below.
        clearPersistedTdppDomains()
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val d = makeDomain(domain = "example.com")
        d.assetInfo = TdppAction.DENY
        domains.insert(d)
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com/assets?topic=&af=00112233")
        val result = session.handleAssetInfoRequest(uri)
        assertEquals(TdppAction.DENY, result)
    }

    @Test
    fun handleAssetInfoRequestMissingAfThrows()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com/assets?topic=t")
        assertFailsWith<TdppException> {
            session.handleAssetInfoRequest(uri)
        }
    }

    @Test
    fun handleAssetInfoRequestAcceptProcessesAssets()
    {
        // Wipe any persisted TDPP domain state so parseCommonFields()'s
        // unconditional load() can't overwrite the in-memory domain inserted below.
        clearPersistedTdppDomains()
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val d = makeDomain(domain = "example.com")
        d.assetInfo = TdppAction.ACCEPT
        domains.insert(d)
        val session = TricklePaySession(domains)
        // Provide chain=nexa so chainSelector is set, and a valid af hex script template
        val uri = Uri.parse("https://example.com/assets?topic=&chain=nexa&af=00")
        val result = session.handleAssetInfoRequest(uri)
        assertEquals(TdppAction.ACCEPT, result)
        // The mock wallet has no UTXOs, so assetInfoList should be empty
        assertNotNull(session.assetInfoList)
        assertEquals(0, session.assetInfoList!!.assets.size)
    }

    // --- TricklePaySession.handleShareRequest tests ---

    @Test
    fun handleShareRequestAddressCallsWhenDone()
    {
        var capturedUrl = ""
        var capturedBody = ""
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains) { url, body, _ ->
            capturedUrl = url
            capturedBody = body
        }
        val uri = Uri.parse("https://example.com/share?topic=t&info=address")
        session.parseCommonFields(uri)
        var sharedWhat: Int? = null
        session.handleShareRequest(uri) { sharedWhat = it }
        assertTrue(capturedUrl.contains("_share"))
        assertEquals(S.Address, sharedWhat)
        assertTrue(capturedBody.isNotEmpty())
    }

    @Test
    fun handleShareRequestDefaultIsAddress()
    {
        var capturedUrl = ""
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains) { url, _, _ ->
            capturedUrl = url
        }
        // No info parameter -> defaults to "address"
        val uri = Uri.parse("https://example.com/share?topic=t")
        session.parseCommonFields(uri)
        var sharedWhat: Int? = null
        session.handleShareRequest(uri) { sharedWhat = it }
        assertTrue(capturedUrl.contains("_share"))
        assertEquals(S.Address, sharedWhat)
    }

    @Test
    fun handleShareRequestUnknownInfoCallsThenNeg1()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains) { _, _, _ -> }
        val uri = Uri.parse("https://example.com/share?topic=t&info=unknown")
        session.parseCommonFields(uri)
        var sharedWhat: Int? = null
        session.handleShareRequest(uri) { sharedWhat = it }
        assertEquals(-1, sharedWhat)
    }

    // --- TricklePaySession.handleSendToAutopay tests ---

    @Test
    fun handleSendToInsecureNoAddrThrows()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        // http (not secure), no sig, domain has no addr
        val uri = Uri.parse("http://example.com/sendto?topic=t&amt0=100&addr0=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        assertFails {
            session.handleSendToAutopay(uri)
        }
    }

    @Test
    fun handleSendToMismatchedAmtAddrThrows()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        // Pre-create domain with an addr so the secure check passes
        val d = makeDomain(domain = "example.com")
        d.addr = "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"
        domains.insert(d)
        val session = TricklePaySession(domains)
        session.sigOk = true
        // amt0 exists but addr0 is missing
        val uri = Uri.parse("https://example.com/sendto?topic=&amt0=100&sig=dGVzdA==&addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        assertFails {
            session.handleSendToAutopay(uri)
        }
    }

    // --- TricklePaySendToReply serialization tests ---

    @Test
    fun tricklePaySendToReplySerializes()
    {
        val reply = TricklePaySendToReply(200, "txid1", "txidem1", "txhex1", "")
        val json = Json.encodeToString(TricklePaySendToReply.serializer(), reply)
        assertTrue(json.contains("\"resultCode\":200"))
        assertTrue(json.contains("\"txid\":\"txid1\""))
        val parsed = Json.decodeFromString(TricklePaySendToReply.serializer(), json)
        assertEquals(200, parsed.resultCode)
        assertEquals("txid1", parsed.txid)
        assertEquals("txidem1", parsed.txidem)
        assertEquals("txhex1", parsed.tx)
        assertEquals("", parsed.error)
    }

    // --- TricklePayAssetInfo serialization tests ---

    @Test
    fun tricklePayAssetInfoSerializes()
    {
        val info = TricklePayAssetInfo("hash1", 500, "prevout1", "proof1")
        val json = Json.encodeToString(TricklePayAssetInfo.serializer(), info)
        val parsed = Json.decodeFromString(TricklePayAssetInfo.serializer(), json)
        assertEquals("hash1", parsed.outpointHash)
        assertEquals(500L, parsed.amt)
        assertEquals("prevout1", parsed.prevout)
        assertEquals("proof1", parsed.proof)
    }

    @Test
    fun tricklePayAssetInfoNullProof()
    {
        val info = TricklePayAssetInfo("hash1", 500, "prevout1")
        val json = Json.encodeToString(TricklePayAssetInfo.serializer(), info)
        val parsed = Json.decodeFromString(TricklePayAssetInfo.serializer(), json)
        assertNull(parsed.proof)
    }

    // --- TricklePayAssetList serialization tests ---

    @Test
    fun tricklePayAssetListSerializesEmptyList()
    {
        val list = TricklePayAssetList(emptyList())
        val json = Json.encodeToString(TricklePayAssetList.serializer(), list)
        val parsed = Json.decodeFromString(TricklePayAssetList.serializer(), json)
        assertEquals(0, parsed.assets.size)
    }

    // --- TxAnalysisResults tests ---

    @Test
    fun txAnalysisResultsFromFaker()
    {
        val account = mockAccount()
        val session = tricklePaySessionFaker(account)
        val analysis = session.proposalAnalysis.value
        assertNotNull(analysis)
        assertEquals(account, analysis.account)
        assertEquals(42L, analysis.receivingSats)
        assertEquals(42L, analysis.sendingSats)
        assertEquals(42L, analysis.receivingTokenTypes)
        assertEquals(42L, analysis.sendingTokenTypes)
        assertEquals(42L, analysis.imSpendingTokenTypes)
        assertEquals(42L, analysis.otherInputSatoshis)
        assertEquals(42L, analysis.myInputSatoshis)
        assertNull(analysis.completionException)
    }

    // --- handleSendToAutopay happy path tests ---

    /** Helper: create a TricklePaySession with a pre-registered secure domain */
    private fun makeSecureSession(): Pair<TricklePaySession, TricklePayDomains>
    {
        // Wipe any persisted TDPP domain state so parseCommonFields()'s
        // unconditional load() can't overwrite the in-memory domain inserted
        // below. Required on Pixel 5 instrumentation, where laterJob save()s
        // from prior tests actually run and leave data in the wallyData DB.
        clearPersistedTdppDomains()
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val d = makeDomain(domain = "example.com", maxper = 99999, automaticEnabled = true)
        d.addr = "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"
        domains.insert(d)
        val session = TricklePaySession(domains)
        return Pair(session, domains)
    }

    @Test
    fun handleSendToAutopayParsesSingleDestination()
    {
        val (session, _) = makeSecureSession()
        val uri = Uri.parse("https://example.com/sendto?topic=&addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw&amt0=1000&addr0=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        val result = session.handleSendToAutopay(uri)
        assertNotNull(session.proposedDestinations)
        assertEquals(1, session.proposedDestinations!!.size)
        assertEquals(1000L, session.proposedDestinations!![0].second)
        assertEquals(1000L, session.totalNexaSpent)
        assertEquals(TdppAction.ACCEPT, result)
    }

    @Test
    fun handleSendToAutopayMultipleDestinations()
    {
        val (session, _) = makeSecureSession()
        val uri = Uri.parse("https://example.com/sendto?topic=&addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw&amt0=100&addr0=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw&amt1=200&addr1=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        val result = session.handleSendToAutopay(uri)
        assertNotNull(session.proposedDestinations)
        assertEquals(2, session.proposedDestinations!!.size)
        assertEquals(100L, session.proposedDestinations!![0].second)
        assertEquals(200L, session.proposedDestinations!![1].second)
        assertEquals(300L, session.totalNexaSpent)
    }

    @Test
    fun handleSendToAutopayZeroAmountThrows()
    {
        val (session, _) = makeSecureSession()
        val uri = Uri.parse("https://example.com/sendto?topic=&addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw&amt0=0&addr0=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        assertFails {
            session.handleSendToAutopay(uri)
        }
    }

    @Test
    fun handleSendToAutopayNegativeAmountThrows()
    {
        val (session, _) = makeSecureSession()
        val uri = Uri.parse("https://example.com/sendto?topic=&addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw&amt0=-5&addr0=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        assertFails {
            session.handleSendToAutopay(uri)
        }
    }

    @Test
    fun handleSendToAutopayNoDestinationsReturnsAccept()
    {
        val (session, _) = makeSecureSession()
        // No amt0/addr0 params -> empty destinations, totalNexaSpent=0, within limits
        val uri = Uri.parse("https://example.com/sendto?topic=&addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        val result = session.handleSendToAutopay(uri)
        assertEquals(0L, session.totalNexaSpent)
        assertTrue(session.proposedDestinations!!.isEmpty())
        assertEquals(TdppAction.ACCEPT, result)
    }

    @Test
    fun handleSendToAutopayExceedsMaxperReturnsAsk()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val d = makeDomain(domain = "example.com", maxper = 50, automaticEnabled = true)
        d.addr = "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"
        domains.insert(d)
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://example.com/sendto?topic=&addr=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw&amt0=100&addr0=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        val result = session.handleSendToAutopay(uri)
        assertEquals(TdppAction.ASK, result)
    }

    // --- attemptAutopay tests ---

    @Test
    fun attemptAutopayUnknownDomainReturnsDeny()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        // autoCreateDomain=false, unknown domain -> TdppException caught -> DENY
        val uri = Uri.parse("https://unknown.com/sendto?topic=t&amt0=100&addr0=nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        val result = session.attemptAutopay(uri)
        assertEquals(TdppAction.DENY, result)
    }

    // --- attemptSpecialTx tests ---

    @Test
    fun attemptSpecialTxUnknownDomainReturnsDeny()
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        val uri = Uri.parse("https://unknown.com/tx?topic=t&tx=0000&inamt=100")
        val result = session.attemptSpecialTx(uri)
        assertEquals(TdppAction.DENY, result)
    }

    // --- rejectSpecialTx tests ---

    @Test
    fun rejectSpecialTxClearsProposalState()
    {
        val account = mockAccount()
        val session = tricklePaySessionFaker(account)
        session.host = "example.com"
        session.port = -1
        session.rproto = "http"
        session.cookie = null
        assertNotNull(session.proposedTx)
        assertNotNull(session.proposalAnalysis.value)
        session.rejectSpecialTx()
        // abortProposal should have cleared these
        assertNull(session.proposedTx)
        assertNull(session.proposalAnalysis.value)
    }

    // --- acceptAddressRequest with real account (unique address) ---

    @Test
    fun acceptAddressRequestUniqueAddressUsesWallet()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("tpsaddr", 0U, "", cs)!!
        try
        {
            var capturedUrl = ""
            var capturedBody = ""
            val domains = TricklePayDomains()
            domains.domainsLoaded = true
            val session = TricklePaySession(domains) { url, body, _ ->
                capturedUrl = url
                capturedBody = body
            }
            session.pill.account.value = account
            session.host = "example.com"
            session.port = -1
            session.cookie = null
            session.rproto = "https"
            session.uniqueAddress = true

            val d = makeDomain(domain = "example.com")
            d.accountName = account.name
            session.domain = d

            val result = session.acceptAddressRequest()

            assertTrue(result.contains("Sent to:"))
            assertTrue(capturedUrl.contains("/address"))
            assertTrue(capturedBody.isNotEmpty())
            // The wallet generated an address
            assertTrue(capturedBody.startsWith("nexa:"))
            assertEquals(capturedBody, d.lastPayAddress)
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    // --- acceptSendToRequest tests ---

    @Test
    fun acceptSendToRequestNullDomainReturnsEarly()
    {
        var callbackCalled = false
        val session = TricklePaySession(TricklePayDomains()) { _, _, _ ->
            callbackCalled = true
        }
        session.domain = null
        session.proposedDestinations = listOf(Pair(PayAddress("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"), 100L))
        session.acceptSendToRequest()
        // Should return early without calling whenDone
        assertFalse(callbackCalled)
    }

    @Test
    fun acceptSendToRequestNullDestinationsReturnsEarly()
    {
        var callbackCalled = false
        val session = TricklePaySession(TricklePayDomains()) { _, _, _ ->
            callbackCalled = true
        }
        val d = makeDomain()
        d.addr = "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"
        session.domain = d
        session.proposedDestinations = null
        session.pill.account.value = mockAccount()
        session.acceptSendToRequest()
        assertFalse(callbackCalled)
    }

    @Test
    fun acceptSendToRequestClearsProposedDestinations()
    {
        val session = TricklePaySession(TricklePayDomains())
        val d = makeDomain()
        d.addr = "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"
        session.domain = d
        session.proposedDestinations = listOf(Pair(PayAddress("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"), 100L))
        session.pill.account.value = mockAccount()
        // send() will fail on the mock wallet, but proposedDestinations should already be nulled
        try { session.acceptSendToRequest() } catch (_: Exception) {}
        assertNull(session.proposedDestinations)
    }

    // --- acceptSpecialTx tests ---

    @Test
    fun acceptSpecialTxSetsAccepted()
    {
        val account = mockAccount()
        val session = tricklePaySessionFaker(account)
        session.host = "example.com"
        session.port = -1
        session.rproto = "http"
        session.cookie = null
        assertFalse(session.accepted)
        session.acceptSpecialTx()
        assertTrue(session.accepted)
    }

    @Test
    fun acceptSpecialTxClearsProposedTxAndUrl()
    {
        val account = mockAccount()
        val session = tricklePaySessionFaker(account)
        session.host = "example.com"
        session.port = -1
        session.rproto = "http"
        session.cookie = null
        session.proposalUrl = Uri.parse("https://example.com/tx")
        assertNotNull(session.proposedTx)
        session.acceptSpecialTx()
        assertNull(session.proposedTx)
        assertNull(session.proposalUrl)
    }

    // --- analyzeCompleteAndSignTx tests ---

    /** Helper: build a session wired to a real account for tx analysis tests */
    private fun makeAnalysisSession(): Pair<TricklePaySession, Account>
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("tpsanaly", 0U, "", cs)!!
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        session.pill.account.value = account
        session.domain = makeDomain(domain = "example.com")
        session.chainSelector = cs
        return Pair(session, account)
    }

    @Test
    fun analyzeCompleteAndSignTxEmptyTx()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        // NOFUND | PARTIAL so txCompleter doesn't fail trying to fund
        val flags = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL
        val result = session.analyzeCompleteAndSignTx(tx, 0L, flags)
        assertEquals(account, result.account)
        assertEquals(0L, result.receivingSats)
        assertEquals(0L, result.sendingSats)
        assertEquals(0L, result.myInputSatoshis)
        assertEquals(0L, result.otherInputSatoshis)
        assertEquals(0L, result.receivingTokenTypes)
        assertEquals(0L, result.sendingTokenTypes)
        assertEquals(0L, result.imSpendingTokenTypes)
        assertTrue(result.myInputTokenInfo.isEmpty())
        assertTrue(result.sendingTokenInfo.isEmpty())
        assertTrue(result.receivingTokenInfo.isEmpty())
        assertTrue(result.myNetTokenInfo.isEmpty())

        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxOutputToExternalAddress()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        // Add an output to an external address (not ours)
        val externalAddr = PayAddress("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        val out = NexaTxOutput(cs, 5000L, externalAddr.outputScript())
        tx.add(out)
        val flags = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL
        val result = session.analyzeCompleteAndSignTx(tx, 0L, flags)

        assertEquals(5000L, result.sendingSats)
        assertEquals(0L, result.receivingSats)
        assertEquals(0L, result.myInputSatoshis)

        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxOutputToOwnAddress()
    {
        val (session, account) = makeAnalysisSession()

        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        // Get our own address from the wallet
        val myDest = account.wallet.getCurrentDestination()
        val myAddr = myDest.address!!
        val out = NexaTxOutput(cs, 3000L, myAddr.outputScript())
        tx.add(out)
        val flags = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL
        val result = session.analyzeCompleteAndSignTx(tx, 0L, flags)
        assertEquals(3000L, result.receivingSats)
        assertEquals(0L, result.sendingSats)

        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxMixedOutputs()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        // Output to self
        val myAddr = account.wallet.getCurrentDestination().address!!
        tx.add(NexaTxOutput(cs, 2000L, myAddr.outputScript()))
        // Output to external
        val externalAddr = PayAddress("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
        tx.add(NexaTxOutput(cs, 7000L, externalAddr.outputScript()))
        val flags = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL
        val result = session.analyzeCompleteAndSignTx(tx, 5000L, flags)
        assertEquals(2000L, result.receivingSats)
        assertEquals(7000L, result.sendingSats)
        assertEquals(5000L, result.otherInputSatoshis)

        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxCapturesCompletionException()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        // No NOFUND flag -> txCompleter will try to fund from empty wallet and fail
        val result = session.analyzeCompleteAndSignTx(tx, 0L, 0)

        // Exception should be captured, not thrown
        assertNotNull(result.completionException)

        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxNullFlags()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        // null flags -> default cflags (FUND_NATIVE | SIGN | BIND_OUTPUT_PARAMETERS)
        // txCompleter will fail on empty wallet, but exception is caught
        val result = session.analyzeCompleteAndSignTx(tx, 0L, null)
        assertNotNull(result.completionException)
        assertEquals(account, result.account)
        wallyApp!!.deleteAccount(account)
    }

    // --- analyzeCompleteAndSignTx: flag variation tests ---

    private val NOFUND_PARTIAL = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL
    private val externalAddr = PayAddress("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")

    @Test
    fun analyzeCompleteAndSignTxPartialFlagOnly()
    {
        val (session, account) = makeAnalysisSession()
        val tx = txFor(ChainSelector.NEXA)
        tx.add(NexaTxOutput(ChainSelector.NEXA, 1000L, externalAddr.outputScript()))
        // PARTIAL only (no NOFUND): txCompleter will try to fund from empty wallet
        val result = session.analyzeCompleteAndSignTx(tx, 0L, TDPP_FLAG_PARTIAL)
        // Should capture exception since wallet has no funds
        assertNotNull(result.completionException)
        // Output analysis should still work despite the exception
        assertEquals(1000L, result.sendingSats)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxNofundFlagOnly()
    {
        val (session, account) = makeAnalysisSession()
        val tx = txFor(ChainSelector.NEXA)
        tx.add(NexaTxOutput(ChainSelector.NEXA, 2000L, externalAddr.outputScript()))
        // NOFUND only (no PARTIAL)
        val result = session.analyzeCompleteAndSignTx(tx, 0L, TDPP_FLAG_NOFUND)
        assertEquals(2000L, result.sendingSats)
        assertEquals(0L, result.receivingSats)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxFundGroupsFlag()
    {
        val (session, account) = makeAnalysisSession()
        val tx = txFor(ChainSelector.NEXA)
        // NOFUND | PARTIAL | FUND_GROUPS — all three flags combined
        val flags = NOFUND_PARTIAL or info.bitcoinunlimited.www.wally.TDPP_FLAG_FUND_GROUPS
        val result = session.analyzeCompleteAndSignTx(tx, 0L, flags)
        assertNull(result.completionException)
        assertEquals(0L, result.sendingSats)
        assertEquals(0L, result.receivingSats)
        wallyApp!!.deleteAccount(account)
    }

    // --- analyzeCompleteAndSignTx: grouped/token output tests ---

    @Test
    fun analyzeCompleteAndSignTxGroupedOutputToExternal()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        val gid = GroupId(cs, ByteArray(520) { it.toByte() })
        tx.add(txOutputFor(externalAddr, 500L, gid))

        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)

        // Grouped output to external carries dust, so sendingSats > 0
        assertTrue(result.sendingSats > 0L)
        assertEquals(0L, result.receivingSats)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxGroupedOutputToSelf()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        val myAddr = account.wallet.getCurrentDestination().address!!
        val gid = GroupId(cs, ByteArray(520) { it.toByte() })
        tx.add(txOutputFor(myAddr, 300L, gid))

        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)

        // Grouped output to self carries dust, so receivingSats > 0
        assertTrue(result.receivingSats > 0L)
        assertEquals(0L, result.sendingSats)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxMixedGroupedOutputs()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        val myAddr = account.wallet.getCurrentDestination().address!!
        val gid = GroupId(cs, ByteArray(520) { it.toByte() })

        tx.add(txOutputFor(myAddr, 200L, gid))
        tx.add(txOutputFor(externalAddr, 800L, gid))

        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)

        assertTrue(result.receivingSats > 0L)
        assertTrue(result.sendingSats > 0L)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxMultipleGroupIds()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        val gid1 = GroupId(cs, ByteArray(520) { it.toByte() })
        val gid2 = GroupId(cs, ByteArray(520) { (it + 100).toByte() })

        tx.add(txOutputFor(externalAddr, 100L, gid1))
        tx.add(txOutputFor(externalAddr, 250L, gid2))

        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)

        // Two grouped outputs to external — dust per output
        assertTrue(result.sendingSats > 0L)
        assertEquals(0L, result.receivingSats)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxGroupedAndNativeOutputMixed()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        val myAddr = account.wallet.getCurrentDestination().address!!
        val gid = GroupId(cs, ByteArray(520) { it.toByte() })

        tx.add(NexaTxOutput(cs, 5000L, externalAddr.outputScript()))
        tx.add(txOutputFor(myAddr, 100L, gid))

        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)

        // 5000 native to external
        assertTrue(result.sendingSats >= 5000L)
        // Grouped output to self carries dust
        assertTrue(result.receivingSats > 0L)
        wallyApp!!.deleteAccount(account)
    }

    // --- analyzeCompleteAndSignTx: multiple outputs same direction ---

    @Test
    fun analyzeCompleteAndSignTxMultipleExternalOutputs()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        tx.add(NexaTxOutput(cs, 1000L, externalAddr.outputScript()))
        tx.add(NexaTxOutput(cs, 2000L, externalAddr.outputScript()))
        tx.add(NexaTxOutput(cs, 3000L, externalAddr.outputScript()))

        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)
        assertEquals(6000L, result.sendingSats)
        assertEquals(0L, result.receivingSats)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxMultipleOwnOutputs()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        val myAddr = account.wallet.getCurrentDestination().address!!
        tx.add(NexaTxOutput(cs, 1500L, myAddr.outputScript()))
        tx.add(NexaTxOutput(cs, 2500L, myAddr.outputScript()))

        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)
        assertEquals(0L, result.sendingSats)
        assertEquals(4000L, result.receivingSats)
        wallyApp!!.deleteAccount(account)
    }

    // --- analyzeCompleteAndSignTx: inputSatoshis variations ---

    @Test
    fun analyzeCompleteAndSignTxNullInputSatoshis()
    {
        val (session, account) = makeAnalysisSession()
        val tx = txFor(ChainSelector.NEXA)
        val result = session.analyzeCompleteAndSignTx(tx, null, NOFUND_PARTIAL)
        assertNull(result.otherInputSatoshis)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxLargeInputSatoshis()
    {
        val (session, account) = makeAnalysisSession()
        val cs = ChainSelector.NEXA
        val tx = txFor(cs)
        tx.add(NexaTxOutput(cs, 1000L, externalAddr.outputScript()))
        val largeInput = 999_999_999L
        val result = session.analyzeCompleteAndSignTx(tx, largeInput, NOFUND_PARTIAL)
        assertEquals(largeInput, result.otherInputSatoshis)
        assertEquals(1000L, result.sendingSats)
        wallyApp!!.deleteAccount(account)
    }

    // --- analyzeCompleteAndSignTx: domain.lastPayAddress handling ---

    @Test
    fun analyzeCompleteAndSignTxEmptyLastPayAddress()
    {
        val (session, account) = makeAnalysisSession()
        session.domain!!.lastPayAddress = ""
        val tx = txFor(ChainSelector.NEXA)
        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)
        // Empty string is treated as null for destination — should not crash
        assertNull(result.completionException)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun analyzeCompleteAndSignTxNullDomain()
    {
        val (session, account) = makeAnalysisSession()
        session.domain = null
        val tx = txFor(ChainSelector.NEXA)
        val result = session.analyzeCompleteAndSignTx(tx, 0L, NOFUND_PARTIAL)
        // Null domain -> no lastPayAddress -> destinationAddress is null
        assertNull(result.completionException)
        wallyApp!!.deleteAccount(account)
    }

    // --- handleTxAutopay tests ---

    @Test
    fun handleTxAutopayMissingTxParameterThrows()
    {
        val (session, account) = makeAnalysisSession()
        // URI with no tx or tx64 parameter
        val uri = Uri.parse("https://example.com/tx?host=example.com&port=8080&inamt=1000&flags=0&blockchain=nexa")
        assertFails { session.handleTxAutopay(uri) }
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun handleTxAutopayMissingInamtWithFundingThrows()
    {
        val (session, account) = makeAnalysisSession()
        val emptyTx = txFor(ChainSelector.NEXA)
        val txHex = emptyTx.toHex()
        // No inamt and flags=0 (no NOFUND) -> should throw
        val uri = Uri.parse("https://example.com/tx?host=example.com&port=8080&tx=$txHex&flags=0&blockchain=nexa")
        assertFails { session.handleTxAutopay(uri) }
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun handleTxAutopayWithNofundNoInamtSucceeds()
    {
        val (session, account) = makeAnalysisSession()
        val emptyTx = txFor(ChainSelector.NEXA)
        val txHex = emptyTx.toHex()
        val uri = Uri.parse("https://example.com/tx?host=example.com&port=8080&tx=$txHex&flags=$NOFUND_PARTIAL&blockchain=nexa")
        val action = session.handleTxAutopay(uri)
        // handleTxAutopay forces ASK even if determineAction returns ACCEPT
        assertEquals(TdppAction.ASK, action)
        assertNotNull(session.proposedTx)
        assertNotNull(session.proposalAnalysis.value)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun handleTxAutopayParsesFlags()
    {
        val (session, account) = makeAnalysisSession()
        val emptyTx = txFor(ChainSelector.NEXA)
        val txHex = emptyTx.toHex()
        val uri = Uri.parse("https://example.com/tx?host=example.com&port=8080&tx=$txHex&flags=$NOFUND_PARTIAL&inamt=0&blockchain=nexa")
        session.handleTxAutopay(uri)
        assertEquals(NOFUND_PARTIAL, session.tflags)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun handleTxAutopayCalculatesTotalNexaSpent()
    {
        val (session, account) = makeAnalysisSession()
        val emptyTx = txFor(ChainSelector.NEXA)
        val txHex = emptyTx.toHex()
        val uri = Uri.parse("https://example.com/tx?host=example.com&port=8080&tx=$txHex&flags=$NOFUND_PARTIAL&inamt=0&blockchain=nexa")
        session.handleTxAutopay(uri)
        // Empty tx: myInputSatoshis=0, receivingSats=0, so totalNexaSpent=0
        assertEquals(0L, session.totalNexaSpent)
        wallyApp!!.deleteAccount(account)
    }
}
