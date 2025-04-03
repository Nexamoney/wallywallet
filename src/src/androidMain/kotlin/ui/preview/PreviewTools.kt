package info.bitcoinunlimited.www.wally.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.gatherRelevantAddresses
import info.bitcoinunlimited.www.wally.ui2.ScreenId
import info.bitcoinunlimited.www.wally.ui2.ScreenNav
import org.nexa.libnexakotlin.*

class RamKvpDatabase: KvpDatabase
{
    val data = mutableMapOf<ByteArray, ByteArray>()
    override fun clear()
    {
        data.clear()
    }

    override fun close()
    {
        data.clear()
    }

    override fun delete(key: ByteArray)
    {
        data.remove(key)
    }

    override fun get(key: ByteArray): ByteArray
    {
        return data.get(key)!!
    }

    override fun getOrNull(key: ByteArray): ByteArray?
    {
        return data.get(key)
    }

    override fun set(key: ByteArray, value: ByteArray): Boolean
    {
        data[key] = value
        return true
    }
}

class FakeTxDatabase: TxDatabase
{
    val data = mutableMapOf<ByteArray, TransactionHistory>()
    override fun clear()
    {
        data.clear()
    }

    override fun close()
    {
        data.clear()
    }

    override fun delete(vararg idems: ByteArray)
    {
        for (ba in idems)
        {
            data.remove(ba)
        }
    }

    override fun forEach(doit: (TransactionHistory) -> Boolean, startingDate: Long, count: Long)
    {
        for(th in data.values)
        {
            if (th.date <= startingDate)
                if (doit(th)) return
        }
    }

    override fun forEachWithAddress(addr: PayAddress, doit: (TransactionHistory) -> Unit)
    {
        for(th in data.values)
        {
            if (addr in th.gatherRelevantAddresses()) doit(th)
        }
    }

    override fun read(idem: Hash256): TransactionHistory?
    {
        return data[idem.hash]
    }

    override fun readAll(): MutableMap<Hash256, TransactionHistory>
    {
        val ret = mutableMapOf<Hash256, TransactionHistory>()
        for (d in data)
        {
            ret[Hash256(d.key)] = d.value
        }
        return ret
    }

    override fun size(): Long
    {
        return data.size.toLong()
    }

    override fun write(vararg txh: TransactionHistory?)
    {
        for(t in txh)
        {
            if (t!=null)
            {
                data[t.tx.idem.hash] = t
            }
        }
    }

    override fun writeAll(fullmap: MutableMap<Hash256, TransactionHistory>)
    {
        for(t in fullmap)
        {
            data[t.key.hash] = t.value
        }
    }

    override fun writeDirty(fullmap: MutableMap<Hash256, TransactionHistory>): Int
    {
        var count = 0
        for(t in fullmap)
        {
            if (t.value.dirty)
            {
                data[t.key.hash] = t.value
                t.value.dirty = false
                count++
            }
        }
        return count
    }
}

class RamTxoDatabase: TxoDatabase
{
    val data = mutableMapOf<ByteArray, Spendable>()
    override fun clear()
    {
        data.clear()
    }
    override fun close()
    {
        data.clear()
    }

    override fun delete(outpoint: ByteArray)
    {
        data.remove(outpoint)
    }

    override fun delete(outpoints: Collection<iTxOutpoint>)
    {
        for (o in outpoints) delete(o)
    }

    override fun delete(outpoint: iTxOutpoint)
    {
        delete(outpoint.toByteArray())
    }

    override fun forEach(doit: (Spendable) -> Boolean)
    {
        for(d in data.values)
        {
            if (doit(d)) return
        }
    }

    override fun forEachUtxo(doit: (Spendable) -> Boolean)
    {
        for(d in data.values)
        {
            if (d.isUnspent && doit(d)) return
        }
    }

    override fun forEachUtxoWithAddress(addr: PayAddress, doit: (Spendable) -> Boolean)
    {
        for(d in data.values)
        {
            if (d.addr == addr && d.isUnspent && doit(d)) return
        }
    }

    override fun forEachWithAddress(addr: PayAddress, doit: (Spendable) -> Boolean)
    {
        for(d in data.values)
        {
            if (d.addr == addr && doit(d)) return
        }
    }

    override fun numTxos(): Long
    {
        return data.size.toLong()
    }

    override fun numUtxos(): Long
    {
        var count = 0L
        for(d in data.values)
        {
            if (d.isUnspent) count++
        }
        return count
    }

    override fun read(outpoint: ByteArray): Spendable?
    {
        return data[outpoint]
    }

    override fun read(outpoint: iTxOutpoint): Spendable?
    {
        return data[outpoint.toByteArray()]
    }

    override fun readMany(outpoints: Collection<iTxOutpoint>): Map<iTxOutpoint, Spendable>
    {
        val ret = mutableMapOf<iTxOutpoint, Spendable>()
        for (outpoint in outpoints)
        {
            data[outpoint.toByteArray()]?.let { ret[outpoint] = it }
        }
        return ret
    }

    override fun readAll(): MutableMap<iTxOutpoint, Spendable>
    {
        val ret = mutableMapOf<iTxOutpoint, Spendable>()
        for (d in data)
        {
            ret[outpointFor(ChainSelector.NEXAREGTEST, BCHserialized(SerializationType.DISK,d.key))] = d.value
        }
        return ret
    }

    override fun write(vararg splist: Spendable?)
    {
        for (sp in splist)
        {
            if (sp!=null)
            {
                sp.outpoint?.let { data[it.toByteArray()] = sp }
            }
        }
    }

    override fun write(splist: Collection<Spendable?>)
    {
       for (sp in splist)
        {
            if (sp!=null)
            {
                sp.outpoint?.let { data[it.toByteArray()] = sp }
            }
        }
    }

    override fun writeAll(fullmap: MutableMap<iTxOutpoint, Spendable>)
    {
        for (sp in fullmap)
        {
            data[sp.key.toByteArray()] = sp.value
        }
    }

    override fun writeDirty(fullmap: MutableMap<iTxOutpoint, Spendable>): Int
    {
        var ret  = 0
        for (sp in fullmap)
        {
            if (sp.value.dirty)
            {
                sp.value.dirty = false
                data[sp.key.toByteArray()] = sp.value
                ret++
            }
        }
        return ret
    }

}

class RamWalletDatabase: WalletDatabase
{
    override val kvp: KvpDatabase = RamKvpDatabase()
    override val tx: TxDatabase = FakeTxDatabase()
    override val txo: TxoDatabase = RamTxoDatabase()

    override fun close()
    {
        kvp.close()
        tx.close()
        txo.clear()
    }
}

data class PreviewObjects(val nav: ScreenNav, val accounts: Set<Account>)
@OptIn(ExperimentalUnsignedTypes::class)
@Composable
fun setUpPreview(accounts: Int, pos: ScreenId = ScreenId.Home, language: String="en", country:String="us"): PreviewObjects
{
    dbPrefix = "preview_"
    androidContext = LocalContext.current
    setLocale(language, country, LocalContext.current)
    val w:CommonApp = wallyApp?.let { it } ?: run {
        val ret = CommonApp(true)
        ret.onCreate()
        ret
    }
    try
    {
        val name = dbPrefix + "wpw"
        if (kvpDb == null) kvpDb = openKvpDB(name)
    }
    catch(e:Exception)
    {
        println(e)
    }
    wallyApp = w
    if (accounts > 0) initializeLibNexa()
    val acts = mutableSetOf<Account>()
    for (i in 0 until accounts)
    {
        val account = Account("test${i}", chainSelector = ChainSelector.NEXAREGTEST, secretWords = "carpet cat flower chair foot river make image amazing three say shoe", db = RamWalletDatabase())
        w.accounts[account.name] = account
        acts.add(account)
    }

    val nav = ScreenNav()
    nav.push(pos)

    return(PreviewObjects(nav, acts))
}