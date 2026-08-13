package moe.ditto.halo.screens.player

import moe.ditto.halo.player.PlaybackStatus
import moe.ditto.halo.player.PlayerState
import moe.ditto.halo.player.PlayerTrack
import moe.ditto.halo.player.PlayerTracks

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

    /**
     * Subtitles offered by addons, which the API can supply but nothing fetches
     * yet. The detail line carries the addon's own id for the file, which is
     * what distinguishes two same-language results from each other.
     */
    val AddonSubtitles = listOf(
        FixtureAddonSubtitle("a-os-en", "OpenSubtitles", "English · os-en-6821194", "SRT", onDisk = true),
        FixtureAddonSubtitle("a-os-en2", "OpenSubtitles", "English · os-en-6821507", "SRT", onDisk = false),
        FixtureAddonSubtitle("a-ktx", "Kitsunekko", "Japanese · fansub, styled", "ASS", onDisk = false),
    )

    const val SeasonTitle = "Season 2"

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

    /** What the up-next card offers. */
    val NextEpisode = Episodes[4]

    /**
     * Sample line for the subtitle appearance preview. Long enough to wrap at
     * the largest scale, which is when wrapping is the thing being judged.
     */
    const val CaptionSample = "They kept the antenna pointed at nothing for eleven years."

    /**
     * A playback state for the scene harness, which has no engine behind it.
     * Roughly forty-three per cent through a forty-seven minute episode, which
     * puts the transport somewhere it has to lay out both timecodes and a
     * partly filled bar.
     */
    val State = PlayerState(
        status = PlaybackStatus.Playing,
        positionSeconds = 1_230.0,
        durationSeconds = 2_852.0,
        tracks = PlayerTracks(
            audio = listOf(
                PlayerTrack(id = "1", label = "English", language = "eng", codec = "eac3", channels = 6, sampleRateHz = 48000),
                PlayerTrack(id = "2", label = "Japanese", language = "jpn", codec = "aac", channels = 2, sampleRateHz = 48000),
                PlayerTrack(id = "3", label = "Commentary", language = "eng", codec = "aac", channels = 2, sampleRateHz = 48000),
            ),
            subtitles = listOf(
                PlayerTrack(id = "4", label = "English, Signs & Songs", language = "eng", codec = "ass"),
                PlayerTrack(id = "5", label = "English (SDH)", language = "eng", codec = "subrip"),
                PlayerTrack(id = "6", label = "Japanese", language = "jpn", codec = "hdmv_pgs_subtitle"),
            ),
            selectedAudioId = "1",
            selectedSubtitleId = "4",
        ),
    )
}

internal data class FixtureEpisode(
    val tag: String,
    val name: String,
    val progress: Float,
    val downloaded: Boolean,
)

internal data class FixtureAddonSubtitle(
    val id: String,
    val addonName: String,
    val detail: String,
    val format: String,
    val onDisk: Boolean,
)
