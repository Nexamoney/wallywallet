package info.bitcoinunlimited.www.wally.ui.views


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.*
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.theme.*
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.nexa.libnexakotlin.exceptionHandler

/**
 * Select which account you want to receive into
 */
@Composable
fun AccountDropDownSelector(
  accountGuiSlots:  ListifyMap<String, Account>,
  selectedAccountName: String?,
  onAccountNameSelected: (String) -> Unit)
{
    var selectedIndex = mutableStateOf(-1)  // we don't want to "remember" this; it MUST be specified by the selectedAccountName
    accountGuiSlots.forEachIndexed { index, account -> if (account.name == selectedAccountName) selectedIndex.value = index }
    val accountNames = accountGuiSlots.map { it.name }.toMutableList()
    if (selectedIndex.value==-1) // If the selected account is not in our list, show a blank
    {
        accountNames.add(" ")
        selectedIndex.value = accountNames.size-1
    }

    WallyDropdownMenu(
              modifier = Modifier.width(IntrinsicSize.Min),
              label = "",
              items = accountNames,
              selectedIndex = selectedIndex.value,
              style = WallyDropdownStyle.Succinct,
              onItemSelected = { index, item ->
                  selectedIndex.value = index
                  onAccountNameSelected(item) },
            )
}

/**
 * Displays an address and displays a QR code with that address
 */
@Composable
fun AddressQrCode(address: String)
{
    var displayCopiedNotice by remember { mutableStateOf(false) }
    val qrcodePainter = rememberQrCodePainter(address)
    val coroutineScope = rememberCoroutineScope()


    fun onAddressCopied()
    {
        setTextClipboard(address)
        displayCopiedNotice = true
        coroutineScope.launch {
            delay(NORMAL_NOTICE_DISPLAY_TIME)  // Delay of 5 seconds
            displayCopiedNotice = false
        }
    }

    Row(
      modifier = Modifier.padding(2.dp).height(IntrinsicSize.Min).clickable { onAddressCopied() }
    ) {
        Box(modifier = Modifier.background(color = Color.White).padding(8.dp)) {
            if (address.isNotEmpty())
            {
                Image(
                  painter = qrcodePainter,
                  contentDescription = null,
                  modifier = Modifier
                    .size(144.dp)
                )
            }
            else
            {
                Box(
                  modifier = Modifier.size(144.dp)
                )
            }
        }
        Spacer(Modifier.width(4.dp))

        WallyBrightEmphasisBox(Modifier.fillMaxHeight().fillMaxWidth()) {
            Text(if (displayCopiedNotice) i18n(S.copiedToClipboard) else address, fontWeight = FontWeight.Bold, fontSize = FontScale(1.4),color = WallyAddressColor,
              modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically), textAlign = TextAlign.Center, minLines = 2)
        }
    }
}