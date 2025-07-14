package ui
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
//import androidx.compose.ui.semantics.SemanticsProperties
//import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.AccountUiDataViewModel
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModel
import info.bitcoinunlimited.www.wally.ui.views.BalanceViewModelImpl
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.resolveDomain
//import androidx.compose.ui.test.performImeAction
//import androidx.compose.ui.text.AnnotatedString
//import org.nexa.libnexakotlin.GetLog

import kotlin.test.Test

//private val LogIt = GetLog("BU.wally.test")
const val DEV_MODE_PREF = "devinfo"

@OptIn(ExperimentalTestApi::class)
class RecoverTests:WallyUiTestBase()
{
    // DOES not work in CI (this test requires a good external net connection) @Test
    fun testRecoveryOfTestnet()
    {
        if (resolveDomain("www.nexa.org").size == 0)
        {
            println("This test sandbox cannot resolve external domains, so this test cannot be run")
            return
        }
        val preferenceDB = getSharedPreferences(i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
        preferenceDB.edit().putBoolean(DEV_MODE_PREF, true).commit()
        devMode = true  // MUST be on because we are creating a testnet account
        // Create a normal account
        val actName = "nexaAccount"
        val account = wallyApp!!.accounts[actName] ?: wallyApp!!.newAccount(actName, 0U, "", ChainSelector.NEXA)!!

        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
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
                    NavigationRoot(Modifier, WindowInsets(0,0,0,0),
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
            settle()

            // Select "Recover an account" option
            // We scroll to the bottom space to make sure that the add account button isnt hidden behind the thumb buttons when we try to click it.
            onNodeWithTag("AccountListBottomSpace").performScrollTo()
            waitForCatching { onNodeWithTag("AddAccount").isDisplayed() }
            onNodeWithTag("AddAccount").performClick()
            settle()

            val dropdown = onNodeWithText("NEXA")
            dropdown.performClick()
            settle()

            // Try to find the testnet option
            try
            {
                val testnetOption = onNodeWithText("TNEX (Testnet Nexa)")

                // Verify that the testnet option exists
                testnetOption.assertExists("Testnet option should be available in dev mode")

                // Click the testnet option
                testnetOption.performClick()

                // Verify that the testnet is now selected
                onNodeWithText("TNEX (Testnet Nexa)").assertExists()
            }
            catch (e: AssertionError)
            {
                    // If testnet option is not found, it might mean dev mode is not active
                    // or the dropdown implementation differs from expectations
                    throw AssertionError("Testnet option not found. Dev mode might not be active.", e)
            }

            onNodeWithTag("RecoveryPhraseInput")
              .performTextInput("enrich swamp domain cushion produce music visa raven demand seminar pledge erosion")
            settle()

            waitForCatching(5*60000) { onNodeWithTag("CreateDiscoveredAccount").isDisplayed() }
            onNodeWithTag("CreateDiscoveredAccount").performClick()
            settle()

            // Verify we're redirected to the Home screen after recovery
            waitForCatching{
                onNodeWithTag("AccountPillAccountName").isDisplayed()
            }
            settle()
            waitForCatching {
                onNode(hasTestTag("CarouselAccountName") and hasText("tNexa"), useUnmergedTree = true).assertTextEquals("tNexa")
            }
            settle()
        }
    }
}