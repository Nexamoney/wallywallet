package info.bitcoinunlimited.www.wally.ui2.views

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.gatherAssets
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*

data class RecentTransactionUIData(
  val transaction: TransactionHistory,
  val type: String,
  val icon: ImageVector,
  val contentDescription: String,
  val amount: String,
  val currency: String,
  val dateEpochMiliseconds: Long,
  val date: String = if (dateEpochMiliseconds > 1231006505000L) formatLocalEpochMilliseconds(dateEpochMiliseconds) else "",
  val assets: List<AssetPerAccount> = listOf()
)

class TxHistoryViewModel: ViewModel()
{
    val txHistory = MutableStateFlow<List<RecentTransactionUIData>>(listOf())

    init {
        wallyApp!!.focusedAccount.value?.let { account ->
            getAllTransactions(account)
        }
    }

    fun getAllTransactions(acc: Account)
    {
        laterJob {  // Do not do anything blocking (in this case DB access) within a UI or coroutine thread
            val transactions = mutableListOf<RecentTransactionUIData>()
            acc.wallet.forEachTxByDate {
                val amount = it.incomingAmt - it.outgoingAmt
                val txType = if (amount == 0L) "Unknown" else if (amount > 0) "Received" else "Send"
                val txIcon = if (amount == 0L) Icons.Outlined.QuestionMark else if (amount > 0) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward
                val assetsTransacted = it.tx.gatherAssets({
                    // We are going to use the native coin as a hint as to whether this transaction is sending or receiving
                    // If its sending, just look for assets that left this wallet
                    // If its receiving, look for assets coming in.
                    // TODO: look at inputs and accurately describing sending/receiving
                    if (it == null) false
                    else
                    {
                        val result: Boolean = if (amount > 0) acc.wallet.isWalletAddress(it)
                        else !acc.wallet.isWalletAddress(it)
                        result
                    }
                })
                val txUiData = RecentTransactionUIData(
                  type = txType,
                  icon = txIcon,
                  contentDescription = "Transaction",
                  amount = acc.cryptoFormat.format(acc.fromFinestUnit(amount)),
                  currency = acc.currencyCode,
                  dateEpochMiliseconds = it.date,
                  assets = assetsTransacted,
                  transaction = it
                )
                transactions.add(txUiData)
                // This places some data onscreen, in case the actual number of transactions is so large that it takes a lot of time to go through them all
                if (transactions.size == 10 && txHistory.value.size == 0)
                {
                    transactions.sortByDescending { it.dateEpochMiliseconds }
                    txHistory.value = transactions
                }
                false
            }
            // all transactions loaded, so add to the list
            transactions.sortByDescending { it.dateEpochMiliseconds }
            txHistory.value = transactions
        }
    }

    override fun onCleared()
    {
        txHistory.value = listOf()
        super.onCleared()
    }
}

@Composable
fun TransactionsList(modifier: Modifier = Modifier, viewModel: TxHistoryViewModel = viewModel { TxHistoryViewModel() })
{
    val transactions = viewModel.txHistory.collectAsState(emptyList()).value
    val account = wallyApp!!.focusedAccount.collectAsState().value
    if (account != null)
    {
        val balance = account.balanceState.collectAsState().value
        LaunchedEffect(balance) {
            viewModel.getAllTransactions(account)
        }
    }
    else
    {
        viewModel.txHistory.value = listOf()
    }

    if (transactions.isEmpty())
    {
        Spacer(Modifier.height(32.dp))
        CenteredText(i18n(S.NoAccountActivity))
    }

    LazyColumn(
      modifier = modifier
    ) {
        items(transactions) { tx ->
            RecentTransactionListItem(tx)
            Spacer(Modifier.height(8.dp))
            if (tx.assets.isNotEmpty())
            {
                tx.assets.forEach { asset ->
                    // TODO: Check if assets were actually sent or received here. How?
                    AssetListItem(asset, tx)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        // Since the thumb buttons cover the bottom most row, this blank bottom row allows the user to scroll the account list upwards enough to
        // uncover the last account.  Its not necessary if there are just a few accounts though.
        if (transactions.size >= 2)
            item {
                Spacer(Modifier.height(144.dp))
            }
    }
}
