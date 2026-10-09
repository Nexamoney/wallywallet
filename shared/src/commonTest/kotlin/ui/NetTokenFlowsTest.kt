package ui

import info.bitcoinunlimited.www.wally.ui.netTokenFlows
import org.nexa.libnexakotlin.*
import kotlin.test.Test
import kotlin.test.assertEquals

class NetTokenFlowsTest
{
    init {
        initializeLibNexa()
    }

    val cs = ChainSelector.NEXA
    private val mine = PayAddress("nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw")
    // Plain token groups: the last byte of a group id holds its flags (covenant, fenced), so leave it 0
    private val gidA = GroupId(cs, ByteArray(32).also { it[0] = 1 })
    private val gidB = GroupId(cs, ByteArray(32).also { it[0] = 2 })

    /** A history of a tx with [outputs], where [incoming] are the wallet's outputs and [spent] the wallet's txos it spends */
    private fun history(outputs: List<iTxOutput>, incoming: List<Long>, spent: List<iTxOutput>): TransactionHistory
    {
        val tx = txFor(cs)
        outputs.forEach { tx.add(it) }
        val txh = TransactionHistory(cs, tx)
        txh.incomingIdxes.addAll(incoming)
        txh.spentTxos.addAll(spent)
        return txh
    }

    @Test
    fun receivedTokensArePositive()
    {
        val txh = history(listOf(txOutputFor(mine, 300L, gidA)), incoming = listOf(0L), spent = listOf())
        assertEquals(mapOf(gidA to 300L), txh.netTokenFlows())
    }

    @Test
    fun sentTokensNetOutTheChange()
    {
        // Spend 1000 of the wallet's tokens, pay 800 away and get 200 back as change
        val txh = history(listOf(txOutputFor(mine, 800L, gidA), txOutputFor(mine, 200L, gidA)),
          incoming = listOf(1L), spent = listOf(txOutputFor(mine, 1000L, gidA)))
        assertEquals(mapOf(gidA to -800L), txh.netTokenFlows())
    }

    @Test
    fun eachGroupKeepsItsOwnDirection()
    {
        // A swap: A leaves the wallet while B comes in, whichever way the native coin went
        val txh = history(listOf(txOutputFor(mine, 50L, gidA), txOutputFor(mine, 7L, gidB)),
          incoming = listOf(1L), spent = listOf(txOutputFor(mine, 50L, gidA)))
        assertEquals(mapOf(gidA to -50L, gidB to 7L), txh.netTokenFlows())
    }

    @Test
    fun tokensMovedBetweenOwnAddressesAreLeftOut()
    {
        val txh = history(listOf(txOutputFor(mine, 400L, gidA)), incoming = listOf(0L), spent = listOf(txOutputFor(mine, 400L, gidA)))
        assertEquals(mapOf(), txh.netTokenFlows())
    }
}
