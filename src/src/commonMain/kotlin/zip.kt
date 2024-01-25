package info.bitcoinunlimited.www.wally
import okio.*
import org.nexa.libnexakotlin.decodeUtf8
import org.nexa.libnexakotlin.toHex
import org.nexa.libnexakotlin.toPositiveInt
import org.nexa.libnexakotlin.toPositiveLong


expect fun inflateRfc1951(compressedBytes: ByteArray, expectedfinalSize: Long):ByteArray

private fun byteToLongLE(bytes: ByteArray): Long
{
    var result = 0L
    var shift = 0
    for (byte in bytes)
    {
        result = result or (byte.toPositiveLong() shl shift)
        shift += 8
    }
    return result
}

fun byteToIntLE(bytes: ByteArray): Int
{
    var result = 0
    var shift = 0
    for (byte in bytes)
    {
        result = result or (byte.toPositiveInt() shl shift)
        shift += 8
    }
    return result
}

fun BufferedSource.readLE4():Long
{
    val tmp = this.readByteArray(4)
    return byteToLongLE(tmp)
}

fun BufferedSource.readLE2():Int
{
    val tmp = this.readByteArray(2)
    return byteToIntLE(tmp)
}

open class ZipException: Exception()

class ZipRecordException(val id: ByteArray):ZipException()

val ZipFileHeaderId = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
val ZipDirRecordId = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
val ZipDirEndId = byteArrayOf(0x50, 0x4b, 0x05, 0x06)

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

data class ZipDirRecord(
  val structureId: ByteArray,
  val versionMadeBy: Int,
  val versionToExtract: Int,
  val bitFlag:Int,
  val compression: Int,
  val lastModTime: Int,
  val lastModDate: Int,
  val crc32: Long,
  val compressedSize: Long,
  val uncompressedSize: Long,
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
            val structId: ByteArray = ds.peek().readByteArray(4)
            if (!(structId contentEquals ZipDirRecordId))
            {
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
}

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
        fun from(ds: BufferedSource): ZipFileHeader
        {
            val structId: ByteArray = ds.peek().readByteArray(4)
            if (!(structId contentEquals ZipFileHeaderId))
            {
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
}


data class ZipDirEndRecord(
  val structureId: ByteArray,
  val diskNumber: Int,
  val diskDirStart: Int,
  val diskNumRecords: Int,
  val numRecords: Int,
  val dirSize: Long,
  val dirOffset: Long,
  val commentLen: Int,
  val comment:String
)
{
    companion object
    {
        fun from(ds: BufferedSource): ZipDirEndRecord
        {
            val structId: ByteArray = ds.peek().readByteArray(4)
            if (!(structId contentEquals ZipDirEndId))
            {
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
}

/** look at @ba as a zip file and call handler for every file in it.
 * @handler should return true to abort the for each loop
 */
fun zipForeach(ba: ByteArray, handler: (ZipFileHeader, BufferedSource?) -> Boolean)
{
    val b = Buffer()
    b.write(ba)
    zipForeach(b, handler)
}

/** look at @ds as a zip file and call handler for every file in it.
 * @handler should return true to abort the for each loop
 */
fun zipForeach(ds: BufferedSource, handler: (ZipFileHeader, BufferedSource?) -> Boolean)
{
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