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
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.View.*
import android.view.inputmethod.EditorInfo
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.ParseException
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import info.bitcoinunlimited.www.wally.databinding.ActivityMainBinding
import info.bitcoinunlimited.www.wally.databinding.AssetSuccinctListItemBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Thread.sleep
import com.ionspin.kotlin.bignum.decimal.*
import java.math.RoundingMode
import java.net.URL
import java.util.*

import kotlin.concurrent.thread
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.mainActivity")

val ERROR_DISPLAY_TIME = 10000L
val NOTICE_DISPLAY_TIME = 4000L
val NORMAL_NOTICE_DISPLAY_TIME = 2000L

/** if phone is asleep for this time, lock wallets */
val RELOCK_TIME = 5000L

val MAX_ACCOUNTS = 10 // What are the maximum # of accounts this wallet GUI can show

var SEND_ALL_TEXT = "all"  // Fixed up in onCreate when we have access to strings

var appContext: Context? = null

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
    lateinit var sendAssetsLayoutManager: LinearLayoutManager
    lateinit var sendAssetsAdapter: GuiList<AssetInfo, AssetSuccinctBinder>
    override var navActivityId = R.id.navigation_home

    var app: WallyApp? = null

    var dynOrStaticOrientation: Int = -1  // Used to remember the screen orientation when temporarily disabling int

    val accounts: MutableMap<String, Account>
        get() = app!!.accounts

    //* last paste so we don't try to paste the same thing again
    var lastPaste: String = ""

    /** If there's a payment proposal that this app has seen, information about it is located here */
    var paymentInProgress: ProspectivePayment? = null

    /** what assets are marked to be transferred */
    var sendAssetList: List<AssetInfo> = listOf()

    /** If this program is changing the GUI, rather then the user, then there are some logic differences */
    var machineChangingGUI: Boolean = false

    var sleepMonitor: SleepMonitor? = null

    var curWalletsSynced: Boolean? = null
    var walletsSynced:Boolean = true

    /** If we've already put up an error for this address, don't do it again */
    var alreadyErroredAddress: PayAddress? = null

    /** Do this once we get file read permissions */
    var doOnFileReadPerms: (() -> Unit)? = null

    /** Do this once we get file read permissions */
    var doOnMediaReadPerms: (() -> Unit)? = null


    /** track a 2 phase send operation */
    var askedForConfirmation = false

    fun onBlockchainChange(blockchain: Blockchain)
    {
        later {  // Don't execute within the callback context
            for ((_, c) in accounts)
            {
                if (c.chain == blockchain)
                    c.onChange()  // coin onWalletChange also updates the blockchain state GUI
            }
        }
    }

    fun onWalletChange(wallet: Wallet)  // callback provided to the wallet code
    {
        later {  // Don't execute this change code within the wallet-atomic context of this callback because it just updates the UI
            for ((_, c) in accounts)
            {
                if (c.wallet == wallet)
                {
                    c.onChange()
                    // recalculate the QR code if needed early to speed up response time
                    //GlobalScope.launch { c.getReceiveQR(minOf(imageView.layoutParams.width, imageView.layoutParams.height, 1024)) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        app = (getApplication() as WallyApp)  // !! how can we have an activity without an app?
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        val ctxt = applicationContext
        appContext = ctxt

        sleepMonitor = SleepMonitor(this)

        SEND_ALL_TEXT = i18n(R.string.sendAll)

        ui.sendToAddress.text.clear()
        ui.sendQuantity.text.clear()

        ui.GuiSendAssetList.visibility = View.GONE
        sendAssetsLayoutManager = LinearLayoutManager(this)
        ui.GuiSendAssetList.layoutManager = sendAssetsLayoutManager
        setAssetsToTransfer(null)  // Not transferring any assets

        // Load the model with persistently saves stuff when this activity is created
        if (savedInstanceState != null)
        {
            ui.sendToAddress.text.append(savedInstanceState.getString("sendToAddress", "") ?: "")
            ui.sendQuantity.text.append(savedInstanceState.getString("sendQuantity", "") ?: "")
            mainActivityModel.lastSendCurrencyType = savedInstanceState.getString("sendCurrencyType", currentlySelectedAccount)
            mainActivityModel.lastRecvIntoAccount = savedInstanceState.getString("recvCoinType", currentlySelectedAccount)
            mainActivityModel.lastSendFromAccount = savedInstanceState.getString("sendCoinType", currentlySelectedAccount)
        }
        else
        {
            mainActivityModel.lastSendCurrencyType = currentlySelectedAccount
            mainActivityModel.lastRecvIntoAccount = currentlySelectedAccount
            mainActivityModel.lastSendFromAccount = currentlySelectedAccount
        }

        ui.sendButton.setOnClickListener { onSendButtonClicked(it) }
        ui.sendCancelButton.setOnClickListener { onSendCancelButtonClicked(it) }
        ui.splitBillButton.setOnClickListener { onSplitBill(it) }
        ui.purchaseButton.setOnClickListener { onPurchaseButton(it) }

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
            // show all helper buttons
            val sqvis = if (hasFocus) View.VISIBLE else View.GONE
            ui.amountAllButton?.visibility = sqvis
            ui.amountThousandButton?.visibility = sqvis
            ui.amountMillionButton?.visibility = sqvis
            ui.amountClearButton?.visibility = sqvis
        })

         ui.sendToAddress.setOnFocusChangeListener(OnFocusChangeListener { view, hasFocus ->
             // only helper button for sendToAddress is clear
             val sqnovis = if (hasFocus) View.GONE else View.VISIBLE
             val sqvis = if (hasFocus) View.VISIBLE else View.GONE
             ui.amountAllButton?.visibility = sqnovis
             ui.amountThousandButton?.visibility = sqnovis
             ui.amountMillionButton?.visibility = sqnovis
             ui.amountClearButton?.visibility = sqvis
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
                    setAssetsToTransfer(wallyApp!!.assetManager.transferList, c)
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
                    val oldc = accounts[currentlySelectedAccount]
                    if (oldc != null)
                    {
                        oldc.updateReceiveAddressUI = null
                    }
                    currentlySelectedAccount = c.name
                    c.updateReceiveAddressUI = { it -> updateReceiveAddressUI(it) }
                    updateReceiveAddressUI(c)
                }
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {
            }
        }

        ui.TopInformation.setOnClickListener {
            dbgAssertGuiThread()
            clearRecoveryKeyNotBackedUpWarning()
        }

        // touching the divider line switches it
        ui.sendReceiveDivider.setOnClickListener {
            if (ui.sendUI.visibility == View.GONE)
            {
                sendVisibility(true)
            }
            else
            {
                sendVisibility(false)
                if (ui.receiveUI.visibility == View.GONE) receiveVisibility(true)
            }
        }
        ui.sendUI.setOnClickListener {
            if (ui.sendUI.visibility == View.GONE)
            {
                sendVisibility(true)
            }
            else
            {
                sendVisibility(false)
                if (ui.receiveUI.visibility == View.GONE) receiveVisibility(true)
            }
        }

        ui.receiveBalancesDivider.setOnClickListener {
            if (ui.receiveUI.visibility == View.GONE)
            {
                receiveVisibility(true)
            }
            else
            {
                sendVisibility(false)
                if (ui.sendUI.visibility == View.GONE) receiveVisibility(true)
            }
        }
        ui.AccountInfo.setOnClickListener {
            if (ui.receiveUI.visibility == View.GONE)
            {
                receiveVisibility(true)
            }
            else
            {
                receiveVisibility(false)
                if (ui.sendUI.visibility == View.GONE) receiveVisibility(true)
            }
        }
        ui.balanceTitle.setOnClickListener {
            if (ui.receiveUI.visibility == View.GONE)
            {
                receiveVisibility(true)
            }
            else
            {
                receiveVisibility(false)
                if (ui.sendUI.visibility == View.GONE) receiveVisibility(true)
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
                    laterUI {
                        delay(1000)
                        displayError(R.string.NoPermission, i18n(R.string.NoPermissionDetails) % mapOf("perm" to "Read external storage"))
                    }
                }
                return
            }
            READ_MEDIA_IMAGES_RESULT ->
            {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                {
                    doOnMediaReadPerms?.invoke()
                }
                else
                {
                    laterUI {
                        delay(1000)
                        displayError(R.string.NoPermission, i18n(R.string.NoPermissionDetails) % mapOf("perm" to "Read media images"))
                    }
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

    override fun setVisibleSoftKeys(which: SoftKeys)
    {
        ui.amountAllButton?.visOrGone(which.contains(SoftKey.ALL))
        ui.amountThousandButton?.visOrGone(which.contains(SoftKey.THOUSAND))
        ui.amountMillionButton?.visOrGone(which.contains(SoftKey.MILLION))
        ui.amountClearButton?.visOrGone(which.contains(SoftKey.CLEAR))
    }

    // call this with a function to execute whenever that function needs file read permissions
    fun onReadStoragePermissionGranted(doit: () -> Unit): Boolean
    {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            doit()
        else
        {
            doOnFileReadPerms = doit
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_FILES_PERMISSION_RESULT)
        }
        return false
    }

    // call this with a function to execute whenever that function needs file read permissions
    fun onReadMediaPermissionGranted(doit: () -> Unit): Boolean
    {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            doit()
        else
        {
            doOnMediaReadPerms = doit
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), READ_MEDIA_IMAGES_RESULT)
        }
        return false
    }

    fun setFocusedAccount(account: Account?)
    {
        wallyApp!!.focusedAccount = account
        if (account != null)
        {
            ui.recvIntoAccount.setSelection(account.name)
            ui.sendAccount.setSelection(account.name)
            setAssetsToTransfer(wallyApp!!.assetManager.transferList, account)
            updateReceiveAddressUI(account)
        }
    }

    val guiAccountList = GuiAccountList(this)

    fun clearAccountUI()
    {
        ui.AccountList?.visibility = View.INVISIBLE
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
            c.onChange()  // update all wallet UI fields since just starting up
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

    /*
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean
    {
        LogIt.info(sourceLoc() + " touch event")
        return super.dispatchTouchEvent(ev)
    }
     */

    override fun onStart()
    {
        super.onStart()
        dbgAssertGuiThread()
        appContext = applicationContext
        // (set in XML) sendVisibility(false)

        thread(true, true, null, "startup")
        {
            // Wait until stuff comes up
            while (!coinsCreated) Thread.sleep(50)
            LogIt.info("coins created")

            val a = app
            if (a != null)
            {
                // Ok coins were loaded and guess what no accounts exist.  So create one to help out new users.
                if (runningTheTests == false && devMode == false && a.firstRun == true && a.accounts.isEmpty())
                {
                    // Automatically create a Nexa wallet and put up some info
                    val ac = a.newAccount("nexa", ACCOUNT_FLAG_NONE, "", ChainSelector.NEXA)
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

        laterUI {
            delay(500)
            val a = app
            if (a != null)
            {
                val warnBackupRecoveryKey = a.warnBackupRecoveryKey.receive()
                if (warnBackupRecoveryKey == true)
                {
                    laterUI {
                        displayRecoveryKeyNotBackedUpWarning()
                    }
                }
            }
        }

        laterUI {
            delay(5000)  // give a little time to start up
            checkNofificationPermission()
        }
    }


    fun clearRecoveryKeyNotBackedUpWarning()
    {
        val t = i18n(R.string.WriteDownRecoveryPhraseWarning)
        if (ui.TopInformation.text == t)
        {
            ui.TopInformation.text=""
            ui.TopInformation.visibility = GONE
        }
    }
    fun displayRecoveryKeyNotBackedUpWarning()
    {
        val t = i18n(R.string.WriteDownRecoveryPhraseWarning)
        ui.TopInformation.text = t
        ui.TopInformation.visibility = VISIBLE
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

        laterUI {
            delay(2000)  // give a little time because presumably user is still manipulating phone
            if (dynOrStaticOrientation != -1)  // If we turned off dynamic screen orientation, set it back to whatever it was
            {
                requestedOrientation = dynOrStaticOrientation // ActivityInfo.SCREEN_ORIENTATION_SENSOR  // Set it back after turning it off while scanning QR
                dynOrStaticOrientation = -1
            }
        }

        appContext = applicationContext
        val preferenceDB = getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)
        fiatCurrencyCode = preferenceDB.getString(LOCAL_CURRENCY_PREF, "USD") ?: "USD"
        ui.xchgRateText?.text = ""

        // If there are any notifications waiting we need to show them when the app resumes,
        // but not if a different intent was launched (that's not just hey start the app)
        val tnt = intent
        if (tnt == null || tnt.action == Intent.ACTION_MAIN)
        {
            val newI = wallyApp?.getNotificationIntent()
            if (newI != null)
            {
                LogIt.info(sourceLoc() + "onResume handle local intent")
                handleAnyIntent(newI.toUri(0))
            }
        }

        /* never automatically populate the send field on resume, user can just press the clipboard button
        // Look in the paste buffer
        if ((wallyApp?.firstRun == false) && (ui.sendToAddress.text.toString().trim() == "")) // App started or resumed with nothing in the send field -- let's see if there's something in the paste buffer we can auto-populate
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
                ui.sendToAddress.text.clear()
            }
        }
         */

        later {
            // Thread.sleep(100)  // Wait for accounts to be loaded
            while (!coinsCreated) Thread.sleep(50)
            laterUI {

                assignWalletsGuiSlots()
                assignCryptoSpinnerValues()
                updateGUI()

                for (c in accounts.values)
                {
                    c.updateReceiveAddressUI = { it -> updateReceiveAddressUI(it) }
                    c.onResume()
                }

                val tl = app?.assetManager?.transferList
                if (tl != null && tl.size > 0)
                {
                    setAssetsToTransfer(tl)
                    sendVisibility(true)
                    receiveVisibility(false)
                }

            }
        }

        thread {
            while (true)
            {
                Thread.sleep(1000)
                var tmp = true
                for (c in accounts)
                {
                        if (c.value.visible && !c.value.wallet.synced()) tmp = false
                }
                if (walletsSynced != tmp) walletsSynced = tmp
                Thread.sleep(2000)
            }
        }
        // Poll the syncing icon update because it doesn't matter how long it takes
        laterUI {
            while (true)
            {
                updateSyncingIcon()
                delay(3000)
            }
        }

        wallyApp?.firstRun = false
    }


    var hasNotifPerm = 0

    val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        synchronized(hasNotifPerm)
        {
            if (isGranted)
            {
                    hasNotifPerm = 1
            }
            else
            {
                hasNotifPerm = 0
            }
        }
    }
    fun checkNofificationPermission(): Unit
    {
        if (hasNotifPerm == 1) return

        when
        {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ->
            {
                hasNotifPerm = 1
                return
            }

            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
            {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected. In this UI,
                // include a "cancel" or "no thanks" button that allows the user to
                // continue using your app without granting the permission.
                //showInContextUI(...)
            }

            else ->
            {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                  Manifest.permission.POST_NOTIFICATIONS
                )
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
                // If we don't know the exchange rate, we can't offer fiat entry
                val spinData = if (account.fiatPerCoin != -1.toBigDecimal()) arrayOf(acc.currencyCode, fiatCurrencyCode) else arrayOf(acc.currencyCode)
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
        }
        catch (e: PayAddressBlankException)
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

        accounts[currentlySelectedAccount]?.let { updateReceiveAddressUI(it) }

        // Process the intent that caused this activity to resume
        if (intent.scheme != null)  // its null if normal app startup
        {
            handleNewIntent(intent)
        }

        for (c in accounts)
        {
            c.value.onChange()
        }

        updateSyncingIcon()
    }

    fun updateSyncingIcon()
    {
        dbgAssertGuiThread()
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

    fun handleNonIntentText(text: String)
    {
        // NOTE: in certain contexts (app is background), the UI thread may not even be running so do not require completion of any laterUI tasks
        LogIt.info(sourceLoc() + "handleNonIntentText: " + text)
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
                    val nid = wallyApp?.notifyPopup(intent, i18n(R.string.sendRequestNotification), i18n(R.string.toColon) + addrString, this, false)
                    intent.extras?.putIntegerArrayList("notificationId", arrayListOf(nid))
                }
            }
            return
        }
        throw PasteUnintelligibleException()
    }
    /** If some unknown text comes from the UX, maybe QR code, maybe clipboard this function handles it by trying to figure out what it is and
    then updating the appropriate fields in the UX */
    fun handleInputedText(text: String)
    {
        if (text != "")
        {
            if (!handleAnyIntent(text))
            {
                handleNonIntentText(text)
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
            {
                ui.sendAccount.setSelection(i18n(R.string.choose))
                setAssetsToTransfer(null)
            }
            else if (matches.size == 1)
            {
                ui.sendAccount.setSelection(matches[0].name)
                setAssetsToTransfer(wallyApp!!.assetManager.transferList, matches[0])
            }
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

        ui.sendToAddress.set(pa.toString())

        paymentInProgress = null
        updateSendBasedOnPaymentInProgress()

        // Change the send currency type to reflect the pasted data if I need to
        updateSendAccount(pa)
        // Update the sendCurrencyType field to contain our coin selection
        updateSendCurrencyType()
        receiveVisibility(false)
        sendVisibility(true)
    }

    fun updateSendAddress(text: String, updateIfNonAddress:Boolean = false): PayAddress
    {
        val t = text.trim()
        var ret = try
        {
            val pa = PayAddress(t) // attempt to convert into an address to trigger an exception and a subsequent UI error if its bad
            updateSendAddress(pa)
            pa
        }
        catch(e: UnknownBlockchainException)
        {
            // ok let's try to pick an address out of a mess of text
            val pa = scanForFirstAddress(t)
            if (pa != null) { updateSendAddress(pa); pa }
            else
            {
                if (updateIfNonAddress)
                {
                    ui.sendToAddress.set(t)  // allow user to edit the bad text by showing it anyway
                    receiveVisibility(false)
                    sendVisibility(true)
                }
                throw e
            }
        }

        receiveVisibility(false)
        sendVisibility(true)
        return ret
    }



    //? A new intent to pay someone could come from either startup (onResume) or just on it own (onNewIntent) so create a single function to deal with both
    fun handleNewIntent(receivedIntent: Intent)
    {
        val iuri = receivedIntent.toUri(0)  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME
        LogIt.info("on new Intent: " + iuri)
        try
        {
            if (receivedIntent.scheme == "tdpp")
            {

            }
            else
            {
                var handled = false
                for (c in accounts.values)
                {
                    if (receivedIntent.scheme != null)  // its null if normal app startup
                    {
                        if (receivedIntent.scheme == c.chain.uriScheme)
                        {
                            handleSendURI(iuri)
                            handled = true
                            break
                        }
                    }
                }
                if (!handled) // This should never happen because the AndroidManifest.xml Intent filter should match the URIs that we handle
                {
                    displayError(R.string.BadLink, receivedIntent.scheme.toString())
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
                    displayNotice(R.string.badCryptoCode, chainToCurrencyCode[chainSelector] ?: i18n(R.string.unknownCurrency))
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

                // This payment in progress looks ok, set up the UX to show it
                if (true)
                {
                    setAssetsToTransfer(null)  // PIP spec does not cover any assets so make sure none are selected
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
        var amt: BigDecimal = CURRENCY_NEG1
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
                stramt.toCurrency()
                //stramt.toBigDecimal(currencyMath).setScale(currencyScale)  // currencyScale because BCH may have more decimals than mBCH
            }
            catch (e: NumberFormatException)
            {
                throw BadAmountException(R.string.detailsOfBadAmountFromIntent)
            }
            catch (e: ArithmeticException)  // Rounding error
            {
                // If someone is asking for sub-satoshi quantities, round up and overpay them
                LogIt.warning("Sub-satoshi quantity ${stramt} requested.  Rounding up")
                BigDecimal.fromString(stramt, nexaMathMode)
            }
        }

        val lc = sta.lowercase(Locale.getDefault())

        val pa:PayAddress?
        var amtString = "0"
        val act: Account?
        try
        {
            pa = PayAddress(lc)
            val acts = app?.accountsFor(pa.blockchain)
            if ((acts == null)|| acts.isEmpty())
            {
                displayError(R.string.NoAccounts)
                return
            }
            act = acts[0]
            amt = act.fromPrimaryUnit(amt)
            amtString = act.format(amt)
        }
        catch (e: UnknownBlockchainException)
        {
            displayError(R.string.badAddress, scheme)
            return
        }


        laterUI {
            // TODO label and message
            updateSendAddress(pa) // This also updates the send account

            if (amt >= BigDecimal.ZERO)
            {
                ui.sendQuantity.text.clear()
                ui.sendQuantity.text.append(amtString)
                checkSendQuantity(ui.sendQuantity.text.toString())
            }

            receiveVisibility(false)
            sendVisibility(true)
        }
    }

    /** actually update the UI elements based on the provided data.  Must be called in the GUI context */
    fun updateReceiveAddressUI(recvAddrStr: String, recvAddrQR: Bitmap)
    {
        dbgAssertGuiThread()
        if (recvAddrStr != ui.receiveAddress.text)  // Only update if something has changed
        {
            ui.GuiReceiveQRCode.setImageBitmap(recvAddrQR)
            ui.receiveAddress.text = recvAddrStr
        }
    }

    /** actually update the UI elements based on the provided data. */
    fun updateReceiveAddressUI(account: Account)
    {
        // Demonstration of the significant pain required to only access UI elements in the UI thread, and other blocking calls outside of the UI thread!
        laterUI {
            if (ui.recvIntoAccount.selectedItem?.toString() == account.name)  // Only update the UI if this coin is selected to be received
            {
                val width = ui.GuiReceiveQRCode.layoutParams.width
                val height = ui.GuiReceiveQRCode.layoutParams.height
                later {
                    account.ifUpdatedReceiveInfo(minOf(width, height, 1024)) { recvAddrStr, recvAddrQR ->
                        laterUI {
                            updateReceiveAddressUI(
                              recvAddrStr,
                              recvAddrQR
                            )
                        }
                    }
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
            ui.approximatelyText.text = ""
            return true // send quantity is valid or irrelevant since the currency type is unknown
        }

        // This sets the scale assuming mBch.  mBch will have more decimals (5) than fiat (2) so we are ok
        val qty = try
        {
            if (s.lowercase() == SEND_ALL_TEXT)  // Special case transferring everything
            {
                coin.fromFinestUnit(coin.wallet.balance)
            }
            else
            {
                s.toCurrency(coin.chain.chainSelector)
            }
        }
        catch (e: java.text.ParseException)
        {
            ui.approximatelyText.text = i18n(R.string.invalidQuantity)
            return false
        }
        catch (e: NumberFormatException)
        {
            ui.approximatelyText.text = i18n(R.string.invalidQuantity)
            return false
        }
        catch (e: ArithmeticException)
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
                ui.xchgRateText.text = ""
                return true
            }
            else
            {
                try
                {
                    ui.approximatelyText.text = ""
                    val mbchToSend = qty / fiatPerCoin
                    val coinPerFiat = CURRENCY_1/fiatPerCoin
                    val sats = coin.toFinestUnit(mbchToSend)
                    if (sats <= dust(coin.chain.chainSelector))
                        ui.approximatelyText.text = i18n(R.string.sendingDustWarning)
                    else
                        ui.approximatelyText.text = i18n(R.string.actuallySendingT) % mapOf("qty" to mBchFormat.format(mbchToSend), "crypto" to coin.currencyCode) + availabilityWarning(coin, mbchToSend)
                    ui.xchgRateText.text = i18n(R.string.exchangeRate) % mapOf("amt" to coin.format(coinPerFiat), "crypto" to coin.currencyCode, "fiat" to fiatCurrencyCode)
                    return true
                }
                catch (e: ArithmeticException)  // Division by zero
                {
                    ui.xchgRateText.text = i18n(R.string.retrievingExchangeRate)
                    return true
                }
                catch (e: java.lang.ArithmeticException)  // Division by zero
                {
                    ui.xchgRateText.text = i18n(R.string.retrievingExchangeRate)
                    return true
                }
            }
        }
        else
        {
            if (qty <= coin.fromFinestUnit(dust(coin.chain.chainSelector)))
                ui.approximatelyText.text = i18n(R.string.sendingDustWarning)
            else
                ui.approximatelyText.text = ""

            val fpc = coin.fiatPerCoin

            if (fpc == -1.toBigDecimal())
            {
                ui.xchgRateText.text = i18n(R.string.unavailableExchangeRate)
                return true
            }
            else if (fpc > BigDecimal.ZERO)
            {
                try
                {
                    var fiatDisplay = qty * fpc
                    val coinPerFiat = CURRENCY_1 / fpc
                    if (ui.approximatelyText.text == "")
                        ui.approximatelyText.text = i18n(R.string.approximatelyT) % mapOf("qty" to fiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) + availabilityWarning(coin, qty)
                    ui.xchgRateText.text = i18n(R.string.exchangeRate) % mapOf("amt" to coin.format(coinPerFiat), "crypto" to coin.currencyCode, "fiat" to fiatCurrencyCode)
                    return true
                }
                catch(e: ArithmeticException)
                {
                   ui.xchgRateText.text = i18n(R.string.retrievingExchangeRate)
                   return true
                }
                catch(e: java.lang.ArithmeticException)
                {
                   ui.xchgRateText.text = i18n(R.string.retrievingExchangeRate)
                   return true
                }
            }
            else
            {
                ui.xchgRateText.text = i18n(R.string.retrievingExchangeRate)
                return true
            }

        }
    }

    /** This triggers image selection (presumably a QR code) */
    private fun openGalleryForImage()
    {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE_RESULT)

        //val intent = Intent(Intent.ACTION_PICK)
        //intent.type = "image/*"
        //startActivityForResult(intent, IMAGE_RESULT)
    }

    /** this handles the result of variety of launched subactivities including:
     * a QR code scan.  We want to accept QR codes of any different format and "do what I mean" based on the QR code's contents
     * an image selection (presumably its a QR code)
     * an identity or trickle pay activity completion
     * */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        LogIt.info(sourceLoc() + " activity completed $requestCode $resultCode")
        displayPendingTopbarMessages()

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
        val shareItem = menu.findItem(R.id.menu_item_share)
        shareItem.setOnMenuItemClickListener {
            val account = accounts[currentlySelectedAccount]
            var recvAddrStr: String? = account?.currentReceive?.address.toString()
            if (recvAddrStr == null) recvAddrStr = i18n(R.string.NoAccounts)
            val receiveAddrSendIntent: Intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, recvAddrStr)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(receiveAddrSendIntent, null)
            startActivity(shareIntent)
            true
        }

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
        outState.putString("sendCurrencyType",
          (exceptNull{ui.sendCurrencyType.selectedItem } ?: currentlySelectedAccount) as String)
        outState.putString("recvCoinType",
          (exceptNull{ui.recvIntoAccount.selectedItem } ?: currentlySelectedAccount) as String)
    }

    @Suppress("UNUSED_PARAMETER")
      /** If user clicks on the receive address, copy it to the clipboard */
    fun onNewAccount(view: View)
    {
        LogIt.info("new account")
        if (app?.accounts?.size ?: 1000 >= MAX_ACCOUNTS)
        {
            displayError(R.string.accountLimitReached)
            return
        }

        val intent = Intent(this@MainActivity, NewAccount::class.java)
        startActivity(intent)
    }

    @Suppress("UNUSED_PARAMETER")
      /** If user clicks on the receive address, copy it to the clipboard */
    fun onReceiveAddrTextClicked(view: View)
    {
        try
        {
            var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val account = accounts[currentlySelectedAccount]
            if (account == null) throw BadCryptoException(R.string.badCryptoCode)

            val recvAddrStr: String? = account.currentReceive?.address?.toString()

            if (recvAddrStr != null)
            {
                var clip = ClipData.newPlainText("text", recvAddrStr)
                clipboard.setPrimaryClip(clip)

                // visual bling that indicates text copied
                ui.receiveAddress.text = i18n(R.string.copiedToClipboard)
                laterUI {
                    delay(5000); accounts[currentlySelectedAccount]?.let { updateReceiveAddressUI(it) }
                }
            }
            else throw UnavailableException(R.string.receiveAddressUnavailable)
        } catch (e: Exception)
        {
            displayException(e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    /** Start the split bill activity when the split bill button is pressed */
    public fun onSplitBill(v: View)
    {
        val intent = Intent(this@MainActivity, SplitBillActivity::class.java)
        startActivity(intent)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onQRfromGalleryClicked(view: View)
    {
        onReadMediaPermissionGranted { openGalleryForImage() }
        // onReadStoragePermissionGranted { openGalleryForImage() }
    }


    @Suppress("UNUSED_PARAMETER")
    /** Start the purchase activity when the purchase button is pressed */
    public fun onPurchaseButton(v: View)
    {
        //val intent = Intent(this@MainActivity, InvoicesActivity::class.java)
        //startActivity(intent)
    }

    public fun onAllButtonClicked(v:View)
    {
        val focus = currentFocus
        if (focus is EditText)
        {
            focus.text.clear()
            focus.text.append(SEND_ALL_TEXT)
        }
    }

    public fun onClearButtonClicked(v:View)
    {
        val focus = currentFocus  // If you don't want this  button to work on this edit text, don't show the button!
        if (focus is EditText)
        {
            focus.text.clear()
        }
    }
    public fun onAmountThousandButtonClicked(v:View)
    {
        val focus = currentFocus  // If you don't want this  button to work on this edit text, don't show the button!
        if (focus is EditText)
        {
            val curText = focus.text.toString()
            var amt = try
            {
                CurrencyDecimal(curText)
            }
            catch(e:java.lang.NumberFormatException)
            {
                if ((focus.text.length == 0) || (curText=="all")) CURRENCY_1
                else return
            }
            amt *= BigDecimal.fromInt(1000)
            focus.set(amt.toString())
        }
    }

    public fun onAmountMillionButtonClicked(v:View)
    {
        val focus = currentFocus  // If you don't want this  button to work on this edit text, don't show the button!
        if (focus is EditText)
        {
            val curText = focus.text.toString()
            var amt = try
            {
                CurrencyDecimal(curText)
            }
            catch(e:java.lang.NumberFormatException)
            {
                if ((focus.text.length == 0) || (curText=="all")) CURRENCY_1
                else return
            }
            amt *= BigDecimal.fromInt(1000000)
            focus.set(amt.toString())
        }
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
        ui.softKeyboardExtensions.visibility = vis

    }

    /** Allow the user to add/edit the send note */
    public fun onEditSendNoteButtonClicked(v: View)
    {
        val esn = ui.editSendNote
        val sn = ui.sendNote
        if (esn == null) return
        if (sn == null) return
        if (esn.visibility == View.VISIBLE) // hit the button again
        {
            esn.visibility = View.GONE
            val tmp = esn.text.toString()
            sn.setVisibility(if (tmp == "") View.GONE else View.VISIBLE)
            return
        }
        sn.setVisibility(View.GONE)
        esn.setVisibility(View.VISIBLE)
        v.getRootView().invalidate()

        laterUI {
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
                        ui.sendNote.let {
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
                            ui.sendNote.let {
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
                        ui.sendNote.let {
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
    }

    public fun setAssetsToTransfer(assets: List<AssetInfo>?=null, account: Account? = null)
    {
        if ((assets == null)||(assets.size == 0))
        {
            sendAssetList = listOf()
            ui.GuiSendAssetList.visibility = View.GONE
            ui.AssetsHeader.visibility = View.GONE
        }
        else
        {
            // if no account is chosen, pick the account of the first asset
            var act = account
            if (act == null) act = assets[0].account  // actually no asset should be in this list that has no account (because how did user select it?)
            if (act == null) return  // we don't know what account to use
            // make sure the send from account combo box is correct.
            ui.sendAccount.setSelection(act.name)
            // filter the assets by the chosen account
            sendAssetList = assets.filter { it.account == act }

            if (sendAssetList.size == 0)
            {
                sendAssetList = listOf()
                ui.GuiSendAssetList.visibility = View.GONE
                ui.AssetsHeader.visibility = View.GONE
            }
            else
            {
                ui.GuiSendAssetList.visibility = View.VISIBLE
                ui.AssetsHeader.visibility = View.VISIBLE

                sendAssetsAdapter = GuiList(ui.GuiSendAssetList, sendAssetList, this, {
                    val ui = AssetSuccinctListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
                    AssetSuccinctBinder(ui, this)
                })
                sendAssetsAdapter.rowBackgroundColors = WallyAssetRowColors
                sendAssetsAdapter.layout()
                sendAssetsLayoutManager.requestLayout()
            }
        }
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

    /** Show or hide the send function visibility */
    public fun sendVisibility(show: Boolean)
    {
        if (show)
        {
            ui.sendUI.visibility = View.VISIBLE
            ui.editSendNoteButton.visibility = View.VISIBLE
            ui.sendCancelButton.visibility = View.VISIBLE
            ui.splitBillButton.visibility = View.GONE
        }
        else
        {
            ui.sendUI.visibility = View.GONE
            ui.sendCancelButton.visibility = View.GONE
            ui.editSendNoteButton.visibility = View.GONE
            ui.splitBillButton.visibility = View.VISIBLE
        }
    }
    /** Show or hide the receive functionality */
    public fun receiveVisibility(show: Boolean)
    {
        if (show)
        {
            ui.receiveUI.visibility = View.VISIBLE
        }
        else
        {
             ui.receiveUI.visibility = View.GONE
        }
    }

    public fun confirmVisibility(show: Boolean)
    {
        if (show)
        {
            ui.SendConfirm.visibility = View.VISIBLE
            ui.sendCancelButton.visibility = View.VISIBLE
            ui.editSendNoteButton.visibility = View.GONE
            ui.splitBillButton.visibility = View.GONE
            ui.sendButton.visibility = View.VISIBLE
            ui.sendButton.text = i18n(R.string.confirm)
        }
        else
        {
            ui.SendConfirm.visibility = View.GONE
            ui.sendButton.text = i18n(R.string.Send)
            ui.editSendNoteButton.visibility = View.GONE
            ui.splitBillButton.visibility = View.VISIBLE
        }
    }


    public fun onSendCancelButtonClicked(v: View)
    {
        confirmVisibility(false)
        sendVisibility(false)
        receiveVisibility(true)
        askedForConfirmation = false

        // Clear out any assets lined up to be transferred if the send is cancelled
        wallyApp?.assetManager?.transferList?.clear()
        setAssetsToTransfer(null)
    }

    @Suppress("UNUSED_PARAMETER")
    /** Create and post a transaction when the send button is pressed */
    public fun onSendButtonClicked(v: View)
    {
        if ((ui.sendUI.visibility != View.VISIBLE)&&(ui.SendConfirm.visibility != View.VISIBLE))  // First step in send, show the UI elements
        {
            receiveVisibility(false)
            sendVisibility(true)
            return
        }
        dbgAssertGuiThread()
        LogIt.info("send button clicked")

        hideKeyboard()
        ui.sendQuantity.clearFocus()
        val note:String? = if (ui.editSendNote.visibility == View.VISIBLE) ui.editSendNote.text.toString() else if (ui.sendNote.visibility == View.VISIBLE) ui.sendNote.text.toString() else null

        if (paymentInProgress != null)
        {
            displayNotice(R.string.Processing)
            coMiscScope.launch {
                paymentInProgressSend()
            }
            return
        }

        var currencyType: String? = ui.sendCurrencyType.selectedItem as String?

        val tmp = ui.sendAccount.selectedItem
        if (tmp == null)
        {
            displayError(R.string.badCryptoCode)
            return
        }

        // Which crypto are we sending
        val walletName: String = try
        {
            tmp as String
        }
        catch (e: NullPointerException)
        {
            displayException(R.string.badCryptoCode, e)
            return
        }
        catch (e: TypeCastException)  // No wallets are defined so no sendCoinType is possible
        {
            displayException(R.string.badCryptoCode, e)
            return
        }
        if (walletName == i18n(R.string.choose))
        {
            displayError(R.string.chooseAccountError)
            return
        }
        val account: Account = accounts.getOrElse(walletName) {
            displayError(R.string.accountUnavailable, R.string.accountUnavailableDetails)
            return@onSendButtonClicked
        }

        if (currencyType == null)
        {
            displayError(R.string.badCryptoCode)
            return
        }

        if (account.locked)
        {
            displayError(R.string.accountLocked)
            return
        }

        val amtstr: String = ui.sendQuantity.text?.toString() ?: ""

        var spendAll = false
        var amount: BigDecimal = try
        {
            // Its ok to send nothing if you are sending assets
            if ((amtstr.isEmpty()) && (sendAssetList.isNotEmpty())) BigDecimal.ZERO
            // Special case transferring everything
            else if (amtstr.lowercase() == SEND_ALL_TEXT)
            {
                spendAll = true
                account.fromFinestUnit(account.wallet.balance)
            }
            // Transferring a specific amount
            else
            {
                amtstr.toCurrency(account.chain.chainSelector)
            }
        }
        catch (e: java.text.ParseException)
        {
            displayError(R.string.badAmount, i18n(R.string.badAmount) + " " + amtstr)
            return
        }
        catch (e: NumberFormatException)
        {
            displayError(R.string.badAmount, i18n(R.string.badAmount) + " " + amtstr)
            return
        }
        catch (e: ArithmeticException)  // Rounding error
        {
            // If someone is asking to send sub-satoshi quantities, round up and ask them to click send again.
            ui.sendQuantity.text.clear()
            ui.sendQuantity.text.append(amtstr.toCurrency(account.chain.chainSelector).toString())
            displayError(R.string.badAmount, R.string.subSatoshiQuantities)
            ui.approximatelyText.text = i18n(R.string.roundedUpClickSendAgain)
            return
        }
        catch(e: ParseException)
        {
            displayError(R.string.badAmount, amtstr)
            return
        }

        // Make sure the address is consistent with the selected coin to send
        val addrText = ui.sendToAddress.text.trim().toString()
        val sendAddr = try
        {
            PayAddress(addrText)
        }
        catch (e: WalletNotSupportedException)
        {
            val details = i18n(R.string.badAddress) + " " + if (addrText == "") i18n(R.string.empty) else addrText
            displayError(R.string.badAddress, details)
            return
        }
        catch (e: UnknownBlockchainException)
        {
            val details = i18n(R.string.unknownCurrency) + " " + if (addrText == "") i18n(R.string.empty) else addrText
            displayError(R.string.badAddress, details)
            return
        }
        if (account.wallet.chainSelector != sendAddr.blockchain)
        {
            displayError(R.string.chainIncompatibleWithAddress, ui.sendToAddress.text.toString())
            return
        }
        if (sendAddr.type == PayAddressType.NONE)
        {
            displayError(R.string.badAddress, ui.sendToAddress.text.toString())
            return
        }

        if (currencyType == account.currencyCode)
        {
        }
        else if (currencyType == fiatCurrencyCode)
        {
            val fpc = account.fiatPerCoin
            try
            {
                if (fpc != BigDecimal.ZERO)
                    amount = amount / fpc
                else throw UnavailableException(R.string.retrievingExchangeRate)
            }
            catch (e:java.lang.ArithmeticException)
            {
                throw UnavailableException(R.string.retrievingExchangeRate)
            }
            catch (e:ArithmeticException)
            {
                throw UnavailableException(R.string.retrievingExchangeRate)
            }
        }
        else throw BadUnitException()

        val preferenceDB = getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)
        val confirmAmtString = preferenceDB.getString(CONFIRM_ABOVE_PREF, "0") ?: "0"
        val confirmAmt = try
        {
            CurrencyDecimal(confirmAmtString)
        }
        catch (e:Exception)
        {
            CURRENCY_ZERO
        }

        // TODO confirm assets
        if ((amount >= confirmAmt)&&(!askedForConfirmation))
        {
            sendVisibility(false)
            val fiatAmt = if (account.fiatPerCoin > BigDecimal.ZERO)
            {
                var fiatDisplay = amount * account.fiatPerCoin
                i18n(R.string.approximatelyT) % mapOf("qty" to fiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode)
            }
            else
            {
                ""
            }

            val assetsNote = if (sendAssetList.isEmpty()) "" else i18n(R.string.AndSomeAssets)
            ui.SendConfirm.text = i18n(R.string.SendConfirmSummary) % mapOf("amt" to account.format(amount), "currency" to account.currencyCode,
            "dest" to sendAddr.toString(),
            "inFiat" to fiatAmt,
            "assets" to assetsNote)
            confirmVisibility(true)
            askedForConfirmation = true  // Require send button to be pressed ONE MORE TIME!
        }
        else  // ok actually send
        {
            displayNotice(R.string.Processing)
            coMiscScope.launch {  // avoid network on main thread exception
                val cs = account.wallet.chainSelector
                var tx: iTransaction = txFor(cs)
                try
                {
                    val atomAmt = account.toFinestUnit(amount)
                    if (sendAssetList.size == 0)
                    {
                        // If we are spending all, then deduct the fee from the amount (which was set above to the full ungrouped balance)
                        tx = account.wallet.send(atomAmt, sendAddr, spendAll, false, note = note)
                    }
                    else
                    {
                        // TBD: It would be interesting to automatically use an authority, if one is sent to this account: TxCompletionFlags.USE_GROUP_AUTHORITIES
                        var cflags = TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.FUND_GROUPS or TxCompletionFlags.SIGN

                        if (spendAll)
                        {
                            cflags = cflags or TxCompletionFlags.SPEND_ALL_NATIVE or TxCompletionFlags.DEDUCT_FEE_FROM_OUTPUT
                        }

                        // Construct outputs that send all selected assets
                        var assetDustOut = 0L
                        for (asset in sendAssetList)
                        {
                            if (asset.account == account)
                            {
                                val aout = txOutputFor(cs)
                                aout.amount = dust(cs)
                                assetDustOut += aout.amount
                                aout.script = sendAddr.groupedConstraintScript(asset.groupInfo.groupId, asset.displayAmount ?: 1)
                                tx.add(aout)
                            }
                            else
                            {
                                LogIt.info("asset from the wrong account in sendAssetList!  (Should never happen)")
                            }
                        }
                        //
                        // Construct an output that sends the right amount of native coin
                        if (atomAmt > 0)
                        {
                            val coinOut = txOutputFor(cs)
                            coinOut.amount = atomAmt
                            if (spendAll) coinOut.amount -= assetDustOut  // it doesn't matter because txCompleter will solve but needs to not be too much
                            coinOut.script = sendAddr.outputScript()
                            tx.add(coinOut)
                        }

                        // Attempt to pay for the constructed transaction
                        account.wallet.txCompleter(tx, 0, cflags, null, if (spendAll) (tx.outputs.size-1) else null)
                        account.wallet.send(tx,false, note = note)
                    }
                    LogIt.info("Sending TX: ${tx.toHex()}")
                    onSendSuccess(atomAmt, sendAddr, tx)
                    wallyApp?.assetManager?.transferList?.removeAll(sendAssetList)
                    laterUI {
                        setAssetsToTransfer(null)
                        sendVisibility(false)
                        confirmVisibility(false)
                        receiveVisibility(true)
                    }
                }
                catch (e: Exception)  // We don't want to crash, we want to tell the user what went wrong
                {
                    laterUI {
                        confirmVisibility(false)
                        sendVisibility(true)
                    }
                    displayException(e)
                    handleThreadException(e)
                    LogIt.info("Failed transaction is: ${tx.toHex()}")
                    askedForConfirmation = false  // Force reconfirm is there is any error with the send
                }
            }

            val sn = ui.sendNote
            if (sn != null)
            {
                sn.text = ""
                sn.visibility = View.GONE
            }
            ui.editSendNote.let { it.visibility = View.GONE }
            askedForConfirmation = false  // We are done with a send so reset state machine
        }
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

