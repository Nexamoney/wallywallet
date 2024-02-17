package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import kotlinx.coroutines.*
import okio.utf8Size
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
  selectedAccountName: String,
  accountNames: List<String>,
  currencyCode: String,
  toAddress: String,
  note: MutableState<String>,
  sendQuantity: MutableState<String>,
  paymentInProgress: ProspectivePayment?,
  approximatelyText: String,
  xchgRateText: String,
  currencies: MutableState<List<String>>,
  setSendQuantity: (String) -> Unit,
  onCurrencySelectedCode: (String) -> Unit,
  setToAddress: (String) -> Unit,
  onCancel: () -> Unit,
  onPaymentInProgress: (ProspectivePayment?) -> Unit,
  updateSendBasedOnPaymentInProgress: () -> Unit,
  onApproximatelyText: (String) -> Unit,
  checkSendQuantity: (s: String, account: Account) -> Unit,
  onSendSuccess: () -> Unit,
  onAccountNameSelected: (name: String) -> Unit
)
{
    var account: Account = wallyApp!!.accounts[selectedAccountName] ?: run {
        displayNotice(S.NoAccounts, null)
        return
    }
    var accountExpanded by remember { mutableStateOf(false) }
    var displayNoteInput by remember { mutableStateOf(false) }
    var sendConfirm by remember { mutableStateOf("") }
    var spendAll by remember { mutableStateOf(false) }
    val sendToAddress: MutableState<PayAddress?> = remember { mutableStateOf(null) }
    val fpcState = account.fiatPerCoinObservable.collectAsState()
    val amountState: MutableState<BigDecimal?> = remember { mutableStateOf(null) }

    account.getXchgRates("USD")

    val coroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        LogIt.error(context.toString())
        LogIt.error(throwable.message ?: throwable.toString())
        GlobalScope.launch(Dispatchers.Main) {
            displayError(S.unknownError, throwable.message ?: throwable.toString())
        }
    }

    fun onCurrencySelected()
    {
        val sendQty = sendQuantity.value
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
            displayUnexpectedException(e)
        }
    }

    fun onSendSuccess(amt: Long, addr: PayAddress, tx: iTransaction)
    {
        displayNotice(S.sendSuccess, "$amt -> $addr: ${tx.idem}")
        sendToAddress.value = null
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
                tx = account.wallet.send(atomAmt, sendAddress, spendAll, false, note = note.value)
                note.value = ""
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
                displayUnexpectedException(e)
                handleThreadException(e)
                LogIt.info("Failed transaction is: ${tx.toHex()}")
                sendConfirm = ""  // Force reconfirm is there is any error with the send
            }
        }
        sendConfirm = ""  // We are done with a send so reset state machine
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

        val amountString: String = sendQuantity.value

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

        when (currencyCode)
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

        // If sending a large amount, ask to confirm
        if (amount >= confirmAmt)
        {
            val fiatAmt = if (account.fiatPerCoin > BigDecimal.ZERO)
            {
                val fiatDisplay = amount * account.fiatPerCoin
                i18n(S.approximatelyT) % mapOf("qty" to FiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode)
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
                  "assets" to "",
                )
            }
        }
        else  // otherwise just send it
        {
            actuallySend(sendAddr, amount)
        }
        amountState.value = amount
    }

    fun afterTextChanged()
    {
        onPaymentInProgress(null)
        updateSendBasedOnPaymentInProgress()
        checkSendQuantity(sendQuantity.value, account)
    }

    if (sendConfirm.isNotEmpty())
        amountState.value?.let { amt ->
            ConfirmDismissNoteDialog(amt, sendConfirm.isNotEmpty(), S.confirm, sendConfirm, note.value, S.cancel, S.confirm, onDismiss = {
                sendConfirm = ""
            }, onConfirm = {
                actuallySend(sendToAddress.value ?: throw Exception("address is null"), it)
                sendConfirm = ""
            })
        }

    var ccIndex by remember { mutableStateOf(0) }

    ccIndex = currencies.value.indexOf(currencyCode)

    // give some side margins if the platform supports it
    val sendPadding = if (platform().spaceConstrained) 0.dp else 8.dp
    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(sendPadding, 0.dp, sendPadding, 0.dp)) {
        // Now show the actual content:

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
        StringInputField(S.toColon, S.sendToAddressHint, toAddress, WallySectionTextStyle(), Modifier) {
            setToAddress(it)
        }

        // Display note input
        if (displayNoteInput || note.value.utf8Size() > 0)
            StringInputTextField(S.editSendNoteHint, note.value, { note.value = it })

        Spacer(Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            SectionText(S.Amount)
            // Send quantity input
            WallyDecimalEntry(sendQuantity.value, Modifier.weight(1f)) {
                // TODO need to serialize clearing vs new accounts: Serialize ALL alerts.  clearAlerts()
                setSendQuantity(it)
                afterTextChanged()
                checkSendQuantity(sendQuantity.value, account)
            }
            WallyDropdownMenu(modifier = Modifier.wrapContentSize().weight(0.5f), label = "", items = currencies.value, selectedIndex = ccIndex, onItemSelected = { index, _ ->
                if (index < currencies.value.size)
                {
                    ccIndex = index
                    onCurrencySelectedCode(currencies.value[index])
                    onCurrencySelected()
                    afterTextChanged()
                }
            })
        }

        OptionalInfoText(approximatelyText)
        OptionalInfoText(xchgRateText)
        Spacer(modifier = Modifier.size(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,

          ) {
            WallyBoringLargeIconButton("icons/menu_edit.png", interactionSource = MutableInteractionSource(),
              onClick = { displayNoteInput = !displayNoteInput }
            )
            WallyBoringLargeTextButton(S.Send, onClick = { onSendButtonClicked() })
            WallyBoringLargeTextButton(S.SendCancel, onClick = onCancel)
        }
    }
}

/**
 * Approximately how much are we sending in FIAT?
 */
@Composable
fun OptionalInfoText(text: String)
{
    if (text.utf8Size() > 0)
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
