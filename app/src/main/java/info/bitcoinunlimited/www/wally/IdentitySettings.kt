package info.bitcoinunlimited.www.wally

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
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
import info.bitcoinunlimited.www.wally.databinding.ActivityIdentityYourdataBinding
import info.bitcoinunlimited.www.wally.databinding.InfoeditrowBinding
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.IdentityActivity")

class TextDataPairBinder(val ui: InfoeditrowBinding, val ii: IdentityInfo): GuiListItemBinder<Pair<String, String>>(ui.root)
{
    // Fill the view with this data
    override fun populate()
    {
        data?.let { data ->
            ui.fieldName.text = data.first
            ui.fieldValue.text.clear()
            ui.fieldValue.text.append(ii.getString(data.second,""))
        }
        ui.fieldValue.addTextChangedListener(object: TextWatcher
        {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int)
            {
                data?.let {
                    ii.putString(it.second, ui.fieldValue.text.toString())
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
    private lateinit var ui: ActivityIdentityYourdataBinding
    private lateinit var adapter: GuiList<Pair<String, String>, TextDataPairBinder>
    private lateinit var wallet: Bip44Wallet

    override var navActivityId = R.id.navigation_identity
    var identityInfo:IdentityInfo? = null
    var fields = listOf<Pair<String, String>>()

    init
    {
        val account = wallyApp!!.primaryAccount
        if ((account == null) || (!account.visible))
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
        ui = ActivityIdentityYourdataBinding.inflate(layoutInflater)
        setContentView(ui.root)

        identityInfo?.let { ii ->
            adapter = GuiList(ui.infoValueRecycler, fields, this, {
                val ui = InfoeditrowBinding.inflate(LayoutInflater.from(it.context), it, false)
                TextDataPairBinder(ui, ii)
            })
        }

        ui.infoValueRecycler.layoutManager = LinearLayoutManager(this)
    }

    override fun onStart()
    {
        super.onStart()

        val intent = getIntent()
        val title = intent.getStringExtra("title")
        if (title != null)
        {
            setTitle(title)
        }
    }
}