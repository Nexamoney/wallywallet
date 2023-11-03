package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import org.jetbrains.compose.resources.ExperimentalResourceApi

val testDropDown = listOf("big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "this_is_a_test_of_a_long_string",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  "any", "big","list","here","and", "there",
  )

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
fun HomeScreen(navigation: ChildNav)
{
    var isSending by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("any") }
    var isCreatingNewAccount by remember { mutableStateOf(false) }
    val selectedAccount = remember { mutableStateOf<Account?>(null) }
    val displayAccountDetailScreen = navigation.displayAccountDetailScreen.collectAsState()

    if (isCreatingNewAccount)
    {
        NewAccountScreen(assignWalletsGuiSlots(), devMode) {
            isCreatingNewAccount = it
        }
    }

    if(displayAccountDetailScreen.value == null && !isCreatingNewAccount)
        Box(modifier = WallyPageBase) {
        Column {
            Text("HomeScreen")

            //Row() {  // bug leaves a big gap
            Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Text("Drop boxes: ")
                var selectedIndex by remember { mutableStateOf(-1) }
                WallyDropdownMenu(
                  modifier = Modifier.width(IntrinsicSize.Min),
                  label = "Succinct",
                  items = testDropDown,
                  selectedIndex = selectedIndex,
                  style = WallyDropdownStyle.Succinct,
                  onItemSelected = { index, _ -> selectedIndex = index },
                )

                Text(", ")
                var selectedIndex2 by remember { mutableStateOf(-1) }
                WallyDropdownMenu(
                  modifier = Modifier.width(IntrinsicSize.Min).weight(1f),
                  label = "Field",
                  items = testDropDown,
                  selectedIndex = selectedIndex2,
                  style = WallyDropdownStyle.Field,
                  onItemSelected = { index, _ -> selectedIndex2 = index },
                )

                Text(", and ")
                var selectedIndex3 by remember { mutableStateOf(-1) }
                WallyDropdownMenu(
                  modifier = Modifier.width(IntrinsicSize.Min).weight(1f),
                  label = "Outlined",
                  items = testDropDown,
                  selectedIndex = selectedIndex3,
                  style = WallyDropdownStyle.Outlined,
                  onItemSelected = { index, _ -> selectedIndex3 = index },
                )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                  text = selected,
                  modifier = Modifier.clickable(onClick = { expanded = true })
                )
                IconButton(onClick = {expanded = true}) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "")
                }
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false },
              modifier = Modifier.background(Color.Magenta)
            ) {
                testDropDown.forEachIndexed { _, s ->
                    DropdownMenuItem(
                      onClick = {
                          expanded = false
                          selected = s
                      },
                      text = { Text(text = s) }
                    )
                }
            }
            WallyDivider()
            if(isSending)
            {
                SendFormView(
                  onComplete = { isSending = false }
                )
            }
            else if(!isSending)
            {
                WallyRoundedTextButton(S.Send) { isSending = true }
                WallyDivider()
                ReceiveView()
                WallyDivider()
                Spacer(modifier = Modifier.height(8.dp))
                ResImageView("icons/plus.xml",
                  modifier = Modifier.size(26.dp).absoluteOffset(0.dp, -8.dp).clickable {
                      isCreatingNewAccount = true
                  })
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
        AccountDetailScreen(navigation, displayAccountDetailScreen.value!!)
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
fun ReceiveView()
{
    Text("ReceiveView")
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