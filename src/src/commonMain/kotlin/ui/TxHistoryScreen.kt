package info.bitcoinunlimited.www.wally.ui

import WalletDb.TxHistory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.*
import kotlinx.datetime.*
import org.nexa.libnexakotlin.*
private val LogIt = GetLog("BU.wally.TxHistory")
fun TransactionHistory.toCSV(): String
{
    val rcvWalletAddr = StringBuilder()
    val rcvForeignAddr = StringBuilder()
    for (i in 0 until tx.outputs.size)
    {
        val out = tx.outputs[i]
        if (incomingIdxes.contains(i.toLong()))
        {
            rcvWalletAddr.append(" " + (out.script.address?.toString() ?: ""))
        }
        else
        {
            rcvForeignAddr.append(" " + (out.script.address?.toString() ?: ""))
        }
    }

    val spentWalletAddr = StringBuilder()
    val spentForeignAddr = StringBuilder()
    for (i in 0L until tx.inputs.size)
    {
        val inp = tx.inputs[i.toInt()]
        val idx = outgoingIdxes.find({ it == i })
        if (idx != null)
        {
            if (idx < spentTxos.size)
                rcvWalletAddr.append(" " + (spentTxos[idx.toInt()].script.address?.toString() ?: "") )
            else
            {
                LogIt.info(sourceLoc() + " data consistency error")
            }
        }
        else
        {
            rcvForeignAddr.append(" " + inp)
        }
    }

    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(date)
    val localTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val fdate = localTime.toString()
    val ret = StringBuilder()
    ret.append(fdate)
    ret.append(",")
    ret.append(incomingAmt - outgoingAmt)
    ret.append(",")
    ret.append(if (incomingAmt > outgoingAmt) "received" else "payment")
    ret.append(",")
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
    ret.append("\"" + note + "\"")
    ret.append(",\n")
    return ret.toString()
}

fun TransactionHistoryHeaderCSV(): String
{
    val ret = StringBuilder()
    ret.append("date")
    ret.append(",")
    ret.append("amount (Satoshi NEX)")
    ret.append(",")
    ret.append("change")
    ret.append(",")
    ret.append("transaction and index")
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
    ret.append("spent foreign addresses")
    ret.append(",")
    ret.append("received addresses")
    ret.append(",")
    ret.append("sent to addresses")
    ret.append(",")

    ret.append("note")
    ret.append(",\n")
    return ret.toString()
}


fun iTransaction.gatherAssets(b:Blockchain):List<AssetInfo>
{
    val ret = mutableListOf<AssetInfo>()
    for (i in outputs)
    {
        val gi = i.script.groupInfo(i.amount)
        if ((gi != null)&&(!gi.isAuthority()))  // TODO not dealing with authority txos in Wally mobile
        {
            val ai = AssetInfo(gi)
            wallyApp?.let { ai.load(b, it.assetManager) }
            ret.add(ai)
        }
    }
    return ret
}

/** returns the destination addresses if this tx is sending, or the receipt addresses if this tx is receiving */
fun TransactionHistory.gatherRelevantAddresses():List<PayAddress>
{
    val data = this
    val addrs = mutableListOf<PayAddress>()
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

/**
 * Transaction history for an account
 */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun TxHistoryScreen(acc: Account, nav: ScreenNav)
{
    val txes = remember { mutableStateListOf<TransactionHistory>() }
    val timeZone = TimeZone.currentSystemDefault()

    /**
     * Populates all transactions
     */
    fun fillTxList()
    {
        //val txList = mutableListOf<TransactionHistory>()
        acc.wallet.forEachTxByDate {
            //txList.add(it)
            txes.add(it)
            false
        }
        //txList.sortBy { it.date }
    }

    fillTxList()

    fun onCopied(text: String)
    {
        setTextClipboard(text)
        displayNotice(S.copiedToClipboard)
    }

    var inUsedSection = false
    var inUnusedSection = false
    LazyColumn {
        txes.forEachIndexed { idx, it ->
            item(key = it.tx.idem.toHex()) {
                val amt = it.incomingAmt - it.outgoingAmt
                val color = if (idx % 2 == 1)
                {
                    WallyRowAbkg1
                }
                else WallyRowAbkg2

                if(idx != 0)
                    Spacer(modifier = Modifier.height(2.dp))

                Column(modifier = Modifier.fillMaxWidth().background(color).padding(1.dp).clickable {
                    onCopied(it.tx.idem.toHex())
                }) {
                    Row {
                        val uriHandler = LocalUriHandler.current
                        if (amt > 0)
                        {
                            ResImageView("icons/receivearrow.xml", modifier = Modifier.size(30.dp))
                        }
                        else if (amt < 0)
                        {
                            ResImageView("icons/sendarrow.xml", modifier = Modifier.size(30.dp))
                        }
                        else Spacer(Modifier.size(30.dp))
                        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(it.date)
                        val localTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                        Text(localTime.date.toString() + "\n" + localTime.time.toString())
                        CenteredFittedWithinSpaceText(text = acc.cryptoFormat.format(acc.fromFinestUnit(amt)), startingFontScale = 1.5, fontWeight = FontWeight.Bold,
                          modifier = Modifier.weight(1f))
                        val uri = if (it.chainSelector == ChainSelector.NEXA)
                            NEXA_EXPLORER_URL + "/tx/${it.tx.idem.toHex()}"
                        else if (it.chainSelector == ChainSelector.NEXATESTNET)
                            NEXA_TESTNET_EXPLORER_URL + "/tx/${it.tx.idem.toHex()}"
                        else null
                        if (uri != null)
                        {
                            WallyBoringButton({ uriHandler.openUri(uri) }, modifier = Modifier.padding(0.dp, 0.dp, 10.dp, 0.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, tint = colorConfirm, contentDescription = "view transaction")
                            }
                        }
                    }
                    CenteredFittedText(text = it.tx.idem.toHex(), fontWeight = FontWeight.Bold, modifier = Modifier)
                    Spacer(Modifier.size(3.dp))

                    val addrs = it.gatherRelevantAddresses()
                    for (a in addrs)
                    {
                       CenteredFittedText(text = a.toString())
                    }

                    if (it.note.isNotBlank()) CenteredText(text = it.note)
                    val assets = it.tx.gatherAssets(acc.wallet.blockchain)
                    if (assets.isNotEmpty())
                    {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            var index = 0
                            assets.forEach {
                                val entry = it
                                val indexFreezer = index  // To use this in the item composable, we need to freeze it to a val, because the composable is called out-of-scope
                                Box(Modifier.padding(4.dp, 1.dp).fillMaxWidth().background(WallyAssetRowColors[indexFreezer % WallyAssetRowColors.size])) {
                                        AssetListItemView(entry, 0, Modifier.padding(0.dp, 2.dp))
                                    }
                                index++
                            }
                        }
                    }
                }
            }
        }
    }
}
