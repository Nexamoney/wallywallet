import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY
import info.bitcoinunlimited.www.wally.ACCOUNT_FLAG_HIDE_UNTIL_PIN
import info.bitcoinunlimited.www.wally.ACCOUNT_FLAG_NONE
import info.bitcoinunlimited.www.wally.ACCOUNT_FLAG_REUSE_ADDRESSES
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.EncodePIN
import info.bitcoinunlimited.www.wally.KotlinTarget
import info.bitcoinunlimited.www.wally.containsAccountWithName
import info.bitcoinunlimited.www.wally.dbPrefix
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.wallyAccountDbFileName
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.ChainSelector
import ui.WallyUiTestBase
import ui.mockAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountTest : WallyUiTestBase()
{
    private fun withRealAccount(
        name: String,
        cs: ChainSelector = ChainSelector.NEXA,
        block: (Account) -> Unit
    )
    {
        val acc = wallyApp!!.newAccount(name, 0U, "", cs)!!
        try { block(acc) }
        finally { wallyApp!!.deleteAccount(acc) }
    }

    // ======================================================================
    // wallyAccountDbFileName
    // ======================================================================

    @Test
    fun wallyAccountDbFileName_default()
    {
        val saved = dbPrefix
        try
        {
            dbPrefix = ""
            assertEquals("myWallet_wallet", wallyAccountDbFileName("myWallet"))
        }
        finally { dbPrefix = saved }
    }

    @Test
    fun wallyAccountDbFileName_withPrefix()
    {
        val saved = dbPrefix
        try
        {
            dbPrefix = "test_"
            assertEquals("test_myWallet_wallet", wallyAccountDbFileName("myWallet"))
        }
        finally { dbPrefix = saved }
    }

    // ======================================================================
    // containsAccountWithName
    // ======================================================================

    @Test
    fun containsAccountWithName_found()
    {
        withRealAccount("alice") { a ->
            withRealAccount("bob") { b ->
                assertTrue(containsAccountWithName(listOf(a, b), "bob"))
            }
        }
    }

    @Test
    fun containsAccountWithName_notFound()
    {
        withRealAccount("alice") { a ->
            assertFalse(containsAccountWithName(listOf(a), "charlie"))
        }
    }

    @Test
    fun containsAccountWithName_emptyList()
    {
        assertFalse(containsAccountWithName(emptyList(), "any"))
    }

    // ======================================================================
    // Account flags constants
    // ======================================================================

    @Test
    fun flagConstants_areDistinctBits()
    {
        assertEquals(0UL, ACCOUNT_FLAG_NONE)
        assertEquals(1UL, ACCOUNT_FLAG_HIDE_UNTIL_PIN)
        assertEquals(2UL, ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY)
        assertEquals(4UL, ACCOUNT_FLAG_REUSE_ADDRESSES)
        assertEquals(0UL, ACCOUNT_FLAG_HIDE_UNTIL_PIN and ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY)
        assertEquals(0UL, ACCOUNT_FLAG_HIDE_UNTIL_PIN and ACCOUNT_FLAG_REUSE_ADDRESSES)
        assertEquals(0UL, ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY and ACCOUNT_FLAG_REUSE_ADDRESSES)
    }

    @Test
    fun flagConstants_combineCorrectly()
    {
        val combined = ACCOUNT_FLAG_HIDE_UNTIL_PIN or ACCOUNT_FLAG_REUSE_ADDRESSES
        assertEquals(5UL, combined)
        assertTrue((combined and ACCOUNT_FLAG_HIDE_UNTIL_PIN) > 0UL)
        assertTrue((combined and ACCOUNT_FLAG_REUSE_ADDRESSES) > 0UL)
        assertFalse((combined and ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY) > 0UL)
    }

    // ======================================================================
    // toFinestUnit / fromFinestUnit
    // ======================================================================

    @Test
    fun toFinestUnit_nexa()
    {
        withRealAccount("toFinest") { acc ->
            assertEquals(0L, acc.toFinestUnit(BigDecimal.Companion.ZERO))
            assertEquals(100L, acc.toFinestUnit(BigDecimal.Companion.fromInt(1)))
            assertEquals(4200L, acc.toFinestUnit(BigDecimal.Companion.fromInt(42)))
            assertEquals(150L, acc.toFinestUnit(BigDecimal.Companion.fromDouble(1.5)))
        }
    }

    @Test
    fun fromFinestUnit_nexa()
    {
        withRealAccount("fromFinest") { acc ->
            assertEquals(BigDecimal.Companion.ZERO, acc.fromFinestUnit(0L))
            val one = acc.fromFinestUnit(100L)
            assertEquals(100L, acc.toFinestUnit(one))
        }
    }

    @Test
    fun toFromFinestUnit_roundTrip()
    {
        withRealAccount("finestRT") { acc ->
            val original = BigDecimal.Companion.fromInt(123)
            assertEquals(original, acc.fromFinestUnit(acc.toFinestUnit(original)))
        }
    }

    // ======================================================================
    // toPrimaryUnit / fromPrimaryUnit
    // ======================================================================

    @Test
    fun toPrimaryUnit_nexa()
    {
        withRealAccount("primaryTo") { acc ->
            // NEXA: factor = 1, identity
            val qty = BigDecimal.Companion.fromInt(500)
            assertEquals(qty, acc.toPrimaryUnit(qty))
        }
    }

    @Test
    fun fromPrimaryUnit_nexa()
    {
        withRealAccount("primaryFrom") { acc ->
            val qty = BigDecimal.Companion.fromInt(500)
            assertEquals(qty, acc.fromPrimaryUnit(qty))
        }
    }

    @Test
    fun toFromPrimaryUnit_roundTrip()
    {
        withRealAccount("primaryRT") { acc ->
            val original = BigDecimal.Companion.fromInt(999)
            assertEquals(original, acc.fromPrimaryUnit(acc.toPrimaryUnit(original)))
        }
    }

    // ======================================================================
    // format
    // ======================================================================

    @Test
    fun format_zero()
    {
        withRealAccount("fmtZero") { acc ->
            assertEquals("0.00", acc.format(BigDecimal.Companion.ZERO))
        }
    }

    @Test
    fun format_wholeNumber()
    {
        withRealAccount("fmtWhole") { acc ->
            val s = acc.format(BigDecimal.Companion.fromInt(1234))
            // iOS DecimalFormat omits grouping separator; JVM/Android include it.
            assertTrue(s == "1,234.00" || s == "1234.00", "unexpected format: $s")
        }
    }

    @Test
    fun format_fractional()
    {
        withRealAccount("fmtFrac") { acc ->
            assertEquals("42.50", acc.format(BigDecimal.Companion.fromDouble(42.5)))
        }
    }

    @Test
    fun format_large()
    {
        withRealAccount("fmtLarge") { acc ->
            val s = acc.format(BigDecimal.Companion.fromLong(1_000_000_000L))
            // Strip grouping separators for cross-platform assertion.
            assertTrue(s.replace(",", "").contains("1000000000"), "unexpected format: $s")
        }
    }

    // ======================================================================
    // transactionInfoWebUrl / addressInfoWebUrl
    // ======================================================================

    @Test
    fun transactionInfoWebUrl_nexa()
    {
        withRealAccount("txUrl") { acc ->
            assertNull(acc.transactionInfoWebUrl(null))
            assertEquals("http://explorer.nexa.org/tx/abc123", acc.transactionInfoWebUrl("abc123"))
        }
    }

    @Test
    fun addressInfoWebUrl_nexa()
    {
        withRealAccount("addrUrl") { acc ->
            assertNull(acc.addressInfoWebUrl(null))
            assertEquals(
                "http://explorer.nexa.org/address/nexa:myaddr",
                acc.addressInfoWebUrl("nexa:myaddr")
            )
        }
    }

    @Test
    fun transactionInfoWebUrl_nexaTestnet()
    {
        withRealAccount("txUrlTN", ChainSelector.NEXATESTNET) { acc ->
            assertEquals(
                "http://testnet-explorer.nexa.org/tx/deadbeef",
                acc.transactionInfoWebUrl("deadbeef")
            )
        }
    }

    @Test
    fun addressInfoWebUrl_nexaTestnet()
    {
        withRealAccount("addrUrlTN", ChainSelector.NEXATESTNET) { acc ->
            assertEquals(
                "http://testnet-explorer.nexa.org/address/nexatest:addr1",
                acc.addressInfoWebUrl("nexatest:addr1")
            )
        }
    }

    // ======================================================================
    // visible / lockable / locked
    // ======================================================================

    @Test
    fun newAccount_isVisible()
    {
        withRealAccount("visNew") { acc ->
            assertTrue(acc.visible)
        }
    }

    @Test
    fun newAccount_isNotLockable()
    {
        withRealAccount("lockNew") { acc ->
            assertFalse(acc.lockable)
            assertFalse(acc.locked)
        }
    }

    @Test
    fun accountWithPin_isLockable()
    {
        withRealAccount("lockPin") { acc ->
            val epin = EncodePIN(acc.name, "1234")
            acc.saveAccountPin(epin)
            acc.encodedPin = epin
            assertTrue(acc.lockable)
        }
    }

    @Test
    fun accountWithPin_notEntered_isLocked()
    {
        withRealAccount("lockd") { acc ->
            val epin = EncodePIN(acc.name, "5678")
            acc.saveAccountPin(epin)
            acc.encodedPin = epin
            acc.pinEntered = false
            assertTrue(acc.locked)
        }
    }

    @Test
    fun accountWithPin_entered_isNotLocked()
    {
        withRealAccount("unlockd") { acc ->
            val epin = EncodePIN(acc.name, "5678")
            acc.saveAccountPin(epin)
            acc.encodedPin = epin
            acc.pinEntered = true
            assertFalse(acc.locked)
        }
    }

    @Test
    fun accountWithHideFlag_notEntered_isHidden()
    {
        withRealAccount("hiddenAcc") { acc ->
            val epin = EncodePIN(acc.name, "9999")
            acc.saveAccountPin(epin)
            acc.encodedPin = epin
            acc.flags = ACCOUNT_FLAG_HIDE_UNTIL_PIN
            acc.pinEntered = false
            assertFalse(acc.visible)
        }
    }

    @Test
    fun accountWithHideFlag_entered_isVisible()
    {
        withRealAccount("shownAcc") { acc ->
            val epin = EncodePIN(acc.name, "9999")
            acc.saveAccountPin(epin)
            acc.encodedPin = epin
            acc.flags = ACCOUNT_FLAG_HIDE_UNTIL_PIN
            acc.pinEntered = true
            assertTrue(acc.visible)
        }
    }

    // ======================================================================
    // submitAccountPin
    // ======================================================================

    @Test
    fun submitAccountPin_correctPinReturnsOne()
    {
        withRealAccount("pinOk") { acc ->
            val epin = EncodePIN(acc.name, "4321")
            acc.saveAccountPin(epin)
            acc.encodedPin = epin
            acc.pinEntered = false
            assertEquals(1, acc.submitAccountPin("4321"))
            assertTrue(acc.pinEntered)
        }
    }

    @Test
    fun submitAccountPin_wrongPinReturnsZero()
    {
        withRealAccount("pinBad") { acc ->
            val epin = EncodePIN(acc.name, "4321")
            acc.saveAccountPin(epin)
            acc.encodedPin = epin
            acc.pinEntered = false
            assertEquals(0, acc.submitAccountPin("0000"))
        }
    }

    @Test
    fun submitAccountPin_noPinReturnsZero()
    {
        withRealAccount("pinNone") { acc ->
            // New account has no PIN
            assertEquals(0, acc.submitAccountPin("1234"))
        }
    }

    // ======================================================================
    // Asset queries (real account — brand new, no assets)
    // ======================================================================

    @Test
    fun hasAssets_emptyReturnsFalse()
    {
        withRealAccount("assetEmpty") { acc ->
            assertFalse(acc.hasAssets())
        }
    }

    @Test
    fun assetList_emptyReturnsEmptyList()
    {
        withRealAccount("assetList") { acc ->
            assertTrue(acc.assetList().isEmpty())
        }
    }

    @Test
    fun clearAssetTransferList_emptyReturnsZero()
    {
        withRealAccount("assetClear") { acc ->
            assertEquals(0, acc.clearAssetTransferList())
        }
    }

    // ======================================================================
    // currencyCode
    // ======================================================================

    @Test
    fun currencyCode_nexa()
    {
        withRealAccount("ccNexa") { acc ->
            assertEquals("NEXA", acc.currencyCode)
        }
    }

    @Test
    fun currencyCode_nexaTestnet()
    {
        withRealAccount("ccTN", ChainSelector.NEXATESTNET) { acc ->
            assertEquals("tNEX", acc.currencyCode)
        }
    }

    // ======================================================================
    // name / nameAndChain
    // ======================================================================

    @Test
    fun name_matchesCreatedName()
    {
        withRealAccount("namedAcc") { acc ->
            assertEquals("namedAcc", acc.name)
        }
    }

    @Test
    fun nameAndChain_containsNameAndChain()
    {
        withRealAccount("namedAcc2") { acc ->
            val nac = acc.nameAndChain
            assertTrue(nac.contains("namedAcc2"), "expected name in '$nac'")
            assertTrue(nac.contains("nexa"), "expected chain in '$nac'")
        }
    }

    // ======================================================================
    // saveAccountFlags / loadAccountFlags
    // ======================================================================

    @Test
    fun flagsRoundTrip()
    {
        withRealAccount("flagRT") { acc ->
            acc.flags = ACCOUNT_FLAG_HIDE_UNTIL_PIN or ACCOUNT_FLAG_REUSE_ADDRESSES
            acc.saveAccountFlags()
            acc.flags = 0UL
            acc.loadAccountFlags()
            assertEquals(ACCOUNT_FLAG_HIDE_UNTIL_PIN or ACCOUNT_FLAG_REUSE_ADDRESSES, acc.flags)
        }
    }

    // ======================================================================
    // saveAccountPin / loadEncodedPin
    // ======================================================================

    @Test
    fun pinRoundTrip()
    {
        withRealAccount("pinRT") { acc ->
            val fakePin = byteArrayOf(10, 20, 30, 40)
            acc.saveAccountPin(fakePin)
            val loaded = acc.loadEncodedPin()
            assertNotNull(loaded)
            assertTrue(fakePin.contentEquals(loaded))
        }
    }

    @Test
    fun pinSaveNull()
    {
        withRealAccount("pinNull") { acc ->
            acc.saveAccountPin(null)
            assertNull(acc.loadEncodedPin())
        }
    }

    // ======================================================================
    // Balance
    // ======================================================================

    @Test
    fun balance_canBeSet()
    {
        withRealAccount("balSet") { acc ->
            acc.balance = BigDecimal.Companion.fromInt(999)
            assertEquals(BigDecimal.Companion.fromInt(999), acc.balance)
        }
    }

    @Test
    fun balanceState_reflectsCurrentBalance()
    {
        withRealAccount("balState") { acc ->
            acc.balance = BigDecimal.Companion.fromInt(42)
            assertEquals(BigDecimal.Companion.fromInt(42), acc.balanceState.value)
        }
    }

    // ======================================================================
    // Wallet / chain wiring
    // ======================================================================

    @Test
    fun walletIsNexa()
    {
        withRealAccount("walNexa") { acc ->
            assertEquals(ChainSelector.NEXA, acc.wallet.chainSelector)
        }
    }

    @Test
    fun chainMatchesWallet()
    {
        withRealAccount("chainMatch") { acc ->
            assertEquals(acc.wallet.chainSelector, acc.chain.chainSelector)
        }
    }

    // ======================================================================
    // Lifecycle methods
    // ======================================================================

    @Test
    fun lifecycleMethods_doNotThrow()
    {
        // Use mock account for iOS target because it was failing tests by not being able to connect to a node in an infinite loop.
        if (platform().target == KotlinTarget.iOS)
        {
            val acc = mockAccount()
            acc.start()
            acc.onResume()
            acc.changeAsyncProcessing()
            acc.installChangeHandlers()
            acc.removeChangeHandlers()
            acc.saveAccountAddress()
            acc.loadAccountAddress()
            acc.saveAccountFlags()
            acc.loadAccountFlags()
        }
        else
        {
            withRealAccount("lifecycle") { acc ->
                acc.start()
                acc.onResume()
                acc.changeAsyncProcessing()
                acc.installChangeHandlers()
                acc.removeChangeHandlers()
                acc.saveAccountAddress()
                acc.loadAccountAddress()
                acc.saveAccountFlags()
                acc.loadAccountFlags()
            }
        }
    }
}