package info.bitcoinunlimited.www.wally

import info.bitcoinunlimited.www.wally.ui.HomeScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import info.bitcoinunlimited.www.wally.databinding.ActivityUnlockBinding
import androidx.activity.compose.setContent
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import org.nexa.libnexakotlin.Bip44Wallet

class ComposeActivity: CommonActivity()
{
    var nav: ScreenNav? = null
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed()
            {
                if (nav?.back() == null) finish()
            }

        })

        setContent {
            nav = NavigationRoot()
        }
    }
}

