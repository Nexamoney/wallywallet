package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.foundation.clickable
import info.bitcoinunlimited.www.wally.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallySwitchRowUi2
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SettingsScreen")

@Composable fun ShowScreenNavSwitchUi2(preference: String, navChoice: NavChoiceUi2, textRes: Int, globalPref: MutableStateFlow<Boolean>)
{
    val itemsState = menuItemsUi2.collectAsState().value
    val moreMenuItemsState = moreMenuItems.collectAsState().value

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
                  val items = itemsState.toMutableSet()
                  val itemsMore = moreMenuItemsState.toMutableSet()
                  if (items.size < BOTTOM_NAV_ITEMS)
                      items.add(navChoice)
                  else
                      itemsMore.add(navChoice)
                  menuItemsUi2.value = items.sortedBy { it.location }.toSet()
                  moreMenuItems.value = itemsMore.sortedBy { it.location }.toSet()
              }
              else
              {
                  val items = itemsState.toMutableSet()
                  val itemsMore = moreMenuItemsState.toMutableSet()
                  items.remove(navChoice)
                  itemsMore.remove(navChoice)
                  menuItemsUi2.value = items.sortedBy { it.location }.toSet()
                  moreMenuItems.value = itemsMore.sortedBy { it.location }.toSet()
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
fun SettingsScreenUi2()
{
    val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
    var devModeView by mutableStateOf(devMode)
    var darkModeView by mutableStateOf(darkMode)
    val generalSettingsSwitches = mutableListOf(
      GeneralSettingsSwitch(info.bitcoinunlimited.www.wally.ui.ACCESS_PRICE_DATA_PREF, S.AccessPriceData),
    )
    val hasNewUIState = newUI.collectAsState()
    val hasNewUI = hasNewUIState.value

    if (platform().supportsBackgroundSync)
    {
        generalSettingsSwitches.add(GeneralSettingsSwitch(BACKGROUND_SYNC_PREF, S.backgroundSync))
    }

    // When we leave the settings screen, set global variables based on settings changes
    nav.onDepart {
        var nodeAddr: String? = null

        allowAccessPriceData = preferenceDB.getBoolean(info.bitcoinunlimited.www.wally.ui.ACCESS_PRICE_DATA_PREF, true)

        for (chain in chainToURI)
        {
            val name = chain.value
            nodeAddr = preferenceDB.getString(name + "." + info.bitcoinunlimited.www.wally.ui.CONFIGURED_NODE, null)
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

            Text(i18n(S.version) % mapOf("ver" to Version.VERSION_NUMBER + "-" + Version.GIT_COMMIT_HASH, "date" to Version.BUILD_DATE), modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
        }

        Column(
          modifier = Modifier.padding(start = 32.dp)
        ) {
            LocalCurrency(preferenceDB)
            ConfirmAboveUi2(preferenceDB)
            ShowScreenNavSwitchUi2(SHOW_IDENTITY_PREF, NavChoiceUi2(ScreenId.Identity, S.title_activity_identity, Icons.Default.Person), S.enableIdentityMenu, showIdentityPref)
            ShowScreenNavSwitchUi2(SHOW_TRICKLEPAY_PREF, NavChoiceUi2(ScreenId.TricklePay, S.title_activity_trickle_pay, Icons.Default.WaterDrop), S.enableTricklePayMenu, showTricklePayPref)
            // Only let them choose to not show assets if they don't have any assets
            if (showAssetsPref.value == false || wallyApp?.hasAssets()==false)
                ShowScreenNavSwitchUi2(SHOW_ASSETS_PREF, NavChoiceUi2(ScreenId.Assets, S.title_activity_assets, Icons.Default.Menu), S.enableAssetsMenu, showAssetsPref)
            WallyHalfDivider()
            generalSettingsSwitches.forEach { GeneralSettingsSwitchView(it) }

            if (false) // Dark mode is not implemented so don't show the button
                WallySwitchRowUi2(darkModeView, S.enableDarkMode) {
                    preferenceDB.edit().putBoolean(DARK_MODE_PREF, it).commit()
                    darkModeView = it
                    darkMode = it
                }
            WallySwitchRowUi2(hasNewUI, S.enableExperimentalUx) {
                preferenceDB.edit().putBoolean(EXPERIMENTAL_UX_MODE_PREF, it).commit()
                newUI.value = it
            }
            WallySwitchRowUi2(devModeView, S.enableDeveloperView) {
                LogIt.info("devmode $it")
                preferenceDB.edit().putBoolean(info.bitcoinunlimited.www.wally.ui.DEV_MODE_PREF, it).commit()
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
                BlockchainSourceUi2(ChainSelector.NEXA, preferenceDB, horizontalAlignmentLine)
                if (devMode)
                {
                    BlockchainSourceUi2(ChainSelector.NEXATESTNET, preferenceDB, horizontalAlignmentLine)
                    BlockchainSourceUi2(ChainSelector.NEXAREGTEST, preferenceDB, horizontalAlignmentLine)
                }
                // BlockchainSource(ChainSelector.BCH, preferenceDB, horizontalAlignmentLine)
            }

            if(devMode)
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
                            /*  uncomment if you need this for dev
                            Button(onClick = { onWipeDatabase() }) {
                                Text("WIPE DATABASE")
                            }
                             */
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

@Composable
fun ConfirmAboveUi2(preferenceDB: SharedPreferences)
{
    val v = preferenceDB.getString(info.bitcoinunlimited.www.wally.ui.CONFIRM_ABOVE_PREF, "0") ?: "0"
    val dec = try {
        CurrencyDecimal(v)
    }
    catch (e:Exception)
    {
        CurrencyDecimal(0)
    }
    var textState = remember { mutableStateOf<String>(preferenceDB.getString(info.bitcoinunlimited.www.wally.ui.CONFIRM_ABOVE_PREF, NexaFormat.format(dec)) ?: "0") }

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
                      putString(info.bitcoinunlimited.www.wally.ui.CONFIRM_ABOVE_PREF, CurrencySerializeFormat.format(newDec))
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
fun BlockchainSourceUi2(chain: ChainSelector, preferenceDB: SharedPreferences, switchAlignment: HorizontalAlignmentLine)
{
    val name = chainToURI[chain] ?: ""
    val exclusiveNodeKey = "$name.$EXCLUSIVE_NODE_SWITCH"
    val preferNodeKey = "$name.$PREFER_NODE_SWITCH"
    val configuredNodeKey = "$name.${info.bitcoinunlimited.www.wally.ui.CONFIGURED_NODE}"
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
          textStyle = TextStyle(fontSize = 14.sp),
          bkgCol = Color.White
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