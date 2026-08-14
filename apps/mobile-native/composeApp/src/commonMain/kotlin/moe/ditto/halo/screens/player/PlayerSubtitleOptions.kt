package moe.ditto.halo.screens.player

import moe.ditto.halo.PlaybackHost
import moe.ditto.halo.api.AddonSubtitles
import moe.ditto.halo.player.PlayerTrack
import moe.ditto.halo.player.PlayerTracks
import moe.ditto.halo.storage.SubtitleChoice
import moe.ditto.halo.storage.SubtitleChoiceKind
import moe.ditto.halo.ui.languageLabel

/**
 * One subtitle an addon offered, in the shape the rail draws and the memory
 * records.
 *
 * [id] is scoped by addon because subtitle ids are only unique within the addon
 * that minted them, and two addons answering the same video with "1" is
 * ordinary. [subId] keeps the addon's own id, which is what a remembered choice
 * is re-found by.
 */
internal data class AddonSubtitleOption(
    val id: String,
    val addonId: String,
    val addonName: String,
    val lang: String,
    val subId: String,
    val url: String,
) {
    /** "English · os-en-6821194": what distinguishes two same-language results. */
    val detail: String get() = "${languageLabel(lang)} · $subId"

    /** Null when the URL does not name a format, which is not worth guessing at. */
    val format: String? get() = subtitleFileFormat(url)
}

/**
 * Formats are read off the URL's extension rather than fetched, because the
 * badge exists before anything is downloaded. Query strings are stripped first:
 * a signed URL ends in a signature, not a file name.
 */
internal fun subtitleFileFormat(url: String): String? {
    val path = url.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('/', "").substringAfterLast('.', "")
    return when (extension.lowercase()) {
        "srt" -> "SRT"
        "vtt", "webvtt" -> "VTT"
        "ass", "ssa" -> "ASS"
        "sub" -> "SUB"
        else -> null
    }
}

internal fun addonSubtitleOptions(results: List<AddonSubtitles>): List<AddonSubtitleOption> =
    results.flatMap { group ->
        group.subtitles.map { subtitle ->
            AddonSubtitleOption(
                id = "${group.addon.id}:${subtitle.id}",
                addonId = group.addon.id,
                addonName = group.addon.name,
                lang = subtitle.lang,
                subId = subtitle.id,
                url = subtitle.url,
            )
        }
    }

/**
 * What to show when playback starts.
 *
 * [Unchanged] is a real outcome and not a failure: with nothing remembered and
 * no preferred language, the engine's own automatic selection is a better
 * answer than anything this could impose.
 */
internal sealed interface SubtitleSelection {
    data object Unchanged : SubtitleSelection
    data object Off : SubtitleSelection
    data class Embedded(val trackId: String) : SubtitleSelection
    data class External(val option: AddonSubtitleOption) : SubtitleSelection
}

/**
 * Restores what the viewer last chose for this video, falling back to language
 * when the exact track is gone.
 *
 * The fallback is the whole point of remembering a language alongside an id: a
 * different release of the next episode has different track ids and different
 * addon results, and "the English one" is what was actually meant.
 *
 * [preferredLang] applies only when nothing was remembered. It is a standing
 * preference, not a decision about this video, which is why choosing it here
 * must never be written back as one.
 */
internal fun resolveSubtitleSelection(
    remembered: SubtitleChoice?,
    tracks: PlayerTracks,
    addonSubtitles: List<AddonSubtitleOption>,
    preferredLang: String?,
): SubtitleSelection {
    if (remembered == null) return preferredSelection(tracks, addonSubtitles, preferredLang)

    return when (remembered.kind) {
        SubtitleChoiceKind.Off -> SubtitleSelection.Off

        SubtitleChoiceKind.Embedded -> {
            val exact = tracks.subtitles.firstOrNull { it.label == remembered.trackName }
            val byLanguage = tracks.subtitles.firstOrNull { it.matches(remembered.lang) }
            (exact ?: byLanguage)?.let { SubtitleSelection.Embedded(it.id) }
                // The remembered track is not in this file. Its language may
                // still be, from an addon.
                ?: addonSubtitles.firstOrNull { it.matches(remembered.lang) }
                    ?.let { SubtitleSelection.External(it) }
                ?: SubtitleSelection.Unchanged
        }

        // A downloaded subtitle is restored the same way as an external one
        // here: this screen has no on-disk copies, so the addon result for the
        // same language is the closest true answer.
        SubtitleChoiceKind.External,
        SubtitleChoiceKind.Downloaded,
        -> {
            val exact = addonSubtitles.firstOrNull { it.subId == remembered.subId }
            val byLanguage = addonSubtitles.firstOrNull { it.matches(remembered.lang) }
            (exact ?: byLanguage)?.let { SubtitleSelection.External(it) }
                ?: tracks.subtitles.firstOrNull { it.matches(remembered.lang) }
                    ?.let { SubtitleSelection.Embedded(it.id) }
                ?: SubtitleSelection.Unchanged
        }
    }
}

/**
 * In-file tracks win over addon results at equal language: they are already
 * present, already timed to this file, and cost no request.
 */
private fun preferredSelection(
    tracks: PlayerTracks,
    addonSubtitles: List<AddonSubtitleOption>,
    preferredLang: String?,
): SubtitleSelection {
    val lang = preferredLang?.takeIf { it.isNotBlank() } ?: return SubtitleSelection.Unchanged
    tracks.subtitles.firstOrNull { it.matches(lang) }?.let { return SubtitleSelection.Embedded(it.id) }
    addonSubtitles.firstOrNull { it.matches(lang) }?.let { return SubtitleSelection.External(it) }
    return SubtitleSelection.Unchanged
}

/** What the viewer just picked, in the form the store keeps it. */
internal fun embeddedChoice(track: PlayerTrack): SubtitleChoice = SubtitleChoice(
    kind = SubtitleChoiceKind.Embedded,
    lang = track.language,
    trackName = track.label,
)

internal fun externalChoice(option: AddonSubtitleOption): SubtitleChoice = SubtitleChoice(
    kind = SubtitleChoiceKind.External,
    lang = option.lang,
    subId = option.subId,
)

internal val OffChoice = SubtitleChoice(kind = SubtitleChoiceKind.Off)

/**
 * Language codes arrive from addons and from track metadata, and the two do not
 * agree on spelling: ISO 639-2 has both a bibliographic and a terminological
 * code for several languages, and some addons send the two-letter form.
 */
private fun PlayerTrack.matches(lang: String?): Boolean =
    languageMatches(language, lang)

private fun AddonSubtitleOption.matches(other: String?): Boolean = languageMatches(lang, other)

internal fun languageMatches(left: String?, right: String?): Boolean {
    val a = left?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
    val b = right?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
    if (a == b) return true
    return canonicalLanguage(a) == canonicalLanguage(b)
}

/**
 * Folds the spellings that mean one language onto one code. Only the pairs the
 * addons in use actually produce; an unknown code stays itself, so two unknown
 * codes still have to match exactly.
 */
private fun canonicalLanguage(code: String): String = when (code) {
    "en", "eng" -> "eng"
    "es", "spa" -> "spa"
    "pt", "por" -> "por"
    "pb", "pob" -> "pob"
    "fr", "fre", "fra" -> "fre"
    "de", "ger", "deu" -> "ger"
    "it", "ita" -> "ita"
    "nl", "dut", "nld" -> "dut"
    "pl", "pol" -> "pol"
    "ru", "rus" -> "rus"
    "ja", "jpn" -> "jpn"
    "ko", "kor" -> "kor"
    "zh", "chi", "zho" -> "chi"
    "ar", "ara" -> "ara"
    "tr", "tur" -> "tur"
    "sv", "swe" -> "swe"
    "cs", "cze", "ces" -> "cze"
    "el", "gre", "ell" -> "gre"
    "he", "heb" -> "heb"
    "fa", "per", "fas" -> "per"
    "ro", "rum", "ron" -> "rum"
    else -> code
}

/**
 * Puts a resolved selection into effect.
 *
 * External subtitles are handed to the engine as the addon gave them, not
 * through the server's addon proxy. That proxy is an authenticated route and
 * the engine sends no Authorization header, so a proxied subtitle URL cannot be
 * opened at all; it is for fetches this app's own code makes. The engine
 * already opens the source URL directly, and a subtitle from the same addon is
 * no wider an exposure than the video it belongs to.
 */
internal suspend fun applySubtitleSelection(
    selection: SubtitleSelection,
    playback: PlaybackHost,
    onAddonSelected: (String?) -> Unit,
) {
    when (selection) {
        SubtitleSelection.Unchanged -> Unit
        SubtitleSelection.Off -> {
            onAddonSelected(null)
            playback.selectSubtitleTrack(null)
        }
        is SubtitleSelection.Embedded -> {
            onAddonSelected(null)
            playback.selectSubtitleTrack(selection.trackId)
        }
        is SubtitleSelection.External -> {
            onAddonSelected(selection.option.id)
            playback.addSubtitle(selection.option.url)
        }
    }
}
