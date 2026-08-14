package moe.ditto.halo.downloads

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * iOS's downloads directory: `Documents`, marked as excluded from backup.
 *
 * Documents rather than Caches because the system purges Caches under storage
 * pressure, and a film someone downloaded for a flight is not something to
 * regenerate. Documents is backed up by default, though, which for media
 * measured in gigabytes would mean copying the whole library into iCloud, so
 * the directory carries `NSURLIsExcludedFromBackupKey`. The flag belongs to the
 * directory and is inherited by everything written inside it, which is why it
 * is set here rather than per file.
 *
 * This is Kotlin/Native over Foundation; no Swift file participates.
 * Compile-verified only, since this machine has no Mac.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosDownloadStorage : DownloadStoragePort {

    override fun directory(): String? {
        val documents = NSSearchPathForDirectoriesInDomains(
            directory = NSDocumentDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: return null

        val path = "$documents/$DirectoryName"
        val manager = NSFileManager.defaultManager
        if (!manager.fileExistsAtPath(path)) {
            val created = manager.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            if (!created) return null
        }
        // A Kotlin Boolean bridges to the NSNumber the resource key expects.
        NSURL.fileURLWithPath(path, isDirectory = true).setResourceValue(
            value = true,
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        )
        return path
    }

    override fun freeBytes(): Long? {
        val documents = NSSearchPathForDirectoriesInDomains(
            directory = NSDocumentDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: return null
        val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(documents, null)
        return (attributes?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
    }

    private companion object {
        const val DirectoryName = "downloads"
    }
}
