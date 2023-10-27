// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import info.bitcoinunlimited.www.wally.databinding.ActivitySplitBillBinding
import java.lang.Exception
import com.ionspin.kotlin.bignum.decimal.*
import org.nexa.libnexakotlin.*



private val LogIt = GetLog("BU.wally.splitBillActivity")

// TODO: allow a bill split to be shared via various means like SM or email

class SplitBillActivity : CommonNavActivity()
{
    private lateinit var ui:ActivitySplitBillBinding
    override var navActivityId = -1

    //* Currently selected fiat currency code
    var fiatCurrencyCode: String = "USD"
    var cryptoCurrencyCode: String = ""

    //* The relevant account (only used to figure out the exchange rate and crypto currency code)
    var acct: Account? = null

    //* convenience variable mirroring the Ways GUI element
    var splitWays = BigDecimal.fromInt(2)

    //* convenience variable mirroring the Tip GUI element
    var tipFrac: BigDecimal? = CURRENCY_0
    var tipAmt = CURRENCY_0
    var ignoreTipAmountChange = false  //? This tells us that the program is changing the tip amount, not the user

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivitySplitBillBinding.inflate(layoutInflater)
        setContentView(ui.root)

        if (true)
        {
            val acts = wallyApp!!.accounts
            if (!acts.isEmpty())
            {
                acct = acts[currentlySelectedAccount] ?: acts.values.first()
                cryptoCurrencyCode = acct?.currencyCode ?: ""
            }
        }

        ui.splitCount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                val v = ui.splitCount.getSelectedItem().toString()
                splitWays = v.toBigDecimal()
                LogIt.info("split count clicked: " + v)
                updateUI()
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {
            }
        }

        ui.tipPercentage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                loadtipFracFromSpinner()
                tipFrac?.let {
                    tipAmt = getAmount() * it  // override the amount entry field because the user selected a percentage
                    overrideTipAmount(tipAmt)
                }
                updateUI()
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {
            }
        }
        ui.splitCurrencyType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                updateUI()
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)  // should not be possible
            {
                updateUI()
            }
        }

        ui.splitQuantity.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p0: Editable?)
            {
                // Recalculate the tip since the amount changed, unless "--" is selected as the tip %, which means manual tip quantity
                tipFrac?.let {
                    tipAmt = getAmount() * it
                    overrideTipAmount(tipAmt)
                }
                updateUI()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })

        ui.splitBillTipAmount.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p0: Editable?)
            {
                if (ignoreTipAmountChange) return
                try
                {
                    val qty = CurrencyDecimal(ui.splitBillTipAmount.text.toString())
                    if (qty != null)
                    {
                        tipAmt = qty
                        ui.tipPercentage.setSelection(0)
                    }
                    else
                    {
                        displayError(R.string.invalidQuantity)
                    }
                } catch (e: java.lang.NumberFormatException)
                {
                    displayError(R.string.invalidQuantity)
                }
                updateUI()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })

        /*
        val prefDb = getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)

        if (prefDb != null)
        {
            splitQuantity.text.append(prefDb.getString("splitbill.splitAmount", "0"))
            splitCurrencyType.setSelection(prefDb.getString("splitbill.splitCurrencyType", fiatCurrencyCode))
            splitWays = BigDecimal(prefDb.getString("splitbill.splitWays", "2"))
            tipFrac = BigDecimal(prefDb.getString("splitbill.tipFrac", "0"))
            tipAmt = BigDecimal(prefDb.getString("splitbill.tipAmt", "0"))
        }
        else
        {
            splitQuantity.text.clear()
            splitQuantity.text.insert(0, "0")
        }

        tipPercentage.setSelection((tipFrac*(100.toBigDecimal())).toString() + "%")
        splitBillTipAmount.text.clear()
        splitBillTipAmount.text.insert(0,tipAmt.toString())

         */
    }


    /*
    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?)
    {
        super.onSaveInstanceState(outState, outPersistentState)
        if (outState != null)
        {
            outState.putString("splitbill.splitAmount", splitQuantity.text.toString())
            outState.putString("splitbill.splitCurrencyType", splitCurrencyType.selectedItem as String)
            outState.putString("splitbill.splitWays", splitWays.toString())
            outState.putString("splitbill.tipFrac", tipFrac.toString())
            outState.putString("splitbill.tipAmt", tipAmt.toString())
        }
    }
     */

    fun overrideTipAmount(qty: BigDecimal)
    {
        ignoreTipAmountChange = true
        ui.splitBillTipAmount.text.clear()
        ui.splitBillTipAmount.text.insert(0, formatAsInputCurrency(qty))
        ignoreTipAmountChange = false

    }


    fun loadtipFracFromSpinner()
    {
        val v = ui.tipPercentage.getSelectedItem().toString()
        if (v.last() != '%')  // Its the not applicable symbol -- user is entering a tip quantity manually
        {
            tipFrac = null
        }
        else
        {
            tipFrac = CurrencyDecimal(v.dropLast(1)) / BigDecimal.fromInt(100)  // get rid of the % and convert to a bigdecimal decimal rather than percentage
        }
    }

    fun formatAsInputCurrency(qty: BigDecimal, includeCurrencyCode: Boolean = false): String
    {
        var ctype: String? = ui.splitCurrencyType.selectedItem as? String
        val currencyCode = if (!includeCurrencyCode) "" else ctype
        if (ctype == fiatCurrencyCode)
        {
            return fiatFormat.format(qty) + " " + currencyCode
        }
        else
        {
            if (acct == null)
            {
                return mBchFormat.format(qty) + " " + currencyCode
            }
            else
            {
                return acct!!.format(qty) + " " + currencyCode
            }
        }
    }

    override fun onResume()
    {
        super.onResume()

        val prefDb = getSharedPreferences(i18n(R.string.preferenceFileName), PREF_MODE_PRIVATE)
        fiatCurrencyCode = prefDb.getString(i18n(R.string.localCurrency), "USD") ?: "USD"

        // Set the send currency type spinner options to your default fiat currency or your currently selected crypto
        val spinData = arrayOf(fiatCurrencyCode, cryptoCurrencyCode)
        val aa = ArrayAdapter(this, R.layout.currency_selection_spinner, spinData)
        ui.splitCurrencyType.setAdapter(aa)


        ui.splitQuantity.text.append(prefDb.getString("splitbill.splitAmount", "0"))
        val sel1: String = prefDb.getString("splitbill.splitCurrencyType", fiatCurrencyCode) ?: fiatCurrencyCode
        ui.splitCurrencyType.setSelection(sel1)
        val sel2 = prefDb.getString("splitbill.splitWays", "2") ?: "2"
        splitWays = CurrencyDecimal(sel2)
        ui.splitCount.setSelection(sel2)
        val tippct = prefDb.getString("splitbill.tipFrac", null)
        tipAmt = CurrencyDecimal(prefDb.getString("splitbill.tipAmt", "0") ?: "0")

        overrideTipAmount(tipAmt)

        if ((tippct != null) && (tippct != "null"))
        {
            tipFrac = CurrencyDecimal(tippct)
            val pct = (CurrencyDecimal(tippct) * (100.toBigDecimal())).toLong().toString() + "%"
            ui.tipPercentage.setSelection(pct)
        }
        else
        {
            tipFrac = null
            ui.tipPercentage.setSelection(0)
        }

    }

    override fun onDestroy()
    {
        val prefDb = getSharedPreferences(i18n(R.string.preferenceFileName), PREF_MODE_PRIVATE)
        with(prefDb.edit())
        {
            putString("splitbill.splitAmount", ui.splitQuantity.text.toString())
            putString("splitbill.splitCurrencyType", ui.splitCurrencyType.selectedItem as String)
            putString("splitbill.splitWays", splitWays.toString())
            putString("splitbill.tipFrac", tipFrac.toString())
            putString("splitbill.tipAmt", tipAmt.toString())
            commit()
        }
        // clean up my installed callbacks
        ui.tipPercentage.onItemSelectedListener = null
        ui.splitCount.onItemSelectedListener = null
        super.onDestroy()
    }

    fun getAmount(): BigDecimal
    {
        try
        {
            return CurrencyDecimal(ui.splitQuantity.text.toString())
        } catch (e: Exception)  // If we can't parse the user's input for any reason, just make it 0
        {
            return CURRENCY_0
        }
    }

    fun getAmountInCrypto(): BigDecimal = toCrypto(getAmount())

    //? Converts an input quantity to its value in crypto, if its not ALREADY in crypto based on the selected splitCurrencyType
    fun toCrypto(inQty: BigDecimal): BigDecimal
    {
        var amt = inQty
        var ctype: String? = ui.splitCurrencyType.selectedItem as? String

        if (ctype == fiatCurrencyCode)
        {
            if (acct == null)
            {
                // TODO better error report
                return 0.toBigDecimal()
            }
            val fpc = acct!!.fiatPerCoin
            try
            {
                if (fpc == -1.toBigDecimal())  // No conversion
                {
                    amt = BigDecimal.ZERO
                }
                else amt = amt / fpc
            }
            catch(e: ArithmeticException)
            {
                amt = BigDecimal.ZERO
            }
            catch(e: java.lang.ArithmeticException)
            {
                amt = BigDecimal.ZERO
            }
        }
        return amt
    }

    fun updateUI()
    {
        try
        {

            var total = getAmount() + tipAmt
            ui.splitBillTotal.text = formatAsInputCurrency(total, true)
            var qty = if (splitWays > BigDecimal.ZERO) toCrypto(total) / splitWays else toCrypto(total)

            var fiatStr = ""
            if (acct != null)
            {
                val fpc = acct!!.fiatPerCoin
                if (fpc == -1.toBigDecimal())
                {
                    fiatStr = " (" + i18n(R.string.unavailableExchangeRate) + ")"
                }
                else
                {
                    val fiatQty: BigDecimal = qty * fpc
                    fiatStr = " " + i18n(R.string.or) + " " + fiatFormat.format(fiatQty) + " " + fiatCurrencyCode
                }
                acct!!.receiveInfoWithQuantity(qty, 200, { updateQR(it) })
            }

            ui.perSplitAmount.text = (acct?.format(qty) ?: mBchFormat.format(qty)) + " " + cryptoCurrencyCode + fiatStr
        } catch (e: java.lang.NumberFormatException)
        {
            ui.perSplitAmount.text = i18n(R.string.badAmount)
        } catch (e: java.lang.ArithmeticException)  // division by zero because coin.fiatPerCoin is not loaded yet
        {
            displayNotice(i18n(R.string.cantConvert))
            ui.perSplitAmount.text = i18n(R.string.cantConvert)
        }
    }

    fun updateQR(v: ReceiveInfoResult)
    {
        laterUI {
            if (v.qr != null)
                ui.splitBillQR.setImageBitmap(v.qr)
        }
    }


}
