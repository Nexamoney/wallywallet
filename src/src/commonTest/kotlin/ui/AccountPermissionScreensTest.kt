package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalUnsignedTypes::class)
class AccountPermissionScreensTest
{
    init {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @Test
    fun sendToPermScreenTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendto", 0U, "", cs)!!
        }
        val tp = TricklePaySession(wallyApp!!.tpDomains)

        setContent {
            SendToPermScreen(account, tp, ScreenNav())
        }
    }

    @Test
    fun assetInfoPermScreenTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendto", 0U, "", cs)!!
        }
        val tp = TricklePaySession(wallyApp!!.tpDomains)

        setContent {
            AssetInfoPermScreen(account, tp, ScreenNav())
        }

        /**
         * Assert text is displayed and click "deny"
         */
        onNodeWithText(i18n(S.TpAssetRequestFrom)).assertIsDisplayed()
        onNodeWithText(i18n(S.TpHandledByAccount)).assertIsDisplayed()
        onNodeWithText(i18n(S.TpAssetInfoNotXfer)).assertIsDisplayed()
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        onNodeWithText(i18n(S.deny)).assertIsDisplayed()
        onNodeWithText(i18n(S.deny)).performClick()
    }

    @Test
    fun identityPermScreenUriNullTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        val nav = ScreenNav()
        lateinit var account: Account
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("sendto", 0U, "", cs)!!
        }

        setContent {
            IdentityPermScreen(account, IdentitySession(null), nav)
        }
    }
}
