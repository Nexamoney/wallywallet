package info.bitcoinunlimited.www.wally

import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.GroupId
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL.Companion.URLWithString
import platform.Foundation.NSUserDomainMask
import platform.darwin.YESEXPR
private val LogIt = GetLog("wally.assets_ios")

// Does not work
/*
actual fun loadhttps(url: String):ByteArray
{
    val session = platform.Foundation.NSURLSession()
    val dt = session.dataTaskWithURL(URLWithString(url)!!)
    session.getAllTasksWithCompletionHandler {
        if (it != null) for (i in it)
        {
            println(i)
        }
    }
    return byteArrayOf()
}

 */

object IosAssetManagerStorage:AssetManagerStorage
{
    override fun storeAssetFile(filename: String, data: ByteArray): String
    {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        var path = if (dirs.size > 0) dirs[0].toString().toPath() / "assets" else "assets".toPath()
        if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path = path / filename

        FileSystem.SYSTEM.write(path) {
            this.write(data)
        }
        LogIt.info("Wrote ai file $path")
        return path.toString()
    }
    override fun loadAssetFile(filename: String): Pair<String, EfficientFile>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()

        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        var path = if (dirs.size > 0) dirs[0].toString().toPath() / "assets" else "assets".toPath()
        //if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path = path / filename
        if (!FileSystem.SYSTEM.exists(path))
        {
            LogIt.info("ai file does not exist at ${path}")
        }
        /*
        FileSystem.SYSTEM.read(path) {
            val data = this.readByteArray()
            return Pair(path.toString(), EfficientFile(data))
        }
         */
        return Pair(path.toString(), EfficientFile(path, FileSystem.SYSTEM))
    }

    override fun deleteAssetFile(filename: String)
    {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        var path = if (dirs.size > 0) dirs[0].toString().toPath() / "assets" else "assets".toPath()
        if (!FileSystem.SYSTEM.exists(path)) return
        path = path / filename
        FileSystem.SYSTEM.delete(path)
    }

    override fun deleteAssetFiles()
    {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        var path = if (dirs.size > 0) dirs[0].toString().toPath() / "assets" else "assets".toPath()
        if (!FileSystem.SYSTEM.exists(path)) return
        FileSystem.SYSTEM.deleteRecursively(path)
    }

    override fun storeCardFile(filename: String, data: ByteArray): String
    {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        var path = if (dirs.size > 0) dirs[0].toString().toPath() / "cache" else "cache".toPath()
        if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path = path / filename

        FileSystem.SYSTEM.write(path) {
            this.write(data)
        }
        val ret = "cache/" + filename
        LogIt.info("Wrote card file $ret (actually $path)")
        return ret
    }
    override fun loadCardFile(filename: String): Pair<String, ByteArray>
    {
        var localname = if (filename.startsWith("file://")) filename.drop(7) else filename
        if (DBG_NO_ASSET_CACHE) throw Exception()
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        var path = if (dirs.size > 0) dirs[0].toString().toPath() else "".toPath(true)
        path = path / localname
        if (!FileSystem.SYSTEM.exists(path))
        {
            LogIt.error("loadCardfile $path does not exist")
        }
        FileSystem.SYSTEM.read(path) {
            val data = this.readByteArray()
            return Pair(path.toString(), data)
        }
    }

    override fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
    {
        val uriStr = media.first
        var b = media.second
        if (b != null)
        {
            // Choose to always cache the file regardless of whether we also hold onto its bytes
            if (true) //if ((b.size > MAX_UNCACHED_FILE_SIZE) || (uriStr != null && isVideo(uriStr)))
            {
                val result = canonicalSplitExtension(uriStr)
                if (result == null) return Pair(uriStr, b)  // never going to happen because uriStr != null
                val (fnoext, ext) = result

                val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                var path = if (dirs.size > 0) dirs[0].toString().toPath() / "cache" else "cache".toPath()
                if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)

                val fname = groupId.toStringNoPrefix() + "_" + fnoext + "." + ext
                path = path / fname
                FileSystem.SYSTEM.write(path) {  // ASYNC
                    this.write(b!!)
                }
                if (b.size > MAX_UNCACHED_FILE_SIZE) b = null
                // The base directory path changes every run so can't be part of the returned path
                // anyone using this filename will need to search for it off of NSSearchPathForDirectoriesInDomains(...)
                return Pair("cache/" + fname, b)
            }
        }
        return Pair(uriStr, b)
    }
}

actual fun assetManagerStorage(): AssetManagerStorage
{
    return IosAssetManagerStorage
}