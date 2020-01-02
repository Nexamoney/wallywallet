// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import bitcoinunlimited.libbitcoincash.*
import kotlinx.android.synthetic.main.activity_split_bill.*
import java.lang.Exception
import java.math.BigDecimal

import java.util.logging.Logger
private val LogIt = Logger.getLogger("bitcoinunlimited.splitBillActivity")

// TODO: allow a bill split to be shared via various means like SM or email

class SplitBillActivity : CommonActivity()
{
    override var navActivityId = -1

    //* Currently selected fiat currency code
    var fiatCurrencyCode: String = "USD"

    //* convenience variable mirroring the Ways GUI element
    var splitWays = BigDecimal(2)
    //* convenience variable mirroring the Tip GUI element
    var tipFrac: BigDecimal? = BigDecimal(0)
    var tipAmt = BigDecimal(0)
    var ignoreTipAmountChange = false  //? This tells us that the program is changing the tip amount, not the user

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_split_bill)

        splitCount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
    {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
        {
            val v = splitCount.getSelectedItem().toString()
            splitWays = v.toBigDecimal()
            LogIt.info("split count clicked: " + v)
            updateUI()
        }

        override fun onNothingSelected(parent: AdapterView<out Adapter>?)
        {
        }
    }

        tipPercentage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
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
        splitCurrencyType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
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

        splitQuantity.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                // Recalculate the tip since the amount changed, unless "--" is selected as the tip %, which means manual tip quantity
                tipFrac?.let {
                    tipAmt = getAmount() * it
                    overrideTipAmount(tipAmt)
                }
                updateUI()
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        splitBillTipAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                if (ignoreTipAmountChange) return
                try
                {
                    val qty = splitBillTipAmount?.text?.toString()?.toBigDecimal(currencyMath)?.setScale(currencyScale)
                    if (qty != null)
                    {
                        tipAmt = qty
                        tipPercentage.setSelection(0)
                    }
                    else
                    {
                        displayError(R.string.invalidQuantity)
                    }
                }
                catch(e:java.lang.NumberFormatException)
                {
                    displayError(R.string.invalidQuantity)
                }
                updateUI()
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
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

    fun overrideTipAmount(qty:BigDecimal)
    {
        ignoreTipAmountChange = true
        splitBillTipAmount.text.clear()
        splitBillTipAmount.text.insert(0,formatAsInputCurrency(qty))
        ignoreTipAmountChange = false

    }


    fun loadtipFracFromSpinner() {
        val v = tipPercentage.getSelectedItem().toString()
        if (v.last() != '%')  // Its the not applicable symbol -- user is entering a tip quantity manually
        {
            tipFrac = null
        }
        else
        {
            tipFrac = v.dropLast(1).toBigDecimal(currencyMath).setScale(currencyScale) / 100.toBigDecimal()  // get rid of the % and convert to a bigdecimal decimal rather than percentage
        }
    }

    fun formatAsInputCurrency(qty: BigDecimal, includeCurrencyCode: Boolean = false):String
    {
        var ctype: String? = splitCurrencyType.selectedItem as? String
        val currencyCodeStr = if (includeCurrencyCode) " " + ctype else ""
        if (ctype == fiatCurrencyCode)
        {
            return fiatFormat.format(qty) + currencyCodeStr
        }
        else
        {
            val coin = ((getApplication() as WallyApp).accounts)[defaultAccount]
            if (coin == null)
            {
                return mBchFormat.format(qty) + currencyCodeStr
            }
            else
            {
                return coin.format(qty) + currencyCodeStr
            }
        }
    }

    override fun onResume()
    {
        super.onResume()

        val prefDb = getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)
        fiatCurrencyCode = prefDb.getString(i18n(R.string.localCurrency), "USD") ?: "USD"

        // Set the send currency type spinner options to your default fiat currency or your currently selected crypto
        val spinData = arrayOf(fiatCurrencyCode, defaultAccount)
        val aa = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinData)
        splitCurrencyType!!.setAdapter(aa)


        splitQuantity.text.append(prefDb.getString("splitbill.splitAmount", "0"))
        val sel1: String = prefDb.getString("splitbill.splitCurrencyType", fiatCurrencyCode) ?: fiatCurrencyCode
        splitCurrencyType.setSelection(sel1)
        val sel2 = prefDb.getString("splitbill.splitWays", "2") ?: "2"
        splitWays = BigDecimal(sel2)
        splitCount.setSelection(sel2)
        val tippct = prefDb.getString("splitbill.tipFrac", null)
        tipAmt = BigDecimal(prefDb.getString("splitbill.tipAmt", "0"))

        overrideTipAmount(tipAmt)

        if ((tippct != null) && (tippct != "null"))
        {
            tipFrac = BigDecimal(tippct)
            val pct = (BigDecimal(tippct) * (100.toBigDecimal())).toInt().toString() + "%"
            tipPercentage.setSelection(pct)
        }
        else
        {
            tipFrac = null
            tipPercentage.setSelection(0)
        }

    }

    override fun onDestroy()
    {
        val prefDb = getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)
        with (prefDb.edit())
        {
            putString("splitbill.splitAmount", splitQuantity.text.toString())
            putString("splitbill.splitCurrencyType", splitCurrencyType.selectedItem as String)
            putString("splitbill.splitWays", splitWays.toString())
            putString("splitbill.tipFrac", tipFrac.toString())
            putString("splitbill.tipAmt", tipAmt.toString())
            commit()
        }
        // clean up my installed callbacks
        tipPercentage.onItemSelectedListener = null
        splitCount.onItemSelectedListener = null
        super.onDestroy()
    }

    fun getAmount(): BigDecimal
    {
        try
        {
            return splitQuantity.text.toString().toBigDecimal(currencyMath).setScale(currencyScale)
        }
        catch(e: Exception)  // If we can't parse the user's input for any reason, just make it 0
        {
            return BigDecimal(0, currencyMath).setScale(currencyScale)
        }
    }

    fun getAmountInCrypto(): BigDecimal = toCrypto(getAmount())

    //? Converts an input quantity to its value in crypto, if its not ALREADY in crypto based on the selected splitCurrencyType
    fun toCrypto(inQty: BigDecimal): BigDecimal
    {
        var amt = inQty
        var ctype: String? = splitCurrencyType.selectedItem as? String

        if (ctype == fiatCurrencyCode)
        {
            val coins:MutableMap<String,Account> = (getApplication() as WallyApp).accounts

            val coin = coins[defaultAccount]
            if (coin == null)
            {
                // TODO better error report
                return 0.toBigDecimal()
            }
            amt = amt / coin.fiatPerCoin
        }
        return amt
    }

    fun updateUI()
    {
        try
        {

            var total = getAmount() + tipAmt
            splitBillTotal.text = formatAsInputCurrency(total, true)
            var qty = toCrypto(total)/splitWays

            val coins:MutableMap<String,Account> = (getApplication() as WallyApp).accounts
            val coin = coins[defaultAccount]
            var fiatStr = ""
            if (coin != null)
            {
                val fiatQty:BigDecimal = qty*coin.fiatPerCoin
                fiatStr = " " + i18n(R.string.or) + " " + fiatFormat.format(fiatQty) + " " + fiatCurrencyCode
                coin.receiveInfoWithQuantity(qty, 200, { updateQR(it) } )
            }
            perSplitAmount.text = mBchFormat.format(qty) + " " + defaultAccount + fiatStr
        }
        catch(e: java.lang.NumberFormatException)
        {
            perSplitAmount.text = i18n(R.string.badAmount)
        }
        catch(e: java.lang.ArithmeticException)  // division by zero because coin.fiatPerCoin is not loaded yet
        {
            displayNotice(i18n(R.string.cantConvert))
            perSplitAmount.text = i18n(R.string.cantConvert)
        }
    }

    fun updateQR(v: Account.ReceiveInfoResult)
    {
        laterUI {
            if (v.qr != null)
                splitBillQR.setImageBitmap(v.qr)
        }
    }


}
