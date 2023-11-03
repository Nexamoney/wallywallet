package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import org.nexa.libnexakotlin.*
import org.nexa.threads.Thread

private val supportedBlockchains =
  mapOf(
    "NEXA" to ChainSelector.NEXA,
    "BCH (Bitcoin Cash)" to ChainSelector.BCH,
    "TNEX (Testnet Nexa)" to ChainSelector.NEXATESTNET,
    "RNEX (Regtest Nexa)" to ChainSelector.NEXAREGTEST,
    "TBCH (Bitcoin Cash)" to ChainSelector.BCHTESTNET,
    "RBCH (Bitcoin Cash)" to ChainSelector.BCHREGTEST
  )
const val ACCOUNT_FLAG_NONE = 0UL
const val ACCOUNT_FLAG_HIDE_UNTIL_PIN = 1UL
const val ACCOUNT_FLAG_HAS_VIEWED_RECOVERY_KEY = 2UL
const val ACCOUNT_FLAG_REUSE_ADDRESSES = 4UL
private val LogIt = GetLog("BU.wally.NewAccountScreen")

data class NewAccountState(
  val hideUntilPinEnter: Boolean = false,
  val errorMessage: String = "",
  val accountName: String = "",
  val validAccountName: Boolean = accountName.length in 1..8,
  val recoveryPhrase: String = "",
  val validOrNoRecoveryPhrase: Boolean = true,
  val pin: String = "",
  val validOrNoPin: Boolean = true,
  val isSuccessDialogOpen: Boolean = false
)

@Composable fun NewAccountScreen(accounts: List<Account>, devMode: Boolean, creatingAccount: (Boolean) -> Unit)
{
    val blockchains = supportedBlockchains.filter { devMode || it.value.isMainNet }
    var selectedBlockChain by remember { mutableStateOf(blockchains.entries.first()) }
    var newAcState by remember { mutableStateOf( NewAccountState() ) }

    NewAccountScreenContent(
      newAcState,
      selectedBlockChain,
      blockchains,
      onBackButton = { creatingAccount(false) },
      onChainSelected = { selectedBlockChain = it },
      onNewAccountName = {
          newAcState = newAcState.copy(
            accountName = it,
            validAccountName = it.length in 1..8,
          )
      },
      onNewRecoveryPhrase = {
          val words = processSecretWords(it)
          newAcState = newAcState.copy(
            recoveryPhrase = it,
            validOrNoRecoveryPhrase = isValidOrEmptyRecoveryPhrase(words)
          )
      },
      onPinChange = {
          val validOrNoPin = if (it.isEmpty())
          {
              true
          }
          else if (it.length < 4)
          {
              false
          }
          else it.length >= 4
          newAcState = newAcState.copy(pin = it, validOrNoPin = validOrNoPin)
      },
      onHideUntilPinEnterChanged = {
          newAcState =  newAcState.copy(hideUntilPinEnter = it)
      },
      onClickCreateAccount =  {
          var inputValid = true
          val words = processSecretWords(newAcState.recoveryPhrase)
          val incorrectWords = Bip39InvalidWords(words)
          newAcState = newAcState.copy(
            errorMessage = ""
          )

          if (newAcState.accountName.isEmpty() || newAcState.accountName.length > 8)
          {
              newAcState = newAcState.copy(errorMessage = (newAcState.errorMessage + i18n(S.invalidAccountName)))
              inputValid = false
          }
          else if (containsAccountWithName(accounts, newAcState.accountName)) {
              newAcState = newAcState.copy(errorMessage = (newAcState.errorMessage + i18n(S.invalidAccountName)))
              inputValid = false
          }

          if (words.size > 12)
          {
              newAcState = newAcState.copy(errorMessage = i18n(S.TooManyRecoveryWords))
              inputValid = false
          }
          else if (words.size in 1..11)
          {
              newAcState = newAcState.copy(errorMessage = i18n(S.NotEnoughRecoveryWords))
              inputValid = false
          }
          else if (incorrectWords.isNotEmpty())
          {
              newAcState = newAcState.copy(errorMessage = i18n(S.invalidRecoveryPhrase))
              inputValid = false
          }

          if (newAcState.pin.isNotEmpty() && newAcState.pin.length < 4) {
              newAcState = newAcState.copy(errorMessage = i18n(S.InvalidPIN))
              inputValid = false
          }

          val flags: ULong = if (newAcState.hideUntilPinEnter) ACCOUNT_FLAG_HIDE_UNTIL_PIN else ACCOUNT_FLAG_NONE

          if (inputValid && words.size == 12) // account recovery
          {
              Thread("recoverAccount") {
                  newAcState = try {
                      wallyApp!!.recoverAccount(newAcState.accountName, flags, newAcState.pin, words.joinToString(" "), selectedBlockChain.value, null, null, null)
                      newAcState.copy(isSuccessDialogOpen = true)
                  }
                  catch (e: Error)
                  {
                      newAcState.copy(errorMessage = i18n(S.unknownError))
                  }
              }
          }
          if (inputValid && words.isEmpty()) {
              later {
                  val account = wallyApp!!.newAccount(newAcState.accountName, flags, newAcState.pin, selectedBlockChain.value)
                  newAcState = if (account == null)
                  {
                      newAcState.copy(errorMessage = i18n(S.unknownError))
                  }
                  else
                  {
                      newAcState.copy(isSuccessDialogOpen = true)
                  }
              } // Can't happen in GUI thread
          }
      },
      onDismissAccountCreatedSuccessDialog = {
          newAcState = newAcState.copy(isSuccessDialogOpen = false)
          creatingAccount(false)
      }
    )
}

@Composable fun AccountCreatedSuccessDialog(displayed: Boolean, accountName: String, onDismiss: () -> Unit) {
    if (displayed) {
        AlertDialog(
          onDismissRequest = onDismiss,
          confirmButton = {
              Button(onClick = onDismiss ) {
                  Text("Ok")
              }
          },
          title = { Text("Success") },
          text = { Text("Account created: $accountName") },
        )
    }
}

@Composable fun NewAccountScreenContent(
  newAcState: NewAccountState,
  selectedChain: Map.Entry<String, ChainSelector>,
  blockchains: Map<String, ChainSelector>,
  onBackButton: () -> Unit,
  onChainSelected: (Map.Entry<String, ChainSelector>) -> Unit,
  onNewAccountName: (String) -> Unit,
  onNewRecoveryPhrase: (String) -> Unit,
  onPinChange: (String) -> Unit,
  onHideUntilPinEnterChanged: (Boolean) -> Unit,
  onClickCreateAccount: () -> Unit,
  onDismissAccountCreatedSuccessDialog: () -> Unit
)
{
    Column(
      modifier = Modifier.padding(4.dp).fillMaxSize()
    ) {
        IconButton(onClick = onBackButton) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
        }
        if (newAcState.errorMessage.isNotEmpty())
        {
            WallyError(newAcState.errorMessage)
        }
        BlockchainDropDownMenu(selectedChain, blockchains, onChainSelected)
        AccountNameInput(newAcState.accountName, newAcState.validAccountName, onNewAccountName)
        RecoveryPhraseInput(newAcState.recoveryPhrase, newAcState.validOrNoRecoveryPhrase, onNewRecoveryPhrase)
        pinInput(newAcState.pin, newAcState.validOrNoPin, onPinChange)
        Text(i18n(S.PinSpendingUnprotected), fontSize = 12.sp)
        WallySwitch(newAcState.hideUntilPinEnter, S.PinHidesAccount, onHideUntilPinEnterChanged)
        WallyRoundedTextButton(i18n(S.createAccount), onClick = onClickCreateAccount)
        AccountCreatedSuccessDialog(newAcState.isSuccessDialogOpen, newAcState.accountName, onDismissAccountCreatedSuccessDialog)
    }
}

@Composable fun BlockchainDropDownMenu(
  selectedChain: Map.Entry<String, ChainSelector>,
  blockchains: Map<String, ChainSelector>,
  onChainSelected: (Map.Entry<String, ChainSelector>) -> Unit
)
{
    var expanded by remember { mutableStateOf(false) }

    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, tint = colorDebit ,contentDescription = "Check or not check")
        Spacer(Modifier.width(8.dp))
        Text(i18n(S.Blockchain))
        Spacer(Modifier.width(8.dp))
        Box {
            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                  text = selectedChain.key,
                  modifier = Modifier.clickable(onClick = { expanded = true })
                )
                IconButton(onClick = {expanded = true}) {
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false }
            ) {
                blockchains.forEach {
                    DropdownMenuItem(
                      onClick = {
                          onChainSelected(it)
                          expanded = false
                      },
                      text = { Text(it.key) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AccountNameInput(accountName: String, validAccountName: Boolean, onNewAccountName: (String) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(S.AccountName))
        Spacer(Modifier.width(8.dp))
        if (validAccountName)
        {
            Icon(imageVector = Icons.Default.Check, tint = colorDebit, contentDescription = null)
        }
        else
        {
            Icon(Icons.Default.Clear, tint = colorCredit, contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        TextField(
          value = accountName,
          onValueChange = onNewAccountName,
          colors = TextFieldDefaults.textFieldColors(containerColor = Color.Transparent),
          placeholder = { Text(i18n(S.AccountNameHint)) },
          singleLine = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RecoveryPhraseInput(recoveryPhrase: String, validOrNoRecoveryPhrase: Boolean, onValueChange: (String) -> Unit)
{
    Column {
        Text(i18n(S.AccountRecoveryPhrase))
        Spacer(Modifier.width(8.dp))
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
            if (validOrNoRecoveryPhrase)
            {
                Icon(Icons.Default.Check, tint = colorDebit ,contentDescription = null)
            }
            else
            {
                Icon(Icons.Default.Clear, tint = colorCredit ,contentDescription = null)
            }
            TextField(
              value = recoveryPhrase,
              onValueChange = onValueChange,
              colors = TextFieldDefaults.textFieldColors(containerColor = Color.Transparent),
              placeholder = { Text(i18n(S.LeaveEmptyNewWallet)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              textStyle = TextStyle(fontSize = 12.sp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun pinInput(pin: String, validOrNoPin: Boolean, onPinChange : (String) -> Unit)
{
    Column {
        Text(i18n(S.CreatePIN))
        Spacer(Modifier.width(8.dp))
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
            if(validOrNoPin)
            {
                Icon(Icons.Default.Check, tint = colorDebit, contentDescription = null)
            }
            else
            {
                Icon(Icons.Default.Clear, tint = colorCredit, contentDescription = null)
            }
            TextField(
              value = pin,
              onValueChange = onPinChange,
              colors = TextFieldDefaults.textFieldColors(containerColor = Color.Transparent),
              placeholder = { Text(i18n(S.PinSuggestions), fontSize = 12.sp) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
