package info.bitcoinunlimited.www.wally

import okio.FileSystem
import okio.Path.Companion.toPath
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.GroupId
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
private val LogIt = GetLog("wally.assetsJvm")

object JvmAssetManagerStorage:AssetManagerStorage
{
    override fun storeAssetFile(filename: String, data: ByteArray): String
    {
        val dir = File("assets")
        if (!dir.exists()) dir.mkdir()

        val file = File("assets", filename)
        FileOutputStream(file).use {
            it.write(data)
        }
        return file.absolutePath
    }
    override fun loadAssetFile(filename: String): Pair<String, EfficientFile>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()
        val file = File("assets", filename)
        val name = file.absolutePath
        //FileInputStream(file).use {
        //    return Pair(name, it.readBytes())
        //}
        val ef = EfficientFile(name.toPath(), FileSystem.SYSTEM)
        return Pair(name, ef)
    }

    /** delete a particular asset file */
    override fun deleteAssetFile(filename: String)
    {
        val dir = File("assets")
        if (!dir.exists()) return
        val file = File(dir, filename)
        file.delete()
    }

    /** delete all asset files */
    override fun deleteAssetFiles()
    {
        val dir = File("assets")
        if (!dir.exists()) return
        val files = dir.listFiles()
        for (f in files)
            f.delete()
    }


    override fun storeCardFile(filename: String, data: ByteArray): String
    {
        val dir = File("wallyCache")
        if (!dir.exists()) dir.mkdir()

        val file = File("wallyCache", filename)
        FileOutputStream(file).use {
            it.write(data)
        }
        LogIt.info("Wrote ${file.absolutePath} ($file)")
        return file.absolutePath
    }
    override fun loadCardFile(filename: String): Pair<String, ByteArray>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()
        var localname = if (filename.startsWith("file://")) filename.drop(7) else filename
        val file = if (!localname.startsWith(File.separator)) File("wallyCache", localname) else File(localname)
        val name = file.absolutePath
        FileInputStream(file).use {
            return Pair(name, it.readBytes())
        }
    }

    override fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
    {
        val dir = File("wallyCache")
        if (!dir.exists()) dir.mkdir()

        val cacheDir = File("wallyCache")
        var uriStr = media.first
        var b = media.second
        if (b != null)
        {
            // Choose to always cache the file regardless of whether we also hold onto its bytes
            if (true) // ((b.size > MAX_UNCACHED_FILE_SIZE) || (uriStr!=null && isVideo(uriStr)))
            {
                val result = canonicalSplitExtension(uriStr)
                if (result == null) return Pair(uriStr, b)  // never going to happen because uriStr != null
                val (fnoext, ext) = result
                val f = File(cacheDir, groupId.toStringNoPrefix() + "_" + fnoext + "." + ext)
                uriStr = f.absolutePath
                f.writeBytes(b)
                if (b.size > MAX_UNCACHED_FILE_SIZE) b = null  // We want to load this from cache file so don't save the bytes
            }
        }
        return Pair(uriStr, b)
    }


}

actual fun assetManagerStorage(): AssetManagerStorage = JvmAssetManagerStorage