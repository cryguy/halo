package moe.ditto.halo.screens.player

import moe.ditto.halo.api.AddonSource
import moe.ditto.halo.api.AddonSubtitles
import moe.ditto.halo.api.Subtitle
import moe.ditto.halo.player.PlayerTrack
import moe.ditto.halo.player.PlayerTracks
import moe.ditto.halo.storage.SubtitleChoice
import moe.ditto.halo.storage.SubtitleChoiceKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlayerSubtitleOptionsTest {

    private val tracks = PlayerTracks(
        subtitles = listOf(
            PlayerTrack(id = "4", label = "English, Signs & Songs", language = "eng", codec = "ass"),
            PlayerTrack(id = "5", label = "English (SDH)", language = "eng", codec = "subrip"),
            PlayerTrack(id = "6", label = "Japanese", language = "jpn", codec = "hdmv_pgs_subtitle"),
        ),
    )

    private val addons = addonSubtitleOptions(
        listOf(
            AddonSubtitles(
                addon = AddonSource("opensubtitles", "OpenSubtitles"),
                subtitles = listOf(
                    Subtitle(id = "6821194", url = "https://subs.test/6821194.srt", lang = "eng"),
                    Subtitle(id = "774120", url = "https://subs.test/774120.vtt?sig=abc", lang = "spa"),
                ),
            ),
            AddonSubtitles(
                addon = AddonSource("kitsunekko", "Kitsunekko"),
                // Same id as another addon's result: ids are only unique within
                // the addon that minted them.
                subtitles = listOf(Subtitle(id = "6821194", url = "https://subs.test/jp.ass", lang = "ja")),
            ),
        ),
    )

    @Test
    fun optionsAreScopedByAddonSoTwoAddonsCannotCollide() {
        assertEquals(
            listOf("opensubtitles:6821194", "opensubtitles:774120", "kitsunekko:6821194"),
            addons.map { it.id },
        )
        assertEquals("English · 6821194", addons[0].detail)
    }

    @Test
    fun formatsComeFromTheUrlWithoutItsQueryString() {
        assertEquals("SRT", addons[0].format)
        // A signed URL ends in a signature; the extension is before the query.
        assertEquals("VTT", addons[1].format)
        assertEquals("ASS", addons[2].format)
        assertNull(subtitleFileFormat("https://subs.test/download?id=12"))
    }

    @Test
    fun nothingRememberedAndNoPreferenceLeavesTheEnginesOwnChoiceAlone() {
        assertEquals(
            SubtitleSelection.Unchanged,
            resolveSubtitleSelection(null, tracks, addons, preferredLang = null),
        )
    }

    @Test
    fun aPreferredLanguagePicksTheFilesOwnTrackOverAnAddonResult() {
        // The in-file track is already timed to this exact file and costs no
        // request, so at equal language it wins.
        assertEquals(
            SubtitleSelection.Embedded("4"),
            resolveSubtitleSelection(null, tracks, addons, preferredLang = "eng"),
        )
    }

    @Test
    fun aPreferredLanguageTheFileLacksFallsToAnAddon() {
        assertEquals(
            SubtitleSelection.External(addons[1]),
            resolveSubtitleSelection(null, tracks, addons, preferredLang = "spa"),
        )
    }

    @Test
    fun offIsRememberedAsADecisionAndRestoredAsOne() {
        assertEquals(
            SubtitleSelection.Off,
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.Off),
                tracks,
                addons,
                preferredLang = "eng",
            ),
        )
    }

    @Test
    fun anExactTrackNameBeatsTheLanguageItShares() {
        // Two English tracks: the remembered name is what separates them.
        assertEquals(
            SubtitleSelection.Embedded("5"),
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.Embedded, lang = "eng", trackName = "English (SDH)"),
                tracks,
                addons,
                preferredLang = null,
            ),
        )
    }

    @Test
    fun aRememberedTrackMissingFromThisReleaseFallsBackToItsLanguage() {
        // The next episode is a different release with different track names,
        // which is exactly the case the language fallback exists for.
        assertEquals(
            SubtitleSelection.Embedded("4"),
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.Embedded, lang = "eng", trackName = "Full Subtitles"),
                tracks,
                addons,
                preferredLang = null,
            ),
        )
    }

    @Test
    fun aRememberedLanguageAbsentFromTheFileIsLookedForInAddonResults() {
        assertEquals(
            SubtitleSelection.External(addons[1]),
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.Embedded, lang = "spa", trackName = "Spanish"),
                tracks,
                addons,
                preferredLang = null,
            ),
        )
    }

    @Test
    fun anExternalChoiceIsRefoundByItsAddonId() {
        assertEquals(
            SubtitleSelection.External(addons[0]),
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.External, lang = "eng", subId = "6821194"),
                tracks,
                addons,
                preferredLang = null,
            ),
        )
    }

    @Test
    fun anExternalChoiceWhoseResultIsGonePrefersAnotherAddonResultInThatLanguage() {
        // The viewer was offered both and chose an addon subtitle, so another
        // addon result in the same language honours that better than the
        // in-file track they passed over.
        assertEquals(
            SubtitleSelection.External(addons[0]),
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.External, lang = "eng", subId = "no-longer-offered"),
                tracks,
                addons,
                preferredLang = null,
            ),
        )
    }

    @Test
    fun anExternalChoiceNoAddonCanSupplyFallsBackToTheFilesOwnTrack() {
        assertEquals(
            SubtitleSelection.Embedded("6"),
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.External, lang = "jpn", subId = "gone"),
                tracks,
                addonSubtitles = emptyList(),
                preferredLang = null,
            ),
        )
    }

    @Test
    fun aDownloadedChoiceIsRestoredLikeAnExternalOne() {
        // This screen has no on-disk copies, so the addon result for the same
        // language is the closest true answer rather than nothing.
        assertEquals(
            SubtitleSelection.External(addons[1]),
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.Downloaded, lang = "spa", fileName = "spanish.srt"),
                tracks,
                addons,
                preferredLang = null,
            ),
        )
    }

    @Test
    fun aRememberedLanguageNothingCanSupplyChangesNothing() {
        assertEquals(
            SubtitleSelection.Unchanged,
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.External, lang = "fin", subId = "gone"),
                tracks,
                addons,
                preferredLang = null,
            ),
        )
    }

    @Test
    fun twoAndThreeLetterCodesForOneLanguageAreTheSameLanguage() {
        // Addons send both spellings, and ISO 639-2 itself has two codes for
        // several languages; treating them as different would lose the match.
        assertEquals(
            SubtitleSelection.External(addons[2]),
            resolveSubtitleSelection(
                SubtitleChoice(kind = SubtitleChoiceKind.External, lang = "jpn", subId = "gone"),
                PlayerTracks(),
                addons,
                preferredLang = null,
            ),
        )
        assertEquals(true, languageMatches("fre", "fra"))
        assertEquals(true, languageMatches("de", "ger"))
        // An unknown code still has to match exactly.
        assertEquals(false, languageMatches("xyz", "xyw"))
        assertEquals(false, languageMatches("eng", null))
    }

    @Test
    fun anOlderExternalSubtitleCannotOverwriteANewerSelection() = runTest {
        val coordinator = ExternalSubtitleSelectionCoordinator()
        val firstResolved = CompletableDeferred<String>()
        val loaded = mutableListOf<String>()
        val firstAttempt = coordinator.begin()
        val first = async {
            coordinator.load(
                attempt = firstAttempt,
                resolve = { firstResolved.await() },
                addToPlayer = { loaded += it },
            )
        }

        val secondAttempt = coordinator.begin()
        val secondLoaded = coordinator.load(
            attempt = secondAttempt,
            resolve = { "/cache/second.srt" },
            addToPlayer = { loaded += it },
        )
        firstResolved.complete("/cache/first.srt")

        assertEquals(true, secondLoaded)
        assertFalse(first.await())
        assertEquals(listOf("/cache/second.srt"), loaded)
    }

    @Test
    fun newerSelectionCommitsAfterAnOlderPlayerLoadAlreadyInProgress() = runTest {
        val coordinator = ExternalSubtitleSelectionCoordinator()
        val firstLoadStarted = CompletableDeferred<Unit>()
        val finishFirstLoad = CompletableDeferred<Unit>()
        val loaded = mutableListOf<String>()
        val firstAttempt = coordinator.begin()
        val first = async {
            coordinator.load(
                attempt = firstAttempt,
                resolve = { "/cache/first.srt" },
                addToPlayer = {
                    firstLoadStarted.complete(Unit)
                    finishFirstLoad.await()
                    loaded += it
                },
            )
        }
        firstLoadStarted.await()

        val secondAttempt = coordinator.begin()
        val second = async {
            coordinator.load(
                attempt = secondAttempt,
                resolve = { "/cache/second.srt" },
                addToPlayer = { loaded += it },
            )
        }
        finishFirstLoad.complete(Unit)

        assertFalse(first.await())
        assertEquals(true, second.await())
        assertEquals(listOf("/cache/first.srt", "/cache/second.srt"), loaded)
    }

    @Test
    fun failedExternalLoadCannotUpdateSelectionMemory() = runTest {
        val coordinator = ExternalSubtitleSelectionCoordinator()
        val attempt = coordinator.begin()
        var remembered = false

        assertFailsWith<IllegalStateException> {
            coordinator.load(
                attempt = attempt,
                resolve = { throw IllegalStateException("fixture download failed") },
                addToPlayer = {},
                onLoaded = { remembered = true },
            )
        }

        assertFalse(remembered)
    }
}
