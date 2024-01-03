package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.AccountListView
import info.bitcoinunlimited.www.wally.ui.views.ReceiveView
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.nexa.libnexakotlin.launch

@OptIn(ExperimentalResourceApi::class)
@Composable
fun HomeScreen(accountGuiSlots: MutableState<ListifyMap<String, Account>>, nav: ScreenNav, navigation: ChildNav)
{
    var isSending by remember { mutableStateOf(false) }
    //val selectedAccount = remember { mutableStateOf<Account?>(wallyApp?.focusedAccount) }
    val selectedAccount = remember { MutableStateFlow<Account?>(wallyApp?.focusedAccount) }
    val displayAccountDetailScreen = navigation.displayAccountDetailScreen.collectAsState()
    val synced = remember { mutableStateOf(wallyApp!!.isSynced()) }

    var currentReceive by remember { mutableStateOf<String?>(null) }

    // TODO actually show the warning
    var warnBackupRecoveryKey = remember { mutableStateOf(false) }

    LaunchedEffect(selectedAccount) {
    selectedAccount.collect {
            selectedAccount.value?.onUpdatedReceiveInfoCommon { recvAddrStr -> currentReceive = recvAddrStr}
        }
     }

    launch {
        delay(500)
        val wbk = wallyApp?.warnBackupRecoveryKey?.receive()
        if (wbk == true) warnBackupRecoveryKey.value = true
        if ((selectedAccount.value == null) && (wallyApp?.focusedAccount != null))
        {
            selectedAccount.value = wallyApp?.focusedAccount
        }
    }

    // TODO: When this HomeScreen is repeatedly called, how do I exit old coroutine launches?
    launch {
        try
        {
            while (true)
            {
                synced.value = wallyApp!!.isSynced()
                delay(1000)
            }
        }
        catch(e: IllegalStateException)
        {
            // When the mutableState is stale, this exception is thrown.
            // Stale state means we just want to stop checking; a different launch is checking the new state
        }
    }

    if (displayAccountDetailScreen.value == null)
        Box(modifier = WallyPageBase) {
        Column {
            ConstructTitleBar(nav, S.app_name)
            if(isSending)
            {
                SendFormView(
                  onComplete = { isSending = false }
                )
            }
            else if(!isSending)
            {
                Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    WallyBoringLargeTextButton(S.Send) { isSending = true }
                    WallyBoringLargeTextButton(S.title_split_bill) { nav.go(ScreenId.SplitBill) }
                }
                WallyDivider()
                ReceiveView(
                  selectedAccount.value?.name ?: "",
                  currentReceive ?: "",
                  accountGuiSlots.value.map { it.name },
                  onAccountNameSelected = { accountName ->
                      accountGuiSlots.value.forEach {
                          if (it.name == accountName)
                          {
                              selectedAccount.value = it
                              wallyApp?.focusedAccount = it
                          }

                      }
                  })
            }

            WallyDivider()
            Row(horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                // .background(Color.Red)
                .padding(0.dp)
                .fillMaxWidth().padding(0.dp, 0.dp))
            {
                //Spacer(Modifier.padding(10.dp, 0.dp))
                ResImageView(if (synced.value) "icons/check.xml" else "icons/syncing.xml", // MP doesn't support animation drawables "icons/ani_syncing.xml",
                      modifier = Modifier.size(30.dp)) // .background(Color.Blue)) //.absoluteOffset(0.dp, -8.dp))
                SectionText(S.AccountListHeader, Modifier.weight(1f)) // .background(Color.Green))

                // BUG this button is padding out vertically
                WallyImageButton("icons/plus.xml", true, Modifier.size(26.dp)) {
                    nav.go(ScreenId.NewAccount)
                }


                //ResImageView("icons/plus.xml",
                //      modifier = Modifier.size(26.dp).clickable {
                //          nav.go(ScreenId.NewAccount)
                //      })
                //Spacer(Modifier.padding(10.dp, 0.dp))
            }
            AccountListView(
              accountGuiSlots,
              selectedAccount,
              navigation,
            )
            WallyDivider()
            QrCodeScannerView()
        }
    }
    else if(displayAccountDetailScreen.value is Account)
        AccountDetailScreenNav(navigation, displayAccountDetailScreen.value!!, accountGuiSlots)
}

@Composable
fun SendFormView(onComplete: () -> Unit)
{
    Text("Send from account:")
    Text("To:...")
    Text("Amount..")
    Row {
        WallyRoundedTextButton(S.Send) {
            // TODO: Send...
            // TODO: Display success/failure AlertDialog
            onComplete()
        }
        Spacer(Modifier.width(10.dp))
        WallyBoringTextButton(S.SendCancel) {
            // TODO: Clear text in "To" field
            // TODO: Clear quantity in "Amount" field
            onComplete()
        }
    }
}

@Composable
fun QrCodeScannerView()
{
    var isDialogOpen by remember { mutableStateOf(false) }
    WallyRoundedTextButton("Scan QR code") {
          isDialogOpen = true
          // TODO: Implement QR-code scanner
      }

    if (isDialogOpen) {
        AlertDialog(
          onDismissRequest = { },
          confirmButton = {
              Button(onClick = { isDialogOpen = false }) {
                  Text("OK")
              }
          },
          title = { Text("Qr scanner") },
          text = { Text("...not implemented") },
        )
    }
}