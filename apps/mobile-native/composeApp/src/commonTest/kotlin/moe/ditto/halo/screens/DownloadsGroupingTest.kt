package moe.ditto.halo.screens

import moe.ditto.halo.downloads.DownloadEntry
import moe.ditto.halo.downloads.DownloadMedia
import moe.ditto.halo.downloads.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadsGroupingTest {

    @Test
    fun downloadsBelongToTheTitleTheyCameFrom() {
        val groups = groupDownloads(
            listOf(
                episode("s1e2", tag = "S01E02", createdAt = 20),
                movie("film", createdAt = 30),
                episode("s1e1", tag = "S01E01", createdAt = 10),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(listOf("movie:film", "series:show"), groups.map { it.itemId }.sorted())
        val show = groups.single { it.itemId == "series:show" }
        // Episode order, not download order: filling a gap in a season last must
        // not leave that episode at the bottom.
        assertEquals(listOf("S01E01", "S01E02"), show.entries.map { it.media.episodeTag })
    }

    @Test
    fun theMostRecentlyAddedTitleComesFirst() {
        val groups = groupDownloads(
            listOf(
                episode("s1e1", tag = "S01E01", createdAt = 10),
                movie("film", createdAt = 30),
            ),
        )

        assertEquals(listOf("movie:film", "series:show"), groups.map { it.itemId })
    }

    @Test
    fun aGroupTakesArtFromWhicheverEntryHasIt() {
        val groups = groupDownloads(
            listOf(
                episode("s1e1", tag = "S01E01", createdAt = 10, poster = null),
                episode("s1e2", tag = "S01E02", createdAt = 20, poster = "https://art.test/p.jpg"),
            ),
        )

        assertEquals("https://art.test/p.jpg", groups.single().poster)
    }

    @Test
    fun aRowIsNamedByItsEpisodeAndAFilmByItself() {
        assertEquals(
            "S01E02 · The One With The Thing",
            downloadRowTitle(episode("s1e2", tag = "S01E02", episodeName = "The One With The Thing")),
        )
        assertEquals("S01E02", downloadRowTitle(episode("s1e2", tag = "S01E02")))
        assertEquals("A Film", downloadRowTitle(movie("film", showTitle = "A Film")))
    }

    @Test
    fun everyStateSaysWhatItIsDoing() {
        assertEquals(
            "Downloaded · 1.5 GB",
            downloadStatusLabel(movie("f", status = DownloadStatus.Done, total = 1_610_612_736, done = 1_610_612_736)),
        )
        assertEquals(
            "512 MB of 1.5 GB",
            downloadStatusLabel(
                movie("f", status = DownloadStatus.Downloading, total = 1_610_612_736, done = 536_870_912),
            ),
        )
        // Nothing has arrived and the source declared no size: there is no
        // fraction and no byte count to show yet.
        assertEquals(
            "Starting…",
            downloadStatusLabel(movie("f", status = DownloadStatus.Downloading, total = 0, done = 0)),
        )
        assertEquals(
            "Paused · 512 MB",
            downloadStatusLabel(movie("f", status = DownloadStatus.Paused, total = 1_610_612_736, done = 536_870_912)),
        )
        assertEquals(
            "Waiting for the current download",
            downloadStatusLabel(movie("f", status = DownloadStatus.Queued, total = 0, done = 0)),
        )
    }

    @Test
    fun aFailureShowsWhatFailedRatherThanAByteCount() {
        val failed = movie("f", status = DownloadStatus.Failed, total = 100, done = 40)
            .copy(failureMessage = "This source is no longer available.")

        assertEquals("This source is no longer available.", downloadStatusLabel(failed))
    }

    @Test
    fun aFailureWithNothingToSayStillSaysSomething() {
        val failed = movie("f", status = DownloadStatus.Failed, total = 100, done = 40)

        assertTrue(downloadStatusLabel(failed).isNotEmpty())
    }

    @Test
    fun summariesCountWhatIsActuallyOnTheDevice() {
        val entries = listOf(
            movie("a", status = DownloadStatus.Done, total = 1_073_741_824, done = 1_073_741_824),
            // Half arrived: the summary is about space used, not space promised.
            movie("b", status = DownloadStatus.Paused, total = 1_073_741_824, done = 536_870_912),
        )

        assertEquals("2 items · 1.5 GB on device", downloadsSummary(entries))
    }

    @Test
    fun aGroupSaysHowManyAreStillRunning() {
        val entries = listOf(
            episode("s1e1", tag = "S01E01", status = DownloadStatus.Done, total = 1_073_741_824),
            episode("s1e2", tag = "S01E02", status = DownloadStatus.Downloading, total = 0, done = 0),
        )

        assertEquals("2 downloads · 1.0 GB · 1 in progress", downloadGroupSummary(entries))
    }

    @Test
    fun anEmptyLibraryHasNothingToSummarise() {
        assertEquals("", downloadsSummary(emptyList()))
    }
}

private fun movie(
    videoId: String,
    showTitle: String = "A Film",
    status: DownloadStatus = DownloadStatus.Done,
    total: Long = 100,
    done: Long = 100,
    createdAt: Long = 1,
): DownloadEntry = DownloadEntry(
    media = DownloadMedia(
        videoId = videoId,
        type = "movie",
        metaId = videoId,
        showTitle = showTitle,
        sourceUrl = "https://source.test/$videoId.mkv",
        addonId = "addon",
    ),
    fileName = "$videoId.mkv",
    status = status,
    totalBytes = total,
    downloadedBytes = done,
    createdAt = createdAt,
    updatedAt = createdAt,
)

private fun episode(
    videoId: String,
    tag: String? = null,
    episodeName: String? = null,
    status: DownloadStatus = DownloadStatus.Done,
    total: Long = 100,
    done: Long = 100,
    createdAt: Long = 1,
    poster: String? = "https://art.test/p.jpg",
): DownloadEntry = DownloadEntry(
    media = DownloadMedia(
        videoId = videoId,
        type = "series",
        metaId = "show",
        showTitle = "A Show",
        episodeTag = tag,
        episodeName = episodeName,
        poster = poster,
        sourceUrl = "https://source.test/$videoId.mkv",
        addonId = "addon",
    ),
    fileName = "$videoId.mkv",
    status = status,
    totalBytes = total,
    downloadedBytes = done,
    createdAt = createdAt,
    updatedAt = createdAt,
)
