package moe.ditto.halo.player

import android.content.Context
import android.util.Log
import android.view.Surface
import dev.jdtech.mpv.MPVLib

/**
 * Owned thin adapter over libmpv's Android JNI ([MPVLib], mpv-android lineage) —
 * the direct parallel to iOS's `MPVCore.swift`. The prebuilt AAR is only the
 * binary provider; MPVLib types never leave this file, so the boundary the
 * common shell sees is the platform-neutral player contract, and the binary
 * provider stays replaceable (production ships an owned reproducible build).
 *
 * mpv's own thread delivers property/events through [MPVLib.EventObserver]; this
 * class translates them to a small owned [Listener] using seconds and the
 * neutral [PlayerTracks] schema. It never surfaces an mpv event id, format int,
 * or property name upward.
 */
internal class MpvCore private constructor(
    private val mpv: MPVLib,
    val id: String,
) {
    interface Listener {
        fun onReady(durationSeconds: Double?)
        fun onPosition(positionSeconds: Double)
        fun onPauseChanged(paused: Boolean)
        fun onTracks(tracks: PlayerTracks)
        /** Null once the cache stops stalling playback. */
        fun onBuffering(buffering: PlayerBuffering?)
        fun onBufferedPosition(positionSeconds: Double)
        fun onEnded()
        fun onError(message: String)
    }

    @Volatile private var listener: Listener? = null
    @Volatile private var destroyed = false
    // mpv fires time-pos many times per second; forward only whole-second
    // changes. The shell displays seconds, and flooding recompositions would
    // otherwise starve Compose's idle sync (and waste work).
    @Volatile private var lastPositionSecond = Long.MIN_VALUE
    // Same reasoning for the cache's reach: the transport bar draws it as a
    // fraction of a whole timeline, so sub-second updates buy nothing visible.
    @Volatile private var lastBufferedSecond = Long.MIN_VALUE
    // Whether the cache is currently stalling playback, and how full mpv
    // considers it. Held because the two arrive as separate observations and
    // the overlay needs both at once.
    @Volatile private var pausedForCache = false
    @Volatile private var cacheFillPercent: Int? = null

    private val eventObserver = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) { /* NODE/none formats: ignored */ }

        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "track-list/count" -> emitTracks()
                "cache-buffering-state" -> {
                    cacheFillPercent = value.toInt()
                    if (pausedForCache) emitBuffering()
                }
            }
        }

        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> {
                    val second = value.toLong()
                    if (second != lastPositionSecond) {
                        lastPositionSecond = second
                        listener?.onPosition(value)
                    }
                }
                "demuxer-cache-time" -> {
                    val second = value.toLong()
                    if (second != lastBufferedSecond) {
                        lastBufferedSecond = second
                        listener?.onBufferedPosition(value)
                    }
                }
            }
        }

        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> listener?.onPauseChanged(value)
                "eof-reached" -> if (value) listener?.onEnded()
                "paused-for-cache" -> {
                    pausedForCache = value
                    emitBuffering()
                }
            }
        }

        override fun eventProperty(property: String, value: String) { /* unused */ }

        override fun event(eventId: Int) {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> onFileLoaded()
                MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> listener?.onEnded()
            }
        }
    }

    // MPVLib.LogObserver is a plain Kotlin interface (not `fun interface`), so no
    // SAM lambda — an explicit object is required.
    private val logObserver = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            // Primary diagnostic channel: mpv's own log lines land in logcat under
            // a greppable tag (Configuration/vo/hwdec/subtitle-track selection).
            Log.i(LOG_TAG, "[$id][$prefix] ${text.trimEnd()}")
        }
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /** Attach the render surface and turn the GPU video output on (mpv-android order). */
    fun attachSurface(surface: Surface, width: Int, height: Int) {
        if (destroyed) return
        mpv.attachSurface(surface)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("android-surface-size", "${width}x$height")
        mpv.setOptionString("vo", VO)
    }

    fun setSurfaceSize(width: Int, height: Int) {
        if (destroyed || width <= 0 || height <= 0) return
        mpv.setPropertyString("android-surface-size", "${width}x$height")
    }

    /**
     * Selects or drops the video track.
     *
     * Dropping it is how the decoder is destroyed on demand: mpv reinitialises
     * the video chain synchronously here, so when this returns no MediaCodec
     * instance is left holding buffers. Audio is untouched.
     */
    fun setVideoEnabled(enabled: Boolean) {
        if (destroyed) return
        mpv.setPropertyString("vid", if (enabled) "auto" else "no")
    }

    /** Turn video output off before releasing the surface (mpv-android order). */
    fun detachSurface() {
        if (destroyed) return
        mpv.setPropertyString("vo", "null")
        mpv.setOptionString("force-window", "no")
        mpv.detachSurface()
    }

    fun load(url: String) {
        if (destroyed) return
        mpv.command(arrayOf("loadfile", url))
    }

    fun stop() {
        if (destroyed) return
        mpv.command(arrayOf("stop"))
    }

    fun setPaused(paused: Boolean) {
        if (destroyed) return
        mpv.setPropertyBoolean("pause", paused)
    }

    fun seekTo(positionSeconds: Double) {
        if (destroyed) return
        mpv.command(arrayOf("seek", positionSeconds.toString(), "absolute"))
    }

    fun selectAudioTrack(id: String?) {
        if (destroyed) return
        mpv.setPropertyString("aid", id ?: "no")
    }

    fun selectSubtitleTrack(id: String?) {
        if (destroyed) return
        mpv.setPropertyString("sid", id ?: "no")
    }

    fun setPlaybackRate(rate: Double) {
        if (destroyed) return
        mpv.setPropertyDouble("speed", rate)
    }

    /**
     * `panscan` is a 0..1 blend between fitting the picture and cropping it to
     * the screen, and the two ends are the only settings the design offers.
     */
    fun setVideoFillsScreen(fills: Boolean) {
        if (destroyed) return
        mpv.setPropertyDouble("panscan", if (fills) 1.0 else 0.0)
    }

    fun setAudioDelay(seconds: Double) {
        if (destroyed) return
        mpv.setPropertyDouble("audio-delay", seconds)
    }

    // Live subtitle controls — the exact capability libVLC lacked on mobile:
    // these apply to the running core with no recreation.
    fun setSubtitleDelay(seconds: Double) {
        if (destroyed) return
        mpv.setPropertyDouble("sub-delay", seconds)
    }

    fun setSubtitleScale(scale: Double) {
        if (destroyed) return
        mpv.setPropertyDouble("sub-scale", scale)
    }

    fun setSubtitleFont(font: String?) {
        if (destroyed) return
        mpv.setPropertyString("sub-font", font ?: DefaultSubtitleFont)
    }

    /**
     * `no` leaves a script's own styling alone; `force` replaces its fonts,
     * sizes and positions with the player's. mpv has no third state, and the
     * two names read backwards from the switch, which is why the mapping is
     * here rather than at the call site.
     */
    fun setSubtitleTrackStyling(keepScript: Boolean) {
        if (destroyed) return
        mpv.setPropertyString("sub-ass-override", if (keepScript) "no" else "force")
    }

    fun setSubtitleOutline(widthPixels: Double) {
        if (destroyed) return
        mpv.setPropertyDouble("sub-border-size", widthPixels)
    }

    fun setSubtitleShadow(offsetPixels: Double) {
        if (destroyed) return
        mpv.setPropertyDouble("sub-shadow-offset", offsetPixels)
    }

    /**
     * mpv counts `sub-pos` downwards from the top, so a lift is a subtraction.
     * 100 is the resting place at the bottom edge.
     */
    fun setSubtitleLift(percent: Int) {
        if (destroyed) return
        mpv.setPropertyInt("sub-pos", 100 - percent)
    }

    /**
     * `cached` rather than `select`: it re-selects a URL already added instead
     * of adding it a second time. Restoring a remembered subtitle and then
     * tapping the same row otherwise leaves two identical tracks in the list,
     * which is visible on device and cannot be undone from the UI.
     */
    fun addSubtitle(url: String) {
        if (destroyed) return
        mpv.command(arrayOf("sub-add", url, "cached"))
    }

    /**
     * Deterministic, idempotent teardown (mirrors the iOS core's contract):
     * detach observers first so no event races the native destroy, then
     * terminate. Safe to call twice.
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        listener = null
        runCatching { mpv.removeObserver(eventObserver) }
        runCatching { mpv.removeLogObserver(logObserver) }
        runCatching { mpv.destroy() }
    }

    private fun onFileLoaded() {
        val duration = mpv.getPropertyDouble("duration")?.takeIf { it.isFinite() && it >= 0.0 }
        listener?.onReady(duration)
        emitTracks()
    }

    /**
     * Reports the stall, reading the rate and depth only while one is happening.
     *
     * `paused-for-cache` is the gate rather than `cache-buffering-state`,
     * because the latter sits below 100 through perfectly healthy streaming and
     * would flash the overlay over a picture that never stopped moving. The
     * two figures are read here instead of observed: they only matter while the
     * overlay is up, and mpv revises the fill percentage as it refills, so this
     * runs again for each revision.
     */
    private fun emitBuffering() {
        if (destroyed) return
        if (!pausedForCache) {
            listener?.onBuffering(null)
            return
        }
        listener?.onBuffering(
            PlayerBuffering(
                percent = cacheFillPercent,
                // mpv types cache-speed as int64; the JNI hands back an Int,
                // which cannot hold a rate a phone will ever see anyway.
                bytesPerSecond = mpv.getPropertyInt("cache-speed")?.toLong(),
                cachedSeconds = mpv.getPropertyDouble("demuxer-cache-duration"),
            ),
        )
    }

    private fun emitTracks() {
        if (destroyed) return
        val count = mpv.getPropertyInt("track-list/count") ?: return
        val audio = mutableListOf<PlayerTrack>()
        val subs = mutableListOf<PlayerTrack>()
        var selectedAudio: String? = null
        var selectedSub: String? = null
        for (i in 0 until count) {
            val type = mpv.getPropertyString("track-list/$i/type") ?: continue
            val trackId = mpv.getPropertyInt("track-list/$i/id")?.toString() ?: continue
            val lang = mpv.getPropertyString("track-list/$i/lang")
            val title = mpv.getPropertyString("track-list/$i/title")
            val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false
            val codec = mpv.getPropertyString("track-list/$i/codec")
            val channels = mpv.getPropertyInt("track-list/$i/demux-channel-count")
            val sampleRateHz = mpv.getPropertyInt("track-list/$i/demux-samplerate")
            val label = title ?: lang ?: "$type $trackId"
            when (type) {
                "audio" -> {
                    audio += PlayerTrack(trackId, label, lang, codec, channels, sampleRateHz)
                    if (selected) selectedAudio = trackId
                }
                "sub" -> {
                    subs += PlayerTrack(trackId, label, lang, codec = codec)
                    if (selected) selectedSub = trackId
                }
            }
        }
        listener?.onTracks(
            PlayerTracks(
                audio = audio,
                subtitles = subs,
                selectedAudioId = selectedAudio,
                selectedSubtitleId = selectedSub,
            ),
        )
    }

    companion object {
        const val LOG_TAG = "HALO_MPV"
        private const val VO = "gpu"

        /** What "no particular font" means to the renderer. */
        private const val DefaultSubtitleFont = "sans-serif"

        /** Create + configure + initialize a fresh core with a stable [id]. */
        fun create(context: Context, id: String): MpvCore {
            val mpv = MPVLib.create(context) ?: error("MPVLib.create returned null")
            // Pre-init options (mpv-android's proven Android render/decode stack).
            mpv.setOptionString("config", "no")
            mpv.setOptionString("vo", "null") // no output until a surface attaches
            mpv.setOptionString("gpu-context", "android")
            mpv.setOptionString("opengl-es", "yes")
            mpv.setOptionString("hwdec", "mediacodec-copy") // auto-falls back to sw
            mpv.setOptionString("ao", "audiotrack")
            mpv.setOptionString("mute", "yes") // test playback is always silent
            mpv.setOptionString("keep-open", "yes") // so eof-reached fires
            // Subtitle auto-select + rendering; embedded fonts make ASS render
            // without depending on Android system fonts.
            mpv.setOptionString("slang", "eng,en")
            mpv.setOptionString("subs-fallback", "yes")
            mpv.setOptionString("embeddedfonts", "yes")
            // Fonts the app ships, so a chosen family resolves to that family
            // rather than to whichever system face fontconfig settles on.
            // Pre-init: libass builds its font provider during initialisation.
            SubtitleFontLibrary.prepare(context)?.let { mpv.setOptionString("sub-fonts-dir", it) }

            val core = MpvCore(mpv, id)
            mpv.addLogObserver(core.logObserver)
            mpv.addObserver(core.eventObserver)
            mpv.init()

            // Observe the neutral property set the boundary needs.
            mpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("track-list/count", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("cache-buffering-state", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            return core
        }
    }
}
