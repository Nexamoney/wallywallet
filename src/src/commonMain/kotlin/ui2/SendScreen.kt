package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.views.QrScannerDialog
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallyThemeUi2
import info.bitcoinunlimited.www.wally.ui2.themeUi2.samsungKeyBoardGray
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.views.MpMediaView
import info.bitcoinunlimited.www.wally.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SendScreen")

data class SendScreenUi(
  val toAddress: String = "",
  val note: String = "",
  val amount: String = "",
  val toAddressFinal: PayAddress? = null,
  val isConfirming: Boolean = false,
  val fiatAmount: String = "",
  val currencyCode: String = "NEX"
)
{
    val amountFinal: BigDecimal = if (amount.isEmpty())
        BigDecimal.ZERO
    else try
    {
        BigDecimal.fromString(amount, NexaMathMode)
    }
    catch (e: Exception)  // NumberFormatException is expected if amount is not a number, but no matter what we do not want to crash here
    {
        BigDecimal.ZERO
    }
}

abstract class SendScreenViewModel(act: Account): ViewModel()
{
    val account = MutableStateFlow(act)
    val assetsToSend: MutableStateFlow<List<AssetPerAccount>> = MutableStateFlow(listOf())
    val uiState = MutableStateFlow(SendScreenUi())

    abstract fun setAccount(account: Account)
    abstract fun checkUriAndSetUi(urlStr: String)

    fun setToAddress(address: String)
    {
        uiState.value = uiState.value.copy(
          toAddress = address
        )
    }

    fun setNote(newNote: String)
    {
        uiState.value = uiState.value.copy(
          note = newNote
        )
    }

    fun setSendQty(sendQty: String)
    {
        val cleanedQty = sendQty.replace(",", "")
        uiState.value = uiState.value.copy(
          amount = cleanedQty
        )
    }

    fun setSendQty(sendQty: BigDecimal)
    {
        uiState.value = uiState.value.copy(
          amount = sendQty.toStringExpanded()
        )
    }

    abstract fun multiplySendQty(multiplier: Int)
    abstract fun populateAssetsList(assetTransferList: MutableList<GroupId>, assets: Map<GroupId, AssetPerAccount>)
    abstract fun onSendButtonClicked()
    abstract fun actuallySend(toAddress: PayAddress? = null, amount: BigDecimal? = null)

    fun resetUi()
    {
        uiState.value = SendScreenUi()
    }
}

class SendScreenViewModelFake(act: Account): SendScreenViewModel(act)
{
    override fun setAccount(account: Account)
    {

    }

    override fun checkUriAndSetUi(urlStr: String)
    {

    }

    override fun multiplySendQty(multiplier: Int)
    {

    }

    override fun populateAssetsList(assetTransferList: MutableList<GroupId>, assets: Map<GroupId, AssetPerAccount>)
    {

    }

    override fun onSendButtonClicked()
    {

    }

    override fun actuallySend(toAddress: PayAddress?, amount: BigDecimal?)
    {

    }
}

class SendScreenViewModelImpl(act: Account): SendScreenViewModel(act)
{
    var balanceJob: Job? = null

    init {
        setAccount(act)
    }

    override fun setAccount(account: Account)
    {
        populateAssetsList(account.assetTransferList, account.assets)
        // Automatically switch the currency code to whatever the selected account is using
        // currencyCodeShared is the currency that amount dialog boxes use
        // TODO: What we really want to do here only set it if its currently a Crypto currency code. Obviously if you've selected an account holding a different crypto, you want to send that crypto.
        // TODO: BUT if its a fiat code, do not set it and interpret the amount field as an amount of that fiat see (https://gitlab.com/wallywallet/wallet/-/issues/234)
        currencyCodeShared.value = account.currencyCode
    }

    override fun checkUriAndSetUi(urlStr: String)
    {
        var amt: BigDecimal? = null

        try {
            val uri = Uri.parse(urlStr)
            val scheme = uri.scheme?.lowercase()
            val attribs = uri.queryMap()
            val note = attribs["label"]

            attribs["amount"]?.let {
                amt = try
                {
                    it.toCurrency()
                }
                catch (e: NumberFormatException)
                {
                    throw BadAmountException(S.detailsOfBadAmountFromIntent)
                }
                catch (e: ArithmeticException)  // Rounding error
                {
                    // If someone is asking for sub-satoshi quantities, round up and overpay them
                    LogIt.warning("Sub-satoshi quantity ${it} requested.  Rounding up")
                    BigDecimal.fromString(it, NexaMathMode)
                }
            }

            // see if this is an address without the prefix
            // Check if Uri contains a valid chain or else return
            val whichChain = if (scheme == null)
            {
                try
                {
                    ChainSelectorFromAddress(urlStr)
                }
                catch (e: UnknownBlockchainException)
                {
                    displayError(S.unknownCryptoCurrency, urlStr)
                    return
                }
            }
            else uriToChain[scheme]

            val sendAddress = chainToURI[whichChain] + ":" + uri.body()
            uiState.value = uiState.value.copy(
              toAddress = sendAddress,
            )
            note?.let { setNote(note) }
            amt?.let { setSendQty(it) }
        }
        catch(e: Exception)
        {
            LogIt.info("unexpected exception handling url string $urlStr")
            displayUnexpectedException(e)
        }
    }

    override fun multiplySendQty(multiplier: Int)
    {
        val amount = uiState.value.amountFinal
        val bigDecMultiplier = BigDecimal.fromInt(multiplier)

        val newAmount = if (amount == BigDecimal.ZERO)
            BigDecimal.ONE.multiply(bigDecMultiplier)
        else
            amount.multiply(bigDecMultiplier)

        uiState.value = uiState.value.copy(
          amount = newAmount.toPlainString()
        )
    }

    override fun populateAssetsList(assetTransferList: MutableList<GroupId>, assets: Map<GroupId, AssetPerAccount>)
    {
        val assetsToSendTmp: MutableList<AssetPerAccount> = mutableListOf()

        assetTransferList.forEach { groupId ->
            val asset = assets[groupId]
            asset?.let {
                assetsToSendTmp.add(it)
            }
        }

        assetsToSend.value = assetsToSendTmp.toList()
    }

    override fun onSendButtonClicked()
    {
        val acc = account.value
        val sendingTheseAssets = acc.assetTransferList
        val fpc = acc.fiatPerCoinObservable.value
        val amount = uiState.value.amount
        val toAddress = uiState.value.toAddress
        val currencyCode = currencyCodeShared.value

        /*
            if (paymentInProgress != null)
            {
                displayNotice(S.Processing, null)
                tlater("pipSend") {
                    paymentInProgressSend()
                }
                return
            }
         */

        if (acc.locked)
        {
            displayError(S.accountLocked, "")
            return
        }

        // var spendAll = false
        var amountDec = if (amount.isBlank()) BigDecimal.ZERO  // zero is ok if we have assets (which will be checked later)
        else try
        {
            // Special case transferring everything
            if (amount.lowercase() == i18n(S.sendAll))
            {
                // spendAll = true
                acc.fromFinestUnit(acc.wallet.balance)
            }
            // Transferring a specific amount
            else
            {
                amount.toCurrency(acc.chain.chainSelector)
            }
        }
        // There used to be a java parseException here
        catch (e: NumberFormatException)
        {
            displayError(S.badAmount, i18n(S.badAmount) + " " + amount)
            return
        }
        catch (e: ArithmeticException)  // Rounding error
        {
            // If someone is asking to send sub-satoshi quantities, round up and ask them to click send again.
            setSendQty("0")
            displayError(S.badAmount, i18n(S.subSatoshiQuantities))
            // onApproximatelyText(i18n(S.roundedUpClickSendAgain))
            return
        }
        catch (e: Exception)
        {
            displayError(S.badAmount, amount)
            return
        }

        // make sure something is being sent
        if ((amountDec == BigDecimal.ZERO)&&sendingTheseAssets.isEmpty())
        {
            displayError(S.badAmount, amount)
            return
        }

        if (acc.toFinestUnit(amountDec) > acc.wallet.balance)
        {
            displayError(S.insufficentBalance, "Account contains ${acc.wallet.balance}.  Attempted to send ${amountDec}.")
            return
        }

        // Make sure the address is consistent with the selected coin to send
        val addrText = toAddress.trim()

        if (addrText.isEmpty())
        {
            displayError(i18n(S.badAddress))
        }

        val sendAddress = try
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

        uiState.value = uiState.value.copy(
          toAddressFinal = sendAddress
        )

        if (acc.wallet.chainSelector != sendAddress.blockchain)
        {
            displayError(S.chainIncompatibleWithAddress, toAddress)
            return
        }
        if (sendAddress.type == PayAddressType.NONE)
        {
            displayError(S.badAddress, toAddress)
            return
        }

        when (currencyCode)
        {
            acc.currencyCode ->
            {
            }
            fiatCurrencyCode ->
            {
                try
                {
                    if (fpc != BigDecimal.ZERO)
                    {
                        val decimalPrecision = 20L
                        val roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
                        val decimalMode = DecimalMode(decimalPrecision, roundingMode)
                        amountDec = amountDec.divide(fpc, decimalMode)
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
        val confirmAmtString = preferenceDB.getString(info.bitcoinunlimited.www.wally.CONFIRM_ABOVE_PREF, "0") ?: "0"
        val confirmAmt = try
        {
            CurrencyDecimal(confirmAmtString)
        }
        catch (e: Exception)
        {
            CURRENCY_ZERO
        }

        // If sending a large amount, or any assets, ask to confirm
        if ((amountDec >= confirmAmt)|| sendingTheseAssets.isNotEmpty())
        {
            val fiatAmt = if ((acc.fiatPerCoin > BigDecimal.ZERO)&&(amountDec > BigDecimal.ZERO))
            {
                val fiatDisplay = amountDec * acc.fiatPerCoin
                "(" + i18n(S.approximatelyT) % mapOf("qty" to FiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) + ")"
            }
            else
            {
                ""
            }

            // sendToAddress.value = sendAddr

            LogIt.info("checking against ${acc.name}")
            if (acc.toFinestUnit(amountDec) <= acc.wallet.balance)
            {
                // TODO: Display sendConfirm instead
                // When sendConfirm is not empty, a confirmation dialog is displayed
                /*
                sendConfirm = i18n(S.SendConfirmSummary) % mapOf(
                  "amt" to account.format(amount), "currency" to account.currencyCode,
                  "dest" to sendAddr.toString(),
                  "inFiat" to fiatAmt,
                  "assets" to if (sendingTheseAssets.isEmpty()) "" else i18n(S.AndSomeAssets),
                )
                 */

                uiState.value = uiState.value.copy(
                  isConfirming = true,
                  toAddressFinal = sendAddress,
                  fiatAmount = fiatAmt
                )
            }
        }
        else  // otherwise just send it
        {
            actuallySend(sendAddress, amountDec)
        }
    }

    override fun actuallySend(toAddress: PayAddress?, amount: BigDecimal?)
    {
        val account = selectedAccountUi2.value ?: return
        val note = uiState.value.note
        val sendAddressTmp = toAddress ?: uiState.value.toAddressFinal
        val qty = amount ?: uiState.value.amountFinal
        val balance = account.balanceState.value
        val spendAll = qty == balance

        if (sendAddressTmp == null)
        {
            displayError("Destination address is required to send")
            return
        }
        // Smart cast to non-nullable types
        val sendAddress: PayAddress = sendAddressTmp

        displayNotice(S.Processing, null)

        // Launch to avoid network on main thread exception
        // Grab copies of all the data we need
        val sendList = account.assetTransferList
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
                    tx = txFor(cs)
                    try
                    {
                        val atomAmt = act.toFinestUnit(qty)
                        if (sendList.size == 0)
                        {
                            // If we are spending all, then deduct the fee from the amount (which was set above to the full ungrouped balance)
                            tx = act.wallet.send(atomAmt, sendAddress, spendAll, false, note = note)
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
                            act.wallet.send(tx,false, note = note)
                        }
                        LogIt.info("Sending TX: ${tx.toHex()}")
                        clearAlerts()
                        displayNotice(S.sendSuccess, "$atomAmt -> $sendAddress: ${tx.idem}")
                        account.clearAssetTransferList()
                        nav.go(ScreenId.Home)
                        uiState.value = SendScreenUi() // We are done with a send so reset state machine
                    }
                    catch (e: Exception)  // We don't want to crash, we want to tell the user what went wrong
                    {
                        displayUnexpectedException(e)
                        handleThreadException(e)
                        LogIt.info("Failed transaction is: ${tx.toHex()}")
                        uiState.value = uiState.value.copy(
                          isConfirming = false // Force reconfirm is there is any error with the send
                        )
                    }
                }
                catch (e: WalletNotEnoughBalanceException)
                {
                    displayError(i18n(S.insufficentBalance))
                    LogIt.info("Failed transaction is: ${tx.toHex()}")
                    uiState.value = uiState.value.copy(
                      isConfirming = false // Force reconfirm is there is any error with the send
                    )
                }
                catch (e: Exception)  // We don't want to crash, we want to tell the user what went wrong
                {
                    displayUnexpectedException(e)
                    handleThreadException(e)
                    LogIt.info("Failed transaction is: ${tx.toHex()}")
                    uiState.value = uiState.value.copy(
                      isConfirming = false // Force reconfirm is there is any error with the send
                    )
                }
            }
        }
    }

    override fun onCleared()
    {
        super.onCleared()
        balanceJob?.cancel()
        assetsToSend.value = listOf()
    }
}

data class SendScreenNavParams(
  val toAddress: String = "",
  val amount: BigDecimal? = null,
  val note: String? = null
)

@Composable
fun SendScreenContent(
  viewModel: SendScreenViewModel,
  balanceViewModel: BalanceViewModel = viewModel { BalanceViewModelImpl() },
  syncViewModel: SyncViewModel = viewModel { SyncViewModelImpl() },
  params: SendScreenNavParams
)
{
    val keyboardController = LocalSoftwareKeyboardController.current
    var isScanningQr by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val uiState = viewModel.uiState.collectAsState()
    val assetsToSendState = viewModel.assetsToSend.collectAsState()
    val sendingTheseAssets = assetsToSendState.value
    val toAddress = uiState.value.toAddress
    val note = uiState.value.note
    val amount = uiState.value.amount
    val isConfirming = uiState.value.isConfirming

    LaunchedEffect(params.toAddress) {
        if (params.toAddress.isEmpty() && uiState.value.toAddress.isEmpty())
            focusRequester.requestFocus()
    }

    Box (
      modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()
          .background(Color.White)
          .verticalScroll(rememberScrollState())
          .clickable (
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { keyboardController?.hide() }
        ) {
            Spacer(Modifier.height(16.dp))
            AccountPill(buttonsEnabled = false, balanceViewModel, syncViewModel)
            Column(
              modifier = Modifier.wrapContentHeight()
                .fillMaxWidth()
                .padding(16.dp)
                .clickable (
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null
                ) { keyboardController?.hide() }
                .testTag("SendScreenContentColumn")
            ) {
                if (isConfirming)
                {
                    ConfirmSend(viewModel)
                    Spacer(Modifier.height(16.dp))
                }
                WallyInputField(
                  mod = Modifier.focusRequester(focusRequester).testTag("sendToAddress"),
                  text = toAddress,
                  label = i18n(S.sendToAddressHint),
                  placeholder = i18n(S.enterAddress),
                  iconContentDescription = i18n(S.clearAddress),
                  isSingleLine = false,
                  isReadOnly = isConfirming
                ) {
                    viewModel.setToAddress(it)
                }
                Spacer(Modifier.height(8.dp))
                WallyInputField(
                  mod = Modifier.testTag("noteInput"),
                  text = note,
                  label = i18n(S.noteOptional),
                  placeholder = i18n(S.editSendNoteHint),
                  iconContentDescription = i18n(S.clearNote),
                  isReadOnly = isConfirming
                ) {
                    viewModel.setNote(it)
                }
                Spacer(Modifier.height(8.dp))
                WallyNumericInputFieldBalance(
                  mod = Modifier.testTag("amountToSendInput"),
                  amountString = amount,
                  label = i18n(S.amountPlain),
                  placeholder = i18n(S.enterNEXAmount),
                  isReadOnly = isConfirming,
                  hasIosDoneButton = !isConfirming,
                  vm = viewModel
                ) {
                    viewModel.setSendQty(it)
                }
                Spacer(Modifier.height(16.dp))
                if (sendingTheseAssets.isNotEmpty()) {
                    AssetsList(sendingTheseAssets, true, viewModel)
                    Spacer(Modifier.height(160.dp))
                }
            }
        }

        if (!isConfirming)
            Column(
              modifier = Modifier.align(Alignment.BottomCenter)
                .wrapContentHeight()
                .fillMaxWidth()
            ) {
                ThumbButtonFAB(
                  onScanQr = { isScanningQr = true },
                  onResult = {
                      viewModel.checkUriAndSetUi(it)
                  }
                )
                Spacer(Modifier.height(80.dp))
            }
        SendBottomButtons(Modifier.align(Alignment.BottomCenter), viewModel)
    }

    if (isScanningQr && platform().hasQrScanner)
    {
        QrScannerDialog(
          onDismiss = {
              clearAlerts()
              isScanningQr = false
          },
          onScan = {
              if (it.isNotEmpty() && isScanningQr)
                  isScanningQr = false
              viewModel.checkUriAndSetUi(it)
          }
        )
    }
}

@Composable
fun ConfirmSend(viewModel: SendScreenViewModel)
{
    val uiState = viewModel.uiState.collectAsState()
    val assetsToSendState = viewModel.assetsToSend.collectAsState()
    val toAddress = uiState.value.toAddress
    val currencyCode = uiState.value.currencyCode
    val quantity = uiState.value.amount
    val note = uiState.value.note
    val assetsToSend = assetsToSendState.value.size

    Column (
      modifier = Modifier.fillMaxWidth().wrapContentHeight()
    ) {
        Text(
          text = i18n(S.confirmSend),
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Box(
          modifier = Modifier.fillMaxWidth()
            .wrapContentHeight()
            .border(
              width = 1.dp,
              color = Color.LightGray,
              shape = RoundedCornerShape(16.dp)
            )
        ) {
            Column(
              modifier = Modifier.fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconLabelValueRow(
                  icon = Icons.Outlined.Mail,
                  label = i18n(S.To),
                  value = toAddress // spendingTokenTypes.toString()
                )
                if (quantity.isNotEmpty())
                {
                    Spacer(modifier = Modifier.height(16.dp))
                    IconLabelValueRow(
                      icon = Icons.Default.AttachMoney, // TODO: Nexa logo
                      label = currencyCode,
                      value = quantity, // cc.cryptoFormat.format(acc.fromFinestUnit(netSats))
                    )
                }
                if (assetsToSend > 0)
                {
                    Spacer(modifier = Modifier.height(16.dp))
                    IconLabelValueRow(
                      icon = Icons.Outlined.Image,
                      labelRes = S.assets,
                      value = assetsToSend.toString()
                    )
                }
                if (note.isNotEmpty())
                {
                    Spacer(modifier = Modifier.height(16.dp))
                    IconLabelValueRow(
                      icon = Icons.Outlined.Note,
                      label = i18n(S.note),
                      value = note
                    )
                }
            }
        }
    }
}

@Composable
fun SendBottomButtons(mod: Modifier, viewModel: SendScreenViewModel)
{
    val uiState = viewModel.uiState.collectAsState()
    val isConfirming = uiState.value.isConfirming

    // Row with buttons at the bottom
    Row(
      modifier = mod.fillMaxWidth()
        .background(Color.White)
        .padding(2.dp),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (isConfirming)
            IconTextButtonUi2(
              icon = Icons.Outlined.Send,
              modifier = Modifier.weight(1f),
              description = i18n(S.confirmSend),
              color = wallyPurple,
            ) {
                viewModel.actuallySend()
            }
        else
            IconTextButtonUi2(
              icon = Icons.Outlined.Send,
              modifier = Modifier.weight(1f),
              description = i18n(S.Send),
              color = wallyPurple,
            ) {
                viewModel.onSendButtonClicked()
            }
        IconTextButtonUi2(
          icon = Icons.Outlined.Cancel,
          modifier = Modifier.weight(1f),
          description = i18n(S.SendCancel),
          color = wallyPurple,
        ) {
            nav.back()
            viewModel.resetUi()
        }
    }
}

@Composable
fun SendScreen(account: Account, navParams: SendScreenNavParams, viewModel: SendScreenViewModel = viewModel { SendScreenViewModelImpl(account) })
{
    /*
       Update UI when sending with a new account or the account has changed.
     */
    LaunchedEffect(account) {
        viewModel.setAccount(account)
    }

    // Update UI when sending to a new address
    LaunchedEffect(navParams.toAddress) {
        if (navParams.toAddress.isNotEmpty())
            viewModel.setToAddress(navParams.toAddress)
    }

    // Update UI when amount is set in nav params
    LaunchedEffect(navParams.amount) {
        navParams.amount?.let {
            viewModel.setSendQty(it)
        }
    }

    // Update UI when note is set in nav params
    LaunchedEffect(navParams.note) {
        navParams.note?.let {
            viewModel.setNote(it)
        }
    }

    WallyThemeUi2 {
        Surface(
          modifier = Modifier.fillMaxSize().background(Color.White)
        ) {
            SendScreenContent(viewModel, params = navParams)
        }
    }
}

@Composable
fun AssetsList(assetList: List<AssetPerAccount>, editable: Boolean = true, viewModel: SendScreenViewModel) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column {
        Text(
          text = i18n(S.title_activity_assets),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.align(Alignment.CenterHorizontally).clickable { keyboardController?.hide() }
        )
        Spacer(Modifier.height(16.dp).clickable { keyboardController?.hide() })
        assetList.forEach { asset ->
            AssetListItemEditable(asset, editable, viewModel.uiState.collectAsState().value.isConfirming)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AssetListItemEditable(assetPerAccount: AssetPerAccount, editable: Boolean = true, isConfirming: Boolean = false) {
    val asset = assetPerAccount.assetInfo
    val tokenAmount = assetPerAccount.groupInfo.tokenAmt
    val expandable: Boolean = if(tokenAmount == 1L) false else true
    var expanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf(assetPerAccount.editableAmount?.toPlainString() ?: "") }
    val assetNameState = asset.nameObservable.collectAsState()
    val nft = asset.nft
    val creator = nft?.author ?: "" // author missing
    val series = nft?.series ?: "" // series missing
    val name = if(nft != null && nft.title.isNotEmpty())
        nft.title
    else
        assetNameState.value ?: (asset.ticker ?: asset.groupId.toString())

    lateinit var containerModifier: Modifier
    lateinit var clickableModifier: Modifier

    fun initModifiers()
    {
        containerModifier = if (expandable && editable)
            Modifier
              .animateContentSize()
              .clickable { expanded = !expanded }
        else
            Modifier
        clickableModifier = if (expandable && editable)
            Modifier.clickable { expanded = !expanded }
        else
            Modifier
    }
    initModifiers()
    LaunchedEffect(editable) {
        initModifiers()
    }

    Column(
      modifier = containerModifier.background(Color.White)
    ){
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
            MpMediaView(asset.iconImage, asset.iconBytes, asset.iconUri?.toString(), hideMusicView = true) { mi, draw ->
                val m = clickableModifier.background(Color.Transparent).size(75.dp).padding(8.dp)
                draw(m)
            }

            Spacer(Modifier.width(16.dp))

            Row (
              modifier = clickableModifier
            ){
                Column(
                  modifier = clickableModifier.weight(4f),
                  verticalArrangement = Arrangement.Center
                ) {
                    Text(
                      modifier = clickableModifier,
                      text = name,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      modifier = clickableModifier,
                      text = series,
                      fontSize = 14.sp,
                      color = wallyPurple,
                      fontWeight = FontWeight.Bold
                    )
                }
                Text(
                  text = creator,
                  modifier = clickableModifier.padding(start = 8.dp).weight(2f),
                  textAlign = TextAlign.End,
                  fontSize = 14.sp,
                  fontStyle = FontStyle.Italic,
                  color = Color.Black
                )
                if (expandable && editable)
                {
                    IconButton(onClick = {
                        expanded = !expanded
                        // TODO: change amount for this asset
                    }) {
                        if(expanded)
                            Icon(Icons.Filled.ExpandLess, contentDescription = "expand less")
                        else
                            Icon(Icons.Filled.ExpandMore, contentDescription = "expand")
                    }
                }
            }
        }
        val amount = assetPerAccount.editableAmount ?: BigDecimal.ZERO
        if (amount > BigDecimal.ONE)
            Text(i18n(S.Amount) % mapOf("amt" to amount.toString()))
        else if (amount == BigDecimal.ONE  || amount == BigDecimal.ZERO)
            Text(i18n(S.Amount) % mapOf("amt" to "1"))

        if(expandable && editable)
            AnimatedVisibility(visible = expanded) {
                WallyNumericInputFieldAsset(
                  amountString = quantity,
                  label = i18n(S.amountPlain),
                  placeholder = i18n(S.enterAmount),
                  decimals = false,
                  isReadOnly = isConfirming,
                  hasIosDoneButton = !isConfirming,
                  onValueChange = {
                      quantity = it
                      if (it.isEmpty())
                          assetPerAccount.editableAmount = BigDecimal.ZERO
                      else
                          assetPerAccount.editableAmount = assetPerAccount.tokenDecimalFromString(it)
                  }
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallyInputField(
  mod: Modifier = Modifier,
  text: String,
  label: String,
  placeholder: String,
  iconContentDescription: String,
  isSingleLine: Boolean = true,
  isReadOnly: Boolean = false,
  onValueChange: (String) -> Unit
)
{
    OutlinedTextField(
      value = text,
      onValueChange = { newValue -> onValueChange(newValue) },
      keyboardOptions = KeyboardOptions.Default.copy(
        imeAction = ImeAction.Next
      ),
      label = { Text(label) },
      placeholder = { Text(placeholder) },
      trailingIcon = {
          if (text.isNotEmpty()) {
              IconButton(onClick = { if (!isReadOnly) onValueChange("") }) {
                  Icon(Icons.Filled.Close, contentDescription = iconContentDescription)
              }
          }
      },
      modifier = mod.fillMaxWidth(),
      singleLine = isSingleLine,
      readOnly = isReadOnly
    )
}

@Composable
fun WallyNumericInputFieldBalance(
    mod: Modifier = Modifier,
    amountString: String,
    label: String,
    placeholder: String,
    singleLine: Boolean = true,
    decimals: Boolean = true,
    vm: SendScreenViewModel,
    action: ImeAction = ImeAction.Done,
    isReadOnly: Boolean = true,
    hasIosDoneButton: Boolean = true,
    onValueChange: (String) -> Unit
)
{
    val balanceViewModel = viewModel { BalanceViewModelImpl() }
    val balanceState = balanceViewModel.balance.collectAsState()
    val balance = balanceState.value

    // Validate input and allow max 2 decimal places
    fun validateInput(input: String): Boolean {
        val regex = if (decimals)
            // Allow empty input or input that matches the regex for numbers with max 2 decimal places
            Regex("^\\d*(\\.\\d{0,2})?\$")
        else
            // Allow only whole numbers (no decimals)
            Regex("^\\d*\$")
        return input.matches(regex)
    }

    Row {
        OutlinedTextField(
          value = amountString,
          onValueChange = { newValue ->
              if (validateInput(newValue)) {
                  onValueChange(newValue)
              }
          },
          keyboardOptions = KeyboardOptions(
            imeAction = action,
            keyboardType = KeyboardType.Number
          ),
          label = { Text(label) },
          placeholder = { Text(placeholder) },
          trailingIcon = {
              if (amountString.isNotEmpty()) {
                  IconButton(onClick = { if (!isReadOnly) onValueChange("") }) {
                      Icon(Icons.Filled.Close, contentDescription = i18n(S.clearAmount))
                  }
              }
          },
          modifier = mod.weight(1f).onFocusChanged {
              if (it.isFocused)
              {
                  softKeyboardBar.value = { modifier ->
                      Row(modifier.background(samsungKeyBoardGray), horizontalArrangement = Arrangement.SpaceEvenly) {
                          val mod = Modifier.weight(1f)
                          val fontStyle = MaterialTheme.typography.labelLarge
                          TextButton(
                            modifier = mod,
                            content = { Text(i18n(S.sendAll), style = fontStyle) },
                            onClick = { vm.setSendQty(balance) }
                          )
                          TextButton(
                            modifier = mod,
                            content = { Text(i18n(S.thousand), style = fontStyle) },
                            onClick = {
                              vm.multiplySendQty(1000) }
                          )
                          TextButton(
                            modifier = mod,
                            content = { Text(i18n(S.million), style = fontStyle) },
                            onClick = { vm.multiplySendQty(1000000) }
                          )
                          TextButton(
                            modifier = mod,
                            content = { Text(i18n(S.cancel), style = fontStyle) }, onClick = { vm.setSendQty("") }
                          )
                      }
                  }
              }
              else
              {
                  softKeyboardBar.value = null
              }
          },
          singleLine = singleLine,
          readOnly = isReadOnly
        )

        if (hasIosDoneButton)
            DoneButtonOptional(Modifier.align(Alignment.CenterVertically))
    }
}


/*
    This is a temporary workaround because compose does not support Done button for iOS numeric keyboard
    I think it is possible to a native iOS input in the iosMain module that adds a Done button
 */
@Composable
fun DoneButtonOptional(mod: Modifier = Modifier, onClick: () -> Unit = {})
{
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasDoneButton = platform().hasDoneButton

    if (hasDoneButton)
    {
        Spacer(Modifier.width(2.dp))
        Button(
          modifier = mod,
          onClick = {
              // Also manually dismiss the keyboard on Done button click
              keyboardController?.hide()
              onClick()
          }
        ) {
            Text(text = i18n(S.done))
        }
    }
}

@Composable
fun WallyNumericInputFieldAsset(
  amountString: String,
  label: String,
  placeholder: String,
  singleLine: Boolean = true,
  decimals: Boolean = true,
  action: ImeAction = ImeAction.Done,
  isReadOnly: Boolean = true,
  hasIosDoneButton: Boolean = true,
  onValueChange: (String) -> Unit
)
{
    val isIos = !platform().hasGallery
    val keyboardController = LocalSoftwareKeyboardController.current

    // Validate input and allow max 2 decimal places
    fun validateInput(input: String): Boolean {
        val regex = if (decimals)
        // Allow empty input or input that matches the regex for numbers with max 2 decimal places
            Regex("^\\d*(\\.\\d{0,2})?\$")
        else
        // Allow only whole numbers (no decimals)
            Regex("^\\d*\$")
        return input.matches(regex)
    }

    Row {
        OutlinedTextField(
          value = amountString,
          onValueChange = { newValue ->
              if (validateInput(newValue)) {
                  onValueChange(newValue)
              }
          },
          keyboardOptions = KeyboardOptions(
            imeAction = action,
            keyboardType = KeyboardType.Number
          ),
          label = { Text(label) },
          placeholder = { Text(placeholder) },
          trailingIcon = {
              if (amountString.isNotEmpty()) {
                  IconButton(onClick = { if (!isReadOnly) onValueChange("") }) {
                      Icon(Icons.Filled.Close, contentDescription = i18n(S.clearAmount))
                  }
              }
          },
          modifier = Modifier.weight(1f).testTag("WallyNumericInputFieldAsset"),
          singleLine = singleLine,
          readOnly = isReadOnly
        )

        /*
            This is a temporary workaround because compose does not support Done button for iOS numeric keyboard
            I think it is possible to a native iOS input in the iosMain module that adds a Done button
         */
        if (isIos && hasIosDoneButton)
        {
            Spacer(Modifier.width(2.dp))
            Button(
              modifier = Modifier.align(Alignment.CenterVertically),
              onClick = {
                  // Also manually dismiss the keyboard on Done button click
                  keyboardController?.hide()
              }
            ) {
                Text(text = i18n(S.done))
            }
        }
    }
}

@Composable
fun ThumbButton(icon: ImageVector, textRes: Int, mod: Modifier = Modifier, color: Color = Color.White)
{
    Column(
      modifier = mod.wrapContentHeight().wrapContentWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Icon(icon, contentDescription = i18n(textRes), tint = color)
        Spacer(Modifier.height(4.dp))
        Text(i18n(textRes), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ThumbButtonFAB(pasteIcon: ImageVector = Icons.Outlined.ContentPaste, onResult: (String) -> Unit, onScanQr: () -> Unit, clipmgr: ClipboardManager = LocalClipboardManager.current)
{
    Row(
      modifier = Modifier.wrapContentHeight().fillMaxWidth(),
      horizontalArrangement = Arrangement.Center
    ) {
        Row(
          modifier = Modifier.wrapContentHeight()
            .wrapContentWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(32.dp))
            .background(wallyPurple, shape = RoundedCornerShape(32.dp)),
          horizontalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.width(32.dp))
            if (platform().hasGallery)
                ThumbButton(
                  icon = Icons.Outlined.DocumentScanner,
                  textRes = S.imageQr,
                  mod = Modifier.clickable {
                    ImageQrCode { qrContent ->
                        qrContent?.let {
                            clearAlerts()
                            onResult(it)
                        }
                    }
                  }.padding(end = 12.dp))
            if (platform().hasQrScanner)
                ThumbButton(
                  icon = Icons.Outlined.QrCodeScanner,
                  textRes = S.scanQr,
                  mod = Modifier.clickable {
                    clearAlerts()
                    onScanQr()
                  }.padding(end = 16.dp))
            if (!platform().usesMouse)
                ThumbButton(
                  icon = pasteIcon,
                  textRes = S.paste,
                  mod = Modifier.clickable {
                      clearAlerts()
                      val clipText = clipmgr.getText()?.text
                      if (clipText != null && clipText != "")
                          onResult(clipText)
                      else
                          displayNotice(S.pasteIsEmpty)
                  }.padding(end = 32.dp)
                )
        }
    }
}