package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.DEV_MODE_PREF
import info.bitcoinunlimited.www.wally.ui.LOCAL_CURRENCY_PREF
import info.bitcoinunlimited.www.wally.ui.ChildNav
import info.bitcoinunlimited.www.wally.ui.theme.WallyRowAbkg2
import info.bitcoinunlimited.www.wally.ui.theme.colorDebit
import info.bitcoinunlimited.www.wally.ui.theme.defaultListHighlight
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.nexa.libnexakotlin.*

@Composable fun AccountListView(accounts: List<Account>, selectedAccount: MutableState<Account?>, preferenceDB: SharedPreferences, nav: ChildNav)
{
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 1.dp, horizontal = 4.dp)
        .height(350.dp), // TODO: Position relative to parent view
    ) {
        LazyColumn(
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            accounts.forEach {
                item {
                    val fiatCurrencyCode by remember { mutableStateOf(preferenceDB.getString(LOCAL_CURRENCY_PREF, "USD")) }
                    // TODO: Get FIAT balance instead of using BigDecimal.ZERO directly
                    val fiatValue by remember { mutableStateOf("${i18n(S.approximatelyT)}  ${it.format(BigDecimal.ZERO)}  $fiatCurrencyCode") }
                    val balanceFormatted by remember { mutableStateOf(it.format(it.fromFinestUnit(it.wallet.balance))) }
                    val lockable by remember { mutableStateOf(it.lockable) }
                    val locked by remember { mutableStateOf(it.locked) }
                    val devMode by remember { mutableStateOf( preferenceDB.getBoolean(DEV_MODE_PREF, false)) }
                    val devInfoMock = "mock:devInfoMockdevInfoMockdevInfoMockdevInfoMockdevInfoMockdevInfoMockdevInfoMockdevInfoMockdevInfoMock"
                    val isSelected = selectedAccount.value?.name == it.name
                    val chain = it.wallet.chainSelector
                    val currencyCode = chainToDisplayCurrencyCode[chain]?: throw Exception("Cannot get currencyCode in AccountListView")
                    val unconfirmedMock = 10000L // TODO: observe unconfirmed balance
                    val unconfirmedText = if(unconfirmedMock > 0)
                    {
                        // Format i18n to format string with params unconfirmedFormattedMock and currencyCode?
                        // val unconfirmedFormattedMock =it.format(it.fromFinestUnit(10000L))
                        // "includes + $unconfirmedFormattedMock $currencyCode pending (mock)"
                        i18n(S.incoming)
                    }
                    else
                    {
                        ""
                    }

                    AccountItemView(
                      it.name,
                      balanceFormatted,
                      chain,
                      currencyCode,
                      lockable,
                      locked,
                      isSelected,
                      fiatValue,
                      devInfoMock,
                      devMode,
                      unconfirmedText,
                      onClickAccount = {
                          selectedAccount.value = it
                    },
                      onClickGearIcon = {
                          nav.displayAccount(it)
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun AccountItemView(
  accountName: String,
  balance: String,
  chainSelector: ChainSelector,
  currencyCode: String,
  lockable: Boolean,
  locked: Boolean,
  isSelected: Boolean,
  info: String,
  devInfo: String,
  devMode: Boolean,
  unconfirmedValue: String,
  onClickAccount: () -> Unit,
  onClickGearIcon: () -> Unit
) {
    val backgroundColor = if(isSelected) defaultListHighlight else WallyRowAbkg2
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(backgroundColor)
        .clickable(onClick = onClickAccount),
      contentAlignment = Alignment.Center
    ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row {

                Row(
                  verticalAlignment = Alignment.Bottom
                ) {
                    ResImageView(getAccountIconResPath(chainSelector), Modifier.size(32.dp), "Blockchain icon")
                    Spacer(Modifier.width(32.dp))
                    Text(text = accountName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Row(
                      verticalAlignment = Alignment.Bottom
                    ) {
                        Text(text = balance, fontSize = 28.sp, color = colorDebit)
                        Text(text = currencyCode, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(16.dp))

                    if(lockable)
                    {
                        ResImageView(if (locked) "icons/lock.xml" else "icons/unlock.xml", modifier = Modifier.size(26.dp))
                    }
                }

                Row(
                  horizontalArrangement = Arrangement.End,
                  verticalAlignment = Alignment.Bottom,
                  modifier = Modifier.fillMaxWidth()
                ) {
                    if(isSelected)
                        ResImageView("icons/gear.xml", Modifier.size(26.dp).clickable(onClick = onClickGearIcon))
                }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center
            ) {
                Text(text = info, fontSize = 16.sp)
            }
            if(unconfirmedValue.isNotEmpty())
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = unconfirmedValue, )
                }
            if(devMode)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center
                ){
                    Text(text = devInfo, fontSize = 12.sp)
                }
        }
    }
}

private fun getAccountIconResPath(chainSelector: ChainSelector): String
{
    return when(chainSelector)
    {
        ChainSelector.NEXA -> "icons/nexa_icon.png"
        ChainSelector.NEXATESTNET -> "icons/nexatest_icon.png"
        ChainSelector.NEXAREGTEST -> "icons/nexareg_icon.png"
        ChainSelector.BCH -> "icons/bitcoin_cash_token.xml"
        ChainSelector.BCHTESTNET -> "icons/bitcoin_cash_token.xml"
        ChainSelector.BCHREGTEST -> "icons/bitcoin_cash_token.xml"
    }
}
