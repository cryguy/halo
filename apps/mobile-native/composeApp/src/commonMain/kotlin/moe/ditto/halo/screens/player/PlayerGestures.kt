package moe.ditto.halo.screens.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/**
 * How much of the screen's height a drag must cross to sweep the whole range.
 *
 * Less than the full height on purpose: a gesture that needs the entire screen
 * cannot reach either end without starting at the opposite one, and the top and
 * bottom of a player are where the chrome is.
 */
private const val DragTravelFraction = 0.7f

/** Which half of the screen a vertical drag started on decides what it changes. */
internal enum class VerticalDragTarget {
    Brightness,
    Volume,
}

/**
 * Turns a vertical drag into values, one per whole percent it crosses.
 *
 * Quantising is not cosmetic. Brightness is a window attribute and volume is a
 * system call, and a drag reports movement per frame; sending every frame's
 * fractional change would make hundreds of calls to cross the range and would
 * jitter the readout with numbers no one can see the difference between.
 *
 * The baseline is captured when the gesture starts rather than read per frame,
 * so the value tracks the finger instead of chasing whatever the last call set.
 */
internal class VerticalDragAdjustment(
    private val baseline: Float,
    heightPx: Float,
) {
    private val travelPx = heightPx * DragTravelFraction
    private var lastPercent = percentOf(baseline)

    /**
     * [totalDragPx] is the distance from where the gesture began, positive
     * downwards. Returns the new value only when it has moved by a whole
     * percent, and null otherwise so the caller makes no call at all.
     */
    fun advance(totalDragPx: Float): Float? {
        if (travelPx <= 0f || !totalDragPx.isFinite()) return null
        // Up increases, which is the direction every device's own brightness
        // and volume sliders move.
        val value = (baseline - totalDragPx / travelPx).coerceIn(0f, 1f)
        val percent = percentOf(value)
        if (percent == lastPercent) return null
        lastPercent = percent
        return percent / 100f
    }

    private fun percentOf(value: Float): Int = (value.coerceIn(0f, 1f) * 100f).roundToInt()
}

/** Left half is brightness, right half is volume, as every mobile player does. */
internal fun verticalDragTarget(startX: Float, widthPx: Float): VerticalDragTarget =
    if (widthPx > 0f && startX > widthPx / 2f) VerticalDragTarget.Volume else VerticalDragTarget.Brightness

/**
 * The video's own gestures.
 *
 * Taps and drags are separate detectors on the same surface rather than one
 * hand-rolled loop: each is a well-defined interaction with its own slop and
 * timing rules, and combining them by hand is how a drag starts eating taps.
 *
 * A single tap is delayed by the platform's double-tap window, which is
 * unavoidable while both gestures exist on the same surface: nothing can know a
 * tap was single until the window has passed without a second one.
 */
internal fun Modifier.playerGestures(
    onTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onDragStart: (VerticalDragTarget, Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { offset ->
                if (offset.x > size.width / 2f) onDoubleTapRight() else onDoubleTapLeft()
            },
        )
    }
    .pointerInput(Unit) {
        var totalDrag = 0f
        detectVerticalDragGestures(
            onDragStart = { offset: Offset ->
                totalDrag = 0f
                onDragStart(verticalDragTarget(offset.x, size.width.toFloat()), size.height.toFloat())
            },
            onVerticalDrag = { _, delta ->
                totalDrag += delta
                onDrag(totalDrag)
            },
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
        )
    }

/** The readout that belongs to each drag target. */
internal fun VerticalDragTarget.hudKind(): GestureHudKind = when (this) {
    VerticalDragTarget.Brightness -> GestureHudKind.Brightness
    VerticalDragTarget.Volume -> GestureHudKind.Volume
}
