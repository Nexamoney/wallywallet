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
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Spinner
import androidx.core.content.ContextCompat
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.databinding.ActivitySettingsBinding
import java.math.BigDecimal
import java.util.logging.Logger


val LOCAL_CURRENCY_PREF = "localCurrency"
val SHOW_DEV_INFO = "devinfo"

val EXCLUSIVE_NODE_SWITCH = "exclusiveNodeSwitch"
val CONFIGURED_NODE = "NodeAddress"
val PREFER_NODE_SWITCH = "preferNodeSwitch"

var defaultBlockchain = chainToURI[ChainSelector.NEXA]  // The default crypto I'm using

private val LogIt = Logger.getLogger("BU.wally.settings")

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

enum class ConfirmationFor
{
    Delete, Rediscover, RediscoverBlockchain, Reassess, RecoveryPhrase
}

// SharedPreferences is used to communicate settings from this activity to the rest of the program and to persist these choices between executions
class Settings : CommonActivity()
{
    private lateinit var ui:ActivitySettingsBinding
    var app: WallyApp? = null

    val accounts: MutableMap<String, Account>
        get() = app!!.accounts

    var askingAbout: ConfirmationFor? = null

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

        val preferenceDB: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        if (SetupBooleanPreferenceGui(SHOW_DEV_INFO, preferenceDB, ui.GuiDeveloperInfoSwitch) { _, isChecked ->
              if (isChecked)
              {
                  ui.GuiClearIdentityDomains.visibility = VISIBLE
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
          })
        {
            ui.GuiClearIdentityDomains.visibility = VISIBLE
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

        var name = chainToURI[ChainSelector.NEXA]
        SetupBooleanPreferenceGui(name + "." + EXCLUSIVE_NODE_SWITCH, preferenceDB, ui.GuiNexaExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(name + "." + PREFER_NODE_SWITCH, preferenceDB, ui.GuiNexaPreferNodeSwitch)
        SetupTextPreferenceGui(name + "." + CONFIGURED_NODE, preferenceDB, ui.GuiNexaNodeAddr)

        name = chainToURI[ChainSelector.NEXATESTNET]
        SetupBooleanPreferenceGui(name + "." + EXCLUSIVE_NODE_SWITCH, preferenceDB, ui.GuiNexatestExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(name + "." + PREFER_NODE_SWITCH, preferenceDB, ui.GuiNexatestPreferNodeSwitch)
        SetupTextPreferenceGui(name + "." + CONFIGURED_NODE, preferenceDB, ui.GuiNexatestNodeAddr)

        name = chainToURI[ChainSelector.NEXAREGTEST]
        SetupBooleanPreferenceGui(name + "." + EXCLUSIVE_NODE_SWITCH, preferenceDB, ui.GuiNexaregExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(name + "." + PREFER_NODE_SWITCH, preferenceDB, ui.GuiNexaregPreferNodeSwitch)
        SetupTextPreferenceGui(name + "." + CONFIGURED_NODE, preferenceDB, ui.GuiNexaregNodeAddr)

        name = chainToURI[ChainSelector.BCH]
        SetupBooleanPreferenceGui(name + "." + EXCLUSIVE_NODE_SWITCH, preferenceDB, ui.GuiBchExclusiveNodeSwitch)
        SetupBooleanPreferenceGui(name + "." + PREFER_NODE_SWITCH, preferenceDB, ui.GuiBchPreferNodeSwitch)
        SetupTextPreferenceGui(name + "." + CONFIGURED_NODE, preferenceDB, ui.GuiBchNodeAddr)

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

        ui.GuiSettingsAccountChoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long)
            {
                val item = ui.GuiSettingsAccountChoice.selectedItem
                if (item == null) return
                val accountName = item.toString()

                val coin = accounts[accountName]
                if (coin == null) return onNothingSelected(parent)

                ui.GuiPINInvisibility.setEnabled(true)
                ui.GuiPINInvisibility.setChecked(coin.flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN > 0UL)
            }

            override fun onNothingSelected(parent: AdapterView<out Adapter>?)
            {
                ui.GuiPINInvisibility.setChecked(false)
                ui.GuiPINInvisibility.setEnabled(false)
            }
        }

        ui.GuiPINInvisibility.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            val item = ui.GuiSettingsAccountChoice.selectedItem
            if (item != null)
            {
                val accountName = item.toString()

                val coin = accounts[accountName]

                if (coin != null)
                {
                    if (isChecked)
                        coin.flags = coin.flags or ACCOUNT_FLAG_HIDE_UNTIL_PIN
                    else
                        coin.flags = coin.flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN.inv()
                    launch {  // Can't be in UI thread
                        coin.saveAccountFlags()
                    }
                }
            }
        })

        setupAccountSelection()
    }

    fun setupAccountSelection()
    {
        dbgAssertGuiThread()
        // Set up the crypto spinners to contain all the cryptos this wallet supports
        val coinSpinData = app!!.visibleAccountNames()

        val coinAa = ArrayAdapter(this, android.R.layout.simple_spinner_item, coinSpinData)
        ui.GuiSettingsAccountChoice?.setAdapter(coinAa)
    }

    override fun onStop()
    {
        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)

        var nodeAddr: String? = null

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

    @Suppress("UNUSED_PARAMETER")
    fun onClearIdentityDomains(v: View?)
    {
        launch {
            val wallet: CommonWallet = try
            {
                (application as WallyApp).primaryAccount.wallet
            } catch (e: PrimaryWalletInvalidException)
            {
                //displayError(R.string.pleaseWait)
                return@launch
            }
            wallet.identityDomain.clear()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onYes(v: View?)
    {
        ui.ConfirmationConstraint.visibility = GONE
        ui.confirmationOps.visibility = VISIBLE

        val a = askingAbout
        if (a == null) return

        val item = ui.GuiSettingsAccountChoice.selectedItem
        if (item == null) return
        val accountName = item.toString()

        askingAbout = null
        when (a)
        {
            ConfirmationFor.RediscoverBlockchain ->
            {
                val coin = accounts[accountName]
                if (coin == null) return
                launch {
                    val bc = coin.wallet.blockchain
                    // If you reset the wallet first, it'll start rediscovering the existing blockchain before it gets reset.
                    bc.rediscover()
                    for (c in accounts)  // Rediscover tx for EVERY wallet using this blockchain
                    {
                        if (c.value.wallet.blockchain == bc)
                            c.value.wallet.rediscover(true, true)
                    }

                }
                displayNotice(i18n(R.string.rediscoverNotice))
            }
            ConfirmationFor.Rediscover ->
            {
                val coin = accounts[accountName]
                if (coin == null) return
                launch {
                    coin.wallet.rediscover(true, false)
                    displayNotice(i18n(R.string.rediscoverNotice))
                }
            }
            ConfirmationFor.Reassess ->
            {
                val coin = accounts[accountName]
                if (coin == null) return
                launch {
                    try
                    {
                        // TODO while we don't have Rostrum (electrum) we can't reassess, so just forget them under the assumption that they will be confirmed and accounted for, or are bad.
                        // coin.wallet.reassessUnconfirmedTx()
                        coin.wallet.cleanUnconfirmed()
                        displayNotice(i18n(R.string.unconfAssessmentNotice))
                    } catch (e: Exception)
                    {
                        displayNotice(e.message ?: e.toString())
                    }
                }
            }
            ConfirmationFor.Delete ->
            {
                val account = accounts[accountName]
                if (account == null) return
                account.detachUI()
                accounts.remove(accountName)  // remove this coin from any global access before we delete it

                launch { // cannot access db in UI thread
                    app?.saveActiveAccountList()
                    account.delete()
                }
                displayNotice(i18n(R.string.accountDeleteNotice))
                setupAccountSelection()  // reload this spinner since an account was removed
            }
            ConfirmationFor.RecoveryPhrase ->
            {
                ui.buttonNo.visibility = VISIBLE
                ui.buttonYes.text = i18n(R.string.yes)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onNo(v: View?)
    {
        askingAbout = null
        ui.ConfirmationConstraint.visibility = GONE
        ui.confirmationOps.visibility = VISIBLE
    }

    fun showConfirmation()
    {
        ui.confirmationOps.visibility = GONE
        ui.ConfirmationConstraint.visibility = VISIBLE
    }

    @Suppress("UNUSED_PARAMETER")
    fun onConfirmationOps(v: View?): Boolean
    {
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRediscoverBlockchain(v: View?): Boolean
    {
        // Strangely, if the contraint layout is touched, it calls this function
        if (v != ui.GuiRediscoverBlockchainButton) return false

        askingAbout = ConfirmationFor.RediscoverBlockchain
        ui.GuiConfirmationText.text = i18n(R.string.rediscoverBlockchainConfirmation)
        showConfirmation()
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRediscoverWallet(v: View?): Boolean
    {
        // Strangely, if the contraint layout is touched, it calls this function
        if (v != ui.GuiRediscoverButton) return false
        askingAbout = ConfirmationFor.Rediscover
        ui.GuiConfirmationText.text = i18n(R.string.rediscoverConfirmation)
        showConfirmation()
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun onViewRecoveryPhrase(v: View?)
    {
        askingAbout = ConfirmationFor.RecoveryPhrase

        val item = ui.GuiSettingsAccountChoice.selectedItem
        if (item == null) return
        val accountName = item.toString()

        val coin = accounts[accountName]
        if (coin == null) return
        ui.GuiConfirmationText.text = i18n(R.string.recoveryPhrase) + "\n\n" + coin.wallet.secretWords
        showConfirmation()
        ui.buttonNo.visibility = GONE
        ui.buttonYes.text = i18n(R.string.done)
    }

    @Suppress("UNUSED_PARAMETER")
    /** Reassess unconfirmed transactions */
    public fun onAssessUnconfirmedButton(v: View)
    {
        askingAbout = ConfirmationFor.Reassess
        ui.GuiConfirmationText.text = i18n(R.string.reassessConfirmation)
        showConfirmation()
    }

    @Suppress("UNUSED_PARAMETER")
    /** Delete a wallet account */
    public fun onDeleteAccountButton(v: View)
    {
        askingAbout = ConfirmationFor.Delete

        val item = ui.GuiSettingsAccountChoice.selectedItem
        if (item == null) return
        val accountName = item.toString()

        val coin: Account? = accounts[accountName]
        if (coin == null) return

        ui.GuiConfirmationText.text = i18n(R.string.deleteConfirmation) % mapOf("accountName" to coin.name, "blockchain" to coin.currencyCode)
        showConfirmation()
    }

}
