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
const val MAX_UNCACHED_FILE_SIZE = 2000000


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
        return path.toString()
    }
    override fun loadAssetFile(filename: String): Pair<String, ByteArray>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()

        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        var path = if (dirs.size > 0) dirs[0].toString().toPath() / "assets" else "assets".toPath()
        if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path = path / filename

        FileSystem.SYSTEM.read(path) {
            val data = this.readByteArray()
            return Pair(path.toString(), data)
        }
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
        return path.toString()
    }
    override fun loadCardFile(filename: String): Pair<String, ByteArray>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        var path = if (dirs.size > 0) dirs[0].toString().toPath() / "cache" else "cache".toPath()
        if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)
        path = path / filename
        FileSystem.SYSTEM.read(path) {
            val data = this.readByteArray()
            return Pair(path.toString(), data)
        }
    }

    override fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
    {
        val uriStr = media.first
        val b = media.second
        if (b != null)
        {
            if ((b.size > MAX_UNCACHED_FILE_SIZE) || (uriStr != null && isVideo(uriStr)))
            {
                val result = canonicalSplitExtension(uriStr)
                if (result == null) return Pair(uriStr, b)  // never going to happen because uriStr != null
                val (fnoext, ext) = result

                val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                var path = if (dirs.size > 0) dirs[0].toString().toPath() / "cache" else "cache".toPath()
                if (!FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.createDirectories(path)

                path = path / (groupId.toStringNoPrefix() + "_" + fnoext + "." + ext)
                FileSystem.SYSTEM.write(path) {  // ASYNC
                    this.write(b)
                }
                return Pair(path.toString(), null)
            }
        }
        return Pair(uriStr, b)
    }
}

actual fun assetManagerStorage(): AssetManagerStorage
{
    return IosAssetManagerStorage
}