package moe.ditto.halo.screens.player

import kotlin.math.floor

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
 * Playback progress as a 0..1 fraction, or 0 while the duration is unknown.
 * Callers draw a track from this, so it must never be NaN.
 */
internal fun progressFraction(positionSeconds: Double, durationSeconds: Double?): Float {
    val duration = durationSeconds ?: return 0f
    if (!duration.isFinite() || duration <= 0.0) return 0f
    if (!positionSeconds.isFinite()) return 0f
    return (positionSeconds / duration).coerceIn(0.0, 1.0).toFloat()
}
