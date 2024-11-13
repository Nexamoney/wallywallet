package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.foundation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.AccountUIData
import info.bitcoinunlimited.www.wally.ui.views.OFFER_FAST_FORWARD_GAP
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import info.bitcoinunlimited.www.wally.ui.views.accountListDetail
import info.bitcoinunlimited.www.wally.ui.views.startAccountFastForward
import info.bitcoinunlimited.www.wally.ui.views.uiData
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurpleExtraLight
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurpleLight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.millinow
import org.nexa.libnexakotlin.rem


class AccountUiDataViewModel: ViewModel()
{
    val accountUIData: MutableStateFlow<Map<String, AccountUIData>> = MutableStateFlow(mapOf())

    fun setup() = viewModelScope.launch {
        for(c in accountChangedNotification)
        {
            if (c == "*all changed*")  // this is too long to be a valid account name
            {
                wallyApp?.orderedAccounts(true)?.forEach { account ->
                    setAccountUiDataForAccount(account)
                }
            }
            else
            {
                val act = wallyApp?.accounts?.get(c)
                if (act != null)
                {
                    accountUIData.update {
                        val updatedMap = it.toMutableMap()
                        updatedMap[c] = act.uiData()
                        updatedMap.toMap()
                    }
                }
            }
        }
    }

    fun setAccountUiDataForAccount(account: Account)
    {
        // Updates the MutableStateFlow.value atomically
        accountUIData.update {
            val updatedMap = it.toMutableMap()
            updatedMap[account.name] = account.uiData()
            updatedMap.toMap()
        }
    }

    // This should probably be moved to a viewModel with only one account
    fun fastForwardSelectedAccount()
    {
        selectedAccountUi2.value?.let { selectedAccount ->
            val allAccountsUiData = accountUIData.value.toMutableMap()
            val uiData = allAccountsUiData[selectedAccount.name] ?: AccountUIData(selectedAccount)
            uiData.fastForwarding = true
            allAccountsUiData[selectedAccount.name] = uiData
            accountUIData.value = allAccountsUiData

            startAccountFastForward(selectedAccount) {
                val tmp = accountUIData.value.toMutableMap()
                val uiDatatmp = allAccountsUiData[selectedAccount.name] ?: AccountUIData(selectedAccount)
                uiDatatmp.fastForwarding = it != null
                tmp[selectedAccount.name] = uiData
                accountUIData.value = tmp

                uiData.account.fastforwardStatus = it
                triggerAccountsChanged(uiData.account)
            }
        }
    }
}

@Composable fun AccountListViewUi2(
    nav: ScreenNav,
    accountUIData: Map<String, AccountUIData>,
    accounts: ListifyMap<String, Account>
)
{
    val selAct = selectedAccountUi2.collectAsState().value

    Column (
      modifier = Modifier.wrapContentHeight()
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
        ,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        accounts.forEachIndexed { idx, it ->
            val backgroundColor = if (selAct == it) wallyPurpleLight else wallyPurpleExtraLight
            accountUIData[it.name]?.let {  uiData ->
                AccountItemViewUi2(uiData, idx, selAct == it, devMode, backgroundColor, hasFastForwardButton = false,
                    onClickAccount = {
                        setSelectedAccount(it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            modifier = Modifier.fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors().copy(
                contentColor = Color.Black,
                containerColor = Color.White,
            ),
            onClick = {
                clearAlerts()
                nav.go(ScreenId.NewAccount)
            }
        ) {
            Text(i18n(S.addAccountPlus))
        }

        // Since the thumb buttons cover the bottom most row, this blank bottom row allows the user to scroll the account list upwards enough to
        // uncover the last account.  Its not necessary if there are just a few accounts though.
        if (accounts.size >= 2)
            Spacer(Modifier.height(144.dp))
    }
}

private fun getAccountIconResPath(chainSelector: ChainSelector): String
{
    return when(chainSelector)
    {
        ChainSelector.NEXA -> "icons/nexa_icon.png"
        ChainSelector.NEXATESTNET -> "icons/nexatest_icon.png"
        ChainSelector.NEXAREGTEST -> "icons/nexareg_icon.png"
        ChainSelector.BCH -> "icons/bitcoin_cash_token.xml"
        ChainSelector.BCHTESTNET -> "icons/bitcoin_cash_token.xml"
        ChainSelector.BCHREGTEST -> "icons/bitcoin_cash_token.xml"
    }
}

@Composable fun AccountItemLineTop(uidata: AccountUIData, hasFastForwardButton: Boolean = true, isSelected: Boolean, onClickAccount: () -> Unit)
{
    val curSync = uidata.account.wallet.chainstate?.syncedDate ?: 0
    val offerFastForward = (millinow() /1000 - curSync) > OFFER_FAST_FORWARD_GAP

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
                if (offerFastForward && !uidata.fastForwarding && hasFastForwardButton)
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
                    if (uidata.locked)
                        IconButton(
                          onClick = {
                              onClickAccount()
                              triggerUnlockDialog()
                          }
                        ) {
                            Icon(
                              imageVector = Icons.Default.Lock,
                              contentDescription = "Locked",
                            )
                        }
                    else
                        IconButton(
                          onClick = {
                              onClickAccount()
                              uidata.account.pinEntered = false
                              tlater("assignGuiSlots") {
                                  triggerAssignAccountsGuiSlots()  // In case it should be hidden
                                  later { accountChangedNotification.send(uidata.name) }
                              }
                          }
                        ) {
                            Icon(
                              imageVector = Icons.Default.LockOpen,
                              contentDescription = "Locked",
                            )
                        }
                }

                // Show the account settings gear at the end
                if (isSelected)
                {
                    IconButton(
                      onClick = { nav.go(ScreenId.AccountDetails) },
                      content = {
                          Icon(Icons.Outlined.ManageAccounts, contentDescription = "Account detail")
                      }
                    )
                }
            }
        }
    }
}

@Composable
fun AssetListItem(
  uidata: AccountUIData,
  hasFastForwardButton: Boolean = true,
  isSelected: Boolean,
  backgroundColor: Color,
  onClickAccount: () -> Unit
) {
    val curSync = uidata.account.wallet.chainstate?.syncedDate ?: 0
    val offerFastForward = (millinow() /1000 - curSync) > OFFER_FAST_FORWARD_GAP

    ListItem(
      colors = ListItemDefaults.colors(containerColor = backgroundColor),
      modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
      leadingContent = {
          // Show blockchain icon
          ResImageView(getAccountIconResPath(uidata.chainSelector), Modifier.size(32.dp), "Blockchain icon")
      },
      headlineContent = {
          // Account name and Nexa amount
          Column {
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
      },
      trailingContent = {
          // Account-specific buttons
          Column {
              Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
              ) {
                  val actButtonSize = Modifier.padding(5.dp, 0.dp).size(28.dp)
                  // Fast forward button
                  if (offerFastForward && !uidata.fastForwarding && hasFastForwardButton)
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
                      if (uidata.locked)
                          IconButton(
                            onClick = {
                                onClickAccount()
                                triggerUnlockDialog()
                            }
                          ) {
                              Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                              )
                          }
                      else
                          IconButton(
                            onClick = {
                                onClickAccount()
                                uidata.account.pinEntered = false
                                tlater("assignGuiSlots") {
                                    triggerAssignAccountsGuiSlots()  // In case it should be hidden
                                    later { accountChangedNotification.send(uidata.name) }
                                }
                            }
                          ) {
                              Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Locked",
                              )
                          }
                  }

                  // Show the account settings gear at the end
                  if (isSelected)
                  {
                      IconButton(
                        onClick = { nav.go(ScreenId.AccountDetails) },
                        content = {
                            Icon(Icons.Outlined.ManageAccounts, contentDescription = "Account detail")
                        }
                      )
                  }
              }
          }
      }
    )
}

@Composable
fun AccountItemViewUi2(
    uidata: AccountUIData,
    index: Int,
    isSelected: Boolean,
    devMode: Boolean,
    backgroundColor: Color,
    hasFastForwardButton: Boolean,
    account: Account = uidata.account,
    onClickAccount: () -> Unit
) {
        Row(
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp).fillMaxWidth().testTag("AccountItemView").clickable(onClick = onClickAccount),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                // top line, icon, quantity, and fastforward
                // AccountItemLineTop(uidata, hasFastForwardButton, isSelected, onClickAccount)
                AssetListItem(uidata, hasFastForwardButton, isSelected, backgroundColor, onClickAccount)

                // Fast Forwarding status
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    val ffs = account.fastForwardStatusState.collectAsState().value
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

                if (experimentalUx && isSelected)
                {
                    Spacer(Modifier.height(4.dp))
                    accountListDetail(uidata, index, devMode)
                }

            }
        }
}

