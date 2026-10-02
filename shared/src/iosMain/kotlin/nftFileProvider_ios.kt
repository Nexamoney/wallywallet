package org.nexa.assets

import info.bitcoinunlimited.www.wally.CAMOUFLAGE_MEDITATION
import info.bitcoinunlimited.www.wally.CAMOUFLAGE_SUDOKU
import info.bitcoinunlimited.www.wally.wallyApp
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.nexa.libnexakotlin.GetLog
import org.nexa.threads.Mutex
import platform.FileProvider.NSFileProviderDomain
import platform.FileProvider.NSFileProviderManager
import platform.FileProvider.NSFileProviderWorkingSetContainerItemIdentifier
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private val LogIt = GetLog("wally.nftFileProvider_ios")

/* The downloaded NFT files are shown to other apps (in the Files app and the document picker) by the NftFileProvider
 * extension in iosApp/NftFileProvider.  The extension runs in its own sandbox, so the asset files live in this App Group
 * container, which both can read.  These names must match the extension's (FileProviderExtension.swift). */
const val APP_GROUP_ID = "group.info.bitcoinunlimited.www.wally"
const val NFT_EXPORT_LIST_FILE = "nftExports.txt"
private const val NFT_DOMAIN_ID = "nfts"
private const val NFT_DOMAIN_NAME = "Nexa Assets"
private val NFT_FILE = Regex("[0-9a-fA-F]+\\.zip")

// The files are read-only and nothing can be deleted, so do not offer a "Recently Deleted" (trash) folder
private val nftDomain = NSFileProviderDomain(identifier = NFT_DOMAIN_ID, displayName = NFT_DOMAIN_NAME).apply { supportsSyncingTrash = false }

private fun documentsDir(): Path?
{
    val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    return if (dirs.size > 0) dirs[0].toString().toPath() else null
}

/** The shared App Group container, or null if this build is not entitled to it */
private fun groupDir(): Path? = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)?.path?.toPath()

private val access = Mutex("nftFileProvider")
private var assetDirCache: Path? = null

/** Where IosAssetManagerStorage keeps its asset files.  This used to be Documents/assets, so the first call moves any
 * files there into the App Group container.  If the App Group is not available, NFTs cannot be shared, and Documents/assets is used. */
fun iosAssetDir(): Path = access.lock {
    assetDirCache?.let { return@lock it }
    val oldDir = documentsDir()?.let { it / "assets" } ?: "assets".toPath()
    val group = groupDir()
    val ret = if (group == null)
    {
        LogIt.warning("App group $APP_GROUP_ID is not available: NFTs will not be shared with other apps")
        oldDir
    }
    else
    {
        val newDir = group / "assets"
        try
        {
            if (FileSystem.SYSTEM.exists(oldDir))
            {
                FileSystem.SYSTEM.createDirectories(newDir)
                for (f in FileSystem.SYSTEM.list(oldDir))
                {
                    val dest = newDir / f.name
                    if (FileSystem.SYSTEM.exists(dest)) FileSystem.SYSTEM.delete(f)
                    else FileSystem.SYSTEM.atomicMove(f, dest)
                }
                FileSystem.SYSTEM.deleteRecursively(oldDir)
                LogIt.info("Moved asset files from $oldDir to $newDir")
            }
        }
        catch (e: Exception)  // Nothing is lost: any file not moved is just downloaded again when needed
        {
            LogIt.error("Cannot move asset files to the app group: $e")
        }
        newDir
    }
    assetDirCache = ret
    ret
}

private fun camouflaged(): Boolean
{
    val prefs = wallyApp?.preferenceDB ?: return false
    return prefs.getBoolean(CAMOUFLAGE_MEDITATION, false) || prefs.getBoolean(CAMOUFLAGE_SUDOKU, false)
}

private var domainAdded: Boolean? = null

/** Shows the NFT location in the Files app, or removes it (and every copy the system made of its files) while a
 * camouflage mode is on, since a "Nexa Assets" location would give the wallet away. */
fun updateNftFileProviderDomain(hide: Boolean = camouflaged())
{
    if (groupDir() == null) return  // The extension could not read the files anyway
    val changed = access.lock {
        val c = domainAdded != !hide
        domainAdded = !hide
        c
    }
    if (!changed) return
    val done = { err: platform.Foundation.NSError? ->
        if (err == null) LogIt.info("NFT file provider domain ${if (hide) "removed" else "added"}")
        else
        {
            LogIt.error("Cannot ${if (hide) "remove" else "add"} NFT file provider domain: ${err.localizedDescription}")
            access.lock { domainAdded = null }  // retry next time
        }
    }
    if (hide) NSFileProviderManager.removeDomain(nftDomain, done)
    else NSFileProviderManager.addDomain(nftDomain, done)
}

/** Tells the extension that the NFT files it shows may have changed */
fun signalNftFileProvider()
{
    if (domainAdded != true) return
    NSFileProviderManager.managerForDomain(nftDomain)?.signalEnumeratorForContainerItemIdentifier(NSFileProviderWorkingSetContainerItemIdentifier) { err ->
        if (err != null) LogIt.info("NFT file provider signal failed: ${err.localizedDescription}")
    }
}

/** Replaces the list of NFT files the extension may show and the names it shows them as
 * (see AssetManagerStorage.setExportedAssetFiles): one "<file name>\t<name shown>\t<group id>" per line, as on Android. */
fun writeNftExports(files: Map<String, NftExport>)
{
    updateNftFileProviderDomain()
    if (groupDir() == null) return
    val dir = iosAssetDir()
    val list = dir / NFT_EXPORT_LIST_FILE
    val content = files.filterKeys { NFT_FILE.matches(it) }.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value.name}\t${it.value.groupId}" }
    if (FileSystem.SYSTEM.exists(list) && FileSystem.SYSTEM.read(list) { readUtf8() } == content) return
    // Write then rename, so the extension never reads a partial list
    val tmp = dir / "$NFT_EXPORT_LIST_FILE.tmp"
    FileSystem.SYSTEM.createDirectories(dir)
    FileSystem.SYSTEM.write(tmp) { writeUtf8(content) }
    FileSystem.SYSTEM.atomicMove(tmp, list)
    LogIt.info("Shared ${files.size} NFT files with other apps")
    signalNftFileProvider()
}
