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
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.exceptionHandler
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.theme.*
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/**
 * Select which account you want to receive into
 */
@Composable
fun AccountDropDownSelector(
  accountGuiSlots:  MutableState<ListifyMap<String, Account>>,
  selectedAccountName: String?,
  onAccountNameSelected: (Int) -> Unit)
{
    var selectedIndex by remember { mutableStateOf(0) }
    val accountNames = accountGuiSlots.value.map { it.name }
    accountGuiSlots.value.forEachIndexed { index, account -> if (account.name == selectedAccountName) selectedIndex = index }

    WallyDropdownMenu(
              modifier = Modifier.width(IntrinsicSize.Min),
              label = "",
              items = accountNames,
              selectedIndex = selectedIndex,
              style = WallyDropdownStyle.Succinct,
              onItemSelected = { index, _ ->
                  selectedIndex = index
                  onAccountNameSelected(index) },
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

    fun onAddressCopied()
    {
        setTextClipboard(address)
        displayCopiedNotice = true
        GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
            delay(NORMAL_NOTICE_DISPLAY_TIME)  // Delay of 5 seconds
            withContext(Dispatchers.Default + exceptionHandler) {
                displayCopiedNotice = false
            }
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
        }
        Spacer(Modifier.width(4.dp))

        WallyBrightEmphasisBox(Modifier.fillMaxHeight().fillMaxWidth()) {
            Text(if (displayCopiedNotice) i18n(S.copiedToClipboard) else address, fontWeight = FontWeight.Bold, fontSize = FontScale(1.4),color = WallyAddressColor,
              modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically))
        }
    }
}