package info.bitcoinunlimited.www.wally

import info.bitcoinunlimited.www.wally.ui.HomeScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import info.bitcoinunlimited.www.wally.databinding.ActivityUnlockBinding
import androidx.activity.compose.setContent
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import org.nexa.libnexakotlin.Bip44Wallet

class ComposeActivity: CommonActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContent {
            NavigationRoot()
        }
    }
}