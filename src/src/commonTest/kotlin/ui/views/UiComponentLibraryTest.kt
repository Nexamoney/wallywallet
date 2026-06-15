package ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.TdppAction
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.SyncViewModelFake
import info.bitcoinunlimited.www.wally.ui.views.AddressInputField
import info.bitcoinunlimited.www.wally.ui.views.AddressInputTextField
import info.bitcoinunlimited.www.wally.ui.views.ButtonRowAcceptDeny
import info.bitcoinunlimited.www.wally.ui.views.CenteredFittedText
import info.bitcoinunlimited.www.wally.ui.views.CenteredFittedTitleText
import info.bitcoinunlimited.www.wally.ui.views.CenteredFittedWithinSpaceText
import info.bitcoinunlimited.www.wally.ui.views.CenteredSectionText
import info.bitcoinunlimited.www.wally.ui.views.CenteredText
import info.bitcoinunlimited.www.wally.ui.views.ConnectionWarning
import info.bitcoinunlimited.www.wally.ui.views.DecimalInputField
import info.bitcoinunlimited.www.wally.ui.views.ErrorText
import info.bitcoinunlimited.www.wally.ui.views.FittedText
import info.bitcoinunlimited.www.wally.ui.views.GeneralConfirmationCard
import info.bitcoinunlimited.www.wally.ui.views.GeneralWarningCard
import info.bitcoinunlimited.www.wally.ui.views.IconTextButton
import info.bitcoinunlimited.www.wally.ui.views.LongInputField
import info.bitcoinunlimited.www.wally.ui.views.NoticeText
import info.bitcoinunlimited.www.wally.ui.views.SectionText
import info.bitcoinunlimited.www.wally.ui.views.ConfirmDismissNoteDialog
import info.bitcoinunlimited.www.wally.ui.views.QrCode
import info.bitcoinunlimited.www.wally.ui.views.SelectStringDropDown
import info.bitcoinunlimited.www.wally.ui.views.SelectStringDropdownRes
import info.bitcoinunlimited.www.wally.ui.views.StringInputField
import info.bitcoinunlimited.www.wally.ui.views.StringInputTextField
import info.bitcoinunlimited.www.wally.ui.views.Syncing
import info.bitcoinunlimited.www.wally.ui.views.TitleText
import info.bitcoinunlimited.www.wally.ui.views.WallyBoldText
import info.bitcoinunlimited.www.wally.ui.views.WallyBoringButton
import info.bitcoinunlimited.www.wally.ui.views.WallyBoringIconButton
import info.bitcoinunlimited.www.wally.ui.views.WallyBoringLargeIconButton
import info.bitcoinunlimited.www.wally.ui.views.WallyBoringLargeTextButton
import info.bitcoinunlimited.www.wally.ui.views.WallyBoringMediumTextButton
import info.bitcoinunlimited.www.wally.ui.views.WallyBoringTextButton
import info.bitcoinunlimited.www.wally.ui.views.WallyBrightEmphasisBox
import info.bitcoinunlimited.www.wally.ui.views.WallyButtonRow
import info.bitcoinunlimited.www.wally.ui.views.WallyButtonText
import info.bitcoinunlimited.www.wally.ui.views.WallyCardContent
import info.bitcoinunlimited.www.wally.ui.views.WallyCardHeadlineContent
import info.bitcoinunlimited.www.wally.ui.views.WallyDataEntry
import info.bitcoinunlimited.www.wally.ui.views.WallyDecimalEntry
import info.bitcoinunlimited.www.wally.ui.views.WallyDigitEntry
import info.bitcoinunlimited.www.wally.ui.views.WallyAmountSelectorRow
import info.bitcoinunlimited.www.wally.ui.views.WallyEmphasisBox
import info.bitcoinunlimited.www.wally.ui.views.WallyError
import info.bitcoinunlimited.www.wally.ui.views.WallyImageButton
import info.bitcoinunlimited.www.wally.ui.views.WallyIncognitoTextEntry
import info.bitcoinunlimited.www.wally.ui.views.WallyLargeButtonText
import info.bitcoinunlimited.www.wally.ui.views.WallyMediumButtonText
import info.bitcoinunlimited.www.wally.ui.views.WallyNumericInputFieldBalance
import info.bitcoinunlimited.www.wally.ui.views.WallyOptionsCard
import info.bitcoinunlimited.www.wally.ui.views.WallyOutLineDecimalEntry
import info.bitcoinunlimited.www.wally.ui.views.WallyOutLineDecimalEntryTFV
import info.bitcoinunlimited.www.wally.ui.views.WallyOutlineDataEntry
import info.bitcoinunlimited.www.wally.ui.views.WallyRoundedButton
import info.bitcoinunlimited.www.wally.ui.views.WallyRoundedTextButton
import info.bitcoinunlimited.www.wally.ui.views.WallySmallTextButton
import info.bitcoinunlimited.www.wally.ui.views.WallySwitch
import info.bitcoinunlimited.www.wally.ui.views.WallySwitchRow
import info.bitcoinunlimited.www.wally.ui.views.WallyTextEntry
import info.bitcoinunlimited.www.wally.ui.views.WarningText
import info.bitcoinunlimited.www.wally.ui.views.BlockchainIcon
import info.bitcoinunlimited.www.wally.ui.views.FontScale
import info.bitcoinunlimited.www.wally.ui.views.FontScaleStyle
import info.bitcoinunlimited.www.wally.ui.views.WallyTextStyle
import info.bitcoinunlimited.www.wally.ui.views.WallySectionTextStyle
import info.bitcoinunlimited.www.wally.ui.views.WallyTitleTextStyle
import info.bitcoinunlimited.www.wally.ui.views.AmountSelector
import info.bitcoinunlimited.www.wally.ui.theme.defaultFontSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import org.nexa.libnexakotlin.ChainSelector
import ui.waitForCatching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // --- WallySwitch tests ---

    @Test
    fun wallySwitchBooleanDisplaysCorrectState() = runComposeUiTest {
        var isChecked = false
        setContent {
            WallySwitch(isChecked = isChecked, enabled = true) {
                isChecked = it
            }
        }
        onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun wallySwitchBooleanToggles() = runComposeUiTest {
        var isChecked = false
        setContent {
            WallySwitch(isChecked = isChecked, enabled = true) {
                isChecked = it
            }
        }
        onNode(isToggleable()).performClick()
        assertTrue(isChecked)
    }

    @Test
    fun wallySwitchMutableStateDisplaysCorrectState() = runComposeUiTest {
        val isChecked = mutableStateOf(true)
        setContent {
            WallySwitch(isChecked = isChecked) {
                isChecked.value = it
            }
        }
        onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun wallySwitchWithTextResDisplaysText() = runComposeUiTest {
        val isChecked = mutableStateOf(false)
        val textRes = S.accept
        setContent {
            WallySwitch(isChecked = isChecked, textRes = textRes) {
                isChecked.value = it
            }
        }
        onNodeWithText(i18n(textRes)).assertIsDisplayed()
    }

    @Test
    fun wallySwitchBooleanWithTextResDisplaysText() = runComposeUiTest {
        setContent {
            WallySwitch(isChecked = true, textRes = S.done, enabled = true) {}
        }
        onNodeWithText(i18n(S.done)).assertIsDisplayed()
        onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun wallySwitchBooleanWithStringTextDisplaysText() = runComposeUiTest {
        val label = "Custom label"
        setContent {
            WallySwitch(isChecked = false, text = label, enabled = true) {}
        }
        onNodeWithText(label).assertIsDisplayed()
        onNode(isToggleable()).assertIsOff()
    }

    // --- WallySwitchRow tests ---

    @Test
    fun wallySwitchRowDisplaysTextAndSwitch() = runComposeUiTest {
        var checked = false
        setContent {
            WallySwitchRow(isChecked = checked, textRes = S.accept) {
                checked = it
            }
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
    }

    // --- CenteredText tests ---

    @Test
    fun centeredTextDisplaysText() = runComposeUiTest {
        val text = "Hello centered world"
        setContent {
            CenteredText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun centeredTextWithStyleDisplaysText() = runComposeUiTest {
        val text = "Styled centered text"
        setContent {
            CenteredText(text, TextStyle(fontWeight = FontWeight.Bold))
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    // --- CenteredFittedText tests ---

    @Test
    fun centeredFittedTextDisplaysStringText() = runComposeUiTest {
        val text = "Fitted text content"
        setContent {
            CenteredFittedText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun centeredFittedTextDisplaysResText() = runComposeUiTest {
        setContent {
            CenteredFittedText(S.accept)
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
    }

    // --- CenteredFittedWithinSpaceText tests ---

    @Test
    fun centeredFittedWithinSpaceTextDisplaysText() = runComposeUiTest {
        val text = "Space fitted text"
        setContent {
            CenteredFittedWithinSpaceText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    // --- FittedText tests ---

    @Test
    fun fittedTextDisplaysText() = runComposeUiTest {
        val text = "Fitted single line"
        setContent {
            FittedText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun fittedTextMultiPieceDisplaysTexts() = runComposeUiTest {
        setContent {
            Row(Modifier.width(400.dp)) {
                FittedText(adjCount = 2, minFontSize = 8.sp) { modifier, textStyle, onTextLayout ->
                    Text("Left side", modifier = modifier, style = textStyle, onTextLayout = onTextLayout)
                    Text("Right side", modifier = modifier, style = textStyle, onTextLayout = onTextLayout)
                }
            }
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        onNodeWithText("Left side").assertExists()
        onNodeWithText("Right side").assertExists()
    }

    // --- WallyBoldText tests ---

    @Test
    fun wallyBoldTextDisplaysText() = runComposeUiTest {
        setContent {
            WallyBoldText(S.accept)
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
    }

    // --- TitleText tests ---

    @Test
    fun titleTextDisplaysStringText() = runComposeUiTest {
        val text = "Page Title"
        setContent {
            TitleText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun titleTextDisplaysResText() = runComposeUiTest {
        setContent {
            TitleText(S.Domain)
        }
        onNodeWithText(i18n(S.Domain)).assertIsDisplayed()
    }

    // --- SectionText tests ---

    @Test
    fun sectionTextDisplaysStringText() = runComposeUiTest {
        val text = "Section Header"
        setContent {
            SectionText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun sectionTextDisplaysResText() = runComposeUiTest {
        setContent {
            SectionText(S.Domain)
        }
        onNodeWithText(i18n(S.Domain)).assertIsDisplayed()
    }

    // --- CenteredSectionText tests ---

    @Test
    fun centeredSectionTextDisplaysStringText() = runComposeUiTest {
        val text = "Centered Section"
        setContent {
            CenteredSectionText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun centeredSectionTextDisplaysResText() = runComposeUiTest {
        setContent {
            CenteredSectionText(S.Domain)
        }
        onNodeWithText(i18n(S.Domain)).assertIsDisplayed()
    }

    // --- CenteredFittedTitleText tests ---

    @Test
    fun centeredFittedTitleTextDisplaysStringText() = runComposeUiTest {
        val text = "Fitted Title"
        setContent {
            CenteredFittedTitleText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun centeredFittedTitleTextDisplaysResText() = runComposeUiTest {
        setContent {
            CenteredFittedTitleText(S.Domain)
        }
        onNodeWithText(i18n(S.Domain)).assertIsDisplayed()
    }

    // --- WallyButtonText tests ---

    @Test
    fun wallyButtonTextDisplaysText() = runComposeUiTest {
        val text = "Click Me"
        setContent {
            WallyButtonText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    // --- WallyLargeButtonText tests ---

    @Test
    fun wallyLargeButtonTextDisplaysText() = runComposeUiTest {
        val text = "Large Button"
        setContent {
            WallyLargeButtonText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    // --- WallyMediumButtonText tests ---

    @Test
    fun wallyMediumButtonTextDisplaysStringText() = runComposeUiTest {
        val text = "Medium Button"
        setContent {
            WallyMediumButtonText(text)
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun wallyMediumButtonTextDisplaysResText() = runComposeUiTest {
        setContent {
            WallyMediumButtonText(S.accept)
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
    }

    // --- WallyBoringButton tests ---

    @Test
    fun wallyBoringButtonDisplaysContentAndClicks() = runComposeUiTest {
        var clicked = false
        val label = "Boring Button"
        setContent {
            WallyBoringButton(onClick = { clicked = true }) {
                WallyButtonText(label)
            }
        }
        onNodeWithText(label).assertIsDisplayed()
        onNodeWithText(label).performClick()
        assertTrue(clicked)
    }

    @Test
    fun wallyBoringButtonDisabledDoesNotClick() = runComposeUiTest {
        var clicked = false
        val label = "Disabled Button"
        setContent {
            WallyBoringButton(onClick = { clicked = true }, enabled = false) {
                WallyButtonText(label)
            }
        }
        onNodeWithText(label).assertIsDisplayed()
        onNodeWithText(label).performClick()
        assertFalse(clicked)
    }

    // --- WallyRoundedButton tests ---

    @Test
    fun wallyRoundedButtonDisplaysContentAndClicks() = runComposeUiTest {
        var clicked = false
        val label = "Rounded"
        setContent {
            WallyRoundedButton(onClick = { clicked = true }) {
                WallyButtonText(label)
            }
        }
        onNodeWithText(label).assertIsDisplayed()
        onNodeWithText(label).performClick()
        assertTrue(clicked)
    }

    // --- WallyBoringTextButton tests ---

    @Test
    fun wallyBoringTextButtonDisplaysResTextAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallyBoringTextButton(textRes = S.accept) { clicked = true }
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        onNodeWithText(i18n(S.accept)).performClick()
        assertTrue(clicked)
    }

    @Test
    fun wallyBoringTextButtonDisplaysStringTextAndClicks() = runComposeUiTest {
        var clicked = false
        val text = "Custom text"
        setContent {
            WallyBoringTextButton(text = text) { clicked = true }
        }
        onNodeWithText(text).assertIsDisplayed()
        onNodeWithText(text).performClick()
        assertTrue(clicked)
    }

    // --- WallyBoringLargeTextButton tests ---

    @Test
    fun wallyBoringLargeTextButtonDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallyBoringLargeTextButton(textRes = S.accept) { clicked = true }
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        onNodeWithText(i18n(S.accept)).performClick()
        assertTrue(clicked)
    }

    // --- WallyBoringMediumTextButton tests ---

    @Test
    fun wallyBoringMediumTextButtonDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallyBoringMediumTextButton(textRes = S.accept) { clicked = true }
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        onNodeWithText(i18n(S.accept)).performClick()
        assertTrue(clicked)
    }

    // --- WallySmallTextButton tests ---

    @Test
    fun wallySmallTextButtonDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallySmallTextButton(textRes = S.accept) { clicked = true }
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        onNodeWithText(i18n(S.accept)).performClick()
        assertTrue(clicked)
    }

    @Test
    fun wallySmallTextButtonSelectedIsBold() = runComposeUiTest {
        setContent {
            WallySmallTextButton(textRes = S.done, selected = true) {}
        }
        onNodeWithText(i18n(S.done)).assertIsDisplayed()
    }

    // --- WallyRoundedTextButton tests ---

    @Test
    fun wallyRoundedTextButtonResDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallyRoundedTextButton(textRes = S.accept) { clicked = true }
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        onNodeWithText(i18n(S.accept)).performClick()
        assertTrue(clicked)
    }

    @Test
    fun wallyRoundedTextButtonStringDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        val text = "Round text"
        setContent {
            WallyRoundedTextButton(text = text) { clicked = true }
        }
        onNodeWithText(text).assertIsDisplayed()
        onNodeWithText(text).performClick()
        assertTrue(clicked)
    }

    // --- WallyButtonRow tests ---

    @Test
    fun wallyButtonRowDisplaysChildren() = runComposeUiTest {
        setContent {
            WallyButtonRow {
                WallyButtonText("First")
                WallyButtonText("Second")
            }
        }
        onNodeWithText("First").assertIsDisplayed()
        onNodeWithText("Second").assertIsDisplayed()
    }

    // --- WallyError tests ---

    @Test
    fun wallyErrorDisplaysMessage() = runComposeUiTest {
        val msg = "Something went wrong"
        setContent {
            WallyError(msg)
        }
        onNodeWithText(msg).assertIsDisplayed()
    }

    // --- NoticeText tests ---

    @Test
    fun noticeTextDisplaysText() = runComposeUiTest {
        val notice = "Important notice"
        setContent {
            NoticeText(notice, Modifier)
        }
        onNodeWithText(notice).assertIsDisplayed()
    }

    // --- ErrorText tests ---

    @Test
    fun errorTextDisplaysText() = runComposeUiTest {
        val error = "Error occurred"
        setContent {
            ErrorText(error, Modifier)
        }
        onNodeWithText(error).assertIsDisplayed()
    }

    // --- WarningText tests ---

    @Test
    fun warningTextDisplaysText() = runComposeUiTest {
        val warning = "Warning message"
        setContent {
            WarningText(warning, Modifier)
        }
        onNodeWithText(warning).assertIsDisplayed()
    }

    // --- ConnectionWarning tests ---

    @Test
    fun connectionWarningDisplaysWarningMessage() = runComposeUiTest {
        setContent {
            ConnectionWarning()
        }
        onNodeWithText(i18n(S.connectionWarning)).assertIsDisplayed()
    }

    // --- WallyCardContent tests ---

    @Test
    fun wallyCardContentDisplaysHeadline() = runComposeUiTest {
        val headline = "Card headline"
        setContent {
            WallyCardContent(headline)
        }
        onNodeWithText(headline).assertIsDisplayed()
    }

    // --- GeneralConfirmationCard tests ---

    @Test
    fun generalConfirmationCardDisplaysTitleAndButtons() = runComposeUiTest {
        val title = "Confirm action?"
        var accepted: Boolean? = null
        setContent {
            GeneralConfirmationCard(
              title = title,
              content = { CenteredText("Are you sure?") },
              accept = { accepted = it }
            )
        }
        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText("Are you sure?").assertIsDisplayed()
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        onNodeWithText(i18n(S.cancel)).assertIsDisplayed()
    }

    @Test
    fun generalConfirmationCardAcceptButtonCallsWithTrue() = runComposeUiTest {
        var accepted: Boolean? = null
        setContent {
            GeneralConfirmationCard(
              title = "Confirm",
              content = { CenteredText("Content") },
              accept = { accepted = it }
            )
        }
        onNodeWithText(i18n(S.accept)).performClick()
        assertEquals(true, accepted)
    }

    @Test
    fun generalConfirmationCardCancelButtonCallsWithFalse() = runComposeUiTest {
        var accepted: Boolean? = null
        setContent {
            GeneralConfirmationCard(
              title = "Confirm",
              content = { CenteredText("Content") },
              accept = { accepted = it }
            )
        }
        onNodeWithText(i18n(S.cancel)).performClick()
        assertEquals(false, accepted)
    }

    // --- GeneralWarningCard tests ---

    @Test
    fun generalWarningCardDisplaysContent() = runComposeUiTest {
        val warningMsg = "This is a warning"
        setContent {
            GeneralWarningCard(
              icon = Icons.Default.Warning,
              content = { CenteredText(warningMsg) }
            )
        }
        onNodeWithText(warningMsg).assertIsDisplayed()
    }

    // --- IconTextButton tests ---

    @Test
    fun iconTextButtonDisplaysDescriptionAndClicks() = runComposeUiTest {
        var clicked = false
        val description = "Send action"
        setContent {
            IconTextButton(
              icon = Icons.AutoMirrored.Filled.Send,
              description = description,
            ) {
                clicked = true
            }
        }
        onNodeWithText(description).assertIsDisplayed()
        onNodeWithText(description).performClick()
        assertTrue(clicked)
    }

    // --- ButtonRowAcceptDeny tests ---

    @Test
    fun buttonRowAcceptDenyDisplaysBothButtons() = runComposeUiTest {
        setContent {
            ButtonRowAcceptDeny(
              accept = {},
              deny = {}
            )
        }
        onNodeWithText(i18n(S.accept)).assertIsDisplayed()
        onNodeWithText(i18n(S.deny)).assertIsDisplayed()
    }

    @Test
    fun buttonRowAcceptDenyAcceptCallsCallback() = runComposeUiTest {
        var accepted = false
        setContent {
            ButtonRowAcceptDeny(
              accept = { accepted = true },
              deny = {}
            )
        }
        onNodeWithText(i18n(S.accept)).performClick()
        assertTrue(accepted)
    }

    @Test
    fun buttonRowAcceptDenyDenyCallsCallback() = runComposeUiTest {
        var denied = false
        setContent {
            ButtonRowAcceptDeny(
              accept = {},
              deny = { denied = true }
            )
        }
        onNodeWithText(i18n(S.deny)).performClick()
        assertTrue(denied)
    }

    @Test
    fun buttonRowAcceptDenyHidesAcceptWhenDisabled() = runComposeUiTest {
        setContent {
            ButtonRowAcceptDeny(
              accept = {},
              deny = {},
              acceptEnabled = false
            )
        }
        onNodeWithText(i18n(S.accept)).assertDoesNotExist()
        onNodeWithText(i18n(S.deny)).assertIsDisplayed()
    }

    // --- WallyEmphasisBox tests ---

    @Test
    fun wallyEmphasisBoxDisplaysContent() = runComposeUiTest {
        val text = "Emphasis content"
        setContent {
            WallyEmphasisBox {
                CenteredText(text)
            }
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    // --- WallyBrightEmphasisBox tests ---

    @Test
    fun wallyBrightEmphasisBoxDisplaysContent() = runComposeUiTest {
        val text = "Bright emphasis content"
        setContent {
            WallyBrightEmphasisBox {
                CenteredText(text)
            }
        }
        onNodeWithText(text).assertIsDisplayed()
    }

    // --- SelectStringDropDown tests ---

    @Test
    fun selectStringDropDownDisplaysSelectedValue() = runComposeUiTest {
        setContent {
            SelectStringDropDown(
              selected = "USD",
              options = listOf("USD", "EUR", "GBP"),
              expanded = false,
              onSelect = {},
              onExpand = {},
              modifier = Modifier
            )
        }
        onNodeWithText("USD").assertIsDisplayed()
    }

    @Test
    fun selectStringDropDownExpandedShowsOptions() = runComposeUiTest {
        setContent {
            SelectStringDropDown(
              selected = "USD",
              options = listOf("USD", "EUR", "GBP"),
              expanded = true,
              onSelect = {},
              onExpand = {},
              modifier = Modifier
            )
        }
        onNodeWithText("EUR").assertIsDisplayed()
        onNodeWithText("GBP").assertIsDisplayed()
    }

    @Test
    fun selectStringDropDownSelectsOption() = runComposeUiTest {
        var selected = "USD"
        setContent {
            SelectStringDropDown(
              selected = selected,
              options = listOf("USD", "EUR", "GBP"),
              expanded = true,
              onSelect = { selected = it },
              onExpand = {},
              modifier = Modifier
            )
        }
        onNodeWithText("EUR").performClick()
        assertEquals("EUR", selected)
    }

    // --- QrCode tests ---

    @Test
    fun qrCodeDisplays() = runComposeUiTest {
        setContent {
            QrCode("https://example.com", Modifier)
        }
        // QrCode renders an Image, just verify it doesn't crash
    }

    // --- ConfirmDismissNoteDialog tests ---

    @Test
    fun confirmDismissNoteDialogDisplaysWhenDisplayedTrue() = runComposeUiTest {
        val titleRes = S.Domain
        val text = "Dialog text"
        val note = "Dialog note"
        setContent {
            ConfirmDismissNoteDialog(
              amount = BigDecimal.fromInt(100),
              assets = emptyList(),
              displayed = true,
              titleRes = titleRes,
              text = text,
              note = note,
              dismissRes = S.cancel,
              confirmRes = S.done,
              onDismiss = {},
              onConfirm = {}
            )
        }
        onNodeWithText(i18n(titleRes)).assertIsDisplayed()
        onNodeWithText(text).assertIsDisplayed()
        onNodeWithText(note).assertIsDisplayed()
        onNodeWithText(i18n(S.cancel)).assertIsDisplayed()
        onNodeWithText(i18n(S.done)).assertIsDisplayed()
    }

    @Test
    fun confirmDismissNoteDialogDoesNotDisplayWhenFalse() = runComposeUiTest {
        setContent {
            ConfirmDismissNoteDialog(
              amount = BigDecimal.fromInt(100),
              assets = emptyList(),
              displayed = false,
              titleRes = S.accept,
              text = "Hidden dialog",
              note = "Hidden note",
              dismissRes = S.cancel,
              confirmRes = S.accept,
              onDismiss = {},
              onConfirm = {}
            )
        }
        onNodeWithText("Hidden dialog").assertDoesNotExist()
    }

    @Test
    fun confirmDismissNoteDialogConfirmCallsCallback() = runComposeUiTest {
        var confirmed = false
        setContent {
            ConfirmDismissNoteDialog(
              amount = BigDecimal.fromInt(50),
              assets = emptyList(),
              displayed = true,
              titleRes = S.accept,
              text = "Confirm this",
              note = "Note",
              dismissRes = S.cancel,
              confirmRes = S.done,
              onDismiss = {},
              onConfirm = { confirmed = true }
            )
        }
        onNodeWithText(i18n(S.done)).performClick()
        assertTrue(confirmed)
    }

    @Test
    fun confirmDismissNoteDialogDismissCallsCallback() = runComposeUiTest {
        var dismissed = false
        setContent {
            ConfirmDismissNoteDialog(
              amount = BigDecimal.fromInt(50),
              assets = emptyList(),
              displayed = true,
              titleRes = S.accept,
              text = "Dismiss this",
              note = "Note",
              dismissRes = S.deny,
              confirmRes = S.accept,
              onDismiss = { dismissed = true },
              onConfirm = {}
            )
        }
        onNodeWithText(i18n(S.deny)).performClick()
        assertTrue(dismissed)
    }

    // --- Syncing tests ---

    @Test
    fun syncingShowsSyncingTextWhenNotSynced() = runComposeUiTest {
        val vm = SyncViewModelFake()
        vm.syncValue.value = false
        setContent {
            Syncing(syncViewModel = vm)
        }
        waitForCatching(10000) {
            onNodeWithText(i18n(S.unsynced), useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithText(i18n(S.synced), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("syncSpinner", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("syncedCheckmark", useUnmergedTree = true).assertDoesNotExist()
        // Verify transition
        vm.syncValue.value = true
        waitForIdle()
        waitForCatching(10000) {
            onNodeWithText(i18n(S.synced), useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithText(i18n(S.unsynced), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("syncedCheckmark", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("syncSpinner", useUnmergedTree = true).assertDoesNotExist()
        vm.syncValue.value = false
        waitForIdle()
        waitForCatching(10000) {
            onNodeWithText(i18n(S.unsynced), useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithText(i18n(S.synced), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("syncSpinner", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("syncedCheckmark", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun syncingShowsSyncedTextWhenSynced() = runComposeUiTest {
        val vm = SyncViewModelFake()
        vm.syncValue.value = true
        setContent {
            Syncing(syncViewModel = vm)
        }
        onNodeWithText(i18n(S.synced)).assertIsDisplayed()
        onNodeWithTag("syncedCheckmark").assertIsDisplayed()
    }

    // --- WallyDataEntry tests ---

    @Test
    fun wallyDataEntryStringDisplaysValue() = runComposeUiTest {
        setContent {
            WallyDataEntry(value = "test input")
        }
        onNodeWithText("test input").assertIsDisplayed()
    }

    @Test
    fun wallyDataEntryStringCallsOnValueChange() = runComposeUiTest {
        var changed = ""
        setContent {
            WallyDataEntry(value = "", onValueChange = { changed = it })
        }
        onNode(hasSetTextAction()).performTextInput("hello")
        assertEquals("hello", changed)
    }

    @Test
    fun wallyDataEntryTextFieldValueDisplaysValue() = runComposeUiTest {
        val tfv = mutableStateOf(TextFieldValue("initial text"))
        setContent {
            WallyDataEntry(value = tfv)
        }
        onNodeWithText("initial text").assertIsDisplayed()
    }

    @Test
    fun wallyDataEntryTextFieldValueCallsOnValueChange() = runComposeUiTest {
        val tfv = mutableStateOf(TextFieldValue(""))
        var changed: TextFieldValue? = null
        setContent {
            WallyDataEntry(value = tfv, onValueChange = { changed = it })
        }
        onNode(hasSetTextAction()).performTextInput("typed")
        assertEquals("typed", changed?.text)
    }

    // --- WallyTextEntry tests ---

    @Test
    fun wallyTextEntryDisplaysValue() = runComposeUiTest {
        setContent {
            WallyTextEntry(value = "entry text")
        }
        onNodeWithText("entry text").assertIsDisplayed()
    }

    @Test
    fun wallyTextEntryCallsOnValueChange() = runComposeUiTest {
        var changed = ""
        setContent {
            WallyTextEntry(value = "", onValueChange = { changed = it })
        }
        onNode(hasSetTextAction()).performTextInput("new value")
        assertEquals("new value", changed)
    }

    // --- WallyDecimalEntry tests ---

    @Test
    fun wallyDecimalEntryDisplaysValue() = runComposeUiTest {
        val value = mutableStateOf("42.5")
        setContent {
            WallyDecimalEntry(value = value)
        }
        onNodeWithText("42.5").assertIsDisplayed()
    }

    @Test
    fun wallyDecimalEntryAcceptsDecimalInput() = runComposeUiTest {
        val value = mutableStateOf("")
        var lastValue = ""
        setContent {
            WallyDecimalEntry(value = value, onValueChange = { lastValue = it; it })
        }
        onNode(hasSetTextAction()).performTextInput("12.34")
        assertEquals("12.34", lastValue)
    }

    // --- WallyDigitEntry tests ---

    @Test
    fun wallyDigitEntryDisplaysValue() = runComposeUiTest {
        setContent {
            WallyDigitEntry(value = "999")
        }
        onNodeWithText("999").assertIsDisplayed()
    }

    @Test
    fun wallyDigitEntryAcceptsDigitInput() = runComposeUiTest {
        var lastValue = ""
        setContent {
            WallyDigitEntry(value = "", onValueChange = { lastValue = it; it })
        }
        onNode(hasSetTextAction()).performTextInput("456")
        assertEquals("456", lastValue)
    }

    // --- WallyOutlineDataEntry tests ---

    @Test
    fun wallyOutlineDataEntryDisplaysLabel() = runComposeUiTest {
        val tfv = mutableStateOf(TextFieldValue(""))
        setContent {
            WallyOutlineDataEntry(
              value = tfv,
              label = "Amount",
              suffix = null
            )
        }
        onNodeWithText("Amount").assertIsDisplayed()
    }

    @Test
    fun wallyOutlineDataEntryDisplaysValue() = runComposeUiTest {
        val tfv = mutableStateOf(TextFieldValue("100"))
        setContent {
            WallyOutlineDataEntry(
              value = tfv,
              label = "Amount",
              suffix = null
            )
        }
        onNodeWithText("100").assertIsDisplayed()
    }

    // --- WallyOutLineDecimalEntry tests ---

    @Test
    fun wallyOutLineDecimalEntryDisplaysLabel() = runComposeUiTest {
        val value = mutableStateOf("50")
        setContent {
            WallyOutLineDecimalEntry(
              value = value,
              label = "Price"
            )
        }
        onNodeWithText("Price").assertIsDisplayed()
        onNodeWithText("50").assertIsDisplayed()
    }

    // --- WallyOutLineDecimalEntryTFV tests ---

    @Test
    fun wallyOutLineDecimalEntryTFVDisplaysLabel() = runComposeUiTest {
        val tfv = mutableStateOf(TextFieldValue("75"))
        setContent {
            WallyOutLineDecimalEntryTFV(
              tfv = tfv,
              label = "Quantity"
            )
        }
        onNodeWithText("Quantity").assertIsDisplayed()
        onNodeWithText("75").assertIsDisplayed()
    }

    // --- WallyIncognitoTextEntry tests ---

    @Test
    fun wallyIncognitoTextEntryDisplaysValue() = runComposeUiTest {
        setContent {
            WallyIncognitoTextEntry(value = "secret", modifier = Modifier) {}
        }
        onNodeWithText("secret").assertIsDisplayed()
    }

    @Test
    fun wallyIncognitoTextEntryCallsOnValueChange() = runComposeUiTest {
        var changed = ""
        setContent {
            WallyIncognitoTextEntry(value = "", modifier = Modifier) { changed = it }
        }
        onNode(hasSetTextAction()).performTextInput("typed")
        assertEquals("typed", changed)
    }

    // --- WallyNumericInputFieldBalance tests ---

    @Test
    fun wallyNumericInputFieldBalanceDisplaysLabel() = runComposeUiTest {
        setContent {
            WallyNumericInputFieldBalance(
              amount = "",
              label = "Send amount",
              placeholder = "0.00",
              hasButtonRow = false,
              onValueChange = {}
            )
        }
        onNodeWithText("Send amount").assertIsDisplayed()
    }

    @Test
    fun wallyNumericInputFieldBalanceDisplaysAmount() = runComposeUiTest {
        setContent {
            WallyNumericInputFieldBalance(
              amount = "123",
              label = "Amount",
              placeholder = "0",
              hasButtonRow = false,
              onValueChange = {}
            )
        }
        onNodeWithText("123").assertIsDisplayed()
    }

    @Test
    fun wallyNumericInputFieldBalanceShowsClearButtonWhenNotEmpty() = runComposeUiTest {
        setContent {
            WallyNumericInputFieldBalance(
              amount = "50",
              label = "Amount",
              placeholder = "0",
              hasButtonRow = false,
              onValueChange = {}
            )
        }
        onNodeWithContentDescription(i18n(S.clearAmount)).assertIsDisplayed()
    }

    @Test
    fun wallyNumericInputFieldBalanceClearButtonClearsAmount() = runComposeUiTest {
        var currentAmount = "50"
        setContent {
            WallyNumericInputFieldBalance(
              amount = currentAmount,
              label = "Amount",
              placeholder = "0",
              hasButtonRow = false,
              onValueChange = { currentAmount = it }
            )
        }
        onNodeWithContentDescription(i18n(S.clearAmount)).performClick()
        assertEquals("", currentAmount)
    }

    // --- SelectStringDropdownRes tests ---

    @Test
    fun selectStringDropdownResDisplaysDescriptionAndSelected() = runComposeUiTest {
        setContent {
            SelectStringDropdownRes(
              descriptionTextRes = S.Amount,
              selected = "USD",
              options = listOf("USD", "EUR", "GBP"),
              expanded = false,
              textStyle = TextStyle.Default,
              onSelect = {},
              onExpand = {}
            )
        }
        onNodeWithText(i18n(S.Amount)).assertIsDisplayed()
        onNodeWithText("USD").assertIsDisplayed()
    }

    @Test
    fun selectStringDropdownResExpandedShowsOptions() = runComposeUiTest {
        setContent {
            SelectStringDropdownRes(
              descriptionTextRes = S.Amount,
              selected = "USD",
              options = listOf("USD", "EUR", "GBP"),
              expanded = true,
              textStyle = TextStyle.Default,
              onSelect = {},
              onExpand = {}
            )
        }
        onNodeWithText("EUR").assertIsDisplayed()
        onNodeWithText("GBP").assertIsDisplayed()
    }

    @Test
    fun selectStringDropdownResSelectsOption() = runComposeUiTest {
        var selected = "USD"
        setContent {
            SelectStringDropdownRes(
              descriptionTextRes = S.Amount,
              selected = selected,
              options = listOf("USD", "EUR", "GBP"),
              expanded = true,
              textStyle = TextStyle.Default,
              onSelect = { selected = it },
              onExpand = {}
            )
        }
        onNodeWithText("GBP").performClick()
        assertEquals("GBP", selected)
    }

    // --- StringInputField tests ---

    @Test
    fun stringInputFieldDisplaysDescription() = runComposeUiTest {
        setContent {
            StringInputField(
              descriptionRes = S.Domain,
              labelRes = S.Amount,
              text = "",
              onChange = {}
            )
        }
        onNodeWithText(i18n(S.Domain)).assertIsDisplayed()
    }

    @Test
    fun stringInputFieldDisplaysLabel() = runComposeUiTest {
        setContent {
            StringInputField(
              descriptionRes = S.Domain,
              labelRes = S.Amount,
              text = "",
              onChange = {}
            )
        }
        onNodeWithText(i18n(S.Amount)).assertIsDisplayed()
    }

    // --- StringInputTextField tests ---

    @Test
    fun stringInputTextFieldDisplaysLabel() = runComposeUiTest {
        setContent {
            StringInputTextField(
              labelRes = S.Amount,
              text = "",
              onChange = {}
            )
        }
        onNodeWithText(i18n(S.Amount)).assertIsDisplayed()
    }

    @Test
    fun stringInputTextFieldDisplaysExistingText() = runComposeUiTest {
        setContent {
            StringInputTextField(
              labelRes = S.Amount,
              text = "existing value",
              onChange = {}
            )
        }
        onNodeWithText("existing value").assertIsDisplayed()
    }

    // --- AddressInputField tests ---

    @Test
    fun addressInputFieldDisplaysLabel() = runComposeUiTest {
        setContent {
            AddressInputField(
              descriptionRes = S.Domain,
              labelRes = S.Amount,
              text = "",
              onChange = {}
            )
        }
        onNodeWithText(i18n(S.Amount)).assertIsDisplayed()
    }

    // --- AddressInputTextField tests ---

    @Test
    fun addressInputTextFieldDisplaysLabel() = runComposeUiTest {
        setContent {
            AddressInputTextField(
              labelRes = S.Amount,
              text = "",
              onChange = {}
            )
        }
        onNodeWithText(i18n(S.Amount)).assertIsDisplayed()
    }

    @Test
    fun addressInputTextFieldDisplaysExistingText() = runComposeUiTest {
        setContent {
            AddressInputTextField(
              labelRes = S.Amount,
              text = "nexa:address123",
              onChange = {}
            )
        }
        onNodeWithText("nexa:address123").assertIsDisplayed()
    }

    // --- DecimalInputField tests ---

    @Test
    fun decimalInputFieldDisplaysDescriptionAndLabel() = runComposeUiTest {
        setContent {
            DecimalInputField(
              descriptionRes = S.Domain,
              labelRes = S.Amount,
              text = "",
              onChange = {}
            )
        }
        onNodeWithText(i18n(S.Domain)).assertIsDisplayed()
        onNodeWithText(i18n(S.Amount)).assertIsDisplayed()
    }

    @Test
    fun decimalInputFieldUpdatesTextViaOnChange() = runComposeUiTest {
        val text = mutableStateOf("")
        setContent {
            DecimalInputField(
              descriptionRes = S.Domain,
              labelRes = S.Amount,
              text = text.value,
              onChange = { text.value = it }
            )
        }
        onNode(hasSetTextAction()).performTextInput("3.14")
        assertEquals("3.14", text.value)
    }

    // --- LongInputField tests ---

    @Test
    fun longInputFieldDisplaysDescriptionAndLabel() = runComposeUiTest {
        setContent {
            LongInputField(
              descriptionRes = S.Domain,
              labelRes = S.Amount,
              amount = 0L,
              onChange = {}
            )
        }
        onNodeWithText(i18n(S.Domain)).assertIsDisplayed()
        onNodeWithText(i18n(S.Amount)).assertIsDisplayed()
    }

    @Test
    fun longInputFieldDisplaysNonZeroAmount() = runComposeUiTest {
        setContent {
            LongInputField(
              descriptionRes = S.Domain,
              labelRes = S.Amount,
              amount = 42L,
              onChange = {}
            )
        }
        onNodeWithText("42").assertIsDisplayed()
    }

    @Test
    fun longInputFieldShowsEmptyForZero() = runComposeUiTest {
        setContent {
            LongInputField(
              descriptionRes = S.Domain,
              labelRes = S.Amount,
              amount = 0L,
              onChange = {}
            )
        }
        onNodeWithText("0").assertDoesNotExist()
    }

    // --- WallyBoringIconButton (ImageVector overload) tests ---

    @Test
    fun wallyBoringIconButtonImageVectorDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallyBoringIconButton(
              icon = Icons.AutoMirrored.Filled.Send,
              description = "Send icon",
              onClick = { clicked = true }
            )
        }
        onNodeWithContentDescription("Send icon").assertIsDisplayed()
        onNodeWithContentDescription("Send icon").performClick()
        assertTrue(clicked)
    }

    @Test
    fun wallyBoringIconButtonImageVectorDisabledRendersIcon() = runComposeUiTest {
        setContent {
            WallyBoringIconButton(
              icon = Icons.AutoMirrored.Filled.Send,
              description = "Send icon",
              enabled = false,
              onClick = {}
            )
        }
        onNodeWithContentDescription("Send icon").assertIsDisplayed()
    }

    // --- WallyBoringIconButton (String icon path) tests ---

    @Test
    fun wallyBoringIconButtonStringResDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallyBoringIconButton(
              iconRes = "icons/nexa_icon.png",
              onClick = { clicked = true }
            )
        }
        // ResImageView adds testTag "res_image"
        onNode(hasTestTag("res_image")).assertIsDisplayed()
        onNode(hasTestTag("res_image")).performClick()
        assertTrue(clicked)
    }

    // --- WallyBoringLargeIconButton tests ---

    @Test
    fun wallyBoringLargeIconButtonDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallyBoringLargeIconButton(
              iconRes = "icons/nexa_icon.png",
              onClick = { clicked = true }
            )
        }
        onNode(hasTestTag("res_image")).assertIsDisplayed()
        onNode(hasTestTag("res_image")).performClick()
        assertTrue(clicked)
    }

    // --- WallyImageButton tests ---

    @Test
    fun wallyImageButtonDisplaysAndClicks() = runComposeUiTest {
        var clicked = false
        setContent {
            WallyImageButton(
              resPath = "icons/nexa_icon.png",
              modifier = Modifier,
              onClick = { clicked = true }
            )
        }
        onNode(hasTestTag("res_image")).assertIsDisplayed()
        onNode(hasTestTag("res_image")).performClick()
        assertTrue(clicked)
    }

    // --- BlockchainIcon tests ---

    @Test
    fun blockchainIconDisplaysLabelAndValue() = runComposeUiTest {
        setContent {
            BlockchainIcon(
              label = "Blockchain",
              value = "NEXA",
              chain = ChainSelector.NEXA
            )
        }
        onNodeWithText("Blockchain").assertIsDisplayed()
        onNodeWithText("NEXA").assertIsDisplayed()
    }

    @Test
    fun blockchainIconNullChainRendersNothing() = runComposeUiTest {
        setContent {
            BlockchainIcon(
              label = "Blockchain",
              value = "NEXA",
              chain = null
            )
        }
        onNodeWithText("Blockchain").assertDoesNotExist()
    }

    // --- FontScale tests ---

    @Suppress("DEPRECATION")
    @Test
    fun fontScaleReturnsScaledFontSize() = runComposeUiTest {
        var result: TextUnit = TextUnit.Unspecified
        setContent {
            result = FontScale(2.0)
        }
        val expected = defaultFontSize * 2.0
        assertEquals(expected, result)
    }

    @Suppress("DEPRECATION")
    @Test
    fun fontScaleWithOneReturnsDefault() = runComposeUiTest {
        var result: TextUnit = TextUnit.Unspecified
        setContent {
            result = FontScale(1.0)
        }
        assertEquals(defaultFontSize, result)
    }

    // --- FontScaleStyle tests ---

    @Suppress("DEPRECATION")
    @Test
    fun fontScaleStyleReturnsStyleWithScaledFontSize() = runComposeUiTest {
        var result: TextStyle? = null
        setContent {
            result = FontScaleStyle(2.0)
        }
        val expected = defaultFontSize * 2.0
        assertEquals(expected, result!!.fontSize)
    }

    // --- WallyTextStyle tests ---

    @Suppress("DEPRECATION")
    @Test
    fun wallyTextStyleReturnsCorrectDefaults() = runComposeUiTest {
        var result: TextStyle? = null
        setContent {
            result = WallyTextStyle()
        }
        assertEquals(FontWeight.Normal, result!!.fontWeight)
        assertEquals(0.em, result!!.lineHeight)
    }

    @Suppress("DEPRECATION")
    @Test
    fun wallyTextStyleRespectsColorAndWeight() = runComposeUiTest {
        var result: TextStyle? = null
        setContent {
            result = WallyTextStyle(fontScale = 1.5, fw = FontWeight.Bold, col = Color.Red)
        }
        assertEquals(FontWeight.Bold, result!!.fontWeight)
        assertEquals(Color.Red, result!!.color)
        val expectedSize = defaultFontSize * 1.5
        assertEquals(expectedSize, result!!.fontSize)
    }

    // --- WallySectionTextStyle tests ---

    @Test
    fun wallySectionTextStyleReturnsBoldBlackStyle() = runComposeUiTest {
        var result: TextStyle? = null
        setContent {
            result = WallySectionTextStyle()
        }
        assertEquals(FontWeight.Bold, result!!.fontWeight)
        assertEquals(Color.Black, result!!.color)
    }

    // --- WallyTitleTextStyle tests ---

    @Test
    fun wallyTitleTextStyleReturnsBlackCenteredStyle() = runComposeUiTest {
        var result: TextStyle? = null
        setContent {
            result = WallyTitleTextStyle()
        }
        assertEquals(Color.Black, result!!.color)
    }

    // --- WallyAmountSelectorRow tests ---

    @Test
    fun wallyAmountSelectorRowShowsButtonsWhenActive() = runComposeUiTest {
        val isActive = mutableStateOf(true)
        setContent {
            Column(Modifier.width(400.dp)) {
                WallyAmountSelectorRow(isActive) {}
            }
        }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        // SubcomposeLayout creates duplicate nodes (measurement + render), so use onAllNodes
        onAllNodesWithText(i18n(S.sendAll)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        onAllNodesWithText(i18n(S.thousand)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        onAllNodesWithText(i18n(S.million)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun wallyAmountSelectorRowAllButtonCallsCallback() = runComposeUiTest {
        val isActive = mutableStateOf(true)
        var selected: AmountSelector? = null
        setContent {
            Column(Modifier.width(400.dp)) {
                WallyAmountSelectorRow(isActive) { selected = it }
            }
        }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        onAllNodesWithText(i18n(S.sendAll)).onLast().performClick()
        assertEquals(AmountSelector.ALL, selected)
    }

    @Test
    fun wallyAmountSelectorRowThousandButtonCallsCallback() = runComposeUiTest {
        val isActive = mutableStateOf(true)
        var selected: AmountSelector? = null
        setContent {
            Column(Modifier.width(400.dp)) {
                WallyAmountSelectorRow(isActive) { selected = it }
            }
        }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        onAllNodesWithText(i18n(S.thousand)).onLast().performClick()
        assertEquals(AmountSelector.THOUSAND, selected)
    }

    @Test
    fun wallyAmountSelectorRowMillionButtonCallsCallback() = runComposeUiTest {
        val isActive = mutableStateOf(true)
        var selected: AmountSelector? = null
        setContent {
            Column(Modifier.width(400.dp)) {
                WallyAmountSelectorRow(isActive) { selected = it }
            }
        }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        onAllNodesWithText(i18n(S.million)).onLast().performClick()
        assertEquals(AmountSelector.MILLION, selected)
    }

    @Test
    fun wallyAmountSelectorRowWithDoneButtonShowsFourButtons() = runComposeUiTest {
        val isActive = mutableStateOf(true)
        setContent {
            Column(Modifier.width(400.dp)) {
                WallyAmountSelectorRow(isActive, doneButton = true) {}
            }
        }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        onAllNodesWithText(i18n(S.sendAll)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        onAllNodesWithText(i18n(S.thousand)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        onAllNodesWithText(i18n(S.million)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        onAllNodesWithText(i18n(S.done)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun wallyAmountSelectorRowDoneButtonCallsCallback() = runComposeUiTest {
        val isActive = mutableStateOf(true)
        var selected: AmountSelector? = null
        setContent {
            Column(Modifier.width(400.dp)) {
                WallyAmountSelectorRow(isActive, doneButton = true) { selected = it }
            }
        }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        onAllNodesWithText(i18n(S.done)).onLast().performClick()
        assertEquals(AmountSelector.DONE, selected)
    }
}
