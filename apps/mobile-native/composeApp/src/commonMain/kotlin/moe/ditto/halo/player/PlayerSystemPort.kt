package moe.ditto.halo.player

/**
 * What the player needs from the device rather than from the media engine.
 *
 * Deliberately not part of [PlayerPort]. None of this is playback: the screen's
 * brightness, the system's media volume, which way the window is allowed to
 * rotate and whether the display may sleep are all properties of the device
 * that happen to matter while watching something. Putting them behind the media
 * boundary would mean every engine implementation had to answer questions about
 * window attributes.
 *
 * Volume drives the *system* media stream rather than the engine's own volume,
 * which is what every mobile player does: the hardware keys and this gesture
 * then move the same number, and a viewer who turns the sound down here does
 * not find it turned up again in the next app.
 *
 * Every method is main-thread and cheap; these are window and audio-manager
 * attributes, not I/O.
 */
interface PlayerSystemPort {
    /**
     * Screen brightness as 0..1, or null when the platform cannot say. Null is
     * a real answer: on a window that has never overridden it, there is no
     * window brightness to read, only the system's, which may be unreadable.
     */
    fun screenBrightness(): Float?

    /** Overrides brightness for this window only; the rest of the device is untouched. */
    fun setScreenBrightness(value: Float)

    /** Hands brightness back to the system, for when the player goes away. */
    fun clearScreenBrightnessOverride()

    /** System media volume as 0..1. */
    fun volume(): Float

    fun setVolume(value: Float)

    /**
     * How many steps the system's own volume has. A gesture finer than one step
     * cannot be honoured, so the caller rounds to this rather than pretending
     * to a precision the device does not have.
     */
    fun volumeSteps(): Int

    /** Landscape for the duration of playback, restored on the way out. */
    fun lockLandscape()

    fun restoreOrientation()

    fun setKeepScreenOn(enabled: Boolean)
}

/**
 * For platforms with no implementation yet, and for tests. Reporting null
 * brightness and zero volume is what makes the gesture layer decline to act
 * rather than act on invented numbers.
 */
object NoPlayerSystemPort : PlayerSystemPort {
    override fun screenBrightness(): Float? = null
    override fun setScreenBrightness(value: Float) = Unit
    override fun clearScreenBrightnessOverride() = Unit
    override fun volume(): Float = 0f
    override fun setVolume(value: Float) = Unit
    override fun volumeSteps(): Int = 0
    override fun lockLandscape() = Unit
    override fun restoreOrientation() = Unit
    override fun setKeepScreenOn(enabled: Boolean) = Unit
}
