package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.IdentityScreen
import info.bitcoinunlimited.www.wally.ui2.ScreenNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.rem
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class IdentityScreenTest
{
    init {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @Test
    fun identityScreenTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("idtst", 0U, "", cs)!!
        }
        setContent {
            IdentityScreen(account, null, ScreenNav())
        }

        /**
         * Check that titles are displayed, click edit button and verify that some titles in edit view are displayed
         */
        onNodeWithText(i18n(S.commonIdentityForAccount) % mapOf("act" to account.name)).isDisplayed()
        onNodeWithText(i18n(S.IdentityRegistrations)).isDisplayed()
        onNodeWithTag("EditIdentityButton")
        onNodeWithText(i18n(S.IdentityAssociatedWith))
        onNodeWithText(i18n(S.provideEmail))
        onNodeWithText(i18n(S.NameText))
    }
}