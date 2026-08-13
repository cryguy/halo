package moe.ditto.halo.screens.player

/**
 * What the player is playing, beyond the URL it plays.
 *
 * Everything here is resolved before playback is entered by the screen that
 * chose the source, from data it already had. The player never has to wait
 * on the network to say what it is showing. It arrives as one object rather than
 * a dozen parameters because the slices after this one hand the same identity to
 * watch-state reporting, the subtitle search and the next-episode lookup, and
 * those want the whole of it rather than a field each.
 *
 * The identity fields are the addon protocol's: [metaId] addresses the title and
 * [videoId] the video within it, which are the same string for a film and are
 * not for an episode.
 */
internal data class PlaybackContext(
    val url: String,
    /** `"movie"` or `"series"`, as the addon protocol spells it. */
    val type: String,
    val metaId: String,
    val videoId: String,
    val showTitle: String,
    /** Null for films: they are one video, and there is no episode to tag. */
    val episodeTag: String? = null,
    val episodeName: String? = null,
    /** The addon that offered this source, for asking the same one what follows. */
    val addonId: String,
    val bingeGroup: String? = null,
    /** Behaviour hints the addon attached to the source, all optional to it. */
    val filename: String? = null,
    val videoSize: Long? = null,
    val videoHash: String? = null,
    /** Raw source naming, for badge parsing only. Never displayed as given. */
    val streamName: String? = null,
    val streamTitle: String? = null,
) {
    /** True when this is an episode of something rather than a film. */
    val isEpisode: Boolean
        get() = episodeTag != null

    /**
     * One line naming what is playing, for the places that have room for only
     * one: the engine's own media title, and anything reporting on the session.
     */
    val displayTitle: String
        get() = if (episodeTag == null) showTitle else "$showTitle · $episodeTag"
}
