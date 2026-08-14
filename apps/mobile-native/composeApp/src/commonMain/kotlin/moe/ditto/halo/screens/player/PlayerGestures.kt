package moe.ditto.halo.screens.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
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
    onFillScreenChange: (Boolean) -> Unit,
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
    .pointerInput(Unit) {
        // Hand-rolled rather than `detectTransformGestures`, which never says
        // where one gesture ends: the commit has to be per pinch, or the first
        // one of a session would be the only one that could ever decide.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val pinch = PinchCommit()
            var event: PointerEvent
            do {
                event = awaitPointerEvent()
                if (event.changes.count { it.pressed } < 2) continue
                pinch.advance(event.calculateZoom())?.let(onFillScreenChange)
                if (pinch.hasCommitted) {
                    // Consumed so the release of a committed pinch is not also
                    // read as a tap that hides the chrome.
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * How far a pinch has to travel before it means anything. Below this it is a
 * two-finger tap or a hand settling on the screen, and switching the picture's
 * shape on either would be an accident.
 */
private const val PinchThreshold = 0.12f

/**
 * Reads a pinch as a single decision rather than a continuous zoom.
 *
 * The picture has two shapes and nothing in between, so what matters is which
 * direction the pinch went and whether it went far enough. Committing once per
 * gesture is what stops a slow pinch from flipping the shape repeatedly as the
 * fingers wander past the threshold.
 */
internal class PinchCommit {
    private var zoom = 1f
    private var committed = false

    /** Returns true to fill the screen, false to fit inside it, null for neither yet. */
    fun advance(gestureZoom: Float): Boolean? {
        if (committed || !gestureZoom.isFinite() || gestureZoom <= 0f) return null
        zoom *= gestureZoom
        if (zoom >= 1f + PinchThreshold) {
            committed = true
            return true
        }
        if (zoom <= 1f - PinchThreshold) {
            committed = true
            return false
        }
        return null
    }

    /** True once this gesture has decided, so its release is not also a tap. */
    val hasCommitted: Boolean get() = committed

}

/** The readout that belongs to each drag target. */
internal fun VerticalDragTarget.hudKind(): GestureHudKind = when (this) {
    VerticalDragTarget.Brightness -> GestureHudKind.Brightness
    VerticalDragTarget.Volume -> GestureHudKind.Volume
}
