package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import ui.camouflage.MeditationScreen
import ui.camouflage.MeditationViewModelFake
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MeditationScreenTest
{
    @Test
    fun testPreparationViewDisplayed() = runComposeUiTest {
        val vm = MeditationViewModelFake()
        setContent { MeditationScreen(vm) }

        // Preparation view shows start and meditation text
        onNodeWithText("Start").assertIsDisplayed()
        onNodeWithText("meditation").assertIsDisplayed()

        // Ongoing/complete elements should not exist
        onNodeWithText("Cancel").assertDoesNotExist()
        onNodeWithText("Okay").assertDoesNotExist()
    }

    @Test
    fun testStartMeditationTransitionsToOngoing() = runComposeUiTest {
        val vm = MeditationViewModelFake()
        setContent { MeditationScreen(vm) }

        onNodeWithText("Start").performClick()

        // Should now show the ongoing view with timer and cancel
        onNodeWithText("20:00").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
        onNodeWithText("Meditating...").assertIsDisplayed()

        // Start button should no longer be visible
        onNodeWithText("Start").assertDoesNotExist()
    }

    @Test
    fun testCancelMeditationReturnsToPreparation() = runComposeUiTest {
        val vm = MeditationViewModelFake()
        setContent { MeditationScreen(vm) }

        // Start then cancel
        onNodeWithText("Start").performClick()
        onNodeWithText("Cancel").performClick()

        // Should be back to preparation view
        onNodeWithText("Start").assertIsDisplayed()
        onNodeWithText("meditation").assertIsDisplayed()
        onNodeWithText("Meditating...").assertDoesNotExist()
    }

    @Test
    fun testMeditationCompleteView() = runComposeUiTest {
        val vm = MeditationViewModelFake()
        setContent { MeditationScreen(vm) }

        // Start then complete
        onNodeWithText("Start").performClick()
        vm.completeMeditation()

        // Should show the complete view with Okay button
        onNodeWithText("Meditation complete!").assertIsDisplayed()
        onNodeWithText("00:00").assertIsDisplayed()
        onNodeWithText("Okay").assertIsDisplayed()
    }

    @Test
    fun testOkayAfterCompleteReturnsToPreparation() = runComposeUiTest {
        val vm = MeditationViewModelFake()
        setContent { MeditationScreen(vm) }

        // Start, complete, then press Okay
        onNodeWithText("Start").performClick()
        vm.completeMeditation()
        onNodeWithText("Okay").performClick()

        // Should return to preparation view
        onNodeWithText("Start").assertIsDisplayed()
        onNodeWithText("meditation").assertIsDisplayed()
    }

    @Test
    fun testIntervalSoundToggle() = runComposeUiTest {
        val vm = MeditationViewModelFake()
        setContent { MeditationScreen(vm) }

        // Open the drawer by clicking the menu icon
        onNodeWithContentDescription("Open menu").performClick()

        // Interval sound label should be visible and switch should be on by default
        onNodeWithText("1 min interval sound").assertIsDisplayed()
        onNode(isToggleable() and hasParent(hasText("1 min interval sound"))).assertIsOn()

        // Toggle off via the label row
        onNodeWithText("1 min interval sound").performClick()
        onNode(isToggleable() and hasParent(hasText("1 min interval sound"))).assertIsOff()

        // Toggle back on
        onNodeWithText("1 min interval sound").performClick()
        onNode(isToggleable() and hasParent(hasText("1 min interval sound"))).assertIsOn()
    }
}
