// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.View.*
import android.widget.Adapter
import android.widget.AdapterView
import androidx.core.content.ContextCompat
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.databinding.ActivitySettingsBinding
import java.math.BigDecimal
import java.util.logging.Logger


val LOCAL_CURRENCY_PREF = "localCurrency"
val PRIMARY_ACT_PREF = "primaryAccount"
val DEV_MODE_PREF = "devinfo"
val SHOW_IDENTITY_PREF = "showIdentityMenu"
val SHOW_TRICKLEPAY_PREF = "showTricklePayMenu"
val SHOW_ASSETS_PREF = "showAssetsMenu"
val CONFIRM_ABOVE_PREF = "confirmAbove"

val ACCESS_PRICE_DATA_PREF = "accessPriceData"

val EXCLUSIVE_NODE_SWITCH = "exclusiveNodeSwitch"
val CONFIGURED_NODE = "NodeAddress"
val PREFER_NODE_SWITCH = "preferNodeSwitch"

var currentlySelectedAccount = chainToURI[ChainSelector.NEXA]  // The default crypto I'm using

private val LogIt = Logger.getLogger("BU.wally.settings")


fun enableMenu(ctxt: Context, menuPref: String)
{
    val prefDB = ctxt.getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)
    val show = prefDB.getBoolean(menuPref, false)
    if (!show)
    {
        with(prefDB.edit())
        {
            putBoolean(menuPref, true)
            commit()
        }
    }
}

// SharedPreferences is used to communicate settings from this activity to the rest of the program and to persist these choices between executions
@Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
class Settings : CommonActivity()
{
    private lateinit var ui:ActivitySettingsBinding
    var app: WallyApp? = null

    var devModeChecks = 0

    val accounts: MutableMap<String, Account>
        get() = app!!.accounts

    @Suppress("UNUSED_PARAMETER")
    fun onFiatChange(guiElem: View?): Boolean
    {
        val preferenceDB = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        with(preferenceDB.edit())
        {
            putString(LOCAL_CURRENCY_PREF, ui.GuiFiatCurrencySpinner.selectedItem as String)
            commit()
        }

        // wipe out all the exchange rate info, so we know that new info needs to be loaded for the new fiat currency
        val a = app
        if (a != null)
        {
            for (i in a.accounts)
            {
                i.value.fiatPerCoin = BigDecimal.ZERO

            }
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(ui.root)

        app = (getApplication() as WallyApp)

        origTitle = title.toString()
        var titlebar: View = findViewById(R.id.action_bar)
        origTitleBackground = ColorDrawable(ContextCompat.getColor(applicationContext, R.color.titleBackground))
        origTitleBackground?.let { titlebar.background = it }  // Set the title background color here, so we don't need to match the background defined in some resource file

        ui.GuiSoftwareVersion.text = i18n(R.string.version) % mapOf("ver" to BuildConfig.VERSION_NAME)

        val preferenceDB: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        if (SetupBooleanPreferenceGui(DEV_MODE_PREF, preferenceDB, false, ui.GuiDeveloperInfoSwitch) { _, isChecked ->
              devMode = isChecked
              if (isChecked)
              {
                  //ui.GuiClearIdentityDomains.visibility = VISIBLE
                  ui.GuiLogInterestingData.visibility = VISIBLE
                  ui.nexaregBlockchainSettings.visibility = VISIBLE
                  ui.nexatestBlockchainSettings.visibility = VISIBLE
                  if (devModeChecks == 3)
                  {
                      brokenMode = true
                      this.displayNotice("enabled negative test mode")
                      devModeChecks = 0
                  }
                  else devModeChecks++
              }
              else
              {
                  ui.GuiClearIdentityDomains.visibility = GONE
                  ui.GuiLogInterestingData.visibility = GONE
                  ui.nexaregBlockchainSettings.visibility = GONE
                  ui.nexatestBlockchainSettings.visibility = GONE
                  brokenMode = false
              }
          })
        {
            //ui.GuiClearIdentityDomains.visibility = VISIBLE
            ui.GuiLogInterestingData.visibility = VISIBLE
            ui.nexaregBlockchainSettings.visibility = VISIBLE
            ui.nexatestBlockchainSettings.visibility = VISIBLE
        }
        else
        {
            ui.GuiClearIdentityDomains.visibility = GONE
            ui.GuiLogInterestingData.visibility = GONE
            ui.nexaregBlockchainSettings.visibility = GONE
            ui.nexatestBlockchainSettings.visibility = GONE
        }

        SetupNexCurrencyPreferenceGui(CONFIRM_ABOVE_PREF, preferenceDB, ui.AreYouSureAmt)

        var name = chainToURI[ChainSelector.NEXA]
        SetupBooleanPreferenceGui(name + "." + EXCLUSIVE_NODE_SWITCH, preferenceDB, false, ui.GuiNexaExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(name + "." + PREFER_NODE_SWITCH, preferenceDB, false, ui.GuiNexaPreferNodeSwitch)
        SetupTextPreferenceGui(name + "." + CONFIGURED_NODE, preferenceDB, ui.GuiNexaNodeAddr)

        name = chainToURI[ChainSelector.NEXATESTNET]
        SetupBooleanPreferenceGui(name + "." + EXCLUSIVE_NODE_SWITCH, preferenceDB, false, ui.GuiNexatestExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(name + "." + PREFER_NODE_SWITCH, preferenceDB, false, ui.GuiNexatestPreferNodeSwitch)
        SetupTextPreferenceGui(name + "." + CONFIGURED_NODE, preferenceDB, ui.GuiNexatestNodeAddr)

        name = chainToURI[ChainSelector.NEXAREGTEST]
        SetupBooleanPreferenceGui(name + "." + EXCLUSIVE_NODE_SWITCH, preferenceDB, false, ui.GuiNexaregExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(name + "." + PREFER_NODE_SWITCH, preferenceDB, false, ui.GuiNexaregPreferNodeSwitch)
        SetupTextPreferenceGui(name + "." + CONFIGURED_NODE, preferenceDB, ui.GuiNexaregNodeAddr)

        name = chainToURI[ChainSelector.BCH]
        SetupBooleanPreferenceGui(name + "." + EXCLUSIVE_NODE_SWITCH, preferenceDB, false, ui.GuiBchExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(name + "." + PREFER_NODE_SWITCH, preferenceDB, false, ui.GuiBchPreferNodeSwitch)
        SetupTextPreferenceGui(name + "." + CONFIGURED_NODE, preferenceDB, ui.GuiBchNodeAddr)

        SetupBooleanPreferenceGui(ACCESS_PRICE_DATA_PREF, preferenceDB,true, ui.GuiAccessPriceDataSwitch)
        SetupBooleanPreferenceGui(SHOW_IDENTITY_PREF, preferenceDB,false, ui.GuiIdentityMenu)
        SetupBooleanPreferenceGui(SHOW_TRICKLEPAY_PREF, preferenceDB,false, ui.GuiTricklePayMenu)
        SetupBooleanPreferenceGui(SHOW_ASSETS_PREF, preferenceDB,false, ui.GuiAssetsMenu)

        val curCode: String = preferenceDB.getString(LOCAL_CURRENCY_PREF, "USD") ?: "USD"
        ui.GuiFiatCurrencySpinner.setSelection(curCode)

        ui.GuiFiatCurrencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                this@Settings.onFiatChange(view)
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {
            }
        }

    }

    override fun onStop()
    {
        // Load any temporaries with the final preference choices

        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        var nodeAddr: String? = null

        allowAccessPriceData = prefs.getBoolean(ACCESS_PRICE_DATA_PREF, true)

        for (chain in chainToURI)
        {
            val name = chain.value
            nodeAddr = prefs.getString(name + "." + CONFIGURED_NODE, null)
            val excl = prefs.getBoolean(name + "." + EXCLUSIVE_NODE_SWITCH, false)
            val prefd = prefs.getBoolean(name + "." + PREFER_NODE_SWITCH, false)

            val appv = app
            if (appv != null)  // for every account on this blockchain, install the exclusive node or send a null saying not exclusive anymore
                for (account in appv.accounts.values)
                {
                    if (account.chain.chainSelector == chain.key)
                    {
                        val nodeSet: Set<String> = nodeAddr?.toSet() ?: setOf()
                        if (!excl || (nodeSet.size == 0)) account.cnxnMgr.exclusiveNodes(null)
                        else account.cnxnMgr.exclusiveNodes(nodeSet)
                        if (!prefd || (nodeSet.size == 0)) account.cnxnMgr.preferNodes(null)
                        else account.cnxnMgr.preferNodes(nodeSet)
                    }
                }
        }

        super.onStop()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onLogDebugData(v: View?)
    {
        launch {
            val coins: MutableMap<String, Account> = (getApplication() as WallyApp).accounts

            LogIt.info("LOG DEBUG BUTTON")
            for (c in coins)
            {
                c.value.wallet.debugDump()
            }
        }
    }



}
