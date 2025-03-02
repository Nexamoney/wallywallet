package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.IdentityEditScreen
import info.bitcoinunlimited.www.wally.ui2.IdentityScreen
import info.bitcoinunlimited.www.wally.ui2.ScreenNav
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.rem
import ui2.settle
import ui2.setupTestEnv
import ui2.waitForCatching
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class IdentityScreenTest
{
    init
    {
       setupTestEnv()
    }

    @Test
    fun identityScreenTest()
    {
        val cs = ChainSelector.NEXA
        val account: Account = wallyApp!!.newAccount("idtst", 0U, "", cs)!!
        runComposeUiTest {
            setContent {
                IdentityScreen(account, null, ScreenNav())
            }
            settle()

            /**
             * Check that titles are displayed, click edit button and verify that some titles in edit view are displayed
             */
            waitForCatching {  onNodeWithText(i18n(S.commonIdentityForAccount) % mapOf("act" to account.name)).isDisplayed() }
            onNodeWithText(i18n(S.IdentityRegistrations)).assertIsDisplayed()
            account.delete()
        }
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
            account.delete()
        }
    }
}