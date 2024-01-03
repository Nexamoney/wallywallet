package info.bitcoinunlimited.www.wally

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import kotlinx.coroutines.delay
import org.nexa.libnexakotlin.launch


fun SetTitle(title: String)
{
    val ca = currentActivity
    if (ca != null)
    {
        ca.setTitle(title)
    }
}
class ComposeActivity: CommonActivity()
{
    var nav = ScreenNav()
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed()
            {
                if (nav?.back() == null) finish()
            }

        })

        // Wait for accounts to be loaded before we show the screen
        launch {
            while(!coinsCreated) delay(250)
            setContent {
                val currentRootScreen = remember { mutableStateOf(ScreenId.Home) }
                //val n = ScreenNav(currentRootScreen)
                nav.reset(currentRootScreen)
                SetTitle(nav.title())
                NavigationRoot(nav)
            }
        }
    }
}

