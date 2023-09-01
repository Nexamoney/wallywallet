package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import kotlin.synchronized

private val LogIt = GetLog("BU.wally.accountdiscovery")

/** Given a string, this cleans up extra spaces and returns a list of the actual words */
fun processSecretWords(secretWords: String): List<String>
{
    val txt: String = secretWords.trim()
    val wordSplit = txt.split(' ','\n','\t')
    val junkDropped = wordSplit.filter { it.length > 0 }
    return junkDropped
}


/*
fun peekActivity(secretWords: String, chainSelector: ChainSelector, aborter: Objectify<Boolean>)
{
    val (svr, port) = try
    {
        wallyApp!!.getElectrumServerOn(chainSelector)
    }
    catch (e: BadCryptoException)
    {
        LogIt.info("peek not supported for this blockchain")
        return
    }

    if (aborter.obj) return
    val ec = try
    {
        ElectrumClient(chainSelector, svr, port, useSSL = true)
    }
    catch (e: ElectrumConnectError) // covers java.net.ConnectException, UnknownHostException and a few others that could trigger
    {
        try
        {
            ElectrumClient(chainSelector, svr, port, useSSL = false, accessTimeoutMs = 60000, connectTimeoutMs = 10000)
        }
        catch (e: ElectrumConnectError)
        {
            if (chainSelector == ChainSelector.BCH)
                ElectrumClient(chainSelector, LAST_RESORT_BCH_ELECTRS)
            else if (chainSelector == ChainSelector.NEXA)
                ElectrumClient(chainSelector, LAST_RESORT_NEXA_ELECTRS, DEFAULT_NEXA_TCP_ELECTRUM_PORT, useSSL = false)
            else throw e
        }
        catch (e: ElectrumConnectError)
        {
            laterUI {
                ui.GuiNewAccountStatus.text = i18n(R.string.ElectrumNetworkUnavailable)
                ui.GuiStatusOk.setImageResource(android.R.drawable.ic_delete)
            }
            return
        }
    }
    ec.start()
    if (aborter.obj) return

    val passphrase = "" // TODO: support a passphrase
    val secret = generateBip39Seed(secretWords, passphrase)

    val addressDerivationCoin = Bip44AddressDerivationByChain(chainSelector)

    LogIt.info("Searching in ${addressDerivationCoin}")
    var earliestActivityP =
      searchActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, {
          libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, addressDerivationCoin, 0, false, it).first
      }, { time, height ->
          laterUI {
              ui.GuiNewAccountStatus.text = i18n(R.string.Bip44ActivityNotice) + " " + (i18n(R.string.FirstUseDateHeightInfo) % mapOf(
                "date" to epochToDate(time),
                "height" to height.toString())
                )
              synchronized(earliestActivityHeight) {
                  earliestActivity = time
                  earliestActivityHeight = height
              }
          }
          true
      })

    if (aborter.obj) return

    LogIt.info("Searching in ${AddressDerivationKey.ANY}")
    // Look for activity in the identity and common location
    var earliestActivityId =
      searchActivity(ec, chainSelector, IDENTITY_DERIVATION_PATH_SEARCH_DEPTH, {
          libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.ANY, 0, false, it).first
      })
    if (aborter.obj) return

    // Set earliestActivityP to the lesser of the two
    if (earliestActivityP == null) earliestActivityP = earliestActivityId
    else
    {
        if ((earliestActivityId != null) && (earliestActivityId.first < earliestActivityP.first)) earliestActivityP = earliestActivityId
    }
    if (aborter.obj) return
    val Bip44Msg = if (earliestActivityP != null)
    {
        synchronized(earliestActivityHeight) {
            earliestActivity = earliestActivityP.first - 1 // -1 so earliest activity is just before the activity
            earliestActivityHeight = earliestActivityP.second
        }
        i18n(R.string.Bip44ActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
          "date" to epochToDate(earliestActivityP.first),
          "height" to earliestActivityP.second.toString()
        )
    }
    else
    {
        synchronized(earliestActivityHeight) {
            earliestActivity = null
            earliestActivityHeight = 0
        }
        i18n(R.string.NoBip44ActivityNotice)
    }

    val Bip44BTCMsg = ""

    if (false)
    {
        // Look in non-standard places for activity
        val BTCactivity =
          bracketActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 0, it) })
        var BTCchangeActivity: HDActivityBracket?

        // This code checks whether coins exist on the Bitcoin derivation path to see if any prefork coins exist.  This is irrelevant for Nexa.
        // I'm leaving the code in though because someday we might want to share pubkeys between BTC/BCH and Nexa and in that case we'd need to use their derivation path.

        var Bip44BTCMsg = if (BTCactivity != null)
        {
            BTCchangeActivity =
              bracketActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 1, it) })
            nonstandardActivity.clear()  // clear because peek can be called multiple times if the user changes the secret
            nonstandardActivity.add(Pair(Bip44Wallet.HdDerivationPath(null, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 0, BTCactivity.lastAddressIndex), BTCactivity))
            if (BTCchangeActivity != null)
            {
                nonstandardActivity.add(Pair(Bip44Wallet.HdDerivationPath(null, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 1, BTCchangeActivity.lastAddressIndex), BTCchangeActivity))
            }

            i18n(R.string.Bip44BtcActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
              "date" to epochToDate(BTCactivity.startTime),
              "height" to BTCactivity.startBlockHeight.toString()
            )
        }
        else i18n(R.string.NoBip44BtcActivityNotice)



        earliestActivityP = searchActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP43, AddressDerivationKey.BTC, 0, 0, it) })
        var Bip44BTCMsg = if (earliestActivityP != null)
        {
            earliestActivity = earliestActivityP.first - 1 // -1 so earliest activity is just before the activity
            i18n(R.string.Bip44BtcActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
              "date" to epochToDate(earliestActivityP.first),
              "height" to earliestActivityP.second.toString())
        }
        else i18n(R.string.NoBip44BtcActivityNotice)
    }

    laterUI {
        ui.GuiNewAccountStatus.text = Bip44Msg + "\n" + Bip44BTCMsg
        if (earliestActivity != null) ui.GuiStatusOk.setImageResource(R.drawable.ic_check)
        else ui.GuiStatusOk.setImageResource(android.R.drawable.ic_delete)
    }
}

*/