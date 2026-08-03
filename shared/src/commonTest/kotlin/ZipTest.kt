@file:OptIn(ExperimentalUnsignedTypes::class)

import okio.Buffer
import okio.BufferedSource
import org.nexa.assets.*
import org.nexa.libnexakotlin.decodeUtf8
import org.nexa.libnexakotlin.encodeUtf8
import org.nexa.libnexakotlin.fromHex
import org.nexa.libnexakotlin.initializeLibNexa
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZipTest
{
    init { initializeLibNexa() }

    // ---------- ByteArray.toBuffer ----------

    @Test
    fun toBufferCopiesAllBytes()
    {
        val src = byteArrayOf(1, 2, 3, 4, 5)
        val out = src.toBuffer().readByteArray()
        assertContentEquals(src, out)
    }

    @Test
    fun toBufferEmptyArrayProducesEmptyBuffer()
    {
        assertEquals(0L, byteArrayOf().toBuffer().size)
    }

    // ---------- ByteArray.endsWith ----------

    @Test
    fun endsWithMatchesExactSuffix()
    {
        assertTrue(byteArrayOf(1, 2, 3).endsWith(byteArrayOf(2, 3)))
        assertTrue(byteArrayOf(1, 2, 3).endsWith(byteArrayOf(3)))
    }

    @Test
    fun endsWithMatchesSelf()
    {
        assertTrue(byteArrayOf(1, 2, 3).endsWith(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun endsWithEmptySuffixIsAlwaysTrue()
    {
        assertTrue(byteArrayOf(1, 2, 3).endsWith(byteArrayOf()))
        assertTrue(byteArrayOf().endsWith(byteArrayOf()))
    }

    @Test
    fun endsWithSuffixLongerThanSourceIsFalse()
    {
        assertFalse(byteArrayOf(1, 2).endsWith(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun endsWithMismatchIsFalse()
    {
        assertFalse(byteArrayOf(1, 2, 3).endsWith(byteArrayOf(2, 4)))
    }

    // ---------- ByteArray.findLastOf ----------

    @Test
    fun findLastOfReturnsLastOccurrence()
    {
        val data = byteArrayOf(9, 1, 2, 3, 9, 1, 2, 3, 9)
        assertEquals(5, data.findLastOf(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun findLastOfReturnsNegativeOneWhenAbsent()
    {
        assertEquals(-1, byteArrayOf(1, 2, 3).findLastOf(byteArrayOf(9, 9)))
    }

    @Test
    fun findLastOfStartAtBoundsTheSearch()
    {
        val data = byteArrayOf(1, 2, 3, 4, 1, 2, 3, 4)
        val target = byteArrayOf(1, 2, 3)
        // Default search scans the whole array and finds the last occurrence.
        assertEquals(4, data.findLastOf(target))
        // Starting before the last occurrence forces it to find the first one instead.
        assertEquals(0, data.findLastOf(target, startAt = 3))
    }

    // ---------- Int spans ----------

    @Test
    fun spansProducesInclusiveRange()
    {
        assertEquals(IntRange(5, 7), 5 spans 3)
        assertEquals(IntRange(0, 0), 0 spans 1)
    }

    // ---------- crc32 ----------

    @Test
    fun crc32EmptyIsZero()
    {
        assertEquals(0u, crc32(byteArrayOf()))
    }

    @Test
    fun crc32StandardVector()
    {
        // "123456789" → 0xCBF43926 (CRC-32/ISO-HDLC reference vector).
        assertEquals(0xCBF43926u, crc32("123456789".encodeUtf8()))
    }

    @Test
    fun crc32RespectsOffsetAndLength()
    {
        val full = byteArrayOf(9) + "123456789".encodeUtf8() + byteArrayOf(9)
        assertEquals(0xCBF43926u, crc32(full, offset = 1, length = 9))
    }

    // ---------- LE read / write helpers ----------

    @Test
    fun leRoundTripOfAllWidths()
    {
        val b = Buffer()
        b.writeLE2(0x1234)
        b.writeLE4(0x01020304)
        b.writeLE4(0x12345678L)
        b.writeLE8(0x0102030405060708L)

        assertEquals(0x1234, b.readLE2())
        assertEquals(0x01020304L, b.readLE4())
        assertEquals(0x12345678L, b.readLE4())
        assertEquals(0x0102030405060708L, b.readLE8())
    }

    @Test
    fun leWriteUsesLittleEndianByteOrder()
    {
        val b = Buffer()
        b.writeLE2(0x1234)
        assertContentEquals(byteArrayOf(0x34, 0x12), b.readByteArray())
    }

    // ---------- BufferedSource.readAndClose ----------

    @Test
    fun readAndCloseReadsAllBytes()
    {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val b = Buffer().also { it.write(data) }
        assertContentEquals(data, b.readAndClose())
    }

    @Test
    fun readAndCloseReadsBoundedLength()
    {
        val b = Buffer().also { it.write(byteArrayOf(1, 2, 3, 4, 5)) }
        assertContentEquals(byteArrayOf(1, 2, 3), b.readAndClose(3))
    }

    // ---------- EfficientFile ----------

    @Test
    fun efficientFileFromByteArrayReportsSizeAndReadsBack()
    {
        val bytes = byteArrayOf(10, 20, 30, 40, 50)
        val ef = EfficientFile(bytes)
        assertEquals(5L, ef.size)
        assertContentEquals(bytes, ef.openAt(0).readAndClose())
    }

    @Test
    fun efficientFileOpenAtSkipsPrefix()
    {
        val ef = EfficientFile(byteArrayOf(10, 20, 30, 40, 50))
        assertContentEquals(byteArrayOf(30, 40, 50), ef.openAt(2).readAndClose())
    }

    @Test
    fun efficientFileFromBuffer()
    {
        val bytes = byteArrayOf(1, 2, 3)
        val ef = EfficientFile(Buffer().also { it.write(bytes) })
        assertEquals(3L, ef.size)
        assertContentEquals(bytes, ef.openAt(0).readAndClose())
    }

    @Test
    fun efficientFileFromBufferedSource()
    {
        val bytes = byteArrayOf(7, 8, 9, 10)
        val src: BufferedSource = Buffer().also { it.write(bytes) }
        val ef = EfficientFile(src)
        assertEquals(4L, ef.size)
        assertContentEquals(byteArrayOf(9, 10), ef.openAt(2).readAndClose())
    }

    @Test
    fun efficientFileHash256MatchesRawHash()
    {
        val bytes = "hello zip".encodeUtf8()
        val ef = EfficientFile(bytes)
        val direct = org.nexa.libnexakotlin.libnexa.hash256(bytes)
        assertContentEquals(direct, ef.hash256())
    }

    // ---------- ZipDataDescriptor ----------

    @Test
    fun zipDataDescriptorFromRoundTrip()
    {
        val buf = Buffer()
        buf.write(ZipDataDescriptorId)
        buf.writeLE4(0x01020304L)  // crc
        buf.writeLE4(100L)         // compressed
        buf.writeLE4(200L)         // uncompressed

        val dd = ZipDataDescriptor.from(buf)
        assertTrue(ZipDataDescriptorId contentEquals dd.structureId)
        assertEquals(0x01020304L, dd.crc32)
        assertEquals(100L, dd.compressedSize)
        assertEquals(200L, dd.uncompressedSize)
    }

    @Test
    fun zipDataDescriptorFromWrongIdThrowsZipRecordException()
    {
        val badId = byteArrayOf(1, 2, 3, 4)
        val buf = Buffer().also { it.write(badId + ByteArray(12)) }
        val ex = assertFailsWith<ZipRecordException> { ZipDataDescriptor.from(buf) }
        assertTrue(badId contentEquals ex.id)
    }

    @Test
    fun zipDataDescriptorEqualsAndHashCode()
    {
        val a = ZipDataDescriptor(ZipDataDescriptorId, 1L, 2L, 3L)
        val b = ZipDataDescriptor(ZipDataDescriptorId, 1L, 2L, 3L)
        val c = ZipDataDescriptor(ZipDataDescriptorId, 1L, 2L, 9L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    // ---------- ZipFileHeader ----------

    @Test
    fun zipFileHeaderFromNameAndDataPopulatesCrcSizeAndName()
    {
        val data = "hello".encodeUtf8()
        val hdr = ZipFileHeader.from("hello.txt", data)
        assertEquals("hello.txt", hdr.fileName)
        assertEquals(data.size.toLong(), hdr.compressedSize)
        assertEquals(data.size.toLong(), hdr.uncompressedSize)
        assertEquals("hello.txt".encodeUtf8().size, hdr.fileNameLength)
        assertEquals(COMPRESSION_NONE, hdr.compression)
        assertEquals(crc32(data).toLong(), hdr.crc32)
        assertTrue(ZipFileHeaderId contentEquals hdr.structureId)
    }

    @Test
    fun zipFileHeaderWriteReadRoundTrip()
    {
        val original = ZipFileHeader(
          ZipFileHeaderId, VERSION_MADE_BY, bitFlag = 0, compression = 0,
          lastModTime = 12345, lastModDate = 23456,
          crc32 = 0x01020304L, compressedSize = 42L, uncompressedSize = 42L,
          fileNameLength = "x".encodeUtf8().size, extraFieldLength = 0,
          fileName = "x", extra = byteArrayOf()
        )
        val buf = Buffer()
        original.write(buf)
        assertEquals(original, ZipFileHeader.from(buf))
    }

    @Test
    fun zipFileHeaderFromWrongIdThrowsZipRecordException()
    {
        val badId = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val buf = Buffer().also { it.write(badId) }
        val ex = assertFailsWith<ZipRecordException> { ZipFileHeader.from(buf) }
        assertTrue(badId contentEquals ex.id)
    }

    // ---------- ZipDirRecord ----------

    @Test
    fun zipDirRecordWriteReadRoundTrip()
    {
        val original = ZipDirRecord(
          ZipDirRecordId, VERSION_MADE_BY, VERSION_MADE_BY,
          bitFlag = 0, compression = 0, lastModTime = 1, lastModDate = 2,
          crc32 = 0x01020304L, compressedSize = 10L, uncompressedSize = 10L,
          fileNameLength = "f".encodeUtf8().size, extraFieldLength = 0,
          fileCommentLength = "c".encodeUtf8().size, diskNumberStart = 0,
          internalFileAttrs = 0, externalFileAttrs = 0L,
          localHeaderOffset = 77L, fileName = "f", extra = byteArrayOf(),
          comment = "c"
        )
        val buf = Buffer()
        original.write(buf)
        assertEquals(original, ZipDirRecord.from(buf))
    }

    @Test
    fun zipDirRecordPatchFillsInZeroSizes()
    {
        val hdr = ZipFileHeader.from("k", byteArrayOf(1, 2, 3, 4, 5))
        val rec = ZipDirRecord(
          ZipDirRecordId, 20, 20, 0, 0, 0, 0, hdr.crc32,
          compressedSize = 0, uncompressedSize = 0,
          fileNameLength = hdr.fileNameLength, extraFieldLength = 0,
          fileCommentLength = 0, diskNumberStart = 0,
          internalFileAttrs = 0, externalFileAttrs = 0L,
          localHeaderOffset = 0L, fileName = "k",
          extra = byteArrayOf(), comment = ""
        )
        rec.patch(hdr)
        assertEquals(5L, rec.compressedSize)
        assertEquals(5L, rec.uncompressedSize)
    }

    @Test
    fun zipDirRecordPatchDoesNotOverwriteNonZeroSizes()
    {
        val hdr = ZipFileHeader.from("k", byteArrayOf(1, 2, 3, 4, 5))
        val rec = ZipDirRecord(
          ZipDirRecordId, 20, 20, 0, 0, 0, 0, hdr.crc32,
          compressedSize = 99, uncompressedSize = 99,
          fileNameLength = hdr.fileNameLength, extraFieldLength = 0,
          fileCommentLength = 0, diskNumberStart = 0,
          internalFileAttrs = 0, externalFileAttrs = 0L,
          localHeaderOffset = 0L, fileName = "k",
          extra = byteArrayOf(), comment = ""
        )
        rec.patch(hdr)
        assertEquals(99L, rec.compressedSize)
        assertEquals(99L, rec.uncompressedSize)
    }

    @Test
    fun zipDirRecordFromWrongIdThrowsZipRecordException()
    {
        val badId = byteArrayOf(9, 9, 9, 9)
        val buf = Buffer().also { it.write(badId) }
        val ex = assertFailsWith<ZipRecordException> { ZipDirRecord.from(buf) }
        assertTrue(badId contentEquals ex.id)
    }

    // ---------- ZipDirEndRecord ----------

    @Test
    fun zipDirEndRecordWriteReadRoundTrip()
    {
        val original = ZipDirEndRecord(
          ZipDirEndId, diskNumber = 0, diskDirStart = 0,
          diskNumRecords = 3, numRecords = 3,
          dirSize = 120L, dirOffset = 500L,
          commentLen = "bye".encodeUtf8().size, comment = "bye"
        )
        val buf = Buffer()
        original.write(buf)
        assertEquals(original, ZipDirEndRecord.from(buf))
    }

    @Test
    fun zipDirEndRecordFromWrongIdThrowsZipRecordException()
    {
        val badId = byteArrayOf(9, 9, 9, 9)
        val buf = Buffer().also { it.write(badId) }
        val ex = assertFailsWith<ZipRecordException> { ZipDirEndRecord.from(buf) }
        assertTrue(badId contentEquals ex.id)
    }

    @Test
    fun zipDirEndRecordEqualsAndHashCode()
    {
        val a = ZipDirEndRecord(ZipDirEndId, 0, 0, 1, 1, 50, 100, 0, "")
        val b = ZipDirEndRecord(ZipDirEndId, 0, 0, 1, 1, 50, 100, 0, "")
        val c = ZipDirEndRecord(ZipDirEndId, 0, 0, 1, 2, 50, 100, 0, "")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    // ---------- ZipEntry ----------

    @Test
    fun zipEntryFromBuilderLambdaPopulatesData()
    {
        val payload = "builder-content".encodeUtf8()
        val ze = ZipEntry.from("built.txt") { sink -> sink.write(payload) }
        assertEquals("built.txt", ze.header.fileName)
        assertContentEquals(payload, ze.data)
        assertEquals(payload.size.toLong(), ze.header.compressedSize)
    }

    @Test
    fun zipEntryWriteEmitsHeaderFollowedByData()
    {
        val data = "payload".encodeUtf8()
        val ze = ZipEntry(ZipFileHeader.from("f.bin", data), data)
        val buf = Buffer()
        ze.write(buf)

        val readHdr = ZipFileHeader.from(buf)
        assertEquals("f.bin", readHdr.fileName)
        assertContentEquals(data, buf.readByteArray())
    }

    @Test
    fun zipEntryGetDirRecordEchoesHeaderFields()
    {
        val data = byteArrayOf(1, 2, 3, 4)
        val ze = ZipEntry(ZipFileHeader.from("x.bin", data), data, comment = "hi")
        val rec = ze.getDirRecord(headerOffset = 42L)
        assertEquals("x.bin", rec.fileName)
        assertEquals(42L, rec.localHeaderOffset)
        assertEquals(data.size.toLong(), rec.compressedSize)
        assertEquals(data.size.toLong(), rec.uncompressedSize)
        assertEquals("hi", rec.comment)
        assertEquals("hi".length, rec.fileCommentLength)
        assertTrue(ZipDirRecordId contentEquals rec.structureId)
    }

    // ---------- CountingSink ----------

    @Test
    fun countingSinkTracksBytesWritten()
    {
        val delegate = Buffer()
        val counter = CountingSink(delegate)

        counter.write(Buffer().also { it.write(byteArrayOf(1, 2, 3, 4, 5)) }, 5)
        assertEquals(5L, counter.bytesWritten)

        counter.write(Buffer().also { it.write(byteArrayOf(9, 8)) }, 2)
        assertEquals(7L, counter.bytesWritten)

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 9, 8), delegate.readByteArray())
    }

    // ---------- zipFindDirEnd / zipForeach / ZipFile round-trip ----------

    @Test
    fun zipFindDirEndReturnsNullWhenNoEndRecord()
    {
        val random = ByteArray(1024) { (it and 0xFF).toByte() }
        assertNull(zipFindDirEnd(EfficientFile(random)))
    }

    @Test
    fun zipFileWriteAndZipForeachRoundTripPreservesEntries()
    {
        val zf = ZipFile()
        val fileA = "hello.txt" to "Hello, world!".encodeUtf8()
        val fileB = "data.bin" to byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        zf.putNextEntry(ZipEntry(ZipFileHeader.from(fileA.first, fileA.second), fileA.second))
        zf.putNextEntry(ZipEntry(ZipFileHeader.from(fileB.first, fileB.second), fileB.second))
        zf.zipComment = "test-comment"

        val bytes = zf.toByteArray()

        val found = mutableMapOf<String, ByteArray>()
        val b = Buffer().also { it.write(bytes) }
        zipForeach(b) { info, data ->
            found[info.fileName] = data?.readByteArray() ?: byteArrayOf()
            false
        }
        assertEquals(2, found.size)
        assertContentEquals(fileA.second, found[fileA.first])
        assertContentEquals(fileB.second, found[fileB.first])

        val end = zipFindDirEnd(EfficientFile(bytes))
        assertEquals(2, end?.numRecords)
        assertEquals("test-comment", end?.comment)
    }

    @Test
    fun zipForeachHandlerReturningTrueAbortsIteration()
    {
        val zf = ZipFile()
        repeat(3) { i ->
            val data = "content-$i".encodeUtf8()
            zf.putNextEntry(ZipEntry(ZipFileHeader.from("f$i.txt", data), data))
        }
        val bytes = zf.toByteArray()

        val seen = mutableListOf<String>()
        zipForeach(bytes) { info, _ ->
            seen.add(info.fileName)
            seen.size >= 1  // abort after first entry
        }
        assertEquals(1, seen.size)
    }

    @Test
    fun zipForeachOnByteArrayMatchesBufferOverload()
    {
        val zf = ZipFile()
        val data = "abc".encodeUtf8()
        zf.putNextEntry(ZipEntry(ZipFileHeader.from("a.txt", data), data))
        val bytes = zf.toByteArray()

        val viaBytes = mutableListOf<String>()
        zipForeach(bytes) { info, _ -> viaBytes.add(info.fileName); false }

        val viaBuffer = mutableListOf<String>()
        zipForeach(Buffer().also { it.write(bytes) }) { info, _ ->
            viaBuffer.add(info.fileName); false
        }

        assertEquals(viaBytes, viaBuffer)
        assertEquals(listOf("a.txt"), viaBytes)
    }

    @Test
    fun zipFileHash256MatchesRawHashOfBytes()
    {
        val zf = ZipFile()
        val data = "content".encodeUtf8()
        zf.putNextEntry(ZipEntry(ZipFileHeader.from("x", data), data))
        val bytes = zf.toByteArray()
        val direct = org.nexa.libnexakotlin.libnexa.hash256(bytes)
        assertContentEquals(direct, zf.hash256())
    }

    @Test
    fun zipFileGrindZipSubgroupProducesHashEndingWithTarget()
    {
        val zf = ZipFile()
        val data = "grind".encodeUtf8()
        zf.putNextEntry(ZipEntry(ZipFileHeader.from("g.txt", data), data))

        // A one-byte target hits on average after ~256 tries. Use a fixed value so
        // the test is deterministic; worst case is still cheap.
        val target = byteArrayOf(0x42)
        val bytes = zf.grindZipSubgroup(target) { "nonce $it" }
        assertTrue(org.nexa.libnexakotlin.libnexa.hash256(bytes).endsWith(target))
    }
}
