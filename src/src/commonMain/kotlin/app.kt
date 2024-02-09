// Copyright (c) 2023 Bitcoin Unlimited
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.ACCESS_PRICE_DATA_PREF
import info.bitcoinunlimited.www.wally.ui.DEV_MODE_PREF
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
import kotlinx.coroutines.newFixedThreadPoolContext
import org.nexa.libnexakotlin.*
import org.nexa.threads.Mutex
import org.nexa.threads.iMutex
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

private val LogIt = GetLog("BU.wally.app")

var wallyApp: CommonApp? = null
var forTestingDoNotAutoCreateWallets = false
var kvpDb: KvpDatabase? = null

//@Volatile
//var coinsCreated = false

data class LongPollInfo(val proto: String, val hostPort: String, val cookie: String?, var active: Boolean = true)

/** incompatible changes, extra fields added, fields and field sizes are the same, but content may be extended (that is, addtl bits in enums) */
val WALLY_DATA_VERSION = byteArrayOf(1, 0, 0)


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

    val coMiscCtxt: CoroutineContext = newFixedThreadPoolContext(6, "app")
    val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    val accountLock = org.nexa.threads.Mutex()
    val accounts: MutableMap<String, Account> = mutableMapOf()

    // The currently selected account
    var focusedAccount: Account? = null

    // You can access the primary account object in a manner that throws an exception or returns a null, your choice
    var nullablePrimaryAccount: Account? = null

    val assetManager = AssetManager(this)
    val tpDomains: TricklePayDomains = TricklePayDomains(this)

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

    fun defaultPrimaryAccount(): Account
    {
        return accountLock.lock {
            // return the first Nexa wallet
            for (i in accounts.values)
            {
                LogIt.info("looking for primary at wallet " + i.name + "blockchain: " + i.chain.name)
                if (i.wallet.chainSelector == ChainSelector.NEXA) return@lock i
            }
            for (i in accounts.values)
            {
                LogIt.info("falling back to testnet")
                if (i.wallet.chainSelector == ChainSelector.NEXATESTNET) return@lock i
            }
            for (i in accounts.values)
            {
                LogIt.info("falling back to regtest")
                if (i.wallet.chainSelector == ChainSelector.NEXAREGTEST) return@lock i
            }
            throw PrimaryWalletInvalidException()
        }
    }

    fun amIbackground():Boolean  // TODO return true if the app is in the background
    {
        return false
    }

    /** Do whatever you pass but not within the user interface context, asynchronously.
     * Launching into these threads means your task will outlast the activity it was launched in */
    fun later(fn: suspend () -> Unit): Unit
    {
        launch(coMiscScope) {
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
    fun handlePaste(urlStr: String): Boolean
    {
        try
        {
            val uri = Uri.parse(urlStr)
            val scheme = uri.scheme
            // TODO val notify = amIbackground()
            val app = wallyApp
            if (app == null) return false // should never occur

            // see if this is an address without the prefix
            val whichChain = if (scheme == null)
            {
                try
                {
                    ChainSelectorFromAddress(urlStr)
                }
                catch (e: UnknownBlockchainException)
                {
                    displayError(S.unknownCryptoCurrency)
                    return false
                }
            }
            else uriToChain[scheme]

            if (whichChain != null)  // handle a blockchain address (by preparing the send to)
            {
                val attribs = uri.queryMap()
                val act = accountsFor(whichChain).firstOrNull()

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

                // Inject a change into the GUI
                launch {
                    externalDriver.send(GuiDriver(ScreenId.Home, sendAddress = chainToURI[whichChain] + ":" + uri.body(), amount = amt, note = attribs["label"], chainSelector = whichChain, account = act))
                }
            }
            else if (scheme == IDENTITY_URI_SCHEME)
            {
                HandleIdentity(uri)
            }
            else if (scheme == TDPP_URI_SCHEME)
            {
                HandleTdpp(uri)
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

        val prefs = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
        devMode = prefs.getBoolean(DEV_MODE_PREF, false)
        allowAccessPriceData = prefs.getBoolean(ACCESS_PRICE_DATA_PREF, true)
        openAllAccounts()
        tpDomains.load()
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

        // put the primary account first
        nullablePrimaryAccount?.let {
            if (chain == it.chain.chainSelector && it.visible) ret.add(it)
        }

        accountLock.lock {
            // Look for any other match
            for (account in accounts.values)
            {
                if (account.visible && (chain == account.wallet.chainSelector) && (nullablePrimaryAccount != account))
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

        // I only want to write the PIN once when the account is first created
        val epin = if (pin.length > 0) EncodePIN(name, pin) else byteArrayOf()

        return accountLock.lock {
            val ac = try
            {
                val prehistoryDate = (epochMilliSeconds() / 1000L) - PREHISTORY_SAFEFTY_FACTOR // Set prehistory to 2 hours ago to account for block timestamp variations
                Account(name, flags, chainSelector, startPlace = prehistoryDate)
            } catch (e: IllegalStateException)
            {
                LogIt.warning("Error creating account: ${e.message}")
                return@lock null
            }

            ac.pinEntered = true  // for convenience, new accounts begin as if the pin has been entered
            ac.start()
            ac.onChange()
            ac.saveAccountPin(name, epin)
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
    )
    {
        // If the account is being restored from a recovery key, then the user must have it saved somewhere already
        val flags = flags_p or ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY
        dbgAssertNotGuiThread()

        // I only want to write the PIN once when the account is first created
        val epin = try
        {
            EncodePIN(name, pin.trim())
        }
        // catch (e: InvalidKeySpecException)
        catch (e: Exception)  // If the pin is bad (generally whitespace or null) ignore it
        {
            byteArrayOf()
        }

        var veryEarly = earliestActivity
        if (nonstandardActivity != null)
        {
            for (n in nonstandardActivity)
            {
                veryEarly = min(n.second.startTime, veryEarly ?: n.second.startTime)
            }
        }
        if (veryEarly != null) veryEarly = veryEarly - 1  // Must be earlier than the first activity

        accountLock.lock {
            val ac = Account(name, flags, chainSelector, secretWords, veryEarly, earliestHeight, nonstandardActivity)
            ac.pinEntered = true // for convenience, new accounts begin as if the pin has been entered
            ac.start()
            ac.onChange()
            ac.saveAccountPin(name, epin)

            accounts[name] = ac
            // Write the list of existing accounts, so we know what to load
            saveActiveAccountList()
            // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()
        }
    }

    fun openAllAccounts()
    {
        if (!forTestingDoNotAutoCreateWallets)  // If I'm running the unit tests, don't auto-create any wallets since the tests will do so
        {
            // Initialize the currencies supported by this wallet
            later {
                val prefs = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
                LogIt.info(sourceLoc() + " Wally Wallet App Started")
                kvpDb = openKvpDB(dbPrefix + "wpw")

                if (REG_TEST_ONLY)  // If I want a regtest only wallet for manual debugging, just create it directly
                {
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
                }
                else  // OK, recreate the wallets saved on this phone
                {
                    val db = kvpDb!!

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

                    val accountNameStr = accountNames.decodeUtf8()
                    LogIt.info("Loading active accounts: $accountNameStr")
                    val accountNameList = accountNameStr.split(",")
                    for (name in accountNameList)
                    {
                        if (name.length > 0)  // Note in kotlin "".split(",") results in a list of one element containing ""
                        {
                            LogIt.info(sourceLoc() + " " + name + ": Loading account")
                            try
                            {
                                val ac = Account(name, prefDB = prefs)
                                accountLock.lock { accounts[ac.name] = ac }
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

                // Cannot pick the primary account until accounts are loaded
                val primaryActName = prefs.getString(PRIMARY_ACT_PREF, null)
                nullablePrimaryAccount = accountLock.lock { if (primaryActName != null) accounts[primaryActName] else null }
                try
                {
                    if (nullablePrimaryAccount == null) primaryAccount = defaultPrimaryAccount()
                    focusedAccount = nullablePrimaryAccount ?: defaultPrimaryAccount()
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
                if (recoveryWarning != null) externalDriver.send(GuiDriver(show = setOf(ShowIt.WARN_BACKUP_RECOVERY_KEY), account = recoveryWarning))
            }
        }

    }

    /** If you need to do a POST operation within the App context (because you are ending the activity) call these functions */
    fun post(url: String, contents: (HttpRequestBuilder) -> Unit)
    {
        info.bitcoinunlimited.www.wally.later()
        {
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
        info.bitcoinunlimited.www.wally.later()
        {
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

