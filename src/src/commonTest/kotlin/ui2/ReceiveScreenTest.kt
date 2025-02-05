package ui2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.CommonApp
import info.bitcoinunlimited.www.wally.KotlinTarget
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui2.BalanceViewModelFake
import info.bitcoinunlimited.www.wally.ui2.ReceiveScreenContent
import info.bitcoinunlimited.www.wally.ui2.SyncViewModelFake
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui2.views.AccountUiDataViewModelFake
import info.bitcoinunlimited.www.wally.wallyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReceiveScreenTest:WallyUiTestBase()
{
    lateinit var viewModelStoreOwner: ViewModelStoreOwner
    lateinit var address: Pay2PubKeyTemplateDestination

    @BeforeTest
    fun setup()
    {
        viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore
                get() = ViewModelStore()
        }
        address = Pay2PubKeyTemplateDestination(ChainSelector.NEXA, UnsecuredSecret(ByteArray(32, { 1.toByte()})), 1234)
    }

    @AfterTest
    fun clean()
    {
    }

    @Test
    fun receiveScreenContentTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("receiveScreenContentTest", 0U, "", cs)!!

        runComposeUiTest {
            // Set selected account to populate the UI
            setSelectedAccount(account)

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    ReceiveScreenContent(address, Modifier)
                }
            }
            settle()
            onNodeWithText(address.address.toString()).isDisplayed()
            onNodeWithText(address.address.toString()).performClick()
            settle()
        }
    }
}