@file:OptIn(ExperimentalUnsignedTypes::class)

package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyHalfDivider
import info.bitcoinunlimited.www.wally.ui.theme.colorDefault
import info.bitcoinunlimited.www.wally.ui.theme.colorError
import info.bitcoinunlimited.www.wally.ui.theme.colorValid
import info.bitcoinunlimited.www.wally.ui.views.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*
import org.nexa.libnexakotlin.SearchDerivationPathActivity
import org.nexa.threads.*

const val MAX_NAME_LEN_UI2 = 16

private val LogIt = org.nexa.libnexakotlin.GetLog("BU.wally.NewAccountScreen")

val supportedBlockchains =
  mapOf(
    "NEXA" to ChainSelector.NEXA,
    // "BCH (Bitcoin Cash)" to ChainSelector.BCH,
    "TNEX (Testnet Nexa)" to ChainSelector.NEXATESTNET,
    "RNEX (Regtest Nexa)" to ChainSelector.NEXAREGTEST,
    // "TBCH (Bitcoin Cash)" to ChainSelector.BCHTESTNET,
    // "RBCH (Bitcoin Cash)" to ChainSelector.BCHREGTEST
  )

fun fromFinestUnit(amount: Long, chainSelector:ChainSelector): BigDecimal
{
    val factor = when (chainSelector)
    {
        ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> SATperNEX
        ChainSelector.BCH, ChainSelector.BCHREGTEST, ChainSelector.BCHTESTNET -> SATperUBCH
    }
    val f : BigDecimal = BigDecimal.fromInt(factor.toInt())
    return CurrencyDecimal(amount) / f
}

const val MAX_NAME_LEN = 8
const val MIN_PIN_LEN = 4

data class NewAccountState(
  val hideUntilPinEnter: Boolean = false,
  val errorMessage: String = "",
  val accountName: String = "",
  val validAccountName: Boolean = accountName.length.let { it > 0 && it <= MAX_NAME_LEN },
  val recoveryPhrase: String = "",
  val validOrNoRecoveryPhrase: Boolean = true,
  val pin: String = "",
  val validOrNoPin: Boolean = true,
  val earliestActivity: Long? = null,
  val earliestActivityHeight: Int = -1,  // -1 means not even looking (either done and found nothing, or not applicable due to bad phrase or similar)
  val discoveredAccountHistory: List<TransactionHistory> = listOf(),
  val discoveredAddresses: Set<PayDestination> = setOf(),
  val discoveredAddressCount: Long = 0,
  val discoveredAccountBalance: Long = 0,
  val discoveredAddressIndex: Int = 0,
  val discoveredTip: iBlockHeader? = null
)

val chainToName: Map<ChainSelector, String> = mapOf(
  ChainSelector.NEXATESTNET to "tNexa", ChainSelector.NEXAREGTEST to "rNexa", ChainSelector.NEXA to "nexa",
  ChainSelector.BCH to "bch", ChainSelector.BCHTESTNET to "tBch", ChainSelector.BCHREGTEST to "rBch"
)
fun ProposeAccountName(cs: ChainSelector):String?
{
    val a = wallyApp
    if (a != null)
    {
        var count = 0
        var countS = ""
        while(true)
        {
            val proposedName = chainToName[cs] + countS  // countS should be empty string if 0, otherwise a number
            if (!a.accounts.contains(proposedName))  // If there's already a default choice, then don't offer one
            {
                return (proposedName)
            }
            count+=1
            countS = count.toString()
        }
    }
    return null
}

data class NewAccountDriver(val peekText: String?= null, val fastForwardText: String?= null, val earliestActivity:Long? = null, val earliestActivityHeight:Int? = null)
val newAccountDriver = Channel<NewAccountDriver>()
fun displayRecoveryInfo(s:String) = later { newAccountDriver.send(NewAccountDriver(s)) }
fun displayFastForwardInfo(s:String) = later { newAccountDriver.send(NewAccountDriver(fastForwardText=s)) }

fun updateRecoveryInfo(earliestActivity:Long?, earliestActivityHeight:Int?, s:String? )
{
    later { newAccountDriver.send(NewAccountDriver(s, earliestActivity=earliestActivity, earliestActivityHeight=earliestActivityHeight)) }
}

fun launchRecoverAccountThread(acState: NewAccountState, flags: ULong, secret: String, chainSelector: ChainSelector)
{
    Thread("recoverAccount")
    {
        try
        {
            wallyApp!!.recoverAccount(acState.accountName, flags, acState.pin, secret, chainSelector, acState.earliestActivity, acState.earliestActivityHeight.toLong(), null)
            triggerAssignAccountsGuiSlots()
        }
        catch (e: Error)
        {
            displayUnexpectedError(e)
            // acState.copy(errorMessage = i18n(S.unknownError))
        }
        catch (e: Exception)
        {
            displayUnexpectedException(e)
            // acState.copy(errorMessage = i18n(S.unknownError))
        }
    }
}

var newAccountState: MutableStateFlow<NewAccountState> = MutableStateFlow(NewAccountState())


fun CreateAccountRecoveryThread(acState: NewAccountState, chainSelector: ChainSelector)
{
    Thread("actRecovery")
    {
        millisleep(200U)
        val flags: ULong = if (acState.hideUntilPinEnter) ACCOUNT_FLAG_HIDE_UNTIL_PIN else ACCOUNT_FLAG_NONE
        val words = processSecretWords(acState.recoveryPhrase)
        try
        {
            wallyApp!!.recoverAccount(acState.accountName, flags, acState.pin, words.joinToString(" "), chainSelector, acState.discoveredAccountHistory, acState.discoveredAddresses, acState.discoveredTip!!, acState.discoveredAddressIndex)
            triggerAssignAccountsGuiSlots()
        }
        catch (e: Error)
        {
            displayUnexpectedError(e)
        }
        catch (e: Exception)
        {
            displayUnexpectedException(e)
        }
    }
}


@Composable fun NewAccountScreen(accounts: State<ListifyMap<String, Account>>, devMode: Boolean)
{
    val blockchains = supportedBlockchains.filter { devMode || it.value.isMainNet }
    var selectedBlockChain by remember { mutableStateOf(blockchains.entries.first().toPair()) }

    // Typically this code is just run the first time thru, to propose an account name based on the default blockchain
    LaunchedEffect(Unit)
    {
        if (newAccountState.value.accountName == "")
        {
            val name = ProposeAccountName(selectedBlockChain.second)
            if (name != null) newAccountState.value = newAccountState.value.copy(accountName = name, validAccountName = (name != null))
        }
    }

    val newAcState = newAccountState.collectAsState().value

    var recoverySearchText by remember { mutableStateOf("") }
    var fastForwardText by remember { mutableStateOf<String?>(null) }
    var creatingAccountLoading by remember { mutableStateOf(false) }

    val aborter = remember { mutableStateOf(Objectify<Boolean>(false)) }
    var createClicks by remember {mutableStateOf(0) }  // Some operations require you click create twice

    var firstActThread:iThread? = null
    var allActThread:iThread? = null

    // When we leave this screen, we want to wipe most of the data and and info.  The user should not nav away from this page
    // and expect to come back to finish account set up.
    nav.onDepart {
        // close out any search thread that are running
        aborter.value.obj = true
        // forget the secret for security reasons & almost everything else for consistency
        newAccountState.value = newAccountState.value.copy(errorMessage = "", recoveryPhrase = "", validOrNoRecoveryPhrase = true, pin = "", validOrNoPin = true, earliestActivityHeight = -1, earliestActivity = null,
          discoveredAccountHistory = listOf(), discoveredAccountBalance = 0, discoveredAddressCount = -1, discoveredAddressIndex = 0, discoveredTip = null)
        // forget any info text
        recoverySearchText = ""
        fastForwardText = ""
        creatingAccountLoading = false
        createClicks = 0
        // make a new thread aborter for next time
        aborter.value = Objectify(false)
    }


    fun FinalDataCheck(): Boolean
    {
        var inputValid = false
        val words = processSecretWords(newAcState.recoveryPhrase)
        val incorrectWords = bip39InvalidWords(words)

        // Clear any old error
        newAccountState.value = newAccountState.value.copy(
          errorMessage = ""
        )

        if (newAcState.accountName.isEmpty() || newAcState.accountName.length > 16)
        {
            newAccountState.value = newAccountState.value.copy(errorMessage = (newAcState.errorMessage + i18n(S.invalidAccountName)))
        }
        else if (containsAccountWithName(accounts.value, newAcState.accountName)) {
            newAccountState.value = newAccountState.value.copy(errorMessage = (newAcState.errorMessage + i18n(S.invalidAccountName)))
        }
        else if (words.size > 12)
        {
            newAccountState.value = newAccountState.value.copy(errorMessage = i18n(S.TooManyRecoveryWords), earliestActivityHeight = -1)
        }
        else if (words.size in 1..11)
        {
            newAccountState.value = newAccountState.value.copy(errorMessage = i18n(S.NotEnoughRecoveryWords), earliestActivityHeight = -1)
        }
        else if (incorrectWords.isNotEmpty())
        {
            newAccountState.value = newAccountState.value.copy(errorMessage = i18n(S.invalidRecoveryPhrase), earliestActivityHeight = -1)
        }
        else if (newAcState.pin.isNotEmpty() && newAcState.pin.length < MIN_PIN_LEN)
        {
            newAccountState.value = newAccountState.value .copy(errorMessage = i18n(S.InvalidPIN))
        }
        else inputValid = true

        return inputValid
    }

    fun CleanState()
    {
        newAccountState.value = NewAccountState()
    }

    fun CreateDiscoveredAccount()
    {
        var inputValid = FinalDataCheck()

        // data checks specific to discovered accounts
        if (newAccountState.value.discoveredTip == null || newAccountState.value.discoveredAccountHistory == null)
        {
            newAccountState.value = newAccountState.value.copy(errorMessage = i18n(S.NewAccountSearchFailure), earliestActivityHeight = -1)
            inputValid = false
        }

        if (inputValid)
        {
            // Freeze a copy of the data, for use in the deferred account creation
            val acState = newAccountState.value.copy()
            val chainSelector = selectedBlockChain.second
            aborter.value.obj = true
            CleanState()
            nav.back()  // since the data is wiped when we go back
            // get account creation out of the UI thread
            // launching a co-routine somehow delays the UI update by seconds
            // Also, by wrapping the thread launch in a non-compose function,
            // we ensure that the compose context is not imported into the thread
            CreateAccountRecoveryThread(acState, chainSelector)
        }

    }

    fun CreateSyncAccount()
    {
        val inputValid = FinalDataCheck()
        // Grab all the data because when I go back it will be wiped from the UX
        val acState = newAccountState.value.copy()
        val chainSelector = selectedBlockChain.second
        val flags: ULong = if (acState.hideUntilPinEnter) ACCOUNT_FLAG_HIDE_UNTIL_PIN else ACCOUNT_FLAG_NONE

        if (inputValid)
        {
            val words = processSecretWords(acState.recoveryPhrase)
            if (words.size == 12) // account recovery
            {
                if ((createClicks == 0) && (acState.earliestActivity == null))
                {
                    // We are looking for confirmation here so do not clean the state
                    createClicks += 1
                    recoverySearchText = i18n(S.creatingNoHistoryAccountWarning)
                }
                else
                {
                    CleanState()  // Clean it after taking a copy but right away
                    creatingAccountLoading = true
                    launchRecoverAccountThread(acState, flags, words.joinToString(" "), chainSelector)
                    nav.back()
                }
            }
            else if (words.isEmpty())
            {
                CleanState()  // Clean it after taking a copy but right away
                nav.back()
                later {
                    val account = wallyApp!!.newAccount(acState.accountName, flags, acState.pin, chainSelector)
                    if (account == null)
                    {
                        displayError(i18n(S.unknownError))
                        // acState.copy(errorMessage = i18n(S.unknownError))
                    }
                    else
                    {
                        triggerAssignAccountsGuiSlots()
                    }
                } // Can't happen in GUI thread
            }
        }
    }


    LaunchedEffect(true)
    {
        for (c in newAccountDriver)
        {
            // LogIt.info(sourceLoc() + ": external screen driver received")
            c.peekText?.let {
                recoverySearchText = it
            }
            c.fastForwardText?.let {
                fastForwardText = it
            }
            c.earliestActivity?.let {
                newAccountState.value = newAccountState.value.copy(earliestActivity = it)
            }
            c.earliestActivityHeight?.let {
                // newAcState = newAcState.copy(earliestActivityHeight = it)
                newAccountState.value = newAccountState.value.copy(earliestActivityHeight = it)
            }
        }
    }

    // unfocus is not being called if the focused composable is destroyed
    // so this catch-all puts the navbar, etc back up when the new account screen
    // goes away.
    DisposableEffect(Unit) {
        onDispose {
            UxInTextEntry(false)
        }
    }

    fun HandleRecoveryPhrase(userInput: String, force: Boolean = false)
    {
        val words = processSecretWords(userInput)
        val valid = isValidOrEmptyRecoveryPhrase(words)
        val priorPhrase = newAcState.recoveryPhrase
        newAccountState.value = newAccountState.value.copy(recoveryPhrase = userInput, validOrNoRecoveryPhrase = valid)
        if (force || words != processSecretWords(priorPhrase))  // If the recovery phrase is equivalent nothing to do, otherwise set the new one
        {
            recoverySearchText = ""  // phrase changed so need to search again
            // If the recovery phrase changes materially, we need to rediscover the wallet
            newAccountState.value = newAccountState.value.copy(earliestActivity = null, earliestActivityHeight = if (valid && words.isNotEmpty()) 0 else -1, discoveredAccountBalance = 0L, discoveredTip = null, discoveredAccountHistory = listOf(), discoveredAddressIndex = 0, discoveredAddressCount = 0)
            if (valid && words.size == 12) // Launch the wallet discoverer if the recovery phrase is ok
            {
                // If the recovery phrase is good, let's peek at the blockchain to see if there's activity
                // thread(true, true, null, "peekWallet") // kotlin api does not offer stack size setting
                aborter.value.obj = true  // Abort the current peek
                firstActThread?.join()
                allActThread?.join()
                aborter.value = Objectify<Boolean>(false)  // and create a new object for the next one
                recoverySearchText = i18n(S.NewAccountSearchingForTransactions)

                LogIt.info(sourceLoc() + ": launching wallet peek")
                firstActThread = Thread("actPeek") {
                    try
                    {
                        peekFirstActivity(words.joinToString(" "), selectedBlockChain.second, aborter.value)
                    }
                    catch (e: Exception)
                    {
                        recoverySearchText = i18n(S.NewAccountSearchFailure)
                        LogIt.severe(sourceLoc() + "wallet peek error: " + e.toString())
                    }
                }

                allActThread = Thread("actSearch") {
                    try
                    {
                        searchAllActivity(words.joinToString(" "), selectedBlockChain.second, aborter.value)
                    }
                    catch (e: Exception)
                    {
                        displayFastForwardInfo(i18n(S.NoNodes))
                        //LogIt.severe(sourceLoc() + " wallet search error: " + e.toString())
                        //LogIt.severe(e.stackTraceToString())
                        displayUnexpectedException(e)
                    }
                }
            }
            else
                recoverySearchText == ""
        }
    }

    NewAccountScreenContent(
      recoverySearchText,
      fastForwardText,
      selectedBlockChain,
      blockchains,
      onChainSelected = {
          if (it != selectedBlockChain)  // Don't do anything if we selected the same as we already were
          {
              selectedBlockChain = it
              val name = ProposeAccountName(selectedBlockChain.second)
              if (name != null) newAccountState.value = newAccountState.value.copy(accountName = name, validAccountName = (name != null))
              // We've changed the blockchain, so we need to rediscover any activity on the new one.
              HandleRecoveryPhrase(newAccountState.value.recoveryPhrase, force = true)
          }
                        },
      onNewAccountName = {
          val actNameValid = (it.length > 0 && it.length <= MAX_NAME_LEN_UI2) && (!containsAccountWithName(accounts.value, it))
          newAccountState.value = newAccountState.value.copy(
            accountName = it,
            validAccountName = actNameValid,
          )
      },
      onNewRecoveryPhrase = {
          HandleRecoveryPhrase(it)
      },
      onPinChange = {
          val validOrNoPin = (it.isEmpty() || ((it.length >= MIN_PIN_LEN) && it.onlyDigits()) )
          if (it.onlyDigits())
          {
              newAccountState.value = newAccountState.value.copy(pin = it, validOrNoPin = validOrNoPin)
              it
          }
          else newAcState.pin  // refuse to change if nondigits are in the field
      },
      onHideUntilPinEnterChanged = {
          newAccountState.value = newAccountState.value.copy(hideUntilPinEnter = it)
      },
      onClickCreateAccount =  { CreateSyncAccount() },
      onClickCreateDiscoveredAccount = { CreateDiscoveredAccount() },
      creatingAccountLoading
    )
}

@Composable fun NewAccountScreenContent(
  recoverySearchText: String,
  fastForwardText: String?,
  selectedChain: Pair<String, ChainSelector>,
  blockchains: Map<String, ChainSelector>,
  onChainSelected: (Pair<String, ChainSelector>) -> Unit,
  onNewAccountName: (String) -> Unit,
  onNewRecoveryPhrase: (String) -> Unit,
  onPinChange: (String) -> String,
  onHideUntilPinEnterChanged: (Boolean) -> Unit,
  onClickCreateAccount: () -> Unit,
  onClickCreateDiscoveredAccount: () -> Unit,
  creatingAccountLoading: Boolean
)
{
    val newAcState by newAccountState.collectAsState()
    Column(
      modifier = Modifier.padding(4.dp).fillMaxSize()
    ) {
        if (newAcState.errorMessage.isNotEmpty())
        {
            WallyError(newAcState.errorMessage)
        }
        WallyDropDown<ChainSelector>(selectedChain, blockchains, onChainSelected, modifier = Modifier.testTag("selectBlockchain"))
        AccountNameInput(newAcState.accountName, newAcState.validAccountName, onNewAccountName)
        Spacer(Modifier.height(5.dp))
        RecoveryPhraseInput(newAcState.recoveryPhrase, newAcState.validOrNoRecoveryPhrase, onNewRecoveryPhrase)
        Spacer(Modifier.height(5.dp))
        pinInput(newAcState.pin, newAcState.validOrNoPin, onPinChange)
        Text(i18n(S.PinSpendingUnprotected), fontSize = 14.sp)
        Spacer(Modifier.height(5.dp))

        val enabled = newAcState.pin.length >= MIN_PIN_LEN
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
            .clickable(enabled) {
                onHideUntilPinEnterChanged(!newAcState.hideUntilPinEnter)
            }.testTag("PinHidesAccount")
        ) {
            Checkbox(
              enabled = enabled,
              checked = newAcState.hideUntilPinEnter,
              onCheckedChange = { onHideUntilPinEnterChanged(it) }
            )
            val textColor = if (enabled) Color.Unspecified else Color.Gray
            Text(i18n(S.HiddenAccount), color = textColor)
        }
        Text(i18n(S.HiddenAccountExplainer), fontSize = 14.sp)
        Spacer(Modifier.height(5.dp))
        if (!creatingAccountLoading)
        {
            // fast forward search
            val discoveredSomething = newAcState.discoveredAccountHistory.size > 0
            if (discoveredSomething)
            {
                Row(Modifier.fillMaxWidth()) {
                    ResImageView("icons/check.xml", modifier = Modifier.size(50.dp))
                    CenteredText(i18n(S.discoveredAccountDetails) % mapOf("tx" to newAcState.discoveredAccountHistory.size.toString(), "addr" to newAcState.discoveredAddressCount.toString(),
                      "bal" to NexaFormat.format(fromFinestUnit(newAcState.discoveredAccountBalance, chainSelector = selectedChain.second)), "units" to (chainToDisplayCurrencyCode[selectedChain.second] ?:"")))
                }
                Row(Modifier.fillMaxWidth()) { CenteredText(i18n(S.discoveredWarning)) }
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().testTag("CreateDiscoveredAccount"))
                {
                    Button(onClick = onClickCreateDiscoveredAccount) {
                        Text(i18n(S.createDiscoveredAccount))
                    }
                }
            }
            else
            {
                if (fastForwardText != null) CenteredText(fastForwardText)
                else if (newAcState.earliestActivityHeight >= 0)
                {
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.size(50.dp)) {
                            LoadingAnimationContent()
                        }
                        CenteredText(i18n(S.NewAccountSearchingForAllTransactions))
                    }
                }
            }
            WallyHalfDivider()
            // Full sync
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // I'm cheating a bit here and using the contents of the recoverySearchText to pick what icon to show
                if (recoverySearchText == i18n(S.NewAccountSearchingForTransactions))
                {
                    Box(Modifier.size(50.dp)) {
                            LoadingAnimationContent()
                        }
                }
                else if (recoverySearchText == "")
                    Spacer(modifier = Modifier.size(10.dp))
                else if (newAcState.earliestActivity != null)
                    ResImageView("icons/check.xml", modifier = Modifier.size(50.dp))
                else if (recoverySearchText.length < 200)
                    Icon(Icons.Default.Clear, modifier = Modifier.size(50.dp), tint = colorError, contentDescription = null)

                CenteredText(recoverySearchText)
            }
            if (newAcState.earliestActivity != null) Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth())
            {
                OutlinedButton(onClick = onClickCreateAccount) {
                    Text(i18n(S.createSyncAccount))
                }
            }
            else Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth())
            {
                 Button(onClick = onClickCreateAccount, modifier = Modifier.testTag("onClickCreateAccount")) {
                     Text(i18n(S.createNewAccount))
                 }
            }

        }
        else CenteredSectionText(i18n(S.Processing))
    }
}

@Composable fun CheckOrX(valid: Boolean, testTag: String?=null)
{
    val focusManager = LocalFocusManager.current
    var mod = Modifier.clickable { focusManager.clearFocus() }
    if (testTag != null) mod = mod.testTag(testTag + if (valid) "C" else "X")
    if (valid)
        Icon(imageVector = Icons.Default.Check, tint = colorValid, contentDescription = null, modifier = mod)
    else  // For some reason Clear is a red X
        Icon(Icons.Default.Clear, tint = colorError, contentDescription = null, modifier = mod)
}

@Composable fun AccountNameInput(accountName: String, validAccountName: Boolean, onNewAccountName: (String) -> Unit)
{
    val focusManager = LocalFocusManager.current
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        CheckOrX(validAccountName, "AccountName_")
        Spacer(Modifier.width(8.dp))
        Text(i18n(S.AccountName), modifier = Modifier.clickable { focusManager.clearFocus() })
        Spacer(Modifier.width(8.dp))
        WallyTextEntry(
          value = accountName,
          onValueChange = onNewAccountName,
          modifier = Modifier.weight(1f).testTag("AccountNameInput"),
          bkgCol = Color.White
        )
    }
}

@Composable fun RecoveryPhraseInput(recoveryPhrase: String, validOrNoRecoveryPhrase: Boolean, onValueChange: (String) -> Unit)
{
    val focusManager = LocalFocusManager.current
    val ia = remember { MutableInteractionSource() }

    LaunchedEffect(ia) {
        ia.interactions.collect {
            when(it) {
                // Hover for mouse platforms, Focus for touch platforms
                is HoverInteraction.Enter, is FocusInteraction.Focus -> {
                    UxInTextEntry(true)
                }
                is HoverInteraction.Exit, is FocusInteraction.Unfocus -> {
                    UxInTextEntry(false)
                }
            }
        }
    }

    val scale = if (platform().spaceConstrained) FontScale(0.75) else FontScale(1.0)
    Column {
        Text(i18n(S.AccountRecoveryPhrase), modifier = Modifier.clickable { focusManager.clearFocus() })
        Spacer(Modifier.width(8.dp))
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
            CheckOrX(validOrNoRecoveryPhrase, "recoveryPhrase_")
            TextField(
              value = recoveryPhrase,
              onValueChange = onValueChange,
              interactionSource = ia,
              colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
              placeholder = { Text(i18n(S.LeaveEmptyNewWallet)) },
              minLines = 1,
              maxLines = 4,
              modifier = Modifier.fillMaxWidth().testTag("RecoveryPhraseInput"),
              textStyle = TextStyle(fontSize = scale),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }
    }
}

@Composable fun pinInput(pin: String, validOrNoPin: Boolean, onPinChange : (String) -> String)
{
    val focusManager = LocalFocusManager.current
    Column {
        Text(i18n(S.CreatePIN), modifier = Modifier.clickable { focusManager.clearFocus() })
        Spacer(Modifier.width(8.dp))
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
            CheckOrX(validOrNoPin, "pin_")
            WallyDigitEntry(pin,modifier = Modifier.weight(1f).testTag("NewAccountPinInput"), bkgCol = Color.White ,onValueChange = onPinChange)
        }
    }
}


/** Searches for activity, returning a pair of epoch time, height that corresponds to the first time the wallet was used */
fun searchFirstActivityUi2(getEc: () -> ElectrumClient, chainSelector: ChainSelector, count: Int, secretDerivation: (Int) -> ByteArray, activityFound: ((Long, Int) -> Boolean)? = null): Pair<Long, Int>?
{
    var index = 0
    var ret: Pair<Long, Int>? = null
    while (index < count)
    {
        val newSecret = secretDerivation(index)
        val us = UnsecuredSecret(newSecret)

        val dests = mutableListOf<SatoshiScript>(Pay2PubKeyHashDestination(chainSelector, us, index.toLong()).lockingScript())  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
        //LogIt.info(sourceLoc() + " " + name + ": New Destination " + tmp.toString() + ": " + dest.address.toString())
        if (chainSelector.hasTemplates)
            dests.add(Pay2PubKeyTemplateDestination(chainSelector, us, index.toLong()).lockingScript())

        for (dest in dests)
        {
            try
            {
                val use = getEc().getFirstUse(dest, 10000)
                if (use.block_hash != null)
                {
                    val bh = use.block_height
                    if (bh != null)
                    {
                        LogIt.info(sourceLoc() +": Found first use activity at index $index in ${dest.address.toString()}")
                        val headerBin = getEc().getHeader(bh)
                        val blkHeader = blockHeaderFor(chainSelector, BCHserialized(headerBin, SerializationType.HASH))
                        if (ret == null || blkHeader.time < ret.first)
                        {
                            activityFound?.invoke(blkHeader.time, bh)
                            ret = Pair(blkHeader.time, bh)
                        }
                    }
                }
                else
                {
                    LogIt.info(sourceLoc() +": didn't find first use activity at index $index in ${dest.address.toString()}")
                }
            }
            catch (e: ElectrumNotFound)
            {
                LogIt.info(sourceLoc() + ": didn't find first use activity at index $index in ${dest.address.toString()}")
            }
        }
        index++
    }
    return ret
}

var WALLET_RECOVERY_DERIVATION_PATH_FIRST_USE_DEPTH = 30
fun peekFirstActivity(secretWords: String, chainSelector: ChainSelector, aborter: Objectify<Boolean>)
{
    val net = connectBlockchain(chainSelector).net

    var ec = retry(10) {
        val ec = net?.getElectrum()
        if (ec == null)
        {
            displayRecoveryInfo(i18n(S.ElectrumNetworkUnavailable))
            millisleep(1000U)
        }
        ec
    }

    try
    {
        if (aborter.obj) return

        val passphrase = "" // TODO: support a passphrase
        val secret = generateBip39Seed(secretWords, passphrase)

        val addressDerivationCoin = Bip44AddressDerivationByChain(chainSelector)

        LogIt.info("Searching in ${addressDerivationCoin}")
        var earliestActivityP =
          searchFirstActivityUi2( {
              if (ec.open) return@searchFirstActivityUi2 ec
              ec = net.getElectrum()
              return@searchFirstActivityUi2(ec)
          }, chainSelector, WALLET_RECOVERY_DERIVATION_PATH_FIRST_USE_DEPTH, {
              libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, addressDerivationCoin, 0, false, it).first
          }, { time, height ->

              displayRecoveryInfo(i18n(S.Bip44ActivityNotice) + " " + (i18n(S.FirstUseDateHeightInfo) % mapOf(
                "date" to epochToDate(time),
                "height" to height.toString())
                ))
              updateRecoveryInfo(time, height, null)
              true
          })

        if (aborter.obj)
        {
            displayRecoveryInfo("")
            return
        }

        LogIt.info("Searching in ${AddressDerivationKey.ANY}")
        // Look for activity in the identity and common location
        val earliestActivityId =
          searchFirstActivity({
              if (ec.open) return@searchFirstActivity ec
              ec = net.getElectrum()
              return@searchFirstActivity(ec)
          }, chainSelector, WALLET_RECOVERY_IDENTITY_DERIVATION_PATH_SEARCH_DEPTH, {
              libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.ANY, 0, false, it).first
          })
        if (aborter.obj)
        {
            displayRecoveryInfo("")
            return
        }

        // Set earliestActivityP to the lesser of the two
        if (earliestActivityP == null) earliestActivityP = earliestActivityId
        else
        {
            if ((earliestActivityId != null) && (earliestActivityId.first < earliestActivityP.first)) earliestActivityP = earliestActivityId
        }
        if (aborter.obj)
        {
            displayRecoveryInfo("")
            return
        }

        if (earliestActivityP != null)
        {
            updateRecoveryInfo(earliestActivityP.first - 1, earliestActivityP.second, // -1 so earliest activity is just before the activity
              i18n(S.Bip44ActivityNotice) + " " + i18n(S.FirstUseDateHeightInfo) % mapOf(
                "date" to epochToDate(earliestActivityP.first),
                "height" to earliestActivityP.second.toString()
              ))
        }
        else
        {
            updateRecoveryInfo(null, -1, i18n(S.NoBip44ActivityNotice))
        }
    }
    finally
    {
        net.returnElectrum(ec)
    }
    LogIt.info(sourceLoc() +": Activity peek is complete")
}


fun searchAllActivity(secretWords: String, chainSelector: ChainSelector, aborter: Objectify<Boolean>, ecCnxn: ElectrumClient? = null)
{
    val net = connectBlockchain(chainSelector).net
    var ec: ElectrumClient? = null

    fun getEc():ElectrumClient
    {
        return retry(10) {
            val tmp = ec
            if (tmp != null && tmp.open) ec
            else
            {
                // LogIt.info(sourceLoc() + ": search activity, getting Electrum connection")
                try
                {
                    ec = net.getElectrum()
                }
                catch (e: ElectrumNoNodesException)
                {
                    displayFastForwardInfo(i18n(S.ElectrumNetworkUnavailable))
                    millisleep(200U)
                }
                LogIt.info(sourceLoc() + ": search activity, getting Electrum connection is $ec")
                ec
            }
        }
    }

    val passphrase = "" // TODO: support a passphrase
    val secret = generateBip39Seed(secretWords, passphrase)
    var addrText = ""
    var summaryText = ""
    var fromText = ""
    val runningBalance = atomic(0L)

    fun tderivation(coin: Long, account: Long, change: Boolean, index: Int): PayDestination
    {
        if (aborter.obj) throw EarlyExitException()
        val key = libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, coin, account, change, index).first
        val us = UnsecuredSecret(key)
        val dest = Pay2PubKeyTemplateDestination(chainSelector, us, index.toLong())
        addrText = "\n${i18n(S.Address)} ${index}"
        fromText = if (ec != null) "\n${i18n(S.fromColon)} ${ec?.logName}" else "\n${i18n(S.NoNodes)}"
        displayFastForwardInfo(i18n(S.NewAccountSearchingForAllTransactions) + fromText + addrText + summaryText)
        return dest
    }

    fun pderivation(coin: Long, account: Long, change: Boolean, index: Int): PayDestination
    {
        if (aborter.obj) throw EarlyExitException()
        val key = libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, coin, account, change, index).first
        val us = UnsecuredSecret(key)
        val dest = Pay2PubKeyHashDestination(chainSelector, us, index.toLong())
        addrText = "\n${i18n(S.Address)} ${index}"
        fromText = if (ec != null) "\n${i18n(S.fromColon)} ${ec?.logName}" else "\n${i18n(S.NoNodes)}"
        displayFastForwardInfo(i18n(S.NewAccountSearchingForAllTransactions) + fromText + addrText + summaryText)
        return dest
    }

    try
    {
        if (aborter.obj) return
        val (tip, tipHeight) = getEc().getTip()
        val addressDerivationCoin = Bip44AddressDerivationByChain(chainSelector)
        LogIt.info("Searching all activity in ${addressDerivationCoin}")


        val activity = try
        {
            val sdpa = SearchDerivationPathActivity(chainSelector,::getEc,true) {
                summaryText = "\n" + i18n(S.discoveredAccountDetails) % mapOf("tx" to it.txh.size.toString(), "addr" to it.addrCount.toString(),
                  "bal" to NexaFormat.format(fromFinestUnit(runningBalance.value + it.balance, chainSelector = chainSelector)), "units" to (chainToDisplayCurrencyCode[chainSelector] ?: ""))
                displayFastForwardInfo(i18n(S.NewAccountSearchingForAllTransactions) + fromText + addrText + summaryText)
            }
            val r = sdpa.search(WALLET_FULL_RECOVERY_DERIVATION_PATH_MAX_GAP) { tderivation(addressDerivationCoin, 0, false, it) }
            sdpa.finalize()
            r
        }
        catch (e: EarlyExitException)
        {
            return
        }
        if (aborter.obj)
        {
            displayFastForwardInfo("")
            return
        }
        runningBalance += activity.balance

        // TODO need to explicitly push nonstandard addresses into the wallet, by explicitly returning them.
        // otherwise the transactions won't be noticed by the wallet when we jam them in.
        LogIt.info("Searching in p2pkh ${addressDerivationCoin}")
        // Look for activity in the identity and common location
        val activity4 = try
        {
            val sdpa = SearchDerivationPathActivity(chainSelector, ::getEc,true) {
                summaryText = "\n" + i18n(S.discoveredAccountDetails) % mapOf("tx" to it.txh.size.toString(), "addr" to it.addrCount.toString(),
                    "bal" to NexaFormat.format(fromFinestUnit(runningBalance.value + it.balance, chainSelector = chainSelector)), "units" to (chainToDisplayCurrencyCode[chainSelector] ?:""))

                  displayFastForwardInfo(i18n(S.NewAccountSearchingForAllTransactions) + fromText + addrText + summaryText)
            }
            val r = sdpa.search(WALLET_FULL_RECOVERY_NONSTD_DERIVATION_PATH_MAX_GAP) { pderivation(addressDerivationCoin, 0, false, it) }
            sdpa.finalize()
            r
        }
        catch (e: EarlyExitException)
        {
            return
        }
        if (aborter.obj)
        {
            displayFastForwardInfo("")
            return
        }
        runningBalance += activity4.balance

        // TODO need to explicitly push nonstandard addresses into the wallet, by explicitly returning them.
        // otherwise the transactions won't be noticed by the wallet when we jam them in.
        LogIt.info("Searching in ${AddressDerivationKey.ANY}")
        // Look for activity in the identity and common location
        val activity2 = try
        {
            SearchDerivationPathActivity(chainSelector, ::getEc, true) {
                summaryText = "\n" + i18n(S.discoveredAccountDetails) % mapOf("tx" to it.txh.size.toString(), "addr" to it.addrCount.toString(),
                  "bal" to NexaFormat.format(fromFinestUnit(runningBalance.value + it.balance, chainSelector = chainSelector)), "units" to (chainToDisplayCurrencyCode[chainSelector] ?:""))
                displayFastForwardInfo(i18n(S.NewAccountSearchingForAllTransactions) + fromText + addrText + summaryText)
            }.search(WALLET_FULL_RECOVERY_NONSTD_DERIVATION_PATH_MAX_GAP) { tderivation(AddressDerivationKey.ANY, 0, false, it) }
        }
        catch (e: EarlyExitException)
        {
            return
        }
        if (aborter.obj)
        {
            displayFastForwardInfo("")
            return
        }
        runningBalance += activity2.balance

        val activity3 = try
        {
            SearchDerivationPathActivity(chainSelector, ::getEc, true) {
                summaryText = "\n" + i18n(S.discoveredAccountDetails) % mapOf("tx" to it.txh.size.toString(), "addr" to it.addrCount.toString(),
                  "bal" to NexaFormat.format(fromFinestUnit(runningBalance.value + it.balance, chainSelector = chainSelector)), "units" to (chainToDisplayCurrencyCode[chainSelector] ?:""))
                displayFastForwardInfo(i18n(S.NewAccountSearchingForAllTransactions) + fromText + addrText + summaryText)
            }.search(WALLET_FULL_RECOVERY_CHANGE_DERIVATION_PATH_MAX_GAP) { tderivation(addressDerivationCoin, 0, true, it) }
        }
        catch (e: EarlyExitException)
        {
            return
        }
        if (aborter.obj)
        {
            displayFastForwardInfo("")
            return
        }

        val act = mutableListOf<TransactionHistory>()
        act.addAll(activity.txh.values)
        activity.txh.clear()
        act.addAll(activity2.txh.values)
        activity2.txh.clear()
        act.addAll(activity3.txh.values)
        activity3.txh.clear()
        act.addAll(activity4.txh.values)
        activity4.txh.clear()

        val addrs = activity.addresses + activity2.addresses + activity3.addresses + activity4.addresses
        val addrCount = activity.addrCount + activity2.addrCount + activity3.addrCount + activity4.addrCount
        val bal = activity.balance + activity2.balance + activity3.balance + activity4.balance
        newAccountState.value = newAccountState.value.copy(discoveredAccountHistory = act, discoveredAddresses = addrs, discoveredAddressCount = addrCount, discoveredAccountBalance = bal, discoveredAddressIndex = activity.lastAddressIndex, discoveredTip = tip)
    }
    finally
    {
        ec?.let { net.returnElectrum(it) }
    }
    LogIt.info("Account search is complete")
}

/** Searches for activity, returning a pair of epoch time, height that corresponds to the first time the wallet was used */
fun searchFirstActivity(getEc: () -> ElectrumClient, chainSelector: ChainSelector, count: Int, secretDerivation: (Int) -> ByteArray, activityFound: ((Long, Int) -> Boolean)? = null): Pair<Long, Int>?
{
    var index = 0
    var ret: Pair<Long, Int>? = null
    while (index < count)
    {
        val newSecret = secretDerivation(index)
        val us = UnsecuredSecret(newSecret)

        val dests = mutableListOf<SatoshiScript>(Pay2PubKeyHashDestination(chainSelector, us, index.toLong()).lockingScript())  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
        //LogIt.info(sourceLoc() + " " + name + ": New Destination " + tmp.toString() + ": " + dest.address.toString())
        if (chainSelector.hasTemplates)
            dests.add(Pay2PubKeyTemplateDestination(chainSelector, us, index.toLong()).lockingScript())

        for (dest in dests)
        {
            try
            {
                val use = getEc().getFirstUse(dest, 10000)
                if (use.block_hash != null)
                {
                    val bh = use.block_height
                    if (bh != null)
                    {
                        LogIt.info(sourceLoc() +": Found first use activity at index $index in ${dest.address.toString()}")
                        val headerBin = getEc().getHeader(bh)
                        val blkHeader = blockHeaderFor(chainSelector, BCHserialized(headerBin, SerializationType.HASH))
                        if (ret == null || blkHeader.time < ret.first)
                        {
                            activityFound?.invoke(blkHeader.time, bh)
                            ret = Pair(blkHeader.time, bh)
                        }
                    }
                }
                else
                {
                    LogIt.info(sourceLoc() +": didn't find first use activity at index $index in ${dest.address.toString()}")
                }
            }
            catch (e: ElectrumNotFound)
            {
                LogIt.info(sourceLoc() + ": didn't find first use activity at index $index in ${dest.address.toString()}")
            }
        }
        index++
    }
    return ret
}

fun bracketActivity(ec: ElectrumClient, chainSelector: ChainSelector, giveUpGap: Int, secretDerivation: (Int) -> ByteArray): HDActivityBracket?
{
    var index = 0
    var lastFoundIndex = 0
    var startTime = 0L
    var startBlock = 0
    var lastTime = 0L
    var lastBlock = 0

    while (index < lastFoundIndex + giveUpGap)
    {
        val newSecret = secretDerivation(index)

        val dest = Pay2PubKeyHashDestination(chainSelector, UnsecuredSecret(newSecret), index.toLong())  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated

        try
        {
            val use = ec.getFirstUse(dest.lockingScript(), 10000)
            if (use.block_hash != null)
            {
                if (use.block_height != null)
                {
                    lastFoundIndex = index
                    lastBlock = use.block_height!!
                    if (startBlock == 0) startBlock = use.block_height!!
                }
            }
            else
            {
                LogIt.info("didn't find activity")
            }
        } catch (e: ElectrumNotFound)
        {
            LogIt.info("didn't find activity")
        }
        index++
    }

    if (startBlock == 0) return null  // Safe to use 0 because no spendable tx in genesis block

    if (true)
    {
        val headerBin = ec.getHeader(startBlock)
        val blkHeader = blockHeaderFor(chainSelector, BCHserialized(headerBin, SerializationType.HASH))
        startTime = blkHeader.time
    }
    if (true)
    {
        val headerBin = ec.getHeader(lastBlock)
        val blkHeader = blockHeaderFor(chainSelector, BCHserialized(headerBin, SerializationType.HASH))
        lastTime = blkHeader.time
    }

    return HDActivityBracket(startTime, startBlock, lastTime, lastBlock, lastFoundIndex)
}