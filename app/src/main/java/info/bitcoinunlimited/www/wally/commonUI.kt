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
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
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
const val INVOICES_MESSAGE = "info.bitcoinunlimited.www.wally.invoices"
const val IDENTITY_MESSAGE = "info.bitcoinunlimited.www.wally.identity"

val IDENTITY_OP_RESULT = 27720
val IDENTITY_SETTINGS_RESULT = 27721
val TRICKLEPAY_RESULT = 27722
val IMAGE_RESULT = 27723
val READ_FILES_PERMISSION_RESULT = 27724

private val LogIt = Logger.getLogger("bitcoinunlimited.commonUI")

fun String.toSet():Set<String>
{
    return split(","," ").map({it.trim()}).filter({it.length > 0}).toSet()
}

fun isCashAddrScheme(s: String): Boolean
{
    return (s == "BITCOINCASH") || (s == "bitcoincash") || (s == "bchtest") || (s == "BCHTEST") || (s == "bchreg") || (s == "BCHREG") || (s == "NEX") || (s == "nex")
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
        if (context is Activity) return context as Activity
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
            val intent = Intent(ths, InvoicesActivity::class.java).apply {  // TODO create Assets Activity
                putExtra(INVOICES_MESSAGE, message)
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
