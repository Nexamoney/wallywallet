package info.bitcoinunlimited.www.wally.uiv2

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.onCopyToClipboardButton
import info.bitcoinunlimited.www.wally.ui.onInvokeButton
import info.bitcoinunlimited.www.wally.ui.onTradeButton
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurpleExtraLight
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*


private val LogIt = GetLog("wally.assets")

@Composable
fun AssetListItemViewUi2(assetPerAccount: AssetPerAccount, verbosity: Int = 1, allowAmountEdit: Boolean = false, showAuthor: Boolean = false, modifier: Modifier = Modifier)
{
    val apc by remember { mutableStateOf(assetPerAccount) }
    val asset = apc.assetInfo
    val nft = asset.nftObservable.collectAsState().value
    val assetName = asset.nameObservable.collectAsState().value
    // This automatically re-renders the compose view on changes to loadStateObservable
    // TODO: Find another way to do this.
    @Suppress("unused")
    val loadState = asset.loadStateObservable.collectAsState()

    val hasImage = if (asset.iconImage != null) "yes" else "null"
    LogIt.info("Asset ${asset.name} icon Image ${hasImage} icon bytes: ${asset.iconBytes?.size} icon url: ${asset.iconUri}")
    val amt = tokenAmountString(apc.groupInfo.tokenAmt, asset.tokenInfo?.genesisInfo?.decimal_places)
    val name = (if ((nft != null) && (nft.title.isNotEmpty())) nft.title else assetName)

    Spacer(Modifier.height(8.dp))
    Column {
        if ((devMode)&&(verbosity>0)) SelectionContainer(Modifier.fillMaxWidth()) { CenteredFittedText(asset.groupId.toStringNoPrefix()) }
        ListItem(
          colors = ListItemDefaults.colors(
            containerColor = wallyPurpleExtraLight
          ),
          leadingContent = {
              MpMediaView(asset.iconImage, asset.iconBytes, asset.iconUri?.toString(), hideMusicView = true) { mi, draw ->
                  val m = (if (verbosity > 0) Modifier.background(Color.Transparent).size(64.dp, 64.dp)
                  else  Modifier.background(Color.Transparent).size(26.dp, 26.dp))// .align(Alignment.CenterVertically)
                  draw(m)
              }
          },
          headlineContent = {
              Column {
                  nft?.series?.let {
                      Text(it, style = MaterialTheme.typography.bodyLarge.copy(color = wallyPurple), fontWeight = FontWeight.Bold)
                  }
                  if (name != null) Text(name, style = MaterialTheme.typography.titleLarge)
                  else Text(text = i18n(S.loading), modifier = Modifier.padding(0.dp).fillMaxWidth(), textAlign = TextAlign.Center, style = TextStyle(fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic))
                  asset.docUrl?.let { docUrl ->
                      val url = com.eygraber.uri.Url.parseOrNull(docUrl)
                      val host = try
                      {
                          url?.host  // although host is supposedly not null, I can get "java.lang.IllegalArgumentException: Url requires a non-null host"
                      }
                      catch (e: IllegalArgumentException)
                      {
                          null
                      }
                      host?.let {
                          Text(it, fontStyle = FontStyle.Italic)
                      }
                  }
              }
          },
          trailingContent = {
              // If its an NFT, don't show the quantity if they have just 1
              if ((nft == null)||(apc.groupInfo.tokenAmt != 1L))
                  Text(amt)
          }
        )
    }
}


@Composable
fun AssetViewUi2(asset: AssetInfo, parentMod: Modifier = Modifier)
{
    var showing by remember { mutableStateOf(S.NftPublicMedia) }  // Reuse the i18n int to indicate what subscreen is being shown
    val nftState = asset.nftObservable.collectAsState()
    val nft = nftState.value
    val assetName = asset.nameObservable.collectAsState()
    val name = (if ((nft != null) && (nft.title.length > 0)) nft.title else assetName.value) ?: "<name missing>"
    val options = remember { mutableStateOf(mutableSetOf<Int>()) }

    LaunchedEffect(asset.groupId) {
        if (asset.iconUri != null) options.value.add(S.NftCardFront)
        if (asset.publicMediaUri != null) options.value.add(S.Content)
        if ((nft?.info ?: "") != "") options.value.add(S.NftInfo)
        if (asset.ownerMediaUri != null) options.value.add(S.Private)
        if ((nft?.license ?: "") != "") options.value.add(S.NftLegal)
        if (asset.iconBackUri != null) options.value.add(S.NftCardBack)

        if (asset.iconUri != null)
            showing = S.NftCardFront
        else if (asset.publicMediaUri != null)
            showing = S.Content
        else if ((nft?.info ?: "") != "")
            showing = S.NftInfo
        else if (asset.ownerMediaUri != null)
            showing = S.Private
        else if ((nft?.license ?: "") != "")
            showing = S.NftLegal
        else if (asset.iconBackUri != null)
            showing = S.NftCardBack
    }

    Column(modifier = parentMod.fillMaxSize().padding(8.dp, 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val a = asset
        Spacer(Modifier.height(16.dp))
        Text(
          text = name,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        if (nft != null)
            Row {
                nft.series?.let { series ->
                    Text(
                      text = "$series by",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(2.dp))
                nft.author.let { author ->
                    Text(
                      text = author,
                      style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        Spacer(Modifier.height(8.dp))
        Text( // TODO
          text = "Provider: niftyart.cash",
          style = MaterialTheme.typography.labelLarge,
          fontStyle = FontStyle.Italic
        )
        Spacer(Modifier.height(24.dp))
        if (options.value.isNotEmpty())
            HorizontalRadioButtonGroup(options.value.toList()) { option ->
                showing = option
            }

        if ((a.tokenInfo != null) && (a.tokenInfo?.tddSig == null))
        {
            CenteredText(i18n(S.TokenUnsigned))
        }

            when(showing)
            {
                S.NftCardFront ->
                {
                    val url = a.iconUri
                    val mediaBytes = a.iconBytes
                    if (mediaBytes == null && (url?.protocol == URLProtocol.HTTP || url?.protocol == URLProtocol.HTTPS)) throw UnimplementedException("load from URL")
                    val surfShape = RoundedCornerShape(20.dp)
                    MpMediaView(null, asset.iconBytes, asset.iconUri?.toString(), autoplay = true) { mi, draw ->
                        // Fill the media available space's x or y with the media, but draw a nice box around that space.
                        // Its is amazing that this is so hard.
                        // My approach is to determine the aspect ratio (x/y)of the image, and the aspect ratio of the available space.
                        // If the image AR is > the space AR, then the image is relatively wider than the space so we should fill max width, and
                        // set the height as appropriate.  Otherwise do the equivalent but fill max height

                        val ar = mi.width.toFloat()/mi.height.toFloat()
                        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
                            // maxWidth and maxHeight provide the screen size
                            // min W and H appears to provide not 0dp which makes sense but is trivial, but the minimum size of the Box with the modifiers
                            // applied, in this case fillMaxSize(), so the size of the view
                            val spaceAr = minWidth/minHeight

                            val mod = if (ar >= spaceAr)  // media is wider than the space I have to show it in
                                Modifier.fillMaxWidth().aspectRatio(ar)
                            else
                                Modifier.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                            Surface(shape = surfShape, modifier = mod.align(Alignment.Center).border(WallyModalOutline, surfShape))
                            {
                                draw(null)
                            }
                        }
                    }
                }
                S.Content ->
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
                        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
                            // maxWidth and maxHeight provide the screen size
                            // min W and H appears to provide not 0dp which makes sense but is trivial, but the minimum size of the Box with the modifiers
                            // applied, in this case fillMaxSize(), so the size of the view
                            val spaceAr = minWidth/minHeight

                            val mod = if (ar >= spaceAr)  // media is wider than the space I have to show it in
                                Modifier.fillMaxWidth().aspectRatio(ar)
                            else
                                Modifier.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                            Surface(shape = surfShape, modifier = mod.align(Alignment.Center).border(WallyModalOutline, surfShape))
                            {
                                draw(null)
                            }
                        }
                    }
                }

                S.Private ->
                {
                    val mediaBytes = a.ownerMediaBytes
                    val url = a.ownerMediaCache ?: a.ownerMediaUri?.toString()

                    Text("This content belongs to the owner of the asset")

                    //if (mediaBytes == null && (url?.protocol == URLProtocol.HTTP || url?.protocol == URLProtocol.HTTPS)) throw UnimplementedException("load from URL")

                    val surfShape = RoundedCornerShape(20.dp)

                    MpMediaView(null, mediaBytes, url.toString(), autoplay = true) { mi, draw ->
                        val ar = mi.width.toFloat()/mi.height.toFloat()
                        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
                            val spaceAr = minWidth/minHeight
                            val mod = if (ar >= spaceAr)  // media is wider than the space I have to show it in
                                Modifier.fillMaxWidth().aspectRatio(ar)
                            else
                                Modifier.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                            Surface(shape = surfShape, modifier = mod.align(Alignment.Center).border(WallyModalOutline, surfShape))
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
                        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
                            val spaceAr = minWidth/minHeight
                            val mod = if (ar >= spaceAr)  // media is wider than the space I have to show it in
                                Modifier.fillMaxWidth().aspectRatio(ar)
                            else
                                Modifier.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                            Surface(shape = surfShape, modifier = mod.align(Alignment.Center).border(WallyModalOutline, surfShape))
                            {
                                draw(null)
                            }
                        }
                    }
                }

                S.NftInfo ->
                {
                    Spacer(Modifier.height(16.dp))
                    // TODO formatting (support minimal HTML)
                    Text(nft?.info ?: "")
                }

                S.NftLegal ->
                {
                    Column(
                      modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Spacer(Modifier.height(16.dp))
                        // TODO formatting (support minimal HTML)
                        Text(nft?.license ?: "")
                    }
                }
            }

        // Hack to not overlap with bottom buttons
        Spacer(Modifier.height(100.dp))
    }
}

private val assetListState: MutableMap<String, MutableStateFlow<LazyListState?> > = mutableMapOf() //MutableStateFlow(null)

@Composable
fun AssetScreenUi2(account: Account)
{
    val uriHandler = LocalUriHandler.current
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
    val a = assetFocus
    if (a == null)  // Nothing is in focus, show the whole list
    {
        Column(Modifier.fillMaxSize()) {
            if (assets.size == 0)
            {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = i18n(S.NoAssetsAccount),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = wallyPurple
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
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
                            Box(Modifier.padding(4.dp, 4.dp).fillMaxWidth().background(bkg).clickable {
                                assetFocus = assets[key]
                                assetFocusIndex = indexFreezer
                                nav.go(ScreenId.Assets, assetFocus?.groupInfo?.groupId?.toByteArray())
                            }) {
                                AssetListItemViewUi2(it, 1, false, modifier = Modifier.padding(0.dp, 2.dp))
                            }
                        }
                        index++
                    }
                    item {
                        Spacer(Modifier.height(50.dp))
                    }
                }
            }
        }
    }
    else  // Show a specific asset
    {
        Column (
          modifier = Modifier.fillMaxSize()
        ) {
            AssetViewUi2(a.assetInfo, Modifier.weight(1f))

            Row(
              modifier = Modifier.fillMaxWidth()
                .background(Color.White)
                .padding(2.dp),
              horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconTextButtonUi2(
                  icon = Icons.Outlined.Send,
                  modifier = Modifier.weight(1f),
                  description = i18n(S.Send),
                  color = wallyPurple,
                ) {
                    val defaultAmt = BigDecimal.fromInt(1, tokenDecimalMode(a.assetInfo.tokenInfo?.genesisInfo?.decimal_places ?: 0)) // The default send is to transfer a single "one" (you can change in the send screen) -- whatever that means WRT the # of decimal places
                    account.addAssetToTransferList(a.groupInfo.groupId, defaultAmt)
                    displaySuccess(S.AssetAddedToTransferList)
                }
                IconTextButtonUi2(
                  icon = Icons.Outlined.Balance,
                  modifier = Modifier.weight(1f),
                  description = i18n(S.AssetMarketplace),
                  color = wallyPurple,
                ) {
                    onTradeButton(a.assetInfo, uriHandler)
                }
                IconTextButtonUi2(
                  icon = Icons.Outlined.ArrowBack,
                  modifier = Modifier.weight(1f),
                  description = "Back",
                  color = wallyPurple,
                ) {
                    nav.back()
                }
            }
        }
    }
}
@Composable
fun HorizontalRadioButtonGroup(options: List<Int>, onClick: (Int) -> Unit) {
    var selectedOption by remember { mutableStateOf(options.first()) }
    val horizontalPadding = if (options.size > 5)
        0.dp
    else
        8.dp


    Row(
      modifier = Modifier.fillMaxWidth().wrapContentHeight()
        .border(1.dp, Color.Gray, RoundedCornerShape(16.dp))
        .clip(RoundedCornerShape(16.dp)),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption

            Box(
              modifier = Modifier
                .weight(1f)
                .clickable {
                    selectedOption = option
                    onClick(option)
                }
                .background(if (isSelected) wallyPurple else Color.White)
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
                Text(
                  text = i18n(option),
                  style = MaterialTheme.typography.labelMedium,
                  color = if (isSelected) Color.White else wallyPurple,
                  modifier = Modifier.padding(horizontal = horizontalPadding)
                )
            }
        }
    }
}

@Composable fun AssetDetail(account: Account, a: AssetPerAccount, modifier: Modifier = Modifier)
{
    val uriHandler = LocalUriHandler.current

    Box (
      modifier = Modifier.fillMaxSize(),
    ) {
        AssetViewUi2(a.assetInfo)
    }
    WallyButtonRow {
        WallyBoringIconButton("icons/clipboard.xml", Modifier.width(26.dp).height(26.dp)) {
            onCopyToClipboardButton(a.assetInfo)
        }
        a.groupInfo.groupId.let {
            WallyBoringIconButton("icons/open_in_browser.png", Modifier.width(26.dp).height(26.dp)) {
                account.wallet.chainSelector.explorer("/token/$it").let {
                    uriHandler.openUri(it)
                }
            }
        }
        WallyRoundedTextButton(S.Send, onClick = {
            val defaultAmt = BigDecimal.fromInt(1, tokenDecimalMode(a.assetInfo.tokenInfo?.genesisInfo?.decimal_places ?: 0)) // The default send is to transfer a single "one" (you can change in the send screen) -- whatever that means WRT the # of decimal places
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