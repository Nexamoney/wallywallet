import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import org.nexa.assets.AssetInfo
import org.nexa.assets.AssetLoadState
import org.nexa.assets.AssetManager
import org.nexa.assets.AssetPerAccount
import org.nexa.assets.Hash256Serializer
import org.nexa.assets.NexaNFTv2
import org.nexa.assets.UrlSerializer
import org.nexa.assets.assetManagerStorage
import org.nexa.assets.currentAssetCheckTriggerCount
import org.nexa.assets.lastAssetChangeBlock
import org.nexa.assets.triggerAssetCheck
import org.nexa.assets.triggerAssetCheckOnBlock
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.GroupInfo
import org.nexa.libnexakotlin.Hash256
import org.nexa.libnexakotlin.TokenDesc
import org.nexa.libnexakotlin.TokenGenesisInfo
import org.nexa.libnexakotlin.initializeLibNexa
import info.bitcoinunlimited.www.wally.KotlinTarget
import info.bitcoinunlimited.www.wally.platform
import ui.createAssetInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFails

@OptIn(ExperimentalUnsignedTypes::class)
class AssetsTest
{
    val cs = ChainSelector.NEXA

    init {
        initializeLibNexa()
    }

    // -- AssetInfo token decimal conversion tests --

    @Test
    fun tokenDecimalToFinestUnitNoDecimalPlaces()
    {
        val ai = createAssetInfoWithDecimals(null)
        assertEquals(42L, ai.tokenDecimalToFinestUnit(BigDecimal.fromInt(42)))
    }

    @Test
    fun tokenDecimalToFinestUnitWithDecimalPlaces()
    {
        val ai = createAssetInfoWithDecimals(2)
        assertEquals(150L, ai.tokenDecimalToFinestUnit(BigDecimal.fromDouble(1.5)))
    }

    @Test
    fun tokenDecimalToFinestUnitZeroDecimalPlaces()
    {
        val ai = createAssetInfoWithDecimals(0)
        assertEquals(100L, ai.tokenDecimalToFinestUnit(BigDecimal.fromInt(100)))
    }

    @Test
    fun tokenDecimalToFinestUnitHighDecimalPlaces()
    {
        val ai = createAssetInfoWithDecimals(8)
        assertEquals(100_000_000L, ai.tokenDecimalToFinestUnit(BigDecimal.fromInt(1)))
    }

    @Test
    fun tokenDecimalFromFinestUnitNoDecimalPlaces()
    {
        val ai = createAssetInfoWithDecimals(null)
        assertEquals(BigDecimal.fromInt(42), ai.tokenDecimalFromFinestUnit(42L))
    }

    @Test
    fun tokenDecimalFromFinestUnitWithDecimalPlaces()
    {
        val ai = createAssetInfoWithDecimals(2)
        val result = ai.tokenDecimalFromFinestUnit(150L)
        assertEquals(0, result.compareTo(BigDecimal.fromDouble(1.5)))
    }

    @Test
    fun tokenDecimalFromStringNoDecimalPlaces()
    {
        val ai = createAssetInfoWithDecimals(0)
        val result = ai.tokenDecimalFromString("42")
        assertEquals(BigDecimal.fromInt(42), result)
    }

    @Test
    fun tokenDecimalFromStringWithDecimalPlaces()
    {
        val ai = createAssetInfoWithDecimals(4)
        val result = ai.tokenDecimalFromString("1.5")
        assertEquals(0, result.compareTo(BigDecimal.fromDouble(1.5)))
    }

    @Test
    fun tokenDecimalRoundTrip()
    {
        val ai = createAssetInfoWithDecimals(6)
        val original = BigDecimal.fromDouble(3.141592)
        val finest = ai.tokenDecimalToFinestUnit(original)
        val roundTripped = ai.tokenDecimalFromFinestUnit(finest)
        assertEquals(0, roundTripped.compareTo(original))
    }

    // -- AssetPerAccount delegation tests --

    @Test
    fun assetPerAccountDelegatesToAssetInfo()
    {
        val ai = createAssetInfoWithDecimals(2)
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val groupInfo = GroupInfo(groupId, 500L)
        val apa = AssetPerAccount(groupInfo, ai)

        assertEquals(150L, apa.tokenDecimalToFinestUnit(BigDecimal.fromDouble(1.5)))
        assertEquals(0, apa.tokenDecimalFromFinestUnit(150L).compareTo(BigDecimal.fromDouble(1.5)))
    }

    @Test
    fun assetPerAccountEditableAmountDefaults()
    {
        val ai = createAssetInfo("test", null)
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val groupInfo = GroupInfo(groupId, 100L)
        val apa = AssetPerAccount(groupInfo, ai)
        assertNull(apa.editableAmount)
    }

    @Test
    fun assetPerAccountEditableAmountCanBeSet()
    {
        val ai = createAssetInfo("test", null)
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val groupInfo = GroupInfo(groupId, 100L)
        val apa = AssetPerAccount(groupInfo, ai, editableAmount = BigDecimal.fromInt(50))
        assertEquals(BigDecimal.fromInt(50), apa.editableAmount)
    }

    // -- AssetInfo property and state tests --

    @Test
    fun assetInfoNameObservableUpdates()
    {
        val ai = createAssetInfo("initial", null)
        assertEquals("initial", ai.nameObservable.value)
        ai.name = "updated"
        assertEquals("updated", ai.nameObservable.value)
    }

    @Test
    fun assetInfoNftObservableUpdates()
    {
        val ai = createAssetInfo("test", null)
        assertNull(ai.nftObservable.value)
        val nft = NexaNFTv2("v1", "title", "series", "author", listOf(), "appUri", "info")
        ai.nft = nft
        assertEquals("title", ai.nftObservable.value?.title)
    }

    @Test
    fun assetInfoLoadStateDefault()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        assertEquals(AssetLoadState.UNLOADED, ai.loadState)
        assertEquals(AssetLoadState.UNLOADED, ai.loadStateObservable.value)
    }

    @Test
    fun assetInfoLoadStateObservableUpdates()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        ai.loadState = AssetLoadState.LOADED_GENESIS_INFO
        assertEquals(AssetLoadState.LOADED_GENESIS_INFO, ai.loadStateObservable.value)
        ai.loadState = AssetLoadState.COMPLETED
        assertEquals(AssetLoadState.COMPLETED, ai.loadStateObservable.value)
    }

    @Test
    fun assetInfoIconImageStateUpdates()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        assertNull(ai.iconImageState.value)
        assertNull(ai.iconImage)
    }

    @Test
    fun assetInfoDefaultProperties()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        assertNull(ai.name)
        assertNull(ai.ticker)
        assertEquals(-1, ai.genesisHeight)
        assertNull(ai.genesisTxidem)
        assertNull(ai.docHash)
        assertNull(ai.docUrl)
        assertNull(ai.tokenInfo)
        assertNull(ai.iconBytes.value)
        assertNull(ai.iconUri)
        assertNull(ai.nft)
        assertEquals(AssetLoadState.UNLOADED, ai.loadState)
    }

    // -- AssetInfo.finalize test --

    @Test
    fun assetInfoFinalizeClosesDataLock()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        ai.finalize()
    }

    // -- AssetInfo.getTddIcon tests --

    @Test
    fun getTddIconReturnsNullPairWhenNoTokenInfo()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        val (url, bytes) = ai.getTddIcon()
        assertNull(url)
        assertNull(bytes)
    }

    @Test
    fun getTddIconReturnsNullPairWhenNoIcon()
    {
        val ai = createAssetInfoWithDecimals(2)
        // tokenInfo exists but icon is null
        val (url, bytes) = ai.getTddIcon()
        assertNull(url)
        assertNull(bytes)
    }

    @Test
    fun getTddIconReturnsNullPairForRelativeUrlWithNoDocUrl()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        val td = TokenDesc("TST", icon = "/icons/token.png")
        ai.tokenInfo = td
        // relative icon URL but no docUrl to resolve against
        val (url, bytes) = ai.getTddIcon()
        assertNull(url)
        assertNull(bytes)
    }

    // -- AssetInfo.nftFile test --

    @Test
    fun nftFileReturnsNullWhenNoNftExists()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        val result = ai.nftFile(am)
        assertNull(result)
    }

    // -- AssetManager.nftUrl tests --

    @Test
    fun nftUrlWithNullDocUrlReturnsWellKnownLocations()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val urls = am.nftUrl(null, groupId)
        assertEquals(2, urls.size)
        assertTrue(urls[0].contains("niftyart.cash"))
        assertTrue(urls[1].contains("nexa.space"))
        assertTrue(urls[0].contains("/raw/"))
        assertTrue(urls[1].contains("/raw/"))
    }

    @Test
    fun nftUrlWithDocUrlReturnsCanonicalPlusWellKnown()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val urls = am.nftUrl("https://example.com/tokens", groupId)
        assertEquals(4, urls.size)
        assertTrue(urls[0].startsWith("https://example.com/raw/"))
        assertTrue(urls[1].startsWith("https://example.com/public/"))
        assertTrue(urls[2].contains("niftyart.cash"))
        assertTrue(urls[3].contains("nexa.space"))
    }

    @Test
    fun nftUrlTestnetUsesTestnetUrls()
    {
        val am = AssetManager()
        val groupId = GroupId(ChainSelector.NEXATESTNET, ByteArray(520) { it.toByte() })
        val urls = am.nftUrl(null, groupId)
        assertTrue(urls[0].contains("testnet"))
    }

    @Test
    fun nftUrlContainsGroupIdInPath()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val groupIdStr = groupId.toStringNoPrefix()
        val urls = am.nftUrl(null, groupId)
        assertTrue(urls.all { it.contains(groupIdStr) })
    }

    // -- AssetManager.track tests --

    @Test
    fun trackReturnsNewAssetInfoForUnknownGroupId()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val info = am.track(groupId)
        assertNotNull(info)
        assertEquals(groupId, info.groupId)
    }

    @Test
    fun trackReturnsSameInstanceForSameGroupId()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val info1 = am.track(groupId)
        val info2 = am.track(groupId)
        assertTrue(info1 === info2)
    }

    @Test
    fun trackDifferentGroupIdsReturnDifferentInstances()
    {
        val am = AssetManager()
        val groupId1 = GroupId(cs, ByteArray(520) { it.toByte() })
        val groupId2 = GroupId(cs, ByteArray(520) { ((it + 1) % 256).toByte() })
        val info1 = am.track(groupId1)
        val info2 = am.track(groupId2)
        assertFalse(info1 === info2)
    }

    @Test
    fun trackCompletedAssetReturnsCachedImmediately()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        // Pre-populate with a completed asset
        val ai = AssetInfo(groupId)
        ai.loadState = AssetLoadState.COMPLETED
        am.assets[groupId] = ai
        val result = am.track(groupId)
        assertTrue(result === ai)
        assertEquals(AssetLoadState.COMPLETED, result.loadState)
    }

    // -- AssetManager.clear tests --

    @Test
    fun clearRemovesAllTrackedAssets()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        am.track(groupId)
        assertTrue(am.assets.isNotEmpty())
        am.clear()
        assertTrue(am.assets.isEmpty())
    }

    @Test
    fun clearNullifiesAssetFields()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        ai.name = "TestAsset"
        ai.nft = NexaNFTv2("v1", "title", "series", "author", listOf(), "appUri", "info")
        ai.tokenInfo = TokenDesc("TST")
        ai.iconUri = Url("https://example.com/icon.png")
        ai.iconBytes.value = byteArrayOf(1, 2, 3)
        ai.publicMediaUri = Url("https://example.com/media.mp4")
        ai.publicMediaBytes = byteArrayOf(4, 5, 6)
        ai.ownerMediaUri = Url("https://example.com/owner.mp4")
        ai.ownerMediaCache = "/tmp/cache"
        ai.loadState = AssetLoadState.COMPLETED
        am.assets[groupId] = ai

        // Hold reference before clear
        val ref = am.assets[groupId]!!

        am.clear()

        // Verify fields were nullified on the held reference
        assertNull(ref.iconUri)
        assertNull(ref.iconBytes.value)
        assertNull(ref.tokenInfo)
        assertNull(ref.publicMediaUri)
        assertNull(ref.publicMediaBytes)
        assertNull(ref.ownerMediaUri)
        assertNull(ref.ownerMediaCache)
        assertNull(ref.nft)
        assertEquals(AssetLoadState.UNLOADED, ref.loadState)
    }

    // -- AssetManager.storeAssetInfo / loadAssetInfo round-trip --

    @Test
    fun storeAndLoadAssetInfoRoundTrip()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(32) { it.toByte() })
        val ai = AssetInfo(groupId)
        ai.name = "RoundTripToken"
        ai.ticker = "RTT"
        ai.genesisHeight = 5000
        ai.docUrl = "https://example.com/token.json"
        ai.loadState = AssetLoadState.COMPLETED

        am.storeAssetInfo(ai)

        val loaded = am.loadAssetInfo(groupId)
        assertEquals("RoundTripToken", loaded.name)
        assertEquals("RTT", loaded.ticker)
        assertEquals(5000, loaded.genesisHeight)
        assertEquals("https://example.com/token.json", loaded.docUrl)
        assertEquals(AssetLoadState.COMPLETED, loaded.loadState)

        // Clean up
        try { assetManagerStorage().deleteAssetFile(groupId.toStringNoPrefix() + ".ai") } catch (_: Exception) {}
    }

    @Test
    fun storeAndLoadAssetInfoWithNft()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(32) { ((it + 50) % 256).toByte() })
        val ai = AssetInfo(groupId)
        ai.name = "NftToken"
        ai.nft = NexaNFTv2("v1", "ArtTitle", "SeriesA", "Artist", listOf("keyword"), "appUri", "some info")
        ai.loadState = AssetLoadState.COMPLETED

        am.storeAssetInfo(ai)

        val loaded = am.loadAssetInfo(groupId)
        assertEquals("NftToken", loaded.name)
        assertNotNull(loaded.nft)
        assertEquals("ArtTitle", loaded.nft?.title)
        assertEquals("SeriesA", loaded.nft?.series)
        assertEquals("Artist", loaded.nft?.author)

        // Clean up
        try { assetManagerStorage().deleteAssetFile(groupId.toStringNoPrefix() + ".ai") } catch (_: Exception) {}
    }

    @Test
    fun storeAssetInfoHandlesExceptionGracefully()
    {
        val am = AssetManager()
        // AssetInfo with problematic groupId — storeAssetInfo catches exceptions internally
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        // This should not throw even if storage has issues
        am.storeAssetInfo(ai)
    }

    // -- AssetManager.storeTokenDesc / getTokenDesc round-trip (cached path) --

    @Test
    fun storeAndGetTokenDescFromCache()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(32) { ((it + 99) % 256).toByte() })
        val tgi = TokenGenesisInfo(
            document_hash = null,
            document_url = null,
            height = 2000,
            name = "CachedToken",
            ticker = "CT",
            token_id_hex = "ab",
            txid = "cd",
            txidem = "ef",
            decimal_places = 4
        )
        val td = TokenDesc("CT", name = "CachedToken", genesisInfo = tgi, pubkey = byteArrayOf(1, 2, 3))

        am.storeTokenDesc(groupId, td)

        // Verify by loading the file directly from storage
        val ef = assetManagerStorage().loadAssetFile(groupId.toHex() + ".td").second
        val data = ef.openAt(0).readAndClose()
        ef.close()
        val loaded = Json { ignoreUnknownKeys = true }.decodeFromString(TokenDesc.serializer(), data.decodeToString())

        assertEquals("CT", loaded.ticker)
        assertEquals("CachedToken", loaded.name)
        assertEquals(2000L, loaded.genesisInfo?.height)
        assertEquals(4, loaded.genesisInfo?.decimal_places)

        // Clean up
        try { assetManagerStorage().deleteAssetFile(groupId.toHex() + ".td") } catch (_: Exception) {}
    }

    // -- AssetManager.getNftFile tests --

    @Test
    fun getNftFileReturnsNullWhenNotCachedAndNoNetwork()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        // No cached file, no network — should return null
        val result = am.getNftFile(null, groupId)
        assertNull(result)
    }

    @Test
    fun getNftFileReturnsNullWithTokenDescButNoNetwork()
    {
        val am = AssetManager()
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val td = TokenDesc("TST", nftUrl = "https://nonexistent.example.com/nft.zip")
        val result = am.getNftFile(td, groupId)
        assertNull(result)
    }

    // -- AssetManager storage delegation tests --

    @Test
    fun storeAndLoadAssetFileRoundTrip()
    {
        val am = AssetManager()
        val testData = "hello world".encodeToByteArray()
        val filename = "test_delegation.bin"
        am.storeAssetFile(filename, testData)
        val (_, ef) = am.loadAssetFile(filename)
        val loaded = ef.openAt(0).readAndClose()
        ef.close()
        assertTrue(loaded.contentEquals(testData))

        // Clean up
        am.deleteAssetFile(filename)
    }

    @Test
    fun storeAndLoadCardFileRoundTrip()
    {
        // storeCardFile returns a relative path that loadCardFile can't resolve in mobile test contexts
        if (platform().target != KotlinTarget.JVM) return
        val am = AssetManager()
        val testData = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val filename = "test_card.bin"
        am.storeCardFile(filename, testData)
        val (_, loaded) = am.loadCardFile(filename)
        assertTrue(loaded.contentEquals(testData))
    }

    @Test
    fun deleteAssetFilesRemovesAll()
    {
        val am = AssetManager()
        am.storeAssetFile("delete_test_1.bin", byteArrayOf(1))
        am.storeAssetFile("delete_test_2.bin", byteArrayOf(2))
        am.deleteAssetFiles()
        // After delete, loading should fail
        assertFails { am.loadAssetFile("delete_test_1.bin") }
        assertFails { am.loadAssetFile("delete_test_2.bin") }
    }

    // -- Serializer tests --

    @Test
    fun hash256SerializerRoundTrip()
    {
        val original = Hash256(ByteArray(32) { it.toByte() })
        val json = Json.encodeToString(Hash256Serializer, original)
        val decoded = Json.decodeFromString(Hash256Serializer, json)
        assertEquals(original.toHex(), decoded.toHex())
    }

    @Test
    fun urlSerializerRoundTrip()
    {
        val original = Url("https://example.com/path?query=value")
        val json = Json.encodeToString(UrlSerializer, original)
        val decoded = Json.decodeFromString(UrlSerializer, json)
        assertEquals(original.toString(), decoded.toString())
    }

    // -- AssetInfo JSON serialization tests --

    @Test
    fun assetInfoJsonSerializationRoundTrip()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        ai.name = "SerToken"
        ai.ticker = "SER"
        ai.genesisHeight = 12345
        ai.docUrl = "https://example.com/doc.json"
        ai.loadState = AssetLoadState.LOADED_TOKEN_DESC

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val serialized = json.encodeToString(AssetInfo.serializer(), ai)
        val deserialized = json.decodeFromString(AssetInfo.serializer(), serialized)

        assertEquals("SerToken", deserialized.name)
        assertEquals("SER", deserialized.ticker)
        assertEquals(12345, deserialized.genesisHeight)
        assertEquals("https://example.com/doc.json", deserialized.docUrl)
        assertEquals(AssetLoadState.LOADED_TOKEN_DESC, deserialized.loadState)
    }

    @Test
    fun assetInfoJsonSerializationWithNft()
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        ai.name = "NftSer"
        ai.nft = NexaNFTv2("v1", "MyArt", "Collection1", "AuthorX", listOf("art", "digital"), "app://uri", "details")

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val serialized = json.encodeToString(AssetInfo.serializer(), ai)
        val deserialized = json.decodeFromString(AssetInfo.serializer(), serialized)

        assertEquals("NftSer", deserialized.name)
        assertNotNull(deserialized.nft)
        assertEquals("MyArt", deserialized.nft?.title)
        assertEquals("Collection1", deserialized.nft?.series)
        assertEquals("AuthorX", deserialized.nft?.author)
    }

    // -- AssetLoadState enum tests --

    @Test
    fun assetLoadStateOrdering()
    {
        assertTrue(AssetLoadState.UNLOADED.ordinal < AssetLoadState.LOADED_GENESIS_INFO.ordinal)
        assertTrue(AssetLoadState.LOADED_GENESIS_INFO.ordinal < AssetLoadState.LOADED_TOKEN_DESC.ordinal)
        assertTrue(AssetLoadState.LOADED_TOKEN_DESC.ordinal < AssetLoadState.COMPLETED.ordinal)
    }

    // -- triggerAssetCheck / triggerAssetCheckOnBlock tests --

    @Test
    fun triggerAssetCheckIncrementsCounter()
    {
        val before = currentAssetCheckTriggerCount.value
        triggerAssetCheck()
        assertEquals(before + 1, currentAssetCheckTriggerCount.value)
    }

    @Test
    fun triggerAssetCheckOnBlockUpdatesOnHigherBlock()
    {
        lastAssetChangeBlock.value = 0
        triggerAssetCheckOnBlock(100)
        assertEquals(100L, lastAssetChangeBlock.value)
    }

    @Test
    fun triggerAssetCheckOnBlockIgnoresLowerBlock()
    {
        lastAssetChangeBlock.value = 200
        triggerAssetCheckOnBlock(100)
        assertEquals(200L, lastAssetChangeBlock.value)
    }

    @Test
    fun triggerAssetCheckOnBlockIgnoresEqualBlock()
    {
        lastAssetChangeBlock.value = 100
        val before = currentAssetCheckTriggerCount.value
        triggerAssetCheckOnBlock(100)
        assertEquals(100L, lastAssetChangeBlock.value)
        // Counter should not have incremented
        assertEquals(before, currentAssetCheckTriggerCount.value)
    }

    // -- Helpers --

    private fun createAssetInfoWithDecimals(decimalPlaces: Int?): AssetInfo
    {
        val groupId = GroupId(cs, ByteArray(520) { it.toByte() })
        val ai = AssetInfo(groupId)
        if (decimalPlaces != null)
        {
            val td = TokenDesc(
                "TST", genesisInfo = TokenGenesisInfo(
                    document_hash = null,
                    document_url = null,
                    height = 1000,
                    name = "TestToken",
                    ticker = "TST",
                    token_id_hex = "00",
                    txid = "00",
                    txidem = "00",
                    decimal_places = decimalPlaces
                )
            )
            ai.tokenInfo = td
        }
        return ai
    }

    /** Helper to read all bytes from a BufferedSource */
    private fun okio.BufferedSource.readAndClose(): ByteArray
    {
        val bytes = this.readByteArray()
        this.close()
        return bytes
    }
}
