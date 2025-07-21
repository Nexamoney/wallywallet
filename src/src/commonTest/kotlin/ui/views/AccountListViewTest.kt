package ui.views

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.views.AccountItemView
import info.bitcoinunlimited.www.wally.ui.views.AccountUIData
import org.nexa.libnexakotlin.ChainSelector
import ui.settle
import ui.setupTestEnv
import ui.waitForCatching
import kotlin.test.Test
import kotlin.test.assertTrue

class AccountListViewTest
{
    init
    {
        setupTestEnv()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accountItemViewTest()
    {
        val cs = ChainSelector.NEXA
        val account = wallyApp!!.newAccount("itemvie", 0U, "", cs)!!
        runComposeUiTest {
            val iSelectedMock = mutableStateOf(false)

            val accUiData = AccountUIData(
              account = account
            )

            setContent {
                AccountItemView(
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
            settle()
            waitForCatching { onNodeWithTag("AccountItemView").isDisplayed() }
            onNodeWithTag("AccountItemView").performClick()
            settle()
            assertTrue(iSelectedMock.value)
        }
        wallyApp!!.deleteAccount(account)
    }
}