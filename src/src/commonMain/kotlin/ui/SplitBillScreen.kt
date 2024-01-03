package info.bitcoinunlimited.www.wally.ui
import androidx.compose.foundation.background
import info.bitcoinunlimited.www.wally.ui.theme.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.ui.theme.WallySwitch
import info.bitcoinunlimited.www.wally.ui.theme.WallySwitchRow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SplitBillScreen")

private val tipAmounts = listOf(-1, 0, 5, 10, 15, 20, 25, 30,50)

fun CurrencyDecimal(b: BigDecimal): BigDecimal
{
    return BigDecimal.fromBigDecimal(b, currencyMath)
}

@Composable
fun SplitBillScreen(nav: ScreenNav)
{
   //  fiatCurrencyCode = preferenceDB.getString(LOCAL_CURRENCY_PREF, "USD")
    val acct = wallyApp?.focusedAccount
    val cryptoCurrencyCode = acct?.currencyCode ?: chainToCurrencyCode[ChainSelector.NEXA] ?: "NEXA"
    var currencyTypeExpanded by remember { mutableStateOf(false) }
    var usingCurrency by remember { mutableStateOf(fiatCurrencyCode) }
    var usingFiatCurrency by remember { mutableStateOf(true) }
    var usingCurrencySelectedIndex by remember { mutableStateOf(0) }

    var total by remember { mutableStateOf(CurrencyDecimal(0)) }

    var amount by remember { mutableStateOf(CurrencyDecimal(0)) }
    var amountString by remember { mutableStateOf(fiatFormat.format(amount)) }

    var tipSelectedIndex by remember { mutableStateOf(0) }
    var tipAmount = CurrencyDecimal(0)
    var tipAmountString by remember { mutableStateOf(fiatFormat.format(tipAmount)) }

    var waysSelectedIndex by remember { mutableStateOf(0) }

    var finalSplit by remember { mutableStateOf(BigDecimal.ZERO) }
    var finalSplitCrypto by remember { mutableStateOf(BigDecimal.ZERO) }
    var finalSplitString by remember { mutableStateOf("") }

    val rowSpacer = 20.dp

    val addr = acct?.currentReceive

    var qrString by remember { mutableStateOf<String?>(null) }

    //? Converts an input quantity to its value in crypto, if its not ALREADY in crypto based on the selected splitCurrencyType
    fun toCrypto(inQty: BigDecimal): BigDecimal
    {
        var amt = inQty
        amt = CurrencyDecimal(inQty.toPlainString())


        if (usingCurrency == fiatCurrencyCode)
        {
            if (acct == null)
            {
                // TODO better error report
                return CurrencyDecimal(0)
            }
            val fpc = acct!!.fiatPerCoin
            try
            {
                if (fpc == -1.toBigDecimal())  // No conversion
                {
                    amt = CurrencyDecimal(0)
                }
                else amt = amt / fpc
            }
            catch(e: ArithmeticException)
            {
                amt = CurrencyDecimal(0)
            }
        }
        return amt
    }

    fun formatAsInputCurrency(qty: BigDecimal, includeCurrencyCode: Boolean = false): String
    {
        if (usingCurrency == fiatCurrencyCode)
        {
            return fiatFormat.format(qty) + " " + fiatCurrencyCode
        }
        else
        {
            if (acct == null)
            {
                return nexFormat.format(qty) + " " + cryptoCurrencyCode
            }
            else
            {
                return acct!!.format(qty) + " " + cryptoCurrencyCode
            }
        }
    }

    fun updateNumbers()
    {
        if (tipSelectedIndex == 0)  // selected manual entry, not a percent, so don't change the tip amount
        {
        }
        else
        {
            val pct = tipAmounts[tipSelectedIndex]
            tipAmount = (amount*pct)/100
            tipAmountString = fiatFormat.format(tipAmount)
        }
        total = amount + tipAmount

        finalSplit = CurrencyDecimal(total)/CurrencyDecimal(waysSelectedIndex+1)

        val qty = toCrypto(finalSplit)

        var fiatStr: String =""
        if (acct != null)
        {
            val fpc = acct!!.fiatPerCoin
            if (fpc == -1.toBigDecimal())
            {
                fiatStr = " (" + i18n(S.unavailableExchangeRate) + ")"
            }
            else
            {
                val fiatQty: BigDecimal = qty * fpc
                fiatStr = " " + i18n(S.or) + " " + fiatFormat.format(fiatQty) + " " + fiatCurrencyCode
            }
        }

        finalSplitString  = (acct?.format(qty) ?: nexFormat.format(qty)) + " " + cryptoCurrencyCode + fiatStr

        val a = acct
        if (a != null)
        {
            finalSplitCrypto = a.toPrimaryUnit(toCrypto(finalSplit))
            val tmp = addr?.address?.toString()
            qrString = if (tmp != null) tmp + "?amount=" + nexFormat.format(finalSplitCrypto) else null
        }
        else qrString = null
    }

    Column(
      modifier = Modifier.padding(16.dp).fillMaxSize()
    ) {
        ConstructTitleBar(nav, S.title_split_bill)
        Text(i18n(S.SplitBillDescription))
        Spacer(Modifier.height(10.dp))
        // Amount row:
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            SectionText(S.Amount)
            WallyTextEntry(
              value = amountString,
              onValueChange = {
                  amountString = it
                  try
                  {
                      amount = CurrencyDecimal(it)
                      updateNumbers()
                  }
                  catch(e: Exception)
                  {
                      // X it
                  }
              },
              modifier = Modifier.weight(1.0f, false))
            Spacer(Modifier.width(rowSpacer/5))
            WallyDropdownMenu(
              modifier = Modifier.weight(0.75f, false),
              label = "",
              items = listOf(fiatCurrencyCode, cryptoCurrencyCode),
              selectedIndex = usingCurrencySelectedIndex,
              style = WallyDropdownStyle.Field,
              onItemSelected = { index, _ ->
                  usingCurrencySelectedIndex = index
                  if (index == 0)
                  {
                      usingFiatCurrency = true
                      usingCurrency = fiatCurrencyCode
                  }
                  else
                  {
                      usingFiatCurrency = false
                      usingCurrency = cryptoCurrencyCode
                  }
                  updateNumbers()
                               },
            )
        }
        Spacer(Modifier.height(5.dp))

        // TIP line
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionText(S.Tip)
            Spacer(Modifier.width(rowSpacer))
            WallyDropdownMenu(
              modifier = Modifier.width(IntrinsicSize.Min).weight(0.75f),
              label = "",
              items = tipAmounts.map { if (it == -1) "--" else it.toString() + "%"},
              selectedIndex = tipSelectedIndex,
              style = WallyDropdownStyle.Field,
              onItemSelected = { index, _ ->
                  tipSelectedIndex = index
                  updateNumbers()
              },
            )
            Spacer(Modifier.width(rowSpacer))
            Text(i18n(S.asciiArrow))
            Spacer(Modifier.width(rowSpacer))
            WallyTextEntry(
              value = tipAmountString,
              onValueChange = {
                  tipAmountString = it
                  try
                  {
                      tipAmount = CurrencyDecimal(it)
                      tipSelectedIndex = 0  // If the user manually enters a tip, pop the combo box to --
                      updateNumbers()
                  }
                  catch(e: Exception)
                  {
                      tipAmount = CURRENCY_ZERO
                  }
              },
              modifier = Modifier.width(IntrinsicSize.Min).weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        // Total line: "Total: <amount> NEX"
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionText(S.Total)
            Spacer(Modifier.width(rowSpacer))
            if (usingFiatCurrency) SectionText(fiatFormat.format(total))
            else SectionText(nexFormat.format(total))
            Spacer(Modifier.width(rowSpacer))
            SectionText(usingCurrency)
        }
        // space commented out because text entry is so big
        // Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionText(S.Ways)
            WallyDropdownMenu(
              modifier = Modifier.width(IntrinsicSize.Min),
              modalModifier = Modifier.width(IntrinsicSize.Min),
              label = "",
              items = List(10, { it+1 }),
              selectedIndex = waysSelectedIndex,
              style = WallyDropdownStyle.Field,
              onItemSelected = { index, _ ->
                  waysSelectedIndex = index
                  updateNumbers()
              },
            )
        }
        Spacer(Modifier.height(30.dp))


        Text(finalSplitString,
          modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally), //.background(Color.Magenta),
          style = LocalTextStyle.current.copy(
            color = Color.Black,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = LocalTextStyle.current.fontSize.times(1.25)
          ))

        val s = qrString
        if (s != null)
        {
            Box(Modifier.padding(20.dp).fillMaxWidth().fillMaxHeight().align(Alignment.CenterHorizontally)) {
                QrCode(s, Modifier.background(Color.White).width(300.dp).height(300.dp).align(Alignment.Center))
            }
        }
    }
}
