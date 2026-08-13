package moe.ditto.halo.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.ditto.halo.player.PlayerTracks
import moe.ditto.halo.ui.HaloColors
import moe.ditto.halo.ui.HaloPlayerColors
import moe.ditto.halo.ui.HaloRadius

private val PreviewStripFill = Color(0xFF12141B)
private val PreviewStripBorder = Color.White.copy(alpha = 0.08f)
private val NoteCardFill = Color.White.copy(alpha = 0.05f)
private val SpeedIdleFill = Color.White.copy(alpha = 0.05f)
private val SpeedIdleBorder = Color.White.copy(alpha = 0.08f)

/** How much smaller the preview draws the caption than the video does. */
private const val PreviewCaptionRatio = 0.82f

internal val PlaybackRates = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)

/**
 * What a subtitle track is made of, which decides which appearance controls can
 * do anything at all. Text tracks can be restyled; a bitmap track is a picture
 * of text and can only be scaled.
 */
internal enum class SubtitleFormat {
    Ass,
    Text,
    Bitmap,
    Unknown,
}

@Composable
internal fun SubtitlesTab(
    tracks: PlayerTracks,
    subtitleScale: Double,
    subtitleDelaySeconds: Double,
    subtitleFont: String?,
    trackStyling: Boolean,
    selectedAddonId: String?,
    captionBaseSize: TextUnit,
    onSelectTrack: (String?) -> Unit,
    onSelectAddonSubtitle: (String) -> Unit,
    onScaleChange: (Double) -> Unit,
    onDelayChange: (Double) -> Unit,
    onTrackStylingChange: (Boolean) -> Unit,
    onFontChange: (String?) -> Unit,
) {
    val format = SubtitleFormat.Unknown
    val bitmap = format == SubtitleFormat.Bitmap

    RailSectionLabel("IN THIS FILE")
    RailSelectableRow(
        label = "Off",
        detail = "No subtitles",
        selected = tracks.selectedSubtitleId == null && selectedAddonId == null,
        onClick = { onSelectTrack(null) },
    )
    tracks.subtitles.forEach { track ->
        RailSelectableRow(
            label = track.label,
            detail = track.language,
            selected = track.id == tracks.selectedSubtitleId && selectedAddonId == null,
            onClick = { onSelectTrack(track.id) },
        )
    }

    RailSectionLabel("FROM ADDONS")
    PlayerFixtures.AddonSubtitles.forEach { subtitle ->
        RailSelectableRow(
            label = subtitle.addonName,
            detail = subtitle.detail,
            selected = subtitle.id == selectedAddonId,
            onClick = { onSelectAddonSubtitle(subtitle.id) },
            format = subtitle.format,
            onDisk = subtitle.onDisk,
        )
    }

    RailHairline(Modifier.padding(vertical = 2.dp))
    RailSectionLabel("APPEARANCE")

    RailCard {
        RailCardHeader(
            label = "Size",
            hint = if (bitmap) "Bitmap track, scaled but not restyled" else "mpv sub-scale, live",
            value = formatScalePercent(subtitleScale),
        )
        RailSlider(
            value = subtitleScale.toFloat(),
            valueRange = 0.5f..2f,
            onValueChange = { onScaleChange(it.toDouble()) },
            modifier = Modifier.padding(top = 6.dp),
        )
        RailSliderTicks(
            ticks = listOf(0.5f, 1f, 2f),
            valueRange = 0.5f..2f,
            label = { scale -> (scale * 100f).toInt().toString() },
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    RailCard {
        RailCardHeader(label = "Delay", hint = "Shifts the track against the picture")
        RailDelayStepper(
            seconds = subtitleDelaySeconds,
            onChange = onDelayChange,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    RailCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Track styling",
                    color = HaloColors.Text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = trackStylingHint(format, trackStyling),
                    color = HaloColors.TextDim,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Disabled only where the format makes it meaningless. A track
            // whose codec the engine has not reported yet may well be styled,
            // so the switch stays usable rather than dead.
            RailSwitch(
                checked = trackStyling,
                onCheckedChange = onTrackStylingChange,
                enabled = format != SubtitleFormat.Bitmap && format != SubtitleFormat.Text,
            )
        }
        // With a script's own styling in force, a font choice is ignored: the
        // chips stay so the current selection is still readable, but they are
        // dimmed to say they are not in effect.
        RailFontChips(
            selected = subtitleFont,
            onSelect = onFontChange,
            modifier = Modifier.padding(top = 10.dp),
            inert = bitmap || (format == SubtitleFormat.Ass && trackStyling),
        )
    }

    RailCard {
        RailSectionLabel("PREVIEW")
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(HaloRadius.Sm))
                .background(PreviewStripFill)
                .border(1.dp, PreviewStripBorder, RoundedCornerShape(HaloRadius.Sm))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = PlayerFixtures.CaptionSample,
                color = HaloColors.Text,
                fontSize = captionBaseSize * subtitleScale.toFloat() * PreviewCaptionRatio,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The switch means different things per format, and for two of them it means
 * nothing at all. Saying so is the only way the control is honest: a disabled
 * switch with no explanation reads as a bug.
 */
private fun trackStylingHint(format: SubtitleFormat, enabled: Boolean): String = when (format) {
    SubtitleFormat.Ass ->
        if (enabled) "Keeping the script's own fonts and positions"
        else "Overriding the script with Halo styling"
    SubtitleFormat.Text -> "Plain text track: Halo styling always applies"
    SubtitleFormat.Bitmap -> "PGS is rendered images: font and outline do not apply"
    // Until the engine reports each track's codec there is no way to tell which
    // of the three a track is, so the control states the general rule.
    SubtitleFormat.Unknown -> "Applies to styled tracks only"
}

@Composable
internal fun AudioTab(
    tracks: PlayerTracks,
    audioDelaySeconds: Double,
    onSelectTrack: (String?) -> Unit,
    onDelayChange: (Double) -> Unit,
) {
    RailSectionLabel("TRACKS")
    if (tracks.audio.isEmpty()) {
        Text(
            text = "This source reports no audio tracks.",
            color = HaloColors.TextDim,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
    tracks.audio.forEach { track ->
        RailSelectableRow(
            label = track.label,
            detail = track.language,
            selected = track.id == tracks.selectedAudioId,
            onClick = { onSelectTrack(track.id) },
        )
    }

    RailHairline(Modifier.padding(vertical = 2.dp))

    RailCard {
        RailCardHeader(label = "Delay", hint = "Shifts the sound against the picture")
        RailDelayStepper(
            seconds = audioDelaySeconds,
            onChange = onDelayChange,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun SpeedTab(
    rate: Double,
    onRateChange: (Double) -> Unit,
) {
    RailSectionLabel("SPEED")
    PlaybackRates.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { candidate ->
                SpeedCell(
                    rate = candidate,
                    selected = candidate == rate,
                    onClick = { onRateChange(candidate) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HaloRadius.Md))
            .background(NoteCardFill)
            .padding(12.dp),
    ) {
        Text(
            text = "Pitch is corrected up to 2×. Subtitle timing follows the rate automatically.",
            color = HaloColors.TextDim,
            fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun SpeedCell(
    rate: Double,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(HaloRadius.Md)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) HaloPlayerColors.ChipActiveFill else SpeedIdleFill)
            .border(
                width = 1.dp,
                color = if (selected) HaloPlayerColors.ChipActiveBorder else SpeedIdleBorder,
                shape = shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatRate(rate),
            color = if (selected) Color.White else HaloColors.TextMeta,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = rateDescription(rate),
            color = HaloColors.TextDim,
            fontSize = 10.5.sp,
        )
    }
}

/** Says what a rate does, so the number does not have to be converted mentally. */
private fun rateDescription(rate: Double): String {
    if (rate == 1.0) return "Normal"
    val percent = ((rate - 1.0) * 100.0).let { if (it < 0) -it else it }
    val rounded = percent.toInt()
    return if (rate < 1.0) "$rounded% slower" else "$rounded% faster"
}

