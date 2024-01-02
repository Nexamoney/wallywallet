package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.ui.theme.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp


import androidx.compose.ui.graphics.vector.ImageVector
import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** return true if this platform has a native title bar (and therefore do not generate one). */
var hasNativeTitleBar = true

enum class ScreenId
{
    None,
    Home,
    Identity,
    TricklePay,
    Assets,
    Shopping,
    Settings,
    SplitBill,
    NewAccount,
    AccountDetails,
    Test;

    val isEntirelyScrollable:Boolean
        get()
    {
        if (this == Settings) return true
        return false
    }

    fun up(): ScreenId
    {
        return when (this)
        {
            SplitBill -> Home
            AccountDetails -> Home
            NewAccount -> Home
            else -> None
        }
    }

    fun title(): String
    {
        return when (this)
        {
            None -> ""
            Home -> i18n(S.app_name)
            Identity -> i18n(S.title_activity_identity)
            TricklePay -> i18n(S.title_activity_trickle_pay)
            Assets -> i18n(S.title_activity_assets)
            Shopping -> i18n(S.title_activity_shopping)
            Settings -> i18n(S.title_activity_settings)
            SplitBill -> i18n(S.title_split_bill)
            NewAccount -> i18n(S.title_activity_new_account)
            AccountDetails -> i18n(S.title_activity_account_details)
            Test -> "Test"
        }
    }
}

class ScreenNav()
{
    var currentScreen: MutableState<ScreenId> = mutableStateOf(ScreenId.Home)
    val path = ArrayDeque<ScreenId>(10)

    /** If everything is recomposed, we may have a new mutable screenid tracker */
    fun reset(newMutable: MutableState<ScreenId>)
    {
        currentScreen = newMutable
    }

    /** Add a screen onto the stack */
    fun push(screen: ScreenId) = path.add(screen)


    /* push the current screen onto the stack, and set the passed screen to be the current one */
    fun go(screen: ScreenId)
    {
        path.add(currentScreen.value)
        currentScreen.value = screen
    }

    fun title() = currentScreen.value.title()

    /* pop the current screen from the stack and go there */
    fun back():ScreenId?
    {
        // See if there is anything in the back stack.
        var prior = path.removeLastOrNull()
        // If I can't go back, go up
        if (prior == null) prior = currentScreen.value.up()
        if (prior != null) currentScreen.value = prior
        return prior
    }
}

fun assignAccountsGuiSlots(): ListifyMap<String, Account>
{
    // We have a Map of account names to values, but we need a list
    // Sort the accounts based on account name
    val lm: ListifyMap<String, Account> = ListifyMap(wallyApp!!.accounts, { it.value.visible }, object : Comparator<String>
    {
        override fun compare(p0: String, p1: String): Int
        {
            if (wallyApp?.nullablePrimaryAccount?.name == p0) return Int.MIN_VALUE
            if (wallyApp?.nullablePrimaryAccount?.name == p1) return Int.MAX_VALUE
            return p0.compareTo(p1)
        }
    })

    /*  TODO set up change notifications moving upwards from the wallets
    for (c in wallyApp!!.accounts.values)
    {
        c.wallet.setOnWalletChange({ it -> onWalletChange(it) })
        c.wallet.blockchain.onChange = { it -> onBlockchainChange(it) }
        c.wallet.blockchain.net.changeCallback = { _, _ -> onWalletChange(c.wallet) }  // right now the wallet GUI update function also updates the cnxn mgr GUI display
        c.onChange()  // update all wallet UI fields since just starting up
    }
     */

    return lm
}

// This function should build a title bar (with a back button) if the platform doesn't already have one.  Otherwise it should
// set up the platform's title bar
@Composable fun ConstructTitleBar(nav: ScreenNav, title: Int)
{
    if (!hasNativeTitleBar)
    {
        Row(verticalAlignment = Alignment.CenterVertically)
        {
            IconButton(onClick = { nav.back() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
            TitleText(title, Modifier.weight(2f))
        }
    }
}

@Composable
fun NavigationRoot(nav: ScreenNav)
{
    val scrollState = rememberScrollState()
    val accountGuiSlots = mutableStateOf(assignAccountsGuiSlots())

    WallyTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = WallyPageBase) {
            Column(
              modifier = Modifier.fillMaxSize()
            ) {
                // This will take up the most space but leave enough for the navigation menu
                val mod = if (nav.currentScreen.value.isEntirelyScrollable)
                {
                    Modifier.weight(1f).verticalScroll(scrollState).fillMaxWidth()
                }
                else
                {
                   Modifier.weight(1f).fillMaxWidth()
                }
                Box(
                  modifier = mod
                ) {
                    when (nav.currentScreen.value)
                    {
                        ScreenId.None -> HomeScreen(accountGuiSlots, nav, ChildNav)
                        ScreenId.Home -> HomeScreen(accountGuiSlots, nav, ChildNav)
                        ScreenId.SplitBill -> SplitBillScreen(nav)
                        ScreenId.NewAccount -> NewAccountScreen(accountGuiSlots, devMode, nav)
                        ScreenId.Test -> TestScreen(400.dp)
                        ScreenId.Settings -> SettingsScreen(nav)
                        ScreenId.AccountDetails -> Text("TODO: Unexpected top level account details")
                        ScreenId.Assets -> Text("TODO: Implement AssetsScreen")
                        ScreenId.Shopping -> ShoppingScreen(nav)
                        ScreenId.TricklePay -> Text("TODO: Implement TricklePayScreen")
                        ScreenId.Identity -> Text("TODO: Implement IdentityScreen")
                    }
                }

                // This will always be at the bottom and won't overlap with the content above
                Box(modifier = Modifier.fillMaxWidth().background(NavBarBkg).height(IntrinsicSize.Min).padding(0.dp)) {
                    NavigationMenu(nav)
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

data class NavChoice(val location: ScreenId, val textId: Int, val image: ImageVector?)

var bottomNavChoices = mutableListOf<NavChoice>(
  NavChoice(ScreenId.Home, S.title_home, Icons.Default.Home),
  NavChoice(ScreenId.Identity, S.title_activity_identity, Icons.Default.Home),
  NavChoice(ScreenId.TricklePay, S.title_activity_trickle_pay, Icons.Default.Home),
  NavChoice(ScreenId.Assets, S.title_activity_assets, Icons.Default.Home),
  NavChoice(ScreenId.Shopping, S.title_activity_shopping, Icons.Default.Home),
  NavChoice(ScreenId.Settings, S.title_activity_settings, Icons.Default.Settings),
  NavChoice(ScreenId.Test, S.title_test, null),
  )

@Composable
fun NavigationMenu(nav: ScreenNav)
{
    Column {
        // Horizontal row to layout navigation buttons
        Row(modifier = Modifier.padding(0.dp, 0.dp).height(IntrinsicSize.Min)) {
            for (ch in bottomNavChoices)
            {
                if (ch.image != null)
                {
                    Button(
                      onClick = { nav.go(ch.location) },
                      // Change button appearance based on current screen
                      enabled = nav.currentScreen.value != ch.location,
                      shape = RoundedCornerShape(50),
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
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp),
                          modifier = Modifier.width(IntrinsicSize.Max).height(IntrinsicSize.Min)
                        ) {
                            Icon(ch.image, i18n(ch.textId), Modifier.padding(0.dp).fillMaxWidth())  // .background(Color.Blue) for debugging
                            val tmp = TextStyle.Default.copy(lineHeight = 0.em,
                              lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None))
                            // I want this text to be able to push the button larger.  But that appears to not be happening
                            Text(text = i18n(ch.textId), fontSize = 9.sp, modifier = Modifier.padding(4.dp, 0.dp).wrapContentWidth(Alignment.CenterHorizontally, true), // .background(Color.Red),
                              style = tmp, textAlign = TextAlign.Center, softWrap = false, maxLines = 1)
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
                Spacer(modifier = Modifier.width(4.dp).height(1.dp))
            }

        }
        WallyDivider()
    }
}
