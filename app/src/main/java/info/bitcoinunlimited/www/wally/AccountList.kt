package info.bitcoinunlimited.www.wally

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.PathShape
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.Shape
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.nexa.libnexakotlin.*
import info.bitcoinunlimited.www.wally.databinding.AccountListItemBinding
import kotlinx.coroutines.delay
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.accountlist")


fun graphShape(dataPoints: Array<Double>, padding:Double, color: Int, bkgcolor: Int): Drawable
{
    val STD_HEIGHT = 250f
    val STD_WIDTH = 1000f
    val min = dataPoints.min()
    val max = dataPoints.max()
    val delta = padding + (max - min)
    var dp = dataPoints.map { STD_HEIGHT - (STD_HEIGHT*(it - min + padding/2)/delta).toFloat()  }
    if (dp.size==1) dp = listOf(dp[0], dp[0])    // Workaround for just 1 point
    val xspacing = STD_WIDTH/(dp.size-1)

    val bkg = ShapeDrawable(RectShape()).apply {
        paint.color = bkgcolor.toInt()
    }

    val graph: ShapeDrawable = run {
        val p = Path()
        var xPos = 0f
        var lastY = 0.0f
        if (dp.size > 0)
        {
            for (pt in dp)
            {
                p.lineTo(xPos, pt)
                lastY = pt
                xPos += xspacing
            }
            p.lineTo(STD_WIDTH, lastY)
            p.lineTo(STD_WIDTH, STD_HEIGHT)
            p.lineTo(0f, STD_HEIGHT)
            p.lineTo(0f, dp[0])

            ShapeDrawable(PathShape(p, STD_WIDTH, STD_HEIGHT)).apply {
                // If the color isn't set, the shape uses black as the default.
                paint.color = color.toInt()
                // If the bounds aren't set, the shape can't be drawn.
                setBounds(0, 0, STD_WIDTH.toInt(), STD_HEIGHT.toInt())
            }
        }
        else
            ShapeDrawable()
    }
    //return graph
    return LayerDrawable(arrayOf(bkg, graph))
}

class AccountListBinder(val ui: AccountListItemBinding, val guiList: GuiAccountList): GuiListItemBinder<Account>(ui.root)
{
    override fun populate()
    {
        if (devMode) ui.devInfo.visibility = View.VISIBLE
        else ui.devInfo.visibility = View.GONE
        val d = data

        // Clear this out just in case it is recycled as one of the blanks at the bottom
        // or onChange is slow to update
        ui.balanceUnconfirmedValue.text == ""
        ui.devInfo.text = ""
        ui.info.text = ""
        ui.accountIcon.visibility = View.INVISIBLE
        ui.lockIcon.visibility = View.INVISIBLE
        ui.balanceTickerText.text = ""
        ui.balanceValue.text = ""
        ui.balanceUnits.text = ""

        if (d != null)
        {
            if (ui.balanceUnconfirmedValue.text == "") ui.balanceUnconfirmedValue.visibility = View.GONE
            d.setUI(ui, guiList, ui.accountIcon, ui.balanceTickerText, ui.balanceValue, ui.balanceUnconfirmedValue, ui.devInfo)

            ui.balanceUnits.text = d.currencyCode
            if (d.wallet.chainSelector == ChainSelector.NEXA) ui.accountIcon.setImageResource(R.drawable.nexa_icon)
            if (d.wallet.chainSelector == ChainSelector.NEXATESTNET) ui.accountIcon.setImageResource(R.drawable.nexatest_icon)
            if (d.wallet.chainSelector == ChainSelector.NEXAREGTEST) ui.accountIcon.setImageResource(R.drawable.nexareg_icon)
            if (d.wallet.chainSelector == ChainSelector.BCH) ui.accountIcon.setImageResource(R.drawable.bitcoin_cash_token)
            ui.accountIcon.visibility = View.VISIBLE
            d.onChange(true)
        }

        ui.lockIcon.setOnClickListener({toggleLock() })

        ui.GuiAccountDetailsButton.setOnClickListener({ launchAccountDetails() })

        val touch = object: OnSwipeTouchListener(ui.root, false) // false claims we haven't handled it.  This allows the default behavior (e.g. long press selection) to work
        {
            override fun onClick(pos: Pair<Float, Float>): Boolean
            {
                onClick(view)
                return false
            }
        }

        for (view in listOf(ui.accountIcon, ui.balanceTicker, ui.balanceTickerText, ui.balanceValue, ui.balanceUnits, ui.balanceUnconfirmedValue, ui.info, ui.devInfo))
        {
            view.setOnTouchListener(touch)
        }
        super.populate()
    }

    override fun unpopulate()
    {
        // Clear this out just in case it is recycled as one of the blanks at the bottom
        // or onChange is slow to update
        ui.balanceUnconfirmedValue.text == ""
        ui.devInfo.text = ""
        ui.info.text = ""
        ui.accountIcon.visibility = View.INVISIBLE
        ui.lockIcon.visibility = View.INVISIBLE
        ui.balanceTickerText.text = ""
        ui.balanceValue.text = ""
        ui.balanceUnits.text = ""
        val d = data
        if (d != null) d.detachUI()
        super.unpopulate()
    }

    override fun backgroundColor(highlight: Boolean):Long
    {
        if (highlight && data != null) ui.GuiAccountDetailsButton.visibility = View.VISIBLE
        else ui.GuiAccountDetailsButton.visibility = View.GONE
        if (data == null)  // Its blank
            return 0x00000000  // clear
        return -1 // Do not actually recommend a color
    }
    override fun backgroundDrawable(highlight: Boolean): Drawable?
    {
        val d = data
        if (d == null) return null  // dont show a graph for an empty row
        if (highlight) focusAccount(true)
        if (d.chain.chainSelector == ChainSelector.NEXA)  // I only support history for NEXA right now
        {
            val appR = appResources
            val priceData = NexDaily(fiatCurrencyCode)?.map { it.toDouble() }?.toTypedArray()
            if ((appR != null) && (priceData != null))
            {
                var cf = R.color.WallyRowAbkg2
                var cb = R.color.WallyRowAbkg1

                if (highlight)
                {
                    cf = R.color.defaultListHighlight
                    cb = R.color.defaultListHighlight2
                }
                else if ((pos and 1) == 1)
                {
                    cf = R.color.WallyRowBbkg2
                    cb = R.color.WallyRowBbkg1
                }

                return graphShape(
                      priceData, 0.00000050,  // how much value on either side?  with scale 8, last digit is pennies
                      ResourcesCompat.getColor(appR, cf, null), // .toLong(),
                      ResourcesCompat.getColor(appR, cb, null) //.toLong()
                    )
            }
            else  // We don't have the data to draw the background (not loaded yet)
            {
                launch {
                    var count = 0
                    while (count < 3)  // don't want to be leaving spinner code around forever
                    {
                        delay(1000)
                        val pData = NexDaily(fiatCurrencyCode)
                        if (pData != null)  // if we get the data, trigger a redraw
                        {
                            guiList.layout()
                            break
                        }
                        count++
                    }
                }
            }
        }

        // if we can't supply a graph, don't provide any background and it will fall back to color then default
        return null
    }

    fun toggleLock()
    {
        val d = data
        if (d != null)
        {
            if (d.lockable)
            {
                if (d.locked)
                {
                    val intent = Intent(guiList.activity, UnlockActivity::class.java)
                    guiList.activity.startActivity(intent)
                }
                else
                {
                    d.pinEntered = false
                    d.onChange(true)
                    // account should be hidden if locked
                    if (!d.visible) guiList.layout()
                }
            }
        }
    }

    override fun onClick(v: View)
    {
        guiList.onItemClicked(this)
    }

    fun focusAccount(focused: Boolean)
    {
        if (focused) try
        {
            dbgAssertGuiThread()
            val account = data
            if (account == null) return

            // ui.sendAccount.setSelection(account.name)
            guiList.activity.setFocusedAccount(account)

        }
        catch (e: Exception)
        {
            LogIt.warning("Exception clicking on balance: " + e.toString())
            handleThreadException(e)
        }
    }
    fun launchAccountDetails()
    {
        val d = data
        if (d != null)
        {
            val intent = Intent(guiList.activity, AccountDetailsActivity::class.java)
            wallyApp?.let { a ->
                a.accounts[d.name]?.let { a.focusedAccount = it }
            }
            intent.putExtra("WalletName", d.name)
            guiList.activity.startActivity(intent)
        }
    }
}


/* This gives a Map a list interface, sorted by the passed comparator */
class ListifyMap<K, E>(val origmap: Map<K, E>, var filterPredicate: (Map.Entry<K, E>) -> Boolean, var comparator: Comparator<K>): List<E>
{
    protected var map = origmap.filter(filterPredicate)
    protected var order: List<K> = map.keys.sortedWith(comparator)
    override val size: Int
        get() = map.size

    override fun get(index: Int): E
    {
        return map[order[index]]!!
    }

    override fun isEmpty(): Boolean = map.isEmpty()

    fun changed()
    {
        order = map.keys.sortedWith(comparator)
    }

    class LmIterator<K, E>(val lm: ListifyMap<K, E>):Iterator<E>
    {
        var pos = 0
        override fun hasNext(): Boolean
        {
            return (pos < lm.size)
        }

        override fun next(): E
        {
            val ret = lm.get(pos)
            pos += 1
            return ret
        }
    }

    override fun iterator(): Iterator<E>
    {
        return LmIterator(this)
    }

    override fun listIterator(): ListIterator<E>
    {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): ListIterator<E>
    {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<E>
    {
        TODO("Not yet implemented")
    }

    override fun lastIndexOf(element: E): Int
    {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: E): Int
    {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<E>): Boolean
    {
        TODO("Not yet implemented")
    }

    override fun contains(element: E): Boolean
    {
        for (i in map)
        {
            if (i.value == element) return true
        }
        return false
    }

}


open class GuiAccountList(val activity: MainActivity)
{
    private lateinit var linearLayoutManager: LinearLayoutManager
    public lateinit var adapter: GuiList<Account, AccountListBinder>
    var list:List<Account> = listOf()

    open fun onItemClicked(holder: AccountListBinder)
    {
        adapter.highlight(holder.adapterPosition)
        activity.setFocusedAccount(holder.data)
    }

    /** call this if the order, item, or # of elements in the list has changed */
    fun layout()
    {
        laterUI { activity.assignWalletsGuiSlots() }
    }

    fun inflate(context: Context, uiElem: RecyclerView, accountList: List<Account> )
    {
        list = accountList
        linearLayoutManager = LinearLayoutManager(context)
        adapter = GuiList(uiElem, accountList, context, { vg ->
            val ui = AccountListItemBinding.inflate(LayoutInflater.from(vg.context), vg, false)
            AccountListBinder(ui, this)
        })
        adapter.emptyBottomLines = 3
        val wallyAccountRowColors = arrayOf(ContextCompat.getColor(context, R.color.WallyRowAbkg1),ContextCompat.getColor(context, R.color.WallyRowBbkg1) )
        adapter.rowBackgroundColors = wallyAccountRowColors
        uiElem.layoutManager = linearLayoutManager
    }
}