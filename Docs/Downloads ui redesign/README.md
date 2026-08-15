# Handoff: Halo Downloads screen (native)

## Overview

A redesign of the Downloads tab in `apps/mobile-native`
(`composeApp/src/commonMain/kotlin/moe/ditto/halo/screens/DownloadsScreen.kt`).

Today the screen is a grouped list: one card per title, one row per video, a 4dp progress
bar and a status line assembled by `downloadStatusLabel()`. It answers "what is on the
device" well and "how is this transfer going, and can I watch it yet" poorly.

The redesign adds two things and reorganises around them:

1. **Transfer speed as a first-class readout** — an aggregate throughput figure with a
   60-second history chart, and per-item MB/s, bytes moved and time remaining.
2. **Direct playback from this screen** — every finished item has a Play button on the row;
   an in-flight item becomes playable once enough of it is contiguous on disk, with an
   explicit "Play now — N% buffered" affordance.

Everything else on the screen — storage accounting, pause/resume, delete, licence expiry —
is arranged around those two.

Phone and tablet are both specified here. Tablet landscape gains a right-hand detail pane;
tablet portrait and phone use the single-column layout.

## About the design files

`Halo Downloads - native.dc.html` is a **design reference written as HTML**, not production
code. It is a working prototype: the device switcher across the top re-lays the same screen
at five sizes, the state switcher moves it between transfers-running, waiting-for-Wi-Fi and
empty-queue, and the transfers actually tick so the speed readout and chart can be judged in
motion.

`support.js` is the runtime the HTML file loads. To open the prototype, serve this folder
over HTTP (`python3 -m http.server`) and visit the HTML file; it needs `support.js` as a
sibling.

**The task is to recreate this design in Compose Multiplatform**, using that module's
existing components, theme objects and idioms — not to port HTML, CSS or JavaScript. Every
colour, radius and spacing value in the prototype already exists as a Kotlin constant in
`HaloTheme.kt`; use the constant, never the hex string.

The prototype's chrome is not part of the design: the outer page (heading, device pills,
state pills, the metric strip along the bottom) exists only to present it. Build what is
inside the device frame.

## Fidelity

**High fidelity on layout, type, colour, motion and copy**, with these deliberate
placeholders:

1. **Posters and stills** are striped boxes labelled `poster`. Real art comes from
   `HaloAsyncImage` with `DownloadMedia.poster` / `episodeThumbnail`, exactly as the current
   screen loads them.
2. **Icons** are prototype stand-ins in Material 24dp style. Use `HaloIcons` — `Play`,
   `Pause`, `Trash`, `Refresh`, `Download`, `Settings`, `Home`, `Bookmark` are already there
   or already used by the current screen. Nothing new is required.
3. **Content** (show names, episode titles, sizes, licence expiry lines) is fixture data.

## What is already in place

Read these before starting; the design is built to fit them.

| File | What it gives you |
| --- | --- |
| `downloads/DownloadEntry.kt` | `status`, `totalBytes`, `downloadedBytes`, `fraction`, `secondsRemaining`, `bytesPerSecond` (transient), `media` |
| `downloads/TransferRate.kt` | Smoothed bytes/sec, already fed into `DownloadEntry.bytesPerSecond` |
| `downloads/DownloadsCoordinator.kt` | `entries: StateFlow<List<DownloadEntry>>`, `isAvailable`, `start`, `pause`, `resume`, `remove`, `playbackFiles` |
| `downloads/DownloadStoragePort.kt` | `directory()`, `freeBytes()` — the storage meter's data source |
| `screens/DownloadsGrouping.kt` | `groupDownloads`, `downloadRowTitle`, `downloadStatusLabel`, `formatRemaining`, `downloadsSummary` |
| `ui/Format.kt` | `formatBytes`, `formatSpeed` — use these verbatim for every figure on screen |
| `ui/HaloTheme.kt` | `HaloColors`, `HaloSpacing`, `HaloRadius`, `HaloType`, `monoStyle` |
| `ui/Responsive.kt` | `rememberResponsive()` — drives the phone/tablet split |

### Three gaps this design opens

Flag these before building; two of them are engine work, not UI work.

1. **The coordinator runs one transfer at a time.** The prototype shows three items moving
   at once purely to make the design legible. Do **not** change the concurrency model to
   match the picture. Render `DownloadStatus.Queued` items in the same In progress list with
   their bar at whatever they have, speed suppressed, and the status word `Queued` where the
   MB/s figure sits. Only the `Downloading` entry shows a live rate. The aggregate figure at
   the top is then simply that one transfer's rate — which is correct, and still worth
   showing, because the chart is what tells you whether the link is steady.

2. **Partial playback does not exist yet.** The transfer writes `<fileName>.part` and only a
   `Done` entry is playable. "Play now" requires the coordinator to expose a contiguous-bytes
   figure and `playbackFiles()` to hand back the `.part` path. See *Playing an unfinished
   download* below. If that engine work is not in scope for this pass, **ship the design
   without the Play-now button** — every other element stands on its own — and leave the rest
   of the layout unchanged.

3. **Licence expiry is not modelled.** `DownloadEntry` has no expiry field. If Halo has no
   time-limited downloads, drop the expiry line and the "Renew licence" button entirely, and
   let the row's third line carry quality and size only. Do not invent an expiry.

## Layout

### Phone (single column, 390–430dp)

Vertical order inside the tab's scroll:

1. **Sticky header** — `Downloads` in `HaloType.LargeTitle`, a summary line under it
   (`downloadsSummary()`), and a `Manage` button on the right. Sticky with a blurred
   gradient scrim so the list passes under it legibly.
2. **Throughput panel** — glass card, `HaloRadius.Xl`. Holds the aggregate speed figure
   (mono, 24sp, tabular figures), a queue line, `Pause all` / `Resume all`, and the
   60-second history chart. On phone the figure and the button stack; from ~700dp wide they
   sit on one row.
3. **Storage meter** — two-segment bar (downloads / other apps) over a free-space track,
   with a three-item legend. Data from `DownloadStoragePort.freeBytes()` plus the summed
   `downloadedBytes` of all entries. If `freeBytes()` returns null, hide the whole block —
   do not draw a bar with a guessed total.
4. **IN PROGRESS** section label, count on the right.
5. **In-progress cards** — one per active entry.
6. **ON THIS DEVICE** section label, count + total size on the right.
7. **Finished rows** — one per `Done` entry.

The floating tab bar overlays the list; keep the existing `HaloDimensions.TabBarSpace`
bottom padding.

### Tablet landscape (≥1024dp)

Same list, narrowed, with a **detail pane** on the right (360dp, 400dp above 1300dp wide),
separated by a hairline. Selecting a finished row selects it in the pane; the pane holds a
16:9 still with a play overlay, the title, a full-width Play/Resume button, a facts table
(quality, file size, audio, subtitles, licence) and secondary actions. The rail nav replaces
the bottom bar, matching the tablet shell already specified in the tablet handoff.

Tablet portrait uses the phone layout at tablet spacing (`--g` 28dp) with the bottom bar.

## Components

### In-progress card

Glass card, `HaloRadius.Xl`, 1px `HaloColors.GlassBorder`, a soft top-lit gradient fill and
a low shadow. Contents, left to right:

- **Poster**, 2:3, stretched to the card's full height (104dp wide on phone, 118dp on
  tablet), `HaloRadius.Md`, hairline inner ring.
- **Body column**:
  - Title (15sp, SemiBold, `HaloColors.Text`) and subtitle (12.5sp, `HaloColors.TextDim`),
    both single-line with ellipsis. Use `downloadRowTitle()`.
  - **Pause/resume** and **delete** circular buttons, 36dp, top-right of the card. Delete is
    red: `HaloColors.Danger` at 38% for the border, a 20%→8% vertical gradient fill, icon at
    full `#FF6259`. It is the only destructive control on the screen and reads as such.
    Deleting still routes through the existing `SelectSheet` confirmation.
  - **Progress bar**, 6dp, pill, inset track. Active fill is an accent→light-blue gradient
    with a soft glow; a live transfer also carries a sheen that travels across it on a 2.4s
    loop. Paused fills flat white at 45%; a stalled/failed transfer fills amber. Width
    animates over 600ms, so a progress jump slides rather than snaps.
  - **Meta line** (mono, 11.5sp, tabular): a breathing status dot, then speed
    (`formatSpeed`, `HaloColors.Success` when live), bytes (`formatBytes` of downloaded /
    total), time remaining (`formatRemaining`), and quality in dim text. The line wraps to
    two lines on narrow phones rather than truncating.
  - **Play now — N% buffered** button, accent glass, shown only when the entry is playable
    (see below).

Card animates in with a 12px rise + fade over 280ms.

### Finished row

Same glass treatment at row scale. Poster (2:3, stretched to row height, 52dp phone / 62dp
tablet) with a watched-progress hairline across its bottom edge when the entry is partly
watched. Title, subtitle, then a mono line with quality · size and the licence line. A white
**Play** button (gradient white→silver, inner highlight) and an overflow button on the
right. The whole row is tappable to play, as it is today; on tablet a tap selects it in the
detail pane and the Play button plays.

### Throughput chart

28 bars, one per ~2 seconds of a rolling 60-second window, height normalised to the window's
peak with a floor of 4%. Older bars fade back in opacity; the four newest are fully lit with
a glow. Heights animate over 500ms. Under it, two mono captions: `THROUGHPUT · LAST 60s` and
`PEAK n.n MB/S`.

Feed it by sampling `DownloadEntry.bytesPerSecond` on the same cadence the coordinator
already reports progress. Keep the ring buffer in the screen's state — it describes the
session, and a chart restored from disk would be about a connection that no longer exists,
for the same reason `bytesPerSecond` is `@Transient`.

## States

| State | What changes |
| --- | --- |
| Transfers running | As drawn: live rates, sheen, breathing dot, chart moving |
| Paused (single) | That card's speed reads `paused`, bar flattens to white, dot stops, sheen off |
| Pause all | Every active card as above; aggregate figure reads `0.0 MB/s`, button becomes `Resume all` |
| Waiting / stalled | Card border and bar go amber, speed reads `0.0 MB/s`, ETA replaced by the reason |
| Failed | Existing behaviour: `failureMessage` on the status line in `HaloColors.Danger`, retry via the resume control |
| Nothing downloading | In-progress section and its label disappear entirely; header summary reads storage only |
| No downloads at all | Existing `CenterMessage` empty state, unchanged |
| No storage available | Existing `isAvailable` placeholder, unchanged |

## Playing an unfinished download

The rule in the prototype: an item is playable once **25% of it is contiguous on disk**, and
the button states the buffered percentage so the offer is honest.

To support it:

1. The transfer already appends to `<fileName>.part` sequentially, so `downloadedBytes` *is*
   the contiguous prefix — no new bookkeeping is needed as long as that stays true. If
   ranged/parallel fetching is ever introduced, this assumption breaks and the button must
   go behind a real contiguous-bytes figure.
2. `DownloadsCoordinator.playbackFiles(entry)` must be able to return the `.part` path for
   an active entry, not only the finished file.
3. The player is entered through the ordinary route with the local path — `DownloadMedia`
   already carries everything the offline player needs.
4. mpv must be told not to treat EOF at the end of the partial file as end of stream while
   the transfer is still running. Simplest correct behaviour for this pass: play the
   buffered part, and on reaching its end, pause with the ordinary buffering indicator until
   more bytes land.

The playback surface itself is the existing player screen, entered as it is today. The
overlay drawn in the prototype (dimmed still, back button, title, an `OFFLINE · NO NETWORK
USED` badge, scrub bar) is only there to show that Play leads somewhere — do not rebuild the
player from it. The one element worth carrying over is the offline badge, as a state chip in
the player's existing chip row when the source is a local file.

## Motion

| Element | Motion |
| --- | --- |
| Progress bar width | 600ms, standard easing |
| Chart bar heights | 500ms, standard easing |
| Live-transfer sheen | 2.4s linear, infinite, only while downloading |
| Status dot | 1.8s ease-in-out opacity breathe, only while downloading |
| Card entry | 280ms rise + fade |
| Playback overlay | 180ms fade |
| Button hover/press | 120–180ms |

All of it stops when the transfer stops. Nothing animates on a paused, queued or finished
item — motion on this screen means bytes are moving.

## Acceptance checklist

- [ ] Phone 390 and 430: no horizontal overflow, meta lines wrap cleanly, tap targets ≥44dp
- [ ] Tablet landscape: detail pane tracks the selected row; rail nav, no bottom bar
- [ ] Speed, size and remaining figures all come from `formatSpeed` / `formatBytes` /
      `formatRemaining`, in tabular figures, and do not jitter as they tick
- [ ] Queued entries show as queued, not as a second live transfer
- [ ] Pausing kills the rate, the sheen and the dot; resuming restores them
- [ ] Storage block hides entirely when `freeBytes()` is null
- [ ] Delete still routes through the existing confirmation sheet
- [ ] Play from a row and Play from the detail pane enter the same player route
- [ ] Every colour, radius and spacing value resolves to a `HaloTheme.kt` constant

## Out of scope

Starting a download (that stays on the streams screen), the download settings screen behind
`Manage`, background transfer scheduling, and any change to the storage or transfer engine
beyond the partial-playback path above.
