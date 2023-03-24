// This should become a library
package info.bitcoinunlimited.www.wally

import bitcoinunlimited.libbitcoincash.GroupId
import bitcoinunlimited.libbitcoincash.toHex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.*
import java.io.*
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import java.util.logging.Logger
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists

private val LogIt = Logger.getLogger("nifty.nftTools")

val NFTY_MINT_MAX_FILE_SIZE = 1024*1024*50  // Note this is the max size that this web site's creation supports, not the max size of any Nifty file.
val NFTY_MINT_MAX_CARD_SIZE = 2*1024*1024

// Canonical file names that must appear in the .zip
val NFTY_CARD_FRONT_MEDIA = "cardf"
val NFTY_INFO_FILE = "info.json"   // Info.json must be UTF-8 encoded

// Canonical file names that may appear in the .zip
val NFTY_PUBLIC_MEDIA = "public"
val NFTY_OWNER_MEDIA = "owner"
val NFTY_CARD_BACK_MEDIA = "cardb"

val NFTY_SUPPORTED_VIDEO = listOf(".avif", ".webp", ".ogg", ".mp4", ".mpeg", ".mpg", ".webm").let { it + it.map { it.uppercase()}}
val NFTY_SUPPORTED_IMAGE = listOf(".svg", ".gif", ".png", ".apng", ".jpg", ".jpeg").let { it + it.map { it.uppercase()}}
val NFTY_SUPPORTED_MEDIA = NFTY_SUPPORTED_VIDEO + NFTY_SUPPORTED_IMAGE

val NFTY_FILE_EXT = ".zip"

// To use the image and video resizing functionality, you need to point to these programs on your system
var FFMPEG = "/usr/bin/ffmpeg"
var IMMAG = "/usr/bin/convert"

// And create a scratch space for temporary files
var BASE_DIR = ""
var NFTY_TMP_PATH = Path(BASE_DIR + "/tmp")
var NFTY_TMP_PREFIX = "nfty"

// If we have to transcode, this is what we convert to
var NFTY_DEFAULT_VIDEO_SUFFIX = ".mp4"
var NFTY_MINT_CARD_PIX = 300
var NFTY_PREFERRED_BITMAP_EXTENSION = "png" // convert svg card images that are too large into this format


// Escapes double quotes and backslashes so this string is a valid json string
fun String.jsonString(): String
{
    var s = replace("\\","\\\\")
    s = s.replace("\n"," ")  // new lines are not acceptable in json strings
    return s.replace(""""""","""\"""")
}

fun isVideo(name:String): Boolean
{
    for (ext in NFTY_SUPPORTED_VIDEO)
    {
        if (name.endsWith(ext)) return true
    }
    return false
}

fun canonicalExtension(s: String?): String?
{
    if (s == null) return null
    val r = s.split('.').last().lowercase()
    if (r == "jpg") return "jpeg"
    return r
}

fun canonicalSplitExtension(s: String?): Pair<String,String>?
{
    if (s == null) return null
    var r = s.substringAfterLast('.').lowercase()
    if (r == "jpg") r = "jpeg"
    return Pair(s.substringBeforeLast('.'), r)
}

@Serializable
data class NexaNFTv2(
  val niftyVer:String,
  val title: String,
  val series:String?=null,
  val author: String,
  val keywords: List<String>,
  val appuri: String,
  val info: String,
  val bindata: String?=null,
  val data: JsonObject?=null,
  val license: String?=null,
)

/** return filename and data of the public card front */
fun nftCardFront(nftyZip: ByteArray):Pair<String?, ByteArray?>
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

fun nftCardBack(nftyZip: ByteArray):Pair<String?, ByteArray?>
{
    val zipIn = ZipInputStream(ByteArrayInputStream(nftyZip))
    var entry = zipIn.nextEntry

    while (entry != null)
    {
        if (entry.name.startsWith("cardb"))
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

fun nftPublicMedia(nftyZip: ByteArray):Pair<String?, ByteArray?>
{
    val zipIn = ZipInputStream(ByteArrayInputStream(nftyZip))
    var entry = zipIn.nextEntry

    while (entry != null)
    {
        if (entry.name.startsWith("public"))
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

fun nftOwnerMedia(nftyZip: ByteArray):Pair<String?, ByteArray?>
{
    val zipIn = ZipInputStream(ByteArrayInputStream(nftyZip))
    var entry = zipIn.nextEntry

    while (entry != null)
    {
        if (entry.name.startsWith("owner"))
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

fun nftData(nftyZip: ByteArray): NexaNFTv2?
{
    val zipIn = ZipInputStream(ByteArrayInputStream(nftyZip))
    var entry = zipIn.nextEntry

    while (entry != null)
    {
        if (entry.name.lowercase() == "info.json")
        {
            val data = zipIn.readBytes()
            val s = String(data, Charsets.UTF_8)
            val js = Json { ignoreUnknownKeys = true }
            val nftInfo = js.decodeFromString<NexaNFTv2>(s)
            zipIn.close()
            return nftInfo
        }
        zipIn.closeEntry()
        entry = zipIn.nextEntry
    }
    zipIn.close()
    return null
}

// dataFile includes the extension, and mediaType is the "canonicalExtension()" so they are a little redundant
data class NFTCreationData(val dataFile: String, val mediaType: String,
  val ownerFile: String?, val ownerMediaType: String?,
  val cardFrontFile: String?, val cardfMediaType: String?,
  val cardBackFile: String?, val cardbMediaType: String?,
  val license: String,
  val bindata: ByteArray?,
  val title: String, val series: String, val author: String, val keywords: List<String>, val info: String, val appuri: String, val data: String, val quantity: Int)

fun makeNftyZip(outFile: Path, data: NFTCreationData, outputPrefix: String, parentGroupId: GroupId): GroupId
{
    if (true)
    {
        val zout = ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile.toFile())))

        val metaDataEntry = ZipEntry("info.json")
        zout.putNextEntry(metaDataEntry)

        val keywordsAsJson = data.keywords.map { "\"" + it + "\"" }.joinToString(",")

        val nftSpecificData = if (data.data == "") "{}" else data.data  // TODO validate its JSON

        // NFT author puts any JSON in the data dictionary (presumably for use by a custom app that interacts with this NFT)

        val series = if (data.series != "") """"series":"${data.series}",""" else ""
        val bindata = if (data.bindata != null && data.bindata.size > 0) """"bindata":"${data.bindata.toHex()}",""" else ""

        zout.write("""{
  "niftyVer":"2.0",
  "title": "${data.title}",
  $series 
  "author": "${data.author}",
  "keywords": [ $keywordsAsJson ],
  "appuri": "${data.appuri}",
  "info": "${data.info.jsonString()}",
  $bindata
  "data" : $nftSpecificData,
  "license": "${data.license}"
}
""".toByteArray())

        zout.closeEntry()

        // If the file is too big to show as a card, then convert it to a smaller one
        val cardFrontFile = generateCardFile(data.cardFrontFile, data.dataFile)
        val differentFront = cardFrontFile != File(data.dataFile)

        val cardBackFile = generateCardFile(data.cardBackFile, null)

        if (cardFrontFile != null)
        {
            val cardFrontEntry = ZipEntry("cardf." + cardFrontFile.extension)
            var fi = FileInputStream(cardFrontFile)
            var from = BufferedInputStream(fi)
            zout.putNextEntry(cardFrontEntry)
            from.copyTo(zout)
            zout.closeEntry()
            cardFrontFile.delete()  // Clean it up
        }

        // These don't have to exist if they are no different than cardf
        if (cardBackFile != null)
        {
            val cardBackEntry = ZipEntry("cardb." + cardBackFile.extension)
            var fi = FileInputStream(cardBackFile)
            var from = BufferedInputStream(fi)
            zout.putNextEntry(cardBackEntry)
            from.copyTo(zout)
            zout.closeEntry()
        }

        if (differentFront)
        {
            val dFile = File(data.dataFile)
            val zippedFile = ZipEntry("public." + dFile.extension)
            var fi = FileInputStream(dFile)
            var from = BufferedInputStream(fi)
            zout.putNextEntry(zippedFile)
            from.copyTo(zout)
            zout.closeEntry()
        }

        if (data.ownerFile != null)
        {
            val dFile = File(data.ownerFile)
            val zippedFile = ZipEntry("owner." + dFile.extension)
            var fi = FileInputStream(dFile)
            var from = BufferedInputStream(fi)
            zout.putNextEntry(zippedFile)
            from.copyTo(zout)
            zout.closeEntry()
        }

        zout.finish()
        zout.close()
    }

    val zipFile = FileInputStream(outFile.toFile())
    val zipBytes = zipFile.readBytes()

    var nftHash = bitcoinunlimited.libbitcoincash.Hash.hash256(zipBytes)
    if (data.bindata != null && data.bindata.size > 0)
    {
        nftHash = nftHash+data.bindata
    }
    val nftGroup = parentGroupId.subgroup(nftHash)

    val finalName = Path(outputPrefix + "/" + nftGroup.toHex() + ".zip")
    outFile.copyTo(finalName)
    outFile.toFile().delete()

    LogIt.info("created: " + finalName)
    return nftGroup
}

fun generateCardFile(preferred: String?, backup: String?): File?
{
    val origfname = preferred ?: backup
    if (origfname == null) return null

    // If the file is too big to show as a card, then convert it to a smaller one
    val ret = if (File(origfname).length() > NFTY_MINT_MAX_CARD_SIZE)
    {
        // So createTempFile is carefully made to not allow it to overlap with some other newly created file
        // But we NEED that to happen because ffmpeg is going to generate the file
        // so delete the file, holding on to the file name
        var fname:String
        if (isVideo(origfname))
        {
            val tfile = kotlin.io.path.createTempFile(NFTY_TMP_PATH, NFTY_TMP_PREFIX, NFTY_DEFAULT_VIDEO_SUFFIX)
            tfile.deleteIfExists()
            fname = tfile.absolutePathString()
            // -an drops audio
            // size - 1024 because a few bytes is needed to close out the file
            // -2 means keep ratio, but be a multiple of 2
            val exec:String = FFMPEG + " -y -i " + origfname + " -vf scale=$NFTY_MINT_CARD_PIX:-2 -an " + " -fs " + (NFTY_MINT_MAX_CARD_SIZE-1024).toString() + " " + fname
            LogIt.info("Running: " + exec)
            val result = exec.runCommand()
            LogIt.info(result)
        }
        else
        {
            val newExt = if (File(origfname).extension == "svg") NFTY_PREFERRED_BITMAP_EXTENSION else File(origfname).extension
            val tfile = kotlin.io.path.createTempFile(NFTY_TMP_PATH, NFTY_TMP_PREFIX, "." + newExt)
            tfile.deleteIfExists()
            fname = tfile.absolutePathString()

            val exec:String = IMMAG + " " + origfname + " -auto-orient -resize ${NFTY_MINT_CARD_PIX}x${NFTY_MINT_CARD_PIX} " + fname
            //val exec:String = FFMPEG + " -y -i " + data.dataFile + " -vf scale=$NFTY_MINT_CARD_X_PIX:-2 " + fname
            LogIt.info("Running: " + exec)
            val result = exec.runCommand()
            LogIt.info(result)
        }
        File(fname)
    }
    else File(origfname)

    return ret
}
