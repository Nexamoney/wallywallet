package info.bitcoinunlimited.www.wally

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.*
import android.graphics.Color.WHITE
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.nexa.libnexakotlin.GetLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

fun readQRcode(imageName: String): String
{
    val reader = MultiFormatReader()
    reader.setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))

    var bmp: Bitmap = BitmapFactory.decodeFile(imageName)
    if (bmp.height > 2000 || bmp.width > 2000)  // Keep things sane for the analysis code
    {
        bmp = getResizedBitmap(bmp, 2000, 2000) ?: bmp
    }
    val lsource = BitmapLuminance(bmp)
    val binarizer = HybridBinarizer(lsource)
    val imbin = BinaryBitmap(binarizer)
    val result = reader.decode(imbin)
    return result.text

    /* Attempt to wrap white around the image, but the moire is probably the real problem
    try
    {
        val result = reader.decode(imbin)
        return result.text
    }
    catch (e: com.google.zxing.NotFoundException)
    {
        // Its pretty common to forget that the white space around the QR code *IS* part of the QR code
        // stick the bitmap in a field of white and try again
        var bmp2 = Bitmap.createBitmap(bmp.width*3/2, bmp.height*3/2, bmp.config)
        val cnvs = Canvas(bmp2)
        cnvs.drawColor(WHITE)
        cnvs.drawBitmap(bmp, (bmp.width / 4).toFloat(), (bmp.height / 4).toFloat(), Paint())

        if (bmp2.height > 1000 || bmp2.width > 1000)  // Keep things sane for the analysis code
        {
            bmp2 = getResizedBitmap(bmp2, 1000, 1000) ?: bmp2
        }

        try
        {
            //val state = Environment.getExternalStorageState()
            val file = File(appContext!!.context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES)[0], "/testQRoutput.png")
            //val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/Camera/testQRoutput.png")
            LogIt.info("Dumping file to: " + file.toString())

            FileOutputStream(file).use { out ->
                bmp2.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
                out.close()
            }
            val file2 = File(appContext!!.context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES)[0], "/foo.txt")
            FileOutputStream(file2).use { out ->
                out.write("hello world".toByteArray())
                out.close()
            }
        } catch (e: IOException)
        {
            e.printStackTrace()
        }

        val lsource = BitmapLuminance(bmp2)
        val binarizer = HybridBinarizer(lsource)
        val imbin = BinaryBitmap(binarizer)
        val result = reader.decode(imbin)
        return result.text
    }

     */
}

// from: https://handyopinion.com/get-path-from-uri-in-kotlin-android/
class URIPathHelper
{
    fun getPath(context: Context, uri: Uri): String?
    {
        val isKitKatorAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKatorAbove && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }

            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String?
    {
        if (uri == null) return null
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,null)
            if (cursor != null && cursor.moveToFirst())
            {
                val column_index: Int = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(column_index)
            }
        }
        finally
        {
            if (cursor != null) cursor.close()
        }
        return null
    }

    fun isExternalStorageDocument(uri: Uri): Boolean
    {
        return "com.android.externalstorage.documents" == uri.authority
    }

    fun isDownloadsDocument(uri: Uri): Boolean
    {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    fun isMediaDocument(uri: Uri): Boolean
    {
        return "com.android.providers.media.documents" == uri.authority
    }
}