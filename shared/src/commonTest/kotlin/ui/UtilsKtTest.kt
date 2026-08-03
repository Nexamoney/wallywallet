package ui

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.chainToDisplayCurrencyCode
import info.bitcoinunlimited.www.wally.currencyDecimals
import info.bitcoinunlimited.www.wally.formatAmount
import info.bitcoinunlimited.www.wally.formatLocalDateTime
import info.bitcoinunlimited.www.wally.formatLocalEpochMilliseconds
import info.bitcoinunlimited.www.wally.isCashAddrScheme
import info.bitcoinunlimited.www.wally.onlyDecimal
import info.bitcoinunlimited.www.wally.onlyDigits
import info.bitcoinunlimited.www.wally.resolve
import info.bitcoinunlimited.www.wally.scanForFirstAddress
import info.bitcoinunlimited.www.wally.splitIntoSet
import info.bitcoinunlimited.www.wally.trimToNull
import info.bitcoinunlimited.www.wally.first
import io.ktor.http.Url
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.nexa.libnexakotlin.ChainSelector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-logic tests for utils.kt. Kept separate from [UtilsTest] (which holds the
 * composable visual test) so these can run without a Compose UI harness.
 */
class UtilsKtTest : WallyUiTestBase()
{
    // ---------- String.onlyDigits ----------

    @Test fun onlyDigits_allDigits() = assertTrue("12345".onlyDigits())
    @Test fun onlyDigits_empty() = assertTrue("".onlyDigits())
    @Test fun onlyDigits_withLetter() = assertFalse("12a45".onlyDigits())
    @Test fun onlyDigits_withDot() = assertFalse("12.3".onlyDigits())
    @Test fun onlyDigits_withSign() = assertFalse("-12".onlyDigits())
    @Test fun onlyDigits_withSpace() = assertFalse(" 12".onlyDigits())

    // ---------- String.onlyDecimal ----------

    @Test fun onlyDecimal_dot() = assertTrue("12.34".onlyDecimal())
    @Test fun onlyDecimal_comma() = assertTrue("12,34".onlyDecimal())
    @Test fun onlyDecimal_plain() = assertTrue("12".onlyDecimal())
    @Test fun onlyDecimal_empty() = assertTrue("".onlyDecimal())
    @Test fun onlyDecimal_exponent() = assertFalse("12.34e5".onlyDecimal())
    @Test fun onlyDecimal_negative() = assertFalse("-12.3".onlyDecimal())
    // Current impl does not enforce a single separator; pin this behavior.
    @Test fun onlyDecimal_multipleSeparatorsAccepted() = assertTrue("1.2.3".onlyDecimal())

    // ---------- String.trimToNull ----------

    @Test fun trimToNull_trimmed() = assertEquals("hello", "  hello  ".trimToNull())
    @Test fun trimToNull_empty() = assertNull("".trimToNull())
    @Test fun trimToNull_blank() = assertNull("   ".trimToNull())
    @Test fun trimToNull_whitespaceOnly() = assertNull("\t\n".trimToNull())
    @Test fun trimToNull_single() = assertEquals("x", "x".trimToNull())

    // ---------- String.splitIntoSet ----------

    @Test fun splitIntoSet_commas() = assertEquals(setOf("a", "b", "c"), "a,b,c".splitIntoSet())
    @Test fun splitIntoSet_spaces() = assertEquals(setOf("a", "b", "c"), "a b c".splitIntoSet())
    @Test fun splitIntoSet_mixedWithWhitespace() = assertEquals(setOf("a", "b", "c", "d"), "a, b ,c  d".splitIntoSet())
    @Test fun splitIntoSet_empty() = assertEquals(emptySet(), "".splitIntoSet())
    @Test fun splitIntoSet_separatorsOnly() = assertEquals(emptySet(), ",, ,".splitIntoSet())
    @Test fun splitIntoSet_dedup() = assertEquals(setOf("a", "b"), "a,a,b".splitIntoSet())

    // ---------- isCashAddrScheme ----------

    @Test fun isCashAddrScheme_nexaLower() = assertTrue(isCashAddrScheme("nexa"))
    @Test fun isCashAddrScheme_nexaUpper() = assertTrue(isCashAddrScheme("NEXA"))
    @Test fun isCashAddrScheme_bogus() = assertFalse(isCashAddrScheme("ethereum"))
    @Test fun isCashAddrScheme_empty() = assertFalse(isCashAddrScheme(""))

    // ---------- ChainSelector.currencyDecimals ----------

    @Test fun currencyDecimals_nexaVariantsAllEqual()
    {
        val d = ChainSelector.NEXA.currencyDecimals
        assertEquals(d, ChainSelector.NEXATESTNET.currencyDecimals)
        assertEquals(d, ChainSelector.NEXAREGTEST.currencyDecimals)
    }

    @Test fun currencyDecimals_bchVariantsAllEqual()
    {
        val d = ChainSelector.BCH.currencyDecimals
        assertEquals(d, ChainSelector.BCHTESTNET.currencyDecimals)
        assertEquals(d, ChainSelector.BCHREGTEST.currencyDecimals)
    }

    // ---------- chainToDisplayCurrencyCode ----------

    @Test fun chainToDisplayCurrencyCode_entries()
    {
        assertEquals(6, chainToDisplayCurrencyCode.size)
        assertEquals("NEXA", chainToDisplayCurrencyCode[ChainSelector.NEXA])
        assertEquals("tNEX", chainToDisplayCurrencyCode[ChainSelector.NEXATESTNET])
        assertEquals("rNEX", chainToDisplayCurrencyCode[ChainSelector.NEXAREGTEST])
        assertEquals("uBCH", chainToDisplayCurrencyCode[ChainSelector.BCH])
        assertEquals("tuBCH", chainToDisplayCurrencyCode[ChainSelector.BCHTESTNET])
        assertEquals("ruBCH", chainToDisplayCurrencyCode[ChainSelector.BCHREGTEST])
    }

    // ---------- formatLocalDateTime ----------

    @Test fun formatLocalDateTime_padsSingleDigits()
    {
        val ldt = LocalDateTime(2024, 1, 2, 3, 4, 5)
        assertEquals("2024-01-02 03:04:05", formatLocalDateTime(ldt))
    }

    @Test fun formatLocalDateTime_customSplitter()
    {
        val ldt = LocalDateTime(2024, 12, 31, 23, 59, 59)
        assertEquals("2024-12-31T23:59:59", formatLocalDateTime(ldt, "T"))
    }

    // ---------- formatLocalEpochMilliseconds ----------

    @OptIn(kotlin.time.ExperimentalTime::class)
    @Test fun formatLocalEpochMilliseconds_formatsKnownInstant()
    {
        // Derive epochMs from a local datetime using the system TZ so the expected
        // string is timezone-independent on any CI machine.
        val local = LocalDateTime(2024, 3, 14, 9, 26, 53)
        val epochMs = local.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        // NOTE: current impl uses `ldt.month.toString()` (enum name), not the zero-padded number.
        // Pinned here so a future fix to use `monthNumber` surfaces as a failing test to update.
        assertEquals("2024-MARCH-14 09:26:53", formatLocalEpochMilliseconds(epochMs))
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    @Test fun formatLocalEpochMilliseconds_customSplitter()
    {
        val local = LocalDateTime(2024, 3, 14, 9, 26, 53)
        val epochMs = local.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        assertEquals("2024-MARCH-14T09:26:53", formatLocalEpochMilliseconds(epochMs, "T"))
    }

    // ---------- scanForFirstAddress ----------

    @Test fun scanForFirstAddress_findsEmbeddedAddress()
    {
        val addr = "nexa:nqtsq5g53fjq76u7y5kfuw8xqj3s7almzsqsjgypt2gs8ne2"
        val pa = scanForFirstAddress("Please send 5 NEXA to $addr, thanks!")
        assertNotNull(pa)
        assertEquals(addr, pa.toString())
    }

    @Test fun scanForFirstAddress_returnsNullWhenNone()
    {
        assertNull(scanForFirstAddress("no address here at all"))
    }

    @Test fun scanForFirstAddress_skipsShortWords()
    {
        // Nothing >32 chars that is also a valid address
        assertNull(scanForFirstAddress("abc def ghi jkl mno pqr stu vwx yz"))
    }

    // ---------- io.ktor.http.Url.resolve custom extension function ----------

    @Test fun urlResolve_relativeString()
    {
        val base = Url("https://example.com/api/")
        val resolved = base.resolve("v1/ping")
        assertEquals("https://example.com/api/v1/ping", resolved.toString())
    }

    @Test fun urlResolve_absoluteUrlReplaces()
    {
        val base = Url("https://example.com/api/")
        val resolved = base.resolve(Url("https://other.com/path"))
        assertTrue(resolved.toString().startsWith("https://other.com/"))
    }

    // ---------- List<String>.first shadow ----------
    // The module-local extension shadows kotlin.collections.first for List<String>.
    // Verify current behavior so a later removal surfaces as a failing test.

    @Test fun listStringFirst_returnsFirstElement()
    {
        assertEquals("a", listOf("a", "b").first())
    }

    @Test fun listStringFirst_emptyThrows()
    {
        // this[0] on empty list -> IndexOutOfBoundsException
        assertFails { listOf<String>().first() }
    }
}
