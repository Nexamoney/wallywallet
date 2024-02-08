package info.bitcoinunlimited.www.wally

import android.content.Context
import org.nexa.libnexakotlin.GroupId
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

const val MAX_UNCACHED_FILE_SIZE = 5000000

object AndroidAssetManagerStorage:AssetManagerStorage
{
    override fun storeAssetFile(filename: String, data: ByteArray): String
    {
        val context = wallyAndroidApp

        val dir = context!!.getDir("asset", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        FileOutputStream(file).use {
            it.write(data)
        }
        return file.absolutePath
    }
    override fun loadAssetFile(filename: String): Pair<String, ByteArray>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()
        val context = wallyAndroidApp
        val dir = context!!.getDir("asset", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        val name = file.absolutePath
        FileInputStream(file).use {
            return Pair(name, it.readBytes())
        }
    }
    override fun storeCardFile(filename: String, data: ByteArray): String
    {
        val context = wallyAndroidApp

        /*
    val dir = context.getDir("card", Context.MODE_PRIVATE)
    val file = File(dir, filename)
    FileOutputStream(file).use {
        it.write(data)
    }
     */

        val file = context!!.openFileOutput(filename, Context.MODE_PRIVATE)
        file.use {
            it.write(data)
        }
        return context.getFileStreamPath(filename).path  //.absolutePath
    }

    override fun loadCardFile(filename: String): Pair<String, ByteArray>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()
        val context = wallyAndroidApp
        //val dir = context!!.getDir("card", Context.MODE_PRIVATE)
        //val file = File(dir, filename)
        val file = File(filename)
        val name = file.absolutePath
        FileInputStream(file).use {
            return Pair(name, it.readBytes())
        }
    }

    override fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
    {
        val cacheDir = wallyAndroidApp!!.cacheDir
        var uriStr = media.first
        var b = media.second
        if (b != null)
        {
            if ((b.size > MAX_UNCACHED_FILE_SIZE) || (uriStr!=null && isVideo(uriStr)))
            {
                val result = canonicalSplitExtension(uriStr)
                if (result == null) return Pair(uriStr, b)  // never going to happen because uriStr != null
                val (fnoext, ext) = result
                File.createTempFile(groupId.toStringNoPrefix() + "_" + fnoext, ext, cacheDir)
                val f = File(cacheDir, groupId.toStringNoPrefix() + "_" + fnoext + "." + ext)
                uriStr = f.absolutePath
                f.writeBytes(b)
                b = null  // We want to load this from cache file so don't save the bytes
            }
        }
        return Pair(uriStr, b)
    }
}


actual fun assetManagerStorage(): AssetManagerStorage = AndroidAssetManagerStorage