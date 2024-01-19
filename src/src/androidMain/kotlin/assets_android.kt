package info.bitcoinunlimited.www.wally

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


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
        val dir = context!!.getDir("card", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        val name = file.absolutePath
        FileInputStream(file).use {
            return Pair(name, it.readBytes())
        }
    }
}


actual fun assetManagerStorage(): AssetManagerStorage = AndroidAssetManagerStorage