package info.bitcoinunlimited.www.wally.old

import android.database.sqlite.SQLiteDatabase
import info.bitcoinunlimited.www.wally.ACCOUNT_FLAG_NONE
import info.bitcoinunlimited.www.wally.WallyApp
import info.bitcoinunlimited.www.wally.wallyApp
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("oldPersist")

/** Helper class that saves/loads data needed by the Bip44Wallet */
class OldBip44WalletData() : BCHserializable
{
    var id: String = ""
    var secretWords: String = ""
    var maxAddress: Int = 0
    var chainSelector: ChainSelector = ChainSelector.NEXA

    override fun BCHserialize(format: SerializationType): BCHserialized
    {
        return BCHserialized(format) + id + secretWords + BCHserialized.uint32(maxAddress.toLong()) + BCHserialized.uint16(chainSelector.v.toLong())
    }

    override fun BCHdeserialize(stream: BCHserialized): BCHserialized
    {
        id = stream.deString()
        secretWords = stream.deString()
        maxAddress = stream.deint32()
        try  // TODO remove after a bit
        {
            chainSelector = ChainSelectorFromValue(stream.deuint16().toByte())
        }
        catch (e: Exception)
        {
            // old save format so use default chainselector
        }
        return stream
    }
}


fun oldRawOpenKvpDB(app: WallyApp, name: String): SQLiteDatabase?
{
    val dbn = app.getDatabasePath(name)
    if (!dbn.exists())
    {
        return null
    }

    val db = SQLiteDatabase.openDatabase(dbn, SQLiteDatabase.OpenParams.Builder().addOpenFlags(SQLiteDatabase.OPEN_READONLY).build())
    return db
}

fun convertOldAccounts(app: WallyApp): Boolean
{
    val db = oldRawOpenKvpDB(app, "bip44walletdb")
    if (db == null) return false
    var numAccounts = 0
    if (true)
    {
        val cursor = db.query("KvpData", null, null, null, null, null, null)

        var activeAccountNames:List<String>? = null
        val walData:MutableMap<String, ByteArray> = mutableMapOf()
        val pinData:MutableMap<String, ByteArray> = mutableMapOf()
        val flagsData:MutableMap<String, ULong> = mutableMapOf()
        val chainstateData:MutableMap<String, Pair<Long, Long>> = mutableMapOf()
        if (cursor.moveToFirst())
        {
            while (!cursor.isAfterLast)
            {
                val bakey = cursor.getBlob(0)
                val baval = cursor.getBlob(1)
                val keyStr = bakey.decodeUtf8()
                // LogIt.info(keyStr + " -> " + (if (baval.size > 1000) baval.sliceArray(0 .. 200).toHex() else baval.toHex()))
                if (keyStr == "activeAccountNames") activeAccountNames = String(baval).split(",")
                if (keyStr.startsWith("bip44wallet_"))
                {
                    walData[keyStr] = baval
                }
                if (keyStr.startsWith("accountPin_"))
                {
                    pinData[keyStr] = baval
                }
                if (keyStr.startsWith("accountFlags_"))
                {
                    val ser = BCHserialized(baval, SerializationType.NETWORK)
                    flagsData[keyStr] = ser.deuint32().toULong()
                }
                if (keyStr.contains("_chainstate_"))
                {
                    val stream = BCHserialized(baval, SerializationType.NETWORK)
                    val csver = stream.debyte()
                    val csver2 = stream.debyte()
                    val syncedHeight = stream.deuint64()
                    val prehistoryDate = stream.deuint64()
                    val prehistoryHeight = stream.deuint64()
                    chainstateData[keyStr] = Pair(prehistoryDate, prehistoryHeight)

                }
                cursor.moveToNext()
            }
            cursor.close()
        }

        for(w in activeAccountNames ?: listOf())
        {
            val d = walData["bip44wallet_" + w]
            if (d != null)
            {
                val epin = pinData["accountPin_" + w]
                val flags = flagsData["accountFlags_" + w] ?: ACCOUNT_FLAG_NONE
                val wd: OldBip44WalletData = OldBip44WalletData()
                wd.BCHdeserialize(BCHserialized(d, SerializationType.DISK))
                // LogIt.info("Convert ${wd.id} ON ${wd.chainSelector} recoveryWords: ${wd.secretWords} maxAddress: ${wd.maxAddress}")

                val cs = chainstateData["wallet_" + w + "_chainstate_" + wd.chainSelector]

                val earliestActivity = cs?.first
                val earliestHeight = cs?.second

                val acc = wallyApp!!.recoverAccount(w, flags, "",  wd.secretWords, wd.chainSelector, earliestActivity, earliestHeight, null)
                // Write the encoded pin directly because we do not know its unencoded value
                epin?.let { acc?.saveAccountPin(it) }
                numAccounts += 1
            }
        }
    }
    db.close()
    return numAccounts > 0
}