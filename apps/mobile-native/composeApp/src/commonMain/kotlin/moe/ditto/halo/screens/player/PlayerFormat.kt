package moe.ditto.halo.screens.player

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Timecodes for the transport row and the scrub preview.
 *
 * Minutes are padded only past an hour (`4:07`, then `1:04:07`), which is what
 * every player does and what keeps the common case narrow. Anything not finite
 * or not yet known reads as zero rather than as an error: a duration arrives a
 * moment after a load, and a dash or a blank there flickers on every start.
 */
internal fun formatTimecode(totalSeconds: Double): String {
    if (!totalSeconds.isFinite() || totalSeconds <= 0.0) return "0:00"

    val total = floor(totalSeconds).toLong()
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600

    val paddedSeconds = seconds.toString().padStart(2, '0')
    if (hours <= 0) return "$minutes:$paddedSeconds"
    return "$hours:${minutes.toString().padStart(2, '0')}:$paddedSeconds"
}

/**
 * The "how much is left" readout, or null while the duration is unknown, in
 * which case the label is dropped rather than shown counting down from zero.
 */
internal fun formatRemaining(positionSeconds: Double, durationSeconds: Double?): String? {
    val duration = durationSeconds ?: return null
    if (!duration.isFinite() || duration <= 0.0) return null
    return "${formatTimecode(duration - positionSeconds)} left"
}

/**
 * A track delay, in milliseconds with an explicit sign.
 *
 * The sign is what the control is for: the reader needs to know which way the
 * track has been pushed, not just by how much. Zero carries no sign, and the
 * minus is a real minus sign rather than a hyphen so it lines up with the plus
 * in a monospaced column.
 */
internal fun formatDelay(seconds: Double): String {
    if (!seconds.isFinite()) return "0 ms"
    val millis = (seconds * 1_000.0).roundToInt()
    if (millis == 0) return "0 ms"
    val sign = if (millis > 0) "+" else "−"
    return "$sign${abs(millis)} ms"
}

/** A subtitle scale as the percentage the slider is labelled in. */
internal fun formatScalePercent(scale: Double): String {
    if (!scale.isFinite() || scale <= 0.0) return "100%"
    return "${(scale * 100.0).roundToInt()}%"
}

/**
 * Playback rate, written the way the design labels it: whole rates lose their
 * decimal so the common case reads `1×` rather than `1.0×`.
 */
internal fun formatRate(rate: Double): String {
    if (!rate.isFinite() || rate <= 0.0) return "1×"
    val rounded = (rate * 100.0).roundToInt()
    val text = when {
        rounded % 100 == 0 -> (rounded / 100).toString()
        rounded % 10 == 0 -> "${rounded / 100}.${(rounded % 100) / 10}"
        else -> "${rounded / 100}.${(rounded % 100).toString().padStart(2, '0')}"
    }
    return "$text×"
}

/**
 * Playback progress as a 0..1 fraction, or 0 while the duration is unknown.
 * Callers draw a track from this, so it must never be NaN.
 */
internal fun progressFraction(positionSeconds: Double, durationSeconds: Double?): Float {
    val duration = durationSeconds ?: return 0f
    if (!duration.isFinite() || duration <= 0.0) return 0f
    if (!positionSeconds.isFinite()) return 0f
    return (positionSeconds / duration).coerceIn(0.0, 1.0).toFloat()
}
