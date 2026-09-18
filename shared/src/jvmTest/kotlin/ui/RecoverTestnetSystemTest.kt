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
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.NavigationRoot
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.supportedBlockchains
import info.bitcoinunlimited.www.wally.ui.views.UnlockViewModel
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.chainToURI
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

private val LogIt = GetLog("BU.wally.test.recoverTestnet")

/** The 12 word testnet recovery phrase.  Without it this test does nothing -- do not commit a phrase into the repo. */
const val RECOVERY_PHRASE_ENV = "WALLY_TEST_MNEMONIC"

/** The phrase itself, or its base64 encoding -- Gitlab refuses to mask a CI variable holding whitespace */
private fun readRecoveryPhrase(): String?
{
    val raw = System.getenv(RECOVERY_PHRASE_ENV)?.trim()
    if (raw.isNullOrEmpty() || raw.contains(' ')) return raw
    return try
    {
        Base64.getDecoder().decode(raw).decodeToString().trim()
    }
    catch (e: IllegalArgumentException)
    {
        raw
    }
}

/** Nodes offered to the tNexa "prefer" setting, the first one that resolves wins */
val TESTNET_NODE_DOMAINS = listOf("nexa-testnet-seeder.bitcoinunlimited.info", "testnetseeder.nexa.org", "testnet-electrum.nexa.org")

const val ACTIVITY_SEARCH_TIMEOUT = 300000
const val ACCOUNT_CREATION_TIMEOUT = 120000
const val FAST_SYNC_TIMEOUT = 600000
val WHOLE_TEST_TIMEOUT = 30.minutes

private fun resolveTestnetNode(): String?
{
    for (domain in TESTNET_NODE_DOMAINS)
    {
        val ip = try
        {
            InetAddress.getAllByName(domain).filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
        }
        catch (e: Exception)
        {
            null
        }
        if (ip != null) return ip
    }
    return null
}

@OptIn(ExperimentalTestApi::class)
private fun SemanticsNodeInteraction.textOf(): String =
  fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.fastJoinToString("") ?: ""

/** Scrolls a node into view, tolerating the screens that are only scrollable when they hold enough content */
@OptIn(ExperimentalTestApi::class)
private fun SemanticsNodeInteraction.scrollToIfPossible(): SemanticsNodeInteraction
{
    try
    {
        performScrollTo()
    }
    catch (e: Throwable) {}
    return this
}

/** True if the displayed amount holds a nonzero digit, whatever its grouping and decimal separators are */
private fun String.isNonZeroAmount(): Boolean = any { it.isDigit() && it != '0' }

/**
 * Recovers a wallet on testnet from the 12 word recovery phrase in [RECOVERY_PHRASE_ENV] and fast syncs it, driving
 * settings, node preference, account creation and fast sync through the UI.  This talks to live testnet nodes, so it
 * is not part of the normal suite:
 *
 *   WALLY_TEST_MNEMONIC="..." ./gradlew :shared:jvmTest -PsystemTests --tests "ui.RecoverTestnetSystemTest"
 */
@OptIn(ExperimentalTestApi::class)
class RecoverTestnetSystemTest: WallyUiTestBase()
{
    val actName = "tnexRecovery"

    @Test
    fun recoverTestnetAccountAndFastSync()
    {
        val recoveryPhrase = readRecoveryPhrase()
        if (recoveryPhrase.isNullOrEmpty())
        {
            println("$RECOVERY_PHRASE_ENV is not set, so the testnet recovery system test cannot be run")
            return
        }
        val testnetNode = resolveTestnetNode()
        if (testnetNode == null)
        {
            println("This test sandbox cannot resolve any testnet node, so this test cannot be run")
            return
        }
        LogIt.info("Preferring testnet node $testnetNode")

        val testnetOption = supportedBlockchains.entries.first { it.value == ChainSelector.NEXATESTNET }.key
        val chainName = chainToURI[ChainSelector.NEXATESTNET]

        cleanupAccounts(actName, null)
        awaitDeletionsComplete()

        runComposeUiTest(testTimeout = WHOLE_TEST_TIMEOUT) {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }
            val unlock = UnlockViewModel(wallyApp!!.focusedAccount)

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRoot(Modifier, WindowInsets(0, 0, 0, 0), unlock = unlock)
                }
            }

            nav.switch(ScreenId.Home)
            settle()

            // Navigate to Settings and turn on developer mode so that testnet becomes selectable
            onNodeWithContentDescription("Settings").performClick()
            settle()
            waitForCatching { onNodeWithTag("SettingsScreenScrollable").isDisplayed() }
            if (!devMode)
            {
                onNodeWithTag("DevModeSwitch").scrollToIfPossible().performClick()
                settle()
            }
            assertTrue(devMode, "developer mode did not turn on")

            // Point tNexa at a testnet node and prefer it
            waitForCatching { onAllNodesWithTag("${chainName}NodeEntry").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("${chainName}NodeEntry").scrollToIfPossible().performTextReplacement(testnetNode)
            settle()
            onNodeWithTag("${chainName}PreferSwitch").scrollToIfPossible().performClick()
            settle()

            // Back to the Home screen and on to the recovery screen
            onNodeWithTag("backButton").performClick()
            settle()
            onNodeWithTag("AccountListBottomSpace", true).scrollToIfPossible()
            waitForCatching { onAllNodesWithTag("AddAccount").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("AddAccount").scrollToIfPossible().performClick()
            settle()

            // Recover on testnet, under our own account name
            onNodeWithTag("DropdownMenuItemSelected").performClick()
            settle()
            onNodeWithTag("DropdownMenuItem-$testnetOption").performClick()
            settle()
            onNodeWithTag("DropdownMenuItemSelected").assertTextEquals(testnetOption)
            onNodeWithTag("AccountNameInput").performTextReplacement(actName)
            settle()
            onNodeWithTag("RecoveryPhraseInput").performTextReplacement(recoveryPhrase)
            settle()

            // The careful sync button only appears once the blockchain peek has found this wallet's first activity
            waitForCatching(ACTIVITY_SEARCH_TIMEOUT, { "no activity was found for the supplied recovery phrase" })
            {
                onAllNodesWithTag("onClickCreateSyncAccount").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("onClickCreateSyncAccount").scrollToIfPossible().performClick()
            settle()

            // Account creation happens on its own thread, and lands us back on the Home screen
            val account = waitForCatching(ACCOUNT_CREATION_TIMEOUT) { wallyApp!!.accounts[actName] }
            waitForCatching { onAllNodes(hasTestTag("CarouselAccountName") and hasText(actName), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            onNode(hasTestTag("CarouselAccountName") and hasText(actName), useUnmergedTree = true).scrollToIfPossible().performClick()
            settle()

            waitForCatching { onNodeWithTag("AccountPillAccountName").textOf() == actName }
            onNodeWithTag("AccountPillAccountName").assertTextEquals(actName)

            // A recovered account is behind the chain tip: the pill says so, and so does its row in the account list
            waitForCatching(lazyErrorMsg = { "the account pill never reported the account as unsynced" })
            {
                onNodeWithTag("SyncStatusText").textOf() == i18n(S.unsynced)
            }
            waitForCatching(lazyErrorMsg = { "the account list never reported the account as unsynced" })
            {
                // As soon as the recovered wallet has a chainstate the row swaps "Syncing" for the date it has a balance for
                onAllNodesWithText(i18n(S.unsynced)).fetchSemanticsNodes().isNotEmpty()
                || onAllNodesWithText(i18n(S.balanceOnTheDate).substringBefore("%date"), substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            // Fast sync, which the careful (slow) sync recovery offers in the pill
            waitForCatching(lazyErrorMsg = { "the pill never offered fast sync for the recovered account" })
            {
                onAllNodesWithTag("FastSyncButton").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("FastSyncButton").performClick()
            settle()
            waitForCatching { onAllNodesWithText(i18n(S.fastforwardStatus).substringBefore("%info"), substring = true).fetchSemanticsNodes().isNotEmpty() }

            // The whole point of the exercise: the recovered account holds coins
            val pillBalance = waitForCatching(FAST_SYNC_TIMEOUT, { "the account pill balance stayed empty after the fast sync" })
            {
                onNodeWithTag("AccountPillBalance").textOf().takeIf { it.isNonZeroAmount() }
            }
            LogIt.info("Recovered balance $pillBalance")
            assertTrue(onNodeWithTag("AccountCarouselBalance_$actName", true).textOf().isNonZeroAmount(),
              "the account list balance stayed empty after the fast sync")
            assertEquals(ChainSelector.NEXATESTNET, account.chain.chainSelector)
        }

        cleanupAccounts(actName, null)
        awaitDeletionsComplete()
    }
}
