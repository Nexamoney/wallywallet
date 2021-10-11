package info.bitcoinunlimited.www.wally

import com.google.zxing.*
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.client.*
import com.google.zxing.client.android.*

import android.graphics.BitmapFactory
import android.graphics.Bitmap;
import com.google.zxing.LuminanceSource;

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore

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
    //reader.setHints(mapOf())

    val bmp: Bitmap = BitmapFactory.decodeFile(imageName)
    val lsource = BitmapLuminance(bmp)
    val binarizer = HybridBinarizer(lsource)
    val imbin = BinaryBitmap(binarizer)
    val result = reader.decode(imbin)
    return result.text
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