@file:OptIn(ExperimentalUnsignedTypes::class)

package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.theme.WallyRowAbkg1
import info.bitcoinunlimited.www.wally.ui2.theme.WallyRowAbkg2
import info.bitcoinunlimited.www.wally.ui2.theme.defaultListHighlight
import info.bitcoinunlimited.www.wally.ui2.themeUi2.defaultFontSize
import info.bitcoinunlimited.www.wally.ui2.views.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.accountlistview")
private val accountListState:MutableStateFlow<LazyListState?> = MutableStateFlow(null)


@Composable fun AccountListView(nav: ScreenNav, selectedAccount: MutableStateFlow<Account?>,
  modifier: Modifier = Modifier, onAccountSelected: (Account) -> Unit)
{
    val accountUIData = remember { mutableStateMapOf<String, AccountUIData>() }
    val accounts = accountGuiSlots.collectAsState()
    if (accountListState.value == null) accountListState.value = rememberLazyListState()

    LaunchedEffect(true)
    {
        for(c in accountChangedNotification)
        {
            if (c == "*all changed*")  // this is too long to be a valid account name
            {
                wallyApp?.orderedAccounts(true)?.forEach {
                    val uid = it.uiData()
                    accountUIData[it.name] = uid
                }
            }
            else
            {
                val act = wallyApp?.accounts?.get(c)
                if (act != null)
                {
                    accountUIData[c] = act.uiData()
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val tmp = accountListState.collectAsState(scope.coroutineContext).value ?: rememberLazyListState()

    val selAct = selectedAccount.collectAsState().value
    if (false && selAct != null) // && experimentalUx)
    {
        if (accountUIData[selAct.name] == null) accountUIData[selAct.name] = selAct.uiData()
        AccountItemView(accountUIData[selAct.name]!!, 0, true, devMode, Color.Transparent,
          onClickAccount = { onAccountSelected(selAct) },
          onClickGearIcon = {
              nav.go(ScreenId.AccountDetails)
          })
    }
    else
    {
        LazyColumn(state = tmp, horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
            accounts.value.forEachIndexed { idx, it ->
                item(key = it.name) {
                    // I would think that capturing this data would control redraw of each item, but it appears to not do so.
                    // Redraw is controlled of the entire AccountListView, or not at all.
                    //val anyChanges: MutableState<AccountUIData> = remember { mutableStateOf(it.uiData()) }
                    // scope.launch { listState.animateScrollToItem(idx, -1) }  // -1 puts our item more in the center
                    if (accountUIData[it.name] == null) accountUIData[it.name] = it.uiData()
                    val backgroundColor = if (selAct == it) defaultListHighlight else if (idx and 1 == 0) WallyRowAbkg1 else WallyRowAbkg2
                    AccountItemView(accountUIData[it.name]!!, idx, selAct == it, devMode, backgroundColor,
                      onClickAccount = { onAccountSelected(it) },
                      onClickGearIcon = {
                          nav.go(ScreenId.AccountDetails)
                      })
                }
            }
            // Since the thumb buttons cover the bottom most row, this blank bottom row allows the user to scroll the account list upwards enough to
            // uncover the last account.  Its not necessary if there are just a few accounts though.
            if (accounts.value.size >= 2)
            {
                item(key = "") {
                    Spacer(Modifier.height(150.dp))
                }
            }

        }
    }
}

@Composable fun AccountItemLineTop(uidata: AccountUIData, isSelected: Boolean,onClickAccount: () -> Unit, onClickGearIcon: () -> Unit)
{
    val curSync = uidata.account.wallet.chainstate?.syncedDate ?: 0
    val offerFastForward = (millinow()/1000 - curSync) > OFFER_FAST_FORWARD_GAP

    Row(modifier = Modifier.fillMaxWidth())
    {
        // Show blockchain icon
        Column(Modifier.align(Alignment.CenterVertically).padding(0.dp,0.dp,4.dp, 0.dp)) {
            ResImageView(getAccountIconResPath(uidata.chainSelector), Modifier.size(32.dp).align(Alignment.Start), "Blockchain icon")
        }
        // Account name and Nexa amount
        Column(modifier = Modifier.weight(1f)) {
            // Account Name
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = uidata.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            // Nexa Amount
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val startingBalStyle = FontScaleStyle(1.75)
                val startingCcStyle = FontScaleStyle(0.6)
                var balTextStyle by remember { mutableStateOf(startingBalStyle) }
                var ccTextStyle by remember { mutableStateOf(startingCcStyle) }
                var showingCurrencyCode:String by remember { mutableStateOf(uidata.currencyCode) }
                var drawBal by remember { mutableStateOf(false) }
                var drawCC by remember { mutableStateOf(false) }
                var scale by remember { mutableStateOf(1.0) }
                Text(text = uidata.balance, style = balTextStyle, color = uidata.balColor, modifier = Modifier.padding(0.dp).drawWithContent { if (drawBal) drawContent() }, textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
                  onTextLayout = { textLayoutResult ->
                      if (textLayoutResult.didOverflowWidth)
                      {
                          scale = scale * 0.90
                          balTextStyle = startingBalStyle.copy(fontSize = startingBalStyle.fontSize * scale)
                      }
                      else drawBal = true
                  })

                if (showingCurrencyCode.length > 0) Text(text = showingCurrencyCode ?: "", style = ccTextStyle, modifier = Modifier.padding(5.dp, 0.dp).fillMaxWidth().drawWithContent { if (drawCC) drawContent() }, textAlign = TextAlign.Start, maxLines = 1, softWrap = false,
                  onTextLayout = { textLayoutResult ->
                      if (textLayoutResult.didOverflowWidth)
                      {
                          scale = scale * 0.90
                          if (scale > 0.40) // If this field gets too small, just drop it
                          {
                              ccTextStyle = ccTextStyle.copy(fontSize = startingCcStyle.fontSize * scale)
                          }
                          else
                          {
                              showingCurrencyCode = ""
                              drawCC = true
                          }
                      }
                      else drawCC = true
                  }
                )
            }
            // Approximately amount or as of date (we don't want to show a fiat amount if we are syncing)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                uidata.approximately?.let {
                    Text(modifier = Modifier.fillMaxWidth(), text = it, fontSize = 16.sp, color = uidata.approximatelyColor, fontWeight = uidata.approximatelyWeight, textAlign = TextAlign.Start)
                }
            }
        }

        // Account-specific buttons
        Column(modifier = Modifier.align(Alignment.CenterVertically)) {
            Row(
              modifier = Modifier.wrapContentWidth(),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically
            ) {
                val actButtonSize = Modifier.padding(5.dp, 0.dp).size(28.dp)
                // Fast forward button
                if (offerFastForward && !uidata.fastForwarding)
                {
                    ResImageView("icons/fastforward.png", modifier = actButtonSize.clickable {
                        uidata.fastForwarding = true
                        startAccountFastForward(uidata.account) {
                            uidata.account.fastforwardStatus = it
                            triggerAccountsChanged(uidata.account)
                        }
                    })
                }
                // Lock
                if (uidata.lockable)
                {
                    ResImageView(if (uidata.locked) "icons/lock.xml" else "icons/unlock.xml",
                      modifier = actButtonSize.clickable {
                          onClickAccount()  // I want to select the whole account & then try to unlock/lock
                          if (uidata.locked)
                          {
                              triggerUnlockDialog()
                          }
                          else
                          {
                              uidata.account.pinEntered = false
                              tlater("assignGuiSlots") {
                                  triggerAssignAccountsGuiSlots()  // In case it should be hidden
                                  later { accountChangedNotification.send(uidata.name) }
                              }
                          }
                      })
                }
                // else Spacer(actButtonSize) // these would stop the buttons from moving when other buttons disappear

                // Show the account settings gear at the end
                if (isSelected)
                {
                    ResImageView("icons/gear.xml", actButtonSize.clickable(onClick = onClickGearIcon).testTag("accountSettingsGearIcon"))
                }
                // else Spacer(actButtonSize) // these would stop the buttons from moving when other buttons disappear
            }
        }
    }
}

@Composable
fun AccountItemView(
  uidata: AccountUIData,
  index: Int,
  isSelected: Boolean,
  devMode: Boolean,
  backgroundColor: Color,
  onClickAccount: () -> Unit,
  onClickGearIcon: () -> Unit
) {
    Box(modifier = Modifier.testTag("AccountItemView").fillMaxWidth().padding(2.dp).background(backgroundColor).clickable(onClick = onClickAccount), contentAlignment = Alignment.Center) {
        // Each account
        Row(modifier = Modifier.padding(5.dp, 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

            Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                // top line, icon, quantity, and fastforward
                Row(modifier = Modifier.fillMaxWidth()) {
                    AccountItemLineTop(uidata, isSelected, onClickAccount, onClickGearIcon)
                }

                // Fast Forwarding status
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    val ffs = uidata.ffStatus
                    if (uidata.fastForwarding && (ffs != null))
                    {
                        Text(modifier = Modifier.fillMaxWidth(), text = i18n(S.fastforwardStatus) % mapOf("info" to ffs), fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }

                // includes (amount)   --- NEXA pending amount
                if (uidata.unconfBal.isNotEmpty()) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Text(text = uidata.unconfBal, color = uidata.unconfBalColor)
                }
                // Devmode connectivity text
                if (devMode)
                {
                    // Give a little extra height because the unicode up and down arrows don't fit causing the line to go bigger.
                    var lh = LocalTextStyle.current.lineHeight
                    if (lh == TextUnit.Unspecified) lh = defaultFontSize
                    val devModeTextStyle = LocalTextStyle.current.copy(lineHeightStyle = LineHeightStyle(
                      alignment = LineHeightStyle.Alignment.Proportional,
                      trim = LineHeightStyle.Trim.None),
                      lineHeight = lh.times(1.05)
                      )
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp,4.dp,4.dp, 4.dp), horizontalArrangement = Arrangement.Start) {
                        Text(text = uidata.devinfo, fontSize = 12.sp, maxLines = 5, minLines = 3, style = devModeTextStyle, textAlign = TextAlign.Center)
                    }
                }

                /*
                if (experimentalUx && isSelected)
                {
                    Spacer(Modifier.height(4.dp))
                    accountListDetail(uidata, index, devMode)
                }
                 */
            }
        }

    }
}

/*
@Composable fun accountListDetail(uidata: AccountUIData, index:Int, devMode:Boolean)
{
    val acc = uidata.account
    val ai = acc.assetList()
    val nftlls = rememberLazyListState()
    val toklls = rememberLazyListState()
    val histlls = rememberLazyListState()

    var numNFTs = 0
    var numAssets = 0
    var numContracts = 0  // TODO contracts
    ai.forEach {
        if (it.groupInfo.groupId.subgroupData().size >= 32) numNFTs++
        if ((it.assetInfo.ticker != null) && (it.groupInfo.groupId.subgroupData().size < 32)) numAssets++
    }

    // Decide how many rows of NFTs to show by adding some as the # of NFTs owned grows
    val NftRows = min(4,numNFTs/40)
    // Show NFTs/SFTs by image
    LazyRow(state = nftlls, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        var index = 0
        val iter = ai.iterator()
        val itemGroups = mutableListOf<List<AssetPerAccount>>()
        var curRow = mutableListOf<AssetPerAccount>()
        while(iter.hasNext())
        {
            val it = iter.next()
            if (curRow.size == NftRows)
            {
                itemGroups.add(curRow.toList())
                curRow.clear()
            }
            // What is a NFT vs a token?  We will use whether the group likely commits to a NFT data file as the differentiator
            if (it.groupInfo.groupId.subgroupData().size >= 32)
                curRow.add(it)
        }

        for (row in itemGroups)
        {
            item(key = index) {
                Column {
                    for (i in row)
                    {
                        var entry: AssetPerAccount = i
                        val asset = entry.assetInfo
                        MpMediaView(asset.iconImage, asset.iconBytes, asset.iconUri?.toString(), hideMusicView = true) { mi, draw ->
                                val m = Modifier.background(Color.Transparent).padding(1.dp).size(40.dp, 40.dp).clickable {
                                    nav.go(ScreenId.Assets,  entry?.groupInfo?.groupId?.toByteArray())
                                }
                                draw(m)
                            }
                    }
                }
            }
            index++
        }
    }
    // Show non-NFTs by ticker
    Spacer(Modifier.height(2.dp))
    if (numAssets>0)
    {
        Text(i18n(S.assetsColon))
        LazyRow(state = toklls, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            var index = 0
            ai.forEach {
                val key = it.groupInfo.groupId
                val entry = it
                val indexFreezer = index  // To use this in the item composable, we need to freeze it to a val, because the composable is called out-of-scope
                item(key = key.toByteArray()) {
                    val asset = entry.assetInfo
                    val tck = asset.ticker
                    val qty = tokenAmountString(it.groupInfo.tokenAmt, asset.tokenInfo?.genesisInfo?.decimal_places)
                    // What is a NFT vs a token with an icon?  We will use whether the group likely commits to a NFT data file as the differentiator
                    if ((tck != null) && (it.groupInfo.groupId.subgroupData().size < 32))
                    {
                        Spacer(Modifier.width(2.dp))
                        Text("$tck:$qty")
                        Spacer(Modifier.width(2.dp))
                    }
                }
                index++
            }
        }
    }
    WallyDivider()
    Spacer(Modifier.height(2.dp))
    // Show history
    val SendRecvIconSize = 26.dp
    // Making this a scrollable lazy column would require that the account history list be fully updated on every account change (expensive).
    // Instead the "recentHistory" is only filled with the last 10 items, so no need to make this scrollable
    Column(modifier = Modifier.clickable { nav.go(ScreenId.TxHistory) }) {
        uidata.recentHistory.forEachIndexed { idx, txh ->
            val color = Color.Transparent // if (idx % 2 == 1) WallyRowAbkg1 else WallyRowAbkg2
            Row(modifier = Modifier.padding(2.dp).fillMaxWidth().background(color)) {
                    val amt = txh.incomingAmt - txh.outgoingAmt
                    if (amt !=0L)
                        ResImageView(if (amt>0) "icons/receivearrow.xml" else "icons/sendarrow.xml", modifier = Modifier.size(SendRecvIconSize))
                    else Spacer(Modifier.size(SendRecvIconSize))
                    Spacer(Modifier.width(4.dp))
                    //CenteredFittedWithinSpaceText(text = acc.cryptoFormat.format(acc.fromFinestUnit(amt)), startingFontScale = 1.5, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(acc.cryptoFormat.format(acc.fromFinestUnit(amt)), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (txh.date > 1577836800000) Text(formatLocalEpochMilliseconds(txh.date, " "))
            }
        }
    }
}
*/
