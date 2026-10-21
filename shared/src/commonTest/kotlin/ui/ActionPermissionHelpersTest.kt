package ui

import com.eygraber.uri.Uri
import dev.mokkery.answering.returns
import dev.mokkery.every
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import org.nexa.libnexakotlin.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * On iOS/JVM (hasNativeTitleBar=false) alerts land in the in-memory `alerts` journal.
 * On Android they are routed to the native dialog owned by `currentActivity`, which a unit
 * test cannot observe, so there we can only assert that nothing leaked into the journal.
 */
internal fun assertAlerted(before: Int, expectedMsg: String, expectedLevel: AlertLevel = AlertLevel.ERROR)
{
    if (platform().target == KotlinTarget.Android)
    {
        assertEquals(0, alerts.size - before, "Android must not append to the alerts journal")
        return
    }
    val added = alerts.subList(before, alerts.size).toList()
    assertTrue(added.any { it.msg == expectedMsg && it.level == expectedLevel },
      "expected $expectedLevel '$expectedMsg', got ${added.map { it.level to it.msg }}")
}

/**
 * Tests for the non-composable helpers in ActionPermissionScreens.kt.
 *
 * These drive the identity helpers directly rather than through a screen, so the session is
 * always built with `autoHandle = false` and a `whenDone` continuation -- that combination is
 * what suppresses the outgoing http request in every branch, letting the test observe the
 * request the wallet *would* have made.
 */
@OptIn(ExperimentalUnsignedTypes::class)
class ActionPermissionHelpersTest
{
    val cs = ChainSelector.NEXA

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
        nav.switch(ScreenId.Home)
    }

    @AfterTest
    fun afterTest()
    {
        awaitDeletionsComplete()
        wallyApp = null
    }

    private val account: Account get() = wallyApp!!.focusedAccount.value!!
    private val wallet: Wallet get() = account.wallet

    /** A destination whose key material is supplied directly, so no HD derivation is needed */
    private fun localDestination(fill: Byte = 7) =
      Pay2PubKeyTemplateDestination(cs, UnsecuredSecret(ByteArray(32) { fill }), 42)

    private fun registerDomain(host: String, useIdentity: Long = IdentityDomain.COMMON_IDENTITY): IdentityDomain
    {
        val d = IdentityDomain(host, useIdentity)
        wallet.upsertIdentityDomain(d)
        return d
    }

    // --- identityRegistrationNecessary tests ---

    @Test
    fun identityRegistrationNecessaryTest()
    {
        // Registering and signing are the two operations that can be done without a prior registration
        assertFalse(identityRegistrationNecessary("reg"))
        assertFalse(identityRegistrationNecessary("sign"))

        assertTrue(identityRegistrationNecessary("login"))
        assertTrue(identityRegistrationNecessary("info"))
    }

    @Test
    fun identityRegistrationNecessaryNoOpTest()
    {
        // No operation at all cannot require a registration
        assertFalse(identityRegistrationNecessary(null))
        // ...but an unrecognized one is treated as needing one, so an unknown op cannot widen access
        assertTrue(identityRegistrationNecessary(""))
        assertTrue(identityRegistrationNecessary("bogus"))
    }

    @Test
    fun identityRegistrationNecessaryExpectsLowercaseTest()
    {
        // Every caller lowercases the op first; uncased input falls through to the restrictive answer
        assertTrue(identityRegistrationNecessary("REG"))
        assertTrue(identityRegistrationNecessary("Sign"))
    }

    // --- getSecretAndAddress tests ---

    @Test
    fun getSecretAndAddressNullHostTest()
    {
        val (secret, address) = getSecretAndAddress(wallet, null, "/ignored")
        assertTrue(secret.getSecret().isNotEmpty())
        assertEquals(wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address.toString(), address.toString())
    }

    @Test
    fun getSecretAndAddressUnregisteredHostTest()
    {
        // A host we have never registered with falls back to the common identity
        val (_, address) = getSecretAndAddress(wallet, "never.seen.example.com", "/p")
        assertEquals(wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address.toString(), address.toString())
    }

    @Test
    fun getSecretAndAddressCommonIdentityDomainTest()
    {
        registerDomain("common.example.com", IdentityDomain.COMMON_IDENTITY)
        val (_, address) = getSecretAndAddress(wallet, "common.example.com", "/p")
        assertEquals(wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address.toString(), address.toString())
    }

    @Test
    fun getSecretAndAddressByHashDomainTest()
    {
        if (platform().target == KotlinTarget.iOS) return  // destinationFor(String) fails on native, nexa/libnexakotlin#82
        registerDomain("byhash.example.com", IdentityDomain.IDENTITY_BY_HASH)
        val (_, address) = getSecretAndAddress(wallet, "byhash.example.com", "/p")

        assertEquals(wallet.destinationFor("byhash.example.com/p").address.toString(), address.toString())
        // A per-domain identity must not be the identity handed to everyone else
        assertNotEquals(wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address.toString(), address.toString())
    }

    @Test
    fun getSecretAndAddressByHashDomainSeparatesPathsTest()
    {
        if (platform().target == KotlinTarget.iOS) return  // destinationFor(String) fails on native, nexa/libnexakotlin#82
        registerDomain("paths.example.com", IdentityDomain.IDENTITY_BY_HASH)
        val (_, first) = getSecretAndAddress(wallet, "paths.example.com", "/one")
        val (_, second) = getSecretAndAddress(wallet, "paths.example.com", "/two")
        assertNotEquals(first.toString(), second.toString())

        // A null path is the host on its own, not the host plus "null"
        val (_, noPath) = getSecretAndAddress(wallet, "paths.example.com", null)
        assertEquals(wallet.destinationFor("paths.example.com").address.toString(), noPath.toString())
    }

    // --- onProvideIdentity tests ---

    @Test
    fun onProvideIdentityNullUriTest()
    {
        assertEquals(false, onProvideIdentity(IdentitySession(null)))
    }

    @Test
    fun onProvideIdentityNoHostTest()
    {
        val before = alerts.size
        val sess = IdentitySession(Uri.parse("/nohost?op=login&chal=c1"))
        assertEquals(false, onProvideIdentity(sess))
        assertAlerted(before, i18n(S.badLink))
    }

    @Test
    fun onProvideIdentityNoChallengeTest()
    {
        val before = alerts.size
        val sess = IdentitySession(Uri.parse("nexid://nochal.example.com/?op=login"))
        assertEquals(false, onProvideIdentity(sess))
        assertAlerted(before, i18n(S.badLink))
    }

    @Test
    fun onProvideIdentityLockedAccountTest()
    {
        val locked = mockAccount()
        every { locked.locked } returns true
        val before = alerts.size
        val sess = IdentitySession(Uri.parse("nexid://locked.example.com/?op=login&chal=c1"))
        sess.pill.account.value = locked
        assertEquals(false, onProvideIdentity(sess))
        assertAlerted(before, i18n(S.NoAccounts))
    }

    @Test
    fun onProvideIdentityLoginSignsChallengeTest()
    {
        var seenUrl: String? = null
        val sess = IdentitySession(Uri.parse("nexid://login.example.com/auth?op=login&chal=c1&cookie=k1")) { url, _, _ ->
            seenUrl = url
            true
        }
        sess.autoHandle = false

        assertEquals(true, onProvideIdentity(sess))

        val url = assertNotNull(seenUrl)
        // nexid names a scheme, not a protocol, so the reply goes out over http
        assertTrue(url.startsWith("http://login.example.com/auth?op=login&addr="), url)
        assertTrue(url.contains("&sig="), url)
        assertTrue(url.contains("&cookie=k1"), url)
    }

    @Test
    fun onProvideIdentityLoginHonorsRequestedProtocolTest()
    {
        var seenUrl: String? = null
        val sess = IdentitySession(Uri.parse("nexid://proto.example.com:8443/auth?op=login&chal=c1&proto=https")) { url, _, _ ->
            seenUrl = url
            true
        }
        sess.autoHandle = false

        assertEquals(true, onProvideIdentity(sess))
        assertTrue(assertNotNull(seenUrl).startsWith("https://proto.example.com:8443/auth?op=login"), seenUrl!!)
    }

    @Test
    fun onProvideIdentityRegBindsTdppDomainTest()
    {
        var seenBody: String? = null
        val sess = IdentitySession(Uri.parse("nexid://reg.example.com/?op=reg&chal=c1&cookie=k1&topic=shop")) { _, body, _ ->
            seenBody = body
            true
        }
        sess.autoHandle = false

        assertEquals(true, onProvideIdentity(sess))

        // "reg" is create-and/or-login: it binds this (host, topic) to the consenting account
        assertEquals(account.name, wallyApp!!.tpDomains.loadCreateDomain("reg.example.com", "shop").accountName)

        val body = assertNotNull(seenBody)
        assertTrue(body.contains("\"op\":\"reg\""), body)
        assertTrue(body.contains("\"addr\":"), body)
        assertTrue(body.contains("\"sig\":"), body)
    }

    @Test
    fun onProvideIdentityInfoDoesNotBindTdppDomainTest()
    {
        val sess = IdentitySession(Uri.parse("nexid://info.example.com/?op=info&chal=c1&topic=shop")) { _, _, _ -> true }
        sess.autoHandle = false

        assertEquals(true, onProvideIdentity(sess))

        // "info" can be sent without any trust relationship, so it must never create a registration
        assertEquals("", wallyApp!!.tpDomains.loadCreateDomain("info.example.com", "shop").accountName)
    }

    @Test
    fun onProvideIdentityRegMissingMandatoryInfoTest()
    {
        registerDomain("needsinfo.example.com")
        // email=m makes the email permission granted, but the wallet holds no email to hand over
        val sess = IdentitySession(Uri.parse("nexid://needsinfo.example.com/?op=reg&chal=c1&email=m")) { _, _, _ -> true }
        sess.autoHandle = false

        // null means "not resolved yet" -- the user is sent to fill the information in
        assertNull(onProvideIdentity(sess))
        assertEquals(ScreenId.IdentityEdit, nav.currentScreen.value)
    }

    @Test
    fun onProvideIdentitySignWithoutMessageTest()
    {
        val before = alerts.size
        val sess = IdentitySession(Uri.parse("nexid://sign.example.com/?op=sign&chal=c1")) { _, _, _ -> true }
        sess.autoHandle = false

        assertEquals(false, onProvideIdentity(sess))
        assertAlerted(before, i18n(S.nothingToSign))
    }

    @Test
    fun onProvideIdentitySignBuildsReplyTest()
    {
        var seenUrl: String? = null
        val sess = IdentitySession(Uri.parse("nexid://sign.example.com/p?op=sign&chal=c1&sign=hello&reply=false")) { url, _, _ ->
            seenUrl = url
            true
        }
        sess.autoHandle = false

        assertEquals(true, onProvideIdentity(sess))
        val url = assertNotNull(seenUrl)
        assertTrue(url.startsWith("http://sign.example.com/p?op=sign&addr="), url)
        assertTrue(url.contains("&sig="), url)
    }

    // --- handleSuccessfulIdentity tests ---

    @Test
    fun handleSuccessfulIdentitySavesNewDomainTest()
    {
        val sess = IdentitySession(Uri.parse("nexid://newreg.example.com/?op=reg"))
        // A "reg" against an unknown domain builds the domain in the session; nothing is persisted yet
        assertTrue(sess.newDomain)
        assertNull(wallet.lookupIdentityDomain("newreg.example.com"))

        handleSuccessfulIdentity(sess)

        assertNotNull(wallet.lookupIdentityDomain("newreg.example.com"))
    }

    @Test
    fun handleSuccessfulIdentitySavesChangedDomainTest()
    {
        val sess = IdentitySession(Uri.parse("nexid://changed.example.com/?op=login"))
        val domain = IdentityDomain("changed.example.com", IdentityDomain.COMMON_IDENTITY)
        domain.changed = true
        sess.idData.value = domain

        handleSuccessfulIdentity(sess)

        assertNotNull(wallet.lookupIdentityDomain("changed.example.com"))
        assertFalse(domain.changed)
    }

    @Test
    fun handleSuccessfulIdentityIgnoresUnchangedDomainTest()
    {
        val sess = IdentitySession(Uri.parse("nexid://unchanged.example.com/?op=login"))
        // Neither new nor modified, so there is nothing to write
        sess.idData.value = IdentityDomain("unchanged.example.com", IdentityDomain.COMMON_IDENTITY)

        handleSuccessfulIdentity(sess)

        assertNull(wallet.lookupIdentityDomain("unchanged.example.com"))
    }

    @Test
    fun handleSuccessfulIdentityNoAutoconnectOnSignTest()
    {
        // connect is only honored for login and reg -- signing must not open a long poll
        val sess = IdentitySession(Uri.parse("nexid://nosign.example.com/?op=sign&connect=1"))
        handleSuccessfulIdentity(sess)
        assertNull(wallyApp!!.accessHandler.activeTo("nosign.example.com"))
    }

    @Test
    fun handleSuccessfulIdentityNoAutoconnectWithoutConnectTest()
    {
        val sess = IdentitySession(Uri.parse("nexid://noconnect.example.com/?op=login"))
        handleSuccessfulIdentity(sess)
        assertNull(wallyApp!!.accessHandler.activeTo("noconnect.example.com"))
    }

    // --- AcceptIdentityPermHandler tests ---

    @Test
    fun acceptIdentityPermHandlerNoAccountTest()
    {
        val screenNav = ScreenNav()
        screenNav.go(ScreenId.IdentityOp)
        val sess = IdentitySession(Uri.parse("nexid://noacct.example.com/?op=login&chal=c1"))
        sess.pill.account.value = null
        val before = alerts.size

        AcceptIdentityPermHandler("login", sess, null, null, screenNav)

        assertAlerted(before, i18n(S.NoAccounts), AlertLevel.NOTICE)
        assertEquals(ScreenId.Splash, screenNav.currentScreen.value)
    }

    @Test
    fun acceptIdentityPermHandlerSignWithoutMessageTest()
    {
        val screenNav = ScreenNav()
        screenNav.go(ScreenId.IdentityOp)
        val sess = IdentitySession(Uri.parse("nexid://sign.example.com/?op=sign"))
        val before = alerts.size

        AcceptIdentityPermHandler("sign", sess, null, localDestination(), screenNav)

        assertAlerted(before, i18n(S.nothingToSign))
        assertEquals(ScreenId.Splash, screenNav.currentScreen.value)
    }

    @Test
    fun acceptIdentityPermHandlerSignInvokesContinuationTest()
    {
        var seenUrl: String? = null
        var seenBody: String? = null
        val sess = IdentitySession(Uri.parse("nexid://sign.example.com/path?op=sign&cookie=k1")) { url, body, _ ->
            seenUrl = url
            seenBody = body
            true
        }
        sess.autoHandle = false
        val dest = localDestination()

        AcceptIdentityPermHandler("sign", sess, "hello".toByteArray(), dest, ScreenNav())

        val url = assertNotNull(seenUrl)
        assertTrue(url.startsWith("nexid://sign.example.com/path?op=sign&addr="), url)
        assertTrue(url.contains("&sig="), url)
        assertTrue(url.contains("&cookie=k1"), url)

        // The continuation gets the same json blob that goes onto the clipboard
        val body = assertNotNull(seenBody)
        assertTrue(body.contains("\"message\":\"hello\""), body)
        assertTrue(body.contains(dest.address.toString()), body)
        assertTrue(body.contains("\"signature\": \""), body)
    }

    @Test
    fun acceptIdentityPermHandlerSignUsesSuppliedDestinationTest()
    {
        var firstBody: String? = null
        var secondBody: String? = null
        val uri = Uri.parse("nexid://sign.example.com/path?op=sign")

        val first = IdentitySession(uri) { _, body, _ -> firstBody = body; true }
        first.autoHandle = false
        AcceptIdentityPermHandler("sign", first, "hello".toByteArray(), localDestination(7), ScreenNav())

        val second = IdentitySession(uri) { _, body, _ -> secondBody = body; true }
        second.autoHandle = false
        AcceptIdentityPermHandler("sign", second, "hello".toByteArray(), localDestination(9), ScreenNav())

        // The signature must come from the destination handed in, not from the account's identity key
        assertNotEquals(assertNotNull(firstBody), assertNotNull(secondBody))
    }

    @Test
    fun acceptIdentityPermHandlerNonSignDelegatesTest()
    {
        val screenNav = ScreenNav()
        screenNav.go(ScreenId.IdentityOp)
        // No challenge, so onProvideIdentity fails; a definitive false still dismisses the screen
        val sess = IdentitySession(Uri.parse("nexid://delegate.example.com/?op=login"))
        val before = alerts.size

        AcceptIdentityPermHandler("login", sess, null, null, screenNav)

        assertAlerted(before, i18n(S.badLink))
        assertEquals(ScreenId.Splash, screenNav.currentScreen.value)
    }

    @Test
    fun acceptIdentityPermHandlerRegSavesDomainTest()
    {
        val screenNav = ScreenNav()
        screenNav.go(ScreenId.IdentityOp)
        val sess = IdentitySession(Uri.parse("nexid://accept.example.com/?op=reg&chal=c1")) { _, _, _ -> true }
        sess.autoHandle = false

        AcceptIdentityPermHandler("reg", sess, null, null, screenNav)

        // A successful registration is persisted before the screen is dismissed
        assertNotNull(wallet.lookupIdentityDomain("accept.example.com"))
        assertEquals(ScreenId.Splash, screenNav.currentScreen.value)
    }

    // --- HandleIdentity tests ---

    @Test
    fun handleIdentityNoHostTest()
    {
        val before = alerts.size
        assertFalse(HandleIdentity(Uri.parse("/nohost?op=login"), true))
        assertAlerted(before, i18n(S.unknownOperation))
        assertEquals(ScreenId.Home, nav.currentScreen.value)
    }

    @Test
    fun handleIdentityNoOperationTest()
    {
        val before = alerts.size
        assertFalse(HandleIdentity(Uri.parse("nexid://noop.example.com/"), true))
        assertAlerted(before, i18n(S.unknownOperation))
        assertEquals(ScreenId.Home, nav.currentScreen.value)
    }

    @Test
    fun handleIdentityLoginUnknownDomainTest()
    {
        val before = alerts.size
        // Login needs a prior registration, and no account has one for this domain
        assertFalse(HandleIdentity(Uri.parse("nexid://unknown.example.com/?op=login"), true))
        assertAlerted(before, i18n(S.UnknownDomainRegisterFirst))
        assertEquals(ScreenId.Home, nav.currentScreen.value)
    }

    @Test
    fun handleIdentityLoginKnownDomainTest()
    {
        registerDomain("known.example.com")

        assertTrue(HandleIdentity(Uri.parse("nexid://known.example.com/?op=login&cookie=k1"), true))

        assertEquals(ScreenId.IdentityOp, nav.currentScreen.value)
        val sess = nav.curData.value as IdentitySession
        assertEquals("login", sess.op)
        assertEquals("k1", sess.cookie)
        assertTrue(sess.candidateAccounts!!.contains(account))
        // For login the accounts that could be used and those already registered are the same set
        assertEquals(sess.candidateAccounts!!.toList(), sess.associatedAccounts!!.toList())
    }

    @Test
    fun handleIdentityRegNewDomainTest()
    {
        assertTrue(HandleIdentity(Uri.parse("nexid://newdomain.example.com/?op=reg"), true))

        // Nothing is registered yet, so the user must be asked to register rather than just approve
        assertEquals(ScreenId.Identity, nav.currentScreen.value)
        val sess = nav.curData.value as IdentitySession
        assertTrue(sess.newDomain)
        assertTrue(sess.candidateAccounts!!.contains(account))
        assertTrue(sess.associatedAccounts!!.isEmpty())
    }

    @Test
    fun handleIdentityRegExistingDomainTest()
    {
        registerDomain("existing.example.com")

        // Re-registering an account that is already associated goes straight to the approval screen
        assertTrue(HandleIdentity(Uri.parse("nexid://existing.example.com/?op=reg"), true))

        assertEquals(ScreenId.IdentityOp, nav.currentScreen.value)
        val sess = nav.curData.value as IdentitySession
        assertFalse(sess.newDomain)
        assertTrue(sess.associatedAccounts!!.contains(account))
    }

    @Test
    fun handleIdentityRegAppliesRequestedPermsToExistingDomainTest()
    {
        val domain = registerDomain("widen.example.com")
        assertFalse(domain.emailP)

        assertTrue(HandleIdentity(Uri.parse("nexid://widen.example.com/?op=reg&email=m"), true))

        // IdentitySession writes the request's perms onto the stored registration before
        // HandleIdentity's widening check reads it back, so the check always passes and the
        // "ask the user again" route to ScreenId.Identity is never taken for a known domain.
        assertTrue(domain.emailP)
        assertEquals(ScreenId.IdentityOp, nav.currentScreen.value)
    }

    @Test
    fun handleIdentitySignTest()
    {
        // Signing needs no prior registration, so every account is a candidate and none is associated
        assertTrue(HandleIdentity(Uri.parse("nexid://signing.example.com/?op=sign&sign=hello"), true))

        assertEquals(ScreenId.IdentityOp, nav.currentScreen.value)
        val sess = nav.curData.value as IdentitySession
        assertEquals("sign", sess.op)
        assertTrue(sess.candidateAccounts!!.contains(account))
        assertNull(sess.associatedAccounts)
    }

    @Test
    fun handleIdentityPassesAutoHandleAndContinuationTest()
    {
        registerDomain("cont.example.com")
        val then: (String, String, Boolean?) -> Boolean = { _, _, _ -> true }

        assertTrue(HandleIdentity(Uri.parse("nexid://cont.example.com/?op=login"), false, then))

        val sess = nav.curData.value as IdentitySession
        assertFalse(sess.autoHandle)
        assertEquals(then, sess.whenDone)
    }

    @Test
    fun handleIdentityUppercaseOperationTest()
    {
        registerDomain("upper.example.com")

        // Operations are matched case insensitively
        assertTrue(HandleIdentity(Uri.parse("nexid://upper.example.com/?op=LOGIN"), true))
        assertEquals(ScreenId.IdentityOp, nav.currentScreen.value)
    }
}
