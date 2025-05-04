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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eygraber.uri.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui2.theme.*
import kotlinx.coroutines.*
import info.bitcoinunlimited.www.wally.ui2.theme.WallyThemeUi2
import info.bitcoinunlimited.www.wally.ui2.theme.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.views.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*
import org.nexa.threads.iThread
import org.nexa.threads.millisleep

private val LogIt = GetLog("wally.NavRoot.Ui2")

// Actually inited from preferences by the CommonApp
val showIdentityPref = MutableStateFlow(false)
val showTricklePayPref = MutableStateFlow(false)
val showAssetsPref = MutableStateFlow(false)
val newUI = MutableStateFlow(true)

var permanentMenuItemsUi2: Set<NavChoiceUi2> = if (platform().target == KotlinTarget.iOS)
    setOf(
      NavChoiceUi2(ScreenId.Home, S.title_home, Icons.Default.Home),
      NavChoiceUi2(ScreenId.Assets, S.title_activity_assets, Icons.Default.Image),
      NavChoiceUi2(ScreenId.MoreMenu, S.more, Icons.Default.MoreVert),
    )
else
    setOf(
      NavChoiceUi2(ScreenId.Home, S.title_home, Icons.Default.Home),
      NavChoiceUi2(ScreenId.Assets, S.title_activity_assets, Icons.Default.Image),
      NavChoiceUi2(ScreenId.Shopping, S.title_activity_shopping, Icons.Default.ShoppingCart),
      NavChoiceUi2(ScreenId.MoreMenu, S.more, Icons.Default.MoreVert),
    )


val allMenuItems = if (platform().target == KotlinTarget.iOS)
    setOf(
        NavChoiceUi2(ScreenId.Home, S.title_home, Icons.Default.Home),
        NavChoiceUi2(ScreenId.Assets, S.title_activity_assets, Icons.Default.Image),
        NavChoiceUi2(ScreenId.Identity, S.title_activity_identity, Icons.Default.Person),
        NavChoiceUi2(ScreenId.TricklePay, S.title_activity_trickle_pay, Icons.Default.WaterDrop),
        NavChoiceUi2(ScreenId.Settings, S.title_activity_settings, Icons.Default.Settings),
    )
else
    setOf(
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


val softKeyboardBar:MutableStateFlow<(@Composable (Modifier)->Unit)?> = MutableStateFlow(null)
val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
val isSoftKeyboardShowing = MutableStateFlow(false)


// Only needed if we need to reassign the account slots outside of the GUI's control
val accountChangedNotification = Channel<String>(100, BufferOverflow.DROP_OLDEST)

/** Call this function to cause the GUI to update any view of any accounts.  Provide no arguments to update all of them */
fun triggerAccountsChanged(vararg accounts: Account)
{
    if (accounts.size == 0)
        accountChangedNotification.trySend("*all changed*")
    for (account in accounts)
        accountChangedNotification.trySend(account.name)
}

/** Call this function to cause the GUI to update any view of any accounts.  Provide no arguments to update all of them */
/*
suspend fun suspendTriggerAccountsChanged(vararg accounts: Account)
{
    delay(100)
    if (accounts.size == 0)
        accountChangedNotification.send("*all changed*")
    else for (account in accounts)
    {
        accountChangedNotification.send(account.name)
    }
}
*/

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

val externalDriver = Channel<GuiDriver>(10)

enum class ScreenId
{
    None,
    Splash,
    Home,
    Assets,
    Shopping,
    Send,
    Receive,
    Identity,
    IdentityOp,
    IdentityEdit,
    TricklePay,
    Settings,
    SplitBill,
    NewAccount,
    AccountDetails,
    AddressHistory,
    TxHistory,
    MoreMenu,

    TpSettings,
    SpecialTxPerm,
    AssetInfoPerm,
    SendToPerm,
    Alerts;

    val isEntirelyScrollable:Boolean
        get()
        {
            if (this == Settings) return false
            if (this == NewAccount) return true
            return false
        }

    /** Returns true if this screen should have a share button in the topbar */
    val hasShare:Boolean
        get()
        {
            return this == Home
        }

    fun up(): ScreenId
    {
        return when (this)
        {
            None -> Home
            SplitBill -> Home
            AccountDetails -> Home
            NewAccount -> Home
            SpecialTxPerm -> Home
            AssetInfoPerm -> Home
            SendToPerm -> Home
            TpSettings -> TricklePay
            IdentityEdit -> Identity
            Splash -> Home
            else -> Home
        }
    }

    /** Return true if we want to generate a notification when we go to this screen, because a user decision is needed.
     *  Typically this is only set for the screens that ask the user to authorize something (e.g. TDPP or identity requests)
     *
     *  Whether to generate a notification for a already-foregrounded app is a platform specific decision irrelevant to this return value.
     *  Decide that inside the actual notify/denotify utils.kt implementatons
     */
    val notify:Int?
        get()
    {
        return when (this)
        {
            SpecialTxPerm -> S.SpecialTxNotif
            AssetInfoPerm -> S.AssetNotif
            SendToPerm -> S.PaymentRequest
            IdentityOp -> S.IdentityNotif
            else -> null
        }
    }

    fun title(): String
    {
        fun pva(): String
        {
            return wallyApp?.preferredVisibleAccountOrNull()?.name ?: ""
        }
        return when (this)
        {
            None -> ""
            Splash -> ""
            Home -> ""
            IdentityEdit -> i18n(S.title_activity_identity)
            Identity -> i18n(S.title_activity_identity)
            IdentityOp -> i18n(S.title_activity_identity_op)
            TricklePay -> i18n(S.title_activity_trickle_pay)
            Assets -> i18n(S.assets)
            Shopping -> i18n(S.title_activity_shopping)
            Settings -> i18n(S.title_activity_settings)
            SplitBill -> i18n(S.title_split_bill)
            NewAccount -> i18n(S.title_activity_new_account)
            AccountDetails -> i18n(S.title_activity_account_details) % mapOf("account" to pva())
            AddressHistory -> i18n(S.title_activity_address_history) % mapOf("account" to pva())
            TxHistory -> i18n(S.title_activity_tx_history) % mapOf("account" to pva())

            TpSettings -> i18n(S.title_activity_trickle_pay)
            Alerts -> i18n(S.title_activity_alert_history)

            // TODO make a better title for these permissions screens
            SpecialTxPerm -> i18n(S.title_activity_trickle_pay)
            AssetInfoPerm -> i18n(S.title_activity_trickle_pay)
            SendToPerm -> i18n(S.title_activity_trickle_pay)
            else -> i18n(S.app_name)
        }
    }

}

open class ScreenNav()
{
    enum class Direction {
        LEAVING, DEEPER
    }
    // Screens can put anything into screenSubState to remember their context.
    // This allows them to make the "back" button change subscreen state by pushing the current screenId with a different
    // screenSubState.
    data class ScreenState(val id: ScreenId, val departFn: ((Direction) -> Unit)?, val screenSubState: ByteArray?=null, val data: Any? = null)

    val curData: MutableStateFlow<Any?> = MutableStateFlow(null)
    val currentScreen: MutableStateFlow<ScreenId> = MutableStateFlow(ScreenId.Splash)
    val currentSubState: MutableStateFlow<ByteArray?> = MutableStateFlow(null)
    protected var currentScreenDepart: ((dir: Direction) -> Unit)? = null
    val path = ArrayDeque<ScreenState>(10)

    fun onDepart(fn: (Direction) -> Unit)
    {
        currentScreenDepart = fn
    }

    /** If everything is recomposed, we may have a new mutable screenid tracker */
    fun reset(newMutable: ScreenId)
    {
        currentScreen.value = newMutable
    }

    /** Add a screen onto the stack */
    fun push(screen: ScreenId) = path.add(ScreenState(screen,null))


    /** push the current screen onto the stack, and set the passed screen to be the current one */
    fun go(screen: ScreenId, screenSubState: ByteArray?=null, data: Any? = null): ScreenNav
    {
        clearAlerts()
        currentScreenDepart?.invoke(Direction.DEEPER)
        path.add(ScreenState(currentScreen.value,currentScreenDepart, currentSubState.value, curData.value ))
        currentScreen.value = screen
        currentSubState.value = screenSubState
        curData.value = data
        currentScreenDepart = null
        NativeTitle(title())
        screen.notify?.let {
            val notifId = notify(i18n(it), "", false)
            currentScreenDepart = { denotify(notifId) }
        }
        return this
    }

    /** move without pushing the current screen (but depart will be called if it exists) */
    fun switch(screen: ScreenId, screenSubState: ByteArray?=null, data: Any? = null): ScreenNav
    {
        currentScreenDepart?.invoke(Direction.LEAVING)
        currentScreen.value = screen
        currentSubState.value = screenSubState
        curData.value = data
        currentScreenDepart = null
        NativeTitle(title())
        return this
    }

    fun title() = currentScreen.value.title()

    /** return the destination screenId if you can go back from here, otherwise ScreenId.None */
    fun hasBack(): ScreenId
    {
        var priorId:ScreenId = ScreenId.None
        val prior = path.lastOrNull()
        // If I can't go back, go up
        priorId = prior?.id ?: currentScreen.value.up()
        return priorId
    }

    /** pop the current screen from the stack and go there */
    fun back():ScreenId?
    {
        clearScreenAlerts()
        currentScreenDepart?.invoke(Direction.LEAVING)
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
            // If the screen is none, that means to keep going back but this will execute any currentScreenDepart
            // associated with the None screen which is how we install a "finish activity" in Android
            if (priorId == ScreenId.None)
            {
                return back()
            }
            else currentScreen.value = priorId
        }  // actually trigger going back
        NativeTitle(title())
        return priorId
    }
}
/** Global top level navagation */
val nav = ScreenNav()

fun assignAccountsGuiSlots()
{
    // We have a Map of account names to values, but we need a list
    // Sort the accounts based on account name
    accountGuiSlots.value = wallyApp!!.orderedAccounts()
}


const val TRIGGER_BUG_DELAY = 100L

fun triggerAssignAccountsGuiSlots()
{
    // If the focused account got hidden, then it can't be focused
    val fa = wallyApp?.focusedAccount?.value
    if (fa != null)
    {
        if (fa.visible == false) wallyApp?.focusedAccount?.value = null
    }
    // If the slots got shuffled around, maybe the current receive was deleted or hidden
    assignAccountsGuiSlots()
}

fun MutableStateFlow<Int>.interpolate(timeMs: Int, start: Int?=0, end: Int)
{
    val FRAME_TIME = 20  // how
    val numSteps = timeMs/FRAME_TIME
    val st = start ?: value
    val delta = end-st
    val incr = delta.toFloat()/(numSteps+1)
    var cur:Float = st.toFloat()
    tlater {
        for (i in 0 until numSteps)
        {
            cur = cur + incr
            this.value = cur.toInt()
            millisleep(FRAME_TIME.toULong())
        }
        this.value = end.toInt()
    }
}

fun triggerUnlockDialog(show: Boolean = true, then: ((String)->Unit)? = {})
{
    if (show)
    {
        /*
        later {
            delay(TRIGGER_BUG_DELAY); externalDriver.send(GuiDriver(show = setOf(ShowIt.ENTER_PIN), afterUnlock = then))
        }
         */
        clearAlerts()
        unlockThen = then
        unlockTileSize.interpolate(300, null, 300)
    }
    else
    {
        //later { delay(TRIGGER_BUG_DELAY); externalDriver.send(GuiDriver(noshow = setOf(ShowIt.ENTER_PIN))) }
        unlockTileSize.interpolate(300, null, 0)
    }
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
fun enableNavMenuItem(item: ScreenId, enable:Boolean=true)
{
    later {
        var changed = false
        val e = wallyApp!!.preferenceDB.edit()
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
            buildMenuItemsUi2()
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
        enableNavMenuItem(ScreenId.Assets)
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

fun observeReceiveDestination(account: Account)
{
    account.access.lock {
        if (account.walletOnChange == -1)
        {
            account.walletOnChange = account.wallet.setOnWalletChange { wallet, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try
                    {
                        val tmp = wallet.getCurrentDestination()
                        account.access.lock { account.currentReceive  = tmp }
                    }
                    catch (e: WalletException) // closed
                    {
                        account.access.lock {
                            if (account.wallet.isDeleted)
                            {
                                if (account.walletOnChange != -1)
                                {
                                    account.wallet.removeOnWalletChange(account.walletOnChange)
                                    account.walletOnChange = -1
                                }
                                wallyApp?.accounts?.remove(account.name)
                            }
                        }
                    }
                }
            }
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
        wallyApp!!.focusedAccount.value = account
        setReceiveDestination(account)
        observeReceiveDestination(account)
        wallyApp!!.preferenceDB.edit().putString(SELECTED_ACCOUNT_NAME_PREF, account.name).commit()
    }
}

val handler = CoroutineExceptionHandler {
    _, exception -> LogIt.error("Caught in NavigationRootUi2 CoroutineExceptionHandler: $exception")
}

fun setReceiveDestination(account: Account)
{
    laterJob {
        // Blocking operation, we don't offer a destination until we are sure its installed in connected nodes
        val payDestination = account.wallet.getCurrentDestination()
        account.currentReceive = payDestination
    }

    /*
    lateinit var payDestination: PayDestination
    // This line of code hangs because of getCurrentDestination() when an account with many addresses and functions is syncing.
    // Added a timeout to use a default unused address when getCurrentDestination() silently blocks while syncing.
    // TODO: Refactor libnexakotlin's getCurrentDestination() to suspend function using delay(50) instead of millisleep(50..) so withTimeout can interrupt it
    // TODO: https://gitlab.com/nexa/libnexakotlin/-/issues/24

    val job = CoroutineScope(Dispatchers.IO + handler).launch {
        payDestination = account.wallet.getCurrentDestination() // Blocking operation
        CoroutineScope(Dispatchers.Default + handler).launch {
            // TODO: Disable until sync is complete if address privacy is enabled?
            account.currentReceive = payDestination
        }
    }
    CoroutineScope(Dispatchers.IO + handler).launch {
        delay(2000)
        if (job.isActive)
        {
            // Timeout occurred, Get a non-private fallback address

            // Disable if account privacy is set
            val addressPrivacy = (account.flags and ACCOUNT_FLAG_REUSE_ADDRESSES) == 0UL
            if (!addressPrivacy)
            {
                account.currentReceive?.let { destination ->
                    launch(Dispatchers.Default + handler) {
                        account.currentReceive = destination
                    }
                }
                account.wallet.unusedAddresses.let { unusedAddresses ->
                    account.wallet.generateDestinationsInto(unusedAddresses)
                    if (unusedAddresses.size > 0)
                    {
                        val destination = account.wallet.walletDestination(unusedAddresses.first())
                        if (destination != null)
                            CoroutineScope(Dispatchers.Default + handler).launch {
                                account.currentReceive = destination
                            }
                    }
                }
            }
            else
            {
                account.currentReceive = null
            }
        }
    }
     */
}

fun noSelectedAccount()
{
    wallyApp?.let {
        if (it.focusedAccount.value != null)
        {
            it.preferenceDB.edit().putString(SELECTED_ACCOUNT_NAME_PREF, "").commit()
            it.focusedAccount.value = null
        }
    }
}

/*
    This is the root Composable while we still have two implementations of the UI.
    hasNewUIShared toggled when the user select "new user interface" in settings.
 */
@Composable
fun UiRoot(rootModifier: Modifier, systemPadding: WindowInsets)
{
    val newUi = newUI.collectAsState().value

    if (newUi)
        NavigationRootUi2(rootModifier, systemPadding)
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
                  modifier = Modifier.width(IntrinsicSize.Max).wrapContentHeight().padding(0.dp, 0.dp).defaultMinSize(1.dp, 1.dp).testTag(if (ch.location == ScreenId.Home) "HomeButton" else "${ch.location}Button")
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(bkgCol).padding(0.dp).height(56.dp).testTag("TopBar"))
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
                }, modifier = Modifier.testTag("backButton")) {
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
                IconButton(onClick = { triggerUnlockDialog(true) }, modifier = Modifier.size(iconButtonSize).testTag("GlobalLockIcon")){
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
fun NavigationRootUi2(
  rootModifier: Modifier,
  systemPadding: WindowInsets,
  accountPillViewModel: AccountPillViewModel = viewModel { AccountPill(wallyApp!!.focusedAccount) },
  assetViewModel: AssetViewModel = viewModel { AssetViewModel() },
  accountUiDataViewModel: AccountUiDataViewModel = viewModel { AccountUiDataViewModel() },
)
{
    val curScreen = nav.currentScreen.collectAsState().value
    val subScreen = nav.currentSubState.collectAsState().value

    ToBeShared = {
        wallyApp!!.focusedAccount.value?.currentReceive?.address?.toString() ?: "Address missing"
    }

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
    else if (curScreen == ScreenId.Assets)
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

    val selectedAccountState = wallyApp!!.focusedAccount.collectAsState()
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

    /** Show the passed composable with the selected account if its unlocked, otherwise ask for an unlock first. */
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
                    // If the unlock failed, then we need to go back because the user did not gain permission to see this screen
                    if (pa.locked) nav.back()
                    // Regardless, dismiss the unlock dialog
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
            c.gotoPage?.let {it ->
                clearAlerts()  // If the user explicitly moved to a different screen, they must be aware of the alert
                nav.go(it, data = c.tpSession)
            }
            c.show?.forEach {
                if (it == ShowIt.WARN_BACKUP_RECOVERY_KEY)
                {
                    isShowingRecoveryWarning = true
                }
            }
            c.noshow?.forEach {
                if (it == ShowIt.WARN_BACKUP_RECOVERY_KEY)
                {
                    isShowingRecoveryWarning = false
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

    // This is the IME keyboard bar.  So this box is on top of the main screen
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

    // TODO insets are not working on Compose for android
    //val navBars = WindowInsets.navigationBars
    //val sysBars = WindowInsets.systemBars
    //LogIt.info("navBars ${navBars} sysBars ${sysBars}")

    // The main screen
    Scaffold(
          modifier =
          // Make both the title and the bottom system menu the same color, to bracket the main part of the app
          rootModifier.background(colorTitleBackground).padding(systemPadding.asPaddingValues()).pointerInput(Unit) {
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
          }.testTag("RootScaffold"),
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
                                if (ch.location == ScreenId.MoreMenu)
                                {
                                    if (!expanded.value)
                                    {
                                        scaffoldSheetState.bottomSheetState.expand()
                                    }
                                    else
                                    {
                                        scope.launch {
                                            scaffoldSheetState.bottomSheetState.hide()
                                        }
                                    }
                                    expanded.value = !expanded.value
                                }
                                else
                                {
                                    if (expanded.value)
                                    {
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
                              if (lastClicked.value != ch.location.toString())
                              {
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
                Box(modifier = Modifier.fillMaxSize().background(Color.White).padding(innerPadding)) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        if (isShowingRecoveryWarning)
                            RecoveryPhraseWarningUi2(Modifier.clickable { isShowingRecoveryWarning = false })
                        UnlockTile()

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
                            LaunchedEffect(curScreen) {
                                if (curScreen != ScreenId.MoreMenu)
                                    lastClicked.value = curScreen.toString()
                            }
                            when (curScreen)
                            {
                                ScreenId.None -> HomeScreenUi2(isShowingRecoveryWarning, accountPillViewModel, assetViewModel, accountUiDataViewModel)
                                ScreenId.Splash -> run {} // splash screen is done at the top for max speed and to be outside of the theme
                                ScreenId.MoreMenu -> run {}
                                ScreenId.Home ->
                                {
                                    HomeScreenUi2(isShowingRecoveryWarning, accountPillViewModel, assetViewModel, accountUiDataViewModel)
                                }

                                ScreenId.Send -> withAccount { act -> withSendNavParams { SendScreen(act, it) } }
                                ScreenId.Receive ->
                                {
                                    ReceiveScreen()
                                }

                                ScreenId.SplitBill -> SplitBillScreen()
                                ScreenId.NewAccount ->
                                {
                                    NewAccountScreenUi2(accountGuiSlots.collectAsState(), devMode)
                                }

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

                                ScreenId.AddressHistory -> withAccount { AddressHistoryScreen(it, nav) }
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

                                ScreenId.Alerts -> HomeScreenUi2(isShowingRecoveryWarning, accountPillViewModel, assetViewModel, accountUiDataViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
    // The material theme for the whole app is set here.
}
