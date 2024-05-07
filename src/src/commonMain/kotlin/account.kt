package info.bitcoinunlimited.www.wally

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.ui.CONFIGURED_NODE
import kotlin.concurrent.Volatile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.nexa.libnexakotlin.*
import org.nexa.threads.iThread

/** Account flags: No flag */
const val ACCOUNT_FLAG_NONE = 0UL
/** Account flags: hide this account until pin is entered */
const val ACCOUNT_FLAG_HIDE_UNTIL_PIN = 1UL
/** Account flags: User affirms they've backed up the recovery secret */
const val ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY = 2UL
/** Account flags: Reuse addresses rather than generate a new address each time */
const val ACCOUNT_FLAG_REUSE_ADDRESSES = 4UL

const val RETRIEVE_ONLY_ADDITIONAL_ADDRESSES = 10

/** Do not warn about not having backed up the recovery key until balance exceeds this amount (satoshis) */
const val MAX_NO_RECOVERY_WARN_BALANCE = 1000000 * 10


private val LogIt = GetLog("BU.wally.Account")

/** You can prefix every database (to isolate testing from production, for example) with this string */
var dbPrefix = ""
/** Currently selected fiat currency code */
var fiatCurrencyCode: String = "USD"

// Note that this returns the last time and block when a new address was FIRST USED, so this may not be what you wanted
data class HDActivityBracket(val startTime: Long, val startBlockHeight: Int, val lastTime: Long, val lastBlockHeight: Int, val lastAddressIndex: Int)

expect fun EncodePIN(actName: String, pin: String, size: Int = 64): ByteArray

fun WallyGetCnxnMgr(chain: ChainSelector, name: String? = null, start:Boolean = true): CnxnMgr
{
    val ret = GetCnxnMgr(chain, name, start)
    if (chain == ChainSelector.NEXA)
    {
        later {
            ret.add("nexa.wallywallet.org", NexaPort, 100, true)
            ret.add("p2p.wallywallet.org", NexaPort, 90, true)
        }
    }
    return ret
}


class Account(
  val name: String, //* The name of this account
  var flags: ULong = ACCOUNT_FLAG_NONE,
  val chainSelector: ChainSelector? = null,
  secretWords: String? = null,
  startDate: Long? = null, //* Where to start looking for transactions
  startHeight: Long? = null, //* block height of first activity
  autoInit: Boolean = true, /** Automatically begin the asynchronous initialization phase */
  retrieveOnlyActivity: MutableList<Pair<Bip44Wallet.HdDerivationPath, HDActivityBracket>>? = null,  //* jam in other derivation paths to grab coins from (but use addresses of) (if new account)
  val prefDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
)
{
    val handler = CoroutineExceptionHandler {
        _, exception -> LogIt.error("Caught in Account CoroutineExceptionHandler: $exception")
    }
    var walletDb: WalletDatabase? = openWalletDB(dbPrefix + name + "_wallet", chainSelector)
    val tickerGUI = Reactive<String>("") // Where to show the crypto's ticker
    val balanceGUI = Reactive<String>("")
    val unconfirmedBalanceGUI = Reactive<String>("")
    val infoGUI = Reactive<String>("")

    var encodedPin: ByteArray? = loadEncodedPin()

    @Volatile
    var started = false  // Have the cnxnmgr and blockchain services been started or are we in initialization?

    //? Was the PIN entered properly since the last 15 second sleep?
    var pinEntered = false

    var currentReceive: PayDestination? = null //? This receive address appears on the main screen for quickly receiving coins

    /** Current exchange rate between this currency (in this account's default unit -- NOT the finest unit or blockchain unit) and your selected fiat currency.
     * -1 means that the exchange rate cannot be determined */
    var fiatPerCoin: BigDecimal = CurrencyDecimal(-1)
        set(value) {
            _fiatPerCoinState.value = value
            field = value // 'field' refers to the property itself
        }
    private val _fiatPerCoinState = MutableStateFlow(fiatPerCoin)
    val fiatPerCoinObservable: StateFlow<BigDecimal> = _fiatPerCoinState

    //? Current bch balance (cached from accessing the wallet), in the display units
    var balance: BigDecimal = CurrencyDecimal(0)
    var unconfirmedBalance: BigDecimal = CurrencyDecimal(0)
    var confirmedBalance: BigDecimal = CurrencyDecimal(0)

    //? specify how quantities should be formatted for display
    val cryptoFormat = NexaFormat
    val cryptoInputFormat = DecimalFormat("##########.##")  // I can't handle commas in field entry

    /** This is a common account display descriptor it returns "<account name> on <blockchain>", e.g. "myaccount on nexa" */
    val nameAndChain: String
        get() { return name + " " + i18n(S.onBlockchain) + " " + chainToURI[chain.chainSelector] }


    val wallet: Bip44Wallet = if (chainSelector == null)  // Load existing account
    {
        try
        {
            loadAccountFlags()
        } catch (e: DataMissingException)
        {
            // support older wallets by allowing empty account flags
        }
        LogIt.info(sourceLoc() + " " + ": Loading wallet " + name)
        val t = try {
            Bip44Wallet(walletDb!!, name)
        }  // Load a saved wallet
        catch (e:Exception)
        {
            LogIt.error("exception creating wallet: $e")
            throw e
        }
        LogIt.info(sourceLoc() + " " + ": Loaded wallet " + name)
        val stats = t.statistics()
        LogIt.info(sourceLoc() + " " + name + ": Used Addresses: " + stats.numUsedAddrs + " Unused Addresses: " + stats.numUnusedAddrs + " Num UTXOs: " + stats.numUnspentTxos + " Num wallet events: " + t.numTx())
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

    var cnxnMgr: CnxnMgr = WallyGetCnxnMgr(wallet.chainSelector, name, false)
    var chain: Blockchain = GetBlockchain(wallet.chainSelector, cnxnMgr, chainToURI[wallet.chainSelector], false) // do not start right away so we can configure exclusive/preferred no


    /** A string denoting this wallet's currency units.  That is, the units that this wallet should use in display, in its BigDecimal amount representations, and is converted to and from in fromFinestUnit() and toFinestUnit() respectively */
    val currencyCode: String = chainToDisplayCurrencyCode[wallet.chainSelector]!!

    var assets = mutableMapOf<GroupId, AssetPerAccount>()
    val assetTransferList = mutableListOf<GroupId>()

    // How to abort a fastforward (and its happening if non-null)
    var fastforward:Objectify<Boolean>? = null
    var fastforwardStatus:String? = null

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

        wallet.usesChain(chain)
        if (autoInit) later { asyncInit(startHeight, startDate) }
    }

    fun asyncInit(startHeight: Long?, startDate: Long?)
    {
        LogIt.info(sourceLoc() + name + ": wallet connect blockchain ${chain.name}")
        wallet.startChain(startHeight, startDate)
        LogIt.info(sourceLoc() + name + ": wallet blockchain ${chain.name} connection completed")
        wallet.fillReceivingWithRetrieveOnly()
        wallet.prepareDestinations(2, 2)  // Make sure that there is at least a few addresses before we hook into the network
        if (chainSelector != ChainSelector.NEXA)  // no fiat price for nextchain
        {
            val SatPerDisplayUnit = CurrencyDecimal(SATperUBCH)
            wallet.spotPrice = { currencyCode ->
                try
                {
                    assert(currencyCode == fiatCurrencyCode)
                    fiatPerCoin * CurrencyDecimal(SATperBCH) / SatPerDisplayUnit
                }
                catch (e: ArithmeticException)
                {
                    BigDecimal.ZERO
                }
            }
            // Tell the wallet layer how to get pricing info
            wallet.historicalPrice = { currencyCode: String, epochSec: Long -> historicalUbchInFiat(currencyCode, epochSec) }
        }

        // Tell the net layer how to get potential electrum nodes
        (cnxnMgr as MultiNodeCnxnMgr).getElectrumServerCandidate = { this.getElectrumServerOn(it) }

        setBlockchainAccessModeFromPrefs()
        loadAccountAddress()
        constructAssetMap()
    }

    /** Save the PIN of an account to the database
     * @param epin must be the ENCODED (not plaintext) pin */
    fun saveAccountPin(epin: ByteArray)
    {
        walletDb?.set("accountPin_" + name, epin)
    }

    @Suppress("UNUSED_PARAMETER")
    fun start()
    {
        later {
            if (!started)
            {
                LogIt.info(sourceLoc() + " " + name + ": Account startup: starting threads")
                cnxnMgr.start()
                chain.start()
                started = true
                // Set all the underlying change callbacks to trigger the account update
                wallet.setOnWalletChange { onChange() }
                wallet.blockchain.onChange.add({ onChange() })
                wallet.blockchain.net.changeCallback.add({ _, _ -> onChange() })
            }
        }
    }

    /**
     * This can be called either when the app as been paused, or early during app initialization
     * so we need to check to see if the is an actual resume-after-pause, or an initial startup
     */
    fun onResume()
    {
        if (started)
        {
            LogIt.info(sourceLoc() + " " + name + ": Account resuming: Restarting threads if needed")
            wallet.restart()
            wallet.chainstate?.chain?.restart()
            wallet.chainstate?.chain?.net?.restart()
        }
        else
        {
            LogIt.warning(sourceLoc() + " " + name + ": Account resuming but was not yet started")
        }
    }

    var genericElectrumNodeReqCount = 0 // So when we increment first thing, we end up at 0
    private fun getElectrumServerOn(cs: ChainSelector):IpPort
    {
        val name = chainToURI[cs]
        val excl = prefDB.getBoolean(name + "." + EXCLUSIVE_NODE_SWITCH, false)
        val pref = prefDB.getBoolean(name + "." + PREFER_NODE_SWITCH, false)

        // If we are in exclusive mode, or in preferred mode, once every 4 attempts, try our configured nodes
        if (excl || pref)
        {
            // Return our configured node if we have one
            val nodeStr = prefDB.getString(name + "." + CONFIGURED_NODE, null)
            if (nodeStr != null && nodeStr.isNotBlank() && nodeStr.isNotEmpty())
            {
                val nodes = nodeStr.splitIntoSet().toTypedArray()
                if (nodes.size > 0)
                {
                    // In the preference case, after going thru all preferred choices,
                    // drop through to a standard choice, setting the count back to 0
                    if (pref && genericElectrumNodeReqCount>=nodes.size)
                    {
                        genericElectrumNodeReqCount = 0
                    }
                    else  // otherwise grab a node from the preference list
                    {
                        val node = nodes[genericElectrumNodeReqCount % nodes.size]
                        val ipport = splitIpPort(node, DefaultElectrumTCP[cs] ?: -1)
                        if (ipport.ip.isNotEmpty() && ipport.ip.isNotBlank())
                        {
                            genericElectrumNodeReqCount++
                            return ipport
                        }
                    }
                }
            }
            if (excl) throw ElectrumNoNodesException()
        }
        genericElectrumNodeReqCount++
        return ElectrumServerOn(cs)
    }

    /** Get the locking PIN from storage */
    fun loadEncodedPin(): ByteArray?
    {
        val db = walletDb
        if (db != null)
        {
            try
            {
                val storedEpin = db.get("accountPin_" + name)
                if (storedEpin.size == 1 && storedEpin[0] == 0.toByte()) return null // Bug workaround: SQLDelight crashes on ios with 0-length arrays on iOS
                if (storedEpin.size > 0) return storedEpin
                return null
            }
            catch (e: Exception)
            {
                LogIt.info("DB missing PIN for: " + name + ". " + e.message)
            }
        }
        return null
    }

    /** Check the PIN of an account, return 1 if account unlocked else 0 & update unlocked status */
    fun submitAccountPin(pin: String): Int
    {
        if (encodedPin == null) return 0
        val epin = try
        {
            EncodePIN(name, pin)
        }
        catch (e: Exception) {
            LogIt.error(e.message ?: "Error in submitAccountPin")
            return 0
        }
        /* TODO: Should InvalidKeySpecException be ported to common kotlin?
        catch (e: InvalidKeySpecException)  // ignore invalid PIN, it can't unlock any wallets
        {
            LogIt.info("user entered invalid PIN")
            return 0
        }
         */

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

    /** Set access to the underlying blockchain (exclusive, preferred, or neither) based on the chosen preferences */
    fun setBlockchainAccessModeFromPrefs()
    {
        val cs = chain.chainSelector
        val chainName = chainToURI[cs]
        val exclusiveNode: String? = if (prefDB.getBoolean(chainName + "." + EXCLUSIVE_NODE_SWITCH, false)) prefDB.getString(chainName + "." + CONFIGURED_NODE, null) else null
        val preferredNode: String? = if (prefDB.getBoolean(chainName + "." + PREFER_NODE_SWITCH, false)) prefDB.getString(chainName + "." + CONFIGURED_NODE, null) else null

        // If I prefer an exclusive connection, then start up that way
        if (exclusiveNode != null)
        {
            LogIt.info(sourceLoc() + chain.name + ": Exclusive node mode")
            try
            {
                val nodeSet:Set<String> = exclusiveNode.splitIntoSet()
                cnxnMgr.exclusiveNodes(nodeSet)
            }
            catch (e: Exception)
            {
            } // bad IP:port data
        }
        // If I have a preferred connection, then start up that way
        if (preferredNode != null)
        {
            LogIt.info(sourceLoc() + chain.name + ": Preferred node mode")
            try
            {
                val nodeSet:Set<String> = preferredNode.splitIntoSet()
                cnxnMgr.preferNodes(nodeSet)
            }
            catch (e: Exception)
            {
            } // bad IP:port data provided by user
        }
    }

    /** Is this account currently visible to the user */
    val visible: Boolean
        get()
        {
            if ((encodedPin != null) && ((flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN) > 0UL) && !pinEntered) return false
            return true
        }

    /** Can this account be locked */
    val lockable: Boolean
        get()
        {
            return (encodedPin != null)   // If there is no PIN, can't be locked
        }

    /** Is this account currently locked */
    val locked: Boolean
        get()
        {
            if (encodedPin == null) return false  // Is never locked if there is no PIN
            return (!pinEntered)
        }

    /** Returns true if this account has unspent assets (grouped UTXOs) in it */
    fun hasAssets(): Boolean
    {
        var ret = false

        // TODO switch to a find function
        wallet.forEachTxo { sp ->
            if ((!ret) && sp.isUnspent)
            {
                val grp = sp.groupInfo()
                if ((grp != null) && !grp.isAuthority())  // TODO not dealing with authority txos in Wally mobile
                {
                    ret = true
                }
            }
            ret// stop looking as soon as we find one
        }
        return ret
    }

        /** Adds this asset to the list of assets to be transferred in the next send
         * Send the quantity *in finest units* */
    fun addAssetToTransferList(a: GroupId, amt: BigDecimal): Boolean
    {
        val asset = assets.get(a)
        if (asset == null) // you can't add an asset to the xfer list that you don't even have
        {
            return false
        }
        asset.editableAmount = amt
        if (assetTransferList.contains(a)) return false
        assetTransferList.add(a)
        return true
    }

    /** Clear all assets held by this account from the transfer list */
    fun clearAssetTransferList():Int
    {
        val ret = assetTransferList.size
        for (i in assets)
        {
            i.value.editableAmount = null
        }
        assetTransferList.clear()
        return ret
    }


    /** Constructs a map of assets held by this account.
     * @param getEc if null, the asset map will be constructed rapidly without gathering asset information from the internet, otherwise the returned electrumClient will be used to gather asset info
     */
    fun constructAssetMap(getEc: (() -> ElectrumClient)? = null)
    {
        val am = wallyApp?.assetManager
        if (am == null) return

        // LogIt.info(sourceLoc() + name + ": Construct assets")
        val ast = mutableMapOf<GroupId, GroupInfo>()
        wallet.forEachTxo { sp ->
            if (sp.isUnspent)
            {
                // TODO: this is a workaround for a bug where the script chain is incorrect
                if (sp.priorOutScript.chainSelector != sp.chainSelector)
                {
                    // LogIt.warning("BUG fixup: Script chain is ${sp.priorOutScript.chainSelector} but chain is ${sp.chainSelector}")
                    sp.priorOutScript = SatoshiScript(sp.chainSelector, sp.priorOutScript.type, sp.priorOutScript.flatten())
                }

                val grp = sp.groupInfo()
                if (grp != null)
                {
                    // LogIt.info(sourceLoc() + name + ": unspent asset ${grp.groupId.toHex()}")
                    if (!grp.isAuthority())  // TODO not dealing with authority txos in Wally mobile
                    {
                        val gi: GroupInfo? = ast[grp.groupId]
                        if (gi != null) gi.tokenAmt += grp.tokenAmt
                        else ast[grp.groupId] = grp
                    }
                }
            }
            false
        }

        // Rather than clearing the entire asset dictionary and adding the existing ones,
        // we'll add the existing ones and then remove any that no longer exist.
        // This will ensure that the asset page doesn't suddenly go blank if this process happens right when
        // the user is looking at it.

        // Check if this asset is new, and if so start grabbing the data for all assets (asynchronously)
        // otherwise update the existing entry for amount changes
        for (asset in ast.values)
        {
            // If we don't have it at all, add it to our dictionary
            val assetInfo = am.track(asset.groupId, getEc)
            assets[asset.groupId] = AssetPerAccount(asset, assetInfo)
        }
        // Now remove any assets that are no longer in the wallet
        val curKeys = assets.keys.toList()
        for (assetKey in curKeys)
        {
            if (!(assetKey in ast)) assets.remove(assetKey)
        }
    }

    fun loadAccountAddress()
    {
        val wdb = walletDb
        if (wdb != null)
        {
            try
            {
                val ser = wdb.get("accountAddress_" + name)
                if (ser.size != 0)
                {
                    currentReceive = wallet.walletDestination(PayAddress(ser.decodeToString()))
                }
            }
            catch (e: DataMissingException)
            {
                LogIt.error(e.message ?: "loadAccountAddress:DataMissingException")
                // its fine we'll grab a new one
            }
        }

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

    /** Return a web URL that will provide more information about this address */
    fun addressInfoWebUrl(address: String?): String?
    {
        if (address == null) return null
        if (wallet.chainSelector == ChainSelector.BCH)
            return "https://explorer.bitcoinunlimited.info/address/" + address
        if (wallet.chainSelector == ChainSelector.NEXATESTNET)
            return "http://testnet-explorer.nexa.org/address/" + address
        if (wallet.chainSelector == ChainSelector.NEXA)
            return "http://explorer.nexa.org/address/" + address
        return null
    }

    /** Convert the default display units to the finest granularity of this currency.  For example, mBCH to Satoshis */
    fun toFinestUnit(amount: BigDecimal): Long
    {
        val ret:Long = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET ->
                (amount*CurrencyDecimal(SATperNEX)).toLong()

            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> (amount* CurrencyDecimal(SATperUBCH)).toLong()
        }
        return ret
    }

    //? Convert the finest granularity of this currency to the default display unit.  For example, Satoshis to mBCH
    fun fromFinestUnit(amount: Long): BigDecimal
    {
        val factor = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> SATperNEX
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> SATperUBCH
        }
        val ret = CurrencyDecimal(amount) / factor.toBigDecimal()
        return ret
    }

    /** Convert a value in the wallet's display currency code unit into its primary unit. The "primary unit" is the generally accepted currency unit, AKA "BCH" or "BTC". */
    fun toPrimaryUnit(qty: BigDecimal): BigDecimal
    {
        val factor = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> 1
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> 1000000
        }
        return qty / factor.toBigDecimal()
    }

    /** Convert a value in the wallet's display currency code unit into its primary unit. The "primary unit" is the generally accepted currency unit, AKA "BCH" or "BTC". */
    fun fromPrimaryUnit(qty: BigDecimal): BigDecimal
    {
        val factor = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> 1
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> 1000000
        }
        return qty * factor.toBigDecimal()
    }

    //? Convert the passed quantity to a string in the decimal format suitable for this currency
    fun format(qty: BigDecimal): String
    {
        return when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> NexaFormat.format(qty)
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> uBchFormat.format(qty)
        }
    }

    fun loadAccountFlags()
    {
        val wdb = walletDb
        if (wdb != null)
        {
            val serFlags = wdb.get("accountFlags_" + name)
            val ser = BCHserialized(serFlags, SerializationType.NETWORK)
            flags = ser.deuint32().toULong()
        }
    }

    fun saveAccountFlags()
    {
        walletDb?.set("accountFlags_" + name, BCHserialized.uint32(flags.toLong()).toByteArray())
    }

    // Load the exchange rate
    fun getXchgRates(fiatCurrencyCode: String)
    {
        if (chain.chainSelector == ChainSelector.NEXA)
        {
            if (fiatCurrencyCode == "USD")
            {
                NexInFiat(fiatCurrencyCode) { fiatPerCoin = CurrencyDecimal(it) }
            }
            else fiatPerCoin = CURRENCY_NEG1  // Indicates that the exchange rate is unavailable
            return
        }

        if (chain.chainSelector == ChainSelector.BCH)
        {
            UbchInFiat(fiatCurrencyCode) { v: BigDecimal ->
                fiatPerCoin = v
            }
        }

        fiatPerCoin = -1.toBigDecimal()  // Indicates that the exchange rate is unavailable
        return
    }

    /** Completely delete this wallet, rendering any money you may have in it inaccessible unless the wallet is restored from backup words
     */
    fun delete()
    {
        currentReceive = null
        wallet.stop()
        walletDb = null
        wallet.delete()
        balance = BigDecimal.ZERO
        unconfirmedBalance = BigDecimal.ZERO
    }

    fun changeAsyncProcessing()
    {
        try
        {
            // Update our cache of the balances
            unconfirmedBalance = fromFinestUnit(wallet.unconfirmedBalanceDwim)
            confirmedBalance = fromFinestUnit(wallet.balanceConfirmed)
            balance = fromFinestUnit(wallet.balance)
        }
        catch (e: WalletDisconnectedException)
        {
            // I cannot update the balance if the wallet is not connected, but it will update once the connected so benign
        }
    }

    /** This is called by the underlying layers whenever something in the wallet has changed */
    fun onChange(force: Boolean = false)
    {
        changeAsyncProcessing()
        onChanged(this, force)
        constructAssetMap()
    }

    /**
     * Common implementation of onUpdateReceiveInfo from androidMain
     */
    fun onUpdatedReceiveInfoCommon(refresh: ((String) -> Unit)): Unit
    {
        fun genNewAddress()
        {
            val ret = wallet.getNewDestination()
            currentReceive = ret
            saveAccountAddress()
            refresh.invoke(ret.address.toString())
        }

        val cr = currentReceive
        if (cr == null)  later { genNewAddress() }
        else
        {
            var addr: PayAddress? = cr.address

            if (addr != null)
            {
                // If we have an address, then if re-use is true don't get another one
                if ((flags and ACCOUNT_FLAG_REUSE_ADDRESSES) > 0U)
                    refresh.invoke(addr.toString())
                // Otherwise get another one if our balance on this address is nonzero
                else
                {
                    addr.let {
                        later {
                            if (wallet.getBalanceIn(it) > 0)
                                genNewAddress()
                            else
                                refresh.invoke(addr.toString())
                        }
                    }
                }
            }
            else later { genNewAddress() }
        }
    }

    fun saveAccountAddress()
    {
        val wdb = walletDb
        if (wdb != null)
        {
            later { wdb.set("accountAddress_" + name, (currentReceive?.address?.toString() ?: "").toByteArray()) }
        }
    }
}

expect fun onChanged(account: Account, force: Boolean = false)

fun containsAccountWithName(accounts: List<Account>, name: String): Boolean
{
    for (acc in accounts)
    {
        if (acc.name == name)
            return true
    }
    return false
}
