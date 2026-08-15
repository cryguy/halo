package moe.ditto.halo.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.ditto.halo.auth.EpochClock
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** What asking for a download did. */
internal sealed interface DownloadStartResult {
    data class Started(val entry: DownloadEntry) : DownloadStartResult

    /** One entry per video: asking again for something already held changes nothing. */
    data class AlreadyExists(val entry: DownloadEntry) : DownloadStartResult

    /** This device has nowhere to keep downloads. */
    data object Unavailable : DownloadStartResult

    /**
     * The source declared a size this device cannot take. Only ever returned
     * when both numbers are known: an unknown size or unknown free space means
     * the download is attempted, because refusing on a guess is worse than
     * failing on a full disk.
     */
    data class NotEnoughSpace(val requiredBytes: Long, val freeBytes: Long) : DownloadStartResult
}

/** The files a finished download consists of, as absolute paths. */
internal data class DownloadFiles(val videoPath: String, val subtitlePath: String?)

/**
 * What the volume holding the downloads exists in, for the storage meter.
 *
 * Both figures or neither: a bar drawn from a known free figure over a guessed
 * total says something the device never reported.
 */
internal data class StorageSpace(val freeBytes: Long, val totalBytes: Long)

/**
 * The app's one downloads owner: the index, the queue, and the single transfer
 * that is allowed to run at a time.
 *
 * One at a time on purpose. Sources sit behind resolvers and CDNs that throttle
 * per connection, so three transfers over one mobile link finish later than
 * three in a row, and each of them stalls more. The Expo client started every
 * download the moment it was asked to; this queues them instead, which is the
 * one deliberate behavioural departure from it.
 *
 * Transfers are in-process, so they do not survive the app being killed. That
 * is the same limit the Expo client had, and the reason every active entry
 * comes back paused rather than pretending to have made progress while the app
 * was gone. Queued entries come back paused too: an app that silently resumed
 * gigabytes on launch would be spending someone's mobile data without being
 * asked.
 *
 * This object is the only writer of entry state. Screens observe [entries] and
 * call the four verbs; nothing else touches the index or the files.
 */
internal class DownloadsCoordinator(
    private val index: DownloadIndex,
    private val storage: DownloadStoragePort,
    private val transfer: DownloadTransfer,
    private val clock: EpochClock,
    private val scope: CoroutineScope,
    private val fileSystem: FileSystem = downloadFileSystem(),
    /**
     * Fetched once, when a download is accepted, because a video watched with
     * no network needs its subtitle already on the device.
     */
    private val subtitles: DownloadSubtitleSource = NoDownloadSubtitles,
) {
    private val directory: Path? = storage.directory()?.toPath()

    private val _entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()

    /** False where the platform has no downloads directory; the screen says so. */
    val isAvailable: Boolean get() = directory != null

    private val mutex = Mutex()
    private var active: ActiveTransfer? = null

    /** Measures the one transfer that runs at a time; see [TransferRate]. */
    private val rate = TransferRate()

    private class ActiveTransfer(val videoId: String, val job: Job)

    init {
        // Read synchronously: the store is synchronous, and a Downloads tab
        // that started empty and filled in a frame later would flash its empty
        // state on every launch.
        val snapshot = index.read()
        _entries.value = snapshot.entries.map { entry ->
            if (entry.status.isActive) entry.copy(status = DownloadStatus.Paused) else entry
        }
        scope.launch { settle(sweep = snapshot.complete) }
    }

    fun entryFor(videoId: String): DownloadEntry? = _entries.value.firstOrNull { it.videoId == videoId }

    /**
     * Where this entry's files actually are, for the player to open.
     *
     * Absolute paths are built here and never stored, because only this object
     * knows the directory and the directory is not stable across reinstalls.
     * Null while the download is not finished; there is nothing whole to play.
     */
    fun playbackFiles(entry: DownloadEntry): DownloadFiles? {
        val root = directory ?: return null
        if (entry.status != DownloadStatus.Done) return null
        return DownloadFiles(
            videoPath = (root / entry.fileName).toString(),
            subtitlePath = entry.subtitle?.let { (root / it.fileName).toString() },
        )
    }

    /**
     * How much room this device has, or null when it cannot say.
     *
     * A passthrough rather than the screen holding the port itself: this object
     * already owns the storage collaborator, and the question "how much room is
     * there for downloads" is one about downloads. Both calls hit the
     * filesystem, so read it on a cadence rather than per frame.
     */
    fun storageSpace(): StorageSpace? {
        val free = storage.freeBytes()?.takeIf { it >= 0 } ?: return null
        val total = storage.totalBytes()?.takeIf { it > 0 } ?: return null
        // Free is clamped rather than trusted: the two figures are separate
        // syscalls, and a bar with more free space than volume would draw past
        // its own track.
        return StorageSpace(freeBytes = free.coerceAtMost(total), totalBytes = total)
    }

    /**
     * Accepts a video for download, or explains why it did not.
     *
     * A failed entry is replaced rather than refused: asking again after a
     * failure is a retry with a source the picker may have re-resolved.
     */
    suspend fun start(media: DownloadMedia): DownloadStartResult {
        val root = directory ?: return DownloadStartResult.Unavailable
        val result = mutex.withLock {
            val existing = current(media.videoId)
            if (existing != null && existing.status != DownloadStatus.Failed) {
                return@withLock DownloadStartResult.AlreadyExists(existing)
            }
            spaceShortfall(media)?.let { return@withLock it }
            if (existing != null) discardFiles(existing, root)
            val now = clock.nowMs()
            val entry = DownloadEntry(
                media = media,
                fileName = DownloadPaths.videoFileName(media.videoId, media.sourceUrl),
                status = DownloadStatus.Queued,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            put(entry)
            DownloadStartResult.Started(entry)
        }
        if (result is DownloadStartResult.Started) {
            // Launched rather than awaited: the subtitle search hashes the
            // source over the network, and the video has no reason to wait for
            // it. In the graph's scope, so leaving the picker does not cancel it.
            scope.launch { attachSubtitle(media) }
            pumpNext()
        }
        return result
    }

    /**
     * Refuses only what is known not to fit, keeping [SpaceReserveBytes] back
     * so a download cannot fill the device to the last byte.
     */
    private fun spaceShortfall(media: DownloadMedia): DownloadStartResult.NotEnoughSpace? {
        val required = media.videoSize?.takeIf { it > 0 } ?: return null
        val free = storage.freeBytes()?.takeIf { it >= 0 } ?: return null
        if (free >= required + SpaceReserveBytes) return null
        return DownloadStartResult.NotEnoughSpace(requiredBytes = required, freeBytes = free)
    }

    private suspend fun attachSubtitle(media: DownloadMedia) {
        val found = subtitles.fetch(media) ?: return
        mutex.withLock {
            // The download may have been removed while the subtitle was in
            // flight; the file it left behind is the next sweep's problem.
            val entry = current(media.videoId) ?: return
            if (entry.subtitle != null) return
            put(entry.copy(subtitle = found, updatedAt = clock.nowMs()))
        }
    }

    /** Stops a transfer, keeping what it has already written for the resume. */
    suspend fun pause(videoId: String) {
        val job = mutex.withLock {
            val entry = current(videoId) ?: return
            if (!entry.status.isActive) return
            put(entry.copy(status = DownloadStatus.Paused, updatedAt = clock.nowMs(), bytesPerSecond = 0))
            if (active?.videoId == videoId) rate.clear()
            active?.takeIf { it.videoId == videoId }?.job
        }
        job?.cancelAndJoin()
        pumpNext()
    }

    /** Puts a paused or failed entry back in the queue. */
    suspend fun resume(videoId: String) {
        mutex.withLock {
            val entry = current(videoId) ?: return
            if (entry.status == DownloadStatus.Done || entry.status.isActive) return
            put(
                entry.copy(
                    status = DownloadStatus.Queued,
                    failureMessage = null,
                    updatedAt = clock.nowMs(),
                ),
            )
        }
        pumpNext()
    }

    /** Forgets the entry and deletes everything it owned, whatever state it was in. */
    suspend fun remove(videoId: String) {
        val (entry, job) = mutex.withLock {
            val entry = current(videoId) ?: return
            _entries.value = _entries.value.filterNot { it.videoId == videoId }
            index.write(_entries.value)
            entry to active?.takeIf { it.videoId == videoId }?.job
        }
        // The transfer is stopped before its files are taken away, rather than
        // deleted from under a writer that is still appending to them.
        job?.cancelAndJoin()
        directory?.let { discardFiles(entry, it) }
        pumpNext()
    }

    /**
     * Brings the restored index into line with what is actually on disk, then
     * removes what nothing points at.
     *
     * Byte counts come from the partial files rather than from the index: the
     * index is written at most twice a second, so its last number predates the
     * app going away by up to that much, and the file is the truth.
     */
    private suspend fun settle(sweep: Boolean) {
        val root = directory ?: return
        mutex.withLock {
            fileSystem.createDirectories(root)
            _entries.value = _entries.value.map { entry ->
                val written = fileSystem.metadataOrNull(root / entry.partFileName)?.size
                when {
                    entry.status == DownloadStatus.Done -> entry
                    written == null -> entry
                    else -> entry.copy(downloadedBytes = written)
                }
            }
            // A download whose file is gone (cleared by the system, or removed
            // by hand) is not a download any more, whatever the index says.
            _entries.value = _entries.value.map { entry ->
                if (entry.status != DownloadStatus.Done) return@map entry
                if (fileSystem.exists(root / entry.fileName)) return@map entry
                entry.copy(
                    status = DownloadStatus.Failed,
                    failureMessage = "This download is no longer on the device.",
                    downloadedBytes = 0,
                    updatedAt = clock.nowMs(),
                )
            }
            index.write(_entries.value)
            if (sweep) DownloadPaths.sweepOrphans(fileSystem, root, _entries.value)
        }
    }

    /** Starts the oldest queued entry when nothing else is running. */
    private suspend fun pumpNext() {
        mutex.withLock {
            if (active != null) return
            val next = _entries.value
                .filter { it.status == DownloadStatus.Queued }
                .minByOrNull { it.createdAt }
                ?: return
            val job = scope.launch { run(next.videoId) }
            active = ActiveTransfer(next.videoId, job)
        }
    }

    private suspend fun run(videoId: String) {
        val root = directory ?: return
        try {
            val entry = mutex.withLock {
                val queued = current(videoId) ?: return@withLock null
                val started = queued.copy(status = DownloadStatus.Downloading, updatedAt = clock.nowMs())
                put(started)
                started
            } ?: return

            val result = transfer.transfer(
                sourceUrl = entry.media.sourceUrl,
                partFile = root / entry.partFileName,
                target = root / entry.fileName,
                resumeValidator = entry.resumeValidator,
                onProgress = { progress -> record(videoId, progress) },
            )
            mutex.withLock {
                val current = current(videoId) ?: return@withLock
                rate.clear()
                put(
                    current.copy(
                        status = DownloadStatus.Done,
                        downloadedBytes = result.downloadedBytes,
                        totalBytes = result.totalBytes,
                        resumeValidator = result.validator ?: current.resumeValidator,
                        failureMessage = null,
                        updatedAt = clock.nowMs(),
                        bytesPerSecond = 0,
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            // Pausing and removing both cancel this job after writing the state
            // they intended, so there is nothing to record here.
            throw cancellation
        } catch (failure: Throwable) {
            mutex.withLock {
                val current = current(videoId) ?: return@withLock
                rate.clear()
                put(
                    current.copy(
                        status = DownloadStatus.Failed,
                        failureMessage = failureMessage(failure),
                        updatedAt = clock.nowMs(),
                        bytesPerSecond = 0,
                    ),
                )
            }
        } finally {
            // NonCancellable because this also has to run when the job was
            // cancelled: a slot that is never released stalls the whole queue.
            withContext(NonCancellable) {
                mutex.withLock { if (active?.videoId == videoId) active = null }
            }
        }
        pumpNext()
    }

    /** Progress arrives throttled from the transfer; each report is persisted. */
    private suspend fun record(videoId: String, progress: TransferProgress) {
        mutex.withLock {
            val entry = current(videoId) ?: return
            if (entry.status != DownloadStatus.Downloading) return
            val now = clock.nowMs()
            put(
                entry.copy(
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    resumeValidator = progress.validator ?: entry.resumeValidator,
                    updatedAt = now,
                    bytesPerSecond = rate.sample(videoId, progress.downloadedBytes, now),
                ),
            )
        }
    }

    private fun current(videoId: String): DownloadEntry? =
        _entries.value.firstOrNull { it.videoId == videoId }

    /** Publishes and persists in one step, so the two can never disagree. */
    private fun put(entry: DownloadEntry) {
        val existing = _entries.value.indexOfFirst { it.videoId == entry.videoId }
        _entries.value = if (existing < 0) {
            _entries.value + entry
        } else {
            _entries.value.toMutableList().apply { set(existing, entry) }
        }
        index.write(_entries.value)
    }

    private fun discardFiles(entry: DownloadEntry, root: Path) {
        listOfNotNull(entry.fileName, entry.partFileName, entry.subtitle?.fileName).forEach { name ->
            try {
                fileSystem.delete(root / name, mustExist = false)
            } catch (_: okio.IOException) {
                // The sweep will find it later; failing a removal the viewer
                // asked for because a file will not delete helps nobody.
            }
        }
    }

    private fun failureMessage(failure: Throwable): String =
        (failure as? DownloadTransferException)?.message
            ?: "This download could not be completed."

    private companion object {
        /**
         * Headroom left free after a download. A device with nothing spare
         * cannot install an update or write a log, and a video is not worth
         * that.
         */
        const val SpaceReserveBytes = 256L * 1024 * 1024
    }
}

/** Blocking file access, on whichever dispatcher the caller provides. */
internal expect fun downloadFileSystem(): FileSystem
