package ui

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.ui.CreateAssetOfferScreen
import info.bitcoinunlimited.www.wally.ui.CreateAssetOfferViewModelFake
import org.nexa.libnexakotlin.rem
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CreateAssetOfferScreenTest: WallyUiTestBase()
{
    @Test
    fun createAssetOfferTest() = runComposeUiTest {
        val asset = assetPerAccountFaker()
        val viewModel = CreateAssetOfferViewModelFake(asset)
        val tokenBalance = viewModel.tokenBalance.value
        val name = viewModel.name.value

        setContent {
            CreateAssetOfferScreen(asset, viewModel)
        }
        settle()

        onNodeWithText(i18n(S.CreateAnOffer)).assertIsDisplayed()
        onNodeWithText(i18n(S.AssetAmountName) % mapOf("amount" to tokenBalance, "assetName" to name)).assertIsDisplayed()

        // Updates the amount in the asset amount field.
        val assetAmountTag = "WallyNumericInputFieldAsset"
        onNodeWithTag(assetAmountTag).assertIsDisplayed()
        onNodeWithTag(assetAmountTag).requestFocus()
        onNodeWithTag(assetAmountTag).assertIsFocused()
        onNodeWithTag(assetAmountTag).performTextClearance()
        onNodeWithTag(assetAmountTag).performTextInput("100")
        onNodeWithTag(assetAmountTag).assertTextContains("100")

        // Updates the amount in the offer price field.
        val offerAmountTag = "offerAmountInput"
        onNodeWithTag(offerAmountTag).assertIsDisplayed()
        onNodeWithTag(offerAmountTag).requestFocus()
        onNodeWithTag(offerAmountTag).assertIsFocused()
        onNodeWithTag(offerAmountTag).performTextClearance()
        onNodeWithTag(offerAmountTag).performTextInput("420000000")
        onNodeWithTag(offerAmountTag).assertTextContains("420000000")

        onNodeWithText(i18n(S.confirm)).assertIsDisplayed()
        onNodeWithText(i18n(S.confirm)).performClick()
    }

    @Test
    fun createAssetOfferTestUniqueAsset() = runComposeUiTest {
        val asset = uniqueAssetPerAccountFaker()
        val viewModel = CreateAssetOfferViewModelFake(asset)
        val tokenBalance = viewModel.tokenBalance.value
        val name = viewModel.name.value

        setContent {
            CreateAssetOfferScreen(asset, viewModel)
        }
        settle()

        onNodeWithText(i18n(S.CreateAnOffer)).assertIsDisplayed()
        onNodeWithText(i18n(S.AssetAmountName) % mapOf("amount" to tokenBalance, "assetName" to name)).assertDoesNotExist()

        // Updates the amount in the asset amount field.
        val assetAmountTag = "WallyNumericInputFieldAsset"
        onNodeWithTag(assetAmountTag).assertDoesNotExist()

        // Updates the amount in the offer price field.
        val offerAmountTag = "offerAmountInput"
        onNodeWithTag(offerAmountTag).assertIsDisplayed()
        onNodeWithTag(offerAmountTag).requestFocus()
        onNodeWithTag(offerAmountTag).assertIsFocused()
        onNodeWithTag(offerAmountTag).performTextClearance()
        onNodeWithTag(offerAmountTag).performTextInput("420000000")
        onNodeWithTag(offerAmountTag).assertTextContains("420000000")

        onNodeWithText(i18n(S.confirm)).assertIsDisplayed()
        onNodeWithText(i18n(S.confirm)).performClick()
    }
}