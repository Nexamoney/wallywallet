package info.bitcoinunlimited.www.wally.ui
import info.bitcoinunlimited.www.wally.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults.textFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.S
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SettingsScreen")
const val LOCAL_CURRENCY_PREF = "localCurrency"
const val ACCESS_PRICE_DATA_PREF = "accessPriceData"
const val SHOW_IDENTITY_PREF = "showIdentityMenu"
const val SHOW_TRICKLEPAY_PREF = "showTricklePayMenu"
const val SHOW_ASSETS_PREF = "showAssetsMenu"
const val DEV_MODE_PREF = "devinfo"
const val CONFIRM_ABOVE_PREF = "confirmAbove"
const val EXCLUSIVE_NODE_SWITCH = "exclusiveNodeSwitch"
const val CONFIGURED_NODE = "NodeAddress"
const val PREFER_NODE_SWITCH = "preferNodeSwitch"

@Composable
fun SettingsScreen()
{
    val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
    var devMode by remember { mutableStateOf( preferenceDB.getBoolean(DEV_MODE_PREF, false))}

    Column(
      modifier = Modifier
        .fillMaxWidth(),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.SpaceEvenly
    ) {
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
            AccessPriceData(preferenceDB)
            Identity(preferenceDB)
            TricklePay(preferenceDB)
            Assets(preferenceDB)
            DevMode(devMode) {
                preferenceDB.edit().putBoolean(DEV_MODE_PREF, it)
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

@Composable
fun AccessPriceData(preferenceDB: SharedPreferences)
{
    val isChecked = remember { mutableStateOf(preferenceDB.getBoolean(ACCESS_PRICE_DATA_PREF, true)) }

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
          checked = isChecked.value,
          onCheckedChange = {
              isChecked.value = it
              preferenceDB.edit().putBoolean(ACCESS_PRICE_DATA_PREF, it)
          },
          modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )
        Text( i18n(S.AccessPriceData) )
    }
}

@Composable
fun Identity(preferenceDB: SharedPreferences)
{
    val isChecked = remember { mutableStateOf(preferenceDB.getBoolean(SHOW_IDENTITY_PREF, false)) }  // initial value is true

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
          checked = isChecked.value,
          onCheckedChange = {
              isChecked.value = it
              preferenceDB.edit().putBoolean(SHOW_IDENTITY_PREF, it)
          },
          modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )
        Text(
          text = i18n(S.enableIdentityMenu),
        )
    }
}

@Composable
fun TricklePay(preferenceDB: SharedPreferences)
{
    val isChecked = remember { mutableStateOf(preferenceDB.getBoolean(SHOW_TRICKLEPAY_PREF, false)) }  // initial value is true

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
          checked = isChecked.value,
          onCheckedChange = {
              isChecked.value = it
              preferenceDB.edit().putBoolean(SHOW_TRICKLEPAY_PREF, it)
          },
          modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )
        Text(
          text = i18n(S.enableTricklePayMenu),
        )
    }
}

@Composable
fun Assets(preferenceDB: SharedPreferences)
{
    val isChecked = remember { mutableStateOf(preferenceDB.getBoolean(SHOW_ASSETS_PREF,false)) }  // initial value is true

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
          checked = isChecked.value,
          onCheckedChange = {
              isChecked.value = it
              preferenceDB.edit().putBoolean(SHOW_ASSETS_PREF, it)
          },
          modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )
        Text(
          text = i18n(S.enableAssetsMenu),
        )
    }
}

@Composable
fun DevMode(devMode: Boolean, onClick: (Boolean) -> Unit)
{

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
          checked = devMode,
          onCheckedChange = onClick,
          modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )
        Text(
          text = i18n(S.enableDeveloperView),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
          colors = textFieldColors(containerColor = Color.Transparent),
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
        Switch(
          checked = onlyChecked.value,
          onCheckedChange = {
              onlyChecked.value = it
              preferenceDB.edit().putBoolean(exclusiveNodeKey, it)
          },
          modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )

        Text(
          text = i18n(S.only),
          fontSize = 12.sp
        )

        Switch(
          checked = preferChecked.value,
          onCheckedChange = {
              preferChecked.value = it
              preferenceDB.edit().putBoolean(preferNodeKey, it)
          },
          modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
          colors = SwitchDefaults.colors(
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
          )
        )

        Text(
          text = i18n(S.prefer),
          fontSize = 12.sp
        )
    }
}
