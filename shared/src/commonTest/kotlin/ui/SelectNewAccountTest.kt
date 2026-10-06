package ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.NewAccountScreen
import info.bitcoinunlimited.www.wally.ui.selectIfNoAccountSelected
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** An account is created on a background job, so give it longer than waitUntil's 1 second default. */
private const val ACCOUNT_CREATION_TIMEOUT_MS = 60_000L

/** Fits within MAX_NAME_LEN, and is unlike any account name the other tests use. */
private const val NEW_ACCOUNT_NAME = "sel481"

/** Creating an account while no account is selected must select it (#481). */
@OptIn(ExperimentalTestApi::class)
class SelectNewAccountTest: WallyUiTestBase(false)
{
    /** Run [body] with [selection] selected, then put back whatever the app had selected before. */
    private fun withSelectedAccount(selection: Account?, body: () -> Unit)
    {
        val preexisting = wallyApp!!.focusedAccount.value
        wallyApp!!.focusedAccount.value = selection
        try
        {
            body()
        }
        finally
        {
            wallyApp!!.focusedAccount.value = preexisting
        }
    }

    @Test
    fun accountCreatedOnTheNewAccountScreenIsSelected() = withSelectedAccount(null) {
        val accountGuiSlots = MutableStateFlow(wallyApp!!.orderedAccounts())
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NewAccountScreen(accountGuiSlots.collectAsState(), false)
                }
            }

            onNodeWithTag("AccountNameInput").assertExists()
            onNodeWithTag("AccountNameInput").performTextReplacement(NEW_ACCOUNT_NAME)
            settle()
            onNodeWithText(i18n(S.createNewAccount)).assertExists()
            onNodeWithText(i18n(S.createNewAccount)).performClick()
            settle()
            // Creation is asynchronous: the screen has navigated back before the account exists.
            waitUntil(timeoutMillis = ACCOUNT_CREATION_TIMEOUT_MS) { wallyApp!!.accounts.containsKey(NEW_ACCOUNT_NAME) }
            // Selection runs a step behind creation, on the same background job.
            waitUntil(timeoutMillis = ACCOUNT_CREATION_TIMEOUT_MS) { wallyApp!!.focusedAccount.value != null }

            assertEquals(NEW_ACCOUNT_NAME, wallyApp!!.focusedAccount.value?.name)
        }
    }

    @Test
    fun createdAccountIsSelectedWhenNothingIsSelected() = withSelectedAccount(null) {
        val created = mockAccount()
        selectIfNoAccountSelected(created)
        assertSame(created, wallyApp!!.focusedAccount.value)
    }

    /** Creating a second account must not move the user off the account they are looking at. */
    @Test
    fun createdAccountLeavesAnExistingSelectionAlone()
    {
        val alreadySelected = mockAccount()
        withSelectedAccount(alreadySelected) {
            selectIfNoAccountSelected(mockAccount())
            assertSame(alreadySelected, wallyApp!!.focusedAccount.value)
        }
    }
}
