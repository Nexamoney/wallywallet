package info.bitcoinunlimited.www.wally

import io.ktor.http.*
import okio.Buffer
import okio.BufferedSource
import org.nexa.libnexakotlin.*
import org.nexa.threads.Gate

private val LogIt = GetLog("BU.wally.assets")

// If true, do not cache asset info locally -- load it every time
var DBG_NO_ASSET_CACHE = false

val ASSET_ACCESS_TIMEOUT_MS = 10000

open class IncorrectTokenDescriptionDoc(details: String) : LibNexaException(details, "Incorrect token description document", ErrorSeverity.Expected)

val NIFTY_ART_IP = mapOf(
  ChainSelector.NEXA to "niftyart.cash",
  // Enable manually for niftyart development: ChainSelector.NEXAREGTEST to "192.168.1.5:8988"
)

fun String.runCommand(): String?
{
    throw UnimplementedException("cannot run executables on Android")
}

class AssetInfo(val groupInfo: GroupInfo)
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

    // display this amount if set (for quantity selection during send)
    var displayAmount: Long? = null

    var account: Account? = null

    /** Get the file associated with this NFT (if this is an NFT), or null
     * This function may retrieve this data from a local cache or remotely.
     * This is a function rather than a val with a getter to emphasize that this might be an expensive operation
     * */
    fun nftFile(am: AssetManager):Pair<String,EfficientFile>?
    {
        // TODO: cache both in RAM and on disk
        return am.getNftFile(tokenInfo, groupInfo.groupId)
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
            try
            {
                val url = Url(iconUrl)
                val data = url.readBytes()
                return Pair(url, data)
            }
            catch (e: CannotLoadException)
            {
                val du = docUrl ?: return Pair(null, null)
                try
                {
                    val url = Url(du).resolve(iconUrl)
                    val data = url.readBytes()
                    return Pair(url, data)
                }
                catch(e: Exception)
                {
                    // link is dead
                    return Pair(null, null)
                }
            }
        }
        return Pair(null,null)
    }

    fun load(chain: Blockchain, am: AssetManager)  // Attempt to find all asset info from a variety of data sources
    {
        var td = am.getTokenDesc(chain, groupInfo.groupId)
        synchronized(dataLock)
        {
            LogIt.info("Loading Asset ${groupInfo.groupId.toStringNoPrefix()}")
            var tg = td.genesisInfo
            var dataChanged = false

            if (tg == null)
            {
                td = am.getTokenDesc(chain, groupInfo.groupId, true)
                tg = td.genesisInfo
                if (tg == null) return@synchronized // can't load
            }

            LogIt.info(sourceLoc() + chain.name + ": loaded: " + tg.name)

            name = tg.name
            ticker = tg.ticker
            genesisHeight = tg.height
            genesisTxidem = if (tg.txidem.length > 0) Hash256(tg.txidem) else null

            val du = tg.document_url
            docUrl = du

            if (du != null)
            {
                // Ok find the NFT description
                val nftZipData = am.getNftFile(td, groupInfo.groupId)
                if (nftZipData != null)
                {
                    tokenInfo = td
                    if (td.marketUri == null)
                    {
                        val u: Url = Url(nftZipData.first)
                        if (u.isAbsolutePath)  // This is a real URI, not a local path
                        {
                            td.marketUri = u.resolve("/token/" + groupInfo.groupId.toHex()).toString()
                            am.storeTokenDesc(groupInfo.groupId, td)
                        }
                        else
                        {
                            td.marketUri = Url(du).resolve("/token/" + groupInfo.groupId.toHex()).toString()
                        }
                        tokenInfo = td
                        am.storeTokenDesc(groupInfo.groupId, td)
                    }
                    extractNftData(am, groupInfo.groupId, nftZipData.second)
                    if (iconBackUri == null) getTddIcon().let { iconBackUri = it.first; iconBackBytes = it.second }
                    dataChanged = true
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
                    td.marketUri = Url(du).resolve("/token/" + groupInfo.groupId.toHex()).toString()
                    tokenInfo = td  // ok save all the info
                    am.storeTokenDesc(groupInfo.groupId, td)
                    val iconUrl = tokenInfo?.icon
                    if (iconUrl != null)
                    {
                        getTddIcon().let { iconUri = it.first; iconBytes = it.second }
                        dataChanged = true
                    }
                }
            }
            else // Missing some standard token info, look around for an NFT file
            {
                val nftZipData = am.getNftFile(null, groupInfo.groupId)
                if (nftZipData != null)
                {
                    tokenInfo = td
                    if (td.marketUri == null)
                    {
                        val u: Url = Url(nftZipData.first)
                        if (u.isAbsolutePath)  // This is a real URI, not a local path
                        {
                            td.marketUri = u.resolve("/token/" + groupInfo.groupId.toHex()).toString()
                            am.storeTokenDesc(groupInfo.groupId, td)
                        }
                    }
                    try
                    {
                        extractNftData(am, groupInfo.groupId, nftZipData.second)
                    }
                    catch(e: Exception)
                    {
                        // Something went wrong (probably out of memory).
                        logThreadException(e, "Exception opening NFT")
                    }
                    dataChanged = true
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

class AssetManager(val app: CommonApp): AssetManagerStorage
{
    val transferList = mutableListOf<AssetInfo>()

    fun nftUrl(s: String?, groupId: GroupId):String?
    {
        if (s == null)
        {
            return("http://" + NIFTY_ART_IP[groupId.blockchain] + "/_public/" + groupId.toStringNoPrefix())
        }
        // TODO many more ways to get it
        return null
    }

    /** Adds this asset to the list of assets to be transferred in the next send */
    fun addAssetToTransferList(a: AssetInfo): Boolean
    {
        if (transferList.contains(a)) return false
        transferList.add(a)
        return true
    }

    fun storeTokenDesc(groupId: GroupId, td: TokenDesc)
    {
        val ser = kotlinx.serialization.json.Json.encodeToString(TokenDesc.serializer(), td)
        assetManagerStorage().storeAssetFile(groupId.toHex() + ".td", ser.toByteArray())
    }

    fun getTokenDesc(chain: Blockchain, groupId: GroupId, forceReload:Boolean = false): TokenDesc
    {
        try
        {
            if (DBG_NO_ASSET_CACHE) throw Exception()
            if (forceReload) throw Exception()
            val ef = assetManagerStorage().loadAssetFile(groupId.toHex() + ".td").second
            val data = ef.openAt(0).readByteArray()
            val s = data.decodeUtf8()
            val ret = kotlinx.serialization.json.Json.decodeFromString(TokenDesc.serializer(), s)
            if (ret.genesisInfo?.height ?: 0 >= 0)  // If the data is valid in the asset file
                return ret
        } catch (e: Exception) // file not found, so grab it
        {
        }

        LogIt.info("Genesis Info for ${groupId.toHex()} not in cache")
        // first load the token description doc (TDD)
        val ec = openElectrum(chain.chainSelector)
        val tg = try
        {
            ec.getTokenGenesisInfo(groupId, 30 * 1000)
        } catch (e: ElectrumRequestTimeout)
        {
            displayError(S.ElectrumNetworkUnavailable, e.message)
            LogIt.info(sourceLoc() + ": Rostrum is inaccessible loading token info for ${groupId.toHex()}")
            TokenGenesisInfo(null, null, -1, null, null, "", "", "")
        }

        val docUrl = tg.document_url
        if (docUrl != null)
        {
            var doc: String? = null
            try
            {
                LogIt.info(sourceLoc() + ": Accessing TDD from " + docUrl)
                doc = Url(docUrl).readText(ASSET_ACCESS_TIMEOUT_MS)
            }
            catch (e: CannotLoadException)
            {
            }
            catch (e: Exception)
            {
                LogIt.info(sourceLoc() + ": Error retrieving token description document: " + e.message)
            }

            if (doc != null)  // got the TDD
            {
                val tx = ec.getTx(tg.txid)
                var addr: PayAddress? = null
                for (out in tx.outputs)
                {
                    val gi = out.script.groupInfo(out.amount)
                    if (gi != null)
                    {
                        if (gi.groupId == groupId)  // genesis of group must only produce 1 authority output so just match the groupid
                        {
                            //assert(gi.isAuthority()) // possibly but double check subgroup creation
                            addr = out.script.address
                            break
                        }
                    }
                }

                val td: TokenDesc = decodeTokenDescDoc(doc, addr)
                val tddHash = td.tddHash
                if ((tddHash != null) && (tg.document_hash == Hash256(tddHash).toHex()))
                {
                    td.genesisInfo = tg
                    storeTokenDesc(groupId, td)  // We got good data, so cache it
                    return td
                }
                else
                {
                    LogIt.info("Incorrect token desc document")
                    val tderr = TokenDesc(tg.ticker ?: "", tg.name, "token description document is invalid")
                    tderr.genesisInfo = tg
                    return tderr
                }
            }
            else  // Could not access the doc for some reason, don't cache it so we retry next time we load the token
            {
                val td = TokenDesc(tg.ticker ?: "", tg.name)
                LogIt.info("Cannot access token desc document")
                td.genesisInfo = tg
                return td
            }
        }
        else // There is no token doc, cache what we have since its everything known about this token
        {
            val td = TokenDesc(tg.ticker ?: "", tg.name)
            td.genesisInfo = tg
            storeTokenDesc(groupId, td)
            return td
        }
    }


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
        if (zipBytes == null)
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

        if (zipBytes != null)
        {
            LogIt.info("NFT file loaded for ${groupId.toStringNoPrefix()}")

            val hash = libnexa.hash256(zipBytes)
            if (groupId.subgroupData() contentEquals hash)
            {
                LogIt.info(sourceLoc() + "nft zip file matches hash for ${groupId.toStringNoPrefix()}")
            }
            else
            {
                LogIt.info(sourceLoc() + "nft zip file does not match hash for ${groupId.toStringNoPrefix()}")
                return null
            }
            val ef = EfficientFile(zipBytes)
            val nftData = nftData(ef)  // Sanity check the file
            if (nftData == null)
            {
                LogIt.info("But NOT an NFT file")
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


// TODO this should be down in the net layer
fun getElectrumServerOn(cs: ChainSelector):IpPort
{
    val prefDB = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)

    // Return our configured node if we have one
    var name = chainToURI[cs]
    val node = prefDB.getString(name + "." + CONFIGURED_NODE, null)
    if (node != null) return splitIpPort(node, DefaultElectrumTCP[cs] ?: -1)
    return ElectrumServerOn(cs)
}

// TODO this should be down in the net layer
fun openElectrum(chainSelector: ChainSelector): ElectrumClient
{
    // TODO we need to wrap all electrum access into a retrier
    // val ec = chain.net.getElectrum()

    val (svr, port) = getElectrumServerOn(chainSelector)

    val ec = try
    {
        ElectrumClient(chainSelector, svr, port, useSSL=true)
    }
    catch (e: ElectrumException) // covers java.net.ConnectException, UnknownHostException and a few others that could trigger
    {
        try  // If the port is given, it might be a p2p port so try the default electrum port
        {
            ElectrumClient(chainSelector, svr, DefaultElectrumTCP[chainSelector] ?: -1, useSSL=false)
        }
        catch (e: ElectrumException) // covers java.net.ConnectException, UnknownHostException and a few others that could trigger
        {
            try
            {
                ElectrumClient(chainSelector, svr, port, useSSL = false)
            }
            catch (e: ElectrumException)
            {
                if (chainSelector == ChainSelector.BCH)
                    ElectrumClient(chainSelector, LAST_RESORT_BCH_ELECTRS)
                else if (chainSelector == ChainSelector.NEXA)
                    ElectrumClient(chainSelector, LAST_RESORT_NEXA_ELECTRS, DEFAULT_NEXA_TCP_ELECTRUM_PORT, useSSL = false)
                else throw e
            }
        }
    }
    ec.start()
    return ec
}

