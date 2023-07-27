// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally


import android.content.*
import android.graphics.BitmapFactory
import android.graphics.drawable.PictureDrawable
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.VideoView
import androidx.core.graphics.get
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import bitcoinunlimited.libbitcoincash.*
import com.caverock.androidsvg.SVG
import info.bitcoinunlimited.www.wally.databinding.ActivityAssetsBinding
import info.bitcoinunlimited.www.wally.databinding.AssetListItemBinding
import info.bitcoinunlimited.www.wally.databinding.AssetSuccinctListItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.*
import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors
import java.util.logging.Logger
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext


private val LogIt = Logger.getLogger("BU.wally.assets")

var WallyAssetRowColors = arrayOf(0x4Ff5f8ff.toInt(), 0x4Fd0d0ef.toInt())


var DBG_NO_ASSET_CACHE = false

open class IncorrectTokenDescriptionDoc(details: String) : BUException(details, "Incorrect token description document", ErrorSeverity.Expected)

val NIFTY_ART_IP = mapOf(
  ChainSelector.NEXA to "niftyart.cash",
  ChainSelector.NEXAREGTEST to "192.168.1.5:8988"
)

fun String.runCommand(): String?
{
    throw UnimplementedException("cannot run executables on Android")
}

class AssetManager(val app: WallyApp)
{
    val transferList = mutableListOf<AssetInfo>()

    fun storeAssetFile(filename: String, data: ByteArray): String
    {
        val context = app

        val dir = context.getDir("asset", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        FileOutputStream(file).use {
            it.write(data)
        }
        return file.absolutePath
    }

    fun loadAssetFile(filename: String): Pair<String, ByteArray>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()
        val context = app
        val dir = context.getDir("asset", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        val name = file.absolutePath
        FileInputStream(file).use {
            return Pair(name,it.readBytes())
        }
    }

    fun storeCardFile(filename: String, data: ByteArray): String
    {
        val context = app

        val dir = context.getDir("card", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        FileOutputStream(file).use {
            it.write(data)
        }
        return file.absolutePath
    }

    fun loadCardFile(filename: String): Pair<String, ByteArray>
    {
        if (DBG_NO_ASSET_CACHE) throw Exception()
        val context = app
        val dir = context.getDir("card", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        val name = file.absolutePath
        FileInputStream(file).use {
            return Pair(name,it.readBytes())
        }
    }

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
        storeAssetFile(groupId.toHex() + ".td", ser.toByteArray())
    }

    fun getTokenDesc(chain: Blockchain, groupId: GroupId, forceReload:Boolean = false): TokenDesc
    {
        try
        {
            if (DBG_NO_ASSET_CACHE) throw Exception()
            if (forceReload) throw Exception()
            val data = loadAssetFile(groupId.toHex() + ".td").second
            val ret = kotlinx.serialization.json.Json.decodeFromString(TokenDesc.serializer(), String(data))
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
            app.currentActivity?.displayException(R.string.ElectrumNetworkUnavailable, e)
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
                doc = URL(docUrl).readText()
            } catch (e: java.io.FileNotFoundException)
            {
            } catch (e: Exception)
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


    fun getNftFile(td: TokenDesc?, groupId: GroupId):Pair<String,ByteArray>?
    {
        try
        {
            return loadAssetFile(groupId.toHex() + ".zip")
        }
        catch(e: Exception) // file not found
        {
            LogIt.info("NFT ${groupId.toHex()} not in cache")
        }

        var url = td?.nftUrl ?: nftUrl(td?.genesisInfo?.document_url, groupId)
        LogIt.info(sourceLoc() + ": nft URL: " + url)

        var zipBytes:ByteArray? = null
        if (url != null)
        {
            try
            {
                zipBytes = URL(url).readBytes()
            }
            catch(e:java.net.MalformedURLException)
            {
            }
            catch(e:java.net.ConnectException)
            {
            }
        }

        // Try well known locations
        if (zipBytes == null)
        {
            try
            {
                url = "https://niftyart.cash/_public/${groupId.toHex()}"
                zipBytes = URL(url).readBytes()
            }
            catch(e: Exception)
            {
            }
        }

        if (zipBytes != null)
        {
            val zf = ZipInputStream(ByteArrayInputStream(zipBytes))
            // Just try to go thru the zip dir to see if its basically a valid file
            val files = generateSequence { zf.nextEntry }.map { it.name }.toList()
            LogIt.info(sourceLoc() + ": nft zip contents " + files.joinToString(" "))
            val hash = Hash.hash256(zipBytes)
            if (groupId.subgroupData() contentEquals  hash)
            {
                storeAssetFile(groupId.toHex() + ".zip", zipBytes)
                return Pair(url!!, zipBytes)
            }
            else
            {
                LogIt.info(sourceLoc() + ": nft zip file does not match hash")
            }
        }
        return null
    }

}

fun openElectrum(chainSelector: ChainSelector): ElectrumClient
{
    // TODO we need to wrap all electrum access into a retrier
    // val ec = chain.net.getElectrum()

    val (svr, port) = wallyApp!!.getElectrumServerOn(chainSelector)

    val ec = try
    {
        ElectrumClient(chainSelector, svr, port, useSSL=true)
    }
    catch (e: java.io.IOException) // covers java.net.ConnectException, UnknownHostException and a few others that could trigger
    {
        try  // If the port is given, it might be a p2p port so try the default electrum port
        {
            ElectrumClient(chainSelector, svr, DefaultElectrumTCP[chainSelector] ?: -1, useSSL=false)
        }
        catch (e: java.io.IOException) // covers java.net.ConnectException, UnknownHostException and a few others that could trigger
        {
            try
            {
                ElectrumClient(chainSelector, svr, port, useSSL = false)
            }
            catch (e: java.io.IOException)
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

/** shows this image in the passed imageview.  Returns true if imageView chosen, else false */
fun showMedia(iui: ImageView, vui: VideoView?, uri: Uri?, bytes: ByteArray? = null): Boolean
{
    if (uri == null)
    {
        iui.setImageDrawable(null)
        return true
    }
    if (bytes == null)  throw UnimplementedException("load from uri")
    val name = uri.toString().lowercase()

    if (name.endsWith(".svg", true))
    {
        val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
        val drawable = PictureDrawable(svg.renderToPicture())
        iui.setImageDrawable(drawable)
        iui.visibility=View.VISIBLE
        vui?.visibility=View.GONE
        return true
    }
    else if (name.endsWith(".jpg", true) ||
      name.endsWith(".jpeg", true) ||
      name.endsWith(".png", true) ||
      name.endsWith(".webp",true) ||
      name.endsWith(".gif",true) ||
      name.endsWith(".heic",true) ||
      name.endsWith(".heif",true)
    )
    {
        val bmp = BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
        iui.setImageBitmap(bmp)
        iui.visibility=View.VISIBLE
        vui?.visibility=View.GONE
        return true
    }
    else
    {
        if ((vui != null) && (name.endsWith(".mp4", true) ||
          name.endsWith(".webm", true) ||
          name.endsWith(".3gp", true) ||
          name.endsWith(".mkv", true)))
        {
            LogIt.info("Video URI: ${uri.toString()}")
            vui.setVideoPath(uri.toString())
            vui.start()
            iui.visibility=View.INVISIBLE  // Can't be GONE because other stuff is positioned against it
            vui?.visibility=View.VISIBLE
            return false
        }
        else
        {
            // TODO: pick a better cannot load image
            iui.setImageResource(R.drawable.asset_cannot_show_icon)
            iui.visibility=View.VISIBLE
            vui?.visibility=View.GONE
            return true
        }
    }
}


class AssetInfo(val groupInfo: GroupInfo)
{
    var name:String? = null
    var ticker:String? = null
    var genesisHeight:Long = 0
    var genesisTxidem: Hash256? = null
    var docHash: Hash256? = null
    var docUrl: String? = null
    var tokenInfo: TokenDesc? = null
    var iconBytes: ByteArray? = null
    var iconUri: Uri? = null
    var iconBackBytes: ByteArray? = null
    var iconBackUri: Uri? = null

    var nft: NexaNFTv2? = null

    var publicMediaCache: String? = null
    var publicMediaBytes: ByteArray? = null
    var ownerMediaCache: String? = null
    var ownerMediaBytes: ByteArray? = null

    // Connection to the UI if shown on screen
    var ui:AssetBinder? = null
    var sui:AssetSuccinctBinder? = null

    // display this amount if set (for quantity selection during send)
    var displayAmount: Long? = null

    var account: Account? = null

    /** Get the file associated with this NFT (if this is an NFT), or null
     * This function may retrieve this data from a local cache or remotely.
     * This is a function rather than a val with a getter to emphasize that this might be an expensive operation
     * */
    fun nftFile(am: AssetManager):Pair<String,ByteArray>?
    {
        // TODO: cache both in RAM and on disk
        return am.getNftFile(tokenInfo, groupInfo.groupId)
    }


    fun extractNftData(am: AssetManager, grpId: GroupId, nftZip:ByteArray)
    {
        // grab image from zip file
        val (fname, data) = nftCardFront(nftZip)
        if (fname != null)
        {
            iconBytes = data
            // Note caching is NEEDED to show video (because videoview can only take a file)
            if (data != null) iconUri = Uri.parse(am.storeCardFile(grpId.toHex() + fname, data))
            else iconUri = Uri.parse(fname)
        }
        val (bname, bdata) = nftCardBack(nftZip)
        if (bname != null)
        {
            iconBackBytes = bdata
            // Note caching is NEEDED to show video (because videoview can only take a file)
            if (bdata != null) iconBackUri = Uri.parse(am.storeCardFile(grpId.toHex() + bname, bdata))
            else iconBackUri = Uri.parse(bname)
        }

        // grab NFT text data
        val nfti = nftData(nftZip)
        if (nfti != null)
        {
            nft = nfti
        }
    }

    /** returns the Uri and the bytes, or null if nonexistent, cannot be loaded */
    fun getTddIcon(): Pair<Uri?, ByteArray?>
    {
        val iconUrl = tokenInfo?.icon
        if (iconUrl != null)
        {
            try
            {
                val data = URL(iconUrl).readBytes()
                return Pair(Uri.parse(URL(iconUrl).toString()), data)
            }
            catch (e: java.net.MalformedURLException)
            {
                try
                {
                    val data = URI(docUrl).resolve(iconUrl).toURL().readBytes()
                    return Pair(Uri.parse(URI(docUrl).resolve(iconUrl).toString()), data)
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
        synchronized(this)
        {
            var tg = td.genesisInfo
            var dataChanged = false

            if (tg == null)
            {
                td = am.getTokenDesc(chain, groupInfo.groupId, true)
                tg = td.genesisInfo
                if (tg == null) return // can't load
            }

            LogIt.info(sourceLoc() + chain.name + ": loaded: " + tg.name)

            name = tg.name
            ticker = tg.ticker
            genesisHeight = tg.height
            genesisTxidem = if (tg.txidem.length > 0) Hash256(tg.txidem) else null

            docUrl = tg.document_url

            if (docUrl != null)
            {
                // Ok find the NFT description
                val nftZipData = am.getNftFile(td, groupInfo.groupId)
                if (nftZipData != null)
                {
                    tokenInfo = td
                    if (td.marketUri == null)
                    {
                        val u: URI = URI(nftZipData.first)
                        if (u.isAbsolute)  // This is a real URI, not a local path
                        {
                            td.marketUri = u.resolve("/token/" + groupInfo.groupId.toHex()).toString()
                            am.storeTokenDesc(groupInfo.groupId, td)
                        }
                        else
                        {
                            td.marketUri = URI(docUrl).resolve("/token/" + groupInfo.groupId.toHex()).toString()
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
                    td.marketUri = URI(docUrl).resolve("/token/" + groupInfo.groupId.toHex()).toString()
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
                        val u: URI = URI(nftZipData.first)
                        if (u.isAbsolute)  // This is a real URI, not a local path
                        {
                            td.marketUri = u.resolve("/token/" + groupInfo.groupId.toHex()).toString()
                            am.storeTokenDesc(groupInfo.groupId, td)
                        }
                    }
                    extractNftData(am, groupInfo.groupId, nftZipData.second)
                    dataChanged = true
                }
            }

            laterUI {
                if (dataChanged)
                {
                    ui?.repopulate()
                    sui?.repopulate()
                }
            }
        }
    }

}




class AssetSuccinctBinder(val ui: AssetSuccinctListItemBinding, val activity: CommonNavActivity): GuiListItemBinder<AssetInfo>(ui.root)
{
    var showFront = true
    // Fill the view with this data
    override fun populate()
    {
        LogIt.info(sourceLoc() + "populate: " + (data?.groupInfo?.groupId?.toString() ?: ""))

        // Loop any video that might be playing
        ui.GuiAssetVideoIcon.setOnPreparedListener(OnPreparedListener { mp -> mp.isLooping = true })

        // If this video cannot be played, do not do an ugly popup -- just switch to the cannot show icon
        ui.GuiAssetVideoIcon.setOnErrorListener(object : MediaPlayer.OnErrorListener
        {
            override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean
            {
                mp?.stop()
                ui.GuiAssetVideoIcon.visibility = View.GONE
                ui.GuiAssetIcon.visibility = View.VISIBLE
                ui.GuiAssetIcon.setImageResource(R.drawable.asset_cannot_show_icon)
                return true
            }
        })


        if (true)
        {
            val d = data
            if (d != null)
            {
                d.sui = this

                val nft = d.nft
                if (nft == null)
                {
                    ui.GuiAssetName.text = d.ticker ?: d.name
                    ui.GuiAssetQuantity.text = d.displayAmount?.toString() ?: d.groupInfo.tokenAmt.toString()
                    ui.GuiAssetQuantity.visibility = View.VISIBLE
                }
                else
                {
                    ui.GuiAssetName.text = nft.title ?: d.name ?: ""
                    if (d.groupInfo.tokenAmt == 1L)  // If its an NFT and there's just 1 (remember SFTs could have > 1) then don't bother to show quantity
                    {
                        ui.GuiAssetQuantity.visibility = View.GONE
                    }
                    else
                    {
                        ui.GuiAssetQuantity.visibility = View.VISIBLE
                        ui.GuiAssetQuantity.text = d.displayAmount?.toString() ?: d.groupInfo.tokenAmt.toString()
                    }
                }

                if (showFront)
                {
                    showMedia(ui.GuiAssetIcon, ui.GuiAssetVideoIcon, d.iconUri, d.iconBytes)
                }
                else
                {
                    showMedia(ui.GuiAssetIcon, ui.GuiAssetVideoIcon, d.iconBackUri, d.iconBackBytes)
                }

                ui.GuiAssetIcon.setOnClickListener() {
                    showFront = !showFront
                    if (showFront)
                    {
                        showMedia(ui.GuiAssetIcon, ui.GuiAssetVideoIcon, d.iconUri, d.iconBytes)
                    }
                    else
                    {
                        showMedia(ui.GuiAssetIcon, ui.GuiAssetVideoIcon, d.iconBackUri, d.iconBackBytes)
                    }
                }
            }
            else
            {
                ui.GuiAssetQuantity.text = ""
                ui.GuiAssetIcon.setImageResource(0)
                ui.GuiAssetName.text = ""
                ui.GuiAssetEditQuantity.set("")
            }
        }

        // Allows editing the quantity (for sending partial amounts)
        ui.GuiAssetQuantity.setOnClickListener {
            val d = data ?: return@setOnClickListener
            ui.GuiAssetQuantity.visibility = View.INVISIBLE
            if (d.displayAmount == null) d.displayAmount = 1
            ui.GuiAssetEditQuantity.set((d.displayAmount ?: 1L).toString())
            ui.GuiAssetEditQuantity.visibility = View.VISIBLE
        }

        ui.GuiAssetEditQuantity.setOnFocusChangeListener(object: View.OnFocusChangeListener
        {
            override fun onFocusChange(view: View?, hasFocus: Boolean)
            {
                if (hasFocus)
                {
                    activity.setVisibleSoftKeys(SoftKey.ALL or SoftKey.THOUSAND or SoftKey.MILLION or SoftKey.CLEAR)
                    activity.showKeyboard()
                }
                else
                {
                    try
                    {
                        val d = data ?: return
                        ui.GuiAssetQuantity.visibility = View.VISIBLE
                        ui.GuiAssetEditQuantity.visibility = View.INVISIBLE
                        val s = ui.GuiAssetEditQuantity.text.toString().lowercase()
                        var v = if (s == "all") d.groupInfo.tokenAmt else ui.GuiAssetEditQuantity.text.toString().toLong()
                        if (v > d.groupInfo.tokenAmt)
                            activity.displayNotice(R.string.moreThanAvailable)
                        else
                        {
                            if (v < 0L)
                            {
                                LogIt.info(sourceLoc() + ": Bad token amount $v")
                                activity.displayNotice(R.string.badAmount)
                            }
                        }

                        if (v == 0L)
                        {
                            // TODO remove this element from the list
                        }

                        ui.GuiAssetQuantity.text = v.toString()
                        ui.GuiAssetEditQuantity.set("")  // Set this to empty so its size collapses back to its minimum
                    }
                    catch (e: Exception)
                    {
                        handleThreadException(e)
                        activity.displayNotice(R.string.badAmount)
                    }
                }
            }
        })

        ui.GuiAssetEditQuantity.doAfterTextChanged {
            // remember that this is called even if the change happens programatically
            val d = data ?: return@doAfterTextChanged
            try
            {
                activity.finishShowingNotice()
                val s = ui.GuiAssetEditQuantity.text.toString().lowercase()
                if (s.length == 0) return@doAfterTextChanged
                var v = if (s == "all") d.groupInfo.tokenAmt else ui.GuiAssetEditQuantity.text.toString().toLong()
                if (v > d.groupInfo.tokenAmt)
                    activity.displayNotice(R.string.moreThanAvailable)
                else if (v < 0L) activity.displayNotice(R.string.badAmount)
                else
                    {
                        ui.GuiAssetQuantity.text = v.toString()
                        d.displayAmount = v
                    }
            }
            catch (e: Exception)
            {
                handleThreadException(e)
            }
        }

    }

    fun repopulate()
    {
        laterUI { populate() }
    }

    override fun unpopulate()
    {
        data?.ui = null
        ui.GuiAssetVideoIcon.visibility = View.GONE
        ui.GuiAssetIcon.visibility = View.INVISIBLE
        ui.GuiAssetVideoIcon.stopPlayback()
        ui.GuiAssetIcon.setImageResource(0)
        super.unpopulate()
    }

    override fun onClick(v: View)
    {
        super.onClick(v)
    }
}

class AssetBinder(val ui: AssetListItemBinding, val activity: AssetsActivity): GuiListItemBinder<AssetInfo>(ui.root)
{
    var showFront = true
    var mediaIsImage = true
    // Fill the view with this data
    override fun populate()
    {
        LogIt.info(sourceLoc() + "populate: " + (data?.groupInfo?.groupId?.toString() ?: ""))

        // Loop any video that might be playing
        ui.GuiAssetVideoIcon.setOnPreparedListener(OnPreparedListener { mp -> mp.isLooping = true })

        // If this video cannot be played, do not do an ugly popup -- just switch to the cannot show icon
        ui.GuiAssetVideoIcon.setOnErrorListener(object : MediaPlayer.OnErrorListener {
            override fun onError( mp: MediaPlayer?,  what: Int,  extra: Int): Boolean {
                // mp?.stop()
                ui.GuiAssetVideoIcon.visibility = View.GONE
                ui.GuiAssetIcon.visibility = View.VISIBLE
                ui.GuiAssetIcon.setImageResource(R.drawable.asset_cannot_show_icon)
                return true
            }
        })

        val d = data
        if (d != null)
        {
            synchronized(d)
            {
                d.ui = this
                ui.GuiAssetId.text = d.groupInfo.groupId.toString()
                ui.GuiAssetId.visibility = if (devMode) View.VISIBLE else View.GONE

                val nft = d.nft
                if (nft == null)
                {
                    ui.GuiAssetId.text = d.groupInfo.groupId.toString()
                    ui.GuiAssetName.text = d.name ?: ""
                    var tmp = BigDecimal(d.groupInfo.tokenAmt).setScale(d.tokenInfo?.genesisInfo?.decimal_places ?: 0)
                    tmp = tmp/(BigDecimal(10).pow(d.tokenInfo?.genesisInfo?.decimal_places ?: 0))
                    ui.GuiAssetQuantity.text = tmp.toString()
                    ui.GuiAssetQuantity.visibility = View.VISIBLE
                    ui.GuiAssetAuthor.visibility = View.GONE
                    ui.GuiAssetSeries.visibility = View.GONE
                }
                else
                {
                    ui.GuiAssetName.text = nft.title ?: d.name ?: ""
                    if (d.groupInfo.tokenAmt == 1L)  // If its an NFT and there's just 1 (remember SFTs could have > 1) then don't bother to show quantity
                    {
                        ui.GuiAssetQuantity.visibility = View.INVISIBLE
                    }
                    else
                    {
                        ui.GuiAssetQuantity.visibility = View.VISIBLE
                        var tmp = BigDecimal(d.groupInfo.tokenAmt).setScale(d.tokenInfo?.genesisInfo?.decimal_places ?: 0)
                        tmp = tmp/(BigDecimal(10).pow(d.tokenInfo?.genesisInfo?.decimal_places ?: 0))
                        ui.GuiAssetQuantity.text = tmp.toString()
                    }

                    if ((nft.author != null) && (nft.author.length > 0))
                    {
                        ui.GuiAssetAuthor.text = i18n(R.string.NftAuthor) % mapOf("author" to nft.author)
                        ui.GuiAssetAuthor.visibility = View.VISIBLE
                    }
                    else ui.GuiAssetAuthor.visibility = View.GONE
                    if (nft.series != null)
                    {
                        ui.GuiAssetSeries.text = i18n(R.string.NftSeries) % mapOf("series" to nft.series)
                        ui.GuiAssetSeries.visibility = View.VISIBLE
                    }
                    else ui.GuiAssetSeries.visibility = View.GONE
                }

                if ((showFront) || (d.iconBackBytes == null))
                {
                    mediaIsImage = showMedia(ui.GuiAssetIcon,ui.GuiAssetVideoIcon, d.iconUri, d.iconBytes)
                }
                else
                {
                    mediaIsImage = showMedia(ui.GuiAssetIcon,ui.GuiAssetVideoIcon, d.iconBackUri, d.iconBackBytes)
                }

                ui.GuiAssetIcon.setOnClickListener() {
                    showFront = !showFront
                    if ((showFront) || (d.iconBackBytes == null))
                    {
                        mediaIsImage = showMedia(ui.GuiAssetIcon, ui.GuiAssetVideoIcon, d.iconUri, d.iconBytes)
                    }
                    else
                    {
                        mediaIsImage = showMedia(ui.GuiAssetIcon, ui.GuiAssetVideoIcon, d.iconBackUri, d.iconBackBytes)
                    }
                }
            }
        }
        else
        {
            ui.GuiAssetVideoIcon.visibility = View.GONE
            ui.GuiAssetIcon.visibility = View.INVISIBLE
            // ui.GuiAssetVideoIcon.stopPlayback()
            ui.GuiAssetIcon.setImageResource(0)
            ui.GuiAssetSeries.text = ""
            ui.GuiAssetAuthor.text = ""
            ui.GuiAssetQuantity.text = ""
            ui.GuiAssetId.text=""
            ui.GuiAssetName.text=""
        }

    }

    fun repopulate()
    {
        laterUI {
            LogIt.info("repopulate $this")
            populate()
        }
    }

    override fun unpopulate()
    {
        LogIt.info("unpopulate $this")
        data?.ui = null
        ui.GuiAssetVideoIcon.visibility = View.GONE
        ui.GuiAssetIcon.visibility = View.INVISIBLE
        //ui.GuiAssetVideoIcon.stopPlayback()
        ui.GuiAssetIcon.setImageResource(0)
        super.unpopulate()
    }

    override fun onView(attached: Boolean)
    {
        LogIt.info("onView $attached $this")
        if (attached)
        {
            if (mediaIsImage) ui.GuiAssetIcon.visibility = View.VISIBLE
            else
            {
                ui.GuiAssetVideoIcon.visibility = View.VISIBLE
                //ui.GuiAssetVideoIcon.resume()
                ui.GuiAssetVideoIcon.start()

                /*
                ui.GuiAssetVideoIcon.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder)
                    {
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int)
                    {
                        TODO("Not yet implemented")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder)
                    {
                        TODO("Not yet implemented")
                    }
                })

                 */
            }
        }
        else
        {
            if (mediaIsImage) ui.GuiAssetIcon.visibility = View.INVISIBLE
            else
            {
                ui.GuiAssetVideoIcon.visibility = View.GONE
                //ui.GuiAssetVideoIcon.pause()
                ui.GuiAssetVideoIcon.stopPlayback()
            }

        }
    }

    override fun onClick(v: View)
    {
        super.onClick(v)
        activity.showDetails(pos, data, v.height)
    }
}


class AssetsActivity : CommonNavActivity()
{
    protected val coCtxt: CoroutineContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    protected val coScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    private lateinit var ui: ActivityAssetsBinding
    lateinit var assetsLayoutManager: LinearLayoutManager
    override var navActivityId = R.id.navigation_assets
    var listHeight: Int = 0
    var navHeight: Int = 0

    var account: Account? = null
    var accountIdx = -1
    var currentlyViewing = -1
    var asset: AssetInfo? = null

    lateinit var adapter: GuiList<AssetInfo, AssetBinder>

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityAssetsBinding.inflate(layoutInflater)
        setContentView(ui.root)
        assetsLayoutManager = LinearLayoutManager(this)
        ui.GuiAssetList.layoutManager = assetsLayoutManager

        enableMenu(this, SHOW_ASSETS_PREF)  // If you ever drop into this activity, show it in the menu

        ui.GuiAssetImage.setOnClickListener { onMediaClicked() }
        ui.GuiAssetVideo.setOnClickListener { onMediaClicked() }
        ui.GuiAssetWeb.setOnClickListener { onMediaClicked() }
        ui.GuiAssetMediaRole.setOnClickListener { onMediaClicked() }

        ui.GuiNftCardFrontButton.setOnClickListener { showCardFront() }
        ui.GuiNftPublicButton.setOnClickListener { showPublicMedia() }
        ui.GuiNftOwnerButton.setOnClickListener { showOwnerMedia() }
        ui.GuiNftCardBackButton.setOnClickListener { showCardBack() }
        ui.GuiNftInfo.setOnClickListener { showInfo() }
        ui.GuiNftLegal.setOnClickListener { showLicense() }

        ui.GuiAssetInvoke.setOnClickListener { onInvokeButton() }
        ui.GuiAssetTrade.setOnClickListener { onTradeButton() }
        ui.GuiAssetSend.setOnClickListener { onSendButton() }
        ui.GuiAssetCopyToClipboard.setOnClickListener { onCopyToClipboardButton() }

        ui.GuiAssetVideoBox.setOnPreparedListener(object: OnPreparedListener {
            override fun onPrepared(mp: MediaPlayer?)
            {
                mp?.setLooping(true)
            }
        })

        ui.root.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener
        {
            override fun onGlobalLayout()
            {
                listHeight = max(listHeight, ui.GuiAssetList.measuredHeight)
                navHeight = max(navHeight, ui.navView.measuredHeight)
            }
        })



        laterUI {
            wallyApp?.let { app ->
                if (app !is WallyApp)
                {
                    finish()
                }
                else
                {
                    try
                    {
                        val acc = wallyApp?.focusedAccount ?: wallyApp?.primaryAccount
                        updateAccount(acc)
                    }
                    catch(e: PrimaryWalletInvalidException)
                    {
                        LogIt.info(sourceLoc() + "No focused or primary account")
                        wallyApp?.displayError(R.string.NoAccounts)
                        finish()
                    }
                }
            }
            account?.let { setTitle(i18n(R.string.title_activity_assets) + ": " + it.name) }
        }
    }

    override fun onBackPressed()
    {
        if (ui.GuiAssetDetail.visibility == View.VISIBLE) closeDetails()
        else super.onBackPressed()
    }

    fun constructAssetList(acc: Account): List<AssetInfo>
    {
        LogIt.info(sourceLoc() + acc.name + ": Construct assets")
        val ast = mutableMapOf<GroupId, AssetInfo>()
        for (txo in acc.wallet.txos)
        {
            val sp = txo.value
            if (sp.isUnspent)
            {
                val grp = sp.groupInfo()

                if (grp != null)
                {
                    LogIt.info(sourceLoc() + acc.name + ": unspent asset ${grp.groupId.toHex()}")
                    if (!grp.isAuthority())  // TODO not dealing with authority txos in Wally mobile
                    {
                        val tmp = grp.tokenAmt  // Set the tokenAmt to 0 and than add it back in once we grab or create the AssetInfo
                        grp.tokenAmt = 0
                        val ai: AssetInfo = ast[grp.groupId] ?: AssetInfo(grp)
                        ai.groupInfo.tokenAmt += tmp
                        ai.account = acc
                        ast[grp.groupId] = ai
                    }
                }
            }
        }

        // Start grabbing the data for all assets (asynchronously)
        for (asset in ast.values)
        {
            laterAssets {
                asset.load(acc.wallet.blockchain, wallyApp!!.assetManager)
            }
        }
        return ast.values.toList()
    }

    /** Do whatever you pass but not within the user interface context, asynchronously */
    fun laterAssets(fn: suspend () -> Unit): Unit
    {
        coScope.launch(coCtxt) {
            try
            {
                fn()
            } catch (e: Exception) // Uncaught exceptions will end the app
            {
                LogIt.info(sourceLoc() + ": General exception handler (should be caught earlier!)")
                handleThreadException(e)
            }
        }
    }

    fun onMediaClicked()
    {

    }

    fun onSendButton()
    {
        val a = asset
        if (a!=null)
        {
            a.displayAmount = 1  // The default send is to transfer a single one (for safety)
            if (wallyApp!!.assetManager.addAssetToTransferList(a))
            {
                displayNotice(R.string.AssetAddedToTransferList)
            }
            else
            {
                // Already on list
            }
        }
    }

    fun onCopyToClipboardButton()
    {
        val a = asset
        if (a!=null)
        {
            var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            var clip = ClipData.newPlainText("text", a.groupInfo.groupId.toString())
            clipboard.setPrimaryClip(clip)
            if (android.os.Build.VERSION.SDK_INT <= 32)  // system toasts above this version
                displayNotice(R.string.copiedToClipboard)
        }
    }


    fun onTradeButton()
    {
        val a = asset
        if (a!=null)
        {
            try
            {
                val market = a.tokenInfo?.marketUri
                if (market != null)
                {
                    // TODO create Challenge Transaction proof-of-ownership
                    val uri = Uri.parse(market)
                    val app = wallyApp
                    var cnxn:LongPollInfo? = null
                    if (app != null)
                    {
                        val cnxns = app.accessHandler
                        val h = uri.host
                        if (h != null)
                            cnxn = cnxns.activeTo(h)
                    }

                    if (cnxn != null && cnxn.active)
                    {
                        displayNotice(R.string.IssuedToConnection)
                        val c = cnxn
                        later {  // Can't do network in UI thread
                            val connectedUrl = URL(URL(c.proto + "://" + c.hostPort + "/"), uri.path + "?cookie=${c.cookie}")
                            try
                            {
                                val result = connectedUrl.readText()
                                LogIt.info(sourceLoc() + "read: ${connectedUrl} ->\n${result.toString()}")
                            }
                            catch (e: Exception)
                            {
                                // Trigger activity launch
                                val intent: Intent = Intent(Intent.ACTION_VIEW, uri)
                                intent.putExtra("tokenid", a.groupInfo.groupId.toHex())
                                startActivity(intent)
                            }
                        }
                    }
                    else
                    {
                        val intent: Intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.putExtra("tokenid", a.groupInfo.groupId.toHex())
                        startActivity(intent)
                    }
                }
            }
            catch(e: ActivityNotFoundException)
            {
                LogIt.info("asset marketplace activity not found: ${a.tokenInfo?.marketUri}")
            }
        }
    }

    fun onInvokeButton()
    {
        val a = asset
        if (a!=null)
        {
            var appuri = a.nft?.appuri

            if (appuri != null && appuri.length > 0)
            {
                if (!appuri.contains(":")) appuri = "http://" + appuri
                LogIt.info("launching " + appuri)

                // TODO create Challenge Transaction proof-of-ownership

                if (appuri.lowercase().startsWith("http"))
                {
                    if (appuri.contains("?")) appuri = appuri + "&" + "tokenid=" + a.groupInfo.groupId.toHex()
                    else appuri = appuri + "?" + "tokenid=" + a.groupInfo.groupId.toHex()
                }

                val uri = Uri.parse(appuri)
                val intent: Intent = Intent(Intent.ACTION_VIEW, uri)
                intent.putExtra("tokenid", a.groupInfo.groupId.toHex())
                startActivity(intent)
            }
        }
    }

    fun closeDetails()
    {
        finishShowingNotice()
        ui.GuiAssetDetail.visibility = View.GONE
        ui.GuiAssetList.layoutParams.height = listHeight
        ui.GuiAssetList.requestLayout()
        ui.GuiAssetList.invalidate()
        ui.container.requestLayout()
        assetsLayoutManager.requestLayout()
        return
    }

    fun showDetails(pos:Int, a: AssetInfo?, itemHeight: Int)
    {
        // close
        if ((a == null) || ((ui.GuiAssetDetail.visibility == View.VISIBLE)&&(currentlyViewing == pos)))
        {
            closeDetails()
            return
        }

        val heightButOne = listHeight - itemHeight
        assetsLayoutManager.scrollToPositionWithOffset(pos, 0)
        ui.GuiAssetList.layoutParams.height = itemHeight
        ui.GuiAssetList.requestLayout()
        ui.GuiAssetList.invalidate()
        ui.GuiAssetDetail.visibility = View.VISIBLE
        ui.GuiAssetDetail.layoutParams.height = heightButOne
        ui.GuiAssetDetail.requestLayout()
        ui.container.requestLayout()


        asset = a
        currentlyViewing = pos

        val nftDetail = a.nft
        if (nftDetail != null)
        {
            if (nftDetail.appuri != "")
                ui.GuiAssetInvoke.visibility = View.VISIBLE
            else ui.GuiAssetInvoke.visibility = View.GONE
        }
        else
        {
            ui.GuiAssetInvoke.visibility = View.GONE
        }

        ui.GuiAssetTrade.visOrGone(a.tokenInfo?.marketUri != null)

        showCardFront()

        // Cache any large NFT files, and once we have inventoried what's available show the buttons
        later {
            val nftZipData = a.nftFile(wallyApp!!.assetManager)
            if (nftZipData != null)
            {
                val nftZip = nftZipData.second
                if (true)
                {
                    val (uriStr, b) = cacheNftMedia(a.groupInfo.groupId, nftPublicMedia(nftZip))
                    if (uriStr != null)
                    {
                        a.publicMediaCache = uriStr
                        a.publicMediaBytes = b
                    }
                }
                if (true)
                {
                    val (uriStr, b) = cacheNftMedia(a.groupInfo.groupId, nftOwnerMedia(nftZip))
                    if (uriStr != null)
                    {
                        a.ownerMediaCache = uriStr
                        a.ownerMediaBytes = b
                    }
                }
            }
            laterUI { showAvailableCardButtons() }
        }
    }


    fun cacheNftMedia(groupId: GroupId, media: Pair<String?, ByteArray?>): Pair<String?, ByteArray?>
    {
        val cacheDir = wallyApp!!.cacheDir
        var uriStr = media.first
        var b = media.second
        if (b != null)
        {
            if ((b.size > 10000000) || (uriStr!=null && isVideo(uriStr)))
            {
                val result = canonicalSplitExtension(uriStr)
                if (result == null) return Pair(uriStr, b)  // never going to happen because uriStr != null
                val (fnoext, ext) = result
                File.createTempFile(groupId.toHex() + "_" + fnoext, ext, cacheDir)
                val f = File(cacheDir, groupId.toHex() + "_" + fnoext + "." + ext)
                uriStr = f.absolutePath
                f.writeBytes(b)
                b = null  // We want to load this from cache file so don't save the bytes
            }
        }
        return Pair(uriStr, b)
    }


    fun showAvailableCardButtons()
    {
        val a = asset ?: return
        ui.GuiNftCardFrontButton.visOrGone(a.iconUri != null)
        ui.GuiNftCardBackButton.visOrGone(a.iconBackUri != null)
        ui.GuiNftPublicButton.visOrGone(a.publicMediaCache != null)
        ui.GuiNftOwnerButton.visOrGone(a.ownerMediaCache != null)
        ui.GuiNftInfo.visOrGone((a.nft?.info ?: "") != "")
        ui.GuiNftLegal.visOrGone((a.nft?.license ?: "") != "")
    }
    fun showInfo()
    {
        val a = asset ?: return
        val nft = a.nft ?: return
        ui.GuiAssetVideo.visibility = View.GONE
        ui.GuiAssetWeb.visibility = View.VISIBLE
        ui.GuiAssetImage.visibility = View.GONE
        ui.GuiAssetWebBox.settings.javaScriptEnabled = true
        ui.GuiAssetWebBox.settings.allowFileAccess = false
        ui.GuiAssetWebBox.settings.blockNetworkLoads = false
        ui.GuiAssetWebBox.settings.blockNetworkImage = false
        ui.GuiAssetWebBox.loadData(nft.info ?: i18n(R.string.NftNoInfoProvidedHTML),"text/html; charset=utf-8", "utf-8")
        ui.GuiAssetMediaRole.text = i18n(R.string.NftInfo)
    }

    fun showLicense()
    {
        val a = asset ?: return
        val nft = a.nft ?: return
        ui.GuiAssetVideo.visibility = View.GONE
        ui.GuiAssetWeb.visibility = View.VISIBLE
        ui.GuiAssetImage.visibility = View.GONE
        ui.GuiAssetWebBox.settings.javaScriptEnabled = true
        ui.GuiAssetWebBox.settings.allowFileAccess = false
        ui.GuiAssetWebBox.settings.blockNetworkLoads = false
        ui.GuiAssetWebBox.settings.blockNetworkImage = false
        ui.GuiAssetWebBox.loadData(nft.license ?: i18n(R.string.NftNoInfoProvidedHTML),"text/html; charset=utf-8", "utf-8")
        ui.GuiAssetMediaRole.text = i18n(R.string.NftLegal)
    }


    fun showCardFront()
    {
        val a = asset ?: return
        showDetailMediaUri(a.iconUri ?: null, a.iconBytes)
        ui.GuiAssetMediaRole.text = i18n(R.string.NftCardFront)
    }

    fun showPublicMedia()
    {
        val a = asset ?: return
        ui.GuiAssetMediaRole.text = i18n(R.string.NftPublicMedia)
        if (a.publicMediaCache != null) showDetailMedia(a.publicMediaCache, a.publicMediaBytes)
        else
        {
            later {
                val nftZipData = a.nftFile(wallyApp!!.assetManager)
                if (nftZipData == null) {  }
                else
                {
                    val (uriStr, b) = cacheNftMedia(a.groupInfo.groupId, nftPublicMedia(nftZipData.second))
                    if (uriStr != null)
                    {
                        a.publicMediaCache = uriStr
                        a.publicMediaBytes = b
                        laterUI {
                            showDetailMedia(uriStr, b)
                        }
                    }
                }
            }
        }

    }

    fun showOwnerMedia()
    {
        val a = asset ?: return
        ui.GuiAssetMediaRole.text = i18n(R.string.NftOwnerMedia)
        if (a.ownerMediaCache != null) showDetailMedia(a.ownerMediaCache, a.ownerMediaBytes)
        else
        {

            later {
                val nftZip = a.nftFile(wallyApp!!.assetManager)
                if (nftZip == null) {  }
                else
                {
                    val (uriStr, b) = cacheNftMedia(a.groupInfo.groupId, nftOwnerMedia(nftZip.second))
                    if (uriStr != null)
                    {
                        a.ownerMediaCache = uriStr
                        a.ownerMediaBytes = b
                        laterUI {
                            showDetailMedia(uriStr, b)
                        }
                    }
                }
            }
        }
    }

    fun showCardBack()
    {
        val a = asset ?: return
        if (a.iconBackUri != null)
        {
            showDetailMediaUri(a.iconBackUri ?: null, a.iconBackBytes)
            ui.GuiAssetMediaRole.text = i18n(R.string.NftCardBack)
            return
        }
        else {  }
    }


    fun showDetailMediaUri(uri: Uri?, bytes: ByteArray? = null): ByteArray? = showDetailMedia(uri.toString().lowercase(), bytes)

    fun showDetailMedia(name: String?, bytes: ByteArray? = null): ByteArray?
    {
        ui.GuiAssetVideo.visibility = View.GONE
        ui.GuiAssetWeb.visibility = View.GONE
        ui.GuiAssetImage.visibility = View.GONE
        if (name == null)
        {
            return null
        }
        if (bytes == null && name.startsWith("http"))  throw UnimplementedException("load from uri")

        if (name.endsWith(".svg", true))
        {
            val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
            val svgp = svg.renderToPicture()
            val drawable = PictureDrawable(svgp)
            ui.GuiAssetImageBox.setImageDrawable(drawable)
            ui.GuiAssetImage.visibility=View.VISIBLE
        }
        else if (name.endsWith(".jpg", true) ||
          name.endsWith(".jpeg", true) ||
          name.endsWith(".png", true) ||
          name.endsWith(".webp",true) ||
          name.endsWith(".gif",true) ||
          name.endsWith(".heic",true) ||
          name.endsWith(".heif",true)
        )
        {
            val bmp = BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
            ui.GuiAssetImageBorder.visibility=View.VISIBLE
            if (bmp.hasAlpha())  // If the image has an alpha channel and some transparent pixels, don't show a border
            {
                if ((bmp.get(0,0) ushr 24 != 0xFF) ||
                  (bmp.get(bmp.width - 1,0) ushr 24 != 0xFF) ||
                  (bmp.get(bmp.width - 1,bmp.height-1) ushr 24 != 0xFF) ||
                  (bmp.get(0,bmp.height-1) ushr 24 != 0xFF))
                    ui.GuiAssetImageBorder.visibility=View.GONE
            }
            ui.GuiAssetImageBox.setImageBitmap(bmp)
            ui.GuiAssetImage.visibility=View.VISIBLE
            laterUI {
                // ui.GuiAssetImageBorder.layoutParams.height = ui.GuiAssetImageBox.layoutParams.height
                // ui.GuiAssetImageBorder.layoutParams.width = ui.GuiAssetImageBox.layoutParams.width
                // ui.GuiAssetImageBorder.requestLayout()
            }
        }
        else if (name.endsWith(".mp4", true) ||
           name.endsWith(".webm", true) ||
          name.endsWith(".3gp", true) ||
          name.endsWith(".mkv", true))
        {
            ui.GuiAssetVideo.visibility = View.VISIBLE
            ui.GuiAssetVideoBox.setVideoPath(name)
            ui.GuiAssetVideoBox.start()
        }

        return bytes
    }



    fun updateAccount(acc: Account?)
    {
        account = acc
        var titl:String = i18n(R.string.title_activity_assets)
        if (acc != null) titl = titl + " : " + acc.name
        setTitle(titl)

        if (acc != null)
        {
            val assetList: List<AssetInfo> = constructAssetList(acc)
            LogIt.info(sourceLoc() + ": assets " + assetList.size)
            adapter = GuiList(ui.GuiAssetList, assetList, this, {
                val ui = AssetListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
                AssetBinder(ui, this)
            })
            adapter.rowBackgroundColors = WallyAssetRowColors

            currentlyViewing = -1
            ui.GuiAssetDetail.visibility = View.GONE

        }
    }

    override fun onTitleBarTouched()
    {
        // Move to another account
        LogIt.info("title button pressed")
        // If details is open, close it
        if (ui.GuiAssetDetail.visibility == View.VISIBLE) closeDetails()
        wallyApp?.let {
            if (it.accounts.size == 0)
            {
                displayError(R.string.NoAccounts, null, { })
                return
            }
            val (acti, account) = it.nextAccount(accountIdx)
            if (account == null)  // all accounts mysteriously disappeared!
            {
                wallyApp?.displayNotice(R.string.NoAccounts)
                finish()
            }
            else
            {
                accountIdx = acti
                updateAccount(account)
            }
        }
    }

}
