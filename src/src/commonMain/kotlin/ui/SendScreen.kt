package info.bitcoinunlimited.www.wally.ui

import AudioPlayer
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyTheme
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurple
import info.bitcoinunlimited.www.wally.ui.theme.wallyTranslucentPurple
import info.bitcoinunlimited.www.wally.ui.views.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SendScreen")

data class SendScreenUi(
  val toAddress: String = "",
  val note: String = "",
  val amount: String = "",
  val toAddressFinal: PayAddress? = null,
  val isConfirming: Boolean = false,
  val fiatAmount: String = "",
  val currencyCode: String = ""
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

abstract class SendScreenViewModel(val account:MutableStateFlow<Account?>): ViewModel()
{
    constructor(act: Account?):this(MutableStateFlow(act))

    val assetsToSend: MutableStateFlow<List<AssetPerAccount>> = MutableStateFlow(listOf())
    val uiState = MutableStateFlow(SendScreenUi())
    // TODO: Figure out how to set this to false for various platforms...
    val hasP2pConnection: MutableStateFlow<Boolean> = MutableStateFlow(true)

    var balanceViewModel: BalanceViewModel = BalanceViewModelImpl(account)
    var syncViewModel: SyncViewModel = SyncViewModelImpl()

    val audioPlayer: AudioPlayer = AudioPlayer()

    abstract fun setAccount(account: Account)
    abstract fun checkUriAndSetUi(urlStr: String)

    fun setToAddress(address: String)
    {
        uiState.value = uiState.value.copy(toAddress = address)
        val tmp = wallyApp!!.visibleAccountFor(address, account.value)
        if (tmp!= null)
          setAccount(tmp)
    }

    fun setNote(newNote: String)
    {
        uiState.value = uiState.value.copy(note = newNote)
    }

    fun setSendQty(sendQty: String)
    {
        // TODO we need to get the number grouping separator, not assume comma
        // actual fun getGroupingSeparator(): Char { return java.text.DecimalFormatSymbols.getInstance().groupingSeparator }
        /*actual fun getGroupingSeparator(): Char {
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    val sep = formatter.groupingSeparator ?: ","
    return sep.first()
}
         */
        val cleanedQty:String = if (sendQty == "all") account.value?.balance?.toPlainString() ?: "0" else sendQty.replace(",", "")
        uiState.value = uiState.value.copy(amount = cleanedQty)
    }

    fun setChain(chain: ChainSelector)
    {
        uiState.value = uiState.value.copy(currencyCode = chainToCurrencyCode[chain] ?: "")
    }

    fun setSendQty(sendQty: BigDecimal)
    {
        uiState.value = uiState.value.copy(amount = sendQty.toStringExpanded())
    }

    fun checkForBlockchainConnection()
    {
        account.value?.wallet?.blockchain?.net?.let { connectionManager ->
            hasP2pConnection.value = connectionManager.p2pCnxns.isNotEmpty()
        }
    }

    abstract fun multiplySendQty(multiplier: Int)
    abstract fun populateAssetsList(assetTransferList: MutableList<GroupId>, assets: Map<GroupId, AssetPerAccount>)
    abstract fun onSendButtonClicked()
    abstract fun actuallySend(toAddress: PayAddress? = null, amount: BigDecimal? = null)

    fun resetUi()
    {
        uiState.value = SendScreenUi()
    }

    fun clear()
    {
        assetsToSend.value = listOf()
        val act = account.value
        act?.clearAssetTransferList()  // If you cancel a send (verses going back), you clear out the state
    }

    override fun onCleared()
    {
        super.onCleared()
        clear()
    }
}

class SendScreenViewModelFake(act: Account): SendScreenViewModel(act)
{
    init
    {
        setAccount(act)
    }
    override fun setAccount(act: Account)
    {
        account.value = act
        setChain(act.chain.chainSelector)
    }
    override fun checkUriAndSetUi(urlStr: String) {}
    override fun multiplySendQty(multiplier: Int) {}
    override fun populateAssetsList(assetTransferList: MutableList<GroupId>, assets: Map<GroupId, AssetPerAccount>) {}
    override fun onSendButtonClicked() {}
    override fun actuallySend(toAddress: PayAddress?, amount: BigDecimal?) {}
}

class SendScreenViewModelImpl(act: Account): SendScreenViewModel(act)
{
    var balanceJob: Job? = null

    init
    {
        setAccount(act)
    }

    override fun setAccount(act: Account)
    {
        // Always set this, regardless of whether it was already set
        // because state may have changed (like other assets chosen)
        account.value = act
        populateAssetsList(act.assetTransferList, act.assets)
        balanceViewModel.setAccount(act)
        setChain(act.chain.chainSelector)
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
            setToAddress(sendAddress)

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
        if (acc == null)
        {
            displayError(S.NoAccounts, "")
            return
        }
        val sendingTheseAssets = acc.assetTransferList
        val fpc = acc.fiatPerCoinObservable.value
        val amount = uiState.value.amount
        val toAddress = uiState.value.toAddress
        val currencyCode = chainToDisplayCurrencyCode[acc.wallet.chainSelector]

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

        val preferenceDB = getSharedPreferences(TEST_PREF + i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
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
        val account = account.value
        if (account == null)
        {
            displayError(S.NoAccounts, "")
            return
        }
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
                                    val tokqty = if (eAmt == null) assetPerAccount.groupInfo.tokenAmount  // If they don't change the amount, send all of them (see default in AssetScreen.kt)
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
                        clear()
                        nav.go(ScreenId.Home)
                        uiState.value = SendScreenUi() // We are done with a send so reset state machine
                        requestInAppReview()
                        sendSuccessAnimationIsPlaying.value = true
                        viewModelScope.launch(Dispatchers.Main) {
                            audioPlayer.playSound(0)
                        }
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
    }
}

data class SendScreenNavParams(
  val toAddress: String = "",
  val amount: BigDecimal? = null,
  val note: String? = null
)

@Composable
fun SendScreenContent(
  pillViewModel: AccountPillViewModel,
  viewModel: SendScreenViewModel,
  params: SendScreenNavParams
)
{
    val keyboardController = LocalSoftwareKeyboardController.current
    var isScanningQr by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }
    val uiState = viewModel.uiState.collectAsState()
    val assetsToSendState = viewModel.assetsToSend.collectAsState()
    val hasInternet = viewModel.hasP2pConnection.collectAsState().value
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
            ) {
              keyboardController?.hide()
              viewModel.checkForBlockchainConnection()
          }
        ) {
            Spacer(Modifier.height(16.dp))
            pillViewModel.draw(buttonsEnabled = false)
            Column(
              modifier = Modifier.wrapContentHeight()
                .fillMaxWidth()
                .padding(16.dp)
                .clickable (
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null
                ) {
                    keyboardController?.hide()
                    viewModel.checkForBlockchainConnection()
                }
                .testTag("SendScreenContentColumn")
            ) {
                if (isConfirming)
                {
                    ConfirmSend(viewModel)
                    Spacer(Modifier.height(16.dp))
                }
                else  // We do not want to show the redundant edit boxes if we have the confirmation box up.
                {
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
                    WallyNumericInputFieldBalance(
                      mod = Modifier.testTag("amountToSendInput"),
                      amount = uiState.value.amount,
                      label = i18n(S.amountPlain),
                      placeholder = i18n(S.enterNEXAmount),
                      isReadOnly = isConfirming,
                      hasIosDoneButton = !isConfirming,
                      hasButtonRow = true,
                      focusRequester = amountFocusRequester
                    ) {
                        viewModel.setSendQty(it)
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
                    Spacer(Modifier.height(16.dp))
                    if (!hasInternet)
                        ConnectionWarning()
                }
                if (sendingTheseAssets.isNotEmpty())
                {
                    Spacer(Modifier.height(12.dp))
                    AssetsList(sendingTheseAssets, true, viewModel)
                    Spacer(Modifier.height(200.dp))  // Space this so editing the amount of an asset is not underneath the soft keyboard
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
                    BlockchainIcon(currencyCode, quantity, viewModel.account.collectAsState().value?.wallet?.chainSelector)
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
            IconTextButton(
              icon = Icons.Outlined.Send,
              modifier = Modifier.weight(1f),
              description = i18n(S.confirmSend),
              color = wallyPurple,
            ) {
                viewModel.actuallySend()
            }
        else
            IconTextButton(
              icon = Icons.Outlined.Send,
              modifier = Modifier.weight(1f),
              description = i18n(S.Send),
              color = wallyPurple,
            ) {
                viewModel.onSendButtonClicked()
            }
        IconTextButton(
          icon = Icons.Outlined.Cancel,
          modifier = Modifier.weight(1f),
          description = i18n(S.SendCancel),
          color = wallyPurple,
        ) {
            if (isConfirming)  // If cancel confirming, just go back to editing the send
                viewModel.uiState.value = viewModel.uiState.value.copy(isConfirming = false)
            else
            {
                viewModel.clear()  // If you cancel the gui clears (if you want to preserve the values use the back button)
                nav.back()
                viewModel.resetUi()
            }
        }
    }
}

@Composable
fun SendScreen(pillViewModel: AccountPillViewModel, navParams: SendScreenNavParams, viewModel: SendScreenViewModel = viewModel { SendScreenViewModelImpl(pillViewModel.account.value ?: wallyApp!!.preferredVisibleAccount()) })
{
    val account = pillViewModel.account.collectAsState().value ?: wallyApp!!.preferredVisibleAccount()
    /*
       Update UI when sending with a new account or the account has changed.
     */
    LaunchedEffect(account) {
        viewModel.hasP2pConnection
        viewModel.setAccount(account)
        viewModel.checkForBlockchainConnection()
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

    WallyTheme {
        Surface(
          modifier = Modifier.fillMaxSize().background(Color.White)
        ) {
            SendScreenContent(pillViewModel, viewModel, params = navParams)
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
    val tokenAmount = assetPerAccount.groupInfo.tokenAmount
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

        // Its overwhelmingly likely this is an NFT so has no concept of an amount
        if (assetPerAccount.groupInfo.isSubgroup() && (tokenAmount == 1L))
        {

        }
        else // Otherwise show the amount (and let it be edited)
        {
            val amount = assetPerAccount.editableAmount ?: BigDecimal.ZERO

            // If we are editing the amount, we can't keep showing the amount here, or the number here and in the edit box are inconsistent (or redundant).
            // We also should not show the edit box when confirming because the field is not editable at that point.  That confuses the user.
            if (!(expandable && editable) || isConfirming)
            {
                if (amount > BigDecimal.ONE)
                    Text(i18n(S.AmountWithValue) % mapOf("amt" to amount.toPlainString()))
                else if (amount == BigDecimal.ONE || amount == BigDecimal.ZERO)
                    Text(i18n(S.AmountWithValue) % mapOf("amt" to "1"))
            }
            else
            {
                AnimatedVisibility(visible = expanded) {
                    WallyNumericInputFieldAsset(
                      amountString = quantity,
                      label = i18n(S.amountPlain),
                      placeholder = i18n(S.enterAmount) % mapOf("assetName" to name),
                      decimals = assetPerAccount.assetInfo.tokenInfo?.genesisInfo?.decimal_places ?: 0,
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


// Predeclare regex if possible because regex compilation is actually nontrivial
private val WholeNumbersRegex = Regex("^\\d*\$")

@Composable
fun WallyNumericInputFieldAsset(
  amountString: String,
  label: String,
  placeholder: String,
  singleLine: Boolean = true,
  decimals: Int = 2,
  action: ImeAction = ImeAction.Done,
  isReadOnly: Boolean = true,
  hasIosDoneButton: Boolean = true,
  onValueChange: (String) -> Unit
)
{
    val isIos = !platform().hasGallery
    val keyboardController = LocalSoftwareKeyboardController.current

    // Validate input and allow max decimals decimal places
    fun validateInput(input: String): String?
    {
        val regex = if (decimals != 0)
        // Allow empty input or input that matches the regex for numbers with max 2 decimal places
            Regex("^\\d*(\\.\\d{0,$decimals})?")
        else
        // Allow only whole numbers (no decimals)
            WholeNumbersRegex
        val match = regex.find(input)
        if (match == null) return null
        return match.value
    }

    Row {
        OutlinedTextField(
          value = amountString,
          onValueChange = { newValue ->
              val s = validateInput(newValue)
              if (s != null)
              {
                  onValueChange(s)
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
            .background(wallyTranslucentPurple, shape = RoundedCornerShape(32.dp)),
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