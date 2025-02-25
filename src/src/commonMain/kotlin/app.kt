// Copyright (c) 2023 Bitcoin Unlimited
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui2.*
import io.ktor.client.*
import io.ktor.client.network.sockets.*
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.newFixedThreadPoolContext
import org.nexa.libnexakotlin.*
import org.nexa.threads.*
import kotlin.coroutines.CoroutineContext
import kotlin.concurrent.Volatile

private val LogIt = GetLog("BU.wally.app")
const val SELECTED_ACCOUNT_NAME_PREF = "selectedAccountName"

val i18nLbc = mapOf(
  RinsufficentBalance to S.insufficentBalance,
  RbadWalletImplementation to S.badWalletImplementation,
  RdataMissing to S.PaymentDataMissing,
  RwalletAndAddressIncompatible to S.chainIncompatibleWithAddress,
  RnotSupported to S.notSupported,
  Rexpired to S.expired,
  RsendMoreThanBalance to S.sendMoreThanBalance,
  RbadAddress to S.badAddress,
  RblankAddress to S.blankAddress,
  RblockNotForthcoming to S.blockNotForthcoming,
  RheadersNotForthcoming to S.headersNotForthcoming,
  RbadTransaction to S.badTransaction,
  RfeeExceedsFlatMax to S.feeExceedsFlatMax,
  RexcessiveFee to S.excessiveFee,
  Rbip70NoAmount to S.badAmount,
  RdeductedFeeLargerThanSendAmount to S.deductedFeeLargerThanSendAmount,
  RwalletDisconnectedFromBlockchain to S.walletDisconnectedFromBlockchain,
  RsendDust to S.sendDustError,
  RnoNodes to S.NoNodes,
  RwalletAddressMissing to S.badAddress,
  RunknownCryptoCurrency to S.unknownCryptoCurrency,
  RsendMoreTokensThanBalance to S.insufficentTokenBalance
)

var wallyApp: CommonApp? = null
var forTestingDoNotAutoCreateWallets = false
var kvpDb: KvpDatabase? = null

fun tlater(name: String?=null, job: ()->Unit)
{
    wallyApp?.threadJobPool?.later(name, job)
}

fun onetlater(name: String, job: ()->Unit)
{
    wallyApp?.threadJobPool?.oneLater(name, job)
}

data class LongPollInfo(val proto: String, val hostPort: String, val cookie: String?, var active: Boolean = true)

/** incompatible changes, extra fields added, fields and field sizes are the same, but content may be extended (that is, addtl bits in enums) */
val WALLY_DATA_VERSION = byteArrayOf(1, 0, 0)

/** returns true if some old accounts existed */
expect fun convertOldAccounts(): Boolean

@Volatile var backgroundStop = false   // If true, tell the background loop to stop processing -- reset to false whenever we enter backgroundSync
@Volatile var backgroundOnly = true    // Set to false when the GUI is launched so we know that the app is also in the foreground
/**
    Execute a background sync.  Call the passed completion function when done, do not return until completed
 */
fun backgroundSync(completion: () -> Unit)
{
    org.nexa.threads.setThreadName("background_sync")
    // Perform your background synchronization work here
    // ...
    backgroundStop = false
    wallyApp!!.openAllAccounts()  // Creating an account will automatically launch blockchain and wallet sync threads
    millisleep(10000U)     // Give those threads time to connect to another node and get block headers.
                                  // Otherwise the system may think accounts are synced simply because it doesn't have any up to date info.

    // wait for all accounts to report being synced
    if (wallyApp!!.accounts.size > 0)
    {
        var unsynced = 0
        do
        {
            unsynced = 0
            for (a in wallyApp!!.accounts)
            {
                // We only want to do this if we are in background mode, otherwise the foreground code handles it
                if (backgroundOnly == false) return
                val wal = a.value.wallet
                if (!wal.synced())
                {
                    LogIt.info("${wal.name}: background syncing at ${wal.chainstate?.syncedHeight} of ${wal.blockchain.curHeight}")
                    unsynced++
                }
            }
            LogIt.info(sourceLoc() + " Background work: Still syncing $unsynced accounts")
            if (backgroundStop) break
            millisleep(30000U)
        } while(unsynced != 0 && !backgroundStop)
    }
    for (a in wallyApp!!.accounts)
    {
        a.value.wallet.save(true)
    }
    LogIt.info("Background sync completed")
    // Once done, call completion()
    completion()
}

/**
    To be called shortly before the task’s background time expires.
    Cancel any ongoing work and to do any required cleanup in as short a time as possible.
    `cancelBackgroundSync()` is to be called by a background work expiration handler.
    The time allocated by the system for expiration handlers to cancel ongoing work doesn’t vary with the number of background tasks.
    `cancelBackgroundSync()` must complete before the allocated background task's time.
 */
fun cancelBackgroundSync()
{
    // DRAFT: Improve and test this function
    backgroundStop = true
    wallyApp!!.accountLock.lock {
        for (c in wallyApp!!.accounts.values)
        {
            c.wallet.stop()
            c.chain.stop()
        }
    }
}


class AccessHandler(val app: CommonApp)
{
    var done: Boolean = false

    val sync: iMutex = Mutex()
    val activeLongPolls = mutableMapOf<String, LongPollInfo>()

    fun startLongPolling(proto: String, hostPort: String, cookie: String?)
    {
        app.later { longPolling(proto, hostPort, cookie) }
    }

    fun endLongPolling(url: String)
    {
        sync.synchronized {
            activeLongPolls.remove(url)
        }
    }

    /** Searches for an active connection to this host.  If the host is provided without a port, any connection to that host is used
     * */
    fun activeTo(host: String): LongPollInfo?
    {
        if (host.contains(":") )
        {
            val lpi = activeLongPolls[host]
            if (lpi != null && lpi.active) return activeLongPolls[host]
        }
        // Search for only host (not port)
        for (lpi in activeLongPolls)
        {
            if (lpi.value.hostPort.split(":")[0] == host.split(":")[0])
            {
                if (lpi.value.active)
                    return lpi.value
            }
        }
        return null
    }

    suspend fun longPolling(scheme: String, hostPort: String, cookie: String?)
    {
        var connectProblems = 0
        val cookieString = if (cookie != null) "?cookie=$cookie" else ""
        val url = scheme + "://" + hostPort + "/_lp" + cookieString

        val lpInfo = sync.synchronized()
        {
            if (activeLongPolls.contains(hostPort))
            {
                LogIt.info("Already long polling to $hostPort, replacing it with $url.")
                activeLongPolls[hostPort]?.active = false
            }
            activeLongPolls.put(hostPort, LongPollInfo(scheme, hostPort, cookie))
            activeLongPolls[hostPort]!!
        }

        val client = GetHttpClient(timeoutInMs=60000)

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
                LogIt.info(sourceLoc() + ": Long poll to $url returned with this request: $respText")
                if (respText == "Q")
                {
                    LogIt.info(sourceLoc() + ": Long poll to $url ended (server request).")
                    endLongPolling(hostPort)
                    return // Server tells us to quit long polling
                }
                if (respText != "")
                {
                    app.handlePaste(respText)
                    count += 1
                }
                else
                {
                    LogIt.info(sourceLoc() + ": Long poll to $url finished with no activity.")
                }
            }
            catch (e: ConnectTimeoutException)  // network error?  TODO retry a few times
            {
                if (connectProblems > 500)
                {
                    LogIt.info(sourceLoc() + ": Long poll to $url connection exception $e, stopping.")
                    endLongPolling(hostPort)
                    return
                }
                connectProblems += 1
                delay(1000)
            }
            catch (e: Throwable)
            {
                LogIt.info(sourceLoc() + ": Long poll to $url error, stopping: ")
                //handleThreadException(e, "Long poll to $url error, stopping.", sourceLoc())
                endLongPolling(hostPort)
                return
            }
            val end = epochMilliSeconds()
            avgResponse = ((avgResponse*49f)+(end-start))/50.0f
            if (avgResponse<1000)
                delay(500) // limit runaway polling, if the server misbehaves by responding right away
        }
        LogIt.info(sourceLoc() + ": Long poll to $hostPort ($url) ended (done).")
        endLongPolling(hostPort)
    }
}

open class CommonApp
{
    // Set to true if this is the first time this app has ever been run
    var firstRun = false

    val accessHandler = AccessHandler(this)

    val coMiscCtxt: CoroutineContext = newFixedThreadPoolContext(8, "app")
    val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    val threadJobPool = ThreadJobPool("tapp", 4)

    val accountLock = org.nexa.threads.Mutex()
    val accounts: MutableMap<String, Account> = mutableMapOf()

    // The currently selected account
    var focusedAccount: MutableStateFlow<Account?> = MutableStateFlow(null)

    // You can access the primary account object in a manner that throws an exception or returns a null, your choice
    var nullablePrimaryAccount: Account? = null

    val assetManager = AssetManager()
    val tpDomains: TricklePayDomains = TricklePayDomains()

    var assetLoaderThread: iThread? = null
    var periodicAnalysisThread: iThread? = null

    init
    {
        // Set up the libnexakotlin translations
        appI18n = { libErr: Int -> i18n(i18nLbc[libErr] ?: libErr) }
        // electrumLogging = true
    }

    /** Return an ordered map of the visible accounts (in display order) */
    fun orderedAccounts(visibleOnly: Boolean = true): ListifyMap<String, Account>
    {
        return accountLock.synchronized {
            ListifyMap(accounts, { if (visibleOnly) it.value.visible else true }, object : Comparator<String>
            {
                override fun compare(p0: String, p1: String): Int
                {
                    if (wallyApp?.nullablePrimaryAccount?.name == p0) return Int.MIN_VALUE
                    if (wallyApp?.nullablePrimaryAccount?.name == p1) return Int.MAX_VALUE
                    return p0.compareTo(p1)
                }
            })
        }
    }

    var primaryAccount: Account
        get()
        {
            return accountLock.synchronized {
                var tmp = nullablePrimaryAccount
                if (tmp == null)
                {
                    tmp = defaultPrimaryAccount()
                    nullablePrimaryAccount = tmp
                }
                tmp
            }
        }
        set(act: Account)
        {
            accountLock.synchronized()
            {
                val prefs = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
                with(prefs.edit())
                {
                    putString(PRIMARY_ACT_PREF, act.name)
                    commit()
                }
                nullablePrimaryAccount = act
            }
        }

    /** Return some account that's visible in order of focused, primary, then any visible account starting with Nexa then testnet, regtest.
     * Throws throw PrimaryWalletInvalidException() is there is nothing */
    @Composable
    fun focusedAccount(): Account?
    {
        focusedAccount.collectAsState().value?.let { if (it.visible) return it }
        nullablePrimaryAccount?.let { if (it.visible) return it }
        return defaultPrimaryAccount()
    }

    /** Return some account that's visible in order of focused, primary, then any visible account starting with Nexa then testnet, regtest.
     * Throws throw PrimaryWalletInvalidException() is there is nothing */
    fun preferredVisibleAccount(): Account
    {
        focusedAccount.value?.let { if (it.visible) return it }
        nullablePrimaryAccount?.let { if (it.visible) return it }
        return defaultPrimaryAccount()
    }

    /** Return some account that's visible in order of focused, primary, then any visible account starting with Nexa then testnet, regtest.
     * @return null if there is no matching account */
    fun preferredVisibleAccountOrNull(): Account?
    {
        focusedAccount.value?.let { if (it.visible) return it }
        nullablePrimaryAccount?.let { if (it.visible) return it }
        return try
        {
            defaultPrimaryAccount()
        }
        catch (exception: PrimaryWalletInvalidException)
        {
            null
        }
    }

    // Returns a visible account that matches the blockchain of the passed address, in the following order of preference:
    // The passed account
    // The preferredVisibleAccount (focused/selected account)
    // The defaultPrimaryAccount
    // Order shown in the UI home page
    //
    // If no account matches the blockchain of the passed address (or if that text is not even an address), null is returned
    fun visibleAccountFor(address: String, defaultAccount: Account? = null): Account?
    {
        try
        {
            val p = PayAddress(address)
            if (defaultAccount?.wallet?.chainSelector == p.blockchain) return defaultAccount  // Stick with the current one if we can
            var cand = preferredVisibleAccountOrNull()  // Or try our preferred account
            if (cand?.wallet?.chainSelector == p.blockchain) return cand
            cand = kotlin.runCatching { defaultPrimaryAccount() }.getOrNull()
            if (cand?.wallet?.chainSelector == p.blockchain) return cand
            for (act in orderedAccounts(true))
            {
                if (act.wallet.chainSelector == p.blockchain) return act
            }
            // there's nothing that will handle this address
            return null
        }
        catch(e: Exception)  // not an address
        {
            return null
        }
    }



    fun defaultPrimaryAccount(): Account
    {
        return accountLock.lock {
            // return the one stored in preferences
            val selectedAccountName = preferenceDB.getString(SELECTED_ACCOUNT_NAME_PREF, null)
            val selectedAccountPref = accounts[selectedAccountName]
            if (selectedAccountPref != null && selectedAccountPref.visible)
            {
                return@lock selectedAccountPref
            }

            // return the first visible Nexa wallet
            for (i in accounts.values)
            {
                LogIt.info("looking for primary at wallet " + i.name + "blockchain: " + i.chain.name)
                if ((i.wallet.chainSelector == ChainSelector.NEXA) && i.visible) return@lock i
            }
            for (i in accounts.values)
            {
                LogIt.info("falling back to testnet")
                if ((i.wallet.chainSelector == ChainSelector.NEXATESTNET) && i.visible) return@lock i
            }
            for (i in accounts.values)
            {
                LogIt.info("falling back to regtest")
                if ((i.wallet.chainSelector == ChainSelector.NEXAREGTEST) && i.visible) return@lock i
            }
            throw PrimaryWalletInvalidException()
        }
    }

    fun amIbackground():Boolean  // TODO return true if the app is in the background
    {
        return false
    }

    /** Returns true if any account has any assets */
    fun hasAssets():Boolean
    {
        for (a in accounts)
        {
            if (a.value.hasAssets())
                return true
        }
        return false
    }

    /** Do whatever you pass but not within the user interface context, asynchronously.
     * Launching into these threads means your task will outlast the activity it was launched in */
    fun later(fn: suspend () -> Unit): Unit
    {
        launch(CoroutineScope(coMiscCtxt)) {
            try
            {
                fn()
            } catch (e: Exception) // Uncaught exceptions will end the app
            {
                handleThreadException(e)
            }
        }
    }

    // We wouldn't notify if this app produced the Intent from active user input (like QR scan)
    // but if it came from a long poll, then notify.
    // Notifying and startActivityForResult produces a double call to that intent
    // the callback takes what is loosely the response URL, the response POST data, and a true/false value that if provided,
    // sums up whether the paste was generally "accepted" or "rejected"
    fun handlePaste(urlStrParam: String, then: ((String,String, Boolean?)->Unit)?= null): Boolean
    {
        var urlStr = urlStrParam
        try
        {
            var uri = Uri.parse(urlStr)
            var scheme = uri.scheme?.lowercase()
            // TODO val notify = amIbackground()
            val app = wallyApp
            if (app == null) return false // should never occur

            // This is a workaround to support apps that only link http or https but android can launch it into wally
            // The transformation is http://fake_domain_to_trigger_the_wallet/<actual scheme>/<actual reply domain>/<rest of the intent>
            if (scheme == "http" || scheme == "https")
            {
                //val actualScheme = uri.pathSegments[0]
                //val actualDomain = uri.pathSegments[1]
                val p = (uri.encodedPath?.drop(1) ?: "") + "?" + uri.encodedQuery
                val actualUri = p.replaceFirst("/","://")
                urlStr = actualUri
                uri = Uri.parse(actualUri)
                scheme = uri.scheme?.lowercase()
            }

            // see if this is an address without the prefix
            val whichChain = if (scheme == null)
            {
                try
                {
                    ChainSelectorFromAddress(urlStr)
                }
                catch (e: UnknownBlockchainException)
                {
                    displayError(S.unknownCryptoCurrency, urlStr)
                    return false
                }
            }
            else uriToChain[scheme]
            LogIt.info("QR code refers to $whichChain ${chainToName[whichChain]}")

            if (whichChain != null)  // handle a blockchain address (by preparing the send to)
            {
                val attribs = uri.queryMap()

                val stramt = attribs["amount"]
                var amt: BigDecimal? = null

                if (stramt != null)
                {
                    amt = try
                    {
                        stramt.toCurrency()
                    }
                    catch (e: NumberFormatException)
                    {
                        throw BadAmountException(S.detailsOfBadAmountFromIntent)
                    }
                    catch (e: ArithmeticException)  // Rounding error
                    {
                        // If someone is asking for sub-satoshi quantities, round up and overpay them
                        LogIt.warning("Sub-satoshi quantity ${stramt} requested.  Rounding up")
                        BigDecimal.fromString(stramt, NexaMathMode)
                    }
                }
                else
                {
                    // providing no amount is fine
                }

                // When deep linking from native camera that is currently only supported on iOS, and handling the
                // String content from the QR code navigating directly to the desired screen is enough.
                // I don't think we need an external GUI drive to do this now that nav is a global variable
                if (newUI.value)
                    nav.go(
                      screen = ScreenId.Send,
                      data = SendScreenNavParams(
                        toAddress = chainToURI[whichChain] + ":" + uri.body(),
                        amount = amt,
                        note = attribs["label"]
                      )
                    )
                else // external driver for navigating using driver from before nav was a global variable
                    // Inject a change into the GUI
                    wallyApp!!.later {
                        externalDriver.send(GuiDriver(ScreenId.Home, sendAddress = chainToURI[whichChain] + ":" + uri.body(), amount = amt, note = attribs["label"], chainSelector = whichChain))
                    }

            }
            else if (scheme == IDENTITY_URI_SCHEME)
            {
                HandleIdentity(uri, then)
            }
            else if (scheme == TDPP_URI_SCHEME)
            {
                HandleTdpp(uri, then)
            }
            else
            {
                return false
            }
            return true
        }
        catch (e: TdppException)
        {
            displayError(e.message ?: e.toString())
        }
        catch(e: Exception)
        {
            LogIt.info("unexpected exception handling QR code $urlStr")
            displayUnexpectedException(e)
        }
        return false
    }


    fun handleNonIntentText(text: String)
    {
        // NOTE: in certain contexts (app is background), the UI thread may not even be running so do not require completion of any laterUI tasks
        LogIt.info(sourceLoc() + "TODO: handleNonIntentText: " + text)

        /*
        // Clean out an old payment protocol if you are pasting a new send in
        paymentInProgress = null
        laterUI {
            updateSendBasedOnPaymentInProgress()
        }

        lastPaste = text
        if (text.contains('?'))  // BIP21 or BIP70
        {
            for (c in accounts.values)
            {
                if (text.contains(c.chain.uriScheme))  // TODO: prefix not contains
                {
                    handleSendURI(text)
                    return
                }
            }
        }
        else
        {
            val notify = amIbackground()
            laterUI {
                val a = updateSendAddress(text, !notify)
                if (notify)
                {
                    LogIt.info("Wally in background, sending notification")
                    var intent = Intent(this, MainActivity::class.java)
                    val addrString = a.toString() ?: ""
                    intent.data = addrString.toUri()
                    val nid = app?.notifyPopup(intent, i18n(R.string.sendRequestNotification), i18n(R.string.toColon) + addrString, this, false)
                    intent.extras?.putIntegerArrayList("notificationId", arrayListOf(nid))
                }
            }
            return
        }
        throw PasteUnintelligibleException()

         */
    }


    /** Execute a login request to a 3rd party web site via the nexid protocl.  This is done within the app context so that the login activity can return before the async login process
     * is completed.
     */
    fun handleLogin(loginReqParam: String)
    {
        val url = Url(loginReqParam)
        try
        {
            val result = url.readBytes()
            if (result.size < 100) displayNotice(result.decodeUtf8())  // sanity check result then display it
        }
        catch(e: CannotLoadException)
        {
            displayError(S.connectionException)
        }
        catch(e: Exception)  // java.net.ConnectException (connectin refused probably)
        {
            logThreadException(e, "attempting to GET to $url")
            displayError(S.connectionException)
        }
        /*
        var loginReq = loginReqParam
        var forwarded = 0
        getloop@ while (forwarded < 3)
        {
            LogIt.info(sourceLoc() +": login reply: " + loginReq)
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
                displayError(S.badLink, loginReq)
            } catch (e: java.io.IOException)
            {
                displayError(S.connectionAborted, loginReq)
            } catch (e: java.net.ConnectException)
            {
                displayError(S.connectionException)
            }

            break@getloop  // only way to actually loop is to hit a 301 or 302
        }
        */
    }

    fun handlePostLogin(loginReqParam: String, jsonBody: String)
    {
        val url = Url(loginReqParam)
        try
        {
            val result = url.readPostBytes(jsonBody)
            if (result.size < 100) displayNotice(result.decodeUtf8())  // sanity check result then display it
        }
        catch(e: CannotLoadException)
        {
            displayError(S.connectionException)
        }
        catch(e: Exception)  // java.net.ConnectException (connectin refused probably)
        {
            logThreadException(e, "attempting to POST to $url with contents\n$jsonBody")
            displayError(S.connectionException)
        }

        /*
        var loginReq = loginReqParam
        var forwarded = 0

        postloop@ while (forwarded < 3)
        {
            val url = Url(loginReq)
            LogIt.info("sending registration reply: " + loginReq)
            try
            {
                //val body = """[1,2,3]"""  // URLEncoder.encode("""[1,2,3]""","UTF-8")
                val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                req.requestMethod = "POST"
                req.setRequestProperty("Content-Type", "application/json")
                req.setRequestProperty("Accept", "xxx") // should be: star slash star but that doesn't work in a comment
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
                    displayNotice(resp)
                    return
                }
            }
            catch (e: java.net.SocketTimeoutException)
            {
                LogIt.info("SOCKET TIMEOUT:  If development, check phone's network.  Ensure you can route from phone to target!  " + e.toString())
                displayError(S.connectionException)
                return
            }
            catch (e: java.io.IOException)
            {
                LogIt.info("registration IOException: " + e.toString())
                displayError(S.connectionAborted)
                return
            }
            catch (e: FileNotFoundException)
            {
                LogIt.info("registration FileNotFoundException: " + e.toString())
                displayError(S.badLink)
                return
            }
            catch (e: java.net.ConnectException)
            {
                displayError(S.connectionException)
                return
            }
            catch (e: Throwable)
            {
                displayError(S.unknownError)
                return
            }
            break@postloop  // Only way to actually loop is to get a http 301 or 302
        }
*/
    }


    /** Returns true if accounts are synced */
    fun isSynced(visibleOnly: Boolean = true): Boolean
    {
        val ret = accountLock.lock {
            for (ac in accounts)
            {
                if ((!visibleOnly || ac.value.visible) && (!ac.value.wallet.synced())) return@lock false
            }
            true
        }
        return ret
    }

    fun onCreate()
    {
        notInUIscope = coMiscScope
        LogIt.info(sourceLoc() + " Wally Wallet App Started")

        val availableRam = platformRam()
        LogIt.info("Available RAM: $availableRam")

        if (availableRam != null && availableRam < 2048L*1024L*1024L)
        {
            LogIt.info("Using low RAM settings")
            DEFAULT_MAX_RECENT_BLOCK_CACHE = 2
            DEFAULT_MAX_RECENT_MERKLE_BLOCK_CACHE = 3
            DEFAULT_MAX_RECENT_TX_CACHE = 10
            DEFAULT_MAX_RECENT_HEADER_CACHE = 25
        }

        tlater {
            val prefs = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
            devMode = prefs.getBoolean(DEV_MODE_PREF, false)
            allowAccessPriceData = prefs.getBoolean(ACCESS_PRICE_DATA_PREF, true)
            localCurrency = prefs.getString(LOCAL_CURRENCY_PREF, "USD") ?: "USD"

            openAccountsTriggerGui()
            tpDomains.load()
        }

        assetLoaderThread = AssetLoaderThread()
        periodicAnalysisThread = uxPeriodicAnalysis()
    }

    /** Iterate through all the accounts, looping */
    fun nextAccount(accountIdx: Int):Pair<Int, Account?>
    {
        var actIdx = accountIdx
        val ret = accountLock.lock {
            val actList = visibleAccountNames()
            actIdx++
            if (actIdx >= actList.size) actIdx=0
            accounts[actList[actIdx]]
        }
        return Pair(actIdx, ret)
    }

    /** Return a list of all the visible accounts in the order they should be shown */
    fun visibleAccountNames(): Array<String>
    {
        val ret = mutableListOf<String>()
        accountLock.lock {
            for (ac in accounts)
            {
                if (ac.value.visible) ret.add(ac.key)
            }
        }
        return ret.toTypedArray()
    }

    fun accountsFor(currencyType: String) = accountsFor(currencyCodeToChain[currencyType]!!)

    fun accountsFor(chain: ChainSelector): MutableList<Account>
    {
        val ret = mutableListOf<Account>()

        // put the selected account first
        focusedAccount.value?.let {
            if (chain == it.chain.chainSelector && it.visible) ret.add(it)
        }
        // put the primary account first
        nullablePrimaryAccount?.let {
            if (chain == it.chain.chainSelector && it.visible) ret.add(it)
        }

        accountLock.lock {
            // Look for any other match
            for (account in orderedAccounts(true))
            {
                if ((chain == account.wallet.chainSelector) && (nullablePrimaryAccount != account) && (focusedAccount.value != account))
                {
                    ret.add(account)
                }
            }
        }
        return ret
    }

    /** lock all previously unlocked accounts */
    fun lockAccounts()
    {
        accountLock.lock {
            for (account in accounts.values)
            {
                account.pinEntered = false
            }
        }
    }

    /** Submit this PIN to all accounts, unlocking any that match */
    fun unlockAccounts(pin: String): Int
    {
        var unlocked = 0
        accountLock.lock {
            for (account in accounts.values)
            {
                unlocked += account.submitAccountPin(pin)
            }
        }
        if (unlocked > 0) notifyAccountUnlocked()
        return unlocked
    }

    val interestedInAccountUnlock = mutableListOf<() -> Unit>()
    fun notifyAccountUnlocked()
    {
        for (f in interestedInAccountUnlock) f()
    }

    /** Save the account list to the database */
    fun saveActiveAccountList()
    {
        openKvpDbIfNeeded()
        val s: String = accounts.keys.joinToString(",")
        val db = kvpDb!!
        db.set("activeAccountNames", s.toByteArray())
        db.set("wallyDataVersion", WALLY_DATA_VERSION)
    }

    /** Create a new account
     * @param name Account name 8 chars or less
     * @param flags
     * @param pin Account pin.  Should be numeric.  Pass "" for no PIN.
     * @param chainSelector What blockchain this account uses.
     * */
    fun newAccount(name: String, flags: ULong, pin: String, chainSelector: ChainSelector): Account?
    {
        dbgAssertNotGuiThread()
        openKvpDbIfNeeded()
        // I only want to write the PIN once when the account is first created
        val epin = if (pin.length > 0) EncodePIN(name, pin) else null

        // When creating a new account, I want to make sure that the blockchain is opened so that I can use data from it
        // to fast forward the account to the current time
        val bc = connectBlockchain(chainSelector)
        val tip = bc.nearTip
        // If the blockchain appears to be behind the current time, give the system a short period to catch up.
        // Worst case if this does nothing, it will cause a longer wallet sync as the wallet's start point will be set to
        // whatever the stored blockchain height is rather than the real height.
        if (tip == null || tip.time < (epochMilliSeconds() / 1000L) - PREHISTORY_SAFEFTY_FACTOR) millisleep(250U)

        return accountLock.lock {
            val ac = try
            {
                val prehistoryDate = (epochMilliSeconds() / 1000L) - PREHISTORY_SAFEFTY_FACTOR // Set prehistory to 2 hours ago to account for block timestamp variations
                Account(name, flags, chainSelector, startDate = prehistoryDate, startHeight = bc.curHeight)
            } catch (e: IllegalStateException)
            {
                LogIt.warning("Error creating account: ${e.message}")
                return@lock null
            }

            ac.encodedPin = epin
            ac.pinEntered = true  // for convenience, new accounts begin as if the pin has been entered
            ac.start()
            ac.onChange()
            // I save a blank if no pin just in case there's old data in the database
            ac.saveAccountPin(epin)
            ac.wallet.save(true)

            accounts[name] = ac
            // Write the list of existing accounts, so we know what to load
            saveActiveAccountList()
            // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()
            return@lock ac
        }
    }


    /** Remove an account from this app (note its still available for restoration via recovery key) */
    fun deleteAccount(act: Account)
    {
        accountLock.lock {
            accounts.remove(act.name)  // remove this coin from any global access before we delete it
            // clean up the a reference to this account, if its the primary
            if (nullablePrimaryAccount == act) nullablePrimaryAccount = null
        }

        tlater { // cannot access db in UI thread
            saveActiveAccountList()
            act.delete()
        }
    }

    /** Create an account given a recovery key and the account's earliest activity.
     * This call is time consuming and must be run async from the main or UI thread */
    fun recoverAccount(
      name: String,
      flags_p: ULong,
      pin: String,
      secretWords: String,
      chainSelector: ChainSelector,
      txhist: List<TransactionHistory>,
      dests: Set<PayDestination>,
      histEnd: iBlockHeader,
      histAddressCount: Int
    ): Account
    {
        // If the account is being restored from a recovery key, then the user must have it saved somewhere already
        val flags = flags_p or ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY
        dbgAssertNotGuiThread()
        openKvpDbIfNeeded()
        // I only want to write the PIN once when the account is first created

        val p = pin.trim()
        val epin = try
        {
            if (p.length > 0) EncodePIN(name, p) else null
        }
        // catch (e: InvalidKeySpecException)
        catch (e: Exception)  // If the pin is bad (generally whitespace or null) ignore it
        {
            null
        }

        var earliestDate: Long = Long.MAX_VALUE
        var earliestHeight: Long = Long.MAX_VALUE
        for (txh in txhist)
        {
            if (txh.confirmedHeight < earliestHeight) earliestHeight = txh.confirmedHeight
            if (txh.date < earliestDate) earliestDate = txh.date
        }

        val ac = Account(name, flags, chainSelector, secretWords, earliestDate, earliestHeight, autoInit = false)
        accountLock.lock {  // We can show it early, although it might have the wrong data momentarily
                accounts[name] = ac
            }
        ac.encodedPin = epin
        ac.pinEntered = true // for convenience, new accounts begin as if the pin has been entered

        // normally this can be done asynchronously to account creation, but we need to do it before fastforwarding
        // because if it accidentally runs after the fast forward, it will set the sync point back to these start points
        ac.asyncInit(earliestHeight, earliestDate)
        // We need to pregenerate all the destinations used in the provided transactions, or we won't recognise these transactions as our own
        ac.wallet.prepareDestinations(histAddressCount, histAddressCount)
        (ac.wallet as CommonWallet).injectReceivingAddresses(dests.toList())
        ac.wallet.fastforward(histEnd.height, histEnd.time, histEnd.hash, txhist)
        ac.start()
        ac.constructAssetMap()
        ac.onChange()
        ac.saveAccountPin(epin)
        ac.wallet.save(force=true)  // force the save
        ac.wallet.saveBip44Wallet() // because we jammed in a bunch of tx
        accountLock.lock {
            // Write the list of existing accounts, so we know what to load
            saveActiveAccountList()
        }
        return ac
    }

    /** Create an account given a recovery key and the account's earliest activity */
    fun recoverAccount(
      name: String,
      flags_p: ULong,
      pin: String,
      secretWords: String,
      chainSelector: ChainSelector,
      earliestActivity: Long?,
      earliestHeight: Long?,
      nonstandardActivity: MutableList<Pair<Bip44Wallet.HdDerivationPath, HDActivityBracket>>?
    ): Account
    {
        // If the account is being restored from a recovery key, then the user must have it saved somewhere already
        val flags = flags_p or ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY
        dbgAssertNotGuiThread()
        openKvpDbIfNeeded()
        // I only want to write the PIN once when the account is first created
        val epin = try
        {
            EncodePIN(name, pin.trim())
        }
        // catch (e: InvalidKeySpecException)
        catch (e: Exception)  // If the pin is bad (generally whitespace or null) ignore it
        {
            null
        }

        var veryEarly = earliestActivity
        if (nonstandardActivity != null)
        {
            for (n in nonstandardActivity)
            {
                veryEarly = min(n.second.startTime, veryEarly ?: n.second.startTime)
            }
        }
        if (veryEarly != null) veryEarly = veryEarly - (30*60)  // Must be earlier than the first activity, so subtract 30 min

        return accountLock.lock {
            // If I'm doing a recovery, the prehistory needs to be 1 block before the activity
            val eh = if (earliestHeight != null && earliestHeight > 0) earliestHeight-1 else earliestHeight
            val ac = Account(name, flags, chainSelector, secretWords, veryEarly, eh, retrieveOnlyActivity = nonstandardActivity)
            ac.encodedPin = epin
            ac.pinEntered = true // for convenience, new accounts begin as if the pin has been entered
            ac.start()
            ac.onChange()
            ac.saveAccountPin(epin)

            accounts[name] = ac
            // Write the list of existing accounts, so we know what to load
            saveActiveAccountList()
            // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()
            ac
        }
    }

    /** Opens the key-value pair database (stored in the kvpDb global) if its not already opened
     * Returns true if it needed to be opened, otherwise false
     */
    fun openKvpDbIfNeeded(): Boolean
    {
        return accountLock.lock {
            if (kvpDb == null)
            {
                kvpDb = openKvpDB(dbPrefix + "wpw")
                true
            }
            else false // do not open multiple times
        }
    }

    fun openAllAccounts()
    {
        val prefs = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
        openKvpDbIfNeeded()

        if (REG_TEST_ONLY)  // If I want a regtest only wallet for manual debugging, just create it directly
        {
            /*  Removed as our test infra has expanded
            accountLock.lock {
                accounts.getOrPut("RKEX") {
                    try
                    {
                        val c = Account("RKEX")
                        c
                    }
                    catch (e: DataMissingException)
                    {
                        val c = Account("RKEX", ACCOUNT_FLAG_NONE, ChainSelector.NEXAREGTEST)
                        c
                    }
                }
            }
             */
        }
        else  // OK, recreate the wallets saved on this phone
        {
            val db = kvpDb!!

            LogIt.info(sourceLoc() + " Loading account names")
            val accountNames = try
            {
                // throw Exception("TESTING")  // TODO DBG force old account conversion for testing
                db.get("activeAccountNames")
            }
            catch (e: Exception)
            {
                byteArrayOf()
            }

            // Ok maybe not first run but no wallets
            if (accountNames.size == 0)
            {
                if (!convertOldAccounts()) firstRun = true
            }

            val accountNameStr = accountNames.decodeUtf8()
            LogIt.info("Loading active accounts: $accountNameStr")
            val accountNameList = accountNameStr.split(",")
            for (name in accountNameList)
            {
                if (name.length > 0)  // Note in kotlin "".split(",") results in a list of one element containing ""
                {
                    // isolate disk access into a coroutine so this function can complete quickly
                    LogIt.info(sourceLoc() + " " + name + ": Loading account")
                    try
                    {
                        accountLock.lock {
                            if (!accounts.containsKey(name))  // only create account if its not previously created
                            {
                                val ac = Account(name, prefDB = prefs)
                                accounts[ac.name] = ac
                            }
                        }
                    }
                    catch (e: DataMissingException)
                    {
                        LogIt.warning(sourceLoc() + " " + name + ": Active account $name was not found in the database. Error $e")
                        // Nothing to really do but ignore the missing account
                    }
                    LogIt.info(sourceLoc() + " " + name + ": Loaded account")
                }
            }
        }
    }

    fun openAccountsTriggerGui()
    {
        if (!forTestingDoNotAutoCreateWallets)  // If I'm running the unit tests, don't auto-create any wallets since the tests will do so
        {
            // Initialize the currencies supported by this wallet
            tlater {
                openAllAccounts()
                assignAccountsGuiSlots()

                var recoveryWarning: Account? = null
                accountLock.lock {
                    for (c in accounts.values)
                    {
                        if (((c.flags and ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY) == 0UL) && (c.wallet.balance > MAX_NO_RECOVERY_WARN_BALANCE))
                        {
                            recoveryWarning = c
                            break
                        }
                    }
                }

                if (firstRun == true)
                {
                    newAccount("nexa", ACCOUNT_FLAG_NONE,"", ChainSelector.NEXA)
                }
                val prefs = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
                // Cannot pick the primary account until accounts are loaded
                val primaryActName = prefs.getString(PRIMARY_ACT_PREF, null)
                nullablePrimaryAccount = accountLock.lock { if (primaryActName != null) accounts[primaryActName] else null }
                try
                {
                    if (nullablePrimaryAccount == null) primaryAccount = defaultPrimaryAccount()
                    // If there is only one visible account, select it by default
                    if (visibleAccountNames().size == 1)
                    {
                        if (nullablePrimaryAccount?.visible == true) focusedAccount.value = nullablePrimaryAccount
                        else focusedAccount.value = defaultPrimaryAccount()
                        assert(focusedAccount.value?.visible ?: true)
                    }
                }
                catch(e:PrimaryWalletInvalidException)
                {
                    // nothing to do in the case where there is no account to pick
                }

                triggerAssignAccountsGuiSlots()

                val alist = accountLock.lock { accounts.values }
                for (c in alist)
                {
                    c.setBlockchainAccessModeFromPrefs()
                    c.start()
                    c.onChange()  // update all wallet UI fields since just starting up
                }
                // Going to block here until the GUI asks for this field
                if (recoveryWarning != null) later { externalDriver.send(GuiDriver(show = setOf(ShowIt.WARN_BACKUP_RECOVERY_KEY), account = recoveryWarning)) }
                for (a in alist) a.getXchgRates("USD")
            }
        }
    }

    /** If you need to do a POST operation within the App context (because you are ending the activity) call these functions */
    fun post(url: String, contents: (HttpRequestBuilder) -> Unit)
    {
        later {
            LogIt.info(sourceLoc() + ": POST response to server: $url")
            val client = HttpClient()
            {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpTimeout) { requestTimeoutMillis = HTTP_REQ_TIMEOUT_MS.toLong() }
            }

            try
            {
                val response: HttpResponse = client.post(url, contents)
                val respText = response.bodyAsText()
                displayNotice(respText)
            }
            catch (e: IOException)
            {
                displayError(S.connectionException)
            }
            catch (e: Exception)
            {
                displayError(S.connectionException)
            }
            client.close()
        }
    }

    fun postThen(url: String, contents: (HttpRequestBuilder) -> Unit, next: ()->Unit)
    {
        later {
            LogIt.info(sourceLoc() + ": POST response to server: $url")
            val client = HttpClient()
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
                next()
            }
            catch (e: SocketTimeoutException)
            {
                displayError(S.connectionException)
            }
            client.close()
        }
    }
}

