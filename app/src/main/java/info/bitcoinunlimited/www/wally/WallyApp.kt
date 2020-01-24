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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Exception
import java.math.BigDecimal
import java.util.logging.Logger

val SimulationHostIP = "10.0.2.2"
val LanHostIP = "192.168.1.100"

private val LogIt = Logger.getLogger("bitcoinunlimited.app")

open class PrimaryWalletInvalidException(): BUException("Primary wallet not defined or currently unavailable", "not ready", ErrorSeverity.Abnormal)

var coinsCreated = false

/** Currently selected fiat currency code */
var fiatCurrencyCode:String = "USD"

/** Database name prefix, empty string for mainnet, set for testing */
var dbPrefix = if (RunningTheTests()) "guitest_" else if (REG_TEST_ONLY==true) "regtest_" else ""

val SupportedBlockchains = mapOf("BCH (Bitcoin Cash)" to ChainSelector.BCHMAINNET, "TBCH (Testnet Bitcoin Cash)" to ChainSelector.BCHTESTNET, "RBCH (Regtest Bitcoin Cash)" to ChainSelector.BCHREGTEST)
val ChainSelectorToSupportedBlockchains = SupportedBlockchains.entries.associate{(k,v)-> v to k}

// What is the default wallet and blockchain to use for most functions (like identity)
val PRIMARY_WALLET = if (REG_TEST_ONLY) "mRBCH" else "mBCH"

fun MakeNewWallet(name: String, chain: ChainSelector): Bip44Wallet
{
    if (chain == ChainSelector.BCHREGTEST)
        return Bip44Wallet(name, chain, "trade box today light need route design birth turn insane oxygen sense")
    if (chain == ChainSelector.BCHTESTNET)
        return Bip44Wallet(name, chain, NEW_WALLET)
        //return Bip44Wallet(currencyCode, chain, "")
    if (chain == ChainSelector.BCHMAINNET)
        return Bip44Wallet(name, chain, NEW_WALLET)
    //return Bip44Wallet(currencyCode, chain, "")
    throw BUException("invalid chain selected")
}

// TODO: Right now we create new ones, but in the future reuse an existing
fun GetCnxnMgr(chain: ChainSelector, name:String?=null): CnxnMgr
{
    return when(chain)
    {
    ChainSelector.BCHTESTNET      -> MultiNodeCnxnMgr(name ?: "mTBCH", ChainSelector.BCHTESTNET, arrayOf("testnet-seed.bitcoinabc.org"))
    ChainSelector.BCHMAINNET      -> MultiNodeCnxnMgr(name ?: "mBCH", ChainSelector.BCHMAINNET, arrayOf("seed.bitcoinunlimited.net", "btccash-seeder.bitcoinunlimited.info"))
    ChainSelector.BCHREGTEST      -> MultiNodeCnxnMgr(name ?: "mRBCH", ChainSelector.BCHREGTEST, arrayOf(SimulationHostIP))
    else                          -> throw BadCryptoException()
    }
}

fun ElectrumServerOn(chain: ChainSelector): Pair<String,Int>
{
    return when(chain)
    {
        ChainSelector.BCHMAINNET -> Pair("electrumserver.seed.bitcoinunlimited.net", DEFAULT_ELECTRUM_SERVER_PORT)
        ChainSelector.BCHTESTNET -> Pair("159.65.163.15", DEFAULT_ELECTRUM_SERVER_PORT)
        ChainSelector.BCHREGTEST -> Pair(SimulationHostIP, DEFAULT_ELECTRUM_SERVER_PORT)
        ChainSelector.BCHNOLNET -> throw BadCryptoException()
    }
}

// TODO: Right now we create new ones, but in the future reuse an existing
fun GetBlockchain(chainSelector: ChainSelector, cnxnMgr: CnxnMgr, context: PlatformContext, name:String?=null): Blockchain
{
    // Blockchain(val chainId: ChainSelector, val name: String, net: CnxnMgr, val genesisBlockHash: Hash256, var checkpointPriorBlockId: Hash256, var checkpointId: Hash256, var checkpointHeight: Long, var checkpointWork: BigInteger, val context: PlatformContext)
    //"mBR1" -> Blockchain(ChainSelector.BCHREGTEST, "BR1", cnxnMgr, Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"), Hash256(), Hash256(), -1, -1.toBigInteger(), context)
    //"mBR2" -> Blockchain(ChainSelector.BCHREGTEST, "BR2", cnxnMgr, Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"), Hash256(), Hash256(), -1, -1.toBigInteger(), context)
    // testnet fork: "mTBCH"
    return when(chainSelector)
    {
        ChainSelector.BCHTESTNET -> Blockchain(
            ChainSelector.BCHTESTNET,
            name ?: "mTBCH",
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
            name ?: "mRBCH",
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
            name ?:"mBCH",
            cnxnMgr,
            Hash256("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"),
            Hash256("000000000000000002cba5eaabc2293a3f5b89396258654fa456c29dbcca7b77"),
            Hash256("0000000000000000029f7923ddb3937d3993a59f3bcc2efbfb7de4eb9e5df276"),
            614195,
            "10f8ce72b89feefe6f294c5".toBigInteger(16),
            context, dbPrefix
        )
        else                     -> throw BadCryptoException()
    }
}

class Account(val name: String, //* The name of this account
              val context: PlatformContext,
              chainSelector: ChainSelector? = null,
              secretWords: String? = null
)
{
    val tickerGUI = Reactive<String>("") // Where to show the crypto's ticker
    val balanceGUI = Reactive<String>("")
    val unconfirmedBalanceGUI = Reactive<String>("")
    val infoGUI = Reactive<String>("")

    var wallet: Bip44Wallet = if (chainSelector == null)
    {
        Bip44Wallet(name)  // Load a saved wallet
    }
    else
    {
        if (secretWords == null)
            Bip44Wallet(name, chainSelector, NEW_WALLET)   // New wallet
        else
            Bip44Wallet(name, chainSelector, secretWords)  // Wallet recovery
    }

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
    var updateReceiveAddressUI: ((Account)->Unit)? = null

    /** loading existing wallet */
    init
    {
        val hundredThousand = CurrencyDecimal(SATinMBCH)
        wallet.prepareDestinations(2, 2)  // Make sure that there is at least a few addresses before we hook into the network
        wallet.addBlockchain(chain, chain.nearTip, chain.checkpointHeight) // Since this is a new ram wallet (new private keys), there cannot be any old blocks with transactions
        wallet.spotPrice = { currencyCode -> assert(currencyCode == fiatCurrencyCode); fiatPerCoin/hundredThousand }
        wallet.historicalPrice = { currencyCode: String, epochSec: Long -> historicalMbchInFiat(currencyCode,epochSec)/hundredThousand }

    }

    /** Return a web URL that will provide more information about this transaction */
    fun transactionInfoWebUrl(txHex: String?): String?
    {
        if (txHex == null) return null
        if (wallet.chainSelector == ChainSelector.BCHMAINNET)
            return "https://explorer.bitcoinunlimited.info/tx/" + txHex //"https://blockchair.com/bitcoin-cash/transaction/" + txHex
        if (wallet.chainSelector == ChainSelector.BCHTESTNET)
            return "http://testnet.imaginary.cash/tx/" + txHex
        return null
    }

    //? Completely delete this wallet, losing any money you may have in it
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
        if (balance != null) balanceGUI.reactor = TextViewReactor<String>(balance)
        if (unconf != null) unconfirmedBalanceGUI.reactor = TextViewReactor<String>(unconf)
        if (infoView != null) infoGUI.reactor = TextViewReactor<String>(infoView)
    }

    //? Convert the default display units to the finest granularity of this currency.  For example, mBCH to Satoshis
    fun toFinestUnit(amount: BigDecimal):Long
    {
        val ret = amount * SATinMBCH.toBigDecimal()
        return ret.toLong()
    }

    //? Convert the finest granularity of this currency to the default display unit.  For example, Satoshis to mBCH
    fun fromFinestUnit(amount: Long):BigDecimal
    {
        val ret = BigDecimal(amount, currencyMath).setScale(currencyScale) / SATinMBCH.toBigDecimal()
        return ret
    }

    //? Convert a value in this currency code unit into its primary unit
    fun toPrimaryUnit(qty: BigDecimal):BigDecimal
    {
        return qty/(1000.toBigDecimal())
    }

    //? Convert the passed quantity to a string in the decimal format suitable for this currency
    fun format(qty: BigDecimal): String = mBchFormat.format(qty)


    data class ReceiveInfoResult(val addrString: String?, val qr: Bitmap?)

    suspend fun ifUpdatedReceiveInfo(sz: Int, refresh:(String,Bitmap) -> Unit) = onUpdatedReceiveInfo(sz, refresh)

    suspend fun onUpdatedReceiveInfo(sz: Int, refresh:((String,Bitmap) -> Unit)): Unit
    {
        currentReceive.let {
            val addr: PayAddress? = it?.address
            val qr = currentReceiveQR
            if ((it == null) || (addr == null) || (qr == null) || (wallet.getBalanceIn(addr) > 0))
            {
                currentReceive = null
                currentReceiveQR = null

                val ret = wallet.newDestination()
                val qr2 = TextToImageEncode(ret.address.toString(), sz + 200)
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
    fun receiveInfoWithQuantity(qty: BigDecimal, sz:Int, refresh:((ReceiveInfoResult) -> Unit))
    {
        GlobalScope.launch {
            val addr = currentReceive?.address
            val uri = addr.toString() + "?amount=" + bchFormat.format(toPrimaryUnit(qty))
            val qr = TextToImageEncode(uri, sz)
            refresh(ReceiveInfoResult(uri, qr))
        }
    }


    fun getReceiveQR(sz: Int): Bitmap
    {
        var im = currentReceiveQR
        val cr = currentReceive
        if ((im == null)&&(cr != null))
        {
            im = TextToImageEncode(cr.address.toString(), sz + 200)
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
            infoGUI("at " + (wallet.chainstate?.syncedHash?.toHex()?.takeLast(8) ?: "") + ", " + (wallet.chainstate?.syncedHeight ?:"") + " of " + (wallet.chainstate?.chain?.curHeight?:"") + " blocks, " + (wallet.chainstate?.chain?.net?.numPeers()?:"") + " peers\n" + peers, force)

            tickerGUI(name, force)
        }
    }

    // Load the exchange rate
    fun getXchgRates(fiatCurrencyCode: String)
    {
        // TODO: only good for BCH
        MbchInFiat(fiatCurrencyCode) { v: BigDecimal ->
            fiatPerCoin = v;
            // TODO: checkSendQuantity(sendQuantity.text.toString())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun start(ac: Context)
    {
        cnxnMgr.start()
        chain.start()
    }
}


class WallyApp: Application()
{
    companion object
    {
        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }

    val init = Initialize.LibBitcoinCash(ChainSelector.BCHTESTNET.v)  // Initialize the C library first

    val accounts:MutableMap<String,Account> = mutableMapOf()

    val primaryWallet:Wallet
        get() = accounts[PRIMARY_WALLET]?.wallet ?: throw PrimaryWalletInvalidException()

    fun coinFor(chain: ChainSelector): Account?
    {
        // Check to see if our preferred crypto matches first
        for (account in accounts.values)
        {
            if ((account.name == defaultAccount)&&(chain == account.wallet.chainSelector)) return account
        }

        // Look for any match
        for (account in accounts.values)
        {
            if (chain == account.wallet.chainSelector)
            {
                return account
            }
        }
        return null
    }

    /** Return what account a particular GUI element is bound to or null if its not bound */
    fun accountFromGui(view: View): Account?
    {
        for (a in accounts.values)
        {
            if ((a.tickerGUI.reactor as TextViewReactor<String>).gui == view) return a
            if ((a.balanceGUI.reactor as TextViewReactor<String>).gui == view) return a
            if ((a.unconfirmedBalanceGUI.reactor as TextViewReactor<String>).gui == view) return a
            if ((a.infoGUI.reactor as TextViewReactor<String>).gui == view) return a
        }
        return null
    }

    fun saveActiveAccountList()
    {
        val s:String = accounts.keys.joinToString(",")

        val db = walletDb!!

        db.set("activeAccountNames", s.toByteArray())
    }

    fun newAccount(name: String, chainSelector: ChainSelector)
    {
        dbgAssertNotGuiThread()
        val ctxt = PlatformContext(applicationContext)
        val ac = Account(name, ctxt, chainSelector)
        ac.start(applicationContext)
        ac.onWalletChange()

        accounts[name] = ac
        saveActiveAccountList()
        // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()

    }

    fun recoverAccount(name: String, secretWords: String, chainSelector: ChainSelector)
    {
        dbgAssertNotGuiThread()
        val ctxt = PlatformContext(applicationContext)
        val ac = Account(name, ctxt, chainSelector, secretWords)
        ac.start(applicationContext)
        ac.onWalletChange()

        accounts[name] = ac
        saveActiveAccountList()
        // wallet is saved in wallet constructor so no need to: ac.wallet.SaveBip44Wallet()

    }

    // Called when the application is starting, before any other application objects have been created.
    // Overriding this method is totally optional!
    override fun onCreate()
    {
        super.onCreate()

        val ctxt = PlatformContext(applicationContext)

        walletDb = OpenKvpDB(ctxt, dbPrefix + "bip44walletdb")

        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        val BchExclusiveNode:String? = if (prefs.getBoolean(BCH_EXCLUSIVE_NODE_SWITCH, false)) prefs.getString(BCH_EXCLUSIVE_NODE, null) else null
        val BchPreferredNode:String? = if (prefs.getBoolean(BCH_PREFER_NODE_SWITCH, false)) prefs.getString(BCH_PREFER_NODE, null) else null


        if (!RunningTheTests())  // If I'm running the unit tests, don't create any wallets since the tests will do so
        {
            // Initialize the currencies supported by this wallet
            GlobalScope.launch {

                // Actually choose which of several configured coins to create

                if (REG_TEST_ONLY)  // If I want a regtest only wallet for manual debugging, just create it directly
                {
                    accounts.getOrPut("mRBCH") {
                        try {
                            val c = Account("mRBCH", ctxt);
                            c
                        } catch (e: DataMissingException) {
                            val c = Account("mRBCH", ctxt, ChainSelector.BCHREGTEST)
                            c
                        }
                    }
                }
                else  // OK, recreate the wallets saved on this phone
                {
                    val db = walletDb!!

                    val accountNames = try
                    {
                        db.get("activeAccountNames")
                    }
                    catch(e: DataMissingException)
                    {
                        /*
                        // Temporary: create what we used to do manually
                        accounts.getOrPut("mBCH") {
                            val c = Account("mBCH", ctxt, ChainSelector.BCHMAINNET);
                            c
                        }
                        accounts.getOrPut("mTBCH") {
                            val c = Account("mTBCH", ctxt, ChainSelector.BCHTESTNET);
                            c
                        }
                        saveActiveAccountList()
                        */
                        byteArrayOf()
                    }

                    val accountNameList = String(accountNames).split(",")
                    for (name in accountNameList)
                    {
                        try
                        {
                            val ac = Account(name, ctxt)
                            accounts[ac.name] = ac
                        }
                        catch(e:DataMissingException)
                        {
                            LogIt.warning(sourceLoc() + ": Active account $name was not found in the database")
                            // Nothing to really do but ignore the missing account
                        }

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
                        catch(e: Exception) {} // bad IP:port data
                    }
                    // If I have a preferred connection, then start up that way
                    if ((BchPreferredNode != null) && (c.chain.chainSelector == ChainSelector.BCHMAINNET))
                    {
                        try
                        {
                            val split = SplitIpPort(BchPreferredNode, BlockchainPort[ChainSelector.BCHMAINNET]!!)
                            c.cnxnMgr.preferNode(split.first, split.second)
                        }
                        catch(e: Exception) {} // bad IP:port data provided by user
                    }

                    c.start(applicationContext)
                    c.onWalletChange()  // update all wallet UI fields since just starting up
                }
            }
        }

    }

    // Called by the system when the device configuration changes while your component is running.
    // Overriding this method is totally optional!
    override fun onConfigurationChanged ( newConfig : Configuration)
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