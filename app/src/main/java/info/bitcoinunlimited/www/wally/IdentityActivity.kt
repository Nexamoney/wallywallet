// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.net.Uri
import android.view.*
import androidx.annotation.LayoutRes
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.databinding.ActivityIdentityBinding
import info.bitcoinunlimited.www.wally.databinding.AlertListItemBinding
import info.bitcoinunlimited.www.wally.databinding.IdentityListItemBinding
import java.lang.Exception
import java.net.URLEncoder
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.IdentityActivity")

open class IdentityException(msg: String, shortMsg: String? = null, severity: ErrorSeverity = ErrorSeverity.Abnormal) : BUException(msg, shortMsg, severity)

fun ViewGroup.inflate(@LayoutRes layoutRes: Int, attachToRoot: Boolean = false): View
{
    return LayoutInflater.from(context).inflate(layoutRes, this, attachToRoot)
}

fun nexidUpdateIntentFromPerms(intent: Intent, perms: MutableMap<String, Boolean>)
{
    for (k in nexidParams)  // Update new perms
    {
        intent.putExtra(k + "P", perms[k])
    }
}

fun nexidUpdateIntentFromReqs(intent: Intent, reqs: MutableMap<String, String>)
{
    for (k in nexidParams)  // Update new perms
    {
        intent.putExtra(k, reqs[k])
    }
}


class IdentityDomainBinder(val ui: IdentityListItemBinding): GuiListItemBinder<IdentityDomain>(ui.root)
{
    override fun populate()
    {
        ui.domainNameText.text = data?.domain
    }

    override fun onClick(v: View)
    {
        var reqs = mutableMapOf<String, String>()
        data?.getReqs(reqs)
        var perms = mutableMapOf<String, Boolean>()
        data?.getPerms(perms)

        var intent = Intent(v.context, DomainIdentitySettings::class.java)
        nexidUpdateIntentFromPerms(intent, perms)
        nexidUpdateIntentFromReqs(intent, reqs)
        intent.putExtra("domainName", this.data?.domain)
        intent.putExtra("mode", "edit")
        (v.context as Activity).startActivityForResult(intent, IDENTITY_SETTINGS_RESULT)
    }
}


class IdentityActivity : CommonNavActivity()
{
    private lateinit var ui: ActivityIdentityBinding
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: GuiList<IdentityDomain, IdentityDomainBinder>

    var copylabel = ""

    override var navActivityId = R.id.navigation_identity

    var actUnlockCb = { populate() }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        navActivityId = R.id.navigation_identity
        super.onCreate(savedInstanceState)
        ui = ActivityIdentityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        linearLayoutManager = LinearLayoutManager(this)
        ui.identityList.layoutManager = linearLayoutManager

        val app = (getApplication() as WallyApp)
        app.interestedInAccountUnlock.add(actUnlockCb)

        laterUI {
            val acc: Account = wallyApp?.primaryAccount ?: throw PrimaryWalletInvalidException()
            setTitle(i18n(R.string.title_activity_identity) + ": " + acc.name)
        }
    }

    override fun onDestroy()
    {
        val app = (getApplication() as WallyApp)
        app.interestedInAccountUnlock.remove(actUnlockCb)
        super.onDestroy()
    }

    /** Fill all the fields with data */
    fun populate()
    {
        laterUI()
        {
            try
            {
                val account = (application as WallyApp).primaryAccount
                if ((account == null) || (!account.visible))
                {
                    throw PrimaryWalletInvalidException()
                }
                val wallet = account.wallet
                copylabel = wallet.name
                val identities: ArrayList<IdentityDomain> = ArrayList(wallet.allIdentityDomains())
                LogIt.info("identity domain count:" + identities.size.toString())
                LogIt.info(wallet.allIdentityDomains().map { it.domain }.toString())
                //adapter = RecyclerAdapter(identities)
                //ui.identityList.adapter = adapter
                adapter = GuiList(ui.identityList, identities, this, {
                    val ui = IdentityListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
                    IdentityDomainBinder(ui)
                })
                adapter.rowBackgroundColors = WallyRowColors

                val commonIdDest = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
                val commonIdAddress = commonIdDest.address ?: throw PrimaryWalletInvalidException()
                val identityInfo: IdentityInfo = wallet.lookupIdentityInfo(commonIdAddress) ?: {
                    val ii = IdentityInfo()
                    ii.identity = commonIdAddress
                    wallet.upsertIdentityInfo(ii)
                    ii
                }()

                val hdl: String? = identityInfo.hdl
                val email: String? = identityInfo.email
                val socialmedia: String? = identityInfo.sm

                // Show these common fields for the "common identity" on the front screen
                if (hdl != null) ui.aliasInfo.text = hdl
                if (email != null) ui.emailInfo.text = email
                if (socialmedia != null)
                {
                    val t = socialmedia.split(" ", ",").filter({ it -> it != "" })
                    ui.socialMediaInfo.text = t.joinToString("\n")
                }

                // Show a share identity link on the front screen
                val dest = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
                val destStr = dest.address.toString()
                ui.commonIdentityAddress.text = destStr

                var uri = "nexid://p2p?op=share&addr=" + destStr;
                if (hdl != null && hdl != "") uri = uri + "&hdl=" + URLEncoder.encode(hdl, "utf-8")
                if (email != null && email != "") uri = uri + "&email=" + URLEncoder.encode(email, "utf-8")
                if (socialmedia != null && socialmedia != "") uri = uri + "&sm=" + URLEncoder.encode(socialmedia, "utf-8")
                LogIt.info("encoded URI: " + uri)

                val sz = min(ui.commonIdentityQRCode.getWidth().toLong(), ui.commonIdentityQRCode.getHeight().toLong())
                val qr = textToQREncode(uri, sz.toInt())
                ui.commonIdentityQRCode.setImageBitmap(qr)
            }
            catch (e: PrimaryWalletInvalidException)
            {
                ui.commonIdentityAddress.text = i18n(R.string.NoAccounts)
            }
        }
    }

    override fun onStart()
    {
        super.onStart()
    }

    override fun onResume()
    {
        super.onResume()
        populate()
        // Process the intent that caused this activity to resume
        if (intent.scheme != null)  // its null if normal app startup
        {
            handleNewIntent(intent)
        }
    }

    //? A new intent to pay someone could come from either startup (onResume) or just on it own (onNewIntent) so create a single function to deal with both
    fun handleNewIntent(receivedIntent: Intent)
    {
        val iuri = receivedIntent.toUri(0).toUrl()  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME
        LogIt.info("Identity new Intent: " + iuri)
        try
        {
            if (receivedIntent.scheme == IDENTITY_URI_SCHEME)
            {
                val host = iuri.getHost()
                val path = iuri.getPath()
                LogIt.info("Identity intent host=${host} path=${path}")
            }
            else  // This should never happen because the AndroidManifest.xml Intent filter should match the URIs that we handle
            {
                displayError("bad link " + receivedIntent.scheme)
            }
        } catch (e: Exception)
        {
            displayException(e)
        }
    }

    /** this handles the result of the new domain request, since no other child activities are possible */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        LogIt.info(sourceLoc() + " activity completed $requestCode $resultCode")
        super.onActivityResult(requestCode, resultCode, data)
    }

    /** Inflate the options menu */
    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.identity_options, menu);

        val item2 = menu.findItem(R.id.settings)
        LogIt.info(item2.toString())
        item2.intent = Intent(this, IdentitySettings::class.java)

        val item3 = menu.findItem(R.id.unlock)
        item3.intent = Intent(this, UnlockActivity::class.java)

        initializeHelpOption(menu)
        return super.onCreateOptionsMenu(menu)
    }

    fun onCommonIdentityWrapperClicked(@Suppress("UNUSED_PARAMETER") v:View)
    {
        val tent = Intent(this, IdentitySettings::class.java)
        startActivity(tent)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onCommonIdentityAddrTextClicked(v: View)
    {
        copyTextToClipboard(ui.commonIdentityAddress, copylabel)
    }
};
