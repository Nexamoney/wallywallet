package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.ui.theme.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eygraber.uri.Uri

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.rem
import org.nexa.libnexakotlin.sourceLoc

private val LogIt = GetLog("wally.NavRoot")

val softKeyboardBar:MutableStateFlow<(@Composable (Modifier)->Unit)?> = MutableStateFlow(null)
val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
val isSoftKeyboardShowing = MutableStateFlow(false)

enum class ScreenId
{
    None,
    Splash,
    Home,
    Identity,
    IdentityOp,
    IdentityEdit,
    TricklePay,
    Assets,
    Shopping,
    Settings,
    SplitBill,
    NewAccount,
    AccountDetails,
    AddressHistory,
    TxHistory,

    TpSettings,
    SpecialTxPerm,
    AssetInfoPerm,
    SendToPerm,
    Alerts;

    val isEntirelyScrollable:Boolean
        get()
    {
        if (this == Settings) return true
        if (this == NewAccount) return true
        return false
    }

    val hasShare:Boolean
        get()
    {
        if (this == Home) return true
        return false
    }

    fun up(): ScreenId
    {
        return when (this)
        {
            SplitBill -> Home
            AccountDetails -> Home
            NewAccount -> Home
            SpecialTxPerm -> Home
            AssetInfoPerm -> Home
            SendToPerm -> Home
            TpSettings -> TricklePay
            IdentityEdit -> Identity
            Splash -> Home
            else -> None
        }
    }

    fun title(): String
    {
        return when (this)
        {
            None -> i18n(S.app_name)
            Splash -> ""
            Home -> i18n(S.app_name)
            IdentityEdit -> i18n(S.title_activity_identity)
            Identity -> i18n(S.title_activity_identity)
            IdentityOp -> i18n(S.title_activity_identity_op)
            TricklePay -> i18n(S.title_activity_trickle_pay)
            Assets -> i18n(S.assetsColon) + (wallyApp?.preferredVisibleAccount()?.name ?: "")
            Shopping -> i18n(S.title_activity_shopping)
            Settings -> i18n(S.title_activity_settings)
            SplitBill -> i18n(S.title_split_bill)
            NewAccount -> i18n(S.title_activity_new_account)
            AccountDetails -> i18n(S.title_activity_account_details) % mapOf("account" to (wallyApp?.preferredVisibleAccount()?.name ?: ""))
            AddressHistory -> i18n(S.title_activity_address_history) % mapOf("account" to (wallyApp?.preferredVisibleAccount()?.name ?: ""))
            TxHistory -> i18n(S.title_activity_tx_history) % mapOf("account" to (wallyApp?.preferredVisibleAccount()?.name ?: ""))

            TpSettings -> i18n(S.title_activity_trickle_pay)
            Alerts -> i18n(S.title_activity_alert_history)

            // TODO make a better title for these permissions screens
            SpecialTxPerm -> i18n(S.title_activity_trickle_pay)
            AssetInfoPerm -> i18n(S.title_activity_trickle_pay)
            SendToPerm -> i18n(S.title_activity_trickle_pay)
        }
    }

}

class ScreenNav()
{
    // Screens can put anything into screenSubState to remember their context.
    // This allows them to make the "back" button change subscreen state by pushing the current screenId with a different
    // screenSubState.
    data class ScreenState(val id: ScreenId, val departFn: (() -> Unit)?, val screenSubState: ByteArray?=null)

    var currentScreen: MutableState<ScreenId> = mutableStateOf(ScreenId.Home)
    val currentSubState: MutableState<ByteArray?> = mutableStateOf(null)
    protected var currentScreenDepart: (() -> Unit)? = null
    val path = ArrayDeque<ScreenState>(10)

    fun onDepart(fn: () -> Unit)
    {
        currentScreenDepart = fn
    }

    /** If everything is recomposed, we may have a new mutable screenid tracker */
    fun reset(newMutable: MutableState<ScreenId>)
    {
        currentScreen = newMutable
    }

    /** Add a screen onto the stack */
    fun push(screen: ScreenId) = path.add(ScreenState(screen,null))


    /* push the current screen onto the stack, and set the passed screen to be the current one */
    fun go(screen: ScreenId, screenSubState: ByteArray?=null)
    {
        currentScreenDepart?.invoke()
        path.add(ScreenState(currentScreen.value,currentScreenDepart, currentSubState.value ))
        currentScreen.value = screen
        currentSubState.value = screenSubState
        currentScreenDepart = null
        NativeTitle(title())
    }

    fun title() = currentScreen.value.title()

    /** return the destination screenId if you can go back from here, otherwise ScreenId.None */
    fun hasBack(): ScreenId
    {
        var priorId:ScreenId = ScreenId.None
        val prior = path.lastOrNull()
        // If I can't go back, go up
        if (prior == null) priorId = currentScreen.value.up()
        else priorId = prior.id
        return priorId
    }

    /** pop the current screen from the stack and go there */
    fun back():ScreenId?
    {
        currentScreenDepart?.invoke()
        currentScreenDepart = null
        // See if there is anything in the back stack.
        var priorId:ScreenId? = null
        val prior = path.removeLastOrNull()
        // If I can't go back, go up
        if (prior == null) priorId = currentScreen.value.up()
        if (prior != null)
        {
            priorId = prior.id
            currentScreenDepart = prior.departFn
            currentSubState.value = prior.screenSubState
        }
        else currentSubState.value = null
        if (priorId != null)
        {
            currentScreen.value = priorId
        }  // actually trigger going back
        NativeTitle(title())
        return priorId
    }
}

fun assignAccountsGuiSlots()
{
    // We have a Map of account names to values, but we need a list
    // Sort the accounts based on account name
    accountGuiSlots.value = wallyApp!!.orderedAccounts()
}


const val TRIGGER_BUG_DELAY = 100L

fun triggerAssignAccountsGuiSlots()
{
    // If the slots got shuffled around, maybe the current receive was deleted or hidden
    assignAccountsGuiSlots()
    val act = wallyApp!!.accounts[currentReceiveShared.value.first]
    if (act == null || act.visible == false) try
    {
        wallyApp?.preferredVisibleAccount()?.let {
            it.onUpdatedReceiveInfoCommon { recvAddrStr ->
                    currentReceiveShared.value = Pair(it.name, recvAddrStr)
                }
        }
    }
    catch (e:PrimaryWalletInvalidException)
    {
        currentReceiveShared.value = Pair("", "")
    }

    // later { delay(TRIGGER_BUG_DELAY); externalDriver.send(GuiDriver(regenAccountGui = true)) }
}

fun triggerUnlockDialog(show: Boolean = true, then: (()->Unit)? = null)
{
    if (show)
    {
        later {
            delay(TRIGGER_BUG_DELAY); externalDriver.send(GuiDriver(show = setOf(ShowIt.ENTER_PIN), afterUnlock = then))
        }
    }
    else later { delay(TRIGGER_BUG_DELAY); externalDriver.send(GuiDriver(noshow = setOf(ShowIt.ENTER_PIN))) }
}

fun triggerClipboardAction(doit: (String?) -> Unit)
{
    later { delay(TRIGGER_BUG_DELAY); externalDriver.send(GuiDriver(withClipboard = doit))}

}

// implement a share button (whose behavior may change based on what screen we are on)

// As part of your recompose, update this callback function so that the proper data will be constructed to be shared based on the GUI context
var ToBeShared:(()->String)? = null
fun onShareButton()
{
    ToBeShared?.let { platformShare(it()) }
    LogIt.info("Share Button pressed")
}

// This function should build a title bar (with a back button) if the platform doesn't already have one.  Otherwise it should
// set up the platform's title bar
@Composable fun ConstructTitleBar(nav: ScreenNav, errorText: String, warningText: String, noticeText: String)
{
    if (!platform().hasNativeTitleBar)
    {
        val bkgCol = if (errorText.isNotEmpty()) colorError else if (warningText.isNotEmpty()) colorWarning else if (noticeText.isNotEmpty()) colorNotice else colorTitleBackground

        // Specifying the row height stops changes header bar content to change its height causing the entire window to jerk up or down
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(bkgCol).padding(0.dp).height(32.dp))
        {
            if (nav.hasBack() != ScreenId.None)
            {
                IconButton(onClick = { nav.back() }) {
                    Icon(Icons.Default.ArrowBack, tint = colorTitleForeground, contentDescription = null)
                }
            }
            // We can only fillMaxSize() here because we constrained the height of the row
            if (errorText.isNotEmpty())
                ErrorText(errorText, Modifier.weight(1f).fillMaxSize())
            else if (warningText.isNotEmpty())
                WarningText(warningText, Modifier.weight(1f).fillMaxSize())
            else if (noticeText.isNotEmpty())
                NoticeText(noticeText, Modifier.weight(1f).fillMaxSize())
            //NoticeText(i18n(S.copiedToClipboard))
            else
            {
                TitleText(nav.title(), Modifier.weight(1f).fillMaxSize())
                ResImageView("icons/lock.xml", modifier = Modifier.clickable { triggerUnlockDialog() })
                if (platform().hasShare && nav.currentScreen.value.hasShare) IconButton(onClick = { onShareButton() }) {
                    Icon(Icons.Default.Share, tint = colorTitleForeground, contentDescription = null)
                }
            }
        }
    }
}

// Only needed if we need to reassign the account slots outside of the GUI's control
val accountChangedNotification = Channel<String>()

/** Call this function to cause the GUI to update any view of any accounts.  Provide no arguments to update all of them */
fun triggerAccountsChanged(vararg accounts: Account)
{
    if (accounts.size == 0)
        later { delay(100); accountChangedNotification.send("*all changed*") }
    else for (account in accounts)
    {
        later { delay(100); accountChangedNotification.send(account.name) }
    }
}

// Add other information as needed to drive each page
enum class ShowIt
{
    NONE,
    WARN_BACKUP_RECOVERY_KEY,
    ENTER_PIN
}

var curEventNum = 0L
fun NextEvent(): Long
{
    curEventNum++
    return curEventNum
}

data class GuiDriver(val gotoPage: ScreenId? = null,
  val show: Set<ShowIt>? = null,
  val noshow: Set<ShowIt>? = null,
  val sendAddress: String?=null,
  val amount: BigDecimal?=null,
  val note: String? = null,
  val chainSelector: ChainSelector?=null,
  val account: Account? = null,
  val regenAccountGui: Boolean? = null,
  val withClipboard: ((String?) -> Unit)? = null,
  val tpSession: TricklePaySession? = null,
  val afterUnlock: (()->Unit)? = null,
  val uri: Uri? = null,

  // This is used when the event itself is a recomposable trigger.  Generally this is true, so NextEvent automatically increments.
  // But if you choose this number to be the same as a prior object, the screen will not recompose unless some other state changed.
  val eventNum: Long = NextEvent()
)

val externalDriver = Channel<GuiDriver>()

@Composable fun RecoveryPhraseWarning(account:Account?=null)
{
    Column {
        Text(i18n(S.WriteDownRecoveryPhraseWarning), Modifier.fillMaxWidth().wrapContentHeight(), colorPrimaryDark, maxLines = 10, textAlign = TextAlign.Center,
          fontSize = FontScale(1.25))

        WallyButtonRow {
            WallyRoundedButton({
                externalDriver.trySend(GuiDriver(ScreenId.AccountDetails, noshow = setOf(ShowIt.WARN_BACKUP_RECOVERY_KEY), account = account))
            }) {
                Text(i18n(S.GoThere))
            }
            WallyRoundedButton({
                externalDriver.trySend(GuiDriver(noshow = setOf(ShowIt.WARN_BACKUP_RECOVERY_KEY)))
            }) {
                Text(i18n(S.dismiss))
            }
        }

    }
}

val preferenceDB: SharedPreferences = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
val showIdentityPref = MutableStateFlow(preferenceDB.getBoolean(SHOW_IDENTITY_PREF, false))
val showTricklePayPref = MutableStateFlow(preferenceDB.getBoolean(SHOW_TRICKLEPAY_PREF, false))
val showAssetsPref = MutableStateFlow(preferenceDB.getBoolean(SHOW_ASSETS_PREF, false))

var permanentMenuItems: Set<NavChoice> = setOf(
  NavChoice(ScreenId.Home, S.title_home, "icons/home.xml"),
  NavChoice(ScreenId.Shopping, S.title_activity_shopping, "icons/shopping.xml"),
  NavChoice(ScreenId.Settings, S.title_activity_settings, "icons/gear.xml"),
)

var menuItems: MutableStateFlow<Set<NavChoice>> = MutableStateFlow(permanentMenuItems)

/** Change showing or hiding a menu item */
fun enableNavMenuItem(item: ScreenId, enable:Boolean=true)
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
            buildMenuItems()
            e.commit()
        }
    }
}

fun buildMenuItems()
{
    val items = permanentMenuItems.toMutableSet()

    if(showIdentityPref.value) items.add(NavChoice(ScreenId.Identity, S.title_activity_identity, "icons/person.xml"))
    if(showTricklePayPref.value) items.add(NavChoice(ScreenId.TricklePay, S.title_activity_trickle_pay, "icons/faucet_drip.xml"))
    if(showAssetsPref.value) items.add(NavChoice(ScreenId.Assets, S.title_activity_assets, "icons/invoice.xml"))
    menuItems.value = items.sortedBy { it.location }.toSet()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavigationRoot(nav: ScreenNav)
{
    if (nav.currentScreen.value == ScreenId.Splash)
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
            nav.back()
        }


        return
    }

    val scrollState = rememberScrollState()
    var driver = remember { mutableStateOf<GuiDriver?>(null) }
    var errorText by remember { mutableStateOf("") }
    var warningText by remember { mutableStateOf("") }
    var noticeText by remember { mutableStateOf("") }
    var clickDismiss = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    val selectedAccount = remember { MutableStateFlow<Account?>(wallyApp?.focusedAccount) }

    var unlockDialog by remember { mutableStateOf<(()->Unit)?>(null) }

    val clipmgr: ClipboardManager = LocalClipboardManager.current

    var currentTpSession by remember { mutableStateOf<TricklePaySession?>(null) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }

    buildMenuItems()

    @Composable fun withAccount(then: @Composable (acc: Account) -> Unit)
    {
        // make sure the UX and the app are both tracking the correct focused account
        if (selectedAccount.value != wallyApp!!.focusedAccount)
        {
            // Pick the one the UX set preferentially
            if(selectedAccount.value != null) wallyApp!!.focusedAccount = selectedAccount.value
            else selectedAccount.value = wallyApp!!.focusedAccount
        }
        val pa = selectedAccount.value ?: wallyApp!!.focusedAccount ?: wallyApp?.nullablePrimaryAccount
        //assert(pa?.visible ?: true)
        if (pa == null)
        {
            displayError(S.NoAccounts)
            nav.back()
        }
        else then(pa)

    }

    @Composable fun withUnlockedAccount(then: @Composable (acc: Account) -> Unit)
    {
        val pa = selectedAccount.collectAsState().value
        if (pa == null)
        {
            displayError(S.NoAccounts)
            nav.back()
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

    @Composable fun withTp(then: @Composable (acc: Account, ctp: TricklePaySession) -> Unit)
    {
        val ctp = currentTpSession
        if (ctp == null)
        {
            displayError(S.TpNoSession)  // TODO make this no TP session
            return
        }

        val pa = ctp.getRelevantAccount(selectedAccount.value?.name)
        if (pa == null)
        {
            displayError(S.NoAccounts)
            nav.back()
        }
        else then(pa, ctp)
    }

    // Periodic checking
    LaunchedEffect(true)
    {
        while (true)
        {
            // Check every 10 seconds to see if there are assets in this wallet & enable the menu item if there are
            if (showAssetsPref.value == false && wallyApp?.hasAssets() == true)
            {
                enableNavMenuItem(ScreenId.Assets)
            }
            delay(10000)
        }
    }

    // Allow an external (non-compose) source to "drive" the GUI to a particular state.
    // This implements functionality like scanning/pasting/receiving via a connection a payment request.
    LaunchedEffect(true)
    {
        for(c in externalDriver)
        {
            LogIt.info(sourceLoc() +": external screen driver received")
            driver.value = c
            // If the driver specifies an account, we want to switch to it
            c.account?.let {
                // assert(it.visible)
                selectedAccount.value = it
                wallyApp?.focusedAccount = it
            }
            c.gotoPage?.let { nav.go(it) }
            c.show?.forEach {
                if (it == ShowIt.WARN_BACKUP_RECOVERY_KEY)
                {
                    clickDismiss.value = { RecoveryPhraseWarning(c.account) }
                }
                if (it == ShowIt.ENTER_PIN)
                {
                    LogIt.info(sourceLoc() +": open PIN entry window")
                    unlockDialog = c.afterUnlock ?: {}
                }
            }
            c.noshow?.forEach {
                if (it == ShowIt.WARN_BACKUP_RECOVERY_KEY)
                {
                    clickDismiss.value = null
                }
                if (it == ShowIt.ENTER_PIN)
                {
                    LogIt.info(sourceLoc() +": close PIN entry window")
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
            if (c.tpSession != null) currentTpSession = c.tpSession
            if (c.uri != null) currentUri = c.uri
        }
    }

    LaunchedEffect(true)
    {
        for(alert in alertChannel)
        {
            if (alert.level.level >= AlertLevel.ERROR.level)
            {
                if (alert.msg == "") // clear all alerts this level or below
                {
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
            else if (alert.level.level >= AlertLevel.WARN.level)
            {
                if (alert.msg == "") // clear all alerts this level or below
                {
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
            else if (alert.level.level >= AlertLevel.NOTICE.level)
            {
                if (alert.msg == "") // clear all alerts this level or below
                {
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
        if (isSoftKeyboardShowing.collectAsState().value)
        {
            val keybar = softKeyboardBar.collectAsState().value
            keybar?.invoke(Modifier.padding(0.dp).align(Alignment.BottomStart).fillMaxWidth().background(Color(0xe0d8d8d8)))
        }
    }

    // The main screen
    WallyTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = WallyPageBase) {
            if (unlockDialog != null) UnlockView {  }
            Column(modifier = Modifier.fillMaxSize()) {
                ConstructTitleBar(nav, errorText, warningText, noticeText)

                clickDismiss.value?.let {
                    WallyBrightEmphasisBox(Modifier.fillMaxWidth().wrapContentSize().clickable { clickDismiss.value = null }) { it() }
                }

                // This will take up the most space but leave enough for the navigation menu
                val mod = if (nav.currentScreen.value.isEntirelyScrollable)
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
                    when (nav.currentScreen.value)
                    {
                        ScreenId.None -> HomeScreen(selectedAccount, driver, nav)
                        ScreenId.Splash -> run {} // splash screen is done at the top for max speed and to be outside of the theme
                        ScreenId.Home -> HomeScreen(selectedAccount, driver, nav)
                        ScreenId.SplitBill -> SplitBillScreen(nav)
                        ScreenId.NewAccount -> NewAccountScreen(accountGuiSlots.collectAsState(), devMode, nav)
                        ScreenId.Settings -> SettingsScreen(nav)
                        ScreenId.AccountDetails -> withUnlockedAccount { AccountDetailScreen(it, nav) }
                        ScreenId.Assets -> withAccount { AssetScreen(it, nav) }
                        ScreenId.Shopping -> ShoppingScreen(nav)
                        ScreenId.TricklePay -> withAccount { act -> TricklePayScreen(act, null, nav) }
                        ScreenId.Identity -> withAccount { act ->
                            nav.onDepart { currentUri = null }
                            IdentityScreen(act, currentUri, nav);
                        }
                        ScreenId.IdentityEdit -> withAccount { act ->
                            IdentityEditScreen(act, nav);
                        }
                        ScreenId.AddressHistory ->  withAccount { AddressHistoryScreen(it, nav) }
                        ScreenId.TxHistory -> withAccount { TxHistoryScreen(it, nav) }
                        ScreenId.TpSettings -> withTp { act, ctp -> TricklePayScreen(act, ctp, nav) }
                        ScreenId.SpecialTxPerm -> withTp { act, ctp -> SpecialTxPermScreen(act, ctp, nav) }
                        ScreenId.AssetInfoPerm -> withTp { act, ctp -> AssetInfoPermScreen(act, ctp, nav) }
                        ScreenId.SendToPerm -> withTp { act, ctp -> SendToPermScreen(act, ctp, nav) }
                        ScreenId.IdentityOp -> withAccount { act -> IdentityPermScreen(act, currentUri, nav); }
                        ScreenId.Alerts -> HomeScreen(selectedAccount, driver, nav)  // not currently implemented
                    }
                }
                if (!isSoftKeyboardShowing.collectAsState().value)
                {
                    //Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(NavBarBkg).height(IntrinsicSize.Min).padding(0.dp)) {
                    //    NavigationMenu(nav)
                    val modifier = Modifier.fillMaxWidth().background(NavBarBkg).height(IntrinsicSize.Min).padding(0.dp)
                    NavigationMenu(nav, modifier)

                }

            }
        }
    }
}

object ChildNav {
    private val _displayAccountDetailScreen = MutableStateFlow<Account?>(null)
    val displayAccountDetailScreen: StateFlow<Account?> get() = _displayAccountDetailScreen

    /**
     * Input Account object to display or null to hide
     */
    fun displayAccount(account: Account?) {
        _displayAccountDetailScreen.value = account
    }
}

data class NavChoice(val location: ScreenId, val textId: Int, val imagePath: String)

@Composable
fun NavigationMenu(nav: ScreenNav, modifier: Modifier)
{
    val items by menuItems.collectAsState()

    Column(modifier = modifier) {
        // Horizontal row to layout navigation buttons
        Row(modifier = Modifier.padding(0.dp, 0.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (ch in items)
            {
                if (ch.imagePath != null)
                {
                    Button(
                      onClick = {
                          clearAlerts()  // If the user explicitly moved to a different screen, they must be aware of the alert
                          nav.go(ch.location)
                                },
                      // Change button appearance based on current screen
                      enabled = nav.currentScreen.value != ch.location,
                      shape = RoundedCornerShape(30),
                      contentPadding = PaddingValues(0.dp, 0.dp),
                      // This is opposite of normal: The disabled button is our current screen, so should have the highlight
                      colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = NavBarBkg,
                        disabledContentColor = colorPrimary,
                        containerColor = NavBarBkg,
                        contentColor = colorDefault),
                      //modifier = Modifier.padding(4.dp, 0.dp)
                      modifier = Modifier.width(IntrinsicSize.Max).height(IntrinsicSize.Min).padding(0.dp, 0.dp).defaultMinSize(1.dp, 1.dp)  //width(100.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp), horizontalAlignment = Alignment.CenterHorizontally,
                          modifier = Modifier.width(IntrinsicSize.Max).height(IntrinsicSize.Min).padding(0.dp,4.dp,0.dp,0.dp)
                        ) {
                            ResImageView(ch.imagePath, Modifier.width(30.dp).height(30.dp), description = ch.imagePath)
                            Text(text = i18n(ch.textId), fontSize = 9.sp, modifier = Modifier.padding(0.dp, 0.dp,0.dp, 2.dp).wrapContentWidth(Alignment.CenterHorizontally, true),
                              textAlign = TextAlign.Center, softWrap = false, maxLines = 1)
                        }
                    }
                }
                else
                {
                    // This is opposite of normal: The disabled button is our current screen, so should have the highlight
                    Button(
                      colors = ButtonDefaults.textButtonColors(
                        disabledContainerColor = NavBarBkg,
                        disabledContentColor = colorPrimary,
                        containerColor = NavBarBkg,
                        contentColor = colorDefault,
                      ),
                      contentPadding = PaddingValues(2.dp, 0.dp),
                      //Modifier.padding(2.dp, 0.dp),
                      onClick = { nav.go(ch.location) },
                      // Change button appearance based on current screen
                      enabled = nav.currentScreen.value != ch.location,
                      modifier = Modifier.width(IntrinsicSize.Max).height(IntrinsicSize.Min).padding(0.dp, 0.dp).defaultMinSize(1.dp, 1.dp)
                    ) {
                        Text(i18n(ch.textId), modifier = Modifier.padding(0.dp)
                        )
                    }
                }
                // Some space between buttons
                //Spacer(modifier = Modifier.width(4.dp).height(1.dp))
            }

        }
        WallyDivider()
    }
}
