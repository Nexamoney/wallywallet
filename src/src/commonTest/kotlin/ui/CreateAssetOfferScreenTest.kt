package ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import info.bitcoinunlimited.www.wally.Alert
import info.bitcoinunlimited.www.wally.AlertLevel
import info.bitcoinunlimited.www.wally.KotlinTarget
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.alerts
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.platform
import info.bitcoinunlimited.www.wally.ui.CreateAssetOfferScreen
import info.bitcoinunlimited.www.wally.ui.CreateAssetOfferViewModelFake
import info.bitcoinunlimited.www.wally.ui.CreateAssetOfferViewModelImpl
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.rem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CreateAssetOfferScreenTest: WallyUiTestBase()
{
    private fun snapshotAlerts(): Int = alerts.size
    private fun addedAlerts(before: Int): List<Alert> = alerts.subList(before, alerts.size).toList()

    /**
     * Verify that displayWarning(expectedMsg) was emitted by createOffer().
     *
     * Cross-platform: on iOS/JVM (hasNativeTitleBar=false) the alert is appended
     * to the in-memory `alerts` journal, so we assert delta+1 with the expected
     * level/message. On Android (hasNativeTitleBar=true) the alert is routed to
     * the native dialog via `displayAlert` and the journal stays untouched —
     * the dialog is owned by `currentActivity`, which is unobservable from a
     * unit-test context, so we can only verify the native-title-bar path was
     * taken and that nothing leaked into `alerts`.
     */
    private fun assertWarningEmitted(before: Int, expectedMsg: String)
    {
        if (platform().target == KotlinTarget.Android)
        {
            assertTrue(platform().hasNativeTitleBar, "Android should route warnings via the native title bar")
            assertEquals(0, addedAlerts(before).size, "Android must not append to the alerts journal")
        }
        else
        {
            val added = addedAlerts(before)
            assertEquals(1, added.size)
            assertEquals(AlertLevel.WARN, added.single().level)
            assertEquals(expectedMsg, added.single().msg)
        }
    }

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

    // ---------- CreateAssetOfferViewModelImpl.createOffer ----------

    @Test
    fun createOfferEmptyPriceEmitsPriceWarning()
    {
        val vm = CreateAssetOfferViewModelImpl(assetPerAccountFaker())
        vm.nexaPrice.value = ""
        vm.assetsForSale.value = "1"

        val before = snapshotAlerts()
        vm.createOffer(capdChecked = false)

        assertWarningEmitted(before, i18n(S.setTheNexaPrice))
    }

    @Test
    fun createOfferNonNumericPriceEmitsPriceWarning()
    {
        val vm = CreateAssetOfferViewModelImpl(assetPerAccountFaker())
        vm.nexaPrice.value = "not-a-number"
        vm.assetsForSale.value = "1"

        val before = snapshotAlerts()
        vm.createOffer(capdChecked = false)

        assertWarningEmitted(before, i18n(S.setTheNexaPrice))
    }

    @Test
    fun createOfferZeroPriceEmitsPriceWarning()
    {
        val vm = CreateAssetOfferViewModelImpl(assetPerAccountFaker())
        vm.nexaPrice.value = "0"
        vm.assetsForSale.value = "1"

        val before = snapshotAlerts()
        vm.createOffer(capdChecked = false)

        assertWarningEmitted(before, i18n(S.setTheNexaPrice))
    }

    @Test
    fun createOfferNegativePriceEmitsPriceWarning()
    {
        val vm = CreateAssetOfferViewModelImpl(assetPerAccountFaker())
        vm.nexaPrice.value = "-100"
        vm.assetsForSale.value = "1"

        val before = snapshotAlerts()
        vm.createOffer(capdChecked = false)

        assertWarningEmitted(before, i18n(S.setTheNexaPrice))
    }

    @Test
    fun createOfferZeroAssetAmountEmitsAssetAmountWarning()
    {
        val vm = CreateAssetOfferViewModelImpl(assetPerAccountFaker())
        vm.nexaPrice.value = "100"
        vm.assetsForSale.value = "0"

        val before = snapshotAlerts()
        vm.createOffer(capdChecked = false)

        assertWarningEmitted(before, i18n(S.setTheAssetAmount))
    }

    @Test
    fun createOfferOnUnfundedAccountDoesNotAnnounceSuccess()
    {
        // On an unfunded wallet, createOfferTx cannot build a partial funding
        // transaction, so createOffer either emits a warning/error alert (when the
        // failure surfaces as an Exception subtype) or the failure propagates
        // out (e.g. a kotlin `assert` failure in txCompleter — AssertionError is
        // not an Exception and is not caught). In neither case should the
        // "offer created" success alert fire.
        val account = wallyApp!!.newAccount("createOffer-nofund", 0U, "", ChainSelector.NEXA)!!
        setSelectedAccount(account)

        val vm = CreateAssetOfferViewModelImpl(assetPerAccountFaker())
        vm.nexaPrice.value = "100"
        vm.assetsForSale.value = "1"

        val before = snapshotAlerts()
        runCatching { vm.createOffer(capdChecked = false) }
        val added = addedAlerts(before)

        assertTrue(
          added.none { it.msg == i18n(S.offerCreated) },
          "offer-created notice must not fire on an unfunded wallet; got ${added.map { it.level to it.msg }}"
        )

        wallyApp!!.deleteAccount(account)
    }

    // ---------- CreateAssetOfferViewModelImpl.CommonWallet.createOfferTx ----------

    @Test
    fun createOfferTxOnUnfundedWalletThrowsWalletException()
    {
        // createOfferTx is a member extension function on CommonWallet, so it has to
        // be invoked inside the viewmodel receiver via `with(vm)`. A brand-new
        // account has no UTXOs, so txCompleter's FUND_GROUPS step throws a
        // WalletException (concretely, WalletNotEnoughTokenBalanceException).
        val account = wallyApp!!.newAccount("createOfferTx-nofund", 0U, "", ChainSelector.NEXA)!!
        val apc = assetPerAccountFaker()
        val vm = CreateAssetOfferViewModelImpl(apc)

        assertFails {
            with(vm) {
                account.wallet.createOfferTx(
                  gid = apc.groupInfo.groupId,
                  grpAmt = 1L,
                  nativeAmt = 100L,
                )
            }
        }

        wallyApp!!.deleteAccount(account)
    }
}
