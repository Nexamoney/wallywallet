package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.AccountListView
import info.bitcoinunlimited.www.wally.ui.views.ReceiveView
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import org.jetbrains.compose.resources.ExperimentalResourceApi


fun assignWalletsGuiSlots(): ListifyMap<String, Account>
{
    // We have a Map of account names to values, but we need a list
    // Sort the accounts based on account name
    val lm: ListifyMap<String, Account> = ListifyMap(wallyApp!!.accounts, { it.value.visible }, object : Comparator<String>
    {
        override fun compare(p0: String, p1: String): Int
        {
            if (wallyApp?.nullablePrimaryAccount?.name == p0) return Int.MIN_VALUE
            if (wallyApp?.nullablePrimaryAccount?.name == p1) return Int.MAX_VALUE
            return p0.compareTo(p1)
        }
    })

    /*  TODO set up change notifications moving upwards from the wallets
    for (c in wallyApp!!.accounts.values)
    {
        c.wallet.setOnWalletChange({ it -> onWalletChange(it) })
        c.wallet.blockchain.onChange = { it -> onBlockchainChange(it) }
        c.wallet.blockchain.net.changeCallback = { _, _ -> onWalletChange(c.wallet) }  // right now the wallet GUI update function also updates the cnxn mgr GUI display
        c.onChange()  // update all wallet UI fields since just starting up
    }
     */

    return lm
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun HomeScreen(nav: ScreenNav, navigation: ChildNav)
{
    var isSending by remember { mutableStateOf(false) }
    var isCreatingNewAccount by remember { mutableStateOf(false) }
    val selectedAccount = remember { mutableStateOf<Account?>(null) }
    val displayAccountDetailScreen = navigation.displayAccountDetailScreen.collectAsState()


    selectedAccount.value?.onUpdatedReceiveInfoCommon { recvAddrStr -> }

    if (isCreatingNewAccount)
    {
        NewAccountScreen(assignWalletsGuiSlots(), devMode) {
            isCreatingNewAccount = it
        }
    }

    if(displayAccountDetailScreen.value == null && !isCreatingNewAccount)
        Box(modifier = WallyPageBase) {
        Column {
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
                  selectedAccount.value?.currentReceive?.address?.toString() ?: "",
                  assignWalletsGuiSlots().map { it.name },
                  onAccountNameSelected = { accountName ->
                    assignWalletsGuiSlots().forEach {
                        if(it.name == accountName)
                            selectedAccount.value = it
                    }
                })
                WallyDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {

                    ResImageView("icons/check.xml", // "icons/ani_syncing.xml"
                      modifier = Modifier.size(26.dp).absoluteOffset(0.dp, -8.dp))
                    Text(i18n(S.AccountListHeader))
                    ResImageView("icons/plus.xml",
                      modifier = Modifier.size(26.dp).absoluteOffset(0.dp, -8.dp).clickable {
                          isCreatingNewAccount = true
                      })
                }
            }
            AccountListView(
              assignWalletsGuiSlots(),
              selectedAccount,
              navigation,
            )
            WallyDivider()
            QrCodeScannerView()
        }
    }
    else if(displayAccountDetailScreen.value is Account && !isCreatingNewAccount)
        AccountDetailScreenNav(navigation, displayAccountDetailScreen.value!!, assignWalletsGuiSlots())
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