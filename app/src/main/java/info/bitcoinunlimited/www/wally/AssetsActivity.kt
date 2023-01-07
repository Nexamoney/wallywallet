// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.Intent
import bitcoinunlimited.libbitcoincash.*
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import bitcoinunlimited.libbitcoincash.CurrencyDecimal
import bitcoinunlimited.libbitcoincash.TransactionHistory
import bitcoinunlimited.libbitcoincash.fiatFormat
import kotlinx.android.synthetic.main.asset_list_item.view.*
import kotlinx.android.synthetic.main.tx_history_list_item.view.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.assets")

class AssetInfo
{

}


class AssetBinder(view: View): GuiListItemBinder<AssetInfo>(view)
{
    // Fill the view with this data
    override fun populate()
    {
        //val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

        val activity = (view.context as AssetsActivity)
        val act = activity.account
        if (act == null) return

        data?.let()
        { data ->
            view.GuiAssetName.text = "TODO"
            //view.GuiValueCrypto.text = act.cryptoFormat.format(act.fromFinestUnit(amt))
        }

        // Alternate colors for each row in the list
        val Acol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
        val Bcol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()
        if ((pos and 1) == 0)
            view.background = ColorDrawable(Acol)
        else
            view.background = ColorDrawable(Bcol)
    }
}



class AssetsActivity : CommonNavActivity()
{
    override var navActivityId = R.id.navigation_assets
    var account: Account? = null
    var accountIdx = -1
    private lateinit var adapter: GuiList<AssetInfo, AssetBinder>

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assets)

        laterUI {
            wallyApp?.let { app ->
                if (app !is WallyApp)
                {
                    finish()
                }
                else
                {
                    val acc = wallyApp?.primaryAccount
                    updateAccount(acc)

                }
            }
            account?.let { setTitle(i18n(R.string.title_activity_assets) + " : " + it.name) }
        }
    }

    fun updateAccount(acc: Account?)
    {
        account = acc
        var titl:String = i18n(R.string.title_activity_assets)
        if (acc != null) titl = titl + " : " + acc.name
        setTitle(titl)

        if (acc != null)
        {
            val wallet = acc.wallet
            val assetList: List<AssetInfo> = listOf() ///wallet.txHistory.values.sortedBy { it.date }.reversed()
            adapter = GuiList(assetList, this, {
                val view = layoutInflater.inflate(R.layout.asset_list_item, it, false)
                AssetBinder(view)
            })
        }
    }

    override fun onTitleBarTouched()
    {
        LogIt.info("title button pressed")
        wallyApp?.let {
            if (it.accounts.size == 0)
            {
                displayError(R.string.NoAccounts, null, { })
                return
            }
            accountIdx+=1
            if (accountIdx >= it.accounts.size) accountIdx = 0
            val al = it.accounts.values.toList()
            if (al[accountIdx] == account) accountIdx++  // Avoid a repeat unless this is the only account
            if (accountIdx >= it.accounts.size) accountIdx = 0
            updateAccount(al[accountIdx])
        }
    }

}
