// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.Intent
import bitcoinunlimited.libbitcoincash.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import bitcoinunlimited.libbitcoincash.CurrencyDecimal
import bitcoinunlimited.libbitcoincash.TransactionHistory
import bitcoinunlimited.libbitcoincash.fiatFormat
import info.bitcoinunlimited.www.wally.databinding.ActivityAssetsBinding
import info.bitcoinunlimited.www.wally.databinding.ActivityShoppingBinding
import info.bitcoinunlimited.www.wally.databinding.AssetListItemBinding
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


class AssetBinder(val ui: AssetListItemBinding): GuiListItemBinder<AssetInfo>(ui.root)
{
    // Fill the view with this data
    override fun populate()
    {
        //val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        //val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

        val activity = (view.context as AssetsActivity)
        val act = activity.account
        if (act == null) return

        data?.let()
        { data ->
            ui.GuiAssetName.text = "TODO"
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
    private lateinit var ui: ActivityAssetsBinding
    override var navActivityId = R.id.navigation_assets
    var account: Account? = null
    var accountIdx = -1
    private lateinit var adapter: GuiList<AssetInfo, AssetBinder>

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityAssetsBinding.inflate(layoutInflater)
        setContentView(ui.root)
        ui.root.setBackground(graphShape(arrayOf<Double>(100.0,200.0,100.0,20.0,150.0,200.0,175.0,100.0), 50.0,
          ResourcesCompat.getColor(resources, R.color.WallyRowAbkg1,null).toLong(),
          ResourcesCompat.getColor(resources, R.color.WallyRowAbkg2,null).toLong()))
        enableMenu(this, SHOW_ASSETS_PREF)  // If you ever drop into this activity, show it in the menu

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
            account?.let { setTitle(i18n(R.string.title_activity_assets) + ": " + it.name) }
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
            adapter = GuiList(ui.GuiAssetList, assetList, this, {
                val ui = AssetListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
                AssetBinder(ui)
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
