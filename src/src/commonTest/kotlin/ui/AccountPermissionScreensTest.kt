package ui

import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.deleteWalletFile
import kotlin.test.Test

private val LogIt = GetLog("BU.wally.perm")

@OptIn(ExperimentalTestApi::class, ExperimentalUnsignedTypes::class)
class AccountPermissionScreensTest:WallyUiTestBase()
{
    val cs = ChainSelector.NEXA

    @Test
    fun sendToPermScreenTest()
    {
        val account = wallyApp!!.newAccount("sendto", 0U, "", cs)!!
        runComposeUiTest {
            val tp = TricklePaySession(wallyApp!!.tpDomains)
            setContent {
                SendToPermScreen( tp, ScreenNav())
            }
        }
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun assetInfoPermScreenTest()
    {
        val account = wallyApp!!.newAccount("assetInfo", 0U, "", cs)!!
        waitFor(5000, { "Cannot connect to ${cs.name} network!  Run a local full node."}) {
            account.chain.net.p2pCnxns.size > 0
        }
        runComposeUiTest {
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
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun identityPermScreenUriNullTest()
    {
        val account = Account("identityPerm", chainSelector = cs)
        runComposeUiTest {
            setSelectedAccount(account)
            val nav = ScreenNav()
            val sess = IdentitySession(null)
            setContent {
                IdentityPermScreen(sess, nav)
            }
            LogIt.info("Identity perm screen up")
            settle()
            LogIt.info("Identity perm test done")
        }
        account.delete()
    }
}
