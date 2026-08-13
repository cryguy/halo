package moe.ditto.halo.screens.player

/**
 * Stand-in content for the parts of the player that have no real source yet.
 *
 * These exist so the screen can be built and reviewed in full before the data
 * behind it is wired: the show and episode names arrive with the playback
 * context, the stream badges are parsed from the chosen source, and the episode
 * list comes from the title's meta. Each one is replaced in its own slice, and
 * this file should be empty by the end of it.
 *
 * The values match the approved prototype so the built screen can be compared
 * against it side by side.
 */
internal object PlayerFixtures {
    const val ShowTitle = "Pale Blue Dot"
    const val EpisodeName = "Carrier Lost · Season 2"

    /**
     * Resolution and codec only. The source's provider is deliberately absent:
     * a debrid account name on screen is an account identifier, and it is not
     * information the viewer needs while watching.
     */
    val StreamBadges = listOf("1080p", "HEVC 10-bit")

    /**
     * How far ahead of the playhead the transport's buffered fill is drawn.
     * The real figure is mpv's demuxer cache, which is not observed yet; until
     * it is, the bar shows a plausible lead rather than a permanently empty
     * buffer, which would read as a stalled stream.
     */
    const val BufferedLeadFraction = 0.09f

    val Episodes = listOf(
        FixtureEpisode(tag = "S02E01", name = "Ground Truth", progress = 1f, downloaded = true),
        FixtureEpisode(tag = "S02E02", name = "Ninety Seconds", progress = 1f, downloaded = true),
        FixtureEpisode(tag = "S02E03", name = "Carrier Lost", progress = 1f, downloaded = true),
        FixtureEpisode(tag = "S02E04", name = "Pale Blue Dot", progress = 0.42f, downloaded = false),
        FixtureEpisode(tag = "S02E05", name = "The Long Signal", progress = 0f, downloaded = false),
        FixtureEpisode(tag = "S02E06", name = "Dust Season", progress = 0f, downloaded = false),
    )

    /** The episode the fixture session is playing. */
    val CurrentEpisode = Episodes[3]
}

internal data class FixtureEpisode(
    val tag: String,
    val name: String,
    val progress: Float,
    val downloaded: Boolean,
)
