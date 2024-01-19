package info.bitcoinunlimited.www.wally

object IosAssetManagerStorage:AssetManagerStorage
{
    override fun storeAssetFile(filename: String, data: ByteArray): String
    {
        TODO()
    }
    override fun loadAssetFile(filename: String): Pair<String, ByteArray>
    {
        TODO()
    }
    override fun storeCardFile(filename: String, data: ByteArray): String
    {
        TODO()
    }
    override fun loadCardFile(filename: String): Pair<String, ByteArray>
    {
        TODO()
    }
}

actual fun assetManagerStorage(): AssetManagerStorage
{
    return IosAssetManagerStorage
}