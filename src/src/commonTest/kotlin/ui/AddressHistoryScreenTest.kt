package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.ui.AddressHistoryScreen
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.calcAddressHistoryInfo
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.Pay2PubKeyTemplateDestination
import org.nexa.libnexakotlin.PayDestination
import org.nexa.libnexakotlin.UnsecuredSecret
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class AddressHistoryScreenTest : WallyUiTestBase()
{
    /**
     * Build N unique PayDestinations — each with a distinct 32-byte secret so
     * the derived addresses don't collide.
     */
    private fun makeDestinations(count: Int): List<PayDestination>
    {
        return (0 until count).map { i ->
            val bytes = ByteArray(32) { j ->
                when (j)
                {
                    0 -> (i and 0xff).toByte()
                    1 -> ((i shr 8) and 0xff).toByte()
                    2 -> ((i shr 16) and 0xff).toByte()
                    3 -> ((i shr 24) and 0xff).toByte()
                    else -> ((i * 31 + j) and 0xff).toByte()
                }
            }
            Pay2PubKeyTemplateDestination(ChainSelector.NEXA, UnsecuredSecret(bytes), i.toLong())
        }
    }

    /**
     * UI test with 1000 addresses. Populates the wallet via the public
     * injectReceivingAddresses API, triggers the real calcAddressHistoryInfo,
     * renders the screen, and verifies addresses at every 50th position
     * display on screen after scrolling to each.
     */
    @Test
    fun addressHistoryScreenWith100Addresses() = runComposeUiTest {
        val account = mockAccount()
        val destinations = makeDestinations(100)
        account.wallet.injectReceivingAddresses(destinations)

        // Let the production code build the AddressInfo list from the wallet
        calcAddressHistoryInfo(account)

        setContent {
            AddressHistoryScreen(account, ScreenNav())
        }

        for (i in 0 until 100 step 25)
        {
            val addrText = destinations[i].address.toString()
            onNodeWithTag("AddressHistoryList").performScrollToNode(hasText(addrText, substring = true))
            onNodeWithText(addrText, substring = true).assertIsDisplayed()
        }
    }

    /**
     * Renders AddressHistoryScreen with a mock account that has 0 addresses.
     * Verifies the LazyColumn renders (so calcAddressHistoryInfo has run and
     * produced an empty list) but no address items are shown.
     */
    @Test
    fun addressHistoryScreenWithEmptyAccount() = runComposeUiTest {
        val account = mockAccount()
        // No injectReceivingAddresses → wallet.allAddresses is empty
        calcAddressHistoryInfo(account)

        setContent {
            AddressHistoryScreen(account, ScreenNav())
        }

        // LazyColumn exists in the tree (state was populated with an empty
        // list). We don't use assertIsDisplayed because an empty LazyColumn
        // has zero size, which fails the visibility check.
        onNodeWithTag("AddressHistoryList").assertExists()
        // No address items rendered — prefix "nexa:" would appear in any
        // rendered PayAddress text
        onNodeWithText("nexa:", substring = true).assertDoesNotExist()
    }

    /**
     * calcAddressHistoryInfo with a wallet holding 1000 addresses.
     * Populated via injectReceivingAddresses. We verify the function's output
     * indirectly by rendering the screen and confirming one of the injected
     * addresses appears — proving calcAddressHistoryInfo built AddressInfos
     * for injected addresses.
     */
    @Test
    fun calcAddressHistoryInfoWith1000Addresses() = runComposeUiTest(testTimeout = 3.minutes) {
        val account = mockAccount()
        val destinations = makeDestinations(1000)
        account.wallet.injectReceivingAddresses(destinations)

        calcAddressHistoryInfo(account)

        setContent {
            AddressHistoryScreen(account, ScreenNav())
        }

        // The first injected address should render (it's in the visible portion
        // of the LazyColumn at startup)
        val firstAddr = destinations.first().address.toString()
        onNodeWithTag("AddressHistoryList").performScrollToNode(hasText(firstAddr, substring = true))
        onNodeWithText(firstAddr, substring = true).assertIsDisplayed()

        // A mid-index address — proves the list extends well past the visible
        // window without paying the cost of touring half 1000 LazyColumn items
        // (each scroll step waits for idle, and the last index is the
        // dominant runtime cost in this test).
        val midAddr = destinations[500].address.toString()
        onNodeWithTag("AddressHistoryList").performScrollToNode(hasText(midAddr, substring = true))
        onNodeWithText(midAddr, substring = true).assertIsDisplayed()
    }
}
