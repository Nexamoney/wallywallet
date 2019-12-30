// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import kotlinx.coroutines.*

import android.content.ClipData
import bitcoinunlimited.libbitcoincash.*

import android.content.ClipboardManager
import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.ArrayAdapter

import kotlinx.android.synthetic.main.activity_main.*

import com.google.zxing.EncodeHintType
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PersistableBundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyCharacterMap
import android.view.Menu
import android.view.MenuInflater
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.content.ContextCompat

import androidx.core.view.MenuItemCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.lang.NumberFormatException
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult
import java.lang.ArithmeticException
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

import java.util.logging.Logger
import kotlin.concurrent.thread

private val LogIt = Logger.getLogger("bitcoinunlimited.mainActivity")

val uriToMbch = 1000.toBigDecimal()  // Todo allow other currencies supported by this wallet

val ERROR_DISPLAY_TIME = 6000.toLong()
val NOTICE_DISPLAY_TIME = 4000.toLong()

open class PasteUnintelligibleException(): BUException("", i18n(R.string.pasteUnintelligible), ErrorSeverity.Expected)
open class PasteEmptyException(): BUException("", i18n(R.string.pasteIsEmpty), ErrorSeverity.Abnormal)
open class BadAmountException(msg: Int): BUException(i18n(msg), i18n(R.string.badAmount))
open class BadCryptoException(msg: Int=-1): BUException(i18n(msg), i18n(R.string.badCryptoCode))
open class BadUnitException(msg: Int=-1): BUException(i18n(msg), i18n(R.string.badCurrencyUnit))
open class UnavailableException(msg: Int=-1): BUException(i18n(msg), i18n(R.string.unavailable))

var appContext:PlatformContext? = null

class MainActivityModel
{
    //* On resume, we need to remember what send currency type was selected.  This is given to us in the data bundle provided during onCreate, but is needed later
    var lastSendCurrencyType: String? = null

    //* On resume, we need to remember what receive currency type was selected.  This is given to us in the data bundle provided during onCreate, but is needed later
    var lastRecvCoinType: String? = null

    //* remember last send coin selected
    var lastSendCoinType: String? = null
}

var mainActivityModel = MainActivityModel()


class MainActivity : CommonActivity()
{
    override var navActivityId = R.id.navigation_home

    var app: WallyApp? = null

    val coins:MutableMap<String,Coin>
        get() = app!!.coins

    //* last paste so we don't try to paste the same thing again
    var lastPaste: String = ""

    private var shareActionProvider: ShareActionProvider? = null

    /** If there's a payment proposal that this app has seen, information about it is located here */
    var paymentInProgress: ProspectivePayment? = null

    /** If this program is changing the GUI, rather then the user, then there are some logic differences */
    var machineChangingGUI: Boolean = false

    fun onBlockchainChange(blockchain: Blockchain)
    {
        for ((_,c) in coins)
        {
            if (c.chain == blockchain)
                c.onWalletChange()  // coin onWalletChange also updates the blockchain state GUI
        }
    }

    fun onWalletChange(wallet: Wallet)  // callback provided to the wallet code
    {
        for ((_,c) in coins)
        {
            if (c.wallet == wallet)
            {
                c.onWalletChange()
                // recalculate the QR code if needed early to speed up response time
                //GlobalScope.launch { c.getReceiveQR(minOf(imageView.layoutParams.width, imageView.layoutParams.height, 1024)) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        app = (getApplication() as WallyApp)  // !! how can we have an activity without an app?
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ctxt = PlatformContext(applicationContext)
        appContext = ctxt

        appResources = getResources()

        sendToAddress.text.clear()
        sendQuantity.text.clear()

        // Load the model with persistently saves stuff when this activity is created
        if (savedInstanceState != null)
        {
            sendToAddress.text.append(savedInstanceState.getString("sendToAddress", "") ?: "")
            sendQuantity.text.append(savedInstanceState.getString("sendQuantity", "") ?: "")
            mainActivityModel.lastSendCurrencyType = savedInstanceState.getString("sendCurrencyType", cryptoCurrencyCode)
            mainActivityModel.lastRecvCoinType = savedInstanceState.getString("recvCoinType", cryptoCurrencyCode)
            mainActivityModel.lastSendCoinType = savedInstanceState.getString("sendCoinType", cryptoCurrencyCode)
        }
        else
        {
            mainActivityModel.lastSendCurrencyType = cryptoCurrencyCode
            mainActivityModel.lastRecvCoinType = cryptoCurrencyCode
            mainActivityModel.lastSendCoinType = cryptoCurrencyCode
        }

        readQRCodeButton.setOnClickListener {
            dbgAssertGuiThread()
            LogIt.info("scanning for qr code")
            val v = IntentIntegrator(this)
            v.setPrompt(i18n(R.string.scanPaymentQRcode)).setBeepEnabled(false).setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name).setOrientationLocked(true).setCameraId(0).initiateScan()

        }

        destAddrPasteButton.setOnClickListener {
            dbgAssertGuiThread()
            LogIt.info(sourceLoc() + ": paste button pressed")
            try
            {
                lastPaste = ""  // We never want to completely ignore an explicit paste
                handlePastedData()
            }
            catch(e: Exception)
            {
                displayException(e)
            }
        }

        sendQuantity.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                dbgAssertGuiThread()
                if(!machineChangingGUI)
                {
                    paymentInProgress = null
                    updateSendBasedOnPaymentInProgress()
                }
                val sendQty = sendQuantity?.text?.toString()
                if (sendQty != null) this@MainActivity.checkSendQuantity(sendQty)
                }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }
            })

        // When the send currency type is updated, we need to also update the "approximately" line
        sendCurrencyType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                dbgAssertGuiThread()
                mainActivityModel.lastSendCurrencyType = sendCurrencyType.selectedItem.toString()  // Remember our last selection so we can prefer it after various update operations
                val sendQty = sendQuantity?.text?.toString()
                if (sendQty != null) this@MainActivity.checkSendQuantity(sendQty)
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }

        // When the send coin type is updated, we need to make sure any destination address is valid for that blockchain
        sendCoinType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                dbgAssertGuiThread()
                val sct = sendCoinType.selectedItem.toString()
                val c = coins[sct]
                if (c != null) try
                {
                    mainActivityModel.lastSendCoinType = sct
                    val sendAddr = PayAddress(sendToAddress.text.toString())
                    if (c.chainSelector != sendAddr.blockchain)
                    {
                        displayError(R.string.chainIncompatibleWithAddress)
                        updateSendCoinType(sendAddr)
                    }
                }
                catch(e: PayAddressBlankException) {}  // nothing to update if its blank
                catch(e: UnknownBlockchainException) {}
                catch(e: Exception) { if (DEBUG) throw e} // ignore all problems from user input, unless in debug mode when we should analyze them
                updateSendCurrencyType()
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }


        // When the receive coin type spinner is changed, update the receive address and picture with and address from the relevant coin
        recvCoinType.onItemSelectedListener = object: AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                dbgAssertGuiThread()
                val sel = recvCoinType.selectedItem as? String
                val c = coins[sel]
                if (c != null)
                {
                    mainActivityModel.lastRecvCoinType = sel
                    val oldc = coins[cryptoCurrencyCode]
                    if (oldc != null)
                    {
                        oldc.updateReceiveAddressUI = null
                    }
                    cryptoCurrencyCode = c.currencyCode
                    c.updateReceiveAddressUI = { it -> updateReceiveAddressUI(it) }
                    updateReceiveAddressUI(c)
                }
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }
    }

    override fun onStart()
    {
        super.onStart()
        dbgAssertGuiThread()



        thread(true, true, null, "startup") {
            // Wait until stuff comes up
            while (!coinsCreated) Thread.sleep(100)

            var c1:Coin? = coins["mBCH"] // coins["mBR1"]
            c1?.setUI(balanceTicker, balanceValue, balanceUnconfirmedValue, WalletChainInfo)
            var c2:Coin? = coins["mTBCH"] // coins["mBR1"]
            c2?.setUI(balanceTicker2, balanceValue2, balanceUnconfirmedValue2, WalletChainInfo2)

            var c3:Coin? = coins["mRBCH"] // regtest
            if (REG_TEST_ONLY)
            {
                c3?.setUI(balanceTicker, balanceValue, balanceUnconfirmedValue, WalletChainInfo)
            }
            else
            {
                c3?.setUI(balanceTicker3, balanceValue3, balanceUnconfirmedValue3, null)
            }

            for (c in coins.values)
            {
                c.wallet.setOnWalletChange({ it -> onWalletChange(it) })
                c.wallet.blockchain.onChange = {it -> onBlockchainChange(it)}
                c.wallet.blockchain.net.changeCallback = { _,_ -> onWalletChange(c.wallet) }  // right now the wallet GUI update function also updates the cnxn mgr GUI display
                c.onWalletChange()  // update all wallet UI fields since just starting up
            }

            laterUI {
                dbgAssertGuiThread()
                // First time GUI setup stuff

                // Set up the crypto spinners to contain all the cryptos this wallet supports
                val coinSpinData = coins.keys.toTypedArray()
                val coinAa = ArrayAdapter(this, android.R.layout.simple_spinner_item, coinSpinData)
                sendCoinType?.setAdapter(coinAa)
                recvCoinType?.setAdapter(coinAa)

                // Restore GUI elements to their prior values
                mainActivityModel.lastRecvCoinType?.let { recvCoinType.setSelection(it) }
                mainActivityModel.lastSendCoinType?.let { sendCoinType.setSelection(it) }
                mainActivityModel.lastSendCurrencyType?.let { sendCurrencyType.setSelection(it) }



                // Set the send currency type spinner options to your default fiat currency or your currently selected crypto
                updateSendCurrencyType()

                recvCoinType.selectedItem?.let {
                    val c = coins[it.toString()]
                    c?.onUpdatedReceiveInfo(minOf(imageView.layoutParams.width, imageView.layoutParams.height, 1024)) { recvAddrStr, recvAddrQR ->
                        this@MainActivity.updateReceiveAddressUI(
                            recvAddrStr,
                            recvAddrQR
                        )
                    }
                }

                // Periodically updating GUI stuff
                updateGUI()
            }


        }

    }

    override fun onPause()
    {
        // remove GUI elements that are going to disappear because this activity is going down
        for (c in coins.values)
        {
            c.updateReceiveAddressUI = null
        }
        super.onPause()
    }

    override fun onResume()
    {
        dbgAssertGuiThread()
        super.onResume()
        val preferenceDB = getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)
        fiatCurrencyCode = preferenceDB.getString(i18n(R.string.localCurrency), "USD") ?: "USD"

        mainActivityModel.lastSendCoinType?.let { sendCoinType.setSelection(it) }
        mainActivityModel.lastRecvCoinType?.let { recvCoinType.setSelection(it) }
        mainActivityModel.lastSendCurrencyType?.let { sendCurrencyType.setSelection(it) }

        for (c in coins.values)
        {
            c.updateReceiveAddressUI = { it -> updateReceiveAddressUI(it) }
            c.onResume()
        }
    }

    override fun onDestroy()
    {
        // remove GUI elements that are going to disappear because this activity is going down
        for (c in coins.values)
        {
            c.updateReceiveAddressUI = null
        }

        sendCurrencyType.onItemSelectedListener = null
        super.onDestroy()
    }


    /** Set the send currency type spinner options to your default fiat currency or your currently selected crypto
        Might change if the user changes the default fiat or crypto */
    fun updateSendCurrencyType()
    {
        dbgAssertGuiThread()
        sendCoinType.selectedItem?.let {
            val ccAmountCode = it.toString()
            val curIdx = sendCurrencyType.selectedItemPosition  // We know that this field will be [fiat, crypto] but not which exact choices.  So save the slot and restore it after resetting the values so the UX persists by class
            val spinData = arrayOf(ccAmountCode,fiatCurrencyCode)
            val aa = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinData)
            sendCurrencyType.setAdapter(aa)
            sendCurrencyType.setSelection(curIdx)
        }
    }

    /** Updates all GUI fields that can be changed/updated */
    fun updateGUI()
    {
        dbgAssertGuiThread()

        later {
            for (c in coins.values)
            {
                c.getXchgRates(fiatCurrencyCode)
            }
        }

        // Check consistency between sendToAddress and sendCoinType
        try
        {
            val sta = sendToAddress.text.toString()
            updateSendCoinType(PayAddress(sta))
        }
        catch(e: PayAddressBlankException) {}  // nothing to update if its blank
        catch(e: UnknownBlockchainException) {}
        catch(e: Exception) { if (DEBUG) throw e} // ignore all problems from user input, unless in debug mode when we should analyze them

        updateSendCurrencyType()

        checkSendQuantity(sendQuantity.text.toString())

        coins[cryptoCurrencyCode]?.let { updateReceiveAddressUI(it) }

        // Process the intent that caused this activity to resume
        if (intent.scheme != null)  // its null if normal app startup
        {
            handleNewIntent(intent)
        }

        // Look in the paste buffer
        if (sendToAddress.text.toString() == "")  // App started or resumed with nothing in the send field -- let's see if there's something in the paste buffer we can auto-populate
        {
            try
            {
                handlePastedData()
            }
            catch(e: PasteEmptyException)  // nothing to do, having pasted data is optional on startup
            {

            }
            catch(e: PayAddressBlankException)  // nothing to do, having pasted data is optional on startup
            {

            }
            catch(e: PayAddressDecodeException)  // nothing to do, having pasted data is optional on startup
            {
            }
            catch(e: Exception)
            {
                //LogIt.info(sourceLoc() +" paste exception:")  // there could be random data in the paste, so be tolerant of whatever garbage might be in there but log it
                //LogIt.info(sourceLoc() + Log.getStackTraceString(e))

                displayNotice(R.string.pasteIgnored)  // TODO: we don't want to display an exception for any random data, just for stuff that looks a bit like a crypto destination
                sendToAddress.text.clear()
            }

        }

        for (c in coins)
        {
            c.value.onWalletChange()
        }
    }

    override fun onNewIntent(intent: Intent?)
    {
        super.onNewIntent(intent)
        intent?.let {handleNewIntent(it) }
    }

    //? This function grabs data from the clipboard and puts it into the appropriate fields (sendToAddress and sendQuantity)
    fun handlePastedData()
    {
        var myClipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = myClipboard.getPrimaryClip() ?: throw PasteEmptyException()
        if (clip.itemCount == 0) throw PasteEmptyException()
        val item = clip.getItemAt(0)
        val text = item.text.toString().trim()
        if (text == lastPaste) return  // Already handled it

        if (text != "")
        {
            // Clean out an old payment protocol if you are pasting a new send in
            paymentInProgress = null
            updateSendBasedOnPaymentInProgress()

            lastPaste = text
            if (text.contains('?'))  // BIP21 or BIP70
            {
                for (c in coins.values)
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
                val a = PayAddress(text) // attempt to convert into an address to trigger an exception and a subsequent UI error if its bad
                laterUI { updateSendAddress(a) }
                return
            }
            throw PasteUnintelligibleException()
        }
        else
        {
            throw PasteEmptyException()
        }

    }

    fun updateSendCoinType(chainSelector: ChainSelector)
    {
        dbgAssertGuiThread()

        // First see if the current selection is compatible with what we want.
        // This keeps the user's selection if multiple accounts are compatible
        val sc = sendCoinType.selectedItem
        var curCoin = coins[sc]
        if (curCoin?.chainSelector != chainSelector)  // Its not so find one that is
        {
            curCoin = app!!.coinFor(chainSelector)
            curCoin?.let {
                sendCoinType.setSelection(it.currencyCode)
            }
        }
    }

    fun updateSendCoinType(pa: PayAddress)
    {
        if (pa.type == PayAddressType.NONE) return  // nothing to update
        updateSendCoinType(pa.blockchain)
    }

    /** Update the GUI send address field, and all related GUI elements based on the provided payment address */
    fun updateSendAddress(pa: PayAddress)
    {
        if (pa.type == PayAddressType.NONE) return  // nothing to update
        dbgAssertGuiThread()

        sendToAddress.text.clear()
        sendToAddress.text.append(pa.toString())

        paymentInProgress = null
        updateSendBasedOnPaymentInProgress()

        // Change the send currency type to reflect the pasted data if I need to
        updateSendCoinType(pa)

        // Update the sendCurrencyType field to contain our coin selection
        updateSendCurrencyType()
    }

    //? A new intent to pay someone could come from either startup (onResume) or just on it own (onNewIntent) so create a single function to deal with both
    fun handleNewIntent(receivedIntent: Intent)
    {
        val iuri = receivedIntent.toUri(0)  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME
        LogIt.info("on new Intent: " + iuri)
        try
        {
            for (c in coins.values)
            {
                if (receivedIntent.scheme != null)  // its null if normal app startup
                {
                    if (receivedIntent.scheme == c.chain.uriScheme)
                    {
                        handleSendURI(iuri)
                        break
                    }
                    else  // This should never happen because the AndroidManifest.xml Intent filter should match the URIs that we handle
                    {
                        displayError("bad link " + receivedIntent.scheme)
                    }
                }
            }
        }
        catch(e: Exception)
        {
            displayException(e)
        }
    }


    fun updateSendBasedOnPaymentInProgress()
    {
        dbgAssertGuiThread()
        try
        {
            machineChangingGUI = true
            val pip = paymentInProgress
            if (pip == null)
            {
                if (TopInformation != null)
                {
                    TopInformation.text = ""
                    TopInformation.visibility = View.GONE
                }
            }
            else
            {
                val chainSelector = pip.crypto
                if (chainSelector == null)
                {
                    paymentInProgress = null
                    displayError((R.string.badCryptoCode))
                    return
                }
                val coin = app?.coinFor(chainSelector)
                if (coin == null)
                {
                    paymentInProgress = null
                    displayError((R.string.badCryptoCode))
                }
                else
                {
                    updateSendCoinType(pip.outputs[0].chainSelector)
                    // Update the sendCurrencyType field to contain our coin selection
                    updateSendCurrencyType()

                    val amt = coin.fromFinestUnit(pip.totalSatoshis)
                    sendQuantity.text.clear()
                    sendQuantity.text.append(mBchFormat.format(amt))
                    checkSendQuantity(sendQuantity.text.toString())

                    if (pip.memo != null)
                    {
                        TopInformation.text = pip.memo
                        TopInformation.visibility = View.VISIBLE
                    }

                    /*
                        if (pip.outputs.size > 1)
                        {
                            sendToAddress.text.clear()
                            sendToAddress.text.append(i18n(R.string.multiple))
                        } */

                    sendToAddress.text.clear()
                    var count = 0
                    for (out in pip.outputs)
                    {
                        val addr = out.script.address.toString()
                        if (addr != null)
                        {
                            if (count > 0) sendToAddress.text.append(" ")
                            sendToAddress.text.append(addr)
                        }
                        count += 1
                        if (count > 4)
                        {
                            sendToAddress.text.append("...")
                            break
                        }
                    }
                }
            }
        }
        finally
        {
            machineChangingGUI = false
        }

    }

    /** Process a BIP21 URI */
    fun handleSendURI(iuri: String)
    {
        // replace the scheme with http so we can use URL to parse it
        val index = iuri.indexOf(':')
        val scheme = iuri.take(index)
        // TODO, discover the coin from the scheme
        val u = URL("http" + iuri.drop(index))
        val attribs = u.queryMap()
        LogIt.info(u.path)
        val sta = scheme + ":" + u.path

        val bip72 = attribs["r"]
        val stramt = attribs["amount"]
        var amt:BigDecimal = BigDecimal(-1)
        if (bip72 != null)
        {
            later {
                paymentInProgress = processJsonPay(bip72)
                laterUI {
                    updateSendBasedOnPaymentInProgress()
                }
            }
            return
        }

        if (stramt != null)
        {
            amt = try
            {
                stramt.toBigDecimal(currencyMath).setScale(currencyScale)  // currencyScale because BCH may have more decimals than mBCH
            }
            catch (e: NumberFormatException)
            {
                throw BadAmountException(R.string.detailsOfBadAmountFromIntent)
            }
            amt *= uriToMbch  // convert from bch to mBch
        }

        laterUI {
            // TODO label and message
            updateSendAddress(PayAddress(sta))

            if (amt >= BigDecimal.ZERO)
            {
                sendQuantity.text.clear()
                sendQuantity.text.append(mBchFormat.format(amt))
                checkSendQuantity(sendQuantity.text.toString())
            }
        }

        // attempt to convert into an address to trigger an exception and a subsequent UI error if its bad
        PayAddress(sta)
    }

    /** actually update the UI elements based on the provided data.  Must be called in the GUI context */
    fun updateReceiveAddressUI(recvAddrStr: String, recvAddrQR: Bitmap)
    {
        dbgAssertGuiThread()
        imageView.setImageBitmap(recvAddrQR)
        receiveAddress.text = recvAddrStr

        val receiveAddrSendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, recvAddrStr)
                type = "text/plain"
            }

        laterUI {// somehow this code can cause the GUI to lock up for awhile
            shareActionProvider?.setShareIntent(receiveAddrSendIntent)
        }
    }

    /** actually update the UI elements based on the provided data.  Must be called in the GUI context */
    fun updateReceiveAddressUI(coin: Coin)
    {
        laterUI {
            if (recvCoinType?.selectedItem?.toString() == coin.currencyCode)  // Only update the UI if this coin is selected to be received
            {
                coin.ifUpdatedReceiveInfo(minOf(imageView.layoutParams.width, imageView.layoutParams.height, 1024)) { recvAddrStr, recvAddrQR -> updateReceiveAddressUI(recvAddrStr, recvAddrQR) }
            }
        }
    }


    /** Calculate whether there is enough money available to make a payment and return an appropriate info string for the GUI. Does not need to be called within GUI context */
    fun availabilityWarning(coin: Coin, qty:BigDecimal): String
    {
        val cbal = coin.balance
        val ubal = coin.unconfirmedBalance
        if (cbal + ubal < qty)
        {
            return " " + i18n(R.string.moreThanAvailable)
        }
        if (cbal < qty)
        {
            return " " + i18n(R.string.moreThanConfirmedAvailable)
        }
        return ""
    }

    /** This function both validates the quantity in the send field (return true/false) and updates the "approximately..." text
        It is therefore called both as an onchange validator and directly (when the local currency changes, for example).
    */
    fun checkSendQuantity(s: String): Boolean
    {
        dbgAssertGuiThread()
        var coinType: String? = sendCoinType.selectedItem as? String
        var currencyType: String? = sendCurrencyType.selectedItem as? String
        if (currencyType == null) return true

        val coin = coins[coinType]
        if (coin == null)
        {
            approximatelyText.text = i18n(R.string.badCurrencyUnit)
            return true // send quantity is valid or irrelevant since the currency type is unknown
        }

        // This sets the scale assuming mBch.  mBch will have more decimals (5) than fiat (2) so we are ok
        val qty = try { s.toBigDecimal(currencyMath).setScale(mBchDecimals) } catch(e:NumberFormatException)
        {
            approximatelyText.text = i18n(R.string.invalidQuantity)
            return false
        }

        if (currencyType == coin.currencyCode)
        {
            if (coin.fiatPerCoin != 0.toBigDecimal())
            {
                var fiatDisplay = qty * coin.fiatPerCoin
                approximatelyText.text = i18n(R.string.approximatelyT) % mapOf("qty" to fiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) + availabilityWarning(coin, qty)
                return true
            }
            else
            {
                approximatelyText.text = i18n(R.string.retrievingExchangeRate)
                return true
            }

        }
        else
        {
            val fiatPerCoin = coin.fiatPerCoin
            try
            {
                val mbchToSend = qty / fiatPerCoin
                approximatelyText.text = i18n(R.string.actuallySendingT) % mapOf("qty" to mBchFormat.format(mbchToSend), "crypto" to coin.currencyCode) + availabilityWarning(coin, mbchToSend)
                return true
            }
                catch(e: ArithmeticException)  // Division by zero
            {
                approximatelyText.text = i18n(R.string.retrievingExchangeRate)
                return true
            }
        }
    }

    /** this handles the result of a QR code scan.  We want to accept QR codes of any different format and "do what I mean" based on the QR code's contents */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        LogIt.info("activity completed $requestCode $resultCode")

        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if(result != null)
        {
            if(result.contents != null)
            {
                val QRstring = result.contents.toString()
                // TODO parse other QR code formats
                LogIt.info("QR result: " + QRstring)

                val uri = QRstring.split(":")[0]
                if (uri == IDENTITY_URI_SCHEME)
                {
                    LogIt.info("starting identity operation activity")
                    var intent = Intent(this, IdentityOpActivity::class.java)
                    intent.data = Uri.parse(QRstring)
                    startActivity(intent)
                    return
                }
                else
                {
                    handleSendURI(result.contents)
                }

            }
            else
            {
                laterUI {
                    sendToAddress.text.clear()
                    sendToAddress.text.append(i18n(R.string.scanFailed))
                }
            }
        }
        else
        {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /** Inflate the options menu */
    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.options_menu, menu);

         // Locate MenuItem with ShareActionProvider
        val item = menu.findItem(R.id.menu_item_share)
        // Fetch and store ShareActionProvider
        shareActionProvider = MenuItemCompat.getActionProvider(item) as? ShareActionProvider

        val item2 = menu.findItem(R.id.settings)
        LogIt.info(item2.toString())
        item2.intent = Intent(this, settings::class.java).apply { putExtra(SETTINGS_MESSAGE, "") }

        return true
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?)
    {
        super.onSaveInstanceState(outState, outPersistentState)
        if (outState != null)
        {
            outState.putString("sendToAddress", sendToAddress.text.toString())
            outState.putString("sendQuantity", sendQuantity.text.toString())
            outState.putString("sendCurrencyType", sendCurrencyType.selectedItem as String)
            outState.putString("recvCoinType", recvCoinType.selectedItem as String )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    /** If user clicks on the receive address, copy it to the clipboard */
    fun onReceiveAddrTextClicked(view: View):  Boolean
    {
            try
            {
                var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val coin = coins[cryptoCurrencyCode]
                if (coin == null) throw BadCryptoException(R.string.badCryptoCode)

                val recvAddrStr: String? = coin.currentReceive?.address?.toString()

                if (recvAddrStr != null)
                {
                    var clip = ClipData.newPlainText("text", recvAddrStr)
                    clipboard.primaryClip = clip

                    // visual bling that indicates text copied
                    receiveAddress.text = i18n(R.string.copied)
                    laterUI {
                        delay(3000); coins[cryptoCurrencyCode]?.let { updateReceiveAddressUI(it) }
                    }
                }
                else throw UnavailableException(R.string.receiveAddressUnavailable)
            }
            catch (e: Exception)
            {
                displayException(e)
            }

        return true

    }

    @Suppress("UNUSED_PARAMETER")
    /** Start the split bill activity when the split bill button is pressed */
    public fun onSplitBill(v: View): Boolean
    {
        val intent = Intent(this@MainActivity, SplitBillActivity::class.java)
        startActivity(intent)
        return true
    }


    /** Create and post a transaction when the send button is pressed */
    fun onBalanceTickerClicked(view: View)
    {
        dbgAssertGuiThread()
        val ticker = (view as TextView).text
        LogIt.info("balance ticker clicked: " + ticker)
        val intent = Intent(this@MainActivity, TxHistoryActivity::class.java)
        intent.putExtra("WalletName", ticker)
        startActivity(intent)

    }

        @Suppress("UNUSED_PARAMETER")
    /** Start the purchase activity when the purchase button is pressed */
    public fun onPurchaseButton(v: View): Boolean
    {
        //val intent = Intent(this@MainActivity, InvoicesActivity::class.java)
        //startActivity(intent)
        return true
    }

    public fun paymentInProgressSend()
    {
        var tx:BCHtransaction? = null
        try
        {
            val pip = paymentInProgress
            if (pip == null) return

            // Which crypto are we sending
            var walletName = sendCoinType.selectedItem as String

            val account = coins[walletName]
            if (account == null)
            {
                displayError(R.string.badCryptoCode)
                return
            }
            if (account.chainSelector != pip.crypto)
            {
                displayError(R.string.incompatibleAccount)
                return
            }

            val tx = account.wallet.prepareSend(pip.outputs, 1)  // Bitpay does not allow zero-conf payments -- fix if other payment protocol servers support zero-conf
            // If prepareSend succeeds, we must wrap all further logic in a try catch to ensure that the protocol succeeds or is aborted so that inputs are recovered
            try
            {
                completeJsonPay(pip, tx)
                account.wallet.send(tx)  // If the payment protocol completes, help the merchant by broadcasting the tx, and also mark the inputs as spent in my wallet
            }
            catch(e:Exception)
            {
                account.wallet.abortTransaction(tx)
                throw e
            }
            displayNotice(R.string.sendSuccess)
            paymentInProgress = null
            laterUI {
                sendToAddress.text.clear()
                updateSendBasedOnPaymentInProgress()
            }
        }
        catch(e:Exception)
        {
            displayException(e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    /** Create and post a transaction when the send button is pressed */
    public fun onSendButtonClicked(v: View): Boolean
    {
        dbgAssertGuiThread()
        LogIt.info("send button clicked")
        hideKeyboard()
        sendQuantity.clearFocus()

        if (paymentInProgress != null)
        {
            coMiscScope.launch {
                paymentInProgressSend()
            }
            return true
        }

        val destAddr = sendToAddress.text.toString()
        val amtstr: String = sendQuantity.text.toString()

        var amount = try { amtstr.toBigDecimal(currencyMath).setScale(currencyScale)
        }
        catch (e: NumberFormatException)
        {
            displayError(R.string.badAmount)
            return false
        }

        var currencyType: String? = sendCurrencyType.selectedItem as String?

        if (currencyType == null) throw BadCryptoException()

        // Which crypto are we sending
        var cryptoCode = sendCoinType.selectedItem as String

        val coin = coins[cryptoCode]
        if (coin == null) throw BadCryptoException()

        // Make sure the address is consistent with the selected coin to send
        val sendAddr = try { PayAddress(sendToAddress.text.toString()) }
        catch(e:UnknownBlockchainException)
        {
            displayError(R.string.badAddress)
            return false
        }
        if (coin.chainSelector != sendAddr.blockchain)
        {
            displayError(R.string.chainIncompatibleWithAddress)
            return false
        }

        if (currencyType == coin.currencyCode)
        {
        }
        else if (currencyType == fiatCurrencyCode)
        {
            if (coin.fiatPerCoin != BigDecimal.ZERO)
                amount = amount / coin.fiatPerCoin
            else throw UnavailableException(R.string.retrievingExchangeRate)
        }
        else throw BadUnitException()

        // TODO reenter password based on how much is being spent

        coMiscScope.launch {  // avoid network on main thread exception
            try
            {
                val atomAmt = coin.toFinestUnit(amount)
                coin.wallet.send(atomAmt, destAddr)
                onSendSuccess()
            }
            catch (e: Exception)  // We don't want to crash, we want to tell the user what went wrong
            {
                displayException(e)
            }
        }

        return true
    }

    fun onSendSuccess()
    {
        // TODO Some visual and audible bling
        displayNotice(R.string.sendSuccess)
        laterUI {
            sendToAddress.text.clear()
        }
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
}


@Throws(WriterException::class)
fun TextToImageEncode(value: String, size: Int): Bitmap?
{
    val bitMatrix: BitMatrix

    val hintsMap = mapOf<EncodeHintType, Any>(
        EncodeHintType.CHARACTER_SET to "utf-8",
        EncodeHintType.MARGIN to 1)
    // //hintsMap.put(EncodeHintType.ERROR_CORRECTION, mErrorCorrectionLevel);
    try
    {
        bitMatrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hintsMap)
    }
    catch (e: IllegalArgumentException)
    {

        return null
    }


    val bitMatrixWidth = bitMatrix.getWidth()

    val bitMatrixHeight = bitMatrix.getHeight()

    val pixels = IntArray(bitMatrixWidth * bitMatrixHeight)


    val white:Int = appContext?.let { ContextCompat.getColor(it.context, R.color.white) } ?: 0xFFFFFFFF.toInt();
    val black:Int = appContext?.let { ContextCompat.getColor(it.context, R.color.black) } ?: 0xFF000000.toInt();

    var offset = 0
    for (y in 0 until bitMatrixHeight)
    {
        for (x in 0 until bitMatrixWidth)
        {
            pixels[offset] = if (bitMatrix.get(x, y))
                black
            else
                white
            offset += 1
        }
    }

    LogIt.info("Encode image for $value")
    if (value.contains("Pay2"))
    {
        LogIt.info("Bad image string")
    }
    val bitmap = Bitmap.createBitmap(pixels, bitMatrixWidth, bitMatrixHeight, Bitmap.Config.RGB_565)

    //bitmap.setPixels(pixels, 0, bitMatrixWidth, 0, 0, bitMatrixWidth, bitMatrixHeight)
    return bitmap
}
