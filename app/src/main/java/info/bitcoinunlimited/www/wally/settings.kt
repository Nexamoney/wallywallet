// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.content.Context
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.Spinner
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Logger

val LOCAL_CURRENCY_PREF = "localCurrency"

var cryptoCurrencyCode = "mTBCH"  // The default crypto I'm using

private val LogIt = Logger.getLogger("bitcoinunlimited.settings")

fun Spinner.setSelection(v: String): Boolean
{
    for (i in 0 until count)
    {
        if (getItemAtPosition(i).toString() == v)
        {
            setSelection(i)
            return true
        }
    }
    return false
}

class settings : AppCompatActivity()
{
    // SharedPreferences is used to communicate settings from this activity to the rest of the program and to persist these choices between executions

    val coins:MutableMap<String,Coin>
        get() = (getApplication() as WallyApp).coins

    @Suppress("UNUSED_PARAMETER")
    fun onFiatChange(guiElem: View): Boolean
    {
        val preferenceDB = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        with (preferenceDB.edit())
        {
            putString(LOCAL_CURRENCY_PREF, fiatCurrencySpinner.selectedItem as String)
            commit()
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val preferenceDB = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        val curCode: String = preferenceDB.getString(LOCAL_CURRENCY_PREF, "USD") ?: "USD"
        fiatCurrencySpinner.setSelection(curCode)

        fiatCurrencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long)
            {
                this@settings.onFiatChange(view)
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }

        var wordSeeds: String = ""
        for (c in coins)
        {
           wordSeeds = wordSeeds + c.value.wallet.name + ":" + c.value.wallet.secretWords + "\n"
        }
        wordSeed.text = wordSeeds
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRediscoverBlockchain(v: View?)
    {
        GlobalScope.launch {
            val coins: MutableMap<String, Coin> = (getApplication() as WallyApp).coins

            for (c in coins)
            {
                // If you reset the wallet first, it'll start rediscovering the existing blockchain before it gets reset.
                c.value.wallet.blockchain.rediscover()
                c.value.wallet.rediscover()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onLogDebugData(v: View?)
    {
        GlobalScope.launch {
            val coins: MutableMap<String, Coin> = (getApplication() as WallyApp).coins

            LogIt.info("LOG DEBUG BUTTON")
            for (c in coins)
            {
                c.value.wallet.debugDump()
            }
        }
    }
}
