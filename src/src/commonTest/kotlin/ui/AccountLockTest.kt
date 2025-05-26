package ui
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import org.nexa.libnexakotlin.ChainSelector
import androidx.compose.ui.text.AnnotatedString
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModel
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModelImpl
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.sourceLoc

import kotlin.test.Test

private val LogIt = GetLog("BU.wally.test")

@OptIn(ExperimentalTestApi::class)
class AccountLockTest:WallyUiTestBase()
{
    @Test
    fun testLockAccount()
    {
        // Clear out all test accounts
        val acts = wallyApp!!.accounts.values.toList()
        for(a in acts)
        {
            LogIt.info(sourceLoc() +": Deleting account ${a.name}")
            wallyApp!!.deleteAccount(a)
        }
        // Create a normal account
        val actName = "lockAct"
        val account = wallyApp!!.newAccount(actName, 0U, "", ChainSelector.NEXA)!!

        val wInsets = WindowInsets(0,0,0,0)

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

            // Initialize ViewModels
            val assetViewModel = AssetViewModel()
            val balanceViewModel: BalanceViewModel = BalanceViewModelImpl(account)
            val accountUiDataViewModel = AccountUiDataViewModel()
            /*
                Set content to NavigationRoot (the root composable that handles navigation)
             */
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
            if (try { onNodeWithTag("AccountPillAccountName").isDisplayed() } catch(e:AssertionError) {false})
            {
                val tmp: List<AnnotatedString>? = onNodeWithTag("AccountPillAccountName").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                if (tmp != null)
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
            onNodeWithTag("UnlockTileAccept").performClick()
            settle()
            waitForCatching { account.visible }
            LogIt.info("account is visible")
            wallyApp!!.focusedAccount.value = account
            waitForCatching { onNodeWithTag("AccountPillAccountName").isDisplayed() }
            LogIt.info("Purple tile name shown")
            settle()
            }
        wallyApp!!.deleteAccount(account)
        }

    }
