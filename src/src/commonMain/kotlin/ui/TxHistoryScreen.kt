package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.*
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.*
import org.nexa.libnexakotlin.*
import org.nexa.threads.Mutex
import org.nexa.threads.millisleep
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val LogIt = GetLog("BU.wally.TxHistory")
@OptIn(ExperimentalTime::class)
fun TransactionHistory.toCSV(): String
{
    val rcvWalletAddr = StringBuilder()
    val rcvForeignAddr = StringBuilder()

    // Note asset change right now will show up as a spend and a receive
    val assetsReceived = StringBuilder()
    for (i in 0 until tx.outputs.size)
    {
        val out = tx.outputs[i]
        val scr = out.script
        val gi = out.groupInfo()
        if (incomingIdxes.contains(i.toLong()))
        {
            rcvWalletAddr.append(" " + (out.script.address?.toString() ?: ""))
            if (gi!=null)
            {
                assetsReceived.append("${gi.tokenAmount}_of_${gi.groupId} ")
            }
        }
        else
        {
            rcvForeignAddr.append(" " + (out.script.address?.toString() ?: ""))
        }
    }

    val spentWalletAddr = StringBuilder()
    val spentForeignAddr = StringBuilder()
    val assetsSent = StringBuilder()
    for (i in 0L until tx.inputs.size)
    {
        val inp = tx.inputs[i.toInt()]
        val idx = outgoingIdxes.find({ it == i })
        if (idx != null)  // Its one of ours
        {
            val prevOutScript = inp.spendable.priorOutScript
            val gi = prevOutScript.groupInfo(inp.spendable.amount)
            if (gi!=null)
            {
                assetsSent.append("${gi.tokenAmount}_of_${gi.groupId} ")
            }
            spentWalletAddr.append(" " + (prevOutScript.address?.toString() ?: "") )
        }
        else
        {
            val prevOutScript = inp.spendable.priorOutScript
            if (prevOutScript.size > 0)
                spentForeignAddr.append(" " + (prevOutScript.address?.toString() ?: "") )
        }
    }

    val instant = Instant.fromEpochMilliseconds(date)
    val localTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val fdate = localTime.format(DATE_TIME_FORMAT)
    val ret = StringBuilder()
    // date
    ret.append(fdate)
    ret.append(",")
    // amount
    ret.append(NexaInputFormat.format(SatToNexa(incomingAmt - outgoingAmt)))
    ret.append(",")
    // received
    ret.append(NexaInputFormat.format(SatToNexa(incomingAmt)))
    ret.append(",")
    // sent
    ret.append(NexaInputFormat.format(SatToNexa(outgoingAmt)))
    ret.append(",")
    // fee
    ret.append(NexaInputFormat.format(SatToNexa(tx.fee)))
    ret.append(",")
    // tx
    ret.append(tx.idem.toHex())
    ret.append(",")
    ret.append(basisOverride?.let { CurrencySerializeFormat.format(it) } ?: "")
    ret.append(",")
    ret.append(saleOverride?.let { CurrencySerializeFormat.format(it) } ?: "")
    ret.append(",")
    ret.append(priceWhenIssued.let { CurrencySerializeFormat.format(it) } ?: "")
    ret.append(",")
    ret.append(priceWhatFiat)
    ret.append(",")
    ret.append(spentWalletAddr.toString())
    ret.append(",")
    ret.append(spentForeignAddr.toString())
    ret.append(",")
    ret.append(rcvWalletAddr.toString())
    ret.append(",")
    ret.append(rcvForeignAddr.toString())
    ret.append(",")
    // assets received
    ret.append(assetsReceived.toString())
    ret.append(",")
    // assets sent
    ret.append(assetsSent.toString())
    ret.append(",")
    // note
    ret.append("\"" + note + "\"")
    ret.append(",\n")
    return ret.toString()
}

fun TransactionHistoryHeaderCSV(): String
{
    val ret = StringBuilder()
    ret.append("date")
    ret.append(",")
    ret.append("amount")
    ret.append(",")
    ret.append("amount incoming")
    ret.append(",")
    ret.append("amount outgoing")
    ret.append(",")
    ret.append("fee")
    ret.append(",")
    ret.append("transaction")
    ret.append(",")
    ret.append("basis")
    ret.append(",")
    ret.append("sale")
    ret.append(",")
    ret.append("price")
    ret.append(",")
    ret.append("fiat currency")
    ret.append(",")
    ret.append("spent wallet addresses")
    ret.append(",")
    ret.append("incoming from addresses")
    ret.append(",")
    ret.append("received into addresses")
    ret.append(",")
    ret.append("sent to addresses")
    ret.append(",")
    ret.append("assets received")
    ret.append(",")
    ret.append("assets sent")
    ret.append(",")
    ret.append("note")
    ret.append(",\n")
    return ret.toString()
}


fun iTransaction.gatherAssets(addrFilter: (PayAddress?) -> Boolean = { true}):List<AssetPerAccount>
{
    val ret = mutableListOf<AssetPerAccount>()
    for (i in outputs)
    {
        val addr = i.script.address
        if (addrFilter(addr))  // only gather assets relevant to this wallet
        {
            val gi = i.script.groupInfo(i.amount)
            if ((gi != null) && (!gi.isAuthority()))  // TODO not dealing with authority txos in Wally mobile
            {
                val ai = wallyApp?.assetManager?.track(gi.groupId, null)
                ai?.let { ret.add(AssetPerAccount(gi, ai)) }
            }
        }
    }
    return ret
}


/** returns the destination addresses if this tx is sending, or the receipt addresses if this tx is receiving */
fun TransactionHistory.gatherRelevantAddresses():Set<PayAddress>
{
    val data = this
    val addrs = mutableSetOf<PayAddress>()
    if (data.incomingAmt > data.outgoingAmt)  // receive
    {
        for (i in data.incomingIdxes)
        {
            if (i < data.tx.outputs.size)
            {
                val out = data.tx.outputs[i.toInt()]
                val tp = out.script.parseTemplate(out.amount)
                val addr = out.script.address
                if (addr!=null)
                {
                    if (tp != null)
                    {
                        if (tp.groupInfo == null) addrs.add(addr)
                        // TODO I received a token
                    }
                    else
                        addrs.add(addr)
                }
            }
        }
    }
    else  // Send
    {
        // For a send, we want to show all the addresses we sent TO, so all the addresses that are NOT ours
        for (i in 0L until data.tx.outputs.size)
        {
            if (!data.incomingIdxes.contains(i))
            {
                val addr = data.tx.outputs[i.toInt()].script.address
                if (addr != null)
                    addrs.add(addr)
            }
        }
    }
    return addrs
}

private val txHistoryInfo = MutableStateFlow<Array<MutableStateFlow<TransactionHistory?>>?>(null)
private val txHistoryAccount = MutableStateFlow<Account?>(null)
private val txHistoryMutex = Mutex("txHistory")
fun calcTxHistoryInfo(acc : Account)
{
    val numTxes = min(10000, acc.wallet.getTxCount()+10)
    val txes = Array<MutableStateFlow<TransactionHistory?>>(numTxes.toInt(), { MutableStateFlow<TransactionHistory?>(null) })

    txHistoryMutex.lock {
        txHistoryAccount.value = acc
        txHistoryInfo.value = txes
    }

    var count = 0
    var date = Long.MAX_VALUE
    while(count < numTxes)
    {
        var returnedNothing = true
        acc.wallet.forEachTxByDate(date, 50) {
            txes[count].value = it
            count += 1
            date = it.date-1
            returnedNothing = false
            (count > numTxes) // Stop if its too many
        }
        if (returnedNothing) break
        millisleep(1U)  // give away the processor so the GUI can run
    }
}

fun makeShareableHistory(acc : Account):String
{
    val hinfo = txHistoryInfo.value
    if (hinfo == null) return "account history not calculated"
    val sb = StringBuilder()
    sb.append(TransactionHistoryHeaderCSV())
    for (msf in hinfo)
    {
        val txh = msf.value
        if (txh != null)
        {
            sb.append(txh.toCSV())
        }
    }
    return sb.toString()
}

/**
 * Transaction history for an account
 */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun TxHistoryScreen(acc: Account, nav: ScreenNav)
{
    // You cannot use a function call to trigger some action in compose since stuff can be randomly recomposed or cached and NOT recomposed
    // We also can't regenerate the address history list inline with recomposition because its too slow.
    // We don't need this view to be "live" WRT new transaction coming in.
    // So we choose to asynchronously calculate it whenever its null or when the passed account changes.
    // (and we erase it to null whenever we leave this screen)
    if (txHistoryAccount.value != acc)
    {
        txHistoryInfo.value = null
    }
    if ((txHistoryInfo.value == null) || (txHistoryAccount.value != acc)) laterJob {
        calcTxHistoryInfo(acc)
    }
    val oldshare = ToBeShared
    ToBeShared = {
        makeShareableHistory(acc)
    }
    nav.onDepart {
        // actually keep this around in case the user goes back
        txHistoryInfo.value = null
        ToBeShared = oldshare
    }

    fun onCopied(text: String)
    {
        setTextClipboard(text)
        displayNotice(S.copiedToClipboard)
    }

    val txes = txHistoryInfo.collectAsState().value
    if (txes == null)
    {
        CenteredSectionText(S.Processing)
    }
    else
    {
        LazyColumn {
            txes.forEachIndexed { idx, it ->
                item(key = idx) {
                    val txh = it.collectAsState().value
                    if (txh == null)
                    {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    else
                    {
                        val amt = txh.incomingAmt - txh.outgoingAmt
                        val color = wallyPurpleExtraLight // if (idx % 2 == 1) WallyRowAbkg1 else WallyRowAbkg2
                        if (idx != 0) Spacer(modifier = Modifier.height(6.dp))  // Space in between each record

                        Column(modifier = Modifier.fillMaxWidth().background(color).padding(1.dp).clickable {
                            onCopied(txh.tx.idem.toHex())
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (devMode) Text(idx.toString(), fontSize = 8.sp)
                                if (amt != 0L) ResImageView(if (amt > 0) "icons/receivearrow.xml" else "icons/sendarrow.xml", modifier = Modifier.size(30.dp))
                                else Spacer(Modifier.size(30.dp))
                                if (txh.date > 1577836800000) Text(formatLocalEpochMilliseconds(txh.date, "\n"), Modifier.padding(3.dp), maxLines = 2,
                                  textAlign=TextAlign.Center)  // jan 1 2020, before the genesis block
                                else
                                {
                                    LogIt.info(sourceLoc() + ": tx with date ${txh.date}")
                                }
                                Box(modifier = Modifier.padding(3.dp).weight(1f), contentAlignment = Alignment.Center) {
                                    BasicText(text = acc.cryptoFormat.format(acc.fromFinestUnit(amt)), modifier = Modifier,
                                      style = TextStyle(fontWeight = FontWeight.Bold),
                                      maxLines = 1,
                                      autoSize = TextAutoSize.StepBased(8.sp, 24.sp, 0.5.sp))
                                }
                                val uri = txh.chainSelector.explorer("/tx/${txh.tx.idem.toHex()}")
                                if (devMode) WallyBoringButton({
                                    LogIt.info("send tx to wallywallet.org")
                                    val versionNumber = (i18n(S.version) % mapOf("ver" to Version.VERSION_NUMBER + "-" + Version.GIT_COMMIT_HASH, "date" to Version.BUILD_DATE)).encodeURLParameter()
                                    val url = Url("http://wallywallet.org/debug/submit/tx?txhex=${txh.tx.toHex()}&info=wallywallet$versionNumber")
                                    laterJob {
                                        try
                                        {
                                            displayNotice(S.sending)
                                            val result = url.readText()
                                            LogIt.info("send tx to wallywallet.org: $result")
                                        }
                                        catch (e: Exception)
                                        {
                                            LogIt.error("cannot send tx to wallywallet.org: $e")
                                            displayError(S.connectionException)
                                        }
                                    } }, modifier = Modifier.size(30.dp).padding(0.dp, 0.dp, 10.dp, 0.dp)) {
                                    ResImageView("icons/bug.xml", modifier = Modifier.size(30.dp))
                                }
                                WallyBoringButton({ openUrl(uri) }, modifier = Modifier.padding(0.dp, 0.dp, 10.dp, 0.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, tint = colorConfirm, contentDescription = "view transaction")
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    BasicText(text = txh.tx.idem.toHex(), modifier = Modifier,
                                      style = TextStyle(fontWeight = FontWeight.Bold),
                                      maxLines = 1,
                                      autoSize = TextAutoSize.StepBased(6.sp, 12.sp))
                                }
                            Spacer(Modifier.size(3.dp))

                            val addrs = txh.gatherRelevantAddresses()
                            for (a in addrs)
                            {
                                // padding indents the starting side
                                Box(modifier = Modifier.padding(PaddingValues(10.dp,0.dp,0.dp,0.dp)).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    BasicText(text = a.toString(), maxLines = 1, autoSize = TextAutoSize.StepBased(4.sp, 12.sp))
                                }
                            }

                            if (txh.note.isNotBlank()) CenteredText(text = txh.note)
                            val assets = txh.tx.gatherAssets({
                                // We are going to use the native coin as a hint as to whether this transaction is sending or receiving
                                // If its sending, just look for assets that left this wallet
                                // If its receiving, look for assets coming in.
                                // TODO: look at inputs and accurately describing sending/receiving
                                if (it == null) false
                                else
                                {
                                    val result: Boolean = if (amt > 0) acc.wallet.isWalletAddress(it)
                                    else !acc.wallet.isWalletAddress(it)
                                    result
                                }
                            })
                            if (assets.isNotEmpty())
                            {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    var index = 0
                                    assets.forEach {
                                        val entry = it
                                        val indexFreezer = index  // To use this in the item composable, we need to freeze it to a val, because the composable is called out-of-scope
                                        Box(Modifier.padding(4.dp, 1.dp).fillMaxWidth().background(WallyAssetRowColors[indexFreezer % WallyAssetRowColors.size])) {
                                            AssetListItemViewOld(entry, 0, false, Modifier.padding(0.dp, 2.dp))
                                        }
                                        index++
                                    }
                                }
                            }
                            Spacer(Modifier.size(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssetListItemViewOld(assetPerAccount: AssetPerAccount, verbosity: Int = 1, allowAmountEdit: Boolean = false, modifier: Modifier = Modifier)
{
    var apc by remember { mutableStateOf(assetPerAccount) }
    val asset = apc.assetInfo
    val nftRaw = asset.nftObservable.collectAsState()
    val nft = nftRaw.value
    val assetName = asset.nameObservable.collectAsState()
    // This automatically re-renders the compose view on changes to loadStateObservable
    @Suppress("unused")
    val loadState = asset.loadStateObservable.collectAsState()

    Column(modifier = modifier) {
        if ((devMode)&&(verbosity>0)) SelectionContainer(Modifier.fillMaxWidth()) { CenteredFittedText(asset.groupId.toStringNoPrefix()) }
        Row {
            val hasImage = if (asset.iconImage != null) "yes" else "null"
            LogIt.info("Asset ${asset.name} icon Image ${hasImage} icon bytes: ${asset.iconBytes?.size} icon url: ${asset.iconUri}")
            MpMediaView(asset.iconImage, asset.iconBytes, asset.iconUri?.toString(), hideMusicView = true) { mi, draw ->
                val m = (if (verbosity > 0) Modifier.background(Color.Transparent).size(64.dp, 64.dp)
                else  Modifier.background(Color.Transparent).size(26.dp, 26.dp)).align(Alignment.CenterVertically)
                draw(m)
            }

            // If its an NFT, don't show the quantity if they have just 1
            if ((nft == null)||(apc.groupInfo.tokenAmount != 1L))
            {
                if (allowAmountEdit)
                {
                    // Note the "default" (unedited) amount is ALL tokens.  If you change this default, you must also change it in the actuallySend() function.
                    val amt = assetPerAccount.editableAmount?.toPlainString() ?: tokenAmountString(apc.groupInfo.tokenAmount, asset.tokenInfo?.genesisInfo?.decimal_places)
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
                    val amt = tokenAmountString(apc.groupInfo.tokenAmount, asset.tokenInfo?.genesisInfo?.decimal_places)
                    val lenAdjust = 1.0 // 5.0/max(amt.length,5)
                    val fontSize = if (verbosity > 0) 2.0*lenAdjust else 1.0*lenAdjust
                    Box(modifier = modifier.weight(0.50f).align(Alignment.CenterVertically)) {
                        SelectionContainer {
                            CenteredFittedText(amt, fontSize)
                        }
                    }
                }
                Spacer(modifier.width(4.dp))
            }

            Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                var name = (if ((nft != null) && (nft.title.length > 0)) nft.title else assetName.value)
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
                        CenteredText(i18n(S.NftImproperConstruction), modifier.background(
                            colorWarning
                        ))
                    }
                    else
                    {
                        val urlS = asset.docUrl
                        if (urlS != null)
                        {
                            val url = com.eygraber.uri.Url.parseOrNull(urlS)
                            val host = try
                            {
                                url?.host  // although host is supposedly not null, I can get "java.lang.IllegalArgumentException: Url requires a non-null host"
                            }
                            catch (e: IllegalArgumentException)
                            {
                                null
                            }
                            if (host != null)
                            {
                                CenteredText(host, TextStyle(fontSize = defaultFontSize * 0.75, fontWeight = FontWeight.Light,  fontStyle = FontStyle.Italic))
                            }
                        }
                    }
                }
                else
                {
                    if (name == null) name = asset.ticker ?: asset.groupId.toString()
                    val author = if ((nft?.author != null) && (nft.author.length > 0)) ", " + nft.author else ""
                    CenteredFittedText(name + author)
                }
            }

        }
    }
}

