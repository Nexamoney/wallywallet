@file:OptIn(ExperimentalUnsignedTypes::class)
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.views.fastForwardAccount
import io.ktor.client.network.sockets.*
import okio.*
import okio.Path.Companion.toPath
import org.junit.Assert
import org.nexa.nexarpc.NexaRpcFactory
import org.nexa.libnexakotlin.*
import org.nexa.nexarpc.NexaRpc
import org.nexa.threads.millisleep
import java.util.concurrent.TimeoutException
import kotlin.test.*

val FULL_NODE_IP = "127.0.0.1"
val REGTEST_RPC_PORT=18332
/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class NonGuiTests
{
    val LogIt = GetLog("nonguitests")
    init
    {
        initializeLibNexa()
        runningTheTests = true
    }

    @Test
    fun testSort()
    {
        val lst = listOf(3,10,1,2)
        val sorted = lst.sortedBy { it }
        assert(sorted[0] == 1)
    }

    fun openRpc(): NexaRpc
    {
        val rpcConnection = "http://" + FULL_NODE_IP + ":" + REGTEST_RPC_PORT
        LogIt.info("Connecting to: " + rpcConnection)
        val rpc = NexaRpcFactory.create(rpcConnection)
        val tipIdx = rpc.getblockcount()
        if (tipIdx < 102)
            rpc.generate((101 - tipIdx).toInt())
        else
        {
            val tip = rpc.getblock(tipIdx)
            // The tip is so old that this node won't think its synced so we need to produce a block
            if (epochSeconds() - tip.time > 1000) rpc.generate(1)
        }
        return rpc
    }

    fun openEc(): ElectrumClient
    {
        val ec = ElectrumClient(ChainSelector.NEXAREGTEST, FULL_NODE_IP, DEFAULT_NEXAREG_TCP_ELECTRUM_PORT, useSSL=false)
        ec.start()
        val ver = ec.version()
        LogIt.info("Electrum server Version: ${ver.first} Protocol version: ${ver.second}")
        check(ver.first.startsWith("Rostrum"))
        check(ver.second == "1.4")  // Protocol version, will change
        return ec
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
    fun testManyNfts()
    {
        /* for debugging specific zip files
        if (true)
        {
            val nftyZip = FileSystem.SYSTEM.source("../exampleNft/cacf3d958161a925c28a970d3c40deec1a3fe06796fe1b4a7b68f377cdb900004ca0f7b7cde2254e62907c1656cf997091c7a92dc98635a372c51b98d4daaf9a.zip".toPath()).buffer()
            zipForeach(nftyZip) { info, data ->
                println("${info.fileName}: ${info.uncompressedSize}")
                false
            }
        }
         */

        val fname = "../exampleNft/tr9v70v4s9s6jfwz32ts60zqmmkp50lqv7t0ux620d50xa7dhyqqqx9r0ktuypj5t6sj7gplp7tl50lwr893e9y6vf33gmn4tmt3agjdr7nd0lhk.zip".toPath()
        println("file: ${fname.name}")
        val nftyZip = FileSystem.SYSTEM.source(fname).buffer()
        zipForeach(nftyZip) { info, data ->
            println("${info.fileName}: ${info.uncompressedSize}")
            if (info.fileName == "cardf.jpg")
            {
                check(libnexa.sha256(data!!.readByteArray()) contentEquals "6338e378724ac0822921b8b2ef791457b2bcb69ba153492fdb10276c22e134db".fromHex())
            }
            else if (info.fileName == "public.jpg")
            {
                check(libnexa.sha256(data!!.readByteArray()) contentEquals "53e9fc54356b3e276f9009941207f2b2f294ea4fe0abec9189a1d43f30efa269".fromHex())
            }
            else if (info.fileName == "info.json")
            {
                check(libnexa.sha256(data!!.readByteArray()) contentEquals "b200e71bdedd7b81eb8b2f95e402fced7e0dde2470a593db7e09588d8a6aafa8".fromHex())
            }
            else
            {
                check(false) // should be no other files
            }

            false
        }


        if (true)
        {
            // for every example file, parse it using 3 different methods and check that each method gives the same results
            // for every file within the zip.  This cannot test large files, because some of the methods pull the entire file
            // into ram as a ByteArray
            for (file in FileSystem.SYSTEM.list("../exampleNft".toPath()))
            {
                val results = mutableMapOf<String, ByteArray>()
                println("Trying file: ${file.name}")
                println("  as file:")
                zipForeach(EfficientFile(file, FileSystem.SYSTEM)) { info, data ->
                    println("  ${info.fileName}: ${info.uncompressedSize}")
                    results[info.fileName] = libnexa.sha256(data!!.readByteArray())
                    false
                }
                println("  as buffer:")
                val nftyZip2 = FileSystem.SYSTEM.source(file).buffer()
                zipForeach(nftyZip2) { info, data ->
                    println("${info.fileName}: ${info.uncompressedSize}")
                    val tmp = libnexa.sha256(data!!.readByteArray())
                    check(results[info.fileName] contentEquals  tmp)
                    false
                }

                println("  as bytes:")
                val nftyZipBytes = FileSystem.SYSTEM.source(file).buffer().readByteArray()
                zipForeach(nftyZipBytes) { info, data ->
                    println("${info.fileName}: ${info.uncompressedSize}")
                    val tmp = libnexa.sha256(data!!.readByteArray())
                    check(results[info.fileName] contentEquals  tmp)
                    false
                }

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

        fun checker(info: ZipDirRecord, data: BufferedSource?): Boolean
        {
            if (info.fileName == "cardf.png")
            {
                check(data!!.readByteArray() contentEquals  cardfPng)
            }
            if (info.fileName == "info.json")
            {
                val json = data!!.readByteArray().decodeUtf8()
                check(json == infoJson)
            }
            return false
        }

        // Try a bunch of different underlying data types
        zipForeach(nftyZip) {info, data -> checker(info,data)}
        val b = Buffer()
        b.write(nftyZip)
        zipForeach(b) {info, data -> checker(info,data)}
        zipForeach(b.peek()) {info, data -> checker(info,data)}
    }


    @Test
    fun testParseNftZip()
    {
        // This is a small example NFT zip file
        val nftyZip = "504b03040a00000000005c8333582f3d08f59e0100009e01000009001c0063617264662e706e6755540900031fe9aa65b2e8aa6575780b000104e803000004e803000089504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082504b03041400000008000f878757428b8bb389000000b300000009001c00696e666f2e6a736f6e55540900038e3f72651ae8aa6575780b000104e803000004e8030000abe6525050cacb4c2ba90c4b2d52b25232d23350d20189956496e4a42a59292805e766e6a456820515401289a52519f9452019c7bc94a2d4728547d3f640b464a75696e717a51403e5a2151462c162c98925a9e9f9459540a3fddc4220ea120b0a4a8b32412640f8997969f9701e482025b1245149c14aa1ba162c9f93999c9a570c768c12572d1700504b01021e030a00000000005c8333582f3d08f59e0100009e010000090018000000000000000000b4810000000063617264662e706e6755540500031fe9aa6575780b000104e803000004e8030000504b01021e031400000008000f878757428b8bb389000000b3000000090018000000000001000000b481e1010000696e666f2e6a736f6e55540500038e3f726575780b000104e803000004e8030000504b050600000000020002009e000000ad0200000000".fromHex()
        // This is the image file it contains
        val cardfPng = "89504e470d0a1a0a0000000d494844520000000a0000000a08060000008d32cfbd000001654944415418d335d0bf4b94711cc0f1f7e77bcf43c705a6965acf1d1ae2a4632e2d8f939b2e6781b438044a04de2e82d0e6a8a0706eb5393cfe0fe720482987884a461019fa44433e773eddf3f3e3a0bde7d7f416ee8bfd72550a2f6a6a8fbb0268f265378f0fd78acec50e80b943237578e4697ee92225544a6876e96a9c7b3747e53a80c4fef32af62b4fac49f2a889e4dba880f206d27ea28b65b25661c682bc661eccb27fda4babe53031d64173d83d99a4e2f431dc3740fcfbdda2111257cd008b1f3ed23cd8238acafcb91ee1ecf833ab1b1ea67b942c94090342d23e66e5fd34a5ee216ceb2b3dc513ccc30aafa75e12fe3c24fb6790c47fd6d05cdc8825447f61c927b40d9d708eb4f398ecc72649601a925c395504cff7952050061d431608df4fa118db74894d7ac38c00c4574e5d85f9b00de7cd94b46d187a52c40a0b2481d9aabc3d5f90ffc323dfa96a4c2d0bc44dae2dd2bf85461a98f5a7b3df76006e01b96e9d104f2eba150000000049454e44ae426082".fromHex()
        val (name, contents) = nftCardFront(EfficientFile(nftyZip))
        assert(name == "cardf.png")
        assert(contents contentEquals cardfPng)

        val nftData = nftData(EfficientFile(nftyZip))
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
            Spendable(cs, BchTxOutpoint("00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f", 0), 10001)
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


    /** This test creates a wallet and fills it with some transactions.
     * It then recovers this wallet using both blockchain sync and electrum fast forwarding,
     * and verifies that all techniques result in the same wallet.
     *
     * It then spends all UTXOs back to the full node, from the fast forwarded wallet, showing that all are spendable,
     * and verifies that all the wallet copies also see this spend
     */
    @Test
    fun testWalletRecovery()
    {
        val TIMEOUT = 1200000
        val cs = ChainSelector.NEXAREGTEST

        val rpc = try
        {
            openRpc()
        }
        catch(e: ConnectTimeoutException)
        {
            println("**TEST MALFUNCTION**  Test cannot connect to a full node (probably you did not start one)")
            return
        }
        catch(e: Exception)
        {
            println("**TEST MALFUNCTION**  Test cannot connect to a full node (probably you did not start one)")
            return
        }

        val blkStart = try
        {
            rpc.getblockcount()
        }
        catch(e: Exception)
        {
            println("**TEST MALFUNCTION**  Test cannot connect to a full node (probably you did not start one)")
            return
        }

        LogIt.info("Test starting block is: ${blkStart}")

        // clean up old runs
        deleteWalletFile("testwalletrecovery", wallyAccountDbFileName("testwalletrecovery"), cs)
        deleteWalletFile("a1", wallyAccountDbFileName("a1"), cs)
        deleteWalletFile("a2", wallyAccountDbFileName("a2"), cs)
        deleteWalletFile("a3", wallyAccountDbFileName("a3"), cs)

        REG_TEST_ONLY = true
        if (wallyApp == null)
        {
            wallyApp = CommonApp(true)
            wallyApp!!.onCreate()
            wallyApp!!.openAllAccounts()
        }

        val account = wallyApp!!.newAccount("testwalletrecovery", 0U, "", cs)!!
        account.start()
        rpc.generate(1)
        waitFor(TIMEOUT, {  account.wallet.synced() }, {
            LogIt.info("sync bad")
            "sync unsuccessful, at: ${account.wallet.chainstate!!.syncedHeight}"
        } )

        // New account stats should be empty (if not, its getting data from the deleted account
        val aEmpty = account.wallet.statistics()
        LogIt.info("Empty stats: $aEmpty")
        check(aEmpty.totalTxos==0)
        check(aEmpty.numTransactions==0)
        check(aEmpty.numUnspentTxos==0)

        val NUM_REPEATS = 5
        var amtInWallet = 0L
        repeat(NUM_REPEATS) {
            val addr = account.wallet.getnewaddress()
            rpc.sendtoaddress(addr.toString(), CurrencyDecimal(10000))
            rpc.sendtoaddress(addr.toString(), CurrencyDecimal(10000))
            amtInWallet += 2* CurrencyDecimal(10000).toInt()* NEX
            rpc.generate(1)
            val now = rpc.getblockcount()
            account.wallet.sync(10000, now)
            val tx = account.wallet.send(80000, account.wallet.getnewaddress())  // add a few send-to-self in there because they are different
            amtInWallet -= tx.fee  // sending to myself so just lose the fee
            millisleep(200U)
        }

        // Add a little more because since I've been sending to myself, the balance won't be accurate
        rpc.sendtoaddress(account.wallet.getnewaddress().toString(), CurrencyDecimal(1000))
        amtInWallet += 1000*NEX
        rpc.generate(1)
        val nowBlock = rpc.getblockcount()

        waitFor(TIMEOUT, { account.wallet.synced(nowBlock ) }, { "sync unsuccessful" })
        waitFor(TIMEOUT, { account.wallet.balance > 5*2000000L}, { "wallet load failed"})
        val balance = account.wallet.balance

        val aStat = account.wallet.statistics()

        // Ok, now let's recover this account into new accounts and compare
        println("Recovering from ${blkStart-1}")
        val a1 = wallyApp!!.recoverAccount("a1", 0U, "", account.wallet.secretWords, cs, null, blkStart-1, null)
        waitFor(TIMEOUT, { a1.wallet.synced() }, { "a1 sync unsuccessful" })
        val a1Stat = a1.wallet.statistics()
        LogIt.info("calculated balance: $amtInWallet")
        LogIt.info("recovered balance: ${a1.wallet.balance}  orig bal: $balance at $nowBlock")
        LogIt.info("original stats: $aStat")
        LogIt.info("recovered stats: $a1Stat")
        check(a1.wallet.balance == balance)

        val ec = openEc()
        waitFor(TIMEOUT, {
            val tmp = ec.getTip()
            tmp.second == nowBlock}, { "electrum server never synced"})
        val (ectip, _) = ec.getTip()
        val addressDerivationCoin = Bip44AddressDerivationByChain(cs)
        val srchResults = SearchDerivationPathActivity(cs, {ec }, true) {}.search(100) {
            val secret = libnexa.deriveHd44ChildKey(account.wallet.secret, AddressDerivationKey.BIP44, addressDerivationCoin, 0, false, it).first
            Pay2PubKeyTemplateDestination(cs, UnsecuredSecret(secret), it.toLong())
        }

        check(ectip.height == nowBlock)

        LogIt.info("EC bal: ${srchResults.balance} at ${srchResults.lastHeight}")
        if (srchResults.balance != balance)
        {
            LogIt.warning("Electrum balance differs from wallet")
        }
        //check(srchResults.balance == balance)
        check(srchResults.txh.size == NUM_REPEATS*3 + 1)
        check(srchResults.addrCount > NUM_REPEATS*2 + 1)

        val a2 = wallyApp!!.recoverAccount("a2", 0U, "", account.wallet.secretWords, cs, srchResults.txh.values.toList(), srchResults.addresses, ectip, srchResults.addrCount.toInt())

        val a3 = wallyApp!!.recoverAccount("a3", 0U, "", account.wallet.secretWords, cs, srchResults.txh.values.toList(), srchResults.addresses, ectip, srchResults.addrCount.toInt())
        fastForwardAccount(a3)

        waitFor(TIMEOUT, { a2.wallet.synced(nowBlock) }, { "a2 sync unsuccessful" })
        waitFor(TIMEOUT, { a3.wallet.synced(nowBlock) }, { "a2 sync unsuccessful" })
        LogIt.info("a2 balance: ${a2.wallet.balance}  orig bal: $balance")
        LogIt.info("a3 balance: ${a2.wallet.balance}  orig bal: $balance")
        check(amtInWallet == balance)
        check(a2.wallet.balance == balance)
        check(a3.wallet.balance == balance)


        // Now spend ALL the utxos using the electrum client recovery mechanism
        val returnAddr = rpc.getnewaddress()
        val tx = a2.wallet.send(a2.wallet.balance,returnAddr, true)
        waitFor(TIMEOUT, { rpc.gettxpoolinfo().size > 0 }, { "send all did not work: $tx"})
        check(a2.wallet.balance == 0L)
        rpc.generate(1)

        waitFor(TIMEOUT, { account.wallet.balance == 0L}, { "send all did not work: $tx"})
        check(a1.wallet.balance == 0L)
        LogIt.info("recovery test completed")
    }



    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

}
