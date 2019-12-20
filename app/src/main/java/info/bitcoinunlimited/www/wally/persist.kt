// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package bitcoinunlimited.libbitcoincash

import android.content.Context
import android.database.sqlite.SQLiteBlobTooBigException
import android.database.sqlite.SQLiteConstraintException
import androidx.room.*
import java.math.BigInteger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.logging.Logger
import android.provider.SyncStateContract.Helpers.update
import info.bitcoinunlimited.www.wally.R


open class DataMissingException(msg:String): BUException(msg, i18n(R.string.dataMissing))


private val LogIt = Logger.getLogger("bitcoinunlimited.blockchain.persist")

@Entity
class KvpData()
{
    @PrimaryKey
    var id: ByteArray = byteArrayOf()
    var value: ByteArray = byteArrayOf()

    constructor(k:ByteArray, v:ByteArray):this()
    {
        id = k
        value = v
    }
}

@Dao
interface KvpDao
{
    @Query("SELECT * FROM KvpData WHERE id = :key")
    abstract fun get(key: ByteArray): KvpData

    @Insert
    fun insert(bh: KvpData)

    @Update
    fun update(bh: KvpData)

    @Delete
    fun delete(bh: KvpData)

    @Query("DELETE FROM KvpData")
    fun deleteAll()
}

fun KvpDao.upsert(d: KvpData): Boolean
{
    try
    {
        insert(d)
    }
    catch (exception: SQLiteConstraintException)
    {
        update(d)
    }
    return true
}

@Database(entities = arrayOf(KvpData::class), version = 1)
abstract class KvpDatabase : RoomDatabase()
{
    abstract fun dao(): KvpDao

    /** update or insert a key value pair into the database */
    fun set(key: ByteArray, value: ByteArray) = dao().upsert(KvpData(key, value))
    /** look up the passed key, throwing DataMissingException if it does not exist */
    fun get(key: ByteArray): ByteArray
    {
        try
        {
            val kvp = dao().get(key)
            if (kvp == null) throw DataMissingException("Missing key: " + String(key))
            return kvp.value
        }
        catch (e: SQLiteBlobTooBigException)
        {
            LogIt.info("Stored data is corrupt, rediscovering: ${e.toString()}")
            throw DataMissingException("Blob too big: " + String(key))
        }
    }
    /** look up the passed key, returning the value or null if it does not exist */
    fun getOrNull(key: ByteArray): ByteArray?
    {
        val kvp = dao().get(key)
        if (kvp == null) return null
        return kvp.value
    }

    /** update or insert a key value pair into the database */
    fun set(key: String, value: ByteArray) = set(key.toByteArray(), value)
    /** look up the passed key, returning the value or throwing DataMissingException */
    fun get(key: String): ByteArray = get(key.toByteArray())
    /** look up the passed key, returning the value or null if it does not exist */
    fun getOrNull(key: String): ByteArray? = getOrNull(key.toByteArray())

    /** delete a record */
    fun delete(key: String) = delete(key.toByteArray())

    /** delete a record */
    fun delete(key:ByteArray)
    {
        dao().delete(KvpData(key,byteArrayOf()))
    }
    /*
    fun saveBip44Wallet(wd: Bip44WalletData)
    {
        val dbao = dao()
        while (true)
        {
            try
            {
                //TODO dbao.upsert(wd)
            }
            catch (e: android.database.sqlite.SQLiteConstraintException)
            {
                val sqle = e
                if (sqle.toString().contains("SQLITE_CONSTRAINT_PRIMARYKEY"))
                {
                    LogIt.info("Inserting duplicate block header")
                    return  // Its ok to insert the same block header, but a waste
                }
                if (!sqle.toString().contains("SQLITE_BUSY"))
                    throw(e)
            }
        }
    }

    fun loadBip44Wallet(id: String): Bip44WalletData
    {
        val dbao = dao()
        val ret = dbao.get(id)
        if (ret == null)
            throw(DataMissingException())
        var result:Bip44WalletData = Bip44WalletData()
        result.BCHdeserialize(BCHserialized(ret.value,SerializationType.DISK))
        return result
    }
    */
}

fun OpenKvpDB(context: PlatformContext, name: String): KvpDatabase?
{
    val db = Room.databaseBuilder(context.context, KvpDatabase::class.java, name).build()
    return db
}


@Entity
open class PBlockHeader():BlockHeader()
{
    @PrimaryKey
    @ColumnInfo(name="id")
    var dbkey: ByteArray = byteArrayOf()

    constructor(b: BlockHeader):this()
    {
        // repeat the search columns because IDK how to get room to work for base class fields
        dbkey = b.hashData.hash
        initialize(b)
    }

    /** This constructor lets you override the hash and canned values are used for "known" blocks like the tip */
    constructor(h: ByteArray, b: BlockHeader):this()
    {
        dbkey = h
        initialize(b)
    }

    fun initialize(b:BlockHeader)
    {
        hexHash = b.hexHash
        hashData = b.hashData
        version = b.version
        hashPrevBlock = b.hashPrevBlock
        hashMerkleRoot = b.hashMerkleRoot
        time = b.time
        diffBits = b.diffBits
        nonce = b.nonce

        numTx = b.numTx
        blockSize = b.blockSize
        cumulativeWork = b.cumulativeWork
        height = b.height
    }
}

class Hash256Converters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): Hash256?
    {
        return value?.let { Hash256(it) }
    }

    @TypeConverter
    fun toByteArray(bid: Hash256?): ByteArray?
    {
        return bid?.hash
    }
}

class BigIntegerConverters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): BigInteger?
    {
        return value?.let {
            var ret = 0.toBigInteger()
            for (b in it)
            {
                ret = ret.shiftLeft(8)
                ret += b.toPositiveInt().toBigInteger()
            }
            ret
        }
    }

    @TypeConverter
    fun toByteArray(bid: BigInteger?): ByteArray?
    {
        if (bid==null) return null
        var ret = ByteArray(32)
        var value:BigInteger = bid
        for (i in 1..32)  // By converting by hand we are sure that the byte order means that lexicographical compare is equivalent to numerical compare
        {
            ret[32-i] = value.and(255.toBigInteger()).toByte()
            value = value.shiftRight(8)

        }
        return ret
    }
}


@Dao
interface BlockHeaderDao
{
    @Query("SELECT * FROM pblockHeader")
    fun getAll(): List<PBlockHeader>

    @Query("SELECT * FROM pblockHeader WHERE height IN (:heights)")
    fun loadAllByHeight(heights: IntArray): List<PBlockHeader>

    @Query("SELECT * FROM pblockHeader WHERE height = :height")
    fun get(height: Long): PBlockHeader

    @Query("SELECT * FROM pblockHeader WHERE height = :height")
    fun getAtHeight(height: Long): List<PBlockHeader>

    @Query("SELECT * FROM pblockHeader WHERE id = :blockid")
    abstract fun get(blockid: ByteArray): PBlockHeader

    //@Query("SELECT * FROM blockHeader x INNER JOIN (SELECT height, MAX(height) FROM blockHeader GROUP BY height) y ON x.height = y.height")
    @Query("SELECT a.* FROM pblockHeader a LEFT OUTER JOIN pblockHeader b ON a.height < b.height WHERE b.height IS NULL")
    fun getLast(): PBlockHeader

    @Query("SELECT a.* FROM pblockHeader a LEFT OUTER JOIN pblockHeader b ON a.cumulativeWork < b.cumulativeWork WHERE b.cumulativeWork IS NULL")
    fun getMostWork(): List<PBlockHeader>

    @Insert
    fun insert(vararg bh: PBlockHeader)

    @Update
    fun update(bh: PBlockHeader)

    @Delete
    fun delete(bh: PBlockHeader)

    @Query("DELETE FROM pblockHeader WHERE height = :height")
    fun delete(height: Long)

    @Query("DELETE FROM pblockHeader")
    fun deleteAll()

}

fun BlockHeaderDao.getCachedTip() = get(byteArrayOf(0))

fun BlockHeaderDao.setCachedTip(header: BlockHeader)
{
    val pbh = PBlockHeader(byteArrayOf(0), header)
    try
    {
        insert(pbh)
    }
    catch (exception: SQLiteConstraintException)
    {
        update(pbh)
    }

}

fun PersistInsert(dbdao: BlockHeaderDao, bh: BlockHeader)
{
    if (bh.hash.toHex() == "0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206")
    {
        LogIt.info("DBG: writing genesis block ${bh.cumulativeWork}")
        if (bh.cumulativeWork > BigInteger("10"))
        {
            LogIt.warning("BUG!")
        }
    }

    while (true)
    {
        try
        {
            dbdao.insert(PBlockHeader(bh))
            return
        }
        catch (e: android.database.sqlite.SQLiteConstraintException)
        {
            print(e.toString())
            val sqle = e
            if (sqle.toString().contains("SQLITE_CONSTRAINT_PRIMARYKEY"))
            {
                LogIt.info("Inserting duplicate block header")
                return  // Its ok to insert the same block header, but a waste
            }
            if (!sqle.toString().contains("SQLITE_BUSY"))
                throw(e)
        }
    }
}

@Database(entities = arrayOf(PBlockHeader::class), version = 1)
@TypeConverters(Hash256Converters::class, BigIntegerConverters::class)
abstract class BlockHeaderDatabase : RoomDatabase() {
    abstract fun blockHeaderDao(): BlockHeaderDao
}

fun OpenBlockHeaderDB(context: PlatformContext, name: String): BlockHeaderDatabase?
{
    val db = Room.databaseBuilder(context.context, BlockHeaderDatabase::class.java, name).build()
    return db
}