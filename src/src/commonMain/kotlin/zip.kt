@file:Suppress("unused")
@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalTime::class)

package org.nexa.assets
import okio.*
import kotlin.time.Clock
import kotlinx.datetime.*
import kotlin.also
import kotlin.time.ExperimentalTime
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("wally.zip")

// see https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT 4.4.2 version made by (2 bytes), will choose the simplest msdos/2.0 version
// 2.0: (1993)[1] File entries can be compressed with DEFLATE and use traditional PKWARE encryption (ZipCrypto).
val VERSION_MADE_BY = 2*10 + 0

// All of our major files are already compressed media so we just store everything without compression
val COMPRESSION_NONE = 0

fun ByteArray.toBuffer(): Buffer
{
    val b = Buffer()
    b.write(this)
    return b
}

// Zero allocation partial comparison.  What's faster, doing this by hand or making a new ByteArray with takeLast?
fun ByteArray.endsWith(suffix: ByteArray): Boolean
{
    if (suffix.size > this.size) return false
    val start = this.size - suffix.size
    for (i in suffix.indices) {
        if (this[start + i] != suffix[i]) return false
    }
    return true
}


expect fun inflateRfc1951(compressedBytes: ByteArray, expectedfinalSize: Long):ByteArray
/*  this works for most platforms
actual fun inflateRfc1951(compressedBytes: ByteArray, expectedfinalSize: Long): ByteArray
{
    val inf = Inflater(true)  // true means do not wrap in the gzip header

    inf.setInput(compressedBytes)
    val ba = ByteArray(expectedfinalSize.toInt())
    val sz = inf.inflate(ba)
    if (sz != expectedfinalSize.toInt()) throw Exception("inflate wrong size")
    inf.end()
    return ba
}
 */

/*
object Drain:Sink
{
    override fun close() {}
    override fun flush() {}
    override fun timeout(): Timeout = Timeout.NONE
    override fun write(source: Buffer, byteCount: Long) {}
}
 */

fun BufferedSource.readAndClose():ByteArray
{
    val ret = readByteArray()
    close()
    return ret
}

fun BufferedSource.readAndClose(len: Long):ByteArray
{
    val ret = readByteArray(len)
    close()
    return ret
}

/** This class wraps a file object that attempts to save memory by reducing the number of copies of the data needed in order to
 * run multiple random-access file operations.  Since okio is incapable of "rewinding", this can be hard to do */
class EfficientFile(var openAt: ((Long) -> BufferedSource))
{
    var handle: FileHandle? = null
    var sz:Long? = null

    val size: Long
        get() = sz!!  // You need to have inited the size during construction

    constructor(bytes: BufferedSource): this({
        val tmp = bytes.peek()
        tmp.skip(it)
        tmp
    })
    {
        // just find the file size
        val bs = openAt(0)
        //bs.read(Drain)
        //sz = bs.readAll(Drain)  // There is a bug in okio as of 3.9.1 -- readAll is NOT returning the number of bytes read.
        //bs.close()
        val ba = bs.readAndClose()
        sz = ba.size.toLong()
    }

    // If the entire file has been put into a buffer
    constructor(bytes: Buffer): this({
        val ret = bytes.peek()
        ret.skip(it)
        ret
    })
    {
        sz = bytes.size
    }
    constructor(bytes: ByteArray): this(bytes.toBuffer()) {
        sz = bytes.size.toLong()
    }

    constructor(filePath: Path, fileSystem: FileSystem): this({ Buffer() })
    {
        val tmp = fileSystem.openReadOnly(filePath)
        handle = tmp
        sz = tmp.size()
        openAt = { tmp.source(it).buffer() }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun hash256():ByteArray
    {
        val instrm = openAt(0)
        val zb = instrm.readAndClose()
        val nftHash = libnexa.hash256(zb)
        return nftHash
    }

    fun close() { handle?.close(); handle = null }

    protected fun finalize()
    {
        close()
    }
}

// Make the bytes of read and write completely clear, not using names like short, int and long that may differ per platform
fun BufferedSource.readLE8():Long = readLongLe()
fun BufferedSource.readLE4():Long = readIntLe().toLong()
fun BufferedSource.readLE2():Int = readShortLe().toInt()

fun BufferedSink.writeLE8(i: Long) = writeLongLe(i)
fun BufferedSink.writeLE4(i: Int) = writeIntLe(i)
fun BufferedSink.writeLE4(i: Long) = writeIntLe(i.toInt())
fun BufferedSink.writeLE2(i: Int) = writeShortLe(i)


open class ZipException: Exception()

class ZipRecordException(val id: ByteArray):ZipException()

val ZipFileHeaderId = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
val ZipDirRecordId = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
val ZipDirEndId = byteArrayOf(0x50, 0x4b, 0x05, 0x06)
val ZipDataDescriptorId = byteArrayOf(0x50, 0x4b, 0x07, 0x08)

enum class ZipCompressionMethods(v: Int)
{
    NoCompression(0),
    Shrunk(1),
    Reduced1(2),
    Reduced2(3),
    Reduced3(4),
    Reduced4(5),
    Imploded(6),
    Reserved7(7),
    Deflated(8),
    EnhancedDeflated(9),
    PKWareDclImploded(10),
    Reserved11(11),
    Bzip2(12),
    Reserved(13),
    LZMA(14),
    IbmTerse(18),
    IbmLz77z(19),
    PPMdVi_1(98)
}


// CRC-32 (IEEE 802.3)
private val CRC32_TABLE: UIntArray = UIntArray(256).also { t ->
    for (i in 0U until 256U) {
        var c = i
        repeat(8) { c = if ((c and 1U) != 0U) 0xEDB88320.toUInt() xor (c shr 1) else (c shr 1) }
        t[i.toInt()] = c
    }
}
fun crc32(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): UInt
{
    var crc = 0xFFFFFFFFu
    for (i in offset until offset + length)
    {
        val b = bytes[i].toUInt() and 0xFFu
        val idx = ((crc xor b) and 0xFFu).toInt()
        crc = (crc shr 8) xor CRC32_TABLE[idx]
    }
    return crc xor 0xFFFFFFFFu
}


data class ZipDataDescriptor(
  val structureId: ByteArray,
  val crc32: Long,
  val compressedSize: Long,
  val uncompressedSize: Long,
)
{
    companion object
    {
        fun from(ds: BufferedSource): ZipDataDescriptor
        {
            val structId: ByteArray = ds.peek().readAndClose(4)
            if (!(structId contentEquals ZipDataDescriptorId))
            {
                // may be benign so not log: LogIt.info("ZipDataDescriptor: Incorrect zip record: ${structId.toHex()}")
                throw ZipRecordException(structId)
            }
            ds.readByteArray(4)  // drop what I peeked
            val crc32: Long = ds.readLE4()
            val compressedSize: Long = ds.readLE4()
            val uncompressedSize: Long = ds.readLE4()
            return ZipDataDescriptor(structId, crc32, compressedSize, uncompressedSize)
        }
    }

    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (other !is ZipDataDescriptor) return false

        if (crc32 != other.crc32) return false
        if (compressedSize != other.compressedSize) return false
        if (uncompressedSize != other.uncompressedSize) return false
        if (!structureId.contentEquals(other.structureId)) return false

        return true
    }

    override fun hashCode(): Int
    {
        var result = crc32.hashCode()
        result = 8191 * result + compressedSize.hashCode()
        result = 8191 * result + uncompressedSize.hashCode()
        result = 8191 * result + structureId.contentHashCode()
        return result
    }
}


/** Named "Central directory file header (CDFH)" in the specs.
 *  Every file needs both this record (at the end of the zip) and a [ZipFileHeader] (before the actual file).
 */
data class ZipDirRecord(
  val structureId: ByteArray,
  val versionMadeBy: Int,
  val versionToExtract: Int,
  val bitFlag:Int,
  val compression: Int,
  val lastModTime: Int,
  val lastModDate: Int,
  val crc32: Long,
  var compressedSize: Long,
  var uncompressedSize: Long,
  val fileNameLength: Int,
  val extraFieldLength: Int,
  val fileCommentLength: Int,
  val diskNumberStart: Int,
  val internalFileAttrs: Int,
  val externalFileAttrs: Long,
  val localHeaderOffset: Long,
  val fileName: String,
  val extra: ByteArray,
  val comment: String
)
{
    companion object
    {
        fun from(ds: BufferedSource): ZipDirRecord
        {
            val structId: ByteArray = ds.peek().readAndClose(4)
            if (!(structId contentEquals ZipDirRecordId))
            {
                // may be benign so not log:  LogIt.info("ZipDirRecord: Incorrect zip record: ${structId.toHex()}")
                throw ZipRecordException(structId)
            }
            ds.readByteArray(4)  // drop what I peeked
            val versionMadeBy: Int = ds.readLE2()
            val versionToExtract: Int = ds.readLE2()
            val bitFlag:Int = ds.readLE2()
            val compression: Int = ds.readLE2()
            val lastModTime: Int = ds.readLE2()
            val lastModDate: Int = ds.readLE2()
            val crc32: Long = ds.readLE4()
            val compressedSize: Long = ds.readLE4()
            val uncompressedSize: Long = ds.readLE4()
            val fileNameLength: Int = ds.readLE2()
            val extraFieldLength: Int = ds.readLE2()
            val fileCommentLength: Int = ds.readLE2()
            val diskNumberStart: Int = ds.readLE2()
            val internalFileAttrs: Int = ds.readLE2()
            val extFileAttrs: Long = ds.readLE4()
            val localHeaderOffset: Long = ds.readLE4()
            val fileName = ds.readByteArray(fileNameLength.toLong()).decodeUtf8()
            val extraField = ds.readByteArray(extraFieldLength.toLong())
            val fileComment = ds.readByteArray(fileCommentLength.toLong()).decodeUtf8()
            return ZipDirRecord(structId,versionMadeBy,versionToExtract,bitFlag,compression, lastModTime,
              lastModDate,crc32,compressedSize,uncompressedSize, fileNameLength,extraFieldLength,fileCommentLength,diskNumberStart,
              internalFileAttrs,extFileAttrs,localHeaderOffset, fileName, extraField, fileComment)
        }
    }

    fun write(ds: BufferedSink)
    {
        ds.write(ZipDirRecordId)
        ds.writeLE2(versionMadeBy)
        ds.writeLE2(versionToExtract)
        ds.writeLE2(bitFlag)
        ds.writeLE2(compression)
        ds.writeLE2(lastModTime)
        ds.writeLE2(lastModDate)
        ds.writeLE4(crc32)
        ds.writeLE4(compressedSize)
        ds.writeLE4(uncompressedSize)
        ds.writeLE2(fileNameLength)
        ds.writeLE2(extraFieldLength)
        ds.writeLE2(fileCommentLength)
        ds.writeLE2(diskNumberStart)
        ds.writeLE2(internalFileAttrs)
        ds.writeLE4(externalFileAttrs)
        ds.writeLE4(localHeaderOffset)
        ds.write(fileName.encodeUtf8())
        ds.write(extra)
        ds.write(comment.encodeUtf8())
    }

    fun patch(hdr: ZipFileHeader)
    {
        if (compressedSize == 0L) compressedSize = hdr.compressedSize
        if (uncompressedSize == 0L) uncompressedSize = hdr.uncompressedSize
    }

    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (other !is ZipDirRecord) return false

        if (versionMadeBy != other.versionMadeBy) return false
        if (versionToExtract != other.versionToExtract) return false
        if (bitFlag != other.bitFlag) return false
        if (compression != other.compression) return false
        if (lastModTime != other.lastModTime) return false
        if (lastModDate != other.lastModDate) return false
        if (crc32 != other.crc32) return false
        if (compressedSize != other.compressedSize) return false
        if (uncompressedSize != other.uncompressedSize) return false
        if (fileNameLength != other.fileNameLength) return false
        if (extraFieldLength != other.extraFieldLength) return false
        if (fileCommentLength != other.fileCommentLength) return false
        if (diskNumberStart != other.diskNumberStart) return false
        if (internalFileAttrs != other.internalFileAttrs) return false
        if (externalFileAttrs != other.externalFileAttrs) return false
        if (localHeaderOffset != other.localHeaderOffset) return false
        if (!structureId.contentEquals(other.structureId)) return false
        if (fileName != other.fileName) return false
        if (!extra.contentEquals(other.extra)) return false
        if (comment != other.comment) return false

        return true
    }

    override fun hashCode(): Int
    {
        var result = versionMadeBy
        result = 31 * result + versionToExtract
        result = 31 * result + bitFlag
        result = 31 * result + compression
        result = 31 * result + lastModTime
        result = 31 * result + lastModDate
        result = 31 * result + crc32.hashCode()
        result = 31 * result + compressedSize.hashCode()
        result = 31 * result + uncompressedSize.hashCode()
        result = 31 * result + fileNameLength
        result = 31 * result + extraFieldLength
        result = 31 * result + fileCommentLength
        result = 31 * result + diskNumberStart
        result = 31 * result + internalFileAttrs
        result = 31 * result + externalFileAttrs.hashCode()
        result = 31 * result + localHeaderOffset.hashCode()
        result = 31 * result + structureId.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + extra.contentHashCode()
        result = 31 * result + comment.hashCode()
        return result
    }
}

/** Named "Local file header" in the specs structID is 0x04034b50 or in bytes 504b0304h */
data class ZipFileHeader(
  val structureId: ByteArray,
  val versionMadeBy: Int,
  val bitFlag:Int,
  val compression: Int,
  val lastModTime: Int,
  val lastModDate: Int,
  val crc32: Long,
  val compressedSize: Long,
  val uncompressedSize: Long,
  val fileNameLength: Int,
  val extraFieldLength: Int,
  val fileName: String,
  val extra: ByteArray
)
{

    companion object
    {
        fun from(name: String, data: ByteArray, extra: ByteArray?=null): ZipFileHeader
        {
            val dt = Clock.System.now()
            val ldt = dt.toLocalDateTime(TimeZone.currentSystemDefault())
            val time = (ldt.hour shl 11) or (ldt.minute shl 5) or (ldt.second/2)
            val date = ((ldt.year-1980) shl 9) or (ldt.month.number shl 5) or ldt.day
            val fnlen = name.encodeUtf8().size
            val xlen = extra?.size ?: 0
            val HAS_CRC32_AND_SIZES = 0  // crc and sizes are in the local header, OR  (1 shl 3)  // data descriptor present

            val crc32:Long = crc32(data).toLong()

            return ZipFileHeader(ZipFileHeaderId, VERSION_MADE_BY,  HAS_CRC32_AND_SIZES,
                COMPRESSION_NONE, time, date,
                crc32, data.size.toLong(), data.size.toLong(), fnlen, xlen, name, extra ?: byteArrayOf()
                )
        }

        fun from(ds: BufferedSource): ZipFileHeader
        {
            val structId: ByteArray = ds.peek().readAndClose(4)
            if (!(structId contentEquals ZipFileHeaderId))
            {
                // may be benign so not log: LogIt.info("ZipFileHeader: Incorrect zip record: ${structId.toHex()}")
                throw ZipRecordException(structId)
            }
            ds.readByteArray(4)  // drop what I peeked
            val versionMadeBy: Int = ds.readLE2()
            val bitFlag:Int = ds.readLE2()
            val compression: Int = ds.readLE2()
            val lastModTime: Int = ds.readLE2()
            val lastModDate: Int = ds.readLE2()
            val crc32: Long = ds.readLE4()
            val compressedSize: Long = ds.readLE4()
            val uncompressedSize: Long = ds.readLE4()
            val fileNameLength: Int = ds.readLE2()
            val extraFieldLength: Int = ds.readLE2()
            val fileName = ds.readByteArray(fileNameLength.toLong()).decodeUtf8()
            val extraField = ds.readByteArray(extraFieldLength.toLong())
            return ZipFileHeader(structId,versionMadeBy,bitFlag,compression, lastModTime,
              lastModDate,crc32,compressedSize,uncompressedSize, fileNameLength,extraFieldLength, fileName, extraField)
        }
    }

    fun write(ds: BufferedSink)
    {
        ds.write(ZipFileHeaderId)
        ds.writeLE2(versionMadeBy)
        ds.writeLE2(bitFlag)
        ds.writeLE2(compression)
        ds.writeLE2(lastModTime)
        ds.writeLE2(lastModDate)
        ds.writeLE4(crc32)
        ds.writeLE4(compressedSize)
        ds.writeLE4(uncompressedSize)
        ds.writeLE2(fileNameLength)
        ds.writeLE2(extraFieldLength)
        ds.write(fileName.encodeUtf8())
        ds.write(extra)
    }

    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (other !is ZipFileHeader) return false
        if (versionMadeBy != other.versionMadeBy) return false
        if (bitFlag != other.bitFlag) return false
        if (compression != other.compression) return false
        if (lastModTime != other.lastModTime) return false
        if (lastModDate != other.lastModDate) return false
        if (crc32 != other.crc32) return false
        if (compressedSize != other.compressedSize) return false
        if (uncompressedSize != other.uncompressedSize) return false
        if (fileNameLength != other.fileNameLength) return false
        if (extraFieldLength != other.extraFieldLength) return false
        if (!structureId.contentEquals(other.structureId)) return false
        if (fileName != other.fileName) return false
        if (!extra.contentEquals(other.extra)) return false

        return true
    }

    override fun hashCode(): Int
    {
        var result = versionMadeBy
        result = 31 * result + bitFlag
        result = 31 * result + compression
        result = 31 * result + lastModTime
        result = 31 * result + lastModDate
        result = 31 * result + crc32.hashCode()
        result = 31 * result + compressedSize.hashCode()
        result = 31 * result + uncompressedSize.hashCode()
        result = 31 * result + fileNameLength
        result = 31 * result + extraFieldLength
        result = 31 * result + structureId.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + extra.contentHashCode()
        return result
    }
}


/** "End of central directory record (EOCD)" in the zip standard.  byteArrayOf(0x50, 0x4b, 0x05, 0x06)
 * see https://en.wikipedia.org/wiki/ZIP_(file_format), or https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT */
data class ZipDirEndRecord(
  val structureId: ByteArray,
  val diskNumber: Int,
  val diskDirStart: Int,
  val diskNumRecords: Int,
  val numRecords: Int,
  val dirSize: Long,
  val dirOffset: Long,  // offset of start of central directory, centralDirOffset
  val commentLen: Int,
  val comment:String
)
{
    companion object
    {
        const val SIZE = (4 + 2 + 2 + 2 + 2 + 4 + 4 + 2).toLong()
        fun from(ds: BufferedSource): ZipDirEndRecord
        {
            val structId: ByteArray = ds.peek().readAndClose(4)
            if (!(structId contentEquals ZipDirEndId))
            {
                // may be benign so not log: LogIt.info("ZipDirEndRecord: Incorrect zip record: ${structId.toHex()}")
                throw ZipRecordException(structId)
            }
            ds.readByteArray(4)  // drop what I peeked

            val diskNumber = ds.readLE2()
            val diskDirStart = ds.readLE2()
            val diskNumRecords = ds.readLE2()
            val numRecords = ds.readLE2()
            val dirSize = ds.readLE4()
            val dirOffset = ds.readLE4()
            val commentLen = ds.readLE2()
            val comment = ds.readByteArray(commentLen.toLong()).decodeUtf8()
            return ZipDirEndRecord(structId, diskNumber, diskDirStart, diskNumRecords, numRecords, dirSize, dirOffset, commentLen, comment)
        }
    }

    fun write(ds: BufferedSink)
    {
        ds.write(ZipDirEndId)
        ds.writeLE2(diskNumber)
        ds.writeLE2(diskDirStart)
        ds.writeLE2(diskNumRecords)
        ds.writeLE2(numRecords)
        ds.writeLE4(dirSize )
        ds.writeLE4(dirOffset)
        ds.writeLE2(commentLen)
        ds.write(comment.encodeUtf8())
    }

    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (other !is ZipDirEndRecord) return false

        if (diskNumber != other.diskNumber) return false
        if (diskDirStart != other.diskDirStart) return false
        if (diskNumRecords != other.diskNumRecords) return false
        if (numRecords != other.numRecords) return false
        if (dirSize != other.dirSize) return false
        if (dirOffset != other.dirOffset) return false
        if (commentLen != other.commentLen) return false
        if (!structureId.contentEquals(other.structureId)) return false
        if (comment != other.comment) return false

        return true
    }

    override fun hashCode(): Int
    {
        var result = diskNumber
        result = 31 * result + diskDirStart
        result = 31 * result + diskNumRecords
        result = 31 * result + numRecords
        result = 31 * result + dirSize.hashCode()
        result = 31 * result + dirOffset.hashCode()
        result = 31 * result + commentLen
        result = 31 * result + structureId.contentHashCode()
        result = 31 * result + comment.hashCode()
        return result
    }
}

infix fun Int.spans (sz: Int): IntRange
{
    return IntRange(this, this+sz-1)
}

/*
infix fun Long.spans (sz: Long): IntRange
{
    return IntRange(this.toInt(), (this+sz-1).toInt())
}
 */

fun ByteArray.findLastOf(tgt: ByteArray, startAt:Int=size-1 - tgt.size): Int
{
    var idx = startAt
    while (idx >= 0)
    {
        if (this[idx] == tgt[0])
        {
            if (tgt contentEquals sliceArray(idx spans tgt.size))
                return idx
        }
        idx--
    }
    return -1
}

/*
// return the index of the dirend record
fun zipFindDirEnd(ba: ByteArray): ZipDirEndRecord?
{
    var idx = ba.size-1-4
    while(idx>=0)
    {
        idx = ba.findLastOf(ZipDirEndId, idx)
        if (idx == -1) return null  // No dirend in the data
        val b = Buffer()
        b.write(ba.takeLast(ba.size-idx).toByteArray())
        val dirEnd = ZipDirEndRecord.from(b)
        // Sanity check a bunch of fields to ignore spurious bytes that happen to be equal to ZipDirEndId
        if ((dirEnd.dirSize < ba.size) &&
          (dirEnd.dirOffset < ba.size-ZipDirRecordId.size) &&
          (dirEnd.diskNumRecords < ba.size) &&
          // Check that the dirOffset location actually contains a ZipDirRecordId
          (ZipDirRecordId contentEquals ba.sliceArray( IntRange(dirEnd.dirOffset.toInt(),dirEnd.dirOffset.toInt() + ZipDirRecordId.size - 1)))
        )
        {
            // OK its incrediably unlikely that a zip end comment contained this exact data, unless the comment is itself another zip file,
            // in which case, guess what? the .zip format is ancient and poorly designed and we are screwed :-)
            return dirEnd
        }
        idx--  // If we found something but it was not a valid record, then keep looking
    }
    return null
}
*/

// return the index of the dirend record
fun zipFindDirEnd(ef: EfficientFile): ZipDirEndRecord?
{
    val CHUNK_SIZE = 2048L
    var srchidx = max(0, ef.size-CHUNK_SIZE)
    while(true)
    {
        val ba = ef.openAt(srchidx).readAndClose(min(ef.size,CHUNK_SIZE))
        val idx = ba.findLastOf(ZipDirEndId)
        if (idx != -1)
        {
            // Now reposition to the beginning of the discovered ZipDirEndRecord.
            // We have to do this because the record is variable sized, so might exceed the chunk we grabbed when searching
            val dirEnd = ef.openAt(srchidx + idx).use {
                ZipDirEndRecord.from(it)
            }
            // Sanity check a bunch of fields to ignore spurious bytes that happen to be equal to ZipDirEndId
            if ((dirEnd.dirSize < ef.size) &&
              (dirEnd.dirOffset < ef.size - ZipDirRecordId.size) &&
              (dirEnd.diskNumRecords < ef.size))
              // Check that the dirOffset location actually contains a ZipDirRecordId -- SKIP this check because its another random-access seek
              // (ZipDirRecordId contentEquals ba.sliceArray(IntRange(dirEnd.dirOffset.toInt(), dirEnd.dirOffset.toInt() + ZipDirRecordId.size - 1)))
            {
                // OK its incrediably unlikely that a zip end comment contained this exact data, unless the comment is itself another zip file,
                // in which case, guess what? the .zip format is ancient and poorly designed and we are screwed :-)
                return dirEnd
            }
        }
        if (srchidx == 0L) break // Looked everywhere
        srchidx -= CHUNK_SIZE  // keep looking if nothing found
        if (srchidx < 0) srchidx = 0
    }
    return null
}


/** look at @ds as a zip file and call handler for every file in it.
 * @handler should return true to abort the for each loop
 */
fun zipForeach(ds: Buffer, handler: (ZipDirRecord, BufferedSource?) -> Boolean)
{
    // Zip has to be read from the end first, so in these modern times with streams we need to pull in the entire thing to get the end.
    //val zbytes = ds.readByteArray()
    zipForeach(EfficientFile(ds), handler)
}

/** look at @ds as a zip file and call handler for every file in it.
 * @handler should return true to abort the for each loop
 */
fun zipForeach(ds: BufferedSource, handler: (ZipDirRecord, BufferedSource?) -> Boolean)
{
    // Zip has to be read from the end first, so in these modern times with streams we need to pull in the entire thing to get the end.
    //val zbytes = ds.readByteArray()
    zipForeach(EfficientFile(ds.buffer), handler)
}

/** Look at @ba as a zip file and call handler for every file in it.
 * @handler should return true to abort the for each loop
 */
fun zipForeach(zbytes: ByteArray, handler: (ZipDirRecord, BufferedSource?) -> Boolean)
{
    zipForeach(EfficientFile(zbytes), handler)
}

fun zipForeach(zip: EfficientFile, handler: (ZipDirRecord, BufferedSource?) -> Boolean)
{

    val dirend = zipFindDirEnd(zip)
    if (dirend == null) return

    // Pull in the entire file as a byte array
    //val zbytes = zip.openAt(0).readByteArray()
    //val fullZip = Buffer()
    //fullZip.write(zbytes)
    //val fragment = Buffer()
    //fragment.write(zbytes.takeLast((zbytes.size-dirend.dirOffset).toInt()).toByteArray())
    //val fragment = fullZip.peek()
    //fragment.skip(dirend.dirOffset)

    // use the EfficientFile to just grab it where we need it
    val fragment = zip.openAt(dirend.dirOffset)
    try
    {
        var recordCount = 0

        val dirRecords = mutableMapOf<String, ZipDirRecord>()

        while (!fragment.exhausted() && (recordCount < dirend.numRecords))
        {
            try
            {
                val hdr = ZipDirRecord.from(fragment)
                dirRecords[hdr.fileName] = hdr
                recordCount++
            }
            catch (e: ZipRecordException)
            {
                if (e.id contentEquals ZipDirEndId)
                {
                    ZipDirEndRecord.from(fragment)
                }
                else
                {
                    logThreadException(e)
                    throw e
                }
            }
            catch (_: EOFException)
            {
                break
            }
        }

        // Sometimes the central directory records don't actually point to the correct local dir record offset.
        // They might just point to 0 (experienced, not specced), which is a correct local offset

        // Go through every central dir record, loading all files available from every specified offset.

        val alreadyDid = mutableSetOf<Long>()
        val alreadyDidFile = mutableSetOf<String>()

        for (crec in dirRecords.values)
        {
            // If we already searched local records starting at this offset, then skip doing it again
            if (alreadyDid.contains(crec.localHeaderOffset)) continue
            alreadyDid.add(crec.localHeaderOffset)

            // Grab the data we need to search
            try
            {
                //val fileEntryFrag = Buffer()
                //fileEntryFrag.write(zbytes.takeLast((zbytes.size - crec.localHeaderOffset).toInt()).toByteArray())
                val fileEntryFrag = zip.openAt(crec.localHeaderOffset)
                try
                {

                    while (!fileEntryFrag.exhausted())
                    {
                        val hdr = try
                        {
                            ZipFileHeader.from(fileEntryFrag)  // Get the first local record
                        }
                        catch (e: ZipRecordException)
                        {
                            if (e.id contentEquals ZipDataDescriptorId)  // this is info pertaining to the prior record, so not useful unless we read backwards
                            {
                                ZipDataDescriptor.from(fileEntryFrag)
                                continue
                            }
                            else if (e.id contentEquals ZipDirRecordId)
                            {
                                // We are done iteration through records, because all the central dir records must be at the end.  so just fall thru.
                                break
                            }
                            else
                            {
                                logThreadException(e)
                                throw e
                            }
                        }


                        val crecHdr = dirRecords[hdr.fileName]  // find the corresponding central record (if it exists)

                        // I'm not sure which to prefer for common fields in the central dir and the local.  However, in cases I've seen the one to not use
                        // has always been zero, unless they are both zero, and then that's where it really starts.  So this will work.

                        // prefer the central record data if it exists and is non-zero
                        var compressedSize = hdr.compressedSize
                        if ((crecHdr != null) && (crecHdr.compressedSize != 0L)) compressedSize = crecHdr.compressedSize

                        // prefer the central record data if it exists and is non-zero
                        var uncompressedSize = hdr.uncompressedSize
                        if ((crecHdr != null) && (crecHdr.uncompressedSize != 0L)) uncompressedSize = crecHdr.uncompressedSize

                        if (!alreadyDidFile.contains(hdr.fileName))  // advance, calling the file handler for this new file
                        {
                            val bs: BufferedSource? = when (hdr.compression)
                            {
                                ZipCompressionMethods.NoCompression.ordinal ->
                                {
                                    // I have to do it with a copy, because I can't create a BufferedSource that only peeks N bytes.
                                    val b = Buffer()
                                    b.write(fileEntryFrag.readByteArray(compressedSize))
                                    // not available on macos/ios: b.readFrom(fileEntryFrag.inputStream(), compressedSize)
                                }
                                ZipCompressionMethods.Deflated.ordinal ->
                                {
                                    val tmp = fileEntryFrag.readByteArray(compressedSize)
                                    val uncompressed: ByteArray = inflateRfc1951(tmp, uncompressedSize)
                                    val b = Buffer()
                                    b.write(uncompressed)
                                }
                                else -> null
                            }

                            alreadyDidFile.add(hdr.fileName)

                            if (crecHdr != null) // If there is not central record, this is an old, deleted file.  Skip (for now)
                            {
                                crecHdr.patch(hdr)
                                if (handler(crecHdr, bs)) return
                            }
                        }
                        else  // just advance
                        {
                            fileEntryFrag.skip(compressedSize)
                        }

                    }
                }
                finally
                {
                    fileEntryFrag.close()
                }
            }
            // Its easy to get memory errors because the entire file is being put into memory and then large chunks copied from it
            // In this case, skip the file
            catch (e: Throwable)  // java.lang.OutOfMemoryError does not appear to have a kotlin equivalent
            {

            }

        }
    }
    finally
    {
        fragment.close()
    }

    /*
    while(!ds.exhausted())
    {
        try
        {
            val hdr = ZipFileHeader.from(ds)
            val bs: BufferedSource? = if (hdr.compression == ZipCompressionMethods.NoCompression.ordinal)
            {
                val b = Buffer()
                b.write(ds.readByteArray(hdr.compressedSize))
            }
            else if (hdr.compression == ZipCompressionMethods.Deflated.ordinal)
            {
                val tmp = ds.readByteArray(hdr.compressedSize)
                val uncompressed:ByteArray = inflateRfc1951(tmp,hdr.uncompressedSize)
                val b = Buffer()
                b.write(uncompressed)
            }
            else null

            if (handler(hdr, bs) == true) return
        }
        catch(e: ZipRecordException)
        {
            if (e.id contentEquals ZipDirRecordId)
            {
                val dirRecord = ZipDirRecord.from(ds)
            }
            else if (e.id contentEquals ZipDirEndId)
            {
                val dirEnd = ZipDirEndRecord.from(ds)
            }
            else throw e
        }
    }

     */
}

/*
fun openZip(ds: BufferedSource)
{
    while(!ds.exhausted())
    {
        try
        {
            val hdr = ZipFileHeader.from(ds)
            println(hdr)
            val contents = ds.readByteArray(hdr.compressedSize)
        }
        catch(e: ZipRecordException)
        {
            if (e.id contentEquals ZipDirRecordId)
            {
                val dirRecord = ZipDirRecord.from(ds)
                println(dirRecord)
            }
            else if (e.id contentEquals ZipDirEndId)
            {
                val dirEnd = ZipDirEndRecord.from(ds)
                println(dirEnd)
            }
            else throw e
        }
    }
}

 */

class ZipEntry(val header: ZipFileHeader, val data:ByteArray, val comment:String = "")
{
    companion object
    {
        fun from(name: String, builder:(BufferedSink)->Unit): ZipEntry
        {
            val buf = Buffer()
            builder(buf)
            buf.flush()
            val data = buf.readByteArray()
            return ZipEntry(ZipFileHeader.from(name, data ), data)
        }
    }

    fun write(out: BufferedSink)
    {
        header.write(out)
        out.write(data)
    }

    fun getDirRecord(headerOffset:Long): ZipDirRecord
    {
        val HAS_CRC32_AND_SIZES = (1 shl 3)
        val INTERNAL_FILE_ATTR = 0
        val EXTERNAL_FILE_ATTR = 0L
        val ret = ZipDirRecord(
            ZipDirRecordId,header.versionMadeBy, header.versionMadeBy, header.bitFlag, header.compression, header.lastModTime, header.lastModDate,
            header.crc32, header.compressedSize, header.uncompressedSize, header.fileNameLength, header.extraFieldLength, comment.length, 0,
            INTERNAL_FILE_ATTR, EXTERNAL_FILE_ATTR, headerOffset, header.fileName,header.extra, comment)
        return ret
    }
}

class CountingSink(private val delegate: Sink) : Sink
{
    var bytesWritten: Long = 0
    override fun write(source: Buffer, byteCount: Long)
    {
        delegate.write(source, byteCount)
        bytesWritten += byteCount
    }
    override fun flush() = delegate.flush()
    override fun timeout(): Timeout = delegate.timeout()
    override fun close() = delegate.close()
    override fun toString(): String = "${this::class.simpleName}($delegate)"
}

class ZipFile
{
    val entries = mutableListOf<ZipEntry>()
    var zipComment: String? = null

    fun putNextEntry(ze: ZipEntry)
    {
        entries.add(ze)
    }

    fun write(path: Path)
    {
        FileSystem.SYSTEM.sink(path).use { write(it) }
    }

    fun write(out: Sink)
    {
        val outc = CountingSink(out)
        val outb = outc.buffer()
        // Write out all the files
        val offsets = mutableListOf<Long>()
        for (e in entries)
        {
            outb.flush()
            offsets.add(outc.bytesWritten)
            e.write(outb)
        }
        outb.flush()
        val dirStartOffset = outc.bytesWritten
        for ((e,o) in entries.zip(offsets))
        {
            val rec = e.getDirRecord(o)
            rec.write(outb)
        }
        outb.flush()
        val end = ZipDirEndRecord(ZipDirEndId, 0, 0, entries.size, entries.size,
            outc.bytesWritten-dirStartOffset, dirStartOffset, zipComment?.encodeUtf8()?.size ?: 0, zipComment?:"")
        end.write(outb)
        outb.flush()
    }

    fun toByteArray(): ByteArray
    {
        val buf = Buffer()
        write(buf)
        buf.flush()
        return buf.readByteArray()
    }

    fun hash256(): ByteArray
    {
        val zbytes = toByteArray()
        val nftid = libnexa.hash256(zbytes)
        return nftid
    }

    /** Changes the zip comment to find a zip file whose NFT ID end bytes matches the provided bytes.
     * @Returns the zip file as a ByteArray, and also leaves this ZipFile object set up to generate it.
     */
    fun grindZipSubgroup(findEnd:ByteArray, zipCommentCreator:(Int)->String = { "Nexa NFT (nonce $it)"}): ByteArray
    {
        var nonce = 0
        while (true)
        {
            nonce+=1
            zipComment = zipCommentCreator(nonce)
            val zbytes = toByteArray()
            val nftid = libnexa.hash256(zbytes)
            if (nftid.endsWith(findEnd)) return zbytes
        }
    }
}
