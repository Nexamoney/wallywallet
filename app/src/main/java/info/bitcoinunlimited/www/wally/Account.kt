package info.bitcoinunlimited.www.wally

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.wifi.WifiManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.databinding.AccountListItemBinding
import java.math.BigDecimal
import java.security.spec.InvalidKeySpecException
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.Account")

class Account(
  val name: String, //* The name of this account
  val context: PlatformContext,
  var flags: ULong = ACCOUNT_FLAG_NONE,
  chainSelector: ChainSelector? = null,
  secretWords: String? = null,
  startPlace: Long? = null, //* Where to start looking for transactions
  startHeight: Long? = null, //* block height of first activity
  retrieveOnlyActivity: MutableList<Pair<Bip44Wallet.HdDerivationPath, NewAccount.HDActivityBracket>>? = null  //* jam in other derivation paths to grab coins from (but use addresses of) (if new account)
)
{
    val tickerGUI = Reactive<String>("") // Where to show the crypto's ticker
    val balanceGUI = Reactive<String>("")
    val unconfirmedBalanceGUI = Reactive<String>("")
    val infoGUI = Reactive<String>("")
    var uiBinding: AccountListItemBinding? = null  // Maybe an easier way to do it than piecemeal as above

    val encodedPin: ByteArray? = loadEncodedPin()

    /** This is a common account display descriptor it returns "<account name> on <blockchain>", e.g. "myaccount on nexa" */
    val nameAndChain: String
        get() { return name + " " + i18n(R.string.onBlockchain) + " " + chainToURI[chain.chainSelector] }

    var wallet: Bip44Wallet = if (chainSelector == null)  // Load existing account
    {
        try
        {
            loadAccountFlags()
        } catch (e: DataMissingException)
        {
            // support older wallets by allowing empty account flags
        }
        LogIt.info(sourceLoc() + " " + ": Loading wallet " + name)
        val t = Bip44Wallet(walletDb!!, name)  // Load a saved wallet
        LogIt.info(sourceLoc() + " " + ": Loaded wallet " + name)
        val stats = t.statistics()
        LogIt.info(sourceLoc() + " " + name + ": Used Addresses: " + stats.numUsedAddrs + " Unused Addresses: " + stats.numUnusedAddrs + " Num UTXOs: " + stats.numUnspentTxos + " Num wallet events: " + t.txHistory.size)
        t
    }
    else  // New account
    {
        saveAccountFlags()
        if (secretWords == null)
            Bip44Wallet(walletDb!!, name, chainSelector, NEW_WALLET)   // New wallet
        else
            Bip44Wallet(walletDb!!, name, chainSelector, secretWords)  // Wallet recovery
    }

    //? Was the PIN entered properly since the last 15 second sleep?
    var pinEntered = false

    var currentReceive: PayDestination? = null //? This receive address appears on the main screen for quickly receiving coins
    var currentReceiveQR: Bitmap? = null

    //? Current exchange rate between this currency (including units) and your selected fiat currency
    var fiatPerCoin: BigDecimal = 0.toBigDecimal(currencyMath).setScale(16)

    //? Current bch balance (cached from accessing the wallet), in the display units
    var balance: BigDecimal = 0.toBigDecimal(currencyMath).setCurrency(chainSelector ?: ChainSelector.NEXA)
    var unconfirmedBalance: BigDecimal = 0.toBigDecimal(currencyMath).setCurrency(chainSelector ?: ChainSelector.NEXA)
    var confirmedBalance: BigDecimal = 0.toBigDecimal(currencyMath).setCurrency(chainSelector ?: ChainSelector.NEXA)

    //? specify how quantities should be formatted for display
    val cryptoFormat = mBchFormat

    val cnxnMgr: CnxnMgr = GetCnxnMgr(wallet.chainSelector, name, false)
    val chain: Blockchain = GetBlockchain(wallet.chainSelector, cnxnMgr, context, chainToURI[wallet.chainSelector], false)  // do not start right away so we can configure exclusive/preferred nodes
    var started = false  // Have the cnxnmgr and blockchain services been started or are we in initialization?

    /** A string denoting this wallet's currency units.  That is, the units that this wallet should use in display, in its BigDecimal amount representations, and is converted to and from in fromFinestUnit() and toFinestUnit() respectively */
    val currencyCode: String = chainToDisplayCurrencyCode[wallet.chainSelector]!!

    // If this coin's receive address is shown on-screen, this is not null
    var updateReceiveAddressUI: ((Account) -> Unit)? = null

    /** loading existing wallet */
    init
    {
        if (retrieveOnlyActivity != null)  // push in nonstandard addresses before we connect to the blockchain.
        {
            for (r in retrieveOnlyActivity)
            {
                assert(r.first.index == r.second.lastAddressIndex) // Caller should have properly set this.  Doublecheck.
                var tmp = r.first
                tmp.index += RETRIEVE_ONLY_ADDITIONAL_ADDRESSES
                wallet.retrieveOnlyDerivationPaths.add(tmp)
            }
        }

        wallet.fillReceivingWithRetrieveOnly()
        wallet.prepareDestinations(2, 2)  // Make sure that there is at least a few addresses before we hook into the network
        LogIt.info(sourceLoc() + name +": wallet connect blockchain ${chain.name}")
        wallet.addBlockchain(chain, startHeight, startPlace)
        LogIt.info(sourceLoc() + name +": wallet blockchain ${chain.name} connection completed")
        if (chainSelector != ChainSelector.NEXA)  // no fiat price for nextchain
        {
            val SatPerDisplayUnit = CurrencyDecimal(SATperUBCH)
            wallet.spotPrice = { currencyCode -> assert(currencyCode == fiatCurrencyCode); fiatPerCoin * CurrencyDecimal(SATperBCH) / SatPerDisplayUnit }
            wallet.historicalPrice = { currencyCode: String, epochSec: Long -> historicalUbchInFiat(currencyCode, epochSec) }
        }

        (cnxnMgr as MultiNodeCnxnMgr).getElectrumServerCandidate = { wallyApp!!.getElectrumServerOn(it) }

    }


    val visible: Boolean
        get()
        {
            if ((encodedPin != null) && ((flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN) > 0UL) && !pinEntered) return false
            return true
        }

    val lockable: Boolean
        get()
        {
            return (encodedPin != null)   // If there is no PIN, can't be locked
        }

    val locked: Boolean
        get()
        {
            if (encodedPin == null) return false  // Is never locked if there is no PIN
            return (!pinEntered)
        }

    fun loadEncodedPin(): ByteArray?
    {
        val db = walletDb!!
        try
        {
            val storedEpin = db.get("accountPin_" + name)
            if (storedEpin.size > 0) return storedEpin
            return null
        } catch (e: Exception)
        {
            LogIt.info("DB missing PIN for: " + name + ". " + e.message)
        }
        return null
    }

    /** Save the PIN of an account to the database, return 1 if account unlocked else 0 */
    fun submitAccountPin(pin: String): Int
    {
        if (encodedPin == null) return 0
        val epin = try
        {
            EncodePIN(name, pin)
        } catch (e: InvalidKeySpecException)  // ignore invalid PIN, it can't unlock any wallets
        {
            LogIt.info("user entered invalid PIN")
            return 0
        }

        if (epin.contentEquals(encodedPin))
        {
            LogIt.info("PIN unlocked " + name)
            pinEntered = true
            return 1
        }

        // If its the wrong PIN, don't set pinEntered to false, because the correct PIN might have been entered previously.
        // (This PIN entry might be for a different account)
        return 0
    }

    fun saveAccountFlags()
    {
        walletDb!!.set("accountFlags_" + name, BCHserialized.uint32(flags.toLong()).flatten())
    }

    fun loadAccountFlags()
    {
        val serFlags = walletDb!!.get("accountFlags_" + name)
        val ser = BCHserialized(serFlags, SerializationType.NETWORK)
        flags = ser.deuint32().toULong()
    }


    /** Return a web URL that will provide more information about this transaction */
    fun transactionInfoWebUrl(txHex: String?): String?
    {
        if (txHex == null) return null
        if (wallet.chainSelector == ChainSelector.BCH)
            return "https://explorer.bitcoinunlimited.info/tx/" + txHex //"https://blockchair.com/bitcoin-cash/transaction/" + txHex
        if (wallet.chainSelector == ChainSelector.NEXATESTNET)
            return "http://testnet-explorer.nexa.org/tx/" + txHex
        if (wallet.chainSelector == ChainSelector.NEXA)
            return "http://explorer.nexa.org/tx/" + txHex
        return null
    }

    //? Completely delete this wallet, rendering any money you may have in it inaccessible unless the wallet is restored from backup words
    fun delete()
    {
        currentReceive = null
        currentReceiveQR = null
        wallet.stop()
        wallet.delete()
        balance = BigDecimal.ZERO
        unconfirmedBalance = BigDecimal.ZERO
    }

    //? Disconnect from the UI and clear the UI
    fun detachUI()
    {
        dbgAssertGuiThread()
        tickerGUI("")
        balanceGUI("")
        unconfirmedBalanceGUI("")
        infoGUI("")

        tickerGUI.reactor = null
        balanceGUI.reactor = null
        unconfirmedBalanceGUI.reactor = null
        infoGUI.reactor = null

    }

    //? If running in regtest mode determine whether we are running on a simulated or actual device (on the 192 network)
    //  and return the corresponding hard-coded IP address of the regtest full node
    fun RegtestIP(): String
    {
        val wm = context.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        // LogIt.info("My IP" + ip.toString())
        if ((ip and 255) == 192)
        {
            return LanHostIP
        }
        else return SimulationHostIP
    }

    //? Set the user interface elements for this cryptocurrency
    fun setUI(ui: AccountListItemBinding, al: GuiAccountList, icon: ImageView?, ticker: TextView?, balance: TextView?, unconf: TextView?, infoView: TextView?)
    {
        uiBinding = ui
        if (ticker != null) tickerGUI.reactor = TextViewReactor<String>(ticker)
        else tickerGUI.reactor = null
        if (balance != null) balanceGUI.reactor = TextViewReactor<String>(balance, 0L, { s:String, paint: Paint ->
            val desiredWidth = displayMetrics.widthPixels/2
            paint.setTextSizeForWidth(s,desiredWidth, 30)
        })
        else balanceGUI.reactor = null
        if (unconf != null) unconfirmedBalanceGUI.reactor = TextViewReactor<String>(unconf, TextViewReactor.GONE_IF_EMPTY,
          { s:String, paint: Paint ->
              val desiredWidth = displayMetrics.widthPixels
              paint.setTextSizeForWidth(s,desiredWidth, 18)
          })
        else unconfirmedBalanceGUI.reactor = null
        if (infoView != null) infoGUI.reactor = TextViewReactor<String>(infoView)
        else infoGUI.reactor = null
    }

    fun unsetUI()
    {
        uiBinding = null
        tickerGUI.reactor = null
        balanceGUI.reactor = null
        unconfirmedBalanceGUI.reactor = null
        infoGUI.reactor = null
    }

    //? Convert the default display units to the finest granularity of this currency.  For example, mBCH to Satoshis
    fun toFinestUnit(amount: BigDecimal): Long
    {
        val ret = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> (amount* BigDecimal(SATperNEX)).toLong()
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> (amount* BigDecimal(SATperUBCH)).toLong()
        }
        return ret.toLong()
    }

    //? Convert the finest granularity of this currency to the default display unit.  For example, Satoshis to mBCH
    fun fromFinestUnit(amount: Long): BigDecimal
    {
        val factor = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> SATperNEX
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> SATperUBCH
        }
        val ret = BigDecimal(amount, currencyMath).setScale(currencyScale) / factor.toBigDecimal()
        return ret
    }

    //? Convert a value in the wallet's display currency code unit into its primary unit. The "primary unit" is the generally accepted currency unit, AKA "BCH" or "BTC".
    fun toPrimaryUnit(qty: BigDecimal): BigDecimal
    {
        val factor = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> 1
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> 1000000
        }
        return qty / factor.toBigDecimal()
    }

    //? Convert the passed quantity to a string in the decimal format suitable for this currency
    fun format(qty: BigDecimal): String
    {
        return when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> nexFormat.format(qty)
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> uBchFormat.format(qty)
        }
    }

    data class ReceiveInfoResult(val addrString: String?, val qr: Bitmap?)

    suspend fun ifUpdatedReceiveInfo(sz: Int, refresh: (String, Bitmap) -> Unit) = onUpdatedReceiveInfo(sz, refresh)

    suspend fun onUpdatedReceiveInfo(sz: Int, refresh: ((String, Bitmap) -> Unit)): Unit
    {
        currentReceive.let {
            val addr: PayAddress? = it?.address
            val qr = currentReceiveQR
            if ((it == null) || (addr == null) || (qr == null) || (wallet.getBalanceIn(addr) > 0))
            {
                currentReceive = null
                currentReceiveQR = null

                val ret = wallet.newDestination()
                val qr2 = textToQREncode(ret.address.toString(), sz + 200)
                currentReceive = ret
                currentReceiveQR = qr2
                if (qr2 != null) refresh.invoke(ret.address.toString(), qr2)
            }
            else
            {
                refresh.invoke(it.address.toString(), qr)
            }
        }
    }


    //? Return a string and bitmap that corresponds to the current receive address, with a suggested quantity specified in the URI's standard units, i.e BCH.
    //? Provide qty in this currency code's units (i.e. mBCH)
    fun receiveInfoWithQuantity(qty: BigDecimal, sz: Int, refresh: ((ReceiveInfoResult) -> Unit))
    {
        launch {
            val addr = currentReceive?.address
            val uri = addr.toString() + "?amount=" + bchFormat.format(toPrimaryUnit(qty))
            val qr = textToQREncode(uri, sz)
            refresh(ReceiveInfoResult(uri, qr))
        }
    }


    fun getReceiveQR(sz: Int): Bitmap
    {
        var im = currentReceiveQR
        val cr = currentReceive
        if ((im == null) && (cr != null))
        {
            im = textToQREncode(cr.address.toString(), sz + 200)
            currentReceiveQR = im
        }

        return im!! // It must be not null because if null I set it
    }

    // This can be called either when the app as been paused, or early during app initialization
    // so we need to check to see if the is an actual resume-after-pause, or an initial startup
    fun onResume()
    {
        if (started)
        {
            LogIt.info("App resuming: Restarting threads if needed")
            wallet.restart()
            wallet.chainstate?.chain?.restart()
            wallet.chainstate?.chain?.net?.restart()
            onChange(true)
        }
    }

    /** Call whenever the state of this account has changed so needs to be redrawn.  Or on first draw (with force = true) */
    fun onChange(force: Boolean = false)
    {
        uiBinding?.let {
            if (lockable)
            {
                it.lockIcon.visibility = View.VISIBLE
                if (locked)
                    it.lockIcon.setImageResource(R.drawable.ic_lock)
                else
                    it.lockIcon.setImageResource(R.drawable.ic_unlock)
            }
            else
                it.lockIcon.visibility = View.GONE
        }
        notInUI {
            // Update our cache of the balances
            balance = fromFinestUnit(wallet.balance)
            unconfirmedBalance = fromFinestUnit(wallet.balanceUnconfirmed)
            confirmedBalance = fromFinestUnit(wallet.balanceConfirmed)

            val delta = balance - confirmedBalance
            val chainstate = wallet.chainstate
            var unconfBalStr = ""
            if (chainstate != null)
            {
                if (chainstate.isSynchronized(1,60*60))  // ignore 1 block desync or this displays every time a new block is found
                {
                    unconfBalStr =
                      if (0.toBigDecimal(currencyMath).setScale(currencyScale) == unconfirmedBalance)
                          ""
                      else
                          i18n(R.string.incoming) % mapOf(
                            "delta" to (if (delta > BigDecimal.ZERO) "+" else "") + format(balance - confirmedBalance),
                            "unit" to currencyCode
                          )

                    laterUI {
                        balanceGUI.setAttribute("strength", "normal")
                        if (delta > BigDecimal.ZERO) unconfirmedBalanceGUI.setAttribute("color", R.color.colorCredit)
                        else unconfirmedBalanceGUI.setAttribute("color", R.color.colorDebit)
                    }
                }
                else
                {
                    unconfBalStr = if (chainstate.syncedDate <= 1231416000) i18n(R.string.unsynced)  // for fun: bitcoin genesis block
                        else i18n(R.string.balanceOnTheDate) % mapOf("date" to epochToDate(chainstate.syncedDate))
                    laterUI {
                        balanceGUI.setAttribute("strength", "dim")
                        unconfirmedBalanceGUI.setAttribute("color", R.color.unsyncedStatusColor)
                    }
                }
            }
            else
            {
                balanceGUI.setAttribute("strength","dim")
                //unconfirmedBalanceGUI(i18n(R.string.walletDisconnectedFromBlockchain), force)
                uiBinding?.balanceUnconfirmedValue?.text = i18n(R.string.walletDisconnectedFromBlockchain)
            }


                balanceGUI(format(balance), force)
                unconfirmedBalanceGUI(unconfBalStr, force)
                //uiBinding?.balanceValue?.text = format(balance)
                //uiBinding?.balanceUnconfirmedValue?.text = unconfBalStr

                // If we got something in a receive address, then show a new one
                updateReceiveAddressUI?.invoke(this)

                if (chainstate != null)
                {
                    val cnxnLst = wallet.chainstate?.chain?.net?.mapConnections() { it.name }

                    val trying: List<String> = if (chainstate.chain.net is MultiNodeCnxnMgr) (chainstate.chain.net as MultiNodeCnxnMgr).initializingCnxns.map { it.name } else listOf()
                    val peers = cnxnLst?.joinToString(", ") + (if (trying.isNotEmpty()) (" " + i18n(R.string.trying) + " " + trying.joinToString(", ")) else "")

                    val infoStr = i18n(R.string.at) + " " + (wallet.chainstate?.syncedHash?.toHex()?.take(8) ?: "") + ", " + (wallet.chainstate?.syncedHeight
                      ?: "") + " " + i18n(R.string.of) + " " + (wallet.chainstate?.chain?.curHeight
                      ?: "") + " blocks, " + (wallet.chainstate?.chain?.net?.size ?: "") + " peers\n" + peers
                    infoGUI(force, { infoStr })  // since numPeers takes cnxnLock
                    //uiBinding?.Info?.text = infoStr
                }
                else
                {
                    //uiBinding?.Info?.text = i18n(R.string.walletDisconnectedFromBlockchain)
                    infoGUI(force, { i18n(R.string.walletDisconnectedFromBlockchain) })
                }

                tickerGUI(name, force)
                //uiBinding?.balanceTickerText?.text = name
        }
    }

    // Load the exchange rate
    fun getXchgRates(fiatCurrencyCode: String)
    {
        if (chain.chainSelector != ChainSelector.BCH)
        {
            fiatPerCoin = -1.toBigDecimal()  // Indicates that the exchange rate is unavailable
            return
        }

        // Only for BCH
        MbchInFiat(fiatCurrencyCode) { v: BigDecimal ->
            fiatPerCoin = v;
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun start(ac: Context)
    {
        if (!started)
        {
            cnxnMgr.start()
            chain.start()
            started=true
        }
    }
}
