package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import info.bitcoinunlimited.www.wally.ui.views.LoadingAnimation
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.channels.Channel
import org.nexa.libnexakotlin.*
import org.nexa.threads.Thread

private val supportedBlockchains =
  mapOf(
    "NEXA" to ChainSelector.NEXA,
    // "BCH (Bitcoin Cash)" to ChainSelector.BCH,
    "TNEX (Testnet Nexa)" to ChainSelector.NEXATESTNET,
    "RNEX (Regtest Nexa)" to ChainSelector.NEXAREGTEST,
    // "TBCH (Bitcoin Cash)" to ChainSelector.BCHTESTNET,
    // "RBCH (Bitcoin Cash)" to ChainSelector.BCHREGTEST
  )

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
  val isSuccessDialogOpen: Boolean = false,
  val earliestActivity: Long? = null,
  val earliestActivityHeight: Int = 0
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

data class NewAccountDriver(val peekText: String?= null, val earliestActivity:Long? = null, val earliestActivityHeight:Int? = null)
val newAccountDriver = Channel<NewAccountDriver>()
fun displayRecoveryInfo(s:String) = later { newAccountDriver.send(NewAccountDriver(s)) }

fun updateRecoveryInfo(earliestActivity:Long?, earliestActivityHeight:Int?, s:String? )
{
    later { newAccountDriver.send(NewAccountDriver(s, earliestActivity=earliestActivity, earliestActivityHeight=earliestActivityHeight)) }
}

@Composable fun NewAccountScreen(accounts: MutableState<ListifyMap<String, Account>>, devMode: Boolean, nav: ScreenNav)
{
    val blockchains = supportedBlockchains.filter { devMode || it.value.isMainNet }
    var selectedBlockChain by remember { mutableStateOf(blockchains.entries.first().toPair()) }
    var newAcState by remember { mutableStateOf( NewAccountState(accountName = ProposeAccountName(selectedBlockChain.second) ?: "") ) }

    var recoverySearchText by remember { mutableStateOf("") }

    var aborter = remember { mutableStateOf(Objectify<Boolean>(false)) }
    var createClicks by remember {mutableStateOf(0) }  // Some operations require you click create twice

    LaunchedEffect(true)
    {
        for (c in newAccountDriver)
        {
            LogIt.info(sourceLoc() + ": external screen driver received")
            c.peekText?.let {
                recoverySearchText = it
            }
            c.earliestActivity?.let {
                newAcState = newAcState.copy(earliestActivity = it)
            }
            c.earliestActivityHeight?.let {
                newAcState = newAcState.copy(earliestActivityHeight = it)
            }
        }
    }

    NewAccountScreenContent(
      newAcState,
      recoverySearchText,
      selectedBlockChain,
      blockchains,
      onChainSelected = {
          val oldname = ProposeAccountName(selectedBlockChain.second)
          selectedBlockChain = it
          if (oldname == newAcState.accountName)  // name remains the proposed default, so propose a different one
          {
              val name = ProposeAccountName(selectedBlockChain.second)
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
          val valid = isValidOrEmptyRecoveryPhrase(words)
          val priorPhrase = newAcState.recoveryPhrase
          newAcState = newAcState.copy(recoveryPhrase = it, validOrNoRecoveryPhrase = valid)
          if (words != processSecretWords(priorPhrase))  // If the recovery phrase is equivalent nothing to do, otherwise set the new one
          {
              recoverySearchText = ""  // phrase changed so need to search again
              // If the recovery phrase changes materially, we need to rediscover the wallet
              newAcState = newAcState.copy(earliestActivity = null, earliestActivityHeight = 0)
              if (valid && words.size == 12) // Launch the wallet discoverer if the recovery phrase is ok
              {
                  // If the recovery phrase is good, let's peek at the blockchain to see if there's activity
                  // thread(true, true, null, "peekWallet") // kotlin api does not offer stack size setting
                  aborter.value.obj = true  // Abort the current peek
                  aborter.value = Objectify<Boolean>(false)  // and create a new object for the next one
                  recoverySearchText = i18n(S.NewAccountSearchingForTransactions)
                  LogIt.info("launching wallet peek")
                  val th = Thread {
                      try
                      {
                          peekActivity(words.joinToString(" "), selectedBlockChain.second, aborter.value)
                      }
                      catch (e: Exception)
                      {
                          recoverySearchText = i18n(S.NewAccountSearchFailure)
                          LogIt.severe("wallet peek error: " + e.toString())
                      }
                  }
              }
              else
                  recoverySearchText == ""
          }
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
              if ((createClicks == 0) && (newAcState.earliestActivity == null))
              {
                  createClicks += 1
                  recoverySearchText = i18n(S.creatingNoHistoryAccountWarning)
              }
              else
              {
                  Thread("recoverAccount")
                  {
                      newAcState = try
                      {
                          wallyApp!!.recoverAccount(newAcState.accountName, flags, newAcState.pin, words.joinToString(" "), selectedBlockChain.second, newAcState.earliestActivity, newAcState.earliestActivityHeight.toLong(), null)
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
          }
          if (inputValid && words.isEmpty())
          {
              later {
                  val account = wallyApp!!.newAccount(newAcState.accountName, flags, newAcState.pin, selectedBlockChain.second)
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
  recoverySearchText: String,
  selectedChain: Pair<String, ChainSelector>,
  blockchains: Map<String, ChainSelector>,
  onChainSelected: (Pair<String, ChainSelector>) -> Unit,
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
        WallyDropDownMenuUnidirectional<ChainSelector>(selectedChain, blockchains, onChainSelected)
        AccountNameInput(newAcState.accountName, newAcState.validAccountName, onNewAccountName)
        Spacer(Modifier.height(10.dp))
        RecoveryPhraseInput(newAcState.recoveryPhrase, newAcState.validOrNoRecoveryPhrase, onNewRecoveryPhrase)
        Spacer(Modifier.height(10.dp))
        pinInput(newAcState.pin, newAcState.validOrNoPin, onPinChange)
        Text(i18n(S.PinSpendingUnprotected), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        WallySwitch(newAcState.hideUntilPinEnter, S.PinHidesAccount, true, onHideUntilPinEnterChanged)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // I'm cheating a bit here and using the contents of the recoverySearchText to pick what icon to show
            if (recoverySearchText == i18n(S.NewAccountSearchingForTransactions))
            {
                LoadingAnimation()
            }
            else if (recoverySearchText == "")
                Spacer(modifier = Modifier.size(50.dp))
            else if (newAcState.earliestActivity != null)
                ResImageView("icons/check.xml", modifier = Modifier.size(50.dp))
            else if (recoverySearchText.length < 200)
                Icon(Icons.Default.Clear, modifier = Modifier.size(50.dp), tint = colorError, contentDescription = null)

            CenteredText(recoverySearchText)
        }
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
    else  // For some reason Clear is a red X
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
              //singleLine = true,
              minLines = 1,
              maxLines = 4,
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


fun searchActivity(ec: ElectrumClient, chainSelector: ChainSelector, count: Int, secretDerivation: (Int) -> ByteArray, activityFound: ((Long, Int) -> Boolean)? = null): Pair<Long, Int>?
{
    var index = 0
    var ret: Pair<Long, Int>? = null
    while (index < count)
    {
        val newSecret = secretDerivation(index)
        val us = UnsecuredSecret(newSecret)

        val dests = mutableListOf<SatoshiScript>(Pay2PubKeyHashDestination(chainSelector, us, index.toLong()).outputScript())  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
        //LogIt.info(sourceLoc() + " " + name + ": New Destination " + tmp.toString() + ": " + dest.address.toString())
        if (chainSelector.hasTemplates)
            dests.add(Pay2PubKeyTemplateDestination(chainSelector, us, index.toLong()).ungroupedOutputScript())

        for (dest in dests)
        {
            try
            {
                val use = ec.getFirstUse(dest, 10000)
                if (use.block_hash != null)
                {
                    val bh = use.block_height
                    if (bh != null)
                    {
                        LogIt.info("Found activity at index $index in ${dest.address.toString()}")
                        val headerBin = ec.getHeader(bh)
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
                    LogIt.info("didn't find activity at index $index in ${dest.address.toString()}")
                }
            }
            catch (e: ElectrumNotFound)
            {
                LogIt.info("didn't find activity at index $index in ${dest.address.toString()}")
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
            val use = ec.getFirstUse(dest.outputScript(), 10000)
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


fun peekActivity(secretWords: String, chainSelector: ChainSelector, aborter: Objectify<Boolean>)
{
    val (svr, port) = try
    {
        getElectrumServerOn(chainSelector)
    } catch (e: BadCryptoException)
    {
        LogIt.info("peek not supported for this blockchain")
        return
    }

    if (aborter.obj) return
    val ec = try
    {
        ElectrumClient(chainSelector, svr, port, useSSL=true)
    }
    catch (e: ElectrumConnectError) // covers java.net.ConnectException, UnknownHostException and a few others that could trigger
    {
        try
        {
            ElectrumClient(chainSelector, svr, port, useSSL = false, accessTimeoutMs = 60000, connectTimeoutMs = 10000)
        }
        catch(e: ElectrumConnectError)
        {
            if (chainSelector == ChainSelector.BCH)
                ElectrumClient(chainSelector, LAST_RESORT_BCH_ELECTRS)
            else if (chainSelector == ChainSelector.NEXA)
                ElectrumClient(chainSelector, LAST_RESORT_NEXA_ELECTRS, DEFAULT_NEXA_TCP_ELECTRUM_PORT, useSSL = false)
            else throw e
        }
        catch (e: ElectrumConnectError)
        {
            displayRecoveryInfo(i18n(S.ElectrumNetworkUnavailable))
            // TODO status checkmark    ui.GuiStatusOk.setImageResource(android.R.drawable.ic_delete)
            return
        }
    }
    ec.start()
    if (aborter.obj) return

    val passphrase = "" // TODO: support a passphrase
    val secret = generateBip39Seed(secretWords, passphrase)

    val addressDerivationCoin = Bip44AddressDerivationByChain(chainSelector)

    LogIt.info("Searching in ${addressDerivationCoin}")
    var earliestActivityP =
      searchActivity(ec, chainSelector, WALLET_RECOVERY_DERIVATION_PATH_SEARCH_DEPTH, {
          libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, addressDerivationCoin, 0, false, it).first }, { time, height ->
              displayRecoveryInfo(i18n(S.Bip44ActivityNotice) + " " + (i18n(S.FirstUseDateHeightInfo) % mapOf(
            "date" to epochToDate(time),
            "height" to height.toString())
            ))
          updateRecoveryInfo(time, height, null)
          true })

    if (aborter.obj) return

    LogIt.info("Searching in ${AddressDerivationKey.ANY}")
    // Look for activity in the identity and common location
    var earliestActivityId =
      searchActivity(ec, chainSelector, WALLET_RECOVERY_IDENTITY_DERIVATION_PATH_SEARCH_DEPTH, {
          libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.ANY, 0, false, it).first })
    if (aborter.obj) return

    // Set earliestActivityP to the lesser of the two
    if (earliestActivityP == null) earliestActivityP = earliestActivityId
    else
    {
        if ((earliestActivityId != null) && (earliestActivityId.first < earliestActivityP.first)) earliestActivityP = earliestActivityId
    }
    if (aborter.obj) return

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
        updateRecoveryInfo(null,0, i18n(S.NoBip44ActivityNotice))
    }

    /*
    // Look in non-standard places for activity
    val BTCactivity =
      bracketActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 0, it) })
    var BTCchangeActivity: HDActivityBracket?

    // This code checks whether coins exist on the Bitcoin derivation path to see if any prefork coins exist.  This is irrelevant for Nexa.
    // I'm leaving the code in though because someday we might want to share pubkeys between BTC/BCH and Nexa and in that case we'd need to use their derivation path.

    var Bip44BTCMsg = if (BTCactivity != null)
    {
        BTCchangeActivity =
          bracketActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 1, it) })
        nonstandardActivity.clear()  // clear because peek can be called multiple times if the user changes the secret
        nonstandardActivity.add(Pair(Bip44Wallet.HdDerivationPath(null, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 0, BTCactivity.lastAddressIndex), BTCactivity))
        if (BTCchangeActivity != null)
        {
            nonstandardActivity.add(Pair(Bip44Wallet.HdDerivationPath(null, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 1, BTCchangeActivity.lastAddressIndex), BTCchangeActivity))
        }

        i18n(R.string.Bip44BtcActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
          "date" to epochToDate(BTCactivity.startTime),
          "height" to BTCactivity.startBlockHeight.toString()
        )
    }
    else i18n(R.string.NoBip44BtcActivityNotice)
    */

    /*
    earliestActivityP = searchActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP43, AddressDerivationKey.BTC, 0, 0, it) })
    var Bip44BTCMsg = if (earliestActivityP != null)
    {
        earliestActivity = earliestActivityP.first-1 // -1 so earliest activity is just before the activity
        i18n(R.string.Bip44BtcActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
            "date" to epochToDate(earliestActivityP.first),
            "height" to earliestActivityP.second.toString())
    }
    else i18n(R.string.NoBip44BtcActivityNotice)
     */

}