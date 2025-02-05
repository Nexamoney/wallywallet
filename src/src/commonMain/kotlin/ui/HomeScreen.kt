package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.ui.views.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.NexaFormat
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui2.theme.floatingActionBarBackground
import info.bitcoinunlimited.www.wally.ui2.theme.listDividerFg
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallyPageBase
import info.bitcoinunlimited.www.wally.ui2.views.*
import kotlinx.coroutines.flow.asStateFlow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.HomeScreen")

/* Since composable state needs to be defined within a composable, imagine this composable is actually a singleton class,
with member variables and member functions defined in it.
We could use a composable "State Holder" (in theory) to capture all the state needed by the member functions, but creating a state holder appears to entail
writing a vast amount of inscrutible garbage rather than actual useful code.
* */

private val sendFromAccountShared = MutableStateFlow<Account?>(null)

@Composable fun IconTextButton(
  textRes: Int,
  resPath: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
)
{
    val actionBarSpacerHeight = if (platform().hasGallery) // Android
        4.dp
    else // iOS
        2.dp
    Column(
      modifier = modifier.zIndex(1f).padding(8.dp, 12.dp, 8.dp, 0.dp).background(Color.Transparent).clickable { onClick() },
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ResImageView(
          resPath = resPath,
          modifier = Modifier.width(24.dp).height(24.dp).zIndex(1f).clickable { onClick() }
        )
        Spacer(Modifier.height(actionBarSpacerHeight).clickable { onClick() })
        Text(
          text = i18n(textRes),
          modifier = Modifier.clickable { onClick() },
          color = Color.White,
          fontSize = FontScale(0.55)
        )
    }
}

@Composable fun HomeScreen(
  selectedAccount: MutableStateFlow<Account?>,
  driver: MutableState<GuiDriver?>,
  nav: ScreenNav,
)
{
    var sendNote = remember { mutableStateOf("") }
    val sendCurrencyChoices: MutableState<List<String>> = remember { mutableStateOf(listOf("NEX", "tNEX", "rNEX")) }

    var isSending by remember { mutableStateOf(false) }
    var isScanningQr by remember { mutableStateOf(false) }

    var sendQuantity = remember { mutableStateOf<String>("") }

    var warnBackupRecoveryKey = remember { mutableStateOf(false) }

    var topInformation by remember { mutableStateOf("") }
    var approximatelyText by remember { mutableStateOf("") }
    var xchgRateText by remember { mutableStateOf("") }

    /** If there's a payment proposal that this app has seen, information about it is located here */
    val paymentInProgress: MutableState<ProspectivePayment?> = remember { mutableStateOf(null) }
    var currencyCode = currencyCodeShared.asStateFlow()

    /** If we've already put up an error for this address, don't do it again */
    var alreadyErroredAddress: MutableState<PayAddress?> = remember { mutableStateOf(null) }

    /** remember last send coin selected */
    var lastSendFromAccountName by remember { mutableStateOf("") }

    // Put all the fast-inited vars above so they are available during the blocking initializations below
    // or you may get java.lang.IllegalStateException: Reading a state that was created after the snapshot...

    val ags = accountGuiSlots.collectAsState()
    val synced = remember { mutableStateOf(wallyApp!!.isSynced()) }
    val sendFromAccount = sendFromAccountShared.collectAsState().value
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
                SectionText(text = i18n(S.ReceiveIntoAccount))  // Receive into account
                Spacer(modifier = Modifier.width(8.dp))
                AccountDropDownSelector(
                  ags.value,
                  selectedAccount.value?.name,
                  onAccountNameSelected = onAccountNameSelected,
                )
            }
            val account = selectedAccount.collectAsState().value
            if (account != null)
                account.currentReceiveObservable.value?.let { currentReceive ->
                    AddressQrCode(currentReceive.address?.toString() ?: "")
                    ToBeShared = { currentReceive.address?.toString() ?: "" }
                }
            // update the share function based on whatever my current receive is
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

        if (currencyCode.value == fiatCurrencyCode)
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
            if (!sendCurrencyChoices.value.contains(currencyCode.value)) currencyCodeShared.value = sendCurrencyChoices.value.first()
        }
    }

    fun updateSendAccount(account: Account?)
    {
        if (account != null)
        {
            sendFromAccountShared.value = account
            updateSendCurrencyType(account)
            checkSendQuantity(sendQuantity.value, account)
        }
    }


    /** Get the account we are currently sending from out of the GUI.
     * This function will default to (and set sendFromAccount) the selected account if the sendFromAccount variable is invalid */
    fun getSendFromAccount(): Account?
    {
        // Get an account from the sendFromAccount string
        var account = sendFromAccount
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
        wallyApp!!.focusedAccount.value = c
        var errorShown = false
        try
        {
            if (c != null)
            {
                assert(c.visible)
                lastSendFromAccountName = c.name
                val sendAddr = PayAddress(_sendToAddress.trim())
                if (c.wallet.chainSelector != sendAddr.blockchain)
                {
                    // If the send view is visible, then show an error indicating that the account you've just selected
                    // isn't compatible with the pasted address.  But if the send view is not visible, then don't show;
                    // the user is probably doing something else right now.
                    if (isSending && sendAddr != alreadyErroredAddress.value)
                    {
                        displayError(S.chainIncompatibleWithAddress,
                          i18n(S.chainIncompatibleWithAddressDetails) % mapOf("walletCrypto" to (chainToCurrencyCode[c.wallet.chainSelector]
                            ?: i18n(S.unknownCurrency)), "addressCrypto" to (chainToCurrencyCode[sendAddr.blockchain] ?: i18n(S.unknownCurrency))))
                        errorShown = true
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
        if (!errorShown) clearAlerts()  // because user did something
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
                    val from = sendFromAccount
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

    // Redisplay the accounts 4x a second in dev mode where data is being shown that does not normally
    // trigger an account update (like connection state changes)
    LaunchedEffect(Unit) {
        while(true)
        {
            if (devMode)
            {
                accountChangedNotification.send("*all changed*")
                delay(250)
            }
            else delay(2000)
        }
    }

    // During startup, there is a race condition between loading the accounts and the display of this screen.
    // So if the selectedAccount is null, wait for some accounts to appear
    LaunchedEffect(Unit) {
        while(selectedAccount.value == null)
        {
            delay(100)
            val tmp = wallyApp?.focusedAccount?.value
            if ((selectedAccount.value == null) && (tmp != null))
            {
                selectedAccount.value = tmp
                updateSendAccount(tmp)
                currencyCodeShared.value = tmp.currencyCode
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

    if (sendFromAccount == null)
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
            val actionBarIconHeight = 20.dp
            val actionBarFontScale = 0.68
            val actionBarButtonVerticalPadding = 12.dp
            val actionBarButtonHorizontalPadding = 12.dp
            val actionBarSpacerHeight = if (platform().hasGallery) // Android
                8.dp
            else // iOS
                2.dp
            val actionBarStartEndPadding = if (platform().hasGallery) // Android
                0.dp // 56.dp
            else // iOS
                0.dp // 96.dp
            val actionBarBottomPadding = if (platform().hasGallery) // Android
                60.dp
            else // iOS
                20.dp

            val tbSize = Modifier.size(64.dp, 60.dp)
            // pad these buttons on the bottom to be convenient to press with your thumb
            Row(
              modifier = Modifier
                // .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(2f).padding(actionBarStartEndPadding, 8.dp, actionBarStartEndPadding, actionBarBottomPadding)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(32.dp))
                .background(floatingActionBarBackground, shape = RoundedCornerShape(32.dp)),
              horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (platform().hasGallery)
                {
                    Spacer(Modifier.width(8.dp))
                    IconTextButton(
                        textRes = S.imageQr,
                        resPath = "icons/scan_image_qr_white.png",
                      modifier = tbSize
                    ) {
                        ImageQrCode {
                            it?.let {
                                clearAlerts()
                                if (!wallyApp!!.handlePaste(it)) wallyApp!!.handleNonIntentText(it) }
                        }
                    }
                }
                if (platform().hasQrScanner)
                {
                    Spacer(Modifier.width(2.dp))
                    IconTextButton(
                      textRes = S.scanQr,
                      resPath = "icons/scan_qr_white.png",
                      modifier = tbSize
                    ) {
                        clearAlerts()
                        isScanningQr = true
                    }
                }
                if (!platform().usesMouse)
                {
                    Spacer(Modifier.width(2.dp))
                    IconTextButton(
                      S.paste,
                      resPath = "icons/paste_clipboard_white.png",
                      modifier = tbSize
                    ) {
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
                Spacer(Modifier.width(8.dp))
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isSending)
            {
                val fromAccount: Account = sendFromAccount ?: run {
                    displayNotice(S.NoAccounts, null)
                    return
                }
                SendView(
                  selectedAccountName = fromAccount.name,
                  accountNames = accountGuiSlots.value.map { it.name },
                  toAddress = _sendToAddress,
                  note = sendNote,
                  sendQuantity = sendQuantity,
                  paymentInProgress = paymentInProgress.value,
                  setSendQuantity = {
                      sendQuantity.value = it
                  },
                  setToAddress = { sendToAddress.value = it },
                  onCancel = {
                      isSending = false
                      driver.value = null // hack to fix send section reappearing on nav after an address is provided
                      clearAlerts()  // If user manually cancelled, they understood the problem
                      sendFromAccountShared.value?.clearAssetTransferList()
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
                      sendFromAccountShared.value?.clearAssetTransferList()
                      sendToAddress.value = ""
                      sendQuantity.value = ""
                      isSending = false
                  },
                  onAccountNameSelected = {
                      val act = wallyApp!!.accounts[it]
                      act?.let {
                          updateSendAccount(it)
                      }
                  },
                  account = fromAccount
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
                          wallyApp?.focusedAccount?.value = act
                      }
                  }
                )
            }

            WallyDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                if(synced.value)
                    ResImageView("icons/check.xml", modifier = Modifier.size(24.dp))
                else
                {
                    Box(Modifier.size(24.dp)) { LoadingAnimationContent() }
                }
                Column(modifier = Modifier.weight(0.75f).height(IntrinsicSize.Min), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    SectionText(S.AccountListHeader, Modifier.weight(1f))
                    HorizontalDivider(modifier = Modifier.width(100.dp), color = listDividerFg, thickness = 2.dp)
                }
                ResImageView("icons/plus.xml", modifier = Modifier.size(24.dp).clickable {
                    clearAlerts()
                    nav.go(ScreenId.NewAccount)
                })
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            AccountListView(
              nav,
              selectedAccount,
              modifier = Modifier.weight(1f).testTag("AccountListView"),
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
                      LogIt.info("dismissed QR scan")
                      clearAlerts()
                      isScanningQr = false
                  },
                  onScan = {
                      LogIt.info("handling QR scan $isScanningQr")
                      if (it.isNotEmpty() && isScanningQr)
                      {
                          LogIt.info("actually handling QR scan")
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


