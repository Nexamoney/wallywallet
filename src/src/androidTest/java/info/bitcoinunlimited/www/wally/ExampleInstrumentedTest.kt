package org.wallywallet.androidTestImplementation

import org.nexa.libnexakotlin.*
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.ext.junit.runners.AndroidJUnit4


import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before

import org.junit.Assert.*

import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.libnexa
import java.lang.AssertionError
import java.lang.Exception
import com.ionspin.kotlin.bignum.decimal.*
import org.nexa.threads.Gate
import java.math.BigInteger
import java.security.MessageDigest

import java.util.Random
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

// The IP address of the host machine: Android sets up a fake network with the host hardcoded to this IP
val EMULATOR_HOST_IP = "192.168.1.5" //"10.0.2.2"

val LogIt = GetLog("AndroidTest")

// assert is sometimes compiled "off" but in tests we never want to skip the checks so create a helper function
fun check(v: Boolean?)
{
    if (v == null) throw AssertionError("check failed")
    if (!v) throw AssertionError("check failed")
}


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

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class UnitTest
{
    init {
        initializeLibNexa()
        runningTheTests = true
        runningTheUnitTests = true
        dbPrefix = "test_"
    }

    var applicationContext = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testTest()
    {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("info.bitcoinunlimited.www.wally", appContext.packageName)
        //assertEquals(1, 0)
        LogIt.info("test")
    }

    @Test
    fun testelectrumclient()
    {
        LogIt.info("This test requires an electrum cash server running at ${EMULATOR_HOST_IP}:${DEFAULT_NEXAREG_TCP_ELECTRUM_PORT}")

        val c = try
        {
            ElectrumClient(ChainSelector.NEXAREGTEST, EMULATOR_HOST_IP, DEFAULT_NEXAREG_TCP_ELECTRUM_PORT, "Electrum@${EMULATOR_HOST_IP}:${DEFAULT_NEXAREG_TCP_ELECTRUM_PORT}", useSSL = false)
        } catch (e: java.net.ConnectException)
        {
            LogIt.warning("Cannot connect: Skipping Electrum tests: ${e}")
            null
        } catch (e: java.net.SocketTimeoutException)
        {
            LogIt.warning("Cannot connect: Skipping Electrum tests: ${e}")
            null
        }
        org.junit.Assume.assumeTrue(c != null)
        val cnxn = c!!

        cnxn.start()

        val ret = cnxn.call("server.version", listOf("4.0.1", "1.4"), 1000)
        if (ret != null) LogIt.info(sourceLoc() + ": Server Version returned: " + ret)

        val version = cnxn.version()
        LogIt.info(sourceLoc() + ": Version API call returned: " + version.first + " " + version.second)

        /* TODO enable when electrscash is updated
        val features = cnxn.features()
        LogIt.info(sourceLoc() + ": genesis block hash:" + features.genesis_hash)
        check("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206" == features.genesis_hash)
        check("sha256" == features.hash_function)
        check(features.server_version.contains("ElectrsCash"))  // Clearly this may fail if you connect a different server to this regression test
         */

        val ret2 = cnxn.call("blockchain.block.header", listOf(100, 102), 1000)
        LogIt.info(ret2 ?: "null")

        try
        {
            cnxn.getTx("5a2e45c999509a3505cf543d462977b198957abefcc9c86f4a0ef59525363d00", 1000)  // doesn't exist
            assert(false)
        } catch (e: ElectrumNotFound)
        {
            assert(e.message!!.contains("tx not in blockchain or mempool"))
        }

        //cnxn.getTx("5a2e45c999509a3505cf543d462977b198957abefcc9c86f4a0ef59525363d0b", 1000)

        try
        {
            cnxn.getTx("zz5a2e45c999509a3505cf543d462977b198957abefcc9c86f4a0ef59525363d", 1000) // bad hash
            assert(false)
        } catch (e: ElectrumIncorrectRequest)
        {
        }

        try
        {
            cnxn.getTx("5a2e45c999509a3505cf543d462977b198957abefcc9c86f4a0ef59525363d", 1000) // bad hash (short)
            assert(false)
        } catch (e: ElectrumIncorrectRequest)
        {
        }

        try
        {
            cnxn.getTx("5a2e45c999509a3505cf543d462977b198957abefcc9c86f4a0ef59525363d".repeat(10), 1000) // bad hash (large)
            assert(false)
        } catch (e: ElectrumIncorrectRequest)
        {
        }

        val (name, ver) = cnxn.version(1000)
        LogIt.info("Server name $name, server version $ver")

        cnxn.call("server.banner", null) {
            LogIt.info("Server Banner reply is: " + it)
        }

        @Serializable
        data class BannerReply(val result: String)
        cnxn.parse("server.banner", null, BannerReply.serializer()) {
            LogIt.info("Server Banner reply is: " + it!!.result)
        }


        //@UseExperimental(kotlinx.serialization.ImplicitReflectionSerializer::class)
        //val b:BannerReply? = cnxn.parse("server.banner", null, 1000)
        //LogIt.info(b?.result)

        cnxn.subscribe("blockchain.headers.subscribe") {
            LogIt.info("Received blockchain header notification: ${it}")
        }

        val header = cnxn.getHeader(10000000)  // beyond the tip
        LogIt.info(header.toString())

        try
        {
            cnxn.getHeader(-1)  // beyond the tip
        } catch (e: ElectrumIncorrectRequest)
        {
            LogIt.info(e.toString())
        }
        LogIt.info(header.toString())


        // This code gets the first coinbase transaction and then checks its history.  Based on the normal regtest generation setup, there should be at least 100
        // blocks that generate to this same output.
        val tx = cnxn.getTxAt(1, 0)
        LogIt.info(tx.toHex())
        val txBlkHeader = blockHeaderFor(cnxn.chainSelector, BCHserialized(cnxn.getHeader(1), SerializationType.HASH))
        tx.debugDump()

        /* TODO add in when get_first_use is committed to electrscash.  TODO: check server capabilities
        val firstUse = cnxn.getFirstUse(tx.outputs[0].script)
        LogIt.info("first use in block ${firstUse.block_hash}:${firstUse.block_height}, transaction ${firstUse.tx_hash}")
        check(firstUse.tx_hash != null)
        check(firstUse.block_height!! == 1)
        check(tx.hash.toHex() == firstUse.tx_hash!![0])
        check(txBlkHeader.hash.toHex() == firstUse.block_hash)

        // doesn't exist
        val firstUse2 = cnxn.getFirstUse("5a2e45c999509a3505cf543d462977b198957abefcc9c86f4a0ef59525363d00")
        check(firstUse2.tx_hash == null)
        check(firstUse2.block_hash == null)
        */

        val uses = cnxn.getHistory(tx.outputs[0].script)
        for (use in uses)
        {
            LogIt.info("used in block ${use.first} tx ${use.second}")
        }

        assert(uses.size >= 100)  // Might be wrong if the regtest chain startup is changed.

        val headers = cnxn.getHeadersFor(cnxn.chainSelector, 0, 1000, 10000)
        for (i in headers.indices)
        {
            val hdr = cnxn.getHeader(i, 1000)
            check(hdr contentEquals (headers[i] as NexaBlockHeader).serializeHeader(SerializationType.NETWORK).flatten() )

        }

        //Thread.sleep(20000)
        cnxn.close()
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

        val je = kotlinx.serialization.json.Json.decodeFromString(JsonElement.serializer(),s)
        val jsonArray:JsonArray = je.jsonArray
        val tdjo = jsonArray[0].jsonObject
        val td = kotlinx.serialization.json.Json.decodeFromJsonElement(TokenDesc.serializer(), tdjo)
        val sig: String = jsonArray[1].jsonPrimitive.content

        println(je)
    }

    @Test
    fun testCoCond()
    {
        val coCtxt: CoroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val coScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coCtxt)

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
        Log.e("testCodec", "testCodec start")
        var data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, -1, 127, -127)
        var str = Codec.encode64(data)
        LogIt.info(str)
        Log.e("testCodec", str)
        var data2 = Codec.decode64(str)
        check(data contentEquals data2)
    }

    /*
    @Test
    fun testSerialize()
    {
        val chain = ChainSelector.NEXAREGTEST
        val outpoint = NexaTxOutpoint(Hash256("1f443a6340d2f805e0046b3bcea0d93830844b356edd72da053044dd3fb09f54"), 32)
        var sp = Spendable(chain)
        sp.secret = UnsecuredSecret(byteArrayOf(1,2,3))
        sp.outpoint = outpoint
        sp.priorOutScript = SatoshiScript(chain) + OP.DUP + OP.HASH160 + OP.push(ByteArray(20, { 0})) + OP.EQUALVERIFY + OP.CHECKSIG
        sp.addr = PayAddress("nexareg:qpaj30le3wqz04ldwnsj94x75u9kv782kvh6hgf5e0")
        sp.amount = 4567
        sp.redeemScript = SatoshiScript(chain) + OP.push(byteArrayOf(7,8))
        sp.commitHeight = 987654321
        sp.commitBlockHash = Guid(Hash256("1c2f4377f2222f167a9015c0ee2ca47200b368d5c17e3698962d0f307e565881"))
        sp.spentHeight = 5739243
        sp.spentBlockHash = Guid()

        val rawAddr = (sp.addr as PayAddress).data
        kotlin.check(rawAddr.size == 20)

        val serScr = sp.priorOutScript.BCHserialize(SerializationType.DISK).flatten()
        val scr2 = SatoshiScript(chain)
        scr2.BCHdeserialize(BCHserialized(serScr, SerializationType.DISK))
        kotlin.check(scr2.flatten().contentEquals(sp.priorOutScript.flatten()))

        val ser = sp.BCHserialize(SerializationType.DISK).flatten()

        var ser2 = BCHserialized(ser, SerializationType.DISK)
        val sp2 = Spendable(chain, ser2)

        kotlin.check(sp.secret?.getSecret().contentEquals(sp2.secret!!.getSecret()))
        kotlin.check(sp.outpoint == sp2.outpoint)
        kotlin.check(sp.priorOutScript == sp.priorOutScript)
        kotlin.check(sp.addr == sp2.addr)
        kotlin.check(sp.amount == sp2.amount)
        kotlin.check(sp.redeemScript.contentEquals(sp2.redeemScript))
        kotlin.check(sp.commitHeight == sp2.commitHeight)
        kotlin.check(sp.commitBlockHash == sp2.commitBlockHash)
        kotlin.check(sp.spentHeight == sp2.spentHeight)
        kotlin.check(sp.spentBlockHash == sp2.spentBlockHash)
        kotlin.check(sp.spentUnconfirmed == sp2.spentUnconfirmed)
        kotlin.check(sp.spendableUnconfirmed == sp2.spendableUnconfirmed)

        val td = TdppDomain("domain", "topic", "addr", "currency", -1, 2, 3, 4, "per", "day", "perweek", "permonth", false)

        var ser3 = BCHserialized(td.BCHserialize(SerializationType.DISK).flatten(), SerializationType.DISK)
        var tdc = TdppDomain(ser3)
        check(td.equals(tdc))
    }
    */

    @Test
    fun testHDderivation()
    {
        val secret = ByteArray(32, { _ -> 1 })
        val index = 0
        val newSecret = libnexa.deriveHd44ChildKey(secret, 27, AddressDerivationKey.ANY, 0, true, index.toInt())
        LogIt.info("HD key: " + newSecret.first.toHex())
        assertEquals(newSecret.first.toHex(), "9c219cb84e3199b076aaa0f954418407b1e8d54f79103612fbaf04598bc8be55")

        val wdb = openKvpDB(dbPrefix + "testHDerivationWallet")!!
        // Test vector for wallet derivation path
        val wal = Bip44Wallet(wdb,"mBCH", ChainSelector.BCH,"night wing zebra gate juice absorb student disease gospel maple depth trap")
        val knownAddrs = mutableListOf(
        "bitcoincash:qpwxcdytyswysm7qmvlnmu7pw4zhdgtzrctr5plkfn",
        "bitcoincash:qpxzpeh39jhduns9srpjc5jzhsmx9x95rsqeeyfask",
        "bitcoincash:qqauxecsjlk2y23ssnvljgpdd60p53sfkgear23ct3",
        "bitcoincash:qrs0muk7f48tzj8fkn5qze62vd6jad80fs6rwvsrs2",
        "bitcoincash:qply459hr7q2x9yxg7k53ge3hxykyvq635t4ahaj27",
        "bitcoincash:qqqxnsm3ku23nhhv353kzatrdqqq3pzfaqn3am8m9x",
        "bitcoincash:qqqxqj7wvyhgewqrxvc86k70tm4er83pxsn979qhe2",
        "bitcoincash:qztmzqp70fv69nuc2axvxq0fzjv8rpq0nyh8qglnax",
        "bitcoincash:qqhv59yk4dg25mmr7ntantdge97gw9lgwgzk7yu44v",
        "bitcoincash:qqhz9jpyqh386lqm2k0hunraxlpvf3w25sqt5qnmp4",
        "bitcoincash:qzmkzaau9c2qsh6nnch9n80re265f3x4cc4zcq4hkp",
        "bitcoincash:qr2f65qgg6pjqjd3e6r0gmu5xvjrtwpfdcvcremzgr",
        "bitcoincash:qz8zvka9vjdygyargww8w7ax6h55u5uzag9v4jrva9",
        "bitcoincash:qz2057l2t9mu97mv7xl28s2xv7xkjh5xwsylgaz0x2",
        "bitcoincash:qqfh83y0ynkgg893gvctjh4kfr03ew72vqef6npejl",
        "bitcoincash:qz0qkex8r0jcz5zx3s67fpeme26esfrm8yy0axhxm7",
        "bitcoincash:qpggr3sjzkm44xmktzdsjkjg7yapqtntj5jyt0cwm8",
        "bitcoincash:qr2v54yh7qehgl79y7lvmfc5248q6x7dsq5rqxhh29",
        "bitcoincash:qrja9sckkrckptkckpk6p0pwsuarm5qyr5w4fwvtn8",
        "bitcoincash:qq406tnvlgl52f93ruu9l7uqqu0gnxmk9u02kk28py")
        for (addr in knownAddrs)
        {
            val d = wal.generateDestination()
            assertEquals(d.address.toString(), addr)
        }

        val waln = Bip44Wallet(wdb,"nexa", ChainSelector.NEXA,"night wing zebra gate juice absorb student disease gospel maple depth trap")
        val knownAddrsn = mutableListOf(
          "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw",
          "nexa:nqtsq5g55t9699mcue00frjqql5275r3et45c3dqtxzfz8ru",
          "nexa:nqtsq5g5skc5xfw7dzu2jw7hktf3tg053drevxx94yx44gjc",
          "nexa:nqtsq5g53zpuftghudhhg6szwwerymtrhnwzu7ufrh8yg8t5",
          "nexa:nqtsq5g5pehl9a45ne68sgzjkdneq2lw3qumyuzjlams0jf4")
        for (addr in knownAddrsn)
        {
            val d = waln.generateDestination()
            println(d)
            // Assert.assertEquals(d.address.toString(), addr)
        }

    }

    /*
    @Test
    fun testBlockchain()
    {
        val workBytes = Blockchain.getWorkFromDifficultyBits(0x172c4e11)
        val work = BigInteger(1, workBytes)
        assertEquals(work, BigInteger("5c733e87890743fed65", 16))
    }

     */

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

    /*  Tests deprecated ROOM db converters
    @Test
    fun testConverters()
    {
        // Test big integer conversion
        val bic = BigIntegerConverters()
        val test = 12345678.toBigInteger()
        assertEquals(bic.fromByteArray(bic.toByteArray(test)), test)

        val rnd = Random()
        for (i in 1..1000)
        {
            val bi = BigInteger(256, rnd)
            assertEquals(bic.fromByteArray(bic.toByteArray(bi)), bi)
        }

        // Test Hash256 converter
        val hac = Hash256Converters()
        for (i: Int in 1..1000)
        {
            val b = byteArrayOf(i.and(255).toByte(), ((i shr 8) and 255).toByte(), ((i shr 16) and 255).toByte())
            val v = Hash256(libnexa.sha256(b))
            assertEquals(hac.fromByteArray(hac.toByteArray(v)), v)
        }
    }

     */

    @Test
    fun testBchTransaction()
    {
        var tx = BchTransaction(ChainSelector.BCHREGTEST)
        try
        {
            var in1 = Spendable(ChainSelector.BCHREGTEST, NexaTxOutpoint(Hash256("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f")), 10001)
            assert(false) // Should have thrown because inconsistent blockchain
        } catch (e: java.lang.ClassCastException)
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
        } catch (e: java.lang.ClassCastException)
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

        ser.flatten()
        var tx2 = NexaTransaction(cs, ser)
        var ser2 = tx2.BCHserialize(SerializationType.NETWORK)
        ser2.flatten()
        LogIt.info("tx: " + ser2.toHex())
        assert(ser.toHex() == ser2.toHex())
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

    /*
    @Test
    fun testSignVerifyMessage()
    {
        val testMessage = "This is a test"

        val sbytes = UnsecuredSecret(ByteArray(32, { _ -> 2 }))

        val identityDest = Pay2PubKeyHashDestination(ChainSelector.NEXAREGTEST, sbytes)

        val secret = identityDest.secret ?: throw IdentityException("Wallet failed to provide an identity with a secret", "bad wallet", ErrorSeverity.Severe)
        val address = identityDest.address ?: throw IdentityException("Wallet failed to provide an identity with an address", "bad wallet", ErrorSeverity.Severe)

        val sig = Wallet.signMessage(testMessage.toByteArray(), secret.getSecret())

        val verify = Wallet.verifyMessage(testMessage.toByteArray(), address.data, sig)
        check(verify != null)
        check(verify!!.size != 0)
        check(verify contentEquals identityDest.pubkey)

        // Try bad verifications

        if (true)
        {
            val badaddr = ByteArray(10, { _ -> 3 })
            val badVerify = Wallet.verifyMessage(testMessage.toByteArray(), badaddr, sig)
            check(badVerify == null)
        }

        if (true)
        {
            val badaddr = ByteArray(20, { _ -> 3 })
            val badVerify = Wallet.verifyMessage(testMessage.toByteArray(), badaddr, sig)
            check(badVerify == null)
        }

        if (true)
        {
            val badaddr = address.data.copyOf()
            badaddr[0] = (badaddr[0].toByte() + 1.toByte()).toByte()  // Change the address a little
            val badVerify = Wallet.verifyMessage(testMessage.toByteArray(), badaddr, sig)
            check(badVerify == null)
        }

        if (true)
        {
            val differentMessage = "This is another test"
            val badVerify = Wallet.verifyMessage(differentMessage.toByteArray(), address.data, sig)
            check(badVerify == null)
        }

        if (true)
        {
            val badSig = sig.copyOf()
            badSig[0] = (badSig[0] + 1.toByte()).toByte()
            val badVerify = Wallet.verifyMessage(testMessage.toByteArray(), address.data, badSig)
            check(badVerify == null)
        }
    }

     */

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

        // P2SH

        val P2SH = SatoshiScript(ch) + OP.HASH160 + OP.push(fakepubkey) + OP.EQUAL
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
    fun connectToP2P()
    {
        val coCtxt: CoroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val coScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coCtxt)
        val coCond = CoCond<Boolean>(coScope)

        var cnxn: P2pClient = (try
        {
            P2pClient(ChainSelector.NEXAREGTEST, EMULATOR_HOST_IP, NexaRegtestPort, "regtest@${EMULATOR_HOST_IP}", coScope, coCond).connect(5000)
        } catch (e: java.net.SocketTimeoutException)
        {
            org.junit.Assume.assumeTrue(false)
            null
        })!!

        GlobalScope.launch { cnxn.processForever() }

        cnxn.waitForReady()

        cnxn.send(NetMsgType.PING, BCHserialized(SerializationType.NETWORK) + 1L)

        val loc = BlockLocator()
        loc.add(Hash256())  // This will ask for the genesis block because no hashes will match
        val stop = Hash256()
        var headers: MutableList<out iBlockHeader>? = null
        val waiter = Gate()
        cnxn.getHeaders(loc, stop, { lst, _ -> headers = lst; waiter.wake(); true })

        waiter.timedwaitfor(5000, { headers != null}) {}
        check(headers != null)  // If its null we didn't get the headers

        for (hdr in headers!!)
        {
            LogIt.info(hdr.height.toString() + " hash: " + hdr.hash.toHex() + " prev: " + hdr.hashPrevBlock.toHex())
        }


        LogIt.info("shutting down")
        cnxn.close()
        LogIt.info("TestCompleted")
    }

    @Test
    fun testBip39()
    {
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

    @Test
    fun testBitcoinComPrices()
    {
        val result = historicalUbchInFiat("USD", 1576616203)
        LogIt.info(result.toPlainString())
        check(result == CurrencyDecimal(".00018293"))
    }
}
