package org.wallywallet.androidTestImplementation

import org.nexa.libnexakotlin.*
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4


import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

import info.bitcoinunlimited.www.wally.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.nexa.libnexakotlin.initializeLibNexa
import org.nexa.libnexakotlin.libnexa
import java.lang.AssertionError
import com.ionspin.kotlin.bignum.decimal.*
import org.nexa.threads.Gate

import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

// The IP address of the host machine: Android sets up a fake network with the host hardcoded to this IP
val EMULATOR_HOST_IP = "192.168.1.5" // "127.0.0.1" //"10.0.2.2"

val LogIt = GetLog("AndroidTest")

// assert is sometimes compiled "off" but in tests we never want to skip the checks so create a helper function
fun check(v: Boolean?)
{
    if (v == null) throw AssertionError("check failed")
    if (!v) throw AssertionError("check failed")
}


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
        forTestingDoNotAutoCreateWallets = true
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
    fun testSort()
    {
        val lst = listOf(3,10,1,2)
        val sorted = lst.sortedBy { it }
        assert(sorted[0] == 1)
    }

    @Test
    fun testelectrumclient()
    {
        /*
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
         */
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

    /*
    @Test
    fun testBlockchain()
    {
        val workBytes = Blockchain.getWorkFromDifficultyBits(0x172c4e11)
        val work = BigInteger(1, workBytes)
        assertEquals(work, BigInteger("5c733e87890743fed65", 16))
    }

     */

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
    fun testNexaTransaction()
    {
        /*
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
         */
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
    fun connectToP2P()
    {
        /*
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
         */
    }

    // @Test
    fun testBitcoinComPrices()
    {
        val result = historicalUbchInFiat("USD", 1576616203)
        LogIt.info(result.toPlainString())
        check(result == CurrencyDecimal(".00018293"))
    }
}
