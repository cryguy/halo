package moe.ditto.halo

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shipping player flows over the real fixture API and real libmpv core.
 *
 * Requires `dev:fixtures --media <file>` on port 18790 and
 * `adb reverse tcp:18790 tcp:18790`. The launch is reset for every test and
 * signs into the local fixture account, so no test inherits a route, token or
 * playback position from another one.
 *
 * No coordinate is hard-coded. The one touch on the video uses the root's
 * measured centre, which is what a viewer means by "tap the picture" and keeps
 * working across emulator sizes and orientations.
 */
@RunWith(AndroidJUnit4::class)
class PlayerScreenInstrumentedTest {
    private val launchIntent = Intent(
        ApplicationProvider.getApplicationContext<Context>(),
        MainActivity::class.java,
    ).apply {
        putExtra("serverUrl", "http://127.0.0.1:18790")
        putExtra("resetSession", true)
    }

    @get:Rule(order = 0)
    val rule = createEmptyComposeRule()

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule<MainActivity>(launchIntent)

    @Test
    fun chromeHidesAfterPlaybackSettles() {
        gotoPlayer()
        showChrome()
        rule.onNodeWithText("SUBTITLES").assertExists()

        rule.waitUntil(6_000) { nodesWithText("SUBTITLES").isEmpty() }

        assertTrue(nodesWithText("SUBTITLES").isEmpty())
    }

    @Test
    fun trackSwitchKeepsPlaybackPositionAndDoesNotReloadTheScreen() {
        gotoPlayer()
        showChrome()
        rule.onNodeWithText("SUBTITLES").performClick()
        rule.waitUntil(5_000) { nodesWithText("Off").isNotEmpty() }

        // An open rail suppresses chrome auto-hide, so the dynamic elapsed
        // value remains observable while the real player applies the track.
        val before = waitForElapsedAtLeast(5)
        rule.onNodeWithText("Off").performClick()

        val after = elapsedSeconds()
        assertTrue("position reset from $before to $after after switching a track", after >= before)

        rule.onNodeWithContentDescription("Close playback options").performClick()
        showChrome()
        // The same player is still visible, rather than a picker or a new load.
        rule.onNodeWithText("The Matrix").assertExists()
    }

    @Test
    fun backWindsDownBeforeThePlayerAndItsDeviceClaimsDisappear() {
        gotoPlayer()
        showChrome()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(10_000) { nodesWithText("Watch").isNotEmpty() }

        activityRule.scenario.onActivity { activity ->
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
            assertFalse(
                "player left FLAG_KEEP_SCREEN_ON behind",
                activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
            )
        }
    }

    private fun gotoPlayer() {
        rule.onNodeWithContentDescription("Continue").performClick()
        rule.waitUntil(10_000) { nodesWithText("Username").isNotEmpty() }
        rule.onNode(hasText("Username") and hasSetTextAction()).performTextInput("admin")
        rule.onNode(hasText("Password") and hasSetTextAction()).performTextInput("fixture-pass")
        rule.onNodeWithContentDescription("Sign In").performClick()

        rule.waitUntil(15_000) {
            rule.onAllNodes(hasText("Play") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText("Play") and hasClickAction()).performClick()
        rule.waitUntil(10_000) { nodesWithText(FirstSourceFilename).isNotEmpty() }
        rule.onAllNodes(hasText(FirstSourceFilename) and hasClickAction()).onFirst().performClick()

        rule.waitUntil(15_000) { nodesWithContentDescription("Back").isNotEmpty() }
        showChrome()
        rule.waitUntil(25_000) {
            nodesWithContentDescription("Elapsed", substring = true).isNotEmpty()
        }
    }

    private fun showChrome() {
        if (nodesWithText("SUBTITLES").isNotEmpty()) return
        rule.onRoot().performTouchInput { click(center) }
        rule.waitUntil(3_000) { nodesWithText("SUBTITLES").isNotEmpty() }
    }

    private fun waitForElapsedAtLeast(seconds: Int): Int {
        rule.waitUntil(15_000) { elapsedSeconds() >= seconds }
        return elapsedSeconds()
    }

    private fun elapsedSeconds(): Int {
        val node = nodesWithContentDescription("Elapsed", substring = true).firstOrNull() ?: return -1
        val description = node.config[SemanticsProperties.ContentDescription].firstOrNull() ?: return -1
        val timecode = description.removePrefix("Elapsed ")
        val parts = timecode.split(':').mapNotNull(String::toIntOrNull)
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> -1
        }
    }

    private fun nodesWithText(text: String) =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes()

    private fun nodesWithContentDescription(text: String, substring: Boolean = false) =
        rule.onAllNodes(hasContentDescription(text, substring = substring)).fetchSemanticsNodes()

    private companion object {
        const val FirstSourceFilename = "tt0133093.2160p.WEB-DL.DDP5.1.HDR.HEVC.mkv"
    }
}
