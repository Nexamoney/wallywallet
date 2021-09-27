package info.bitcoinunlimited.www.wally

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.*
import kotlinx.android.synthetic.main.activity_shopping_settings.*
import kotlinx.android.synthetic.main.shopping_settings_list_item.view.*
import java.lang.Exception
import java.net.URI
import java.util.logging.Logger

private val LogIt = Logger.getLogger("bu.shoppingSettingsActivity")

val SHOPPING_DEST_ITEMS: String = "wally."

fun loadShoppingFromPreferences(prefs: SharedPreferences, shopping: MutableList<ShoppingDestination>)
{
    val itemsS = prefs.getString(SHOPPING_DEST_ITEMS, null)
    if (itemsS != null)
    {
        val items = itemsS.split("\n")
        for (item in items)
        {
            val butUrl = item.split("\t")
            if (butUrl.size >= 2)
            {
                var url = butUrl[1]
                if (!url.startsWith("http")) url = "http://" + url
                try
                {
                    val uri = URI(url)
                    shopping.add(ShoppingDestination(butUrl[0], i18n(R.string.OpenWebsite) % mapOf("siteName" to uri.host), url))
                } catch (e: Exception)
                {
                    LogIt.info("Bad URI read from preferences")
                }
            }
        }
    }
}

fun saveShoppingFromPreferences(prefs: SharedPreferences.Editor, shopping: MutableList<ShoppingDestination>)
{
    val s = StringBuilder()
    var first = true
    for (item in shopping)
    {
        if ((item.buttonText.trim() == "") && (item.url.trim() == "")) continue  // skip empties
        if (first) first = false
        else
            s.append("\n")
        s.append(item.buttonText.trim())
        s.append("\t")
        s.append(item.url.trim())
    }
    prefs.putString(SHOPPING_DEST_ITEMS, s.toString())
    prefs.commit()
}


private class ShoppingEditRecyclerAdapter(private val activity: ShoppingSettingsActivity, private val domains: Array<ShoppingDestination>) : RecyclerView.Adapter<ShoppingEditRecyclerAdapter.DomainHolder>()
{

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShoppingEditRecyclerAdapter.DomainHolder
    {
        val inflatedView = parent.inflate(R.layout.shopping_settings_list_item, false)
        return DomainHolder(activity, inflatedView)
    }

    override fun getItemCount(): Int = domains.size


    override fun onBindViewHolder(holder: ShoppingEditRecyclerAdapter.DomainHolder, position: Int)
    {
        val item = domains[position]
        holder.bind(item, position)
    }

    class DomainHolder(private val activity: ShoppingSettingsActivity, private val view: View) : RecyclerView.ViewHolder(view), View.OnClickListener
    {
        var idx = 0
        var txid: Hash256? = null
        var item: ShoppingDestination? = null

        init
        {
            view.setOnClickListener(this)
        }

        override fun onClick(v: View)
        {
        }

        fun bind(obj: ShoppingDestination, pos: Int)
        {
            idx = pos
            item = obj
            view.GuiButtonText.setText(obj.buttonText)
            view.GuiButtonWebLink.setText(obj.url)
            view.GuiButtonText.addTextChangedListener(textChanged {
                item?.let { it.buttonText = view.GuiButtonText.text.toString() }
            })

            view.GuiButtonWebLink.addTextChangedListener(textChanged {
                item?.let { it.url = view.GuiButtonWebLink.text.toString() }
            })

            // Alternate colors for each row in the list
            val Acol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
            val Bcol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()
            //val Acol = 0xFFEEFFEE.toInt()
            //val Bcol = 0xFFBBDDBB.toInt()

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


class ShoppingSettingsActivity : CommonNavActivity()
{
    override var navActivityId = R.id.navigation_shopping

    lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: ShoppingEditRecyclerAdapter

    var shopping = mutableListOf<ShoppingDestination>()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        setTitle(R.string.title_activity_shopping_settings)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_settings)

        linearLayoutManager = LinearLayoutManager(this)
        GuiShoppingEditList.layoutManager = linearLayoutManager

        /*  Not needed if manifest is correct
        val titlebar: ActionBar? = supportActionBar
        titlebar?.setDisplayHomeAsUpEnabled(true)
        titlebar?.setDisplayShowHomeEnabled(true)
        */

        GuiShoppingEditList.addOnScrollListener(object : RecyclerView.OnScrollListener()
        {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int)
            {
                super.onScrollStateChanged(recyclerView, newState)
            }
        }
        )

        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        loadShoppingFromPreferences(prefs, shopping)
        if ((shopping.size == 0) || (shopping[shopping.size - 1].buttonText != ""))
            shopping.add(ShoppingDestination("", ""))  // Add a blank if there isn't one so a new button can be added by the user

        adapter = ShoppingEditRecyclerAdapter(this, shopping.toTypedArray())
        GuiShoppingEditList.adapter = adapter
    }

    override fun onPause()
    {
        super.onPause()
        val prefs: SharedPreferences = getSharedPreferences(getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        saveShoppingFromPreferences(prefs.edit(), shopping)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        /*
        if (item.getItemId() === android.R.id.home)
        {
            finish()
        }
         */
        return super.onOptionsItemSelected(item)
    }
}