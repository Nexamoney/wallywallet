package info.bitcoinunlimited.www.wally.ui.views


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.NoticeText
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.exceptionHandler
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/**
 * View for receiving funds
 */
@Composable
fun ReceiveView(selectedAccountName: String, address: String, accountNames: List<String>, onAccountNameSelected: (String) -> Unit)
{

    Column(
      Modifier.fillMaxWidth()
    ) {
        AccountDropDownSelector(
          accountNames,
          selectedAccountName,
          onAccountNameSelected = onAccountNameSelected,
        )

        AddressQrCode(address)
    }
}

/**
 * Select which account you want to receive into
 */
@Composable
fun AccountDropDownSelector(
  accountNames: List<String>,
  selectedAccountName: String,
  onAccountNameSelected: (String) -> Unit,
)
{
    var expanded by remember { mutableStateOf(false) }

    if (accountNames.isNotEmpty())
        Row(
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = i18n(S.Receive))
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                Row(
                  verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                      text = selectedAccountName,
                      modifier = Modifier.clickable(onClick = { expanded = true })
                    )
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                  expanded = expanded,
                  onDismissRequest = { expanded = false },
                ) {
                    accountNames.forEach { name ->
                        DropdownMenuItem(
                          onClick = {
                              onAccountNameSelected(name)
                              expanded = false
                          },
                          text = { Text(text = name) }
                        )
                    }
                }
            }
        }
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
      modifier = Modifier.clickable { onAddressCopied() }
    ) {
        Box(modifier = Modifier.padding(8.dp).background(color = Color.White)) {
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

        Box {
            if(displayCopiedNotice)
                NoticeText(i18n(S.copiedToClipboard))
            else
                Text(address)
        }
    }
}