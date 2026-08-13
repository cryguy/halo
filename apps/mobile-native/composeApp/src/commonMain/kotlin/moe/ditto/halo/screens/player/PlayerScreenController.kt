package moe.ditto.halo.screens.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which tab the right rail is showing, or null when it is closed. */
internal enum class RailTab {
    Audio,
    Subtitles,
    Speed,
}

/** How long chrome stays up with nothing touching it. */
private const val ChromeIdleMillis = 3_000L

/**
 * Everything the player screen knows that playback does not: whether the chrome
 * is up, which panel is open, and where a scrub is in progress.
 *
 * It is a plain class holding snapshot state rather than a pile of `remember`s
 * inside the composable, for two reasons. The auto-hide rule is a real state
 * machine (a timer that several unrelated interactions arm, cancel and suppress)
 * and it is the kind of thing that silently rots into "chrome hides while the
 * user is dragging the scrubber"; here it can be tested against virtual time
 * with no composition at all. And the rule is genuinely cross-cutting, so
 * spreading it across the widgets that trigger it would leave no single place
 * that states it.
 *
 * Playback-shaped state is not duplicated here. Position, duration, tracks and
 * status live in `PlayerState` and the screen reads them from there; the one
 * thing this holds about playback is whether it is paused, because the auto-hide
 * rule depends on it.
 *
 * [scope] must be a scope tied to the screen: the pending hide is cancelled with
 * it, so a screen that has gone away cannot hide chrome that no longer exists.
 */
internal class PlayerScreenController(private val scope: CoroutineScope) {

    var chromeVisible by mutableStateOf(true)
        private set

    var rail by mutableStateOf<RailTab?>(null)
        private set

    var episodeDrawerOpen by mutableStateOf(false)
        private set

    /** Non-null only while a scrub gesture is in flight, as a 0..1 fraction. */
    var scrubFraction by mutableStateOf<Float?>(null)
        private set

    /**
     * Rail choices the engine cannot hold yet.
     *
     * Playback rate, ASS override and audio delay are all real mpv properties
     * that `PlayerPort` does not expose, and the addon subtitle list is not
     * fetched at all. Keeping them here means the rail is complete and reviewable
     * now, and each one moves to `PlayerState` when its engine call lands. They
     * are deliberately grouped and named so it is obvious what is not yet real.
     */
    var playbackRate by mutableStateOf(1.0)
        private set

    var subtitleTrackStyling by mutableStateOf(true)
        private set

    var audioDelaySeconds by mutableStateOf(0.0)
        private set

    var selectedAddonSubtitleId by mutableStateOf<String?>(null)
        private set

    private var paused = false
    private var hideJob: Job? = null

    /**
     * Chrome hides itself only when there is nothing to look at and nothing in
     * progress. Pausing is a deliberate stop, so the controls stay up; an open
     * rail or drawer is being read; and a scrub in flight is being aimed.
     */
    private val canAutoHide: Boolean
        get() = chromeVisible &&
            !paused &&
            scrubFraction == null &&
            rail == null &&
            !episodeDrawerOpen

    /**
     * Restarts the idle timer. Safe to call after any interaction: it cancels
     * whatever was pending, and starts nothing when hiding is suppressed, so
     * callers never have to know the rule.
     */
    fun armAutoHide() {
        hideJob?.cancel()
        hideJob = null
        if (!canAutoHide) return
        hideJob = scope.launch {
            delay(ChromeIdleMillis)
            chromeVisible = false
        }
    }

    /** Tapping the video. */
    fun toggleChrome() {
        chromeVisible = !chromeVisible
        armAutoHide()
    }

    fun showChrome() {
        chromeVisible = true
        armAutoHide()
    }

    /**
     * Playback's own pause state, pushed in by the screen. Re-arms rather than
     * assuming a direction: resuming starts the timer that pausing suppressed.
     */
    fun onPausedChanged(value: Boolean) {
        if (paused == value) return
        paused = value
        armAutoHide()
    }

    /**
     * Any transport action: the seek buttons, or play/pause. The chrome comes
     * back up if it was down, because these can be reached by gesture too.
     */
    fun onTransportUsed() {
        showChrome()
    }

    /** The rail and the drawer are alternatives; opening one closes the other. */
    fun openRail(tab: RailTab) {
        rail = tab
        episodeDrawerOpen = false
        showChrome()
    }

    fun closeRail() {
        rail = null
        armAutoHide()
    }

    fun toggleEpisodeDrawer() {
        episodeDrawerOpen = !episodeDrawerOpen
        rail = null
        showChrome()
    }

    fun closeEpisodeDrawer() {
        episodeDrawerOpen = false
        armAutoHide()
    }

    /**
     * A scrub gesture. [beginScrub] and [updateScrub] only move the preview;
     * nothing is sent to the engine until [endScrub] returns the position its
     * caller should seek to, so a drag across the track is one seek rather than
     * one per frame.
     */
    fun beginScrub(fraction: Float) {
        scrubFraction = fraction.coerceIn(0f, 1f)
        showChrome()
    }

    fun updateScrub(fraction: Float) {
        if (scrubFraction == null) return
        scrubFraction = fraction.coerceIn(0f, 1f)
    }

    /** Returns the committed fraction, or null if no scrub was in flight. */
    fun endScrub(): Float? {
        val committed = scrubFraction ?: return null
        scrubFraction = null
        armAutoHide()
        return committed
    }

    fun cancelScrub() {
        scrubFraction = null
        armAutoHide()
    }

    // --- Rail choices ----------------------------------------------------

    fun selectPlaybackRate(rate: Double) {
        if (!rate.isFinite() || rate <= 0.0) return
        playbackRate = rate
    }

    fun setTrackStyling(enabled: Boolean) {
        subtitleTrackStyling = enabled
    }

    /** Clamped the same way the subtitle delay is, and for the same reason. */
    fun setAudioDelay(seconds: Double) {
        if (!seconds.isFinite()) return
        audioDelaySeconds = seconds.coerceIn(-MaxDelaySeconds, MaxDelaySeconds)
    }

    /**
     * Choosing an addon subtitle clears the in-file selection in the UI, since
     * only one subtitle can be showing. The engine side of that is a real
     * [moe.ditto.halo.player.PlayerPort.addSubtitle] call, which arrives with
     * the fetch.
     */
    fun selectAddonSubtitle(id: String?) {
        selectedAddonSubtitleId = id
    }
}

/**
 * Delay limits, shared by the subtitle and audio steppers. Five seconds either
 * way covers every real desync; beyond that the track is the wrong one.
 */
internal const val MaxDelaySeconds = 5.0
internal const val DelayStepSeconds = 0.05
