package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    Box(modifier = Modifier.fillMaxSize()) {
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
              modifier = Modifier.fillMaxWidth()
            ) {
                NavigationMenu(currentScreen)
            }
        }
    }
}

@Composable
fun NavigationMenu(currentScreen: MutableState<ScreenNav>) {
    // Horizontal row to layout navigation buttons
    Row(
        modifier = Modifier.padding(16.dp)
    ) {
        // Home Button
        Button(
          onClick = { currentScreen.value = ScreenNav.Home },
          // Change button appearance based on current screen
          enabled = currentScreen.value != ScreenNav.Home
        ) {
            Text("Home")
        }

        // Some space between buttons
        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = { currentScreen.value = ScreenNav.Dashboard },
            // Change button appearance based on current screen
            enabled = currentScreen.value != ScreenNav.Dashboard
        ) {
            Text("Dashboard")
        }

        // Some space between buttons
        Spacer(modifier = Modifier.width(8.dp))

        // Profile Button
        Button(
            onClick = { currentScreen.value = ScreenNav.Settings },
            // Change button appearance based on current screen
            enabled = currentScreen.value != ScreenNav.Settings
        ) {
            Text("Settings")
        }
    }
}
