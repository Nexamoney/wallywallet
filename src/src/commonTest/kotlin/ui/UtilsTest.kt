package ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.AssetOffer
import kotlinx.coroutines.flow.MutableStateFlow
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import org.nexa.assets.AssetInfo
import org.nexa.assets.AssetPerAccount
import org.nexa.assets.NexaNFTv2
import org.nexa.libnexakotlin.Bip44Wallet
import org.nexa.libnexakotlin.Blockchain
import org.nexa.libnexakotlin.Callbacker
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.CnxnMgr
import org.nexa.libnexakotlin.DecimalFormat
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.Pay2PubKeyTemplateDestination
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.PayDestination
import org.nexa.libnexakotlin.UnsecuredSecret
import org.nexa.libnexakotlin.WalletDatabase
import org.nexa.threads.iMutex
import org.nexa.libnexakotlin.iTransaction
import org.nexa.libnexakotlin.txFor
import org.nexa.threads.millisleep
import kotlin.test.Test

private val LogIt = GetLog("wally.test")

val NiftyArtTandC_1_0 = """NiftyArt Terms and Conditions V1.0
    
 Definitions:
    NiftyArt NFT: A unique piece of data that is described by a record in a blockchain.  This record contains a probabilistically unique identifier of a data file containing and/or describing the represented Work.
    Owner/Ownership: (of the NiftyArt NFT)  Modifying, destroying, or transferring an NiftyArt NFT record (called a transaction) is defined by rules specified by the blockchain and by each NiftyArt NFT record.  Successfully following these rules requires pieces of data that are provided as part of the transaction.  Ownership of an NiftyArt NFT is defined as possessing the ability to provide the data required to confirm a transaction on the blockchain.  Proving ownership occurs solely by one of two mechanisms.  First, by executing and confirming said transaction on the blockchain.  This confirms Ownership for the exact duration of the transaction.  Second, by producing a unique transaction upon demand with the correct required data but that is not admissible onto the blockchain for some other reason.  This confirms Ownership until the NFT blockchain record is modified or destroyed.  Every use of the word 'Owner' in these Terms and Conditions implies cryptographic verification of ownership as described.
    Creator: (of the NiftyArt NFT) The entity that originally created the NiftyArt NFT on the blockchain.
    Work: The artwork referenced by this NiftyArt NFT.  The Work may be fully embodied within files named 'public' and/or 'private'.  Additionally, the Work may include a physical and/or external components as specified by the 'info' field within the 'info.json' file.
    Owner Work:  The portion of the Work visible only by the Owner, as described below.
    Public Work:  The portion of the Work visible to anyone, as authorized by the Owner and these Terms and Conditions.
    Service: A third party entity that facilitates the use, inclusion, participation, management, storage, display or trade of NiftyArt NFTs.
   
 Inseparability of Work from the NiftyArt NFT:
    Ownership of this NiftyArt NFT confers all licenses to the Work described herein.  Transfer of this NiftyArt NFT transfers the same.  The licenses described below are forever conferred by and inseparable from Ownership of this NiftyArt NFT.
    The Owner does NOT have the right to create or enter into any contract, agreement or document, electronic, physical or otherwise that changes or overrides the NiftyArt NFT's licenses to the Work as described herein.
 
Visibility of the Work:
   Third parties may reproduce and publish the media files located within this NiftyArt NFT prefixed by 'cardf' and 'cardb' (with various suffixes and extensions) and the data within the 'info.json' file within the context of identifying this NiftyArt NFT.  The fair use of the title and author of a book shall act as precedent for when this data may be published.
   If media files or directories prefixed by 'owner' (with various suffixes and extensions) exist, this data is the Owner Work.   No Service may use, copy, display, or publish this file except to a proven current Owner.  Services that manage, store, use, or trade NiftyArt NFTs may store this file on behalf of and for sole use by the Owner.
   If media files or directories prefixed by 'public' (with various suffixes and extensions) exist, this data is the Public Work.  'cardf' and 'cardb' prefixed files are also within the Public Work.  Control of the Public Work is specific to each NFT and described in the following sections.

Creator Copyright Ownership:
  The Creator of the NiftyArt NFT asserts that they had sole ownership of exclusive copyright to the Work, had the right to transfer that ownership, and that the creation of this NiftyArt NFT inseparably binds the copyright and/or licenses described herein to Ownership of this NiftyArt NFT.
  
"""

val LicenseToPublish = "The Owner of the NiftyArt NFT that includes this notice is hereby granted an unlimited, worldwide license to use, copy, display and publish the Public Work described by the NiftyArt NFT for personal or commercial use.  This right may not be delegated.  This right is not exclusive.  Upon transfer of this NiftyArt NFT, the previous Owner loses all rights to reproduce and publish the public Work.  The public Work must be removed from all locations under the control of the previous Owner, including but not limited to web sites, software, and mobile/tablet applications."

val beaverNFT = "Nexa Beef Beaver is the fearless Beaver who has no issues starting beefs with other coins and thinks Nexa is the best. Keeps stacking it under his dams he’s building and is not selling.  Lives all across Canada, and knows Canada is the best country. Believes in self-sufficiency, prepping, hunting, camping, swimming and outdoors life. Happy to tell the world about his domain."

@OptIn(ExperimentalTestApi::class)
class UtilsTest:WallyUiTestBase()
{
    val cs = ChainSelector.NEXA

    @Test
    fun htmlOrTextDisplayTest()
    {
        runComposeUiTest {
            val viewModelStoreOwner = object : ViewModelStoreOwner
            {
                override val viewModelStore = ViewModelStore()
            }

            setContent {
                CompositionLocalProvider(
                  LocalViewModelStoreOwner provides viewModelStoreOwner
                ) {

                    Column(modifier = Modifier.fillMaxWidth(0.95f).verticalScroll(rememberScrollState())) {
                        Text("THIS IS A MANUAL VISUAL TEST.\nScroll down and check that the following text looks ok.", Modifier.background(Color.Red))
                        Text("Text test:")
                        impreciseDisplayHtml("test of normal text, includingline breaks. HERE\n and again HERE\n and HERE\ndone.", textAlign = TextAlign.Justify)
                        Spacer(Modifier.height(8.dp).fillMaxWidth().background(Color.Blue))
                        Text("HTML Test:")
                        impreciseDisplayHtml("<body><p>html test of normal text CR HERE\n and html para end HERE</p>, including CR HERE\n p and line breaks.</body>", textAlign = TextAlign.Justify)

                        Spacer(Modifier.height(8.dp).fillMaxWidth().background(Color.Blue))
                        Text("Typical Legalese:")
                        impreciseDisplayHtml(NiftyArtTandC_1_0 + LicenseToPublish, textAlign = TextAlign.Justify)

                        Spacer(Modifier.height(8.dp).fillMaxWidth().background(Color.Blue))
                        Text( "Example NFT info:")
                        impreciseDisplayHtml(beaverNFT, textAlign = TextAlign.Justify)
                        Spacer(Modifier.height(8.dp).fillMaxWidth().background(Color.Blue))
                    }
                }
            }
            settle()
            millisleep(15000U)
            settle()
        }
    }
}

fun assetPerAccountFaker(): AssetPerAccount
{
    val groupIdData = ByteArray(520, { it.toByte() })
    val groupId = GroupId(ChainSelector.NEXA, groupIdData)
    val assetInfo = AssetInfo(groupId)
    assetInfo.name = "mockAssetName"
    assetInfo.docUrl = "mock.pages.dev"
    val title = "title"
    val series = "series"
    assetInfo.nft = NexaNFTv2("niftyVer", title, series, "author", listOf(), "appUri", "info")
    val assetAmount = 200L
    val groupInfo = GroupInfo(groupId, assetAmount)
    return AssetPerAccount(groupInfo, assetInfo, null)
}

fun uniqueAssetPerAccountFaker(): AssetPerAccount
{
    val groupIdData = ByteArray(520, { it.toByte() })
    val groupId = GroupId(ChainSelector.NEXA, groupIdData)
    val assetInfo = AssetInfo(groupId)
    assetInfo.name = "mockAssetName"
    assetInfo.docUrl = "mock.pages.dev"
    val title = "title"
    val series = "series"
    assetInfo.nft = NexaNFTv2("niftyVer", title, series, "author", listOf(), "appUri", "info")
    val assetAmount = 1L
    val groupInfo = GroupInfo(groupId, assetAmount)
    return AssetPerAccount(groupInfo, assetInfo, null)
}

fun assetOfferFaker(): AssetOffer
{
    val assetInfo = assetPerAccountFaker().assetInfo
    val address = PayAddress("nexa:nqtsq5g53fjq76u7y5kfuw8xqj3s7almzsqsjgypt2gs8ne2")
    return AssetOffer(
      "some.uri.string.here",
      69000000L,
      assetInfo,
      address,
      mock(),
      42L,
      false
    )
}

class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getString(key: String, defaultValue: String?): String? = data[key] as? String ?: defaultValue
    override fun getInt(key: String, defaultValue: Int): Int
    {
        TODO("Not yet implemented")
    }

    override fun edit(): PreferencesEdit = FakeEditor(data)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean
    {
        return data[key] as Boolean? ?: defaultValue
    }

    // Other methods should be implemented similarly
}

class FakeEditor(private val data: MutableMap<String, Any>) :  PreferencesEdit {
    override fun putString(key: String, value: String): PreferencesEdit
    {
        value.let { data[key] = it }
        return this
    }

    override fun putBoolean(key: String, value: Boolean): PreferencesEdit
    {
        value.let { data[key] = it }
        return this
    }

    override fun putInt(key: String, value: Int): PreferencesEdit
    {
        TODO("Not yet implemented")
    }

    override fun commit()
    {

    }
}

/**
 * In-memory [SharedPreferences] implementation.
 */
class InMemoryPrefs : SharedPreferences
{
    private val data = mutableMapOf<String, Any?>()

    override fun edit(): PreferencesEdit = object : PreferencesEdit
    {
        override fun putString(key: String, value: String): PreferencesEdit { data[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): PreferencesEdit { data[key] = value; return this }
        override fun putInt(key: String, value: Int): PreferencesEdit { data[key] = value; return this }
        override fun commit() {}
    }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = data[key] as? Boolean ?: defaultValue
    override fun getString(key: String, defaultValue: String?): String? = data[key] as? String ?: defaultValue
    override fun getInt(key: String, defaultValue: Int): Int = data[key] as? Int ?: defaultValue
}

/**
* The account is a dev.mokkery [mock] of the [Account] interface.
*
*   * Properties whose types are interfaces / abstract classes ([SharedPreferences],
*     [iMutex], [CnxnMgr], [WalletDatabase]) are stubbed with mokkery mocks or
*     in-memory fakes.
*   * Properties whose types are libnexakotlin `final` classes ([Blockchain],
*     [Bip44Wallet]) — which mokkery cannot mock directly — are constructed via
*     their public Kotlin constructors with the dependencies above wired in. This is
*     a real [Blockchain] / [Bip44Wallet] instance the same way the libnexakotlin
*     factory functions would build one, just without going through the wally
*     `wallyApp.newAccount(...)` plumbing that creates a full [info.bitcoinunlimited.www.wally.AccountImpl].
*   * The [DecimalFormat] used by `format()` is constructed directly with the same
*     pattern that production `NexaFormat` uses.
*   * The receive [PayDestination] is a real [Pay2PubKeyTemplateDestination] (an
*     ordinary value object — not an account) so the receive screen has a non-null
*     address to render and copy to the clipboard.
 */
fun mockAccount(initialBalance: BigDecimal = BigDecimal.ZERO): Account
{
    // Real PayDestination — just a value object, not linked to an account. Provides a non-null address
    val ownDest: PayDestination = Pay2PubKeyTemplateDestination(
      ChainSelector.NEXA,
      UnsecuredSecret(ByteArray(32) { 1.toByte() }),
      1234,
    )
    val currentReceiveFlow = MutableStateFlow<PayDestination?>(ownDest)

    // Stubs for properties whose types are interfaces / abstract classes.
    // The CnxnMgr and WalletDatabase mocks use [MockMode.autofill] so any
    // unstubbed call made by the Blockchain / Bip44Wallet constructors below
    // returns a sensible default (null / 0 / empty) instead of throwing.
    val prefs = InMemoryPrefs()
    val mutex = mock<iMutex>()
    val cnxnMgr = mock<CnxnMgr>(MockMode.autofill) {
        every { p2pCnxns } returns emptyList()
        // Blockchain's constructor (via RequestMgr) installs handlers on these
        // Callbackers, so they must be non-null. Callbacker is a public Kotlin
        // class with a no-arg constructor — no Unsafe needed.
        every { txHandler } returns Callbacker()
        every { rejectHandler } returns Callbacker()
        every { blockHandler } returns Callbacker()
        every { partialBlockHandler } returns Callbacker()
        every { headersHandler } returns Callbacker()
        every { invHandler } returns Callbacker()
    }
    val walletDb = mock<WalletDatabase>(MockMode.autofill) {
        every { kvp } returns mock(MockMode.autofill)
        every { tx } returns mock(MockMode.autofill)
        every { txo } returns mock(MockMode.autofill)
    }

    // Real Blockchain and Bip44Wallet constructed via their public Kotlin
    // constructors with the mocked dependencies above. No real wally
    // AccountImpl is involved — these are bare libnexakotlin instances built
    // the same way the libnexakotlin factory functions would build them.
    val dummyChain = Blockchain(
      ChainSelector.NEXA,
      "test",
      cnxnMgr,
      Hash256(),
      Hash256(),
      Hash256(),
      0L,
      BigInteger.ZERO,
      "",
      false,
    )
    val dummyWallet = Bip44Wallet("mockSelfSendWallet", ChainSelector.NEXA, walletDb)
    // Wire the wallet to the blockchain — same as AccountImpl's init does.
    dummyWallet.usesChain(dummyChain)


    val mockAccount = mock<Account> {
        // Mutable backing flows the mock returns.
        val balanceFlow = MutableStateFlow<BigDecimal?>(initialBalance)
        val unconfirmedFlow = MutableStateFlow<BigDecimal?>(null)
        val confirmedFlow = MutableStateFlow<BigDecimal?>(null)
        val syncedDateFlow = MutableStateFlow(0L)
        val fastForwardStatusFlow = MutableStateFlow<String?>(null)
        val fiatPerCoinFlow = MutableStateFlow(BigDecimal.ZERO)
        val assetsFlow = MutableStateFlow<Map<GroupId, AssetPerAccount>>(emptyMap())

        val nexaFormat = DecimalFormat("##,###,###,###,##0.00")

        // Identity / display
        every { name } returns "mockSelfSend"
        every { nameAndChain } returns "mockSelfSend on nexa"
        every { currencyCode } returns "NEXA"
        every { cryptoFormat } returns nexaFormat
        every { cryptoInputFormat } returns DecimalFormat("##########.##")
        every { format(any()) } calls { (qty: BigDecimal) -> nexaFormat.format(qty) }

        // Balance
        every { balance } calls { balanceFlow.value }
        every { balance = any() } calls { (v: BigDecimal?) -> balanceFlow.value = v }
        every { balanceState } returns balanceFlow
        every { unconfirmedBalance } returns unconfirmedFlow
        every { confirmedBalance } returns confirmedFlow

        // Sync / fiat
        every { syncedDate } returns syncedDateFlow
        every { fiatPerCoin } returns BigDecimal.ZERO
        every { fiatPerCoinObservable } returns fiatPerCoinFlow

        // Lock / visibility
        every { visible } returns true
        every { lockable } returns false
        every { locked } returns false
        every { pinEntered } returns true
        every { encodedPin } returns null
        every { flags } returns 0UL

        // Fastforward
        every { fastforward } returns null
        every { fastforwardStatus } returns null
        every { fastForwardStatusState } returns fastForwardStatusFlow

        // Assets
        every { assets } returns emptyMap()
        every { assetsObservable } returns assetsFlow
        every { assetTransferList } returns mutableListOf()
        every { hasAssets() } returns false
        every { assetList() } returns mutableListOf()
        every { clearAssetTransferList() } returns 0
        every { addAssetToTransferList(any(), any()) } returns false

        // Receive address
        every { currentReceive } calls { currentReceiveFlow.value }
        every { currentReceiveObservable } returns currentReceiveFlow

        // Stubbed wiring — see top-of-file comments
        every { wallet } returns dummyWallet
        every { chain } returns dummyChain
        every { this@mock.cnxnMgr } returns cnxnMgr
        every { prefDB } returns prefs
        every { access } returns mutex
        every { handler } returns kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
        every { this@mock.walletDb } returns walletDb
        every { started } returns true

        // Unit conversions — production formulas inlined here, no real account needed.
        every { toFinestUnit(any()) } calls { (qty: BigDecimal) -> (qty * BigDecimal.fromInt(100)).longValue() }
        every { fromFinestUnit(any()) } calls { (qty: Long) -> BigDecimal.fromLong(qty) / BigDecimal.fromInt(100) }
        every { toPrimaryUnit(any()) } calls { (qty: BigDecimal) -> qty }
        every { fromPrimaryUnit(any()) } calls { (qty: BigDecimal) -> qty }

        // URLs
        every { transactionInfoWebUrl(any()) } returns null
        every { addressInfoWebUrl(any()) } returns null

        // Lifecycle / persistence — all no-ops
        every { start() } returns Unit
        every { onResume() } returns Unit
        every { delete() } returns Unit
        every { changeAsyncProcessing() } returns Unit
        every { onChange(any()) } returns Unit
        every { installChangeHandlers() } returns Unit
        every { removeChangeHandlers() } returns Unit
        every { saveAccountAddress() } returns Unit
        every { loadAccountAddress() } returns Unit
        every { saveAccountFlags() } returns Unit
        every { loadAccountFlags() } returns Unit
        every { saveAccountPin(any()) } returns Unit
        every { submitAccountPin(any()) } returns 0
        every { loadEncodedPin() } returns null
        every { setBlockchainAccessModeFromPrefs() } returns Unit
        every { constructAssetMap(any()) } returns Unit
        every { asyncInit(any(), any()) } returns Unit

        // Wallet change callbacks
        every { cb1 } returns { _, _ -> }
        every { wCb } returns null
        every { blkCb } returns null
        every { netCb } returns null
        every { genericElectrumNodeReqCount } returns 0
    }
    return mockAccount
}

fun assetInfoListFaker(amount: Int = 1): List<AssetInfo>
{
    val assets = mutableListOf<AssetInfo>()
    for (i in 1..amount)
    {
        val groupIdData = ByteArray(420+i, { it.toByte() })
        val groupId = GroupId(ChainSelector.NEXA, groupIdData)
        val assetInfo = AssetInfo(groupId)
        assetInfo.name = "mockAssetName + $i"
        assets.add(assetInfo)
    }
    return assets.toList()
}

fun groupIdMapFaker(amount: Int = 1): Map<GroupId, Long>
{
    val map = mutableMapOf<GroupId, Long>()
    for (i in 1..amount)
    {
        val groupIdData = ByteArray(420+i, { it.toByte() })
        val groupId = GroupId(ChainSelector.NEXA, groupIdData)
        map[groupId] = i.toLong()
    }
    return map.toMap()
}

fun tricklePaySessionFaker(account: Account): TricklePaySession
{
    val domains = TricklePayDomains()
    val session = TricklePaySession(domains)
    session.pill.account.value = account
    val txMock: iTransaction = txFor(ChainSelector.NEXA)
    session.proposedTx = txMock

    val assetViewModel = AssetViewModel()
    val assets = assetInfoListFaker(40)
    assetViewModel.assets.value = assets

    val analysis = TxAnalysisResults(
      account,
      42,
      42,
      42,
      42,
      42,
      42,
      42,
      groupIdMapFaker(4),
      groupIdMapFaker(4),
      groupIdMapFaker(4),
      groupIdMapFaker(4),
      assetViewModel,
      null
    )
    session.proposalAnalysis.value = analysis

    return session
}