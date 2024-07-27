package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal

import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*


private val LogIt = GetLog("wally.assets")

@Composable
fun AssetListItemView(assetPerAccount: AssetPerAccount, verbosity: Int = 1, allowAmountEdit: Boolean = false, modifier: Modifier = Modifier)
{
    var apc by remember { mutableStateOf(assetPerAccount) }
    val asset = apc.assetInfo
    val nft = asset.nft

    Column(modifier = modifier) {
        if ((devMode)&&(verbosity>0)) CenteredFittedText(asset.groupId.toStringNoPrefix())
        Row {
            val hasImage = if (asset.iconImage != null) "yes" else "null"
            LogIt.info("Asset ${asset.name} icon Image ${hasImage} icon bytes: ${asset.iconBytes?.size} icon url: ${asset.iconUri}")
            MpMediaView(asset.iconImage, asset.iconBytes, asset.iconUri?.toString()) { mi, draw ->
                val m = (if (verbosity > 0) Modifier.background(Color.Transparent).size(64.dp, 64.dp)
                else  Modifier.background(Color.Transparent).size(26.dp, 26.dp)).align(Alignment.CenterVertically)
                draw(m)
            }

            // If its an NFT, don't show the quantity if they have just 1
            if ((nft == null)||(apc.groupInfo.tokenAmt != 1L))
            {
                if (allowAmountEdit)
                {
                    // Note the "default" (unedited) amount is ALL tokens.  If you change this default, you must also change it in the actuallySend() function.
                    val amt = assetPerAccount.editableAmount?.toPlainString() ?: tokenAmountString(apc.groupInfo.tokenAmt, asset.tokenInfo?.genesisInfo?.decimal_places)
                    WallyDecimalEntry(mutableStateOf(amt)) {
                        try
                        {
                            assetPerAccount.editableAmount = assetPerAccount.tokenDecimalFromString(it)
                        }
                        catch (e: Exception) // If we can't parse it for any reason, ignore
                        {
                            LogIt.info("Can't parse editable amount ${it}")
                        }
                        it
                    }
                }
                else
                {
                    Spacer(modifier.width(2.dp))
                    val amt = tokenAmountString(apc.groupInfo.tokenAmt, asset.tokenInfo?.genesisInfo?.decimal_places)
                    val lenAdjust = 1.0 // 5.0/max(amt.length,5)
                    val fontSize = if (verbosity > 0) 2.0*lenAdjust else 1.0*lenAdjust
                    SelectionContainer(modifier = modifier.weight(0.75f).align(Alignment.CenterVertically)) {
                        CenteredFittedText(amt, fontSize, modifier = modifier.align(Alignment.CenterVertically))
                    }
                }
                Spacer(modifier.width(4.dp))
            }

            Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                var name = (if ((nft != null) && (nft.title.length > 0)) nft.title else asset.name)
                if (verbosity > 0)
                {
                    if (name != null) Text(text = name, modifier = Modifier.padding(0.dp).fillMaxWidth(), style = WallySectionTextStyle(), textAlign = TextAlign.Center)
                    else Text(text = i18n(S.loading), modifier = Modifier.padding(0.dp).fillMaxWidth(), textAlign = TextAlign.Center, style = TextStyle(fontWeight = FontWeight.Light,  fontStyle = FontStyle.Italic))
                    if ((nft?.author != null) && (nft.author.length > 0))
                    {
                        CenteredText(i18n(S.NftAuthor) % mapOf("author" to nft.author))
                    }
                    if (nft?.series != null)
                    {
                        CenteredText(i18n(S.NftSeries) % mapOf("series" to nft.series))
                    }
                    val ti = asset.tokenInfo
                    if ((ti!=null) && (ti.tddSig == null))
                    {
                        CenteredText(i18n(S.NftImproperConstruction), modifier.background(colorWarning))
                    }
                    else
                    {
                        val urlS = asset.docUrl
                        if (urlS != null)
                        {
                            val url = com.eygraber.uri.Url.parseOrNull(urlS)
                            if (url != null)
                            {
                                CenteredText(url.host, TextStyle(fontSize = defaultFontSize * 0.75, fontWeight = FontWeight.Light,  fontStyle = FontStyle.Italic))
                            }
                        }
                    }
                }
                else
                {
                    if (name == null) name = asset.ticker ?: asset.groupId.toString()
                    var author = if ((nft?.author != null) && (nft.author.length > 0)) ", " + nft.author else ""
                    CenteredFittedText(name + author)
                }
            }

        }
    }
}


@Composable
fun AssetView(assetInfo: AssetInfo, modifier: Modifier = Modifier)
{
    var asset by remember { mutableStateOf(assetInfo) }
    var showing by remember { mutableStateOf(S.NftCardFront) }  // Reuse the i18n int to indicate what subscreen is being shown

    Column(modifier = modifier) {
        val a = asset
        // Token info buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            if (a.iconUri != null) WallySmallTextButton(S.NftCardFront, onClick = {
                showing = S.NftCardFront
            })
            if (a.publicMediaUri != null) WallySmallTextButton(S.NftPublicMedia, onClick = {
                showing = S.NftPublicMedia
            })
            if ((a.nft?.info ?: "") != "") WallySmallTextButton(S.NftInfo, onClick = {
                showing = S.NftInfo
            })
            if (a.ownerMediaUri != null) WallySmallTextButton(S.NftOwnerMedia, onClick = {
                showing = S.NftOwnerMedia
            })
            if ((a.nft?.license ?: "") != "") WallySmallTextButton(S.NftLegal, onClick = {
                showing = S.NftLegal
            })
            if (a.iconBackUri != null) WallySmallTextButton(S.NftCardBack, onClick = {
                showing = S.NftCardBack
            })
        }

        if ((a.tokenInfo != null) && (a.tokenInfo?.tddSig == null))
        {
            CenteredText(i18n(S.TokenUnsigned))
        }

        CenteredSectionText(showing)
        when(showing)
        {
            S.NftCardFront ->
            {
                val url = a.iconUri
                val mediaBytes = a.iconBytes
                if (mediaBytes == null && (url?.protocol == URLProtocol.HTTP || url?.protocol == URLProtocol.HTTPS)) throw UnimplementedException("load from URL")

                val surfShape = RoundedCornerShape(20.dp)

                // Do not show the cached icon since its small
                MpMediaView(null, mediaBytes, url.toString()) { mi, draw ->

                    // Fill the media available space's x or y with the media, but draw a nice box around that space.
                    // Its is amazing that this is so hard.
                    // My approach is to determine the aspect ratio (x/y)of the image, and the aspect ratio of the available space.
                    // If the image AR is > the space AR, then the image is relatively wider than the space so we should fill max width, and
                    // set the height as appropriate.  Otherwise do the equivalent but fill max height

                    val ar = mi.width.toFloat()/mi.height.toFloat()
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        // maxWidth and maxHeight provide the screen size
                        // min W and H appears to provide not 0dp which makes sense but is trivial, but the minimum size of the Box with the modifiers
                        // applied, in this case fillMaxSize(), so the size of the view
                        val spaceAr = minWidth/minHeight

                        var mod = Modifier.border(WallyModalOutline, surfShape)
                        if (ar >= spaceAr)  // media is wider than the space I have to show it in
                            mod = mod.fillMaxWidth().aspectRatio(ar)
                        else
                            mod = mod.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                        Surface(shape = surfShape, modifier = mod.align(Alignment.Center))
                        {
                            draw(null)
                        }
                    }
                }
            }
            S.NftPublicMedia ->
            {
                val mediaBytes = a.publicMediaBytes
                LogIt.info("public media bytes: ${a.publicMediaBytes?.size} cache: ${a.publicMediaCache} uri: ${a.publicMediaUri} ")
                val url = a.publicMediaCache ?: a.publicMediaUri?.toString()

                //if (mediaBytes == null && (url?.protocol == URLProtocol.HTTP || url?.protocol == URLProtocol.HTTPS)) throw UnimplementedException("load from URL")

                val surfShape = RoundedCornerShape(20.dp)

                MpMediaView(null, mediaBytes, url.toString()) { mi, draw ->

                    // Fill the media available space's x or y with the media, but draw a nice box around that space.
                    // Its is amazing that this is so hard.
                    // My approach is to determine the aspect ratio (x/y)of the image, and the aspect ratio of the available space.
                    // If the image AR is > the space AR, then the image is relatively wider than the space so we should fill max width, and
                    // set the height as appropriate.  Otherwise do the equivalent but fill max height

                    val ar = mi.width.toFloat()/mi.height.toFloat()
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        // maxWidth and maxHeight provide the screen size
                        // min W and H appears to provide not 0dp which makes sense but is trivial, but the minimum size of the Box with the modifiers
                        // applied, in this case fillMaxSize(), so the size of the view
                        val spaceAr = minWidth/minHeight

                        var mod = Modifier.border(WallyModalOutline, surfShape)
                        if (ar >= spaceAr)  // media is wider than the space I have to show it in
                            mod = mod.fillMaxWidth().aspectRatio(ar)
                        else
                            mod = mod.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                        Surface(shape = surfShape, modifier = mod.align(Alignment.Center))
                        {
                            draw(null)
                        }
                    }
                }
            }

            S.NftOwnerMedia ->
            {
                val mediaBytes = a.ownerMediaBytes
                val url = a.ownerMediaCache ?: a.ownerMediaUri?.toString()

                //if (mediaBytes == null && (url?.protocol == URLProtocol.HTTP || url?.protocol == URLProtocol.HTTPS)) throw UnimplementedException("load from URL")

                val surfShape = RoundedCornerShape(20.dp)

                MpMediaView(null, mediaBytes, url.toString()) { mi, draw ->
                    val ar = mi.width.toFloat()/mi.height.toFloat()
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val spaceAr = minWidth/minHeight
                        var mod = Modifier.border(WallyModalOutline, surfShape)
                        if (ar >= spaceAr)  // media is wider than the space I have to show it in
                            mod = mod.fillMaxWidth().aspectRatio(ar)
                        else
                            mod = mod.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                        Surface(shape = surfShape, modifier = mod.align(Alignment.Center))
                        {
                            draw(null)
                        }
                    }
                }
            }

            S.NftCardBack ->
            {
                val url = asset.iconBackUri
                val mediaBytes = asset.iconBackBytes
                if (mediaBytes == null && (url?.protocol == URLProtocol.HTTP || url?.protocol == URLProtocol.HTTPS)) throw UnimplementedException("load from URL")

                val surfShape = RoundedCornerShape(20.dp)

                MpMediaView(null, mediaBytes, url.toString()) { mi, draw ->
                    val ar = mi.width.toFloat()/mi.height.toFloat()
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val spaceAr = minWidth/minHeight
                        var mod = Modifier.border(WallyModalOutline, surfShape)
                        if (ar >= spaceAr)  // media is wider than the space I have to show it in
                            mod = mod.fillMaxWidth().aspectRatio(ar)
                        else
                            mod = mod.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                        Surface(shape = surfShape, modifier = mod.align(Alignment.Center))
                        {
                            draw(null)
                        }
                    }
                }
            }

            S.NftInfo ->
            {
                // TODO formatting (support minimal HTML)
                Text(asset.nft?.info ?: "")
            }

            S.NftLegal ->
            {
                // TODO formatting (support minimal HTML)
                Text(asset.nft?.license ?: "")
            }
        }

    }
}

private val assetListState: MutableMap<String, MutableStateFlow<LazyListState?> > = mutableMapOf() //MutableStateFlow(null)

@Composable
fun AssetScreen(account: Account, nav: ScreenNav)
{
    var assetFocus by remember { mutableStateOf<AssetPerAccount?>(null) }
    var assetFocusIndex by remember { mutableStateOf<Int>(0) }
    var assetList = remember { mutableStateListOf<Pair<AssetLoadState,AssetPerAccount>>() }

    var asl = assetListState.get(account.name)
    if (asl == null)
    {
            assetListState[account.name] = MutableStateFlow(rememberLazyListState())
    }


    // Every half second check whether assetList needs to be regenerated
    LaunchedEffect(Unit) {
        while(true)
        {
            val lst = account.assetList()
            lst.sortBy { it.assetInfo.nft?.title ?: it.assetInfo.name ?: it.assetInfo.ticker ?: it.groupInfo.groupId.toString() }

            var redo=false
            if (lst.size != assetList.size)
            {
                LogIt.info("Asset list redraw: Size changed")
                redo=true
            }
            else
            {
                // Check if either the token changed or its loadstate changed
                for (item in lst.zip(assetList))
                {
                    if (item.first.assetInfo.loadState != item.second.first)
                    {
                        LogIt.info("Asset list redraw: loadstate changed for ${item.first.groupInfo.groupId}")
                        redo = true
                        break
                    }
                    if (item.first.groupInfo.groupId != item.second.second.groupInfo.groupId)
                    {
                        LogIt.info("Asset list redraw: order changed for ${item.first.groupInfo.groupId} was ${item.second.second.groupInfo.groupId}")
                        redo = true
                        break
                    }
                }
            }

            if (redo)
            {
                assetList.clear()
                for (item in lst)
                {
                    assetList.add(Pair(item.assetInfo.loadState, item))
                }
            }
            delay(500)
        }
    }

    if (nav.currentSubState.value == null) assetFocus = null  // If I go back from a focused asset, the nav subscreenstate will be null

    Column(Modifier.fillMaxSize()) {
        val a = assetFocus
        if (a == null)  // Nothing is in focus, show the whole list
        {
            if (account.assets.size == 0)
            {
                Spacer(Modifier.height(10.dp))
                CenteredFittedText(i18n(S.NoAssets) % mapOf("act" to account.name), 2.0, modifier=Modifier.background(WallyRowBbkg1).fillMaxWidth())
            }
            else
            {
                val scope = rememberCoroutineScope()
                val tmp = assetListState[account.name]?.collectAsState(scope.coroutineContext)?.value ?: rememberLazyListState()
                //LogIt.info("recomposing asset column")
                LazyColumn(state=tmp, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.2f)) {
                    var index = 0
                    assetList.forEach {
                        val key = it.second.groupInfo.groupId
                        val entry = it.second
                        val indexFreezer = index  // To use this in the item composable, we need to freeze it to a val, because the composable is called out-of-scope
                        item(key = key.toByteArray()) {
                            //LogIt.info("asset item")
                            val bkg = WallyAssetRowColors[indexFreezer % WallyAssetRowColors.size]
                            Box(Modifier.padding(4.dp, 2.dp).fillMaxWidth().background(bkg).clickable {
                                assetFocus = account.assets[key]
                                assetFocusIndex = indexFreezer
                                nav.go(ScreenId.Assets, byteArrayOf(indexFreezer.toByte()))
                            }) {
                                AssetListItemView(entry, 1, false, Modifier.padding(0.dp, 2.dp))
                            }
                        }
                        index++
                    }
                }
            }
        }
        else  // Show a specific asset
        {
            Box(Modifier.padding(4.dp, 2.dp).fillMaxWidth().background(WallyAssetRowColors[assetFocusIndex % WallyAssetRowColors.size]).clickable {
                assetFocus = null  // If you touch the asset list when focused on an asset, then pop back out to the list
                assetFocusIndex = 0
                clearAlerts()
                nav.back()

            }) {
                AssetListItemView(a, 1)
            }

            WallyDivider()
            val uriHandler = LocalUriHandler.current

            AssetView(a.assetInfo, modifier = Modifier.weight(1f).padding(8.dp, 0.dp))
            WallyButtonRow {
                WallyBoringIconButton("icons/clipboard.xml", Modifier.width(26.dp).height(26.dp)) {
                    onCopyToClipboardButton(a.assetInfo)
                }
                WallyRoundedTextButton(S.Send, onClick = {
                    val defaultAmt = BigDecimal.fromInt(1, tokenDecimalMode(a.assetInfo?.tokenInfo?.genesisInfo?.decimal_places ?: 0)) // The default send is to transfer a single "one" (you can change in the send screen) -- whatever that means WRT the # of decimal places
                    account.addAssetToTransferList(a.groupInfo.groupId, defaultAmt)
                    displaySuccess(S.AssetAddedToTransferList)
                })
                if ((a.assetInfo.nft?.appuri ?: "") != "") WallyRoundedTextButton(S.AssetApplication, onClick = {
                    onInvokeButton(a.assetInfo, uriHandler)
                })
                if ((a.assetInfo.tokenInfo?.marketUri ?: "") != "") WallyRoundedTextButton(S.AssetMarketplace, onClick = {
                    onTradeButton(a.assetInfo, uriHandler)
                })
            }
        }

    }
}

fun onCopyToClipboardButton(a: AssetInfo?)
{
    if (a!=null)
    {
        setTextClipboard(a.groupId.toString())
        // TODO: if (android.os.Build.VERSION.SDK_INT <= 32)  // system toasts above this version
        displayNotice(S.copiedToClipboard)
    }
}


fun onTradeButton(a: AssetInfo?, uriHandler: UriHandler)
{
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
                    displayNotice(S.IssuedToConnection)
                    val c = cnxn
                    later {  // Can't do network in UI thread
                        // If we are connected, open the URL in the connected device (computer)
                        val connectedUrl = Url(c.proto + "://" + c.hostPort + "/").resolve( uri.path + "?cookie=${c.cookie}")

                        try
                        {
                            val result = connectedUrl.readText(HTTP_REQ_TIMEOUT_MS)
                            LogIt.info(sourceLoc() + "read: ${connectedUrl} ->\n${result.toString()}")
                        }
                        catch (e: Exception)  // otherwise open it here
                        {
                            if (market.lowercase().startsWith("http"))
                            {
                                // If there are already params, add another with an &.  Otherwise add the first param with a ?
                                val mkt = (if (market.contains("?")) market + "&"
                                else market + "?" + "tokenid=") + a.groupId.toStringNoPrefix()
                                uriHandler.openUri(mkt)
                            }
                        }
                    }
                }
                else
                {
                    // If there are already params, add another with an &.  Otherwise add the first param with a ?
                    val mkt = (if (market.contains("?")) market + "&"
                    else market + "?" + "tokenid=") + a.groupId.toStringNoPrefix()
                    uriHandler.openUri(mkt)
                }
            }
        }
        catch(e: Exception)
        {
            LogIt.info("asset marketplace activity not found: ${a.tokenInfo?.marketUri}")
            displayUnexpectedException(e)
        }
    }
}

fun onInvokeButton(a: AssetInfo?, uriHandler: UriHandler )
{
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
                if (appuri.contains("?")) appuri = appuri + "&" + "tokenid=" + a.groupId.toStringNoPrefix()
                else appuri = appuri + "?" + "tokenid=" + a.groupId.toStringNoPrefix()
            }

            uriHandler.openUri(appuri)
        }
    }
}

