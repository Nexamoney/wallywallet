package info.bitcoinunlimited.www.wally
import okio.*
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("wally.zip")

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
    return readIntLe().toLong()
    //val tmp = this.readByteArray(4)
    //return byteToLongLE(tmp)
}

fun BufferedSource.readLE2():Int
{
    return readShortLe().toInt()
    //val tmp = this.readByteArray(2)
    //return byteToIntLE(tmp)
}

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
            val structId: ByteArray = ds.peek().readByteArray(4)
            if (!(structId contentEquals ZipDataDescriptorId))
            {
                LogIt.info("ZipDataDescriptor: Incorrect zip record: ${structId.toHex()}")
                throw ZipRecordException(structId)
            }
            ds.readByteArray(4)  // drop what I peeked
            val crc32: Long = ds.readLE4()
            val compressedSize: Long = ds.readLE4()
            val uncompressedSize: Long = ds.readLE4()
            return ZipDataDescriptor(structId, crc32, compressedSize, uncompressedSize)
        }
    }
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
            val structId: ByteArray = ds.peek().readByteArray(4)
            if (!(structId contentEquals ZipDirRecordId))
            {
                LogIt.info("ZipDirRecord: Incorrect zip record: ${structId.toHex()}")
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

    fun patch(hdr: ZipFileHeader)
    {
        if (compressedSize == 0L) compressedSize = hdr.compressedSize
        if (uncompressedSize == 0L) uncompressedSize = hdr.uncompressedSize
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
                LogIt.info("ZipFileHeader: Incorrect zip record: ${structId.toHex()}")
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
        // SIZE = 4 + 2 + 2 + 2 + 2 + 4 + 4 + 2
        fun from(ds: BufferedSource): ZipDirEndRecord
        {
            val structId: ByteArray = ds.peek().readByteArray(4)
            if (!(structId contentEquals ZipDirEndId))
            {
                LogIt.info("ZipDirEndRecord: Incorrect zip record: ${structId.toHex()}")
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

/** look at @ds as a zip file and call handler for every file in it.
 * @handler should return true to abort the for each loop
 */
fun zipForeach(ds: BufferedSource, handler: (ZipDirRecord, BufferedSource?) -> Boolean)
{
    // Zip has to be read from the end first, so in these modern times with streams we need to pull in the entire thing to get the end.
    val zbytes = ds.readByteArray()
    zipForeach(zbytes, handler)
}

/** Look at @ba as a zip file and call handler for every file in it.
 * @handler should return true to abort the for each loop
 */
fun zipForeach(zbytes: ByteArray, handler: (ZipDirRecord, BufferedSource?) -> Boolean)
{
    val dirend = zipFindDirEnd(zbytes)
    if (dirend == null) return

    val fullZip = Buffer()
    fullZip.write(zbytes)

    //val fragment = Buffer()
    //fragment.write(zbytes.takeLast((zbytes.size-dirend.dirOffset).toInt()).toByteArray())
    val fragment = fullZip.peek()
    fragment.skip(dirend.dirOffset)

    var recordCount = 0

    val dirRecords = mutableMapOf<String, ZipDirRecord>()

    while(!fragment.exhausted() && (recordCount < dirend.numRecords))
    {
        try
        {
            val hdr = ZipDirRecord.from(fragment)
            dirRecords[hdr.fileName] = hdr
            recordCount++
        }
        catch(e: ZipRecordException)
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
        catch(e:EOFException)
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
        val fileEntryFrag = Buffer()
        fileEntryFrag.write(zbytes.takeLast((zbytes.size-crec.localHeaderOffset).toInt()).toByteArray())
        try
        {
            while (!fileEntryFrag.exhausted())
            {
                val hdr = ZipFileHeader.from(fileEntryFrag)  // Get the first local record

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
                    val bs: BufferedSource? = if (hdr.compression == ZipCompressionMethods.NoCompression.ordinal)
                    {
                        val b = Buffer()
                        b.write(fileEntryFrag.readByteArray(compressedSize))
                        // not available on macos/ios: b.readFrom(fileEntryFrag.inputStream(), compressedSize)
                    }
                    else if (hdr.compression == ZipCompressionMethods.Deflated.ordinal)
                    {
                        val tmp = fileEntryFrag.readByteArray(compressedSize)
                        val uncompressed: ByteArray = inflateRfc1951(tmp, uncompressedSize)
                        val b = Buffer()
                        b.write(uncompressed)
                    }
                    else null

                    alreadyDidFile.add(hdr.fileName)

                    if (crecHdr != null) // If there is not central record, this is an old, deleted file.  Skip (for now)
                    {
                        crecHdr.patch(hdr)
                        if (handler(crecHdr, bs) == true) return
                    }
                }
                else  // just advance
                {
                    fileEntryFrag.readByteArray(compressedSize)
                }

            }
        }
        catch(e: ZipRecordException)
        {
            if (e.id contentEquals ZipDataDescriptorId)  // this is info pertaining to the prior record, so not useful unless we read backwards
            {
                ZipDataDescriptor.from(fileEntryFrag)
            }
            else if (e.id contentEquals ZipDirRecordId)
            {
                // We are done iteration through records, because all the central dir records must be at the end.  so just fall thru.
            }
            else
            {
                logThreadException(e)
                throw e
            }
        }

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