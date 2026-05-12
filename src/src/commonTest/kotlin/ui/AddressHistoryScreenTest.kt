package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.ui.AddressHistoryScreen
import info.bitcoinunlimited.www.wally.ui.AddressInfo
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.addressHistoryAccount
import info.bitcoinunlimited.www.wally.ui.addressHistoryInfo
import info.bitcoinunlimited.www.wally.ui.calcAddressHistoryInfo
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.Pay2PubKeyTemplateDestination
import org.nexa.libnexakotlin.PayDestination
import org.nexa.libnexakotlin.UnsecuredSecret
import kotlin.test.Test

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
     * Build N mock AddressInfo entries directly from N unique PayDestinations.
     * All appear "unused" (no balance, no receive history) so they fall into
     * the unused section of the rendered LazyColumn. Bypasses
     * injectReceivingAddresses + calcAddressHistoryInfo, which on the Pixel 5
     * emulator are slow and depend on the screen's async LaunchedEffect.
     */
    private fun makeMockAddressInfos(count: Int): List<AddressInfo>
    {
        return makeDestinations(count).map { dest ->
            AddressInfo(
              address = dest.address!!,
              givenOut = false,
              amountHeld = 0L,
              totalReceived = 0L,
              firstRecv = Long.MAX_VALUE,
              lastRecv = Long.MIN_VALUE,
              assetTypesReceived = 0L,
              index = Long.MAX_VALUE,
            )
        }
    }

    /**
     * Renders AddressHistoryScreen with 100 mock AddressInfo entries
     * pre-injected into addressHistoryInfo, and verifies the address at every
     * 25th position displays after scrolling.
     */
    @Test
    fun addressHistoryScreenWith100Addresses() = runComposeUiTest {
        val account = mockAccount()
        val addresses = makeMockAddressInfos(100)
        addressHistoryAccount.value = account
        addressHistoryInfo.value = addresses

        setContent {
            AddressHistoryScreen(account, ScreenNav())
        }

        for (i in 0 until 100 step 25)
        {
            val addrText = addresses[i].address.toString()
            waitForCatching {
                onNodeWithTag("AddressHistoryList").performScrollToNode(hasText(addrText, substring = true))
                onNodeWithText(addrText, substring = true).assertIsDisplayed()
            }
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
     * Renders AddressHistoryScreen with 1000 mock AddressInfo entries
     * pre-injected into addressHistoryInfo. Verifies the LazyColumn handles a
     * large list by scrolling to first and mid-index entries.
     */
    @Test
    fun addressHistoryScreenWith1000Addresses() = runComposeUiTest {
        val account = mockAccount()
        val addresses = makeMockAddressInfos(1000)
        addressHistoryAccount.value = account
        addressHistoryInfo.value = addresses

        setContent {
            AddressHistoryScreen(account, ScreenNav())
        }

        val firstAddr = addresses.first().address.toString()
        waitForCatching {
            onNodeWithTag("AddressHistoryList").performScrollToNode(hasText(firstAddr, substring = true))
            onNodeWithText(firstAddr, substring = true).assertIsDisplayed()
        }

        // A mid-index address — proves the list extends well past the visible
        // window without paying the cost of touring half of the LazyColumn.
        // Explicit <Boolean> + trailing `true` because this waitForCatching is
        // in the runComposeUiTest lambda's tail position, which would otherwise
        // force T = Unit and reject the SemanticsNodeInteraction return.
        val midAddr = addresses[500].address.toString()
        waitForCatching<Boolean> {
            onNodeWithTag("AddressHistoryList").performScrollToNode(hasText(midAddr, substring = true))
            onNodeWithText(midAddr, substring = true).assertIsDisplayed()
            true
        }
    }
}
