package ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import info.bitcoinunlimited.www.wally.ui.HomeTabRow
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.GroupAuthorityFlags
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.Pay2PubKeyTemplateDestination
import org.nexa.libnexakotlin.TokenDesc
import org.nexa.libnexakotlin.TokenGenesisInfo
import org.nexa.libnexakotlin.TransactionHistory
import org.nexa.libnexakotlin.UnsecuredSecret
import org.nexa.libnexakotlin.fromHex
import org.nexa.libnexakotlin.txFor
import org.nexa.libnexakotlin.txOutputFor
import repositories.TimeLockContractRepository
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import org.nexa.libnexakotlin.rem
import kotlin.test.Test
import kotlin.test.assertEquals

private val LogIt = GetLog("BU.wally.timeLockScreenTest")

private val TEST_CHAIN = ChainSelector.NEXATESTNET

private fun testGroup(hex: String) = GroupId(TEST_CHAIN, hex.fromHex())

private fun testBaton(hex: String, vararg capabilities: ULong) = GroupInfo(
    testGroup(hex), 0L,
    capabilities.fold(GroupAuthorityFlags.AUTHORITY) { acc, f -> acc or f },
)

private fun testAddress(seed: Int): PayAddress =
    Pay2PubKeyTemplateDestination(TEST_CHAIN, UnsecuredSecret(ByteArray(32) { seed.toByte() }), 0).address!!

/**
 * A wallet tx-history record for the vault history rows. The tx carries one
 * output of [amountSat] paying a key derived from a fixed secret, so each fake
 * gets its own idem the way real records do, with no wallet or chain in play.
 */
private fun testTxHistory(
    amountSat: Long,
    confirmedHeight: Long = -1L,
    dateMillis: Long = 0L,
): TransactionHistory
{
    val dest = Pay2PubKeyTemplateDestination(TEST_CHAIN, UnsecuredSecret(ByteArray(32) { 1 }), 0)
    val tx = txFor(TEST_CHAIN).add(txOutputFor(amountSat, dest.address!!))
    return TransactionHistory(TEST_CHAIN, tx).also {
        it.confirmedHeight = confirmedHeight
        it.date = dateMillis
    }
}

/** The account pill's contract line is where New vault lives, so tests render it the way the screen does. */
@Composable
private fun testPill(onCreate: () -> Unit = {}) =
    VaultPillContractLine(lockedSat = 0L, unitName = "tNexa", onCreate = onCreate)

/**
 * Compose UI tests for the stateless [TimeLockScreenContent] body. The screen
 * is driven through controlled callbacks (fake `onAddVault` / `onClaim`) so the
 * full create and claim card flows can be exercised without a funded wallet or
 * a live chain. Vault rows are fed as [TimeLockViewModel.TimeLockVaultUiData]
 * snapshots, exactly as the real view model emits them.
 */
@OptIn(ExperimentalTestApi::class, kotlin.time.ExperimentalTime::class)
class TimeLockScreenTest : WallyUiTestBase(openAllAccounts = false)
{
    private fun uiVault(
        name: String,
        address: PayAddress,
        unlockBlockHeight: Long,
        isUnlockable: Boolean,
        nativeBalanceSat: Long,
        claimFeeSat: Long = 215L,
        tokenBalances: Map<GroupId, Long> = emptyMap(),
        authorities: List<GroupInfo> = emptyList(),
        hasClaimTx: Boolean = false,
        daysUntilUnlock: Double? = null,
        percentLocked: Float? = null,
    ) = TimeLockViewModel.TimeLockVaultUiData(
        snapshot = TimeLockContractRepository.VaultSnapshot(
            name = name,
            address = address,
            unlockBlockHeight = unlockBlockHeight,
            isUnlockable = isUnlockable,
            blocksUntilUnlock = null,
            nativeBalanceSat = nativeBalanceSat,
            claimFeeSat = claimFeeSat,
            tokenBalances = tokenBalances,
            authorities = authorities,
            earliestDepositHeight = null,
            isBalanceLoaded = true,
            hasClaimTx = hasClaimTx,
        ),
        estimatedUnlock = null,
        daysUntilUnlock = daysUntilUnlock,
        percentLocked = percentLocked,
    )

    private fun lockedVault(name: String = "vault-1") = uiVault(
        name = name,
        address = testAddress(11),
        unlockBlockHeight = 1_100_000L,
        isUnlockable = false,
        nativeBalanceSat = 3_000L,
        daysUntilUnlock = 5.0,
        percentLocked = 0.25f,
    )

    private fun claimableVault(
        name: String = "vault-2",
        nativeBalanceSat: Long = 5_000L,   // above the 2000-sat claim headroom
        tokenBalances: Map<GroupId, Long> = emptyMap(),
        authorities: List<GroupInfo> = emptyList(),
        hasClaimTx: Boolean = false,
    ) = uiVault(
        name = name,
        address = testAddress(12),
        unlockBlockHeight = 1_000_000L,
        isUnlockable = true,
        nativeBalanceSat = nativeBalanceSat,
        tokenBalances = tokenBalances,
        authorities = authorities,
        hasClaimTx = hasClaimTx,
        percentLocked = 1.0f,
    )

    @Test
    fun overviewShowsHeaderAndCreate()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                TimeLockScreenContent(
                    vaults = emptyList(),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill() },
                )
            }
            settle()
            // Create lives on the account pill's contract line; the empty list explains what a vault is.
            onNodeWithText(i18n(S.tlvNewVault)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvExplainer)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvNoVaultsYet)).assertIsDisplayed()
        }
    }

    @Test
    fun emptyLoadedListShowsCreatePrompt()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                TimeLockScreenContent(
                    vaults = emptyList(),
                    isLoading = false,
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill() },
                )
            }
            settle()
            // Loaded with nothing: invite the user to create a vault, and
            // do not show the loading affordance.
            onNodeWithText(i18n(S.tlvNoVaultsYet)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvLoadingVaults)).assertDoesNotExist()
        }
    }

    @Test
    fun emptyLoadingListShowsLoadingIndicator()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                TimeLockScreenContent(
                    vaults = emptyList(),
                    isLoading = true,
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill() },
                )
            }
            settle()
            // Still loading: show the spinner-plus-text, not the "no vaults"
            // prompt (which would wrongly read as "you have none").
            onNodeWithText(i18n(S.tlvLoadingVaults)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvNoVaultsYet)).assertDoesNotExist()
        }
    }

    @Test
    fun loadedListWithVaultsHidesEmptyAndLoadingStates()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                TimeLockScreenContent(
                    vaults = listOf(lockedVault()),
                    isLoading = false,
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill() },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvNoVaultsYet)).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvLoadingVaults)).assertDoesNotExist()
            // The master entry card carries the vault's name, balance, and a
            // grouped-digits unlock status line. A whole amount carries no decimals.
            onNodeWithText("vault-1").assertIsDisplayed()
            onNodeWithText("30").assertIsDisplayed()
            onNodeWithText(i18n(S.tlvUnlocksBlock) % mapOf("num" to "1,100,000")).assertIsDisplayed()
        }
    }

    @Test
    fun vaultCardShowsTokensAndBatons()
    {
        val tokenGroup = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val batonGroup = "1122334455667788990011223344556677889900112233445566778899001122"
        val vault = claimableVault(
            tokenBalances = mapOf(testGroup(tokenGroup) to 42L),
            authorities = listOf(
                testBaton(batonGroup, GroupAuthorityFlags.MINT, GroupAuthorityFlags.MELT),
            ),
        )
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(vault),
                    isLoading = false,
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            // The master entry carries the holdings as chips: the token's amount with its
            // symbol (the shortened group id until the asset resolves), and the baton's.
            onNodeWithText("42 aabbccddee…8899").assertIsDisplayed()
            onNodeWithText("1122334455…1122").assertIsDisplayed()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            // The detail pill carries the same chips.
            onNodeWithText("42 aabbccddee…8899").assertIsDisplayed()
            onNodeWithText("1122334455…1122").assertIsDisplayed()
        }
    }

    @Test
    fun vaultCardScalesTokenAmountByDecimalPlaces()
    {
        val tokenGroup = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val asset = createAssetInfo("Agnar", null)
        asset.tokenInfo = TokenDesc(
            "AGNAR", genesisInfo = TokenGenesisInfo(
                document_hash = null,
                document_url = null,
                height = 1000,
                name = "AGNAR",
                ticker = "AGNAR",
                token_id_hex = tokenGroup,
                txid = "00",
                txidem = "00",
                decimal_places = 2,
            )
        )
        val vault = claimableVault(tokenBalances = mapOf(testGroup(tokenGroup) to 100L))
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(vault),
                    isLoading = false,
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill() },
                    assets = mapOf(testGroup(tokenGroup) to asset),
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            // 100 finest units at 2 decimal places is 1, not the raw 100.
            onNodeWithText("1 aabbccddee…8899").assertIsDisplayed()
            onNodeWithText("100 aabbccddee…8899").assertDoesNotExist()
        }
    }

    @Test
    fun vaultHistoryShowsTokenAndBatonMovements()
    {
        val tokenGroup = "0e38fbf897aabbccddeeff0011223344556677889900aabbccddeeff0011a184"
        val batonGroup = "1122334455667788990011223344556677889900112233445566778899001122"
        val txs = listOf(
            // Token-only deposit: no native value moved (dust rides the token
            // output), so the row must show the token, not a "+0.00" headline.
            TimeLockContractRepository.VaultTx(
                history = testTxHistory(546L, confirmedHeight = 1_000_000L, dateMillis = 3_000L),
                kind = TimeLockContractRepository.VaultTxKind.Deposit,
                amountSat = 0L,
                tokenMovements = mapOf(testGroup(tokenGroup) to 1L),
                authorities = emptyList(),
            ),
            // Baton deposit.
            TimeLockContractRepository.VaultTx(
                history = testTxHistory(547L, confirmedHeight = 1_000_000L, dateMillis = 2_000L),
                kind = TimeLockContractRepository.VaultTxKind.Deposit,
                amountSat = 0L,
                tokenMovements = emptyMap(),
                authorities = listOf(
                    testBaton(batonGroup, GroupAuthorityFlags.MINT, GroupAuthorityFlags.BATON),
                ),
            ),
            // Native funding deposit: the value sent shows, not a fee.
            TimeLockContractRepository.VaultTx(
                history = testTxHistory(10_000L, confirmedHeight = 1_000_000L, dateMillis = 1_000L),
                kind = TimeLockContractRepository.VaultTxKind.Deposit,
                amountSat = 10_000L,
                tokenMovements = emptyMap(),
                authorities = emptyList(),
            ),
        )
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                VaultTxListScreen(
                    unitName = "tNexa",
                    transactions = txs,
                )
            }
            settle()
            // Token movement: one chip carrying the amount and the identifier.
            // With no account in scope it falls back to the shortened group id
            // (the image/name path is exercised live via MCP).
            onNodeWithText("+1 0e38fbf897…a184").assertIsDisplayed()
            // Baton movement line: capabilities + shortened group id.
            onNodeWithText("+" + (i18n(S.tlvAuthorityMovement) % mapOf("caps" to i18n(S.tlvCapMint) + "·" + i18n(S.tlvCapBaton), "token" to "1122334455…1122"))).assertIsDisplayed()
            // The native funding deposit shows the value sent (not the fee); the unit
            // rides beside the amount as its own label.
            onNodeWithText("+100").assertIsDisplayed()
            // The token-only deposit must NOT render a misleading native amount.
            onNodeWithText("+0").assertDoesNotExist()
        }
    }

    @Test
    fun createCardDefaultsAmountToTwentyNexa()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var create by remember { mutableStateOf(false) }
                TimeLockScreenContent(
                    vaults = emptyList(),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill { create = true } },
                    createOpen = create,
                    onOpenCreate = { create = true },
                    onCloseCreate = { create = false },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            // Create is its own child screen: the master list is gone.
            onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).assertIsDisplayed()
            // The pill (and its create action) is pinned to every level; it is the LIST that is gone.
            onNodeWithText(i18n(S.tlvNoVaultsYet)).assertDoesNotExist()
            // The amount field pre-fills with the 20-NEXA minimum funding floor.
            onNodeWithTag("VaultAmountInput").assertTextEquals("20")
        }
    }

    @Test
    fun createFlowReachesSuccessCard()
    {
        var requested: Pair<Long, Long>? = null
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var create by remember { mutableStateOf(false) }
                TimeLockScreenContent(
                    vaults = emptyList(),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { block, amount ->
                        requested = block to amount
                        TimeLockContractRepository.VaultCreated(
                            name = "vault-1",
                            address = testAddress(13),
                            fundingTxIdem = Hash256("ab".repeat(32)),
                        )
                    },
                    pill = { testPill { create = true } },
                    createOpen = create,
                    onOpenCreate = { create = true },
                    onCloseCreate = { create = false },
                )
            }
            settle()

            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).assertIsDisplayed()

            // Amount pre-fills with the 20-NEXA minimum; clear before entering a
            // custom value so the typed text doesn't append to the default.
            onNodeWithTag("VaultAmountInput").performTextClearance()
            onNodeWithTag("VaultAmountInput").performTextInput("30")
            onNodeWithTag("VaultBlockInput").performTextInput("1100000")
            settle()

            onNodeWithText(i18n(S.tlvReview)).performClick()
            settle()
            // The confirm card is headed by the name the vault will carry, and the
            // Pay to row repeats it, so the name is on screen twice.
            onAllNodesWithText(i18n(S.tlvNewTimeLockVault)).onFirst().assertIsDisplayed()

            onNodeWithText(i18n(S.tlvLock)).performClick()
            settle()
            // Success card after a successful create, still on the child screen: it
            // is headed by the vault's own name.
            onNodeWithText("vault-1").assertIsDisplayed()
            onNodeWithText(i18n(S.tlvFundsOnTheirWay)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvBlockNum) % mapOf("num" to "1,100,000")).assertIsDisplayed()

            // Done closes the child screen back to the master list.
            onNodeWithText(i18n(S.tlvDone)).performClick()
            settle()
            onNodeWithText("vault-1").assertDoesNotExist()
            onNodeWithText(i18n(S.tlvNewVault)).assertIsDisplayed()
        }
        check(requested == 1_100_000L to 3_000L) { "onAddVault got unexpected args: $requested" }
    }

    @Test
    fun failedCreateReturnsToOverview()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var create by remember { mutableStateOf(false) }
                TimeLockScreenContent(
                    vaults = emptyList(),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },   // funding failed
                    pill = { testPill { create = true } },
                    createOpen = create,
                    onOpenCreate = { create = true },
                    onCloseCreate = { create = false },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onNodeWithTag("VaultAmountInput").performTextClearance()
            onNodeWithTag("VaultAmountInput").performTextInput("30")
            onNodeWithTag("VaultBlockInput").performTextInput("1100000")
            settle()
            onNodeWithText(i18n(S.tlvReview)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvLock)).performClick()
            settle()
            // No success card; the child closes back to the overview (header +
            // New vault).
            onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvNewVault)).assertIsDisplayed()
            // The flow state was reset too: reopening lands on a fresh form,
            // not the dead confirm card.
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvNewTimeLockVault)).assertDoesNotExist()
        }
    }

    /**
     * Backing out of the create child mid-flow resumes where the user left
     * off — but a Success card dismissed with BACK is dropped: "New vault"
     * always lands on the form, never on a stale receipt.
     */
    @Test
    fun createFlowResumesAfterBackButDropsStaleSuccess()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            var create by mutableStateOf(false)
            setContent {
                TimeLockScreenContent(
                    vaults = emptyList(),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ ->
                        TimeLockContractRepository.VaultCreated(
                            name = "vault-1",
                            address = testAddress(13),
                            fundingTxIdem = Hash256("ab".repeat(32)),
                        )
                    },
                    pill = { testPill { create = true } },
                    createOpen = create,
                    onOpenCreate = { create = true },
                    onCloseCreate = { create = false },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onNodeWithTag("VaultAmountInput").performTextClearance()
            onNodeWithTag("VaultAmountInput").performTextInput("30")
            onNodeWithTag("VaultBlockInput").performTextInput("1100000")
            settle()
            onNodeWithText(i18n(S.tlvReview)).performClick()
            settle()
            onAllNodesWithText(i18n(S.tlvNewTimeLockVault)).onFirst().assertIsDisplayed()

            // Hardware BACK pops the child mid-confirm; reopening resumes it.
            create = false
            settle()
            onNodeWithText(i18n(S.tlvNewVault)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onAllNodesWithText(i18n(S.tlvNewTimeLockVault)).onFirst().assertIsDisplayed()

            // Complete the create, then BACK-dismiss the Success card.
            onNodeWithText(i18n(S.tlvLock)).performClick()
            settle()
            onNodeWithText("vault-1").assertIsDisplayed()
            create = false
            settle()
            // "New vault" opens a fresh form, not the old receipt.
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onNodeWithText("vault-1").assertDoesNotExist()
            onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).assertIsDisplayed()
        }
    }

    /**
     * A claim is scoped to the vault the user tapped, so the card must show that
     * vault's coin alone and never mention a matured sibling.
     */
    @Test
    fun claimCardShowsOnlyTheTappedVault()
    {
        val target = claimableVault(name = "vault-2", nativeBalanceSat = 5_000L)
        val sibling = claimableVault(name = "vault-3", nativeBalanceSat = 7_000L)
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(target, sibling),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    nextReceiveAddress = { testAddress(14) },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText("vault-2").assertIsDisplayed()
            onNodeWithText("vault-3").assertDoesNotExist()
            // Whole amounts carry no decimals, and the unit rides beside them as its own label.
            onAllNodesWithText("50").onFirst().assertIsDisplayed()
            onAllNodesWithText("70").assertCountEquals(0)
            // The card no longer restates a payout, so the header amount is what proves the scoping:
            // vault-2's 50 is there and the sibling's 70 is nowhere on the card.
        }
    }

    /**
     * A matured vault funded at the minimum with tokens deposited to it used
     * to trip a UI-only headroom reserve the repository no longer enforces,
     * permanently greying out Confirm. Only an empty vault blocks the claim.
     */
    @Test
    fun smallVaultWithTokensStaysClaimable()
    {
        val tokenGroup = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val vault = claimableVault(nativeBalanceSat = 2_000L,
            tokenBalances = mapOf(testGroup(tokenGroup) to 5L),
        )
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(vault),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    nextReceiveAddress = { testAddress(14) },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaim)).assertIsEnabled()
            onNodeWithText(i18n(S.tlvNothingToClaim)).assertDoesNotExist()
        }
    }

    /** Back from the confirm card returns to a form that kept what was typed. */
    @Test
    fun backFromConfirmKeepsTheForm()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var create by remember { mutableStateOf(false) }
                TimeLockScreenContent(
                    vaults = emptyList(),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill { create = true } },
                    createOpen = create,
                    onOpenCreate = { create = true },
                    onCloseCreate = { create = false },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onNodeWithTag("VaultAmountInput").performTextClearance()
            onNodeWithTag("VaultAmountInput").performTextInput("37")
            onNodeWithTag("VaultBlockInput").performTextInput("1100000")
            settle()
            onNodeWithText(i18n(S.tlvReview)).performClick()
            settle()
            onAllNodesWithText(i18n(S.tlvNewTimeLockVault)).onFirst().assertIsDisplayed()
            onNodeWithText(i18n(S.Back)).performClick()
            settle()
            onNodeWithTag("VaultAmountInput").assertTextEquals("37")
            onNodeWithTag("VaultBlockInput").assertTextEquals("1100000")
        }
    }

    /** Cancel on the create form closes the child and resets the flow. */
    @Test
    fun createCancelClosesChildAndResets()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var create by remember { mutableStateOf(false) }
                TimeLockScreenContent(
                    vaults = emptyList(),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill { create = true } },
                    createOpen = create,
                    onOpenCreate = { create = true },
                    onCloseCreate = { create = false },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).assertIsDisplayed()
            onNodeWithText(i18n(S.SendCancel)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvNewVault)).assertIsDisplayed()
        }
    }

    @Test
    fun blockHeightOverMaxBlocksReview()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var create by remember { mutableStateOf(false) }
                TimeLockScreenContent(
                    vaults = emptyList(),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill { create = true } },
                    createOpen = create,
                    onOpenCreate = { create = true },
                    onCloseCreate = { create = false },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvNewVault)).performClick()
            settle()
            onNodeWithTag("VaultAmountInput").performTextClearance()
            onNodeWithTag("VaultAmountInput").performTextInput("30")

            val max = TimeLockContractRepository.MAX_UNLOCK_BLOCK_HEIGHT
            // One over the nLockTime height/time split: a full node would read
            // this as a Unix epoch time, so the form refuses it and disables
            // Review rather than letting the library throw on create.
            onNodeWithTag("VaultBlockInput").performTextInput((max + 1).toString())
            settle()
            onNodeWithText(i18n(S.tlvMaxBlockHeight) % mapOf("num" to max.toString())).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvReview)).assertIsNotEnabled()

            // The boundary value itself is accepted: hint clears, Review enables.
            onNodeWithTag("VaultBlockInput").performTextClearance()
            onNodeWithTag("VaultBlockInput").performTextInput(max.toString())
            settle()
            onNodeWithText(i18n(S.tlvMaxBlockHeight) % mapOf("num" to max.toString())).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvReview)).assertIsEnabled()
        }
    }

    @Test
    fun claimableVaultRunsClaimFlowToClaimedCard()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(claimableVault()),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    nextReceiveAddress = { testAddress(14) },
                    onClaim = { _, _ ->
                        TimeLockContractRepository.ClaimResult(
                            txIdem = Hash256("cd".repeat(32)),
                            payoutSat = 900L,
                            feeSat = 200L,
                        )
                    },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            // Master–detail: the entry card routes to the detail level, where
            // the contract pill's Claim opens the claim card.
            onNodeWithText(i18n(S.tlvUnlockedReadyToClaim)).assertIsDisplayed()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaimTo).uppercase()).assertIsDisplayed()
            // On the claim card the same "Claim" label is the confirm action.
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaimed)).assertIsDisplayed()
        }
    }

    @Test
    fun claimCardShowsTokensAndBatons()
    {
        val tokenGroup = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val batonGroup = "1122334455667788990011223344556677889900112233445566778899001122"
        // The default 5000-sat balance carries the sweep fee, so the card renders
        // the full claim rather than the "needs coin for the fee" path.
        val vault = claimableVault(
            tokenBalances = mapOf(testGroup(tokenGroup) to 7L),
            authorities = listOf(
                testBaton(batonGroup, GroupAuthorityFlags.MINT, GroupAuthorityFlags.BATON),
            ),
        )
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(vault),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    nextReceiveAddress = { testAddress(14) },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaimTo).uppercase()).assertIsDisplayed()
            // The claim card's pill header carries everything the sweep returns,
            // not just the native coin: the token chip and the baton chip. Each
            // appears exactly once — the window below never repeats the holdings
            // the header already shows.
            onAllNodesWithText("7 aabbccddee…8899").assertCountEquals(1)
            onAllNodesWithText("1122334455…1122").assertCountEquals(1)
        }
    }

    /**
     * A token-only vault (assets but no coin) cannot pay a sweep fee at all,
     * so the pill says so and the Claim action refuses rather than promising a
     * payout the network would reject.
     */
    @Test
    fun tokenOnlyVaultWithNoCoinCannotClaim()
    {
        val tokenGroup = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val vault = claimableVault(nativeBalanceSat = 0L,
            tokenBalances = mapOf(testGroup(tokenGroup) to 9L),
        )
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(vault),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    nextReceiveAddress = { testAddress(14) },
                    onClaim = { _, _ -> error("a coinless vault must not be claimed") },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvUnlockedNeedsFeeCoin)).assertIsDisplayed()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            onNodeWithText(i18n(S.tlvUnlockedNeedsFeeCoin)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvClaim)).assertIsNotEnabled()
        }
    }

    @Test
    fun lockedVaultClaimIsDisabled()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(lockedVault()),
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    onClaim = { _, _ -> error("claim must not fire for a locked vault") },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithTag("VaultEntry-vault-1").performClick()
            settle()
            // The Claim affordance is present but disabled for a locked vault,
            // so tapping it must NOT open the claim card.
            onNodeWithText(i18n(S.tlvClaim)).assertIsNotEnabled()
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaimTo).uppercase()).assertDoesNotExist()
        }
    }

    /**
     * A claimed vault that the sweep emptied reads "Claimed" and drops the
     * Claim/Re-claim action — but its address stays visible in the detail
     * facts card. Product-owner ruling: showing the vault address for claimed
     * vaults is INTENDED behavior (the address is a permanent record of the
     * contract), so this test asserts the "DEPOSIT ADDRESS" row is present.
     * Only the QR affordance is dropped for a finished vault.
     */
    @Test
    fun claimedAndEmptyVaultShowsClaimedStatusAndHidesClaimButton()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    // A claimed vault that the sweep emptied — nothing left at
                    // the address — is "done": it shows Claimed with no Claim or
                    // Re-claim button.
                    vaults = listOf(claimableVault(nativeBalanceSat = 0L, hasClaimTx = true)),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    onClaim = { _, _ -> error("an emptied claimed vault must not be claimed") },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            // The entry reads as Claimed; the detail says the vault is empty and
            // drops the Claim button so it can't be claimed again. Neither is
            // advertised as "Unlocked" any more.
            onNodeWithText(i18n(S.tlvClaimed)).assertIsDisplayed()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            onNodeWithText(i18n(S.tlvVaultEmptied)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvClaim)).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvReclaim)).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvUnlockedWaitingDeposit)).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvUnlockedNeedsFeeCoin)).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvUnlockedReadyToClaim)).assertDoesNotExist()
            // The address row stays visible for a claimed vault (intended:
            // it is the permanent record of the contract); only the QR
            // affordance is dropped for a finished vault. Tapping the row
            // still reveals the full address for reading/copying.
            onNodeWithText(i18n(S.tlvDepositAddress).uppercase()).assertIsDisplayed()
            onNodeWithContentDescription(i18n(S.tlvQr)).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvDepositAddress).uppercase()).performClick()
            settle()
            onNodeWithText(testAddress(12).toString()).assertIsDisplayed()
            onNodeWithText(i18n(S.CopyAddress)).assertIsDisplayed()
            onNodeWithContentDescription(i18n(S.tlvQr)).assertDoesNotExist()
        }
    }

    @Test
    fun claimedVaultWithResidualBalanceOffersReclaim()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    // A claimed vault that still holds funds — e.g. a deposit, or
                    // a token/baton UTXO that landed or confirmed only after the
                    // sweep. It still reads "Claimed", but a "Re-claim" button
                    // offers the same sweep so the stragglers can be pulled out.
                    vaults = listOf(claimableVault(nativeBalanceSat = 5_000L, hasClaimTx = true)),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    nextReceiveAddress = { testAddress(14) },
                    onClaim = { _, _ ->
                        TimeLockContractRepository.ClaimResult(
                            txIdem = Hash256("ef".repeat(32)),
                            payoutSat = 3_000L,
                            feeSat = 200L,
                        )
                    },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            // Still marked Claimed, but the residual balance surfaces a Re-claim
            // button in place of the (absent) plain Claim button.
            onNodeWithText(i18n(S.tlvClaimedFundsArrivedSince)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvClaim)).assertDoesNotExist()
            onNodeWithText(i18n(S.tlvReclaim)).assertIsDisplayed()
            // Re-claim runs the very same sweep flow: it opens the claim card and
            // confirms through to the claimed receipt.
            onNodeWithText(i18n(S.tlvReclaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaimTo).uppercase()).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaimed)).assertIsDisplayed()
        }
    }

    /**
     * The post-sweep refresh removes a fully-claimed vault from the snapshot
     * while the user is still looking at its receipt. The claimed card must
     * survive that removal, and its Done must close the detail level.
     */
    @Test
    fun claimedCardSurvivesVaultVanishingFromSnapshot()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            var vaults by mutableStateOf(listOf(claimableVault()))
            var closed = false
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = vaults,
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    nextReceiveAddress = { testAddress(14) },
                    onClaim = { _, _ ->
                        TimeLockContractRepository.ClaimResult(
                            txIdem = Hash256("ab".repeat(32)),
                            payoutSat = 900L,
                            feeSat = 200L,
                        )
                    },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = {
                        detail = null
                        closed = true
                    },
                )
            }
            settle()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaim)).performClick()
            settle()
            onNodeWithText(i18n(S.tlvClaimed)).assertIsDisplayed()
            // The refresh drops the vault from the snapshot: the receipt stays.
            vaults = emptyList()
            settle()
            onNodeWithText(i18n(S.tlvClaimed)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvDone)).performClick()
            settle()
            check(closed) { "Done on the fallback receipt must close the detail" }
            onNodeWithText(i18n(S.tlvNoVaultsYet)).assertIsDisplayed()
        }
    }

    /**
     * The deposit row always shows the full, unabbreviated address (address ellipses
     * have been exploited in other wallets); tapping it toggles the QR + copy reveal.
     */
    @Test
    fun factsCardTogglesDepositQr()
    {
        val vault = claimableVault()
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(vault),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            // Collapsed: the full address is already readable; the QR/copy reveal is not open.
            onNodeWithText(vault.snapshot.address!!.toString()).assertIsDisplayed()
            onNodeWithText(i18n(S.CopyAddress)).assertDoesNotExist()
            onNodeWithContentDescription(i18n(S.tlvQr)).performClick()
            settle()
            // Revealed: the copy affordance appears; the toggle flips to close.
            onNodeWithText(i18n(S.CopyAddress)).assertIsDisplayed()
            onNodeWithContentDescription(i18n(S.tlvCloseQr)).performClick()
            settle()
            onNodeWithText(i18n(S.CopyAddress)).assertDoesNotExist()
            onNodeWithText(vault.snapshot.address!!.toString()).assertIsDisplayed()
        }
    }

    /**
     * A matured vault with nothing in it advertises "waiting for a deposit"
     * and disables the Claim action — there is nothing to sweep.
     */
    @Test
    fun claimableEmptyVaultDisablesClaim()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(claimableVault(nativeBalanceSat = 0L)),
                    currentBlockHeight = 1_000_050L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    onClaim = { _, _ -> error("an empty vault must not be claimable") },
                    pill = { testPill() },
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvUnlockedWaitingDeposit)).assertIsDisplayed()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            onNodeWithText(i18n(S.tlvUnlockedWaitingDeposit)).assertIsDisplayed()
            onNodeWithText(i18n(S.tlvClaim)).assertIsNotEnabled()
        }
    }

    /** Stand-in for the home header (pill + tab row) that TimeLockScreen passes through the pill slot. */
    private val headerMarker: @Composable () -> Unit = {
        Text("home header", modifier = Modifier.testTag("HomeHeaderSlot"))
    }

    /** The header slot stays pinned when drilling from the master list into a vault detail. */
    @Test
    fun homeHeaderShownOnMasterAndDetail()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                var detail by remember { mutableStateOf<String?>(null) }
                TimeLockScreenContent(
                    vaults = listOf(claimableVault()),
                    isLoading = false,
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = headerMarker,
                    detailVaultName = detail,
                    onOpenVault = { detail = it },
                    onCloseDetail = { detail = null },
                )
            }
            settle()
            onNodeWithTag("HomeHeaderSlot").assertIsDisplayed()
            onNodeWithTag("VaultEntry-vault-2").performClick()
            settle()
            // Now at the detail level (the master list is gone), header still pinned.
            onNodeWithTag("VaultEntry-vault-2").assertDoesNotExist()
            onNodeWithTag("HomeHeaderSlot").assertIsDisplayed()
        }
    }

    /** Detail of a vault the account no longer has (e.g. right after an account switch) keeps the header. */
    @Test
    fun homeHeaderShownOnDetailOfVanishedVault()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                TimeLockScreenContent(
                    vaults = emptyList(),
                    isLoading = false,
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = headerMarker,
                    detailVaultName = "gone",
                )
            }
            settle()
            onNodeWithTag("HomeHeaderSlot").assertIsDisplayed()
        }
    }

    @Test
    fun homeHeaderShownOnCreate()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                TimeLockScreenContent(
                    vaults = emptyList(),
                    isLoading = false,
                    currentBlockHeight = 1_000_000L,
                    unitName = "tNexa",
                    onAddVault = { _, _ -> null },
                    pill = headerMarker,
                    createOpen = true,
                )
            }
            settle()
            onNodeWithText(i18n(S.tlvCreateTitle).uppercase()).assertIsDisplayed()
            onNodeWithTag("HomeHeaderSlot").assertIsDisplayed()
        }
    }

    @Test
    fun homeHeaderShownOnVaultTxList()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            setContent {
                VaultTxListScreen(
                    unitName = "tNexa",
                    transactions = emptyList(),
                    pill = headerMarker,
                )
            }
            settle()
            onNodeWithTag("HomeHeaderSlot").assertIsDisplayed()
        }
    }

    /** The shared home tab row marks the given tab selected and reports taps by index. */
    @Test
    fun homeTabRowReportsTapsAndSelection()
    {
        androidx.compose.ui.test.v2.runComposeUiTest {
            var picked = -1
            setContent {
                HomeTabRow(selectedIndex = 2) { picked = it }
            }
            settle()
            onNodeWithContentDescription("Accounts").assertIsDisplayed().assertIsNotSelected()
            onNodeWithContentDescription("Transactions").assertIsDisplayed().assertIsNotSelected()
            onNodeWithContentDescription("Contracts").assertIsDisplayed().assertIsSelected()
            onNodeWithContentDescription("Transactions").performClick()
            assertEquals(1, picked)
            onNodeWithContentDescription("Accounts").performClick()
            assertEquals(0, picked)
            onNodeWithContentDescription("Contracts").performClick()
            assertEquals(2, picked)
        }
    }

    /** The countdown label uses the finest fitting unit and pluralizes correctly (never "1 days left"). */
    @Test
    fun timeLeftTextPicksFinestUnit()
    {
        assertEquals(i18n(S.tlvDaysLeft) % mapOf("num" to "3"), timeLeftText(2.5))
        // Within 24 hours the label switches to hours (24 h inclusive), within the hour to minutes.
        assertEquals(i18n(S.tlvHoursLeft) % mapOf("num" to "24"), timeLeftText(1.0))
        assertEquals(i18n(S.tlvHoursLeft) % mapOf("num" to "12"), timeLeftText(0.5))
        assertEquals(i18n(S.tlvOneHourLeft), timeLeftText(60.0 / 1440.0))
        assertEquals(i18n(S.tlvMinutesLeft) % mapOf("num" to "59"), timeLeftText(59.0 / 1440.0))
        assertEquals(i18n(S.tlvOneMinuteLeft), timeLeftText(0.5 / 1440.0))
    }

    /** The elapsed percent renders with one decimal so short lock windows visibly move. */
    @Test
    fun pct1RendersOneDecimal()
    {
        assertEquals("0.0", pct1(0f))
        assertEquals("10.4", pct1(0.104f))
        assertEquals("0.1", pct1(0.0013f))
        assertEquals("100.0", pct1(1f))
    }
}
