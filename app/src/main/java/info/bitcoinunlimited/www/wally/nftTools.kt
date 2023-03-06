// This should become a library
package info.bitcoinunlimited.www.wally


import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.util.zip.ZipInputStream

val NFTY_MINT_MAX_FILE_SIZE = 1024*1024*50  // Note this is the max size that this web site's creation supports, not the max size of any Nifty file.
val NFTY_MINT_MAX_CARD_SIZE = 2*1024*1024

// Canonical file names that must appear in the .zip
val NFTY_CARD_FRONT_MEDIA = "cardf"
val NFTY_INFO_FILE = "info.json"

// Canonical file names that may appear in the .zip
val NFTY_PUBLIC_MEDIA = "public"
val NFTY_OWNER_MEDIA = "owner"
val NFTY_CARD_BACK_MEDIA = "cardb"

val NFTY_SUPPORTED_VIDEO = listOf(".avif", ".webp", ".ogg", ".mp4", ".mpeg", ".mpg", ".webm").let { it + it.map { it.uppercase()}}
val NFTY_SUPPORTED_IMAGE = listOf(".svg", ".gif", ".png", ".apng", ".jpg", ".jpeg").let { it + it.map { it.uppercase()}}
val NFTY_SUPPORTED_MEDIA = NFTY_SUPPORTED_VIDEO + NFTY_SUPPORTED_IMAGE


/** return filename and data of the public card front */
fun cardFront(nftyZip: ByteArray):Pair<String?, ByteArray?>
{
    val zipIn = ZipInputStream(ByteArrayInputStream(nftyZip))
    var entry = zipIn.nextEntry

    while (entry != null)
    {
        if (entry.name.startsWith("cardf"))
        {
            val data = zipIn.readBytes()
            return Pair(entry.name,data)
        }
        zipIn.closeEntry()
        entry = zipIn.nextEntry
    }
    zipIn.close()
    return Pair(null,null)
}