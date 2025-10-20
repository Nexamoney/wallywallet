package info.bitcoinunlimited.www.wally.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui.views.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.*
import org.nexa.threads.millisleep

internal val rediscoverPrehistoryHeight = MutableStateFlow(0L)
internal val rediscoverPrehistoryTime = MutableStateFlow(0L)
internal var aborter = Objectify<Boolean>(false)

// We need to promote some blocking-access data to globals so we can launch threads to load them
var curAddressText = MutableStateFlow<String>("")
var accountDetailAccount:Account? = null

enum class AccountAction
{
    Delete, Search, Rediscover, RediscoverBlockchain, Reassess, RecoveryPhrase, PrimaryAccount, PinChange
}

private val LogIt = GetLog("wally.ui.AccountDetailScreen")

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

fun rediscoverPeekActivity(secretWords: String, chainSelector: ChainSelector, aborter: Objectify<Boolean>): Pair<Long, Int>?
{
    val net = connectBlockchain(chainSelector).net
    var ec = retry(10) {
        val ec = net?.getElectrum()
        if (ec == null) millisleep(1000U)
        ec
    }

    try
    {
        if (aborter.obj) return null
        val passphrase = "" // TODO: support a passphrase
        val secret = generateBip39Seed(secretWords, passphrase)
        val addressDerivationCoin = Bip44AddressDerivationByChain(chainSelector)

        var earliestActivityP =
          searchFirstActivity({
              if (ec.open) return@searchFirstActivity ec
              ec = net.getElectrum()
              return@searchFirstActivity (ec)
          }, chainSelector, 10, {
              libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, addressDerivationCoin, 0, false, it).first
          }, { time, height ->
              true
          })
        return earliestActivityP
    }
    finally
    {
        net?.returnElectrum(ec)
    }
}

open class AccountStatisticsViewModel(val account:MutableStateFlow<Account?>) : ViewModel()
{
    constructor(act: Account) : this(MutableStateFlow(act))

    val accountStats = MutableStateFlow<AccountStatistics?>(null)
    val curAddressText = MutableStateFlow<String>("")
    private var accountJob: Job? = null

    init {
        account.value?.let {
            updateStats(it)
            fetchCurAddressText(it)
        }
        observeAccount()
    }

    protected open fun observeAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch(
          Dispatchers.Default + CoroutineExceptionHandler { context, throwable ->
              LogIt.error(context.toString())
              LogIt.error(throwable.toString())
          }
        ) {
            account.onEach { selectedAccount ->
                selectedAccount?.let {
                    updateStats(selectedAccount)
                    fetchCurAddressText(selectedAccount)
                }
            }.launchIn(this)
        }
    }

    protected fun updateStats(account: Account)
    {
        val chainState = account.wallet.chainstate
        val stats = account.wallet.statistics()
        accountStats.value = AccountStatistics(chainState, stats)
    }

    protected fun fetchCurAddressText(account: Account)  // : Account)
    {
        curAddressText.value = ""  // Account changed so clear this pending a reload
        laterJob {
            val curDest = account.wallet.getCurrentDestination()
            curAddressText.value = i18n(S.CurrentAddress) % mapOf(
                  "num" to curDest.index.toString(),
                  "addr" to curDest.address.toString()
                )
        }
    }

    override fun onCleared()
    {
        super.onCleared()
        accountJob?.cancel()
    }
}

class AccountStatisticsViewModelFake(act: Account) : AccountStatisticsViewModel(act)
{
    override fun observeAccount()
    {
        // Do nothing or else the UI test fails...
    }
}

@Composable fun AccountDetailScreen(account: Account)
{
    AccountDetailScreen(AccountStatisticsViewModel(account))
}

@Composable fun AccountDetailScreen(accountStatsViewModel: AccountStatisticsViewModel)
{
    val account = accountStatsViewModel.account
    val act = account.collectAsState().value
    if (act == null) nav.back()  // no account to show the details of
    else  // we have an account
    {
        val scrollState = rememberScrollState()
        val ap = AccountPill(account)

        Column(modifier = Modifier.verticalScroll(scrollState)) {
            Spacer(Modifier.height(16.dp))
            ap.draw(buttonsEnabled = true)
            Spacer(Modifier.height(2.dp))
            Spacer(modifier = Modifier.height(4.dp))
            AccountActionButtons(act, txHistoryButtonClicked = { nav.go(ScreenId.TxHistory) }, accountDeleted = {
                nav.back()
                triggerAssignAccountsGuiSlots()
            })
            Spacer(modifier = Modifier.height(4.dp))
            WallyDivider()
            TxStatistics(accountStatsViewModel, { nav.go(ScreenId.AddressHistory) }, { nav.go(ScreenId.TxHistory) })
            Spacer(modifier = Modifier.height(4.dp))
            AccountStatisticsCard(accountStatsViewModel)
        }
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
                Text(i18n(S.AccountStatistics), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
      "last" to (if (stat.lastSendHeight == 0L) "never" else stat.lastSendHeight.toString()))
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
    val stat = viewModel.accountStats.collectAsState().value?.stat

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
                  content = { Text("  " + (i18n(S.AccountNumTx) % mapOf("num" to stat.numTransactions.toString())) + "  ", color = Color.White) },
                  onClick = { onTxHistoryButtonClicked() }
                )
            }
        }
}

@Composable
fun AccountActionButtons(acc: Account, txHistoryButtonClicked: () -> Unit, accountDeleted: () -> Unit)
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
            WallySwitchRow(checked, S.AutomaticNewAddress, "addressPrivacy")
            {
                checked = it

                if (!checked)
                    acc.flags = acc.flags or ACCOUNT_FLAG_REUSE_ADDRESSES
                else
                    acc.flags = acc.flags and ACCOUNT_FLAG_REUSE_ADDRESSES.inv()
                laterJob {  // Can't be in UI thread
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
                        val ret = rediscoverPeekActivity(wal.secretWords.getSecret().decodeUtf8(), wal.chainSelector, aborter)
                        if (ret != null)
                        {
                            val (time, height) = ret

                            state.prehistoryDate = time - (30 * 60)
                            rediscoverPrehistoryTime.value = state.prehistoryDate
                            state.prehistoryHeight = height.toLong() - 1
                            rediscoverPrehistoryHeight.value = state.prehistoryHeight
                        }
                    }
                }

                accountAction.value = aa
            }

            val mod = Modifier.fillMaxWidth(0.90f)
            OutlinedButton(content = { Text(i18n(S.txHistoryButton)) }, onClick = txHistoryButtonClicked, modifier = mod)
            OutlinedButton(content = { Text(i18n(S.SetChangePin)) }, onClick = {
                accountAction.value =
                  AccountAction.PinChange
            }, modifier = mod.testTag("SetChangePinButton"))
            if (wallyApp?.nullablePrimaryAccount != acc)    // it not primary
                OutlinedButton(content = { Text(i18n(S.setAsPrimaryAccountButton)) }, onClick = {
                    accountAction.value =
                      AccountAction.PrimaryAccount
                }, modifier = mod)
            OutlinedButton(content = { Text(i18n(S.assessUnconfirmed)) }, onClick = {
                accountAction.value =
                  AccountAction.Reassess
            }, modifier = mod)
            OutlinedButton(content = { Text(i18n(S.searchWalletTx)) }, onClick = { rediscoverWalletTx(AccountAction.Search) }, modifier = mod)
            OutlinedButton(content = { Text(i18n(S.ViewRecoveryPhrase)) }, onClick = {
                accountAction.value =
                  AccountAction.RecoveryPhrase
            }, modifier = mod)
            OutlinedButton(content = { Text(i18n(S.deleteWalletAccount)) }, onClick = {
                accountAction.value =
                  AccountAction.Delete
            }, modifier = mod)

            if (devMode)
            {
                OutlinedButton(content = { Text(i18n(S.rediscoverWalletTx)) }, onClick = { rediscoverWalletTx(AccountAction.Rediscover) }, modifier = mod)
                OutlinedButton(content = { Text(i18n(S.rediscoverBlockchain)) }, onClick = {
                    accountAction.value =
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
            when (accountAction.value)
            {
                AccountAction.PinChange ->
                {
                    Card(
                      modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                      colors = CardDefaults.cardColors(
                        containerColor = Color.White,
                      ),
                      shape = RoundedCornerShape(12.dp),
                      elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                          modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
                        ) {
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
                    }
                }

                AccountAction.RecoveryPhrase ->
                {
                    RecoveryPhraseView(acc) {
                        accountAction.value = null
                    }
                }

                AccountAction.Reassess -> GeneralConfirmationCard(
                  i18n(S.assessUnconfirmed),
                  {
                    Text(
                      text = i18n(S.reassessConfirmation),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                ) { accepted ->
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
                    GeneralConfirmationCard(
                      i18n(S.rediscoverWalletTx),
                      {
                        Text(
                          text = i18n(S.rediscoverConfirmation),
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          style = MaterialTheme.typography.bodySmall
                        )
                      }
                    ) {
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

                AccountAction.Delete -> GeneralConfirmationCard(
                  i18n(S.deleteWalletAccount),
                  {
                    Text(
                      text = i18n(S.deleteConfirmation) % mapOf("accountName" to acc.name, "blockchain" to acc.currencyCode),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                ) {
                    if (it)
                    {
                        laterJob {
                            wallyApp!!.deleteAccount(acc)
                            displayNotice(S.accountDeleteNotice)
                            accountDeleted()
                            noSelectedAccount()  // If we are in the account details, this account is selected.  We need to unselect it.
                        }
                    }
                    accountAction.value = null
                }

                AccountAction.Rediscover ->
                {
                    val wal = acc.wallet
                    val state = wal.chainstate

                    GeneralConfirmationCard(
                      i18n(S.searchWalletTx),
                      {
                        if (state != null)
                        {
                            val dateString = epochToDate(rediscoverPrehistoryTime.collectAsState().value)
                            Spacer(Modifier.height(8.dp))
                            Text(i18n(S.FirstUse) % mapOf("date" to dateString))
                            Text(i18n(S.Block) % mapOf("block" to rediscoverPrehistoryHeight.collectAsState().value.toString()))
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                          text = i18n(S.rediscoverConfirmation),
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          style = MaterialTheme.typography.bodySmall
                        )
                      }
                    ) {
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
                    GeneralConfirmationCard(
                      i18n(S.searchWalletTx),
                      {
                        if (state != null)
                        {
                            val dateString = epochToDate(rediscoverPrehistoryTime.collectAsState().value)
                            Spacer(Modifier.height(8.dp))
                            Text(i18n(S.FirstUse) % mapOf("date" to dateString))
                            Text(i18n(S.Block) % mapOf("block" to rediscoverPrehistoryHeight.collectAsState().value.toString()))
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                          text = i18n(S.searchConfirmation),
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          style = MaterialTheme.typography.bodySmall
                        )
                      }
                    ) {
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

                AccountAction.PrimaryAccount -> GeneralConfirmationCard(
                    i18n(S.setAsPrimaryAccountButton),
                  {
                    Text(
                      text = i18n(S.primaryAccountConfirmation),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                ) {
                    if (it)
                    {
                        wallyApp?.primaryAccount = acc
                        displayNoticePrimaryAccount(acc.name)
                    }
                    accountAction.value = null
                }

                else ->
                {
                }
            }
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

    WallySwitch(pinHidesAccount, S.PinHidesAccount, modifier = Modifier.testTag("PinHidesAccountToggle"))
    {
        pinHidesAccount = it
        if (it) acc.flags = acc.flags or ACCOUNT_FLAG_HIDE_UNTIL_PIN
        else acc.flags = (acc.flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN.inv())

        acc.saveAccountFlags()
    }

    if (acc.lockable)
    {

        AccountDetailPinInput(i18n(S.CurrentPin), i18n(S.EnterPIN), currentPin, currentPinOk) {
            if (it.onlyDigits())
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
            if (it.onlyDigits())
            {
                newPin = it
                newPinOk = it.length >= 4 || it.isEmpty()
            }
        }
    }
    else  // No current PIN
    {
        AccountDetailPinInput(i18n(S.NewPin), i18n(S.EnterPINorBlankToRemove), newPin, newPinOk) {
            if (it.onlyDigits())
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
            acc.pinEntered = true
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
        triggerAccountsChanged(acc)
    }
    Spacer(Modifier.height(16.dp))

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
          }, modifier = Modifier.testTag("AcceptPinButton"),
          content = {
              Text(i18n(S.accept))
          })
        Button(
          onClick = { pinChangedOrCancelled() },
          content = { Text(i18n(S.cancel)) }
        )
    }
}

@Composable
fun AccountDetailPinInput(description: String, placeholder: String, currentPin: String, currentPinOk: Boolean, onPinChanged: (String) -> Unit)
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
                  if ((k == androidx.compose.ui.input.key.Key.Enter) || (k == androidx.compose.ui.input.key.Key.NumPadEnter))
                  {
                      focusManager.moveFocus(FocusDirection.Next)
                      true
                  }
                  else false// do not accept this key
              }.testTag("PinInputField"),
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
        GeneralWarningCard(
          Icons.Default.Warning
        ) {
            Text(
              i18n(S.recoveryPhrase),
              style = MaterialTheme.typography.bodySmall
            )
        }
        var copied by remember { mutableStateOf(false) }

        val clickable = Modifier.clickable {
            setTextClipboard(account.wallet.secretWords.getSecret().decodeUtf8())
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
                      text = account.wallet.secretWords.getSecret().decodeUtf8(),
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
fun AccountDetailAcceptDeclineTextView(text: String, accept: (Boolean) -> Unit)
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
              onClick = { accept(true) },
              content = { Text(i18n(S.accept)) }
            )
            Button(
              onClick = { accept(false) },
              content = { Text(i18n(S.cancel)) }
            )
        }
    }
}
