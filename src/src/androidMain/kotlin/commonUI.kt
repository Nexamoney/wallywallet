// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.*
import java.net.URLEncoder
import java.util.*

import kotlin.math.floor
import com.ionspin.kotlin.bignum.decimal.*
import org.nexa.libnexakotlin.*
import java.net.URL
import java.net.URLDecoder

const val SUP = "UNUSED_PARAMETER"

val IMAGE_RESULT = 27723
val READ_FILES_PERMISSION_RESULT = 27724
val READ_MEDIA_IMAGES_RESULT = 27725

// Assign this in your App.onCreate
var displayMetrics = DisplayMetrics()


private val LogIt = GetLog("BU.wally.commonUI")

fun PayAddress.urlEncode():String
{
    return toString().urlEncode()
}

fun EditText.set(s: String)
{
    val len = text.length
    text.replace(0,len, s)
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

// https://stackoverflow.com/questions/29664993/how-to-convert-dp-px-sp-among-each-other-especially-dp-and-sp
public fun dpToPx(dp: Float): Int
{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics).toInt()
}
fun spToPx(sp: Float): Int
{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, displayMetrics).toInt()
}

fun spToPxF(sp: Float): Float
{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, displayMetrics)
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
        val tmp = spToPxF(maxFontSizeInSp.toFloat())
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


    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    // TODO access resource
    //val white: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.white) } ?: 0xFFFFFFFF.toInt()
    //val black: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.black) } ?: 0xFF000000.toInt()

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

/** Connects a gui switch to a preference DB item.  To be called in onCreate.
 * Returns the current state of the preference item.
 * Uses setOnCheckedChangeListener, so you cannot call that yourself.  Instead pass your listener into this function
 * */
fun SetupBooleanPreferenceGui(key: String, db: SharedPreferences, defaultValue: Boolean, button: CompoundButton, onChecked: ((CompoundButton?, Boolean) -> Unit)? = null): Boolean
{
    val ret = db.getBoolean(key, defaultValue)
    button.setChecked(ret)

    button.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener
    {
        override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean)
        {
            with(db.edit())
            {
                putBoolean(key, isChecked)
                commit()
            }
            if (onChecked != null) onChecked(buttonView, isChecked)
        }
    })
    return ret
}

/** Connects a gui text entry field to a preference DB item.  To be called in onCreate */
fun SetupTextPreferenceGui(key: String, db: SharedPreferences, view: EditText)
{
    view.text.clear()
    view.text.append(db.getString(key, ""))

    view.addTextChangedListener(object : TextWatcher
    {
        override fun afterTextChanged(p: Editable?)
        {
            dbgAssertGuiThread()
            if (p == null) return
            val text = p.toString()
            with(db.edit())
            {
                putString(key, text)
                commit()
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
        {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
        {
        }
    })
}

/** Connects a gui text entry field to a preference DB item.  To be called in onCreate */
fun SetupNexCurrencyPreferenceGui(key: String, db: SharedPreferences, view: EditText)
{
    view.text.clear()
    if (true)
    {
        val v = db.getString(key, "0") ?: "0"
        val dec = try
        {
            CurrencyDecimal(v)
        }
        catch (e:Exception)
        {
            CurrencyDecimal(0)
        }
        view.text.append(db.getString(key, NexaFormat.format(dec)))
    }

    view.addTextChangedListener(object : TextWatcher
    {
        override fun afterTextChanged(p: Editable?)
        {
            dbgAssertGuiThread()
            if (p == null) return
            val text = p.toString()
            try
            {
                val dec = CurrencyDecimal(text)
                with(db.edit())
                {
                    putString(key, CurrencySerializeFormat.format(dec))
                    commit()
                }
            }
            catch (e:Exception)  // number format execption, for one
            {
                logThreadException(e)
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
        {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
        {
        }
    })
}

/**
 * Convert a uri scheme to a url, and then plug it into the java URL parser.
 * @return java.net.URL
 */
fun String.toUrl(): URL
{
    // replace the scheme with http so we can use URL to parse it
    val index = indexOf(':')
    // val scheme = take(index)
    val u = URL("http" + drop(index))
    return u
}