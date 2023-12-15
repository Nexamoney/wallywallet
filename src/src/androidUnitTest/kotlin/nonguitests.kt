import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.setLocale
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
