package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import info.bitcoinunlimited.www.wally.Account

@Composable
fun AccountDetailScreen(nav: ChildNav, account: Account)
{
    Column {
        Text("Account Detail Screen")
        Text("For Account: ${account.name}")
        Button(onClick = { nav.displayAccount(null) }) { Text("Back to HomeScreen (slow loading)") }
    }
}