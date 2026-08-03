package ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.util.fastJoinToString
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.NexaRegtestRpcPort
import org.nexa.libnexakotlin.epochSeconds
import org.nexa.libnexakotlin.sourceLoc
import org.nexa.nexarpc.NexaRpc
import org.nexa.nexarpc.NexaRpcFactory
import org.nexa.threads.millisleep
import kotlin.test.Test

private val LogIt = GetLog("wally.test.SystemTests")

val RPC_USER = "regtest"
val RPC_PASSWORD = "regtest"
val REGTEST_IP="127.0.0.1"
fun getNexaRpc(): NexaRpc
{
    LogIt.info("This test requires a Nexa full node running on regtest at ${REGTEST_IP} and port $NexaRegtestRpcPort")
    // Set up RPC connection
    val rpcConnection = "http://$RPC_USER:$RPC_PASSWORD@$REGTEST_IP:" + NexaRegtestRpcPort
    val nexaRpc = NexaRpcFactory.create(rpcConnection)
    val tipIdx = nexaRpc.getblockcount()
    if (tipIdx < 102)
        nexaRpc.generate((101 - tipIdx).toInt())
    else
    {
        val tip = nexaRpc.getblock(tipIdx)
        // The tip is so old that this node won't think its synced so we need to produce a block
        if (epochSeconds() - tip.time > 1000) nexaRpc.generate(1)
    }
    return nexaRpc
}

fun cleanupAccounts(actName:String?=null, maxActs: Int?=3)
{
    if (maxActs != null)
    {
        if (wallyApp!!.accounts.size > maxActs)
        {
            val cpy = wallyApp!!.accounts.values.toList()
            for (a in cpy) wallyApp!!.deleteAccount(a)
        }
    }
    val existing = wallyApp!!.accounts[actName]
    if (existing != null)
    {
        wallyApp!!.deleteAccount(existing)
        millisleep(500U)
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.wallyVerifyAccountSelected(actName: String)
{
    // Wait until home page comes up, verify that the new account is selected
    waitForCatching {
                onNode(hasTestTag("CarouselAccountName") and hasText(actName), useUnmergedTree = true).assertTextEquals(actName)
    }
}

@OptIn(ExperimentalTestApi::class)
class SystemTests:WallyUiTestBase()
{
    val actName = "singleAddressTest"
    @Test
    fun singleAddressTest()
    {
        /*
        devMode = true
        val cs = ChainSelector.NEXAREGTEST
        cleanupAccounts(actName, 3)
        val account1 = try
        {
            wallyApp!!.newAccount(actName, 0U, "", cs)!!
        }
        catch (e: Exception)
        {
            println(sourceLoc() + ": ERROR creating account: $e")
            throw e
        }
        if (cs == ChainSelector.NEXAREGTEST)
        {
            account1.chain.net.exclusiveNodes(setOf(REGTEST_IP))
        }
        val rpc = getNexaRpc()

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            setSelectedAccount(account1)
            assignAccountsGuiSlots()
            val assetViewModel = AssetViewModel()
            val accountUiDataViewModel = AccountUiDataViewModel()
            val wInsets = WindowInsets(0,0,0,0)
            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(Modifier, wInsets,
                        assetViewModel = assetViewModel,
                        accountUiDataViewModel = accountUiDataViewModel
                    )
                }
            }
            settle()
            nav.switch(ScreenId.Home)  // Skip the splash screen

            // Wait until home page comes up, verify that the new account is selected
            wallyVerifyAccountSelected(actName)

            // Go to account details
            onNode(hasTestTag("AccountDetailsButton"), useUnmergedTree = true).performClick()
            settle()

            // New accounts should have address privacy on by default
            waitForCatching {  onNode(hasTestTag("addressPrivacy")).isDisplayed() }
            onNode(hasTestTag("addressPrivacy")).assertIsOn()

            // Turn off address privacy
            onNode(hasTestTag("addressPrivacy")).performClick()
            waitForCatching { onNode(hasTestTag("addressPrivacy")).assertIsOff() }
            settle()
            waitForCatching {  onNode(hasTestTag("addressPrivacy")).isDisplayed() }

            // go to receive screen, get addr, send coins, verify that addr does not change
            nav.back()
            nav.go(ScreenId.Receive)
            settle()
            waitForCatching {
                onNode(hasTestTag("AccountPillAccountName") and hasText(actName), useUnmergedTree = true).assertTextEquals(actName)
            }
            val addr = onNode(hasTestTag("receiveScreen:receiveAddress")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.fastJoinToString()
            LogIt.info("Address 1: $addr")
            check(addr != null)
            if (addr != null)
            {
                rpc.sendtoaddress(addr, BigDecimal.fromInt(1000))
            }

            waitForCatching {
                onNode(hasTestTag("AccountPillBalance")).assertTextContains("1,000.00")
            }
            val addr2 = onNode(hasTestTag("receiveScreen:receiveAddress")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.fastJoinToString()
            LogIt.info("Address 2: $addr2")
            check(addr == addr2)

            nav.go(ScreenId.AccountDetails)
            waitForCatching {  onNode(hasTestTag("addressPrivacy")).isDisplayed() }
            onNode(hasTestTag("addressPrivacy")).assertIsOff()
            onNode(hasTestTag("addressPrivacy")).performClick()
            onNode(hasTestTag("addressPrivacy")).assertIsOn()

            nav.back()

            val addr3 = onNode(hasTestTag("receiveScreen:receiveAddress")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.fastJoinToString()
            LogIt.info("Address 3: $addr3")
            check(addr3 == addr2)
            if (addr3 != null) rpc.sendtoaddress(addr3, BigDecimal.fromInt(1000))
            waitForCatching {
                onNode(hasTestTag("AccountPillBalance")).assertTextContains("2,000.00")
            }
            val addr4 = onNode(hasTestTag("receiveScreen:receiveAddress")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.fastJoinToString()
            LogIt.info("Address 4: $addr3")
            check(addr3 != addr4)
            true
        }
        wallyApp!!.deleteAccount(account1)
         */
    }

}