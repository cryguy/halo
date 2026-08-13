package moe.ditto.halo.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * The auto-hide rule is the thing worth pinning down: several unrelated
 * interactions arm it, and four independent conditions suppress it. Every case
 * below is one of those conditions, checked against virtual time so the tests
 * cost nothing to run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerScreenControllerTest {

    /** Comfortably past the 3 s idle window. */
    private val pastIdle = 3_500L

    @Test
    fun chromeStartsVisibleAndHidesWhenIdle() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        assertTrue(controller.chromeVisible)

        controller.armAutoHide()
        advanceTimeBy(pastIdle)

        assertFalse(controller.chromeVisible, "chrome should hide itself once idle")
    }

    @Test
    fun chromeStaysUpWhilePaused() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.onPausedChanged(true)

        advanceTimeBy(pastIdle)

        assertTrue(controller.chromeVisible, "a deliberate pause should keep the controls up")
    }

    @Test
    fun resumingAfterAPauseRestartsTheIdleTimer() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.onPausedChanged(true)
        advanceTimeBy(pastIdle)
        assertTrue(controller.chromeVisible)

        controller.onPausedChanged(false)
        advanceTimeBy(pastIdle)

        assertFalse(controller.chromeVisible)
    }

    @Test
    fun chromeStaysUpWhileTheRailIsOpen() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.openRail(RailTab.Subtitles)

        advanceTimeBy(pastIdle)

        assertTrue(controller.chromeVisible)
        assertEquals(RailTab.Subtitles, controller.rail)
    }

    @Test
    fun closingTheRailLetsTheChromeHideAgain() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.openRail(RailTab.Audio)
        controller.closeRail()

        advanceTimeBy(pastIdle)

        assertNull(controller.rail)
        assertFalse(controller.chromeVisible)
    }

    @Test
    fun chromeStaysUpWhileTheEpisodeDrawerIsOpen() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.toggleEpisodeDrawer()

        advanceTimeBy(pastIdle)

        assertTrue(controller.episodeDrawerOpen)
        assertTrue(controller.chromeVisible)
    }

    @Test
    fun theRailAndTheDrawerAreMutuallyExclusive() = runTest {
        val controller = PlayerScreenController(backgroundScope)

        controller.openRail(RailTab.Speed)
        controller.toggleEpisodeDrawer()
        assertNull(controller.rail, "opening the drawer should close the rail")
        assertTrue(controller.episodeDrawerOpen)

        controller.openRail(RailTab.Audio)
        assertFalse(controller.episodeDrawerOpen, "opening the rail should close the drawer")
        assertEquals(RailTab.Audio, controller.rail)
    }

    @Test
    fun chromeStaysUpWhileScrubbing() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.beginScrub(0.5f)

        advanceTimeBy(pastIdle)

        assertTrue(controller.chromeVisible, "chrome must not vanish under a finger mid-drag")
    }

    @Test
    fun aScrubCommitsItsFinalPositionAndReleasesTheTimer() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.beginScrub(0.5f)
        controller.updateScrub(0.8f)

        assertEquals(0.8f, controller.endScrub())
        assertNull(controller.scrubFraction)

        advanceTimeBy(pastIdle)
        assertFalse(controller.chromeVisible)
    }

    @Test
    fun scrubFractionsAreClampedToTheTrack() = runTest {
        val controller = PlayerScreenController(backgroundScope)

        controller.beginScrub(-0.4f)
        assertEquals(0f, controller.scrubFraction)

        controller.updateScrub(1.9f)
        assertEquals(1f, controller.scrubFraction)
    }

    @Test
    fun aScrubUpdateWithoutAScrubInFlightIsIgnored() = runTest {
        val controller = PlayerScreenController(backgroundScope)

        controller.updateScrub(0.7f)

        assertNull(controller.scrubFraction, "a stray drag event must not open a scrub")
        assertNull(controller.endScrub())
    }

    @Test
    fun tappingTheVideoTogglesTheChromeBothWays() = runTest {
        val controller = PlayerScreenController(backgroundScope)

        controller.toggleChrome()
        assertFalse(controller.chromeVisible)

        // Hidden chrome has no timer to arm, so it stays down until asked back.
        advanceTimeBy(pastIdle)
        assertFalse(controller.chromeVisible)

        controller.toggleChrome()
        assertTrue(controller.chromeVisible)
    }

    @Test
    fun audioDelayIsClampedToFiveSecondsEitherWay() = runTest {
        val controller = PlayerScreenController(backgroundScope)

        controller.setAudioDelay(9.0)
        assertEquals(MaxDelaySeconds, controller.audioDelaySeconds)

        controller.setAudioDelay(-9.0)
        assertEquals(-MaxDelaySeconds, controller.audioDelaySeconds)

        controller.setAudioDelay(0.25)
        assertEquals(0.25, controller.audioDelaySeconds)
    }

    @Test
    fun anUnusableDelayLeavesTheCurrentOneAlone() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.setAudioDelay(0.5)

        controller.setAudioDelay(Double.NaN)

        assertEquals(0.5, controller.audioDelaySeconds)
    }

    @Test
    fun playbackRateRejectsZeroAndNegatives() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        assertEquals(1.0, controller.playbackRate)

        controller.selectPlaybackRate(1.5)
        assertEquals(1.5, controller.playbackRate)

        // A rate of zero is a stop, not a speed, and mpv treats it as a hang.
        controller.selectPlaybackRate(0.0)
        controller.selectPlaybackRate(-1.0)
        assertEquals(1.5, controller.playbackRate)
    }

    @Test
    fun usingTheTransportBringsTheChromeBackAndRearmsTheTimer() = runTest {
        val controller = PlayerScreenController(backgroundScope)
        controller.toggleChrome()
        assertFalse(controller.chromeVisible)

        controller.onTransportUsed()
        assertTrue(controller.chromeVisible)

        advanceTimeBy(2_000L)
        assertTrue(controller.chromeVisible, "the timer should have restarted, not resumed")

        advanceTimeBy(2_000L)
        assertFalse(controller.chromeVisible)
    }
}
