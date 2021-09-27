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
import kotlinx.android.synthetic.main.activity_identity.*
import kotlinx.android.synthetic.main.identity_list_item.view.*
import java.lang.Exception
import java.net.URLEncoder
import java.util.logging.Logger

private val LogIt = Logger.getLogger("bu.IdentityActivity")

open class IdentityException(msg: String, shortMsg: String? = null, severity: ErrorSeverity = ErrorSeverity.Abnormal) : BUException(msg, shortMsg, severity)

fun ViewGroup.inflate(@LayoutRes layoutRes: Int, attachToRoot: Boolean = false): View
{
    return LayoutInflater.from(context).inflate(layoutRes, this, attachToRoot)
}

fun BCHidentityUpdateIntentFromPerms(intent: Intent, perms: MutableMap<String, Boolean>)
{
    for (k in BCHidentityParams)  // Update new perms
    {
        intent.putExtra(k + "P", perms[k])
    }
}

fun BCHidentityUpdateIntentFromReqs(intent: Intent, reqs: MutableMap<String, String>)
{
    for (k in BCHidentityParams)  // Update new perms
    {
        intent.putExtra(k, reqs[k])
    }
}


class RecyclerAdapter(private val domains: ArrayList<IdentityDomain>) : RecyclerView.Adapter<RecyclerAdapter.IdentityDomainHolder>()
{

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerAdapter.IdentityDomainHolder
    {
        val inflatedView = parent.inflate(R.layout.identity_list_item, false)
        return IdentityDomainHolder(inflatedView)
    }

    override fun getItemCount(): Int = domains.size


    override fun onBindViewHolder(holder: RecyclerAdapter.IdentityDomainHolder, position: Int)
    {
        val item = domains[position]
        holder.bind(item, position)
    }


    class IdentityDomainHolder(v: View) : RecyclerView.ViewHolder(v), View.OnClickListener
    {
        private var view: View = v
        private var id: IdentityDomain? = null

        init
        {
            v.setOnClickListener(this)
        }

        override fun onClick(v: View)
        {
            var reqs = mutableMapOf<String, String>()
            id?.getReqs(reqs)
            var perms = mutableMapOf<String, Boolean>()
            id?.getPerms(perms)

            var intent = Intent(v.context, DomainIdentitySettings::class.java)
            BCHidentityUpdateIntentFromPerms(intent, perms)
            BCHidentityUpdateIntentFromReqs(intent, reqs)
            intent.putExtra("domainName", this.id?.domain)
            (v.context as Activity).startActivityForResult(intent, IDENTITY_SETTINGS_RESULT)
        }

        fun bind(obj: IdentityDomain, pos: Int)
        {
            this.id = obj
            view.domainNameText.text = obj.domain

            // Alternate colors for each row in the list
            //val Acol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
            //val Bcol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()
            val Acol = 0xFFEEFFEE.toInt()
            val Bcol = 0xFFBBDDBB.toInt()

            if ((pos and 1) == 0)
            {
                view.background = ColorDrawable(Acol)
            }
            else
            {
                view.background = ColorDrawable(Bcol)
            }

        }
    }

}

class IdentityActivity : CommonNavActivity()
{
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: RecyclerAdapter

    override var navActivityId = R.id.navigation_identity

    var actUnlockCb = { populate() }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        navActivityId = R.id.navigation_identity

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identity)

        linearLayoutManager = LinearLayoutManager(this)
        identityList.layoutManager = linearLayoutManager
        // adapter.notifyItemInserted( index of item)

        val app = (getApplication() as WallyApp)
        app.interestedInAccountUnlock.add(actUnlockCb)
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
        laterUI {
            try
            {
                //val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
                val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                val account = (application as WallyApp).primaryAccount
                if (!account.visible)
                {
                    throw PrimaryWalletInvalidException()
                }
                val wallet = account.wallet
                val identities: ArrayList<IdentityDomain> = ArrayList(wallet.allIdentityDomains())
                LogIt.info("identity domain count:" + identities.size.toString())
                LogIt.info(wallet.allIdentityDomains().map { it.domain }.toString())
                adapter = RecyclerAdapter(identities)
                identityList.adapter = adapter

                val name: String? = prefs.getString("hdl", null)
                val email: String? = prefs.getString("email", null)
                val socialmedia: String? = prefs.getString("sm", null)

                // Show these common fields for the "common identity" on the front screen
                if (name != null) aliasInfo.text = name
                if (email != null) emailInfo.text = email
                if (socialmedia != null)
                {
                    val t = socialmedia.split(" ", ",").filter({ it -> it != "" })
                    socialMediaInfo.text = t.joinToString("\n")
                }

                // Show a share identity link on the front screen
                val dest = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
                val destStr = dest.address.toString()
                commonIdentityAddress.text = destStr

                LogIt.info("name: " + name)

                var uri = "bchidentity://p2p?op=share&addr=" + destStr;
                if (name != null && name != "") uri = uri + "&name=" + URLEncoder.encode(name, "utf-8")
                if (email != null && email != "") uri = uri + "&em=" + URLEncoder.encode(email, "utf-8")
                if (socialmedia != null && socialmedia != "") uri = uri + "&sm=" + URLEncoder.encode(socialmedia, "utf-8")
                LogIt.info("encoded URI: " + uri)

                val sz = min(commonIdentityQRCode.getWidth().toLong(), commonIdentityQRCode.getHeight().toLong())
                val qr = textToQREncode(uri, sz.toInt())
                commonIdentityQRCode.setImageBitmap(qr)
            } catch (e: PrimaryWalletInvalidException)
            {
                commonIdentityAddress.text = i18n(R.string.NoAccounts)
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

        val item4 = menu.findItem(R.id.help)
        item4.intent = Intent(Intent.ACTION_VIEW)
        item4.intent.setData(Uri.parse("http://www.bitcoinunlimited.net/wally/faq"))

        return super.onCreateOptionsMenu(menu)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onCommonIdentityAddrTextClicked(v: View)
    {
        copyTextToClipboard(commonIdentityAddress)
    }
};
