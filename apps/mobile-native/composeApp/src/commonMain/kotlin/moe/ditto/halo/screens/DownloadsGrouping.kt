package moe.ditto.halo.screens

import moe.ditto.halo.downloads.DownloadEntry
import moe.ditto.halo.downloads.DownloadStatus
import moe.ditto.halo.ui.formatBytes

/**
 * One title's downloads, as the screen shows them: a header for the title and
 * a row per video underneath it.
 *
 * A download belongs to its show rather than standing on its own, which is what
 * makes a season of a series read as a season rather than as twelve unrelated
 * files.
 */
internal data class DownloadGroup(
    val itemId: String,
    val type: String,
    val metaId: String,
    val name: String,
    val poster: String?,
    val entries: List<DownloadEntry>,
)

/**
 * Groups by library item, newest title first, and orders each group the way its
 * episodes run.
 *
 * Episode order comes from the tag rather than from when each was downloaded:
 * someone who filled a gap in a season last would otherwise find that episode
 * at the bottom. Films and anything untagged fall back to download order, which
 * is the only order they have.
 */
internal fun groupDownloads(entries: List<DownloadEntry>): List<DownloadGroup> {
    val byItem = LinkedHashMap<String, MutableList<DownloadEntry>>()
    entries.forEach { entry -> byItem.getOrPut(entry.itemId) { mutableListOf() }.add(entry) }
    return byItem.map { (itemId, group) ->
        val first = group.minByOrNull { it.createdAt } ?: group.first()
        DownloadGroup(
            itemId = itemId,
            type = first.media.type,
            metaId = first.media.metaId,
            name = first.media.showTitle,
            poster = group.firstNotNullOfOrNull { it.media.poster },
            entries = group.sortedWith(
                compareBy(
                    { it.media.episodeTag ?: "" },
                    { it.createdAt },
                ),
            ),
        )
    }.sortedByDescending { group -> group.entries.maxOf { it.createdAt } }
}

/** What one row is called: the episode, or the film's own name. */
internal fun downloadRowTitle(entry: DownloadEntry): String =
    entry.media.episodeTag?.let { tag ->
        entry.media.episodeName?.let { name -> "$tag · $name" } ?: tag
    } ?: entry.media.showTitle

/**
 * The line under a row's title. Bytes are shown while they are still arriving
 * and once they have; a failure shows what failed instead, because that is the
 * only thing the viewer can act on.
 */
internal fun downloadStatusLabel(entry: DownloadEntry): String = when (entry.status) {
    DownloadStatus.Done -> listOfNotNull(
        "Downloaded",
        formatBytes(entry.totalBytes.takeIf { it > 0 } ?: entry.downloadedBytes).ifEmpty { null },
    ).joinToString(" · ")

    DownloadStatus.Downloading -> when {
        entry.totalBytes > 0 ->
            "${formatBytes(entry.downloadedBytes)} of ${formatBytes(entry.totalBytes)}"
        entry.downloadedBytes > 0 -> formatBytes(entry.downloadedBytes)
        else -> "Starting…"
    }

    DownloadStatus.Queued -> "Waiting for the current download"
    DownloadStatus.Paused -> listOfNotNull(
        "Paused",
        formatBytes(entry.downloadedBytes).ifEmpty { null },
    ).joinToString(" · ")

    DownloadStatus.Failed -> entry.failureMessage ?: "This download did not finish."
}

/**
 * How much of the device this is using, under the screen's own title.
 *
 * Sizes count what is actually on the device rather than what a finished
 * library would weigh, so a paused download contributes the part of it that
 * has arrived.
 */
internal fun downloadsSummary(entries: List<DownloadEntry>): String {
    if (entries.isEmpty()) return ""
    val bytes = entries.sumOf(::bytesOnDevice)
    val count = entries.size
    val size = formatBytes(bytes)
    val items = "$count ${if (count == 1) "item" else "items"}"
    return if (size.isEmpty()) items else "$items · $size on device"
}

/** The same for one title, plus what it is still doing. */
internal fun downloadGroupSummary(entries: List<DownloadEntry>): String {
    val parts = mutableListOf("${entries.size} ${if (entries.size == 1) "download" else "downloads"}")
    formatBytes(entries.sumOf(::bytesOnDevice)).takeIf { it.isNotEmpty() }?.let(parts::add)
    val active = entries.count { it.status.isActive }
    if (active > 0) parts.add("$active in progress")
    return parts.joinToString(" · ")
}

private fun bytesOnDevice(entry: DownloadEntry): Long = when (entry.status) {
    DownloadStatus.Done -> entry.totalBytes.takeIf { it > 0 } ?: entry.downloadedBytes
    else -> entry.downloadedBytes
}
