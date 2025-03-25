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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal

import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui2.theme.WallyRowBbkg1
import info.bitcoinunlimited.www.wally.ui2.theme.colorWarning
import info.bitcoinunlimited.www.wally.ui2.theme.WallyAssetRowColors
import info.bitcoinunlimited.www.wally.ui2.theme.WallyModalOutline
import info.bitcoinunlimited.www.wally.ui2.theme.defaultFontSize
import info.bitcoinunlimited.www.wally.ui2.views.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*


private val LogIt = GetLog("wally.assets")

@Composable
fun AssetView(assetInfo: AssetInfo, modifier: Modifier = Modifier)
{
    var asset by remember { mutableStateOf(assetInfo) }
    var showing by remember { mutableStateOf(S.NftPublicMedia) }  // Reuse the i18n int to indicate what subscreen is being shown
    val nftState = asset.nftObservable.collectAsState()
    val nft = nftState.value

    LaunchedEffect(assetInfo.groupId) {
        if (asset.iconUri != null)
            showing = S.NftCardFront
        else if (asset.publicMediaUri != null)
            showing = S.NftPublicMedia
        else if ((nft?.info ?: "") != "")
            showing = S.NftInfo
        else if (asset.ownerMediaUri != null)
            showing = S.NftOwnerMedia
        else if ((nft?.license ?: "") != "")
            showing = S.NftLegal
        else if (asset.iconBackUri != null)
            showing = S.NftCardBack
    }

    Column(modifier = modifier) {
        val a = asset
        // Token info buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            if (a.iconUri != null) WallySmallTextButton(S.NftCardFront, selected = showing == S.NftCardFront, onClick = {
                showing = S.NftCardFront
            })
            if (a.publicMediaUri != null) WallySmallTextButton(S.NftPublicMedia, selected = showing == S.NftPublicMedia, onClick = {
                showing = S.NftPublicMedia
            })
            if ((nft?.info ?: "") != "") WallySmallTextButton(S.NftInfo, selected = showing == S.NftInfo, onClick = {
                showing = S.NftInfo
            })
            if (a.ownerMediaUri != null) WallySmallTextButton(S.NftOwnerMedia, selected = showing == S.NftOwnerMedia, onClick = {
                showing = S.NftOwnerMedia
            })
            if ((nft?.license ?: "") != "") WallySmallTextButton(S.NftLegal, selected = showing == S.NftLegal, onClick = {
                showing = S.NftLegal
            })
            if (a.iconBackUri != null) WallySmallTextButton(S.NftCardBack, selected = showing == S.NftCardBack, onClick = {
                showing = S.NftCardBack
            })
        }

        if ((a.tokenInfo != null) && (a.tokenInfo?.tddSig == null))
        {
            CenteredText(i18n(S.TokenBadSig))
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
                MpMediaView(null, mediaBytes, url.toString(), autoplay = true) { mi, draw ->

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

                MpMediaView(null, mediaBytes, url.toString(), autoplay = true) { mi, draw ->

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

                MpMediaView(null, mediaBytes, url.toString(), autoplay = true) { mi, draw ->
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

                MpMediaView(null, mediaBytes, url.toString(), autoplay = true) { mi, draw ->
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
                Text(nft?.info ?: "")
            }

            S.NftLegal ->
            {
                // TODO formatting (support minimal HTML)
                Text(nft?.license ?: "")
            }
        }

    }
}

private val assetListState: MutableMap<String, MutableStateFlow<LazyListState?> > = mutableMapOf() //MutableStateFlow(null)

@Composable
fun AssetScreen(account: Account)
{
    val scrn = nav.currentScreen.collectAsState()
    val subScreen = nav.currentSubState.collectAsState()
    val sub = subScreen.value

    // If the subscreen tells us to show a particular asset, then show it.
    var assetFocus by remember { mutableStateOf<AssetPerAccount?>(
      if (sub != null)
      {
          account.assets[GroupId(account.chain.chainSelector, sub)]
      }
      else null
    ) }
    var assetFocusIndex by remember { mutableStateOf<Int>(0) }
    val assetsState = account.assetsObservable.collectAsState()
    val assets = assetsState.value
    val assetList = assets.values.toList().sortedBy { it.assetInfo.nft?.title ?: it.assetInfo.name ?: it.assetInfo.ticker ?: it.groupInfo.groupId.toString() }

    var asl = assetListState.get(account.name)
    if (asl == null)
    {
            assetListState[account.name] = MutableStateFlow(rememberLazyListState())
    }

    if (subScreen.value == null)
    {
        if (assetFocus!=null) clearAlerts()
        assetFocus = null
    }  // If I go back from a focused asset, the nav subscreenstate will be null

    Column(Modifier.fillMaxSize()) {
        val a = assetFocus
        if (a == null)  // Nothing is in focus, show the whole list
        {
            if (assets.size == 0)
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
                        val key: GroupId = it.groupInfo.groupId
                        val indexFreezer = index  // To use this in the item composable, we need to freeze it to a val, because the composable is called out-of-scope
                        item(key = key.toByteArray()) {
                            //LogIt.info("asset item")
                            val bkg = WallyAssetRowColors[indexFreezer % WallyAssetRowColors.size]
                            Box(Modifier.padding(4.dp, 2.dp).fillMaxWidth().background(bkg).clickable {
                                assetFocus = assets[key]
                                assetFocusIndex = indexFreezer
                                nav.go(ScreenId.Assets, assetFocus?.groupInfo?.groupId?.toByteArray())
                            }) {
                                AssetListItemView(it, 1, false, Modifier.padding(0.dp, 2.dp))
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

            AssetView(a.assetInfo, modifier = Modifier.weight(1f).padding(8.dp, 0.dp))
            WallyButtonRow {
                WallyBoringIconButton("icons/clipboard.xml", Modifier.width(26.dp).height(26.dp)) {
                    onCopyToClipboardButton(a.assetInfo)
                }
                assetFocus?.groupInfo?.groupId?.let {
                    WallyBoringIconButton("icons/open_in_browser.png", Modifier.width(26.dp).height(26.dp)) {
                        account.wallet.chainSelector.explorer("/token/$it").let {
                            openUrl(it)
                        }
                    }
                }
                WallyRoundedTextButton(S.Send, onClick = {
                    val defaultAmt = BigDecimal.fromInt(1, tokenDecimalMode(a.assetInfo?.tokenInfo?.genesisInfo?.decimal_places ?: 0)) // The default send is to transfer a single "one" (you can change in the send screen) -- whatever that means WRT the # of decimal places
                    account.addAssetToTransferList(a.groupInfo.groupId, defaultAmt)
                    displaySuccess(S.AssetAddedToTransferList)
                })
                if ((a.assetInfo.nft?.appuri ?: "") != "") WallyRoundedTextButton(S.AssetApplication, onClick = {
                    onInvokeButton(a.assetInfo)
                })
                if ((a.assetInfo.tokenInfo?.marketUri ?: "") != "") WallyRoundedTextButton(S.AssetMarketplace, onClick = {
                    onTradeButton(a.assetInfo)
                })
            }
        }

    }
}
