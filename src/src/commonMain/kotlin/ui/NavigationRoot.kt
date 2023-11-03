package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

enum class ScreenNav
{
    Home,
    Identity,
    TricklePay,
    Assets,
    Shopping,
    Settings,
    Dashboard;

    val isEntirelyScrollable:Boolean
        get()
    {
        if (this == Settings) return true
        return false
    }
}

@Composable
fun NavigationRoot()
{
    val currentRootScreen = remember { mutableStateOf(ScreenNav.Home) }
    val scrollState = rememberScrollState()

    WallyTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = WallyPageBase) {
            Column(
              modifier = Modifier.fillMaxSize()
            ) {
                // This will take up the most space but leave enough for the navigation menu
                val mod = if (currentRootScreen.value.isEntirelyScrollable)
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
                    when (currentRootScreen.value)
                    {
                        ScreenNav.Home -> HomeScreen(ChildNav)
                        ScreenNav.Dashboard -> DashboardScreen(400.dp)
                        ScreenNav.Settings -> SettingsScreen()
                        ScreenNav.Assets -> Text("TODO: Implement AssetsScreen")
                        ScreenNav.Shopping -> ShoppingScreen()
                        ScreenNav.TricklePay -> Text("TODO: Implement TricklePayScreen")
                        ScreenNav.Identity -> Text("TODO: Implement IdentityScreen")
                    }
                }

                // This will always be at the bottom and won't overlap with the content above
                Box(modifier = Modifier.fillMaxWidth().background(NavBarBkg).height(IntrinsicSize.Min).padding(0.dp)) {
                    NavigationMenu(currentRootScreen)
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

data class NavChoice(val location: ScreenNav, val textId: Int, val image: ImageVector?)

var bottomNavChoices = mutableListOf<NavChoice>(
  NavChoice(ScreenNav.Home, S.title_home, Icons.Default.Home),
  NavChoice(ScreenNav.Identity, S.title_activity_identity, Icons.Default.Home),
  NavChoice(ScreenNav.TricklePay, S.title_activity_trickle_pay, Icons.Default.Home),
  NavChoice(ScreenNav.Assets, S.title_activity_assets, Icons.Default.Home),
  NavChoice(ScreenNav.Shopping, S.title_activity_shopping, Icons.Default.Home),
  NavChoice(ScreenNav.Dashboard, S.title_dashboard, null),
  NavChoice(ScreenNav.Settings, S.title_activity_settings, Icons.Default.Settings),
  )

@Composable
fun NavigationMenu(currentScreen: MutableState<ScreenNav>)
{
    Column {
        // Horizontal row to layout navigation buttons
        Row(modifier = Modifier.padding(0.dp, 0.dp).height(IntrinsicSize.Min)) {
            for (ch in bottomNavChoices)
            {
                if (ch.image != null)
                {
                    Button(
                      onClick = { currentScreen.value = ch.location },
                      // Change button appearance based on current screen
                      enabled = currentScreen.value != ch.location,
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
                      onClick = { currentScreen.value = ch.location },
                      // Change button appearance based on current screen
                      enabled = currentScreen.value != ch.location,
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
