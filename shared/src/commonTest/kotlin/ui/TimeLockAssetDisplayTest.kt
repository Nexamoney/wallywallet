package ui

import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GroupAuthorityFlags
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import org.nexa.libnexakotlin.fromHex
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure token/baton display helpers used by the TimeLock
 * vault card and history rows ([shortGroupId], [authorityCapabilities]).
 */
class TimeLockAssetDisplayTest
{
    @Test
    fun shortGroupIdAbbreviatesLongHex()
    {
        val group = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        assertEquals("aabbccddee…8899", shortGroupId(group))
    }

    @Test
    fun shortGroupIdLeavesShortStringsUnchanged()
    {
        assertEquals("abcd1234", shortGroupId("abcd1234"))
        assertEquals("0123456789abcdef", shortGroupId("0123456789abcdef")) // exactly 16
    }

    private fun authority(
        mint: Boolean = false,
        melt: Boolean = false,
        baton: Boolean = false,
        rescript: Boolean = false,
        subgroup: Boolean = false,
    ): GroupInfo
    {
        var flags = GroupAuthorityFlags.AUTHORITY
        if (mint) flags = flags or GroupAuthorityFlags.MINT
        if (melt) flags = flags or GroupAuthorityFlags.MELT
        if (baton) flags = flags or GroupAuthorityFlags.BATON
        if (rescript) flags = flags or GroupAuthorityFlags.RESCRIPT
        if (subgroup) flags = flags or GroupAuthorityFlags.SUBGROUP
        val groupHex = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        return GroupInfo(GroupId(ChainSelector.NEXATESTNET, groupHex.fromHex()), 0L, flags)
    }

    @Test
    fun authorityCapabilitiesJoinsSetFlagsInOrder()
    {
        assertEquals(i18n(S.tlvCapMint) + "·" + i18n(S.tlvCapMelt), authorityCapabilities(authority(mint = true, melt = true)))
        assertEquals(
            listOf(S.tlvCapMint, S.tlvCapMelt, S.tlvCapBaton, S.tlvCapRescript, S.tlvCapSubgroup)
                .joinToString("·") { i18n(it) },
            authorityCapabilities(
                authority(mint = true, melt = true, baton = true, rescript = true, subgroup = true),
            ),
        )
        assertEquals(i18n(S.tlvCapBaton), authorityCapabilities(authority(baton = true)))
    }

    @Test
    fun authorityCapabilitiesFallsBackToAuthorityWhenNoFlags()
    {
        assertEquals(i18n(S.tlvAuthority), authorityCapabilities(authority()))
    }
}
