package moe.ditto.halo.screens.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moe.ditto.halo.player.VideoFrameReader
import moe.ditto.halo.player.VideoFrameSource

/**
 * The rules that keep an expensive reader in front of a fast finger: one decode
 * per slot, only the newest request while one is in flight, a bounded cache, a
 * reader that does not outlive the scrubbing, and no picture shown under a
 * timecode it does not belong to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrubPreviewFramesTest {

    @Test
    fun quantisesPositionsSoOneSlotDecodesOnce() = runTest {
        val source = FakeFrameSource()
        val frames = scrubFrames(source)

        frames.request(31.0)
        frames.request(34.5)
        frames.request(39.9)
        runCurrent()

        assertEquals(listOf(30.0), source.readers.single().requested)
        assertNotNull(frames.frameFor(37.0))
        frames.close()
    }

    @Test
    fun answersOnlyForTheSlotAskedAbout() = runTest {
        val source = FakeFrameSource()
        val frames = scrubFrames(source)

        frames.request(12.0)
        runCurrent()

        // Same slot as the decoded one, so the picture belongs under it.
        assertNotNull(frames.frameFor(19.0))
        // A neighbouring slot is not decoded, and the card must show its
        // placeholder rather than a picture from ten seconds away.
        assertNull(frames.frameFor(22.0))
        frames.close()
    }

    @Test
    fun decodesOnlyTheNewestRequestMadeWhileOneIsInFlight() = runTest {
        val held = CompletableDeferred<Unit>()
        val source = FakeFrameSource(configure = { it.gate = held })
        val frames = scrubFrames(source)

        frames.request(5.0)
        runCurrent()

        // The drag crosses three more slots while the first frame is still
        // being decoded. Only the one it stopped on is worth having.
        frames.request(105.0)
        frames.request(205.0)
        frames.request(305.0)
        held.complete(Unit)
        runCurrent()

        assertEquals(listOf(0.0, 300.0), source.readers.single().requested)
        frames.close()
    }

    @Test
    fun givesUpOnASourceThatWillNotOpen() = runTest {
        val source = FakeFrameSource(openFailures = Int.MAX_VALUE)
        val frames = scrubFrames(source)

        frames.request(10.0)
        runCurrent()
        frames.request(110.0)
        runCurrent()
        // Two failures is the whole allowance: one covers a blip, and a third
        // attempt would block the reader again for a picture that never comes.
        frames.request(210.0)
        runCurrent()

        assertEquals(2, source.opens)
        assertNull(frames.frameFor(10.0))
        frames.close()
    }

    @Test
    fun retriesAfterASingleFailedOpen() = runTest {
        val source = FakeFrameSource(openFailures = 1)
        val frames = scrubFrames(source)

        frames.request(10.0)
        runCurrent()
        assertNull(frames.frameFor(10.0))

        frames.request(110.0)
        runCurrent()

        assertNotNull(frames.frameFor(110.0))
        frames.close()
    }

    @Test
    fun evictsTheOldestFrameOnceTheCacheIsFull() = runTest {
        val source = FakeFrameSource()
        val frames = scrubFrames(source)

        // Thirteen slots into a cache of twelve.
        repeat(13) { index ->
            frames.request(index * 10.0)
            runCurrent()
        }

        assertNull(frames.frameFor(0.0))
        assertNotNull(frames.frameFor(10.0))
        assertNotNull(frames.frameFor(120.0))
        frames.close()
    }

    @Test
    fun releasesTheReaderWhenScrubbingStopsAndReopensOnTheNextScrub() = runTest {
        val source = FakeFrameSource()
        val frames = scrubFrames(source)

        frames.request(10.0)
        runCurrent()
        val first = source.readers.single()
        assertEquals(0, first.closeCount)

        advanceTimeBy(ReaderIdleAllowanceMillis)
        runCurrent()
        assertEquals(1, first.closeCount)

        frames.request(500.0)
        runCurrent()
        assertEquals(2, source.opens)
        // Frames already paid for survive the release; coming back to the same
        // stretch of the timeline is the common case.
        assertNotNull(frames.frameFor(10.0))
        frames.close()
    }

    @Test
    fun closingReleasesTheReaderAndStopsDecoding() = runTest {
        val source = FakeFrameSource()
        val frames = scrubFrames(source)

        frames.request(10.0)
        runCurrent()
        frames.close()

        assertEquals(1, source.readers.single().closeCount)
        assertNull(frames.frameFor(10.0))

        frames.request(500.0)
        runCurrent()
        assertEquals(1, source.opens)
        assertEquals(1, source.readers.single().closeCount)
    }
}

/** Comfortably past the coordinator's own idle window. */
private const val ReaderIdleAllowanceMillis = 30_000L

private const val FixtureUrl = "https://fixture.test/episode.mkv"

private fun kotlinx.coroutines.test.TestScope.scrubFrames(source: FakeFrameSource) =
    ScrubPreviewFrames(
        source = source,
        url = FixtureUrl,
        frameWidthPx = 176,
        frameHeightPx = 99,
        scope = backgroundScope,
    )

private class FakeFrameSource(
    /** How many opens fail before one succeeds. */
    private val openFailures: Int = 0,
    private val configure: (FakeFrameReader) -> Unit = {},
) : VideoFrameSource {
    var opens = 0
        private set
    val readers = mutableListOf<FakeFrameReader>()

    override suspend fun open(url: String): VideoFrameReader? {
        opens += 1
        if (opens <= openFailures) return null
        return FakeFrameReader().also {
            configure(it)
            readers += it
        }
    }
}

private class FakeFrameReader : VideoFrameReader {
    val requested = mutableListOf<Double>()
    var closeCount = 0
        private set

    /** Holds the first decode open, so a drag can overtake it. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun frameAt(positionSeconds: Double, widthPx: Int, heightPx: Int): ImageBitmap? {
        requested += positionSeconds
        gate?.let {
            gate = null
            it.await()
        }
        return FakeImageBitmap(widthPx, heightPx)
    }

    override fun close() {
        closeCount += 1
    }
}

/**
 * [ImageBitmap] is an interface, so the coordinator can be tested without any
 * graphics backend: nothing here ever draws one.
 */
private class FakeImageBitmap(
    override val width: Int,
    override val height: Int,
) : ImageBitmap {
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = false

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit

    override fun prepareToDraw() = Unit
}
