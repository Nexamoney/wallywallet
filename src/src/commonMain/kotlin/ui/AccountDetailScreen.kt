package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.NoticeText
import info.bitcoinunlimited.www.wally.ui.theme.WallyBoringTextButton
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui.theme.WallySwitch
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*

enum class AccountAction
{
    Delete, Rediscover, RediscoverBlockchain, Reassess, RecoveryPhrase, PrimaryAccount, PinChange
}
enum class AccountNav
{
    AccountDetail, TxHistory, AddressHistory
}

@Composable
fun AccountDetailScreenNav(nav: ChildNav, account: Account, allAccounts: List<Account>)
{
    var accountNav by remember { mutableStateOf(AccountNav.AccountDetail) }

    when (accountNav)
    {
        AccountNav.AccountDetail -> {
            AccountDetailScreen(account, allAccounts, onTxHistoryButtonClicked = {
                accountNav = AccountNav.AddressHistory
                },
                onAddressesButtonClicked = {
                    accountNav = AccountNav.AddressHistory
                },
                onBackButton = {
                    nav.displayAccount(null)
                }
            )
        }
        AccountNav.TxHistory -> TxHistoryScreen(account) {
            accountNav = AccountNav.AccountDetail
        }
        AccountNav.AddressHistory -> AddressHistoryScreen(account) {
            accountNav = AccountNav.AccountDetail
        }
    }
}

@Composable
fun AccountDetailScreen(account: Account, allAccounts: List<Account>, onTxHistoryButtonClicked: () -> Unit, onAddressesButtonClicked: () -> Unit, onBackButton: () -> Unit)
{
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBackButton) {
            Icon(Icons.Default.ArrowBack, contentDescription = i18n(S.title_home))
        }
        AccountStatisticsHeader()
        AccountBlockchainDetail(account)
        AccountFirstLastSend(account.wallet.statistics())
        GuiAccountTxStatisticsRow(account.wallet.statistics(), onTxHistoryButtonClicked, onAddressesButtonClicked)
        Spacer(modifier = Modifier.padding(4.dp))
        WallyDivider()
        AccountActions(account, onTxHistoryButtonClicked, allAccounts, accountDeleted = onBackButton)
    }
}

@Composable
fun AccountStatisticsHeader() {
    Text(
      text = i18n(S.AccountStatistics), // replace with your string resource
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )
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
    Text(text)
}

@Composable
fun AccountBlockchainBlockDetails(chainState: GlueWalletBlockchain)
{
    val text = i18n(S.AccountBlockchainDetails) % mapOf(
      "actBlock" to chainState.syncedHeight.toString(),
      "actBlockDate" to epochToDate(chainState.syncedDate),
      "chainBlockCount" to chainState.chain.curHeight.toString()
    )

    Text(
      text = text,
      fontSize = 16.sp,
      fontWeight = FontWeight.Normal,
      textAlign = TextAlign.Center,
      maxLines = 2
    )
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
    Text(text)
}

@Composable
fun GuiAccountTxStatisticsRow(stat: CommonWallet.WalletStatistics, onAddressesButtonClicked: () -> Unit, onTxHistoryButtonClicked: () -> Unit)
{
    Row(
      modifier = Modifier.padding(horizontal = 2.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
        WallyBoringTextButton(
          text = "  " + (i18n(S.AccountNumAddresses) % mapOf("num" to stat.numUsedAddrs.toString())) + "  ",
          onClick = { onAddressesButtonClicked() }
        )

        Spacer(Modifier.width(4.dp))

        WallyBoringTextButton(
          text = "  " +  (i18n(S.AccountNumTx) % mapOf("num" to stat.numTransactions.toString()))  + "  ",
          onClick = { onTxHistoryButtonClicked() }
        )

        Spacer(Modifier.width(4.dp))

        WallyBoringTextButton(
          text = "  " + (i18n(S.AccountNumUtxos) % mapOf("num" to stat.numUnspentTxos.toString())) + "  ",
          onClick = { }
        )
    }
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
    Text(firstLastSend)
    Text(firstLastReceive)
}

@Composable
fun AccountActions(acc: Account, txHistoryButtonClicked: () -> Unit, allAccounts: List<Account>, accountDeleted: () -> Unit)
{

    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var checked by remember { mutableStateOf(acc.flags and ACCOUNT_FLAG_REUSE_ADDRESSES == 0UL) }

        Text(text = i18n(S.AccountActions),modifier = Modifier.align(Alignment.CenterHorizontally), fontSize = 24.sp, fontWeight = FontWeight.Bold)
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

        AccountActionButtons(acc, txHistoryButtonClicked = txHistoryButtonClicked, allAccounts, accountDeleted)
    }
}

@Composable
fun ErrorText(errorText: String)
{
    Box(
      modifier = Modifier
        .background(color = Color.Red)
        .fillMaxWidth()
        .padding(16.dp)
        .wrapContentWidth(align = Alignment.CenterHorizontally)
    ) {
        Text(
          text = errorText,
          style = LocalTextStyle.current.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold
          )
        )
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AccountActionButtons(acc: Account, txHistoryButtonClicked: () -> Unit, allAccounts: List<Account>, accountDeleted: () -> Unit)
{
    val accountAction: MutableState<AccountAction?> = remember { mutableStateOf(null) }
    var errorText by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    if (errorText.isNotEmpty())
        ErrorText(errorText)
    if (notice.isNotEmpty())
        NoticeText(notice)


    fun displayNotice(text: String)
    {
        notice = text
        GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
            delay(NORMAL_NOTICE_DISPLAY_TIME)  // Delay of 5 seconds
            withContext(Dispatchers.Default + exceptionHandler) {
                notice = ""
            }
        }
    }

    fun displayNotice(textRes: Int)
    {
        notice = i18n(textRes)
        displayNotice(text = notice)
    }

    fun displayNoticePrimaryAccount(name: String)
    {
        notice = i18n(S.primaryAccountSuccess) % mapOf("name" to name)
        displayNotice(text = notice)
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

    if (accountAction.value == null)
    {
        WallyBoringTextButton(S.SetChangePin) {
            accountAction.value = AccountAction.PinChange
        }
        WallyBoringTextButton(S.ViewRecoveryPhrase) {
            accountAction.value = AccountAction.RecoveryPhrase
        }
        WallyBoringTextButton(S.txHistoryButton, onClick = txHistoryButtonClicked)
        if(wallyApp?.nullablePrimaryAccount != acc)    // it not primary
            WallyBoringTextButton(S.setAsPrimaryAccountButton) {
                accountAction.value = AccountAction.PrimaryAccount
            }
        WallyBoringTextButton(S.assessUnconfirmed) {
            accountAction.value = AccountAction.Reassess
        }
        WallyBoringTextButton(S.rediscoverWalletTx) {
            accountAction.value = AccountAction.Rediscover
        }
        WallyBoringTextButton(S.rediscoverBlockchain) {
            accountAction.value = AccountAction.RediscoverBlockchain
        }
        WallyBoringTextButton(S.deleteWalletAccount) {
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
                    launch {
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
                launch {
                    val bc = acc.wallet.blockchain
                    // If you reset the wallet first, it'll start rediscovering the existing blockchain before it gets reset.
                    bc.rediscover()
                    for (c in allAccounts)  // Rediscover tx for EVERY wallet using this blockchain
                    {
                        if (c.wallet.blockchain == bc)
                            c.wallet.rediscover(true, true)
                    }
                }
                displayNotice(S.rediscoverNotice)
            }
            AccountAction.Delete -> AccountDetailAcceptDeclineTextView(i18n(S.deleteConfirmation) % mapOf("accountName" to acc.name, "blockchain" to acc.currencyCode)) {

                wallyApp?.deleteAccount(acc)
                wallyApp?.accounts?.remove(acc.name)  // remove this coin from any global access before we delete it
                acc.wallet.stop()
                launch {
                    wallyApp?.saveActiveAccountList()
                }
                displayNotice(S.accountDeleteNotice)
                accountDeleted()
            }
            AccountAction.Rediscover -> AccountDetailAcceptDeclineTextView(S.rediscoverConfirmation) {
                launch {
                    acc.wallet.rediscover(true, false)
                    displayNotice(S.rediscoverNotice)
                }
            }
            AccountAction.PrimaryAccount -> AccountDetailAcceptDeclineTextView(S.primaryAccountConfirmation) {
                wallyApp?.primaryAccount = acc
                displayNoticePrimaryAccount(acc.name)
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

    if (acc.lockable)
    {
        AccountDetailPinInput(i18n(S.CurrentPin), i18n(S.EnterPIN), currentPin, currentPinOk) {
            currentPin = it

            if (it.length < 4)
            {
                currentPinOk = false
            }
            currentPinOk = acc.submitAccountPin(it) != 0 // submitAccountPin returns 0 on wrong pin
        }
        AccountDetailPinInput(i18n(S.NewPin), i18n(S.EnterPINorBlankToRemove), newPin, newPinOk) {
            newPin = it
            newPinOk = it.length >= 4 || it.isEmpty()
        }
    }
    else  // No current PIN
    {
        AccountDetailPinInput(i18n(S.NewPin), i18n(S.EnterPINorBlankToRemove), newPin, newPinOk) {
            newPin = it
            newPinOk = it.length >= 4 || it.isEmpty()
        }
    }

    fun processNewPin()
    {
        val name = acc.name
        if (newPin.length in 2..3)
        {
            displayError(i18n(S.PinTooShort))
        }
        else if (newPin.isNotEmpty())
        {
            val epin = EncodePIN(name, newPin)
            acc.encodedPin = epin
            displayNotice(S.PinChanged)
            later { SaveAccountPin(name, epin) }
            pinChangedOrCancelled()
        }
        else
        {
            acc.encodedPin = null
            later { SaveAccountPin(name, byteArrayOf()) }
            displayNotice(S.PinRemoved)
            pinChangedOrCancelled()
        }
    }

    Row {
        WallyBoringTextButton(S.accept) {
            if (acc.lockable) // Replace pin
            {
                if (currentPin.length < 4)
                {
                    displayError(i18n(S.PinTooShort))
                }
                else if (acc.submitAccountPin(currentPin) == 0) // submitAccountPin returns 0 on wrong pin
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

@Composable
fun AccountDetailPinInput(description: String, placeholder: String, currentPin: String, currentPinOk: Boolean, onPinChanged: (String) -> Unit)
{
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
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier
                .padding(start = 8.dp, end = 8.dp)
                .wrapContentWidth()
                .wrapContentHeight()
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
        Text(i18n(S.recoveryPhrase))
        val tmp = account.wallet.secretWords.split(" ")
        val halfWords:Int = tmp.size/2
        val mnemonic = tmp.subList(0,halfWords).joinToString(" ") + "\n" + tmp.subList(halfWords, tmp.size).joinToString(" ")
        Text(mnemonic, color = Color.Red, fontWeight = FontWeight.Bold)
        AccountDetailAcceptDeclineRow {
            if (it)
            {
                // User wants to be reminded to back up the key again
                account.flags = account.flags and ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY.inv()
                later { account.saveAccountFlags() }
            }
            done()
        }
    }
}

@Composable
fun AccountDetailAcceptDeclineRow(accept: (Boolean) -> Unit)
{
    Row {
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