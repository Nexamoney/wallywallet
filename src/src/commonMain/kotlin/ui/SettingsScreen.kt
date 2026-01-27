package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.clickable
import info.bitcoinunlimited.www.wally.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui.views.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SettingsScreen")

data class GeneralSettingsSwitch(
  val prefKey: String,
  val textRes: Int
)

fun onCopyToClipBoardText(a: String) {
    setTextClipboard(a)
    // TODO: if (android.os.Build.VERSION.SDK_INT <= 32)  // system toasts above this version
    displayNotice(S.copiedToClipboard)
}


@Composable
fun LocalCurrency(preferenceDB: SharedPreferences)
{
    var expanded by remember { mutableStateOf(false) }
    val fiatCurrencies = listOf("BRL", "CAD", "CNY", "EUR", "GBP", "JPY", "RUB", "USD", "XAU")
    val selectedFiatCurrency = remember { mutableStateOf(preferenceDB.getString(info.bitcoinunlimited.www.wally.LOCAL_CURRENCY_PREF, "")) }

    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = i18n(S.localCurrency), Modifier.testTag(i18n(S.localCurrency)))
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
                          preferenceDB.edit().putString(info.bitcoinunlimited.www.wally.LOCAL_CURRENCY_PREF, s).commit()
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

@Composable fun ShowScreenNavSwitch(preference: String, navChoice: NavChoice, textRes: Int, globalPref: MutableStateFlow<Boolean>)
{
    val itemsState = menuItems.collectAsState().value
    val moreMenuItemsState = moreMenuItems.collectAsState().value

    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
          text = i18n(textRes)
        )
        Switch(
          checked = globalPref.collectAsState().value,
          onCheckedChange = {
              globalPref.value = it
              wallyApp?.preferenceDB?.edit()?.putBoolean(preference, it)?.commit()

              if(it)
              {
                  val items = itemsState.toMutableSet()
                  val itemsMore = moreMenuItemsState.toMutableSet()
                  if (items.size < BOTTOM_NAV_ITEMS)
                      items.add(navChoice)
                  else
                      itemsMore.add(navChoice)
                  menuItems.value = items.sortedBy { it.location }.toSet()
                  moreMenuItems.value = itemsMore.sortedBy { it.location }.toSet()
              }
              else
              {
                  val items = itemsState.toMutableSet()
                  val itemsMore = moreMenuItemsState.toMutableSet()
                  items.remove(navChoice)
                  itemsMore.remove(navChoice)
                  menuItems.value = items.sortedBy { it.location }.toSet()
                  moreMenuItems.value = itemsMore.sortedBy { it.location }.toSet()
              }
          },
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )

    }
}

@Composable fun GeneralSettingsSwitchView(generalSettingsSwitch: GeneralSettingsSwitch)
{
    val preferenceDB: SharedPreferences = wallyApp!!.preferenceDB
    val isChecked = remember { mutableStateOf(preferenceDB.getBoolean(generalSettingsSwitch.prefKey, true)) }

    WallySwitchRow(isChecked.value, generalSettingsSwitch.textRes) {
        isChecked.value = it
        preferenceDB.edit().putBoolean(generalSettingsSwitch.prefKey, it).commit()
    }
}

@Composable
fun SettingsScreen(preferenceDB: SharedPreferences = wallyApp!!.preferenceDB)
{
    var devModeView by mutableStateOf(devMode)
    var darkModeView by mutableStateOf(darkMode)
    val versionNumber = i18n(S.version) % mapOf("ver" to Version.VERSION_NUMBER + "-" + Version.GIT_COMMIT_HASH, "date" to Version.BUILD_DATE)
    val generalSettingsSwitches = mutableListOf(
      GeneralSettingsSwitch(ACCESS_PRICE_DATA_PREF, S.AccessPriceData),
    )

    if (platform().supportsBackgroundSync)
    {
        generalSettingsSwitches.add(GeneralSettingsSwitch(BACKGROUND_SYNC_PREF, S.backgroundSync))
    }

    // When we leave the settings screen, set global variables based on settings changes
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
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("SettingsScreenScrollable"),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(15.dp, 2.dp),
          horizontalAlignment = Alignment.Start
        ) {
            CenteredSectionText("Currency Settings")
            WallyDivider()
        }

        Column(
          modifier = Modifier.fillMaxWidth().padding(15.dp, 2.dp)
        ) {
            LocalCurrency(preferenceDB)
            ConfirmAbove(preferenceDB)
            Spacer(Modifier.height(16.dp))
            CenteredFittedText(
              versionNumber,
              startingFontScale = .8,
              modifier = Modifier.fillMaxWidth(),
              fontWeight = FontWeight.Normal
            )

            Spacer(Modifier.height(16.dp))
            CenteredSectionText(i18n(S.GeneralSettings))

            WallyDivider()
            ShowScreenNavSwitch(SHOW_IDENTITY_PREF, NavChoice(ScreenId.Identity, S.title_activity_identity, Icons.Default.Person), S.enableIdentityMenu, showIdentityPref)
            ShowScreenNavSwitch(SHOW_TRICKLE_PAY_PREF, NavChoice(ScreenId.TricklePay, S.title_activity_trickle_pay, Icons.Default.WaterDrop), S.enableTricklePayMenu, showTricklePayPref)
            // Only let them choose to not show assets if they don't have any assets
            if (showAssetsPref.value == false || wallyApp?.hasAssets() == false)
                ShowScreenNavSwitch(SHOW_ASSETS_PREF, NavChoice(ScreenId.Assets, S.title_activity_assets, Icons.Default.Image), S.enableAssetsMenu, showAssetsPref)
            generalSettingsSwitches.forEach { GeneralSettingsSwitchView(it) }

            if (false) // Dark mode is not implemented so don't show the button
                WallySwitchRow(darkModeView, S.enableDarkMode) {
                    CoroutineScope(Dispatchers.IO).launch {
                        preferenceDB.edit().putBoolean(DARK_MODE_PREF, it).commit()
                    }
                    darkModeView = it
                    darkMode = it
                }
            WallySwitchRow(devModeView, S.enableDeveloperView) {
                LogIt.info("devmode $it")
                CoroutineScope(Dispatchers.IO).launch {
                    preferenceDB.edit().putBoolean(DEV_MODE_PREF, it).commit()
                }
                devModeView = it
                devMode = it
            }
            WallySwitchRow(experimentalUI.collectAsState().value, S.enableExperimentalUx) {
                preferenceDB.edit().putBoolean(EXPERIMENTAL_UX_MODE_PREF, it).commit()
                experimentalUI.value = it
            }
            WallySwitchRow(soundEnabled.collectAsState().value, S.enableSound) {
                preferenceDB.edit().putBoolean(SOUND_ENABLED_PREF, it).commit()
                soundEnabled.value = it
            }

            Spacer(Modifier.height(16.dp))
            CenteredSectionText(i18n(S.BlockchainSettings))
            WallyDivider()
            Column(
              modifier = Modifier.padding(start = 4.dp, end = 4.dp).testTag("BlockchainSelectors")
            ) {
                val horizontalAlignmentLine = HorizontalAlignmentLine(merger = { old, new -> max(old, new) })

                // Wrap a column around this so we can then drop all the sources in the center of the page
                Column(modifier = Modifier.wrapContentSize().align(Alignment.CenterHorizontally))
                {
                    BlockchainSource(ChainSelector.NEXA, preferenceDB, horizontalAlignmentLine)
                    if (devMode)
                    {
                        BlockchainSource(ChainSelector.NEXATESTNET, preferenceDB, horizontalAlignmentLine)
                        BlockchainSource(ChainSelector.NEXAREGTEST, preferenceDB, horizontalAlignmentLine)
                    }
                    // BlockchainSource(ChainSelector.BCH, preferenceDB, horizontalAlignmentLine)
                }

                if (devMode)
                {
                    Spacer(Modifier.height(32.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = { onLogDebugData() }) {
                                    Text("Log Info")
                                }
                                Button(onClick = { onReloadAssets() }) {
                                    Text("Reload Assets")
                                }
                                Button(onClick = { onCloseP2pConnections() }) {
                                    Text("Close P2P")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                /* This is dangerous enough, devs should uncomment if they want to use
                            Button(onClick = { onWipeAccounts() }) {
                                Text("Delete Accounts")
                            }*/
                                Button(onClick = { onWipeHeaders() }) {
                                    Text("Delete Headers")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                WallyBoringButton({ openUrl(Version.GITLAB_URL) }, modifier = Modifier
                                ) {
                                    ResImageView("icons/gitlab-logo-300.png", modifier = Modifier.width(100.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
          bkgCol = Color.White,
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

@Composable
fun BlockchainSource(chain: ChainSelector, preferenceDB: SharedPreferences, switchAlignment: HorizontalAlignmentLine)
{
    val name = chainToURI[chain] ?: ""
    val exclusiveNodeKey = "$name.$EXCLUSIVE_NODE_SWITCH"
    val preferNodeKey = "$name.$PREFER_NODE_SWITCH"
    val configuredNodeKey = "$name.$CONFIGURED_NODE"
    val configuredNode: String = preferenceDB.getString(configuredNodeKey, "") ?: ""
    val preferNode: Boolean = preferenceDB.getBoolean(preferNodeKey, false)
    val exclusiveNode: Boolean = preferenceDB.getBoolean(exclusiveNodeKey, false)
    val onlyChecked = remember { mutableStateOf(exclusiveNode) }
    val preferChecked = remember { mutableStateOf(preferNode) }
    var textState by remember { mutableStateOf(configuredNode) }

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
          onValueChange = { newNode ->
              textState = newNode
              CoroutineScope(Dispatchers.IO).launch {
                  with(preferenceDB.edit())
                  {
                      putString(configuredNodeKey, newNode)
                      commit()
                  }
              }
          },
          keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
          modifier = Modifier.weight(1f),
          textStyle = TextStyle(fontSize = 14.sp),
          bkgCol = Color.White
        )
        Spacer(modifier = Modifier.weight(0.01f).alignBy(switchAlignment))
        WallySwitch(onlyChecked, S.only) {
            onlyChecked.value = it
            if (it) preferChecked.value = false  // if one is true the other must be false
            CoroutineScope(Dispatchers.IO).launch {
                preferenceDB.edit().putBoolean(exclusiveNodeKey, it).putBoolean(preferNodeKey, preferChecked.value).commit()
            }
        }
        Spacer(modifier = Modifier.weight(0.01f))
        WallySwitch(preferChecked, S.prefer) {
            preferChecked.value = it
            if (it) onlyChecked.value = false  // if one is true the other must be false
            CoroutineScope(Dispatchers.IO).launch {
                preferenceDB.edit().putBoolean(preferNodeKey, it).putBoolean(exclusiveNodeKey, onlyChecked.value).commit()
            }
        }
    }
}

fun onWipeAccounts()
{
    laterJob {
        wallyApp?.accounts?.forEach {
            it.value.delete()
        }
    }
}
fun onWipeHeaders()
{
    laterJob {
        blockchains.forEach {
            it.value.db.clear()
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
        displayNotice("Log written")
    }
}

fun onReloadAssets()
{
    later {
        val app = wallyApp!!
        app.assetManager.clear()

        LogIt.info("Reload Assets Button")
        displayNotice("Reloading assets")
        triggerAssetCheck()
    }
}

fun onCloseP2pConnections()
{
    later {
        for (bc in blockchains)
        {
            for (cxn in bc.value.net.p2pCnxns)
            {
                cxn.close()
            }
        }
        LogIt.info("All P2P connections closed")
        displayNotice("Connections closed")
    }
}

