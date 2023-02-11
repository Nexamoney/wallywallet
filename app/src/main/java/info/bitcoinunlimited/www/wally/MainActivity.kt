// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.EditorInfo
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import bitcoinunlimited.libbitcoincash.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import info.bitcoinunlimited.www.wally.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URL
import java.util.*
import java.util.logging.Logger
import kotlin.concurrent.thread


private val LogIt = Logger.getLogger("BU.wally.mainActivity")

val uriToMbch = 1000.toBigDecimal()  // Todo allow other currencies supported by this wallet

val ERROR_DISPLAY_TIME = 10000L
val NOTICE_DISPLAY_TIME = 4000L

/** if phone is asleep for this time, lock wallets */
val RELOCK_TIME = 5000L

val MAX_ACCOUNTS = 10 // What are the maximum # of accounts this wallet GUI can show

var SEND_ALL_TEXT = "all"  // Fixed up in onCreate when we have access to strings

var appContext: PlatformContext? = null

class MainActivityModel
{
    //* On resume, we need to remember what send currency type was selected.  This is given to us in the data bundle provided during onCreate, but is needed later
    var lastSendCurrencyType: String? = null

    //* On resume, we need to remember what receive currency type was selected.  This is given to us in the data bundle provided during onCreate, but is needed later
    var lastRecvIntoAccount: String? = null

    //* remember last send coin selected
    var lastSendFromAccount: String? = null
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
    public lateinit var ui: ActivityMainBinding
    override var navActivityId = R.id.navigation_home

    var app: WallyApp? = null

    var dynOrStaticOrientation: Int = -1  // Used to remember the screen orientation when temporarily disabling int

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

    /** Do this once we get file read permissions */
    var doOnFileReadPerms: (() -> Unit)? = null

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
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        val ctxt = PlatformContext(applicationContext)
        appContext = ctxt

        sleepMonitor = SleepMonitor(this)

        SEND_ALL_TEXT = i18n(R.string.sendAll)

        ui.sendToAddress.text.clear()
        ui.sendQuantity.text.clear()

        // Load the model with persistently saves stuff when this activity is created
        if (savedInstanceState != null)
        {
            ui.sendToAddress.text.append(savedInstanceState.getString("sendToAddress", "") ?: "")
            ui.sendQuantity.text.append(savedInstanceState.getString("sendQuantity", "") ?: "")
            mainActivityModel.lastSendCurrencyType = savedInstanceState.getString("sendCurrencyType", defaultBlockchain)
            mainActivityModel.lastRecvIntoAccount = savedInstanceState.getString("recvCoinType", defaultBlockchain)
            mainActivityModel.lastSendFromAccount = savedInstanceState.getString("sendCoinType", defaultBlockchain)
        }
        else
        {
            mainActivityModel.lastSendCurrencyType = defaultBlockchain
            mainActivityModel.lastRecvIntoAccount = defaultBlockchain
            mainActivityModel.lastSendFromAccount = defaultBlockchain
        }

        ui.readQRCodeButton.setOnClickListener {
            dbgAssertGuiThread()
            LogIt.info("scanning for qr code")
            val v = IntentIntegrator(this)
            dynOrStaticOrientation = requestedOrientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
            v.setPrompt(i18n(R.string.scanPaymentQRcode)).setBeepEnabled(false).setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name).setOrientationLocked(true).setCameraId(0).initiateScan()
        }

        ui.pasteFromClipboardButton.setOnClickListener {
            dbgAssertGuiThread()
            //LogIt.info(sourceLoc() + ": paste button pressed")
            try
            {
                lastPaste = ""  // We never want to completely ignore an explicit paste
                handlePastedData()
            } catch (e: Exception)
            {
                displayException(e)
            }
        }

        ui.sendQuantity.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p0: Editable?)
            {
                dbgAssertGuiThread()
                if (!machineChangingGUI)
                {
                    paymentInProgress = null
                    updateSendBasedOnPaymentInProgress()
                }
                val sendQty = ui.sendQuantity?.text?.toString()
                if (sendQty != null) this@MainActivity.checkSendQuantity(sendQty)
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })

        ui.sendQuantity.setOnFocusChangeListener(OnFocusChangeListener { view, hasFocus ->
            val sqvis = if (hasFocus) View.VISIBLE else View.GONE
            ui.amountAllButton?.visibility = sqvis
            ui.amountThousandButton?.visibility = sqvis
            ui.amountMillionButton?.visibility = sqvis
        })

        // When the send currency type is updated, we need to also update the "approximately" line
        ui.sendCurrencyType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                dbgAssertGuiThread()
                mainActivityModel.lastSendCurrencyType = ui.sendCurrencyType.selectedItem.toString()  // Remember our last selection so we can prefer it after various update operations
                val sendQty = ui.sendQuantity?.text?.toString()
                if (sendQty != null) this@MainActivity.checkSendQuantity(sendQty)
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }

        // When the send coin type is updated, we need to make sure any destination address is valid for that blockchain
        ui.sendAccount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                dbgAssertGuiThread()
                val sct = ui.sendAccount.selectedItem.toString()
                val c = accounts[sct]
                if (c != null) try
                {
                    mainActivityModel.lastSendFromAccount = sct
                    val sendAddr = PayAddress(ui.sendToAddress.text.toString().trim())
                    if (c.wallet.chainSelector != sendAddr.blockchain)
                    {
                        if (sendAddr != alreadyErroredAddress)
                        {
                            displayError(R.string.chainIncompatibleWithAddress,
                              i18n(R.string.chainIncompatibleWithAddressDetails) % mapOf("walletCrypto" to (chainToCurrencyCode[c.wallet.chainSelector] ?: i18n(R.string.unknownCurrency)), "addressCrypto" to (chainToCurrencyCode[sendAddr.blockchain] ?: i18n(R.string.unknownCurrency))))

                            alreadyErroredAddress = sendAddr
                        }
                        updateSendAccount(sendAddr)
                    }
                } catch (e: PayAddressBlankException)
                {
                }  // nothing to update if its blank
                catch (e: UnknownBlockchainException)
                {
                } catch (e: Exception)
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
        ui.recvIntoAccount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                dbgAssertGuiThread()
                val sel = ui.recvIntoAccount.selectedItem as? String
                val c = accounts[sel]
                if (c != null)
                {
                    mainActivityModel.lastRecvIntoAccount = sel
                    val oldc = accounts[defaultBlockchain]
                    if (oldc != null)
                    {
                        oldc.updateReceiveAddressUI = null
                    }
                    defaultBlockchain = c.name
                    c.updateReceiveAddressUI = { it -> updateReceiveAddressUI(it) }
                    updateReceiveAddressUI(c)
                }
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        when (requestCode)
        {
            READ_FILES_PERMISSION_RESULT ->
            {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                {
                    doOnFileReadPerms?.invoke()
                }
                else
                {
                    // Explain to the user that the feature is unavailable because the features requires a permission that the user has denied.
                }
                return
            }
            // Add other 'when' lines to check for other permissions this app might request.
            else ->
            {
                // Ignore all other requests.
            }
        }

    }

    // call this with a function to execute whenever that function needs file read permissions
    fun onReadStoragePermissionGranted(doit: () -> Unit): Boolean
    {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            doit()
        else
            doOnFileReadPerms = doit
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_FILES_PERMISSION_RESULT)
        return false
    }

    fun setFocusedAccount(account: Account?)
    {
        if (account != null)
        {
            ui.recvIntoAccount.setSelection(account.name)
            ui.sendAccount.setSelection(account.name)
            updateReceiveAddressUI(account)
        }
    }

    val guiAccountList = GuiAccountList(this)

    fun clearAccountUI()
    {
        ui.AccountList?.visibility = View.INVISIBLE
        guiAccountList.changed()
    }
    fun assignWalletsGuiSlots()
    {
        dbgAssertGuiThread()

        // We have a Map of account names to values, but we need a list
        // Sort the accounts based on account name
        val lm = ListifyMap(app!!.accounts, { it.value.visible }, object : Comparator<String>
        {
            override fun compare(p0: String, p1: String): Int
            {
                if (wallyApp?.nullablePrimaryAccount?.name == p0) return Int.MIN_VALUE
                if (wallyApp?.nullablePrimaryAccount?.name == p1) return Int.MAX_VALUE
                return p0.compareTo(p1)
            }
        })
        guiAccountList.inflate(this, ui.AccountList!!, lm)

        for (c in accounts.values)
        {
            c.wallet.setOnWalletChange({ it -> onWalletChange(it) })
            c.wallet.blockchain.onChange = { it -> onBlockchainChange(it) }
            c.wallet.blockchain.net.changeCallback = { _, _ -> onWalletChange(c.wallet) }  // right now the wallet GUI update function also updates the cnxn mgr GUI display
            c.onWalletChange()  // update all wallet UI fields since just starting up
        }
         ui.AccountList?.visibility = View.VISIBLE
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
            val coinAa = ArrayAdapter(this, R.layout.account_selection_spinner, sendSpinData)
            ui.sendAccount?.setAdapter(coinAa)
            val coinRecvAa = ArrayAdapter(this, R.layout.account_selection_spinner, coinSpinData)
            ui.recvIntoAccount?.setAdapter(coinRecvAa)

            // Restore GUI elements to their prior values
            mainActivityModel.lastSendFromAccount?.let { ui.sendAccount.setSelection(it) }
            mainActivityModel.lastRecvIntoAccount?.let { ui.recvIntoAccount.setSelection(it) }
            mainActivityModel.lastSendCurrencyType?.let { ui.sendCurrencyType.setSelection(it) }
        }
    }

    override fun onStart()
    {
        super.onStart()
        dbgAssertGuiThread()
        appContext = PlatformContext(applicationContext)
        // (set in XML) sendVisibility(false)

        thread(true, true, null, "startup")
        {
            // Wait until stuff comes up
            while (!coinsCreated) Thread.sleep(50)
            LogIt.info("coins created")
            Thread.sleep(50)

            val a = app
            if (a != null)
            {
                // Ok coins were loaded and guess what no accounts exist.  So create one to help out new users.
                if (a.firstRun == true && a.accounts.isEmpty())
                {
                    // Automatically create a Nexa wallet and put up some info
                    a.newAccount("nexa", ACCOUNT_FLAG_NONE, "", ChainSelector.NEXA)
                    a.firstRun = false
                }
            }

            laterUI {
                dbgAssertGuiThread()
                // First time GUI setup stuff

                assignWalletsGuiSlots()
                assignCryptoSpinnerValues()

                // Set the send currency type spinner options to your default fiat currency or your currently selected crypto
                updateSendCurrencyType()

                ui.recvIntoAccount.selectedItem?.let {
                    val c = accounts[it.toString()]
                    c?.onUpdatedReceiveInfo(minOf(ui.GuiReceiveQRCode.layoutParams.width, ui.GuiReceiveQRCode.layoutParams.height, 1024)) { recvAddrStr, recvAddrQR ->
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
            /*  Start up the welcome guided setup
            LogIt.info("starting welcome activity")
            var intent = Intent(this, Welcome::class.java)
            startActivity(intent)
             */
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
        if (dynOrStaticOrientation != -1)  // If we turned off dynamic screen orientation, set it back to whatever it was
        {
            requestedOrientation =  ActivityInfo.SCREEN_ORIENTATION_SENSOR //dynOrStaticOrientation  // Set it back after turning it off while scanning QR
            dynOrStaticOrientation = -1
        }
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
        ui.xchgRateText?.text = ""

        // If there are any notifications waiting we need to show them when the app resumes,
        // but not if a different intent was launched (that's not just hey start the app)
        val tnt = intent
        if (tnt == null || tnt.action == Intent.ACTION_MAIN) wallyApp?.getNotificationIntent()

        // Look in the paste buffer
        if (ui.sendToAddress.text.toString().trim() == "")  // App started or resumed with nothing in the send field -- let's see if there's something in the paste buffer we can auto-populate
        {
            try
            {
                handlePastedData()
            } catch (e: PasteEmptyException)  // nothing to do, having pasted data is optional on startup
            {

            } catch (e: PayAddressBlankException)  // nothing to do, having pasted data is optional on startup
            {

            } catch (e: PayAddressDecodeException)  // nothing to do, having pasted data is optional on startup
            {
            } catch (e: Exception)
            {
                //LogIt.info(sourceLoc() +" paste exception:")  // there could be random data in the paste, so be tolerant of whatever garbage might be in there but log it
                //LogIt.info(sourceLoc() + Log.getStackTraceString(e))

                // displayNotice(R.string.pasteIgnored)  // TODO: we don't want to display an exception for any random data, just for stuff that looks a bit like a crypto destination
                ui.sendToAddress.text.clear()
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
            while (true)
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

        ui.sendCurrencyType.onItemSelectedListener = null
        super.onDestroy()
    }


    /** Set the send currency type spinner options to your default fiat currency or your currently selected crypto
    Might change if the user changes the default fiat or crypto */
    fun updateSendCurrencyType()
    {
        dbgAssertGuiThread()
        ui.sendAccount.selectedItem?.let {
            val account = accounts[it.toString()]
            account?.let { acc ->
                val curIdx =
                  ui.sendCurrencyType.selectedItemPosition  // We know that this field will be [fiat, crypto] but not which exact choices.  So save the slot and restore it after resetting the values so the UX persists by class
                val spinData = arrayOf(acc.currencyCode, fiatCurrencyCode)
                val aa = ArrayAdapter(this, R.layout.currency_selection_spinner, spinData)
                ui.sendCurrencyType.setAdapter(aa)
                ui.sendCurrencyType.setSelection(curIdx)
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
            val sta = ui.sendToAddress.text.toString().trim()
            updateSendAccount(PayAddress(sta))
        } catch (e: PayAddressBlankException)
        {
        }  // nothing to update if its blank
        catch (e: UnknownBlockchainException)
        {
        } catch (e: Exception)
        {
            if (DEBUG) throw e
        } // ignore all problems from user input, unless in debug mode when we should analyze them

        updateSendCurrencyType()

        checkSendQuantity(ui.sendQuantity.text.toString())

        accounts[defaultBlockchain]?.let { updateReceiveAddressUI(it) }

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
                ui.syncingIcon.setImageDrawable(d)
                d.start()
            }

        }
        else
        {
            if (curWalletsSynced != true)
            {
                ui.syncingIcon.setImageDrawable(getDrawable(R.drawable.ic_check))
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
        handleInputedText(text)
    }

    /** If some unknown text comes from the UX, maybe QR code, maybe clipboard this function handles it by trying to figure out what it is and
    then updating the appropriate fields in the UX */
    fun handleInputedText(text: String)
    {
        if (text != "")
        {
            if (!handleAnyIntent(text))
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
                      laterUI {
                          updateSendAddress(a)
                          sendVisibility(true)
                          receiveVisibility(false)
                      }
                      return
                  }
                  throw PasteUnintelligibleException()
              }
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
        val sc = ui.sendAccount.selectedItem
        val curAccount = accounts[sc]
        if (curAccount?.wallet?.chainSelector != chainSelector)  // Its not so find one that is
        {
            val matches = app!!.accountsFor(chainSelector)
            if (matches.size > 1)
                ui.sendAccount.setSelection(i18n(R.string.choose))
            else if (matches.size == 1)
                ui.sendAccount.setSelection(matches[0].name)
        }
    }

    /** Find an account that can send to this PayAddress and switch the send account to it */
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

        ui.sendToAddress.text.clear()
        ui.sendToAddress.text.append(pa.toString())

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
                        displayError(R.string.BadLink, receivedIntent.scheme.toString())
                    }
                }
            }
        } catch (e: Exception)
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
                if (ui.TopInformation != null)
                {
                    ui.TopInformation.text = ""
                    ui.TopInformation.visibility = View.GONE
                }
            }
            else
            {
                val chainSelector = pip.crypto
                if (chainSelector == null)
                {
                    paymentInProgress = null
                    displayError(R.string.badCryptoCode, pip.toString())
                    return
                }
                val a = app
                if (a == null) return
                val acts = a.accountsFor(chainSelector)

                var amt: BigDecimal = if (acts.size == 0)
                {
                    paymentInProgress = null
                    displayNotice(R.string.badCryptoCode, chainToCurrencyCode[chainSelector] ?: "unknown currency")
                    a.primaryAccount?.fromFinestUnit(pip.totalSatoshis) ?: throw WalletInvalidException()
                }
                else if (acts.size > 1)
                {
                    ui.sendAccount.setSelection(i18n(R.string.choose))
                    acts[0].fromFinestUnit(pip.totalSatoshis)
                }
                else
                {
                    acts[0].fromFinestUnit(pip.totalSatoshis)
                }

                if (true)
                {
                    updateSendAccount(chainSelector)
                    // Update the sendCurrencyType field to contain our coin selection
                    updateSendCurrencyType()

                    ui.sendQuantity.text.clear()
                    ui.sendQuantity.text.append(mBchFormat.format(amt))
                    checkSendQuantity(ui.sendQuantity.text.toString())

                    if (pip.memo != null)
                    {
                        ui.TopInformation.text = pip.memo
                        ui.TopInformation.visibility = View.VISIBLE
                    }

                    /*
                        if (pip.outputs.size > 1)
                        {
                            sendToAddress.text.clear()
                            sendToAddress.text.append(i18n(R.string.multiple))
                        } */

                    ui.sendToAddress.text.clear()
                    var count = 0
                    for (out in pip.outputs)
                    {
                        val addr = out.script.address.toString()
                        if (true) // addr != null)
                        {
                            if (count > 0) ui.sendToAddress.text.append(" ")
                            ui.sendToAddress.text.append(addr)
                        }
                        count += 1
                        if (count > 4)
                        {
                            ui.sendToAddress.text.append("...")
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
        if (index == -1) throw NotUriException() // Can't be a URI if no colon
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
                        sendVisibility(true)
                        receiveVisibility(false)
                    }
                }
                catch (e: Bip70Exception)
                {
                    displayException(e)
                } catch (e: java.lang.Exception)
                {
                    displayException(e)
                }
            }
            return
        }

        if (stramt != null)
        {
            amt = try
            {
                stramt.toBigDecimal(currencyMath).setScale(currencyScale)  // currencyScale because BCH may have more decimals than mBCH
            } catch (e: NumberFormatException)
            {
                throw BadAmountException(R.string.detailsOfBadAmountFromIntent)
            } catch (e: ArithmeticException)  // Rounding error
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
                    val lc = sta.lowercase(Locale.getDefault())
                    val uc = sta.uppercase(Locale.getDefault())
                    if (uc.contentEquals(sta) || lc.contentEquals(sta))  // Its all uppercase or all uppercase
                    {
                        updateSendAddress(PayAddress(lc))
                        sendVisibility(true)
                        receiveVisibility(false)
                    }
                    else  // Mixed upper/lower case not allowed
                    {
                        displayError(R.string.badAddress, "Mixed upper and lower case is not allowed in the CashAddr format")
                        return@laterUI
                    }
                }
                else
                {
                    updateSendAddress(PayAddress(sta))
                    sendVisibility(true)
                    receiveVisibility(false)
                }
            }
            catch (e: UnknownBlockchainException)
            {
                displayError(R.string.badAddress, scheme)
                return@laterUI
            }

            if (amt >= BigDecimal.ZERO)
            {
                ui.sendQuantity.text.clear()
                ui.sendQuantity.text.append(mBchFormat.format(amt))
                checkSendQuantity(ui.sendQuantity.text.toString())
            }
        }

        // attempt to convert into an address to trigger an exception and a subsequent UI error if its bad
        PayAddress(sta)
    }

    /** actually update the UI elements based on the provided data.  Must be called in the GUI context */
    fun updateReceiveAddressUI(recvAddrStr: String, recvAddrQR: Bitmap)
    {
        dbgAssertGuiThread()
        if (recvAddrStr != ui.receiveAddress.text)  // Only update if something has changed
        {
            ui.GuiReceiveQRCode.setImageBitmap(recvAddrQR)
            ui.receiveAddress.text = recvAddrStr

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
            if (ui.recvIntoAccount?.selectedItem?.toString() == account.name)  // Only update the UI if this coin is selected to be received
            {
                account.ifUpdatedReceiveInfo(minOf(ui.GuiReceiveQRCode.layoutParams.width, ui.GuiReceiveQRCode.layoutParams.height, 1024)) { recvAddrStr, recvAddrQR ->
                    updateReceiveAddressUI(
                      recvAddrStr,
                      recvAddrQR
                    )
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
            ui.approximatelyText.text = i18n(R.string.emptyQuantityField)
            return false
        }
        var coinType: String? = ui.sendAccount.selectedItem as? String
        var currencyType: String? = ui.sendCurrencyType.selectedItem as? String
        if (currencyType == null) return true

        val coin = accounts[coinType]
        if (coin == null)
        {
            ui.approximatelyText.text = i18n(R.string.badCurrencyUnit)
            return true // send quantity is valid or irrelevant since the currency type is unknown
        }

        // This sets the scale assuming mBch.  mBch will have more decimals (5) than fiat (2) so we are ok
        val qty = try
        {
            s.toBigDecimal(currencyMath).setCurrency(coin.chain.chainSelector)
        } catch (e: NumberFormatException)
        {
            if (s == SEND_ALL_TEXT)  // Special case transferring everything
            {
                coin.fromFinestUnit(coin.wallet.balance)
            }
            else
            {
                ui.approximatelyText.text = i18n(R.string.invalidQuantity)
                return false
            }
        } catch (e: ArithmeticException)
        {
            ui.approximatelyText.text = i18n(R.string.invalidQuantityTooManyDecimalDigits)
            return false
        }

        if (currencyType == fiatCurrencyCode)
        {
            val fiatPerCoin = coin.fiatPerCoin
            if (coin.fiatPerCoin == -1.toBigDecimal())
            {
                ui.approximatelyText.text = i18n(R.string.unavailableExchangeRate)
                ui.xchgRateText?.text = ""
                return true
            }
            else
            {
                try
                {
                    ui.approximatelyText.text = ""
                    val mbchToSend = qty / fiatPerCoin
                    val sats = coin.toFinestUnit(mbchToSend)
                    if (sats <= Dust(coin.chain.chainSelector))
                        ui.approximatelyText.text = i18n(R.string.sendingDustWarning)
                    else
                        ui.approximatelyText.text = i18n(R.string.actuallySendingT) % mapOf("qty" to mBchFormat.format(mbchToSend), "crypto" to coin.currencyCode) + availabilityWarning(coin, mbchToSend)
                    ui.xchgRateText?.text = i18n(R.string.exchangeRate) % mapOf("amt" to fiatFormat.format(fiatPerCoin), "crypto" to coin.currencyCode, "fiat" to fiatCurrencyCode)
                    return true
                } catch (e: ArithmeticException)  // Division by zero
                {
                    ui.xchgRateText?.text = i18n(R.string.retrievingExchangeRate)
                    return true
                }
            }
        }
        else
        {
            if (qty <= coin.fromFinestUnit(Dust(coin.chain.chainSelector)))
                ui.approximatelyText.text = i18n(R.string.sendingDustWarning)
            else
                ui.approximatelyText.text = ""

            if (coin.fiatPerCoin == -1.toBigDecimal())
            {
                ui.xchgRateText?.text = i18n(R.string.unavailableExchangeRate)
                return true
            }
            else if (coin.fiatPerCoin != BigDecimal.ZERO)
            {
                var fiatDisplay = qty * coin.fiatPerCoin
                if (ui.approximatelyText.text == "")
                    ui.approximatelyText.text = i18n(R.string.approximatelyT) % mapOf("qty" to fiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) + availabilityWarning(coin, qty)
                ui.xchgRateText?.text = i18n(R.string.exchangeRate) % mapOf("amt" to fiatFormat.format(coin.fiatPerCoin), "crypto" to coin.currencyCode, "fiat" to fiatCurrencyCode)
                return true
            }
            else
            {
                ui.xchgRateText?.text = i18n(R.string.retrievingExchangeRate)
                return true
            }

        }
    }

    /** This triggers image selection (presumably a QR code) */
    private fun openGalleryForImage()
    {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_RESULT)
    }

    /** this handles the result of variety of launched subactivities including:
     * a QR code scan.  We want to accept QR codes of any different format and "do what I mean" based on the QR code's contents
     * an image selection (presumably its a QR code)
     * an identity or trickle pay activity completion
     * */
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

        // Gallery Image selection (presumably a QR code)
        if (requestCode == IMAGE_RESULT)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                var im = data?.data
                LogIt.info(sourceLoc() + ": Parse QR from image: " + im)
                if (im != null)
                {
                    val path = URIPathHelper().getPath(this, im)
                    if (path != null)
                    {
                        try
                        {
                            val qrdata = readQRcode(path)

                            LogIt.info(sourceLoc() + ": QR result: " + qrdata)
                            displayNotice(R.string.goodQR, qrdata)
                            handleInputedText(qrdata)
                        }
                        catch (e: com.google.zxing.NotFoundException)
                        {
                            displayError(R.string.badImageQR, R.string.badImageQRhelp)
                        }
                        catch(e: Exception)
                        {
                            displayException(R.string.badImageQR, e)
                        }
                    }
                }
            }
        }

        // QR code scanning
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null)
        {
            if (result.contents != null)
            {
                laterUI {
                    val QRstring = result.contents.toString()
                    displayNotice(i18n(R.string.scanSuccess), "QR text: " + QRstring, 2000)
                    // TODO parse other QR code formats
                    LogIt.info(sourceLoc() + ": QR result: " + QRstring)
                    try
                    {
                        handleInputedText(QRstring)
                    }
                    catch (e: Exception)  // I can't handle it as plain text
                    {
                        LogIt.info(sourceLoc() + ": QR contents invalid: " + QRstring)
                        displayError(R.string.badAddress, QRstring)
                    }

                    /*  Replaced by just handleInputedText, kept for reference temporarily
                    if (!handleAnyIntent(QRstring))
                    {
                        try
                        {
                            handleSendURI(result.contents)
                        } catch (e: Exception)
                        {
                            try
                            {
                                handleInputedText(QRstring)
                            } catch (e: Exception)  // I can't handle it as plain text
                            {
                                LogIt.info(sourceLoc() + ": QR contents invalid: " + QRstring)
                                displayError(R.string.badAddress, QRstring)
                            }
                        }
                    }
                     */
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

        initializeHelpOption(menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onSaveInstanceState(outState: Bundle)
    {
        super.onSaveInstanceState(outState)
        outState.putString("sendToAddress", ui.sendToAddress.text.toString().trim())
        outState.putString("sendQuantity", ui.sendQuantity.text.toString().trim())
        outState.putString("sendCurrencyType", (ui.sendCurrencyType.selectedItem ?: defaultBlockchain) as String)
        outState.putString("recvCoinType", (ui.recvIntoAccount.selectedItem ?: defaultBlockchain) as String)
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
            val account = accounts[defaultBlockchain]
            if (account == null) throw BadCryptoException(R.string.badCryptoCode)

            val recvAddrStr: String? = account.currentReceive?.address?.toString()

            if (recvAddrStr != null)
            {
                var clip = ClipData.newPlainText("text", recvAddrStr)
                clipboard.setPrimaryClip(clip)

                // visual bling that indicates text copied
                ui.receiveAddress.text = i18n(R.string.copiedToClipboard)
                laterUI {
                    delay(5000); accounts[defaultBlockchain]?.let { updateReceiveAddressUI(it) }
                }
            }
            else throw UnavailableException(R.string.receiveAddressUnavailable)
        } catch (e: Exception)
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

    @Suppress("UNUSED_PARAMETER")
    fun onQRfromGalleryClicked(view: View)
    {
        onReadStoragePermissionGranted { openGalleryForImage() }
    }


    @Suppress("UNUSED_PARAMETER")
    /** Start the purchase activity when the purchase button is pressed */
    public fun onPurchaseButton(v: View): Boolean
    {
        //val intent = Intent(this@MainActivity, InvoicesActivity::class.java)
        //startActivity(intent)
        return true
    }

    public fun onAllButtonClicked(v:View): Boolean
    {
        if (ui.sendQuantity.hasFocus())
        {
            ui.sendQuantity.text.clear()
            ui.sendQuantity.text.append(SEND_ALL_TEXT)
        }
        return true
    }

    public fun onClearButtonClicked(v:View): Boolean
    {
        if (ui.sendQuantity.hasFocus()) ui.sendQuantity.text.clear()
        else if (ui.sendToAddress.hasFocus()) ui.sendToAddress.text.clear()
        else if (ui.editSendNote?.hasFocus() == true) ui.editSendNote?.text?.clear()
        return true
    }
    public fun onAmountThousandButtonClicked(v:View): Boolean
    {
        if (ui.sendQuantity.hasFocus())
        {
            val curText = ui.sendQuantity.text.toString()
            var amt = try
            {
                BigDecimal(curText.toString())
            }
            catch(e:java.lang.NumberFormatException)
            {
                if ((ui.sendQuantity.text.length == 0) || (curText=="all")) BigDecimal(1)
                else return false
            }
            amt *= BigDecimal(1000)
            ui.sendQuantity.set(amt.toString())
        }
        return true
    }

    public fun onAmountMillionButtonClicked(v:View): Boolean
    {
        if (ui.sendQuantity.hasFocus())
        {
            val curText = ui.sendQuantity.text.toString()
            var amt = try
            {
                BigDecimal(curText)
            }
            catch(e:java.lang.NumberFormatException)
            {
                if ((ui.sendQuantity.text.length == 0) || (curText=="all")) BigDecimal(1)
                else return false
            }
            amt *= BigDecimal(1000000)
            ui.sendQuantity.set(amt.toString())
        }
        return true
    }

    override fun onSoftKeyboard(shown: Boolean)
    {
        super.onSoftKeyboard(shown)
        // We want to show these buttons only when the soft keyboard is NOT up
        val oppositeVis = if (shown) View.GONE else View.VISIBLE
        ui.readQRCodeButton.visibility = oppositeVis
        ui.pasteFromClipboardButton.visibility = oppositeVis
        ui.qrFromGallery.visibility = oppositeVis

        // We want to show these when the soft keyboard IS up
        val vis = if (shown) View.VISIBLE else View.GONE
        ui.softKeyboardExtensions?.visibility = vis

    }

    /** Allow the user to add/edit the send note */
    public fun onEditSendNoteButtonClicked(v: View): Boolean
    {
        val esn = ui.editSendNote
        val sn = ui.sendNote
        if (esn == null) return false
        if (sn == null) return false
        if (esn.visibility == View.VISIBLE) // hit the button again
        {
            esn.visibility = View.GONE
            val tmp = esn.text.toString()
            sn.setVisibility(if (tmp == "") View.GONE else View.VISIBLE)
            return true
        }
        sn.setVisibility(View.GONE)
        esn.setVisibility(View.VISIBLE)
        v.getRootView().invalidate()

        asyncUI {
            val esn = ui.editSendNote
            if (esn != null)
            {
                delay(100)
                esn.requestFocus()
                showKeyboard()
                delay(300)  // Give time for the keyboard to be not shown as we move from some other focus and then be shown for us
                var l: KeyboardToggleListener? = null
                esn.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE)
                    {
                        val tmp = esn.text.toString()
                        ui.sendNote?.let {
                            it.text = tmp
                            it.setVisibility(if (tmp == "") View.GONE else View.VISIBLE)
                        }
                        esn.setVisibility(View.GONE)
                        l?.remove()
                        hideKeyboard()
                        false  // Sure I did something but I want the OS to do its thing as well (dispel the edit-only view if it put one up)
                    }
                    else false
                }
                esn.setOnFocusChangeListener(object : View.OnFocusChangeListener
                {
                    override fun onFocusChange(p0: View?, hasFocus: Boolean)
                    {
                        if (!hasFocus)
                        {
                            val tmp = esn.text.toString()
                            ui.sendNote?.let {
                                it.text = tmp
                                it.setVisibility(if (tmp == "") View.GONE else View.VISIBLE)
                            }
                            esn.setVisibility(View.GONE)
                            l?.remove()
                        }
                    }
                })
                l = onKeyboardToggle(esn, { shown: Boolean ->
                    if (shown == false)
                    {
                        val tmp = esn.text.toString()
                        ui.sendNote?.let {
                            it.text = tmp
                            it.setVisibility(if (tmp == "") View.GONE else View.VISIBLE)
                        }
                        esn.setVisibility(View.GONE)
                        hideKeyboard()
                        l?.remove()
                    }

                }
                )
            }
        }
        return true
    }


    public fun paymentInProgressSend()
    {
        try
        {
            val pip = paymentInProgress
            if (pip == null) return

            // Which crypto are we sending
            var walletName = ui.sendAccount.selectedItem as String

            val account = accounts[walletName]
            if (account == null)
            {
                displayError(R.string.badCryptoCode, i18n(R.string.badCryptoCodeDetails) % mapOf("currency" to walletName))
                return
            }
            if (account.wallet.chainSelector != pip.crypto)
            {
                displayError(R.string.incompatibleAccount, i18n(R.string.incompatibleAccountDetails) %
                  mapOf("cryptoAct" to (chainToCurrencyCode[account.wallet.chainSelector] ?: i18n(R.string.unknownCurrency)), "cryptoPay" to (chainToCurrencyCode[pip.crypto] ?: i18n(R.string.unknownCurrency))))
                return
            }

            val tx = account.wallet.prepareSend(pip.outputs, 1)  // Bitpay does not allow zero-conf payments -- fix if other payment protocol servers support zero-conf
            // If prepareSend succeeds, we must wrap all further logic in a try catch to ensure that the protocol succeeds or is aborted so that inputs are recovered
            try
            {
                completeJsonPay(pip, tx)
                account.wallet.send(tx)  // If the payment protocol completes, help the merchant by broadcasting the tx, and also mark the inputs as spent in my wallet
            } catch (e: Exception)
            {
                account.wallet.abortTransaction(tx)
                throw e
            }
            displayNotice(R.string.sendSuccess)
            paymentInProgress = null
            laterUI {
                ui.sendToAddress.text.clear()
                updateSendBasedOnPaymentInProgress()
            }
        } catch (e: Exception)
        {
            displayException(e)
        }
    }

    /* Show or hide the send function visibility */
    public fun sendVisibility(show: Boolean)
    {
        if (show)
        {
            ui.sendUI?.visibility = View.VISIBLE
            ui.editSendNoteButton?.visibility = View.VISIBLE
            ui.sendCancelButton?.visibility = View.VISIBLE
            ui.splitBillButton?.visibility = View.GONE
        }
        else
        {
            ui.sendUI?.visibility = View.GONE
            ui.sendCancelButton?.visibility = View.GONE
            ui.editSendNoteButton?.visibility = View.GONE
            ui.splitBillButton?.visibility = View.VISIBLE
        }
    }
    /* Show or hide the receive functionality */
    public fun receiveVisibility(show: Boolean)
    {
        if (show)
        {
            ui.receiveUI?.visibility = View.VISIBLE
        }
        else
        {
             ui.receiveUI?.visibility = View.GONE
        }
    }


    public fun onSendCancelButtonClicked(v: View): Boolean
    {
        sendVisibility(false)
        receiveVisibility(true)
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    /** Create and post a transaction when the send button is pressed */
    public fun onSendButtonClicked(v: View): Boolean
    {
        if (ui.sendUI?.visibility != View.VISIBLE)
        {
            receiveVisibility(false)
            sendVisibility(true)
            return true
        }
        dbgAssertGuiThread()
        LogIt.info("send button clicked")
        displayNotice(R.string.Processing)
        hideKeyboard()
        ui.sendQuantity.clearFocus()
        val note:String? = if (ui.editSendNote?.visibility == View.VISIBLE) ui.editSendNote?.text.toString() else if (ui.sendNote?.visibility == View.VISIBLE) ui.sendNote?.text.toString() else null

        if (paymentInProgress != null)
        {
            coMiscScope.launch {
                paymentInProgressSend()
            }
            return true
        }

        var currencyType: String? = ui.sendCurrencyType.selectedItem as String?
        // Which crypto are we sending
        val walletName = try
        {
            ui.sendAccount.selectedItem as String
        } catch (e: TypeCastException)  // No wallets are defined so no sendCoinType is possible
        {
            displayException(R.string.badCryptoCode, e)
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
            displayError(R.string.badCryptoCode, i18n(R.string.badCryptoCodeDetails) % mapOf("currency" to currencyType) )
            return false
        }
        if (account.locked)
        {
            displayError(R.string.accountLocked)
            return false
        }

        val amtstr: String = ui.sendQuantity.text.toString()

        var deductFeeFromAmount = false
        var amount = try
        {
            amtstr.toBigDecimal(currencyMath).setCurrency(account.chain.chainSelector)
        }
        catch (e: NumberFormatException)
        {
            if (amtstr == SEND_ALL_TEXT)
            {
                deductFeeFromAmount = true
                account.fromFinestUnit(account.wallet.balance)
            }
            else
            {
                displayException(R.string.badAmount, e, true)
                return false
            }
        }
        catch (e: ArithmeticException)  // Rounding error
        {
            // If someone is asking to send sub-satoshi quantities, round up and ask them to click send again.
            ui.sendQuantity.text.clear()
            ui.sendQuantity.text.append(amtstr.toBigDecimal().setScale(account.chain.chainSelector.currencyDecimals, RoundingMode.UP).toString())
            displayError(R.string.badAmount, R.string.subSatoshiQuantities)
            ui.approximatelyText.text = i18n(R.string.roundedUpClickSendAgain)
            return false
        }

        // Make sure the address is consistent with the selected coin to send
        val sendAddr = try
        {
            PayAddress(ui.sendToAddress.text.toString().trim())
        }
        catch (e: WalletNotSupportedException)
        {
            displayError(R.string.badAddress, ui.sendToAddress.text.toString())
            return false
        }
        catch (e: UnknownBlockchainException)
        {
            displayError(R.string.badAddress, ui.sendToAddress.text.toString())
            return false
        }
        if (account.wallet.chainSelector != sendAddr.blockchain)
        {
            displayError(R.string.chainIncompatibleWithAddress, ui.sendToAddress.text.toString())
            return false
        }
        if (sendAddr.type == PayAddressType.NONE)
        {
            displayError(R.string.badAddress, ui.sendToAddress.text.toString())
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
                val tx = account.wallet.send(atomAmt, sendAddr, deductFeeFromAmount, false, note = note)
                onSendSuccess(atomAmt, sendAddr, tx)
                laterUI {
                    receiveVisibility(true)
                    sendVisibility(false)
                }
            }
            catch (e: Exception)  // We don't want to crash, we want to tell the user what went wrong
            {
                displayException(e)
            }
        }

        val sn = ui.sendNote
        if (sn != null)
        {
            sn.text = ""
            sn.visibility = View.GONE
        }
        ui.editSendNote?.let { it.visibility = View.GONE }
        return true
    }

    fun onSendSuccess(amt: Long, addr: PayAddress, tx: iTransaction)
    {
        // TODO Some visual and audible bling
        displayNotice(R.string.sendSuccess, "$amt -> $addr: ${tx.idem}")
        laterUI {
            ui.sendToAddress.text.clear()
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
}

