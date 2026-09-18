package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.text.input.TextFieldValue
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.NewAccountScreenContent
import info.bitcoinunlimited.www.wally.ui.NewAccountState
import info.bitcoinunlimited.www.wally.ui.newAccountState
import info.bitcoinunlimited.www.wally.ui.supportedBlockchains
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.TransactionHistory
import org.nexa.libnexakotlin.txFor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NewAccountScreenContent draws from two sources: its parameters, and the newAccountState global it
 * collects directly.  These tests drive both, so they reach the display branches that the tests
 * against the stateful NewAccountScreen cannot reach.
 */
@OptIn(ExperimentalTestApi::class)
class NewAccountScreenContentTest: WallyUiTestBase(false)
{
    private val nexa = supportedBlockchains.entries.first().toPair()
    private val entered = newAccountState.value

    @AfterTest
    fun restoreNewAccountState()
    {
        newAccountState.value = entered
    }

    @Composable
    private fun Content(
      recoverySearchText: String = "",
      fastForwardText: String? = null,
      selectedChain: Pair<String, ChainSelector> = nexa,
      blockchains: Map<String, ChainSelector> = supportedBlockchains,
      onChainSelected: (Pair<String, ChainSelector>) -> Unit = {},
      onNewAccountName: (String) -> Unit = {},
      onNewRecoveryPhrase: (TextFieldValue) -> Unit = {},
      onPinChange: (String) -> String = { it },
      onHideUntilPinEnterChanged: (Boolean) -> Unit = {},
      onClickCreateAccount: () -> Unit = {},
      onClickCreateDiscoveredAccount: () -> Unit = {},
      creatingAccountLoading: Boolean = false
    ) = NewAccountScreenContent(recoverySearchText, fastForwardText, selectedChain, blockchains, onChainSelected,
      onNewAccountName, onNewRecoveryPhrase, onPinChange, onHideUntilPinEnterChanged, onClickCreateAccount,
      onClickCreateDiscoveredAccount, creatingAccountLoading)

    /** A non-null fastForwardText is shown verbatim */
    @Test
    fun fastForwardTextIsDisplayed() = runComposeUiTest {
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(fastForwardText = "peeked at 3 addresses") }
        settle()
        onNodeWithText("peeked at 3 addresses").assertIsDisplayed()
    }

    /** A null fastForwardText falls back to the search-in-progress row, but only while a search is live */
    @Test
    fun nullFastForwardTextFallsBackToSearchProgress() = runComposeUiTest {
        newAccountState.value = NewAccountState(accountName = "nexa", earliestActivityHeight = 0)
        setContent { Content(fastForwardText = null) }
        settle()
        onNodeWithText(i18n(S.NewAccountSearchingForAllTransactions)).assertIsDisplayed()

        // -1 means nothing is being searched for, so the progress row goes away
        newAccountState.value = newAccountState.value.copy(earliestActivityHeight = -1)
        settle()
        onNodeWithText(i18n(S.NewAccountSearchingForAllTransactions)).assertDoesNotExist()
    }

    /** recoverySearchText is shown and tracks updates */
    @Test
    fun recoverySearchTextIsDisplayed() = runComposeUiTest {
        var text by mutableStateOf("looking for transactions")
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(recoverySearchText = text) }
        settle()
        onNodeWithText("looking for transactions").assertIsDisplayed()

        text = "no activity found"
        settle()
        onNodeWithText("no activity found").assertIsDisplayed()
        onNodeWithText("looking for transactions").assertDoesNotExist()
    }

    /** The chain shown in the selector follows the selectedChain parameter */
    @Test
    fun selectedChainFollowsParameter() = runComposeUiTest {
        val testnet = supportedBlockchains.entries.elementAt(1).toPair()
        var chain by mutableStateOf(nexa)
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(selectedChain = chain) }
        settle()
        onNodeWithTag("DropdownMenuItemSelected").assertTextContains(nexa.first)

        chain = testnet
        settle()
        onNodeWithTag("DropdownMenuItemSelected").assertTextContains(testnet.first)
    }

    /** Only the chains passed in blockchains are offered */
    @Test
    fun blockchainsParameterControlsTheOptions() = runComposeUiTest {
        val testnet = supportedBlockchains.entries.elementAt(1).toPair()
        var chains by mutableStateOf(supportedBlockchains)
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(blockchains = chains) }
        settle()
        onNodeWithTag("DropdownMenuItemSelected").performClick()
        settle()
        onNodeWithText(testnet.first).assertIsDisplayed()

        // Mainnet-only, the way the screen restricts itself when devMode is off
        chains = supportedBlockchains.filter { it.value.isMainNet }
        settle()
        onNodeWithText(testnet.first).assertDoesNotExist()
    }

    /** Picking a different chain reports it */
    @Test
    fun chainSelectionInvokesCallback() = runComposeUiTest {
        val testnet = supportedBlockchains.entries.elementAt(1).toPair()
        var selected: Pair<String, ChainSelector>? = null
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(onChainSelected = { selected = it }) }
        settle()
        onNodeWithTag("DropdownMenuItemSelected").performClick()
        settle()
        onNodeWithText(testnet.first).performClick()
        settle()
        assertEquals(testnet, selected)
    }

    /** Typing an account name reports it */
    @Test
    fun accountNameInputInvokesCallback() = runComposeUiTest {
        var name: String? = null
        newAccountState.value = NewAccountState(accountName = "")
        setContent { Content(onNewAccountName = { name = it }) }
        settle()
        onNodeWithTag("AccountNameInput").performTextInput("wallet")
        settle()
        assertEquals("wallet", name)
    }

    /** Typing a recovery phrase reports it */
    @Test
    fun recoveryPhraseInputInvokesCallback() = runComposeUiTest {
        var phrase: TextFieldValue? = null
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(onNewRecoveryPhrase = { phrase = it }) }
        settle()
        onNodeWithTag("RecoveryPhraseInput").performTextInput("legal")
        settle()
        assertEquals("legal", phrase?.text)
    }

    /** Typing a PIN reports it, and non-digits never reach the callback */
    @Test
    fun pinInputInvokesCallback() = runComposeUiTest {
        var pin: String? = null
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(onPinChange = { pin = it; it }) }
        settle()
        onNodeWithTag("NewAccountPinInput").performTextInput("1234")
        settle()
        assertEquals("1234", pin)

        pin = null
        onNodeWithTag("NewAccountPinInput").performTextInput("abc")
        settle()
        assertNull(pin)
    }

    /** The hidden-account checkbox only reports while a usable PIN is set */
    @Test
    fun hiddenAccountCheckboxRequiresAPin() = runComposeUiTest {
        var hidden: Boolean? = null
        newAccountState.value = NewAccountState(accountName = "nexa", pin = "")
        setContent { Content(onHideUntilPinEnterChanged = { hidden = it }) }
        settle()
        onNodeWithTag("PinHidesAccount").performClick()
        settle()
        assertNull(hidden)

        newAccountState.value = newAccountState.value.copy(pin = "1234")
        settle()
        onNodeWithTag("PinHidesAccount").performClick()
        settle()
        assertEquals(true, hidden)
    }

    /** Both create buttons route to onClickCreateAccount */
    @Test
    fun createAccountButtonInvokesCallback() = runComposeUiTest {
        var clicks = 0
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(onClickCreateAccount = { clicks++ }) }
        settle()
        onNodeWithTag("onClickCreateAccount").performClick()
        settle()
        assertEquals(1, clicks)

        // Once activity has been found the plain create button is swapped for the careful-sync one
        newAccountState.value = newAccountState.value.copy(earliestActivity = 1700000000L)
        settle()
        onNodeWithTag("onClickCreateAccount").assertDoesNotExist()
        onNodeWithText(i18n(S.createSyncAccount)).performClick()
        settle()
        assertEquals(2, clicks)
    }

    /** A discovered account swaps in its own summary and button */
    @Test
    fun discoveredAccountInvokesCallback() = runComposeUiTest {
        var clicked = false
        newAccountState.value = NewAccountState(accountName = "nexa").copy(
          discoveredAccountHistory = listOf(TransactionHistory(ChainSelector.NEXA, txFor(ChainSelector.NEXA))),
          discoveredAddressCount = 2,
          discoveredAccountBalance = 100000
        )
        setContent { Content(onClickCreateDiscoveredAccount = { clicked = true }) }
        settle()
        onNodeWithTag("CreateDiscoveredAccount").assertExists()
        onNodeWithText(i18n(S.discoveredWarning)).assertIsDisplayed()
        onNodeWithText(i18n(S.createDiscoveredAccount)).performClick()
        settle()
        assertTrue(clicked)
    }

    /** While the account is being created the whole form tail is replaced by a progress message */
    @Test
    fun loadingReplacesTheFormActions() = runComposeUiTest {
        var loading by mutableStateOf(false)
        newAccountState.value = NewAccountState(accountName = "nexa")
        setContent { Content(creatingAccountLoading = loading) }
        settle()
        onNodeWithTag("onClickCreateAccount").assertExists()
        onNodeWithText(i18n(S.Processing)).assertDoesNotExist()

        loading = true
        settle()
        onNodeWithText(i18n(S.Processing)).assertIsDisplayed()
        onNodeWithTag("onClickCreateAccount").assertDoesNotExist()

        loading = false
        settle()
        onNodeWithTag("onClickCreateAccount").assertExists()
    }

    /** An error message from the state is surfaced above the form */
    @Test
    fun errorMessageIsDisplayed() = runComposeUiTest {
        newAccountState.value = NewAccountState(accountName = "nexa", errorMessage = i18n(S.invalidAccountName))
        setContent { Content() }
        settle()
        onNodeWithText(i18n(S.invalidAccountName)).assertIsDisplayed()
        assertFalse(newAccountState.value.errorMessage.isEmpty())
    }
}
