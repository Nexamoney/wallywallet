package ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.devMode
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.ReceiveScreenContent
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
private val LogIt = GetLog("wally.test")

@OptIn(ExperimentalTestApi::class)
class ReceiveScreenTest:WallyUiTestBase(false)
{
    val cs = ChainSelector.NEXAREGTEST
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

    // Address whose bytes change with the passed seed so the tests can't accidentally
    // collide across scenarios.
    private fun seededAddress(
      chain: ChainSelector = cs,
      seed: Byte = 1,
      index: Long = 1234,
    ): Pay2PubKeyTemplateDestination =
      Pay2PubKeyTemplateDestination(chain, UnsecuredSecret(ByteArray(32) { seed }), index)

    private fun renderReceiveScreenContent(
      account: Account,
      address: PayDestination,
      compose: ComposeUiTest,
    )
    {
        val pill = AccountPill(MutableStateFlow<Account?>(account))
        compose.setContent {
            CompositionLocalProvider(
              LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                ReceiveScreenContent(pill, account, address)
            }
        }
        compose.settle()
    }

    @Test
    fun receiveScreenContentDisplaysAddressText()
    {
        val account = mockAccount(chainSelector = cs)
        val address = seededAddress()
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            val tag = "receiveScreen:receiveAddress"
            onNodeWithTag(tag).isDisplayed()
            onNodeWithTag(tag).assertTextEquals(address.address.toString())
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentRendersQrCode()
    {
        val account = mockAccount(chainSelector = cs)
        val address = seededAddress(seed = 2)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            onNodeWithTag("qrcode").isDisplayed()
            onNodeWithTag("qrcode").assertIsDisplayed()
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentYourAddressLabelUsesNexaregChainUri()
    {
        val account = mockAccount(chainSelector = ChainSelector.NEXAREGTEST)
        val address = seededAddress(chain = ChainSelector.NEXAREGTEST, seed = 3)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            val expected = i18n(S.YourAddress) % mapOf("blockchain" to "nexareg")
            onNodeWithText(expected).isDisplayed()
            onNodeWithText(expected).assertIsDisplayed()
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentYourAddressLabelUsesMainnetChainUri()
    {
        val account = mockAccount(chainSelector = ChainSelector.NEXA)
        val address = seededAddress(chain = ChainSelector.NEXA, seed = 4)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            val expected = i18n(S.YourAddress) % mapOf("blockchain" to "nexa")
            onNodeWithText(expected).isDisplayed()
            onNodeWithText(expected).assertIsDisplayed()
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentWithNullAddressRendersNothing()
    {
        // PayDestination defaults `address` to null; a minimal anonymous subclass
        // keeps the property at null so the composable hits its early return.
        val nullAddressDest = object : PayDestination(cs)
        {
            override val maxSignedSize: Long = 0
            override val pubkey: ByteArray? = null
            override var secret: Secret? = null
            override fun serializeDerived(format: SerializationType): BCHserialized =
              BCHserialized(format)
            override fun clean() {}
        }
        val account = mockAccount(chainSelector = cs)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, nullAddressDest, this)

            onNodeWithTag("receiveScreen:receiveAddress").assertDoesNotExist()
            onNodeWithTag("qrcode").assertDoesNotExist()
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentAddressTextIsClickable()
    {
        val account = mockAccount(chainSelector = cs)
        val address = seededAddress(seed = 5)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            val tag = "receiveScreen:receiveAddress"
            onNodeWithTag(tag).isDisplayed()
            onNodeWithTag(tag).assertHasClickAction()
            onNodeWithTag(tag).performClick()
            settle()
            // Click must not change what's rendered — the same address stays visible.
            onNodeWithTag(tag).assertTextEquals(address.address.toString())
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentQrCodeIsClickable()
    {
        val account = mockAccount(chainSelector = cs)
        val address = seededAddress(seed = 6)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            onNodeWithTag("qrcode").isDisplayed()
            onNodeWithTag("qrcode").assertHasClickAction()
            onNodeWithTag("qrcode").performClick()
            settle()
            onNodeWithTag("qrcode").assertIsDisplayed()
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentShowsDestinationIndexInDevMode()
    {
        devMode = true
        val account = mockAccount(chainSelector = cs)
        val idx = 4242L
        val address = seededAddress(seed = 7, index = idx)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            onNodeWithText("Providing address $idx").isDisplayed()
            onNodeWithText("Providing address $idx").assertIsDisplayed()
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentHidesDestinationIndexWhenDevModeOff()
    {
        devMode = false
        val account = mockAccount(chainSelector = cs)
        val idx = 7777L
        val address = seededAddress(seed = 8, index = idx)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            val tag = "receiveScreen:receiveAddress"
            onNodeWithTag(tag).isDisplayed()
            onNodeWithText("Providing address $idx").assertDoesNotExist()
        }

        account.delete()
    }

    @Test
    fun receiveScreenContentDevModeReflectsDifferentIndexes()
    {
        devMode = true
        val account = mockAccount(chainSelector = cs)
        val address = seededAddress(seed = 9, index = 99L)
        setSelectedAccount(account)

        runComposeUiTest {
            renderReceiveScreenContent(account, address, this)

            onNodeWithText("Providing address 99").isDisplayed()
            onNodeWithText("Providing address 99").assertIsDisplayed()
            // And that it doesn't also render a stale/other index.
            onNodeWithText("Providing address 0").assertDoesNotExist()
            onNodeWithText("Providing address 1234").assertDoesNotExist()
        }

        account.delete()
    }
}
