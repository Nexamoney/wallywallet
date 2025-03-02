package ui2

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.ui2.ReceiveScreenContent
import info.bitcoinunlimited.www.wally.ui2.setSelectedAccount
import org.nexa.libnexakotlin.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReceiveScreenTest:WallyUiTestBase()
{
    lateinit var viewModelStoreOwner: ViewModelStoreOwner

    @BeforeTest
    fun setup()
    {
        viewModelStoreOwner = object : ViewModelStoreOwner
        {
            override val viewModelStore: ViewModelStore
                get() = ViewModelStore()
        }
    }

    @AfterTest
    fun clean()
    {
    }

    @Test
    fun receiveScreenContentTest()
    {
        val cs = ChainSelector.NEXAREGTEST
        //val account = wallyApp!!.newAccount("receiveScreenContentTest", 0U, "", cs)!!
        val account = Account("rcvScrnContent", chainSelector = cs)
        val address = Pay2PubKeyTemplateDestination(ChainSelector.NEXAREGTEST, UnsecuredSecret(ByteArray(32, { 1.toByte()})), 1234)

        runComposeUiTest {
            //val clipboardText = mutableStateOf<String?>(null)
            // Set selected account to populate the UI
            setSelectedAccount(account)
            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    val clip = LocalClipboardManager.current
                    ReceiveScreenContent(account, address)
                    /*  TODO this code gets the clipboard of the host, not the android device
                    LaunchedEffect(clipboardText) {
                        while(true)
                        {
                            delay(500)
                            clipboardText.value = clip.getText()?.text
                            println("clipboard: ${clipboardText.value}")
                        }
                    }

                     */
                }

            }
            settle()
            val tag = "receiveScreen:receiveAddress"
            waitForCatching(60000) { onNodeWithTag(tag).isDisplayed() }
            println("address: ${address.address.toString()}")
            onNodeWithTag(tag).assertTextEquals(address.address.toString())
            println("Performing click")
            onNodeWithTag(tag).performClick()
            println("Finished")
            settle()
            // waitFor<Boolean> { clipboardText.value == address.address.toString() }
        }
        account.delete()
    }
}