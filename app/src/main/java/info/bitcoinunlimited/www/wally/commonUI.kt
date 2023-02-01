// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.uriToChain
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Exception
import java.util.logging.Logger

const val SPLITBILL_MESSAGE = "info.bitcoinunlimited.www.wally.splitbill"
const val TRICKLEPAY_MESSAGE = "info.bitcoinunlimited.www.wally.tricklepay"
const val EXCHANGE_MESSAGE = "info.bitcoinunlimited.www.wally.exchange"
const val SETTINGS_MESSAGE = "info.bitcoinunlimited.www.wally.settings"
const val ASSETS_MESSAGE = "info.bitcoinunlimited.www.wally.invoices"
const val IDENTITY_MESSAGE = "info.bitcoinunlimited.www.wally.identity"

val IDENTITY_OP_RESULT = 27720
val IDENTITY_SETTINGS_RESULT = 27721
val TRICKLEPAY_RESULT = 27722
val IMAGE_RESULT = 27723
val READ_FILES_PERMISSION_RESULT = 27724

private val LogIt = Logger.getLogger("BU.wally.commonUI")

fun String.toSet():Set<String>
{
    return split(","," ").map({it.trim()}).filter({it.length > 0}).toSet()
}

fun isCashAddrScheme(s: String): Boolean
{
    val chain = uriToChain[s.lowercase()]
    return chain != null
    //return (s == "BITCOINCASH") || (s == "bitcoincash") || (s == "bchtest") || (s == "BCHTEST") || (s == "bchreg") || (s == "BCHREG") || (s == "NEX") || (s == "nex")
}

/** Do whatever you pass within the user interface context, synchronously */
fun <RET> doUI(fn: suspend () -> RET): RET
{
    return runBlocking(Dispatchers.Main) {
        fn()
    }
}

/** Do whatever you pass within the user interface context, asynchronously */
fun asyncUI(fn: suspend () -> Unit): Unit
{
    GlobalScope.launch(Dispatchers.Main) {
        try
        {
            fn()
        } catch (e: Exception)
        {
            LogIt.warning("Exception in laterUI: " + e.toString())
        }

    }
}

// see https://stackoverflow.com/questions/8276634/how-to-get-hosting-activity-from-a-view
fun getActivity(view: View): Activity?
{
    var context: Context = view.getContext();
    while (context is ContextWrapper)
    {
        if (context is Activity) return context
        context = context.getBaseContext()
    }
    return null
}



fun textChanged(cb: () -> Unit): TextWatcher
{
    return object : TextWatcher
    {
        override fun afterTextChanged(p0: Editable?)
        {
            cb()
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
        {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
        {
        }
    }
}

// note to stop multiple copies of activities from being launched, use android:launchMode="singleTask" in the activity definition in AndroidManifest.xml

fun bottomNavSelectHandler(item: MenuItem, ths: Activity): Boolean
{
    when (item.itemId)
    {
        R.id.navigation_identity ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, IdentityActivity::class.java).apply {
                putExtra(IDENTITY_MESSAGE, message)
            }
            ths.startActivity(intent)
            return true
        }

        R.id.navigation_trickle_pay ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, TricklePayActivity::class.java).apply {
                putExtra(TRICKLEPAY_MESSAGE, message)
            }
            ths.startActivity(intent)
            return true
        }
/*
        R.id.navigation_exchange ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, ExchangeActivity::class.java).apply {
                putExtra(EXCHANGE_MESSAGE, message)
            }
            ths.startActivity(intent)
            return true
        }

        R.id.navigation_invoices ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, InvoicesActivity::class.java).apply {
                putExtra(INVOICES_MESSAGE, message)
            }
            ths.startActivity(intent)
            return true
        }
*/
        R.id.navigation_assets ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, AssetsActivity::class.java).apply {  // TODO create Assets Activity
                putExtra(ASSETS_MESSAGE, message)
            }
            ths.startActivity(intent)
            return true
        }

        R.id.navigation_home ->
        {
            val intent = Intent(ths, MainActivity::class.java)
            ths.startActivity(intent)
            return true

            /* This goes back */
            /*
            if (!(ths is MainActivity))
            {
                ths.finish()
                return@bottomNavSelectHandler true
            }
            return false
            */
        }

        R.id.navigation_shopping ->
        {
            val intent = Intent(ths, ShoppingActivity::class.java)
            ths.startActivity(intent)
            return true
        }
    }

    return false
}

@Throws(WriterException::class)
fun textToQREncode(value: String, size: Int): Bitmap?
{
    val bitMatrix: BitMatrix

    val hintsMap = mapOf<EncodeHintType, Any>(
      EncodeHintType.CHARACTER_SET to "utf-8",
      EncodeHintType.MARGIN to 1
    )
    // //hintsMap.put(EncodeHintType.ERROR_CORRECTION, mErrorCorrectionLevel);
    try
    {
        bitMatrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hintsMap)
    } catch (e: IllegalArgumentException)
    {

        return null
    }


    val bitMatrixWidth = bitMatrix.getWidth()

    val bitMatrixHeight = bitMatrix.getHeight()

    val pixels = IntArray(bitMatrixWidth * bitMatrixHeight)


    //val white = 0xFFFFFFFF.toInt()
    //val black = 0xFF000000.toInt()
    val white: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.white) } ?: 0xFFFFFFFF.toInt()
    val black: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.black) } ?: 0xFF000000.toInt()

    var offset = 0
    for (y in 0 until bitMatrixHeight)
    {
        for (x in 0 until bitMatrixWidth)
        {
            pixels[offset] = if (bitMatrix.get(x, y))
                black
            else
                white
            offset += 1
        }
    }

    LogIt.info("Encode image for $value")
    if (value.contains("Pay2"))
    {
        LogIt.info("Bad image string")
    }
    val bitmap = Bitmap.createBitmap(pixels, bitMatrixWidth, bitMatrixHeight, Bitmap.Config.RGB_565)

    //bitmap.setPixels(pixels, 0, bitMatrixWidth, 0, 0, bitMatrixWidth, bitMatrixHeight)
    return bitmap
}

// stores and recycles views as they are scrolled off screen
open class GuiListItemBinder<DATA> (val view: View) : RecyclerView.ViewHolder(view), View.OnClickListener
{
    var pos: Int = -1
    var data: DATA? = null

    init
    {
        view.setOnClickListener(this)
    }

    // null is passed to d if and only if the position is beyond the end of the list because you have selected empty bottom lines
    fun bind(position: Int, d: DATA?)
    {
        pos = position
        data = d
        populate()
    }

    fun unbind()
    {
        if (pos != -1)
        {
            unpopulate()
            pos = -1
            data = null
        }
    }

    override fun onClick(v: View)
    {
        changed()
    }

    // Fill the view with this data
    open fun populate()
    {
    }

    open fun changed()
    {
    }

    // Pull the data from the view
    open fun unpopulate()
    {
        changed()
    }

    // Override to pick a custom background color based on this item's contents
    open fun backgroundColor(highlight: Boolean = false):Long
    {
        return -1
    }
}

class GuiList<DATA, BINDER: GuiListItemBinder<DATA>> internal constructor(val view: RecyclerView, var data: List<DATA>,  @Suppress("UNUSED_PARAMETER") context: Context?, val factory: (ViewGroup) -> BINDER) : RecyclerView.Adapter<BINDER>()
{
    // Change (on init, before assignment to the RecyclerView) to have empty bottom lines (note your binder must be able to handle beyond-end-of-list bindings)
    var emptyBottomLines = 0
    // Set this to an array of colors to set the background colors of each row to alternate
    var rowBackgroundColors: Array<Int>? = null

    var highlightPos = -1

    /** Assign this to a new list (by reference) */
    fun set(newData: List<DATA>)
    {
        data = newData
        // detach and reattach the adapter since the data has changed (seems weird that all this is needed)
        view.adapter = null
        val tmp = view.layoutManager
        view.layoutManager = null
        view.adapter = this
        view.layoutManager = tmp
        notifyDataSetChanged()
        tmp?.requestLayout()
    }
    /** call to highlight the item at this position in the list (only 1 item may be highlighted, any existing highlight is removed) */
    fun highlight(position: Int)
    {
        dehighlight()
        highlightPos = position
        val vh = view.findViewHolderForAdapterPosition(position)
        if (vh != null)
        {
            setBackgroundColorFor(vh as BINDER, position, true)
        }
    }

    /** call to remove any highlighting */
    fun dehighlight()
    {
        val hp = highlightPos
        if (hp != -1)
        {
            val vh = view.findViewHolderForAdapterPosition(hp)
            if (vh != null)
            {
                setBackgroundColorFor((vh as BINDER), hp, false)
            }
            highlightPos = -1
        }
    }

    protected fun setBackgroundColorFor(holder: BINDER, position: Int, highlight: Boolean = false)
    {
        val bk = holder.backgroundColor(highlight)
        if (bk == -1L)
        {
            val rbc = rowBackgroundColors
            if (rbc != null)
            {
                val colIdx = position % rbc.size
                holder.view.setBackgroundColor(rbc[colIdx])
            }
        }
        else
        {
            holder.view.setBackgroundColor(bk.toInt())
        }
    }

    // inflates the row layout from xml when needed
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BINDER
    {
        //val view: View = inflater.inflate(R.layout.filterrow, parent, false)
        //return factory(view)
        return(factory(parent))
    }

    override fun onDetachedFromRecyclerView (rv: RecyclerView)
    {
        return
    }

    override fun onViewDetachedFromWindow(holder: BINDER)
    {
        holder.unpopulate()
    }

    override fun onFailedToRecycleView(holder: BINDER): Boolean
    {
        holder.unpopulate()
        return true
    }

    override fun onViewRecycled(holder: BINDER)
    {
        holder.unbind()
    }

    // binds the data to the TextView in each row
    override fun onBindViewHolder(holder: BINDER, position: Int)
    {
        holder.unbind()

        if (position < data.size)
        {
            val d = data[position]
            holder.bind(position, d)
        }
        else
        {
            holder.bind(position, null)
        }

        setBackgroundColorFor(holder, position)
    }

    // total number of rows
    override fun getItemCount(): Int
    {
        return data.size + emptyBottomLines
    }

    // convenience method for getting data at click position
    fun getItem(id: Int): DATA
    {
        return data[id]
    }

    init {
        view.adapter = this
    }
}
