package info.bitcoinunlimited.www.wally

import android.graphics.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.nexa.libnexakotlin.GetLog
import java.io.InputStream
import java.lang.Double.min


private val LogIt = GetLog("BU.wally.QR")


// Modified from: https://stackoverflow.com/questions/4837715/how-to-resize-a-bitmap-in-android
fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap?
{
    val width = bm.width
    val height = bm.height
    val scaleWidth = newWidth.toDouble() / width
    val scaleHeight = newHeight.toDouble() / height
    val scale = min(scaleWidth, scaleHeight).toFloat()
    val matrix = Matrix()
    matrix.postScale(scale, scale)
    val resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false)
    if (resizedBitmap != null) bm.recycle()
    return resizedBitmap
}


class BitmapLuminance(val bmp: Bitmap):LuminanceSource(bmp.width, bmp.height)
{
    var values: ByteArray = ByteArray(bmp.width * bmp.height)
    init
    {
        val argbs = IntArray(bmp.width * bmp.height)
        bmp.getPixels(argbs, 0, bmp.width, 0 , 0, bmp.width, bmp.height)
        // Load the pixels into
        for (i in 0 until argbs.size)
        {
            val c = argbs[i]
            val grey = ((c and 255) + ((c shr 8) and 255) + ((c shr 16) and 255))/3
            values[i] = grey.toByte()
        }

    }

    override fun getMatrix(): ByteArray = values

    override fun getRow(y: Int, reuse: ByteArray): ByteArray
    {
        var b = if (reuse.size == width) reuse else ByteArray(width, { 0} )
        var offset = y*width
        for (i in 0 until width)
        {
            b[i] = values[offset]
        }
        return b
    }
}

fun readQRcode(strm: InputStream): String
{
    val reader = MultiFormatReader()
    reader.setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))

    var bmp: Bitmap = BitmapFactory.decodeStream(strm)
    if (bmp.height > 5000 || bmp.width > 5000)  // Keep things sane for the analysis code
    {
        bmp = getResizedBitmap(bmp, 5000, 5000) ?: bmp
    }
    val lsource = BitmapLuminance(bmp)
    val binarizer = HybridBinarizer(lsource)
    val imbin = BinaryBitmap(binarizer)
    val result = reader.decode(imbin)
    return result.text
}