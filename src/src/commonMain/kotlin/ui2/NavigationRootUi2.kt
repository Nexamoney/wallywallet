package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.ui.theme.*
import androidx.compose.ui.zIndex
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AddressHistoryScreen
import info.bitcoinunlimited.www.wally.ui.AssetInfoPermScreen
import info.bitcoinunlimited.www.wally.ui.EXPERIMENTAL_UX_MODE_PREF
import info.bitcoinunlimited.www.wally.ui.GuiDriver
import info.bitcoinunlimited.www.wally.ui.IdentityEditScreen
import info.bitcoinunlimited.www.wally.ui.IdentityPermScreen
import info.bitcoinunlimited.www.wally.ui.IdentityScreen
import info.bitcoinunlimited.www.wally.ui.IdentitySession
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.SendToPermScreen
import info.bitcoinunlimited.www.wally.ui.ShowIt
import info.bitcoinunlimited.www.wally.ui.SplitBillScreen
import info.bitcoinunlimited.www.wally.ui.ToBeShared
import info.bitcoinunlimited.www.wally.ui.TricklePayScreen
import info.bitcoinunlimited.www.wally.ui.TxHistoryScreen
import info.bitcoinunlimited.www.wally.ui.UnlockView
import info.bitcoinunlimited.www.wally.ui.accountGuiSlots
import info.bitcoinunlimited.www.wally.ui.assignAccountsGuiSlots
import info.bitcoinunlimited.www.wally.ui.currentReceiveShared
import info.bitcoinunlimited.www.wally.ui.externalDriver
import info.bitcoinunlimited.www.wally.ui.isSoftKeyboardShowing
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.onShareButton
import info.bitcoinunlimited.www.wally.ui.preferenceDB
import info.bitcoinunlimited.www.wally.ui.showAssetsPref
import info.bitcoinunlimited.www.wally.ui.showIdentityPref
import info.bitcoinunlimited.www.wally.ui.showTricklePayPref
import info.bitcoinunlimited.www.wally.ui.softKeyboardBar
import info.bitcoinunlimited.www.wally.ui.triggerUnlockDialog
import kotlinx.coroutines.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import info.bitcoinunlimited.www.wally.ui2.themeUi2.WallyThemeUi2
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple
import info.bitcoinunlimited.www.wally.uiv2.AccountDetailScreenUi2
import info.bitcoinunlimited.www.wally.uiv2.AssetScreenUi2
import info.bitcoinunlimited.www.wally.uiv2.HomeScreenUi2
import info.bitcoinunlimited.www.wally.uiv2.SpecialTxPermScreenUi2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.sourceLoc
import org.nexa.threads.iThread
import org.nexa.threads.millisleep

private val LogIt = GetLog("wally.NavRoot.Ui2")
val newUI = MutableStateFlow(preferenceDB.getBoolean(EXPERIMENTAL_UX_MODE_PREF, true))

/*
private val selectedAccountName = preferenceDB.getString(SELECTED_ACCOUNT_NAME_PREF, "")
private val _selectedAccountUi2 =  MutableStateFlow<Account?>(wallyApp?.accounts?.get(
    selectedAccountName
))
 */

val selectedAccountUi2: StateFlow<Account?> //=  //_selectedAccountUi2.asStateFlow() // Move to viewmodel for selected account?
    get() {
        return wallyApp!!.focusedAccount
    }

var permanentMenuItemsUi2: Set<NavChoiceUi2> = setOf(
  NavChoiceUi2(ScreenId.Home, S.title_home, Icons.Default.Home),
  NavChoiceUi2(ScreenId.Assets, S.title_activity_assets, Icons.Default.Image),
  NavChoiceUi2(ScreenId.Shopping, S.title_activity_shopping, Icons.Default.ShoppingCart),
  NavChoiceUi2(ScreenId.MoreMenu, S.more, Icons.Default.MoreVert),
)

val allMenuItems = setOf(
  NavChoiceUi2(ScreenId.Home, S.title_home, Icons.Default.Home),
  NavChoiceUi2(ScreenId.Assets, S.title_activity_assets, Icons.Default.Image),
  NavChoiceUi2(ScreenId.Shopping, S.title_activity_shopping, Icons.Default.ShoppingCart),
  NavChoiceUi2(ScreenId.Identity, S.title_activity_identity, Icons.Default.Person),
  NavChoiceUi2(ScreenId.TricklePay, S.title_activity_trickle_pay, Icons.Default.WaterDrop),
  NavChoiceUi2(ScreenId.Settings, S.title_activity_settings, Icons.Default.Settings),
)

var menuItemsUi2: MutableStateFlow<Set<NavChoiceUi2>> = MutableStateFlow(
    permanentMenuItemsUi2
)
var moreMenuItems: MutableStateFlow<Set<NavChoiceUi2>> = MutableStateFlow(setOf())
const val BOTTOM_NAV_ITEMS = 5

fun buildMenuItemsUi2()
{
    val items = permanentMenuItemsUi2.toMutableSet()
    val identity = NavChoiceUi2(ScreenId.Identity, S.title_activity_identity, Icons.Default.Person)
    val tricklePay = NavChoiceUi2(ScreenId.TricklePay, S.title_activity_trickle_pay, Icons.Default.WaterDrop)
    val assets = NavChoiceUi2(ScreenId.Assets, S.title_activity_assets, Icons.Default.Image)

    if(showIdentityPref.value && items.size < BOTTOM_NAV_ITEMS) items.add(identity)
    if(showTricklePayPref.value && items.size < BOTTOM_NAV_ITEMS) items.add(tricklePay)
    if(showAssetsPref.value && items.size < BOTTOM_NAV_ITEMS) items.add(assets)
    val moreitems = allMenuItems.minus(items).toMutableSet()
    if (!showIdentityPref.value) moreitems.remove(NavChoiceUi2(ScreenId.Identity, S.title_activity_identity, Icons.Default.Person))
    if (!showTricklePayPref.value) moreitems.remove(NavChoiceUi2(ScreenId.TricklePay, S.title_activity_trickle_pay, Icons.Default.WaterDrop))

    menuItemsUi2.value = items
    moreMenuItems.value = moreitems.toSet()
}


/** Change showing or hiding a menu item */
fun enableNavMenuItemUi2(item: ScreenId, enable:Boolean=true)
{
    later {
        var changed = false
        val e = preferenceDB.edit()
        if (item == ScreenId.Identity && showIdentityPref.value != enable)
        {
            changed = true
            e.putBoolean(SHOW_IDENTITY_PREF, enable)
            showIdentityPref.value = enable
        }
        if (item == ScreenId.TricklePay && showTricklePayPref.value != enable)
        {
            changed = true
            e.putBoolean(SHOW_TRICKLEPAY_PREF, enable)
            showTricklePayPref.value = enable
        }
        if (item == ScreenId.Assets && showAssetsPref.value != enable)
        {
            changed = true
            e.putBoolean(SHOW_ASSETS_PREF, enable)
            showAssetsPref.emit(enable)
        }
        if (changed)
        {
            // buildMenuItemsUi2()
            e.commit()
        }
    }
}

/** There are some error display race conditions that are happening
 * The error Alert is passed in a coroutine Channel and processed asynchronously.
 * However, if you've just entered a screen, the code clears prior alerts -- which wins the "prior" clear or the processing of this error?
 * Until these race conditions are solved, call this function which delays stuff a bit (TBH its nicer for the user to see these small delays)
 * avoiding the race conditions.
 */
fun displayErrorAndGoBack(errNo: Int)
{
    later {
        delay(100)
        displayError(errNo)
        delay(200)
        nav.back()
    }
}

// Periodic checking of the wallet's activity to auto-enable nav menu functionality when the wallet engages in it.
fun updateNavMenuContentsUi2()
{
    // Check every 10 seconds to see if there are assets in this wallet & enable the menu item if there are
    if (!showAssetsPref.value && (wallyApp?.hasAssets() == true))
    {
        enableNavMenuItemUi2(ScreenId.Assets)
    }
}

// UX related periodic analysis
fun uxPeriodicAnalysisUi2(): iThread
{
    return org.nexa.threads.Thread("periodicAnalysis") {
        while (true)
        {
            updateNavMenuContentsUi2()
            millisleep(5000U)
        }
    }
}

/*
    Use this method ONLY to change the selected account
 */
fun setSelectedAccount(account: Account)
{
    if (wallyApp!!.focusedAccount.value != account)
    {
        preferenceDB.edit().putString(SELECTED_ACCOUNT_NAME_PREF, account.name).commit()
        wallyApp!!.focusedAccount.value = account
    }
}

fun noSelectedAccount()
{
    if (wallyApp!!.focusedAccount.value != null)
    {
        preferenceDB.edit().putString(SELECTED_ACCOUNT_NAME_PREF, "").commit()
        wallyApp!!.focusedAccount.value = null
    }
}

/*
    This is the root Composable while we still have two implementations of the UI.
    hasNewUIShared toggled when the user select "new user interface" in settings.
 */
@Composable
fun UiRoot(systemPadding: Modifier)
{
    val newUi = newUI.collectAsState().value

    if (newUi)
        NavigationRootUi2(Modifier)
    else
        NavigationRoot(systemPadding)
}

data class NavChoiceUi2(val location: ScreenId, val textId: Int, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavMenu(scope: CoroutineScope, bottomSheetController: BottomSheetScaffoldState, expanded: MutableState<Boolean>, lastClicked: MutableState<String>)
{
    val items = menuItemsUi2.collectAsState().value.sortedBy { navItem -> navItem.location}

    // Horizontal row to layout navigation buttons
    Row(
      modifier = Modifier.fillMaxWidth()
        .wrapContentHeight()
        .background(Color.White)
        .drawBehind {
            drawLine(
              color = Color.Gray,
              start = androidx.compose.ui.geometry.Offset(0f, 0f),
              end = androidx.compose.ui.geometry.Offset(size.width, 0f),
              strokeWidth = 1.dp.toPx()
            )
        }
    ) {
        Row(modifier = Modifier.fillMaxWidth()
          .wrapContentHeight()
          .background(Color.White)
          .padding(top = 6.dp, bottom = 1.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
            for (ch in items)
            {
                Button(
                  onClick = {
                      clearAlerts()  // If the user explicitly moved to a different screen, they must be aware of the alert
                      scope.launch {
                          if (ch.location != ScreenId.MoreMenu)
                            lastClicked.value = ch.location.toString()
                          if (ch.location == ScreenId.MoreMenu) {
                              if (!expanded.value) {
                                  bottomSheetController.bottomSheetState.expand()
                              } else {
                                  bottomSheetController.bottomSheetState.hide()
                              }
                              expanded.value = !expanded.value
                          } else {
                              if (expanded.value) {
                                  bottomSheetController.bottomSheetState.hide()
                                  expanded.value = false
                              }
                              nav.switch(ch.location)
                          }

                      }
                  },
                  // Change button appearance based on current screen
                  shape = RoundedCornerShape(30),
                  contentPadding = PaddingValues(0.dp, 0.dp),
                  // This is opposite of normal: The disabled button is our current screen, so should have the highlight
                  colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color.White,
                    disabledContentColor = wallyPurple,
                    containerColor = Color.White,
                    contentColor = Color.Gray
                  ),
                  modifier = Modifier.width(IntrinsicSize.Max).wrapContentHeight().padding(0.dp, 0.dp).defaultMinSize(1.dp, 1.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp), horizontalAlignment = Alignment.CenterHorizontally,
                      modifier = Modifier.width(IntrinsicSize.Max).wrapContentHeight().padding(0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        // TODO: Reuse fun IconTextButton()
                        if(lastClicked.value != ch.location.toString()){
                            Icon(ch.icon, "", Modifier.width(30.dp).height(30.dp))
                            Text(text = i18n(ch.textId), fontSize = 9.sp, modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally, true),
                              textAlign = TextAlign.Center, softWrap = false, maxLines = 1)
                        }  else{
                            Icon(ch.icon, "", Modifier.width(30.dp).height(30.dp), tint = wallyPurple)
                            Text(text = i18n(ch.textId), fontSize = 9.sp, modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally, true),
                              textAlign = TextAlign.Center, softWrap = false, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// This function should build a title bar (with a back button) if the platform doesn't already have one.  Otherwise it should
// set up the platform's title bar
@Composable fun TopBar(errorText: String, warningText: String, noticeText: String, lastClicked: MutableState<String>)
{
    val currentScreen = nav.currentScreen.collectAsState().value

    if (!platform().hasNativeTitleBar)
    {
        val bkgCol = if (errorText.isNotEmpty()) colorError else if (warningText.isNotEmpty()) colorWarning else if (noticeText.isNotEmpty()) colorNotice else colorTitleBackground
        // Specifying the row height stops changes header bar content to change its height causing the entire window to jerk up or down
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(bkgCol).padding(0.dp).height(56.dp))
        {
            if (currentScreen == ScreenId.Home)
                ResImageView(
                  resPath = "icons/wally_logo_small.png",
                  modifier = Modifier.size(40.dp).padding(start = 4.dp)
                )
            else if (nav.hasBack() != ScreenId.None)
            {
                IconButton(onClick = {
                    lastClicked.value = nav.hasBack().toString()
                    nav.back()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, tint = colorTitleForeground, contentDescription = null)
                }
            }

            if (errorText.isNotEmpty())
                ErrorText(errorText, Modifier.weight(1f).fillMaxSize().padding(0.dp, 15.dp, 0.dp, 0.dp))
            else if (warningText.isNotEmpty())
                WarningText(warningText, Modifier.weight(1f).fillMaxSize().padding(0.dp, 15.dp, 0.dp, 0.dp))
            else if (noticeText.isNotEmpty())
                NoticeText(noticeText, Modifier.weight(1f).fillMaxSize().padding(0.dp, 15.dp, 0.dp, 0.dp))
            else
            {
                val iconButtonSize = 32.dp
                TitleText(nav.title(), Modifier.weight(1f).fillMaxSize().padding(0.dp, 15.dp, 0.dp, 0.dp))
                IconButton(onClick = { triggerUnlockDialog() }, modifier = Modifier.size(iconButtonSize)){
                    Icon(Icons.Filled.Lock, tint = Color.White, contentDescription = "Lock")
                }
                if (platform().hasShare && nav.currentScreen.collectAsState().value.hasShare)
                    IconButton(onClick = { onShareButton() }, modifier = Modifier.size(iconButtonSize)) {
                        Icon(Icons.Filled.Share, tint = Color.White, contentDescription = "Share")
                    }
                IconButton(onClick = {}, modifier = Modifier.size(iconButtonSize)){
                    Icon(Icons.Filled.Settings, tint = Color.White, contentDescription = "Settings", modifier = Modifier.clickable {
                        // Clicking this settings icon while in settings screen was causing the back button to navigate to settings...
                        if(nav.currentScreen.value != ScreenId.Settings)
                            nav.go(ScreenId.Settings)
                    })
                }
            }
        }
    }
}

@Composable fun RecoveryPhraseWarningUi2(clickable: Modifier, account:Account?=null)
{
    val curScreen = nav.currentScreen.collectAsState().value

    // Don˙t show in send screen. This was messing with the amount selector in send screen under deadline and
    // When you are sending you don't need to back up your key because you are moving coins and assets to a new key.
    if (curScreen != ScreenId.Send)
        Card(
          modifier = clickable
            .padding(12.dp)
            .fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = Color.White,
          ),
          shape = RoundedCornerShape(12.dp),
          elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
              modifier = clickable.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
            ) {
                Text(
                  text = i18n(S.WriteDownRecoveryPhraseWarningUi2),
                  style = MaterialTheme.typography.bodyLarge,
                  color = wallyPurple,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                WallyButtonRow {
                    OutlinedButton({
                        externalDriver.trySend(
                            GuiDriver(
                                ScreenId.AccountDetails, noshow = setOf(
                                    ShowIt.WARN_BACKUP_RECOVERY_KEY
                                ), account = account)
                        )
                    }) {
                        Text(i18n(S.GoThere))
                    }
                    OutlinedButton({
                        externalDriver.trySend(GuiDriver(noshow = setOf(ShowIt.WARN_BACKUP_RECOVERY_KEY)))
                    }) {
                        Text(i18n(S.dismiss))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        /*
        Column {
            Text(i18n(S.WriteDownRecoveryPhraseWarning), Modifier.fillMaxWidth().wrapContentHeight(), colorPrimaryDark, maxLines = 10, textAlign = TextAlign.Center,
              fontSize = FontScale(1.25))

            Spacer(Modifier.height(4.dp))

            WallyButtonRow {
                Button({
                    externalDriver.trySend(GuiDriver(ScreenId.AccountDetails, noshow = setOf(ShowIt.WARN_BACKUP_RECOVERY_KEY), account = account))
                }) {
                    Text(i18n(S.GoThere))
                }
                Button({
                    externalDriver.trySend(GuiDriver(noshow = setOf(ShowIt.WARN_BACKUP_RECOVERY_KEY)))
                }) {
                    Text(i18n(S.dismiss))
                }
            }

        }
         */
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationRootUi2(systemPadding: Modifier)
{
    val curScreen = nav.currentScreen.collectAsState().value
    val subScreen = nav.currentSubState.collectAsState().value

    val currentReceiveAddrState = currentReceiveShared.collectAsState()
    ToBeShared = { currentReceiveAddrState.value.second }

    var showBottomBar by remember { mutableStateOf(true) }

    if (curScreen == ScreenId.Home || (curScreen == ScreenId.Assets && subScreen == null) || curScreen == ScreenId.Shopping || curScreen == ScreenId.Settings
      || curScreen == ScreenId.SplitBill || curScreen == ScreenId.NewAccount || curScreen == ScreenId.AccountDetails || curScreen == ScreenId.AddressHistory
      || curScreen == ScreenId.TxHistory || curScreen == ScreenId.MoreMenu || curScreen == ScreenId.TpSettings
      || curScreen == ScreenId.AssetInfoPerm || curScreen == ScreenId.SendToPerm
      )
        showBottomBar = true
    else if (curScreen == ScreenId.Send || curScreen == ScreenId.Receive || curScreen == ScreenId.SpecialTxPerm)
        showBottomBar = false
    // Asset detail screen
    else if (curScreen == ScreenId.Assets && subScreen != null)
        showBottomBar = false

    LaunchedEffect(Unit) {
        buildMenuItemsUi2()
    }
    if (curScreen == ScreenId.Splash)
    {
        val nativeSplash = NativeSplash(true)
        if (!nativeSplash)
        {
            Box(Modifier.fillMaxSize().background(Color(0xFF725092))) {
                ResImageView("icons/wallyicon2024_800x800.png", modifier = Modifier.background(Color(0xFF725092)).align(Alignment.Center).fillMaxSize(0.5f), "")
            }
        }

        LaunchedEffect(true) {
            delay(2000)
            if (nativeSplash) NativeSplash(false)
            nav.switch(ScreenId.Home)
        }
        return
    }

    val softKeyboardShowing = isSoftKeyboardShowing.collectAsState().value
    val scrollState = rememberScrollState()
    val driver = remember { mutableStateOf<GuiDriver?>(null) }
    var errorText by remember { mutableStateOf("") }
    var warningText by remember { mutableStateOf("") }
    var noticeText by remember { mutableStateOf("") }
    var alertPersistAcrossScreens by remember { mutableStateOf(0) }
    var isShowingRecoveryWarning by remember { mutableStateOf(false) }

    val selectedAccountState = selectedAccountUi2.collectAsState()
    val selectedAccount = selectedAccountState.value

    var unlockDialog by remember { mutableStateOf<(() -> Unit)?>(null) }

    val clipmgr: ClipboardManager = LocalClipboardManager.current

    // buildMenuItemsUi2()

    /*
        Select an account when opening the app.
        Either user an account name from local preferences or use the first account found.
     */
    try
    {
        setSelectedAccount(wallyApp!!.preferredVisibleAccount())
    }
    catch(e: Exception)
    {
    }

    @Composable
    fun withAccount(then: @Composable (acc: Account) -> Unit)
    {
        val pa = selectedAccount ?: wallyApp!!.focusedAccount.value ?: wallyApp?.nullablePrimaryAccount
        if (pa == null)
        {
            displayErrorAndGoBack(S.NoAccounts)
        }
        else then(pa)

    }

    @Composable
    fun withUnlockedAccount(then: @Composable (acc: Account) -> Unit)
    {
        val pa = selectedAccount
        if (pa == null)
        {
            displayErrorAndGoBack(S.NoAccounts)
        }
        else
        {
            if (!pa.locked)
            {
                then(pa)
            }
            else
            {
                triggerUnlockDialog {
                    if (pa.locked) nav.back()  // fail
                    triggerUnlockDialog(false)
                }
            }
        }
    }


    @Composable
    fun withTp(then: @Composable (acc: Account, ctp: TricklePaySession) -> Unit)
    {
        val ctp = nav.curData.value as? TricklePaySession
        if (ctp == null)
        {
            displayErrorAndGoBack(S.TpNoSession)  // TODO make this no TP session
        }
        else
        {
            val pa = try
            {
                ctp.getRelevantAccount(selectedAccount?.name)
            }
            catch (e: WalletInvalidException)
            {
                displayErrorAndGoBack(S.NoAccounts)
                null
            }
            pa?.let { then(it, ctp) }
        }
    }

    @Composable
    fun withSendNavParams(then: @Composable (sendScreenNavParams: SendScreenNavParams) -> Unit)
    {
        val ctp = nav.curData.value as? SendScreenNavParams
        then(ctp ?: SendScreenNavParams())
    }

    // Allow an external (non-compose) source to "drive" the GUI to a particular state.
    // This implements functionality like scanning/pasting/receiving via a connection a payment request.
    LaunchedEffect(true)
    {
        for (c in externalDriver)
        {
            LogIt.info(sourceLoc() + ": external screen driver received")
            driver.value = c
            //if (c.uri != null) currentUri = c.uri
            // If the driver specifies an account, we want to switch to it
            c.account?.let {
                wallyApp?.focusedAccount?.value = it
            }
            c.gotoPage?.let {
                clearAlerts()  // If the user explicitly moved to a different screen, they must be aware of the alert
                nav.go(it, data = c.tpSession)
            }
            c.show?.forEach {
                if (it == ShowIt.WARN_BACKUP_RECOVERY_KEY)
                {
                    isShowingRecoveryWarning = true
                }
                if (it == ShowIt.ENTER_PIN)
                {
                    LogIt.info(sourceLoc() + ": open PIN entry window")
                    unlockDialog = c.afterUnlock ?: {}
                }
            }
            c.noshow?.forEach {
                if (it == ShowIt.WARN_BACKUP_RECOVERY_KEY)
                {
                    isShowingRecoveryWarning = false
                }
                if (it == ShowIt.ENTER_PIN)
                {
                    LogIt.info(sourceLoc() + ": close PIN entry window")
                    unlockDialog?.invoke()
                    unlockDialog = null
                }
            }
            if (c.regenAccountGui == true)
            {
                assignAccountsGuiSlots()
            }
            if (c.withClipboard != null)
            {
                val s = clipmgr.getText()
                c.withClipboard.invoke(s?.text)
            }
        }
    }

    LaunchedEffect(true)
    {
        for (alert in alertChannel)
        {
            if (alert.level.level >= AlertLevel.ERROR.level)
            {
                if (alert.msg == "" && (alert.persistAcrossScreens >= alertPersistAcrossScreens) ) // clear all alerts this level or below
                {
                    alertPersistAcrossScreens = 0
                    errorText = ""
                    noticeText = ""
                    warningText = ""
                }
                else
                {
                    errorText = alert.msg
                    later {
                        delay(alert.longevity ?: ERROR_DISPLAY_TIME)
                        if (errorText == alert.msg) errorText = ""  // do not erase if the error has changed
                    }
                }
            }
            else if (alert.level.level >= AlertLevel.WARN.level && (alert.persistAcrossScreens >= alertPersistAcrossScreens) )
            {
                if (alert.msg == "") // clear all alerts this level or below
                {
                    alertPersistAcrossScreens = 0
                    warningText = ""
                    noticeText = ""
                }
                else
                {
                    warningText = alert.msg
                    later {
                        delay(alert.longevity ?: NORMAL_NOTICE_DISPLAY_TIME)
                        if (warningText == alert.msg) warningText = ""  // do not erase if the error has changed
                    }
                }
            }
            else if (alert.level.level >= AlertLevel.NOTICE.level && (alert.persistAcrossScreens >= alertPersistAcrossScreens) )
            {
                if (alert.msg == "") // clear all alerts this level or below
                {
                    alertPersistAcrossScreens = 0
                    warningText = ""
                }
                else
                {
                    noticeText = alert.msg
                    later {
                        delay(alert.longevity ?: NOTICE_DISPLAY_TIME)
                        if (noticeText == alert.msg) noticeText = ""  // do not erase if the error has changed
                    }
                }
            }
        }
    }

    // This box is on top of the main screen
    Box(modifier = Modifier.zIndex(1000f).fillMaxSize()) {
        if (softKeyboardShowing)
        {
            val keybar = softKeyboardBar.collectAsState().value
            if (keybar != null)
            {
                val imeHeight = getImeHeight()
                keybar.invoke(Modifier.align(Alignment.BottomStart).padding(bottom = imeHeight).fillMaxWidth())
            }
        }
    }
    val scope = rememberCoroutineScope()
    val scaffoldSheetState = rememberBottomSheetScaffoldState(bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false))
    val expanded = remember { mutableStateOf(false) }
    val lastClicked = remember { mutableStateOf(ScreenId.Home.toString()) }
    val moreMenuItemsState = moreMenuItems.collectAsState()
    val moreMenuItems = moreMenuItemsState.value

    // The main screen
    Scaffold(
      modifier =
      Modifier.pointerInput(Unit) {
          detectTapGestures(onTap = {
              scope.launch {
                  if (expanded.value)
                  {
                      scaffoldSheetState.bottomSheetState.hide()
                      expanded.value = false
                  }
              }
              lastClicked.value = curScreen.toString()
          })
      },
      contentColor = Color.Black,
      topBar = {
          TopBar(errorText, warningText, noticeText, lastClicked)
      },
      bottomBar = {
          if (showBottomBar)
              BottomNavMenu(scope, scaffoldSheetState, expanded, lastClicked)
      },
    ) { innerPadding ->
        BottomSheetScaffold(
          sheetTonalElevation = 10.dp,
          sheetShadowElevation = 10.dp,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.primary,
          sheetShape = RoundedCornerShape(0.dp),
          sheetDragHandle = {},
          scaffoldState = scaffoldSheetState,
          sheetPeekHeight = 0.dp,
          sheetContent = {
              Column(
                Modifier
                  .padding(bottom = innerPadding.calculateBottomPadding())
                  .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                  // Bottom Sheet content: a list with icons
                  moreMenuItems.forEach { ch ->
                      Button(
                        onClick = {
                            clearAlerts()  // If the user explicitly moved to a different screen, they must be aware of the alert
                            scope.launch {
                                if (ch.location != ScreenId.MoreMenu)
                                    lastClicked.value = ch.location.toString()
                                if (ch.location == ScreenId.MoreMenu) {
                                    if (!expanded.value) {
                                        scaffoldSheetState.bottomSheetState.expand()
                                    } else {
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.hide()
                                        }
                                    }
                                    expanded.value = !expanded.value
                                } else {
                                    if (expanded.value) {
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.hide()
                                        }
                                        expanded.value = false
                                    }
                                    nav.switch(ch.location)
                                }
                            }
                        },
                        // Change button appearance based on current screen
                        shape = RoundedCornerShape(30),
                        contentPadding = PaddingValues(0.dp, 0.dp),
                        // This is opposite of normal: The disabled button is our current screen, so should have the highlight
                        colors = ButtonDefaults.buttonColors(
                          disabledContainerColor = Color.White,
                          disabledContentColor = wallyPurple,
                          containerColor = Color.White,
                          contentColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(0.dp, 0.dp).defaultMinSize(1.dp, 1.dp)  //width(100.dp)
                      ) {
                          Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(0.dp, 4.dp, 0.dp, 0.dp)
                          ) {
                              // TODO: Reuse fun IconTextButton()
                              val fontSize = 12.sp
                              if (lastClicked.value != ch.location.toString()){
                                  Icon(ch.icon, "", Modifier.width(30.dp).height(30.dp))
                                  Spacer(Modifier.width(8.dp))
                                  Text(text = i18n(ch.textId), fontSize = fontSize, modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, true),
                                    textAlign = TextAlign.Center, softWrap = false, maxLines = 1)
                              }
                              else
                              {
                                  Icon(ch.icon, "", Modifier.width(30.dp).height(30.dp), tint = wallyPurple)
                                  Spacer(Modifier.width(8.dp))
                                  Text(text = i18n(ch.textId), fontSize = fontSize, modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, true),
                                    textAlign = TextAlign.Center, softWrap = false, maxLines = 1)
                              }

                          }
                      }
                  }
              }
          },
        ) {
            WallyThemeUi2 {
                Box(modifier = Modifier.fillMaxSize().background(Color.White).padding(innerPadding).then(systemPadding)) {
                    if (unlockDialog != null) UnlockView {  }
                    Column(modifier = Modifier.fillMaxSize()) {

                        if (isShowingRecoveryWarning)
                            RecoveryPhraseWarningUi2(Modifier.clickable { isShowingRecoveryWarning = false})

                        // This will take up the most space but leave enough for the navigation menu
                        val mod = if (curScreen.isEntirelyScrollable)
                        {
                            Modifier.weight(1f).verticalScroll(scrollState).fillMaxWidth()
                        }
                        else
                        {
                           Modifier.weight(1f).fillMaxWidth().fillMaxHeight()
                        }
                        Box(
                          modifier = mod
                        ) {
                            LaunchedEffect(curScreen){
                                if (curScreen != ScreenId.MoreMenu)
                                    lastClicked.value = curScreen.toString()
                            }
                            when (curScreen)
                            {
                                ScreenId.None -> HomeScreenUi2(isShowingRecoveryWarning)
                                ScreenId.Splash -> run {} // splash screen is done at the top for max speed and to be outside of the theme
                                ScreenId.MoreMenu -> run {}
                                ScreenId.Home -> { HomeScreenUi2(isShowingRecoveryWarning) }
                                ScreenId.Send -> withAccount { act -> withSendNavParams { SendScreen(act, it) } }
                                ScreenId.Receive -> { ReceiveScreen() }
                                ScreenId.SplitBill -> SplitBillScreen()
                                ScreenId.NewAccount -> { NewAccountScreenUi2(accountGuiSlots.collectAsState(), devMode) }
                                ScreenId.Settings -> SettingsScreenUi2()
                                ScreenId.AccountDetails -> withUnlockedAccount { AccountDetailScreenUi2(it) }
                                ScreenId.Assets -> withAccount { AssetScreenUi2(it) }
                                ScreenId.Shopping -> ShoppingScreenUi2()
                                ScreenId.TricklePay -> withAccount { act -> TricklePayScreen(act, null, nav) }
                                ScreenId.Identity -> withAccount { act ->
                                    val idsess = nav.curData.value as? IdentitySession
                                    IdentityScreen(act, idsess, nav)
                                }
                                ScreenId.IdentityEdit -> withAccount { act ->
                                    IdentityEditScreen(act, nav)
                                }
                                ScreenId.AddressHistory ->  withAccount { AddressHistoryScreen(it, nav) }
                                ScreenId.TxHistory -> withAccount { TxHistoryScreen(it, nav) }
                                ScreenId.TpSettings -> withTp { act, ctp -> TricklePayScreen(act, ctp, nav) }
                                ScreenId.SpecialTxPerm -> withTp { act, ctp -> SpecialTxPermScreenUi2(act, ctp) }
                                ScreenId.AssetInfoPerm -> withTp { act, ctp -> AssetInfoPermScreen(act, ctp, nav) }
                                ScreenId.SendToPerm -> withTp { act, ctp -> SendToPermScreen(act, ctp, nav) }
                                ScreenId.IdentityOp -> withAccount { act ->
                                    val idsess = nav.curData.value as? IdentitySession
                                    if (idsess != null) IdentityPermScreen(act, idsess, nav)
                                    else nav.back()
                                }
                                ScreenId.Alerts -> HomeScreenUi2(isShowingRecoveryWarning)
                            }
                        }
                    }
                }
            }
        }

    }

    // The material theme for the whole app is set here.
}

// This function should build a title bar (with a back button) if the platform doesn't already have one.  Otherwise it should
// set up the platform's title bar
@Composable fun TopBar(errorText: String, warningText: String, noticeText: String)
{
    if (!platform().hasNativeTitleBar)
    {
        val bkgCol = if (errorText.isNotEmpty()) colorError else if (warningText.isNotEmpty()) colorWarning else if (noticeText.isNotEmpty()) colorNotice else colorTitleBackground
        // Specifying the row height stops changes header bar content to change its height causing the entire window to jerk up or down
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(bkgCol).padding(0.dp).height(56.dp))
        {
            if (nav.hasBack() != ScreenId.None)
            {
                IconButton(onClick = { nav.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, tint = colorTitleForeground, contentDescription = null)
                }
            }
            // We can only fillMaxSize() here because we constrained the height of the row
            if (errorText.isNotEmpty())
                ErrorText(errorText, Modifier.weight(1f).fillMaxSize().padding(0.dp, 15.dp, 0.dp, 0.dp))
            else if (warningText.isNotEmpty())
                WarningText(warningText, Modifier.weight(1f).fillMaxSize().padding(0.dp, 15.dp, 0.dp, 0.dp))
            else if (noticeText.isNotEmpty())
                NoticeText(noticeText, Modifier.weight(1f).fillMaxSize().padding(0.dp, 15.dp, 0.dp, 0.dp))
            //NoticeText(i18n(S.copiedToClipboard))
            else
            {

                TitleText(nav.title(), Modifier.weight(1f).fillMaxSize().padding(0.dp, 15.dp, 0.dp, 0.dp))

                if (platform().hasShare && nav.currentScreen.collectAsState().value.hasShare) IconButton(onClick = { onShareButton() }, modifier = Modifier.size(36.dp).padding(5.dp, 0.dp)) {
                    Icon(Icons.Default.Share, tint = Color.LightGray, contentDescription = null, modifier = Modifier.size(36.dp))
                }

                Icon(Icons.Default.Share, tint = Color.LightGray, contentDescription = null, modifier = Modifier.size(36.dp))

                Icon(Icons.Default.Lock, tint = Color.LightGray, contentDescription = null, modifier = Modifier.size(36.dp))

                Icon(Icons.Default.Settings, tint = Color.LightGray, contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }
    }
}
