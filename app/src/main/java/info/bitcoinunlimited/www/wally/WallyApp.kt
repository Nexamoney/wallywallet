// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.

package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import bitcoinunlimited.libbitcoincash.*
import bitcoinunlimited.libbitcoincash.appI18n
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.net.ConnectException
import java.security.spec.InvalidKeySpecException
import java.util.concurrent.Executors
import java.util.logging.Logger
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.CoroutineContext

val SimulationHostIP = "10.0.2.2"
val LanHostIP = "192.168.1.100"

val LAST_RESORT_BCH_ELECTRS = "bch2.bitcoinunlimited.net" // "electrs.bitcoinunlimited.info"

private val LogIt = Logger.getLogger("bitcoinunlimited.app")

open class PrimaryWalletInvalidException() : BUException("Primary wallet not defined or currently unavailable", "not ready", ErrorSeverity.Abnormal)
open class WalletInvalidException() : BUException("Wallet nonexistent or unavailable", "not ready", ErrorSeverity.Abnormal)

var coinsCreated = false

/** Currently selected fiat currency code */
var fiatCurrencyCode: String = "USD"

/** Database name prefix, empty string for mainnet, set for testing */
var dbPrefix = if (RunningTheTests()) "guitest_" else if (REG_TEST_ONLY == true) "regtest_" else ""

val SupportedBlockchains = if (INCLUDE_NEXTCHAIN)
    mapOf(
      "BCH (Bitcoin Cash)" to ChainSelector.BCH,
      "NEXA" to ChainSelector.NEXA,
      "TNEX (Testnet Nexa)" to ChainSelector.NEXATESTNET,
      "RNEX (Regtest Nexa)" to ChainSelector.REGTEST
    )
else
    mapOf("BCH (Bitcoin Cash)" to ChainSelector.BCH, "TNEX (Testnet Nexa)" to ChainSelector.NEXATESTNET, "RNEX (Regtest Nexa)" to ChainSelector.REGTEST)

val ChainSelectorToSupportedBlockchains = SupportedBlockchains.entries.associate { (k, v) -> v to k }

// What is the default wallet and blockchain to use for most functions (like identity)
val PRIMARY_WALLET = if (REG_TEST_ONLY) "mRBCH" else "mBCH"

/** incompatible changes, extra fields added, fields and field sizes are the same, but content may be extended (that is, addtl bits in enums) */
val WALLY_DATA_VERSION = byteArrayOf(1, 0, 0)

var walletDb: KvpDatabase? = null

const val ACCOUNT_FLAG_NONE = 0UL
const val ACCOUNT_FLAG_HIDE_UNTIL_PIN = 1UL
const val RETRIEVE_ONLY_ADDITIONAL_ADDRESSES = 10

/** If a wallet imports a nonstandard derivation path, include this many addresses past the last used address in the wallet's monitoring */

fun MakeNewWallet(name: String, chain: ChainSelector): Bip44Wallet
{
    if (chain == ChainSelector.REGTEST) // Hard code the regtest wallet secret key for repeatable results
        return Bip44Wallet(walletDb!!, name, chain, "trade box today light need route design birth turn insane oxygen sense")
    if (chain == ChainSelector.NEXATESTNET)
        return Bip44Wallet(walletDb!!, name, chain, NEW_WALLET)
    if (chain == ChainSelector.BCH)
        return Bip44Wallet(walletDb!!, name, chain, NEW_WALLET)
    if (chain == ChainSelector.NEXA)
        return Bip44Wallet(walletDb!!, name, chain, NEW_WALLET)
    throw BUException("invalid chain selected")
}

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


        while (!done && lpInfo.active)
        {
            try
            {

                val response: HttpResponse = client.get(url) {}
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
            } catch (e: ConnectException)  // network error?  TODO retry a few times
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
    var balance: BigDecimal = 0.toBigDecimal(currencyMath).setScale(mBchDecimals)
    var unconfirmedBalance: BigDecimal = 0.toBigDecimal(currencyMath).setScale(mBchDecimals)

    //? specify how quantities should be formatted for display
    val cryptoFormat = mBchFormat

    val cnxnMgr: CnxnMgr = GetCnxnMgr(wallet.chainSelector, name)

    val chain: Blockchain = GetBlockchain(wallet.chainSelector, cnxnMgr, context, name)

    val currencyCode: String = chainToMilliCurrencyCode[wallet.chainSelector]!!

    // If this coin's receive address is shown on-screen, this is not null
    var updateReceiveAddressUI: ((Account) -> Unit)? = null

    /** loading existing wallet */
    init
    {
        val hundredThousand = CurrencyDecimal(SATinMBCH)
        wallet.prepareDestinations(2, 2)  // Make sure that there is at least a few addresses before we hook into the network

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

        LogIt.info("wallet add blockchain")
        wallet.addBlockchain(chain, chain.checkpointHeight, startPlace)
        LogIt.info("wallet add blockchain done")
        if (chainSelector != ChainSelector.NEXA)  // no fiat price for nextchain
        {
            wallet.spotPrice = { currencyCode -> assert(currencyCode == fiatCurrencyCode); fiatPerCoin / hundredThousand }
            wallet.historicalPrice = { currencyCode: String, epochSec: Long -> historicalMbchInFiat(currencyCode, epochSec) / hundredThousand }
        }

    }

    val visible: Boolean
        get()
        {
            if ((encodedPin != null) && ((flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN) > 0UL) && !pinEntered) return false
            return true
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

    fun RegtestIP(): String
    {
        val wm = context.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        LogIt.info("My IP" + ip.toString())
        if ((ip and 255) == 192)
        {
            return LanHostIP
        }
        else return SimulationHostIP
    }

    //? Set the user interface elements for this cryptocurrency
    fun setUI(ticker: TextView?, balance: TextView?, unconf: TextView?, infoView: TextView?)
    {
        if (ticker != null) tickerGUI.reactor = TextViewReactor<String>(ticker)
        else tickerGUI.reactor = null
        if (balance != null) balanceGUI.reactor = TextViewReactor<String>(balance)
        else balanceGUI.reactor = null
        if (unconf != null) unconfirmedBalanceGUI.reactor = TextViewReactor<String>(unconf)
        else unconfirmedBalanceGUI.reactor = null
        if (infoView != null) infoGUI.reactor = TextViewReactor<String>(infoView)
        else infoGUI.reactor = null
    }

    //? Convert the default display units to the finest granularity of this currency.  For example, mBCH to Satoshis
    fun toFinestUnit(amount: BigDecimal): Long
    {
        val ret = amount * SATinMBCH.toBigDecimal()
        return ret.toLong()
    }

    //? Convert the finest granularity of this currency to the default display unit.  For example, Satoshis to mBCH
    fun fromFinestUnit(amount: Long): BigDecimal
    {
        val ret = BigDecimal(amount, currencyMath).setScale(currencyScale) / SATinMBCH.toBigDecimal()
        return ret
    }

    //? Convert a value in this currency code unit into its primary unit. The "primary unit" is the generally accepted currency unit, AKA "BCH" or "BTC".
    fun toPrimaryUnit(qty: BigDecimal): BigDecimal
    {
        return qty / (1000.toBigDecimal())
    }

    //? Convert the passed quantity to a string in the decimal format suitable for this currency
    fun format(qty: BigDecimal): String = mBchFormat.format(qty)

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

    fun onResume()
    {
        LogIt.info("App resuming: Restarting threads if needed")
        wallet.restart()
        wallet.chainstate?.chain?.restart()
        wallet.chainstate?.chain?.net?.restart()
        onWalletChange(true)
    }

    fun onWalletChange(force: Boolean = false)
    {
        notInUI {
            // Update our cache of the balances
            balance = wallet.balance.toBigDecimal(currencyMath).setScale(currencyScale) / SATinMBCH.toBigDecimal()
            unconfirmedBalance = wallet.balanceUnconfirmed.toBigDecimal(currencyMath).setScale(currencyScale) / SATinMBCH.toBigDecimal()

            balanceGUI(mBchFormat.format(balance.setScale(mBchDecimals)), force)

            val unconfBalStr =
              if (0.toBigDecimal(currencyMath).setScale(currencyScale) == unconfirmedBalance)
                  ""
              else
                  "*" + mBchFormat.format(unconfirmedBalance.setScale(mBchDecimals)) + "*"

            unconfirmedBalanceGUI(unconfBalStr, force)

            // If we got something in a receive address, then show a new one
            updateReceiveAddressUI?.invoke(this)

            val cnxnLst = wallet.chainstate?.chain?.net?.mapConnections() { it.name }
            val peers = cnxnLst?.joinToString(", ")
            val infoStr = i18n(R.string.at) + " " + (wallet.chainstate?.syncedHash?.toHex()?.take(8) ?: "") + ", " + (wallet.chainstate?.syncedHeight
              ?: "") + " " + i18n(R.string.of) + " " + (wallet.chainstate?.chain?.curHeight
              ?: "") + " blocks, " + (wallet.chainstate?.chain?.net?.size ?: "") + " peers\n" + peers
            infoGUI(force, { infoStr })  // since numPeers takes cnxnLock

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
    fun start(ac: Context)
    {
        cnxnMgr.start()
        chain.start()
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


    val primaryAccount: Account
        get()
        {
            val prim = accounts[PRIMARY_WALLET]
            if (prim != null) return prim
            LogIt.info("Num accounts: " + accounts.size)

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
                if (i.wallet.chainSelector == ChainSelector.REGTEST) return i
            }
            throw PrimaryWalletInvalidException()
        }

    /** Activity stacks don't quite work.  If task A uses an implicit intent launches a child wally activity, then finish() returns to A
     * if wally wasn't previously running.  But if wally was currently running, it returns to wally's Main activity.
     * Since the implicit activity wasn't launched for result, we can't return an indicator that wally main should finish().
     * Whenever wally resumes, if finishParent > 0, it will immediately finish. */
    var finishParent = 0

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
    fun unlockAccounts(pin: String)
    {
        var unlocked = 0
        for (account in accounts.values)
        {
            if (!account.pinEntered) unlocked += account.submitAccountPin(pin)
        }
        if (unlocked > 0) notifyAccountUnlocked()
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

    fun accountsFor(chain: ChainSelector): MutableList<Account>
    {
        val ret = mutableListOf<Account>()
        /*
        // Check to see if our preferred crypto matches first
        for (account in accounts.values)
        {
            if ((account.name == defaultAccount) && (account.visible) && (chain == account.wallet.chainSelector)) return mutableListOf(account)
        }
         */

        // Look for any match
        for (account in accounts.values)
        {
            if (account.visible && (chain == account.wallet.chainSelector))
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

    fun newAccount(name: String, flags: ULong, pin: String, secretWords: String?, chainSelector: ChainSelector)
    {
        dbgAssertNotGuiThread()
        val ctxt = PlatformContext(applicationContext)

        // I only want to write the PIN once when the account is first created

        val epin = if (pin.length > 0) EncodePIN(name, pin) else byteArrayOf()
        saveAccountPin(name, epin)

        val ac = try
        {
            Account(name, ctxt, flags, chainSelector)
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

    // Called when the application is starting, before any other application objects have been created.
    // Overriding this method is totally optional!
    override fun onCreate()
    {
        super.onCreate()
        LogIt.info(sourceLoc() + " Wally Wallet App Started")

        val ctxt = PlatformContext(applicationContext)
        registerActivityLifecycleCallbacks(ActivityLifecycleHandler(this))  // track the current activity

        walletDb = OpenKvpDB(ctxt, dbPrefix + "bip44walletdb")
        appResources = getResources()
        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        val NexaExclusiveNode: String? = if (prefs.getBoolean(BCH_EXCLUSIVE_NODE_SWITCH, false)) prefs.getString(BCH_EXCLUSIVE_NODE, null) else null
        val NexaPreferredNode: String? = if (prefs.getBoolean(BCH_PREFER_NODE_SWITCH, false)) prefs.getString(BCH_PREFER_NODE, null) else null


        if (!RunningTheTests())  // If I'm running the unit tests, don't create any wallets since the tests will do so
        {
            // Initialize the currencies supported by this wallet
            launch {
                if (REG_TEST_ONLY)  // If I want a regtest only wallet for manual debugging, just create it directly
                {
                    accounts.getOrPut("RKEX") {
                        try
                        {
                            val c = Account("RKEX", ctxt);
                            c
                        } catch (e: DataMissingException)
                        {
                            val c = Account("RKEX", ctxt, ACCOUNT_FLAG_NONE, ChainSelector.REGTEST)
                            c
                        }
                    }
                }
                else  // OK, recreate the wallets saved on this phone
                {
                    val db = walletDb!!

                    LogIt.info("Loading account names")
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
                        LogIt.info(sourceLoc() + " " + name + ": Loading account " + name)
                        try
                        {
                            val ac = Account(name, ctxt)
                            accounts[ac.name] = ac
                        } catch (e: DataMissingException)
                        {
                            LogIt.warning(sourceLoc() + " " + name + ": Active account $name was not found in the database")
                            // Nothing to really do but ignore the missing account
                        }
                        LogIt.info("Loaded account " + name)
                    }
                }

                coinsCreated = true

                for (c in accounts.values)
                {
                    // If I prefer an exclusive connection, then start up that way
                    if ((NexaExclusiveNode != null) && (c.chain.chainSelector == ChainSelector.NEXA))
                    {
                        try
                        {
                            val nodeSet:Set<String> = NexaExclusiveNode.toSet()
                            c.cnxnMgr.exclusiveNodes(nodeSet)
                        } catch (e: Exception)
                        {
                        } // bad IP:port data
                    }
                    // If I have a preferred connection, then start up that way
                    if ((NexaPreferredNode != null) && (c.chain.chainSelector == ChainSelector.NEXA))
                    {
                        try
                        {
                            val nodeSet:Set<String> = NexaPreferredNode.toSet()
                            c.cnxnMgr.preferNodes(nodeSet)
                        } catch (e: Exception)
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

}