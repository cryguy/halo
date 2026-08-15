package moe.ditto.halo

import moe.ditto.halo.player.PlayerSystemPort

/**
 * Swift-owned access to UIKit and the system audio volume.
 *
 * This is intentionally separate from [HaloIosPlayerHost]. Screen presentation
 * and device volume are not libmpv capabilities, and keeping a second boundary
 * preserves the same split common code expresses with [PlayerSystemPort].
 */
interface HaloIosPlayerSystemHost {
    /** Negative means the platform could not read the current brightness. */
    fun screenBrightness(): Double
    fun setScreenBrightness(value: Double)
    fun clearScreenBrightnessOverride()

    fun volume(): Double
    fun setVolume(value: Double)
    fun volumeSteps(): Int

    fun lockLandscape()
    fun releaseLandscape()
    fun keepScreenOn()
    fun releaseScreenOn()
    fun hideSystemBars()
    fun releaseSystemBars()
}

internal class IosPlayerSystemPort(
    private val host: HaloIosPlayerSystemHost,
) : PlayerSystemPort {
    override fun screenBrightness(): Float? = host.screenBrightness()
        .takeIf { it.isFinite() && it >= 0.0 }
        ?.coerceIn(0.0, 1.0)
        ?.toFloat()

    override fun setScreenBrightness(value: Float) {
        host.setScreenBrightness(value.coerceIn(0f, 1f).toDouble())
    }

    override fun clearScreenBrightnessOverride() {
        host.clearScreenBrightnessOverride()
    }

    override fun volume(): Float = host.volume()
        .takeIf { it.isFinite() }
        ?.coerceIn(0.0, 1.0)
        ?.toFloat()
        ?: 0f

    override fun setVolume(value: Float) {
        host.setVolume(value.coerceIn(0f, 1f).toDouble())
    }

    override fun volumeSteps(): Int = host.volumeSteps().coerceAtLeast(0)

    override fun lockLandscape() = host.lockLandscape()
    override fun releaseLandscape() = host.releaseLandscape()
    override fun keepScreenOn() = host.keepScreenOn()
    override fun releaseScreenOn() = host.releaseScreenOn()
    override fun hideSystemBars() = host.hideSystemBars()
    override fun releaseSystemBars() = host.releaseSystemBars()
}
