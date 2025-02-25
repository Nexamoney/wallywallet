package info.bitcoinunlimited.www.wally.ui

import info.bitcoinunlimited.www.wally.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.ACCESS_PRICE_DATA_PREF
import info.bitcoinunlimited.www.wally.CONFIGURED_NODE
import info.bitcoinunlimited.www.wally.CONFIRM_ABOVE_PREF
import info.bitcoinunlimited.www.wally.DEV_MODE_PREF
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.ui2.newUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui2.theme.WallyHalfDivider
import info.bitcoinunlimited.www.wally.ui2.views.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("wally.settingsscreen")

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

interface VersionI
{
    val VERSION: String
    val VERSION_NUMBER: String
    val GIT_COMMIT_HASH: String
    val GITLAB_URL: String
    val BUILD_DATE: String
}

@Composable
fun SettingsScreen(nav: ScreenNav)
{
    val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
    var devModeView by mutableStateOf(devMode)
    var darkModeView by mutableStateOf(darkMode)
    var experimentalUxView by mutableStateOf(newUI.value)
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
                    laterJob {  // Setting these can block when the cnxn manager is accessing them
                        if (!excl || (nodeSet.size == 0)) account.cnxnMgr.exclusiveNodes(null)
                        else account.cnxnMgr.exclusiveNodes(nodeSet)
                        if (!prefd || (nodeSet.size == 0)) account.cnxnMgr.preferNodes(null)
                        else account.cnxnMgr.preferNodes(nodeSet)
                    }
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
            ConfirmAbove(preferenceDB)
            ShowScreenNavSwitch(SHOW_IDENTITY_PREF, NavChoice(ScreenId.Identity, S.title_activity_identity, "icons/person.xml"), S.enableIdentityMenu, showIdentityPref)
            ShowScreenNavSwitch(SHOW_TRICKLEPAY_PREF, NavChoice(ScreenId.TricklePay, S.title_activity_trickle_pay, "icons/faucet_drip.xml"), S.enableTricklePayMenu, showTricklePayPref)
            // Only let them choose to not show assets if they don't have any assets
            if (showAssetsPref.value == false || wallyApp?.hasAssets()==false)
                ShowScreenNavSwitch(SHOW_ASSETS_PREF, NavChoice(ScreenId.Assets, S.title_activity_assets, "icons/invoice.xml"), S.enableAssetsMenu, showAssetsPref)
            WallyHalfDivider()
            generalSettingsSwitches.forEach { GeneralSettingsSwitchView(it) }

            /*
            WallySwitchRow(darkModeView, S.enableDarkMode) {
                preferenceDB.edit().putBoolean(DARK_MODE_PREF, it).commit()
                darkModeView = it
                darkMode = it
            }
             */
            WallySwitchRow(experimentalUxView, S.enableExperimentalUx) {
                preferenceDB.edit().putBoolean(EXPERIMENTAL_UX_MODE_PREF, it).commit()
                newUI.value = it
            }
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

fun onWipeDatabase()
{
    later {
        wallyApp?.accounts?.forEach {
            it.value.delete()
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
          text = i18n(S.darkMode),
        )
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
          onValueChange = {
              try {
                  val newStr = it.ifEmpty {
                      "0"
                  }
                  val newDec = CurrencyDecimal(newStr)
                  CoroutineScope(Dispatchers.IO).launch {
                      preferenceDB.edit().apply {
                          putString(CONFIRM_ABOVE_PREF, CurrencySerializeFormat.format(newDec))
                          commit()
                      }
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
          modifier = Modifier.weight(1f).padding(4.dp,0.dp,0.dp,0.dp).testTag("ConfirmAboveEntry")
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
