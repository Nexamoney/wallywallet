package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*
import wpw.src.generated.resources.Res

enum class AccountAction
{
    Delete, Rediscover, RediscoverBlockchain, Reassess, RecoveryPhrase, PrimaryAccount, PinChange
}

@Composable
fun AccountDetailScreen(account: Account, nav: ScreenNav)
{
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        CenteredSectionText(i18n(S.AccountStatistics))
        if (!account.wallet.isDeleted)
        {
            AccountBlockchainDetail(account)
            AccountFirstLastSend(account.wallet.statistics())
            GuiAccountTxStatisticsRow(account.wallet.statistics(), { nav.go(ScreenId.AddressHistory) }, { nav.go(ScreenId.TxHistory) })
        }
        Spacer(modifier = Modifier.padding(4.dp))
        WallyDivider()
        AccountActions(account, { nav.go(ScreenId.TxHistory) }, accountDeleted = {
            nav.back()
            triggerAssignAccountsGuiSlots()
        })
    }
}


@Composable
fun AccountBlockchainDetail(acc: Account)
{
    val chainState = acc.wallet.chainstate
    if (chainState != null)
    {
        AccountBlockchainSync(chainState)
        AccountBlockchainBlockDetails(chainState)
        AccountBlockchainConnectionDetails(chainState)
    }
    else
    {
        Text(i18n(S.walletDisconnectedFromBlockchain))
    }
}

@Composable
fun AccountBlockchainSync(chainState: GlueWalletBlockchain)
{
    val synced = if (chainState.isSynchronized()) S.synced else S.unsynced

    val text = i18n(S.AccountBlockchainSync) % mapOf(
      "sync" to i18n(synced),
      "chain" to chainState.chain.name,

    )
    val fontSize = if (platform().spaceConstrained && !platform().landscape) FontScale(0.90) else FontScale(1.0)
    Text(text, fontSize = fontSize)
}

@Composable
fun AccountBlockchainBlockDetails(chainState: GlueWalletBlockchain)
{
    val text = i18n(S.AccountBlockchainDetails) % mapOf(
      "actBlock" to chainState.syncedHeight.toString(),
      "actBlockDate" to epochToDate(chainState.syncedDate),
      "chainBlockCount" to chainState.chain.curHeight.toString()
    )

    val fontSize = if (platform().spaceConstrained && !platform().landscape) FontScale(0.90) else FontScale(1.0)
    Text(text = text, maxLines = 2, fontSize = fontSize)
}

@Composable
fun AccountBlockchainConnectionDetails(chainState: GlueWalletBlockchain)
{
    val cnxnLst = chainState.chain.net.mapConnections { it.name }
    val trying:List<String> = if (chainState.chain.net is MultiNodeCnxnMgr) (chainState.chain.net as MultiNodeCnxnMgr).initializingCnxns.map { it.name } else listOf()
    val peers = cnxnLst.joinToString(", ") + if (trying.isNotEmpty()) (" " + i18n(S.trying) + " " + trying.joinToString(", ")) else ""
    val text =  i18n(S.AccountBlockchainConnectionDetails) % mapOf(
      "num" to cnxnLst.size.toString(),
      "names" to peers
    )
    val fontSize = if (platform().spaceConstrained && !platform().landscape) FontScale(0.80) else FontScale(1.0)
    Text(text, maxLines = 2, fontSize = fontSize)
}

@Composable
fun AccountFirstLastSend(stat: CommonWallet.WalletStatistics,)
{
    val firstLastSend = i18n(S.FirstLastSend) % mapOf(
      "first" to (if (stat.firstSendHeight == Long.MAX_VALUE) "never" else stat.firstSendHeight.toString()),
      "last" to (if (stat.lastSendHeight==0L) "never" else stat.lastSendHeight.toString()))
    val firstLastReceive = i18n(S.FirstLastReceive) % mapOf(
      "first" to (if (stat.firstReceiveHeight == Long.MAX_VALUE) "never" else stat.firstReceiveHeight.toString()),
      "last" to (if (stat.lastReceiveHeight == 0L) "never" else stat.lastReceiveHeight.toString()))
    val fontSize = if (platform().spaceConstrained && !platform().landscape) FontScale(0.80) else FontScale(1.0)
    Text(firstLastSend, fontSize = fontSize)
    Text(firstLastReceive, fontSize = fontSize)
}


@Composable
fun GuiAccountTxStatisticsRow(stat: CommonWallet.WalletStatistics, onAddressesButtonClicked: () -> Unit, onTxHistoryButtonClicked: () -> Unit)
{
    Row(
      modifier = Modifier.padding(0.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
        WallyBoringTextButton(
          text = "  " + (i18n(S.AccountNumAddresses) % mapOf("num" to stat.numUsedAddrs.toString())) + "  ",
          onClick = { onAddressesButtonClicked() }
        )

        WallyBoringTextButton(
          text = "  " +  (i18n(S.AccountNumTx) % mapOf("num" to stat.numTransactions.toString()))  + "  ",
          onClick = { onTxHistoryButtonClicked() }
        )

        WallyBoringTextButton(
          text = "  " + (i18n(S.AccountNumUtxos) % mapOf("num" to stat.numUnspentTxos.toString())) + "  ",
          onClick = { }
        )
    }
}

@Composable
fun AccountActions(acc: Account, txHistoryButtonClicked: () -> Unit, accountDeleted: () -> Unit)
{
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CenteredSectionText(i18n(S.AccountActions))
        AccountActionButtons(acc, txHistoryButtonClicked = txHistoryButtonClicked, accountDeleted)
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AccountActionButtons(acc: Account, txHistoryButtonClicked: () -> Unit, accountDeleted: () -> Unit)
{
    val accountAction: MutableState<AccountAction?> = remember { mutableStateOf(null) }
    var checked by remember { mutableStateOf(acc.flags and ACCOUNT_FLAG_REUSE_ADDRESSES == 0UL) }


    fun displayNoticePrimaryAccount(name: String)
    {
        displayNotice(i18n(S.primaryAccountSuccess) % mapOf("name" to name))
    }


    if (accountAction.value == null)
    {
        WallySwitch(checked, S.AutomaticNewAddress)
        {
            checked = it

            if (!checked)
                acc.flags = acc.flags or ACCOUNT_FLAG_REUSE_ADDRESSES
            else
                acc.flags = acc.flags and ACCOUNT_FLAG_REUSE_ADDRESSES.inv()
            launch {  // Can't be in UI thread
                acc.saveAccountFlags()
            }
        }

        WallyBoringMediumTextButton(S.SetChangePin) {
            accountAction.value = AccountAction.PinChange
        }
        WallyBoringMediumTextButton(S.ViewRecoveryPhrase) {
            accountAction.value = AccountAction.RecoveryPhrase
        }
        WallyBoringMediumTextButton(S.txHistoryButton, onClick = txHistoryButtonClicked)

        if(wallyApp?.nullablePrimaryAccount != acc)    // it not primary
            WallyBoringMediumTextButton(S.setAsPrimaryAccountButton) {
                accountAction.value = AccountAction.PrimaryAccount
            }
        WallyBoringMediumTextButton(S.assessUnconfirmed) {
            accountAction.value = AccountAction.Reassess
        }
        WallyBoringMediumTextButton(S.rediscoverWalletTx) {
            accountAction.value = AccountAction.Rediscover
        }
        WallyBoringMediumTextButton(S.rediscoverBlockchain) {
            accountAction.value = AccountAction.RediscoverBlockchain
        }
        WallyBoringMediumTextButton(S.deleteWalletAccount) {
            accountAction.value = AccountAction.Delete
        }
    }
    else
    {
        when(accountAction.value)
        {
            AccountAction.PinChange -> {
                AccountDetailChangePinView(acc,
                  {
                      displayError(it)
                  },
                  {
                      displayNotice(it)
                  },
                  {
                      accountAction.value = null
                  }
                )
            }
            AccountAction.RecoveryPhrase -> {
                RecoveryPhraseView(acc) {
                    accountAction.value = null
                }
            }
            AccountAction.Reassess -> AccountDetailAcceptDeclineTextView(
                S.reassessConfirmation
            ) { accepted ->
                accountAction.value = null
                if(accepted)
                    later {
                        try
                        {
                            // TODO while we don't have Rostrum (electrum) we can't reassess, so just forget them under the assumption that they will be confirmed and accounted for, or are bad.
                            // coin.wallet.reassessUnconfirmedTx()
                            acc.wallet.cleanUnconfirmed()
                            displayNotice(S.unconfAssessmentNotice)
                        }
                        catch (e: Exception)
                        {
                            displayError(e.message ?: e.toString())
                        }
                    }
            }
            AccountAction.RediscoverBlockchain -> AccountDetailAcceptDeclineTextView(S.rediscoverConfirmation) {
                if (it)
                {
                    later {
                        val bc = acc.wallet.blockchain
                        // If you reset the wallet first, it'll start rediscovering the existing blockchain before it gets reset.
                        bc.rediscover()
                        for (c in wallyApp!!.accounts)  // Rediscover tx for EVERY account using this blockchain
                        {
                            val act = c.value
                            if (act.wallet.blockchain == bc)
                                act.wallet.rediscover(true, true)
                        }
                    }
                    displayNotice(S.rediscoverNotice)
                }
                accountAction.value = null
            }
            AccountAction.Delete -> AccountDetailAcceptDeclineTextView(i18n(S.deleteConfirmation) % mapOf("accountName" to acc.name, "blockchain" to acc.currencyCode)) {
                if (it)
                {
                    wallyApp?.deleteAccount(acc)
                    displayNotice(S.accountDeleteNotice)
                    accountDeleted()
                }
                accountAction.value = null
            }
            AccountAction.Rediscover -> AccountDetailAcceptDeclineTextView(S.rediscoverConfirmation) {
                if (it)
                {
                    later {
                        acc.wallet.rediscover(true, false)
                        displayNotice(S.rediscoverNotice)
                    }
                }
                accountAction.value = null
            }
            AccountAction.PrimaryAccount -> AccountDetailAcceptDeclineTextView(S.primaryAccountConfirmation) {
                if (it)
                {
                    wallyApp?.primaryAccount = acc
                    displayNoticePrimaryAccount(acc.name)
                }
                accountAction.value = null
            }
            else -> {}
        }
    }
}

@Composable
fun AccountDetailChangePinView(acc: Account, displayError: (String) -> Unit, displayNotice: (Int) -> Unit, pinChangedOrCancelled: () -> Unit)
{
    var currentPinOk by remember { mutableStateOf(false) }
    var currentPin by remember { mutableStateOf("") }
    var newPinOk by remember { mutableStateOf(true) }
    var newPin by remember { mutableStateOf("") }
    var pinHidesAccount by remember { mutableStateOf((acc.flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN) > 0u) }

    WallySwitch(pinHidesAccount, S.PinHidesAccount)
    {
        pinHidesAccount = it
        if (it) acc.flags = acc.flags or ACCOUNT_FLAG_HIDE_UNTIL_PIN
        else acc.flags = (acc.flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN.inv())

        acc.saveAccountFlags()
    }

    if (acc.lockable)
    {

        AccountDetailPinInput(i18n(S.CurrentPin), i18n(S.EnterPIN), currentPin, currentPinOk) {
            if(it.onlyDigits())
            {
                currentPin = it

                if (it.length < 4)
                {
                    currentPinOk = false
                }
                currentPinOk = acc.submitAccountPin(it) != 0 // submitAccountPin returns 0 on wrong pin
            }
        }
        AccountDetailPinInput(i18n(S.NewPin), i18n(S.EnterPINorBlankToRemove), newPin, newPinOk) {
            if(it.onlyDigits())
            {
                newPin = it
                newPinOk = it.length >= 4 || it.isEmpty()
            }
        }
    }
    else  // No current PIN
    {
        AccountDetailPinInput(i18n(S.NewPin), i18n(S.EnterPINorBlankToRemove), newPin, newPinOk) {
            if(it.onlyDigits())
            {
                newPin = it
                newPinOk = it.length >= 4 || it.isEmpty()
            }
        }
    }

    fun processNewPin()
    {
        val name = acc.name
        if (newPin.length > 0 && newPin.length < 4)
        {
            displayError(i18n(S.PinTooShort))
        }
        else if (!newPin.onlyDigits())
        {
            displayError(i18n(S.PinInvalid))
        }
        else if (newPin.isNotEmpty())
        {
            val epin = EncodePIN(name, newPin)
            acc.encodedPin = epin
            displayNotice(S.PinChanged)
            later { acc.saveAccountPin(name, epin) }
            pinChangedOrCancelled()
        }
        else
        {
            acc.encodedPin = null
            later { acc.saveAccountPin(name, byteArrayOf()) }
            displayNotice(S.PinRemoved)
            pinChangedOrCancelled()
        }
    }

    WallyButtonRow {
        WallyBoringTextButton(S.accept) {
            clearAlerts()
            if (acc.lockable) // Replace pin
            {
                if (acc.submitAccountPin(currentPin) == 0) // submitAccountPin returns 0 on wrong pin
                    displayError(i18n(S.PinInvalid))
                else
                    processNewPin()
            }
            else if (!acc.lockable) // New pin
            {
                processNewPin()
            }
        }
        WallyBoringTextButton(S.cancel) {
            pinChangedOrCancelled()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AccountDetailPinInput(description: String, placeholder: String, currentPin: String, currentPinOk: Boolean, onPinChanged: (String) -> Unit)
{
    val focusManager = LocalFocusManager.current
    Column {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          horizontalArrangement = Arrangement.Start,
          verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPinOk)
                ResImageView(
                  "icons/check.xml",
                  modifier = Modifier
                    .size(24.dp),
                  i18n(S.confirm)
                )
            else
                ResImageView(
                  "icons/delete.png",
                  modifier = Modifier
                    .size(24.dp),
                  i18n(S.confirm)
                )
            Text(
              text = description,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier
                .width(100.dp)
                .wrapContentHeight()
            )
            TextField(
              value = currentPin,
              onValueChange = onPinChanged,
              singleLine = true,
              placeholder = { Text(placeholder) },
              keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
              ),
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
              ),
              // visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.padding(start = 8.dp, end = 8.dp).wrapContentWidth().wrapContentHeight().onKeyEvent {
                    val k = it.key
                    if ((k == androidx.compose.ui.input.key.Key.Enter)||(k == androidx.compose.ui.input.key.Key.NumPadEnter))
                    {
                        focusManager.moveFocus(FocusDirection.Next)
                        true
                    }
                    else false// do not accept this key
              },
            )
        }
    }
}

@Composable
fun RecoveryPhraseView(account: Account, done: () -> Unit)
{
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(i18n(S.recoveryPhrase), modifier = Modifier.padding(8.dp))
        val tmp = account.wallet.secretWords.split(" ")
        val halfWords:Int = tmp.size/2
        var copied by remember { mutableStateOf(false) }

        val mnemonic0 = tmp.subList(0,halfWords).joinToString(" ")
        val mnemonic1 = tmp.subList(halfWords, tmp.size).joinToString(" ")
        SelectionContainer {
            // This ensures that they fit on the line
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            )
            {
                CenteredFittedText(mnemonic0, fontWeight = FontWeight.Bold, color = Color.Blue, modifier =
                Modifier.clickable {
                    setTextClipboard(account.wallet.secretWords)
                    copied = true
                })
                CenteredFittedText(mnemonic1, fontWeight = FontWeight.Bold, color = Color.Blue, modifier =
                Modifier.clickable {
                    setTextClipboard(account.wallet.secretWords)
                    copied = true
                })
            }
            //Text(mnemonic, color = Color.Red, fontWeight = FontWeight.Bold)
        }
        WallyButtonRow {
            WallyBoringTextButton(S.WroteRecoveryPhraseDown) {
                account.flags = account.flags or ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY
                later { account.saveAccountFlags() }
                done()
            }
            WallyBoringTextButton(S.RecoveryPhraseKeepRemindingMe) {
                // User wants to be reminded to back up the key again
                account.flags = account.flags and ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY.inv()
                later { account.saveAccountFlags() }
                done()
            }
        }
        if (copied)
        {
            Text(i18n(S.PastingRecoveryPhraseIsBadIdea), color = Color.Red, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun AccountDetailAcceptDeclineRow(accept: (Boolean) -> Unit)
{
    WallyButtonRow {
        WallyBoringTextButton(S.accept) {
            accept(true)
        }
        WallyBoringTextButton(S.cancel) {
            accept(false)
        }
    }
}
@Composable
fun AccountDetailAcceptDeclineTextView(text: String, accept: (Boolean) -> Unit)
{
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text)

        AccountDetailAcceptDeclineRow {
            accept(it)
        }
    }
}
@Composable
fun AccountDetailAcceptDeclineTextView(textRes: Int, accept: (Boolean) -> Unit)
{
    AccountDetailAcceptDeclineTextView(i18n(textRes), accept)
}