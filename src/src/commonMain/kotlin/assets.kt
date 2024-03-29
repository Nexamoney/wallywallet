package info.bitcoinunlimited.www.wally

import info.bitcoinunlimited.www.wally.ui.displayFastForwardInfo
import io.ktor.http.*
import okio.Buffer
import okio.BufferedSource
import org.nexa.libnexakotlin.*
import org.nexa.threads.Gate
import org.nexa.threads.LeakyBucket
import org.nexa.threads.iThread
import org.nexa.threads.millisleep

private val LogIt = GetLog("BU.wally.assets")

// If true, do not cache asset info locally -- load it every time
var DBG_NO_ASSET_CACHE = false

val ASSET_ACCESS_TIMEOUT_MS = 30*60*1000L  // Check assets every half hour even if nothing happened

open class IncorrectTokenDescriptionDoc(details: String) : LibNexaException(details, "Incorrect token description document", ErrorSeverity.Expected)

val NIFTY_ART_IP = mapOf(
  ChainSelector.NEXA to "niftyart.cash",
  // Enable manually for niftyart development: ChainSelector.NEXAREGTEST to "192.168.1.5:8988"
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
  var displayAmount: Long? = null
)

val assetCheckTrigger = Gate("assetCheckTrigger")
// will take 30 (trigger every 30 seconds on average, max 3 times in a row)
var assetCheckPacer = LeakyBucket(90*1000, 1000, 25*1000)
fun triggerAssetCheck()
{
    assetCheckTrigger.wake()
}

fun AssetLoaderThread(): iThread
{
    return org.nexa.threads.Thread("assetLoader") {
        // Constructing the asset list can use a lot of disk which interferes with startup
        // This will wait until all the accounts are loaded
        while (wallyApp!!.nullablePrimaryAccount == null) millisleep(2000UL)
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
            LogIt.info("asset check")
            try
            {
                val accounts = wallyApp!!.accountLock.lock {
                    wallyApp!!.accounts.values
                }
                for (a in accounts)
                    a.getXchgRates("USD")

                for (a in accounts)
                {
                    try
                    {
                        a.constructAssetMap({ getEc(a.chain) })
                    }
                    catch(e: ElectrumRequestTimeout)
                    {
                        // ec.close()
                    }
                }
                // let all the electrum cnxns go
                //for (ec in ecCnxns)
                //{
                //    ec.value?.close()
                //}
                //ecCnxns.clear()
            }
            catch(e: Exception)
            {
                handleThreadException(e)
            }
            LogIt.info("asset delay")
            // We don't want to recheck assets more often than every 30 sec, regardless of blockchain activity
            assetCheckTrigger.delayuntil(ASSET_ACCESS_TIMEOUT_MS, { assetCheckPacer.trytake(30*1000, { true}) == true })
        }
    }
}


class AssetInfo(val groupId: GroupId)
{
    var dataLock = Gate()
    var name:String? = null
    var ticker:String? = null
    var genesisHeight:Long = 0
    var genesisTxidem: Hash256? = null
    var docHash: Hash256? = null
    var docUrl: String? = null
    var tokenInfo: TokenDesc? = null
    var iconBytes: ByteArray? = null
    var iconUri: Url? = null
    var iconBackBytes: ByteArray? = null
    var iconBackUri: Url? = null

    var nft: NexaNFTv2? = null

    var publicMediaCache: String? = null   // local storage location (if it exists)
    var publicMediaUri: Url? = null        // canonical location (needed to find the file extension to get its type  at a minimum)
    var publicMediaBytes: ByteArray? = null

    var ownerMediaCache: String? = null
    var ownerMediaUri: Url? = null        // canonical location (needed to find the file extension to get its type at a minimum)
    var ownerMediaBytes: ByteArray? = null

    // Connection to the UI if shown on screen
    //var ui:AssetBinder? = null
    //var sui:AssetSuccinctBinder? = null

    var loadState: AssetLoadState = AssetLoadState.UNLOADED

    // TODO remove
    var account: Account? = null

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
                    iconBytes = bytes
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
                    iconBackBytes = bytes
                    // Note caching is NEEDED to show video (because videoview can only take a file)
                    if (iconBackBytes != null) iconBackUri = Url("file://" + am.storeCardFile(grpId.toStringNoPrefix() + "_" + fname, bytes))
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
        var td:TokenDesc = am.getTokenDesc(chain, groupId, getEc)
        synchronized(dataLock)
        {
            LogIt.info("Loading Asset ${groupId.toStringNoPrefix()}")
            var tg = td.genesisInfo
            if (tg == null) return@synchronized
            var dataChanged = false

            LogIt.info(sourceLoc() + chain.name + ": loaded: " + td.name)

            name = td.name
            ticker = td.ticker
            genesisHeight = tg?.height ?: -1
            genesisTxidem = if (tg.txidem.length > 0) Hash256(tg.txidem) else null

            val du = tg.document_url
            docUrl = du

            if (du != null)
            {
                // Ok find the NFT description
                val nftZipData = am.getNftFile(td, groupId)
                if (nftZipData != null)
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
                            else
                            {
                                td.marketUri = Url(du).resolve("/token/" + groupId.toHex()).toString()
                            }
                            tokenInfo = td
                            am.storeTokenDesc(groupId, td)
                        }
                        extractNftData(am, groupId, nftZipData.second)
                        if (iconBackUri == null) getTddIcon().let { iconBackUri = it.first; iconBackBytes = it.second }
                        loadState == AssetLoadState.COMPLETED
                        dataChanged = true
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
                        getTddIcon().let { iconUri = it.first; iconBytes = it.second }
                        dataChanged = true
                    }
                    am.storeTokenDesc(groupId, td)
                    loadState == AssetLoadState.COMPLETED
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
                            loadState == AssetLoadState.COMPLETED
                        }
                        catch (e: Exception)
                        {
                            // Something went wrong (probably out of memory).
                            logThreadException(e, "Exception opening NFT")
                        }
                        dataChanged = true


                    }
                    finally
                    {
                        nftZipData.second.close()
                    }
                }
            }

            /*  TODO ui changes
            laterUI {
                if (dataChanged)
                {
                    ui?.repopulate()
                    sui?.repopulate()
                }
            }

             */
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

class AssetManager(val app: CommonApp): AssetManagerStorage
{
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


    fun track(groupId: GroupId, getEc: (() -> ElectrumClient)? = null): AssetInfo
    {
        var ret = assets[groupId]
        if (ret != null) return ret

        val blockchain = connectBlockchain(groupId.blockchain)
        val gec = getEc ?: ElectrumClientFactory(blockchain)

        ret = AssetInfo(groupId)
        assets[groupId] = ret
        wallyApp?.let { app -> ret.load(blockchain, this, gec) }
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
            if (ret.genesisInfo?.height ?: 0 >= 0)  // If the data is valid in the asset file
                return ret
        }
        catch (e: Exception) // file not found, so grab it from the network
        {
        }

        LogIt.info(sourceLoc() + ": Genesis Info for ${groupId.toString()} (${groupId.toHex()}) not in cache")
        // first load the token description doc (TDD)
        val net = chain.req.net

        try
        {
                val td = getTokenInfo(groupId.parentGroup(), getEc)
                val tg = td.genesisInfo
                if (tg == null)  // Should not happen except network
                {
                    throw ElectrumRequestTimeout()
                }

                // If the genesis commitment to the doc matches the actual document, then we can use it
                val tddHash = td.tddHash
                if ((tddHash != null) && (tg.document_hash == tddHash.toHex()))
                {
                    storeTokenDesc(groupId, td)  // We got good data, so cache it
                    return td
                }
                else
                {
                    LogIt.info("Incorrect or non-existent token desc document")
                    return td
                }
        }
        catch(e: Exception)  // Normalize exceptions
        {
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
        LogIt.info(sourceLoc() + "nft URL: " + url)

        var zipBytes:ByteArray? = null
        if (url != null)
        {
            try
            {
                zipBytes = Url(url).readBytes()
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
                url = "https://niftyart.cash/_public/${groupId.toStringNoPrefix()}"
                zipBytes = Url(url).readBytes()
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

    override fun storeAssetFile(filename: String, data: ByteArray): String
        = assetManagerStorage().storeAssetFile(filename, data)

    override fun loadAssetFile(filename: String): Pair<String, EfficientFile>
        = assetManagerStorage().loadAssetFile(filename)

    override fun storeCardFile(filename: String, data: ByteArray): String
        = assetManagerStorage().storeCardFile(filename, data)

    override fun loadCardFile(filename: String): Pair<String, ByteArray>
        = assetManagerStorage().loadCardFile(filename)

    override fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
        = assetManagerStorage().cacheNftMedia(groupId, media)
}
