// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import com.ionspin.kotlin.bignum.decimal.*

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
import com.caverock.androidsvg.SVG
import info.bitcoinunlimited.www.wally.databinding.ActivityAssetsBinding
import info.bitcoinunlimited.www.wally.databinding.AssetListItemBinding
import info.bitcoinunlimited.www.wally.databinding.AssetSuccinctListItemBinding
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.*
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext

private val LogIt = GetLog("BU.wally.assetactivity")


/** shows this image in the passed imageview.  Returns true if imageView chosen, else false */
fun showMedia(iui: ImageView, vui: VideoView?, url: Url?, bytes: ByteArray? = null): Boolean
{
    try
    {
        if (url == null)
        {
            iui.setImageDrawable(null)
            return true
        }
        val name = url.toString().lowercase()

        if (name.endsWith(".svg", true))
        {
            val svg = if (bytes == null)
            {
                if (url.protocol == null)
                {
                    SVG.getFromInputStream(FileInputStream(name))
                }
                else throw UnimplementedException("non-local data in NFT")
            }
            else
            {
                SVG.getFromInputStream(ByteArrayInputStream(bytes))
            }
            val drawable = PictureDrawable(svg.renderToPicture())
            iui.setImageDrawable(drawable)
            iui.visibility = View.VISIBLE
            vui?.visibility = View.GONE
            return true
        }
        else if (name.endsWith(".jpg", true) ||
          name.endsWith(".jpeg", true) ||
          name.endsWith(".png", true) ||
          name.endsWith(".webp", true) ||
          name.endsWith(".gif", true) ||
          name.endsWith(".heic", true) ||
          name.endsWith(".heif", true)
        )
        {
            if (bytes == null)
            {
                if (url.protocol == null)
                {
                    val bmp = BitmapFactory.decodeFile(name)
                    iui.setImageBitmap(bmp)
                }
                else throw UnimplementedException("non-local data in NFT")
            }
            else
            {
                val bmp = BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
                iui.setImageBitmap(bmp)
            }
            iui.visibility = View.VISIBLE
            vui?.visibility = View.GONE
            return true
        }
        else
        {
            if ((vui != null) && (name.endsWith(".mp4", true) ||
                name.endsWith(".webm", true) ||
                name.endsWith(".3gp", true) ||
                name.endsWith(".mkv", true)))
            {
                LogIt.info("Video URI: ${url.toString()}")
                vui.setVideoPath(url.toString())
                vui.start()
                iui.visibility = View.INVISIBLE  // Can't be GONE because other stuff is positioned against it
                vui.visibility = View.VISIBLE
                return false
            }
            else
            {
                throw UnimplementedException("unsupported video format")
            }
        }
    }
    catch(e: UnimplementedException)
    {
        // TODO: pick a better cannot load image
        iui.setImageResource(R.drawable.asset_cannot_show_icon)
        iui.visibility = View.VISIBLE
        vui?.visibility = View.GONE
        return true
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
                //d.sui = this

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
        // data?.ui = null
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
                // d.ui = this
                ui.GuiAssetId.text = d.groupInfo.groupId.toString()
                ui.GuiAssetId.visibility = if (devMode) View.VISIBLE else View.GONE

                val nft = d.nft
                if (nft == null)
                {
                    ui.GuiAssetId.text = d.groupInfo.groupId.toString()
                    ui.GuiAssetName.text = d.name ?: ""
                    ui.GuiAssetQuantity.text = tokenAmountString(d.groupInfo.tokenAmt, d.tokenInfo?.genesisInfo?.decimal_places)
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
                        ui.GuiAssetQuantity.text = tokenAmountString(d.groupInfo.tokenAmt, d.tokenInfo?.genesisInfo?.decimal_places)
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
        // data?.ui = null
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

    val assetManager
        get() = wallyApp!!.assetManager

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
            wallyAndroidApp?.let { app ->
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
                        displayError(R.string.NoAccounts)
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
        acc.wallet.forEachTxo { sp ->
            if (sp.isUnspent)
            {
                // TODO: this is a workaround for a bug where the script chain is incorrect
                if (sp.priorOutScript.chainSelector != sp.chainSelector)
                {
                    LogIt.warning("BUG fixup: Script chain is ${sp.priorOutScript.chainSelector} but chain is ${sp.chainSelector}")
                    sp.priorOutScript = SatoshiScript(sp.chainSelector, sp.priorOutScript.type, sp.priorOutScript.flatten())
                }

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
            false
        }

        // Start grabbing the data for all assets (asynchronously)
        for (asset in ast.values)
        {
            laterAssets {
                asset.load(acc.wallet.blockchain, assetManager)
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
            if (assetManager.addAssetToTransferList(a))
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
            val nftZipData = a.nftFile(assetManager)
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
        val cacheDir = wallyAndroidApp!!.cacheDir
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
        showDetailMediaUri(a.iconUri, a.iconBytes)
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
                val nftZipData = a.nftFile(assetManager)
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
                val nftZip = a.nftFile(assetManager)
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


    fun showDetailMediaUri(url: Url?, bytes: ByteArray? = null): ByteArray?
    {
        ui.GuiAssetVideo.visibility = View.GONE
        ui.GuiAssetWeb.visibility = View.GONE
        ui.GuiAssetImage.visibility = View.GONE

        if (bytes == null && (url?.protocol == URLProtocol.HTTP || url?.protocol == URLProtocol.HTTPS)) throw UnimplementedException("load from URL")
        when (showMedia(ui.GuiAssetImageBox, ui.GuiAssetVideoBox, url, bytes))
        {
            true -> { ui.GuiAssetImage.visibility = View.VISIBLE }
            false -> { ui.GuiAssetVideo.visibility = View.VISIBLE }
        }
        return bytes
    }

    fun showDetailMedia(name: String?, bytes: ByteArray? = null): ByteArray?
    {
        if (name != null) return showDetailMediaUri(Url(name), bytes)
        else return showDetailMediaUri(null, bytes)
    }

    /*
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

     */



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
                displayNotice(R.string.NoAccounts)
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
