package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.accountdiscovery")

/** Given a string, this cleans up extra spaces and returns a list of the actual words */
fun processSecretWords(secretWords: String): List<String>
{
    val txt: String = secretWords.trim()
    val wordSplit = txt.split(' ','\n','\t')
    val junkDropped = wordSplit.filter { it.length > 0 }
    return junkDropped
}

fun isValidOrEmptyRecoveryPhrase(words: List<String>): Boolean {
    if(words.isEmpty()) return true
    if (words.size != 12)
    {
        return false
    }
    val incorrectWords = Bip39InvalidWords(words)
    return incorrectWords.isEmpty()
}


data class AccountSearchResults(
  val txh: List<TransactionHistory>,
  val addrCount: Long,
  val lastAddressIndex: Int,
  val balance: Long
)

fun searchDerivationPathActivity(ec: ElectrumClient, chainSelector: ChainSelector, maxGap:Int, secretDerivation: (Int) -> ByteArray?): AccountSearchResults
    {
        var addrsFound = 0L
        var index = 0
        var gap = 0
        var ret = mutableMapOf<Hash256, TransactionHistory>()
        var hdrs = mutableMapOf<Int, iBlockHeader>()
        var bal = 0L
        var lastAddressIndex = index
        while (gap < maxGap)
        {
            val newSecret = secretDerivation(index)
            if (newSecret == null) break
            val us = UnsecuredSecret(newSecret)

            val dests = mutableListOf<PayDestination>(Pay2PubKeyHashDestination(chainSelector, us, index.toLong()))  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
            if (chainSelector.hasTemplates)
                dests.add(Pay2PubKeyTemplateDestination(chainSelector, us, index.toLong()))

            var found = false
            for (dest in dests)
            {
                try
                {
                    val script = dest.outputScript()
                    val history = ec.getHistory(script, 10000)
                    if (history.size > 0)
                    {
                        found = true
                        lastAddressIndex = index
                        addrsFound++
                        for (h in history)
                        {
                            // Its easy to get repeats because a wallet sends itself change, spends 2 inputs, etc.
                            // But I don't need to investigate any repeats
                            if (!ret.containsKey(h.second))
                            {
                                val tx = ec.getTx(h.second)
                                val txh: TransactionHistory = TransactionHistory(chainSelector, tx)
                                //  Load the header at this height from a little cache we keep, or from the server
                                val header = hdrs[h.first] ?: run {
                                    val headerBytes = ec.getHeader(h.first)
                                    blockHeaderFor(chainSelector, BCHserialized(SerializationType.NETWORK, headerBytes))
                                }
                                if (header.validate(chainSelector))
                                {
                                    txh.confirmedHeight = h.first.toLong()
                                    txh.confirmedHash = header.hash
                                    txh.date = header.time
                                    ret[h.second] = txh
                                }
                            }
                        }
                        val unspent = ec.listUnspent(dest, 10000)
                        for (u in unspent)
                        {
                            found = true
                            bal += u.amount
                        }
                    }
                }
                catch (e: ElectrumNotFound)
                {
                }
            }
            if (found) gap = 0
            else gap++
            index++
        }
        return AccountSearchResults(ret.values.toList(), addrsFound, lastAddressIndex, bal)
    }
