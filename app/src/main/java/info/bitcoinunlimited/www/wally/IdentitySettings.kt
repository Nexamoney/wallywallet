package info.bitcoinunlimited.www.wally

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.view.MenuItemCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import bitcoinunlimited.libbitcoincash.Bip44Wallet
import bitcoinunlimited.libbitcoincash.PayAddress
import bitcoinunlimited.libbitcoincash.IdentityInfo
import bitcoinunlimited.libbitcoincash.Wallet
import kotlinx.android.synthetic.main.activity_identity.*
import kotlinx.android.synthetic.main.activity_identity_yourdata.*
import kotlinx.android.synthetic.main.activity_identity_yourdata.view.*
import kotlinx.android.synthetic.main.infoeditrow.view.*
import java.util.*
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.IdentityActivity")

class TextDataPairBinder(view: View, val ii: IdentityInfo): GuiListItemBinder<Pair<String, String>>(view)
{
    // Fill the view with this data
    override fun populate()
    {
        data?.let { data ->
            view.fieldName.text = data.first
            view.fieldValue.text.clear()
            view.fieldValue.text.append(ii.getString(data.second,""))
        }
        view.fieldValue.addTextChangedListener(object: TextWatcher
        {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int)
            {
                data?.let {
                    ii.putString(it.second, view.fieldValue.text.toString())
                }
            }
        })
    }

    override fun changed()
    {
        //data?.choose(prefs!!, (view.playingRowCheckBox  as SourcesDataCheckBox).getState())
    }

}


class IdentitySettings(var address: PayAddress?=null) : CommonNavActivity()
{
    private lateinit var adapter: GuiList<Pair<String, String>, TextDataPairBinder>
    private lateinit var wallet: Bip44Wallet

    override var navActivityId = R.id.navigation_identity
    var identityInfo:IdentityInfo? = null
    var fields = listOf<Pair<String, String>>()

    init
    {
        val account = wallyApp!!.primaryAccount
        if (!account.visible)
        {
            throw PrimaryWalletInvalidException()
        }
        wallet = account.wallet

        if (address == null)  // get the default identity from the primary wallet
        {
            val dest = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
            address = dest.address
        }

        var ii = wallet.lookupIdentityInfo(address!!)
        if (ii == null)
        {
            ii = IdentityInfo()
            ii.identity = address
            wallet.upsertIdentityInfo(ii)
        }
        if (ii.identity == null)
        {
            ii.identity = address
            wallet.upsertIdentityInfo(ii)
        }

        fields = listOf(
          Pair(i18n(R.string.UsernameOrAliasText), "hdl"),
          Pair(i18n(R.string.EmailText), "email"),
          Pair(i18n(R.string.NameText), "realname"),
          Pair(i18n(R.string.PostalAddressText), "postal"),
          Pair(i18n(R.string.BillingAddressText), "billing"),
          Pair(i18n(R.string.SocialMediaText), "sm"),
          )
        identityInfo = ii
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        navActivityId = R.id.navigation_identity
        super.onCreate(savedInstanceState)
        //val ret = layoutInflater.inflate(R.layout.activity_identity_yourdata, null)
        setContentView(R.layout.activity_identity_yourdata)

        identityInfo?.let { ii ->
            adapter = GuiList(fields, this, {
                val view = layoutInflater.inflate(R.layout.infoeditrow, it, false)
                TextDataPairBinder(view, ii)
            })
        }

        infoValueRecycler.layoutManager = LinearLayoutManager(this)
        infoValueRecycler.adapter = adapter
    }
}