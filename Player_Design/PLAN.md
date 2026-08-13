# Halo native player — implementation plan (handoff doc)

## Context

`Player_Design/` holds an approved, high-fidelity design for the native player screen in
`apps/mobile-native` (Compose Multiplatform over libmpv). Its `README.md` is the spec: one
landscape screen with layered chrome (labelled state chips, one right-hand rail for
audio/subtitles/speed, episode drawer, buffering/locked/up-next/PiP/error states). The current
`PlayerScreen.kt` is a placeholder (surface + spinner + back button).

The work happens in two phases, per the user's direction:

1. **Wire the mockup** — recreate the whole design in Compose with fixture data; every state
   reachable and interactive on device, nothing depending on engine capabilities that don't exist yet.
2. **Implement one by one** — wire each control to the real engine and data, closing engine gaps
   slice by slice, each slice independently verifiable and committed.

This file is the handoff checklist: pick up at the first unchecked box.

## Decisions locked (user-confirmed 2026-08-13)

- **Android-first wiring.** Common UI is shared; new `PlayerPort` capabilities get real
  implementations in `MpvCore.kt`/`AndroidPlayerHost.kt`. The iOS Kotlin adapter
  (`IosPlayerHostAdapter`) gets safe no-ops; **`HaloIosPlayerHost` (Kotlin-exported protocol) and
  all Swift files stay untouched** so the Mac build never breaks. A "Mac session follow-ups" list
  at the end tracks the iOS half.
- **Real OS PiP and scrub-preview frame extraction are deferred** to optional end slices. The
  in-app PiP presentation and the timecode-only scrub card ship in Phase 1 (the design doc itself
  says to ship the card without frames).
- **Commit per slice** on `feat/native-rewrite`, conventional commits, never push.

Judgment calls made on the user's behalf (flag if wrong):

- **The mockup phase renders over the real video surface** via the existing `PlayerRoute`, plus a
  debug-only "Player design demo" scene-forcing entry from the diagnostics gate (mirrors the HTML
  prototype's STATE buttons) so every transient state is reviewable without a playing video.
- **No true backdrop blur over video.** Haze samples Compose content, not the SurfaceView/UIKitView
  the video paints into (the video layer is below Compose composition). Chrome over video uses the
  spec's translucent fills without blur; the rail/drawer use `glassSurface` where a Compose
  backdrop exists. This is a physical compositing limit, not a shortcut.
- **Volume gesture drives system media volume** (AudioManager), not mpv's `volume` property —
  matches every mobile player's convention. Brightness drives the window attribute, not system.
- **Watch-state reporting is included** (slice 2.10): the drawer's per-episode progress bars and
  resume behaviour read from it, so the design can't be honest without it.

## Key files (read before starting)

| File | Why |
| --- | --- |
| `Player_Design/README.md` | The spec. Every value, colour, behaviour. Authoritative. |
| `Player_Design/Halo Player - native.dc.html` | Working prototype (serve folder over HTTP to view). |
| `apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/screens/PlayerScreen.kt` | Current placeholder; the `leave` wind-down comment is load-bearing. |
| `.../player/PlayerContract.kt`, `PlayerPresenter.kt` | The engine boundary + state. |
| `.../PlaybackHost.kt` | App-wide single playback owner; `windDownForExit()` must stay on every exit path. |
| `.../androidMain/.../player/MpvCore.kt`, `AndroidPlayerHost.kt` | Android engine; where gaps close. |
| `.../iosMain/.../IosHostBridge.kt` | iOS adapter — no-op additions only, never touch `HaloIosPlayerHost`. |
| `.../ui/HaloTheme.kt`, `HaloIcons.kt`, `HaloControls.kt` (`Segmented`), `HaloGlass.kt`, `Responsive.kt` | Tokens/components the design reuses. |
| `.../shell/Routes.kt`, `HaloShell.kt` | `PlayerRoute` grows fields in slice 2.1. |
| `apps/mobile/app/player.tsx`, `src/components/PlayerGestureLayer.tsx`, `src/subtitleMemory.ts` | Old Expo player = behaviour reference (gestures, memory rules, autoplay race guard). |
| `.../storage/SubtitleChoiceStore.kt` | Subtitle memory already ported — reuse, don't rewrite. |
| `.../api/HaloClient.kt` | `getNextEpisode`, `getSubtitles(videoHash/videoSize)`, `getMeta`, `getStreams` all exist. |

## Verification commands (Windows, per slice)

There is no `java` on PATH on this machine and the Gradle wrapper refuses to start without one,
so every command below needs `JAVA_HOME` set first. JDK 17 (Gradle-provisioned, already present):

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
```

```powershell
# in apps/mobile-native
.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata   # common compiles
.\gradlew.bat :composeApp:compileKotlinIosSimulatorArm64    # iOS Kotlin still compiles (no Mac needed)
.\gradlew.bat :composeApp:testDebugUnitTest                 # commonTest suites
.\gradlew.bat :composeApp:assembleDebug                     # APK
# instrumented (device/emulator attached):
.\gradlew.bat :composeApp:connectedDebugAndroidTest
```

Manual playback verification: `pnpm --filter @halo/api dev:fixtures --media <file>` (repo root)
serves the real API on `:18790` (`admin`/`fixture-pass`) with every stream pointing at a local
video. Emulator reaches it at `http://10.0.2.2:18790`; a device uses the LAN IP.

---

## Phase 0 — Groundwork

- [x] **0.1 Commit this plan into the repo** at `Player_Design/PLAN.md` (checklist lives with the
  design bundle; keeps the doc available "anytime anywhere"). Also commit the `Player_Design/`
  bundle itself if the user confirms it should be tracked. **Done:** whole bundle tracked.
- [x] **0.2 Record the departure** from Direction A strict parity in
  `apps/mobile-native/design-demos/direction-approved.md` (the player is the one screen with no
  parity reference; note the approved redesign and its source bundle).
- [x] **0.3 Icons** — add to `HaloIcons.kt`, following its raw-Material-path-data convention
  (path strings verbatim from Material Icons source, no icon dependency): `Pause`,
  `PictureInPicture` (`picture_in_picture_alt`), `FitScreen`, `LockOpen`, `Lock`, `Brightness`
  (`brightness_5`), `VolumeUp`, `Replay10`, `Forward10`, `GridView`.
- [x] **0.4 Player colours** — add the player-only tokens to `HaloTheme.kt` (spec §Design tokens):
  scrim top/bottom, chrome glass .45/.55, rail fill, drawer fill, chip-active fill/border/label
  `#7EC0FF`, meta `#C7CDD9`, diagnostic `#6F7789`, ticks `#565E70`, switch-off `#424753`.
  Group them (e.g. `HaloPlayerColors`) rather than flooding `HaloColors`.
- [x] **0.5 JetBrains Mono** — bundle `JetBrainsMono-Regular.ttf` (already in repo at
  `apps/desktop/fonts/`, OFL) via Compose resources; add a mono `TextStyle` helper (timecodes,
  badges, percentages, engine messages; tabular figures). **Done:** the module had no Compose
  resource pipeline, so this set one up (`components-resources` dependency, `compose.resources`
  block, `src/commonMain/composeResources/`). Only the regular weight exists in the repo, so
  heavier weights synthesise. The font's runtime load is still unexercised: nothing renders
  `monoStyle` until slice 1.1.
- [x] Verify: metadata compile + `testDebugUnitTest` + APK still builds. Commit (one commit for
  0.2, one for 0.3–0.5 is fine). **Done 2026-08-13:** iOS Kotlin compiles too, 252 tests pass.

## Phase 1 — Wired mockup (fixture-driven, all commonMain)

New package `moe.ditto.halo.screens.player`. Everything below is pure Compose over a
`PlayerViewState` + callbacks — no engine calls beyond what already exists. Fixture content in a
`PlayerFixtures` object matching the prototype (show/episode names, tracks, badges, episodes).

State/controller (`PlayerViewState.kt` + `PlayerScreenController.kt`, commonTest-covered):
`chromeVisible` (+ re-armable 3 s auto-hide, suppressed while paused/scrubbing/rail/drawer open),
`rail: Audio|Subtitles|Speed|null`, `episodeDrawerOpen` (mutually exclusive with rail),
`scrubFraction: Float?`, `locked` (+ 3 s pill hide), `cachePercent`, `hudValue`,
`upNextSecondsRemaining` + one-shot advance guard, `subtitleTrackStyling`, `subtitleOutline`.

- [x] **1.1 Screen skeleton + chrome** — rewrite `PlayerScreen.kt` into the new package:
  full-bleed black, surface fills, z-order per spec table; `hPad` = safe-inset floored at
  24/40 dp via `WindowInsets.safeDrawing` + `rememberResponsive()` (`pick(phone=…, tablet=…)`
  for every tablet-scaled value). Scrims; top bar (back → **the existing `leave` wind-down
  lambda, unchanged**, title block, stream badges, 3-icon utility pill); centre −10/▶/+10;
  bottom bar (4 chips + `<remaining> left`, transport row, growing thumb, scrub-preview card
  timecode-only). Chips already read live `PlayerState` where it exists (position, duration,
  selected tracks); everything else fixture. Tap-to-toggle chrome + auto-hide via controller.
- [x] **1.2 Right rail** — slide-in panel + scrim, header, `Segmented` tabs (Audio/Subtitles/
  Speed), shared list-row anatomy, format badges, Subtitles tab (sections, Off row, four
  appearance cards: size slider 50–200 %, delay stepper ±5000 ms step 50, track-styling switch +
  font chips `Default/Inter/Serif/Mono`, preview strip), Audio tab (tracks + delay stepper),
  Speed tab (3-column grid 0.5–2× + note card). All fixture-driven; existing live setters
  (`setSubtitleScale`/`Delay`/`Font`, `selectAudioTrack`/`selectSubtitleTrack`) may be wired
  immediately since they already exist.
- [x] **1.3 Episode drawer** — bottom sheet, header, horizontal episode cards with progress bar,
  current-episode border, download ticks (fixture). Enter animation 240 ms per spec.
- [ ] **1.4 Transient states** — buffering ring + percentage pill; gesture HUD pill + side wash
  (visuals only, no gestures yet); locked scrim + auto-hiding unlock pill; up-next card with
  8 s countdown bar + one-shot advance guard; in-app PiP presentation (dim + floating window);
  error card (mpv message in mono, Retry + Pick another source).
- [ ] **1.5 Demo harness** — debug-only "Player design demo" entry from the diagnostics gate
  (`HaloApp.kt` `ShellScreen.Gate` area): renders the new screen with `PlayerFixtures` and scene
  buttons forcing Playing/Buffering/Rail/Drawer/Locked/Up next/PiP/Error. Absent from
  non-debuggable builds exactly like the gate itself.
- [ ] **1.6 Controller tests** (commonTest): auto-hide arming/suppression rules, rail/drawer
  mutual exclusion, lock pill timer, scrub state, up-next one-shot advance.
  **Partly done in 1.1**: `PlayerScreenControllerTest` covers auto-hide arming and all four
  suppression rules, rail/drawer exclusion, and scrub state; `PlayerFormatTest` covers
  timecodes. Tests are being written with the slice that introduces the behaviour rather than
  saved for the end, so what is left here is the lock pill timer and the up-next guard.

Notes from 1.1 worth carrying forward:

- The chrome adds the vertical safe insets to the design's 12/18dp padding, because the player
  is not immersive yet and the title otherwise sits under the status bar. When system bars are
  hidden the insets go to zero and the design values stand alone, so nothing has to be undone.
- The transport's thumb and played fill follow the finger during a drag while the elapsed
  readout stays on the engine. The prototype does the opposite because a mouse can hover a
  preview without moving anything; a finger cannot.
- `bufferedFraction` is passed in rather than read from a fixture inside the chrome, so 2.4
  only has to change the caller.

Notes from 1.2:

- The rail's fill is flattened onto the app background rather than left translucent. The design
  assumes a 30dp backdrop blur that cannot exist over video, and at 90% opacity with sharp text
  behind it the panel reads as a rendering bug. This is the fallback `HaloGlass` already
  documents. The same applies to the episode drawer in 1.3.
- Slider ticks are placed at their real positions, not spaced evenly: on a 50 to 200 scale the
  100 mark belongs a third of the way along, and an evenly spaced label sits where the thumb
  never is at that value.
- Rail state the engine cannot hold yet (playback rate, ASS override, audio delay, the addon
  subtitle selection) is grouped on the controller and named as such. Each moves to
  `PlayerState` with its engine call: 2.2, 2.5, 2.6 and 2.7 respectively.
- The engine's own track lists drive the in-file subtitle and audio rows, and the subtitle
  scale, delay and font setters are live. Format badges and the per-format styling hints stay
  generic until 2.3 reports each track's codec.
- Em dashes in the design's UI copy were replaced with commas and colons, per the standing
  writing preference. Meaning is unchanged; say the word if the design's punctuation should
  win instead.

Notes from 1.3:

- Missing artwork is drawn as diagonal stripes (`placeholderStripes`), shared by the episode
  thumbnails and the scrub preview. A blank rectangle is indistinguishable from an image that
  failed to load, which is the wrong signal during design review. Both call sites drop it when
  their real source arrives (2.10 for stills, D2 for frames).
- The scrub preview needs `requiredSize`, not `size`: it is a child of the 34dp-tall hit area
  for the transport track, so ordinary constraints clamped it to a third of its height. This
  was invisible until it was seen on a device.
- The "downloaded" marker is the download glyph, not a tick. A tick already means "watched"
  elsewhere in the app, and the rail was using it for the wrong meaning.
- Selecting an episode only closes the drawer for now. It cannot do more until a stream is
  resolved for that episode, which is 2.10.
- Verify each slice: compile + tests + APK on device; review states through the demo harness.
  Commit per slice (`feat(mobile-native): …`).

## Phase 2 — Wire for real, one slice at a time (Android engine, common UI)

Rule for every engine gap: extend `PlayerPort` (+ default no-op where sensible) → implement in
`MpvCore`/`AndroidMpvPlayerHost`/`AndroidPlayerPort` → map in `IosPlayerHostAdapter` as a no-op
(**do not touch `HaloIosPlayerHost`**) → echo state in `PlayerState`/`PlayerPresenter` → wire UI →
presenter/controller tests.

- [ ] **2.1 Playback context through the route** — grow `PlayerRoute` (`Routes.kt`) with
  `type`, `metaId`, `videoId`, `showTitle`, `episodeTag`/`episodeName` (nullable for films),
  `addonId`, `bingeGroup`, `filename`, `videoSize`, `videoHash` (from `StreamBehaviorHints`),
  and the raw stream `name/title` for badge parsing. `StreamsScreen.onPlay` and `HaloShell`
  pass them; `DetailScreen`/`StreamsRoute` already carry type/videoId/title. Top-bar title block
  and EPISODES chip go live. Badge parser (resolution/codec only, **never provider or account
  names**) in commonMain with unit tests.
- [ ] **2.2 Playback speed** — `PlayerPort.setPlaybackRate(rate)` → mpv `speed` property;
  `PlayerState.playbackRate` echo. (The design README claims speed "already exists" — it does
  not; this is a real gap.) Speed tab + SPEED chip go live.
- [ ] **2.3 Track format metadata** — extend `PlayerTrack` with `codec`, `channels`,
  `sampleRateHz` (nullable). `MpvCore.emitTracks` reads `track-list/N/codec`,
  `demux-channel-count`, `demux-samplerate`. `PlayerTracksJson` gains the same fields with
  defaults (old Swift JSON keeps decoding — no Swift change). Unlocks: format badges
  (ASS/SRT/PGS/audio codec), PGS-conditional appearance behaviour, AUDIO/SUBTITLES chip values.
- [ ] **2.4 Buffering readout** — observe `cache-buffering-state` + `paused-for-cache`; read
  `cache-speed` + `demuxer-cache-duration` while buffering. New
  `PlayerEvent.BufferingChanged(active, percent, bytesPerSecond, cachedSeconds)`;
  presenter state; buffering overlay goes live. Also observe `demuxer-cache-time` for the
  transport bar's buffered fill.
- [ ] **2.5 Subtitle styling engine knobs** —
  `setSubtitleTrackStyling(keepScript: Boolean)` → `sub-ass-override` `no`/`force`;
  `setSubtitleOutline(outline)` + shadow → `sub-border-size`/`sub-shadow-offset` (reuse the
  `SubtitleOutline` enum from `ApiDtos.kt`); caption lift when chrome shows → `sub-pos`/margin.
  Font chips: bundle Inter/Source Serif 4/JetBrains Mono for libass (`sub-fonts-dir` — confirm
  the option lands on Android; if not, document the limit honestly rather than faking it).
  Persist via `SettingsRepository` (`subtitleScalePercent`, `subtitleFontFamily`,
  `subtitleOutline`, `subtitleShadow` — all already in `UserSettings`), and apply persisted
  values at load.
- [ ] **2.6 Audio delay** — `setAudioDelay(seconds)` → mpv `audio-delay`; Audio-tab stepper live.
- [ ] **2.7 External subtitles + memory** — fetch `getSubtitles(type, videoId, videoHash,
  videoSize, filename)` at playback start (hash/size from behaviorHints when present; a native
  OpenSubtitles hash via Ktor range requests is a sub-item, port of `packages/core`'s logic);
  FROM ADDONS section live; selection → `addSubtitle(url)` (proxied via `proxyUrl` when needed);
  explicit choices → `SubtitleChoiceStore` (exists); restore per video→item on start;
  preferred-language default never writes memory.
- [ ] **2.8 Gestures + system services** — new commonMain `PlayerSystemPort` (not `PlayerPort`:
  none of this is the media engine): window brightness get/set, system media volume get/set/steps,
  landscape lock/unlock, keep-screen-on. Android impl (Activity window attrs, `AudioManager`,
  `requestedOrientation` SENSOR_LANDSCAPE, `FLAG_KEEP_SCREEN_ON`); iOS no-op for now; inject via
  `PlatformDependencies`. Then the gesture layer per spec + old `PlayerGestureLayer.tsx` rules:
  single tap toggles chrome (280 ms double-tap window), double-tap seeks ∓10 s, vertical drag
  left/right = brightness/volume (baseline at gesture start, 70 % height travel, one call per
  whole percent), pinch → fit mode at 12 % threshold, pinch-commit swallows single-touch.
  Landscape lock applies on player entry, restores on exit (both exit paths: back + error).
- [ ] **2.9 Fit mode** — contain/cover via mpv `panscan` 0.0/1.0; persisted in `KeyValueStore`;
  utility-pill button + pinch both drive it.
- [ ] **2.10 Watch-state reporting + episode drawer data** — report position through
  `WatchStateRepository` (on pause, on exit, periodic ~30 s); drawer reads season episodes from
  `getMeta` via the existing browse/query cache, progress bars from watch state, current-episode
  highlight from route context. Episode select: fetch that episode's streams, prefer same
  `bingeGroup` + addon, else navigate to the stream picker (replace, like `StreamsScreen` does).
- [ ] **2.11 Up next** — at playback start call `getNextEpisode(type, metaId, videoId, addonId,
  bingeGroup)`; hold next episode + matched stream. On `NaturalEnd` show the card (video keeps
  its last frame behind it), 8 s countdown; countdown/`Play now`/`Cancel` funnel through the
  one-shot guard; advance = `playback.play(next)` + route-context update. Presenter: stop
  auto-advancing via `queuedNext` on NaturalEnd when the screen owns the countdown — adjust
  `advanceOrEnd` + `PlayerPresenterTest` accordingly.
- [ ] **2.12 Error card wiring** — mpv's message verbatim (no HTTP codes/hosts added);
  `Retry` = reload same item; `Pick another source` = navigate to `StreamsRoute` (replace).
- [ ] **2.13 Instrumented tests** — extend the Android instrumented suite (pattern:
  `PlayerOwnershipInstrumentedTest`): chrome auto-hides, rail applies without reload
  (track switch keeps position), back winds down. Mirror XCUITest cases belong to the Mac list.

## Deferred (optional, end of plan)

- [ ] **D1 Real Android PiP** — `PictureInPictureParams`, activity plumbing, the in-app
  presentation switches to the OS handoff.
- [ ] **D2 Scrub-preview frames** — mpv frame extraction or sprite sheets; card already ships
  timecode-only.

## Mac session follow-ups (iOS half — tracked, not done here)

- Extend `HaloIosPlayerHost` + `MPVCore.swift`/`MPVPlayerHost.swift` for: speed, track codec
  fields in the tracks JSON, buffering events, `sub-ass-override`/outline/shadow, audio delay,
  fit mode, `PlayerSystemPort` (brightness/volume/orientation/idle-timer), then replace the
  Kotlin no-ops in `IosHostBridge.kt`. Real iOS PiP (`AVPictureInPictureController`). Run the
  ownership/playback XCUITest suites; add the three new player cases from 2.13.

## Risks / honest limits

- **No blur over video** (compositing limit) — glass fills only; noted above.
- **`sub-fonts-dir`/custom fonts for libass on Android** may not work with the prebuilt AAR;
  verify early in 2.5, and if it fails, font chips stay but get an honest disabled state.
- **`dev.jdtech.mpv` prebuilt is emulator-grade** per the module README; anything
  timing-sensitive should be sanity-checked on a real Android device.
- **Route growth (2.1)** makes `PlayerRoute` wide; acceptable because the shipping client does
  the same (URL + context resolved before entry), and serializable routes are the module's
  navigation contract.
