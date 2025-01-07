package ui.views

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.views.AccountItemViewUi2
import info.bitcoinunlimited.www.wally.ui2.views.AccountUIData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class AccountListViewTestUi2
{
    init {
        initializeLibNexa()
        runningTheTests = true
        forTestingDoNotAutoCreateWallets = true
        dbPrefix = "test_"
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accountItemViewTest() = runComposeUiTest {
        val cs = ChainSelector.NEXA
        val iSelectedMock = mutableStateOf(false)

        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("itemvie", 0U, "", cs)!!
        }

        val accUiData = AccountUIData(
          account = account,
          balance = "10,000,000,000"
        )

        setContent {
            AccountItemViewUi2(
              uidata = accUiData,
              index = 0,
              isSelected = iSelectedMock.value,
              devMode = false,
              backgroundColor = Color.Transparent,
              hasFastForwardButton = false,
              onClickAccount = {
                  iSelectedMock.value = !iSelectedMock.value
              }
            )
        }

        onNodeWithTag("AccountItemView").isDisplayed()
        onNodeWithTag("AccountItemView").performClick()
        assertTrue(iSelectedMock.value)
    }
}