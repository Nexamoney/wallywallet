package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.displayNotice
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.setTextClipboard
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.views.IconTextButtonUi2
import info.bitcoinunlimited.www.wally.ui2.theme.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui2.views.CenteredText
import info.bitcoinunlimited.www.wally.wallyApp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.PayDestination
import org.nexa.libnexakotlin.chainToURI
import org.nexa.libnexakotlin.rem
import info.bitcoinunlimited.www.wally.ui2.views.*

typealias AccountName = String


@Composable
fun ReceiveScreen()
{
    val focusedAccount = wallyApp!!.focusedAccount.collectAsState().value
    // Select the first available account if none are available
    if (focusedAccount == null)
    {
        try
        {
            setSelectedAccount(wallyApp!!.preferredVisibleAccount())
        }
        catch (e: PrimaryWalletInvalidException)
        {
            displayErrorAndGoBack(S.NoAccounts)
            return
        }
    }

    if (focusedAccount == null)
    {
        displayErrorAndGoBack(S.NoAccounts)
        return
    }

    val dest = focusedAccount.currentReceiveObservable.collectAsState().value
    val payAddress = dest?.address

    Column (modifier = Modifier.fillMaxSize()) {
        if (payAddress != null)
            ReceiveScreenContent(focusedAccount, dest, Modifier.weight(1f))
        else
            Row {
                Syncing(Color.Black)
                Text(i18n(S.loading))
            }

        // Row with buttons at the bottom
        if (payAddress != null)
            Row(modifier = Modifier.fillMaxWidth().wrapContentHeight().background(Color.White).padding(2.dp),
              horizontalArrangement = Arrangement.Center) {
                IconTextButtonUi2(icon = Icons.Outlined.ContentCopy, modifier = Modifier.weight(1f), description = i18n(S.CopyAddress), color = wallyPurple) {
                    setTextClipboard(payAddress.toString())
                    displayNotice(i18n(S.copiedToClipboard))
                }
                IconTextButtonUi2(icon = Icons.AutoMirrored.Outlined.ArrowBack, modifier = Modifier.weight(1f).testTag("BackButton"), description = i18n(S.Back), color = wallyPurple, ) {
                    nav.back()
                }
            }
    }
}

@Composable
fun ReceiveScreenContent(account: Account, address: PayDestination, modifier: Modifier = Modifier)
{
    if (address.address == null) return  // This PayDestination does not have an address
    val addrStr = address.address.toString()
    val qrcodePainter = rememberQrCodePainter(addrStr)
    Surface(modifier = modifier.fillMaxWidth(), color = Color.White) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
            Spacer(modifier = Modifier.weight(0.04f))
            AccountPill(account).draw(buttonsEnabled = false)
            Spacer(modifier = Modifier.weight(0.1f))
            Image(
              painter = qrcodePainter,
              contentDescription = "QR Code",
              modifier = Modifier
                .padding(32.dp)  // Some QR readers can't handle a QR code without at least some white border (and yes this is actually to the spec)
                .weight(1f) // Take remaining space, but allow other components to take their intrinsic size
                .aspectRatio(1f) // Keeps the image square
                .background(Color.White)  // QR codes MUST have a white background and darker pixels, NOT the opposite (and yes this is to the spec)
                .testTag("qrcode")
                .clickable { setTextClipboard(addrStr) }
            )
            Spacer(modifier = Modifier.weight(0.1f))
            Text(
              text = i18n(S.YourAddress) % mapOf("blockchain" to (chainToURI[address.chainSelector] ?: "")),
              style = MaterialTheme.typography.headlineSmall
            )
            Text(
              text = addrStr,
              style = MaterialTheme.typography.bodyLarge,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(0.8f).clickable { setTextClipboard(addrStr) }.testTag("receiveScreen:receiveAddress")
            )
            if (devMode)
            {
                Spacer(modifier = Modifier.weight(0.1f))
                // Dev mode so don't need i18n
                CenteredText(text = "Providing address ${address.index}", textStyle = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.weight(0.02f))
        }
    }
}