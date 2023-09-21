package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.theme.*
import org.nexa.libnexakotlin.Bip44Wallet

enum class ScreenNav
{
    Home,
    Dashboard,
    Settings
}

@Composable
fun NavigationRoot(accounts: MutableMap<String, Bip44Wallet>)
{
    val currentScreen = remember { mutableStateOf(ScreenNav.Home) }
    val scrollState = rememberScrollState()

    WallyTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = WallyPageBase) {
            Column(
              modifier = Modifier.fillMaxSize()
            ) {
                // This will take up the most space but leave enough for the navigation menu
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .fillMaxWidth()
                ) {
                    when (currentScreen.value)
                    {
                        ScreenNav.Home -> HomeScreen()
                        ScreenNav.Dashboard -> DashboardScreen(400.dp, accounts)
                        ScreenNav.Settings -> SettingsScreen()
                    }
                }

                // This will always be at the bottom and won't overlap with the content above
                Box(
                  modifier = Modifier.fillMaxWidth().background(NavBarBkg)
                ) {
                    NavigationMenu(currentScreen)
                }
            }
        }
    }
}

data class NavChoice(val location: ScreenNav, val textId: Int)  // TODO add icon

var bottomNavChoices = mutableListOf<NavChoice>(
  NavChoice(ScreenNav.Home, S.title_home),
  NavChoice(ScreenNav.Dashboard, S.title_dashboard),
  NavChoice(ScreenNav.Settings, S.title_activity_settings))

@Composable
fun NavigationMenu(currentScreen: MutableState<ScreenNav>) {
    // Horizontal row to layout navigation buttons
    Row(
        modifier = Modifier.padding(4.dp)
    ) {
        for (ch in bottomNavChoices)
        {
            // This is opposite of normal: The disabled button is our current screen, so should have the highlight
            Button(
              colors = ButtonDefaults.textButtonColors(
                disabledContainerColor = colorPrimary,
                disabledContentColor = colorPrimaryDark,
                containerColor = BaseBkg,
                contentColor = colorDefault,
              ),
              contentPadding = PaddingValues(2.dp),
              //Modifier.padding(2.dp, 0.dp),
              onClick = { currentScreen.value = ch.location },
              // Change button appearance based on current screen
              enabled = currentScreen.value != ch.location
            ) {
                Text(i18n(ch.textId), modifier = Modifier.padding(0.dp)
                )
            }
            // Some space between buttons
            Spacer(modifier = Modifier.width(8.dp))
        }

    }
}
