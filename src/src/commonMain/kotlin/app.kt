// Copyright (c) 2023 Bitcoin Unlimited
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally
import info.bitcoinunlimited.www.wally.ui.ACCESS_PRICE_DATA_PREF
import info.bitcoinunlimited.www.wally.ui.DEV_MODE_PREF
import io.ktor.client.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.nexa.libnexakotlin.*
import org.nexa.threads.Mutex
import org.nexa.threads.iMutex
import kotlin.concurrent.Volatile
//import java.net.ConnectException
//import java.security.spec.InvalidKeySpecException
//import java.util.*
import kotlin.coroutines.CoroutineContext

private val LogIt = GetLog("BU.wally.app")

var wallyApp: CommonApp? = null
var forTestingDoNotAutoCreateWallets = false
var kvpDb: KvpDatabase? = null
@Volatile
var coinsCreated = false

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
                    app.handleAnyIntent(respText)
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
    // Set to true if some wallet has a nontrivial balance and its recovery key has not been viewed (and we have not warned since app instantiation)
    var warnBackupRecoveryKey = Channel<Boolean>() // false

    val accessHandler = AccessHandler(this)

    protected val coMiscCtxt: CoroutineContext = newFixedThreadPoolContext(4, "app")
    protected val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    val accountLock = org.nexa.threads.Mutex()
    val accounts: MutableMap<String, Account> = mutableMapOf()

    // The currently selected account
    var focusedAccount: Account? = null

    // You can access the primary account object in a manner that throws an exception or returns a null, your choice
    var nullablePrimaryAccount: Account? = null
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

    fun handleAnyIntent(url: String)
    {
        // TODO call platform specific?
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

    /** Create a new account */
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
            launch(coMiscScope)
            {
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

                    val accountNameList = accountNames.decodeUtf8().split(",")
                    for (name in accountNameList)
                    {
                        LogIt.info(sourceLoc() + " " + name + ": Loading account")
                        try
                        {
                            val ac = Account(name, prefDB = prefs)
                            accountLock.lock { accounts[ac.name] = ac }
                        } catch (e: DataMissingException)
                        {
                            LogIt.warning(sourceLoc() + " " + name + ": Active account $name was not found in the database. Error $e")
                            // Nothing to really do but ignore the missing account
                        }
                        LogIt.info(sourceLoc() + " " + name + ": Loaded account")
                    }
                }

                var warning = false
                accountLock.lock {
                    for (c in accounts.values)
                    {
                        if (((c.flags and ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY) == 0UL) && (c.wallet.balance > MAX_NO_RECOVERY_WARN_BALANCE))
                        {
                            warning = true
                            break
                        }
                    }
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

                coinsCreated = true
                warnBackupRecoveryKey.send(warning)

                val alist = accountLock.lock { accounts.values }
                for (c in alist)
                {
                    c.setBlockchainAccessModeFromPrefs()
                    c.start()
                    c.onChange()  // update all wallet UI fields since just starting up
                }

            }
        }

    }


    var lastError:Int? = null
    var lastErrorDetails:String? = null
    var lastNotice:Int? = null
    var lastNoticeDetails:String? = null

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

    fun displayException(e: LibNexaExceptionI)
    {
        lastError = e.errCode
        lastErrorDetails = e.message
    }
}

