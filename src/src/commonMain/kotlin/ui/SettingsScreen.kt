package info.bitcoinunlimited.www.wally.ui
import info.bitcoinunlimited.www.wally.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults.textFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.ui.theme.WallySwitch
import info.bitcoinunlimited.www.wally.ui.theme.WallySwitchRow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SettingsScreen")
const val LOCAL_CURRENCY_PREF = "localCurrency"
const val ACCESS_PRICE_DATA_PREF = "accessPriceData"
const val SHOW_IDENTITY_PREF = "showIdentityMenu"
const val SHOW_TRICKLEPAY_PREF = "showTricklePayMenu"
const val SHOW_ASSETS_PREF = "showAssetsMenu"
const val DARK_MODE_PREF = "darkModeMenu"
const val DEV_MODE_PREF = "devinfo"
const val CONFIRM_ABOVE_PREF = "confirmAbove"
const val EXCLUSIVE_NODE_SWITCH = "exclusiveNodeSwitch"
const val CONFIGURED_NODE = "NodeAddress"
const val PREFER_NODE_SWITCH = "preferNodeSwitch"

data class GeneralSettingsSwitch(
  val prefKey: String,
  val textRes: Int
)

@Composable
fun SettingsScreen(nav: ScreenNav)
{
    val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
    val darkMode = remember { mutableStateOf( preferenceDB.getBoolean(DARK_MODE_PREF, false)) }
    var devModeView by mutableStateOf(devMode)
    val generalSettingsSwitches = listOf(
      GeneralSettingsSwitch(ACCESS_PRICE_DATA_PREF, S.AccessPriceData),
      GeneralSettingsSwitch(SHOW_IDENTITY_PREF, S.enableIdentityMenu),
      GeneralSettingsSwitch(SHOW_TRICKLEPAY_PREF, S.enableTricklePayMenu),
      GeneralSettingsSwitch(SHOW_ASSETS_PREF, S.enableAssetsMenu),
    )

    Column(
      modifier = Modifier
        .fillMaxWidth(),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.SpaceEvenly
    ) {
        ConstructTitleBar(nav, S.title_activity_settings)
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(i18n(S.GeneralSettings), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(i18n(S.version)) // TODO: Display version with multiplatform...
            LocalCurrency(preferenceDB)
        }
        Column(
          modifier = Modifier.padding(start = 16.dp)
        ) {
            generalSettingsSwitches.forEach { GeneralSettingsSwitchView(it) }
            DarkMode(darkMode) {
                preferenceDB.edit().putBoolean(DARK_MODE_PREF, it)
                darkMode.value = it
            }
            WallySwitchRow(devModeView, S.enableDeveloperView) {
                LogIt.info("devmode $it")
                preferenceDB.edit().putBoolean(DEV_MODE_PREF, it)
                devModeView = it
                devMode = it
            }
            ConfirmAbove(preferenceDB)
        }

        Spacer(Modifier.height(16.dp))
        WallyDivider()
        Spacer(Modifier.height(16.dp))

        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
            Text(text  = i18n(S.BlockchainSettings), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(
          modifier = Modifier.padding(start = 4.dp, end = 4.dp)
        ) {
            BlockchainSource(ChainSelector.NEXA, preferenceDB)
            if(devMode)
            {
                BlockchainSource(ChainSelector.NEXATESTNET, preferenceDB)
                BlockchainSource(ChainSelector.NEXAREGTEST, preferenceDB)

            }
            BlockchainSource(ChainSelector.BCH, preferenceDB)
            if(devMode)
            {
                Spacer(Modifier.height(32.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onLogDebugData() }, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text("LOG DEBUG DATA")
                    }
                }
            }
        }
    }
}

fun onLogDebugData()
{
    launch {
        LogIt.warning("TODO: Implement LOG DEBUG BUTTON")

        /*
        val coins: MutableMap<String, Account> = (getApplication() as WallyApp).accounts

        LogIt.info("LOG DEBUG BUTTON")
        for (c in coins)
        {
            c.value.wallet.debugDump()
        }
         */
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
                          preferenceDB.edit().putString(LOCAL_CURRENCY_PREF, s)
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
        preferenceDB.edit().putBoolean(generalSettingsSwitch.prefKey, it)
    }
}

@Composable fun DarkMode(darkMode: MutableState<Boolean>, onClick: (Boolean) -> Unit)
{
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        WallySwitch(darkMode, onClick)
        Text(
          text = "Enable dark mode",
        )
    }
}

@Composable
fun DevMode(devMode: MutableState<Boolean>, onClick: (Boolean) -> Unit)
{
    WallySwitch(devMode, S.enableDeveloperView, onClick)
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
    var textState by remember { mutableStateOf(preferenceDB.getString(CONFIRM_ABOVE_PREF, nexFormat.format(dec))) }

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
          value = textState ?: throw Exception("ConfirmAbove TextField value cannot be null"),
          onValueChange = {
              try {
                  val newStr = it.ifEmpty {
                      "0"
                  }
                  val newDec = CurrencyDecimal(newStr)
                  with(preferenceDB.edit())
                  {
                      putString(CONFIRM_ABOVE_PREF, serializeFormat.format(newDec))
                  }
              }
              catch (e:Exception) // number format exception, for one
              {
                  logThreadException(e)
              }
              textState = it
          },
          //colors = textFieldColors(containerColor = Color.Transparent),
          colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
          ),
          modifier = Modifier.width(175.dp)
        )
        Text(i18n(S.WhenAskSure))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockchainSource(chain: ChainSelector, preferenceDB: SharedPreferences)
{
    val name = chainToURI[chain] ?: throw Exception("Cannot get chain URI in BlockchainSource")
    val exclusiveNodeKey = "$name.$EXCLUSIVE_NODE_SWITCH"
    val preferNodeKey = "$name.$PREFER_NODE_SWITCH"
    val textPreferenceKey = "$name.$CONFIGURED_NODE"
    val onlyChecked = remember { mutableStateOf(preferenceDB.getBoolean(exclusiveNodeKey, false)) }
    val preferChecked = remember { mutableStateOf(preferenceDB.getBoolean(preferNodeKey, false)) }
    var textState by remember { mutableStateOf(preferenceDB.getString(textPreferenceKey, "") ?: "") }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
          value = textState,
          onValueChange = {
              textState = it
              preferenceDB.edit().putString(textPreferenceKey, it)
          },
          label = { Text(name) },
          singleLine = true,
          modifier = Modifier.width(180.dp),
          colors = textFieldColors(containerColor = Color.Transparent),
          textStyle = TextStyle(fontSize = 12.sp)
        )
        WallySwitch(onlyChecked, S.only) {
            onlyChecked.value = it
            preferenceDB.edit().putBoolean(exclusiveNodeKey, it)
        }
        WallySwitch(preferChecked, S.prefer) {
            preferChecked.value = it
            preferenceDB.edit().putBoolean(preferNodeKey, it)
        }
    }
}
