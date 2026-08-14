package moe.ditto.halo.screens.player

import moe.ditto.halo.api.AddonSource
import moe.ditto.halo.api.AddonStreams
import moe.ditto.halo.api.MetaDetail
import moe.ditto.halo.api.MetaVideo
import moe.ditto.halo.api.Stream
import moe.ditto.halo.api.StreamBehaviorHints
import moe.ditto.halo.api.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerEpisodesTest {

    private val meta = MetaDetail(
        id = "tt0903747",
        type = "series",
        name = "Breaking Bad",
        videos = listOf(
            video("tt0903747:1:1", season = 1, episode = 1, title = "Pilot"),
            video("tt0903747:2:1", season = 2, episode = 1, title = "Seven Thirty-Seven"),
            video("tt0903747:2:2", season = 2, episode = 2, title = "Grilled"),
            video("tt0903747:2:3", season = 2, episode = 3, title = "Bit by a Dead Bee"),
        ),
    )

    @Test
    fun theDrawerShowsTheSeasonBeingWatchedAndNothingElse() {
        val episodes = playerEpisodes(meta, currentVideoId = "tt0903747:2:2", watchStates = null)

        assertEquals(listOf("S02E01", "S02E02", "S02E03"), episodes.map { it.tag })
        assertEquals("Grilled", episodes[1].name)
    }

    @Test
    fun progressComesFromWatchStateAndAFinishedEpisodeReadsAsFull() {
        val states = listOf(
            watchState("tt0903747:2:1", positionSec = 2_700.0, durationSec = 2_820.0, watched = true),
            watchState("tt0903747:2:2", positionSec = 705.0, durationSec = 2_820.0, watched = false),
        )

        val episodes = playerEpisodes(meta, currentVideoId = "tt0903747:2:2", watchStates = states)

        // Watched wins over the raw fraction: someone who stopped in the
        // credits has finished it, and a bar at 96% says they have not.
        assertEquals(1f, episodes[0].progress)
        assertEquals(0.25f, episodes[1].progress)
        assertEquals(0f, episodes[2].progress)
    }

    @Test
    fun anEpisodeWithNoDurationYetHasNoProgressRatherThanInfiniteProgress() {
        val states = listOf(watchState("tt0903747:2:1", positionSec = 30.0, durationSec = 0.0, watched = false))

        assertEquals(0f, playerEpisodes(meta, "tt0903747:2:1", states)[0].progress)
    }

    @Test
    fun aTitleWithNoVideosHasNoDrawerContents() {
        assertEquals(emptyList(), playerEpisodes(null, "tt0133093", null))
        assertEquals(emptyList(), playerEpisodes(meta.copy(videos = emptyList()), "tt0133093", null))
    }

    @Test
    fun theSeasonTitleFallsBackToTheShowWhenNothingIsNumbered() {
        assertEquals("Season 2", playerSeasonTitle(meta, "tt0903747:2:2", fallback = "Breaking Bad"))
        assertEquals("Breaking Bad", playerSeasonTitle(meta, "unknown-video", fallback = "Breaking Bad"))
        assertEquals("The Matrix", playerSeasonTitle(null, "tt0133093", fallback = "The Matrix"))
    }

    @Test
    fun theSameReleaseIsTheSameBingeGroupFromTheSameAddon() {
        val results = listOf(
            AddonStreams(
                addon = AddonSource("torrentio", "Torrentio"),
                streams = listOf(
                    stream("https://a.test/1080p.mkv", "show|1080p"),
                    stream("https://a.test/2160p.mkv", "show|2160p"),
                ),
            ),
            AddonStreams(
                addon = AddonSource("other", "Other"),
                streams = listOf(stream("https://b.test/2160p.mkv", "show|2160p")),
            ),
        )

        val match = sameReleaseStream(results, addonId = "torrentio", bingeGroup = "show|2160p")

        assertEquals("torrentio", match?.first?.id)
        assertEquals("https://a.test/2160p.mkv", match?.second?.url)
    }

    @Test
    fun aDifferentAddonOfferingTheSameGroupIsNotTheSameRelease() {
        val results = listOf(
            AddonStreams(
                addon = AddonSource("other", "Other"),
                streams = listOf(stream("https://b.test/2160p.mkv", "show|2160p")),
            ),
        )

        assertNull(sameReleaseStream(results, addonId = "torrentio", bingeGroup = "show|2160p"))
    }

    @Test
    fun withoutABingeGroupThereIsNothingToMatchOn() {
        val results = listOf(
            AddonStreams(
                addon = AddonSource("torrentio", "Torrentio"),
                streams = listOf(stream("https://a.test/1080p.mkv", bingeGroup = null)),
            ),
        )

        // Guessing here would autoplay an unrelated file rather than letting
        // the viewer choose.
        assertNull(sameReleaseStream(results, addonId = "torrentio", bingeGroup = null))
    }

    @Test
    fun aMatchWithNoUrlIsNotPlayable() {
        val results = listOf(
            AddonStreams(
                addon = AddonSource("torrentio", "Torrentio"),
                streams = listOf(Stream(behaviorHints = StreamBehaviorHints(bingeGroup = "show|2160p"))),
            ),
        )

        assertNull(sameReleaseStream(results, addonId = "torrentio", bingeGroup = "show|2160p"))
    }

    @Test
    fun theLibraryItemIdIsScopedByTypeRatherThanBeingTheBareMetaId() {
        val context = PlaybackContext(
            url = "https://a.test/e.mkv",
            type = "series",
            metaId = "tt0903747",
            videoId = "tt0903747:1:2",
            showTitle = "Breaking Bad",
            addonId = "torrentio",
        )

        // Every reader of a watch state joins on this, and a bare meta id
        // matches nothing: the row exists and no shelf can find it.
        assertEquals("series:tt0903747", context.itemId)
    }

    @Test
    fun theNextEpisodeStaysInsideTheSeason() {
        assertEquals("tt0903747:2:3", nextEpisodeAfter(meta.videos, "tt0903747:2:2")?.id)
        // Last of the season: nothing follows, rather than the first of the next.
        assertNull(nextEpisodeAfter(meta.videos, "tt0903747:2:3"))
        assertNull(nextEpisodeAfter(meta.videos, "not-an-episode"))
    }

    private fun video(id: String, season: Int, episode: Int, title: String) =
        MetaVideo(id = id, title = title, season = season, episode = episode)

    private fun watchState(videoId: String, positionSec: Double, durationSec: Double, watched: Boolean) =
        WatchState(
            videoId = videoId,
            itemId = "tt0903747",
            positionSec = positionSec,
            durationSec = durationSec,
            watched = watched,
            updatedAt = 1,
        )

    private fun stream(url: String, bingeGroup: String?) =
        Stream(url = url, behaviorHints = StreamBehaviorHints(bingeGroup = bingeGroup))
}
