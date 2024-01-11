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

const val MAX_NAME_LEN = 8
const val MIN_PIN_LEN = 4

private val LogIt = GetLog("BU.wally.NewAccountScreen")

data class NewAccountState(
  val hideUntilPinEnter: Boolean = false,
  val errorMessage: String = "",
  val accountName: String = "",
  val validAccountName: Boolean = accountName.length.let { it > 0 && it <= MAX_NAME_LEN },
  val recoveryPhrase: String = "",
  val validOrNoRecoveryPhrase: Boolean = true,
  val pin: String = "",
  val validOrNoPin: Boolean = true,
  val isSuccessDialogOpen: Boolean = false
)

val chainToName: Map<ChainSelector, String> = mapOf(
  ChainSelector.NEXATESTNET to "tNexa", ChainSelector.NEXAREGTEST to "rNexa", ChainSelector.NEXA to "nexa",
  ChainSelector.BCH to "bch", ChainSelector.BCHTESTNET to "tBch", ChainSelector.BCHREGTEST to "rBch"
)
fun ProposeAccountName(cs: ChainSelector):String?
{
    val a = wallyApp
    if ((cs != null) && (a != null))
    {
        var count = 0
        var countS = ""
        while(true)
        {
            val proposedName = chainToName[cs] + countS  // countS should be empty string if 0, otherwise a number
            if ((proposedName != null) && !a.accounts.contains(proposedName))  // If there's already a default choice, then don't offer one
            {
                return (proposedName)
            }
            count+=1
            countS = count.toString()
        }
    }
    return null
}

@Composable fun NewAccountScreen(accounts: MutableState<ListifyMap<String, Account>>, devMode: Boolean, nav: ScreenNav)
{
    val blockchains = supportedBlockchains.filter { devMode || it.value.isMainNet }
    var selectedBlockChain by remember { mutableStateOf(blockchains.entries.first()) }
    var newAcState by remember { mutableStateOf( NewAccountState(accountName = ProposeAccountName(selectedBlockChain.value) ?: "") ) }

    NewAccountScreenContent(
      newAcState,
      selectedBlockChain,
      blockchains,
      onChainSelected = {
          val oldname = ProposeAccountName(selectedBlockChain.value)
          selectedBlockChain = it
          if (oldname == newAcState.accountName)  // name remains the proposed default, so propose a different one
          {
              val name = ProposeAccountName(selectedBlockChain.value)
              if (name != null) newAcState = newAcState.copy(accountName = name)
          }
                        },
      onNewAccountName = {
          val actNameValid = (it.length > 0 && it.length <= MAX_NAME_LEN) && (!containsAccountWithName(accounts.value, it))
          newAcState = newAcState.copy(
            accountName = it,
            validAccountName = actNameValid,
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
          val validOrNoPin = (it.isEmpty() || (it.length >= MIN_PIN_LEN))
          newAcState = newAcState.copy(pin = it, validOrNoPin = validOrNoPin)
      },
      onHideUntilPinEnterChanged = {
          newAcState =  newAcState.copy(hideUntilPinEnter = it)
      },
      onClickCreateAccount =  {
          var inputValid = false
          val words = processSecretWords(newAcState.recoveryPhrase)
          val incorrectWords = Bip39InvalidWords(words)
          newAcState = newAcState.copy(
            errorMessage = ""
          )

          if (newAcState.accountName.isEmpty() || newAcState.accountName.length > 8)
          {
              newAcState = newAcState.copy(errorMessage = (newAcState.errorMessage + i18n(S.invalidAccountName)))

          }
          else if (containsAccountWithName(accounts.value, newAcState.accountName)) {
              newAcState = newAcState.copy(errorMessage = (newAcState.errorMessage + i18n(S.invalidAccountName)))
          }
          else if (words.size > 12)
          {
              newAcState = newAcState.copy(errorMessage = i18n(S.TooManyRecoveryWords))
          }
          else if (words.size in 1..11)
          {
              newAcState = newAcState.copy(errorMessage = i18n(S.NotEnoughRecoveryWords))
          }
          else if (incorrectWords.isNotEmpty())
          {
              newAcState = newAcState.copy(errorMessage = i18n(S.invalidRecoveryPhrase))
          }
          else if (newAcState.pin.isNotEmpty() && newAcState.pin.length < MIN_PIN_LEN) {
              newAcState = newAcState.copy(errorMessage = i18n(S.InvalidPIN))
          }
          else inputValid = true

          val flags: ULong = if (newAcState.hideUntilPinEnter) ACCOUNT_FLAG_HIDE_UNTIL_PIN else ACCOUNT_FLAG_NONE

          if (inputValid && words.size == 12) // account recovery
          {
              Thread("recoverAccount")
              {
                  newAcState = try {
                      wallyApp!!.recoverAccount(newAcState.accountName, flags, newAcState.pin, words.joinToString(" "), selectedBlockChain.value, null, null, null)
                      triggerAssignAccountsGuiSlots()
                      nav.back()
                      newAcState.copy(isSuccessDialogOpen = false)
                  }
                  catch (e: Error)
                  {
                      displayUnexpectedError(e)
                      newAcState.copy(errorMessage = i18n(S.unknownError))
                  }
                  catch (e: Exception)
                  {
                      displayUnexpectedException(e)
                      newAcState.copy(errorMessage = i18n(S.unknownError))
                  }

              }
          }
          if (inputValid && words.isEmpty())
          {
              later {
                  val account = wallyApp!!.newAccount(newAcState.accountName, flags, newAcState.pin, selectedBlockChain.value)
                  newAcState = if (account == null)
                  {
                      displayError(i18n(S.unknownError))
                      newAcState.copy(errorMessage = i18n(S.unknownError))
                  }
                  else
                  {
                      triggerAssignAccountsGuiSlots()
                      nav.back()
                      newAcState.copy(isSuccessDialogOpen = false)
                  }
              } // Can't happen in GUI thread
          }
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
  onChainSelected: (Map.Entry<String, ChainSelector>) -> Unit,
  onNewAccountName: (String) -> Unit,
  onNewRecoveryPhrase: (String) -> Unit,
  onPinChange: (String) -> Unit,
  onHideUntilPinEnterChanged: (Boolean) -> Unit,
  onClickCreateAccount: () -> Unit
)
{
    Column(
      modifier = Modifier.padding(4.dp).fillMaxSize()
    ) {
        if (newAcState.errorMessage.isNotEmpty())
        {
            WallyError(newAcState.errorMessage)
        }
        BlockchainDropDownMenu(selectedChain, blockchains, onChainSelected)
        AccountNameInput(newAcState.accountName, newAcState.validAccountName, onNewAccountName)
        Spacer(Modifier.height(10.dp))
        RecoveryPhraseInput(newAcState.recoveryPhrase, newAcState.validOrNoRecoveryPhrase, onNewRecoveryPhrase)
        Spacer(Modifier.height(10.dp))
        pinInput(newAcState.pin, newAcState.validOrNoPin, onPinChange)
        Text(i18n(S.PinSpendingUnprotected), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        WallySwitch(newAcState.hideUntilPinEnter, S.PinHidesAccount, onHideUntilPinEnterChanged)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth())
            { WallyRoundedTextButton(i18n(S.createAccount), onClick = onClickCreateAccount) }
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

@Composable fun CheckOrX(valid: Boolean)
{
    if (valid)
        Icon(imageVector = Icons.Default.Check, tint = colorValid, contentDescription = null)
    else
        Icon(Icons.Default.Clear, tint = colorError, contentDescription = null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AccountNameInput(accountName: String, validAccountName: Boolean, onNewAccountName: (String) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        CheckOrX(validAccountName)
        Spacer(Modifier.width(8.dp))
        Text(i18n(S.AccountName))
        Spacer(Modifier.width(8.dp))
        WallyTextEntry(
          value = accountName,
          onValueChange = onNewAccountName,
          //colors = TextFieldDefaults.textFieldColors(containerColor = Color.Transparent),
          //placeholder = { Text(i18n(S.AccountNameHint)) },
          //singleLine = true
          modifier = Modifier.weight(1f)
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
            CheckOrX(validOrNoRecoveryPhrase)
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
            CheckOrX(validOrNoPin)
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
