package moe.ditto.halo.downloads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import moe.ditto.halo.storage.StorageKeys
import moe.ditto.halo.sync.FakeClock
import moe.ditto.halo.sync.FakeStore
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadsCoordinatorTest {

    @Test
    fun askingForTheSameVideoTwiceChangesNothing() = runTest {
        val world = world()

        val first = world.coordinator.start(media(videoId = "tt1"))
        val second = world.coordinator.start(media(videoId = "tt1", sourceUrl = "https://other.test/a.mkv"))

        assertIs<DownloadStartResult.Started>(first)
        val existing = assertIs<DownloadStartResult.AlreadyExists>(second)
        assertEquals("https://source.test/movie.mkv", existing.entry.media.sourceUrl)
        assertEquals(1, world.coordinator.entries.value.size)
    }

    @Test
    fun aDeviceWithNowhereToKeepDownloadsRefusesRatherThanPretends() = runTest {
        val world = world(storage = FakeDownloadStorage(path = null))

        val result = world.coordinator.start(media())

        assertIs<DownloadStartResult.Unavailable>(result)
        assertFalse(world.coordinator.isAvailable)
        assertEquals(emptyList(), world.coordinator.entries.value)
    }

    @Test
    fun onlyOneTransferRunsAtATimeAndTheOldestQueuedGoesFirst() = runTest {
        val world = world()

        world.coordinator.start(media(videoId = "first", sourceUrl = "https://source.test/1.mkv"))
        world.clock.now += 10
        world.coordinator.start(media(videoId = "second", sourceUrl = "https://source.test/2.mkv"))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("https://source.test/1.mkv"), world.transfer.calls.map { it.sourceUrl })
        assertEquals(DownloadStatus.Downloading, world.status("first"))
        assertEquals(DownloadStatus.Queued, world.status("second"))

        world.transfer.calls.single().finish()
        testScheduler.advanceUntilIdle()

        assertEquals(DownloadStatus.Done, world.status("first"))
        assertEquals(DownloadStatus.Downloading, world.status("second"))
    }

    @Test
    fun pausingStopsTheTransferAndLetsTheQueueMoveOn() = runTest {
        val world = world()
        world.coordinator.start(media(videoId = "first"))
        world.clock.now += 10
        world.coordinator.start(media(videoId = "second", sourceUrl = "https://source.test/2.mkv"))
        testScheduler.advanceUntilIdle()

        world.coordinator.pause("first")
        testScheduler.advanceUntilIdle()

        assertEquals(DownloadStatus.Paused, world.status("first"))
        assertEquals(DownloadStatus.Downloading, world.status("second"))
        assertTrue(world.transfer.calls[0].cancelled)
    }

    @Test
    fun resumingAFailedEntryClearsWhatItSaidAndQueuesItAgain() = runTest {
        val world = world()
        world.coordinator.start(media(videoId = "tt1"))
        testScheduler.advanceUntilIdle()
        world.transfer.calls.single().fail("the source refused")
        testScheduler.advanceUntilIdle()
        assertEquals(DownloadStatus.Failed, world.status("tt1"))
        assertEquals("the source refused", world.entry("tt1").failureMessage)

        world.coordinator.resume("tt1")
        testScheduler.advanceUntilIdle()

        assertEquals(DownloadStatus.Downloading, world.status("tt1"))
        assertNull(world.entry("tt1").failureMessage)
        assertEquals(2, world.transfer.calls.size)
    }

    @Test
    fun aFinishedDownloadIsNotStartedAgainByResuming() = runTest {
        val world = world()
        world.coordinator.start(media(videoId = "tt1"))
        testScheduler.advanceUntilIdle()
        world.transfer.calls.single().finish()
        testScheduler.advanceUntilIdle()

        world.coordinator.resume("tt1")
        testScheduler.advanceUntilIdle()

        assertEquals(DownloadStatus.Done, world.status("tt1"))
        assertEquals(1, world.transfer.calls.size)
    }

    @Test
    fun removingTakesTheFilesWithIt() = runTest {
        val world = world()
        world.coordinator.start(media(videoId = "tt1"))
        testScheduler.advanceUntilIdle()
        val call = world.transfer.calls.single()
        world.fileSystem.write(call.partFile) { writeUtf8("partial") }

        world.coordinator.remove("tt1")
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), world.coordinator.entries.value)
        assertFalse(world.fileSystem.exists(call.partFile))
        assertTrue(call.cancelled)
    }

    @Test
    fun progressFromTheTransferIsPublishedAndPersisted() = runTest {
        val world = world()
        world.coordinator.start(media(videoId = "tt1"))
        testScheduler.advanceUntilIdle()

        world.transfer.calls.single().onProgress(TransferProgress(512, 2_048, "\"v1\""))
        testScheduler.advanceUntilIdle()

        val entry = world.entry("tt1")
        assertEquals(512, entry.downloadedBytes)
        assertEquals(2_048, entry.totalBytes)
        // Kept so the next resume can guard itself with If-Range.
        assertEquals("\"v1\"", entry.resumeValidator)
        assertEquals(entry, DownloadIndex(world.store).read().entries.single())
    }

    @Test
    fun anEntryThatWasTransferringWhenTheAppDiedComesBackPaused() = runTest {
        val store = FakeStore()
        DownloadIndex(store).write(
            listOf(
                entry(videoId = "was-downloading", status = DownloadStatus.Downloading, downloadedBytes = 10),
                entry(videoId = "was-queued", status = DownloadStatus.Queued, downloadedBytes = 0),
                entry(videoId = "was-done", status = DownloadStatus.Done),
            ),
        )
        val world = world(store = store, files = listOf("was-done.mkv"))
        testScheduler.advanceUntilIdle()

        assertEquals(DownloadStatus.Paused, world.status("was-downloading"))
        // Queued too: resuming gigabytes on launch would spend mobile data
        // nobody asked to spend.
        assertEquals(DownloadStatus.Paused, world.status("was-queued"))
        assertEquals(DownloadStatus.Done, world.status("was-done"))
        assertEquals(emptyList(), world.transfer.calls)
    }

    @Test
    fun byteCountsAreTakenFromThePartialFileRatherThanTheIndex() = runTest {
        val store = FakeStore()
        DownloadIndex(store).write(
            listOf(entry(videoId = "tt1", status = DownloadStatus.Downloading, fileName = "tt1.mkv", downloadedBytes = 10)),
        )
        val world = world(store = store, files = listOf("tt1.mkv.part" to "0123456789abcdef"))
        testScheduler.advanceUntilIdle()

        assertEquals(16, world.entry("tt1").downloadedBytes)
    }

    @Test
    fun aFinishedDownloadWhoseFileIsGoneStopsClaimingToBeOnTheDevice() = runTest {
        val store = FakeStore()
        DownloadIndex(store).write(listOf(entry(videoId = "tt1", fileName = "tt1.mkv")))
        val world = world(store = store)
        testScheduler.advanceUntilIdle()

        assertEquals(DownloadStatus.Failed, world.status("tt1"))
        assertEquals(0, world.entry("tt1").downloadedBytes)
    }

    @Test
    fun anIndexThatCouldNotBeReadIsNeverSweptAgainstTheFilesOnDisk() = runTest {
        val store = FakeStore()
        store.write(StorageKeys.Downloads, "{not json at all")
        val world = world(store = store, files = listOf("a-real-download.mkv"))
        testScheduler.advanceUntilIdle()

        // Every file looks orphaned against an index that failed to decode.
        assertTrue(world.fileSystem.exists("/downloads/a-real-download.mkv".toPath()))
    }

    @Test
    fun filesNoEntryClaimsAreRemovedWhenTheIndexIsWhole() = runTest {
        val store = FakeStore()
        DownloadIndex(store).write(listOf(entry(videoId = "tt1", fileName = "tt1.mkv")))
        val world = world(store = store, files = listOf("tt1.mkv", "stray.mkv"))
        testScheduler.advanceUntilIdle()

        assertTrue(world.fileSystem.exists("/downloads/tt1.mkv".toPath()))
        assertFalse(world.fileSystem.exists("/downloads/stray.mkv".toPath()))
    }

    @Test
    fun askingAgainAfterAFailureReplacesTheEntryWithTheNewSource() = runTest {
        val world = world()
        world.coordinator.start(media(videoId = "tt1"))
        testScheduler.advanceUntilIdle()
        world.transfer.calls.single().fail()
        testScheduler.advanceUntilIdle()

        val result = world.coordinator.start(media(videoId = "tt1", sourceUrl = "https://source.test/better.mkv"))
        testScheduler.advanceUntilIdle()

        assertIs<DownloadStartResult.Started>(result)
        assertEquals("https://source.test/better.mkv", world.entry("tt1").media.sourceUrl)
        assertEquals(DownloadStatus.Downloading, world.status("tt1"))
    }

    @Test
    fun theChosenSubtitleIsStoredAgainstTheEntry() = runTest {
        val subtitle = DownloadSubtitle(fileName = "tt1.srt", lang = "eng", subId = "os-9")
        val world = world(subtitles = FixedDownloadSubtitles(subtitle))
        world.coordinator.start(media(videoId = "tt1"))
        testScheduler.advanceUntilIdle()

        assertEquals(subtitle, world.entry("tt1").subtitle)
        assertEquals(subtitle, DownloadIndex(world.store).read().entries.single().subtitle)
    }

    @Test
    fun aSubtitleThatArrivesAfterTheDownloadWasRemovedIsDropped() = runTest {
        val world = world(subtitles = FixedDownloadSubtitles(DownloadSubtitle(fileName = "tt1.srt", lang = "eng")))
        world.coordinator.start(media(videoId = "tt1"))
        world.coordinator.remove("tt1")
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), world.coordinator.entries.value)
    }

    @Test
    fun aSourceTooBigForTheDeviceIsRefusedBeforeAnythingIsWritten() = runTest {
        val world = world(storage = FakeDownloadStorage(free = 2_000_000_000))

        val result = world.coordinator.start(media(videoId = "tt1", videoSize = 1_900_000_000))

        // Refused on the reserve, not on the raw comparison: filling the device
        // to its last byte is its own failure.
        val refusal = assertIs<DownloadStartResult.NotEnoughSpace>(result)
        assertEquals(1_900_000_000, refusal.requiredBytes)
        assertEquals(2_000_000_000, refusal.freeBytes)
        assertEquals(emptyList(), world.coordinator.entries.value)
    }

    @Test
    fun aSourceThatFitsIsAccepted() = runTest {
        val world = world(storage = FakeDownloadStorage(free = 8_000_000_000))

        val result = world.coordinator.start(media(videoId = "tt1", videoSize = 1_900_000_000))

        assertIs<DownloadStartResult.Started>(result)
    }

    @Test
    fun anUndeclaredSizeIsNeverRefusedOnAGuess() = runTest {
        val world = world(storage = FakeDownloadStorage(free = 1))

        val result = world.coordinator.start(media(videoId = "tt1", videoSize = null))

        assertIs<DownloadStartResult.Started>(result)
    }
}

private class World(
    val coordinator: DownloadsCoordinator,
    val transfer: GatedTransfer,
    val store: FakeStore,
    val clock: FakeClock,
    val fileSystem: FakeFileSystem,
) {
    fun entry(videoId: String): DownloadEntry =
        requireNotNull(coordinator.entryFor(videoId)) { "no entry for $videoId" }

    fun status(videoId: String): DownloadStatus = entry(videoId).status
}

/**
 * [files] seeds the downloads directory; a bare name writes a placeholder, a
 * pair writes the given contents so a size can be asserted.
 */
private fun TestScope.world(
    store: FakeStore = FakeStore(),
    storage: DownloadStoragePort = FakeDownloadStorage(),
    files: List<Any> = emptyList(),
    subtitles: DownloadSubtitleSource = NoDownloadSubtitles,
): World {
    val fileSystem = FakeFileSystem()
    val directory = "/downloads".toPath()
    fileSystem.createDirectories(directory)
    files.forEach { file ->
        val (name, contents) = when (file) {
            is Pair<*, *> -> file.first as String to file.second as String
            else -> file as String to "seed"
        }
        fileSystem.write(directory / name) { writeUtf8(contents) }
    }
    val transfer = GatedTransfer()
    val clock = FakeClock()
    val coordinator = DownloadsCoordinator(
        index = DownloadIndex(store),
        storage = storage,
        transfer = transfer,
        clock = clock,
        // A scope of the test's own rather than backgroundScope: background
        // coroutines are not advanced by the scheduler, so the queue would
        // never be seen to move.
        scope = CoroutineScope(coroutineContext + SupervisorJob()),
        fileSystem = fileSystem,
        subtitles = subtitles,
    )
    return World(coordinator, transfer, store, clock, fileSystem)
}
