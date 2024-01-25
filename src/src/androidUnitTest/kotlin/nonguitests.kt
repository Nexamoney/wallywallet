import info.bitcoinunlimited.www.wally.zipForeach
import info.bitcoinunlimited.www.wally.nftCardFront
import info.bitcoinunlimited.www.wally.nftData
import okio.Buffer
import org.junit.Assert
import org.nexa.nexarpc.NexaRpcFactory
import org.nexa.libnexakotlin.*
import org.nexa.nexarpc.NexaRpc
import org.nexa.threads.millisleep
import java.util.concurrent.TimeoutException
import kotlin.test.*

val FULL_NODE_IP = "192.168.1.5"
val REGTEST_RPC_PORT=18332
val LogIt = GetLog("nonguitests")
/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class NonGuiTests
{
    init
    {
        initializeLibNexa()
        runningTheTests = true
    }

    fun openRpc(): NexaRpc
    {
        val rpcConnection = "http://" + FULL_NODE_IP + ":" + REGTEST_RPC_PORT
        LogIt.info("Connecting to: " + rpcConnection)
        var rpc = NexaRpcFactory.create(rpcConnection)
        var peerInfo = rpc.getpeerinfo()
        check(peerInfo.size > 0)
        return rpc
    }

    fun waitFor(maxTime: Int, predicate:()->Boolean, doFailed:()->String)
    {
        var elapsed = 0
        while(!predicate())
        {
            if (elapsed >= maxTime) throw TimeoutException(doFailed())
            else
            {
                millisleep(500U)
                elapsed += 500
            }
        }
    }


    @Test
    fun testParseZip()
    {
        // This is a small example NFT zip file
        val nftyZip = "504b03040a00000000005c8333582f3d08f59e0100009e01000009001c0063617264662e706e6755540900031fe9aa65b2e8aa6575780b000104e803000004e803000089504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082504b03041400000008000f878757428b8bb389000000b300000009001c00696e666f2e6a736f6e55540900038e3f72651ae8aa6575780b000104e803000004e8030000abe6525050cacb4c2ba90c4b2d52b25232d23350d20189956496e4a42a59292805e766e6a456820515401289a52519f9452019c7bc94a2d4728547d3f640b464a75696e717a51403e5a2151462c162c98925a9e9f9459540a3fddc4220ea120b0a4a8b32412640f8997969f9701e482025b1245149c14aa1ba162c9f93999c9a570c768c12572d1700504b01021e030a00000000005c8333582f3d08f59e0100009e010000090018000000000000000000b4810000000063617264662e706e6755540500031fe9aa6575780b000104e803000004e8030000504b01021e031400000008000f878757428b8bb389000000b3000000090018000000000001000000b481e1010000696e666f2e6a736f6e55540500038e3f726575780b000104e803000004e8030000504b050600000000020002009e000000ad0200000000".fromHex()
        // This is the image file it contains
        val cardfPng = "89504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082".fromHex()

        val infoJson = """{
  "niftyVer":"2.0",
  "title": "Smiley",
   
  "author": "Andrew ▼",
  "keywords": [  ],
  "category":"NFT",
  "appuri": "",
  "info": "",
  
  "data" : {},
  "license": ""
}
"""

        val b = Buffer()
        b.write(nftyZip)
        zipForeach(b) { info, data ->
            if (info.fileName == "cardf.png")
            {
                check(data!!.readByteArray() contentEquals  cardfPng)
            }
            if (info.fileName == "info.json")
            {
                val json = data!!.readByteArray().decodeUtf8()
                check(json == infoJson)
            }
            false
        }
    }


    @Test
    fun testParseNftZip()
    {
        // This is a small example NFT zip file
        val nftyZip = "504b03040a00000000005c8333582f3d08f59e0100009e01000009001c0063617264662e706e6755540900031fe9aa65b2e8aa6575780b000104e803000004e803000089504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082504b03041400000008000f878757428b8bb389000000b300000009001c00696e666f2e6a736f6e55540900038e3f72651ae8aa6575780b000104e803000004e8030000abe6525050cacb4c2ba90c4b2d52b25232d23350d20189956496e4a42a59292805e766e6a456820515401289a52519f9452019c7bc94a2d4728547d3f640b464a75696e717a51403e5a2151462c162c98925a9e9f9459540a3fddc4220ea120b0a4a8b32412640f8997969f9701e482025b1245149c14aa1ba162c9f93999c9a570c768c12572d1700504b01021e030a00000000005c8333582f3d08f59e0100009e010000090018000000000000000000b4810000000063617264662e706e6755540500031fe9aa6575780b000104e803000004e8030000504b01021e031400000008000f878757428b8bb389000000b3000000090018000000000001000000b481e1010000696e666f2e6a736f6e55540500038e3f726575780b000104e803000004e8030000504b050600000000020002009e000000ad0200000000".fromHex()
        // This is the image file it contains
        val cardfPng = "89504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082".fromHex()
        val (name, contents) = nftCardFront(nftyZip)
        assert(name == "cardf.png")
        assert(contents contentEquals cardfPng)

        val nftData = nftData(nftyZip)
        assert(nftData != null)
        if (nftData != null)
        {
            assert(nftData.niftyVer == "2.0")
            assert(nftData.author == "Andrew ▼")
            assert(nftData.appuri == "")
            assert(nftData.info == "")
            assert(nftData.license == "")
        }
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
        Assert.assertEquals(4, 2 + 2)

        ser.toByteArray()
        var tx2 = NexaTransaction(cs, ser)
        var ser2 = tx2.BCHserialize(SerializationType.NETWORK)
        ser2.toByteArray()
        LogIt.info("tx: " + ser2.toHex())
        assert(ser.toHex() == ser2.toHex())
    }

/*
    @Test
    fun test1()
    {
        val wal1 = openOrNewWallet("reg1", ChainSelector.NEXAREGTEST)
        val wal2 = openOrNewWallet("reg2", ChainSelector.NEXAREGTEST)
        wal1.blockchain.req.net.exclusiveNodes(setOf(FULL_NODE_IP))

        val addr1 = wal1.getnewaddress()
        val addr2 = wal2.getnewaddress()

        val node = openRpc()
        val AMT = 5000L
        val LOOP = 100

        node.sendtoaddress(addr1.toString(), BigDecimal.fromLong(AMT*LOOP + (LOOP * 5)))
        node.generate(1)
        millisleep(1000U)


        for (i in 1 .. LOOP)
        {
            println(i.toString() + ": Balance: ${wal1.balance} and ${wal2.balance}")
            wal1.send(AMT, addr2, false, true)
            if ((i % 10) == 0)
            {
                node.generate(1)
                waitFor(5000, { wal2.balanceUnconfirmed + wal2.balanceConfirmed == (i.toLong() * AMT) },
                  { "${wal2.balanceUnconfirmed} + ${wal2.balanceConfirmed} == ${(i * LOOP)}" })
            }
        }
        node.generate(1)
        check(wal2.balance == AMT * LOOP)
        wal2.send(wal2.balance, addr1, true, true)
        node.generate(1)
        check(wal2.balanceConfirmed == 0L)
    }
*/

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

}
