package ui.views

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.TdppAction
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.views.WallyCardHeadlineContent
import info.bitcoinunlimited.www.wally.ui.views.WallyOptionsCard
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class UiComponentLibraryTest
{
    @Test
    fun wallyCardTest() = runComposeUiTest {
        val headline = i18n(S.Domain)
        val content = "bitcoinunlimied.org"
        setContent {
            WallyCardHeadlineContent(headline, content)
        }

        onNodeWithText(headline).isDisplayed()
        onNodeWithText(content).isDisplayed()
    }

    @Test
    fun wallyOptionsCardTest() = runComposeUiTest {
        val headline = "Headline for the options"
        val options = listOf(TdppAction.ACCEPT, TdppAction.ASK, TdppAction.DENY)
        var selectedOption = TdppAction.ASK

        setContent {
            WallyOptionsCard(
              headline = headline,
              options = options,
              selectedOption = selectedOption,
              onOptionChanged = {
                  selectedOption = it
              },
              optionToText = {
                  when(it)
                  {
                      TdppAction.ACCEPT -> i18n(S.accept)
                      TdppAction.ASK -> i18n(S.ask)
                      TdppAction.DENY -> i18n(S.deny)
                  }
              }
            )
        }

        onNodeWithText(headline).isDisplayed()
        onNodeWithText(i18n(S.accept)).isDisplayed()
        onNodeWithText(i18n(S.ask)).isDisplayed()
        onNodeWithText(i18n(S.deny)).isDisplayed()

        onNodeWithText(i18n(S.accept)).performClick()
        assertTrue { selectedOption == TdppAction.ACCEPT }
    }
}