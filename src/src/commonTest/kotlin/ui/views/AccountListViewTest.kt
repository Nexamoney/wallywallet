package ui.views

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.views.AccountItemView
import info.bitcoinunlimited.www.wally.ui2.views.AccountUIData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import ui2.settle
import ui2.setupApp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class AccountListViewTest
{
    init
    {
        forTestingDoNotAutoCreateWallets = true
        setupApp()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accountItemViewTest()
    {
        val cs = ChainSelector.NEXA
        val account: Account = wallyApp!!.newAccount("itemvie", 0U, "", cs)!!
        runComposeUiTest {
            val iSelectedMock = mutableStateOf(false)
            val gearIconCLicked = mutableStateOf(false)

            setContent {
                AccountItemView(
                  uidata = AccountUIData(
                    account = account
                  ),
                  index = 0,
                  isSelected = iSelectedMock.value,
                  devMode = false,
                  backgroundColor = Color.Transparent,
                  onClickAccount = {
                      iSelectedMock.value = !iSelectedMock.value
                  },
                  onClickGearIcon = {
                      gearIconCLicked.value = true
                  }
                )
            }
            settle()
            onNodeWithTag("AccountItemView").isDisplayed()
            onNodeWithTag("AccountItemView").performClick()
            settle()
            assertTrue(iSelectedMock.value)
            onNodeWithTag("accountSettingsGearIcon").isDisplayed()
            onNodeWithTag("accountSettingsGearIcon").performClick()
            settle()
            assertTrue(gearIconCLicked.value)
        }
        wallyApp!!.deleteAccount(account)
    }
}