package ui2
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.views.*
import org.nexa.libnexakotlin.ChainSelector
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.AnnotatedString
import org.nexa.libnexakotlin.GetLog

import kotlin.test.Test

private val LogIt = GetLog("BU.wally.test")

@OptIn(ExperimentalTestApi::class)
class AccountLockTest:WallyUiTestBase()
{
    init
    {
        setupTestEnv()
    }
    @Test
    fun testLockAccount()
    {
        // Create a normal account
        val actName = "nexaAccount"
        val account = wallyApp!!.newAccount(actName, 0U, "", ChainSelector.NEXA)!!

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            // Initialize ViewModels
            val assetViewModel = AssetViewModel()
            val balanceViewModel: BalanceViewModel = BalanceViewModelImpl(account)
            val accountUiDataViewModel = AccountUiDataViewModel()
            /*
                Set content to NavigationRootUi2 (the root composable that handles navigation)
             */
            setContent {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {
                    NavigationRootUi2(Modifier, Modifier,
                        assetViewModel = assetViewModel,
                        accountUiDataViewModel = accountUiDataViewModel
                    )
                }
            }
            // Select the account
            setSelectedAccount(account)
            assignAccountsGuiSlots()

            balanceViewModel.observeBalance(account)
            balanceViewModel.setFiatBalance(account)

            // Navigate to the Home Screen
            nav.switch(ScreenId.Home)
            settle()
            // Verify that the Home Screen is displayed
            waitForCatching { onNodeWithTag("AccountPillAccountName").isDisplayed() }
            onNodeWithTag("AccountPillAccountName").assertTextEquals(account.name)

            waitForCatching { onNodeWithTag("AccountDetailsButton").isDisplayed() }
            onNodeWithTag("AccountDetailsButton").performClick()
            settle()
            // Click the "Set/Change PIN" button using its test tag
            waitForCatching { onNodeWithTag("SetChangePinButton").isDisplayed() }
            onNodeWithTag("SetChangePinButton").performClick()
            settle()
            // Turn on the hide account toggle
            waitForCatching { onNodeWithTag("PinHidesAccountToggle").isDisplayed() }
            onNodeWithTag("PinHidesAccountToggle").performClick()
            settle()

            // Set PIN to 1111
            waitForCatching { onNodeWithTag("PinInputField").isDisplayed() }
            onNodeWithTag("PinInputField").performTextInput("1111")
            onNodeWithTag("PinInputField").multiplatformImeAction()
            settle()
            waitForCatching { onNodeWithTag("AcceptPinButton").isDisplayed() }
            onNodeWithTag("AcceptPinButton").performClick()
            settle()
            onNodeWithTag("HomeButton").performClick()
            settle()
            waitForCatching { onNodeWithTag("AccountPillAccountName").isDisplayed() }
            onNodeWithTag("AccountPillAccountName").assertTextEquals(account.name)

            waitForCatching(60000, {"Unlock icon not displayed"}) { onNodeWithTag("UnlockIcon($actName)").isDisplayed() }
            // Hide the account by clicking the lock icon near the account
            onNodeWithTag("UnlockIcon($actName)").performClick()
            settle()

            // Verify that the account purple pill is not showing this hidden account
            if (onNodeWithTag("AccountPillAccountName").isDisplayed())
            {
                val tmp: List<AnnotatedString>? = onNodeWithTag("AccountPillAccountName").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                if (tmp!= null)
                {
                    check(tmp.first().text != account.name)
                }
            }

            settle()
            triggerUnlockDialog(true, { println("Unlock attempted")})
            settle()

            waitForCatching { onAllNodesWithTag("EnterPIN").onFirst().isDisplayed() }
            LogIt.info("PIN NODES:" + onAllNodesWithTag("EnterPIN").printToString())
            onNodeWithTag("EnterPIN").performTextInput("1111")
            settle()
            onNodeWithTag("EnterPIN").multiplatformImeAction()
            settle()
            waitForCatching { onNodeWithTag("AccountPillAccountName").isDisplayed() }
            settle()
            }
        wallyApp!!.deleteAccount(account)
        }

    }
