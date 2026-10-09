package info.bitcoinunlimited.www.wally

import android.content.ClipDescription
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import com.caverock.androidsvg.SVG
import okio.FileSystem
import okio.Path.Companion.toPath
import org.nexa.assets.EfficientFile
import org.nexa.assets.NFT_GROUP_ID_COLUMN
import org.nexa.assets.NftExport
import org.nexa.assets.isVideo
import org.nexa.assets.nftCardFront
import org.nexa.libnexakotlin.GetLog
import java.io.File
import java.io.FileNotFoundException

private val LogIt = GetLog("wally.NftDocumentsProvider")

/**
 * Exposes downloaded NFT zips to apps with the manifest's READ_ASSET_FILES permission or a URI grant.
 * Uses DocumentsContract URI paths and columns for roots, documents, and children, but is a plain
 * ContentProvider so Android can enforce our custom permission instead of MANAGE_DOCUMENTS.
 * This provider does not participate in the Storage Access Framework system file picker.
 * Only owned files in EXPORT_LIST_FILE are exposed, and all access is hidden while camouflaged.
 */
class NftDocumentsProvider : ContentProvider()
{
    companion object
    {
        private const val ROOT_ID = "nfts"
        /** The folder itself; each NFT's document id is its file name within the asset directory. */
        private const val ROOT_DOC_ID = "nfts"
        private val NFT_FILE = Regex("[0-9a-fA-F]+\\.zip")
        /** Lists the NFT files to show, one "<file name>\t<name shown>\t<group id>" per line; kept in the asset directory.
         * The iOS app writes the same format (nftFileProvider_ios.kt). */
        const val EXPORT_LIST_FILE = "nftExports.txt"
        /** Card front thumbnails are rendered once at this size (px, longest side) and cached; clients scale them. */
        private const val THUMBNAIL_SIZE = 384
        private const val THUMBNAIL_DIR = "nftThumbnails"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_ICON)
        /** Includes NFT_GROUP_ID_COLUMN, the NFT's group id (e.g. "nexa:tq..."), for Nexa-aware apps.  Other apps
         * don't ask for it, and clients can ignore columns they do not know. */
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
          Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, NFT_GROUP_ID_COLUMN)

        fun authority(ctx: Context) = ctx.packageName + ".nfts"

        /** Tells anyone browsing the folder that its contents changed (an NFT file was added or removed). */
        fun notifyChanged(ctx: Context) = notifyChange(ctx, DocumentsContract.buildChildDocumentsUri(authority(ctx), ROOT_DOC_ID))

        /** Tells observers that the folder appeared or disappeared (camouflage mode was changed). */
        fun notifyRootsChanged(ctx: Context) = notifyChange(ctx, DocumentsContract.buildRootsUri(authority(ctx)))

        /** Android throws if no provider is registered for the authority.  This provider is declared in androidApp's
         * manifest, so a package without it (e.g. the shared module's device test APK) has nobody to notify. */
        private fun notifyChange(ctx: Context, uri: Uri)
        {
            try
            {
                ctx.contentResolver.notifyChange(uri, null)
            }
            catch (e: SecurityException)
            {
                LogIt.info("No NFT documents provider registered for ${uri.authority}: ${e.message}")
            }
        }

        /** The NFT files currently allowed to be shown */
        /** The NFT files currently allowed to be shown, and the name each is shown as */
        fun readExports(assetDir: File): Map<String, NftExport>
        {
            val f = File(assetDir, EXPORT_LIST_FILE)
            if (!f.isFile) return mapOf()
            return f.readLines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size == 3 && NFT_FILE.matches(parts[0]) && parts[1].isNotBlank()) parts[0] to NftExport(parts[1], parts[2]) else null
            }.toMap()
        }

        /** Replaces the export list, returning false if it is unchanged. */
        fun writeExports(assetDir: File, files: Map<String, NftExport>): Boolean
        {
            val exports = files.filterKeys { NFT_FILE.matches(it) }
            if (exports == readExports(assetDir)) return false
            // Write then rename, so the provider never reads a partial list
            val tmp = File(assetDir, "$EXPORT_LIST_FILE.tmp")
            tmp.writeText(exports.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value.name}\t${it.value.groupId}" })
            if (!tmp.renameTo(File(assetDir, EXPORT_LIST_FILE))) throw java.io.IOException("cannot replace $EXPORT_LIST_FILE")
            return true
        }

        /** Deletes the cached thumbnails of NFTs that are no longer shown */
        fun pruneThumbnails(ctx: Context, shown: Set<String>)
        {
            File(ctx.cacheDir, THUMBNAIL_DIR).listFiles()?.forEach { if (it.nameWithoutExtension !in shown) it.delete() }
        }

        /** The card front of this NFT file, scaled to fit in size x size, or null if it has none or it cannot be drawn */
        private fun renderCardFront(nftZip: File, size: Int): Bitmap?
        {
            val ef = EfficientFile(nftZip.absolutePath.toPath(), FileSystem.SYSTEM)
            val (name, bytes) = try { nftCardFront(ef) } finally { ef.close() }
            if (name == null || bytes == null) return null

            val full: Bitmap? = if (name.endsWith(".svg", true))
            {
                val svg = SVG.getFromInputStream(bytes.inputStream())
                val ar = svg.documentAspectRatio.takeIf { it > 0f } ?: 1f
                val bmp = if (ar >= 1f) Bitmap.createBitmap(size, (size / ar).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                          else Bitmap.createBitmap((size * ar).toInt().coerceAtLeast(1), size, Bitmap.Config.ARGB_8888)
                svg.setDocumentWidth(bmp.width.toFloat())
                svg.setDocumentHeight(bmp.height.toFloat())
                svg.renderToCanvas(Canvas(bmp))
                bmp
            }
            else
            {
                // Still images (and the first frame of animated gif/webp/avif), decoded no bigger than needed
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                  ?: if (isVideo(name)) videoFrame(bytes) else null
            }
            if (full == null) return null
            val scale = size.toFloat() / maxOf(full.width, full.height)
            if (scale >= 1f) return full
            return Bitmap.createScaledBitmap(full, (full.width * scale).toInt().coerceAtLeast(1), (full.height * scale).toInt().coerceAtLeast(1), true)
        }

        private fun videoFrame(bytes: ByteArray): Bitmap?
        {
            val retriever = MediaMetadataRetriever()
            try
            {
                retriever.setDataSource(object : MediaDataSource()
                {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int
                    {
                        if (position >= bytes.size) return -1
                        val n = minOf(size.toLong(), bytes.size - position).toInt()
                        System.arraycopy(bytes, position.toInt(), buffer, offset, n)
                        return n
                    }
                    override fun getSize(): Long = bytes.size.toLong()
                    override fun close() {}
                })
                return retriever.frameAtTime
            }
            finally
            {
                retriever.release()
            }
        }
    }

    override fun onCreate(): Boolean = true

    /** Only direct document URIs are supported; SAF tree grants are not part of this provider. */
    private fun path(uri: Uri): List<String>
    {
        require(uri.scheme == "content" && uri.authority == authority(ctx())) { "Unknown URI: $uri" }
        return uri.pathSegments
    }

    private fun documentId(uri: Uri): String
    {
        val parts = path(uri)
        require(parts.size == 2 && parts[0] == "document") { "Not a document URI: $uri" }
        return parts[1]
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor
    {
        require(selection == null && selectionArgs.isNullOrEmpty()) { "Selection is not supported" }
        val parts = path(uri)
        return when
        {
            parts == listOf("root") -> queryRoots(projection)
            parts.size == 2 && parts[0] == "document" -> queryDocument(parts[1], projection)
            parts.size == 3 && parts[0] == "document" && parts[2] == "children" ->
                queryChildDocuments(parts[1], projection)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun getType(uri: Uri): String
    {
        val id = documentId(uri)
        if (camouflaged()) throw FileNotFoundException(id)
        if (id == ROOT_DOC_ID) return Document.MIME_TYPE_DIR
        fileFor(id)
        return "application/zip"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        openDocument(documentId(uri), mode, null)

    override fun openFile(uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        openDocument(documentId(uri), mode, signal)

    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?): AssetFileDescriptor =
        openTypedAssetFile(uri, mimeTypeFilter, opts, null)

    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?,
                                    signal: CancellationSignal?): AssetFileDescriptor
    {
        signal?.throwIfCanceled()
        if (opts?.containsKey(ContentResolver.EXTRA_SIZE) == true &&
            ClipDescription.compareMimeTypes("image/png", mimeTypeFilter))
            return openDocumentThumbnail(documentId(uri))
        if (!ClipDescription.compareMimeTypes(getType(uri), mimeTypeFilter))
            throw FileNotFoundException("Unsupported MIME type: $mimeTypeFilter")
        return AssetFileDescriptor(openFile(uri, "r", signal), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("NFT files are read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("NFT files are read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("NFT files are read-only")

    private fun ctx(): Context = context ?: throw IllegalStateException("provider not attached")

    /** Same directory as AndroidAssetManagerStorage uses. */
    private fun assetDir(): File = ctx().getDir("asset", Context.MODE_PRIVATE)

    private fun camouflaged(): Boolean
    {
        // Read directly: this provider can be created before the app has set up its own preference access.
        val p = ctx().getSharedPreferences(TEST_PREF + PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        return p.getBoolean(CAMOUFLAGE_MEDITATION, false) || p.getBoolean(CAMOUFLAGE_SUDOKU, false)
    }


    /** The NFT file a document id names, refusing anything that isn't one (including paths out of the directory). */
    private fun fileFor(documentId: String, exports: Map<String, NftExport> = readExports(assetDir())): File
    {
        if (!NFT_FILE.matches(documentId) || camouflaged()) throw FileNotFoundException(documentId)
        if (documentId !in exports) throw FileNotFoundException(documentId)
        val f = File(assetDir(), documentId)
        if (!f.isFile) throw FileNotFoundException(documentId)
        return f
    }

    private fun addRow(cursor: MatrixCursor, documentId: String, exports: Map<String, NftExport> = readExports(assetDir()))
    {
        val row = cursor.newRow()
        if (documentId == ROOT_DOC_ID)
        {
            row.add(Document.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
            row.add(Document.COLUMN_DISPLAY_NAME, "Nexa Assets")
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            row.add(Document.COLUMN_FLAGS, 0)
            row.add(Document.COLUMN_SIZE, null)
            row.add(Document.COLUMN_LAST_MODIFIED, assetDir().lastModified())
        }
        else
        {
            val f = fileFor(documentId, exports)
            row.add(Document.COLUMN_DOCUMENT_ID, documentId)
            val export = exports.getValue(documentId)  // fileFor checked it is there
            row.add(Document.COLUMN_DISPLAY_NAME, export.name)
            row.add(Document.COLUMN_MIME_TYPE, "application/zip")
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_THUMBNAIL)  // and read-only
            row.add(Document.COLUMN_SIZE, f.length())
            row.add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            row.add(NFT_GROUP_ID_COLUMN, export.groupId)
        }
    }

    private fun queryRoots(projection: Array<out String>?): Cursor
    {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        if (camouflaged()) return result
        val row = result.newRow()
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
        row.add(Root.COLUMN_TITLE, "Nexa Assets")
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY)
        row.add(Root.COLUMN_ICON, R.mipmap.icon2024a)
        return result
    }

    private fun queryDocument(documentId: String, projection: Array<out String>?): Cursor
    {
        if (camouflaged()) throw FileNotFoundException(documentId)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        addRow(result, documentId)
        return result
    }

    private fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?): Cursor
    {
        if (parentDocumentId != ROOT_DOC_ID || camouflaged()) throw FileNotFoundException(parentDocumentId)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val dir = assetDir()
        val exports = readExports(dir)
        for (name in exports.keys) if (File(dir, name).isFile) addRow(result, name, exports)
        result.setNotificationUri(ctx().contentResolver, DocumentsContract.buildChildDocumentsUri(authority(ctx()), ROOT_DOC_ID))
        return result
    }

    /** The NFT's card front, rendered on first request and cached until its file changes */
    private fun openDocumentThumbnail(documentId: String): AssetFileDescriptor
    {
        val f = fileFor(documentId)
        val dir = File(ctx().cacheDir, THUMBNAIL_DIR)
        val thumb = File(dir, f.nameWithoutExtension + ".png")
        if (!thumb.isFile || thumb.lastModified() < f.lastModified())
        {
            val bmp = try { renderCardFront(f, THUMBNAIL_SIZE) }
            catch (e: Exception)
            {
                LogIt.info("Cannot draw card front of $documentId: $e")
                null
            }
            // Clients can show a generic icon when no thumbnail is available
            if (bmp == null) throw FileNotFoundException("no card front for $documentId")
            dir.mkdirs()
            val tmp = File(dir, thumb.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(thumb)) throw FileNotFoundException("cannot cache thumbnail for $documentId")
        }
        return AssetFileDescriptor(ParcelFileDescriptor.open(thumb, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    private fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor
    {
        signal?.throwIfCanceled()
        if (mode != "r") throw UnsupportedOperationException("NFT files are read-only")
        return ParcelFileDescriptor.open(fileFor(documentId), ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
