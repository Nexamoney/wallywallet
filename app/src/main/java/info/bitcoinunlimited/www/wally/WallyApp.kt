// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.

package info.bitcoinunlimited.www.wally

import android.app.*
import android.app.PendingIntent.CanceledException
import android.content.*
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import bitcoinunlimited.libbitcoincash.*
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.spec.InvalidKeySpecException
import java.util.*
import java.util.concurrent.Executors
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

val LAST_RESORT_BCH_ELECTRS = "bch2.bitcoinunlimited.net"
val LAST_RESORT_NEXA_ELECTRS = "electrum.nexa.org"


const val NORMAL_NOTIFICATION_CHANNEL_ID = "n"
const val PRIORITY_NOTIFICATION_CHANNEL_ID = "p"
const val HTTP_REQ_TIMEOUT_MS: Int = 7000

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
var allowAccessPriceData: Boolean = true

const val ACCOUNT_FLAG_NONE = 0UL
const val ACCOUNT_FLAG_HIDE_UNTIL_PIN = 1UL
const val RETRIEVE_ONLY_ADDITIONAL_ADDRESSES = 10

fun epochMilliSeconds(): Long
{
    return Date().time
    // return System.currentTimeMillis()/1000
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

    suspend fun longPolling(scheme: String, hostPort: String, cookie: String?)
    {
        var connectProblems = 0
        val cookieString = if (cookie != null) "?cookie=$cookie" else ""
        val url = scheme + "://" + hostPort + "/_lp" + cookieString

        val lpInfo = synchronized(activeLongPolls)
        {
            if (activeLongPolls.contains(url))
            {
                LogIt.info("Already long polling to $url, replacing it.")
                activeLongPolls[url]?.active = false
            }
            activeLongPolls.put(url, LongPollInfo(scheme, hostPort, cookie))
            activeLongPolls[url]!!
        }

        val client = HttpClient(Android)
        {
            install(HttpTimeout) { requestTimeoutMillis = 60000 } // Long timeout because we don't expect a response right away; its a long poll
        }

        var count = 0
        var avgResponse = 0.0f
        while (!done && lpInfo.active)
        {
            val start = epochMilliSeconds()
            try
            {

                val response: HttpResponse = client.get(url + "&i=${count}") {}
                val respText = response.bodyAsText()
                connectProblems = 0
                LogIt.info(sourceLoc() + ": Long poll to $url resp: $respText")
                if (respText == "Q")
                {
                    LogIt.info(sourceLoc() + ": Long poll to $url ended (server request).")
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
                    LogIt.info(sourceLoc() + ": Long poll to $url connection exception $e, stopping")
                    endLongPolling(url)
                    return
                }
                connectProblems += 1
                delay(1000)
            }
            catch (e: Throwable)
            {
                // LogIt.info(sourceLoc() + ": Long poll to $url error, stopping: ")
                handleThreadException(e, "Long poll to $url error, stopping", sourceLoc())
                endLongPolling(url)
                return
            }
            val end = epochMilliSeconds()
            avgResponse = ((avgResponse*49f)+(end-start))/50.0f
            if (avgResponse<1000)
                delay(500) // limit runaway polling, if the server misbehaves by responding right away
        }
        LogIt.info(sourceLoc() + ": Long poll to $url ended (done).")
        endLongPolling(url)
    }
}

// in app init, we change the lbbc integers to our own resource ids.  So this translation is likely unnecessary
val i18nLbc = mapOf(
  RinsufficentBalance to R.string.insufficentBalance,
  RbadWalletImplementation to R.string.badWalletImplementation,
  RdataMissing to R.string.PaymentDataMissing,
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
  RnoNodes to R.string.NoNodes,
  RwalletAddressMissing to R.string.badAddress,
  RunknownCryptoCurrency to R.string.unknownCryptoCurrency
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

class WallyApp : Application.ActivityLifecycleCallbacks, Application()
{
    init
    {
        RinsufficentBalance = R.string.insufficentBalance
        RbadWalletImplementation = R.string.badWalletImplementation
        RwalletAndAddressIncompatible = R.string.chainIncompatibleWithAddress
        RnotSupported = R.string.notSupported
        Rexpired = R.string.expired
        RsendMoreThanBalance = R.string.sendMoreThanBalance
        RbadAddress = R.string.badAddress
        RblankAddress = R.string.blankAddress
        RblockNotForthcoming = R.string.blockNotForthcoming
        RheadersNotForthcoming = R.string.headersNotForthcoming
        RbadTransaction = R.string.badTransaction
        RfeeExceedsFlatMax = R.string.feeExceedsFlatMax
        RexcessiveFee = R.string.excessiveFee
        Rbip70NoAmount = R.string.badAmount
        RdeductedFeeLargerThanSendAmount = R.string.deductedFeeLargerThanSendAmount
        RwalletDisconnectedFromBlockchain = R.string.walletDisconnectedFromBlockchain
        RsendDust = R.string.sendDustError
        RbadCryptoCode = R.string.badCryptoCode
        RwalletAddressMissing = R.string.badAddress
        RunknownCryptoCurrency = R.string.unknownCryptoCurrency

        // RdataMissing = R.string.dataMissing
        // RnoNodes = 0xf00d + 18 // R.string.noNodes
        // RneedNonexistentAuthority = R.string.n
    }

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
    val notifs: MutableList<Triple<Int, PendingIntent, Intent>> = mutableListOf()

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

    val tpDomains: TricklePayDomains = TricklePayDomains(this)

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
    var lastErrorDetails:String? = null
    var lastNotice:Int? = null
    var lastNoticeDetails:String? = null

    // Track the last item in the clipboard
    var currentClip:ClipData? = null

    /** Display an short error string on the title bar, and then clear it after a bit.  The common activity will check for errors coming from other activities */
    fun displayError(resource: Int, details: Int? = null)
    {
        lastError = resource
        lastErrorDetails = if (details != null) i18n(details) else null
    }

    fun displayError(resource: Int, details: String)
    {
        lastError = resource
        lastErrorDetails = details
    }

    /** Display an short error string on the title bar, and then clear it after a bit.  The common activity will check for errors coming from other activities */
    fun displayNotice(resource: Int, details: Int? = null)
    {
        lastNotice = resource
        lastNoticeDetails = if (details != null) i18n(details) else null
    }
    fun displayNotice(resource: Int, details: String)
    {
        lastNotice = resource
        lastNoticeDetails = details
    }

    /** Use the resource id version of displayNotice, unless you really only have a string (response from a server, etc) */
    fun displayNotice(notice: String)
    {
        lastNotice = -1
        lastNoticeDetails = notice
    }

    fun displayException(e: BUExceptionI)
    {
        lastError = e.errCode
        lastErrorDetails = e.message
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

    /** Save the account list to the database */
    fun saveActiveAccountList()
    {
        val s: String = accounts.keys.joinToString(",")

        val db = walletDb!!

        db.set("activeAccountNames", s.toByteArray())
        db.set("wallyDataVersion", WALLY_DATA_VERSION)
    }

    fun handlePostLogin(loginReqParam: String, jsonBody: String)
    {
        var loginReq = loginReqParam
        var forwarded = 0

        postloop@ while (forwarded < 3)
        {
            LogIt.info("sending registration reply: " + loginReq)
            try
            {
                //val body = """[1,2,3]"""  // URLEncoder.encode("""[1,2,3]""","UTF-8")
                val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                req.requestMethod = "POST"
                req.setRequestProperty("Content-Type", "application/json")
                req.setRequestProperty("Accept", "*/*")
                req.setRequestProperty("Content-Length", jsonBody.length.toString())
                req.setConnectTimeout(HTTP_REQ_TIMEOUT_MS)
                req.doOutput = true
                req.useCaches = false
                val os = DataOutputStream(req.outputStream)
                //os.write(jsonBody.toByteArray())
                os.writeBytes(jsonBody.toString())
                os.flush()
                os.close()
                val resp = req.inputStream.bufferedReader().readText()
                LogIt.info("reg response code:" + req.responseCode.toString() + " response: " + resp)
                if ((req.responseCode >= 200) and (req.responseCode < 300))
                {
                    displayNotice(resp)
                    return
                }
                else if ((req.responseCode == 301) or (req.responseCode == 302))  // Handle URL forwarding
                {
                    loginReq = req.getHeaderField("Location")
                    forwarded += 1
                    continue@postloop
                }
                else
                {
                    wallyApp?.displayNotice(resp)
                    return
                }
            } catch (e: java.net.SocketTimeoutException)
            {
                LogIt.info("SOCKET TIMEOUT:  If development, check phone's network.  Ensure you can route from phone to target!  " + e.toString())
                wallyApp?.displayError(R.string.connectionException)
                return
            } catch (e: IOException)
            {
                LogIt.info("registration IOException: " + e.toString())
                wallyApp?.displayError(R.string.connectionAborted)
                return
            } catch (e: FileNotFoundException)
            {
                LogIt.info("registration FileNotFoundException: " + e.toString())
                wallyApp?.displayError(R.string.badLink)
                return
            } catch (e: java.net.ConnectException)
            {
                wallyApp?.displayError(R.string.connectionException)
                return
            } catch (e: Throwable)
            {
                wallyApp?.displayError(R.string.unknownError)
                return
            }
            break@postloop  // Only way to actually loop is to get a http 301 or 302
        }
    }

    fun handleLogin(loginReqParam: String)
    {
        var loginReq = loginReqParam
        var forwarded = 0
        getloop@ while (forwarded < 3)
        {
            LogIt.info("login reply: " + loginReq)
            try
            {
                val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                req.setConnectTimeout(HTTP_REQ_TIMEOUT_MS)
                val resp = req.inputStream.bufferedReader().readText()
                LogIt.info("login response code:" + req.responseCode.toString() + " response: " + resp)
                if ((req.responseCode >= 200) and (req.responseCode < 250))
                {
                    displayNotice(resp)
                    return
                }
                else if ((req.responseCode == 301) or (req.responseCode == 302))  // Handle URL forwarding (often switching from http to https)
                {
                    loginReq = req.getHeaderField("Location")
                    forwarded += 1
                    continue@getloop
                }
                else
                {
                    displayNotice(resp)
                    return
                }
            } catch (e: FileNotFoundException)
            {
                displayError(R.string.badLink, loginReq)
            } catch (e: IOException)
            {
                displayError(R.string.connectionAborted, loginReq)
            } catch (e: java.net.ConnectException)
            {
                displayError(R.string.connectionException)
            }

            break@getloop  // only way to actually loop is to hit a 301 or 302
        }
    }

    fun newAccount(name: String, flags: ULong, pin: String, chainSelector: ChainSelector): Account?
    {
        dbgAssertNotGuiThread()
        val ctxt = PlatformContext(applicationContext)

        // I only want to write the PIN once when the account is first created

        val epin = if (pin.length > 0) EncodePIN(name, pin) else byteArrayOf()
        SaveAccountPin(name, epin)

        synchronized(accounts)
        {
            val ac = try
            {
                val prehistoryDate = (Date().time / 1000L) - PREHISTORY_SAFEFTY_FACTOR // Set prehistory to 2 hours ago to account for block timestamp variations
                Account(name, ctxt, flags, chainSelector, startPlace = prehistoryDate)
            } catch (e: IllegalStateException)
            {
                LogIt.warning("Error creating account: ${e.message}")
                return null
            }

            ac.pinEntered = true  // for convenience, new accounts begin as if the pin has been entered
            ac.start(applicationContext)
            ac.onChange()
            ac.wallet.save(true)

            accounts[name] = ac
            // Write the list of existing accounts, so we know what to load
            saveActiveAccountList()
            // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()
            return ac
        }
    }


    fun deleteAccount(act: Account)
    {
        synchronized(accounts)
        {
            accounts.remove(act.name)  // remove this coin from any global access before we delete it
            launch { // cannot access db in UI thread
                saveActiveAccountList()
                act.delete()
            }

            /* stopping the blockchain is handled by the wallet/ blockchain
            val bc = act.chain.chainSelector
            var anythingUsingThisBlockchain = false
            for (a in accounts.values)
            {
                if (a.chain.chainSelector == bc)
                {
                    anythingUsingThisBlockchain = true
                    break
                }
            }
            if (!anythingUsingThisBlockchain) blockchains[bc]?.stop()
             */
        }
    }

    fun recoverAccount(
      name: String,
      flags: ULong,
      pin: String,
      secretWords: String,
      chainSelector: ChainSelector,
      earliestActivity: Long?,
      earliestHeight: Long?,
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
        SaveAccountPin(name, epin)

        var veryEarly = earliestActivity
        if (nonstandardActivity != null)
        {
            for (n in nonstandardActivity)
            {
                veryEarly = min(n.second.startTime, veryEarly ?: n.second.startTime)
            }
        }
        if (veryEarly != null) veryEarly = veryEarly - 1  // Must be earlier than the first activity

        synchronized(accounts)
        {
            val ac = Account(name, ctxt, flags, chainSelector, secretWords, veryEarly, earliestHeight, nonstandardActivity)
            ac.pinEntered = true // for convenience, new accounts begin as if the pin has been entered
            ac.start(applicationContext)
            ac.onChange()

            accounts[name] = ac
            // Write the list of existing accounts, so we know what to load
            saveActiveAccountList()
            // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()
        }
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
            val channel = NotificationChannel(NORMAL_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val channelp = NotificationChannel(PRIORITY_NOTIFICATION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
              applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(channelp)
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
        allowAccessPriceData = prefs.getBoolean(ACCESS_PRICE_DATA_PREF, true)

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
                    c.onChange()  // update all wallet UI fields since just starting up
                }
            }
        }

        var myClipboard = getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
        myClipboard.addPrimaryClipChangedListener(object:  ClipboardManager.OnPrimaryClipChangedListener {
            override fun onPrimaryClipChanged()
            {
                val tmp = myClipboard.getPrimaryClip()
                if (tmp != null) currentClip = tmp
            }

        })
        updateClipboardCache()
    }

    fun updateClipboardCache()
    {
        var myClipboard = getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
        val tmp = myClipboard.getPrimaryClip()
        if (tmp != null) currentClip = tmp
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


    /** Automatically handle this intent if its something that can be done without user intervention.
    Returns true if it was handled, false if user-intervention needed.
    * */
    var autoPayNotificationId = -1
    fun autoHandle(intentUri: String): Boolean
    {
        val iuri: Uri = intentUri.toUri()
        val scheme = iuri.scheme // intentUri.split(":")[0]
        val path = iuri.getPath()
        if (scheme == TDPP_URI_SCHEME)
        {
            val tp = TricklePaySession(tpDomains)
            if (path == "/sendto")
            {
                try
                {
                    val result = tp.attemptAutopay(intentUri)
                    val acc = tp.getRelevantAccount()
                    val amtS: String = acc.format(acc.fromFinestUnit(tp.totalNexaSpent)) + " " + acc.currencyCode
                    val act = currentActivity
                    when(result)
                    {
                        TdppAction.ASK ->
                        {
                            var intent = Intent(this, TricklePayActivity::class.java)
                            intent.data = Uri.parse(intentUri)
                            if (act != null) autoPayNotificationId =
                              notifyPopup(intent, i18n(R.string.PaymentRequest), i18n(R.string.AuthAutopay) % mapOf("domain" to tp.domainAndTopic, "amt" to amtS), act, false, autoPayNotificationId)
                            return false
                        }
                        TdppAction.ACCEPT ->
                        {
                            // Intent() means unclickable -- change to pop up configuration if clicked
                            if (act != null) autoPayNotificationId =
                                  notifyPopup(Intent(), i18n(R.string.AuthAutopayTitle), i18n(R.string.AuthAutopay) % mapOf("domain" to tp.domainAndTopic, "amt" to amtS), act, false, autoPayNotificationId)
                            return true
                        }

                        TdppAction.DENY -> return true  // true because "autoHandle" returns whether the intent was "handled" automatically -- denial is handling it
                    }
                }
                catch (e:WalletNotEnoughBalanceException)
                {
                    val act = currentActivity
                    if (act != null)
                        autoPayNotificationId = notifyPopup(Intent(), i18n(R.string.insufficentBalance), e.shortMsg ?: e.message ?: i18n(R.string.unknownError), act, false, autoPayNotificationId)
                }
            }
            if (path == "/lp")  // we are already connected which is how this being called in the app context
            {
                toast(R.string.connected)
                return true
            }
            if (path == "/share")
            {
                val sess = TricklePaySession(tpDomains)
                sess.handleShareRequest(iuri) {
                    if (it != -1)
                    {
                        val msg: String = i18n(R.string.SharedNotification) % mapOf("what" to i18n(it))
                        toast(msg)
                    }
                    else toast(R.string.badQR)
                }
            }
        }
        return false
    }


    /** send a casual popup message that's not a notification */
    fun toast(Rstring: Int) = toast(i18n(Rstring))
    fun toast(s: String)
    {
        looper.handler.post {
            val t = Toast.makeText(this, s, Toast.LENGTH_SHORT)
            val y = displayMetrics.heightPixels
            t.setGravity(android.view.Gravity.TOP, 0, y / 15)
            t.show()
        }
    }

    class LooperThread : Thread()
    {
        lateinit var handler: Handler
        override fun run()
        {
            Looper.prepare()
            handler = object : Handler(Looper.myLooper()!!)
            {
            }
            Looper.loop()
        }

        init{
            start()
        }
    }

    val looper = LooperThread()


    /** Remove a notification that was installed using the notify() function */
    fun denotify(intent: Intent)
    {
        val nid = intent.getIntExtra("wallyNotificationId", -1)
        if (nid != -1) denotify(nid)
    }

    /* Remove a notification */
    fun denotify(id: Int)
    {
        notifs.removeIf { it.first == id }  // clear out our local record of this intent
        with(NotificationManagerCompat.from(this))
        {
            cancel(id)
        }
    }

    fun activeNotifications(): Array<StatusBarNotification>
    {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val n = nm.activeNotifications
        n.sortBy({ it.postTime })
        n.filter { it.packageName == this.packageName }
        return n
    }

    /** Either automatically trigger an intent to be handled, or return the intent the activity should handle, or return null if no intents pending */
    fun getNotificationIntent(): Intent?
    {
        //val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        //val notifs = nm.activeNotifications
        val sysnotifs = activeNotifications()
        var idx = 0
        while(idx < sysnotifs.size)
        {
            val sbn = sysnotifs[idx]
            val id = sbn.id
            val n = sbn.notification
            LogIt.info(sourceLoc() + "onResume handle notification intent:" + n.contentIntent.toString())
            try
            {
                n.contentIntent.send()
                return null
            }
            catch(e:CanceledException)
            {
                idx++
            }
            finally
            {
                denotify(id)
            }
        }

        // If the user turned notifications off for this app, there won't be any but we still need to process incoming requests
        if (notifs.isNotEmpty())
        {
            val n = notifs[0]
            notifs.removeAt(0)
            return(n.third)
        }

        return null
    }

    /** Create a notification of a pending intent */
    fun notify(intent: Intent, content: String, activity: AppCompatActivity, actionRequired: Boolean = true, overwrite: Int = -1): Int
    {
        return notify(intent, "Wally Wallet", content, activity, actionRequired, overwrite)
    }

    fun notifyPopup(intent: Intent, title: String, content: String, activity: AppCompatActivity, actionRequired: Boolean = true, overwrite: Int = -1): Int
    {
        return notify(intent, title, content, activity, actionRequired, overwrite, NotificationCompat.PRIORITY_HIGH)
    }
    /** Create a notification of a pending intent */
    fun notify(intent: Intent, title: String, content: String, activity: AppCompatActivity, actionRequired: Boolean = true, overwrite: Int = -1, priority: Int = NotificationCompat.PRIORITY_DEFAULT): Int
    {
        // Save the notification id into the Intent so we can remove it when needed
        val nid = if (overwrite == -1) notifId++ else overwrite  // reminder: this is a post-increment!
        intent.putExtra("wallyNotificationId", nid)

        val pendingIntent = PendingIntent.getActivity(activity, nid, intent, PendingIntent.FLAG_IMMUTABLE)
        var builder = NotificationCompat.Builder(activity, if (priority == NotificationCompat.PRIORITY_DEFAULT) NORMAL_NOTIFICATION_CHANNEL_ID else PRIORITY_NOTIFICATION_CHANNEL_ID)
          //.setSmallIcon(R.drawable.ic_notifications_black_24dp)
          .setSmallIcon(R.mipmap.wallynexa)
          .setContentTitle(title)
          .setContentText(content)
          .setPriority(priority)
          .setContentIntent(pendingIntent)
          .setAutoCancel(true)

        notifs.add(Triple(nid,pendingIntent, intent))
        with(NotificationManagerCompat.from(this))
        {
            try
            {
                notify(nid, builder.build())
            }
            catch(e:SecurityException)
            {
                // We don't have permission to send notifications
            }
            return nid
        }
    }

    /** If you need to do a POST operation within the App context (because you are ending the activity) call these functions */
    fun post(url: String, contents: (HttpRequestBuilder) -> Unit)
    {
        later()
        {
            LogIt.info(sourceLoc() + ": POST response to server: $url")
            val client = HttpClient(Android)
            {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpTimeout) { requestTimeoutMillis = 5000 }
            }

            try
            {
                val response: HttpResponse = client.post(url, contents)
                val respText = response.bodyAsText()
                displayNotice(respText)
            }
            catch (e: SocketTimeoutException)
            {
                displayError(R.string.connectionException)
            }
            client.close()
        }
    }

    fun postThen(url: String, contents: (HttpRequestBuilder) -> Unit, next: ()->Unit)
    {
        later()
        {
            LogIt.info(sourceLoc() + ": POST response to server: $url")
            val client = HttpClient(Android)
            {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpTimeout) { requestTimeoutMillis = 5000 }
            }

            try
            {
                val response: HttpResponse = client.post(url, contents)
                val respText = response.bodyAsText()
                wallyApp?.displayNotice(respText)
                next()
            }
            catch (e: SocketTimeoutException)
            {
                displayError(R.string.connectionException)
            }
            client.close()
        }
    }

    fun getElectrumServerOn(cs: ChainSelector):IpPort
    {
        val prefDB: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        // Return our configured node if we have one
        var name = chainToURI[cs]
        val node = prefDB.getString(name + "." + CONFIGURED_NODE, null)
        if (node != null) return IpPort(node, DEFAULT_NEXATEST_TCP_ELECTRUM_PORT)
        return ElectrumServerOn(cs)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?)
    {
    }

    override fun onActivityStarted(activity: Activity)
    {
    }

    override fun onActivityResumed(activity: Activity)
    {
    }
    override fun onActivityPostResumed(activity: Activity)
    {
        updateClipboardCache()
    }

    override fun onActivityPaused(activity: Activity)
    {
    }

    override fun onActivityStopped(activity: Activity)
    {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle)
    {
    }

    override fun onActivityDestroyed(activity: Activity)
    {
    }

}