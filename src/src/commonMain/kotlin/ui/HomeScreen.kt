package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.ui.views.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.nexa.libnexakotlin.NexaFormat
import androidx.compose.ui.zIndex
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.HomeScreen")
// stores the account name we are receiving into and the receive address as a pair
val currentReceiveShared: MutableStateFlow<Pair<String,String>> = MutableStateFlow(Pair("",""))
var sendToAddress: MutableStateFlow<String> = MutableStateFlow("")


/* Since composable state needs to be defined within a composable, imagine this composable is actually a singleton class,
with member variables and member functions defined in it.
We could use a composable "State Holder" (in theory) to capture all the state needed by the member functions, but creating a state holder appears to entail
writing a vast amount of inscrutible garbage rather than actual useful code.
* */

private val _sendFromAccount = MutableStateFlow<String>("")
@OptIn(ExperimentalResourceApi::class)
@Composable fun HomeScreen(selectedAccount: MutableStateFlow<Account?>, driver: MutableState<GuiDriver?>, nav: ScreenNav)
{
    var sendNote = remember { mutableStateOf("") }
    val sendCurrencyChoices: MutableState<List<String>> = remember { mutableStateOf(listOf()) }

    var isSending by remember { mutableStateOf(false) }
    var isScanningQr by remember { mutableStateOf(false) }

    var sendQuantity = remember { mutableStateOf<String>("") }

    var warnBackupRecoveryKey = remember { mutableStateOf(false) }

    var topInformation by remember { mutableStateOf("") }
    var approximatelyText by remember { mutableStateOf("") }
    var xchgRateText by remember { mutableStateOf("") }

    /** If there's a payment proposal that this app has seen, information about it is located here */
    val paymentInProgress: MutableState<ProspectivePayment?> = remember { mutableStateOf(null) }
    var currencyCode by remember { mutableStateOf(i18n(S.choose)) } // TODO: get from local db

    /** If we've already put up an error for this address, don't do it again */
    var alreadyErroredAddress: MutableState<PayAddress?> = remember { mutableStateOf(null) }

    /** remember last send coin selected */
    var lastSendFromAccountName by remember { mutableStateOf("") }

    // Put all the fast-inited vars above so they are available during the blocking initializations below
    // or you may get java.lang.IllegalStateException: Reading a state that was created after the snapshot...

    val ags = accountGuiSlots.collectAsState()
    val synced = remember { mutableStateOf(wallyApp!!.isSynced()) }
    var currentReceive = currentReceiveShared.collectAsState()
    val sendFromAccount = _sendFromAccount.collectAsState()
    val _sendToAddress:String = sendToAddress.collectAsState().value

    val clipmgr: ClipboardManager = LocalClipboardManager.current

    var oldDriver = remember { mutableStateOf<GuiDriver?>(null) }



    /**
     * View for receiving funds
     */
    @Composable
    fun ReceiveView(onAccountNameSelected: (String) -> Unit)
    {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SectionText(text = i18n(S.Receive))  // Receive into account
                Spacer(modifier = Modifier.width(8.dp))
                AccountDropDownSelector(
                  ags.value,
                  currentReceive.value.first,
                  onAccountNameSelected = onAccountNameSelected,
                )
            }
            AddressQrCode(currentReceive.value.second)
            // update the share function based on whatever my current receive is
            ToBeShared = { currentReceive.value.second }
        }
    }



    /** Calculate whether there is enough money available to make a payment and return an appropriate info string for the GUI. Does not need to be called within GUI context */
    fun availabilityWarning(account: Account, qty: BigDecimal): String
    {
        val cbal = account.balance
        val ubal = account.unconfirmedBalance
        if (cbal + ubal < qty)
        {
            return " " + i18n(S.moreThanAvailable)
        }
        if (cbal < qty)
        {
            return " " + i18n(S.moreThanConfirmedAvailable)
        }
        return ""
    }


    /** This function both validates the quantity in the send field (return true/false) and updates the "approximately..." text
    It is therefore called both as an onchange validator and directly (when the local currency changes, for example).
     */
    fun checkSendQuantity(s: String, account: Account): Boolean
    {
        val SEND_ALL_TEXT = i18n(S.sendAll)

        if (s == "")
        {
            approximatelyText = i18n(S.emptyQuantityField)
            return false
        }

        val qty: BigDecimal = try
        {
            if (s.lowercase() == SEND_ALL_TEXT)  // Special case transferring everything
            {
                account.fromFinestUnit(account.wallet.balance)
            }
            else
            {
                s.toCurrency(account.chain.chainSelector)
            }
        }
        catch (e: NumberFormatException)
        {
            approximatelyText = i18n(S.invalidQuantity)
            return false
        }
        catch (e: ArithmeticException)
        {
            approximatelyText = i18n(S.invalidQuantityTooManyDecimalDigits)
            return false
        }
        catch (e: Exception) // This used to be a catch (e: java.text.ParseException)
        {
            approximatelyText = i18n(S.invalidQuantity)
            return false
        }

        if (currencyCode == fiatCurrencyCode)
        {
            val fiatPerCoin = account.fiatPerCoin
            if (account.fiatPerCoin == -1.toBigDecimal())
            {
                approximatelyText = i18n(S.unavailableExchangeRate)
                xchgRateText = ""
                return true
            }
            else
            {
                try
                {
                    approximatelyText = ""
                    val cryptoToSend = qty / fiatPerCoin
                    val coinPerFiat = CURRENCY_1 / fiatPerCoin
                    val sats = account.toFinestUnit(cryptoToSend)
                    approximatelyText = if (sats <= dust(account.chain.chainSelector))
                        i18n(S.sendingDustWarning)
                    else
                        i18n(S.actuallySendingT) % mapOf("qty" to mBchFormat.format(cryptoToSend), "crypto" to account.currencyCode) + availabilityWarning(account, cryptoToSend)
                    xchgRateText = i18n(S.exchangeRate) % mapOf("amt" to account.format(coinPerFiat), "crypto" to account.currencyCode, "fiat" to fiatCurrencyCode)
                    return true
                }
                catch (e: ArithmeticException)  // Division by zero
                {
                    xchgRateText = i18n(S.retrievingExchangeRate)
                    return true
                }
            }
        }
        else
        {
            approximatelyText = if (qty <= account.fromFinestUnit(dust(account.chain.chainSelector)))
                i18n(S.sendingDustWarning)
            else
                ""


            val doIhaveEnough = availabilityWarning(account, qty)
            if (doIhaveEnough != "")
            {
                approximatelyText = doIhaveEnough
                return true
            }

            val fpc = account.fiatPerCoin

            if (fpc == (-1).toBigDecimal())
            {
                xchgRateText = i18n(S.unavailableExchangeRate)
                return true
            }
            else if (fpc > BigDecimal.ZERO)
            {
                return try
                {
                    var fiatDisplay = qty * fpc
                    val coinPerFiat = CURRENCY_1 / fpc
                    if (approximatelyText == "")
                    {
                        approximatelyText = i18n(S.approximatelyT) % mapOf("qty" to FiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) + availabilityWarning(account, qty)
                    }
                    xchgRateText = i18n(S.exchangeRate) % mapOf("amt" to account.format(coinPerFiat), "crypto" to account.currencyCode, "fiat" to fiatCurrencyCode)
                    true
                }
                catch (e: ArithmeticException)
                {
                    xchgRateText = i18n(S.retrievingExchangeRate)
                    true
                }
            }
            else
            {
                xchgRateText = i18n(S.retrievingExchangeRate)
                return true
            }
        }
    }

    /** Set the send currency type spinner options to your default fiat currency or your currently selected crypto
    Might change if the user changes the default fiat or crypto */
    fun updateSendCurrencyType(account: Account?)
    {
        account?.let { acc ->
            // If we don't know the exchange rate, we can't offer fiat entry
            sendCurrencyChoices.value = if (acc.fiatPerCoin != -1.toBigDecimal()) listOf(acc.currencyCode, fiatCurrencyCode) else listOf(acc.currencyCode)

            // If we switched to a currency code we don't have, set it to the first one we support
            if (!sendCurrencyChoices.value.contains(currencyCode)) currencyCode = sendCurrencyChoices.value.first()
        }
    }

    fun updateSendAccount(account: Account?)
    {
        if (account != null)
        {
            _sendFromAccount.value = account.name
            updateSendCurrencyType(account)
            checkSendQuantity(sendQuantity.value, account)
        }
    }


    /** Get the account we are currently sending from out of the GUI.
     * This function will default to (and set sendFromAccount) the selected account if the sendFromAccount variable is invalid */
    fun getSendFromAccount(): Account?
    {
        // Get an account from the sendFromAccount string
        var account = wallyApp!!.accounts[sendFromAccount.value]
        if (account == null)   // if no sendFromAccount, grab the selected account
        {
            account = selectedAccount.value
            if (account == null)
            {
                account = wallyApp!!.nullablePrimaryAccount
            }
            updateSendAccount(account)
        }
        return account
    }


    /** Find an account that can send to this blockchain and switch the send account to it */
    fun updateSendAccount(chainSelector: ChainSelector): Account?
    {
        val account = getSendFromAccount()

        // First see if the current selection is compatible with what we want.
        // This keeps the user's selection if multiple accounts are compatible
        if (account?.wallet?.chainSelector != chainSelector)  // Its not so find one that is
        {
            val matches = wallyApp!!.accountsFor(chainSelector)
            if (matches.size > 0)
            {
                updateSendAccount(matches.first())
                return matches.first()
            }
        }
        updateSendAccount(account)
        return account
    }

    /** Find an account that can send to this PayAddress and switch the send account to it */
    fun updateSendAccount(pa: PayAddress): Account?
    {
        if (pa.type == PayAddressType.NONE) return getSendFromAccount() // nothing to update
        return updateSendAccount(pa.blockchain)
    }

    /** Update the GUI send address field, and all related GUI elements based on the provided payment address */
    fun updateSendAddress(pa: PayAddress)
    {
        if (pa.type == PayAddressType.NONE) return  // nothing to update
        dbgAssertGuiThread()

        sendToAddress.value = pa.toString()
        paymentInProgress.value = null

        // Change the send currency type to reflect the pasted data if I need to
        updateSendAccount(pa)
    }

    fun updateSendAddress(text: String, updateIfNonAddress:Boolean = false): PayAddress
    {
        val t = text.trim()
        return try
        {
            val pa = PayAddress(t) // attempt to convert into an address to trigger an exception and a subsequent UI error if its bad
            updateSendAddress(pa)
            pa
        }
        catch (e: UnknownBlockchainException)
        {
            // ok let's try to pick an address out of a mess of text
            val pa = scanForFirstAddress(t)
            if (pa != null)
            {
                updateSendAddress(pa); pa
            }
            else
            {
                if (updateIfNonAddress)
                {
                    sendToAddress.value = t
                }
                throw e
            }
        }
    }



    fun updateSendBasedOnPaymentInProgress()
    {
        try {
            val pip = paymentInProgress.value
            if (pip == null)
            {
                if (topInformation.isNotEmpty())
                {
                    topInformation = ""
                }
            }
            else
            {
                val chainSelector = pip.crypto
                if (chainSelector == null)
                {
                    paymentInProgress.value == null
                    displayError(S.badCryptoCode, paymentInProgress.toString())
                    return
                }
                val a = wallyApp ?: return
                val acts = a.accountsFor(chainSelector)

                var amt: BigDecimal = if (acts.size == 0)
                {
                    paymentInProgress.value == null
                    displayNotice(S.badCryptoCode, chainToCurrencyCode[chainSelector] ?: i18n(S.unknownCurrency))
                    a.primaryAccount.fromFinestUnit(pip.totalSatoshis)
                }
                else if (acts.size > 1)
                {
                    // TODO: ui.sendAccount.setSelection(i18n(S.choose))
                    acts[0].fromFinestUnit(pip.totalSatoshis)
                }
                else
                {
                    acts[0].fromFinestUnit(pip.totalSatoshis)
                }

                // This payment in progress looks ok, set up the UX to show it
                if (true)
                {
                    updateSendAccount(chainSelector)
                    sendQuantity.value = mBchFormat.format(amt)
                    selectedAccount.value?.let {
                        checkSendQuantity(sendQuantity.value, it)
                    }
                    pip.memo?.let {
                        topInformation = it
                    }

                    /*
                    if (pip.outputs.size > 1)
                    {
                        onSendToAddress("")
                        onSendToAddress(i18n(S.multiple))
                    }
                     */

                    sendToAddress.value = ""
                    var count = 0
                    paymentInProgress.value?.outputs?.let {
                        var tmp = StringBuilder()
                        for (out in it)
                        {
                            val addr = out.script.address.toString()
                            val spacer = if (count > 0) " " else ""

                            tmp.append(spacer)
                            tmp.append(addr)
                            count += 1
                            if (count > 4)
                            {
                                tmp.append("...")
                                break
                            }
                        }
                        sendToAddress.value = tmp.toString()
                    }
                }
            }
        }
        catch (e: Exception)
        {

        }
    }

    fun onAccountSelected(c: Account?)
    {
        wallyApp!!.focusedAccount = c
        try
        {
            if (c != null)
            {
                assert(c.visible)
                lastSendFromAccountName = c.name
                val sendAddr = PayAddress(_sendToAddress.trim())
                if (c.wallet.chainSelector != sendAddr.blockchain)
                {
                    if (sendAddr != alreadyErroredAddress.value)
                    {
                        displayError(S.chainIncompatibleWithAddress,
                          i18n(S.chainIncompatibleWithAddressDetails) % mapOf("walletCrypto" to (chainToCurrencyCode[c.wallet.chainSelector]
                            ?: i18n(S.unknownCurrency)), "addressCrypto" to (chainToCurrencyCode[sendAddr.blockchain] ?: i18n(S.unknownCurrency))))

                        alreadyErroredAddress.value = sendAddr
                    }
                    updateSendAccount(sendAddr)
                }
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

    }

    // Handle incoming GUI changes
    if (driver.value != oldDriver.value)
    {
        val tmp = driver.value
        if (tmp?.sendAddress != null)  // If we are driving send data, fill all the fields
        {
            isSending = true
            tmp.sendAddress.let {
                sendToAddress.value = it
                try // If the address blockchain does not match, auto-pick one that does.  But if it does match, don't touch it.
                {
                    val payAddress: PayAddress = PayAddress(tmp.sendAddress)
                    val from = wallyApp!!.accounts[sendFromAccount.value]
                    if (from != null)
                    {
                        if (from.chain.chainSelector != payAddress.blockchain)
                        {
                            val acts = wallyApp!!.accountsFor(payAddress.blockchain)
                            if (acts.size > 0) updateSendAccount(acts[0])
                        }
                    }
                }
                catch (e: Exception) // not something we can turn into an address
                {

                }
            }
            tmp.amount?.let { sendQuantity.value = NexaFormat.format(it) }
            tmp.note?.let { sendNote.value = it }
            tmp.account?.let { updateSendAccount(it) }

            if (tmp?.show?.contains(ShowIt.WARN_BACKUP_RECOVERY_KEY) == true) warnBackupRecoveryKey.value = true
            oldDriver.value = driver.value  // If I don't clear this mutable state, it'll set every single time, rendering these fields uneditable
        }
    }


    // Now specify some state collectors.  These are coroutines that update state from other areas of the code

    // If the selected account changes, we need to update the receiving address
    LaunchedEffect(selectedAccount) {
        selectedAccount.collect {
            selectedAccount.value?.let {
                it.onUpdatedReceiveInfoCommon { recvAddrStr ->
                    currentReceiveShared.value = Pair(it.name, recvAddrStr)
                }
            }
        }
    }

    // During startup, there is a race condition between loading the accounts and the display of this screen.
    // So if the selectedAccount is null, wait for some accounts to appear
    LaunchedEffect(Unit) {
        while(selectedAccount.value == null)
        {
            delay(100)
            val tmp = wallyApp?.focusedAccount
            if ((selectedAccount.value == null) && (tmp != null))
            {
                selectedAccount.value = tmp
                updateSendAccount(tmp)
                currencyCode = tmp.currencyCode
                break
            }
        }
    }

    // Update the syncronization icon based on the underlying synced status
    LaunchedEffect(Unit) {
        try
        {
            while (true)
            {
                synced.value = wallyApp!!.isSynced()
                delay(1000)
            }
        }
        catch (e: IllegalStateException)
        {
            // When the mutableState is stale, this exception is thrown.
            // Stale state means we just want to stop checking; a different launch is checking the new state
        }
    }

    if (sendFromAccount.value == "")
    {
        try
        { val act = wallyApp!!.preferredVisibleAccount()
            updateSendAccount(act)
        }
        catch(e:Exception) {}
    }


    Box(modifier = WallyPageBase) {
        // Don't show the thumb buttons if the softkeyboard is up, because the user is keying something in, not one-handing the phone
        if (!isSoftKeyboardShowing.collectAsState().value)
        {
            // pad these buttons on the bottom to be convenient to press with your thumb
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).zIndex(2f).padding(8.dp, 8.dp, 8.dp, 60.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (platform().hasGallery)
                    WallyBoringIconButton("icons/gallery.xml", Modifier.width(48.dp).height(48.dp).zIndex(1f)) {
                        ImageQrCode {
                            it?.let {
                                clearAlerts()
                                if (!wallyApp!!.handlePaste(it)) wallyApp!!.handleNonIntentText(it) }
                        }
                    }
                if (platform().hasQrScanner)
                    WallyBoringIconButton("icons/scanqr2.xml", Modifier.width(48.dp).height(48.dp).zIndex(1f)) {
                        clearAlerts()
                        isScanningQr = true
                    }

                if (!platform().usesMouse)
                {
                    WallyBoringIconButton("icons/clipboard.xml", Modifier.width(48.dp).height(48.dp).zIndex(1f)) {
                        clearAlerts()
                        val cliptext = clipmgr.getText()?.text
                        if (cliptext != null && cliptext != "")
                        {
                            if (!wallyApp!!.handlePaste(cliptext)) displayNotice(S.pasteUnintelligible)
                        }
                        else
                        {
                            displayNotice(S.pasteIsEmpty)
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isSending)
            {
                SendView(
                      selectedAccountName = sendFromAccount.value,
                      accountNames = accountGuiSlots.value.map { it.name },
                      currencyCode = currencyCode,
                      toAddress = _sendToAddress,
                      note = sendNote,
                      sendQuantity = sendQuantity,
                      paymentInProgress = paymentInProgress.value,
                      setSendQuantity = {
                          sendQuantity.value = it
                      },
                      onCurrencySelectedCode = {
                          currencyCode = it
                      },
                      setToAddress = { sendToAddress.value = it },
                      onCancel = {
                          isSending = false
                          driver.value = null // hack to fix send section reappearing on nav after an address is provided
                          clearAlerts()  // If user manually cancelled, they understood the problem
                          wallyApp!!.assetManager.clearTransferListOfAssetsHeldBy(sendFromAccount.value)
                                 },
                      approximatelyText = approximatelyText,
                      currencies = sendCurrencyChoices,
                      xchgRateText = xchgRateText,
                      onPaymentInProgress = {
                          paymentInProgress.value = it
                      },
                      updateSendBasedOnPaymentInProgress = {
                          updateSendBasedOnPaymentInProgress()
                      },
                      onApproximatelyText = {
                          approximatelyText = it
                      },
                      checkSendQuantity = {s, account ->
                          checkSendQuantity(s, account)
                      },
                      onSendSuccess = {
                          wallyApp!!.assetManager.clearTransferListOfAssetsHeldBy(sendFromAccount.value)
                          sendToAddress.value = ""
                          sendQuantity.value = ""
                          isSending = false
                      },
                      onAccountNameSelected = {
                          val act = wallyApp!!.accounts[it]
                          act?.let {
                              updateSendAccount(it)
                          }
                      }
                    )
                }
                if (!isSending)
                {
                    Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                        if (platform().usesMouse)
                            WallyBoringLargeIconButton("icons/clipboard.xml") {
                                val cliptext = clipmgr.getText()?.text
                                if (cliptext != null && cliptext != "")
                                {
                                    if (!wallyApp!!.handlePaste(cliptext)) displayNotice(S.pasteUnintelligible)
                                }
                                else
                                {
                                    displayNotice(S.pasteIsEmpty)
                                }
                            }
                        WallyBoringLargeTextButton(S.Send) {
                            clearAlerts()
                            isSending = true
                        }
                        WallyBoringLargeTextButton(S.title_split_bill) {
                            clearAlerts()
                            nav.go(ScreenId.SplitBill)
                        }
                    }
                    WallyDivider()
                    ReceiveView(
                      onAccountNameSelected = { accountName ->
                          val act = wallyApp!!.accounts[accountName]
                          if (act != null)
                          {
                              selectedAccount.value = act
                              wallyApp?.focusedAccount = act
                          }
                      }
                    )
                }

                WallyDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    if(synced.value)
                        ResImageView("icons/check.xml", modifier = Modifier.size(45.dp))
                    else
                    {
                        Box(Modifier.size(45.dp)) { LoadingAnimationContent() }
                    }
                    Column(modifier = Modifier.weight(0.75f).height(IntrinsicSize.Min), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        SectionText(S.AccountListHeader, Modifier.weight(1f))
                        HorizontalDivider(modifier = Modifier.width(100.dp), color = listDividerFg, thickness = 2.dp)
                    }
                    ResImageView("icons/plus.xml", modifier = Modifier.size(45.dp).clickable {
                        clearAlerts()
                        nav.go(ScreenId.NewAccount)
                    })
                    Spacer(Modifier.width(8.dp))
                }
                AccountListView(
                  nav,
                  selectedAccount,
                  ags.value,
                  modifier = Modifier.weight(1f),
                  onAccountSelected = {
                      if (selectedAccount.value == it)
                      {
                          // If you click on an already selected account, deselect it
                          // This deselect functionality anticipates having the selected account's line item grow into a more detailed view.
                          selectedAccount.value = null
                          onAccountSelected(null)
                      }
                      else
                      {
                          selectedAccount.value = it
                          updateSendAccount(it) // if an account is selected in the account list, the send from account is updated
                          onAccountSelected(it)
                      }
                  }
                )
                if (isScanningQr && platform().hasQrScanner)
                {
                    QrScannerDialog(
                      onDismiss = {
                          clearAlerts()
                          isScanningQr = false
                      },
                      onScan = {
                          if (it.isNotEmpty() && isScanningQr)
                          {
                              // Clean out an old payment protocol if you are pasting a new send in
                              // paymentInProgress.value = null
                              isScanningQr = false
                              wallyApp!!.handlePaste(it)
                          }
                      }
                    )
                }
            }
        }
    }


