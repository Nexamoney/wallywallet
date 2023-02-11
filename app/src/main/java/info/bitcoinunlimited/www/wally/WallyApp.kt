// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.

package info.bitcoinunlimited.www.wally

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.databinding.AccountListItemBinding
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.net.ConnectException
import java.security.spec.InvalidKeySpecException
import java.util.*
import java.util.concurrent.Executors
import java.util.logging.Logger
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.CoroutineContext

val LAST_RESORT_BCH_ELECTRS = "bch2.bitcoinunlimited.net"
val LAST_RESORT_NEXA_ELECTRS = "electrum.nexa.org"


const val NOTIFICATION_CHANNEL_ID = "n"

private val LogIt = Logger.getLogger("BU.wally.app")

var coinsCreated = false

/** Currently selected fiat currency code */
var fiatCurrencyCode: String = "USD"

val SupportedBlockchains =
    mapOf(
      "NEXA" to ChainSelector.NEXA,
      "BCH (Bitcoin Cash)" to ChainSelector.BCH,
      "TNEX (Testnet Nexa)" to ChainSelector.NEXATESTNET,
      "RNEX (Regtest Nexa)" to ChainSelector.NEXAREGTEST,
      "TBCH (Bitcoin Cash)" to ChainSelector.BCHTESTNET,
      "RBCH (Bitcoin Cash)" to ChainSelector.BCHREGTEST
    )

val ChainSelectorToSupportedBlockchains = SupportedBlockchains.entries.associate { (k, v) -> v to k }

// What is the default wallet and blockchain to use for most functions (like identity)
val PRIMARY_CRYPTO = if (REG_TEST_ONLY) ChainSelector.NEXAREGTEST else ChainSelector.NEXA

/** incompatible changes, extra fields added, fields and field sizes are the same, but content may be extended (that is, addtl bits in enums) */
val WALLY_DATA_VERSION = byteArrayOf(1, 0, 0)

var walletDb: KvpDatabase? = null
var wallyApp: WallyApp? = null

var devMode: Boolean = false

const val ACCOUNT_FLAG_NONE = 0UL
const val ACCOUNT_FLAG_HIDE_UNTIL_PIN = 1UL
const val RETRIEVE_ONLY_ADDITIONAL_ADDRESSES = 10

/** Store the PIN encoded.  However, note that for short PINs a dictionary attack is very feasible */
fun EncodePIN(actName: String, pin: String, size: Int = 64): ByteArray
{
    val salt = "wally pin " + actName
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    val secretkey = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 2048, 512)
    val seed = skf.generateSecret(secretkey)
    return seed.encoded.slice(IntRange(0, size - 1)).toByteArray()
}

data class LongPollInfo(val proto: String, val hostPort: String, val cookie: String?, var active: Boolean = true)

class AccessHandler(val app: WallyApp)
{
    var done: Boolean = false

    val activeLongPolls = mutableMapOf<String, LongPollInfo>()

/* How to know that the app is shutting down in android?
    fun endAll()
    {
        for (lp in longPollInfo)
        {
            launch {
                endLongPolling(lp.proto, lp.hostPort, lp.cookie)
            }
        }
    }

    suspend fun endLongPolling(proto: String, hostPort: String, cookie: String?)
    {
        val cookieString = if (cookie != null) "?cookie=$cookie" else ""
        val url = proto + "//" + hostPort + "/_lpx" + cookieString
        val client = HttpClient(Android)
        {
            install(HttpTimeout) { requestTimeoutMillis = 2000 } // Long timeout because we don't expect a response right away; its a long poll
        }
        val response: HttpResponse = client.get(url) {}
    }
    */

    fun startLongPolling(proto: String, hostPort: String, cookie: String?)
    {
        app.later { longPolling(proto, hostPort, cookie) }
    }

    fun endLongPolling(url: String)
    {
        synchronized(activeLongPolls)
        {
            activeLongPolls.remove(url)
        }
    }

    suspend fun longPolling(proto: String, hostPort: String, cookie: String?)
    {
        var connectProblems = 0
        val cookieString = if (cookie != null) "?cookie=$cookie" else ""
        val url = proto + "//" + hostPort + "/_lp" + cookieString

        val lpInfo = synchronized(activeLongPolls)
        {
            if (activeLongPolls.contains(url))
            {
                LogIt.info("Already long polling to $url, replacing it.")
                activeLongPolls[url]?.active = false
            }
            activeLongPolls.put(url, LongPollInfo(proto, hostPort, cookie))
            activeLongPolls[url]!!
        }

        val client = HttpClient(Android)
        {
            install(HttpTimeout) { requestTimeoutMillis = 60000 } // Long timeout because we don't expect a response right away; its a long poll
        }

        var count = 0
        while (!done && lpInfo.active)
        {
            try
            {

                val response: HttpResponse = client.get(url + "&i=${count}") {}
                val respText = response.bodyAsText()
                connectProblems = 0

                LogIt.info("Long poll to $url resp: $respText")
                if (respText == "Q")
                {
                    LogIt.info("Long poll to $url ended (server request).")
                    endLongPolling(url)
                    return // Server tells us to quit long polling
                }
                val ci = app.currentActivity
                if (ci != null) ci.handleAnyIntent(respText)
                else LogIt.info("cannot handle long poll response, no current activity")
                count += 1
            }
            catch (e: ConnectException)  // network error?  TODO retry a few times
            {
                if (connectProblems > 500)
                {
                    LogIt.info("Long poll to $url connection exception $e, stopping")
                    endLongPolling(url)
                    return
                }
                connectProblems += 1
                delay(1000)
            } catch (e: Throwable)
            {
                LogIt.info("Long poll to $url error, stopping: ")
                LogIt.info(e.toString())
                endLongPolling(url)
                return
            }
            delay(200) // limit runaway polling, if the server misbehaves by responding right away
        }
        LogIt.info("Long poll to $url ended (done).")
        endLongPolling(url)
    }
}

class Account(
  val name: String, //* The name of this account
  val context: PlatformContext,
  var flags: ULong = ACCOUNT_FLAG_NONE,
  chainSelector: ChainSelector? = null,
  secretWords: String? = null,
  startPlace: Long? = null, //* Where to start looking for transactions
  retrieveOnlyActivity: MutableList<Pair<Bip44Wallet.HdDerivationPath, NewAccount.HDActivityBracket>>? = null  //* jam in other derivation paths to grab coins from (but use addresses of) (if new account)
)
{
    val tickerGUI = Reactive<String>("") // Where to show the crypto's ticker
    val balanceGUI = Reactive<String>("")
    val unconfirmedBalanceGUI = Reactive<String>("")
    val infoGUI = Reactive<String>("")
    var uiBinding:AccountListItemBinding? = null  // Maybe an easier way to do it than piecemeal as above

    val encodedPin: ByteArray? = loadEncodedPin()

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
        wallet.addBlockchain(chain, chain.checkpointHeight, startPlace)
        LogIt.info(sourceLoc() + name +": wallet blockchain ${chain.name} connection completed")
        if (chainSelector != ChainSelector.NEXA)  // no fiat price for nextchain
        {
            val SatPerDisplayUnit = CurrencyDecimal(SATperUBCH)
            wallet.spotPrice = { currencyCode -> assert(currencyCode == fiatCurrencyCode); fiatPerCoin * CurrencyDecimal(SATperBCH) / SatPerDisplayUnit }
            wallet.historicalPrice = { currencyCode: String, epochSec: Long -> historicalUbchInFiat(currencyCode, epochSec) }
        }

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
            return "http://testnet.nexa.org/tx/" + txHex
        if (wallet.chainSelector == ChainSelector.NEXA)
            return "http://explorer.nexa.org/tx/" + txHex
        return null
    }

    //? Completely delete this wallet, rendering any money you may have in it inaccessible unless the wallet is restored from backup words
    fun delete()
    {
        currentReceive = null
        currentReceiveQR = null
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
        if (balance != null) balanceGUI.reactor = TextViewReactor<String>(balance, 0L, { s:String, paint:Paint ->
            val desiredWidth = displayMetrics.widthPixels/2
            paint.setTextSizeForWidth(s,desiredWidth, 30)
        })
        else balanceGUI.reactor = null
        if (unconf != null) unconfirmedBalanceGUI.reactor = TextViewReactor<String>(unconf, TextViewReactor.GONE_IF_EMPTY,
          { s:String, paint:Paint ->
            val desiredWidth = displayMetrics.widthPixels
            paint.setTextSizeForWidth(s,desiredWidth, 18)
        })
        else unconfirmedBalanceGUI.reactor = null
        if (infoView != null) infoGUI.reactor = TextViewReactor<String>(infoView)
        else infoGUI.reactor = null
    }

    //? Convert the default display units to the finest granularity of this currency.  For example, mBCH to Satoshis
    fun toFinestUnit(amount: BigDecimal): Long
    {
        val ret = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> (amount*BigDecimal(SATperNEX)).toLong()
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> (amount*BigDecimal(SATperUBCH)).toLong()
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
            onWalletChange(true)
        }
    }

    fun onWalletChange(force: Boolean = false)
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
            if (chainstate != null)
            {
                if (chainstate.isSynchronized(1,60*60))  // ignore 1 block desync or this displays every time a new block is found
                {
                    balanceGUI.setAttribute("strength", "normal")
                    val unconfBalStr =
                      if (0.toBigDecimal(currencyMath).setScale(currencyScale) == unconfirmedBalance)
                          ""
                      else
                        i18n(R.string.incoming) % mapOf("delta" to
                          (if (delta > BigDecimal.ZERO) "+" else "") + format(balance - confirmedBalance)   // "\u2B05" == <--
                        )

                    if (delta > BigDecimal.ZERO) unconfirmedBalanceGUI.setAttribute("color", R.color.colorCredit)
                    else unconfirmedBalanceGUI.setAttribute("color", R.color.colorDebit)
                    unconfirmedBalanceGUI(unconfBalStr, force)
                }
                else
                {
                    balanceGUI.setAttribute("strength","dim")
                    val asOfStr = i18n(R.string.balanceOnTheDate) % mapOf("date" to epochToDate(chainstate.syncedDate))
                    unconfirmedBalanceGUI.setAttribute("color", R.color.unsyncedStatusColor)
                    unconfirmedBalanceGUI(asOfStr, force)
                }
            }
            else
            {
                balanceGUI.setAttribute("strength","dim")
                unconfirmedBalanceGUI(i18n(R.string.walletDisconnectedFromBlockchain), force)
            }

            balanceGUI(format(balance), force)
            // If we got something in a receive address, then show a new one
            updateReceiveAddressUI?.invoke(this)

            if (chainstate != null)
            {
                val cnxnLst = wallet.chainstate?.chain?.net?.mapConnections() { it.name }

                val trying: List<String> = if (chainstate.chain.net is MultiNodeCnxnMgr) (chainstate.chain.net as MultiNodeCnxnMgr).initializingCnxns.map { it.name } else listOf()
                val peers = cnxnLst?.joinToString(", ") + (if (trying.isNotEmpty())  (" " + i18n(R.string.trying) + " " + trying.joinToString(", ")) else "")

                val infoStr = i18n(R.string.at) + " " + (wallet.chainstate?.syncedHash?.toHex()?.take(8) ?: "") + ", " + (wallet.chainstate?.syncedHeight
                  ?: "") + " " + i18n(R.string.of) + " " + (wallet.chainstate?.chain?.curHeight
                  ?: "") + " blocks, " + (wallet.chainstate?.chain?.net?.size ?: "") + " peers\n" + peers
                infoGUI(force, { infoStr })  // since numPeers takes cnxnLock
            }
            else
                infoGUI(force, { i18n(R.string.walletDisconnectedFromBlockchain) })

            tickerGUI(name, force)
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

val i18nLbc = mapOf(
  RinsufficentBalance to R.string.insufficentBalance,
  RbadWalletImplementation to R.string.badWalletImplementation,
  RdataMissing to R.string.dataMissing,
  RwalletAndAddressIncompatible to R.string.chainIncompatibleWithAddress,
  RnotSupported to R.string.notSupported,
  Rexpired to R.string.expired,
  RsendMoreThanBalance to R.string.sendMoreThanBalance,
  RbadAddress to R.string.badAddress,
  RblankAddress to R.string.blankAddress,
  RblockNotForthcoming to R.string.blockNotForthcoming,
  RheadersNotForthcoming to R.string.headersNotForthcoming,
  RbadTransaction to R.string.badTransaction,
  RfeeExceedsFlatMax to R.string.feeExceedsFlatMax,
  RexcessiveFee to R.string.excessiveFee,
  Rbip70NoAmount to R.string.badAmount,
  RdeductedFeeLargerThanSendAmount to R.string.deductedFeeLargerThanSendAmount,
  RwalletDisconnectedFromBlockchain to R.string.walletDisconnectedFromBlockchain,
  RsendDust to R.string.sendDustError,
  RnoNodes to R.string.NoNodes
)

class ActivityLifecycleHandler(private val app: WallyApp) : Application.ActivityLifecycleCallbacks
{
    override fun onActivityPaused(act: Activity)
    {
    }

    override fun onActivityStarted(act: Activity)
    {
        //if (app.currentActivity is CommonActivity)
        try
        {
            app.currentActivity = act as CommonNavActivity
        } catch (e: Throwable)  // Some other activity (QR scanner)
        {
        }
    }

    override fun onActivityDestroyed(act: Activity)
    {
    }

    override fun onActivitySaveInstanceState(act: Activity, b: Bundle)
    {

    }

    override fun onActivityStopped(act: Activity)
    {
    }

    override fun onActivityCreated(act: Activity, b: Bundle?)
    {
    }

    override fun onActivityResumed(act: Activity)
    {
        //if (app.currentActivity is CommonActivity)
        try
        {
            app.currentActivity = act as CommonNavActivity
        } catch (e: Throwable)  // Some other activity (QR scanner)
        {
        }
    }
}

class WallyApp : Application()
{
    var firstRun = false
    var notifId = 0

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    protected val coMiscCtxt: CoroutineContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    protected val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    companion object
    {
        // Used to load the 'native-lib' library on application startup.
        init
        {
            //System.loadLibrary("native-lib")
            System.loadLibrary("nexandroid")
            appI18n = { libErr: Int -> i18n(i18nLbc[libErr] ?: libErr) }
        }
    }

    val init = Initialize.LibBitcoinCash(ChainSelector.NEXATESTNET.v)  // Initialize the C library first

    val accounts: MutableMap<String, Account> = mutableMapOf()
    val accessHandler = AccessHandler(this)
    var currentActivity: CommonNavActivity? = null

    // Track notifications
    val notifs: MutableList<Pair<Int, PendingIntent>> = mutableListOf()

    // You can access the primary account object in a manner that throws an exception or returns a null, your choice
    var nullablePrimaryAccount: Account? = null
    var primaryAccount: Account
        get()
        {
            val tmp = nullablePrimaryAccount
            if (tmp == null) throw PrimaryWalletInvalidException()
            return tmp
        }
        set(act: Account)
        {
            val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
            with(prefs.edit())
            {
                putString(PRIMARY_ACT_PREF, act.name)
                commit()
            }
            nullablePrimaryAccount = act
        }

    // The currently selected account
    var focusedAccount: Account? = null


    fun defaultPrimaryAccount(): Account
    {
        // return the first Nexa wallet
        for (i in accounts.values)
        {
            LogIt.info("looking for primary at wallet " + i.name + "blockchain: " + i.chain.name)
            if (i.wallet.chainSelector == ChainSelector.NEXA) return i
        }
        for (i in accounts.values)
        {
            LogIt.info("falling back to testnet")
            if (i.wallet.chainSelector == ChainSelector.NEXATESTNET) return i
        }
        for (i in accounts.values)
        {
            LogIt.info("falling back to regtest")
            if (i.wallet.chainSelector == ChainSelector.NEXAREGTEST) return i
        }
        throw PrimaryWalletInvalidException()
    }

    /** Activity stacks don't quite work.  If task A uses an implicit intent launches a child wally activity, then finish() returns to A
     * if wally wasn't previously running.  But if wally was currently running, it returns to wally's Main activity.
     * Since the implicit activity wasn't launched for result, we can't return an indicator that wally main should finish().
     * Whenever wally resumes, if finishParent > 0, it will immediately finish. */
    var finishParent = 0

    var lastError:Int? = null
    // var lastErrorStr:String? = null
    var lastErrorDetails:String? = null

    /** Display an short error string on the title bar, and then clear it after a bit.  The common activity will check for errors coming from other activities */
    fun displayError(resource: Int, details: Int? = null, then: (() -> Unit)? = null)
    {
        lastError = resource
        lastErrorDetails = if (details != null) i18n(details) else null
    }

    /** Do whatever you pass but not within the user interface context, asynchronously.
     * Launching into these threads means your task will outlast the activity it was launched in */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun later(fn: suspend () -> Unit): Unit
    {
        coMiscScope.launch {
            try
            {
                fn()
            } catch (e: Exception) // Uncaught exceptions will end the app
            {
                handleThreadException(e)
            }
        }
    }

    /** lock all previously unlocked accounts */
    fun lockAccounts()
    {
        for (account in accounts.values)
        {
            account.pinEntered = false
        }
    }

    /** Submit this PIN to all accounts, unlocking any that match */
    fun unlockAccounts(pin: String): Int
    {
        var unlocked = 0
        for (account in accounts.values)
        {
            unlocked += account.submitAccountPin(pin)
        }
        if (unlocked > 0) notifyAccountUnlocked()
        return unlocked
    }

    val interestedInAccountUnlock = mutableListOf<() -> Unit>()
    fun notifyAccountUnlocked()
    {
        for (f in interestedInAccountUnlock) f()
    }

    fun visibleAccountNames(): Array<String>
    {
        val ret = mutableListOf<String>()
        for (ac in accounts)
        {
            if (ac.value.visible) ret.add(ac.key)
        }
        return ret.toTypedArray()
    }

    fun accountsFor(currencyType: String) = accountsFor(currencyCodeToChain[currencyType]!!)

    fun accountsFor(chain: ChainSelector): MutableList<Account>
    {
        val ret = mutableListOf<Account>()

        // put the primary account first
        nullablePrimaryAccount?.let {
            if (chain == it.chain.chainSelector && it.visible) ret.add(it)
        }

        // Look for any other match
        for (account in accounts.values)
        {
            if (account.visible && (chain == account.wallet.chainSelector) && (nullablePrimaryAccount != account))
            {
                ret.add(account)
            }
        }
        return ret
    }

    /** Return what account a particular GUI element is bound to or null if its not bound */
    fun accountFromGui(view: View): Account?
    {
        try
        {
            for (a in accounts.values)
            {
                if ((a.tickerGUI.reactor is TextViewReactor<String>) && (a.tickerGUI.reactor as TextViewReactor<String>).gui == view) return a
                if ((a.balanceGUI.reactor is TextViewReactor<String>) && (a.balanceGUI.reactor as TextViewReactor<String>).gui == view) return a
                if ((a.unconfirmedBalanceGUI.reactor is TextViewReactor<String>) && (a.unconfirmedBalanceGUI.reactor as TextViewReactor<String>).gui == view) return a
                if ((a.infoGUI.reactor is TextViewReactor<String>) && (a.infoGUI.reactor as TextViewReactor<String>).gui == view) return a
            }
        } catch (e: Exception)
        {
            LogIt.warning("Exception in accountFromGui: " + e.toString())
            handleThreadException(e)
        }
        return null
    }

    /** Save the PIN of an account to the database */
    fun saveAccountPin(actName: String, epin: ByteArray)
    {
        val db = walletDb!!
        db.set("accountPin_" + actName, epin)
    }

    /** Save the account list to the database */
    fun saveActiveAccountList()
    {
        val s: String = accounts.keys.joinToString(",")

        val db = walletDb!!

        db.set("activeAccountNames", s.toByteArray())
        db.set("wallyDataVersion", WALLY_DATA_VERSION)
    }

    fun newAccount(name: String, flags: ULong, pin: String, chainSelector: ChainSelector)
    {
        dbgAssertNotGuiThread()
        val ctxt = PlatformContext(applicationContext)

        // I only want to write the PIN once when the account is first created

        val epin = if (pin.length > 0) EncodePIN(name, pin) else byteArrayOf()
        saveAccountPin(name, epin)

        val ac = try
        {
            val prehistoryDate = (Date().time / 1000L) - PREHISTORY_SAFEFTY_FACTOR // Set prehistory to 2 hours ago to account for block timestamp variations
            Account(name, ctxt, flags, chainSelector, startPlace=prehistoryDate)
        } catch (e: IllegalStateException)
        {
            LogIt.warning("Error creating account: ${e.message}")
            return
        }

        ac.pinEntered = true  // for convenience, new accounts begin as if the pin has been entered
        ac.start(applicationContext)
        ac.onWalletChange()
        ac.wallet.save(true)

        accounts[name] = ac
        // Write the list of existing accounts, so we know what to load
        saveActiveAccountList()
        // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()
    }

    fun recoverAccount(
      name: String,
      flags: ULong,
      pin: String,
      secretWords: String,
      chainSelector: ChainSelector,
      earliestActivity: Long?,
      nonstandardActivity: MutableList<Pair<Bip44Wallet.HdDerivationPath, NewAccount.HDActivityBracket>>?
    )
    {
        dbgAssertNotGuiThread()
        val ctxt = PlatformContext(applicationContext)

        // I only want to write the PIN once when the account is first created
        val epin = try
        {
            EncodePIN(name, pin.trim())
        } catch (e: InvalidKeySpecException)  // If the pin is bad (generally whitespace or null) ignore it
        {
            byteArrayOf()
        }
        saveAccountPin(name, epin)

        var veryEarly = earliestActivity
        if (nonstandardActivity != null)
        {
            for (n in nonstandardActivity)
            {
                veryEarly = min(n.second.startTime, veryEarly ?: n.second.startTime)
            }
        }
        if (veryEarly != null) veryEarly = veryEarly - 1  // Must be earlier than the first activity

        val ac = Account(name, ctxt, flags, chainSelector, secretWords, veryEarly, nonstandardActivity)
        ac.pinEntered = true // for convenience, new accounts begin as if the pin has been entered
        ac.start(applicationContext)
        ac.onWalletChange()

        accounts[name] = ac
        // Write the list of existing accounts, so we know what to load
        saveActiveAccountList()
        // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()
    }

    private fun createNotificationChannel()
    {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val name = "Wally Wallet" //getString(R.string.channel_name)
            val descriptionText = "Wally Wallet Notifications" // getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
              applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Called when the application is starting, before any other application objects have been created.
    // Overriding this method is totally optional!
    override fun onCreate()
    {
        super.onCreate()
        notInUIscope = coMiscScope
        appResources = getResources()
        displayMetrics = getResources().getDisplayMetrics()
        wallyApp = this

        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        devMode = prefs.getBoolean(DEV_MODE_PREF, false)

        registerActivityLifecycleCallbacks(ActivityLifecycleHandler(this))  // track the current activity
        createNotificationChannel()

        if (!RunningTheTests())  // If I'm running the unit tests, don't create any wallets since the tests will do so
        {
            // Initialize the currencies supported by this wallet
            launch(coMiscScope) {
                LogIt.info(sourceLoc() + " Wally Wallet App Started")
                val ctxt = PlatformContext(applicationContext)
                walletDb = OpenKvpDB(ctxt, dbPrefix + "bip44walletdb")
                
                if (REG_TEST_ONLY)  // If I want a regtest only wallet for manual debugging, just create it directly
                {
                    accounts.getOrPut("RKEX") {
                        try
                        {
                            val c = Account("RKEX", ctxt);
                            c
                        } catch (e: DataMissingException)
                        {
                            val c = Account("RKEX", ctxt, ACCOUNT_FLAG_NONE, ChainSelector.NEXAREGTEST)
                            c
                        }
                    }
                }
                else  // OK, recreate the wallets saved on this phone
                {
                    val db = walletDb!!

                    LogIt.info(sourceLoc() + " Loading account names")
                    val accountNames = try
                    {
                        db.get("activeAccountNames")
                    } catch (e: DataMissingException)
                    {
                        firstRun = true
                        byteArrayOf()
                    }
                    if (accountNames.size == 0) firstRun = true // Ok maybe not first run but no wallets

                    val accountNameList = String(accountNames).split(",")
                    for (name in accountNameList)
                    {
                        LogIt.info(sourceLoc() + " " + name + ": Loading account")
                        try
                        {
                            val ac = Account(name, ctxt)
                            accounts[ac.name] = ac
                        } catch (e: DataMissingException)
                        {
                            LogIt.warning(sourceLoc() + " " + name + ": Active account $name was not found in the database")
                            // Nothing to really do but ignore the missing account
                        }
                        LogIt.info(sourceLoc() + " " + name + ": Loaded account")
                    }
                }

                coinsCreated = true

                // Cannot pick the primary account until accounts are loaded
                val primaryActName = prefs.getString(PRIMARY_ACT_PREF, null)
                nullablePrimaryAccount = if (primaryActName != null) accounts[primaryActName] else null
                try
                {
                    if (nullablePrimaryAccount == null) primaryAccount = defaultPrimaryAccount()
                }
                catch(e:PrimaryWalletInvalidException)
                {
                    // nothing to do in the case where there is no account to pick
                }


                for (c in accounts.values)
                {
                    val cs = c.chain.chainSelector
                    val chainName = chainToURI[cs]
                    val exclusiveNode: String? = if (prefs.getBoolean(chainName + "." + EXCLUSIVE_NODE_SWITCH, false)) prefs.getString(chainName + "." + CONFIGURED_NODE, null) else null
                    val preferredNode: String? = if (prefs.getBoolean(chainName + "." + PREFER_NODE_SWITCH, false)) prefs.getString(chainName + "." + CONFIGURED_NODE, null) else null

                    // If I prefer an exclusive connection, then start up that way
                    if (exclusiveNode != null)
                    {
                        LogIt.info(sourceLoc() + c.chain.name + ": Exclusive node mode")
                        try
                        {
                            val nodeSet:Set<String> = exclusiveNode.toSet()
                            c.cnxnMgr.exclusiveNodes(nodeSet)
                        }
                        catch (e: Exception)
                        {
                        } // bad IP:port data
                    }
                    // If I have a preferred connection, then start up that way
                    if (preferredNode != null)
                    {
                        LogIt.info(sourceLoc() + c.chain.name + ": Preferred node mode")
                        try
                        {
                            val nodeSet:Set<String> = preferredNode.toSet()
                            c.cnxnMgr.preferNodes(nodeSet)
                        }
                        catch (e: Exception)
                        {
                        } // bad IP:port data provided by user
                    }

                    c.start(applicationContext)
                    c.onWalletChange()  // update all wallet UI fields since just starting up
                }
            }
        }

    }

    // Called by the system when the device configuration changes while your component is running.
    // Overriding this method is totally optional!
    override fun onConfigurationChanged(newConfig: Configuration)
    {
        super.onConfigurationChanged(newConfig)
    }

    // This is called when the overall system is running low on memory,
    // and would like actively running processes to tighten their belts.
    // Overriding this method is totally optional!
    override fun onLowMemory()
    {
        super.onLowMemory()
    }

    /** Remove a notification that was installed using the notify() function */
    fun denotify(intent: Intent)
    {
        val nid = intent.getIntExtra("wallyNotificationId", -1)
        if (nid != -1) denotify(nid)
    }

    /* Remove a notification */
    fun denotify(id: Int)
    {
        with(NotificationManagerCompat.from(this))
        {
            cancel(id)
        }
    }

    fun activeNotifications()
    {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.activeNotifications
    }

    fun getNotificationIntent()
    {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notifs = nm.activeNotifications
        if (notifs.size > 0)
        {
            val sbn = notifs[0]
            val id = sbn.id
            val n = sbn.notification
            denotify(id)
            n.contentIntent.send()
        }
    }

    /** Create a notification of a pending intent */
    fun notify(intent: Intent, content: String, activity: AppCompatActivity): Int
    {
        // Save the notification id into the Intent so we can remove it when needed
        val nid = notifId
        notifId+=1
        intent.putExtra("wallyNotificationId", nid)

        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, 0)
        var builder = NotificationCompat.Builder(activity, NOTIFICATION_CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_notifications_black_24dp)
          .setContentTitle("Wally Wallet")
          .setContentText(content)
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setContentIntent(pendingIntent)
          .setAutoCancel(true)

        notifs.add(Pair(nid,pendingIntent))
        with(NotificationManagerCompat.from(this))
        {
            notify(nid, builder.build())
            return nid
        }
    }
}