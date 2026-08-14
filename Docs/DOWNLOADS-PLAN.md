# Halo native downloads — implementation plan (handoff doc)

## Context

Device-local downloads for offline watching are the feature Stremio lacks and the reason Halo
exists. The Expo client (`apps/mobile`) shipped them; `apps/mobile-native` does not have them yet
and its Downloads tab is an honest placeholder. This plan builds the engine for the native client,
Android-first.

The behaviour reference is `apps/mobile/src/downloads.ts` plus `apps/mobile/app/(tabs)/downloads.tsx`
and the download action in `apps/mobile/app/streams/[type]/[videoId].tsx`. What that client does,
this one must keep doing:

- One entry per videoId, grouped by itemId in the UI.
- The chosen subtitle is downloaded alongside the video, in its original format.
- In-flight transfers do not survive app kill; entries are re-marked paused on cold start.

The visual target is the approved Direction A prototype
(`apps/mobile-native/design-demos/Halo Android Direction A - Strict Parity.html`): a downloads
screen of per-show sections over episode rows, and a right-hand download column on every source
row. Downloads is not a departure screen; strict parity binds it.

## Decisions locked (user-confirmed 2026-08-15)

- **The transfer engine is shared Kotlin, not a platform port.** Downloading is an HTTP range
  request written to a file, and both halves already exist in commonMain (Ktor, as
  `SubtitleFileCache` uses it, and okio over the existing `subtitleFileSystem()` expect/actual).
  A `DownloadPort` would mean writing the engine twice and shipping an iOS no-op, and the
  in-process behaviour it would replace is exactly the behaviour this slice is asked to keep.
  Only "where do files live and how much room is left" is platform-owned, behind
  `DownloadStoragePort`.
- **One transfer at a time; the rest are queued.** Debrid and CDN hosts throttle per connection, so
  parallel transfers usually finish later than sequential ones and stall more on a mobile link.
  This departs from the old client, which started every download immediately.
- **iOS gets a real Kotlin storage port**, supplied from `MainViewController.kt`. No Swift file
  changes, so the Mac build cannot break. It is compile-verified only; nothing here has been run on
  an iOS device.
- **Commit per slice** on `feat/native-rewrite`, conventional commits, never push.

Judgment calls made on the user's behalf (flag if wrong):

- **Downloaded files live in `filesDir`/`Documents`, not the cache directories** that images and
  subtitles use. A subtitle is re-fetchable; a movie the viewer deliberately kept is not, and the
  system reclaiming it under storage pressure would be data loss. On iOS the directory is marked
  excluded from backup, or every download would be copied into iCloud.
- **Paths are persisted relative to the downloads directory, never absolute.** The old client stored
  absolute URIs and needed re-anchoring logic because iOS rotates the app container UUID on every
  reinstall. Storing a file name removes the failure mode instead of working around it.
- **Resume sends `If-Range` alongside `Range`.** If the source changed or the link was re-minted the
  server answers 200 rather than 206, and the transfer restarts from zero instead of splicing two
  different files into one corrupt one. The old client had no protection against that.
- **The coordinator lives in `SignedInGraph`**, like every other repository, so signing out cancels
  transfers. A different user must not inherit the previous one's. The index is device-local and
  survives.

## Storage model

One JSON document in the existing `KeyValueStore` under `StorageKeys.Downloads`
(`halo.downloads.v1`), decoded per entry so one unreadable record from another build cannot discard
the rest — the same rule `SubtitleChoiceStore` follows. No SQLite: a few hundred entries, and the
only query is "all of them".

`DownloadEntry` carries the video's identity (`videoId`, `itemId`, `type`, `metaId`), its naming
(`showTitle`, `episodeTag`, `episodeName`, `episodeThumbnail`, `poster`), the source and enough
context to re-enter the real player offline (`sourceUrl`, `addonId`, `bingeGroup`, `filename`,
`videoSize`, `videoHash`, `streamName`, `streamTitle`), the relative `fileName` and optional
`subtitleFileName`/`subtitleLang`/`subtitleSubId`, and the transfer's own state (`status`,
`totalBytes`, `downloadedBytes`, `resumeValidator`, `failureMessage`, `createdAt`, `updatedAt`).

File naming: sanitised video id, length-capped, plus a short hash of the raw id, because
sanitisation is lossy and two ids could otherwise collide, plus an extension from a whitelist. The
URL is never trusted for a path component. Transfers write `<name>.part` and become visible through
an atomic move, the contract `SubtitleFileCache` already uses.

## Port surface

```kotlin
interface DownloadStoragePort {
    fun directory(): String?   // created and backup-excluded, or null where the platform has none
    fun freeBytes(): Long?     // null means "cannot say", which means proceed
}
object NoDownloadStorage : DownloadStoragePort
```

Injected through `PlatformDependencies`. A null directory is an answer the Downloads screen renders
honestly rather than a crash or a button that does nothing.

Everything else is commonMain, in `moe.ditto.halo.downloads`:

| File | Responsibility |
| --- | --- |
| `DownloadEntry.kt` | Model, status enum, derived progress helpers |
| `DownloadIndex.kt` | KeyValueStore persistence, per-entry lenient decode |
| `DownloadPaths.kt` | Naming, extension whitelist, relative/absolute join, orphan sweep |
| `DownloadTransfer.kt` | `DownloadTransfer` interface + `HttpRangeTransfer` over Ktor and okio |
| `DownloadsCoordinator.kt` | Queue, state flow, start/pause/resume/remove, cold-start reconcile |
| `DownloadSubtitles.kt` | Preferred-language pick and the alongside fetch |

The interface is the seam for the background-transfer follow-up: WorkManager or URLSession replaces
`HttpRangeTransfer` and nothing else.

## Verification commands (Windows, per slice)

There is no `java` on PATH on this machine, so every command needs `JAVA_HOME` set first.

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
```

```powershell
# in apps/mobile-native
.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata
.\gradlew.bat :composeApp:compileKotlinIosSimulatorArm64
.\gradlew.bat :composeApp:testDebugUnitTest
.\gradlew.bat :composeApp:assembleDebug
# instrumented (device/emulator attached):
.\gradlew.bat :composeApp:connectedDebugAndroidTest
```

Manual verification needs a disposable backend: run `apps/api`'s `dev/server.ts` with tsx
(`node_modules\.bin\tsx.CMD`) and `--media <video file>`, which serves the real API on `:18790`
with every canned stream pointing at one local video over byte ranges. An emulator reaches it at
`http://10.0.2.2:18790`.

---

## Slice 1 — engine, index, storage port (no UI)

- [ ] `DownloadStoragePort` + `NoDownloadStorage`; `AndroidDownloadStorage` (filesDir) and
  `IosDownloadStorage` (Documents, backup-excluded); wired through `PlatformDependencies`,
  `MainActivity` and `MainViewController`.
- [ ] `DownloadEntry`, `DownloadStatus`, `StorageKeys.Downloads`, `DownloadIndex`.
- [ ] `DownloadPaths`: naming, whitelist, join, orphan sweep.
- [ ] `DownloadTransfer` + `HttpRangeTransfer`: `Range` + `If-Range` resume, `.part` file, atomic
  move, throttled progress, stall watchdog.
- [ ] `DownloadsCoordinator` in `SignedInGraph`: queue of one, single writer, state flow,
  cold-start `Downloading → Paused` reconcile.
- [ ] commonTest over okio `FakeFileSystem` and Ktor `MockEngine`: resume from partial, server
  ignoring `Range`, changed validator, pause mid-transfer, queue ordering, cold-start reconcile,
  index round-trip, corrupt-entry tolerance, sweep leaving referenced files alone.

## Slice 2 — the Downloads tab

- [ ] `Trash` and `Refresh` in `HaloIcons`, raw Material path data as the file's convention requires.
- [ ] Real `DownloadsScreen`: sections per `itemId` with poster, name and summary; rows with episode
  label, status line and progress; pause, resume, retry, delete; delete behind a confirmation;
  empty state kept; unavailable state when the port has no directory.
- [ ] Grouping, sorting, summary and status-label logic as pure functions, commonTest-covered.

## Slice 3 — starting a download

- [ ] Download column on every source row, per the prototype: accent, a check when the video is
  already downloaded, disabled while queued or in flight.
- [ ] `poster` added to `StreamsRoute` so a section header has art without a network.
- [ ] Free-space preflight against the declared `videoSize` when both numbers are known.
- [ ] The subtitle alongside: `StreamVideoHasher` for hash and size, `getSubtitles`, pick by the
  account's preferred language, fetch through the authenticated proxy into the downloads
  directory. Failures are silent, as before.

## Slice 4 — offline playback

- [ ] A finished row enters the real player with a `file://` URL and the stored context, so badges,
  the episode drawer and the next-episode lookup all behave.
- [ ] The downloaded subtitle is offered and remembered as `SubtitleChoiceKind.Downloaded`, which
  already exists in `SubtitleChoiceStore` and is currently unreachable.
- [ ] The episode drawer's download ticks stop being fixtures and read real entries.
- [ ] Instrumented test on a device: download a fixture-server video, drop the network, play it with
  its subtitle.

## Risks / honest limits

- **In-flight transfers do not survive app kill**, and entries reconcile to paused on cold start.
  Same as the old client, by design for this work. True background transfer (WorkManager on
  Android, URLSession on iOS) is the follow-up, and is a swap of `DownloadTransfer` only.
- **iOS is compile-verified only.** No download has been run on an iOS device.
- **Progress reported while offline is not persisted.** `QueryCache.mutate` keeps the optimistic
  value in memory when the server is unreachable, but nothing writes watch state to disk, so
  position gained offline is lost on restart. Pre-existing, not introduced here.
- **A stream URL can expire.** A resumed transfer against a dead link fails and the entry says so;
  re-resolving a source automatically is not attempted, because only the picker knows which source
  the viewer chose.
