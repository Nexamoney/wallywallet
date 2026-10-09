package info.bitcoinunlimited.www.wally.ui.views

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
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.TxAssetFlow
import info.bitcoinunlimited.www.wally.ui.gatherAssets
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*
import org.nexa.threads.millisleep

private val LogIt = GetLog("wally.TxHistory")

data class RecentTransactionUIData(
  val transaction: TransactionHistory,
  val type: String,
  val icon: ImageVector,
  val contentDescription: String,
  val amount: String,
  val currency: String,
  val dateEpochMiliseconds: Long,
  val date: String = if (dateEpochMiliseconds > 1231006505000L) formatLocalEpochMilliseconds(dateEpochMiliseconds) else "",
  val assets: List<TxAssetFlow> = listOf()
)

/** Dates per chunk of the history walk; every record of a date comes in the same chunk, so this is a floor on rows. */
private const val HISTORY_CHUNK_DATES = 50L

private fun recentTxUiData(acc: Account, txh: TransactionHistory): RecentTransactionUIData
{
    val amount = txh.incomingAmt - txh.outgoingAmt
    val txType = if (amount == 0L) "Unknown" else if (amount > 0) "Received" else "Send"
    val txIcon = if (amount == 0L) Icons.Outlined.QuestionMark else if (amount > 0) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward
    return RecentTransactionUIData(
      type = txType,
      icon = txIcon,
      contentDescription = "Transaction",
      amount = acc.cryptoFormat.format(acc.fromFinestUnit(amount)),
      currency = acc.currencyCode,
      dateEpochMiliseconds = txh.date,
      assets = txh.gatherAssets(),
      transaction = txh
    )
}

class TxHistoryViewModel: ViewModel()
{
    val txHistory = MutableStateFlow<List<RecentTransactionUIData>>(listOf())
    var priorAccount: Account? = null
    val loading = MutableStateFlow<Boolean>(false)

    // A walk is requested by flag and run by one worker at a time, so a burst of balance changes collapses into one
    // walk and a request that lands mid-walk queues exactly one more instead of being dropped or run in parallel.
    private val walkRequested = atomic(false)
    private val walking = atomic(false)
    // Bumped on every account switch; a walk stops, and stops publishing, once the generation it started under is gone.
    // switchLock makes a walk's "is my generation current, then publish" atomic with respect to a switch.
    private val generation = atomic(0L)
    private val switchLock = org.nexa.threads.Mutex()

    init {
        wallyApp!!.focusedAccount.value?.let { account ->
            getAllTransactions(account)
        }
    }

    fun getAllTransactions(acc: Account)
    {
        // If the account has changed, we want to clear the tx list right away, so there's not slow-update confusion
        // but if the account is the same, go with the cached values until reloaded
        switchLock.lock {
            if (priorAccount != acc)
            {
                generation.incrementAndGet()
                priorAccount = acc
                loading.value = true
                txHistory.value = listOf()
            }
        }
        walkRequested.value = true
        tlater("txHistory") {
            while (true)
            {
                if (!walking.compareAndSet(false, true)) return@tlater
                try
                {
                    while (walkRequested.getAndSet(false))
                    {
                        val (account, gen) = switchLock.lock { Pair(priorAccount, generation.value) }
                        if (account == null) continue
                        try
                        {
                            walk(account, gen)
                        }
                        catch (e: Exception)
                        {
                            // Keep serving requests: a failed walk must not strand a request queued behind it
                            logThreadException(e, "loading the transaction history of ${account.name}", sourceLoc())
                            ifCurrent(gen) { loading.value = false }
                        }
                    }
                }
                finally { walking.value = false }
                if (!walkRequested.value) return@tlater
            }
        }
    }

    /** Run [block] only if no account switch has happened since [gen], atomically with respect to a switch. */
    private fun ifCurrent(gen: Long, block: () -> Unit) = switchLock.lock { if (generation.value == gen) block() }

    /** Load [acc]'s history newest-first, publishing as it goes. Blocking: worker threads only. */
    private fun walk(acc: Account, gen: Long)
    {
        val transactions = mutableListOf<RecentTransactionUIData>()
        // Where each tx is in [transactions]: the wallet lock is released between chunks, and confirming a tx can move
        // its date back past the cursor, so the same tx can come back in a later chunk
        val rowOf = mutableMapOf<Hash256, Int>()
        // Publish a fresh copy: the flow compares by equality, so republishing the list being built is a no-op.
        // Sort before taking the lock so an account switch never waits on it.
        fun publish()
        {
            val sorted = transactions.sortedByDescending { it.dateEpochMiliseconds }
            ifCurrent(gen) { txHistory.value = sorted }
        }
        // Walk in date chunks so the wallet lock is released between chunks.  A chunk hands back every record of its
        // dates at or below the cursor, so the next cursor is the oldest date seen minus one and an empty chunk ends
        // the walk.  Each chunk moves the cursor strictly down, so the walk always ends.
        var cursor = Long.MAX_VALUE
        // Row count at which to next put partial results onscreen.  It doubles, so the sorted copies of a long walk
        // add up to O(n) rows instead of one copy every few rows.
        var nextPublish = 8
        while (generation.value == gen)
        {
            var seen = 0
            var oldest = cursor
            acc.wallet.forEachTxByDate(cursor, HISTORY_CHUNK_DATES) {
                if (it.date <= cursor)
                {
                    seen++
                    if (it.date < oldest) oldest = it.date
                    val row = recentTxUiData(acc, it)
                    val prior = rowOf[it.tx.idem]
                    if (prior != null) transactions[prior] = row  // came back with a new date: replace it, don't list it twice
                    else
                    {
                        rowOf[it.tx.idem] = transactions.size
                        transactions.add(row)
                    }
                }
                false
            }
            if (seen == 0) break
            cursor = oldest - 1
            // This places some data onscreen, in case the actual number of transactions is so large that it takes a lot
            // of time to go through them all.  Done between chunks so the sort and copy never run under the wallet lock.
            if (transactions.size >= nextPublish)
            {
                if (txHistory.value.size < transactions.size) publish()
                while (nextPublish <= transactions.size) nextPublish *= 2
            }
            millisleep(1U)  // give away the processor so the GUI can run
        }
        publish()
        ifCurrent(gen) { loading.value = false }
    }

    override fun onCleared()
    {
        txHistory.value = listOf()
        super.onCleared()
    }
}

@Composable
fun TransactionsList(modifier: Modifier = Modifier, viewModel: TxHistoryViewModel)
{
    val transactions = viewModel.txHistory.collectAsState(emptyList()).value
    val account = wallyApp!!.focusedAccount.collectAsState().value
    if (account != null)
    {
        val balance = account.balanceState.collectAsState().value
        LaunchedEffect(account, balance) {
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
        if (viewModel.loading.collectAsState().value == true)
            CenteredText(i18n(S.loading))
        else
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
                tx.assets.forEach { flow ->
                    AssetListItem(flow.asset, flow.received)
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
