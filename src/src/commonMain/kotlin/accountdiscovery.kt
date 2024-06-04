package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.accountdiscovery")

/** Given a string, this cleans up extra spaces and returns a list of the actual words */
fun processSecretWords(secretWords: String): List<String>
{
    val txt: String = secretWords.trim().lowercase()
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
  val balance: Long,
  val lastHeight: Long,
  val lastDate: Long,
  val lastHash: Hash256
)

fun searchDerivationPathActivity(getEc: () -> ElectrumClient, chainSelector: ChainSelector, maxGap:Int, secretDerivation: (Int) -> ByteArray?, ongoingResults: ((AccountSearchResults)->Unit)?=null): AccountSearchResults
{
        var addrsFound = 0L
        var index = 0
        var gap = 0
        var ret = mutableMapOf<Hash256, TransactionHistory>()
        var hdrs = mutableMapOf<Int, iBlockHeader>()
        var bal = 0L
        var lastAddressIndex = index
        var gapMultiplier = 1  // Works around an error in early wallets where they did not use addresses in order
        var lastHeight = 0L
        var lastDate = 0L
        var lastHash = Hash256()

        LogIt.info("all activity: first getEc: getTip()")
        val tip = getEc().getTip()
        LogIt.info("all activity: done")

        lastHeight = tip.first.height
        lastDate = tip.first.time
        lastHash = tip.first.hash

        while (gap < maxGap * gapMultiplier)
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
                    val script = dest.lockingScript()
                    LogIt.info("all activity: getEc()")
                    val ec = getEc()
                    LogIt.info("all activity: getHistory()")
                    val history = ec.getHistory(script, 10000)
                    if (history.size > 0)
                    {
                        LogIt.info("all activity: Found activity at address $index script ${script.toHex()} address ${script.address} (${history.size} events)")
                        found = true
                        lastAddressIndex = index
                        addrsFound++
                        for (h in history)
                        {
                            // Its easy to get repeats because a wallet sends itself change, spends 2 inputs, etc.
                            // But I don't need to investigate any repeats
                            if (!ret.containsKey(h.second))
                            {
                                LogIt.info("  all activity: Searching ${h.first} tx: ${h.second}")
                                val tx = getEc().getTx(h.second)
                                val txh: TransactionHistory = TransactionHistory(chainSelector, tx)
                                //  Load the header at this height from a little cache we keep, or from the server
                                val header = hdrs[h.first] ?: run {
                                    var hdr:iBlockHeader? = null
                                    val bc = blockchains[chainSelector]
                                    if (bc != null)
                                    {
                                        try
                                        {
                                            LogIt.info("all activity: get block header")
                                            hdr = bc.getBlockHeader(h.first.toLong())
                                        }
                                        catch(e: HeadersNotForthcoming)
                                        {
                                            // I dont have it saved, will load it via electrum
                                        }
                                    }
                                    if (hdr == null)
                                    {
                                        LogIt.info("all activity: get header")
                                        val headerBytes = getEc().getHeader(h.first)
                                        hdr = blockHeaderFor(chainSelector, BCHserialized(SerializationType.NETWORK, headerBytes))
                                    }
                                    LogIt.info("all activity: get header completed")
                                    hdr
                                }
                                if (header.validate(chainSelector))
                                {
                                    if (txh.confirmedHeight < WALLET_RECOVERY_NON_INCREMENTAL_ADDRESS_HEIGHT) gapMultiplier=3
                                    txh.confirmedHeight = h.first.toLong()
                                    txh.confirmedHash = header.hash
                                    txh.date = header.time
                                    ret[h.second] = txh
                                    hdrs[h.first] = header
                                }
                                else
                                {
                                    LogIt.error("Illegal header when loading asset")
                                }
                            }
                            else
                            {
                                LogIt.info("  Already have tx ${h.second}")
                            }
                        }
                        val unspent = getEc().listUnspent(dest, 10000)
                        for (u in unspent)
                        {
                            found = true
                            bal += u.amount
                        }
                        ongoingResults?.invoke(AccountSearchResults(ret.values.toList(), addrsFound, lastAddressIndex, bal, lastHeight, lastDate, lastHash))
                    }
                    else
                    {
                        LogIt.info("No activity at address $index ${dest.address.toString()}")
                    }
                }
                catch (e: ElectrumNotFound)
                {
                }
                catch (e: ElectrumRequestTimeout)
                {

                }
            }
            if (found) gap = 0
            else gap++
            index++
        }
        hdrs.clear()
        return AccountSearchResults(ret.values.toList(), addrsFound, lastAddressIndex, bal, lastHeight, lastDate, lastHash)
    }
