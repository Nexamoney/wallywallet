// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally


import android.content.Context
import android.graphics.BitmapFactory
import bitcoinunlimited.libbitcoincash.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.PictureDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.drawable.toIcon

import androidx.recyclerview.widget.LinearLayoutManager
import bitcoinunlimited.libbitcoincash.CurrencyDecimal
import bitcoinunlimited.libbitcoincash.TransactionHistory
import bitcoinunlimited.libbitcoincash.fiatFormat
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGImageView
import info.bitcoinunlimited.www.wally.databinding.ActivityAssetsBinding
import info.bitcoinunlimited.www.wally.databinding.ActivityShoppingBinding
import info.bitcoinunlimited.www.wally.databinding.AssetListItemBinding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.*
import java.net.URI
import java.net.URL
import java.util.logging.Logger
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private val LogIt = Logger.getLogger("BU.wally.assets")

open class IncorrectTokenDescriptionDoc(details: String) : BUException(details, "Incorrect token description document", ErrorSeverity.Expected)

val NIFTY_ART_IP = mapOf(
  ChainSelector.NEXA to "niftyart.cash",
  ChainSelector.NEXAREGTEST to "192.168.1.5:8988"
)

class AssetManager(val app: WallyApp)
{
    fun getNftFile(groupId:GroupId)
    {
        if (false)  // streaming
        {
            val fis = FileInputStream(app.filesDir.resolve(groupId.toHex() + ".zip"))
            val zis = ZipInputStream(BufferedInputStream(fis))

            while (true)
            {
                var ze = zis.nextEntry
                if (ze.isDirectory)
                {

                }
            }
        }

        if (true)  // absolute
        {
            val file = File(app.filesDir, groupId.toHex() + ".zip")
            val zf = ZipFile(file)

            val ze = zf.getEntry("blah")
        }

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

    fun storeNftFile(filename: String, data: ByteArray)
    {
        val context = wallyApp!!

        val dir = context.getDir("nft", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        FileOutputStream(file).use {
            it.write(data)
        }
    }

    fun loadNftFile(filename: String): ByteArray
    {
        val context = wallyApp!!
        val dir = context.getDir("nft", Context.MODE_PRIVATE)
        val file = File(dir, filename)
        FileInputStream(file).use {
            return it.readBytes()
        }
    }

    fun nftUrl(s: String?, groupId: GroupId):String?
    {
        if (s == null)
        {
            return("http://" + NIFTY_ART_IP[groupId.blockchain] + "/_public/" + groupId)
        }
        // TODO many more ways to get it
        return null
    }

    fun webGetNftFile(td: TokenDesc?, groupId: GroupId):ByteArray?
    {
        val url = td?.nftUrl ?: nftUrl(td?.genesisInfo?.document_url, groupId)
        LogIt.info(sourceLoc() + ": nft URL: " + url)

        if (url != null)
        {
            try
            {
                val zipBytes = URL(url).readBytes()
                val zf = ZipInputStream(ByteArrayInputStream(zipBytes))
                // Just try to go thru the zip dir to see if its basically a valid file
                val files = generateSequence { zf.nextEntry }.map { it.name }.toList()
                LogIt.info(sourceLoc() + ": nft zip contents " + files.joinToString(" "))
                val hash = Hash.hash256(zipBytes)
                if (groupId.subgroupData() contentEquals  hash)
                {
                    storeNftFile(groupId.toHex() + ".zip", zipBytes)
                    return zipBytes
                }
                else
                {
                     LogIt.info(sourceLoc() + ": nft zip file does not match hash")
                }
            }
            catch(e:java.net.MalformedURLException)
            {
            }
        }
        return null
    }

    fun load(chain: Blockchain)  // Attempt to find all asset info from a variety of data sources
    {
        // first load the token description doc (TDD)
        val ec = openElectrum(chain.chainSelector)
        val tg = ec.getTokenGenesisInfo(groupInfo.groupId, 30*1000)
        LogIt.info(sourceLoc() + chain.name + ": rostrum loaded: " + tg.name)

        name = tg.name
        ticker = tg.ticker
        genesisHeight = tg.height
        genesisTxidem = Hash256(tg.txidem)
        tg.document_hash?.let { docHash = Hash256(it) }
        docUrl = tg.document_url

        var doc: String? = null
        if (docUrl != null)
        {
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

            val d = doc
            LogIt.info(sourceLoc() + ": TDD:" + d)
            if (d != null)
            {
                val (td, hash, sig) = decodeTokenDescDoc(d)
                td.genesisInfo = tg

                val nftZip = webGetNftFile(td, groupInfo.groupId)
                if (nftZip != null)
                {
                    // TODO grab image from zip file
                    val (name, data) = cardFront(nftZip)
                    if (name != null)
                    {
                        iconBytes = data
                        iconUri = Uri.parse(name)  // TODO, actually just a filename
                    }
                }
                else
                {
                    LogIt.info(sourceLoc() + ": $name == ${td.name}, $ticker == ${td.ticker} ")
                    if (ticker != td.ticker)
                    {
                        throw IncorrectTokenDescriptionDoc("ticker does not match asset genesis transaction")
                    }

                    // TODO verify TDD sig
                    name = td.name
                    ticker = td.ticker
                    tokenInfo = td  // ok save all the info
                    val iconUrl = tokenInfo?.icon
                    LogIt.info(sourceLoc() + ": icon " + iconUrl)

                    if (iconUrl != null)
                    {
                        val img = try
                        {
                            iconBytes = URL(iconUrl).readBytes()
                            iconUri = Uri.parse(URL(iconUrl).toString())
                        } catch (e: java.net.MalformedURLException)
                        {
                            iconBytes = URI(docUrl).resolve(iconUrl).toURL().readBytes()
                            iconUri = Uri.parse(URI(docUrl).resolve(iconUrl).toString())
                        }
                        LogIt.info(sourceLoc() + " icon is" + img)
                    }
                }
            }
        }
        else
        {
            val nftZip = webGetNftFile(null, groupInfo.groupId)
            if (nftZip != null)
            {
                // TODO grab image from zip file
                val (name, data) = cardFront(nftZip)
                if (name != null)
                {
                    iconBytes = data
                    iconUri = Uri.parse(name)  // TODO, actually just a filename
                }
            }
        }
    }

}


class AssetBinder(val ui: AssetListItemBinding, val activity: AssetsActivity): GuiListItemBinder<AssetInfo>(ui.root)
{
    // Fill the view with this data
    override fun populate()
    {
        LogIt.info(sourceLoc() + "populate: " + (data?.groupInfo?.groupId?.toString() ?: ""))
        data?.let()
        { d ->
            ui.GuiAssetId.text = d.groupInfo.groupId.toString()
            ui.GuiAssetName.text = d.name ?: ""
            ui.GuiAssetQuantity.text = d.groupInfo.tokenAmt.toString()
            // only works for local Uris
            // if (d.iconUri != null) ui.GuiAssetIcon.setImageURI(d.iconUri)
            if (d.iconUri != null)
            {
                if (d.iconUri.toString().endsWith(".svg", ignoreCase = true))
                {
                    val svg = SVG.getFromInputStream(ByteArrayInputStream(d.iconBytes))
                    val drawable = PictureDrawable(svg.renderToPicture())
                    ui.GuiAssetIcon.setImageDrawable(drawable)
                }
                if (d.iconUri.toString().endsWith(".jpg", ignoreCase = true))
                {
                    val bmp = BitmapFactory.decodeStream(ByteArrayInputStream(d.iconBytes))
                    ui.GuiAssetIcon.setImageBitmap(bmp)
                }
            }
        }
    }

    override fun onClick(v: View)
    {
        super.onClick(v)
        activity.adapter.layout()  // TODO: temporary hack to force showing of image on click -- should auto-redraw when the image is available
    }
}


class AssetsActivity : CommonNavActivity()
{
    private lateinit var ui: ActivityAssetsBinding
    override var navActivityId = R.id.navigation_assets
    var account: Account? = null
    var accountIdx = -1
    lateinit var adapter: GuiList<AssetInfo, AssetBinder>

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityAssetsBinding.inflate(layoutInflater)
        setContentView(ui.root)
        ui.GuiAssetList.layoutManager = LinearLayoutManager(this)

        enableMenu(this, SHOW_ASSETS_PREF)  // If you ever drop into this activity, show it in the menu

        laterUI {
            wallyApp?.let { app ->
                if (app !is WallyApp)
                {
                    finish()
                }
                else
                {
                    val acc = wallyApp?.primaryAccount
                    updateAccount(acc)
                }
            }
            account?.let { setTitle(i18n(R.string.title_activity_assets) + ": " + it.name) }
        }
    }

    fun constructAssetList(acc: Account): List<AssetInfo>
    {
        LogIt.info(sourceLoc() + acc.name + ": Construct assets")
        val ret = mutableListOf<AssetInfo>()
        for (txo in acc.wallet.txos)
        {
            val sp = txo.value
            if (sp.isUnspent)
            {
                val grp = sp.groupInfo()
                if ((grp != null)&& !grp.isAuthority())  // TODO not dealing with authority txos in Wally mobile
                {
                    val ai = AssetInfo(grp)
                    later {
                        ai.load(acc.wallet.blockchain)
                        }
                    ret.add(ai)
                }
            }
        }
        return ret.toList()
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
            adapter.rowBackgroundColors = WallyRowColors
        }
    }

    override fun onTitleBarTouched()
    {
        LogIt.info("title button pressed")
        wallyApp?.let {
            if (it.accounts.size == 0)
            {
                displayError(R.string.NoAccounts, null, { })
                return
            }
            accountIdx+=1
            if (accountIdx >= it.accounts.size) accountIdx = 0
            val al = it.accounts.values.toList()
            if (al[accountIdx] == account) accountIdx++  // Avoid a repeat unless this is the only account
            if (accountIdx >= it.accounts.size) accountIdx = 0
            updateAccount(al[accountIdx])
        }
    }

}
