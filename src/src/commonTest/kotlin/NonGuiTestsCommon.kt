import info.bitcoinunlimited.www.wally.historicalUbchInFiat
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.nexa.libnexakotlin.*
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals


class NonGuiTestsCommon
{
    val LogIt = GetLog("BU.NonGuiTestsCommon")
    val dbPrefix = "test_"

    init
    {
        initializeLibNexa()
        runningTheTests = true
    }

    @Test
    fun testNexaTransaction()
    {
        val cs = ChainSelector.NEXAREGTEST
        var tx = NexaTransaction(cs)
        try
        {
            var in1 = Spendable(cs, BchTxOutpoint("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f", 0), 10001)
            assert(false)
        } catch (e: ClassCastException)
        {
            check(true)
        }

        val in1 = Spendable(cs, NexaTxOutpoint("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f"), 10001)
        tx._inputs.add(NexaTxInput(cs, in1, SatoshiScript(cs), 0xffffffff))
        var out1 = NexaTxOutput(cs, 10001, SatoshiScript(cs, "76a914431ecec94e0a920a7972b084dcfabbd69f61691288ac"))
        tx._outputs.add(out1)
        var ser = tx.BCHserialize(SerializationType.NETWORK)
        LogIt.info("tx: " + ser.toHex())
        assertEquals(4, 2 + 2)

        // ser.flatten()
        var tx2 = NexaTransaction(cs, ser)
        var ser2 = tx2.BCHserialize(SerializationType.NETWORK)
        // ser2.flatten()
        LogIt.info("tx: " + ser2.toHex())
        assert(ser.toHex() == ser2.toHex())
    }

    @Test
    fun testTokenDescDoc()
    {
        val s = """[{
    "ticker": "NIFTY",
    "name": "NiftyArt NFTs",
    "summary": "These are NiftyArt nonfungible tokens",
    "icon": "/td/niftyicon.svg"
},
 "IDeKqpAh/uVJMTX8rEr1kQ/ItKY4fPnvF/iUPuJOtV52MhNongMBNRVPYoYf++HWB+IPOvFZwX225j3tFyyUV10="
]""".trimIndent()

        val je = Json.decodeFromString(JsonElement.serializer(),s)
        val jsonArray: JsonArray = je.jsonArray
        val tdjo = jsonArray[0].jsonObject
        val td = Json.decodeFromJsonElement(TokenDesc.serializer(), tdjo)
        val sig: String = jsonArray[1].jsonPrimitive.content

        println(je)
    }


    @Test
    fun testCoCond()
    {
        val coCtxt: CoroutineContext = newFixedThreadPoolContext(2, "testCoCtxt")
        val coScope = CoroutineScope(coCtxt)

        runBlocking {
            var c1 = CoCond<Nothing?>(coScope)
            var v = 1;
            val cor = GlobalScope.launch { c1.yield(); v = 3 }
            c1.wake(null)
            cor.join()
            assertEquals(v, 3)
        }

        runBlocking {

            var c1 = CoCond<Int>(coScope)
            var v = 1
            val cor = GlobalScope.launch { v = c1.yield()!!; }
            //val cor2 = GlobalScope.launch { v = c1.yield()!!; }
            c1.wake(3)
            //c1.wake(4)
            cor.join()
            //cor2.join()
            check(v == 3 || v == 4)
        }
    }

    @Test
    fun testHash()
    {
        // run against some test vectors...

        var hash1 = libnexa.sha256(ByteArray(64, { _ -> 1 }))
        assertEquals("7c8975e1e60a5c8337f28edf8c33c3b180360b7279644a9bc1af3c51e6220bf5", hash1.toHex())
        var hash2 = libnexa.hash256(ByteArray(64, { _ -> 1 }))
        assertEquals("61a088b4cf1f244e52e5e88fcd91b3b7d6135ebff53476ecc8436e23b5e7f095", hash2.toHex())
        var hash3 = libnexa.hash160(ByteArray(64, { _ -> 1 }))
        assertEquals("171073b9bee66e89f657f50532412e3539bb1d6b", hash3.toHex())

        val data =
          "0100000001ef128218b638f8b34e125d3a87f074974522b07be629f84b72bba549d493abcb0000000049483045022100dbd9a860d31ef53b9ae12306f25a5f64ac732d5951e1d843314ced89d80c585b02202697cd52be31156a1c30f094aa388cb0d5e8e7f767472fd0b03cb1b7e666c23841feffffff0277800f04000000001976a9148132c3672810992a3c780772b980b1d690598af988ac80969800000000001976a914444b7eaa50459a727a4238778cde09a21f9b579a88ac81531400".fromHex()
        var hash4 = Hash256(libnexa.hash256(data))
        assertEquals(hash4.toHex(), "1605af3f8beb87fa26fc12a45e52ce5e0e296d0da551c0775916634d451ca664")

        val tx = BchTransaction(ChainSelector.BCHTESTNET, data, SerializationType.NETWORK)
        assertEquals(tx.hash.toHex(), hash4.toHex())
        // testnet3 block 1
        val blk = BchBlock(ChainSelector.BCHTESTNET, BCHserialized("0100000043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000bac8b0fa927c0ac8234287e33c5f74d38d354820e24756ad709d7038fc5f31f020e7494dffff001d03e4b6720101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0e0420e7494d017f062f503253482fffffffff0100f2052a010000002321021aeaf2f8638a129a3156fbe7e5ef635226b0bafd495ff03afe2c843d7e3a4b51ac00000000".fromHex(), SerializationType.NETWORK))
        assertEquals(blk.hash.toHex(), "00000000b873e79784647a6c82962c70d228557d24a747ea4d1b8bbe878e1206")

    }


    @Test
    fun testCodec()
    {
        LogIt.error("testCodec testCodec start")
        var data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, -1, 127, -127)
        var str = Codec.encode64(data)
        LogIt.info(str)
        LogIt.error("testCodec + $str")
        var data2 = Codec.decode64(str)
        check(data contentEquals data2)
    }

    @Test
    fun testAddressConversion()
    {
        run {
            val p = PayAddress("bchreg:qr02hs6rq52uvkz7fkum28hd0zqewgzhlgjjedf05z")
            check(p.type == PayAddressType.P2PKH)
            check(p.blockchain == ChainSelector.BCHREGTEST)
            check(p.data.toHex() == "deabc3430515c6585e4db9b51eed7881972057fa")
            val q = PayAddress(ChainSelector.BCHREGTEST, p.type, p.data)
            check(p == q)
        }
        run {
            val p = PayAddress("bchtest:qpm4a0mxj3gcmyj38xe2uss50lzh2ww2qqhgf8gzm3")
            check (p.type == PayAddressType.P2PKH)
            check (p.blockchain == ChainSelector.BCHTESTNET)
            check (p.data.toHex() == "775ebf6694518d925139b2ae42147fc57539ca00")
        }
    }

    @Test
    fun testBchTransaction()
    {
        /*
        var tx = BchTransaction(ChainSelector.BCHREGTEST)
        try
        {
            var in1 = Spendable(ChainSelector.BCHREGTEST, NexaTxOutpoint(Hash256("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f")), 10001)
            assert(false) // Should have thrown because inconsistent blockchain
        } catch (e: ClassCastException)
        {
            check(true)
        }
        var in1 = Spendable(ChainSelector.BCHREGTEST, BchTxOutpoint("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f", 2), 10001)
        tx._inputs.add(BchTxInput(ChainSelector.BCHREGTEST, in1, SatoshiScript(ChainSelector.BCHREGTEST), 0xffffffff))
        var out1 = BchTxOutput(ChainSelector.BCHREGTEST, 10001, SatoshiScript(ChainSelector.BCHREGTEST, "76a914431ecec94e0a920a7972b084dcfabbd69f61691288ac"))
        tx._outputs.add(out1)
        var ser = tx.BCHserialize(SerializationType.NETWORK)
        LogIt.info("tx: " + ser.toHex())
        assertEquals(4, 2 + 2)

        ser.flatten()
        var tx2 = BchTransaction(ChainSelector.BCHREGTEST, ser)
        var ser2 = tx2.BCHserialize(SerializationType.NETWORK)
        ser2.flatten()
        LogIt.info("tx: " + ser2.toHex())
        assert(ser.toHex() == ser2.toHex())
         */
    }

    @Test
    fun testKeys()
    {
        // Test that BchTxOutpoint is a proper map key
        val paymentHistory: MutableMap<BchTxOutpoint, Int> = mutableMapOf()
        val testOutpoint = BchTxOutpoint(Hash256("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f"), 0)
        paymentHistory[testOutpoint] = 1
        check(paymentHistory[testOutpoint] == 1)

        val testOutpoint2 = BchTxOutpoint(Hash256("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f"), 0)
        check(paymentHistory[testOutpoint2] != null)
        val testOutpoint3 = BchTxOutpoint(Hash256("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f"), 1)
        check(paymentHistory[testOutpoint3] == null)
        val testOutpoint4 = BchTxOutpoint(Hash256("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e00"), 0)
        check(paymentHistory[testOutpoint4] == null)


        // Test that PayAddress is a proper map key
        var receiving: MutableMap<PayAddress, Int> = mutableMapOf()

        val inserted = PayAddress(ChainSelector.BCHREGTEST, PayAddressType.P2PKH, "0123456789abcdef01230123456789abcdef0123".fromHex())
        receiving[inserted] = 1

        check(receiving.contains(inserted))
        check(receiving.containsKey(PayAddress(ChainSelector.BCHREGTEST, PayAddressType.P2PKH, "0123456789abcdef01230123456789abcdef0123".fromHex())))
    }


    @Test
    fun testScript()
    {
        val ch = ChainSelector.NEXAREGTEST
        val fakepubkey = "0123456789abcdef01230123456789abcdef0123".fromHex()

        // P2PKH

        // Basic match
        val P2PKH1 = SatoshiScript(ch) + OP.DUP + OP.HASH160 + OP.push(ByteArray(20, { 0 })) + OP.EQUALVERIFY + OP.CHECKSIG
        check(P2PKH1.match() != null)

        // A few bad matches
        val P2PKH2 = SatoshiScript(ch) + OP.DUP + OP.HASH160 + OP.push(ByteArray(20, { 0 })) + OP.EQUALVERIFY
        check(P2PKH2.match() == null)

        val P2PKH3 = SatoshiScript(ch) + OP.HASH160 + OP.push(ByteArray(20, { 0 })) + OP.EQUALVERIFY + OP.CHECKSIG
        check(P2PKH3.match() == null)

        val P2PKH4 = SatoshiScript(ch) + OP.DUP + OP.HASH160 + OP.push(ByteArray(21, { 0 })) + OP.EQUALVERIFY + OP.CHECKSIG
        val m = P2PKH4.match()
        check(m == null)

        val P2PKH5 = SatoshiScript(ch) + OP.DUP + OP.HASH160 + OP.push(ByteArray(19, { 0 })) + OP.EQUALVERIFY + OP.CHECKSIG
        check(P2PKH5.match() == null)


        // Test different constructions
        val P2PKH6 = SatoshiScript(ch, SatoshiScript.Type.SATOSCRIPT, OP.DUP, OP.HASH160, OP.push("0123456789abcdef01230123456789abcdef0123".fromHex()), OP.EQUALVERIFY, OP.CHECKSIG)

        if (true)
        {
            val (type, params) = P2PKH6.match() ?: throw AssertionError("should have matched P2PKH template")
            assertEquals(type, PayAddressType.P2PKH)
            check(params[0].contentEquals(fakepubkey))
        }

        val P2PKH8 = SatoshiScript(ch) + OP.DUP + OP.HASH160 + OP.push("0123456789abcdef01230123456789abcdef0123".fromHex()) + OP.EQUALVERIFY + OP.CHECKSIG
        check(P2PKH6.contentEquals(P2PKH8))

        val (type1, params1) = P2PKH6.match() ?: throw AssertionError("should have matched P2PKH template")
        assertEquals(type1, PayAddressType.P2PKH)
        check(params1[0].contentEquals(fakepubkey))

        // P2SH (must be BCHREGTEST becase P2SH removed in Nexa)
        val P2SH = SatoshiScript(ChainSelector.BCHREGTEST) + OP.HASH160 + OP.push(fakepubkey) + OP.EQUAL
        check(P2SH.match() != null)
        val (type2, params2) = P2SH.match() ?: throw AssertionError("should have matched P2SH template")

        assertEquals(type2, PayAddressType.P2SH)
        check(params2[0].contentEquals(fakepubkey))

        // bad matches
        val P2SH2 = SatoshiScript(ch) + OP.HASH160 + OP.push(fakepubkey)
        assertEquals(P2SH2.match(), null)

        val P2SH3 = SatoshiScript(ch) + OP.HASH160 + OP.push(ByteArray(21, { 0 })) + OP.EQUAL
        assertEquals(P2SH3.match(), null)

        val P2SH4 = SatoshiScript(ch) + OP.HASH160 + OP.push(ByteArray(21, { 0 })) + OP.EQUAL
        assertEquals(P2SH4.match(), null)
    }

    @Test
    fun testBip39()
    {
        val bip39TestVector = listOf(
          listOf(
            "00000000000000000000000000000000",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
            "xprv9s21ZrQH143K3h3fDYiay8mocZ3afhfULfb5GX8kCBdno77K4HiA15Tg23wpbeF1pLfs1c5SPmYHrEpTuuRhxMwvKDwqdKiGJS9XFKzUsAF"
          ),
          listOf(
            "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
            "2e8905819b8723fe2c1d161860e5ee1830318dbf49a83bd451cfb8440c28bd6fa457fe1296106559a3c80937a1c1069be3a3a5bd381ee6260e8d9739fce1f607",
            "xprv9s21ZrQH143K2gA81bYFHqU68xz1cX2APaSq5tt6MFSLeXnCKV1RVUJt9FWNTbrrryem4ZckN8k4Ls1H6nwdvDTvnV7zEXs2HgPezuVccsq"
          ),
          listOf(
            "80808080808080808080808080808080",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
            "d71de856f81a8acc65e6fc851a38d4d7ec216fd0796d0a6827a3ad6ed5511a30fa280f12eb2e47ed2ac03b5c462a0358d18d69fe4f985ec81778c1b370b652a8",
            "xprv9s21ZrQH143K2shfP28KM3nr5Ap1SXjz8gc2rAqqMEynmjt6o1qboCDpxckqXavCwdnYds6yBHZGKHv7ef2eTXy461PXUjBFQg6PrwY4Gzq"
          ),
          listOf(
            "ffffffffffffffffffffffffffffffff",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
            "ac27495480225222079d7be181583751e86f571027b0497b5b5d11218e0a8a13332572917f0f8e5a589620c6f15b11c61dee327651a14c34e18231052e48c069",
            "xprv9s21ZrQH143K2V4oox4M8Zmhi2Fjx5XK4Lf7GKRvPSgydU3mjZuKGCTg7UPiBUD7ydVPvSLtg9hjp7MQTYsW67rZHAXeccqYqrsx8LcXnyd"
          ),
          listOf(
            "000000000000000000000000000000000000000000000000",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon agent",
            "035895f2f481b1b0f01fcf8c289c794660b289981a78f8106447707fdd9666ca06da5a9a565181599b79f53b844d8a71dd9f439c52a3d7b3e8a79c906ac845fa",
            "xprv9s21ZrQH143K3mEDrypcZ2usWqFgzKB6jBBx9B6GfC7fu26X6hPRzVjzkqkPvDqp6g5eypdk6cyhGnBngbjeHTe4LsuLG1cCmKJka5SMkmU"
          ),
          listOf(
            "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
            "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal will",
            "f2b94508732bcbacbcc020faefecfc89feafa6649a5491b8c952cede496c214a0c7b3c392d168748f2d4a612bada0753b52a1c7ac53c1e93abd5c6320b9e95dd",
            "xprv9s21ZrQH143K3Lv9MZLj16np5GzLe7tDKQfVusBni7toqJGcnKRtHSxUwbKUyUWiwpK55g1DUSsw76TF1T93VT4gz4wt5RM23pkaQLnvBh7"
          ),
          listOf(
            "808080808080808080808080808080808080808080808080",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic avoid letter always",
            "107d7c02a5aa6f38c58083ff74f04c607c2d2c0ecc55501dadd72d025b751bc27fe913ffb796f841c49b1d33b610cf0e91d3aa239027f5e99fe4ce9e5088cd65",
            "xprv9s21ZrQH143K3VPCbxbUtpkh9pRG371UCLDz3BjceqP1jz7XZsQ5EnNkYAEkfeZp62cDNj13ZTEVG1TEro9sZ9grfRmcYWLBhCocViKEJae"
          ),
          listOf(
            "ffffffffffffffffffffffffffffffffffffffffffffffff",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo when",
            "0cd6e5d827bb62eb8fc1e262254223817fd068a74b5b449cc2f667c3f1f985a76379b43348d952e2265b4cd129090758b3e3c2c49103b5051aac2eaeb890a528",
            "xprv9s21ZrQH143K36Ao5jHRVhFGDbLP6FCx8BEEmpru77ef3bmA928BxsqvVM27WnvvyfWywiFN8K6yToqMaGYfzS6Db1EHAXT5TuyCLBXUfdm"
          ),
          listOf(
            "0000000000000000000000000000000000000000000000000000000000000000",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art",
            "bda85446c68413707090a52022edd26a1c9462295029f2e60cd7c4f2bbd3097170af7a4d73245cafa9c3cca8d561a7c3de6f5d4a10be8ed2a5e608d68f92fcc8",
            "xprv9s21ZrQH143K32qBagUJAMU2LsHg3ka7jqMcV98Y7gVeVyNStwYS3U7yVVoDZ4btbRNf4h6ibWpY22iRmXq35qgLs79f312g2kj5539ebPM"
          ),
          listOf(
            "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
            "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth title",
            "bc09fca1804f7e69da93c2f2028eb238c227f2e9dda30cd63699232578480a4021b146ad717fbb7e451ce9eb835f43620bf5c514db0f8add49f5d121449d3e87",
            "xprv9s21ZrQH143K3Y1sd2XVu9wtqxJRvybCfAetjUrMMco6r3v9qZTBeXiBZkS8JxWbcGJZyio8TrZtm6pkbzG8SYt1sxwNLh3Wx7to5pgiVFU"
          ),
          listOf(
            "8080808080808080808080808080808080808080808080808080808080808080",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic bless",
            "c0c519bd0e91a2ed54357d9d1ebef6f5af218a153624cf4f2da911a0ed8f7a09e2ef61af0aca007096df430022f7a2b6fb91661a9589097069720d015e4e982f",
            "xprv9s21ZrQH143K3CSnQNYC3MqAAqHwxeTLhDbhF43A4ss4ciWNmCY9zQGvAKUSqVUf2vPHBTSE1rB2pg4avopqSiLVzXEU8KziNnVPauTqLRo"
          ),
          listOf(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo vote",
            "dd48c104698c30cfe2b6142103248622fb7bb0ff692eebb00089b32d22484e1613912f0a5b694407be899ffd31ed3992c456cdf60f5d4564b8ba3f05a69890ad",
            "xprv9s21ZrQH143K2WFF16X85T2QCpndrGwx6GueB72Zf3AHwHJaknRXNF37ZmDrtHrrLSHvbuRejXcnYxoZKvRquTPyp2JiNG3XcjQyzSEgqCB"
          ),
          listOf(
            "9e885d952ad362caeb4efe34a8e91bd2",
            "ozone drill grab fiber curtain grace pudding thank cruise elder eight picnic",
            "274ddc525802f7c828d8ef7ddbcdc5304e87ac3535913611fbbfa986d0c9e5476c91689f9c8a54fd55bd38606aa6a8595ad213d4c9c9f9aca3fb217069a41028",
            "xprv9s21ZrQH143K2oZ9stBYpoaZ2ktHj7jLz7iMqpgg1En8kKFTXJHsjxry1JbKH19YrDTicVwKPehFKTbmaxgVEc5TpHdS1aYhB2s9aFJBeJH"
          ),
          listOf(
            "6610b25967cdcca9d59875f5cb50b0ea75433311869e930b",
            "gravity machine north sort system female filter attitude volume fold club stay feature office ecology stable narrow fog",
            "628c3827a8823298ee685db84f55caa34b5cc195a778e52d45f59bcf75aba68e4d7590e101dc414bc1bbd5737666fbbef35d1f1903953b66624f910feef245ac",
            "xprv9s21ZrQH143K3uT8eQowUjsxrmsA9YUuQQK1RLqFufzybxD6DH6gPY7NjJ5G3EPHjsWDrs9iivSbmvjc9DQJbJGatfa9pv4MZ3wjr8qWPAK"
          ),
          listOf(
            "68a79eaca2324873eacc50cb9c6eca8cc68ea5d936f98787c60c7ebc74e6ce7c",
            "hamster diagram private dutch cause delay private meat slide toddler razor book happy fancy gospel tennis maple dilemma loan word shrug inflict delay length",
            "64c87cde7e12ecf6704ab95bb1408bef047c22db4cc7491c4271d170a1b213d20b385bc1588d9c7b38f1b39d415665b8a9030c9ec653d75e65f847d8fc1fc440",
            "xprv9s21ZrQH143K2XTAhys3pMNcGn261Fi5Ta2Pw8PwaVPhg3D8DWkzWQwjTJfskj8ofb81i9NP2cUNKxwjueJHHMQAnxtivTA75uUFqPFeWzk"
          ),
          listOf(
            "c0ba5a8e914111210f2bd131f3d5e08d",
            "scheme spot photo card baby mountain device kick cradle pact join borrow",
            "ea725895aaae8d4c1cf682c1bfd2d358d52ed9f0f0591131b559e2724bb234fca05aa9c02c57407e04ee9dc3b454aa63fbff483a8b11de949624b9f1831a9612",
            "xprv9s21ZrQH143K3FperxDp8vFsFycKCRcJGAFmcV7umQmcnMZaLtZRt13QJDsoS5F6oYT6BB4sS6zmTmyQAEkJKxJ7yByDNtRe5asP2jFGhT6"
          ),
          listOf(
            "6d9be1ee6ebd27a258115aad99b7317b9c8d28b6d76431c3",
            "horn tenant knee talent sponsor spell gate clip pulse soap slush warm silver nephew swap uncle crack brave",
            "fd579828af3da1d32544ce4db5c73d53fc8acc4ddb1e3b251a31179cdb71e853c56d2fcb11aed39898ce6c34b10b5382772db8796e52837b54468aeb312cfc3d",
            "xprv9s21ZrQH143K3R1SfVZZLtVbXEB9ryVxmVtVMsMwmEyEvgXN6Q84LKkLRmf4ST6QrLeBm3jQsb9gx1uo23TS7vo3vAkZGZz71uuLCcywUkt"
          ),
          listOf(
            "9f6a2878b2520799a44ef18bc7df394e7061a224d2c33cd015b157d746869863",
            "panda eyebrow bullet gorilla call smoke muffin taste mesh discover soft ostrich alcohol speed nation flash devote level hobby quick inner drive ghost inside",
            "72be8e052fc4919d2adf28d5306b5474b0069df35b02303de8c1729c9538dbb6fc2d731d5f832193cd9fb6aeecbc469594a70e3dd50811b5067f3b88b28c3e8d",
            "xprv9s21ZrQH143K2WNnKmssvZYM96VAr47iHUQUTUyUXH3sAGNjhJANddnhw3i3y3pBbRAVk5M5qUGFr4rHbEWwXgX4qrvrceifCYQJbbFDems"
          ),
          listOf(
            "23db8160a31d3e0dca3688ed941adbf3",
            "cat swing flag economy stadium alone churn speed unique patch report train",
            "deb5f45449e615feff5640f2e49f933ff51895de3b4381832b3139941c57b59205a42480c52175b6efcffaa58a2503887c1e8b363a707256bdd2b587b46541f5",
            "xprv9s21ZrQH143K4G28omGMogEoYgDQuigBo8AFHAGDaJdqQ99QKMQ5J6fYTMfANTJy6xBmhvsNZ1CJzRZ64PWbnTFUn6CDV2FxoMDLXdk95DQ"
          ),
          listOf(
            "8197a4a47f0425faeaa69deebc05ca29c0a5b5cc76ceacc0",
            "light rule cinnamon wrap drastic word pride squirrel upgrade then income fatal apart sustain crack supply proud access",
            "4cbdff1ca2db800fd61cae72a57475fdc6bab03e441fd63f96dabd1f183ef5b782925f00105f318309a7e9c3ea6967c7801e46c8a58082674c860a37b93eda02",
            "xprv9s21ZrQH143K3wtsvY8L2aZyxkiWULZH4vyQE5XkHTXkmx8gHo6RUEfH3Jyr6NwkJhvano7Xb2o6UqFKWHVo5scE31SGDCAUsgVhiUuUDyh"
          ),
          listOf(
            "066dca1a2bb7e8a1db2832148ce9933eea0f3ac9548d793112d9a95c9407efad",
            "all hour make first leader extend hole alien behind guard gospel lava path output census museum junior mass reopen famous sing advance salt reform",
            "26e975ec644423f4a4c4f4215ef09b4bd7ef924e85d1d17c4cf3f136c2863cf6df0a475045652c57eb5fb41513ca2a2d67722b77e954b4b3fc11f7590449191d",
            "xprv9s21ZrQH143K3rEfqSM4QZRVmiMuSWY9wugscmaCjYja3SbUD3KPEB1a7QXJoajyR2T1SiXU7rFVRXMV9XdYVSZe7JoUXdP4SRHTxsT1nzm"
          ),
          listOf(
            "f30f8c1da665478f49b001d94c5fc452",
            "vessel ladder alter error federal sibling chat ability sun glass valve picture",
            "2aaa9242daafcee6aa9d7269f17d4efe271e1b9a529178d7dc139cd18747090bf9d60295d0ce74309a78852a9caadf0af48aae1c6253839624076224374bc63f",
            "xprv9s21ZrQH143K2QWV9Wn8Vvs6jbqfF1YbTCdURQW9dLFKDovpKaKrqS3SEWsXCu6ZNky9PSAENg6c9AQYHcg4PjopRGGKmdD313ZHszymnps"
          ),
          listOf(
            "c10ec20dc3cd9f652c7fac2f1230f7a3c828389a14392f05",
            "scissors invite lock maple supreme raw rapid void congress muscle digital elegant little brisk hair mango congress clump",
            "7b4a10be9d98e6cba265566db7f136718e1398c71cb581e1b2f464cac1ceedf4f3e274dc270003c670ad8d02c4558b2f8e39edea2775c9e232c7cb798b069e88",
            "xprv9s21ZrQH143K4aERa2bq7559eMCCEs2QmmqVjUuzfy5eAeDX4mqZffkYwpzGQRE2YEEeLVRoH4CSHxianrFaVnMN2RYaPUZJhJx8S5j6puX"
          ),
          listOf(
            "f585c11aec520db57dd353c69554b21a89b20fb0650966fa0a9d6f74fd989d8f",
            "void come effort suffer camp survey warrior heavy shoot primary clutch crush open amazing screen patrol group space point ten exist slush involve unfold",
            "01f5bced59dec48e362f2c45b5de68b9fd6c92c6634f44d6d40aab69056506f0e35524a518034ddc1192e1dacd32c1ed3eaa3c3b131c88ed8e7e54c49a5d0998",
            "xprv9s21ZrQH143K39rnQJknpH1WEPFJrzmAqqasiDcVrNuk926oizzJDDQkdiTvNPr2FYDYzWgiMiC63YmfPAa2oPyNB23r2g7d1yiK6WpqaQS"
          )
        )

        LogIt.info("testBip39")
        val result = GenerateEntropy(128)
        check(result.size == 128 / 8)

        for (tv in bip39TestVector)
        {
            val b = tv[0].fromHex()
            val words = GenerateBip39SecretWords(b)
            check(words == tv[1])
            val seed = generateBip39Seed(words, "TREZOR", 64)
            LogIt.info(seed.toHex())
            check(seed.toHex() == tv[2])
        }
    }
}
