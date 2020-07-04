// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import bitcoinunlimited.libbitcoincash.*
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.logging.Logger


val LOCAL_CURRENCY_PREF = "localCurrency"
val SHOW_DEV_INFO = "devinfo"
val BCH_EXCLUSIVE_NODE_SWITCH = "BCH.exclusiveNodeSwitch"
val BCH_EXCLUSIVE_NODE = "BCH.exclusiveNode"
val BCH_PREFER_NODE_SWITCH = "BCH.preferNodeSwitch"
val BCH_PREFER_NODE = "BCH.preferNode"

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

    var origTitle = String()
    var origTitleBackground: ColorDrawable? = null  //* The app's title background color (I will sometimes overwrite it with a temporary error message)

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(resource: Int) = displayNotice(getString(resource))

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(msg: String, then: (()->Unit)? = null, time: Long = NOTICE_DISPLAY_TIME)
    {
        laterUI {
            // This coroutine has to be limited to this thread because only the main thread can touch UI views
            // Display the error by changing the title and title bar color temporarily
            setTitle(msg);

            var titlebar: View = findViewById(R.id.action_bar)
            val errorColor = ContextCompat.getColor(applicationContext, R.color.notice)
            titlebar.background = ColorDrawable(errorColor)

            delay(time)
            setTitle(origTitle)
            origTitleBackground?.let { titlebar.background = it }
            if (then != null) then()
        }
    }

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

        origTitle = title.toString()
        var titlebar: View = findViewById(R.id.action_bar)
        origTitleBackground = ColorDrawable(ContextCompat.getColor(applicationContext, R.color.titleBackground))

        origTitleBackground?.let { titlebar.background = it }  // Set the title background color here, so we don't need to match the background defined in some resource file

        val preferenceDB:SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        SetupBooleanPreferenceGui(SHOW_DEV_INFO, preferenceDB, GuiDeveloperView)
        SetupBooleanPreferenceGui(BCH_EXCLUSIVE_NODE_SWITCH, preferenceDB, GuiBchExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(BCH_PREFER_NODE_SWITCH, preferenceDB, GuiBchPreferNodeSwitch)

        SetupTextPreferenceGui(BCH_EXCLUSIVE_NODE,preferenceDB, GuiBchExclusiveNode)
        SetupTextPreferenceGui(BCH_PREFER_NODE,preferenceDB, GuiBchPreferNode)

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

    override fun onStop()
    {
        val prefs:SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        var exclusiveNode:String? = null
        var preferNode:String? = null

        if (prefs.getBoolean(BCH_EXCLUSIVE_NODE_SWITCH, false) == true)
        {
            exclusiveNode = prefs.getString(BCH_EXCLUSIVE_NODE, null)
            if (exclusiveNode?.length == 0) exclusiveNode = null
        }

        if (prefs.getBoolean(BCH_PREFER_NODE_SWITCH, false) == true)
        {
            preferNode = prefs.getString(BCH_PREFER_NODE, null)
            if (preferNode?.length == 0) preferNode = null
        }


        val appv = app
        if (appv != null)  // for every account on this blockchain, install the exclusive node or send a null saying not exclusive anymore
            for (account in appv.accounts.values)
            {
                if (account.chain.chainSelector == ChainSelector.BCHMAINNET)
                {
                    val dport = BlockchainPort[account.chain.chainSelector]!!

                    if (true)
                    {
                        val (ip, port) = try
                        {
                            if (exclusiveNode != null) SplitIpPort(exclusiveNode, dport) else Pair(null, dport)
                        }
                        catch (e: Exception)
                        {
                            Pair(null, dport)
                        }
                        account.cnxnMgr.exclusiveNode(ip, port)
                    }

                    if (true)
                    {
                        val (ip, port) = try
                        {
                            if (preferNode != null) SplitIpPort(preferNode, dport) else Pair(null, dport)
                        }
                        catch (e:Exception)
                        {
                            Pair(null, dport)
                        }
                        account.cnxnMgr.preferNode(ip, port)
                    }
                }
            }

        super.onStop()
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
    fun onClearIdentityDomains(v: View?)
    {
        GlobalScope.launch {
            val wallet:CommonWallet = try
                {
                    ((application as WallyApp).primaryWallet as CommonWallet)
                }
                catch (e: PrimaryWalletInvalidException)
                {
                    //displayError(R.string.pleaseWait)
                    return@launch
                }
            wallet.identityDomain.clear()
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
            // coin.wallet.blockchain.rediscover()
            coin.wallet.rediscover()
        }
        displayNotice(i18n(R.string.rediscoverNotice))
    }

    @Suppress("UNUSED_PARAMETER")
    /** Reassess unconfirmed transactions */
    public fun onAssessUnconfirmedButton(v: View)
    {
        val accountName = deleteWalletAccountChoice.selectedItem.toString()
        val coin = accounts[accountName]
        if (coin == null) return
        GlobalScope.launch {
            coin.wallet.reassessUnconfirmedTx()
        }
        displayNotice(i18n(R.string.unconfAssessmentNotice))
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
        displayNotice(i18n(R.string.accountDeleteNotice))
    }

}
