package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ErrorText
import info.bitcoinunlimited.www.wally.ui.theme.*
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*
import kotlin.coroutines.CoroutineContext

private val LogIt = GetLog("BU.wally.SenView")

/**
 * Send all text
 */
val SEND_ALL_TEXT: String = i18n(S.sendAll)

/**
 * Coroutine context for send view
 */
val coMiscCtxt: CoroutineContext = newFixedThreadPoolContext(4, "send")
/**
 * Coroutine scope for send view
 */
val coMiscScope = CoroutineScope(coMiscCtxt)

/**
 * View for sending coins
 */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun SendView(
  account: Account,
  selectedAccountName: String,
  accountNames: List<String>,
  currencyCode: String,
  toAddress: String,
  sendQuantity: String,
  paymentInProgress: ProspectivePayment?,
  approximatelyText: String,
  xchgRateText: String,
  currencies: List<String>,
  setSendQuantity: (String) -> Unit,
  onCurrencySelectedCode: (String) -> Unit,
  setToAddress: (String) -> Unit,
  onCancel: () -> Unit,
  onPaymentInProgress: (ProspectivePayment?) -> Unit,
  updateSendBasedOnPaymentInProgress: () -> Unit,
  onApproximatelyText: (String) -> Unit,
  checkSendQuantity: (s: String, account: Account) -> Unit,
  onSendSuccess: () -> Unit,
  displayNotice: (res: Int, message: String?) -> Unit,
  onAccountNameSelected: (name: String) -> Unit
)
{
    var accountExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var displayNoteInput by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var errorMessageText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var sendConfirm by remember { mutableStateOf("") }
    var spendAll by remember { mutableStateOf(false) }
    val amount: MutableState<BigDecimal?> = remember { mutableStateOf(null) }
    val sendToAddress: MutableState<PayAddress?> = remember { mutableStateOf(null) }
    val fpcState = account.fiatPerCoinObservable.collectAsState()
    val amountState: MutableState<BigDecimal?> = remember { mutableStateOf(null) }

    account.getXchgRates("USD")

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

    fun displayError(res: Int, message: String)
    {
        displayError(i18n(res))
        errorMessageText = message
        GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
            delay(ERROR_DISPLAY_TIME)  // Delay of 5 seconds
            withContext(Dispatchers.Default + exceptionHandler) {
                errorMessageText = ""
            }
        }
    }

    val coroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        LogIt.error(context.toString())
        LogIt.error(throwable.message ?: throwable.toString())
        GlobalScope.launch(Dispatchers.Main) {
            displayError(S.unknownError, throwable.message ?: throwable.toString())
        }
    }

    fun displayException(e: Exception, res: Int? = null)
    {
        displayError(e.message ?: "")
        if (res != null)
            displayError(i18n(res))
    }

    fun onCurrencySelected()
    {
        val sendQty = sendQuantity.toString()
        checkSendQuantity(sendQty, account)
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
            GlobalScope.launch(Dispatchers.Main + coroutineExceptionHandler) {
                setToAddress("")
                updateSendBasedOnPaymentInProgress()
            }
        } catch (e: Exception)
        {
            displayException(e)
        }
    }

    /** Create and post a transaction when the send button is pressed */
    fun onSendButtonClicked()
    {
        LogIt.info("send button clicked")

        if (paymentInProgress != null)
        {
            displayNotice(S.Processing, null)
            coMiscScope.launch {
                paymentInProgressSend()
            }
            return
        }

        if (account.locked)
        {
            displayError(S.accountLocked, "")
            return
        }

        val amountString: String = sendQuantity

        spendAll = false
        var amount = try
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

        when (currencyCode) {
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
                        displayException(UnavailableException(), S.retrievingExchangeRate)
                        return
                    }
                }
                catch (e: ArithmeticException)
                {
                    LogIt.error(e.message ?: e.toString())
                    displayException(UnavailableException(), S.retrievingExchangeRate)
                    return
                }
            }
            else -> throw BadUnitException()
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

        if (amount >= confirmAmt)
        {
            val fiatAmt = if (account.fiatPerCoin > BigDecimal.ZERO)
            {
                val fiatDisplay = amount * account.fiatPerCoin
                i18n(S.approximatelyT) % mapOf("qty" to fiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode)
            }
            else
            {
                ""
            }

            sendToAddress.value = sendAddr

            // When sendConfirm is not empty, a confirmation dialog is displayed
            sendConfirm = i18n(S.SendConfirmSummary) % mapOf("amt" to account.format(amount), "currency" to account.currencyCode,
              "dest" to sendAddr.toString(),
              "inFiat" to fiatAmt,
              "assets" to "",
            )
        }
        amountState.value = amount
    }

    fun onSendSuccess(amt: Long, addr: PayAddress, tx: iTransaction)
    {
        displayNotice(S.sendSuccess, "$amt -> $addr: ${tx.idem}")
        sendToAddress.value = null
        note = ""
        onSendSuccess()
    }

    fun actuallySend(sendAddress: PayAddress, qty: BigDecimal)
    {
        displayNotice(S.Processing, null)

        // avoid network on main thread exception
        coMiscScope.launch {
            val cs = account.wallet.chainSelector
            var tx: iTransaction = txFor(cs)
            try
            {
                val atomAmt = account.toFinestUnit(qty)
                // If we are spending all, then deduct the fee from the amount (which was set above to the full ungrouped balance)
                tx = account.wallet.send(atomAmt, sendAddress, spendAll, false, note = note)
                LogIt.info("Sending TX: ${tx.toHex()}")
                onSendSuccess(atomAmt, sendAddress, tx)
            }
            catch (e: WalletNotEnoughBalanceException)
            {
                displayError(i18n(S.insufficentBalance))
                LogIt.info("Failed transaction is: ${tx.toHex()}")
                sendConfirm = ""  // Force reconfirm is there is any error with the send
            }
            catch (e: Exception)  // We don't want to crash, we want to tell the user what went wrong
            {
                displayException(e)
                handleThreadException(e)
                LogIt.info("Failed transaction is: ${tx.toHex()}")
                sendConfirm = ""  // Force reconfirm is there is any error with the send
            }
        }

        sendConfirm = ""  // We are done with a send so reset state machine
    }

    fun afterTextChanged()
    {
        onPaymentInProgress(null)
        updateSendBasedOnPaymentInProgress()
        checkSendQuantity(sendQuantity, account)
    }

    if (approximatelyText.isNotEmpty())
        NoticeText(approximatelyText)
    if (errorText.isNotEmpty())
        ErrorText(errorText)
    if (errorMessageText.isNotEmpty())
        ErrorText(errorMessageText)
    if (sendConfirm.isNotEmpty())
        amountState.value?.let { amt ->
            ConfirmDismissNoteDialog(amt, sendConfirm.isNotEmpty(), S.confirm, sendConfirm, note, S.cancel, S.confirm, onDismiss = {
                sendConfirm = ""
            }, onConfirm = {
                actuallySend(sendToAddress.value ?: throw Exception("address is null"), it)
                sendConfirm = ""
            })
        }

    SendViewContent(
      selectedAccountName,
      accountNames,
      accountExpanded,
      currencyCode,
      currencyExpanded,
      toAddress,
      sendQuantity,
      xchgRateText,
      approximatelyText,
      displayNoteInput,
      note,
      getApproximatelyText = { checkSendQuantity(sendQuantity, account) },
      onAccountExpanded = { accountExpanded = it },
      onCurrencySelected = {
          onCurrencySelectedCode(it)
          onCurrencySelected()
      },
      onCurrencyExpanded = { currencyExpanded = it },
      onAccountNameSelected = { onAccountNameSelected(it) },
      onToAddressChange = { setToAddress(it) },
      onSendQuantityChanged = {
          setSendQuantity(it)
          afterTextChanged()
      },
      onDisplayNoteInput = { displayNoteInput = it },
      onNoteChange = { note = it },
      onCancel = {
          onCancel()
      },
      onSend = { onSendButtonClicked() },
      currencies = currencies,
    )
}

/**
 * The content of Send view
 */
@Composable
fun SendViewContent(
  primaryAccountName: String,
  accountNames: List<String>,
  accountExpanded: Boolean,
  currencyCode: String,
  currencyExpanded: Boolean,
  toAddress: String,
  sendQty: String,
  xchgRateText: String,
  approximatelyText: String,
  displayNoteInput: Boolean,
  note: String,
  getApproximatelyText: () -> Unit,
  onAccountNameSelected: (String) -> Unit,
  onAccountExpanded: (Boolean) -> Unit,
  onCurrencySelected: (String) -> Unit,
  onCurrencyExpanded: (Boolean) -> Unit,
  onToAddressChange: (String) -> Unit,
  onSendQuantityChanged: (String) -> Unit,
  onDisplayNoteInput: (Boolean) -> Unit,
  onNoteChange: (String) -> Unit,
  onCancel: () -> Unit,
  onSend: () -> Unit,
  currencies: List<String>,
)
{
    /**
     * Select from account
    */
    SelectStringDropdownRes(
      S.fromAccountColon,
      primaryAccountName,
      accountNames,
      accountExpanded,
      onSelect = onAccountNameSelected,
      onExpand = onAccountExpanded,
    )

    /**
     * Input address to send to
     */
    StringInputField(S.toColon, S.sendToAddressHint, toAddress, onToAddressChange)

    /**
     * Display note input
     */
    if (displayNoteInput)
        StringInputTextField(S.editSendNoteHint, note, onNoteChange)

    Row(
      modifier = Modifier.fillMaxWidth(),
    ) {
        /**
         * Send quantity input
         */
        TextField(
          value = sendQty,
          onValueChange = {
              onSendQuantityChanged(it)
              getApproximatelyText()
          },
          label = { Text(i18n(S.Amount)) },
          singleLine = true,
          modifier = Modifier.wrapContentWidth(),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
          ),
          textStyle = TextStyle(fontSize = 12.sp),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
          )
        )
        SelectStringDropDown(
          currencyCode,
          currencies,
          currencyExpanded,
          onSelect = onCurrencySelected,
          onExpand = onCurrencyExpanded
        )
    }

    Spacer(modifier = Modifier.size(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center
    ) {
        WallyBoringLargeIconButton("icons/menu_edit.png", interactionSource = MutableInteractionSource(),
          onClick = {
              onDisplayNoteInput(!displayNoteInput)
          }
        )
        Spacer(modifier = Modifier.size(32.dp))
        WallyBoringLargeTextButton(S.Send, onClick = onSend)
        Spacer(modifier = Modifier.size(32.dp))
        WallyBoringLargeTextButton(S.SendCancel, onClick = onCancel)
    }

    ApproximatelyText(approximatelyText)
    ApproximatelyText(xchgRateText)
}

/**
 * Approximately how much are we sending in FIAT?
 */
@Composable
fun ApproximatelyText(text: String)
{
    Text(
      text = text,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      textAlign = TextAlign.Center,
      fontSize = 14.sp,
      color = Color.Gray
    )
}
