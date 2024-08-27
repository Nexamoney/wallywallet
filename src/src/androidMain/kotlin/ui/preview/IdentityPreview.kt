package info.bitcoinunlimited.www.wally.previews

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import info.bitcoinunlimited.www.wally.ui.IdentityEditScreen
import info.bitcoinunlimited.www.wally.ui.IdentityScreen
import info.bitcoinunlimited.www.wally.ui.IdentitySession
import info.bitcoinunlimited.www.wally.ui.theme.WallyPageBase


@Composable
@Preview
fun IdentityScreenPreview()
{
    val fakes = setUpPreview(accounts = 5)
    Box(modifier = WallyPageBase) {
        IdentityScreen(fakes.accounts.first(), IdentitySession(null, null, {a,b, c ->}), fakes.nav)
    }
}

@Composable
@Preview
fun IdentityEditScreenPreview()
{
    val fakes = setUpPreview(accounts = 1)
    Box(modifier = WallyPageBase) {
        IdentityEditScreen(fakes.accounts.first(), fakes.nav)
    }
}