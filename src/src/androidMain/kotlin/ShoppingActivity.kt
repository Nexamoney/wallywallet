package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import info.bitcoinunlimited.www.wally.databinding.ActivityShoppingBinding
import info.bitcoinunlimited.www.wally.databinding.ShoppingListItemBinding
import org.nexa.libnexakotlin.*



private val LogIt = GetLog("BU.wally.shoppingActivity")


fun ShoppingDestination.launch(view: View)
{
    val activity: Activity = getActivity(view) ?: return
    val pm: PackageManager = activity.packageManager

    try
    {
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
    catch(e: SecurityException)
    {
        (activity as CommonActivity)?.displayError(R.string.NoPermission)
    }
}



class ShoppingListBinder(val ui: ShoppingListItemBinding): GuiListItemBinder<ShoppingDestination>(ui.root)
{
    override fun populate()
    {
        val d = data
        if (d != null)
        {
            ui.GuiShoppingButton.setText(d.buttonText)
            // obsoleted by compose if (d.icon != null) ui.GuiShoppingIcon.setImageResource(d.icon)
            ui.GuiShoppingExplain.text = d.explain
        }

        ui.GuiShoppingButton.setOnClickListener({ onButtonClick(it)})
    }

    @Suppress("UNUSED_PARAMETER")
    fun onButtonClick(v: View)
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

        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), PREF_MODE_PRIVATE)
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

        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), PREF_MODE_PRIVATE)
        loadShoppingFromPreferences(prefs, shopping)
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