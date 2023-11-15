package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.AccountListView
import info.bitcoinunlimited.www.wally.ui.views.ReceiveView
import info.bitcoinunlimited.www.wally.ui.views.QrScannerDialog
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import info.bitcoinunlimited.www.wally.ui.views.SendView
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.HomeScreen")

fun assignWalletsGuiSlots(): ListifyMap<String, Account>
{
    // We have a Map of account names to values, but we need a list
    // Sort the accounts based on account name
    val lm: ListifyMap<String, Account> = ListifyMap(wallyApp!!.accounts, { it.value.visible }, object : Comparator<String>
    {
        override fun compare(p0: String, p1: String): Int
        {
            if (wallyApp?.nullablePrimaryAccount?.name == p0) return Int.MIN_VALUE
            if (wallyApp?.nullablePrimaryAccount?.name == p1) return Int.MAX_VALUE
            return p0.compareTo(p1)
        }
    })

    /*  TODO set up change notifications moving upwards from the wallets
    for (c in wallyApp!!.accounts.values)
    {
        // NOTE: Will this trigger a recompose bug?
        c.wallet.setOnWalletChange({ it -> onWalletChange(it) })
        c.wallet.blockchain.onChange = { it -> onBlockchainChange(it) }
        c.wallet.blockchain.net.changeCallback = { _, _ -> onWalletChange(c.wallet) }  // right now the wallet GUI update function also updates the cnxn mgr GUI display
        c.onChange()  // update all wallet UI fields since just starting up
    }
     */

    return lm
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun HomeScreen(nav: ScreenNav, navigation: ChildNav)
{
    var isSending by remember { mutableStateOf(false) }
    var isScanningQr by remember { mutableStateOf(false) }
    var isCreatingNewAccount by remember { mutableStateOf(false) }
    val selectedAccount = remember { mutableStateOf<Account?>(null) }
    val displayAccountDetailScreen = navigation.displayAccountDetailScreen.collectAsState()
    val accountNames = assignWalletsGuiSlots().map { it.name }
    val selectedAccountName = selectedAccount.value?.name ?: ""
    var topInformation by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var noticeDetails by remember { mutableStateOf("") }
    var approximatelyText by remember { mutableStateOf("") }
    var xchgRateText by remember { mutableStateOf("") }

    /** If there's a payment proposal that this app has seen, information about it is located here */
    val paymentInProgress: MutableState<ProspectivePayment?> = remember { mutableStateOf(null) }
    var currencyCode by remember { mutableStateOf(i18n(S.choose)) } // TODO: get from local db
    LaunchedEffect("init") {
        currencyCode = selectedAccount.value?.currencyCode ?: fiatCurrencyCode
    }
    var errorText by remember { mutableStateOf("") }
    var sendToAddress by remember { mutableStateOf("") }
    val currencies: MutableState<List<String>> = remember { mutableStateOf(listOf()) }
    var sendQuantity by remember { mutableStateOf("") }

    /** If we've already put up an error for this address, don't do it again */
    var alreadyErroredAddress: MutableState<PayAddress?> = remember { mutableStateOf(null) }

    /** remember last send coin selected */
    var lastSendFromAccountName by remember { mutableStateOf("") }

    fun displayNotice(res: Int, message: String?)
    {
        notice = i18n(res)
        if (message != null)
            noticeDetails = message
        GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
            delay(ERROR_DISPLAY_TIME)  // Delay of 5 seconds
            withContext(Dispatchers.Default + exceptionHandler) {
                notice = ""
                noticeDetails = ""
            }
        }
    }

    fun displayError(message: String)
    {
        errorText = message
        GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
            delay(ERROR_DISPLAY_TIME)  // Delay of 5 seconds
            withContext(Dispatchers.Default + exceptionHandler) {
                errorText = ""
            }
        }
    }

    fun displayError(res: Int)
    {
        errorText = i18n(res)
        GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
            delay(ERROR_DISPLAY_TIME)  // Delay of 5 seconds
            withContext(Dispatchers.Default + exceptionHandler) {
                errorText = ""
            }
        }
    }

    fun displayError(res: Int, message: String)
    {
        displayError(res)
        errorText = message
        GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
            delay(ERROR_DISPLAY_TIME)  // Delay of 5 seconds
            withContext(Dispatchers.Default + exceptionHandler) {
                errorText = ""
            }
        }
    }


    fun displayException(e: Exception)
    {
        displayError(e.message ?: "")
    }


    /** Find an account that can send to this blockchain and switch the send account to it */
    fun updateSendAccount(chainSelector: ChainSelector)
    {
        // First see if the current selection is compatible with what we want.
        // This keeps the user's selection if multiple accounts are compatible
        if (selectedAccount.value?.wallet?.chainSelector != chainSelector)  // Its not so find one that is
        {
            val matches = wallyApp!!.accountsFor(chainSelector)
            if (matches.size > 1)
            {
                selectedAccount.value = matches.first()
            }
        }
    }

    /** Find an account that can send to this PayAddress and switch the send account to it */
    fun updateSendAccount(pa: PayAddress)
    {
        if (pa.type == PayAddressType.NONE) return  // nothing to update
        updateSendAccount(pa.blockchain)
    }

    /** Set the send currency type spinner options to your default fiat currency or your currently selected crypto
    Might change if the user changes the default fiat or crypto */
    fun updateSendCurrencyType()
    {
        val account = selectedAccount.value ?: throw Exception("no account in updateSendCurrencyType")
        account.let { acc ->
            // If we don't know the exchange rate, we can't offer fiat entry
            currencies.value = if (account.fiatPerCoin != -1.toBigDecimal()) listOf(acc.currencyCode, fiatCurrencyCode) else listOf(acc.currencyCode)
        }
    }

    /** Update the GUI send address field, and all related GUI elements based on the provided payment address */
    fun updateSendAddress(pa: PayAddress)
    {
        if (pa.type == PayAddressType.NONE) return  // nothing to update
        dbgAssertGuiThread()


        sendToAddress = pa.toString()
        paymentInProgress.value = null

        // Change the send currency type to reflect the pasted data if I need to
        updateSendAccount(pa)
        // Update the sendCurrencyType field to contain our coin selection
        updateSendCurrencyType()
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
                    sendToAddress = t
                }
                throw e
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
                    val mbchToSend = qty / fiatPerCoin
                    val coinPerFiat = CURRENCY_1 / fiatPerCoin
                    val sats = account.toFinestUnit(mbchToSend)
                    approximatelyText = if (sats <= dust(account.chain.chainSelector))
                        i18n(S.sendingDustWarning)
                    else
                        i18n(S.actuallySendingT) % mapOf("qty" to mBchFormat.format(mbchToSend), "crypto" to account.currencyCode) + availabilityWarning(account, mbchToSend)
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
                        approximatelyText = i18n(S.approximatelyT) % mapOf("qty" to fiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) + availabilityWarning(account, qty)
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
                    // Update the sendCurrencyType field to contain our coin selection
                    updateSendCurrencyType()

                    sendQuantity = mBchFormat.format(amt)

                    selectedAccount.value?.let {
                        checkSendQuantity(sendQuantity, it)
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

                    sendToAddress = ""

                    var count = 0
                    paymentInProgress.value?.outputs?.let {
                        for (out in it)
                        {
                            val addr = out.script.address.toString()
                            if (count > 0)
                            {
                                sendToAddress = "$sendToAddress "
                            }
                            sendToAddress = sendToAddress + addr
                            count += 1
                            if (count > 4)
                            {
                                sendToAddress = "$sendToAddress..."
                                break
                            }
                        }
                    }
                }
            }
        }
        catch (e: Exception)
        {

        }
    }

    /** Process a BIP21 URI */
    fun handleSendURI(suri: String)
    {
        val uri = Uri.parse(suri)

        // replace the scheme with http so we can use URL to parse it
        //val index = iuri.indexOf(':')
        //if (index == -1) throw NotUriException() // Can't be a URI if no colon
        //val scheme = iuri.take(index)
        // To decode the parameters, we drop our scheme (blockchain identifier) and replace with http, so the standard Url parser will do the job for us.
        //val u = Uri("http" + iuri.drop(index))
        val attribs = uri.queryMap()
        val scheme = uri.scheme
        val body = uri.body()
        if (body.contains("/"))  // But in BIP21 there must be only an address, not a path
        {
            displayError(S.badAddress, suri)
            return
        }
        // Now put our scheme back in, dropping the parameters.  So we should have something like "nexa:<address>"
        val sta = scheme + ":" + body

        val bip72 = attribs["r"]
        val stramt = attribs["amount"]
        var amt: BigDecimal = CURRENCY_NEG1
        if (bip72 != null)
        {
            later {
                try
                {
                    paymentInProgress.value = processJsonPay(bip72)
                    // laterUI {
                        updateSendBasedOnPaymentInProgress()
                    //    sendVisibility(true)
                    //    receiveVisibility(false)
                    // }
                }
                catch (e: Bip70Exception)
                {
                    displayException(e)
                } catch (e: Exception)
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
                throw BadAmountException(S.detailsOfBadAmountFromIntent)
            }
            catch (e: ArithmeticException)  // Rounding error
            {
                // If someone is asking for sub-satoshi quantities, round up and overpay them
                LogIt.warning("Sub-satoshi quantity ${stramt} requested.  Rounding up")
                BigDecimal.fromString(stramt, nexaMathMode)
            }
        }

        val lc = sta.lowercase()

        val pa:PayAddress?
        var amtString = "0"
        val act: Account?
        try
        {
            pa = PayAddress(lc)
            val acts = wallyApp?.accountsFor(pa.blockchain)
            if ((acts == null)|| acts.isEmpty())
            {
                displayError(S.NoAccounts)
                return
            }
            act = acts[0]
            amt = act.fromPrimaryUnit(amt)
            amtString = act.format(amt)
        }
        catch (e: UnknownBlockchainException)
        {
            displayError(S.badAddress, suri)
            return
        }


        // TODO label and message
        updateSendAddress(pa) // This also updates the send account

        if (amt >= BigDecimal.ZERO)
        {
            sendQuantity = amtString
            // ui.sendQuantity.text.append(amtString)
            selectedAccount.value?.let {
                checkSendQuantity(sendQuantity.toString(), it)
            }
        }
    }

    fun handleNonIntentText(text: String)
    {
        // NOTE: in certain contexts (app is background), the UI thread may not even be running so do not require completion of any laterUI tasks
        LogIt.info(sourceLoc() + "handleNonIntentText: " + text)
        // Clean out an old payment protocol if you are pasting a new send in
        paymentInProgress.value = null
        // laterUI {
        updateSendBasedOnPaymentInProgress()
        // }

        // lastPaste = text
        if (text.contains('?'))  // BIP21 or BIP70
        {
            for (c in wallyApp!!.accounts.values)
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
            updateSendAddress(text)
            return
        }
        throw PasteUnintelligibleException()
    }

    fun onAccountSelected(c: Account)
    {
        try
        {
            lastSendFromAccountName = c.name
            val sendAddr = PayAddress(sendToAddress.trim())
            if (c.wallet.chainSelector != sendAddr.blockchain)
            {
                if (sendAddr != alreadyErroredAddress.value)
                {
                    displayError(S.chainIncompatibleWithAddress,
                      i18n(S.chainIncompatibleWithAddressDetails) % mapOf("walletCrypto" to (chainToCurrencyCode[c.wallet.chainSelector] ?: i18n(S.unknownCurrency)), "addressCrypto" to (chainToCurrencyCode[sendAddr.blockchain] ?: i18n(S.unknownCurrency))))

                    alreadyErroredAddress.value = sendAddr
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

    fun onAccountNameSelected(name: String)
    {
        assignWalletsGuiSlots().forEach { acc ->
            if (acc.name == name)
                selectedAccount.value = acc
        }
    }

    selectedAccount.value?.onUpdatedReceiveInfoCommon { recvAddrStr -> }

    if (isCreatingNewAccount)
    {
        NewAccountScreen(assignWalletsGuiSlots(), devMode) {
            isCreatingNewAccount = it
        }
    }

    if(displayAccountDetailScreen.value == null && !isCreatingNewAccount) {
        Box(modifier = WallyPageBase) {

            if (assignWalletsGuiSlots().size > 0)
                Row(Modifier.align(Alignment.BottomCenter).zIndex(1f).padding(16.dp).clickable {
                    isScanningQr = true
                }) {
                    WallyBoringIconButton("icons/scanqr2.xml", Modifier.width(48.dp)){
                        isScanningQr = true
                    }
                }
            Column {
                val account = selectedAccount.value

                if (topInformation.isNotEmpty())
                    NoticeText(topInformation)
                if (errorText.isNotEmpty())
                    ErrorText(errorText)
                if (notice.isNotEmpty())
                    NoticeText(notice)
                if (noticeDetails.isNotEmpty())
                    NoticeText(noticeDetails)

                if(isSending && account != null)
                {
                    SendView(
                      account = account,
                      selectedAccountName = selectedAccountName,
                      accountNames = accountNames,
                      currencyCode = currencyCode,
                      toAddress = sendToAddress,
                      sendQuantity = sendQuantity,
                      paymentInProgress = paymentInProgress.value,
                      setSendQuantity = {
                          sendQuantity = it
                      },
                      onCurrencySelectedCode = {
                          currencyCode = it
                      },
                      setToAddress = { sendToAddress = it },
                      onCancel = { isSending = false},
                      approximatelyText = approximatelyText,
                      currencies = currencies.value,
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
                          sendToAddress = ""
                          sendQuantity = ""
                          isSending = false
                      },
                      displayNotice = { res, message ->
                          displayNotice(res, message)
                      },
                      onAccountNameSelected = {
                          onAccountNameSelected(it)
                      }
                    )
                }
                else if (!isSending)
                {
                    Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                        WallyBoringLargeTextButton(S.Send) { isSending = true }
                        WallyBoringLargeTextButton(S.title_split_bill) { nav.go(ScreenId.SplitBill) }
                    }
                    WallyDivider()
                    ReceiveView(
                      selectedAccount.value?.name ?: "",
                      selectedAccount.value?.currentReceive?.address?.toString() ?: "",
                      assignWalletsGuiSlots().map { it.name },
                      onAccountNameSelected = {
                          onAccountNameSelected(it)
                      }
                    )
                    WallyDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {

                        ResImageView("icons/check.xml", // "icons/ani_syncing.xml"
                          modifier = Modifier.size(26.dp).absoluteOffset(0.dp, -8.dp))
                        Text(i18n(S.AccountListHeader))
                        ResImageView("icons/plus.xml",
                          modifier = Modifier.size(26.dp).absoluteOffset(0.dp, -8.dp).clickable {
                              isCreatingNewAccount = true
                          })
                    }
                }
                AccountListView(
                  assignWalletsGuiSlots(),
                  selectedAccount,
                  navigation,
                  onAccountSelected = {
                      selectedAccount.value = it
                      currencyCode = it.currencyCode
                      onAccountSelected(it)
                  }
                )
                WallyDivider()
                if (isScanningQr)
                    QrScannerDialog(
                      onDismiss = {
                          isScanningQr = false
                      },
                      onScan = {
                          if (it.isNotEmpty() && isScanningQr)
                          {
                              // Clean out an old payment protocol if you are pasting a new send in
                              paymentInProgress.value = null
                              isScanningQr = false
                              isSending = true
                              handleNonIntentText(it)
                          }
                      }
                    )
            }
        }
    }
    else if(displayAccountDetailScreen.value is Account && !isCreatingNewAccount)
        AccountDetailScreenNav(navigation, displayAccountDetailScreen.value!!, assignWalletsGuiSlots())
}
