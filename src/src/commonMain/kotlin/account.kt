package info.bitcoinunlimited.www.wally

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.ui.receivedNexaIsPlaying
import info.bitcoinunlimited.www.wally.ui.triggerAccountsChanged
import kotlin.concurrent.Volatile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Transient
import org.nexa.assets.AssetInfo
import org.nexa.assets.AssetPerAccount
import org.nexa.assets.triggerAssetCheck
import org.nexa.assets.triggerAssetCheckOnBlock
import org.nexa.libnexakotlin.*
import org.nexa.threads.Mutex
import org.nexa.threads.iMutex
import org.nexa.threads.millisleep
import kotlin.random.Random

/** Account flags: No flag */
const val ACCOUNT_FLAG_NONE = 0UL
/** Account flags: hide this account until pin is entered */
const val ACCOUNT_FLAG_HIDE_UNTIL_PIN = 1UL
/** Account flags: User affirms they've backed up the recovery secret */
const val ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY = 2UL
/** Account flags: Reuse addresses rather than generate a new address each time */
const val ACCOUNT_FLAG_REUSE_ADDRESSES = 4UL

const val RETRIEVE_ONLY_ADDITIONAL_ADDRESSES = 10

/** constructAssetMap publishes a StateFlow snapshot every this many newly added assets so the UI
 *  updates progressively on wallets with many NFTs without paying per-asset re-sort and list-
 *  rebuild cost on every change. Updates to existing entries and removals don't count toward the
 *  batch -- they ride along on the next batched or final emit. */
const val ASSET_EMIT_BATCH = 50

/** Do not warn about not having backed up the recovery key until balance exceeds this amount (satoshis) */
const val MAX_NO_RECOVERY_WARN_BALANCE = 1000000 * 10


private val LogIt = GetLog("BU.wally.Account")

/** You can prefix every database (to isolate testing from production, for example) with this string */
var dbPrefix = ""
/** Currently selected fiat currency code */
var fiatCurrencyCode: String = "USD"

/** What file name wally wallet uses to open accounts */
fun wallyAccountDbFileName(name: String):String
{
    return dbPrefix + name + "_wallet"
}

// Note that this returns the last time and block when a new address was FIRST USED, so this may not be what you wanted
data class HDActivityBracket(val startTime: Long, val startBlockHeight: Int, val lastTime: Long, val lastBlockHeight: Int, val lastAddressIndex: Int)

expect fun EncodePIN(actName: String, pin: String, size: Int = 64): ByteArray

fun CfgCnxnMgr(cm:CnxnMgr)
{
    if (cm.chainSelector == ChainSelector.NEXA)
    {
        laterJob {
            cm.add("nexa.wallywallet.org", NexaPort, Random.nextInt(90,101), true)
            cm.add("p2p.wallywallet.org", NexaPort, Random.nextInt(90,101), true)
            cm.add("usa.wallywallet.org", NexaPort, Random.nextInt(90,101), true)
            cm.add("india.wallywallet.org", NexaPort, Random.nextInt(90,101), true)
            cm.add("eu.wallywallet.org", NexaPort, Random.nextInt(90,101), true)
            cm.add("w.nexa.org", NexaPort, Random.nextInt(90,101), true)
        }
    }
}

/** Given a string, this cleans up extra spaces and returns a list of the actual words */
fun processSecretWords(secretWords: String): List<String>
{
    val txt: String = secretWords.trim().lowercase()
    val wordSplit = txt.split(' ','\n','\t')
    val junkDropped = wordSplit.filter { it.length > 0 }
    return junkDropped
}

fun isValidOrEmptyRecoveryPhrase(words: List<String>): Boolean
{
    if(words.isEmpty()) return true
    if (words.size != 12)
    {
        return false
    }
    val incorrectWords = bip39InvalidWords(words)
    if (!incorrectWords.isEmpty()) return false
    return validBip39Checksum(words.toTypedArray())
}

/**
 * Account interface — exposes the public surface of an account so that tests can
 * substitute a [dev.mokkery.mock] in places where a real [AccountImpl] would be too
 * heavy to instantiate (the impl's primary constructor opens the wallet DB and spins
 * up wallet/blockchain state).
 *
 * Production code should keep using `Account` everywhere as a type; only direct
 * constructor calls need to use `AccountImpl(...)`.
 */
interface Account
{
    // ----- Identity / config -----
    val name: String
    var flags: ULong
    val prefDB: SharedPreferences

    // ----- Concurrency -----
    val access: iMutex
    val handler: CoroutineExceptionHandler

    // ----- Persistence / lifecycle state -----
    var walletDb: WalletDatabase?
    var started: Boolean
    var pinEntered: Boolean
    var encodedPin: ByteArray?

    // ----- Receive address -----
    var currentReceive: PayDestination?
    val currentReceiveObservable: MutableStateFlow<PayDestination?>

    // ----- Sync / fiat -----
    val syncedDate: MutableStateFlow<Long>
    var fiatPerCoin: BigDecimal
    val fiatPerCoinObservable: StateFlow<BigDecimal>

    // ----- Display formats -----
    val cryptoFormat: DecimalFormat
    val cryptoInputFormat: DecimalFormat
    val nameAndChain: String

    // ----- Wallet / chain -----
    val wallet: Bip44Wallet
    var balance: BigDecimal?
    val balanceState: StateFlow<BigDecimal?>
    var unconfirmedBalance: MutableStateFlow<BigDecimal?>
    var confirmedBalance: MutableStateFlow<BigDecimal?>
    val cnxnMgr: CnxnMgr
    var chain: Blockchain
    val currencyCode: String

    // ----- Assets -----
    var assets: Map<GroupId, AssetPerAccount>
    val assetsObservable: StateFlow<Map<GroupId, AssetPerAccount>>
    val assetTransferList: MutableList<GroupId>

    // ----- Fastforward -----
    var fastforward: Objectify<Boolean>?
    var fastforwardStatus: String?
    val fastForwardStatusState: StateFlow<String?>

    // ----- Wallet change callbacks -----
    val cb1: (Wallet, List<TransactionHistory>?) -> Unit
    var wCb: Int?
    var blkCb: Int?
    var netCb: Int?

    // ----- Visibility / lock state -----
    val visible: Boolean
    val lockable: Boolean
    val locked: Boolean

    // ----- Misc state -----
    var genericElectrumNodeReqCount: Int

    // ----- wallet / chain -----

    fun getRecoveryPhrase(): String

    // ----- Lifecycle / async -----
    fun asyncInit(startHeight: Long?, startDate: Long?)
    fun saveAccountPin(epin: ByteArray?)
    fun start()
    fun removeChangeHandlers()
    fun installChangeHandlers()
    fun onResume()
    fun loadEncodedPin(): ByteArray?
    fun submitAccountPin(pin: String): Int
    fun setBlockchainAccessModeFromPrefs()

    // ----- Assets -----
    fun hasAssets(): Boolean
    fun addAssetToTransferList(a: GroupId, amt: BigDecimal): Boolean
    fun clearAssetTransferList(): Int
    fun constructAssetMap(getEc: (() -> ElectrumClient)? = null)
    fun assetList(): MutableList<AssetPerAccount>

    // ----- Address / receive -----
    fun loadAccountAddress()
    fun saveAccountAddress()

    // ----- URLs / formatting / unit conversion -----
    fun transactionInfoWebUrl(txHex: String?): String?
    fun addressInfoWebUrl(address: String?): String?
    fun toFinestUnit(amount: BigDecimal): Long
    fun fromFinestUnit(amount: Long): BigDecimal
    fun toPrimaryUnit(qty: BigDecimal): BigDecimal
    fun fromPrimaryUnit(qty: BigDecimal): BigDecimal
    fun format(qty: BigDecimal): String

    // ----- Persistence helpers -----
    fun loadAccountFlags()
    fun saveAccountFlags()

    // ----- Mutators -----
    fun delete()
    fun changeAsyncProcessing()
    fun onChange(force: Boolean = false)
}

class AccountImpl(
  override val name: String,
  override var flags: ULong = ACCOUNT_FLAG_NONE,
  chainSelector: ChainSelector? = null,
  secretWords: String? = null,
  startDate: Long? = null, //* Where to start looking for transactions
  startHeight: Long? = null, //* block height of first activity
  autoInit: Boolean = true, /** Automatically begin the asynchronous initialization phase */
  retrieveOnlyActivity: MutableList<Pair<Bip44Wallet.HdDerivationPath, HDActivityBracket>>? = null,  //* jam in other derivation paths to grab coins from (but use addresses of) (if new account)
  override val prefDB: SharedPreferences = getSharedPreferences(TEST_PREF + PREFERENCE_FILE_NAME, PREF_MODE_PRIVATE),
  db: WalletDatabase? = null
) : Account
{
    init {
        LogIt.info(sourceLoc() + ": Creating Account $name")
    }
    override val access = Mutex("actMut")
    override val handler = CoroutineExceptionHandler {
        _, exception -> LogIt.error("Caught in Account CoroutineExceptionHandler: $exception")
    }
    override var walletDb: WalletDatabase? = db ?: openWalletDB(wallyAccountDbFileName(name), chainSelector)
    @Volatile
    override var started = false  // Have the cnxnmgr and blockchain services been started or are we in initialization?
    //? Was the PIN entered properly since the last 15 second sleep?
    override var pinEntered = false
    override var encodedPin: ByteArray? = loadEncodedPin()

    override var currentReceive: PayDestination? = null //? This receive address appears on the main screen for quickly receiving coins
        set(value) {
            currentReceiveObservable.value = value
            field = value
        }
    override val currentReceiveObservable: MutableStateFlow<PayDestination?> = MutableStateFlow(null)

    override val syncedDate = MutableStateFlow<Long>(0L)

    /** Current exchange rate between this currency (in this account's default unit -- NOT the finest unit or blockchain unit) and your selected fiat currency.
     * -1 means that the exchange rate cannot be determined */
    override var fiatPerCoin: BigDecimal = CurrencyDecimal(-1)
        set(value) {
            _fiatPerCoinState.value = value
            field = value // 'field' refers to the property itself
        }
    private val _fiatPerCoinState = MutableStateFlow(fiatPerCoin)
    override val fiatPerCoinObservable: StateFlow<BigDecimal> = _fiatPerCoinState

    //? specify how quantities should be formatted for display
    override val cryptoFormat = NexaFormat
    override val cryptoInputFormat = DecimalFormat("##########.##")  // I can't handle commas in field entry

    /** This is a common account display descriptor it returns "<account name> on <blockchain>", e.g. "myaccount on nexa" */
    override val nameAndChain: String
        get() { return name + " " + i18n(S.onBlockchain) + " " + chainToURI[chain.chainSelector] }

    init {
        LogIt.info(sourceLoc() + ": Wallet")
    }

    override val wallet: Bip44Wallet = if (chainSelector == null)  // Load existing account
    {
        try
        {
            loadAccountFlags()
        } catch (e: DataMissingException)
        {
            // support older wallets by allowing empty account flags
        }
        LogIt.info(sourceLoc() + ": Loading wallet " + name)
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
        laterJob {  // Wait for the network to come up, then check to see if my utxos are valid
            while(t.blockchain.net.size == 0) millisleep(1000U)
            t.checkUtxos()
        }
        t
    }
    else  // New account
    {
        LogIt.info(sourceLoc() + ": Creating new wallet")
        saveAccountFlags()
        if (secretWords == null)
            Bip44Wallet(walletDb!!, name, chainSelector, NEW_WALLET)   // New wallet
        else
            Bip44Wallet(walletDb!!, name, chainSelector, secretWords)  // Wallet recovery
    }

    //? Current balance (cached from accessing the wallet), in the display units
    override var balance: BigDecimal? = null
        set(value)
        {
            _balanceState.value = value
            field = value
        }
    private val _balanceState = MutableStateFlow<BigDecimal?>(balance)
    override val balanceState = _balanceState.asStateFlow()

    override var unconfirmedBalance = MutableStateFlow<BigDecimal?>(null)
    override var confirmedBalance = MutableStateFlow<BigDecimal?>(null)

    // do not start right away so we can configure exclusive/preferred no
    override var chain: Blockchain = connectBlockchain(wallet.chainSelector) {
          CfgCnxnMgr(net)
    }

    override val cnxnMgr: CnxnMgr
        get()
        {
            return chain.net
        }

    /** A string denoting this wallet's currency units.  That is, the units that this wallet should use in display, in its BigDecimal amount representations, and is converted to and from in fromFinestUnit() and toFinestUnit() respectively */
    override val currencyCode: String = chainToDisplayCurrencyCode[wallet.chainSelector]!!

    init { LogIt.info(sourceLoc() + ": Assets") }

    override var assets = mapOf<GroupId, AssetPerAccount>()
        set(value) {
            _assetsState.value = value
            field = value
        }

    init { LogIt.info(sourceLoc() + ": Flows") }
    @Transient private val _assetsState = MutableStateFlow<Map<GroupId, AssetPerAccount>>(mapOf<GroupId, AssetPerAccount>())
    @Transient override val assetsObservable = _assetsState.asStateFlow()
    override val assetTransferList = mutableListOf<GroupId>()

    // How to abort a fastforward (and its happening if non-null)
    override var fastforward:Objectify<Boolean>? = null
    override var fastforwardStatus:String? = null
        set(value) {
            _fastForwardStatusState.value = value
            field = value
        }
    private val _fastForwardStatusState = MutableStateFlow<String?>(null)
    override val fastForwardStatusState = _fastForwardStatusState.asStateFlow()

    init
    {
        LogIt.info(sourceLoc() + ": Connect blockchain to wallet")
        if (retrieveOnlyActivity != null)  // push in nonstandard addresses before we connect to the blockchain.
        {
            for (r in retrieveOnlyActivity)
            {
                assert(r.first.index == r.second.lastAddressIndex) // Caller should have properly set this.  Doublecheck.
                val tmp = r.first
                tmp.index += RETRIEVE_ONLY_ADDITIONAL_ADDRESSES
                wallet.retrieveOnlyDerivationPaths.add(tmp)
            }
        }
        //LogIt.info(sourceLoc() + ": Uses chain")
        wallet.usesChain(chain)

        if (autoInit)
        {
            //LogIt.info(sourceLoc() + ": Launch account asyncInit job")
            tlater {
                //LogIt.info(sourceLoc() + ": account asyncInit is running")
                asyncInit(startHeight, startDate)
            }
            //LogIt.info(sourceLoc() + ": Job launch finished")
        }
        LogIt.info(sourceLoc() + ": Connect completed.  Account setup complete")
    }

    override fun asyncInit(startHeight: Long?, startDate: Long?)
    {
        LogIt.info(sourceLoc() + name + ": wallet connect blockchain ${chain.name}")
        loadAccountAddress()
        wallet.startChain(startHeight, startDate)
        LogIt.info(sourceLoc() + name + ": wallet blockchain ${chain.name} connection completed")
        wallet.fillReceivingWithRetrieveOnly()
        wallet.prepareDestinations(2, 2)  // Make sure that there is at least a few addresses before we hook into the network
        if (wallet.chainSelector != ChainSelector.NEXA)  // no fiat price for nextchain
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
        (cnxnMgr as MultiNodeCnxnMgr).getElectrumServerCandidate = { ch, excl, pref ->
            if (excl!=null && excl.isNotEmpty())
            {
                IpPort(excl.random().split(':').first(), DefaultElectrumTCP[ch] ?: DEFAULT_NEXA_TCP_ELECTRUM_PORT)
            }
            else if (pref!=null && pref.isNotEmpty())
            {
                val tmp = Random.nextInt()%(pref.size+1)
                if (tmp == pref.size) ElectrumServerOn(ch)
                else IpPort(pref.random().split(':').first(), DefaultElectrumTCP[ch] ?: DEFAULT_NEXA_TCP_ELECTRUM_PORT)
            }
            this.getElectrumServerOn(ch)
        }

        setBlockchainAccessModeFromPrefs()
        // Build the asset map on its own job so asyncInit returns promptly: with many NFTs the
        // walk reads .ai files per asset and (when an ElectrumClient is available) fetches each
        // one over the network. laterOneJob's name keying collapses any duplicate scheduling
        // from the AssetLoaderThread.
        laterOneJob("constructAssets-$name") { constructAssetMap() }
    }

    /** Save the PIN of an account to the database
     * @param epin must be the ENCODED (not plaintext) pin */
    override fun saveAccountPin(epin: ByteArray?)
    {
        val ep = epin ?: byteArrayOf()
        walletDb?.set("accountPin_" + name, ep)
    }

    // A transaction came in
    override val cb1: ((Wallet,List<TransactionHistory>?) -> Unit) =
      { w, txes ->
          if (txes!=null) for (txh in txes)
          {
              // A TDPP helper owns user-facing feedback for txs it submitted; skip the default
              // notifications for those idems but keep internal state updates via onChange() below.
              if (isTdppPending(txh.tx.idem) || txh.relatedTo["TDPP"] != null) continue

              // Only show received animation on unconfirmed or recent confirmed block, not for syncing blocks
              if ((txh.confirmedHeight == -1L) || (txh.confirmedHeight >= w.blockchain.curHeight-1))
              {
                  LogIt.info("Incoming Tx ${txh.confirmedHeight}")
                  /* If the change is not a rejected transaction, then see if we should play the receive animation */
                  val rejr: String? = txh.rejectedReason
                  if (rejr == null)
                  {
                      if (txh.incomingAmt - txh.outgoingAmt > 0)
                      {
                          receivedNexaIsPlaying.value = true
                      }
                  }
                  else
                  {
                      displayWarning(rejr)
                  }
              }
          }
          onChange()
      }
    @Volatile override var wCb: Int? = null
    @Volatile override var blkCb: Int? = null
    @Volatile override var netCb: Int? = null

    @Suppress("UNUSED_PARAMETER")
    override fun start()
    {
        if (!started)
        {
            installChangeHandlers()
            LogIt.info(sourceLoc() + " " + name + ": Account startup: starting threads")
            cnxnMgr.start()
            chain.start()
            started = true
        }
    }

    /** Stop underlying changes from updating the state of this account
     * This function is primarily used during test to prevent real events from overriding faked data.
     */
    override fun removeChangeHandlers()
    {
        wCb?.let { wallet.removeOnWalletChange(it); wCb = null }
        blkCb?.let { wallet.blockchain.onChange.remove(it); blkCb = null }
        netCb?.let { wallet.blockchain.net.changeCallback.remove(it); netCb = null }
    }

    /** Install handlers for underlying changes to update the state in this account.
     * These handlers are installed automatically upon account creation so you should not need to call this function.
     */
    override fun installChangeHandlers()
    {
        // Set all the underlying change callbacks to trigger the account update
        if (wCb==null) wCb = wallet.setOnWalletChange(cb1)
        if (blkCb == null) blkCb = wallet.blockchain.onChange.add({
            onChange()
            it.nearTip?.height?.let { triggerAssetCheckOnBlock(it) }  // When a new block comes in, we should retry any assets we are waiting for because blockchain sources may not provide the data
        })
        if (netCb == null) netCb = wallet.blockchain.net.changeCallback.add({ it ->
            if (it.first == CnxnMgr.Event.CONNECTED) triggerAssetCheck()
            onChange()
        })
    }

    /**
     * This can be called either when the app as been paused, or early during app initialization
     * so we need to check to see if the is an actual resume-after-pause, or an initial startup
     */
    override fun onResume()
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

    override var genericElectrumNodeReqCount = 0 // So when we increment first thing, we end up at 0
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
            if (excl) throw ElectrumNoNodesException(chain.chainSelector)
        }
        genericElectrumNodeReqCount++
        return ElectrumServerOn(cs)
    }

    /** Get the 12 word mnemonic key secret as a string */
    override fun getRecoveryPhrase(): String
    {
        return wallet.secretWords.getSecret().decodeUtf8()
    }

    /** Get the locking PIN from storage */
    override fun loadEncodedPin(): ByteArray?
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
    override fun submitAccountPin(pin: String): Int
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
    override fun setBlockchainAccessModeFromPrefs()
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
    override val visible: Boolean
        get()
        {
            if ((encodedPin != null) && ((flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN) > 0UL) && !pinEntered) return false
            return true
        }

    /** Can this account be locked */
    override val lockable: Boolean
        get()
        {
            return (encodedPin != null)   // If there is no PIN, can't be locked
        }

    /** Is this account currently locked */
    override val locked: Boolean
        get()
        {
            if (encodedPin == null) return false  // Is never locked if there is no PIN
            return (!pinEntered)
        }

    /** Returns true if this account has unspent assets (grouped UTXOs) in it */
    override fun hasAssets(): Boolean
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
            ret // stop looking as soon as we find one
        }
        return ret
    }

        /** Adds this asset to the list of assets to be transferred in the next send
         * Send the quantity *in finest units* */
    override fun addAssetToTransferList(a: GroupId, amt: BigDecimal): Boolean
    {
        return access.lock {
            val asset = assets.get(a)
            if (asset == null) // you can't add an asset to the xfer list that you don't even have
            {
                false
            }
            else
            {
                asset.editableAmount = amt
                if (assetTransferList.contains(a)) false
                else
                {
                    assetTransferList.add(a)
                    true
                }
            }
        }
    }

    /** Clear all assets held by this account from the transfer list */
    override fun clearAssetTransferList():Int
    {
        return access.lock {
            val ret = assetTransferList.size
            for (i in assets)
            {
                i.value.editableAmount = null
            }
            assetTransferList.clear()
            ret
        }
    }


    /** Constructs a map of assets held by this account.
     * @param getEc if null, the asset map will be constructed rapidly without gathering asset information from the internet, otherwise the returned electrumClient will be used to gather asset info
     */
    override fun constructAssetMap(getEc: (() -> ElectrumClient)?)
    {
        val am = wallyApp?.assetManager
        if (am == null) return

        // LogIt.info(sourceLoc() + name + ": Construct assets")
        val ast = mutableMapOf<GroupId, GroupInfo>()
        wallet.forEachUtxo { sp ->
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
                    if (gi != null)
                    {
                        gi.tokenAmount += grp.tokenAmount
                    }
                    else ast[grp.groupId] = grp
                }
            }
            false
        }

        // Update the asset map in place rather than clearing and rebuilding: this keeps any
        // per-asset UI state (e.g. send quantity) on entries that survive, and means the asset
        // page never blinks blank if the user is viewing it while we run.
        //
        // Build a local working copy outside access.lock so am.track() (disk read, plus a
        // synchronous electrum fetch when getEc != null) doesn't block other readers. Publish
        // a snapshot every ASSET_EMIT_BATCH new entries so a wallet with many NFTs renders
        // the first ones while the rest are still loading. Token-amount changes mutate in
        // place; removals plus any additions since the last batch flush in one final emit.
        val tmp = access.lock { assets.toMutableMap() }
        var dirtySinceEmit = false
        var addedSinceEmit = 0

        for (asset in ast.values)
        {
            val cur = tmp[asset.groupId]
            if (cur != null)
            {
                cur.groupInfo.tokenAmount = asset.tokenAmount  // in-place: preserve other per-asset state
                continue
            }
            val assetInfo = am.track(asset.groupId, getEc)  // disk read, plus electrum fetch when getEc != null
            tmp[asset.groupId] = AssetPerAccount(asset, assetInfo)
            dirtySinceEmit = true
            addedSinceEmit++
            if (addedSinceEmit >= ASSET_EMIT_BATCH)
            {
                access.lock { assets = tmp.toMap() }  // snapshot copy: we keep mutating tmp after this batch
                dirtySinceEmit = false
                addedSinceEmit = 0
            }
        }

        // Drop assets no longer present in the wallet, then publish one final snapshot covering
        // removals plus any additions since the last batch. Direct tmp assignment (not toMap)
        // is safe -- tmp is not mutated after this.
        access.lock {
            val iter = tmp.keys.iterator()
            while (iter.hasNext())
            {
                if (iter.next() !in ast)
                {
                    iter.remove()
                    dirtySinceEmit = true
                }
            }
            if (dirtySinceEmit) assets = tmp
        }
    }

    /** Return a list of assets held by this account */
    override fun assetList():MutableList<AssetPerAccount>
    {
        return access.lock {
            assets.values.toMutableList()
        }
    }

    override fun loadAccountAddress()
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
                getAndCacheWalletAddress()
            }
        }

    }

    /** Return a web URL that will provide more information about this transaction */
    override fun transactionInfoWebUrl(txHex: String?): String?
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
    override fun addressInfoWebUrl(address: String?): String?
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
    override fun toFinestUnit(amount: BigDecimal): Long
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
    override fun fromFinestUnit(amount: Long): BigDecimal
    {
        val factor = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> SATperNEX
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> SATperUBCH
        }
        val ret = NexaDecimal(amount) / factor.toBigDecimal()
        return ret
    }

    /** Convert a value in the wallet's display currency code unit into its primary unit. The "primary unit" is the generally accepted currency unit, AKA "BCH" or "BTC". */
    override fun toPrimaryUnit(qty: BigDecimal): BigDecimal
    {
        val factor = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> 1
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> 1000000
        }
        return qty / factor.toBigDecimal()
    }

    /** Convert a value in the wallet's display currency code unit into its primary unit. The "primary unit" is the generally accepted currency unit, AKA "BCH" or "BTC". */
    override fun fromPrimaryUnit(qty: BigDecimal): BigDecimal
    {
        val factor = when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> 1
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> 1000000
        }
        return qty * factor.toBigDecimal()
    }

    //? Convert the passed quantity to a string in the decimal format suitable for this currency
    override fun format(qty: BigDecimal): String
    {
        // TODO replace with NexaFormat when a new version of lnk is released
        val nexaFormat = DecimalFormat("##,###,###,###,##0.00")
        //LogIt.info("format ${qty.toPlainString()} -> ${nexaFormat.format(qty)}")
        return when (chain.chainSelector)
        {
            ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> nexaFormat.format(qty)
            ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> uBchFormat.format(qty)
        }
    }

    override fun loadAccountFlags()
    {
        val wdb = walletDb
        if (wdb != null)
        {
            val serFlags = wdb.get("accountFlags_" + name)
            val ser = BCHserialized(serFlags, SerializationType.NETWORK)
            flags = ser.deuint32().toULong()
        }
    }

    override fun saveAccountFlags()
    {
        walletDb?.set("accountFlags_" + name, BCHserialized.uint32(flags.toLong()).toByteArray())
    }

    // Load the exchange rate
    /*
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
     */

    /** Completely delete this wallet, rendering any money you may have in it inaccessible unless the wallet is restored from backup words
     */
    override fun delete()
    {
        removeChangeHandlers()
        currentReceive = null
        wallet.stop()
        wallet.delete(wallyAccountDbFileName(wallet.name))
        walletDb = null
        balance = null
        unconfirmedBalance.value = null
        confirmedBalance.value = null
    }

    init
    {
        laterJob("accountUpdate") {
            try
            {
                // Update our cache of the balances
                unconfirmedBalance.value = fromFinestUnit(wallet.unconfirmedBalanceDwim)
                confirmedBalance.value = fromFinestUnit(wallet.balanceConfirmed)
                balance = fromFinestUnit(wallet.balance)
            }
            catch (e: WalletDisconnectedException)
            {
                // I cannot update the balance if the wallet is not connected, but it will update once the connected so benign
            }
        }
    }

    override fun changeAsyncProcessing()
    {
        try
        {
            syncedDate.value = wallet.chainstate?.syncedDate ?: 0 // if no connected blockchain, its not synced.
            // Update our cache of the balances
            val newBalance = fromFinestUnit(wallet.balance)
            balance = newBalance
            unconfirmedBalance.value = fromFinestUnit(wallet.unconfirmedBalanceDwim)
            confirmedBalance.value = fromFinestUnit(wallet.balanceConfirmed)
            onUpdatedReceiveInfo()
        }
        catch (e: WalletDisconnectedException)
        {
        // I cannot update the balance if the wallet is not connected, but it will update once the connected so benign
        }
    }

    /** This is called by the underlying layers whenever something in the wallet has changed */
    override fun onChange(force: Boolean)
    {
        onetlater("accountChanged_${name}") {
            changeAsyncProcessing()
            triggerAccountsChanged(this)
        }
        onetlater("accountAssetMap_${name}") {
            constructAssetMap()
            triggerAccountsChanged(this)
        }
        //onChanged(this, force)  // calls changeAsyncProcessing
    }


    protected fun getAndCacheWalletAddress()
    {
        val ret = wallet.getCurrentDestination()  // Will pop forward but only if needed  //getNewDestination()
        currentReceive = ret
        saveAccountAddress()
    }

    /**
     * Common implementation of onUpdateReceiveInfo from androidMain
     */
    protected fun onUpdatedReceiveInfo(refresh: ((String) -> Unit)? = null): Unit
    {
        fun genNewAddress()
        {
            val ret = wallet.getCurrentDestination()  // Will pop forward but only if needed  //getNewDestination()
            currentReceive = ret
            saveAccountAddress()
            refresh?.invoke(ret.address.toString())
        }

        val cr = currentReceive
        if (cr == null)  tlater { genNewAddress() }
        else
        {
            val addr: PayAddress? = cr.address

            if (addr != null)
            {
                // If we have an address, then if re-use is true don't get another one
                if ((flags and ACCOUNT_FLAG_REUSE_ADDRESSES) > 0U)
                    refresh?.invoke(addr.toString())
                // Otherwise get another one if our balance on this address is nonzero
                else
                {
                    addr.let {
                        tlater {
                            if (wallet.getBalanceIn(it) > 0)
                                genNewAddress()
                            else
                                refresh?.invoke(addr.toString())
                        }
                    }
                }
            }
            else
            {
                LogIt.error(sourceLoc() +": Receiving Destination has no addres!!!")
                tlater { genNewAddress() }
            }
        }
    }

    override fun saveAccountAddress()
    {
        val wdb = walletDb
        if (wdb != null)
        {
            val addr = currentReceive?.address?.toString()
            tlater {
                if (addr != null)
                  wdb.set("accountAddress_" + name, addr.toByteArray())
            }
        }
    }
}

// expect fun onChanged(account: Account, force: Boolean = false)

fun containsAccountWithName(accounts: List<Account>, name: String): Boolean
{
    for (acc in accounts)
    {
        if (acc.name == name)
            return true
    }
    return false
}
