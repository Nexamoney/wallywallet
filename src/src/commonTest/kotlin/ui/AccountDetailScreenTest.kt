package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AccountDetailScreen
import info.bitcoinunlimited.www.wally.ui2.ScreenNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AccountDetailScreenTest
{
    @BeforeTest
    fun setup()
    {
        if (platform().target == KotlinTarget.JVM)
            Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun clean()
    {
        if (platform().target == KotlinTarget.JVM)
            Dispatchers.resetMain()
    }

    init {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @Test
    fun accountDetailScreenTest() = runComposeUiTest {

        val cs = ChainSelector.NEXA
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("itemvie", 0U, "", cs)!!
        }
        setContent {
            AccountDetailScreen(account, ScreenNav())
        }

        /**
         * Open change pin View and click cancel button to close it.
         */
        onNodeWithText(i18n(S.AccountStatistics)).isDisplayed()
        onNodeWithText(i18n(S.AccountActions)).isDisplayed()
        onNodeWithText(i18n(S.SetChangePin)).performClick()
        onNodeWithText(i18n(S.PinHidesAccount)).isDisplayed()
        onNodeWithText(i18n(S.cancel)).performClick()
    }
}