package moe.ditto.halo.downloads

import moe.ditto.halo.storage.StorageKeys
import moe.ditto.halo.sync.FakeStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadIndexTest {

    @Test
    fun everythingAnEntryKnowsSurvivesARoundTrip() {
        val store = FakeStore()
        val index = DownloadIndex(store)
        val original = entry(videoId = "tt1:1:2", status = DownloadStatus.Paused).copy(
            subtitle = DownloadSubtitle(fileName = "tt1.srt", lang = "eng", subId = "os-9"),
            resumeValidator = "\"abc\"",
            downloadedBytes = 40,
        )

        index.write(listOf(original))
        val snapshot = DownloadIndex(store).read()

        assertTrue(snapshot.complete)
        assertEquals(listOf(original), snapshot.entries)
    }

    @Test
    fun anEmptyStoreReadsAsAnEmptyIndexThatIsStillCompleteEnoughToSweep() {
        val snapshot = DownloadIndex(FakeStore()).read()

        assertEquals(emptyList(), snapshot.entries)
        assertTrue(snapshot.complete)
    }

    @Test
    fun anUnreadableDocumentYieldsNothingAndSaysItIsNotTheWholeStory() {
        val store = FakeStore()
        store.write(StorageKeys.Downloads, "{not json at all")

        val snapshot = DownloadIndex(store).read()

        assertEquals(emptyList(), snapshot.entries)
        // The sweep keys off this: an index that failed to decode looks exactly
        // like an empty one, and sweeping against it would delete every file.
        assertFalse(snapshot.complete)
    }

    @Test
    fun oneUnreadableRecordKeepsTheOthersAndStillBlocksTheSweep() {
        val store = FakeStore()
        DownloadIndex(store).write(listOf(entry(videoId = "good")))
        val stored = store.values.getValue(StorageKeys.Downloads)
        store.write(StorageKeys.Downloads, stored.dropLast(1) + ""","broken":7}""")

        val snapshot = DownloadIndex(store).read()

        assertEquals(listOf("good"), snapshot.entries.map { it.videoId })
        assertFalse(snapshot.complete)
    }

    @Test
    fun oneEntryPerVideoIsHowTheDocumentIsShaped() {
        val store = FakeStore()
        val index = DownloadIndex(store)

        index.write(
            listOf(
                entry(videoId = "same", status = DownloadStatus.Paused),
                entry(videoId = "same", status = DownloadStatus.Done),
            ),
        )

        val snapshot = index.read()
        assertEquals(1, snapshot.entries.size)
        assertEquals(DownloadStatus.Done, snapshot.entries.single().status)
    }
}
