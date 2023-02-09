package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.Hash256
import info.bitcoinunlimited.www.wally.databinding.ActivityShoppingBinding
import info.bitcoinunlimited.www.wally.databinding.ShoppingListItemBinding

import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.shoppingActivity")

class ShoppingDestination(var buttonText: String = "", var explain: String = "", var url: String = "", var androidPackage: String = "", var icon: Int = 0)
{
    fun launch(view: View)
    {
        val activity: Activity = getActivity(view) ?: return
        val pm: PackageManager = activity.packageManager

        if (androidPackage != "")
        {
            val launchIntent: Intent? = pm.getLaunchIntentForPackage(androidPackage)
            launchIntent?.let {
                activity.startActivity(it)
                return
            }
        }

        if (url != "")
        {
            if (!url.startsWith("http")) url = "https://" + url
            val webIntent: Intent = Uri.parse(url).let { webpage -> Intent(Intent.ACTION_VIEW, webpage) }
            activity.startActivity(webIntent)
            return
        }
    }
}

val initialShopping: ArrayList<ShoppingDestination> = arrayListOf(
  /*
  ShoppingDestination(i18n(R.string.GiftCardButton), i18n(R.string.ExplainGiftCards), i18n(R.string.GiftCardUrl), i18n(R.string.GiftCardAppPackage), R.mipmap.ic_egifter),
  ShoppingDestination(i18n(R.string.RestaurantButton), i18n(R.string.ExplainRestaurant), i18n(R.string.RestaurantUrl), i18n(R.string.RestaurantAppPackage), R.mipmap.ic_menufy),
  ShoppingDestination(i18n(R.string.StoreMapButton), i18n(R.string.ExplainStoreMap), i18n(R.string.StoreMapUrl), i18n(R.string.StoreMapAppPackage))
   */
  ShoppingDestination(i18n(R.string.NFTs), i18n(R.string.ExplainNFTs), i18n(R.string.NftUrl), "", R.drawable.ic_niftyart_logo_plain),
  ShoppingDestination(i18n(R.string.CexButton), i18n(R.string.ExplainTxBit), "https://txbit.io/Trade/NEXA/USDT", "", R.drawable.txbit_io),
)


class ShoppingListBinder(val ui: ShoppingListItemBinding): GuiListItemBinder<ShoppingDestination>(ui.root)
{
    override fun populate()
    {
        val d = data
        if (d != null)
        {
            ui.GuiShoppingButton.setText(d.buttonText)
            if (d.icon != 0) ui.GuiShoppingIcon.setImageResource(d.icon)
            ui.GuiShoppingExplain.text = d.explain
        }
    }

    override fun onClick(v: View)
    {
        val i = data
        if (i != null)
        {
            LogIt.info("clicked on " + i.buttonText)
            try
            {
                i.launch(ui.root)
            }
            catch (e: ActivityNotFoundException)
            {
                //activity.displayError(R.string.BadLink)
            }
        }
    }

}

private class ShoppingRecyclerAdapter(private val activity: ShoppingActivity, private val domains: MutableList<ShoppingDestination>) : RecyclerView.Adapter<ShoppingRecyclerAdapter.DomainHolder>()
{
    private lateinit var ui:ShoppingListItemBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShoppingRecyclerAdapter.DomainHolder
    {
        ui = ShoppingListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DomainHolder(activity, ui)
    }

    override fun getItemCount(): Int = domains.size


    override fun onBindViewHolder(holder: ShoppingRecyclerAdapter.DomainHolder, position: Int)
    {
        val item = domains[position]
        holder.bind(item, position)
    }

    class DomainHolder(private val activity: ShoppingActivity, private val ui: ShoppingListItemBinding) : RecyclerView.ViewHolder(ui.root), View.OnClickListener
    {
        private var item: ShoppingDestination? = null
        private val view = ui.root
        var idx = 0
        var txid: Hash256? = null

        init
        {
            ui.root.setOnClickListener(this)
            ui.GuiShoppingButton.setOnClickListener(this)
        }

        override fun onClick(v: View)
        {
            val i = item
            if (i != null)
            {
                LogIt.info("clicked on " + i.buttonText)
                try
                {
                    i.launch(ui.root)
                } catch (e: ActivityNotFoundException)
                {
                    activity.displayError(R.string.BadLink)
                }
            }
        }

        fun bind(obj: ShoppingDestination, pos: Int)
        {
            item = obj
            ui.GuiShoppingButton.setText(obj.buttonText)

            if (obj.icon != 0) ui.GuiShoppingIcon.setImageResource(obj.icon)
            ui.GuiShoppingExplain.text = obj.explain

            // Alternate colors for each row in the list
            //val Acol:Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
            //val Bcol:Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()
            val Acol = 0xFFF0EEFF.toInt()
            val Bcol = 0xFFE0D0EF.toInt()

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


class ShoppingActivity : CommonNavActivity()
{
    private lateinit var ui: ActivityShoppingBinding
    override var navActivityId = R.id.navigation_shopping
    lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: GuiList<ShoppingDestination, ShoppingListBinder>
    // private lateinit var adapter: ShoppingRecyclerAdapter

    var shopping = mutableListOf<ShoppingDestination>()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityShoppingBinding.inflate(layoutInflater)
        setContentView(ui.root)

        for (s in initialShopping)
            shopping.add(s)

        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        loadShoppingFromPreferences(prefs, shopping)

        linearLayoutManager = LinearLayoutManager(this)
        ui.GuiShoppingList.layoutManager = linearLayoutManager
    }

    override fun onResume()
    {
        super.onResume()

        shopping.clear()
        for (s in initialShopping)
            shopping.add(s)

        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        loadShoppingFromPreferences(prefs, shopping)
        //adapter = ShoppingRecyclerAdapter(this, shopping)
        adapter = GuiList(ui.GuiShoppingList, shopping, this, {
            val ui = ShoppingListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
            ShoppingListBinder(ui)
        })
        adapter.rowBackgroundColors = WallyRowColors

    }

    /** Inflate the options menu */
    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.shopping_options, menu);

        val item2 = menu.findItem(R.id.settings)
        item2.intent = Intent(this, ShoppingSettingsActivity::class.java)
        return super.onCreateOptionsMenu(menu)
    }

}