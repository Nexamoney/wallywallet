package info.bitcoinunlimited.www.wally.ui
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import info.bitcoinunlimited.www.wally.*

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.ui.theme.WallyBorder
import info.bitcoinunlimited.www.wally.ui.views.*
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.SplitBillScreen")

private val tipAmounts = listOf(-1, 0, 5, 10, 15, 20, 25, 30,50)

// Remove when supported by libnexakotlin
val FiatInputFormat = DecimalFormat("#########0.00")

@Composable
fun SplitBillScreen(acct: Account? = wallyApp?.preferredVisibleAccountOrNull() )
{
    val cryptoCurrencyCode = acct?.currencyCode ?: chainToCurrencyCode[ChainSelector.NEXA] ?: "NEXA"
    var usingCurrency by remember { mutableStateOf(fiatCurrencyCode) }
    var usingFiatCurrency by remember { mutableStateOf(true) }
    var usingCurrencySelectedIndex by remember { mutableStateOf(0) }

    var total by remember { mutableStateOf(CurrencyDecimal(0)) }
    var totalAmountString = remember { mutableStateOf<String>("") }

    var amount by remember { mutableStateOf(CurrencyDecimal(0)) }
    var amountString = remember { mutableStateOf(FiatFormat.format(amount)) }

    var tipSelectedIndex by remember { mutableStateOf(0) }
    var tipAmount by remember  { mutableStateOf(CurrencyDecimal(0)) }
    val tipAmountString = remember { mutableStateOf(TextFieldValue(FiatFormat.format(tipAmount))) }

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
        var amt = CurrencyDecimal(inQty)
        if (usingCurrency == fiatCurrencyCode)
        {
            if (acct == null)
            {
                // TODO better error report
                return CurrencyDecimal(0)
            }
            val fpc = acct.fiatPerCoin
            try
            {
                if (fpc == -1.toBigDecimal())  // No conversion
                {
                    amt = CurrencyDecimal(0)
                }
                else amt = amt / CurrencyDecimal(fpc)
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
            return FiatFormat.format(qty) + if (includeCurrencyCode) (" " + fiatCurrencyCode) else ""
        }
        else
        {
            if (acct == null)
            {
                return NexaFormat.format(qty) + if (includeCurrencyCode) (" " + cryptoCurrencyCode) else ""
            }
            else
            {
                return acct.format(qty) + if (includeCurrencyCode) (" " + cryptoCurrencyCode) else ""
            }
        }
    }

    fun updateGivenTotal()
    {
        finalSplit = CurrencyDecimal(total)/CurrencyDecimal(waysSelectedIndex+1)

        val qty = toCrypto(finalSplit)

        var fiatStr: String =""
        if (acct != null)
        {
            val fpc = acct.fiatPerCoin
            if (fpc == -1.toBigDecimal())
            {
                fiatStr = " (" + i18n(S.unavailableExchangeRate) + ")"
            }
            else
            {
                val fiatQty: BigDecimal = qty * fpc
                fiatStr = " " + i18n(S.or) + " " + FiatFormat.format(fiatQty) + " " + fiatCurrencyCode
            }
        }

        finalSplitString  = (acct?.format(qty) ?: NexaFormat.format(qty)) + " " + cryptoCurrencyCode + fiatStr

        val a = acct
        if (a != null)
        {
            finalSplitCrypto = a.toPrimaryUnit(toCrypto(finalSplit))
            val tmp = addr?.address?.toString()
            qrString = if (tmp != null) tmp + "?amount=" + NexaFormat.format(finalSplitCrypto) else null
        }
        else qrString = null
    }

    fun updateNumbers()
    {
        if (tipSelectedIndex == 0)  // selected manual entry, not a percent, so don't change the tip amount
        {
        }
        else
        {
            val pct = tipAmounts[tipSelectedIndex]
            tipAmount = (amount * pct) / 100
            tipAmountString.value = tipAmountString.value.copy(FiatInputFormat.format(tipAmount))
        }
        total = amount + tipAmount
        totalAmountString.value = if (usingFiatCurrency) FiatInputFormat.format(total) else NexaInputFormat.format(total)
        LogIt.info("Total Amount String: " + totalAmountString.value)
        updateGivenTotal()
    }


    val localFocusManager = LocalFocusManager.current
    val sendPadding = if (platform().spaceConstrained) 5.dp else 8.dp
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
        detectTapGestures(onTap = {
            localFocusManager.clearFocus()
        })
    }){
        Column(
          modifier = Modifier.padding(sendPadding).conditional(!platform().spaceConstrained) {
              width(IntrinsicSize.Min)
          }
        ) {
            Text(i18n(S.SplitBillDescription), modifier = Modifier.padding(10.dp))
            Spacer(Modifier.height(10.dp))
            // Amount row:
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                WallyOutLineDecimalEntry(
                  amountString,
                  label = i18n(S.BillAmount),
                  modifier = Modifier.weight(1.0f, false).testTag("SplitBillScreenAmountInput").fillMaxWidth(),
                  suffix = { WallyDropdownMenu(
                    modifier = Modifier.weight(0.75f, false),
                    label = "",
                    items = listOf(fiatCurrencyCode, cryptoCurrencyCode),
                    selectedIndex = usingCurrencySelectedIndex,
                    style = WallyDropdownStyle.Succinct,
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
                  ) }
                ) {
                    amountString.value = it
                    try
                    {
                        amount = CurrencyDecimal(it)
                        updateNumbers()
                        amountString.value
                    }
                    catch(e: Exception)
                    {
                        // X it
                        ""
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            // TIP line
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                WallyDropdownMenu(
                  modifier = Modifier.weight(0.75f).fillMaxWidth(),
                  label = i18n(S.TipPercentage),
                  items = tipAmounts.map { if (it == -1) "--" else it.toString() + "%"},
                  selectedIndex = tipSelectedIndex,
                  style = WallyDropdownStyle.Outlined,
                  onItemSelected = { index, _ ->
                      tipSelectedIndex = index
                      updateNumbers()
                  },
                )
                Text(i18n(S.OR),
                  style = LocalTextStyle.current.copy(
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = FontScale(1.25)
                  ),
                  modifier = Modifier.padding(20.dp, 5.dp, 20.dp, 0.dp))
                WallyOutLineDecimalEntryTFV(tipAmountString,
                  label = i18n(S.TipAmount),
                  onValueChange = {
                      try
                      {
                          val tmp = CurrencyDecimal(it)
                          if (tmp != tipAmount)
                          {
                              tipAmount = tmp
                              tipSelectedIndex = 0  // If the user manually enters a tip, pop the combo box to --
                              updateNumbers()
                              tipAmountString.value = tipAmountString.value.copy(it)
                          }
                          it
                      }
                      catch(e: Exception)
                      {
                          val zero = ""
                          tipAmount = CURRENCY_ZERO
                          tipAmountString.value = tipAmountString.value.copy(zero)
                          zero
                      }
                  },
                  modifier = Modifier.testTag("SplitBillScreenTipInput").weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))

            // Total line: "Total: <amount> NEX"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(0.75F, true)) {
                    OutlinedTextField(
                        value = totalAmountString.value,
                        {
                            // If the user manually enters a total, update the various fields (change the tip amount and final QR)
                            try
                            {
                                val tmp = it.toBigDecimal()
                                if (tmp != total)
                                {
                                    total = tmp
                                    tipAmount = total - amount
                                    tipSelectedIndex = 0  // If the user manually enters a tip, pop the combo box to --
                                    tipAmountString.value = tipAmountString.value.copy(FiatFormat.format(tipAmount))
                                    updateGivenTotal()
                                }

                            } catch(e:Exception)
                            {
                                // Ignore bad input (nothing to do)
                            }
                            totalAmountString.value = it // but we still want to show it so user can clean it up
                        },
                        label = {
                            Text(i18n(S.Total))
                        },
                        suffix = {Text(usingCurrency)},
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
                      )
                }
                Spacer(Modifier.width(5.dp))

                Column {
                    WallyDropdownMenu(
                      modifier = Modifier.width(100.dp),
                      modalModifier = Modifier.width(IntrinsicSize.Min),
                      label = i18n(S.Ways),
                      items = List(10, { it+1 }),
                      selectedIndex = waysSelectedIndex,
                      style = WallyDropdownStyle.Outlined,
                      onItemSelected = { index, _ ->
                          waysSelectedIndex = index
                          updateNumbers()
                      },
                    )
                }
            }
            // space commented out because text entry is so big
            Spacer(Modifier.height(25.dp))

            val s = qrString
            if (s != null)
            {
                Text(i18n(S.QRCodeFor),
                  modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally), //.background(Color.Magenta),
                  style = LocalTextStyle.current.copy(
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                  )
                )
                Text(finalSplitString,
                  modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally), //.background(Color.Magenta),
                  style = LocalTextStyle.current.copy(
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = FontScale(1.25)
                  )
                )
                Box(Modifier.fillMaxSize().align(Alignment.CenterHorizontally)) {
                    QrCode(s, Modifier.background(Color.White).width(300.dp).height(300.dp).align(Alignment.Center).border(BorderStroke(2.dp,
                        WallyBorder
                    )))
                }
            }
        }
    }

}
fun Modifier.conditional(condition : Boolean, modifier : Modifier.() -> Modifier) : Modifier {
    return if (condition) {
        then(modifier(Modifier))
    } else {
        this
    }
}
