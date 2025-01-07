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
import info.bitcoinunlimited.www.wally.ui.theme.WallyTheme
import info.bitcoinunlimited.www.wally.ui2.ReceiveScreenContent
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallyThemeUi2
import info.bitcoinunlimited.www.wally.ui2.AccountPill
import info.bitcoinunlimited.www.wally.ui2.IconTextButtonUi2
import info.bitcoinunlimited.www.wally.ui2.TransactionsList
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
            AccountPill()
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
    TransactionsList(Modifier)
}

@Composable
@Preview
fun ReceiveScreenPreview()
{
    val mockAddress = Pay2PubKeyTemplateDestination(ChainSelector.NEXA, UnsecuredSecret(ByteArray(32, { 1.toByte()})), 1234)
    WallyThemeUi2() {
        ReceiveScreenContent(address = mockAddress)
    }
}