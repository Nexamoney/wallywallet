package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.nexa.libnexakotlin.launch

@OptIn(ExperimentalResourceApi::class)
@Composable
/* Since composable state needs to be defined within a composable, imagine this composable is actually a singleton class,
with member variables and member functions defined in it.
We could use a composable "State Holder" (in theory) to capture all the state needed by the member functions, but creating a state holder appears to entail
writing a vast amount of inscrutible garbage rather than actual useful code.
* */
fun HomeScreen(accountGuiSlots: MutableState<ListifyMap<String, Account>>, nav: ScreenNav, navigation: ChildNav)
{
    var isSending by remember { mutableStateOf(false) }

    val selectedAccount = remember { MutableStateFlow<Account?>(wallyApp?.focusedAccount) }
    var sendFromAccount by remember { mutableStateOf<String>("") }
    val displayAccountDetailScreen = navigation.displayAccountDetailScreen.collectAsState()
    val synced = remember { mutableStateOf(wallyApp!!.isSynced()) }
    var currentReceive by remember { mutableStateOf<String?>(null) }

    var warnBackupRecoveryKey = remember { mutableStateOf(false) }


    @Composable
    fun SendFromView(onComplete: () -> Unit)
    {
        val sendToAddress = remember { MutableStateFlow<String>("") }  // TODO populate this

        Row(modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
            SectionText(i18n(S.fromAccountColon))
            AccountDropDownSelector(
              accountGuiSlots,
              sendFromAccount,
              onAccountNameSelected = {
                  //sendFromAccount.value = accountGuiSlots.value[it].name
              },
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(i18n(S.toColon))
            WallyTextEntry(sendToAddress.value, Modifier.weight(1f)) {
                sendToAddress.value = it
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(i18n(S.Amount))
            WallyTextEntry(sendToAddress.value, Modifier.weight(1f)) {
                sendToAddress.value = it
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WallyBoringLargeTextButton(S.Send) {
                // TODO: Send...
                // TODO: Display success/failure AlertDialog
                onComplete()
            }
            WallyBoringLargeTextButton(S.SendCancel) {
                // TODO: Clear text in "To" field
                // TODO: Clear quantity in "Amount" field
                onComplete()
            }
        }
    }

    /**
     * View for receiving funds
     */
    @Composable
    fun ReceiveView(onAccountNameSelected: (Int) -> Unit)
    {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SectionText(text = i18n(S.Receive))  // Receive into account
                Spacer(modifier = Modifier.width(8.dp))
                AccountDropDownSelector(
                  accountGuiSlots,
                  selectedAccount.value?.name ?: "",
                  onAccountNameSelected = onAccountNameSelected,
                )
            }
            AddressQrCode(currentReceive ?: "")
        }
    }


    // Now specify some state collectors.  These are coroutines that update state from other areas of the code

    // If the selected account changes, we need to update the receiving address
        LaunchedEffect(selectedAccount) {
            selectedAccount.collect {
                selectedAccount.value?.onUpdatedReceiveInfoCommon { recvAddrStr ->
                    currentReceive = recvAddrStr
                }
            }
        }

    // During startup, there is a race condition between loading the accounts and the display of this screen.
    // So if the selectedAccount is null, wait for some accounts to appear
        launch {
            while(selectedAccount.value == null)
            {
                delay(50)
                val wbk = wallyApp?.warnBackupRecoveryKey?.receive()
                if (wbk == true) warnBackupRecoveryKey.value = true
                if ((selectedAccount.value == null) && (wallyApp?.focusedAccount != null))
                {
                    selectedAccount.value = wallyApp?.focusedAccount
                }
            }
        }

    // Update the syncronization icon based on the underlying synced status
        launch {
            try
            {
                while (true)
                {
                    synced.value = wallyApp!!.isSynced()
                    delay(1000)
                }
            }
            catch (e: IllegalStateException)
            {
                // When the mutableState is stale, this exception is thrown.
                // Stale state means we just want to stop checking; a different launch is checking the new state
            }
        }

    // Now show the page
    Box(modifier = WallyPageBase) {
        Column {
            ConstructTitleBar(nav, S.app_name)
            if (isSending)
            {
                SendFromView(onComplete = { isSending = false })
            }
            else if (!isSending)
            {
                Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {

                    WallyBoringLargeTextButton(S.Send) { isSending = true }
                    WallyBoringLargeTextButton(S.title_split_bill) { nav.go(ScreenId.SplitBill) }
                }
                WallyDivider()

                ReceiveView(
                  onAccountNameSelected = { accountIdx ->
                      val act = accountGuiSlots.value[accountIdx]
                      if (act != null)
                      {
                          selectedAccount.value = act
                          wallyApp?.focusedAccount = act
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
            AccountListView(nav, selectedAccount, accountGuiSlots) {
                selectedAccount.value = it
                sendFromAccount = it.name
                wallyApp?.focusedAccount = it
            }
            WallyDivider()
            QrCodeScannerView()
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

    if (isDialogOpen)
    {
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
