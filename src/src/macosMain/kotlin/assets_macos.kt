package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.GroupId

object MacosAssetManagerStorage:AssetManagerStorage
{
    override fun storeAssetFile(filename: String, data: ByteArray): String
    {
        TODO()
    }
    override fun loadAssetFile(filename: String): Pair<String, EfficientFile>
    {
        TODO()
    }

    override fun deleteAssetFile(filename: String)
    {
        TODO("Not yet implemented")
    }

    override fun deleteAssetFiles()
    {
        TODO("Not yet implemented")
    }

    override fun storeCardFile(filename: String, data: ByteArray): String
    {
        TODO()
    }
    override fun loadCardFile(filename: String): Pair<String, ByteArray>
    {
        TODO()
    }

    override fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
    {
        TODO("Not yet implemented")
    }
}

actual fun assetManagerStorage(): AssetManagerStorage
{
    return MacosAssetManagerStorage
}