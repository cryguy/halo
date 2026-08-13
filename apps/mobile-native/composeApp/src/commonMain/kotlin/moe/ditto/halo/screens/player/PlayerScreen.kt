package moe.ditto.halo.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import moe.ditto.halo.NativePlayerSurface
import moe.ditto.halo.PlaybackHost
import moe.ditto.halo.player.MediaItem
import moe.ditto.halo.player.PlaybackStatus
import moe.ditto.halo.player.PlayerState
import moe.ditto.halo.player.PlayerTracks
import moe.ditto.halo.ui.HaloColors
import moe.ditto.halo.ui.HaloSpacing
import moe.ditto.halo.ui.HaloType

/** How far the bottom bar rises as it appears. */
private const val ChromeFadeMillis = 180
private const val BottomBarRiseMillis = 220
private const val RailEnterMillis = 220
private const val SeekStepSeconds = 10.0

/**
 * The playback screen.
 *
 * Everything except the video is chrome layered over a native render surface,
 * in the z-order the design specifies: the surface itself, then the scrims, then
 * the top bar, centre transport and bottom bar, which appear and disappear
 * together.
 *
 * The chrome that is here is real, but not all of it is connected yet. The
 * right rail, the episode drawer and the transient states (buffering, locked,
 * up next, picture in picture, the error card) are separate pieces of work, and
 * the values behind the speed and episode chips are still fixtures. What is live
 * is the transport: position, duration, play state, seeking, and the audio and
 * subtitle values the engine reports.
 */
// BackHandler is still marked experimental in Compose 1.11; the opt-in is
// scoped to this screen rather than turned on for the whole module, so a future
// signature change surfaces here and nowhere else.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PlayerScreen(
    playback: PlaybackHost,
    surface: NativePlayerSurface,
    url: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by playback.state.collectAsState()
    val scope = rememberCoroutineScope()
    val metrics = rememberPlayerMetrics()
    val controller = remember(scope) { PlayerScreenController(scope) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        playback.play(MediaItem(id = url, title = title, url = url))
    }

    // The chrome starts up and hides itself once playback settles; pausing
    // suppresses that, which is why the controller is told about it rather than
    // reading playback state itself.
    LaunchedEffect(Unit) { controller.armAutoHide() }
    LaunchedEffect(state.status) {
        controller.onPausedChanged(state.status != PlaybackStatus.Playing)
    }

    /**
     * Leaving is asynchronous on purpose, and every way out goes through here.
     *
     * The engine is wound down first and the navigation happens only once that
     * has finished — the screen is still on screen while it runs, which is the
     * one moment its render surface is guaranteed to still exist. Doing this
     * from a disposal callback instead cannot work: the surface is torn down
     * before those run.
     *
     * Playback is paused rather than torn down. Teardown is terminal for the
     * presenter, and on iOS it shuts libmpv down for the rest of the process,
     * so a screen that tore down on the way out would play exactly once per
     * launch.
     */
    val leave: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                playback.windDownForExit()
                onBack()
            }
        }
    }

    // The system gesture and button take the same path as the button drawn
    // here; a back that skipped the wind-down would hang the app just as
    // reliably as no wind-down at all.
    BackHandler(enabled = !leaving, onBack = leave)

    val paused = state.status != PlaybackStatus.Playing

    // Which tab the rail was last showing, so its exit animation still has
    // something to draw after the rail itself has been closed.
    var lastRailTab by remember { mutableStateOf(RailTab.Subtitles) }
    LaunchedEffect(controller.rail) {
        controller.rail?.let { lastRailTab = it }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // The engine paints the whole box; everything else sits on top of it.
        surface.Content(Modifier.fillMaxSize())

        // The video is also the control that shows and hides the chrome. This
        // sits below the chrome, so a tap on a button reaches the button.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { controller.toggleChrome() }
                },
        )

        AnimatedVisibility(
            visible = controller.chromeVisible,
            enter = fadeIn(tween(ChromeFadeMillis)),
            exit = fadeOut(tween(ChromeFadeMillis)),
        ) {
            PlayerScrims()
        }

        AnimatedVisibility(
            visible = controller.chromeVisible,
            enter = fadeIn(tween(ChromeFadeMillis)),
            exit = fadeOut(tween(ChromeFadeMillis)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTopBar(
                metrics = metrics,
                showTitle = PlayerFixtures.ShowTitle,
                episodeTag = PlayerFixtures.CurrentEpisode.tag,
                episodeName = PlayerFixtures.EpisodeName,
                streamBadges = PlayerFixtures.StreamBadges,
                locked = false,
                onBack = leave,
                // Picture in picture, fit mode and lock each need something
                // that does not exist yet (an OS handoff, an mpv panscan call,
                // and the locked scrim). They are drawn now and connected in
                // their own slices; a button that half-works would be worse.
                onPictureInPicture = {},
                onToggleFit = {},
                onToggleLock = {},
            )
        }

        AnimatedVisibility(
            visible = controller.chromeVisible,
            enter = fadeIn(tween(ChromeFadeMillis)),
            exit = fadeOut(tween(ChromeFadeMillis)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            PlayerCentreControls(
                metrics = metrics,
                paused = paused,
                onSeekBack = {
                    controller.onTransportUsed()
                    scope.launch { playback.seekTo(state.positionSeconds - SeekStepSeconds) }
                },
                onTogglePlay = {
                    controller.onTransportUsed()
                    scope.launch { playback.setPaused(!paused) }
                },
                onSeekForward = {
                    controller.onTransportUsed()
                    scope.launch { playback.seekTo(state.positionSeconds + SeekStepSeconds) }
                },
            )
        }

        AnimatedVisibility(
            visible = controller.chromeVisible,
            enter = fadeIn(tween(ChromeFadeMillis)) +
                slideInVertically(tween(BottomBarRiseMillis)) { height -> height / 8 },
            exit = fadeOut(tween(ChromeFadeMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomBar(
                metrics = metrics,
                chips = playerChips(state, controller),
                positionSeconds = state.positionSeconds,
                durationSeconds = state.durationSeconds,
                bufferedFraction = progressFraction(state.positionSeconds, state.durationSeconds) +
                    PlayerFixtures.BufferedLeadFraction,
                scrubFraction = controller.scrubFraction,
                onScrubStart = controller::beginScrub,
                onScrubMove = controller::updateScrub,
                onScrubEnd = {
                    val committed = controller.endScrub()
                    if (committed != null) {
                        scope.launch { playback.seekTo(secondsAt(committed, state.durationSeconds)) }
                    }
                },
                onScrubCancel = controller::cancelScrub,
                onSeekToFraction = { fraction ->
                    controller.showChrome()
                    scope.launch { playback.seekTo(secondsAt(fraction, state.durationSeconds)) }
                },
            )
        }

        // The rail covers the chrome, so it is a sibling above it rather than a
        // child of it: the chrome's own auto-hide must not be able to take the
        // rail down with it.
        AnimatedVisibility(
            visible = controller.rail != null,
            enter = fadeIn(tween(RailEnterMillis)) +
                slideInHorizontally(tween(RailEnterMillis)) { width -> width / 8 },
            exit = fadeOut(tween(RailEnterMillis)) +
                slideOutHorizontally(tween(RailEnterMillis)) { width -> width / 8 },
        ) {
            // Held across the exit animation so the panel does not blank out as
            // it slides away.
            val tab = controller.rail ?: lastRailTab
            PlayerRail(
                tab = tab,
                metrics = metrics,
                tracks = state.tracks,
                subtitleScale = state.subtitleScale,
                subtitleDelaySeconds = state.subtitleDelaySeconds,
                subtitleFont = state.subtitleFont,
                trackStyling = controller.subtitleTrackStyling,
                selectedAddonSubtitleId = controller.selectedAddonSubtitleId,
                audioDelaySeconds = controller.audioDelaySeconds,
                playbackRate = controller.playbackRate,
                onSelectTab = controller::openRail,
                onClose = controller::closeRail,
                onSelectSubtitleTrack = { id ->
                    controller.selectAddonSubtitle(null)
                    scope.launch { playback.selectSubtitleTrack(id) }
                },
                onSelectAddonSubtitle = controller::selectAddonSubtitle,
                onSubtitleScaleChange = { scale -> scope.launch { playback.setSubtitleScale(scale) } },
                onSubtitleDelayChange = { seconds ->
                    scope.launch {
                        playback.setSubtitleDelay(seconds.coerceIn(-MaxDelaySeconds, MaxDelaySeconds))
                    }
                },
                onTrackStylingChange = controller::setTrackStyling,
                onSubtitleFontChange = { font -> scope.launch { playback.setSubtitleFont(font) } },
                onSelectAudioTrack = { id -> scope.launch { playback.selectAudioTrack(id) } },
                onAudioDelayChange = controller::setAudioDelay,
                onPlaybackRateChange = controller::selectPlaybackRate,
            )
        }

        // Placeholders for two states the design gives proper treatments: a
        // buffering overlay and an error card, both in a later slice. Until
        // then a dead source has to stay distinguishable from a slow one,
        // because both look like a black rectangle.
        when (state.status) {
            PlaybackStatus.Loading -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = HaloColors.Accent)
            }
            PlaybackStatus.Failed -> Text(
                text = state.error ?: "This source could not be played.",
                style = HaloType.Body.copy(color = HaloColors.Danger),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = HaloSpacing.Xl),
            )
            else -> Unit
        }
    }
}

private fun secondsAt(fraction: Float, durationSeconds: Double?): Double {
    val duration = durationSeconds ?: return 0.0
    if (!duration.isFinite() || duration <= 0.0) return 0.0
    return duration * fraction.coerceIn(0f, 1f)
}

/**
 * The four state chips. Subtitles and audio read what the engine actually has
 * selected; speed and episodes are still fixtures, and each goes live with the
 * capability behind it.
 */
private fun playerChips(state: PlayerState, controller: PlayerScreenController): List<PlayerChip> = listOf(
    PlayerChip(
        kicker = "SUBTITLES",
        value = subtitleChipValue(state.tracks),
        active = controller.rail == RailTab.Subtitles,
        onClick = { controller.openRail(RailTab.Subtitles) },
    ),
    PlayerChip(
        kicker = "AUDIO",
        value = audioChipValue(state.tracks),
        active = controller.rail == RailTab.Audio,
        onClick = { controller.openRail(RailTab.Audio) },
    ),
    PlayerChip(
        kicker = "SPEED",
        value = formatRate(controller.playbackRate),
        active = controller.rail == RailTab.Speed,
        onClick = { controller.openRail(RailTab.Speed) },
    ),
    PlayerChip(
        kicker = "EPISODES",
        value = PlayerFixtures.CurrentEpisode.tag,
        active = controller.episodeDrawerOpen,
        onClick = controller::toggleEpisodeDrawer,
    ),
)

/**
 * The design shows language and format together (`English · ASS`). Format needs
 * the track's codec, which the engine does not report yet, so the language
 * stands alone until it does.
 */
private fun subtitleChipValue(tracks: PlayerTracks): String {
    val selected = tracks.subtitles.firstOrNull { it.id == tracks.selectedSubtitleId } ?: return "Off"
    return selected.language ?: selected.label
}

private fun audioChipValue(tracks: PlayerTracks): String {
    val selected = tracks.audio.firstOrNull { it.id == tracks.selectedAudioId }
        ?: tracks.audio.firstOrNull()
        ?: return "None"
    return selected.language ?: selected.label
}
