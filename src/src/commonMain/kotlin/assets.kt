package info.bitcoinunlimited.www.wally

import androidx.compose.ui.graphics.ImageBitmap
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.FileNotFoundException
import org.nexa.libnexakotlin.*
import org.nexa.threads.*
import kotlin.coroutines.CoroutineContext


const val ASSET_ICON_SIZE = 100
const val MAX_UNCACHED_FILE_SIZE = 20000

val assetCoCtxt: CoroutineContext = newFixedThreadPoolContext(4, "asset")

/*
// on android this fails with couldn't find "libskiko-android-arm64.so", see https://github.com/JetBrains/skiko/issues/531
fun scaleUsingSurface(image: Image, width: Int, height: Int): Image
{
    //val bmp = Bitmap.makeFromImage(image)
    val bmp = Bitmap(width, height)
    val cvs = Canvas(bmp)
    val sfx = width.toFloat() / image.width.toFloat()
    val sfy = height.toFloat() / image.height.toFloat()
    // Pick the scale factor that's closest to no change (1)
    val sf = if (abs(1.0-sfx) > abs(1.0-sfy)) sfy else sfx
    cvs.scale(sf, sf)
    val ret = Image.
    return im
}

fun scaleTo(imageBytes: ByteArray, width: Int, height: Int): Image
{
    val imIn = Image.makeFromEncoded(imageBytes)
    imIn.
    val im = scaleUsingSurface(imIn, width, height)
    val d = im.encodeToData(outFormat)
    return d?.bytes
}

fun scaleTo(image: Image, width: Int, height: Int, outFormat: EncodedImageFormat): ByteArray?
{
    val im = scaleUsingSurface(image, width, height)
    val d = im.encodeToData(outFormat)
    return d?.bytes
}

actual fun scaleTo(imageBytes: ByteArray, width: Int, height: Int, outFormat: EncodedImageFormat): ByteArray?
{
    val imIn = Image.makeFromEncoded(imageBytes)
    val im = scaleUsingSurface(imIn, width, height)
    val d = im.encodeToData(outFormat)
    return d?.bytes
}
*/

private val LogIt = GetLog("BU.wally.assets")

// If true, do not cache asset info locally -- load it every time
var DBG_NO_ASSET_CACHE = false

val ASSET_ACCESS_TIMEOUT_MS = 30*60*1000L  // Check assets every half hour even if nothing happened

open class IncorrectTokenDescriptionDoc(details: String) : LibNexaException(details, "Incorrect token description document", ErrorSeverity.Expected)

val NIFTY_ART_IP = mapOf(
  ChainSelector.NEXA to "niftyart.cash",
  // Enable manually for niftyart development: ChainSelector.NEXAREGTEST to "192.168.1.5:8988"
  ChainSelector.NEXATESTNET to "192.168.2.11:8988"
)
val NIFTY_ART_WEB = mapOf(
  ChainSelector.NEXA to "https://niftyart.cash",
  // Enable manually for niftyart development: ChainSelector.NEXAREGTEST to "192.168.1.5:8988"
    ChainSelector.NEXATESTNET to "http://192.168.2.11:8988"
)

fun String.runCommand(): String?
{
    throw UnimplementedException("cannot run executables on Android")
}

enum class AssetLoadState
{
    UNLOADED,
    LOADED_GENESIS_INFO,
    LOADED_TOKEN_DESC,
    // LOADED_NFT,  same as completed at this point
    COMPLETED
}

class AssetPerAccount(
  val groupInfo: GroupInfo,
  val assetInfo:AssetInfo,
  // display this amount if set (for quantity selection during send)
  var editableAmount: BigDecimal? = null
)
{
    fun tokenDecimalToFinestUnit(amt: BigDecimal): Long
    {
        val decimalPlaces = assetInfo.tokenInfo?.genesisInfo?.decimal_places ?: 0
        return (amt * BigDecimal.fromInt(10, ).pow(decimalPlaces)).toLong()
    }

    fun tokenDecimalFromString(s: String): BigDecimal
    {
        val dp = assetInfo.tokenInfo?.genesisInfo?.decimal_places ?: 0
        val dm = tokenDecimalMode(dp)
        val bd = BigDecimal.fromString(s, dm)
        return bd
    }

    fun tokenDecimalFromFinestUnit(finestAmount: Long): BigDecimal
    {
        val decimalPlaces = assetInfo.tokenInfo?.genesisInfo?.decimal_places ?: 0
        var tmp = BigDecimal.fromLong(finestAmount,tokenDecimalMode(decimalPlaces))
        tmp = tmp/(BigDecimal.fromInt(10).pow(decimalPlaces ?: 0))
        return tmp
    }
}

val assetCheckTrigger = Gate("assetCheckTrigger")
// will take 30 (trigger every 30 seconds on average, max 3 times in a row)
var assetCheckPacer = LeakyBucket(90*1000, 1000, 25*1000)
fun triggerAssetCheck()
{
    assetCheckPacer.level = 90*1000  // force recheck regardless of leaky bucket
    assetCheckTrigger.wake()
}

fun AssetLoaderThread(): iThread
{
    return org.nexa.threads.Thread("assetLoader")
    {
        // Constructing the asset list can use a lot of disk which interferes with startup
        // This will wait until all the accounts are loaded
        while (wallyApp!!.nullablePrimaryAccount == null) millisleep(5000UL)
        val ecCnxns = mutableMapOf<ChainSelector, ElectrumClient?>()

        fun getEc(chain:Blockchain): ElectrumClient
        {
            val cs: ChainSelector = chain.chainSelector
            var e = ecCnxns[cs]
            if ((e == null) || (e.open == false))
            {
                e = chain.net.getElectrum()
                LogIt.info("Got electrum for ${cs}: ${e}")
                ecCnxns[cs] = e
            }
            return e
        }

        while (true)
        {
            LogIt.info(sourceLoc() + ": asset check")
            try
            {
                // Take a copy
                val accounts = wallyApp!!.accountLock.lock {
                    wallyApp!!.accounts.values.toList()
                }
                for (a in accounts)
                    a.getXchgRates(localCurrency)

                // Refresh all assets
                for (a in accounts)
                {
                    LogIt.info(sourceLoc() + ":   asset check for ${a.name}")
                    try
                    {
                        a.constructAssetMap({ getEc(a.chain) })
                    }
                    catch(e: ElectrumRequestTimeout)
                    {
                        // ec.close()
                    }
                }
            }
            catch(e: Exception)
            {
                handleThreadException(e)
            }
            LogIt.info(sourceLoc() + ": asset check complete")
            // We don't want to recheck assets more often than every 30 sec, regardless of blockchain activity
            assetCheckTrigger.delayuntil(ASSET_ACCESS_TIMEOUT_MS, { assetCheckPacer.trytake(30*1000, { true}) == true })
        }
    }
}

/*
object BigDecimalSerializer: JsonTransformingSerializer<BigDecimal>(tSerializer = object: KSerializer<BigDecimal>
{
    // java override fun deserialize(decoder: Decoder): BigDecimal = decoder.decodeString().toBigDecimal()
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal.fromString(decoder.decodeString(), SerializationMode)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
})
{
    override fun transformDeserialize(element: JsonElement): JsonElement
    {
        var s = element.toString()
        if (s[0] == '"') s = s.drop(1).dropLast(1)
        return JsonPrimitive(value = s)
    }
}
 */

object Hash256Serializer: KSerializer<Hash256>
{
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Hash256", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Hash256
    {
        return Hash256(decoder.decodeString())
    }
    override fun serialize(encoder: Encoder, value: Hash256)
    {
        encoder.encodeString(value.toHex())
    }
}

object UrlSerializer: KSerializer<Url>
{
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Url", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Url
    {
        return Url(decoder.decodeString())
    }
    override fun serialize(encoder: Encoder, value: Url)
    {
        encoder.encodeString(value.toString())
    }
}


@Serializable
class AssetInfo(val groupId: GroupId) // :BCHserializable
{
    companion object
    {
        const val SERIALIZE_VERSION = 1.toByte()
    }
    @kotlinx.serialization.Transient var dataLock = Gate()
    var name: String? = null
        set(value) {
            _nameState.value = value
            field = value
        }
    @Transient private val _nameState: MutableStateFlow<String?> = MutableStateFlow(name)
    @Transient val nameObservable = _nameState.asStateFlow()
    var ticker:String? = null
    var genesisHeight:Long = -1
    @Serializable(with = Hash256Serializer::class) var genesisTxidem: Hash256? = null
    @Serializable(with = Hash256Serializer::class) var docHash: Hash256? = null
    var docUrl: String? = null
    var tokenInfo: TokenDesc? = null
    var iconBytes: ByteArray? = null
    @Transient var iconImage: ImageBitmap? = null
    @Serializable(with = UrlSerializer::class) var iconUri: Url? = null
    var iconBackBytes: ByteArray? = null
    @Serializable(with = UrlSerializer::class) var iconBackUri: Url? = null

    var nft: NexaNFTv2? = null
        set(value) {
            _nftState.value = value
            field = value
        }
    @Transient private var _nftState: MutableStateFlow<NexaNFTv2?> = MutableStateFlow(nft)
    @Transient val nftObservable = _nftState.asStateFlow()

    var publicMediaCache: String? = null   // local storage location (if it exists)
    @Serializable(with = UrlSerializer::class)
    var publicMediaUri: Url? = null        // canonical location (needed to find the file extension to get its type  at a minimum)
    @Transient
    var publicMediaBytes: ByteArray? = null

    var ownerMediaCache: String? = null
    @Serializable(with = UrlSerializer::class)
    var ownerMediaUri: Url? = null        // canonical location (needed to find the file extension to get its type at a minimum)
    @Transient
    var ownerMediaBytes: ByteArray? = null

    var loadState: AssetLoadState = AssetLoadState.UNLOADED
        set(value) {
            _loadState.value = value
            field = value
        }
    @Transient private val _loadState: MutableStateFlow<AssetLoadState> = MutableStateFlow(loadState)
    @Transient var loadStateObservable = _loadState.asStateFlow()
    /*
    // We know our serialization format is stable so its the safest choice to use
    override fun BCHdeserialize(stream: BCHserialized): BCHserialized
    {
        val ver = stream.debyte()
        if (ver != SERIALIZE_VERSION) throw SerializationException("Invalid version deserializing AssetInfo: $ver")
        name = stream.denullString()
        ticker = stream.denullString()
        genesisHeight = stream.deint64()
        genesisTxidem = stream.denullHash()
        docHash = stream.denullHash()
        tokenInfo = stream.deNullable { TokenDesc.BCHdeserialize(it) }
        iconBytes = stream.denullByteArray()
        iconBackBytes = stream.denullByteArray()
        nft = NexaNFTv2.deserialize(stream)

        publicMediaCache = stream.denullString()
        publicMediaUri = stream.denullString()
    }

    override fun BCHserialize(format: SerializationType): BCHserialized
    {
        var ret = BCHserialized(format).add(SERIALIZE_VERSION).add(name).add(ticker).addInt64(genesisHeight).add(genesisTxidem ?: Hash256())
          .add(docHash ?: Hash256()).add(docUrl)
          .addNullable(tokenInfo)
          .addNullableVariableSized(iconBytes)
          .addNullableVariableSized(iconBackBytes)
          .addNullable(nft)

        ret.add(publicMediaCache)  // TODO do not want to serialize "null" .add(publicMediaUri.toString())
        // If we've stored the image locally, do not save its bytes
        if (publicMediaCache == null) ret.addNullableVariableSized(publicMediaBytes)
        else ret.addNullableVariableSized(null)

        ret.add(ownerMediaCache) // TODO do not want to serialize "null" .add(ownerMediaUri.toString())
        if (ownerMediaCache == null) ret.addNullableVariableSized(ownerMediaBytes)
        else ret.addNullableVariableSized(null)

        ret.addUint8(loadState.ordinal)
        return ret
    }

     */

    fun finalize()
    {
        dataLock.finalize()
    }

    /** Get the file associated with this NFT (if this is an NFT), or null
     * This function may retrieve this data from a local cache or remotely.
     * This is a function rather than a val with a getter to emphasize that this might be an expensive operation
     * */
    fun nftFile(am: AssetManager):Pair<String,EfficientFile>?
    {
        // TODO: cache both in RAM and on disk
        return am.getNftFile(tokenInfo, groupId)
    }

    fun extractNftData(am: AssetManager, grpId: GroupId, nftZip:EfficientFile)
    {
        zipForeach(nftZip) { zipDirRecord, data ->
            val fname = zipDirRecord.fileName
            if (fname.startsWith("cardf"))
            {
                // Note caching is NEEDED to show video (because videoview can only take a file)
                if (data != null)
                {
                    val bytes = data.readByteArray()
                    iconBytes = if (bytes.size < MAX_UNCACHED_FILE_SIZE) bytes else null
                    iconImage = makeImageBitmap(bytes, ASSET_ICON_SIZE,ASSET_ICON_SIZE, ScaleMode.INSIDE)
                    val tmp = am.storeCardFile(grpId.toStringNoPrefix() + "_" + fname, bytes)
                    iconUri = Url("file://" + tmp)
                }
                else
                {
                    iconBytes = null
                    iconUri = Url("file://" + fname)
                }
            }
            else if (fname.startsWith("cardb"))
            {
                if (data != null)
                {
                    val bytes = data.readByteArray()
                    iconBackBytes = if (bytes.size < MAX_UNCACHED_FILE_SIZE) bytes else null
                    // Note caching is NEEDED to show video (because videoview can only take a file)
                    if (iconBackBytes == null) iconBackUri = Url("file://" + am.storeCardFile(grpId.toStringNoPrefix() + "_" + fname, bytes))
                    else iconBackUri = Url("file://" + fname)
                }
            }
            else if (fname.startsWith("public"))
            {
                // I need to know the filename so I know the file format (how to decode the file).  So make a Uri with the filename
                publicMediaUri = Url("file://" + (if (!fname.startsWith("/")) "/" else "") + fname)
                val bytes = data?.readByteArray()
                val ptmp = am.cacheNftMedia(grpId, Pair(fname, bytes))
                publicMediaCache = ptmp.first
                publicMediaBytes = ptmp.second
            }
            else if (fname.startsWith("owner"))
            {
                // I need to know the filename so I know the file formant (how to decode the file).  So make a Uri with the filename
                ownerMediaUri = Url("file://" + (if (!fname.startsWith("/")) "/" else "") + fname)
                val bytes = data?.readByteArray()
                val otmp = am.cacheNftMedia(grpId, Pair(fname, bytes))
                ownerMediaCache = otmp.first
                ownerMediaBytes = otmp.second
            }
            else if (fname == "info.json")
            {
                val bytes = data?.readByteArray()

                if (bytes != null)
                {
                    nftDataFromInfoFile(bytes)?.let { nft = it }
                }
            }
            false
        }

    }

    /*
    fun extractNftDataSlow(am: AssetManager, grpId: GroupId, nftZip:ByteArray)
    {
        // TODO rewrite using zip.foreach to grab every piece of data at once for efficiency

        // grab image from zip file
        val (fname, data) = nftCardFront(nftZip)
        if (fname != null)
        {
            iconBytes = data
            // Note caching is NEEDED to show video (because videoview can only take a file)
            if (data != null)
            {
                val tmp = am.storeCardFile(grpId.toStringNoPrefix() + "_" + fname, data)
                iconUri = Url("file://" + tmp)
            }
            else iconUri = Url("file://" + fname)
        }
        val (bname, bdata) = nftCardBack(nftZip)
        if (bname != null)
        {
            iconBackBytes = bdata
            // Note caching is NEEDED to show video (because videoview can only take a file)
            if (bdata != null) iconBackUri = Url("file://" + am.storeCardFile(grpId.toStringNoPrefix() + "_" + bname, bdata))
            else iconBackUri = Url("file://" + bname)
        }

        // For the public and owner media, indicate that its there and possibly put it into the cache
        val pub = nftPublicMedia(nftZip)
        pub.first?.let {// I need to know the filename so I know the file formant (how to decode the file).  So make a Uri with the filename
            publicMediaUri = Url("file://" + (if (!it.startsWith("/")) "/" else "") + it)
        }
        val ptmp = am.cacheNftMedia(grpId, pub)
        publicMediaCache = ptmp.first
        publicMediaBytes = ptmp.second

        val own = nftOwnerMedia(nftZip)
        own.first?.let {// I need to know the filename so I know the file formant (how to decode the file).  So make a Uri with the filename
            ownerMediaUri = Url("file://" + (if (!it.startsWith("/")) "/" else "") + own.first)
        }
        val otmp = am.cacheNftMedia(grpId, own)
        ownerMediaCache = otmp.first
        ownerMediaBytes = otmp.second

        // grab NFT text data
        val nfti = nftData(nftZip)
        if (nfti != null)
        {
            nft = nfti
        }
    }

     */

    /** returns the Uri and the bytes, or null if nonexistent, cannot be loaded */
    fun getTddIcon(): Pair<Url?, ByteArray?>
    {
        val iconUrl = tokenInfo?.icon
        if (iconUrl != null)
        {
            var url = Url(iconUrl)
            // isRelativePath appears to be broken on android
            if ((url.isRelativePath) || (url.host == "localhost") || (iconUrl.startsWith("/")))
            {
                val du = docUrl ?: return Pair(null, null)
                url = Url(du).resolve(iconUrl)
            }
            try
            {
                val data = url.readBytes()
                return Pair(url, data)
            }
            catch (e: Exception)
            {
                return Pair(null, null)
            }
        }
        return Pair(null,null)
    }

    fun load(chain: Blockchain, am: AssetManager, getEc: () -> ElectrumClient)  // Attempt to find all asset info from a variety of data sources
    {
        if (loadState == AssetLoadState.COMPLETED) return
        LogIt.info(sourceLoc() + chain.name + ": Loading Asset: Get Token Desc ${groupId}")
        var td:TokenDesc = am.getTokenDesc(chain, groupId, getEc)
        LogIt.info(sourceLoc() + chain.name + ": Get Token Desc complete: ${groupId} td: ${td}")
        synchronized(dataLock)
        {
            LogIt.info(sourceLoc() + chain.name + ": Loading Asset: Process genesis info ${groupId.toStringNoPrefix()}")
            var tg = td.genesisInfo
            if (tg == null) return@synchronized

            LogIt.info(sourceLoc() + chain.name + ": loaded: " + tg.name)

            name = tg.name ?: td.name
            ticker = tg.ticker ?: td.ticker
            genesisHeight = tg?.height ?: -1
            genesisTxidem = if (tg.txidem.length > 0) Hash256(tg.txidem) else null

            val du = tg.document_url
            docUrl = du

            if (du != null)
            {
                LogIt.info(sourceLoc() +": Getting NFT file for ${groupId}")
                // Ok find the NFT description
                val nftZipData = am.getNftFile(td, groupId)
                if (nftZipData != null)
                {
                    LogIt.info(sourceLoc() +": Got NFT file for ${groupId}")
                    try
                    {
                        tokenInfo = td
                        if (td.marketUri == null)
                        {
                            val u: Url = Url(nftZipData.first)
                            if (u.isAbsolutePath)  // This is a real URI, not a local path
                            {
                                td.marketUri = u.resolve("/token/" + groupId.toHex()).toString()
                            }
                            else
                            {
                                td.marketUri = Url(du).resolve("/token/" + groupId.toHex()).toString()
                            }
                            tokenInfo = td
                            am.storeTokenDesc(groupId, td)
                        }
                        extractNftData(am, groupId, nftZipData.second)
                        if (iconBackUri == null) getTddIcon().let { iconBackUri = it.first; iconBackBytes = it.second }
                        LogIt.info("NFT download complete for ${groupId}")
                        loadState = AssetLoadState.COMPLETED
                    }
                    finally
                    {
                        nftZipData.second.close()
                    }
                }
                else  // Not an NFT, so fill in the data from the TDD
                {
                    LogIt.info(sourceLoc() + ": $name == ${td.name}, $ticker == ${td.ticker} ")
                    if (ticker != td.ticker)
                    {
                        throw IncorrectTokenDescriptionDoc("ticker does not match asset genesis transaction")
                    }

                    if (td.pubkey != null)  // the document signature passed
                    {
                        name = td.name
                        ticker = td.ticker
                    }
                    // Guess one location for the market
                    td.marketUri = Url(du).resolve("/token/" + groupId.toHex()).toString()
                    tokenInfo = td  // ok save all the info
                    val iconUrl = td?.icon
                    if (iconUrl != null)
                    {
                        getTddIcon().let {
                            iconUri = it.first
                            iconBytes = it.second
                            iconBytes?.let { b -> iconImage = makeImageBitmap(b, ASSET_ICON_SIZE,ASSET_ICON_SIZE, ScaleMode.INSIDE) }
                        }
                        loadState = AssetLoadState.COMPLETED
                    }
                    am.storeTokenDesc(groupId, td)
                }
            }
            else // Missing some standard token info, look around for an NFT file
            {
                val nftZipData = am.getNftFile(null, groupId)
                if (nftZipData != null)  // This is a verified NFT file
                {
                    try
                    {
                        tokenInfo = td
                        if (td.marketUri == null)
                        {
                            val u: Url = Url(nftZipData.first)
                            if (u.isAbsolutePath)  // This is a real URI, not a local path
                            {
                                td.marketUri = u.resolve("/token/" + groupId.toHex()).toString()
                                am.storeTokenDesc(groupId, td)
                            }
                        }

                        try
                        {
                            extractNftData(am, groupId, nftZipData.second)
                            loadState = AssetLoadState.COMPLETED
                        }
                        catch (e: Exception)
                        {
                            // Something went wrong (probably out of memory).
                            logThreadException(e, "Exception opening NFT")
                        }
                    }
                    finally
                    {
                        nftZipData.second.close()
                    }
                }
            }
        }
    }

}


/** The asset manager storage class is a wrapper to store and retrieve NFT assets.
 *
 * Since different platforms have different chosen locations to store different media types,
 * this entire class was made expect/actual, rather than relying on a general purpose multiplatform
 * file library.
*/
interface AssetManagerStorage
{
    /** This should save data in a more permanent and possibly shared location; recommend "assets" directory */
    fun storeAssetFile(filename: String, data: ByteArray): String
    /** Load a file from the @storeAssetFile location */
    fun loadAssetFile(filename: String): Pair<String, EfficientFile>

    /** delete a particular asset file */
    fun deleteAssetFile(filename: String)

    /** delete all asset files */
    fun deleteAssetFiles()

    /** Save data in a temporary/cache location; it is only expected that this data continue to exist while the program
     * is running.  Recommend "wallyCache" directory.
     */
    fun storeCardFile(filename: String, data: ByteArray): String

    /** Load data from where it was stored using @storeCardFile */
    fun loadCardFile(filename: String): Pair<String, ByteArray>

    /** Chooses to cache the media or not depending on platform capabilities and the media size.
     *  It is only expected that this data continue to exist while the program is running.
     *  Note that some platforms CANNOT (due to API limitations) stream video from ByteArrays so on those platforms
     *  the cache is always used for video.
     *  If cached, must return a string Url that points to the local cached file in a path format appropriate for this platform, and null for bytearray
     *  If not cached, returns the global Uri location, most importantly including the filename (for media type determination), and data in the bytearray */
    fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
}


// Returns a function that returns the same electrum client until that client fails, and then creates a new one.
// TODO clean up the client when the factory is done.
fun ElectrumClientFactory(blockchain: Blockchain): ()->ElectrumClient
{
    var ec:ElectrumClient? = null
    return {
        retry(10) {
            val tmp = ec
            if (tmp != null && tmp.open) ec
            else
            {
                ec = blockchain.net.getElectrum()
                if (ec == null) millisleep(1000U)
                ec
            }
        }
    }
}

class AssetManager(): AssetManagerStorage
{
    protected var access = Mutex("assetLock")
    var assets = mutableMapOf<GroupId, AssetInfo>()

    fun nftUrl(s: String?, groupId: GroupId):String?
    {
        if (s == null)
        {
            return("http://" + NIFTY_ART_IP[groupId.blockchain] + "/_public/" + groupId.toStringNoPrefix())
        }
        // TODO many more ways to get it
        return null
    }


    /** Start tracking this asset & return the current info if already tracking.
     * If not tracking and the passed electrum client is null, this asset's info will be loaded asynchronously.
     * Otherwise the passed electrum client will be used to load the asset synchronously
     */
    fun track(groupId: GroupId, getEc: (() -> ElectrumClient)? = null): AssetInfo
    {

        var ret = access.lock { assets[groupId] }
        if (ret != null)
        {
            // LogIt.info(sourceLoc() + ": Asset manager: already tracking ${groupId.toString()}")
            return ret
        }
        LogIt.info(sourceLoc() + ": Asset manager: track ${groupId.toString()}")
        try
        {
            val ai = loadAssetInfo(groupId)
            LogIt.info(sourceLoc() + ":   Asset manager: loaded ${ai.name} icon(${ai.iconBytes?.size ?: -1} bytes): ${ai.iconUri.toString()} group: ${groupId}")
            access.lock { assets[groupId] = ai }
            return ai
        }
        catch (e: Throwable)  // out of memory?
        {
            // logThreadException(e, "Ignored exception loading asset info for ${groupId}")
        }
        catch (e: Exception)  // probably file not found which is expected
        {
            // logThreadException(e, "Ignored exception loading asset info for ${groupId}")
        }

        val blockchain = connectBlockchain(groupId.blockchain)
        val gec = getEc ?: ElectrumClientFactory(blockchain)

        ret = AssetInfo(groupId)
        access.lock { assets[groupId] = ret }
        if (getEc != null)
        {
            LogIt.info("Immediate asset load for ${groupId}")
            ret.load(blockchain, this, gec)
            if (ret.loadState == AssetLoadState.COMPLETED)
                storeAssetInfo(ret)
        }
        else later(CoroutineScope(assetCoCtxt))
        {
            delay(1000)
            LogIt.info("Deferred asset load for ${groupId} happening now")
            try
            {
                ret.load(blockchain, this, gec)
                if (ret.loadState == AssetLoadState.COMPLETED)
                    storeAssetInfo(ret)
            }
            catch(e:ElectrumRequestTimeout)
            {
                // nothing to do, electrum not available
                LogIt.info("Cannot load ${groupId}, no electrum servers")
            }
        }
        return ret
    }

    fun storeTokenDesc(groupId: GroupId, td: TokenDesc)
    {
        val ser = kotlinx.serialization.json.Json.encodeToString(TokenDesc.serializer(), td)
        assetManagerStorage().storeAssetFile(groupId.toHex() + ".td", ser.toByteArray())
    }

    fun getTokenDesc(chain: Blockchain, groupId: GroupId, getEc: () -> ElectrumClient, forceReload:Boolean = false): TokenDesc
    {
        try
        {
            if (DBG_NO_ASSET_CACHE) throw Exception()
            if (forceReload) throw Exception()
            val ef = assetManagerStorage().loadAssetFile(groupId.toHex() + ".td").second
            val data = ef.openAt(0).readAndClose()
            ef.close()
            val s = data.decodeUtf8()
            val ret = kotlinx.serialization.json.Json.decodeFromString(TokenDesc.serializer(), s)
            if (((ret.genesisInfo?.height ?: 0) >= 0) && (ret.pubkey != null))  // If the data is valid in the asset file
                return ret
        }
        catch (e: Exception) // file not found, so grab it from the network
        {
        }

        LogIt.info(sourceLoc() + ": Token info not in cache or incomplete for ${groupId} (${groupId.toHex()})")
        // first load the token description doc (TDD)
        try
        {
                val td = getTokenInfo(groupId.parentGroup(), getEc, chain.net)
                LogIt.info(sourceLoc() + ": Got token info for ${groupId} (${groupId.toHex()})")
                val tg = td.genesisInfo
                if (tg == null)  // Should not happen except network
                {
                    LogIt.info(sourceLoc() + ": No token info available")
                    throw ElectrumRequestTimeout()
                }

                // If the genesis commitment to the doc matches the actual document, then we can use it
                val tddHash = td.tddHash
                if ((tddHash != null) && (tg.document_hash == tddHash.toHex()))
                {
                    LogIt.info(sourceLoc() + ": Saving token info for ${groupId}")
                    storeTokenDesc(groupId, td)  // We got good data, so cache it
                    return td
                }
                else
                {
                    LogIt.info(sourceLoc() + ": Incorrect or non-existent token desc document for ${groupId}")
                    return td
                }
        }
        catch(e: Exception)  // Normalize exceptions
        {
            handleThreadException(e, "getting token info (assetmanager.getTokenDesc) for ${groupId}")
            throw ElectrumRequestTimeout()
        }
    }


    /** Checks various sources for the NFT file, and returns it, only if it's hash properly matches the subgroup.
     * This means that a source cannot lie about what the NFT file is, so we can check untrusted sources. */
    fun getNftFile(td: TokenDesc?, groupId: GroupId):Pair<String, EfficientFile>?
    {
        try
        {
            return assetManagerStorage().loadAssetFile(groupId.toHex() + ".zip")
        }
        catch(e: Exception) // file not found
        {
            LogIt.info(sourceLoc() +": NFT ${groupId.toHex()} not in cache")
        }

        var url = td?.nftUrl ?: nftUrl(td?.genesisInfo?.document_url, groupId)
        LogIt.info(sourceLoc() + "${groupId} NFT URL: " + url)

        var zipBytes:ByteArray? = null
        if (url != null)
        {
            try
            {
                LogIt.info(sourceLoc() + "${groupId} trying NFT URL: " + url)
                zipBytes = Url(url).readBytes(context = tokenCoCtxt)
            }
            catch(e:Exception)
            {
                logThreadException(e, "(from token doc location) NFT not loaded ")
            }
        }

        // Try well known locations
        if (zipBytes == null || zipBytes.size == 0)
        {
            try
            {
                url = "${NIFTY_ART_WEB[groupId.blockchain]}/_public/${groupId.toStringNoPrefix()}"
                LogIt.info(sourceLoc() + "${groupId} trying NFT URL: " + url)
                zipBytes = Url(url).readBytes(context = tokenCoCtxt)
                LogIt.info(sourceLoc() + "${groupId} received NFT URL: " + url)
            }
            catch(e: Exception)
            {
                logThreadException(e, "(trying niftyart) NFT not loaded ")
            }
        }

        if (zipBytes != null && zipBytes.size > 0)
        {
            LogIt.info("NFT file loaded for ${groupId.toStringNoPrefix()}")

            val hash = libnexa.hash256(zipBytes)
            if (groupId.subgroupData() contentEquals hash)
            {
                LogIt.info(sourceLoc() + "nft zip file matches hash for ${groupId.toStringNoPrefix()}")
            }
            else
            {
                // check another typical but nonstandard hash
                val hash = libnexa.sha256(zipBytes)
                if (groupId.subgroupData() contentEquals hash)
                {
                    LogIt.info(sourceLoc() + "nft zip file matches sha256 hash for ${groupId.toStringNoPrefix()}")
                }
                else return null
            }
            val ef = EfficientFile(zipBytes)
            val nftData = nftData(ef)  // Sanity check the file
            if (nftData == null)
            {
                LogIt.info("but is NOT an NFT file")
                return null
            }
            storeAssetFile(groupId.toHex() + ".zip", zipBytes)
            return Pair(url!!, ef)
        }

        return null
    }

    fun storeAssetInfo(assetInfo: AssetInfo)
    {
        /* Asset info is too big for sqlite database
        val db = kvpDb!!
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
        db.set(assetInfo.groupId.data, json.encodeToString(AssetInfo.serializer(), assetInfo).toByteArray())
         */
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val ais = json.encodeToString(AssetInfo.serializer(), assetInfo).toByteArray()
        assetManagerStorage().storeAssetFile(assetInfo.groupId.toStringNoPrefix() + ".ai", ais)
    }

    fun loadAssetInfo(groupId: GroupId): AssetInfo
    {
        /* Asset info is too big for sqlite database
        val db = kvpDb!!
        val data = db.get(groupId.data)
        */
        val ef = assetManagerStorage().loadAssetFile(groupId.toStringNoPrefix() + ".ai")
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val data = ef.second.openAt(0).readAndClose()
        val ret = json.decodeFromString<AssetInfo>(AssetInfo.serializer(), data.decodeToString())
        ef.second.close()
        if (ret.iconUri != null)
        {
            val bytes = if (ret.iconBytes != null) ret.iconBytes else
            {
                ret.iconUri?.toString()?.let {
                    try
                    {
                        val cf = loadCardFile(it)
                        cf.second
                    }
                    catch(e:FileNotFoundException)  // its benign if we load the asset info but not the icon
                    {
                        LogIt.info("Note asset info for ${ret.name} loaded, but no card file ${it}")
                        null
                    }
                }
            }
            bytes?.let { ret.iconImage = makeImageBitmap(it, ASSET_ICON_SIZE, ASSET_ICON_SIZE, ScaleMode.INSIDE) }
        }
        return ret
    }

    fun clear()
    {
        val assetKeys = access.lock { assets.keys.toList() }
        for (k in assetKeys)
           if (k!=null) kvpDb?.delete(k.data)
        access.lock {
            for (v in assets.values)  // clean this data structure in case someone is holding a copy of it
            {
                v.iconUri = null
                v.iconBytes = null
                v.tokenInfo = null
                v.publicMediaUri = null
                v.publicMediaBytes = null
                v.ownerMediaUri = null
                v.ownerMediaCache = null
                v.nft = null
                v.loadState = AssetLoadState.UNLOADED
            }
        }
        access.lock { assets.clear() }
        assetManagerStorage().deleteAssetFiles()
    }

    override fun storeAssetFile(filename: String, data: ByteArray): String
        = assetManagerStorage().storeAssetFile(filename, data)

    override fun loadAssetFile(filename: String): Pair<String, EfficientFile>
        = assetManagerStorage().loadAssetFile(filename)

    override fun deleteAssetFile(filename: String) =
        assetManagerStorage().deleteAssetFile(filename)

    override fun deleteAssetFiles() = assetManagerStorage().deleteAssetFiles()

    override fun storeCardFile(filename: String, data: ByteArray): String
        = assetManagerStorage().storeCardFile(filename, data)

    override fun loadCardFile(filename: String): Pair<String, ByteArray>
        = assetManagerStorage().loadCardFile(filename)

    override fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
        = assetManagerStorage().cacheNftMedia(groupId, media)
}
