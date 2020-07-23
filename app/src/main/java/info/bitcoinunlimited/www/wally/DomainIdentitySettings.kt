// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Switch
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity;
import bitcoinunlimited.libbitcoincash.BCHidentityParams
import bitcoinunlimited.libbitcoincash.CommonWallet
import bitcoinunlimited.libbitcoincash.IdentityDomain

import kotlinx.android.synthetic.main.activity_domain_identity_settings.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

private val LogIt = Logger.getLogger("bu.domainidentitysettings")


@kotlinx.coroutines.ExperimentalCoroutinesApi
class DomainIdentitySettings : CommonActivity()
{
    var ui4params = arrayOf<Switch>()
    val reqs = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_domain_identity_settings)
        ui4params = arrayOf(provideAttestations, provideAvatar, provideBillingAddress, provideBirthday, provideEmail, provideNameAlias, provideRealName, providePhone, providePostalAddress, provideSocialMedia)
    }

    override fun onStart()
    {
        super.onStart()

        val intent = getIntent()
        val domain = intent.getStringExtra("domainName")
        val title = intent.getStringExtra("title")
        if (title != null)
        {
            setTitle(title)
        }
        domainName.text = domain

        for ((param, ui) in BCHidentityParams zip ui4params)
        {
            var b:Boolean? = null
            if (intent.hasExtra(param + "P")) b = intent.getBooleanExtra(param + "P", false)
            var r:String? = null
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

    fun upsertDomainIdentity()
    {
        var changed = false
        val wallet = (application as WallyApp).primaryAccount.wallet

        val id: Long = if (uniqueIdentitySwitch.isChecked) IdentityDomain.IDENTITY_BY_HASH else IdentityDomain.COMMON_IDENTITY

        var idData = wallet.lookupIdentityDomain(domainName.text.toString())

        if (idData == null)
        {
            runBlocking {
                wallet.upsertIdentityDomain(IdentityDomain(domainName.text.toString(), id))
                idData = wallet.lookupIdentityDomain(domainName.text.toString())
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
        for ((param, ui) in BCHidentityParams zip ui4params)
        {
            perms[param] = ui.isChecked
        }

        changed = (idData?.setPerms(perms) ?: false) or changed
        changed = (idData?.setReqs(reqs) ?: false) or changed

        // Save the wallet if something has changed
        if (changed) GlobalScope.launch { (wallet.save()) }  // do this out-of-band so UI response is quicker
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

    fun onNextButton(view: View)
    {
        upsertDomainIdentity()
        intent.putExtra("repeat", "true") // Tell identityOp not to come back here
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
