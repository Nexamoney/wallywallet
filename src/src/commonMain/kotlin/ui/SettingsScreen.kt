package info.bitcoinunlimited.www.wally.ui
import info.bitcoinunlimited.www.wally.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SettingsScreen")
const val LOCAL_CURRENCY_PREF = "localCurrency"
const val ACCESS_PRICE_DATA_PREF = "accessPriceData"
const val DARK_MODE_PREF = "darkModeMenu"
const val DEV_MODE_PREF = "devinfo"
const val CONFIRM_ABOVE_PREF = "confirmAbove"
const val CONFIGURED_NODE = "NodeAddress"

data class GeneralSettingsSwitch(
  val prefKey: String,
  val textRes: Int
)

@Composable fun ShowScreenNavSwitch(preference: String, navChoice: NavChoice, textRes: Int, globalPref: MutableStateFlow<Boolean>)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {

        Switch(
          checked = globalPref.collectAsState().value,
          onCheckedChange = {
              globalPref.value = it
              preferenceDB.edit().putBoolean(preference, it).commit()

              if(it)
              {
                  val items = menuItems.value.toMutableSet()
                  items.add(navChoice)
                  menuItems.value = items.sortedBy { it.location }.toSet()
              }
              else
              {
                  val items = menuItems.value.toMutableSet()
                  items.remove(navChoice)
                  menuItems.value = items
              }
          },
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )
        Text(
          text = i18n(textRes),
          modifier = Modifier.padding(4.dp, 0.dp, 0.dp, 0.dp)
        )
    }
}

@Composable
fun SettingsScreen(nav: ScreenNav)
{
    val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
    // TODO val darkMode = remember { mutableStateOf( preferenceDB.getBoolean(DARK_MODE_PREF, false)) }
    var devModeView by mutableStateOf(devMode)
    val generalSettingsSwitches = listOf(
      GeneralSettingsSwitch(ACCESS_PRICE_DATA_PREF, S.AccessPriceData)
    )

    nav.onDepart {
        var nodeAddr: String? = null

        allowAccessPriceData = preferenceDB.getBoolean(ACCESS_PRICE_DATA_PREF, true)

        for (chain in chainToURI)
        {
            val name = chain.value
            nodeAddr = preferenceDB.getString(name + "." + CONFIGURED_NODE, null)
            val excl = preferenceDB.getBoolean(name + "." + EXCLUSIVE_NODE_SWITCH, false)
            val prefd = preferenceDB.getBoolean(name + "." + PREFER_NODE_SWITCH, false)

            // for every account on this blockchain, install the exclusive node or send a null saying not exclusive anymore
            for (account in wallyApp!!.accounts.values)
            {
                if (account.chain.chainSelector == chain.key)
                {
                    val nodeSet: Set<String> = nodeAddr?.splitIntoSet() ?: setOf()
                    if (!excl || (nodeSet.size == 0)) account.cnxnMgr.exclusiveNodes(null)
                    else account.cnxnMgr.exclusiveNodes(nodeSet)
                    if (!prefd || (nodeSet.size == 0)) account.cnxnMgr.preferNodes(null)
                    else account.cnxnMgr.preferNodes(nodeSet)
                }
            }
        }
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.Start
        ) {
            CenteredSectionText(i18n(S.GeneralSettings))
            Text(i18n(S.version) % mapOf("ver" to BuildInfo.VERSION_NAME), modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
        }
        Column(
          modifier = Modifier.padding(start = 32.dp)
        ) {
            LocalCurrency(preferenceDB)
            ConfirmAbove(preferenceDB)
            ShowScreenNavSwitch(SHOW_IDENTITY_PREF, NavChoice(ScreenId.Identity, S.title_activity_identity, "icons/person.xml"), S.enableIdentityMenu, showIdentityPref)
            ShowScreenNavSwitch(SHOW_TRICKLEPAY_PREF, NavChoice(ScreenId.TricklePay, S.title_activity_trickle_pay, "icons/faucet_drip.xml"), S.enableTricklePayMenu, showTricklePayPref)
            // Only let them choose to not show assets if they don't have any assets
            if (showAssetsPref.value == false || wallyApp?.hasAssets()==false)
                ShowScreenNavSwitch(SHOW_ASSETS_PREF, NavChoice(ScreenId.Assets, S.title_activity_assets, "icons/invoice.xml"), S.enableAssetsMenu, showAssetsPref)
            WallyHalfDivider()
            generalSettingsSwitches.forEach { GeneralSettingsSwitchView(it) }
            /* TODO
            DarkMode(darkMode) {
                preferenceDB.edit().putBoolean(DARK_MODE_PREF, it).commit()
                darkMode.value = it
            }
             */
            WallySwitchRow(devModeView, S.enableDeveloperView) {
                LogIt.info("devmode $it")
                preferenceDB.edit().putBoolean(DEV_MODE_PREF, it).commit()
                devModeView = it
                devMode = it
            }
        }

        Spacer(Modifier.height(16.dp))
        WallyDivider()

        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
            Text(text  = i18n(S.BlockchainSettings), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(
          modifier = Modifier.padding(start = 4.dp, end = 4.dp)
        ) {
            val horizontalAlignmentLine = HorizontalAlignmentLine(merger = { old, new -> max(old, new)})

            // Wrap a column around this so we can then drop all the sources in the center of the page
            Column( modifier = Modifier.wrapContentSize().align(Alignment.CenterHorizontally))
            {
                BlockchainSource(ChainSelector.NEXA, preferenceDB, horizontalAlignmentLine)
                if (devMode)
                {
                    BlockchainSource(ChainSelector.NEXATESTNET, preferenceDB, horizontalAlignmentLine)
                    BlockchainSource(ChainSelector.NEXAREGTEST, preferenceDB, horizontalAlignmentLine)
                }
                // BlockchainSource(ChainSelector.BCH, preferenceDB, horizontalAlignmentLine)
            }

            if(devMode)
            {
                Spacer(Modifier.height(32.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onLogDebugData() }) {
                            Text("LOG DEBUG DATA")
                        }
                        /*  uncomment if you need this for dev
                        Button(onClick = { onWipeDatabase() }) {
                            Text("WIPE DATABASE")
                        }
                         */
                    }
                }
            }
        }
    }
}

fun onWipeDatabase()
{
    later {
        wallyApp?.accounts?.forEach {
            it.value.delete()
            deleteWallet(it.key, it.value.chain.chainSelector)
        }
    }
}

fun onLogDebugData()
{
    later {
        val coins: MutableMap<String, Account> = wallyApp!!.accounts
        LogIt.info("LOG DEBUG BUTTON")
        for (c in coins)
            c.value.wallet.debugDump()
    }
}

@Composable
fun LocalCurrency(preferenceDB: SharedPreferences)
{
    var expanded by remember { mutableStateOf(false) }
    val fiatCurrencies = listOf("BRL", "CAD", "CNY", "EUR", "GBP", "JPY", "RUB", "USD", "XAU")
    val selectedFiatCurrency = remember { mutableStateOf(preferenceDB.getString(LOCAL_CURRENCY_PREF, "")) }

    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = i18n(S.localCurrency))
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                  text = selectedFiatCurrency.value ?: "",
                  modifier = Modifier.clickable(onClick = { expanded = true })
                )
                IconButton(onClick = {expanded = true}) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Fiat currency dropdown")
                }
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false },
            ) {
                fiatCurrencies.forEachIndexed { _, s ->
                    DropdownMenuItem(
                      onClick = {
                          preferenceDB.edit().putString(LOCAL_CURRENCY_PREF, s).commit()
                          selectedFiatCurrency.value = s
                          expanded = false
                      },
                      text = { Text(text = s) }
                    )
                }
            }
        }
    }
}

@Composable fun GeneralSettingsSwitchView(generalSettingsSwitch: GeneralSettingsSwitch)
{
    val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
    val isChecked = remember { mutableStateOf(preferenceDB.getBoolean(generalSettingsSwitch.prefKey, true)) }

    WallySwitch(isChecked, generalSettingsSwitch.textRes) {
        isChecked.value = it
        preferenceDB.edit().putBoolean(generalSettingsSwitch.prefKey, it).commit()
    }
}

@Composable fun DarkMode(darkMode: MutableState<Boolean>, onClick: (Boolean) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        WallySwitch(darkMode, Modifier, onClick)
        Text(
          text = "Enable dark mode",
        )
    }
}

@Composable
fun DevMode(devMode: MutableState<Boolean>, onClick: (Boolean) -> Unit)
{
    WallySwitch(devMode, S.enableDeveloperView, Modifier, onClick)
}

@Composable
fun ConfirmAbove(preferenceDB: SharedPreferences)
{
    val v = preferenceDB.getString(CONFIRM_ABOVE_PREF, "0") ?: "0"
    val dec = try {
        CurrencyDecimal(v)
    }
    catch (e:Exception)
    {
        CurrencyDecimal(0)
    }
    var textState = remember { mutableStateOf<String>(preferenceDB.getString(CONFIRM_ABOVE_PREF, NexaFormat.format(dec)) ?: "0") }

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(i18n(S.WhenAskSure))
        WallyDecimalEntry(
          value = textState,
          onValueChange = {
              try {
                  val newStr = it.ifEmpty {
                      "0"
                  }
                  val newDec = CurrencyDecimal(newStr)
                  with(preferenceDB.edit())
                  {
                      putString(CONFIRM_ABOVE_PREF, CurrencySerializeFormat.format(newDec))
                      commit()
                  }
              }
              catch (e:Exception) // number format exception, for one
              {
                  logThreadException(e)
              }
              textState.value = it
              textState.value
          }
          //colors = textFieldColors(containerColor = Color.Transparent),
          //colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
          //  unfocusedContainerColor = Color.Transparent
          ,
          modifier = Modifier.weight(1f).padding(4.dp,0.dp,0.dp,0.dp)
        )
        Text(chainToCurrencyCode[ChainSelector.NEXA]!!)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockchainSource(chain: ChainSelector, preferenceDB: SharedPreferences, switchAlignment: HorizontalAlignmentLine)
{
    val name = chainToURI[chain] ?: ""
    val exclusiveNodeKey = "$name.$EXCLUSIVE_NODE_SWITCH"
    val preferNodeKey = "$name.$PREFER_NODE_SWITCH"
    val configuredNodeKey = "$name.$CONFIGURED_NODE"
    val onlyChecked = remember { mutableStateOf(preferenceDB.getBoolean(exclusiveNodeKey, false)) }
    val preferChecked = remember { mutableStateOf(preferenceDB.getBoolean(preferNodeKey, false)) }
    var textState by remember { mutableStateOf(preferenceDB.getString(configuredNodeKey, "") ?: "") }

    val dispName = chainToName[chain]?.replaceFirstChar { it.uppercase() } ?: ""
    val focusManager = LocalFocusManager.current
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(5.dp, 0.dp),
    ) {
        Text(dispName, modifier= Modifier.clickable {
            focusManager.clearFocus()
        })
        Spacer(modifier = Modifier.width(4.dp))
        WallyTextEntry(
          value = textState,
          onValueChange = {
              textState = it
              LogIt.info(configuredNodeKey)
              with(preferenceDB.edit())
            {
                putString(configuredNodeKey, it)
                commit()
            }
          },
          keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
          modifier = Modifier.weight(1f),
          textStyle = TextStyle(fontSize = 14.sp)
        )
        Spacer(modifier = Modifier.weight(0.01f).alignBy(switchAlignment))
        WallySwitch(onlyChecked, S.only) {
            onlyChecked.value = it
            if (it) preferChecked.value = false  // if one is true the other must be false
            preferenceDB.edit().putBoolean(exclusiveNodeKey, it).putBoolean(preferNodeKey, preferChecked.value).commit()
        }
        Spacer(modifier = Modifier.weight(0.01f))
        WallySwitch(preferChecked, S.prefer) {
            preferChecked.value = it
            if (it) onlyChecked.value = false  // if one is true the other must be false
            preferenceDB.edit().putBoolean(preferNodeKey, it).putBoolean(exclusiveNodeKey, onlyChecked.value).commit()
        }
    }
}
