package ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.ui.ReceiveScreenContent
import info.bitcoinunlimited.www.wally.ui.SyncViewModelFake
import info.bitcoinunlimited.www.wally.ui.theme.WallyTheme
import info.bitcoinunlimited.www.wally.ui.views.AccountPillViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModelImpl
import info.bitcoinunlimited.www.wally.ui.views.TransactionsList
import info.bitcoinunlimited.www.wally.ui.views.TxHistoryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.Pay2PubKeyTemplateDestination
import org.nexa.libnexakotlin.UnsecuredSecret


@Composable
@Preview
fun RecentTransactionItemPreview()
{
    // RecentTransactionListItem(recentTransactionMock.first())
}

@Composable
@Preview
fun RecentTransactionsListPreview()
{
    TransactionsList(Modifier, TxHistoryViewModel())
}

@Composable
@Preview
fun ReceiveScreenPreview()
{
    val mockAccount = Account("mockaccount")
    val actFlow = MutableStateFlow<Account?>(mockAccount)
    val balanceViewModel = BalanceViewModelImpl(mockAccount)
    val mockPill = AccountPillViewModelFake(actFlow, balanceViewModel, SyncViewModelFake())
    val mockAddress = Pay2PubKeyTemplateDestination(ChainSelector.NEXA, UnsecuredSecret(ByteArray(32, { 1.toByte()})), 1234)
    WallyTheme() {
        ReceiveScreenContent(mockPill, mockAccount, address = mockAddress)
    }
}