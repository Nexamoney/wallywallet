package ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.text.input.TextFieldValue
import info.bitcoinunlimited.www.wally.ui.RecoveryPhraseInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RecoveryPhraseInputTest: WallyUiTestBase(false)
{
    /** The field shows the phrase it is given, and follows later changes to it */
    @Test
    fun showsAndFollowsRecoveryPhrase() = runComposeUiTest {
        var phrase by mutableStateOf(TextFieldValue("abandon ability"))
        setContent {
            RecoveryPhraseInput(phrase, true) { phrase = it }
        }
        settle()
        onNodeWithTag("RecoveryPhraseInput").assertTextEquals("abandon ability")

        phrase = TextFieldValue("zoo zone")
        settle()
        onNodeWithTag("RecoveryPhraseInput").assertTextEquals("zoo zone")
    }

    /** The check/X marker follows validOrNoRecoveryPhrase */
    @Test
    fun validityMarkerFollowsValidFlag() = runComposeUiTest {
        var valid by mutableStateOf(true)
        setContent {
            RecoveryPhraseInput(TextFieldValue("abandon"), valid) { }
        }
        settle()
        onNodeWithTag("recoveryPhrase_C").assertExists()
        onNodeWithTag("recoveryPhrase_X").assertDoesNotExist()

        valid = false
        settle()
        onNodeWithTag("recoveryPhrase_X").assertExists()
        onNodeWithTag("recoveryPhrase_C").assertDoesNotExist()
    }

    /** Typing reaches onValueChange, and the field renders what the caller writes back */
    @Test
    fun typingInvokesOnValueChange() = runComposeUiTest {
        var phrase by mutableStateOf(TextFieldValue(""))
        var callbacks = 0
        setContent {
            RecoveryPhraseInput(phrase, true) { phrase = it; callbacks++ }
        }
        settle()
        onNodeWithTag("RecoveryPhraseInput").performTextInput("legal")
        settle()
        assertTrue(callbacks > 0)
        assertEquals("legal", phrase.text)
        onNodeWithTag("RecoveryPhraseInput").assertTextEquals("legal")
    }

    /** A partial word offers bip39 completions, and picking one replaces just that word */
    @Test
    fun suggestionCompletesTrailingWord() = runComposeUiTest {
        var phrase by mutableStateOf(TextFieldValue("zoo aban"))
        setContent {
            RecoveryPhraseInput(phrase, true) { phrase = it }
        }
        settle()
        // "aban" only prefixes "abandon", so exactly one suggestion is offered
        onNodeWithText("abandon").assertExists()
        onNodeWithText("abandon").performClick()
        settle()
        assertEquals("zoo abandon ", phrase.text)
    }

    /** A complete word is not a prefix of anything else, so no suggestions are offered */
    @Test
    fun noSuggestionsWithoutAPartialWord() = runComposeUiTest {
        setContent {
            RecoveryPhraseInput(TextFieldValue("abandon "), true) { }
        }
        settle()
        onNodeWithText("abandon").assertDoesNotExist()
    }
}
