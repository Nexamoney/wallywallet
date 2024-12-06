package info.bitcoinunlimited.www.wally.uiv2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.aborter
import info.bitcoinunlimited.www.wally.ui.rediscoverPrehistoryHeight
import info.bitcoinunlimited.www.wally.ui.rediscoverPrehistoryTime
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import info.bitcoinunlimited.www.wally.ui2.noSelectedAccount
import info.bitcoinunlimited.www.wally.ui2.selectedAccountUi2
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallySwitchRowUi2
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.*

data class AccountStatistics(
  val chainState: GlueWalletBlockchain?,
  val stat: Wallet.WalletStatistics,
  val synced: Int = if (chainState?.isSynchronized() == true) S.synced else S.unsynced,
  val chainName: String = chainState?.chain?.name ?: "",
  val syncStatus: String = i18n(S.AccountBlockchainSync) % mapOf(
    "sync" to i18n(synced),
    "chain" to chainName,
  ),
  val latestBlockTimeHeight: String = if (chainState != null)
  {
      i18n(S.AccountBlockchainDetails) % mapOf(
        "actBlock" to chainState.syncedHeight.toString(),
        "actBlockDate" to epochToDate(chainState.syncedDate),
        "chainBlockCount" to chainState.chain.curHeight.toString()
      )
  }
  else
      "",
  val preHistory: String = if (chainState != null)
  {
      i18n(S.AccountEarliestActivity) % mapOf(
        "actPrehistoryBlock" to chainState.prehistoryHeight.toString(),
        "actPrehistoryDate" to epochToDate(chainState.prehistoryDate)
      )
  }
  else
      "",
  val prehistory: String = if (chainState != null)
      i18n(S.AccountEarliestActivity) % mapOf(
        "actPrehistoryBlock" to chainState.prehistoryHeight.toString(),
        "actPrehistoryDate" to epochToDate(chainState.prehistoryDate)
      )
  else
      "",
  val peerCountNames: String = if (chainState != null)
  {
      val cnxnLst = chainState.chain.net.mapConnections { it.name }
      val trying:List<String> = if (chainState.chain.net is MultiNodeCnxnMgr) (chainState.chain.net as MultiNodeCnxnMgr).initializingCnxns().map { it.name } else listOf()
      val peers = cnxnLst.joinToString(", ") + if (trying.isNotEmpty()) (" " + i18n(S.trying) + " " + trying.joinToString(", ")) else ""
      i18n(S.AccountBlockchainConnectionDetails) % mapOf(
        "num" to cnxnLst.size.toString(),
        "names" to peers
      )
  }
  else
      "",
  val firstLastSend: String = if (stat.lastSendHeight > 0L) i18n(S.FirstLastSend) % mapOf(
    "first" to (if (stat.firstSendHeight == Long.MAX_VALUE) "never" else stat.firstSendHeight.toString()),
    "last" to (if (stat.lastSendHeight==0L) "never" else stat.lastSendHeight.toString()))
  else
      i18n(S.FirstWithdraw) + " " + i18n(S.never),
  val firstLastReceive: String = if (stat.lastReceiveHeight > 0L) i18n(S.FirstLastReceive) % mapOf(
    "first" to (if (stat.firstReceiveHeight == Long.MAX_VALUE) "never" else stat.firstReceiveHeight.toString()),
    "last" to (if (stat.lastReceiveHeight == 0L) "never" else stat.lastReceiveHeight.toString()))
  else
      i18n(S.FirstDeposit) + " " + i18n(S.never)
)

class AccountStatisticsViewModel(): ViewModel() {
    val accountStats = MutableStateFlow<AccountStatistics?>(null)
    // We need to promote some blocking-access data to globals so we can launch threads to load them
    val curAddressText = MutableStateFlow<String>("")
    var accountDetailAccount: Account? = null
    var accountJob: Job? = null

    init {
        selectedAccountUi2.value?.let {
            updateStats(it)
            fetchCurAddressText(it)
        }
        observeSelectedAccount()
    }

    private fun observeSelectedAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            selectedAccountUi2.onEach { selectedAccount ->
                selectedAccount?.let {
                    updateStats(selectedAccount)
                    fetchCurAddressText(selectedAccount)
                }
            }.launchIn(this)
        }
    }

    fun updateStats(account: Account)
    {
        val chainState = account.wallet.chainstate
        val stats = account.wallet.statistics()
        accountStats.value = AccountStatistics(chainState, stats)
    }

    fun fetchCurAddressText(account: Account)
    {
        if (account != accountDetailAccount)
        {
            accountDetailAccount = account
            curAddressText.value = ""  // Account changed so clear this pending a reload
            laterJob {
                // this is potentially blocking because it ensures that the address is installed in the Bloom filter before its handed out
                val curDest = account.wallet.getCurrentDestination()
                curAddressText.value = i18n(S.CurrentAddress) % mapOf("num" to curDest.index.toString(), "addr" to curDest.address.toString())
            }
        }
    }

    override fun onCleared()
    {
        super.onCleared()
        accountJob?.cancel()
    }
}

@Composable
fun AccountDetailScreenUi2(account: Account)
{
    val viewModel = viewModel { AccountStatisticsViewModel() }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Spacer(Modifier.height(16.dp))
        AccountPill(buttonsEnabled = true)
        Spacer(Modifier.height(2.dp))
        Spacer(modifier = Modifier.height(4.dp))
        AccountActionButtonsUi2(account, txHistoryButtonClicked = { nav.go(ScreenId.TxHistory) }, accountDeleted = {
            nav.back()
            triggerAssignAccountsGuiSlots()
        })
        Spacer(modifier = Modifier.height(4.dp))
        WallyDivider()
        TxStatistics(viewModel, { nav.go(ScreenId.AddressHistory) }, { nav.go(ScreenId.TxHistory) })
        Spacer(modifier = Modifier.height(4.dp))
        AccountStatisticsCard(viewModel)
    }
}

@Composable
fun AccountStatisticsCard(viewModel: AccountStatisticsViewModel)
{
    val accountStats = viewModel.accountStats.collectAsState().value

    if (accountStats != null)
        Card(
          modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = Color.White,
          ),
          shape = RoundedCornerShape(12.dp),
          elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
              modifier = Modifier.padding(16.dp)
            ) {
                Text(i18n(S.AccountStatistics), style = MaterialTheme.typography.headlineMedium,textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Public,
                      contentDescription = "Blockchain status",
                      modifier = Modifier.size(24.dp),
                      tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = accountStats.syncStatus,
                      style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                      imageVector = Icons.Default.DateRange,
                      contentDescription = "Date",
                      modifier = Modifier.size(24.dp),
                      tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                          text = accountStats.latestBlockTimeHeight,
                          style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                          text = accountStats.prehistory,
                          style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.NetworkCheck,
                      contentDescription = "Nodes",
                      modifier = Modifier.size(24.dp),
                      tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = accountStats.peerCountNames,
                      style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                      imageVector = Icons.Default.AlternateEmail,
                      contentDescription = "Address",
                      modifier = Modifier.size(24.dp),
                      tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = viewModel.curAddressText.collectAsState().value,
                      style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                      imageVector = Icons.Default.ArrowUpward,
                      contentDescription = "Withdraw",
                      modifier = Modifier.size(24.dp),
                      tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = accountStats.firstLastSend,
                      style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                      imageVector = Icons.Default.ArrowDownward,
                      contentDescription = "Deposit",
                      modifier = Modifier.size(24.dp),
                      tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = accountStats.firstLastReceive,
                      style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
}

@Composable
fun AccountFirstLastSendIterati(stat: Wallet.WalletStatistics)
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
fun TxStatistics(viewModel: AccountStatisticsViewModel, onAddressesButtonClicked: () -> Unit, onTxHistoryButtonClicked: () -> Unit)
{
    val stat = viewModel.accountStats.value?.stat

    if (stat != null)
        Column {
            Text("  " + (i18n(S.AccountNumUtxos) % mapOf("num" to stat.numUnspentTxos.toString())) + "  ", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(
              modifier = Modifier.padding(0.dp).fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                  content = { Text("  " + (i18n(S.AccountNumAddresses) % mapOf("num" to stat.numUsedAddrs.toString())) + "  ", color = Color.White) },
                  onClick = { onAddressesButtonClicked() }
                )

                Button(
                  content = { Text("  " +  (i18n(S.AccountNumTx) % mapOf("num" to stat.numTransactions.toString()))  + "  ", color = Color.White) },
                  onClick = { onTxHistoryButtonClicked() }
                )
            }
        }
}

@Composable
fun AccountActionButtonsUi2(acc: Account, txHistoryButtonClicked: () -> Unit, accountDeleted: () -> Unit)
{
    val accountAction: MutableState<AccountAction?> = remember { mutableStateOf(null) }
    var checked by remember { mutableStateOf(acc.flags and ACCOUNT_FLAG_REUSE_ADDRESSES == 0UL) }

    fun displayNoticePrimaryAccount(name: String)
    {
        displayNotice(i18n(S.primaryAccountSuccess) % mapOf("name" to name))
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (accountAction.value == null)
        {
            WallySwitchRowUi2(checked, S.AutomaticNewAddress)
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

            fun rediscoverWalletTx(aa: AccountAction)
            {
                // Launch a thread to find when the wallet was first used whenever this button is clicked
                val wal = acc.wallet
                val state = wal.chainstate
                if (state != null)
                {
                    rediscoverPrehistoryTime.value = state.prehistoryDate
                    rediscoverPrehistoryHeight.value = state.prehistoryHeight
                    laterJob {
                        aborter.obj = true  // abort any old searches
                        aborter = Objectify<Boolean>(false)
                        val ret = rediscoverPeekActivity(wal.secretWords, wal.chainSelector, aborter)
                        if (ret != null)
                        {
                            val (time, height) = ret

                            state.prehistoryDate = time - (30*60)
                            rediscoverPrehistoryTime.value = state.prehistoryDate
                            state.prehistoryHeight = height.toLong() - 1
                            rediscoverPrehistoryHeight.value = state.prehistoryHeight
                        }
                    }
                }

                accountAction.value = aa
            }

            val mod = Modifier.fillMaxWidth(0.90f)
            OutlinedButton(content = {Text(i18n(S.txHistoryButton))}, onClick = txHistoryButtonClicked, modifier = mod)
            OutlinedButton(content = {Text(i18n(S.SetChangePin))}, onClick = { accountAction.value =
                AccountAction.PinChange
            }, modifier = mod)
            if(wallyApp?.nullablePrimaryAccount != acc)    // it not primary
                OutlinedButton(content = {Text(i18n(S.setAsPrimaryAccountButton))}, onClick = { accountAction.value =
                    AccountAction.PrimaryAccount
                }, modifier = mod)
            OutlinedButton(content = {Text(i18n(S.assessUnconfirmed))}, onClick = { accountAction.value =
                AccountAction.Reassess
            }, modifier = mod)
            OutlinedButton(content = {Text(i18n(S.searchWalletTx))}, onClick = { rediscoverWalletTx(AccountAction.Search) }, modifier = mod)
            OutlinedButton(content = {Text(i18n(S.ViewRecoveryPhrase))}, onClick = { accountAction.value =
                AccountAction.RecoveryPhrase
            }, modifier = mod)
            OutlinedButton(content = {Text(i18n(S.deleteWalletAccount))}, onClick = { accountAction.value =
                AccountAction.Delete
            }, modifier = mod)

            if (devMode)
            {
                OutlinedButton(content = {Text(i18n(S.rediscoverWalletTx))}, onClick = { rediscoverWalletTx(AccountAction.Rediscover) }, modifier = mod)
                OutlinedButton(content = {Text(i18n(S.rediscoverBlockchain))}, onClick = { accountAction.value =
                    AccountAction.RediscoverBlockchain
                }, modifier = mod)
                /*  Messes up the account prehistory to see if rediscover properly corrects it
                WallyBoringTextButton("DEV: randomize prehistory") {
                    acc.wallet.chainstate?.prehistoryHeight = Random.nextLong(-10L, 100000L)
                    acc.wallet.chainstate?.prehistoryDate = 0
                }
                 */
            }
        }
        else
        {
            when(accountAction.value)
            {
                AccountAction.PinChange ->
                {
                    AccountDetailChangePinViewUi2(acc,
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

                AccountAction.RecoveryPhrase ->
                {
                    RecoveryPhraseViewUi2(acc) {
                        accountAction.value = null
                    }
                }

                AccountAction.Reassess -> AccountDetailAcceptDeclineTextViewUi2(i18n(S.reassessConfirmation)) { accepted ->
                    accountAction.value = null
                    if (accepted)
                        tlater("cleanUnconfirmed") {
                            try
                            {
                                // TODO while we don't have Rostrum (electrum) we can't reassess, so just forget them under the assumption that they will be confirmed and accounted for, or are bad.
                                // coin.wallet.reassessUnconfirmedTx()
                                acc.wallet.cleanUnconfirmed()
                                acc.wallet.cleanReserved()
                                displayNotice(S.unconfAssessmentNotice)
                            }
                            catch (e: Exception)
                            {
                                displayError(e.message ?: e.toString())
                            }
                        }
                }

                AccountAction.RediscoverBlockchain ->
                {
                    AccountDetailAcceptDeclineTextViewUi2(i18n(S.rediscoverConfirmation)) {
                        if (it)
                        {
                            tlater("rediscoverBlockchain") {
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
                }

                AccountAction.Delete -> AccountDetailAcceptDeclineTextViewUi2(i18n(S.deleteConfirmation) % mapOf("accountName" to acc.name, "blockchain" to acc.currencyCode)) {
                    if (it)
                    {
                        wallyApp!!.deleteAccount(acc)
                        displayNotice(S.accountDeleteNotice)
                        accountDeleted()
                        noSelectedAccount()  // If we are in the account details, this account is selected.  We need to unselect it.
                    }
                    accountAction.value = null
                }

                AccountAction.Rediscover ->
                {
                    val wal = acc.wallet
                    val state = wal.chainstate
                    if (state != null)
                    {
                        val dateString = epochToDate(rediscoverPrehistoryTime.collectAsState().value)
                        Spacer(Modifier.height(8.dp))
                        Text(i18n(S.FirstUse) % mapOf("date" to dateString) )
                        Text(i18n(S.Block) % mapOf("block" to rediscoverPrehistoryHeight.collectAsState().value.toString()))
                        Spacer(Modifier.height(8.dp))
                    }
                    AccountDetailAcceptDeclineTextViewUi2(i18n(S.rediscoverConfirmation)) {
                        if (it)
                        {
                            tlater("rediscover") {
                                // Choosing to not forget the addresses is kind of cheating, but there is an issue that is hard to resolve with very busy wallets
                                // where a chunk of addresses is consumed in a single block, preventing the bloom filter to be updated to the new addresses
                                // for that block.  This can cause transactions to be missed.  By keeping addresses around, repeated rediscovers find all the transactions.
                                acc.wallet.rediscover(false, false, true)
                                displayNotice(S.rediscoverNotice)
                            }
                        }
                        accountAction.value = null
                    }
                }
                AccountAction.Search ->
                {
                    val wal = acc.wallet
                    val state = wal.chainstate
                    if (state != null)
                    {
                        val dateString = epochToDate(rediscoverPrehistoryTime.collectAsState().value)
                        Spacer(Modifier.height(8.dp))
                        Text(i18n(S.FirstUse) % mapOf("date" to dateString) )
                        Text(i18n(S.Block) % mapOf("block" to rediscoverPrehistoryHeight.collectAsState().value.toString()))
                        Spacer(Modifier.height(8.dp))
                    }
                    AccountDetailAcceptDeclineTextViewUi2(i18n(S.searchConfirmation)) {
                        if (it)
                        {
                            tlater("search") {
                                // Choosing to not forget the addresses is kind of cheating, but there is an issue that is hard to resolve with very busy wallets
                                // where a chunk of addresses is consumed in a single block, preventing the bloom filter to be updated to the new addresses
                                // for that block.  This can cause transactions to be missed.  By keeping addresses around, repeated rediscovers find all the transactions.
                                acc.wallet.rediscover(false, false, false)
                                displayNotice(S.searchNotice)
                            }
                        }
                        accountAction.value = null
                    }
                }

                AccountAction.PrimaryAccount -> AccountDetailAcceptDeclineTextViewUi2(i18n(S.primaryAccountConfirmation)) {
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

}

@Composable
fun AccountDetailChangePinViewUi2(acc: Account, displayError: (String) -> Unit, displayNotice: (Int) -> Unit, pinChangedOrCancelled: () -> Unit)
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

        AccountDetailPinInputUi2(i18n(S.CurrentPin), i18n(S.EnterPIN), currentPin, currentPinOk) {
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
        AccountDetailPinInputUi2(i18n(S.NewPin), i18n(S.EnterPINorBlankToRemove), newPin, newPinOk) {
            if(it.onlyDigits())
            {
                newPin = it
                newPinOk = it.length >= 4 || it.isEmpty()
            }
        }
    }
    else  // No current PIN
    {
        AccountDetailPinInputUi2(i18n(S.NewPin), i18n(S.EnterPINorBlankToRemove), newPin, newPinOk) {
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
            tlater("savePin") { acc.saveAccountPin(epin) }
            pinChangedOrCancelled()
        }
        else
        {
            acc.encodedPin = null
            tlater("savePin") { acc.saveAccountPin(byteArrayOf()) }
            displayNotice(S.PinRemoved)
            pinChangedOrCancelled()
        }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
          onClick = {
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
          },
          content = { Text(i18n(S.accept))
          })
        Button(
          onClick = { pinChangedOrCancelled() },
          content = { Text(i18n(S.cancel)) }
        )
    }
}

@Composable
fun AccountDetailPinInputUi2(description: String, placeholder: String, currentPin: String, currentPinOk: Boolean, onPinChanged: (String) -> Unit)
{
    val focusManager = LocalFocusManager.current
    Column {
        Row(
          modifier = Modifier.fillMaxWidth()
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
fun RecoveryPhraseViewUi2(account: Account, done: () -> Unit)
{
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(i18n(S.recoveryPhrase), modifier = Modifier.padding(8.dp))
        var copied by remember { mutableStateOf(false) }

        val clickable = Modifier.clickable {
            setTextClipboard(account.wallet.secretWords)
            copied = true
        }
        SelectionContainer {
            Card(
              modifier = clickable.fillMaxWidth()
                .padding(vertical = 16.dp),
              elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                  modifier = clickable
                    .padding(16.dp)
                ) {
                    Text(
                      text = account.wallet.secretWords,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 18.sp,
                      modifier = clickable.padding(vertical = 2.dp)
                    )
                }
            }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
              content = { Text(i18n(S.WroteRecoveryPhraseDown)) },
              onClick = {
                  account.flags = account.flags or ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY
                  tlater("saveAccountFlags") { account.saveAccountFlags() }
                  done()
              }
            )
            Button(
              content = { Text(i18n(S.RecoveryPhraseKeepRemindingMe)) },
              onClick = {
                  // User wants to be reminded to back up the key again
                  account.flags = account.flags and ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY.inv()
                  tlater("saveAccountFlags") { account.saveAccountFlags() }
                  done()
              }
            )
        }
        if (copied)
        {
            Text(i18n(S.PastingRecoveryPhraseIsBadIdea), color = Color.Red, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun AccountDetailAcceptDeclineTextViewUi2(text: String, accept: (Boolean) -> Unit)
{
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
              onClick = { accept(true)},
              content = { Text(i18n(S.accept)) }
            )
            Button(
              onClick = { accept(false) },
              content = { Text(i18n(S.cancel)) }
            )
        }
    }
}