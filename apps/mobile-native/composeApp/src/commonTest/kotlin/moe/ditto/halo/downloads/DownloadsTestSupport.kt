package moe.ditto.halo.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import okio.Path

internal fun media(
    videoId: String = "tt0111161",
    type: String = "movie",
    metaId: String = "tt0111161",
    showTitle: String = "The Shawshank Redemption",
    episodeTag: String? = null,
    sourceUrl: String = "https://source.test/movie.mkv",
    videoSize: Long? = null,
): DownloadMedia = DownloadMedia(
    videoId = videoId,
    type = type,
    metaId = metaId,
    showTitle = showTitle,
    episodeTag = episodeTag,
    poster = "https://art.test/poster.jpg",
    sourceUrl = sourceUrl,
    addonId = "addon-1",
    videoSize = videoSize,
)

internal fun entry(
    videoId: String = "tt0111161",
    status: DownloadStatus = DownloadStatus.Done,
    fileName: String = "$videoId.mkv",
    subtitle: DownloadSubtitle? = null,
    totalBytes: Long = 100,
    downloadedBytes: Long = 100,
    createdAt: Long = 1_000,
): DownloadEntry = DownloadEntry(
    media = media(videoId = videoId),
    fileName = fileName,
    subtitle = subtitle,
    status = status,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    createdAt = createdAt,
    updatedAt = createdAt,
)

/** A subtitle search with a fixed answer, so the coordinator's side of it is testable. */
internal class FixedDownloadSubtitles(private val subtitle: DownloadSubtitle?) : DownloadSubtitleSource {
    override suspend fun fetch(media: DownloadMedia): DownloadSubtitle? = subtitle
}

internal class FakeDownloadStorage(
    private val path: String? = "/downloads",
    private val free: Long? = null,
) : DownloadStoragePort {
    override fun directory(): String? = path
    override fun freeBytes(): Long? = free
}

/**
 * A transfer that does nothing until the test says so, which is what makes the
 * queue observable: while one call is outstanding, no other may have started.
 */
internal class GatedTransfer : DownloadTransfer {
    val calls = mutableListOf<Call>()

    class Call(
        val sourceUrl: String,
        val partFile: Path,
        val target: Path,
        val resumeValidator: String?,
        val onProgress: suspend (TransferProgress) -> Unit,
    ) {
        val gate = CompletableDeferred<TransferProgress>()

        /** Set when the coordinator cancelled this call, which is how a pause reaches a transfer. */
        var cancelled: Boolean = false
            internal set

        fun finish(downloadedBytes: Long = 100, totalBytes: Long = 100, validator: String? = null) {
            gate.complete(TransferProgress(downloadedBytes, totalBytes, validator))
        }

        fun fail(message: String = "the source refused") {
            gate.completeExceptionally(DownloadTransferException(message))
        }
    }

    override suspend fun transfer(
        sourceUrl: String,
        partFile: Path,
        target: Path,
        resumeValidator: String?,
        onProgress: suspend (TransferProgress) -> Unit,
    ): TransferProgress {
        val call = Call(sourceUrl, partFile, target, resumeValidator, onProgress)
        calls += call
        try {
            return call.gate.await()
        } catch (cancellation: CancellationException) {
            call.cancelled = true
            throw cancellation
        }
    }
}
