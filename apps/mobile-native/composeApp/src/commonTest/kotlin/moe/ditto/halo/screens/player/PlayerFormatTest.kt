package moe.ditto.halo.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Timecodes are read constantly and are the one place a player looks broken
 * immediately. The cases that matter are the boundaries: the hour mark, and the
 * moments before a duration is known, where a stream reports nothing useful.
 */
class PlayerFormatTest {

    @Test
    fun minutesAreUnpaddedUnderAnHour() {
        assertEquals("0:00", formatTimecode(0.0))
        assertEquals("0:07", formatTimecode(7.4))
        assertEquals("4:07", formatTimecode(247.0))
        assertEquals("59:59", formatTimecode(3_599.0))
    }

    @Test
    fun minutesArePaddedOnceThereAreHours() {
        assertEquals("1:00:00", formatTimecode(3_600.0))
        assertEquals("1:04:07", formatTimecode(3_847.0))
        assertEquals("12:00:00", formatTimecode(43_200.0))
    }

    @Test
    fun secondsTruncateRatherThanRound() {
        // Rounding would let a 3:59.7 position display as 4:00 while the
        // engine is still in the previous second.
        assertEquals("3:59", formatTimecode(239.9))
    }

    @Test
    fun unusableInputReadsAsZero() {
        assertEquals("0:00", formatTimecode(-12.0))
        assertEquals("0:00", formatTimecode(Double.NaN))
        assertEquals("0:00", formatTimecode(Double.POSITIVE_INFINITY))
    }

    @Test
    fun remainingCountsDownFromTheDuration() {
        assertEquals("40:52 left", formatRemaining(positionSeconds = 400.0, durationSeconds = 2_852.0))
        assertEquals("0:00 left", formatRemaining(positionSeconds = 2_852.0, durationSeconds = 2_852.0))
    }

    @Test
    fun remainingIsDroppedWhileTheDurationIsUnknown() {
        assertNull(formatRemaining(positionSeconds = 12.0, durationSeconds = null))
        assertNull(formatRemaining(positionSeconds = 12.0, durationSeconds = 0.0))
        assertNull(formatRemaining(positionSeconds = 12.0, durationSeconds = Double.NaN))
    }

    @Test
    fun progressIsAFractionAndNeverNaN() {
        assertEquals(0.5f, progressFraction(50.0, 100.0))
        assertEquals(0f, progressFraction(50.0, null))
        assertEquals(0f, progressFraction(50.0, 0.0))
        assertEquals(0f, progressFraction(Double.NaN, 100.0))
    }

    @Test
    fun delayCarriesItsSignAndUnit() {
        assertEquals("0 ms", formatDelay(0.0))
        assertEquals("+150 ms", formatDelay(0.15))
        assertEquals("−50 ms", formatDelay(-0.05))
        assertEquals("+5000 ms", formatDelay(5.0))
    }

    @Test
    fun delayUsesAMinusSignRatherThanAHyphen() {
        // The stepper draws it in a monospaced column beside a plus, where a
        // hyphen sits at the wrong height and the wrong width.
        assertEquals('−', formatDelay(-0.05).first())
    }

    @Test
    fun scaleReadsAsAWholePercentage() {
        assertEquals("100%", formatScalePercent(1.0))
        assertEquals("50%", formatScalePercent(0.5))
        assertEquals("200%", formatScalePercent(2.0))
        assertEquals("125%", formatScalePercent(1.25))
        assertEquals("100%", formatScalePercent(Double.NaN))
    }

    @Test
    fun wholeRatesDropTheirDecimal() {
        assertEquals("1×", formatRate(1.0))
        assertEquals("2×", formatRate(2.0))
        assertEquals("0.5×", formatRate(0.5))
        assertEquals("0.75×", formatRate(0.75))
        assertEquals("1.25×", formatRate(1.25))
        assertEquals("1.5×", formatRate(1.5))
    }

    @Test
    fun progressIsClampedWhenPositionOverrunsDuration() {
        // Live edges and rounding both produce a position past the reported
        // duration; a fraction over 1 would draw the played fill past the track.
        assertEquals(1f, progressFraction(120.0, 100.0))
    }
}
