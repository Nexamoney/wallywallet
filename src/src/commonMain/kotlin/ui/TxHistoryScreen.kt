package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import info.bitcoinunlimited.www.wally.Account

@Composable
fun TxHistoryScreen(account: Account, back: () -> Unit)
{
    Column {
        IconButton(onClick = back) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
        }
        Text(account.name)
    }
}