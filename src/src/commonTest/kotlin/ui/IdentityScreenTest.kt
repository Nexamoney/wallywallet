package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.IdentityEditScreen
import info.bitcoinunlimited.www.wally.ui.IdentityScreen
import info.bitcoinunlimited.www.wally.ui.ScreenNav
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.rem
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class IdentityScreenTest:  WallyUiTestBase(false)
{

    @Test
    fun identityScreenTest()
    {
        val cs = ChainSelector.NEXA
        val account: Account = wallyApp!!.newAccount("idtst", 0U, "", cs)!!
        val ap = AccountPill(account)
        runComposeUiTest {
            setContent {
                IdentityScreen(account, ap, null, ScreenNav())
            }
            settle()

            /**
             * Check that titles are displayed, click edit button and verify that some titles in edit view are displayed
             */
            waitForCatching {  onNodeWithText(i18n(S.commonIdentityForAccount) % mapOf("act" to account.name)).isDisplayed() }
            onNodeWithText(i18n(S.IdentityRegistrations)).assertIsDisplayed()
        }
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun identityEditScreenTest()
    {
        val cs = ChainSelector.NEXA
        val account: Account = wallyApp!!.newAccount("idtst", 0U, "", cs)!!
        runComposeUiTest {
            setContent {
                IdentityEditScreen(account, ScreenNav())
            }
            settle()

            // TODO
            //onNodeWithText(i18n(S.IdentityAssociatedWith)).assertIsDisplayed()
            //onNodeWithText(i18n(S.provideEmail)).assertIsDisplayed()
            //onNodeWithText(i18n(S.NameText)).assertIsDisplayed()
        }
        wallyApp!!.deleteAccount(account)
    }
}