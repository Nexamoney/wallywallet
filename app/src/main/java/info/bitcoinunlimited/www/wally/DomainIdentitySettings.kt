// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity;
import bitcoinunlimited.libbitcoincash.IdentityDomain

import kotlinx.android.synthetic.main.activity_domain_identity_settings.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DomainIdentitySettings : CommonActivity()
{

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_domain_identity_settings)
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
    }

    override fun onStop()
    {
        val wallet = (application as WallyApp).primaryWallet
        runBlocking {
            val id:Long = if (uniqueIdentitySwitch.isChecked) IdentityDomain.IDENTITY_BY_HASH else IdentityDomain.COMMON_IDENTITY
            wallet.upsertIdentityDomain(IdentityDomain(domainName.text.toString(), id))
        }
        super.onStop()
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
        finish()
    }
}
