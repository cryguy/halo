package moe.ditto.halo.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager

/**
 * Android's answers to [PlayerSystemPort], all of them attributes of the one
 * Activity the app runs in.
 *
 * The Activity is held directly rather than through the application context
 * because three of these are window attributes and the fourth is the window's
 * requested orientation; none of them exist without it. It is the same Activity
 * that owns the render surface, so its lifetime already covers the player's.
 */
internal class AndroidPlayerSystemPort(private val activity: Activity) : PlayerSystemPort {

    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * The window's own override if it has one, otherwise the device's current
     * setting, so a brightness drag starts from what is actually on screen
     * rather than from the middle of the range.
     */
    override fun screenBrightness(): Float? {
        val override = activity.window.attributes.screenBrightness
        if (override >= 0f) return override.coerceIn(0f, 1f)
        return systemBrightness()
    }

    override fun setScreenBrightness(value: Float) {
        applyWindowBrightness(value.coerceIn(MinimumVisibleBrightness, 1f))
    }

    override fun clearScreenBrightnessOverride() {
        applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    override fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override fun setVolume(value: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val step = (value.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
        // No flags: the system's own volume panel appearing over the video is
        // exactly what the in-player readout exists to replace.
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
    }

    override fun volumeSteps(): Int = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /**
     * Sensor landscape rather than a fixed one, so the device may still be
     * turned end for end; what is locked out is portrait.
     */
    override fun lockLandscape() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun restoreOrientation() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyWindowBrightness(value: Float) {
        val attributes = activity.window.attributes
        attributes.screenBrightness = value
        activity.window.attributes = attributes
    }

    /**
     * Readable without a permission, and reported on the same 0..255 scale the
     * settings slider uses. Absent on a device that has never stored one, which
     * is not worth a failure: the caller falls back to leaving brightness alone.
     */
    private fun systemBrightness(): Float? = try {
        val raw = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        (raw / MaxSystemBrightness).coerceIn(0f, 1f)
    } catch (_: Settings.SettingNotFoundException) {
        null
    }

    private companion object {
        const val MaxSystemBrightness = 255f

        /**
         * Zero is a black screen with no way back other than by feel, so the
         * lowest a drag can reach is dim rather than off.
         */
        const val MinimumVisibleBrightness = 0.01f
    }
}
