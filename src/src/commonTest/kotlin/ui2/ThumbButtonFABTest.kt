package ui2

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import info.bitcoinunlimited.www.wally.KotlinTarget
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui2.ThumbButton
import info.bitcoinunlimited.www.wally.ui2.ThumbButtonFAB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ThumbButtonFABTest
{
    @Test
    fun thumbButtonTest() = runComposeUiTest {
        val res = S.imageQr
        var isClicked = false

        setContent {
            ThumbButton(
              icon = Icons.Outlined.DocumentScanner,
              textRes = res,
              mod = Modifier.clickable {
                  isClicked = true
              }
            )
        }

        onNodeWithText(i18n(res)).assertIsDisplayed()
        onNodeWithContentDescription(i18n(res)).assertIsDisplayed()
        onNodeWithContentDescription(i18n(res)).performClick()
        assertTrue { isClicked }
    }

    @Test
    fun thumbButtonFAB_showsAppropriateButtonsBasedOnPlatform() = runComposeUiTest {
        setContent {
            ThumbButtonFAB(
              onResult = {},
              onScanQr = {},
              pasteIcon = Icons.Outlined.ContentPaste
            )
        }

        val imageQrText = i18n(S.imageQr)
        if (platform().hasGallery) {
            onNodeWithContentDescription(imageQrText).assertIsDisplayed()
        } else {
            onNodeWithContentDescription(imageQrText).assertDoesNotExist()
        }

        val scanQrText = i18n(S.scanQr)
        if (platform().hasQrScanner) {
            onNodeWithContentDescription(scanQrText).assertIsDisplayed()
        } else {
            onNodeWithContentDescription(scanQrText).assertDoesNotExist()
        }

        val pasteText = i18n(S.paste)
        if (platform().target != KotlinTarget.JVM) {
            onNodeWithContentDescription(pasteText).assertIsDisplayed()
        } else {
            onNodeWithContentDescription(pasteText).assertDoesNotExist()
        }
    }

    @Test
    fun thumbButtonFAB_clickTriggersActions() = runComposeUiTest {
        var qrResult = ""
        var qrScanCalled = false
        val clipboardText = "Test Clipboard Content"
        class FakeClipboardManager : ClipboardManager
        {
            private var text: AnnotatedString = AnnotatedString(clipboardText)
            override fun getText(): AnnotatedString = text
            override fun setText(annotatedString: AnnotatedString)
            {
                text = annotatedString
            }
        }
        val clipboardManager = FakeClipboardManager()

        setContent {
            ThumbButtonFAB(
              onResult = { qrResult = it },
              onScanQr = { qrScanCalled = true },
              clipmgr = clipboardManager
            )
        }

        // Test Document Scanner button
        if (platform().hasGallery) {
            onNodeWithContentDescription(i18n(S.imageQr)).performClick()
        }


        // Test QR Code Scanner button
        if (platform().hasQrScanner) {
            onNodeWithContentDescription(i18n(S.scanQr)).performClick()
            assertTrue(qrScanCalled)
        }

        // Test Content Paste button
        if (platform().target != KotlinTarget.JVM) {
            onNodeWithContentDescription(i18n(S.paste)).performClick()
            assertEquals(clipboardText, qrResult)
        }
    }
}