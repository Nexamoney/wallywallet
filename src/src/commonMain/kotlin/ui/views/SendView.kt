package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AssetListItemView
import info.bitcoinunlimited.www.wally.ui.currencyCodeShared
import info.bitcoinunlimited.www.wally.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import okio.utf8Size
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SendView")

/**
 * Send all text
 */
val SEND_ALL_TEXT: String = i18n(S.sendAll)

/**
 * View for sending coins
 */
@Composable
fun SendView(
  selectedAccountName: String,
  accountNames: List<String>,
  toAddress: String,
  note: MutableState<String>,
  sendQuantity: MutableState<String>,
  paymentInProgress: ProspectivePayment?,
  approximatelyText: String,
  xchgRateText: String,
  currencies: MutableState<List<String>>,
  setSendQuantity: (String) -> Unit,
  setToAddress: (String) -> Unit,
  onCancel: () -> Unit,
  onPaymentInProgress: (ProspectivePayment?) -> Unit,
  updateSendBasedOnPaymentInProgress: () -> Unit,
  onApproximatelyText: (String) -> Unit,
  checkSendQuantity: (s: String, account: Account) -> Unit,
  onSendSuccess: () -> Unit,
  onAccountNameSelected: (name: String) -> Unit,
  account: Account,
)
{
    val currencyCode = currencyCodeShared.asStateFlow()
    var accountExpanded by remember { mutableStateOf(false) }
    var displayNoteInput by remember { mutableStateOf(false) }
    var sendConfirm by remember { mutableStateOf("") }
    var spendAll by remember { mutableStateOf(false) }
    val sendToAddress: MutableState<PayAddress?> = remember { mutableStateOf(null) }
    val fpcState = account.fiatPerCoinObservable.collectAsState()
    val amountState: MutableState<BigDecimal?> = remember { mutableStateOf(null) }
    val sendingTheseAssets = account.assetTransferList
    var ccIndex by remember { mutableStateOf(0) }
    ccIndex = currencies.value.indexOf(currencyCode.value)
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val coroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        LogIt.error(context.toString())
        LogIt.error(throwable.message ?: throwable.toString())
        displayError(S.unknownError, throwable.message ?: throwable.toString())
    }

    fun onCurrencySelected()
    {
        val sendQty = sendQuantity.value
        checkSendQuantity(sendQty, account)
    }

    fun afterTextChanged()
    {
        onPaymentInProgress(null)
        updateSendBasedOnPaymentInProgress()
        checkSendQuantity(sendQuantity.value, account)
    }

    fun paymentInProgressSend()
    {
        try
        {
            val pip = paymentInProgress ?: return

            if (account.wallet.chainSelector != pip.crypto)
            {
                displayError(S.incompatibleAccount, i18n(S.incompatibleAccountDetails) %
                  mapOf("cryptoAct" to (chainToCurrencyCode[account.wallet.chainSelector] ?: i18n(S.unknownCurrency)), "cryptoPay" to (chainToCurrencyCode[pip.crypto] ?: i18n(S.unknownCurrency))))
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
            displayNotice(S.sendSuccess, null)
            onPaymentInProgress(null)
            coroutineScope.launch(coroutineExceptionHandler) {
                setToAddress("")
                updateSendBasedOnPaymentInProgress()
            }
        } catch (e: Exception)
        {
            displayUnexpectedException(e)
        }
    }

    fun onSendSuccess(amt: Long, addr: PayAddress, tx: iTransaction)
    {
        clearAlerts()
        displayNotice(S.sendSuccess, "$amt -> $addr: ${tx.idem}")
        sendToAddress.value = null
        onSendSuccess()
    }

    fun actuallySend(sendAddress: PayAddress, qty: BigDecimal)
    {
        displayNotice(S.Processing, null)

        // Launch to avoid network on main thread exception
        // Grab copies of all the data we need
        val sendList = sendingTheseAssets.toList()
        val act = account
        val assets = act.assets.toMap()
        tlater("actuallySend") {
            val cs = act.wallet.chainSelector
            var tx: iTransaction = txFor(cs)
            if ((qty == BigDecimal.ZERO)&&sendList.isEmpty())  // Sending nothing
            {
                displayError(i18n(S.badAmount))
            }
            else
            {
                try
                {
                    // val atomAmt = account.toFinestUnit(qty)
                    // If we are spending all, then deduct the fee from the amount (which was set above to the full ungrouped balance)
                    //tx = account.wallet.send(atomAmt, sendAddress, spendAll, false, note = note.value)

                    tx = txFor(cs)
                    try
                    {
                        val atomAmt = act.toFinestUnit(qty)
                        if (sendList.size == 0)
                        {
                            // If we are spending all, then deduct the fee from the amount (which was set above to the full ungrouped balance)
                            tx = act.wallet.send(atomAmt, sendAddress, spendAll, false, note = note.value)
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
                            for (groupId in sendList)
                            {
                                val assetPerAccount = assets[groupId]
                                if (assetPerAccount != null)
                                {
                                    val eAmt = assetPerAccount.editableAmount
                                    val tokqty = if (eAmt == null) assetPerAccount.groupInfo.tokenAmt  // If they don't change the amount, send all of them (see default in AssetScreen.kt)
                                    else assetPerAccount.tokenDecimalToFinestUnit(eAmt)

                                    if (tokqty != null && tokqty > 0)
                                    {
                                        val aout = txOutputFor(cs)
                                        aout.amount = dust(cs)
                                        assetDustOut += aout.amount
                                        aout.script = sendAddress.groupedLockingScript(groupId, tokqty)
                                        tx.add(aout)
                                    }
                                }
                            }
                            //
                            // Construct an output that sends the right amount of native coin
                            if (atomAmt > 0)
                            {
                                val coinOut = txOutputFor(cs)
                                coinOut.amount = atomAmt
                                if (spendAll) coinOut.amount -= assetDustOut  // it doesn't matter because txCompleter will solve but needs to not be too much
                                coinOut.script = sendAddress.lockingScript()
                                tx.add(coinOut)
                            }

                            // Attempt to pay for the constructed transaction
                            act.wallet.txCompleter(tx, 0, cflags, null, if (spendAll) (tx.outputs.size-1) else null)
                            act.wallet.send(tx,false, note = note.value)
                        }
                        note.value = ""
                        LogIt.info("Sending TX: ${tx.toHex()}")
                        onSendSuccess(atomAmt, sendAddress, tx)
                        sendConfirm = "" // We are done with a send so reset state machine
                    }
                    catch (e: Exception)  // We don't want to crash, we want to tell the user what went wrong
                    {
                        displayUnexpectedException(e)
                        handleThreadException(e)
                        LogIt.info("Failed transaction is: ${tx.toHex()}")
                        sendConfirm = ""  // Force reconfirm is there is any error with the send
                    }
                }
                catch (e: WalletNotEnoughBalanceException)
                {
                    displayError(i18n(S.insufficentBalance))
                    LogIt.info("Failed transaction is: ${tx.toHex()}")
                    sendConfirm = ""  // Force reconfirm is there is any error with the send
                }
                catch (e: Exception)  // We don't want to crash, we want to tell the user what went wrong
                {
                    displayUnexpectedException(e)
                    handleThreadException(e)
                    LogIt.info("Failed transaction is: ${tx.toHex()}")
                    sendConfirm = ""  // Force reconfirm is there is any error with the send
                }
            }
        }
        sendConfirm = ""  // We are done with a send so reset state machine
    }

    /** Create and post a transaction when the send button is pressed */
    fun onSendButtonClicked()
    {
        if (paymentInProgress != null)
        {
            displayNotice(S.Processing, null)
            tlater("pipSend") {
                paymentInProgressSend()
            }
            return
        }

        if (account.locked)
        {
            displayError(S.accountLocked, "")
            return
        }

        val amountString: String = sendQuantity.value

        spendAll = false
        var amount = if (amountString.isBlank()) BigDecimal.ZERO  // zero is ok if we have assets (which will be checked later)
        else try
        {
            // Special case transferring everything
            if (amountString.lowercase() == SEND_ALL_TEXT)
            {
                spendAll = true
                account.fromFinestUnit(account.wallet.balance)
            }
            // Transferring a specific amount
            else
            {
                amountString.toCurrency(account.chain.chainSelector)
            }
        }
        // There used to be a java parseException here
        catch (e: NumberFormatException)
        {
            displayError(S.badAmount, i18n(S.badAmount) + " " + amountString)
            return
        }
        catch (e: ArithmeticException)  // Rounding error
        {
            // If someone is asking to send sub-satoshi quantities, round up and ask them to click send again.
            setSendQuantity("")
            displayError(S.badAmount, i18n(S.subSatoshiQuantities))
            onApproximatelyText(i18n(S.roundedUpClickSendAgain))
            return
        }
        catch (e: Exception)
        {
            displayError(S.badAmount, amountString)
            return
        }

        // make sure something is being sent
        if ((amount == BigDecimal.ZERO)&&sendingTheseAssets.isEmpty())
        {
            displayError(S.badAmount, amountString)
            return
        }

        if (account.toFinestUnit(amount) > account.wallet.balance)
        {
            sendConfirm = ""
            displayError(S.insufficentBalance, "Account contains ${account.wallet.balance}.  Attempted to send ${amount}.")
            return
        }

        // Make sure the address is consistent with the selected coin to send
        val addrText = toAddress.trim()

        if (addrText.isEmpty())
        {
            displayError(i18n(S.badAddress))
        }

        val sendAddr = try
        {
            PayAddress(addrText)
        }
        catch (e: WalletNotSupportedException)
        {
            val details = i18n(S.badAddress) + " " + if (addrText == "") i18n(S.empty) else addrText
            displayError(S.badAddress, details)
            return
        }
        catch (e: UnknownBlockchainException)
        {
            val details = i18n(S.unknownCurrency) + " " + if (addrText == "") i18n(S.empty) else addrText
            displayError(S.badAddress, details)
            return
        }

        if (account.wallet.chainSelector != sendAddr.blockchain)
        {
            displayError(S.chainIncompatibleWithAddress, toAddress)
            return
        }
        if (sendAddr.type == PayAddressType.NONE)
        {
            displayError(S.badAddress, toAddress)
            return
        }

        when (currencyCode.value)
        {
            account.currencyCode ->
            {
            }
            fiatCurrencyCode ->
            {
                val fpc = fpcState.value
                try
                {
                    if (fpc != BigDecimal.ZERO)
                    {
                        val decimalPrecision = 20L
                        val roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
                        val decimalMode = DecimalMode(decimalPrecision, roundingMode)
                        amount = amount.divide(fpc, decimalMode)
                    }
                    else
                    {
                        displayError(S.unavailable, S.retrievingExchangeRate, persistAcrossScreens = 0)
                        return
                    }
                }
                catch (e: ArithmeticException)
                {
                    LogIt.error(e.message ?: e.toString())
                    displayError(S.unavailable, S.retrievingExchangeRate, persistAcrossScreens = 0)
                    return
                }
            }
            else -> {
                displayError(S.badCurrencyUnit)
                return
            }
        }

        val preferenceDB = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
        val confirmAmtString = preferenceDB.getString(CONFIRM_ABOVE_PREF, "0") ?: "0"
        val confirmAmt = try
        {
            CurrencyDecimal(confirmAmtString)
        }
        catch (e: Exception)
        {
            CURRENCY_ZERO
        }

        // If sending a large amount, or any assets, ask to confirm
        if ((amount >= confirmAmt)|| sendingTheseAssets.isNotEmpty())
        {
            val fiatAmt = if ((account.fiatPerCoin > BigDecimal.ZERO)&&(amount > BigDecimal.ZERO))
            {
                val fiatDisplay = amount * account.fiatPerCoin
                "(" + i18n(S.approximatelyT) % mapOf("qty" to FiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) + ")"
            }
            else
            {
                ""
            }

            sendToAddress.value = sendAddr

            LogIt.info("checking against ${account.name}")
            if (account.toFinestUnit(amount) <= account.wallet.balance)
            {
                // When sendConfirm is not empty, a confirmation dialog is displayed
                sendConfirm = i18n(S.SendConfirmSummary) % mapOf(
                  "amt" to account.format(amount), "currency" to account.currencyCode,
                  "dest" to sendAddr.toString(),
                  "inFiat" to fiatAmt,
                  "assets" to if (sendingTheseAssets.isEmpty()) "" else i18n(S.AndSomeAssets),
                )
            }
        }
        else  // otherwise just send it
        {
            actuallySend(sendAddr, amount)
        }
        amountState.value = amount
    }

    // give some side margins if the platform supports it
    val sendPadding = if (platform().spaceConstrained) 5.dp else 8.dp
    val localFocusManager = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(sendPadding, 0.dp, sendPadding, 0.dp).pointerInput(Unit) {
        detectTapGestures(onTap = {
            localFocusManager.clearFocus()
        })
    }) {
        // Now show the actual content:

        val sendConfirmMsg:String = sendConfirm
        if (sendConfirmMsg.isEmpty())
        {
            // Send from account:
            SelectStringDropdownRes(
              S.fromAccountColon,
              selectedAccountName,
              accountNames,
              accountExpanded,
              WallySectionTextStyle(),
              onSelect = onAccountNameSelected,
              onExpand = { accountExpanded = it },
            )


            // Input address to send to
            AddressInputField(S.toColon, S.sendToAddressHint, toAddress, WallySectionTextStyle(), Modifier) {
                setToAddress(it)
            }


            // Display note input
            if (displayNoteInput || note.value.utf8Size() > 0)
            {
                Spacer(Modifier.height(8.dp))
                StringInputTextField(S.editSendNoteHint, note.value, Modifier.testTag("noteInputFieldSendView"), { note.value = it })
            }
            Spacer(Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) {
                // Send quantity input
                WallyOutLineDecimalEntry(sendQuantity, Modifier.weight(1f), label = i18n(S.Amount)) {
                    // TODO need to serialize clearing vs new accounts: Serialize ALL alerts.  clearAlerts()
                    setSendQuantity(it)
                    afterTextChanged()
                    checkSendQuantity(sendQuantity.value, account)
                    sendQuantity.value
                }
                WallyDropdownMenu(modifier = Modifier.wrapContentSize().weight(0.5f), label = "", items = currencies.value, selectedIndex = ccIndex, onItemSelected = { index, _ ->
                    if (index < currencies.value.size)
                    {
                        ccIndex = index
                        currencyCodeShared.value = currencies.value[index]
                        onCurrencySelected()
                        afterTextChanged()
                    }
                })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row (horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(0.dp, 5.dp)) {
                OptionalInfoText(approximatelyText)
            }
            Row (horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(0.dp, 5.dp)) {
                OptionalInfoText(xchgRateText)
            }
            Spacer(modifier = Modifier.size(16.dp))


            if (sendingTheseAssets.size > 0)
            {
                CenteredSectionText(S.assetsColon)
                LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().heightIn(0.dp, 200.dp), userScrollEnabled = true) {
                    var index = 0
                    sendingTheseAssets.forEach {
                        val entry = account.assets[it]
                        if (entry != null)
                        {
                            val indexFreezer = index  // To use this in the item composable, we need to freeze it to a val, because the composable is called out-of-scope
                            item(key = indexFreezer) {
                                Box(Modifier.padding(4.dp, 1.dp).fillMaxWidth() // .clickable {}
                                ) {
                                    AssetListItemView(entry, 0, true, Modifier.padding(0.dp, 2.dp))
                                }
                            }
                            index++
                        }
                    }
                }
            }
            WallyButtonRow {
                WallyBoringLargeIconButton("icons/edit_pencil.png", interactionSource = MutableInteractionSource(), modifier = Modifier.testTag("noteButtonSendView"),
                  onClick = { displayNoteInput = !displayNoteInput }
                )
                WallyBoringLargeTextButton(S.Send, onClick = {
                    focusManager.clearFocus()
                    onSendButtonClicked()
                })
                WallyBoringLargeTextButton(S.SendCancel, onClick = {
                    focusManager.clearFocus()
                    onCancel()
                })
            }
        }
        else
        {
            Spacer(Modifier.height(10.dp))
            WallyEmphasisBox {
                val amt = amountState.value
                val addr = sendToAddress.value
                if (amt!=null && addr!=null)
                {
                    Column {
                        //Text(sendConfirmMsg, color = colorError, maxLines = 6, fontSize = FontScale(1.1), style = WallyTextStyle(1.1), modifier = Modifier.fillMaxWidth(), softWrap = true)
                        Text(sendConfirmMsg, color = colorTitleBackground, maxLines = 6, fontSize = FontScale(1.2), modifier = Modifier.fillMaxWidth().padding(8.dp,10.dp), softWrap = true)
                        WallyButtonRow {
                            WallyBoringLargeTextButton(S.Send, onClick = {
                                actuallySend(addr, amt)
                                sendConfirm = ""
                            })
                            WallyBoringLargeTextButton(S.SendCancel, onClick = {
                                sendConfirm = ""
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * Approximately how much are we sending in FIAT?
 */
@Composable
fun OptionalInfoText(text: String)
{
    if (text.utf8Size() > 0) Text(
      text = text,
      modifier = Modifier.fillMaxWidth().wrapContentHeight(),
//      textAlign = TextAlign.Center,
      fontSize = 16.sp,
      color = Color.Gray,
      fontWeight = FontWeight.Bold
    )
}
