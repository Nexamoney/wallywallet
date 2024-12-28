package ui2

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.ReceiveScreenContent
import org.nexa.libnexakotlin.*
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReceiveScreenTest
{
    @Test
    fun receiveScreenContentTest() = runComposeUiTest {
        initializeLibNexa()
        wallyApp = CommonApp()
        val address = Pay2PubKeyTemplateDestination(ChainSelector.NEXA, UnsecuredSecret(ByteArray(32, { 1.toByte()})), 1234)

        setContent {
            ReceiveScreenContent(address, Modifier)
        }

        onNodeWithText(address.address.toString()).isDisplayed()
        onNodeWithText(address.address.toString()).performClick()
    }
}