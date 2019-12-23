// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.widget.TextView
import bitcoinunlimited.libbitcoincash.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.logging.Logger

val SimulationHostIP = "10.0.2.2"
val LanHostIP = "192.168.1.100"

private val LogIt = Logger.getLogger("bitcoinunlimited.app")

open class PrimaryWalletInvalidException(): BUException("Primary wallet not defined or currently unavailable", "not ready", ErrorSeverity.Abnormal)

var coinsCreated = false

//? Currently selected fiat currency code
var fiatCurrencyCode:String = "USD"


// What is the default wallet and blockchain to use for most functions (like identity)
val PRIMARY_WALLET = if (REG_TEST_ONLY) "mRBCH" else "mBCH"

fun MakeNewWallet(currencyCode: String, chain: ChainSelector): Bip44Wallet
{
    if (currencyCode == "mRBCH")
        return Bip44Wallet(currencyCode, chain, "trade box today light need route design birth turn insane oxygen sense")
    if (currencyCode == "mTBCH")
        return Bip44Wallet(currencyCode, chain, NEW_WALLET)
        //return Bip44Wallet(currencyCode, chain, "")

    return Bip44Wallet(currencyCode, chain, NEW_WALLET)
    //return Bip44Wallet(currencyCode, chain, "")
}


class Coin(
    val currencyCode: String, //* The name and units of this crypto
    val chainSelector: ChainSelector, //* What blockchain this is
    val context: PlatformContext
)
{
    val tickerGUI = Reactive<String>("") // Where to show the crypto's ticker
    val balanceGUI = Reactive<String>("")
    val unconfirmedBalanceGUI = Reactive<String>("")
    val infoGUI = Reactive<String>("")

    // Create our wallet
    //var wallet: RamWallet = RamWallet(currencyCode)  // TODO RAM wallet just for testing
    var wallet: Bip44Wallet = try { Bip44Wallet(currencyCode, chainSelector, LOAD_WALLET) }
    catch (_:WalletKeyDataMissingException)
    {
        MakeNewWallet(currencyCode, chainSelector)
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

    val cnxnMgr: CnxnMgr = when (currencyCode)
    {
        "mBR1" -> RegTestCnxnMgr("BR1", SimulationHostIP, BCHregtestPort)
        "mBR2" -> RegTestCnxnMgr("BR2", SimulationHostIP, BCHregtest2Port)
        "mTBCH" -> MultiNodeCnxnMgr("mTBCH", ChainSelector.BCHTESTNET, arrayOf("testnet-seed.bitcoinabc.org"))
        "mBCH" -> MultiNodeCnxnMgr("mBCH", ChainSelector.BCHMAINNET, arrayOf("seed.bitcoinunlimited.net", "btccash-seeder.bitcoinunlimited.info"))
        //"mRBCH" -> RegTestCnxnMgr("mRBCH", RegtestIP(), mRBCHPort)
        "mRBCH" -> MultiNodeCnxnMgr("mRBCH", ChainSelector.BCHREGTEST, arrayOf("regtest"))

        else -> throw BadCryptoException()
    }

    val chain: Blockchain = when (currencyCode)
    {
        // Blockchain(val chainId: ChainSelector, val name: String, net: CnxnMgr, val genesisBlockHash: Hash256, var checkpointPriorBlockId: Hash256, var checkpointId: Hash256, var checkpointHeight: Long, var checkpointWork: BigInteger, val context: PlatformContext)
        "mBR1" -> Blockchain(ChainSelector.BCHREGTEST, "BR1", cnxnMgr, Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"), Hash256(), Hash256(), -1, -1.toBigInteger(), context)
        "mBR2" -> Blockchain(ChainSelector.BCHREGTEST, "BR2", cnxnMgr, Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"), Hash256(), Hash256(), -1, -1.toBigInteger(), context)
        // testnet fork: "mTBCH"
        "mTBCH" -> Blockchain(ChainSelector.BCHTESTNET, "mTBCH", cnxnMgr, Hash256("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"), Hash256("000000000003cab8d8465f4ea4efcb15c28e5eed8e514967883c085351c5b134"), Hash256("000000000005ae0f3013e89ce47b6f949ae489d90baf6621e10017490f0a1a50"), 1348366, "52bbf4d7f1bcb197f2".toBigInteger(16), context)

        // Regtest for use alongside testnet
        "mRBCH" -> Blockchain(ChainSelector.BCHREGTEST, "mRBCH", cnxnMgr, Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"), Hash256("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"), Hash256("2a11fa1399e126cf549b9b9118436d4c39a95897933705c38e9cd706ef1f24dd"), 1, 4.toBigInteger(), context)

        // Bitcoin Cash mainnet chain
        "mBCH" -> Blockchain(ChainSelector.BCHMAINNET, "mBCH", cnxnMgr, Hash256("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"), Hash256("000000000000000002cba5eaabc2293a3f5b89396258654fa456c29dbcca7b77"), Hash256("0000000000000000029f7923ddb3937d3993a59f3bcc2efbfb7de4eb9e5df276"), 614195, "10f8ce72b89feefe6f294c5".toBigInteger(16), context)

        else -> throw BadCryptoException()
    }

    init
    {
        val hundredThousand = CurrencyDecimal(SATinMBCH)
        wallet.prepareDestinations(2, 2)  // Make sure that there is at least a few addresses before we hook into the network
        wallet.addBlockchain(chain, chain.nearTip, chain.checkpointHeight) // Since this is a new ram wallet (new private keys), there cannot be any old blocks with transactions
        wallet.spotPrice = { currencyCode -> assert(currencyCode == fiatCurrencyCode); fiatPerCoin/hundredThousand }
        wallet.historicalPrice = { currencyCode: String, epochSec: Long -> historicalMbchInFiat(currencyCode,epochSec)/hundredThousand }
    }

    // If this coin's receive address is shown on-screen, this is not null
    var updateReceiveAddressUI: ((Coin)->Unit)? = null

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

            tickerGUI(currencyCode, force)
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

    val coins:MutableMap<String,Coin> = mutableMapOf()

    val primaryWallet:Wallet
        get() = coins[PRIMARY_WALLET]?.wallet ?: throw PrimaryWalletInvalidException()

    fun coinFor(chain: ChainSelector): Coin?
    {
        // Check to see if our preferred crypto matches first
        for (coin in coins.values)
        {
            if ((coin.currencyCode == cryptoCurrencyCode)&&(chain == coin.chainSelector)) return coin
        }

        // Look for any match
        for (coin in coins.values)
        {
            if (chain == coin.chainSelector)
            {
                return coin
            }
        }
        return null
    }

    // Called when the application is starting, before any other application objects have been created.
    // Overriding this method is totally optional!
    override fun onCreate()
    {
        super.onCreate()

        val ctxt = PlatformContext(applicationContext)

        walletDb = OpenKvpDB(ctxt, "bip44walletdb")

        if (!RunningTheTests())
        {
            // Initialize the currencies supported by this wallet
            GlobalScope.launch {

                // Actually choose which of several configured coins to create

                // REGTEST
                //coins.getOrPut("mBR1", { val c = Coin("mBR1", ctxt); c.getReceiveInfo(1); c })
                //coins.getOrPut("mBR2", { val c = Coin("mBR2", ctxt); c.getReceiveInfo(1); c })

                if (REG_TEST_ONLY)
                {

                    coins.getOrPut("mRBCH") {
                        val c = Coin("mRBCH", ChainSelector.BCHREGTEST, ctxt);
                        c
                    }
                }
                else
                {
                    coins.getOrPut("mBCH") {
                        val c = Coin("mBCH", ChainSelector.BCHMAINNET, ctxt);
                        c
                    }
                    coins.getOrPut("mTBCH") {
                        val c = Coin("mTBCH", ChainSelector.BCHTESTNET, ctxt);
                        c
                    }

                }




                coinsCreated = true

                // Wait for the blockchain to come up so the new wallets start near its tip
                GlobalScope.launch {
                    delay(1000);
                    for (c in coins.values)
                    {
                        //GlobalScope.launch { c.chain.deleteAllPersistentData() }
                        c.start(applicationContext)
                        c.onWalletChange()  // update all wallet UI fields since just starting up
                    }
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