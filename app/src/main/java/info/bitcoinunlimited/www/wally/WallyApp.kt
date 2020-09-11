// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.view.View
import android.widget.TextView
import bitcoinunlimited.libbitcoincash.*
import java.lang.Exception
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.security.spec.InvalidKeySpecException
import java.util.logging.Logger
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

val SimulationHostIP = "10.0.2.2"
val LanHostIP = "192.168.1.100"

val LAST_RESORT_BCH_ELECTRS = "bch2.bitcoinunlimited.net" // "electrs.bitcoinunlimited.info"

private val LogIt = Logger.getLogger("bitcoinunlimited.app")

open class PrimaryWalletInvalidException() : BUException("Primary wallet not defined or currently unavailable", "not ready", ErrorSeverity.Abnormal)

var coinsCreated = false

/** Currently selected fiat currency code */
var fiatCurrencyCode: String = "USD"

/** Database name prefix, empty string for mainnet, set for testing */
var dbPrefix = if (RunningTheTests()) "guitest_" else if (REG_TEST_ONLY == true) "regtest_" else ""

val SupportedBlockchains = if (INCLUDE_NEXTCHAIN)
    mapOf("BCH (Bitcoin Cash)" to ChainSelector.BCHMAINNET,
        "NXC (NextChain)" to ChainSelector.NEXTCHAIN,
        "TBCH (Testnet Bitcoin Cash)" to ChainSelector.BCHTESTNET,
        "RBCH (Regtest Bitcoin Cash)" to ChainSelector.BCHREGTEST)
else
    mapOf("BCH (Bitcoin Cash)" to ChainSelector.BCHMAINNET, "TBCH (Testnet Bitcoin Cash)" to ChainSelector.BCHTESTNET, "RBCH (Regtest Bitcoin Cash)" to ChainSelector.BCHREGTEST)

val ChainSelectorToSupportedBlockchains = SupportedBlockchains.entries.associate { (k, v) -> v to k }

// What is the default wallet and blockchain to use for most functions (like identity)
val PRIMARY_WALLET = if (REG_TEST_ONLY) "mRBCH" else "mBCH"

/** incompatible changes, extra fields added, fields and field sizes are the same, but content may be extended (that is, addtl bits in enums) */
val WALLY_DATA_VERSION = byteArrayOf(1,0,0)

var walletDb: KvpDatabase? = null

const val ACCOUNT_FLAG_NONE = 0UL
const val ACCOUNT_FLAG_HIDE_UNTIL_PIN = 1UL


fun MakeNewWallet(name: String, chain: ChainSelector): Bip44Wallet
{
    if (chain == ChainSelector.BCHREGTEST)
        return Bip44Wallet(walletDb!!, name, chain, "trade box today light need route design birth turn insane oxygen sense")
    if (chain == ChainSelector.BCHTESTNET)
        return Bip44Wallet(walletDb!!, name, chain, NEW_WALLET)
    //return Bip44Wallet(currencyCode, chain, "")
    if (chain == ChainSelector.BCHMAINNET)
        return Bip44Wallet(walletDb!!, name, chain, NEW_WALLET)
    //return Bip44Wallet(currencyCode, chain, "")
    throw BUException("invalid chain selected")
}

/** Store the PIN encoded.  However, note that for short PINs a dictionary attack is very feasible */
fun EncodePIN(actName: String, pin:String, size:Int=64):ByteArray
{
    val salt = "wally pin " + actName
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    val secretkey = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 2048, 512)
    val seed = skf.generateSecret(secretkey)
    return seed.encoded.slice(IntRange(0,size-1)).toByteArray()
}

var cnxnMgrs: MutableMap<ChainSelector, CnxnMgr> = mutableMapOf()
fun GetCnxnMgr(chain: ChainSelector, name: String? = null): CnxnMgr
{
    synchronized(cnxnMgrs)
    {
        LogIt.info(sourceLoc() + " " + "Get Cnxn Manager")
        val existing = cnxnMgrs[chain]
        if (existing != null) return existing

        val result = when (chain)
        {
            ChainSelector.BCHTESTNET -> MultiNodeCnxnMgr(name ?: "TBCH", ChainSelector.BCHTESTNET, arrayOf("testnet-seed.bitcoinabc.org"))
            ChainSelector.BCHMAINNET -> MultiNodeCnxnMgr(name ?: "BCH", ChainSelector.BCHMAINNET, arrayOf("seed.bitcoinunlimited.net", "btccash-seeder.bitcoinunlimited.info"))
            ChainSelector.BCHREGTEST -> MultiNodeCnxnMgr(name ?: "RBCH", ChainSelector.BCHREGTEST, arrayOf(SimulationHostIP))
            ChainSelector.NEXTCHAIN  ->
            {
                val cmgr = MultiNodeCnxnMgr(name ?: "NXC", ChainSelector.NEXTCHAIN, arrayOf("seed.nextchain.cash", "node1.nextchain.cash", "node2.nextchain.cash"))
                cmgr.desiredConnectionCount = 2  // NXC chain doesn't have many nodes so reduce the desired connection count or there may be more desired nodes than exist in the nxc chain
                cmgr
            }
            else                     -> throw BadCryptoException()
        }
        result.getElectrumServerCandidate = { chain -> ElectrumServerOn(chain) }
        cnxnMgrs[chain] = result
        return result
    }
}

fun ElectrumServerOn(chain: ChainSelector): IpPort
{
    return when (chain)
    {
        ChainSelector.BCHMAINNET -> IpPort("electrum.seed.bitcoinunlimited.net", DEFAULT_ELECTRUM_SERVER_PORT)
        ChainSelector.BCHTESTNET -> IpPort("159.65.163.15", DEFAULT_ELECTRUM_SERVER_PORT)
        ChainSelector.BCHREGTEST -> IpPort(SimulationHostIP, DEFAULT_ELECTRUM_SERVER_PORT)
        ChainSelector.NEXTCHAIN  -> IpPort("electrumserver.seed.nextchain.cash", 7229)
        ChainSelector.BCHNOLNET  -> throw BadCryptoException()
    }
}

var blockchains: MutableMap<ChainSelector, Blockchain> = mutableMapOf()
fun GetBlockchain(chainSelector: ChainSelector, cnxnMgr: CnxnMgr, context: PlatformContext, name: String? = null): Blockchain
{

    synchronized(blockchains)
    {
        LogIt.info(sourceLoc() + " " + "Get Blockchain")
        val existing = blockchains[chainSelector]
        if (existing != null) return existing
        val result = when (chainSelector)
        {
            ChainSelector.BCHTESTNET -> Blockchain(
                ChainSelector.BCHTESTNET,
                name ?: "TBCH",
                cnxnMgr,
                Hash256("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"),
                Hash256("000000000003cab8d8465f4ea4efcb15c28e5eed8e514967883c085351c5b134"),
                Hash256("000000000005ae0f3013e89ce47b6f949ae489d90baf6621e10017490f0a1a50"),
                1348366,
                "52bbf4d7f1bcb197f2".toBigInteger(16),
                context, dbPrefix
            )

            // Regtest for use alongside testnet
            ChainSelector.BCHREGTEST -> Blockchain(
                ChainSelector.BCHREGTEST,
                name ?: "RBCH",
                cnxnMgr,
                // If checkpointing the genesis block, set the prior block id to the genesis block as well
                Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"),
                Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"),
                Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"),
                0,
                0.toBigInteger(),
                context, dbPrefix
            )
            // Bitcoin Cash mainnet chain
            ChainSelector.BCHMAINNET -> Blockchain(
                ChainSelector.BCHMAINNET,
                name ?: "BCH",
                cnxnMgr,
                genesisBlockHash = Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"),
                checkpointPriorBlockId = Hash256("000000000000000000cc4d859bd0a2c2cf46aa6cf821986c9895b78d65162bbb"),
                checkpointId = Hash256("000000000000000002c310db434163004e28d7cb25dcfb45a90653f179519336"),
                checkpointHeight = 642337,
                checkpointWork = "13a2a5fc1efbc0514731aa5".toBigInteger(16),
                context = context, dbPrefix = dbPrefix
            )
            // Bitcoin Cash mainnet chain
            ChainSelector.NEXTCHAIN  -> Blockchain(
                ChainSelector.NEXTCHAIN,
                name ?: "NXC",
                cnxnMgr,
                Hash256("9623194f62f31f7a7065467c38e83cf060a2b866190204f3dd16f6587d8d9374"),
                Hash256("9623194f62f31f7a7065467c38e83cf060a2b866190204f3dd16f6587d8d9374"),
                Hash256("83f09ccd686052095cf6c3a24a0561752eb4b270a8c6841f9519b30ca7b3071f"),
                1,
                0x100101.toBigInteger(),
                context, dbPrefix
            )
            else                     -> throw BadCryptoException()
        }
        blockchains[chainSelector] = result
        return result
    }
}


class Account(
    val name: String, //* The name of this account
    val context: PlatformContext,
    var flags: ULong = ACCOUNT_FLAG_NONE,
    chainSelector: ChainSelector? = null,
    secretWords: String? = null,
    startPlace: Long? = null //* Where to start looking for transactions
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
        }
        catch (e:DataMissingException)
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
        LogIt.info("wallet add blockchain")
        wallet.addBlockchain(chain, chain.checkpointHeight, startPlace)
        LogIt.info("wallet add blockchain done")
        if (chainSelector != ChainSelector.NEXTCHAIN)  // no fiat price for nextchain
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
        return(!pinEntered)
    }

    fun loadEncodedPin(): ByteArray?
    {
        val db = walletDb!!
        try
        {
            val storedEpin = db.get("accountPin_" + name)
            if (storedEpin.size>0) return storedEpin
            return null
        }
        catch(e:Exception)
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
        }
        catch(e: InvalidKeySpecException)  // ignore invalid PIN, it can't unlock any wallets
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
        if (wallet.chainSelector == ChainSelector.BCHMAINNET)
            return "https://explorer.bitcoinunlimited.info/tx/" + txHex //"https://blockchair.com/bitcoin-cash/transaction/" + txHex
        if (wallet.chainSelector == ChainSelector.BCHTESTNET)
            return "http://testnet.imaginary.cash/tx/" + txHex
        if (wallet.chainSelector == ChainSelector.NEXTCHAIN)
            return "http://explorer.nextchain.cash/tx/" + txHex
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

    //? Convert a value in this currency code unit into its primary unit
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
                    "(" + mBchFormat.format(unconfirmedBalance.setScale(mBchDecimals)) + ")"

            unconfirmedBalanceGUI(unconfBalStr, force)

            // If we got something in a receive address, then show a new one
            updateReceiveAddressUI?.invoke(this)

            val cnxnLst = wallet.chainstate?.chain?.net?.mapConnections() { it.name }
            val peers = cnxnLst?.joinToString(", ")
            infoGUI(force,
                {
                    i18n(R.string.at) + " " + (wallet.chainstate?.syncedHash?.toHex()?.takeLast(8) ?: "") + ", " + (wallet.chainstate?.syncedHeight ?: "") + " " + i18n(R.string.of) + " " + (wallet.chainstate?.chain?.curHeight
                        ?: "") + " blocks, " + (wallet.chainstate?.chain?.net?.numPeers() ?: "") + " peers\n" + peers
                })

            tickerGUI(name, force)
        }
    }

    // Load the exchange rate
    fun getXchgRates(fiatCurrencyCode: String)
    {
        if (chain.chainSelector != ChainSelector.BCHMAINNET)
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


class WallyApp : Application()
{
    var firstRun = false

    companion object
    {
        // Used to load the 'native-lib' library on application startup.
        init
        {
            //System.loadLibrary("native-lib")
            System.loadLibrary("bitcoincashandroid")
        }
    }

    val init = Initialize.LibBitcoinCash(ChainSelector.BCHTESTNET.v)  // Initialize the C library first

    val accounts: MutableMap<String, Account> = mutableMapOf()

    val primaryAccount: Account
        get()
        {
            // return the wallet named "mBCH"
            val prim = accounts[PRIMARY_WALLET]
            if (prim != null) return prim
            // return the first BCH wallet
            for (i in accounts.values)
            {
                if (i.wallet.chainSelector == ChainSelector.BCHMAINNET) return i
            }
            throw PrimaryWalletInvalidException()
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
        var unlocked=0
        for (account in accounts.values)
        {
            if (!account.pinEntered) unlocked += account.submitAccountPin(pin)
        }
        if (unlocked > 0) notifyAccountUnlocked()
    }

    val interestedInAccountUnlock = mutableListOf<()->Unit>()
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
        }
        catch(e: Exception)
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
        }
        catch (e: IllegalStateException)
        {
            LogIt.warning("Error creating account: ${e.message}")
            return
        }

        ac.pinEntered = true  // for convenience, new accounts begin as if the pin has been entered
        ac.start(applicationContext)
        ac.onWalletChange()

        accounts[name] = ac
        // Write the list of existing accounts, so we know what to load
        saveActiveAccountList()
        // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()
    }

    fun recoverAccount(name: String, flags: ULong, pin: String, secretWords: String, chainSelector: ChainSelector, earliestActivity: Long?)
    {
        dbgAssertNotGuiThread()
        val ctxt = PlatformContext(applicationContext)

        // I only want to write the PIN once when the account is first created
        val epin = try
        {
            EncodePIN(name, pin.trim())
        }
        catch(e: InvalidKeySpecException)  // If the pin is bad (generally whitespace or null) ignore it
        {
            byteArrayOf()
        }
        saveAccountPin(name, epin)

        val ac = Account(name, ctxt, flags, chainSelector, secretWords, earliestActivity)
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

        walletDb = OpenKvpDB(ctxt, dbPrefix + "bip44walletdb")
        appResources = getResources()
        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        val BchExclusiveNode: String? = if (prefs.getBoolean(BCH_EXCLUSIVE_NODE_SWITCH, false)) prefs.getString(BCH_EXCLUSIVE_NODE, null) else null
        val BchPreferredNode: String? = if (prefs.getBoolean(BCH_PREFER_NODE_SWITCH, false)) prefs.getString(BCH_PREFER_NODE, null) else null


        if (!RunningTheTests())  // If I'm running the unit tests, don't create any wallets since the tests will do so
        {
            // Initialize the currencies supported by this wallet
            launch {
                if (REG_TEST_ONLY)  // If I want a regtest only wallet for manual debugging, just create it directly
                {
                    accounts.getOrPut("mRBCH") {
                        try
                        {
                            val c = Account("mRBCH", ctxt);
                            c
                        }
                        catch (e: DataMissingException)
                        {
                            val c = Account("mRBCH", ctxt, ACCOUNT_FLAG_NONE, ChainSelector.BCHREGTEST)
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
                    }
                    catch (e: DataMissingException)
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
                        }
                        catch (e: DataMissingException)
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
                    if ((BchExclusiveNode != null) && (c.chain.chainSelector == ChainSelector.BCHMAINNET))
                    {
                        try
                        {
                            val split = SplitIpPort(BchExclusiveNode, BlockchainPort[ChainSelector.BCHMAINNET]!!)
                            c.cnxnMgr.exclusiveNode(split.first, split.second)
                        }
                        catch (e: Exception)
                        {
                        } // bad IP:port data
                    }
                    // If I have a preferred connection, then start up that way
                    if ((BchPreferredNode != null) && (c.chain.chainSelector == ChainSelector.BCHMAINNET))
                    {
                        try
                        {
                            val split = SplitIpPort(BchPreferredNode, BlockchainPort[ChainSelector.BCHMAINNET]!!)
                            c.cnxnMgr.preferNode(split.first, split.second)
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

}