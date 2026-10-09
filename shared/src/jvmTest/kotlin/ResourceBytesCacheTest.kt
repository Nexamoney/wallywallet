import info.bitcoinunlimited.www.wally.getResourceBytes
import java.io.File
import kotlin.test.*

class ResourceBytesCacheTest
{
    @Test
    fun resourceIsReadOnceAndCached()
    {
        // readResourceFile falls back to the filesystem, so a temp file stands in for a bundled resource
        val f = File.createTempFile("wally-res", ".bin")
        try
        {
            f.writeBytes(byteArrayOf(1, 2, 3))
            val first = getResourceBytes(f.absolutePath)
            assertContentEquals(byteArrayOf(1, 2, 3), first)

            // a second call must come from the cache, not the file
            f.writeBytes(byteArrayOf(9))
            assertSame(first, getResourceBytes(f.absolutePath))
        }
        finally
        {
            f.delete()
        }
    }

    @Test
    fun missingResourceThrowsAndIsNotCached()
    {
        val f = File.createTempFile("wally-res", ".bin")
        f.delete()
        assertFails { getResourceBytes(f.absolutePath) }

        // once it exists it must be read, not stuck on the earlier miss
        try
        {
            f.writeBytes(byteArrayOf(7))
            assertContentEquals(byteArrayOf(7), getResourceBytes(f.absolutePath))
        }
        finally
        {
            f.delete()
        }
    }
}
