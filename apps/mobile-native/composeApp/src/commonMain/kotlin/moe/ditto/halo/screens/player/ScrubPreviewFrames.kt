package moe.ditto.halo.screens.player

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.ditto.halo.player.VideoFrameReader
import moe.ditto.halo.player.VideoFrameSource

/**
 * How far apart the frames behind the scrub card are.
 *
 * The picture is approximate and the timecode on it is exact, which is the
 * arrangement every scrubber with thumbnails uses: a finger crossing a
 * two-hour timeline moves through several seconds per pixel, so a frame per
 * position would mean a decode per pixel and none of them would arrive. Ten
 * seconds is also near the keyframe spacing of most releases, so a finer step
 * would mostly re-decode the same picture under a different label.
 */
private const val FrameStepSeconds = 10.0

/**
 * How many decoded frames are kept.
 *
 * Each is the size of the card — a few hundred pixels square at device
 * density, so a handful of megabytes at this count. It is a cache for moving
 * back and forth across the same stretch, which is what aiming a scrub
 * actually looks like, not a filmstrip of the whole episode.
 */
private const val MaxCachedFrames = 12

/**
 * How long the reader outlives the last scrub before it is released.
 *
 * It holds a second connection to the source for as long as it is open, and
 * some hosts count those against the one that is playing. Long enough that
 * consecutive scrubs reuse it rather than paying to reopen; short enough that
 * watching an episode does not hold it for an hour.
 */
private const val ReaderIdleMillis = 20_000L

/**
 * How many failed opens are tolerated before this source is given up on.
 *
 * A failure cannot be attributed: an unsupported container and a network blip
 * look the same from here. One retry covers the blip, and giving up after it
 * keeps an unreadable file from paying the open cost on every gesture — which
 * is seconds of a blocked reader, for a picture that will never come.
 */
private const val MaxOpenFailures = 2

/**
 * The frames behind the scrub preview card.
 *
 * All of the awkwardness here comes from one fact: a frame is expensive and a
 * finger is fast. So requests are quantised to [FrameStepSeconds], only the
 * newest is honoured (the channel is conflated, so a drag that crosses twenty
 * slots decodes the one it stopped on rather than all twenty), decoded frames
 * are cached, and the reader is opened on the first scrub rather than at
 * playback and released once scrubbing stops.
 *
 * [frameFor] answers only for the slot asked about. It never substitutes a
 * neighbouring frame: the card puts the target timecode on the picture, and a
 * picture from elsewhere under that number is a lie rather than an
 * approximation. Missing is drawn as the placeholder the card shipped with.
 *
 * [scope] must be tied to the screen; the worker and the idle timer are
 * cancelled with it. [close] is still required, because the reader it holds is
 * not owned by that scope.
 */
internal class ScrubPreviewFrames(
    private val source: VideoFrameSource,
    private val url: String,
    private val frameWidthPx: Int,
    private val frameHeightPx: Int,
    private val scope: CoroutineScope,
) {
    private val frames = mutableStateMapOf<Long, ImageBitmap>()

    /** Insertion order, for eviction; the snapshot map does not promise one. */
    private val cached = ArrayDeque<Long>()

    private val requests = Channel<Long>(Channel.CONFLATED)

    private var reader: VideoFrameReader? = null
    private var worker: Job? = null
    private var idleRelease: Job? = null
    private var lastRequestedSlot: Long? = null
    private var openFailures = 0
    private var closed = false

    private val enabled: Boolean
        get() = !closed && frameWidthPx > 0 && frameHeightPx > 0 && openFailures < MaxOpenFailures

    /** The frame for [positionSeconds]'s own slot, or null if it is not decoded. */
    fun frameFor(positionSeconds: Double): ImageBitmap? = frames[slotOf(positionSeconds)]

    /**
     * Asks for the frame at [positionSeconds]. Returns immediately; the frame
     * appears through [frameFor] when it has been decoded, and may never
     * appear at all.
     */
    fun request(positionSeconds: Double) {
        if (!enabled) return
        val slot = slotOf(positionSeconds)
        // The caller feeds this every pointer move, and most moves stay inside
        // the slot they started in. Doing nothing for those is what keeps the
        // idle timer and the channel from churning through a drag.
        if (slot == lastRequestedSlot) return
        lastRequestedSlot = slot
        armIdleRelease()
        if (frames.containsKey(slot)) return
        startWorker()
        requests.trySend(slot)
    }

    /** Releases the reader and forgets the frames. Safe to call twice. */
    fun close() {
        if (closed) return
        closed = true
        worker?.cancel()
        worker = null
        idleRelease?.cancel()
        idleRelease = null
        releaseReader()
        frames.clear()
        cached.clear()
    }

    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            for (slot in requests) {
                if (!enabled) break
                if (frames.containsKey(slot)) continue
                val open = reader ?: openReader() ?: continue
                // A decode failure at one position says nothing about the next
                // one, so it is dropped rather than counted: only a source that
                // will not open at all is given up on.
                val frame = try {
                    open.frameAt(slot.toDouble(), frameWidthPx, frameHeightPx)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    null
                }
                if (frame != null) cache(slot, frame)
                armIdleRelease()
            }
        }
    }

    private suspend fun openReader(): VideoFrameReader? {
        val opened = try {
            source.open(url)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            null
        }
        if (opened == null) {
            openFailures += 1
            return null
        }
        // A close that landed while the open was in flight owns this reader's
        // fate: nothing else will release it once close() has already run.
        if (closed) {
            opened.close()
            return null
        }
        openFailures = 0
        reader = opened
        return opened
    }

    private fun cache(slot: Long, frame: ImageBitmap) {
        frames[slot] = frame
        cached.addLast(slot)
        while (cached.size > MaxCachedFrames) {
            frames.remove(cached.removeFirst())
        }
    }

    /**
     * Frames already decoded survive the release. They cost nothing to keep and
     * a viewer who comes back to the scrubber is usually coming back to the
     * same part of the timeline.
     */
    private fun armIdleRelease() {
        idleRelease?.cancel()
        if (closed) return
        idleRelease = scope.launch {
            delay(ReaderIdleMillis)
            releaseReader()
        }
    }

    private fun releaseReader() {
        reader?.close()
        reader = null
    }

    private fun slotOf(positionSeconds: Double): Long {
        val position = if (positionSeconds.isFinite()) positionSeconds.coerceAtLeast(0.0) else 0.0
        val step = FrameStepSeconds.toLong()
        return (position / FrameStepSeconds).toLong() * step
    }
}
