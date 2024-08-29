package ui.views

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.views.AccountItemView
import info.bitcoinunlimited.www.wally.ui.views.AccountUIData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.runningTheTests
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class AccountListViewTest
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
        val gearIconCLicked = mutableStateOf(false)
        wallyApp = CommonApp()
        wallyApp!!.onCreate()
        wallyApp!!.openAllAccounts()
        lateinit var account: Account
        runBlocking(Dispatchers.IO) {
            account = wallyApp!!.newAccount("itemvie", 0U, "", cs)!!
        }

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

        onNodeWithTag("AccountItemView").isDisplayed()
        onNodeWithTag("AccountItemView").performClick()
        assertTrue(iSelectedMock.value)
        onNodeWithTag("accountSettingsGearIcon").isDisplayed()
        onNodeWithTag("accountSettingsGearIcon").performClick()
        assertTrue(gearIconCLicked.value)
    }
}