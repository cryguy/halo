package moe.ditto.halo.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.ditto.halo.player.PlayerTracks
import moe.ditto.halo.ui.HaloColors
import moe.ditto.halo.ui.HaloIcons
import moe.ditto.halo.ui.HaloPlayerColors
import moe.ditto.halo.ui.HaloRadius
import moe.ditto.halo.ui.Segmented

private val RailScrim = Color(red = 4f / 255f, green = 5f / 255f, blue = 8f / 255f, alpha = 0.5f)

/**
 * The design's rail fill is translucent because it is meant to sit over a 30dp
 * backdrop blur, and there is no blur to be had over video: the picture is
 * painted by the platform below the whole composition, so nothing in the Compose
 * tree can sample it. Flattened onto the app background instead, which is the
 * same fallback `HaloGlass` takes when no blur source is registered, and for the
 * same reason: translucency with sharp content behind it does not read as glass,
 * it reads as a panel that failed to draw.
 */
private val RailPanelFill = HaloPlayerColors.RailFill.compositeOver(HaloColors.Background)
private val RailEdge = Color.White.copy(alpha = 0.11f)
private val CloseButtonFill = Color.White.copy(alpha = 0.08f)
private val RailCornerRadius = 22.dp

private const val AudioLabel = "Audio"
private const val SubtitlesLabel = "Subtitles"
private const val SpeedLabel = "Speed"

/**
 * The one panel for audio, subtitles and speed.
 *
 * It replaces both of the old player's surfaces: a half-width side sheet for
 * track selection and a separate full-screen overlay for subtitle appearance.
 * Being narrow and side-anchored is the functional part, not a style choice.
 * Subtitle size and delay can only sensibly be judged against the picture they
 * apply to, and a full-screen panel hides exactly that.
 *
 * Everything here applies to the running core. Nothing reloads, which is what
 * the header says out loud, because the old player could not do it and the
 * habit of expecting a reload is worth breaking explicitly.
 */
@Composable
internal fun PlayerRail(
    tab: RailTab,
    metrics: PlayerMetrics,
    tracks: PlayerTracks,
    subtitleScale: Double,
    subtitleDelaySeconds: Double,
    subtitleFont: String?,
    trackStyling: Boolean,
    selectedAddonSubtitleId: String?,
    bundledSubtitleFonts: Set<String>,
    addonSubtitles: List<AddonSubtitleOption>,
    addonSubtitlesFetching: Boolean,
    subtitleLoadError: String?,
    audioDelaySeconds: Double,
    playbackRate: Double,
    onSelectTab: (RailTab) -> Unit,
    onClose: () -> Unit,
    onSelectSubtitleTrack: (String?) -> Unit,
    onSelectAddonSubtitle: (AddonSubtitleOption) -> Unit,
    onSubtitleScaleChange: (Double) -> Unit,
    onSubtitleDelayChange: (Double) -> Unit,
    onTrackStylingChange: (Boolean) -> Unit,
    onSubtitleFontChange: (String?) -> Unit,
    onSelectAudioTrack: (String?) -> Unit,
    onAudioDelayChange: (Double) -> Unit,
    onPlaybackRateChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // Tapping away closes, which is the fastest way out of a panel that
        // covers the picture it is tuning. No ripple: this is a dismiss target,
        // not a button.
        Box(
            Modifier
                .fillMaxSize()
                .background(RailScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Close playback options",
                    onClick = onClose,
                ),
        )

        val shape = RoundedCornerShape(topStart = RailCornerRadius, bottomStart = RailCornerRadius)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(metrics.railWidth)
                .fillMaxHeight(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(RailPanelFill)
                // Swallows taps so they do not reach the dismiss scrim behind.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                // The panel is full-bleed but its contents are not: the fill
                // runs to the edges while the header clears the status bar.
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            RailHeader(onClose = onClose)

            Segmented(
                options = listOf(AudioLabel, SubtitlesLabel, SpeedLabel),
                value = tab.label(),
                onChange = { label -> onSelectTab(label.toRailTab()) },
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    RailTab.Subtitles -> SubtitlesTab(
                        tracks = tracks,
                        subtitleScale = subtitleScale,
                        subtitleDelaySeconds = subtitleDelaySeconds,
                        subtitleFont = subtitleFont,
                        trackStyling = trackStyling,
                        selectedAddonId = selectedAddonSubtitleId,
                        bundledFonts = bundledSubtitleFonts,
                        addonSubtitles = addonSubtitles,
                        addonSubtitlesFetching = addonSubtitlesFetching,
                        subtitleLoadError = subtitleLoadError,
                        captionBaseSize = metrics.captionSize,
                        onSelectTrack = onSelectSubtitleTrack,
                        onSelectAddonSubtitle = onSelectAddonSubtitle,
                        onScaleChange = onSubtitleScaleChange,
                        onDelayChange = onSubtitleDelayChange,
                        onTrackStylingChange = onTrackStylingChange,
                        onFontChange = onSubtitleFontChange,
                    )
                    RailTab.Audio -> AudioTab(
                        tracks = tracks,
                        audioDelaySeconds = audioDelaySeconds,
                        onSelectTrack = onSelectAudioTrack,
                        onDelayChange = onAudioDelayChange,
                    )
                    RailTab.Speed -> SpeedTab(
                        rate = playbackRate,
                        onRateChange = onPlaybackRateChange,
                    )
                }
            }
        }

            // The visible edge, drawn over the panel rather than as part of it
            // so the rounded corners carry it too. Non-interactive.
            Box(Modifier.fillMaxSize().border(1.dp, RailEdge, shape))
        }
    }
}

@Composable
private fun RailHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Playback", color = HaloColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Applies live, nothing reloads",
                color = HaloColors.TextDim,
                fontSize = 11.5.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(HaloRadius.Pill))
                .background(CloseButtonFill)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HaloIcons.Close,
                contentDescription = "Close playback options",
                tint = HaloColors.Text,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun RailTab.label(): String = when (this) {
    RailTab.Audio -> AudioLabel
    RailTab.Subtitles -> SubtitlesLabel
    RailTab.Speed -> SpeedLabel
}

private fun String.toRailTab(): RailTab = when (this) {
    AudioLabel -> RailTab.Audio
    SpeedLabel -> RailTab.Speed
    else -> RailTab.Subtitles
}
