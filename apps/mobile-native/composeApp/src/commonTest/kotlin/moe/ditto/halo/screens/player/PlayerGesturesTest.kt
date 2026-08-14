package moe.ditto.halo.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The drag rules, which decide how much of a screen a viewer has to cross to
 * change something and how many calls that costs the system on the way.
 */
class PlayerGesturesTest {

    @Test
    fun sevenTenthsOfTheHeightCoversTheWholeRange() {
        // 1000px tall, so 700px of travel is the full sweep. Starting at the
        // bottom of the range, dragging up by all of it reaches the top.
        val adjustment = VerticalDragAdjustment(baseline = 0f, heightPx = 1_000f)

        assertEquals(1f, adjustment.advance(-700f))
    }

    @Test
    fun draggingUpIncreasesAndDownDecreases() {
        val up = VerticalDragAdjustment(baseline = 0.5f, heightPx = 1_000f)
        assertEquals(0.7f, up.advance(-140f))

        val down = VerticalDragAdjustment(baseline = 0.5f, heightPx = 1_000f)
        assertEquals(0.3f, down.advance(140f))
    }

    @Test
    fun onlyWholePercentsAreReported() {
        val adjustment = VerticalDragAdjustment(baseline = 0.5f, heightPx = 1_000f)

        // Seven pixels is one percent of the 700px sweep; anything less is a
        // frame of movement nobody could see and no call worth making.
        assertNull(adjustment.advance(-1f))
        assertNull(adjustment.advance(-3f))
        assertEquals(0.51f, adjustment.advance(-7f))
        assertNull(adjustment.advance(-8f))
        assertEquals(0.52f, adjustment.advance(-11f))
    }

    @Test
    fun theRangeIsClampedRatherThanOverrun() {
        val adjustment = VerticalDragAdjustment(baseline = 0.9f, heightPx = 1_000f)

        assertEquals(1f, adjustment.advance(-500f))
        // Still off the end: no further change, so no further calls.
        assertNull(adjustment.advance(-900f))
    }

    @Test
    fun aGestureThatNeverMovesChangesNothing() {
        val adjustment = VerticalDragAdjustment(baseline = 0.42f, heightPx = 1_000f)

        assertNull(adjustment.advance(0f))
    }

    @Test
    fun anUnmeasuredSurfaceIsDeclinedRatherThanDividedBy() {
        val adjustment = VerticalDragAdjustment(baseline = 0.5f, heightPx = 0f)

        assertNull(adjustment.advance(-100f))
    }

    @Test
    fun whichHalfTheGestureStartedOnDecidesWhatItChanges() {
        assertEquals(VerticalDragTarget.Brightness, verticalDragTarget(startX = 10f, widthPx = 1_000f))
        assertEquals(VerticalDragTarget.Brightness, verticalDragTarget(startX = 500f, widthPx = 1_000f))
        assertEquals(VerticalDragTarget.Volume, verticalDragTarget(startX = 501f, widthPx = 1_000f))
        // An unmeasured surface has no halves; brightness is the safer default,
        // since it changes nothing outside this window.
        assertEquals(VerticalDragTarget.Brightness, verticalDragTarget(startX = 10f, widthPx = 0f))
    }
}
