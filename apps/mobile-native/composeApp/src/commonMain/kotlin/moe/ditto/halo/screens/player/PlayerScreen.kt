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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.ditto.halo.NativePlayerSurface
import moe.ditto.halo.PlaybackHost
import moe.ditto.halo.SignedInGraph
import moe.ditto.halo.api.VideoFitMode
import moe.ditto.halo.cache.QueryState
import moe.ditto.halo.player.MediaItem
import moe.ditto.halo.player.PlaybackStatus
import moe.ditto.halo.player.PlayerState
import moe.ditto.halo.player.PlayerSystemPort
import moe.ditto.halo.player.PlayerTracks
import moe.ditto.halo.ui.HaloColors

/** How far the bottom bar rises as it appears. */
private const val ChromeFadeMillis = 180
private const val BottomBarRiseMillis = 220
private const val RailEnterMillis = 220
private const val DrawerEnterMillis = 240
private const val SeekStepSeconds = 10.0

/**
 * How far the caption steps up while the chrome is showing. Enough to clear the
 * transport row, which is what lands on it.
 */
private const val ChromeCaptionLiftPercent = 12

/**
 * How long an appearance change has to settle before it is written. A drag
 * across the size slider is one decision, not forty.
 */
private const val SettingsWriteDelayMillis = 800L

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
    graph: SignedInGraph,
    playback: PlaybackHost,
    surface: NativePlayerSurface,
    /** See [moe.ditto.halo.PlatformDependencies.bundledSubtitleFonts]. */
    bundledSubtitleFonts: Set<String>,
    /** Brightness, volume, orientation and the sleep timer. */
    system: PlayerSystemPort,
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
    // Held across the frames of one drag; null between gestures, and null
    // during one the platform could not give a starting value for.
    var dragAdjustment by remember { mutableStateOf<VerticalDragAdjustment?>(null) }
    var dragTarget by remember { mutableStateOf<VerticalDragTarget?>(null) }

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

    // Stored appearance first, then the source: applying it afterwards would
    // show the first caption in the wrong size and correct it a frame later.
    // The write-back is debounced because the size slider emits continuously
    // and every settings write is a whole-document PUT.
    LaunchedEffect(item) {
        val settings = graph.settings.current()
        val stored = subtitleStyleOf(settings)
        playback.applySubtitleStyle(stored)
        playback.play(item)

        playback.setVideoFillsScreen(settings.videoFitMode == VideoFitMode.Cover)

        val storedPreference = SubtitlePreference(stored.scale, stored.font)
        playback.state
            .map { SubtitlePreference(it.subtitleScale, it.subtitleFont) }
            .distinctUntilChanged()
            .debounce(SettingsWriteDelayMillis)
            .collect { preference ->
                if (preference == storedPreference) return@collect
                graph.settings.update { it.withSubtitlePreference(preference) }
            }
    }

    // Fit mode is written back the moment it changes rather than debounced:
    // unlike the size slider it is one decision per gesture, not a stream.
    LaunchedEffect(item) {
        val stored = graph.settings.current().videoFitMode
        playback.state
            .map { it.videoFillsScreen }
            .distinctUntilChanged()
            .collect { fills ->
                val mode = if (fills) VideoFitMode.Cover else VideoFitMode.Contain
                if (mode == stored) return@collect
                graph.settings.update { it.withVideoFitMode(mode) }
            }
    }

    // Addon subtitles, matched to this exact file when the source can be
    // hashed. The hash comes from the addon's own hints when it supplied them,
    // and otherwise from two range requests over the source; either way it is
    // best effort, and a failure falls back to a name-based search rather than
    // to no subtitles.
    var fingerprint by remember(item) { mutableStateOf(context.fingerprint()) }
    LaunchedEffect(item) {
        if (fingerprint == null) fingerprint = graph.videoHasher.fingerprint(context.url)
    }
    val addonSubtitleState by remember(item, fingerprint) {
        graph.browse.subtitles(
            type = context.type,
            videoId = context.videoId,
            videoHash = fingerprint?.hash,
            videoSize = fingerprint?.sizeBytes,
            filename = context.filename,
        )
    }.collectAsState(QueryState())
    val addonSubtitles = remember(addonSubtitleState.value) {
        addonSubtitleOptions(addonSubtitleState.value.orEmpty())
    }

    // Applied once the engine has reported this file's own tracks, because the
    // choice is between those and the addon results and both have to be known
    // to pick between them. Re-running on a later track list would fight the
    // viewer, so the guard is a claim rather than a comparison.
    var selectionApplied by remember(item) { mutableStateOf(false) }
    LaunchedEffect(item, state.tracks, addonSubtitleState.isFetching) {
        if (selectionApplied || addonSubtitleState.isFetching) return@LaunchedEffect
        if (state.tracks.subtitles.isEmpty() && addonSubtitles.isEmpty()) return@LaunchedEffect

        val selection = resolveSubtitleSelection(
            remembered = graph.subtitleChoices.choiceFor(context.videoId, context.metaId),
            tracks = state.tracks,
            addonSubtitles = addonSubtitles,
            preferredLang = graph.settings.current().preferredSubtitleLang,
        )
        // Claimed after the reads, not before them: reading settings suspends,
        // and the engine reporting its tracks again in that window cancels this
        // effect. A claim taken first would be kept by the cancelled run and
        // the restart would decline to do the work.
        selectionApplied = true
        applySubtitleSelection(selection, playback, controller::selectAddonSubtitle)
    }

    // The bottom bar sits exactly where captions do, so the caption steps up
    // out of its way while the chrome is up and drops back when it goes.
    LaunchedEffect(controller.chromeVisible) {
        playback.setSubtitleLift(if (controller.chromeVisible) ChromeCaptionLiftPercent else 0)
    }

    // Landscape and a display that will not sleep, for as long as this screen
    // exists. Disposal rather than the back handler, because the error card's
    // exit and a system-initiated one leave the same way and would otherwise
    // strand the device in landscape with the screen pinned on.
    DisposableEffect(system) {
        system.lockLandscape()
        system.setKeepScreenOn(true)
        onDispose {
            system.restoreOrientation()
            system.setKeepScreenOn(false)
            // The window's brightness override belongs to the player, not to
            // the app: leaving it set would dim every other screen.
            system.clearScreenBrightnessOverride()
        }
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

        // The video is also the control surface. This sits below the chrome, so
        // a tap on a button reaches the button rather than the video.
        Box(
            Modifier
                .fillMaxSize()
                .playerGestures(
                    onTap = controller::toggleChrome,
                    onDoubleTapLeft = {
                        scope.launch { playback.seekTo(state.positionSeconds - SeekStepSeconds) }
                    },
                    onDoubleTapRight = {
                        scope.launch { playback.seekTo(state.positionSeconds + SeekStepSeconds) }
                    },
                    onDragStart = { target, heightPx ->
                        val baseline = when (target) {
                            VerticalDragTarget.Brightness -> system.screenBrightness()
                            VerticalDragTarget.Volume -> system.volume()
                        }
                        // A platform that will not report the current value has
                        // nothing to adjust from, and guessing one would make
                        // the first movement of the drag a jump.
                        dragAdjustment = baseline?.let { VerticalDragAdjustment(it, heightPx) }
                        dragTarget = target
                        if (baseline != null) controller.showHud(target.hudKind(), baseline)
                    },
                    onDrag = { totalDragPx ->
                        val value = dragAdjustment?.advance(totalDragPx) ?: return@playerGestures
                        val target = dragTarget ?: return@playerGestures
                        when (target) {
                            VerticalDragTarget.Brightness -> system.setScreenBrightness(value)
                            VerticalDragTarget.Volume -> system.setVolume(value)
                        }
                        controller.showHud(target.hudKind(), value)
                    },
                    onDragEnd = {
                        dragAdjustment = null
                        dragTarget = null
                    },
                    onFillScreenChange = { fills ->
                        scope.launch { playback.setVideoFillsScreen(fills) }
                    },
                ),
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
                onToggleFit = {
                    controller.showChrome()
                    scope.launch { playback.setVideoFillsScreen(!state.videoFillsScreen) }
                },
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
                // The cache's own reach. Before the engine reports one there is
                // no lead to draw, and the bar's own floor keeps the fill from
                // ever sitting behind the playhead after a backwards seek.
                bufferedFraction = progressFraction(
                    state.bufferedPositionSeconds ?: 0.0,
                    state.durationSeconds,
                ),
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
                trackStyling = state.subtitleTrackStyling,
                selectedAddonSubtitleId = controller.selectedAddonSubtitleId,
                bundledSubtitleFonts = bundledSubtitleFonts,
                audioDelaySeconds = state.audioDelaySeconds,
                playbackRate = state.playbackRate,
                onSelectTab = controller::openRail,
                onClose = controller::closeRail,
                addonSubtitles = addonSubtitles,
                addonSubtitlesFetching = addonSubtitleState.isFetching,
                onSelectSubtitleTrack = { id ->
                    controller.selectAddonSubtitle(null)
                    scope.launch { playback.selectSubtitleTrack(id) }
                    // Only a deliberate choice is remembered. Restoring one is
                    // not a new decision, and writing it back would let a
                    // preference reinforce itself into looking like one.
                    val track = state.tracks.subtitles.firstOrNull { it.id == id }
                    graph.subtitleChoices.remember(
                        videoId = context.videoId,
                        itemId = context.metaId,
                        choice = track?.let(::embeddedChoice) ?: OffChoice,
                    )
                },
                onSelectAddonSubtitle = { option ->
                    controller.selectAddonSubtitle(option.id)
                    scope.launch { playback.addSubtitle(option.url) }
                    graph.subtitleChoices.remember(
                        videoId = context.videoId,
                        itemId = context.metaId,
                        choice = externalChoice(option),
                    )
                },
                onSubtitleScaleChange = { scale -> scope.launch { playback.setSubtitleScale(scale) } },
                onSubtitleDelayChange = { seconds ->
                    scope.launch { playback.setSubtitleDelay(clampedDelay(seconds)) }
                },
                onTrackStylingChange = { keep -> scope.launch { playback.setSubtitleTrackStyling(keep) } },
                onSubtitleFontChange = { font -> scope.launch { playback.setSubtitleFont(font) } },
                onSelectAudioTrack = { id -> scope.launch { playback.selectAudioTrack(id) } },
                onAudioDelayChange = { seconds ->
                    scope.launch { playback.setAudioDelay(clampedDelay(seconds)) }
                },
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

        state.buffering?.let { buffering ->
            BufferingOverlay(
                percent = buffering.percent,
                throughput = formatThroughput(buffering.bytesPerSecond),
                cached = formatCachedAhead(buffering.cachedSeconds),
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
    return listOfNotNull(selected.language ?: selected.label, subtitleBadge(selected.codec))
        .distinct()
        .joinToString(" · ")
}

private fun audioChipValue(tracks: PlayerTracks): String {
    val selected = tracks.audio.firstOrNull { it.id == tracks.selectedAudioId }
        ?: tracks.audio.firstOrNull()
        ?: return "None"
    return listOfNotNull(selected.language ?: selected.label, selected.codec?.uppercase())
        .distinct()
        .joinToString(" · ")
}
