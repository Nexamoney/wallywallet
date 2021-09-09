// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.*
import android.graphics.Bitmap
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.view.MenuItemCompat
import bitcoinunlimited.libbitcoincash.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URL
import java.util.logging.Logger
import kotlin.concurrent.thread

private val LogIt = Logger.getLogger("bitcoinunlimited.mainActivity")

val uriToMbch = 1000.toBigDecimal()  // Todo allow other currencies supported by this wallet

val ERROR_DISPLAY_TIME = 10000L
val NOTICE_DISPLAY_TIME = 4000L

/** if phone is asleep for this time, lock wallets */
val RELOCK_TIME = 5000L

val MAX_ACCOUNTS = 3 // What are the maximum # of accounts this wallet GUI can show

var SEND_ALL_TEXT = "all"  // Fixed up in onCreate when we have access to strings


open class PasteUnintelligibleException() : BUException("", i18n(R.string.pasteUnintelligible), ErrorSeverity.Expected)
open class PasteEmptyException() : BUException("", i18n(R.string.pasteIsEmpty), ErrorSeverity.Abnormal)
open class BadAmountException(msg: Int) : BUException(i18n(msg), i18n(R.string.badAmount))
open class BadCryptoException(msg: Int = -1) : BUException(i18n(msg), i18n(R.string.badCryptoCode))
open class BadUnitException(msg: Int = -1) : BUException(i18n(msg), i18n(R.string.badCurrencyUnit))
open class UnavailableException(msg: Int = -1) : BUException(i18n(msg), i18n(R.string.unavailable))

var appContext: PlatformContext? = null

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

class SleepMonitor(val activity: MainActivity) : BroadcastReceiver()
{
    var screenOn = true
    var sleepStarted = 0L
    var sleepDuration = 0L

    init
    {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        activity.registerReceiver(this, filter)
    }

    override fun onReceive(context: Context?, intent: Intent)
    {
        val action = intent.action
        if (action == null) return
        if (action.equals(Intent.ACTION_SCREEN_OFF))
        {
            LogIt.info("Phone Sleep")
            sleepStarted = System.nanoTime()
            screenOn = false
        }
        else if (action.equals(Intent.ACTION_SCREEN_ON))
        {
            LogIt.info("Phone Wake")
            sleepDuration = (System.nanoTime() - sleepStarted) / 1000000  // get duration in milliseconds
            screenOn = true
            if (sleepDuration >= RELOCK_TIME)
            {
                activity.app?.lockAccounts()
                activity.laterUI {
                    activity.assignWalletsGuiSlots()
                    activity.assignCryptoSpinnerValues()
                }
            }
        }
    }

}


class MainActivity : CommonNavActivity()
{
    override var navActivityId = R.id.navigation_home

    var app: WallyApp? = null

    val accounts: MutableMap<String, Account>
        get() = app!!.accounts

    //* last paste so we don't try to paste the same thing again
    var lastPaste: String = ""

    private var shareActionProvider: ShareActionProvider? = null

    /** If there's a payment proposal that this app has seen, information about it is located here */
    var paymentInProgress: ProspectivePayment? = null

    /** If this program is changing the GUI, rather then the user, then there are some logic differences */
    var machineChangingGUI: Boolean = false

    var sleepMonitor: SleepMonitor? = null

    var curWalletsSynced: Boolean? = null

    /** If we've already put up an error for this address, don't do it again */
    var alreadyErroredAddress: PayAddress? = null

    fun onBlockchainChange(blockchain: Blockchain)
    {
        for ((_, c) in accounts)
        {
            if (c.chain == blockchain)
                c.onWalletChange()  // coin onWalletChange also updates the blockchain state GUI
        }
    }

    fun onWalletChange(wallet: Wallet)  // callback provided to the wallet code
    {
        for ((_, c) in accounts)
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

        sleepMonitor = SleepMonitor(this)

        SEND_ALL_TEXT = i18n(R.string.sendAll)

        sendToAddress.text.clear()
        sendQuantity.text.clear()

        // Load the model with persistently saves stuff when this activity is created
        if (savedInstanceState != null)
        {
            sendToAddress.text.append(savedInstanceState.getString("sendToAddress", "") ?: "")
            sendQuantity.text.append(savedInstanceState.getString("sendQuantity", "") ?: "")
            mainActivityModel.lastSendCurrencyType = savedInstanceState.getString("sendCurrencyType", defaultAccount)
            mainActivityModel.lastRecvCoinType = savedInstanceState.getString("recvCoinType", defaultAccount)
            mainActivityModel.lastSendCoinType = savedInstanceState.getString("sendCoinType", defaultAccount)
        }
        else
        {
            mainActivityModel.lastSendCurrencyType = defaultAccount
            mainActivityModel.lastRecvCoinType = defaultAccount
            mainActivityModel.lastSendCoinType = defaultAccount
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
            catch (e: Exception)
            {
                displayException(e)
            }
        }

        sendQuantity.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p0: Editable?)
            {
                dbgAssertGuiThread()
                if (!machineChangingGUI)
                {
                    paymentInProgress = null
                    updateSendBasedOnPaymentInProgress()
                }
                val sendQty = sendQuantity?.text?.toString()
                if (sendQty != null) this@MainActivity.checkSendQuantity(sendQty)
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
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
        sendAccount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                dbgAssertGuiThread()
                val sct = sendAccount.selectedItem.toString()
                val c = accounts[sct]
                if (c != null) try
                {
                    mainActivityModel.lastSendCoinType = sct
                    val sendAddr = PayAddress(sendToAddress.text.toString())
                    if (c.wallet.chainSelector != sendAddr.blockchain)
                    {
                        if (sendAddr != alreadyErroredAddress)
                        {
                            displayError(R.string.chainIncompatibleWithAddress)
                            alreadyErroredAddress = sendAddr
                        }
                        updateSendAccount(sendAddr)
                    }
                }
                catch (e: PayAddressBlankException)
                {
                }  // nothing to update if its blank
                catch (e: UnknownBlockchainException)
                {
                }
                catch (e: Exception)
                {
                    if (DEBUG) throw e
                } // ignore all problems from user input, unless in debug mode when we should analyze them
                updateSendCurrencyType()
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }


        // When the receive coin type spinner is changed, update the receive address and picture with and address from the relevant coin
        recvCoinType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                dbgAssertGuiThread()
                val sel = recvCoinType.selectedItem as? String
                val c = accounts[sel]
                if (c != null)
                {
                    mainActivityModel.lastRecvCoinType = sel
                    val oldc = accounts[defaultAccount]
                    if (oldc != null)
                    {
                        oldc.updateReceiveAddressUI = null
                    }
                    defaultAccount = c.name
                    c.updateReceiveAddressUI = { it -> updateReceiveAddressUI(it) }
                    updateReceiveAddressUI(c)
                }
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }
    }

    fun clearAccountUI()
    {
        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        val showDev = prefs.getBoolean(SHOW_DEV_INFO, false)

        data class UI(val ticker: TextView, val balance: TextView, val units: TextView, val unconf: TextView, val info: TextView?)

        val ui = listOf(UI(balanceTicker, balanceValue, balanceUnits, balanceUnconfirmedValue, WalletChainInfo),
            UI(balanceTicker2, balanceValue2, balanceUnits2, balanceUnconfirmedValue2, WalletChainInfo2),
            UI(balanceTicker3, balanceValue3, balanceUnits3, balanceUnconfirmedValue3, WalletChainInfo3))

        // Clear all info in case we remap it
        for (u in ui)
        {
            u.ticker.text = ""
            u.balance.text = ""
            u.units.text = ""
            u.unconf.text = ""
            u.info?.text = ""
            u.info?.visibility = if (showDev) View.VISIBLE else View.GONE

            // Invalidate so that the image gets cleared to keep accounts hidden during unlock
            u.ticker.invalidate()
            u.balance.invalidate()
            u.units.invalidate()
            u.unconf.invalidate()
            u.info?.invalidate()
        }
    }

    fun assignWalletsGuiSlots()
    {
        dbgAssertGuiThread()
        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        // val showDev = prefs.getBoolean(SHOW_DEV_INFO, false)

        data class UI(val ticker: TextView, val balance: TextView, val units: TextView, val unconf: TextView, val info: TextView?)

        val ui = listOf(UI(balanceTicker, balanceValue, balanceUnits, balanceUnconfirmedValue, WalletChainInfo),
            UI(balanceTicker2, balanceValue2, balanceUnits2, balanceUnconfirmedValue2, WalletChainInfo2),
            UI(balanceTicker3, balanceValue3, balanceUnits3, balanceUnconfirmedValue3, WalletChainInfo3))

        clearAccountUI()

        var uiLoc = 1  // Start at 1 because spot 0 is reserved for the primary wallet

        var foundAPrimary = false
        for (ac in accounts.values)
        {
            var uiSet = false
            if (ac.visible)
            {
                val curLoc = if (!foundAPrimary && (ac.currencyCode == PRIMARY_WALLET))
                {
                    foundAPrimary = true; 0
                }
                else uiLoc
                if (curLoc >= ui.size) continue
                ui[curLoc].let {
                    ac.setUI(it.ticker, it.balance, it.unconf, it.info)
                    uiSet = true
                    laterUI { ui[curLoc].units.text = ac.currencyCode }
                }
                if (curLoc != 0) uiLoc++
            }
            if (!uiSet)
            {
                ac.setUI(null, null, null, null)
            }
        }

        for (c in accounts.values)
        {
            c.wallet.setOnWalletChange({ it -> onWalletChange(it) })
            c.wallet.blockchain.onChange = { it -> onBlockchainChange(it) }
            c.wallet.blockchain.net.changeCallback = { _, _ -> onWalletChange(c.wallet) }  // right now the wallet GUI update function also updates the cnxn mgr GUI display
            c.onWalletChange()  // update all wallet UI fields since just starting up
        }
    }

    fun assignCryptoSpinnerValues()
    {
        // Set up the crypto spinners to contain all the cryptos this wallet supports
        val a = app
        if (a != null)
        {
            val coinSpinData = a.visibleAccountNames()
            val sendSpinData = coinSpinData.toMutableList()
            sendSpinData.add(i18n(R.string.choose))
            val coinAa = ArrayAdapter(this, android.R.layout.simple_spinner_item, sendSpinData)
            sendAccount?.setAdapter(coinAa)
            val coinRecvAa = ArrayAdapter(this, android.R.layout.simple_spinner_item, coinSpinData)
            recvCoinType?.setAdapter(coinRecvAa)
        }
    }

    override fun onStart()
    {
        super.onStart()
        dbgAssertGuiThread()
        appContext = PlatformContext(applicationContext)

        thread(true, true, null, "startup")
        {
            // Wait until stuff comes up
            while (!coinsCreated) Thread.sleep(50)
            LogIt.info("coins created")
            Thread.sleep(50)

            laterUI {
                dbgAssertGuiThread()
                // First time GUI setup stuff

                assignWalletsGuiSlots()
                assignCryptoSpinnerValues()
                // Restore GUI elements to their prior values
                mainActivityModel.lastRecvCoinType?.let { recvCoinType.setSelection(it) }
                mainActivityModel.lastSendCoinType?.let { sendAccount.setSelection(it) }
                mainActivityModel.lastSendCurrencyType?.let { sendCurrencyType.setSelection(it) }

                // Set the send currency type spinner options to your default fiat currency or your currently selected crypto
                updateSendCurrencyType()

                recvCoinType.selectedItem?.let {
                    val c = accounts[it.toString()]
                    c?.onUpdatedReceiveInfo(minOf(GuiReceiveQRCode.layoutParams.width, GuiReceiveQRCode.layoutParams.height, 1024)) { recvAddrStr, recvAddrQR ->
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

        if (app?.firstRun == true)
        {
            LogIt.info("starting welcome activity")
            var intent = Intent(this, Welcome::class.java)
            startActivity(intent)
        }

    }

    override fun onPause()
    {
        LogIt.info("Wally is pausing")

        // remove GUI elements that are going to disappear because this activity is going down
        for (c in accounts.values)
        {
            c.updateReceiveAddressUI = null
        }
        clearAccountUI()
        super.onPause()
    }

    override fun onResume()
    {
        LogIt.info("Wally is resuming")
        dbgAssertGuiThread()
        clearAccountUI()
        super.onResume()

        // This code pops out of the main activity is the child requests it.  This is needed when an external intent directly
        // spawns a child activity of wally's main activity, but upon completion of that child we want to drop back to the
        // spawner not to wally's main screen
        wallyApp?.let {
            if (it.finishParent > 0)
            {
                it.finishParent = 0
                finish()
                return
            }
        }

        appContext = PlatformContext(applicationContext)
        val preferenceDB = getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)
        fiatCurrencyCode = preferenceDB.getString(LOCAL_CURRENCY_PREF, "USD") ?: "USD"
        xchgRateText?.text = ""

        mainActivityModel.lastSendCoinType?.let { sendAccount.setSelection(it) }
        mainActivityModel.lastRecvCoinType?.let { recvCoinType.setSelection(it) }
        mainActivityModel.lastSendCurrencyType?.let { sendCurrencyType.setSelection(it) }

                // Look in the paste buffer
        if (sendToAddress.text.toString() == "")  // App started or resumed with nothing in the send field -- let's see if there's something in the paste buffer we can auto-populate
        {
            try
            {
                handlePastedData()

            }
            catch (e: PasteEmptyException)  // nothing to do, having pasted data is optional on startup
            {

            }
            catch (e: PayAddressBlankException)  // nothing to do, having pasted data is optional on startup
            {

            }
            catch (e: PayAddressDecodeException)  // nothing to do, having pasted data is optional on startup
            {
            }
            catch (e: Exception)
            {
                //LogIt.info(sourceLoc() +" paste exception:")  // there could be random data in the paste, so be tolerant of whatever garbage might be in there but log it
                //LogIt.info(sourceLoc() + Log.getStackTraceString(e))

                // displayNotice(R.string.pasteIgnored)  // TODO: we don't want to display an exception for any random data, just for stuff that looks a bit like a crypto destination
                sendToAddress.text.clear()
            }
        }

        later {
            // Thread.sleep(100)  // Wait for accounts to be loaded
            laterUI {
                assignWalletsGuiSlots()
                assignCryptoSpinnerValues()

                for (c in accounts.values)
                {
                    c.updateReceiveAddressUI = { it -> updateReceiveAddressUI(it) }
                    c.onResume()
                }
            }
        }
        // Poll the syncing icon update because it doesn't matter how long it takes
        laterUI {
            while(true)
            {
                updateSyncingIcon()
                delay(7000)
            }
        }
    }

    override fun onDestroy()
    {
        // remove GUI elements that are going to disappear because this activity is going down
        for (c in accounts.values)
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
        sendAccount.selectedItem?.let {
            val account = accounts[it.toString()]
            account?.let { acc ->
                val curIdx =
                    sendCurrencyType.selectedItemPosition  // We know that this field will be [fiat, crypto] but not which exact choices.  So save the slot and restore it after resetting the values so the UX persists by class
                val spinData = arrayOf(acc.currencyCode, fiatCurrencyCode)
                val aa = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinData)
                sendCurrencyType.setAdapter(aa)
                sendCurrencyType.setSelection(curIdx)
            }
        }
    }

    /** Updates all GUI fields that can be changed/updated */
    fun updateGUI()
    {
        dbgAssertGuiThread()

        later {
            for (c in accounts.values)
            {
                c.getXchgRates(fiatCurrencyCode)
            }
        }

        // Check consistency between sendToAddress and sendCoinType
        try
        {
            val sta = sendToAddress.text.toString()
            updateSendAccount(PayAddress(sta))
        }
        catch (e: PayAddressBlankException)
        {
        }  // nothing to update if its blank
        catch (e: UnknownBlockchainException)
        {
        }
        catch (e: Exception)
        {
            if (DEBUG) throw e
        } // ignore all problems from user input, unless in debug mode when we should analyze them

        updateSendCurrencyType()

        checkSendQuantity(sendQuantity.text.toString())

        accounts[defaultAccount]?.let { updateReceiveAddressUI(it) }

        // Process the intent that caused this activity to resume
        if (intent.scheme != null)  // its null if normal app startup
        {
            handleNewIntent(intent)
        }

        for (c in accounts)
        {
            c.value.onWalletChange()
        }

        updateSyncingIcon()
    }

    fun updateSyncingIcon()
    {
        dbgAssertGuiThread()
        val walletsSynced = syncNotInUI {
            var walletsSynced = true
            for (c in accounts)
            {
                if (c.value.visible && !c.value.wallet.synced()) walletsSynced = false
            }
            return@syncNotInUI walletsSynced
        }

        if (!walletsSynced)
        {
            if (curWalletsSynced != false)
            {
                val d: AnimatedVectorDrawable = getDrawable(R.drawable.ani_syncing) as AnimatedVectorDrawable // Insert your AnimatedVectorDrawable resource identifier
                syncingIcon.setImageDrawable(d)
                d.start()
            }

        }
        else
        {
            if (curWalletsSynced != true)
            {
                syncingIcon.setImageDrawable(getDrawable(R.drawable.ic_check))
            }
        }
        curWalletsSynced = walletsSynced
    }

    override fun onNewIntent(intent: Intent?)
    {
        super.onNewIntent(intent)
        intent?.let { handleNewIntent(it) }
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

    /** Find an account that can send to this blockchain and switch the send account to it */
    fun updateSendAccount(chainSelector: ChainSelector)
    {
        dbgAssertGuiThread()

        // First see if the current selection is compatible with what we want.
        // This keeps the user's selection if multiple accounts are compatible
        val sc = sendAccount.selectedItem
        val curAccount = accounts[sc]
        if (curAccount?.wallet?.chainSelector != chainSelector)  // Its not so find one that is
        {
            val matches = app!!.accountsFor(chainSelector)
            if (matches.size > 1)
                sendAccount.setSelection(i18n(R.string.choose))
            else if (matches.size == 1)
                sendAccount.setSelection(matches[0].name)
        }
    }

    fun updateSendAccount(pa: PayAddress)
    {
        if (pa.type == PayAddressType.NONE) return  // nothing to update
        updateSendAccount(pa.blockchain)
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
        updateSendAccount(pa)

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
            for (c in accounts.values)
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
        catch (e: Exception)
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
                val a = app
                if (a == null) return
                val acts = a.accountsFor(chainSelector)

                var amt: BigDecimal = BigDecimal.ZERO
                val coin = if (acts.size == 0)
                {
                    paymentInProgress = null
                    displayNotice((R.string.badCryptoCode))
                    amt = a.primaryAccount.fromFinestUnit(pip.totalSatoshis)
                    null
                }
                else if (acts.size > 1)
                {
                    sendAccount.setSelection(i18n(R.string.choose))
                    amt = acts[0].fromFinestUnit(pip.totalSatoshis)
                    acts[0]  //
                }
                else
                {
                    amt = acts[0].fromFinestUnit(pip.totalSatoshis)
                    acts[0]
                }

                if (true)
                {
                    updateSendAccount(pip.outputs[0].chainSelector)
                    // Update the sendCurrencyType field to contain our coin selection
                    updateSendCurrencyType()

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
                        if (true) // addr != null)
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
        var amt: BigDecimal = BigDecimal(-1)
        if (bip72 != null)
        {
            later {
                try
                {
                    paymentInProgress = processJsonPay(bip72)
                    laterUI {
                        updateSendBasedOnPaymentInProgress()
                    }
                }
                catch (e: Bip70Exception)
                {
                    e.message?.let {
                        displayError(it)
                    }
                }
                catch (e: java.lang.Exception)
                {
                    e.message?.let {
                        displayError(it)
                    }
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
            catch (e: ArithmeticException)  // Rounding error
            {
                // If someone is asking for sub-satoshi quantities, round up and overpay them
                LogIt.warning("Sub-satoshi quantity ${stramt} requested.  Rounding up")
                stramt.toBigDecimal().setScale(bchDecimals, RoundingMode.UP)
            }
            amt *= uriToMbch  // convert from bch to mBch
        }

        laterUI {
            // TODO label and message
            try
            {
                if (isCashAddrScheme(scheme))  // Handle cashaddr upper/lowercase stuff
                {
                    val lc = sta.toLowerCase()
                    val uc = sta.toUpperCase()
                    if (uc.contentEquals(sta) || lc.contentEquals(sta))  // Its all uppercase or all uppercase
                    {
                        updateSendAddress(PayAddress(lc))
                    }
                    else  // Mixed upper/lower case not allowed
                    {
                        displayError(R.string.badAddress)
                        return@laterUI
                    }
                }
                else
                {
                    updateSendAddress(PayAddress(sta))
                }
            }
            catch (e: UnknownBlockchainException)
            {
                displayError(R.string.badAddress)
                return@laterUI
            }

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
        if (recvAddrStr != receiveAddress.text)  // Only update if something has changed
        {
            GuiReceiveQRCode.setImageBitmap(recvAddrQR)
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
    }

    /** actually update the UI elements based on the provided data.  Must be called in the GUI context */
    fun updateReceiveAddressUI(account: Account)
    {
        laterUI {
            if (recvCoinType?.selectedItem?.toString() == account.name)  // Only update the UI if this coin is selected to be received
            {
                account.ifUpdatedReceiveInfo(minOf(GuiReceiveQRCode.layoutParams.width, GuiReceiveQRCode.layoutParams.height, 1024)) { recvAddrStr, recvAddrQR ->
                    updateReceiveAddressUI(recvAddrStr,
                        recvAddrQR)
                }
            }
        }
    }


    /** Calculate whether there is enough money available to make a payment and return an appropriate info string for the GUI. Does not need to be called within GUI context */
    fun availabilityWarning(account: Account, qty: BigDecimal): String
    {
        val cbal = account.balance
        val ubal = account.unconfirmedBalance
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
        if (s == "")
        {
            approximatelyText.text = i18n(R.string.emptyQuantityField)
            return false
        }
        var coinType: String? = sendAccount.selectedItem as? String
        var currencyType: String? = sendCurrencyType.selectedItem as? String
        if (currencyType == null) return true

        val coin = accounts[coinType]
        if (coin == null)
        {
            approximatelyText.text = i18n(R.string.badCurrencyUnit)
            return true // send quantity is valid or irrelevant since the currency type is unknown
        }

        // This sets the scale assuming mBch.  mBch will have more decimals (5) than fiat (2) so we are ok
        val qty = try
        {
            s.toBigDecimal(currencyMath).setScale(mBchDecimals)
        }
        catch (e: NumberFormatException)
        {
            if (s == SEND_ALL_TEXT)  // Special case transferring everything
            {
                coin.fromFinestUnit(coin.wallet.balance + coin.wallet.balanceUnconfirmed)
            }
            else
            {
                approximatelyText.text = i18n(R.string.invalidQuantity)
                return false
            }
        }
        catch (e: ArithmeticException)
        {
            approximatelyText.text = i18n(R.string.invalidQuantityTooManyDecimalDigits)
            return false
        }

        if (currencyType == fiatCurrencyCode)
        {
            val fiatPerCoin = coin.fiatPerCoin
            if (coin.fiatPerCoin == -1.toBigDecimal())
            {
                approximatelyText.text = i18n(R.string.unavailableExchangeRate)
                xchgRateText?.text = ""
                return true
            }
            else
            {
                try
                {
                    approximatelyText.text = ""
                    val mbchToSend = qty / fiatPerCoin
                    val sats = coin.toFinestUnit(mbchToSend)
                    if (sats <= Dust(coin.chain.chainSelector))
                        approximatelyText.text = i18n(R.string.sendingDustWarning)
                    else
                        approximatelyText.text = i18n(R.string.actuallySendingT) % mapOf("qty" to mBchFormat.format(mbchToSend), "crypto" to coin.currencyCode) + availabilityWarning(coin, mbchToSend)
                    xchgRateText?.text = i18n(R.string.exchangeRate) % mapOf("amt" to fiatFormat.format(fiatPerCoin), "crypto" to coin.currencyCode, "fiat" to fiatCurrencyCode)
                    return true
                }
                catch (e: ArithmeticException)  // Division by zero
                {
                    xchgRateText?.text = i18n(R.string.retrievingExchangeRate)
                    return true
                }
            }
        }
        else
        {
            if (qty <= coin.fromFinestUnit(Dust(coin.chain.chainSelector)))
                approximatelyText.text = i18n(R.string.sendingDustWarning)
            else
                approximatelyText.text = ""

            if (coin.fiatPerCoin == -1.toBigDecimal())
            {
                xchgRateText?.text = i18n(R.string.unavailableExchangeRate)
                return true
            }
            else if (coin.fiatPerCoin != BigDecimal.ZERO)
            {
                var fiatDisplay = qty * coin.fiatPerCoin
                if (approximatelyText.text == "")
                    approximatelyText.text = i18n(R.string.approximatelyT) % mapOf("qty" to fiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) + availabilityWarning(coin, qty)
                xchgRateText?.text = i18n(R.string.exchangeRate) % mapOf("amt" to fiatFormat.format(coin.fiatPerCoin), "crypto" to coin.currencyCode, "fiat" to fiatCurrencyCode)
                return true
            }
            else
            {
                xchgRateText?.text = i18n(R.string.retrievingExchangeRate)
                return true
            }

        }
    }

    /** this handles the result of a QR code scan.  We want to accept QR codes of any different format and "do what I mean" based on the QR code's contents */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        LogIt.info(sourceLoc() + " activity completed $requestCode $resultCode")

        // Handle my sub-activity results
        if ((requestCode == IDENTITY_OP_RESULT) || (requestCode == TRICKLEPAY_RESULT))
        {
            if (data != null)
            {
                val details = data.getStringExtra("details")
                val err = data.getStringExtra("error")
                if (err != null) displayError(err, details)
                else  // Don't display any notices if there's an error
                {
                    val note = data.getStringExtra("notice")
                    if (note != null) displayNotice(note, details)
                }
            }
            return;
        }

        // Handle external activity results

        // QR code scanning
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null)
        {
            if (result.contents != null)
            {
                laterUI {
                    displayNotice(R.string.scanSuccess)
                    delay(2000)  // So that the notice is visible
                    val QRstring = result.contents.toString()
                    // TODO parse other QR code formats
                    LogIt.info(sourceLoc() + ": QR result: " + QRstring)

                    if (!handleAnyIntent(QRstring))
/*
                    val uri = QRstring.split(":")[0]
                    if (uri == IDENTITY_URI_SCHEME)
                    {
                        LogIt.info("starting identity operation activity")
                        var intent = Intent(this, IdentityOpActivity::class.java)
                        intent.data = Uri.parse(QRstring)
                        startActivityForResult(intent, IDENTITY_OP_RESULT)

                    }
                    else if (uri == TDPP_URI_SCHEME)
                    {
                        var intent = Intent(this, TricklePayActivity::class.java)
                        intent.data = Uri.parse(QRstring)
                        startActivityForResult(intent, TRICKLEPAY_RESULT)
                    }
                    else
 */
                    {
                        try
                        {
                            handleSendURI(result.contents)
                        }
                        catch (e: UnknownBlockchainException)
                        {
                            LogIt.info(sourceLoc() + ": QR contents invalid: " + QRstring)
                            displayError(R.string.badAddress)
                        }
                    }
                }
                return
            }
            else
            {
                displayError(R.string.scanFailed)

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
        item2.intent = Intent(this, Settings::class.java)  // .apply { putExtra(SETTINGS_MESSAGE, "") }

        val item3 = menu.findItem(R.id.unlock)
        item3.intent = Intent(this, UnlockActivity::class.java)

        val item4 = menu.findItem(R.id.help)
        item4.intent = Intent(Intent.ACTION_VIEW)
        item4.intent.setData(Uri.parse("http://www.bitcoinunlimited.net/wally/faq"))

        return super.onCreateOptionsMenu(menu)
    }

    override fun onSaveInstanceState(outState: Bundle)
    {
        if (outState != null)
        {
            super.onSaveInstanceState(outState)
            outState.putString("sendToAddress", sendToAddress.text.toString())
            outState.putString("sendQuantity", sendQuantity.text.toString())
            outState.putString("sendCurrencyType", sendCurrencyType.selectedItem as String)
            outState.putString("recvCoinType", recvCoinType.selectedItem as String)
        }
    }

    @Suppress("UNUSED_PARAMETER")
        /** If user clicks on the receive address, copy it to the clipboard */
    fun onNewAccount(view: View): Boolean
    {
        LogIt.info("new account")
        if (app?.accounts?.size ?: 1000 >= MAX_ACCOUNTS)
        {
            displayError(R.string.accountLimitReached)
            return false
        }

        val intent = Intent(this@MainActivity, NewAccount::class.java)
        startActivity(intent)
        return true
    }

    @Suppress("UNUSED_PARAMETER")
        /** If user clicks on the receive address, copy it to the clipboard */
    fun onReceiveAddrTextClicked(view: View): Boolean
    {
        try
        {
            var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val account = accounts[defaultAccount]
            if (account == null) throw BadCryptoException(R.string.badCryptoCode)

            val recvAddrStr: String? = account.currentReceive?.address?.toString()

            if (recvAddrStr != null)
            {
                var clip = ClipData.newPlainText("text", recvAddrStr)
                clipboard.setPrimaryClip(clip)

                // visual bling that indicates text copied
                receiveAddress.text = i18n(R.string.copied)
                laterUI {
                    delay(5000); accounts[defaultAccount]?.let { updateReceiveAddressUI(it) }
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
        try
        {
            dbgAssertGuiThread()
            val ticker = (view as TextView).text
            LogIt.info("balance ticker clicked: " + ticker)
            val intent = Intent(this@MainActivity, TxHistoryActivity::class.java)
            intent.putExtra("WalletName", ticker)
            startActivity(intent)
        }
        catch (e: Exception)
        {
            LogIt.warning("Exception clicking on ticker name: " + e.toString())
        }
    }

    fun onBalanceValueClicked(view: View)
    {
        try
        {
            dbgAssertGuiThread()
            val account = app?.accountFromGui(view)
            if (account == null) return

            sendQuantity.text.clear()
            sendQuantity.text.append(SEND_ALL_TEXT.toString())

            sendAccount.setSelection(account.name)
        }
        catch (e: Exception)
        {
            LogIt.warning("Exception clicking on balance: " + e.toString())
            handleThreadException(e)
        }
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
        try
        {
            val pip = paymentInProgress
            if (pip == null) return

            // Which crypto are we sending
            var walletName = sendAccount.selectedItem as String

            val account = accounts[walletName]
            if (account == null)
            {
                displayError(R.string.badCryptoCode)
                return
            }
            if (account.wallet.chainSelector != pip.crypto)
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
            catch (e: Exception)
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
        catch (e: Exception)
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
        displayNotice(R.string.Processing)
        hideKeyboard()
        sendQuantity.clearFocus()

        if (paymentInProgress != null)
        {
            coMiscScope.launch {
                paymentInProgressSend()
            }
            return true
        }

        var currencyType: String? = sendCurrencyType.selectedItem as String?
        // Which crypto are we sending
        val walletName = try
        {
            sendAccount.selectedItem as String
        }
        catch (e: TypeCastException)  // No wallets are defined so no sendCoinType is possible
        {
            displayError(R.string.badCryptoCode)
            return false
        }
        val account = accounts[walletName]


        if (currencyType == null)
        {
            displayError(R.string.badCryptoCode)
            return false
        }
        if (account == null)
        {
            displayError(R.string.badCryptoCode)
            return false
        }
        if (account.locked)
        {
            displayError(R.string.accountLocked)
            return false
        }

        val amtstr: String = sendQuantity.text.toString()

        var deductFeeFromAmount = false
        var amount = try
        {
            amtstr.toBigDecimal(currencyMath).setScale(mBchDecimals)
        }
        catch (e: NumberFormatException)
        {
            if (amtstr == SEND_ALL_TEXT)
            {
                deductFeeFromAmount = true
                account.fromFinestUnit(account.wallet.balance + account.wallet.balanceUnconfirmed)
            }
            else
            {
                displayError(R.string.badAmount)
                return false
            }
        }
        catch (e: ArithmeticException)  // Rounding error
        {
            // If someone is asking to send sub-satoshi quantities, round up and ask them to click send again.
            sendQuantity.text.clear()
            sendQuantity.text.append(amtstr.toBigDecimal().setScale(mBchDecimals, RoundingMode.UP).toString())
            displayError(R.string.badAmount)
            approximatelyText.text = i18n(R.string.roundedUpClickSendAgain)
            return false
        }

        // Make sure the address is consistent with the selected coin to send
        val sendAddr = try
        {
            PayAddress(sendToAddress.text.toString())
        }
        catch (e: WalletNotSupportedException)
        {
            displayError(R.string.badAddress)
            return false
        }
        catch (e: UnknownBlockchainException)
        {
            displayError(R.string.badAddress)
            return false
        }
        if (account.wallet.chainSelector != sendAddr.blockchain)
        {
            displayError(R.string.chainIncompatibleWithAddress)
            return false
        }
        if (sendAddr.type == PayAddressType.NONE)
        {
            displayError(R.string.badAddress)
            return false
        }

        if (currencyType == account.currencyCode)
        {
        }
        else if (currencyType == fiatCurrencyCode)
        {
            if (account.fiatPerCoin != BigDecimal.ZERO)
                amount = amount / account.fiatPerCoin
            else throw UnavailableException(R.string.retrievingExchangeRate)
        }
        else throw BadUnitException()

        // TODO reenter password based on how much is being spent

        coMiscScope.launch {  // avoid network on main thread exception
            try
            {
                val atomAmt = account.toFinestUnit(amount)
                account.wallet.send(atomAmt, sendAddr, deductFeeFromAmount)
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

