package moe.ditto.halo.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.launch
import moe.ditto.halo.NativePlayerSurface
import moe.ditto.halo.PlaybackHost
import moe.ditto.halo.player.MediaItem
import moe.ditto.halo.player.PlaybackStatus
import moe.ditto.halo.player.PlayerState
import moe.ditto.halo.player.PlayerTracks
import moe.ditto.halo.ui.HaloColors

/** How far the bottom bar rises as it appears. */
private const val ChromeFadeMillis = 180
private const val BottomBarRiseMillis = 220
private const val RailEnterMillis = 220
private const val DrawerEnterMillis = 240
private const val SeekStepSeconds = 10.0

/**
 * The playback screen.
 *
 * Everything except the video is chrome layered over a native render surface,
 * in the z-order the design specifies: the surface itself, then the scrims, then
 * the top bar, centre transport and bottom bar, which appear and disappear
 * together.
 *
 * Above the chrome sit the panels and the transient states, each a sibling
 * rather than a child of it, so the chrome's own auto-hide cannot take one of
 * them down with it.
 *
 * What is driven by the engine: position, duration, play state, seeking, the
 * audio and subtitle track lists and the subtitle styling, and the error card.
 * The naming and the badges come from [context], which the source picker
 * resolved before playback was entered. What is neither, and is waiting on a
 * capability rather than on this screen: playback rate, audio delay, fit mode,
 * the buffering figures, the gesture readout, the episode list and the up-next
 * countdown's contents. Those are reachable from the debug player scene harness
 * so they can be reviewed before the data behind them exists.
 */
// BackHandler is still marked experimental in Compose 1.11; the opt-in is
// scoped to this screen rather than turned on for the whole module, so a future
// signature change surfaces here and nowhere else.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PlayerScreen(
    playback: PlaybackHost,
    surface: NativePlayerSurface,
    /** What is being played and what to call it; see [PlaybackContext]. */
    context: PlaybackContext,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by playback.state.collectAsState()
    val scope = rememberCoroutineScope()
    val metrics = rememberPlayerMetrics()
    val controller = remember(scope) { PlayerScreenController(scope) }
    var leaving by remember { mutableStateOf(false) }

    // The video is the item, so the video id names it. The URL is one way to
    // reach that item and not what it is. Two sources for the same episode are
    // the same thing being watched, which is what the slices reporting on the
    // session have to agree on.
    val item = remember(context) {
        MediaItem(id = context.videoId, title = context.displayTitle, url = context.url)
    }
    // Parsed once per source rather than per frame of chrome: the strings are
    // release names, and the regexes over them are not free.
    val streamBadges = remember(context) {
        streamBadges(filename = context.filename, title = context.streamTitle, name = context.streamName)
    }

    LaunchedEffect(item) {
        playback.play(item)
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
     * has finished. The screen is still on screen while it runs, which is the
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
                showTitle = context.showTitle,
                episodeTag = context.episodeTag,
                episodeName = context.episodeName,
                streamBadges = streamBadges,
                locked = controller.locked,
                onBack = leave,
                onPictureInPicture = controller::enterPictureInPicture,
                // Fit mode is the one utility control still waiting on
                // something that does not exist: an mpv panscan call.
                onToggleFit = {},
                onToggleLock = controller::lock,
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
                chips = playerChips(state, controller, context),
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

        AnimatedVisibility(
            visible = controller.episodeDrawerOpen,
            enter = fadeIn(tween(DrawerEnterMillis)) +
                slideInVertically(tween(DrawerEnterMillis)) { height -> height / 6 },
            exit = fadeOut(tween(DrawerEnterMillis)) +
                slideOutVertically(tween(DrawerEnterMillis)) { height -> height / 6 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerEpisodeDrawer(
                metrics = metrics,
                seasonTitle = PlayerFixtures.SeasonTitle,
                episodes = PlayerFixtures.Episodes,
                currentTag = PlayerFixtures.CurrentEpisode.tag,
                onClose = controller::closeEpisodeDrawer,
                // Choosing an episode has to resolve a stream for it before
                // anything can play, so for now it only closes the drawer.
                onSelectEpisode = { controller.closeEpisodeDrawer() },
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
                playbackRate = state.playbackRate,
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
                onPlaybackRateChange = { rate -> scope.launch { playback.setPlaybackRate(rate) } },
            )
        }

        // A load in progress is not the same overlay as a stalled buffer: the
        // engine reports no cache figures until it has opened the source, so
        // there is no percentage to show yet.
        if (state.status == PlaybackStatus.Loading) {
            CircularProgressIndicator(
                color = HaloColors.Accent,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        controller.hud?.let { hud ->
            GestureHudOverlay(hud)
        }

        controller.upNextSecondsRemaining?.let { remaining ->
            UpNextCard(
                metrics = metrics,
                episodeTag = PlayerFixtures.NextEpisode.tag,
                episodeName = PlayerFixtures.NextEpisode.name,
                secondsRemaining = remaining,
                totalSeconds = UpNextSeconds,
                onCancel = controller::dismissUpNext,
                onPlayNow = controller::advanceToNext,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        if (controller.pictureInPicture) {
            PictureInPictureOverlay(
                metrics = metrics,
                positionFraction = progressFraction(state.positionSeconds, state.durationSeconds),
                onReturn = controller::exitPictureInPicture,
            )
        }

        if (controller.locked) {
            LockedOverlay(
                metrics = metrics,
                pillVisible = controller.unlockPillVisible,
                onScrimTap = controller::revealUnlockPill,
                onUnlock = controller::unlock,
            )
        }

        if (state.status == PlaybackStatus.Failed) {
            PlaybackErrorCard(
                engineMessage = state.error,
                onRetry = { scope.launch { playback.play(item) } },
                // Picking another source means going back to the list, which
                // this screen replaced on the way in. Leaving returns to the
                // title; navigating straight to the picker is a later change.
                onPickAnotherSource = leave,
            )
        }
    }
}

private fun secondsAt(fraction: Float, durationSeconds: Double?): Double {
    val duration = durationSeconds ?: return 0.0
    if (!duration.isFinite() || duration <= 0.0) return 0.0
    return duration * fraction.coerceIn(0f, 1f)
}

/**
 * The state chips. Subtitles and audio read what the engine actually has
 * selected, and episodes what is being watched; speed is still a fixture and
 * goes live with the engine call behind it.
 *
 * A film gets three chips rather than four. The fourth opens a list of episodes,
 * and a film has none. An inert chip reading its own title would be a control
 * that does nothing, which is worse than an absent one.
 */
private fun playerChips(
    state: PlayerState,
    controller: PlayerScreenController,
    context: PlaybackContext,
): List<PlayerChip> = listOfNotNull(
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
        value = formatRate(state.playbackRate),
        active = controller.rail == RailTab.Speed,
        onClick = { controller.openRail(RailTab.Speed) },
    ),
    context.episodeTag?.let { tag ->
        PlayerChip(
            kicker = "EPISODES",
            value = tag,
            active = controller.episodeDrawerOpen,
            onClick = controller::toggleEpisodeDrawer,
        )
    },
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
