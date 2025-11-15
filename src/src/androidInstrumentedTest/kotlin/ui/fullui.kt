
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.FiatFormat
import org.nexa.threads.millisleep
import kotlin.test.Test
import info.bitcoinunlimited.www.wally.ui.theme.WallyTheme
import org.nexa.libnexakotlin.NexaMathMode
import org.nexa.libnexakotlin.fromString
import org.nexa.threads.platformName
import info.bitcoinunlimited.www.wally.ui.*
import org.junit.Rule
import ui.WallyUiTestBase
import ui.settle

@OptIn(ExperimentalTestApi::class)
class AAccountPillTest: WallyUiTestBase()
{
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeActivity>()  // ← key

    @Test
    fun systemTest()
    {
        val cs = ChainSelector.NEXATESTNET
        wallyApp!!.openAllAccounts()
        val account = wallyApp!!.newAccount("fulltest", 0U, "", cs)!!

        val processName = Application.getProcessName()
        println("My android process is $processName")

        setSelectedAccount(account)
        val accountName = account.name
        val currencyCode = account.currencyCode

        composeRule.waitUntilAtLeastOneExists(hasText(accountName), 10_000)
        composeRule.onNodeWithText(accountName).assertIsDisplayed()
        composeRule.onNodeWithText(currencyCode).assertIsDisplayed()

        wallyApp!!.deleteAccount(account)
    }
}