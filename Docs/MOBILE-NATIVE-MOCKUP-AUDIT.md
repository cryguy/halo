# Halo mobile-native mockup and implementation audit

Audit date: 2026-08-15

## Scope

This audit covers the Kotlin Multiplatform application in `apps/mobile-native`, including its
Android and iOS platform hosts. It does not cover the desktop client, the older Expo mobile
client, or standalone HTML design prototypes.

The findings come from tracing the shipping navigation, data repositories, platform dependency
assembly, player controls, fixture references, and empty callbacks in source. Android behavior
has been exercised on an emulator during the current implementation work. iOS Kotlin sources
have been compile-checked on Windows, but the Swift host, simulator behavior, and XCUITest flows
still require a Mac for runtime proof.

## Executive finding

The normal Android application is not a mockup. Home, search, library, details, source selection,
playback, downloads, and settings are connected to real repositories, HTTP requests, storage,
and native mpv playback.

The two material gaps are on iOS:

1. Normal iOS authentication selects a fake host by default.
2. iOS Picture in Picture is represented by an in-app placeholder instead of native system PiP.

The only fixture content visible in the normal player is the example sentence in the subtitle
appearance preview. The full player mockup and the fixture-media player shell are debug-only
tools that are absent from release navigation.

## Status summary

| Area | Status | User impact |
| --- | --- | --- |
| Android product screens | Real | Normal application flows use live repositories and platform services. |
| Android authentication | Real | Uses the Android OIDC host and real auth configuration requests. |
| Android playback | Real | Uses libmpv, real tracks, seeking, speed, fit mode, subtitles, and native PiP where supported. |
| iOS authentication | Fake by default | Repository-defined normal launches use canned discovery and cannot complete OIDC. |
| iOS playback | Implemented, runtime-unverified | Swift and Kotlin contain real libmpv plumbing, but it has not been run on a Mac in this work. |
| iOS Picture in Picture | Placeholder | The button always falls back to the in-app striped representation. |
| Subtitle appearance preview | Sample content | The sentence is illustrative, while the controls it previews are real. |
| Downloads | Real, foreground-only | Transfers resume, persist, and play offline, but do not continue after process death. |
| Player design scenes | Pure debug mockup | Uses fixture state and no-op actions to display every design scene. |
| Debug gate and player shell | Engineering harnesses | They inspect real hosts and drive the real engine with controlled fixture media. |

## Shipping defects

### 1. iOS authentication defaults to a fake host

`apps/mobile-native/iosApp/Halo/AppDelegate.swift:10-17` constructs `FakeAuthHost` unless the
process environment contains `HALO_AUTH_HOST=oidc`.

`apps/mobile-native/iosApp/Halo/FakeNativeHosts.swift:19-47` confirms that this host:

- returns canned auth configuration;
- records an OIDC request without opening a browser;
- never restores or returns a session token;
- never completes an OIDC flow.

Within the repository, `HALO_AUTH_HOST=oidc` is supplied only by UI tests. There is no release
configuration or normal launch configuration that selects `OidcAuthHost`. Therefore the
repository-defined iOS application does not have production-real authentication by default.

This is a shipping blocker, not merely missing runtime verification. The safe correction is to
make `OidcAuthHost` the normal host and require an explicit test-only launch setting for
`FakeAuthHost`.

### 2. iOS Picture in Picture is not native

`PlayerSystemPort.enterPictureInPicture()` defaults to `false` in
`apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/player/PlayerSystemPort.kt:52-65`.

`IosPlayerSystemPort` in
`apps/mobile-native/composeApp/src/iosMain/kotlin/moe/ditto/halo/IosPlayerSystemBridge.kt:30-64`
does not override that method or expose PiP state. Consequently, the player follows the fallback
path in `PlayerScreen.kt:515-519`.

That fallback draws `PictureInPictureOverlay` from `PlayerOverlays.kt:422-466`. Its small striped
box is not live video and it does not leave Halo as an operating-system PiP window. On iOS this
happens every time. On Android it happens only if the operating system rejects PiP or the device
does not support it. Supported Android devices use the real `PictureInPictureParams` path in
`AndroidPlayerSystemPort.kt:91-120`.

## Debug-only mockups and harnesses

### Player design scenes

`PlayerScenePreviewScreen` is a pure visual mockup. It deliberately renders over placeholder art,
reads `PlayerFixtures`, supplies canned buffering and failure states, and leaves engine actions
such as seeking, retrying, and track selection as no-ops. Evidence is in
`apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/screens/player/PlayerScenePreview.kt:63-370`.

This screen is not part of the release user flow. `HaloApp.kt:212-220` supplies the Settings debug
entry only when `diagnosticsEnabled` is true. Android derives that value from the manifest's
debuggable flag, and iOS derives it from whether the Kotlin framework is a debug binary.

### Debug gate

The Debug gate is an engineering diagnostics screen, not a product screen. It exposes host IDs,
creation counters, authentication state, token-fetch testing, and links to the two player tools.
Its controls operate the real host boundaries. See `HaloApp.kt:455-549`.

### Player shell

The Player shell is also an engineering harness rather than a mock player. It loads controlled
ASS and bitmap-subtitle fixture media, but its pause, seek, track, subtitle, resize, teardown, and
core-recreation actions call the real `PlayerPresenter` and native player host. See
`HaloApp.kt:552-858`.

## Intentional sample or fallback visuals

### Subtitle appearance preview

The preview card in `PlayerRailTabs.kt:261-283` displays
`PlayerFixtures.CaptionSample`. This is intentional illustrative text because a stable sentence
makes size and font changes comparable. The actual size, font, delay, track styling, outline,
shadow, and lift setters call the running media engine.

### Scrub-preview placeholder

Scrub thumbnails are real, best-effort frame extraction:

- Android uses `MediaMetadataRetriever` in `AndroidVideoFrameSource.kt`.
- iOS uses `AVAssetImageGenerator` in `IosVideoFrameSource.kt`.

Platform extractors support fewer containers and codecs than mpv. When an extractor cannot open a
source, the scrub card retains its striped timecode placeholder. This is an honest unavailable
state, not invented media data.

### Loading skeletons

Loading skeletons and empty-state artwork are temporary or fallback presentation. Catalog rows,
posters, source results, and library content still come from live repository state.

## Real features with incomplete platform guarantees

### iOS subtitle fonts

`PlatformDependencies.bundledSubtitleFonts` defaults to an empty set in
`PlatformDependencies.kt:64-73`, and `MainViewController.kt:47-73` does not supply an iOS font
bundle. iOS still sends the selected family to mpv, so the control is not a no-op, but the chosen
font is not guaranteed to exist and mpv may substitute another typeface. The UI reports this
honestly through `unbundledFontNotice` in `PlayerRailTabs.kt:332-345`.

Android is complete here. `SubtitleFontLibrary.kt` extracts Inter, Source Serif 4, and JetBrains
Mono into a directory mpv can scan, and `MainActivity.kt:81` reports those bundled families to the
common UI.

### Downloads do not survive process death

Downloads are real ranged HTTP transfers with disk persistence, space checks, pause, resume,
removal, subtitle attachment, and offline playback. `SignedInGraph.kt:90-104` constructs the real
coordinator and transfer implementation, and `StreamsScreen.kt:123-170` connects source-row
download actions to it.

The transfer itself is process-owned. `DownloadsCoordinator.kt:52-57` explicitly records that a
running transfer does not continue after the app is killed. On the next launch it returns as
paused so the user can resume it. This matches the older Expo application's limitation, but a
future background-transfer implementation would be needed for operating-system-managed downloads.

### Apple behavior remains runtime-unverified

The iOS source includes real implementations for libmpv playback, Metal surface ownership,
seeking, tracks, subtitle controls, downloads, brightness, volume, orientation, system-bar
handling, and scrub frames. These are implementations, not mock interfaces. However, their actual
Swift compilation, lifecycle behavior, and device behavior have not been observed during this
Windows-based work.

This distinction matters: iOS authentication and PiP are known source defects, while the other
iOS features are implemented but still need Mac runtime evidence.

## Confirmed real product flow

`apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/shell/HaloShell.kt:130-236`
wires the normal user navigation as follows:

- Home and Library read the signed-in graph and open real details.
- Search queries the real browse repository.
- Details resolve movies and episodes into the Sources screen.
- Sources load addon results, retain per-addon errors, support manual retry, start downloads, and
  pass a selected direct URL into the player.
- Player uses the platform render surface, real engine state, platform system controls, downloaded
  subtitles, episode resolution, and watch-progress reporting.
- Downloads reads the device index and opens completed local files through the normal player.
- Settings reads and mutates server settings, addon manifests, language choices, autoplay, and
  account state.

`SignedInGraph.kt:72-104` backs these screens with `HaloClient`, `QueryCache`, browse, account,
library, watch-state and settings repositories, the subtitle cache, and the downloads coordinator.
Fixture catalogs are not injected into this graph.

The fixture-data search found `PlayerFixtures` consumers only in `PlayerScenePreview` and the one
subtitle preview sentence. No fixture objects feed Home, Search, Library, Detail, Sources,
Downloads, Settings, or the normal player state.

## Empty-callback audit

No user-facing no-op buttons were found outside the debug-only scene preview.

The two empty callbacks in production player components are intentional tap barriers:

- `PlayerEpisodeDrawer.kt:77-83` stops an unused area inside the drawer from toggling the video
  chrome underneath it.
- `PlayerRail.kt:142-147` stops a tap inside the options rail from reaching its dismiss scrim.

Neither callback represents a visible control.

## Misleading comments found during the audit

Two comments in `PlayerScreen.kt` are stale:

- `PlayerScreen.kt:521-525` says fit mode is waiting for an mpv call, but the callback invokes
  `playback.setVideoFillsScreen` and both native engines implement it through mpv panscan.
- `PlayerScreen.kt:797-800` calls speed a fixture, but the chip reads `state.playbackRate` and the
  speed rail calls the real engine setter.

The behavior is real. Only the comments are wrong. They were not changed as part of this audit.

## Recommended completion order

1. Replace fake-default iOS authentication with real-default authentication. This is the only
   current blocker to a normal iOS sign-in flow.
2. Implement native iOS PiP and report its state through `PlayerSystemPort`.
3. Package the selectable subtitle fonts for the iOS mpv renderer, then pass the corresponding
   family set through `PlatformDependencies`.
4. Run the iOS build and focused auth, playback, lifecycle, download, scrub-preview, and system
   control tests on a Mac.
5. Treat background downloads as a separate reliability enhancement, not as mockup removal.

## Audit decisions and uncertainty

- "Our app" was interpreted as `apps/mobile-native`, covering Android and iOS but not desktop or
  web clients.
- Debug engineering tools were classified separately from product features because a controlled
  fixture harness can operate real code without being a shipping user flow.
- Unsupported-media placeholders were classified as honest fallbacks, not mock data.
- No source was changed while producing the underlying audit. The only unverified area in these
  conclusions is Apple runtime behavior that requires a Mac.
