package ui.preview

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.ui.theme.WallyTheme
import info.bitcoinunlimited.www.wally.ui2.ReceiveScreenContent
import info.bitcoinunlimited.www.wally.ui2.theme.WallyThemeUi2
import info.bitcoinunlimited.www.wally.ui2.views.AccountPill
import info.bitcoinunlimited.www.wally.ui2.views.IconTextButtonUi2
import info.bitcoinunlimited.www.wally.ui2.views.TransactionsList
import info.bitcoinunlimited.www.wally.ui2.views.TxHistoryViewModel
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.Pay2PubKeyTemplateDestination
import org.nexa.libnexakotlin.UnsecuredSecret

@Composable
@Preview
fun BalanceSendReceiveWidgetPreview()
{
    WallyTheme(false, false) {
        Surface(
          modifier = Modifier.wrapContentHeight().width(500.dp)
        ) {
            AccountPill(null)
        }
    }
}

@Composable
@Preview
fun MaterialIconButtonPreview()
{
    WallyTheme(false, false) {
        Surface {
            IconTextButtonUi2(
              icon = Icons.Outlined.SwapHoriz,
              description = "Split a bill"
            ) {

            }
        }
    }
}

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
    val mockAddress = Pay2PubKeyTemplateDestination(ChainSelector.NEXA, UnsecuredSecret(ByteArray(32, { 1.toByte()})), 1234)
    WallyThemeUi2() {
        ReceiveScreenContent(mockAccount, address = mockAddress)
    }
}