// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.content.Context
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import bitcoinunlimited.libbitcoincash.dbgAssertGuiThread
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Logger

val LOCAL_CURRENCY_PREF = "localCurrency"

var defaultAccount = "mTBCH"  // The default crypto I'm using

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

// SharedPreferences is used to communicate settings from this activity to the rest of the program and to persist these choices between executions
class Settings : AppCompatActivity()
{

    var app: WallyApp? = null

    val accounts:MutableMap<String,Account>
        get() = app!!.accounts

    @Suppress("UNUSED_PARAMETER")
    fun onFiatChange(guiElem: View?): Boolean
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

        app = (getApplication() as WallyApp)

        val preferenceDB = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        val curCode: String = preferenceDB.getString(LOCAL_CURRENCY_PREF, "USD") ?: "USD"
        fiatCurrencySpinner.setSelection(curCode)

        fiatCurrencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                this@Settings.onFiatChange(view)
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {

            }
        }

        var wordSeeds: String = ""
        for (c in accounts)
        {
           wordSeeds = wordSeeds + c.value.wallet.name + ":" + c.value.wallet.secretWords + "\n"
        }
        wordSeed.text = wordSeeds

        dbgAssertGuiThread()
        // Set up the crypto spinners to contain all the cryptos this wallet supports
        val coinSpinData = accounts.keys.toTypedArray()
        val coinAa = ArrayAdapter(this, android.R.layout.simple_spinner_item, coinSpinData)
        deleteWalletAccountChoice?.setAdapter(coinAa)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onLogDebugData(v: View?)
    {
        GlobalScope.launch {
            val coins: MutableMap<String, Account> = (getApplication() as WallyApp).accounts

            LogIt.info("LOG DEBUG BUTTON")
            for (c in coins)
            {
                c.value.wallet.debugDump()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRediscoverBlockchain(v: View?)
    {
        val accountName = deleteWalletAccountChoice.selectedItem.toString()
        val coin = accounts[accountName]
        if (coin == null) return
        GlobalScope.launch {
            // If you reset the wallet first, it'll start rediscovering the existing blockchain before it gets reset.
            coin.wallet.blockchain.rediscover()
            coin.wallet.rediscover()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    /** Delete a wallet account */
    public fun onDeleteAccountButton(v: View)
    {
        // TODO add a big warning and confirmation dialog
        val accountName = deleteWalletAccountChoice.selectedItem.toString()
        val account = accounts[accountName]
        if (account == null) return
        account.detachUI()

        GlobalScope.launch { // cannot access db in UI thread
            accounts.remove(accountName)  // remove this coin from any global access before we delete it
            app?.saveActiveAccountList()
            account.delete()
            }
    }

}
