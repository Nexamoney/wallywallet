// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Switch
import bitcoinunlimited.libbitcoincash.nexidParams
import bitcoinunlimited.libbitcoincash.IdentityDomain
import bitcoinunlimited.libbitcoincash.launch
import info.bitcoinunlimited.www.wally.databinding.ActivityDomainIdentitySettingsBinding
import info.bitcoinunlimited.www.wally.databinding.ActivityShoppingBinding
import info.bitcoinunlimited.www.wally.databinding.ShoppingListItemBinding

import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.domainidentitysettings")


@kotlinx.coroutines.ExperimentalCoroutinesApi
class DomainIdentitySettings : CommonNavActivity()
{
    private lateinit var ui: ActivityDomainIdentitySettingsBinding
    var ui4params = arrayOf<Switch>()
    val reqs = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityDomainIdentitySettingsBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui4params =
          arrayOf(ui.provideAttestations, ui.provideAvatar, ui.provideBillingAddress, ui.provideBirthday, ui.provideEmail, ui.provideNameAlias, ui.provideRealName, ui.providePhone, ui.providePostalAddress, ui.provideSocialMedia)
    }

    override fun onStart()
    {
        super.onStart()

        if (devMode) ui.uniqueIdentitySwitch.visibility=View.VISIBLE

        val intent = getIntent()
        val domain = intent.getStringExtra("domainName")
        val title = intent.getStringExtra("title")
        if (title != null)
        {
            setTitle(title)
        }
        val mode = intent.getStringExtra("mode")
        if (mode == "reg")
        {
            ui.AcceptButton.visibility = View.VISIBLE
            ui.RejectButton.visibility = View.VISIBLE
            ui.NextButton.visibility = View.GONE
            ui.RemoveButton.visibility = View.GONE
        }
        else if (mode == "edit")
        {
            ui.AcceptButton.visibility = View.GONE
            ui.RejectButton.visibility = View.GONE
            ui.NextButton.visibility = View.VISIBLE
            ui.RemoveButton.visibility = View.VISIBLE

        }

        ui.domainName.text = domain

        for ((param, ui) in nexidParams zip ui4params)
        {
            var b: Boolean? = null
            if (intent.hasExtra(param + "P")) b = intent.getBooleanExtra(param + "P", false)
            var r: String? = null
            if (intent.hasExtra(param))
            {
                r = intent.getStringExtra(param)
                if (r != null) reqs[param] = r
            }
            setOptionState(ui, b, r)
        }
    }

    override fun onStop()
    {
        /*  If we have to do this here, we need to calc the Perms and Reqs
        val wallet = (application as WallyApp).primaryWallet
        runBlocking {
            val id:Long = if (uniqueIdentitySwitch.isChecked) IdentityDomain.IDENTITY_BY_HASH else IdentityDomain.COMMON_IDENTITY
            wallet.upsertIdentityDomain(IdentityDomain(domainName.text.toString(), id))
        }
         */
        upsertDomainIdentity()
        super.onStop()
    }

    fun removeDomainIdentity()
    {
        try
        {

            val wallet = (application as WallyApp).primaryAccount.wallet
            wallet.removeIdentityDomain(ui.domainName.text.toString())
            ui.domainName.text = ""
            launch { wallet.save() }
        }
        catch(e:PrimaryWalletInvalidException)
        {
            // nothing to change if no primary account
        }
    }

    fun upsertDomainIdentity()
    {
        try
        {
            // No domain to save
            if (ui.domainName.text.toString().length == 0) return

            var changed = false
            val wallet = (application as WallyApp).primaryAccount.wallet
            val id: Long = if (ui.uniqueIdentitySwitch.isChecked) IdentityDomain.IDENTITY_BY_HASH else IdentityDomain.COMMON_IDENTITY
            var idData = wallet.lookupIdentityDomain(ui.domainName.text.toString())

            if (idData == null)
            {
                runBlocking {
                    wallet.upsertIdentityDomain(IdentityDomain(ui.domainName.text.toString(), id))
                    idData = wallet.lookupIdentityDomain(ui.domainName.text.toString())
                    changed = true
                }
            }

            // Update what identity to use if that has changed
            if (idData?.useIdentity != id)
            {
                idData?.useIdentity = id
                changed = true
            }

            val perms = mutableMapOf<String, Boolean>()
            for ((param, ui) in nexidParams zip ui4params)
            {
                perms[param] = ui.isChecked
            }

            changed = (idData?.setPerms(perms) ?: false) or changed
            changed = (idData?.setReqs(reqs) ?: false) or changed

            // Save the wallet if something has changed
            if (changed)
            {
                wallet.identityDomainChanged = true
                launch { wallet.save() } // do this out-of-band so UI response is quicker
            }
        }
        catch(e:PrimaryWalletInvalidException)
        {
            LogIt.info("no primary account")
        }
    }

    fun setOptionState(ui: Switch, value: Boolean?, setting: String?)
    {
        if (setting == null)
        {
            ui.isEnabled = false
            ui.isChecked = value ?: false
        }
        else if (setting == "m")  // mandatory item must be provided, and can't be changed
        {
            ui.isChecked = true
            ui.isEnabled = false
        }
        else if (setting == "r") // recommended item can be changed
        {
            ui.isChecked = value ?: true
            ui.isEnabled = true
        }
        else if (setting == "x") // unneeded item
        {
            ui.isChecked = false
            ui.isEnabled = false
        }
        else // for optional or any unknown setting, don't provide it
        {
            ui.isChecked = value ?: false
            ui.isEnabled = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {

        when (item.getItemId())
        {
            android.R.id.home ->
            {
                onBackPressed()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed()
    {
        upsertDomainIdentity()
        intent.putExtra("repeat", "true") // Tell identityOp not to come back here
        setResult(Activity.RESULT_OK, intent)
        super.onBackPressed()
    }

    fun onDoneButton(@Suppress("UNUSED_PARAMETER") view: View)
    {
        upsertDomainIdentity()
        intent.putExtra("repeat", "true") // Tell identityOp not to come back here
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    fun onAcceptButton(@Suppress("UNUSED_PARAMETER") view: View)
    {
        upsertDomainIdentity()
        intent.putExtra("repeat", "true") // Tell identityOp not to come back here
        intent.putExtra("result", "accept") // Tell identityOp not to come back here
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    fun onRejectButton(@Suppress("UNUSED_PARAMETER") view: View)
    {
        upsertDomainIdentity()
        intent.putExtra("repeat", "true") // Tell identityOp not to come back here
        intent.putExtra("result", "reject") // Tell identityOp not to come back here
        setResult(Activity.RESULT_OK, intent)
        finish()
    }


    fun onRemoveButton(@Suppress("UNUSED_PARAMETER") view: View)
    {
        removeDomainIdentity()
        intent.putExtra("repeat", "true") // Tell identityOp not to come back here
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
