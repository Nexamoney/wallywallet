// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.net.URLEncoder
import java.util.logging.Logger
import kotlin.math.floor
import kotlin.math.roundToInt


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

val WallyRowColors = arrayOf(0xFFf5f8ff.toInt(), 0xFFf0f0ff.toInt())

// Assign this in your App.onCreate
var displayMetrics = DisplayMetrics()

private val LogIt = Logger.getLogger("BU.wally.commonUI")

fun String.toSet():Set<String>
{
    return split(","," ").map({it.trim()}).filter({it.length > 0}).toSet()
}

fun String.urlEncode():String
{
    return URLEncoder.encode(this, "utf-8")
}

fun PayAddress.urlEncode():String
{
    return toString().urlEncode()
}


fun EditText.set(s: String)
{
    val len = text.length
    text.replace(0,len, s)
}


/** dig through text looking for addresses */
fun scanForFirstAddress(s: String):PayAddress?
{
    val words = s.split(" ",",","!",".","@","#","$","%","^","&","*","(",")","{","}","[","]","\\","|","/",">","<",";","'","\"","~","+","=","-","_","`","~","?") // None of these characters are allowed in addresses so split the text on them
    for (w in words)
    {
        if (w.length > 32)  // cashaddr type addresses are pretty long
        {
            try
            {
                val pa = PayAddress(w)
                return pa
            }
            catch (e: Exception)
            {
                // not an address
            }
        }
    }
    return null
}

/** Convert a ChainSelector to its currency code at 100M/1000 units */
val chainToDisplayCurrencyCode: Map<ChainSelector, String> = mapOf(
  ChainSelector.NEXATESTNET to "tNEX", ChainSelector.NEXAREGTEST to "rNEX", ChainSelector.NEXA to "NEX",
  ChainSelector.BCH to "uBCH", ChainSelector.BCHTESTNET to "tuBCH", ChainSelector.BCHREGTEST to "ruBCH"
)

fun BigDecimal.setCurrency(chainSelector: ChainSelector): BigDecimal
{
    when (chainSelector)
    {
        ChainSelector.BCHTESTNET, ChainSelector.BCHREGTEST, ChainSelector.BCH -> setScale(uBchDecimals)
        ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> setScale(NexDecimals)
    }
    return this
}

val ChainSelector.currencyDecimals: Int
  get()
{
    return when (this)
    {
        ChainSelector.BCHTESTNET, ChainSelector.BCHREGTEST, ChainSelector.BCH -> uBchDecimals
        ChainSelector.NEXA, ChainSelector.NEXAREGTEST, ChainSelector.NEXATESTNET -> NexDecimals
    }
}

fun Spinner.setSelection(v: String): Boolean
{
    for (i in 0 until count)
    {
        if (getItemAtPosition(i).toString() == v)
        {
            setSelection(i)
            return true
        }
    }
    return false
}
fun isCashAddrScheme(s: String): Boolean
{
    val chain = uriToChain[s.lowercase()]
    return chain != null
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
        }
        catch (e: Exception)
        {
            handleThreadException(e,"Exception in laterUI", sourceLoc())
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



// https://stackoverflow.com/questions/29664993/how-to-convert-dp-px-sp-among-each-other-especially-dp-and-sp
public fun dpToPx(dp: Float): Int
{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics).toInt()
}
fun spToPx(sp: Float): Int
{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, displayMetrics).toInt()
}
fun dpToSp(dp: Float): Int
{
    return (dpToPx(dp) / displayMetrics.scaledDensity).toInt()
}


/** Set text into the textview as big as possible up to an optional max.  A negative desiredWidth is the textview's space minus this amount in SP */
fun TextView.sizedText(t: String, desiredWidthInDp: Int?=null, maxFontSizeInSp: Int? = null)
{
    // Android insanity!  I just want to get the biggest possible visible width for this box!  maxWidth return MAX_INT??!!
    var tvwidth = width
    if (desiredWidthInDp == null || desiredWidthInDp<=0)
    {
        try
        {
            var p = parent
            // 0 means (fit parent)
            while ((tvwidth == 0) && (p != null))
            {
                tvwidth = (p as View).width
                p = (p as View).parent
            }
        } catch (e: Exception)
        {
            tvwidth = displayMetrics.widthPixels
        }
        if (tvwidth == 0) tvwidth = displayMetrics.widthPixels

        if (desiredWidthInDp != null && desiredWidthInDp < 0)
        {
            // Actually subtractin but its a negative number
            tvwidth += dpToPx(desiredWidthInDp.toFloat())
        }
    }
    else
    {
       tvwidth = dpToPx(desiredWidthInDp.toFloat())
    }

    paint.setTextSizeForWidth(t, tvwidth, maxFontSizeInSp)
    text = t
}

/**
 * https://stackoverflow.com/questions/12166476/android-canvas-drawtext-set-font-size-from-width
 * Sets the text size for a Paint object so a given string of text will be a
 * given width.
 *
 * @param paint
 * the Paint to set the text size for
 * @param desiredWidth
 * the desired width
 * @param text
 * the text that should be that width
 */
fun Paint.setTextSizeForWidth(text: String, desiredWidth: Int, maxFontSizeInSp: Int? = null)
{

    // Pick a reasonably large value for the test. Larger values produce
    // more accurate results, but may cause problems with hardware
    // acceleration. But there are workarounds for that, too; refer to
    // http://stackoverflow.com/questions/6253528/font-size-too-large-to-fit-in-cache
    val testTextSize = 80f

    // Get the bounds of the text, using our testTextSize.
    textSize = testTextSize
    val bounds = Rect()
    getTextBounds(text, 0, text.length, bounds)

    // Calculate the desired size as a proportion of our testTextSize.
    var desiredTextSize = testTextSize * desiredWidth / bounds.width()

    // Set the paint for that size.
    if (maxFontSizeInSp != null)
    {
        val tmp = spToPx(maxFontSizeInSp.toFloat())
        if (tmp < desiredTextSize) desiredTextSize = tmp.toFloat()
    }
    textSize = floor(desiredTextSize)
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
    }
    catch (e: IllegalArgumentException)
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
open class GuiListItemBinder<DATA> (val view: View) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnFocusChangeListener
{
    var pos: Int = -1  // Do not change -- only the RecyclerView changes this via the bind() function
    var data: DATA? = null

    init
    {
        view.setOnClickListener(this)
        view.setOnFocusChangeListener(this)
    }

    // null is passed to d if and only if the position is beyond the end of the list because you have selected empty bottom lines
    fun bind(position: Int, d: DATA?)
    {
        synchronized(this)
        {
            pos = position
            data = d
            populate()
        }
    }

    fun unbind()
    {
        synchronized(this)
        {
            if (pos != -1)
            {
                unpopulate()
                pos = -1
                data = null
            }
        }
    }


    /** Default action for simplicity is to make gaining focus act like a click (because you have to touch a list item to gain focus) */
    override fun onFocusChange(v: View, focused: Boolean)
    {
        LogIt.info("onfocus:" + focused)
        if (focused) onClick(v)
    }

    override fun onClick(v: View)
    {
        LogIt.info("onclick")
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


/*  hmmm... tricky kotlin
fun<DATA, BINDER, DATAVIEW> GuiListInflater(view: RecyclerView, data: List<DATA>):GuiList<DATA, BINDER>
{
    return GuiList<DATA, BINDER>(view, data, view.context, {
            val ui = DATAVIEW.inflate(LayoutInflater.from(it.context), it, false)
            BINDER.create(ui)
    })
}
 */

open class GuiList<DATA, BINDER: GuiListItemBinder<DATA>> internal constructor(val view: RecyclerView, var data: List<DATA>,  @Suppress("UNUSED_PARAMETER") context: Context?, val factory: (ViewGroup) -> BINDER) : RecyclerView.Adapter<BINDER>()
{
    // Change (on init, before assignment to the RecyclerView) to have empty bottom lines (note your binder must be able to handle beyond-end-of-list bindings)
    var emptyBottomLines = 0
    // Set this to an array of colors to set the background colors of each row to alternate
    var rowBackgroundColors: Array<Int>? = null
    var highlightColor: Int = ContextCompat.getColor(view.context,R.color.defaultListHighlight)
    var highlightPos = -1

    /** Call if the size of an item changed */
    fun layout()
    {
        // detach and reattach the adapter since the data has changed (seems weird that all this is needed)
        view.adapter = null
        val tmp = view.layoutManager
        view.layoutManager = null
        view.adapter = this
        view.layoutManager = tmp
        tmp?.requestLayout()
    }

    /** Assign this to a new list (by reference) */
    fun set(newData: List<DATA>)
    {
        data = newData
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
            setBackgroundColorFor(vh as? BINDER, position, true)
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
                setBackgroundColorFor(vh as? BINDER, hp, false)
            }
            highlightPos = -1
        }
    }

    protected fun setBackgroundColorFor(holder: BINDER?, position: Int, highlight: Boolean = false)
    {
        if (holder == null) return
        val bk = holder.backgroundColor(highlight)
        if (bk == -1L)
        {
            if (highlight) holder.view.setBackgroundColor(highlightColor)
            else
            {
                val rbc = rowBackgroundColors
                if (rbc != null)
                {
                    val colIdx = position % rbc.size
                    holder.view.setBackgroundColor(rbc[colIdx])
                }
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
        return(factory(parent))
    }

    override fun onDetachedFromRecyclerView (rv: RecyclerView)
    {
        super.onDetachedFromRecyclerView(rv)
        return
    }

    // The holder might still be active, its just scrolled off the screen
    override fun onViewDetachedFromWindow(holder: BINDER)
    {
        super.onViewDetachedFromWindow(holder)
    }

    override fun onFailedToRecycleView(holder: BINDER): Boolean
    {
        holder.unbind()
        return true
    }

    override fun onViewRecycled(holder: BINDER)
    {
        holder.unbind()
    }

    /** override to implement an on-click handler for any item (or do it in the BINDER class per-item) */
    open fun onItemClicked(holder: BINDER)
    {
        LogIt.info(sourceLoc() + " item clicked")

    }

    // binds the data to the View in each row
    override fun onBindViewHolder(holder: BINDER, position: Int)
    {
        holder.unpopulate()
        holder.unbind()

        if (position < data.size)
        {
            holder.bind(position, data[position])
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
        view.setOnClickListener(object:View.OnClickListener {
            override fun onClick(p0: View?)
            {
                LogIt.info(sourceLoc() + "on click")
            }

        })
        view.setOnFocusChangeListener(object:View.OnFocusChangeListener {
            override fun onFocusChange(p0: View?, p1: Boolean)
            {
                LogIt.info(sourceLoc() + "on focus")
            }

        })

    }
}
