import CryptoKit
import FileProvider
import os
import UniformTypeIdentifiers

/** Shows in Console.app (with the device attached) under subsystem info.bitcoinunlimited.www.wally.NftFileProvider */
let log = Logger(subsystem: "info.bitcoinunlimited.www.wally.NftFileProvider", category: "nfts")

/*
 Shows the NFT files Wally has downloaded as a read-only "Nexa Assets" location in the Files app and the document picker,
 so the user can give another app (e.g. a media player) access to them.  This is the iOS counterpart of Android's
 NftDocumentsProvider.

 The app (shared/src/iosMain/kotlin/nftFileProvider_ios.kt) keeps the "<groupId>.zip" files in the App Group container,
 and rewrites the export list whenever it checks its accounts' assets.  Only files in that list are shown, so an NFT
 disappears once no account owns it, even if its file is still cached.  The app removes this location entirely while
 a camouflage mode is on.  The names below must match the app's.
 */
enum NftStore
{
    static let appGroup = "group.info.bitcoinunlimited.www.wally"
    static let exportListFile = "nftExports.txt"
    /** Extended attribute giving each file's group id (e.g. "nexa:tq..."), for Nexa-aware apps */
    static let groupIdAttribute = "org.nexa.groupid"

    static var assetDir: URL?
    {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?.appendingPathComponent("assets", isDirectory: true)
    }

    /** True if the name is an NFT file name, which also rules out paths out of the asset directory */
    static func isNftFile(_ name: String) -> Bool
    {
        name.hasSuffix(".zip") && name.dropLast(4).count > 0 && name.dropLast(4).allSatisfy { $0.isHexDigit }
    }

    /** The exported files, each one "<file name>\t<name shown>\t<group id>" line of the export list, in list order */
    static func exports() -> [(file: String, name: String, groupId: String)]
    {
        guard let dir = assetDir,
              let list = try? String(contentsOf: dir.appendingPathComponent(exportListFile), encoding: .utf8) else { return [] }
        return list.split(separator: "\n").compactMap { line in
            let parts = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
            guard parts.count == 3, isNftFile(parts[0]), !parts[1].isEmpty else { return nil }
            return (parts[0], parts[1], parts[2])
        }
    }

    /** The exported NFT with this identifier (its file name), if its file exists */
    static func item(_ identifier: NSFileProviderItemIdentifier) -> NftItem?
    {
        let file = identifier.rawValue
        guard isNftFile(file), let dir = assetDir, let e = exports().first(where: { $0.file == file }) else { return nil }
        return NftItem(file: dir.appendingPathComponent(file), name: e.name, groupId: e.groupId)
    }

    static func items() -> [NftItem]
    {
        guard let dir = assetDir else { return [] }
        return exports().compactMap { NftItem(file: dir.appendingPathComponent($0.file), name: $0.name, groupId: $0.groupId) }
    }

    /** Changes whenever the set of shown files, their names or any of their contents change.
     * It is a hash because the system refuses anchors over 500 bytes. */
    static func syncAnchor() -> NSFileProviderSyncAnchor
    {
        let state = items().map { "\($0.itemIdentifier.rawValue):\($0.metadata):\($0.version)" }.joined(separator: "\n")
        return NSFileProviderSyncAnchor(Data(SHA256.hash(data: Data(state.utf8))))
    }
}

final class NftItem: NSObject, NSFileProviderItem
{
    let itemIdentifier: NSFileProviderItemIdentifier
    let parentItemIdentifier: NSFileProviderItemIdentifier = .rootContainer
    let filename: String
    let contentType: UTType = .zip
    let capabilities: NSFileProviderItemCapabilities = [.allowsReading]
    let documentSize: NSNumber?
    let contentModificationDate: Date?
    let extendedAttributes: [String: Data]
    /** Changes when the contents change */
    let version: String
    /** Changes when anything else shown about the file changes */
    let metadata: String
    let file: URL

    /** file is the NFT's file; name is what it is shown as ("author - title.nft"); groupId is its group id */
    init?(file: URL, name: String, groupId: String)
    {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: file.path),
              (attrs[.type] as? FileAttributeType) == .typeRegular else { return nil }
        self.file = file
        filename = name
        itemIdentifier = NSFileProviderItemIdentifier(file.lastPathComponent)
        let size = (attrs[.size] as? NSNumber) ?? 0
        let modified = attrs[.modificationDate] as? Date
        documentSize = size
        contentModificationDate = modified
        version = "\(size.int64Value)-\(modified?.timeIntervalSince1970 ?? 0)"
        extendedAttributes = groupId.isEmpty ? [:] : [NftStore.groupIdAttribute: Data(groupId.utf8)]
        metadata = "\(version)\t\(name)\t\(groupId)"
    }

    var itemVersion: NSFileProviderItemVersion
    {
        // Versions are limited in size, so the metadata version is a hash
        NSFileProviderItemVersion(contentVersion: Data(version.utf8), metadataVersion: Data(SHA256.hash(data: Data(metadata.utf8))))
    }
}

final class NftRootItem: NSObject, NSFileProviderItem
{
    let itemIdentifier: NSFileProviderItemIdentifier = .rootContainer
    let parentItemIdentifier: NSFileProviderItemIdentifier = .rootContainer
    let filename = "Nexa Assets"
    let contentType: UTType = .folder
    let capabilities: NSFileProviderItemCapabilities = [.allowsReading, .allowsContentEnumerating]
    /** Every item must have a version (the system aborts otherwise).  The folder's own contents and metadata never
     * change -- the files in it are tracked separately by enumeration -- so this is constant. */
    let itemVersion = NSFileProviderItemVersion(contentVersion: Data("1".utf8), metadataVersion: Data("1".utf8))
}

final class NftEnumerator: NSObject, NSFileProviderEnumerator
{
    func invalidate() {}

    /** Hands over the items a page (of the size the system asks for) at a time.  Our pages are the index to start at;
     * the system starts with one of its own initial page values, which mean index 0. */
    func enumerateItems(for observer: NSFileProviderEnumerationObserver, startingAt page: NSFileProviderPage)
    {
        let items = NftStore.items()
        var start = 0
        if page.rawValue.count == MemoryLayout<Int64>.size
        {
            start = Int(page.rawValue.withUnsafeBytes { $0.loadUnaligned(as: Int64.self) })
        }
        start = min(max(start, 0), items.count)
        let end = min(start + max(observer.suggestedPageSize ?? 200, 1), items.count)
        log.info("enumerating items \(start)..<\(end) of \(items.count)")
        observer.didEnumerate(Array(items[start..<end]))
        if end < items.count
        {
            var next = Int64(end)
            observer.finishEnumerating(upTo: NSFileProviderPage(Data(bytes: &next, count: MemoryLayout<Int64>.size)))
        }
        else
        {
            observer.finishEnumerating(upTo: nil)
        }
    }

    func enumerateChanges(for observer: NSFileProviderChangeObserver, from anchor: NSFileProviderSyncAnchor)
    {
        let current = NftStore.syncAnchor()
        if anchor == current
        {
            observer.finishEnumeratingChanges(upTo: current, moreComing: false)
        }
        else
        {
            // Rather than tracking what changed, have the system enumerate everything again and work out the
            // additions and removals itself.  There are few enough NFTs that this is cheap.
            observer.finishEnumeratingWithError(NSFileProviderError(.syncAnchorExpired))
        }
    }

    func currentSyncAnchor(completionHandler: @escaping (NSFileProviderSyncAnchor?) -> Void)
    {
        completionHandler(NftStore.syncAnchor())
    }
}

final class FileProviderExtension: NSObject, NSFileProviderReplicatedExtension
{
    let domain: NSFileProviderDomain

    required init(domain: NSFileProviderDomain)
    {
        self.domain = domain
        super.init()
        removeOrphanedTempFiles()
        log.info("started; asset directory \(NftStore.assetDir?.path ?? "unavailable"), \(NftStore.exports().count) exported")
    }

    func invalidate() {}

    /** fetchContents hands the system a file in the temporary directory, which the system then takes over.  If this extension
     * is killed before that happens the file is never reclaimed, so clear out old ones on startup.  Recent files are left alone
     * since another instance of the extension may be handing them over right now. */
    private func removeOrphanedTempFiles()
    {
        guard let tmpDir = try? NSFileProviderManager(for: domain)?.temporaryDirectoryURL() else { return }
        let fm = FileManager.default
        let cutoff = Date().addingTimeInterval(-3600)
        let files = (try? fm.contentsOfDirectory(at: tmpDir, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        for f in files
        {
            let modified = (try? f.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate ?? .distantPast
            guard modified < cutoff else { continue }
            do { try fm.removeItem(at: f); log.info("removed orphaned temp file \(f.lastPathComponent)") }
            catch { log.error("cannot remove temp file \(f.lastPathComponent): \(error.localizedDescription)") }
        }
    }

    func item(for identifier: NSFileProviderItemIdentifier, request: NSFileProviderRequest,
              completionHandler: @escaping (NSFileProviderItem?, Error?) -> Void) -> Progress
    {
        if identifier == .rootContainer { completionHandler(NftRootItem(), nil) }
        else if let item = NftStore.item(identifier) { completionHandler(item, nil) }
        else
        {
            log.info("no item \(identifier.rawValue)")
            completionHandler(nil, NSFileProviderError(.noSuchItem))
        }
        return Progress()
    }

    func fetchContents(for itemIdentifier: NSFileProviderItemIdentifier, version requestedVersion: NSFileProviderItemVersion?,
                       request: NSFileProviderRequest,
                       completionHandler: @escaping (URL?, NSFileProviderItem?, Error?) -> Void) -> Progress
    {
        guard let item = NftStore.item(itemIdentifier) else
        {
            completionHandler(nil, nil, NSFileProviderError(.noSuchItem))
            return Progress()
        }
        do
        {
            // The system takes ownership of the returned file, so hand it a link to (or failing that, a copy of) ours
            let tmpDir = try NSFileProviderManager(for: domain)?.temporaryDirectoryURL() ?? FileManager.default.temporaryDirectory
            let dest = tmpDir.appendingPathComponent(UUID().uuidString)
            do { try FileManager.default.linkItem(at: item.file, to: dest) }
            catch { try FileManager.default.copyItem(at: item.file, to: dest) }
            completionHandler(dest, item, nil)
        }
        catch
        {
            log.error("cannot provide \(itemIdentifier.rawValue): \(error.localizedDescription)")
            completionHandler(nil, nil, error)
        }
        return Progress()
    }

    // The NFT files are read-only: nothing can be created, changed or deleted here.

    func createItem(basedOn itemTemplate: NSFileProviderItem, fields: NSFileProviderItemFields, contents url: URL?,
                    options: NSFileProviderCreateItemOptions = [], request: NSFileProviderRequest,
                    completionHandler: @escaping (NSFileProviderItem?, NSFileProviderItemFields, Bool, Error?) -> Void) -> Progress
    {
        completionHandler(nil, [], false, CocoaError(.featureUnsupported))
        return Progress()
    }

    func modifyItem(_ item: NSFileProviderItem, baseVersion version: NSFileProviderItemVersion, changedFields: NSFileProviderItemFields,
                    contents newContents: URL?, options: NSFileProviderModifyItemOptions = [], request: NSFileProviderRequest,
                    completionHandler: @escaping (NSFileProviderItem?, NSFileProviderItemFields, Bool, Error?) -> Void) -> Progress
    {
        completionHandler(nil, [], false, CocoaError(.featureUnsupported))
        return Progress()
    }

    func deleteItem(identifier: NSFileProviderItemIdentifier, baseVersion version: NSFileProviderItemVersion,
                    options: NSFileProviderDeleteItemOptions = [], request: NSFileProviderRequest,
                    completionHandler: @escaping (Error?) -> Void) -> Progress
    {
        completionHandler(CocoaError(.featureUnsupported))
        return Progress()
    }

    func enumerator(for containerItemIdentifier: NSFileProviderItemIdentifier, request: NSFileProviderRequest) throws -> NSFileProviderEnumerator
    {
        // Nothing can be trashed, so say that is unsupported (noSuchItem would tell the system the trash was deleted)
        guard containerItemIdentifier != .trashContainer else { throw CocoaError(.featureUnsupported) }
        // Everything is in the root, so the working set (what the system keeps up to date) is the same list
        guard containerItemIdentifier == .rootContainer || containerItemIdentifier == .workingSet else
        {
            log.info("no enumerator for \(containerItemIdentifier.rawValue)")
            throw NSFileProviderError(.noSuchItem)
        }
        return NftEnumerator()
    }
}
