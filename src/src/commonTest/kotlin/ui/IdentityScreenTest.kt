package ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import com.eygraber.uri.Uri
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.IdentityDomain
import info.bitcoinunlimited.www.wally.ui.IdentityDomainView
import info.bitcoinunlimited.www.wally.ui.IdentityEditScreen
import info.bitcoinunlimited.www.wally.ui.IdentityScreen
import info.bitcoinunlimited.www.wally.ui.IdentitySession
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.ThinDataEntry
import info.bitcoinunlimited.www.wally.ui.TitledBox
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.IdentityDomain as LibIdentityDomain
import org.nexa.libnexakotlin.rem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class IdentityScreenTest : WallyUiTestBase(false)
{
    // ---------- fun IdentityDomain(uri) factory ----------

    @Test
    fun identityDomainFactoryParsesHostPermsAndReqs()
    {
        val uri = Uri.parse(
          "http://service.example?op=reg&hdl=m&email=r&ava=o&realname=m&phone=x"
        )
        val domain = IdentityDomain(uri)

        assertEquals("service.example", domain.domain)
        assertEquals(LibIdentityDomain.COMMON_IDENTITY, domain.useIdentity)

        // Requirements come straight from the query value's first char.
        assertEquals('m', domain.hdlR)
        assertEquals('r', domain.emailR)
        assertEquals('o', domain.avaR)
        assertEquals('m', domain.realnameR)
        assertEquals('x', domain.phoneR)

        // Perms: "m" or "r" → true; anything else → false (unchanged default false).
        assertTrue(domain.hdlP)
        assertTrue(domain.emailP)
        assertFalse(domain.avaP)
        assertTrue(domain.realnameP)
        assertFalse(domain.phoneP)
    }

    @Test
    fun identityDomainFactoryRespectsIdentityByHash()
    {
        val uri = Uri.parse("http://unique.example")
        val domain = IdentityDomain(uri, LibIdentityDomain.IDENTITY_BY_HASH)
        assertEquals("unique.example", domain.domain)
        assertEquals(LibIdentityDomain.IDENTITY_BY_HASH, domain.useIdentity)
    }

    // ---------- fun TitledBox ----------

    @Test
    fun titledBoxDisplaysTitleAndContent()
    {
        runComposeUiTest {
            setContent {
                TitledBox(S.UsernameOrAliasText) {
                    Text("content-inside", modifier = Modifier.testTag("tbContent"))
                }
            }
            settle()
            onNodeWithText(i18n(S.UsernameOrAliasText)).assertIsDisplayed()
            onNodeWithTag("tbContent").assertTextEquals("content-inside")
        }
    }

    // ---------- fun ThinDataEntry ----------

    @Test
    fun thinDataEntryCapturesInput()
    {
        var captured = ""
        runComposeUiTest {
            setContent {
                var value by remember { mutableStateOf("") }
                ThinDataEntry(value, Modifier.testTag("thin")) { value = it; captured = it }
            }
            settle()
            onNodeWithTag("thin").performTextInput("hello-alice")
            settle()
            assertEquals("hello-alice", captured)
            onNodeWithTag("thin").assertTextEquals("hello-alice")
        }
    }

    @Test
    fun thinDataEntryRendersInitialValue()
    {
        runComposeUiTest {
            setContent {
                ThinDataEntry("seed-value", Modifier.testTag("thin"))
            }
            settle()
            onNodeWithTag("thin").assertTextEquals("seed-value")
        }
    }

    // ---------- private fun Switch (exercised via IdentityDomainView) ----------

    @Test
    fun switchHiddenWhenRequirementIsX()
    {
        runComposeUiTest {
            val domain = LibIdentityDomain("silent.example", LibIdentityDomain.COMMON_IDENTITY)
            // All R fields default to 'x'; only hdl has a real requirement.
            domain.hdlR = 'r'
            setContent {
                IdentityDomainView(null, domain, newDomain = false)
            }
            settle()
            onNodeWithText(i18n(S.provideAlias)).assertIsDisplayed()
            // Everything else (still 'x') must not render.
            onNodeWithText(i18n(S.provideEmail)).assertDoesNotExist()
            onNodeWithText(i18n(S.providePhone)).assertDoesNotExist()
            onNodeWithText(i18n(S.provideRealName)).assertDoesNotExist()
        }
    }

    @Test
    fun switchMandatoryLabelIsPrefixedAndForcesPermission()
    {
        runComposeUiTest {
            val domain = LibIdentityDomain("required.example", LibIdentityDomain.COMMON_IDENTITY)
            domain.hdlR = 'm'
            domain.emailR = 'm'
            assertFalse(domain.hdlP)
            assertFalse(domain.emailP)

            setContent {
                IdentityDomainView(null, domain, newDomain = true)
            }
            settle()

            val requiredLabel = "(" + i18n(S.required) + ") " + i18n(S.provideAlias)
            onNodeWithText(requiredLabel).assertIsDisplayed()

            // Side-effect contract of the private Switch: a mandatory requirement
            // flips the backing permission to true the first time it renders.
            assertTrue(domain.hdlP)
            assertTrue(domain.emailP)
        }
    }

    // ---------- fun IdentityDomainView ----------

    @Test
    fun identityDomainViewNewDomainHeader()
    {
        runComposeUiTest {
            val domain = LibIdentityDomain("newservice.example", LibIdentityDomain.COMMON_IDENTITY)
            domain.hdlR = 'r'
            setContent {
                IdentityDomainView(null, domain, newDomain = true)
            }
            settle()
            onNodeWithText(i18n(S.newDomainRequestingIdentity)).assertIsDisplayed()
            onNodeWithText("newservice.example").assertIsDisplayed()
            onNodeWithText(i18n(S.useUniqueIdentity)).assertIsDisplayed()
        }
    }

    @Test
    fun identityDomainViewAssociatedHeaderWhenNoFromAndNotNew()
    {
        runComposeUiTest {
            val domain = LibIdentityDomain("mydomain.example", LibIdentityDomain.COMMON_IDENTITY)
            domain.emailR = 'r'
            setContent {
                IdentityDomainView(null, domain, newDomain = false)
            }
            settle()
            onNodeWithText(i18n(S.IdentityAssociatedWith)).assertIsDisplayed()
            onNodeWithText("mydomain.example").assertIsDisplayed()
            onNodeWithText(i18n(S.provideEmail)).assertIsDisplayed()
        }
    }

    @Test
    fun identityDomainViewTogglingAllSwitchesUpdatesDomain()
    {
        runComposeUiTest(testTimeout = 3.minutes) {
            val domain = LibIdentityDomain("toggle.example", LibIdentityDomain.COMMON_IDENTITY)
            // Every requirement set to 'o' so every Switch is drawn and enabled
            // (mandatory 'm' switches are disabled and can't be toggled off).
            domain.hdlR = 'o'
            domain.emailR = 'o'
            domain.smR = 'o'
            domain.avaR = 'o'
            domain.realnameR = 'o'
            domain.dobR = 'o'
            domain.phoneR = 'o'
            domain.postalR = 'o'
            domain.billingR = 'o'
            domain.attestR = 'o'
            // All P fields start false; useIdentity starts COMMON_IDENTITY.
            assertFalse(domain.hdlP)
            assertFalse(domain.emailP)
            assertFalse(domain.smP)
            assertFalse(domain.avaP)
            assertFalse(domain.realnameP)
            assertFalse(domain.dobP)
            assertFalse(domain.phoneP)
            assertFalse(domain.postalP)
            assertFalse(domain.billingP)
            assertFalse(domain.attestP)
            assertEquals(LibIdentityDomain.COMMON_IDENTITY, domain.useIdentity)

            setContent {
                IdentityDomainView(null, domain, newDomain = false)
            }

            // Declaration order: useUniqueIdentity, then hdl, email, sm, ava,
            // realname, dob, phone, postal, billing, attest → 11 switches.
            val switches = onAllNodes(isToggleable())
            switches.assertCountEquals(11)
            repeat(11) { switches[it].performClick() }

            assertEquals(LibIdentityDomain.IDENTITY_BY_HASH, domain.useIdentity)
            assertTrue(domain.hdlP)
            assertTrue(domain.emailP)
            assertTrue(domain.smP)
            assertTrue(domain.avaP)
            assertTrue(domain.realnameP)
            assertTrue(domain.dobP)
            assertTrue(domain.phoneP)
            assertTrue(domain.postalP)
            assertTrue(domain.billingP)
            assertTrue(domain.attestP)
        }
    }

    @Test
    fun identityDomainViewAdditionalInfoHeaderWhenFromIsSet()
    {
        runComposeUiTest {
            val from = LibIdentityDomain("service.example", LibIdentityDomain.COMMON_IDENTITY)
            val to = LibIdentityDomain("service.example", LibIdentityDomain.COMMON_IDENTITY)
            to.hdlR = 'r'
            setContent {
                IdentityDomainView(from, to, newDomain = false)
            }
            settle()
            onNodeWithText(i18n(S.domainRequestingAdditionalIdentityInfo)).assertIsDisplayed()
            onNodeWithText("service.example").assertIsDisplayed()
        }
    }

    // ---------- fun IdentityScreen ----------

    @Test
    fun identityScreenNoRegistrationsShowsEmptyStateAndCommonIdentity()
    {
        val account: Account = wallyApp!!.newAccount("idtst-empty", 0U, "", ChainSelector.NEXA)!!
        try
        {
            val idSession = IdentitySession(null)
            idSession.pill.account.value = account
            runComposeUiTest {
                setContent {
                    IdentityScreen(idSession, ScreenNav())
                }
                settle()

                onNodeWithText(
                  i18n(S.commonIdentityForAccount) % mapOf("act" to account.name)
                ).assertIsDisplayed()
                onNodeWithText(i18n(S.IdentityRegistrations)).assertIsDisplayed()
                onNodeWithText(i18n(S.NoIdentitiesRegistered)).assertIsDisplayed()
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun identityScreenShowsRegisteredDomainAndOpensOnClick()
    {
        val account: Account = wallyApp!!.newAccount("idtst-list", 0U, "", ChainSelector.NEXA)!!
        try
        {
            val hostname = "testdomain.example"
            account.wallet.upsertIdentityDomain(
              LibIdentityDomain(hostname, LibIdentityDomain.COMMON_IDENTITY).apply { emailR = 'r' }
            )

            val idSession = IdentitySession(null)
            idSession.pill.account.value = account

            runComposeUiTest {
                setContent {
                    IdentityScreen(idSession, ScreenNav())
                }
                settle()

                onNodeWithText(i18n(S.NoIdentitiesRegistered)).assertDoesNotExist()
                onNodeWithText(hostname).assertIsDisplayed()

                // Clicking the registration populates idData, which unfolds IdentityDomainView below.
                onNodeWithText(hostname).performClick()
                settle()
                onNodeWithText(i18n(S.IdentityAssociatedWith)).assertIsDisplayed()
                onNodeWithTag("RemoveIdentityButton").assertIsDisplayed()
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    // ---------- fun IdentityEditScreen ----------

    @Test
    fun identityEditScreenShowsAllLabeledFieldsAndButtons()
    {
        val account: Account = wallyApp!!.newAccount("idtst-edit", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                setContent {
                    IdentityEditScreen(account, ScreenNav())
                }
                settle()

                onNodeWithText(i18n(S.IdentityAssociatedWith)).assertIsDisplayed()
                onNodeWithText(i18n(S.UsernameOrAliasText)).assertIsDisplayed()
                onNodeWithText(i18n(S.EmailText)).assertIsDisplayed()
                onNodeWithText(i18n(S.NameText)).assertIsDisplayed()
                onNodeWithText(i18n(S.PostalAddressText)).assertIsDisplayed()
                onNodeWithText(i18n(S.BillingAddressText)).assertIsDisplayed()
                onNodeWithText(i18n(S.SocialMediaText)).assertIsDisplayed()
                onNodeWithText(i18n(S.done)).assertIsDisplayed()
                onNodeWithText(i18n(S.clear)).assertIsDisplayed()
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun identityEditScreenTypingWritesAllFieldsToIdentityInfo()
    {
        val account: Account = wallyApp!!.newAccount("idtst-typing", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                setContent {
                    IdentityEditScreen(account, ScreenNav())
                }
                settle()

                // ThinDataEntry has no testTag; the screen renders exactly six empty
                // text fields, matched here in declaration order: hdl, email, realname,
                // postal, billing, sm.
                val fields = onAllNodes(hasSetTextAction())
                fields[0].performTextInput("alice")
                fields[1].performTextInput("alice@example.com")
                fields[2].performTextInput("Alice Smith")
                fields[3].performTextInput("1 Post Rd")
                fields[4].performTextInput("1 Billing Rd")
                fields[5].performTextInput("@alice")
                settle()

                fields[0].assertTextEquals("alice")
                fields[1].assertTextEquals("alice@example.com")
                fields[2].assertTextEquals("Alice Smith")
                fields[3].assertTextEquals("1 Post Rd")
                fields[4].assertTextEquals("1 Billing Rd")
                fields[5].assertTextEquals("@alice")

                val addr = account.wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address!!
                val info = account.wallet.lookupIdentityInfo(addr)!!
                assertEquals("alice", info.hdl)
                assertEquals("alice@example.com", info.email)
                assertEquals("Alice Smith", info.realname)
                assertEquals("1 Post Rd", info.postal)
                assertEquals("1 Billing Rd", info.billing)
                assertEquals("@alice", info.sm)
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun identityEditScreenClearButtonEmptiesIdentityInfoMap()
    {
        val account: Account = wallyApp!!.newAccount("idtst-clear", 0U, "", ChainSelector.NEXA)!!
        try
        {
            runComposeUiTest {
                setContent {
                    IdentityEditScreen(account, ScreenNav())
                }
                settle()

                val fields = onAllNodes(hasSetTextAction())
                fields[0].performTextInput("will-be-cleared")
                settle()

                val addr = account.wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address!!
                assertEquals("will-be-cleared", account.wallet.lookupIdentityInfo(addr)?.hdl)
                assertFalse(account.wallet.identityInfo.isEmpty())
                account.wallet.identityInfoChanged = false

                onNodeWithText(i18n(S.clear)).performClick()
                settle()

                assertTrue(account.wallet.identityInfo.isEmpty())
                assertTrue(account.wallet.identityInfoChanged)
            }
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }

    @Test
    fun identityEditScreenDoneButtonCallsNavBack()
    {
        val account: Account = wallyApp!!.newAccount("idtst-done", 0U, "", ChainSelector.NEXA)!!
        try
        {
            // Seed the nav stack so back() has somewhere to pop to.
            val nav = ScreenNav()
            nav.go(ScreenId.IdentityEdit)
            assertEquals(ScreenId.IdentityEdit, nav.currentScreen.value)

            runComposeUiTest {
                setContent {
                    IdentityEditScreen(account, nav)
                }
                settle()

                // Type something so we can also prove the screen's onDepart ran (it
                // upserts the current identity info into the wallet on back()).
                val fields = onAllNodes(hasSetTextAction())
                fields[0].performTextInput("alice-depart")
                settle()

                onNodeWithText(i18n(S.done)).performClick()
                settle()
            }

            // back() popped the prior Splash screen off the stack.
            assertEquals(ScreenId.Splash, nav.currentScreen.value)

            // back() also invoked the onDepart callback installed by IdentityEditScreen,
            // which upserts the identityInfo into the wallet.
            val addr = account.wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED).address!!
            assertEquals("alice-depart", account.wallet.lookupIdentityInfo(addr)?.hdl)
        }
        finally
        {
            wallyApp!!.deleteAccount(account)
        }
    }
}
