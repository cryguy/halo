# Handoff: Halo native player UI

## Overview

A redesigned playback screen for the native Halo client (`apps/mobile-native`, Compose
Multiplatform over libmpv). It replaces the current placeholder `PlayerScreen.kt` — which
today shows only the render surface, a loading spinner, an error string and a back button —
with a full landscape playback overlay: labelled state chips, a single right-hand rail for
audio / subtitles / speed, a scrub preview, an in-player episode drawer, and the full set of
transient states (buffering, gesture HUD, locked, up next, Picture in Picture, error).

The old Expo player (`apps/mobile/app/player.tsx`) is the feature reference. This design keeps
its feature set and its visual system, and regroups the controls. Approved direction from the
user: *"rethink the overlay — new control grouping, keep the glassy-dark palette."*

Note that this **departs from `apps/mobile-native/design-demos/direction-approved.md`**, which
locks the app to Direction A strict parity. The player is the one screen that had no parity
reference when that decision was made. If this design ships, record the departure in that file.

## About the design files

The two files in this bundle are **design references written as HTML**, not production code.
They are prototypes that show intended look and behaviour.

- `Halo Player — native.dc.html` — the new design. Working prototype.
- `Halo Player — old parity reference.dc.html` — the current Expo player, rebuilt at exact
  theme values, as the before-picture.
- `support.js` — the runtime the two HTML files load. Needed only to open them in a browser.

**The task is to recreate the new design in Compose Multiplatform inside
`apps/mobile-native`**, using that module's existing components, theme objects and idioms —
not to port HTML, CSS or JavaScript. Every colour, radius and spacing value in the prototype
already exists as a Kotlin constant in `HaloTheme.kt`; use the constant, not the hex string.

Open either file by serving the folder over HTTP (`python -m http.server`) and visiting it;
they need `support.js` as a sibling.

## Fidelity

**High fidelity.** Colours, type sizes, radii, spacing, animation durations and copy are all
final and should be reproduced exactly, with three exceptions that are deliberately unfinished:

1. **Video frame and episode thumbnails** are striped placeholders with monospace labels. Real
   frames come from the mpv surface; episode stills come from Cinemeta meta.
2. **Icons.** Back chevron, chevron-down, play, close and check are the app's own Material
   24dp path data lifted verbatim from `HaloIcons.kt`. Pause, PiP, fit, lock, brightness,
   volume, seek-10 and grid are prototype stand-ins in the same style — `HaloIcons.kt` does not
   carry them yet, and the real ones should be added there from the same Material Icons source
   (`pause`, `picture_in_picture_alt`, `fit_screen`, `lock_open`, `lock`, `brightness_5`,
   `volume_up`, `replay_10`, `forward_10`, `grid_view`), following that file's existing
   convention of holding raw path strings rather than taking the icon artifact as a dependency.
3. **Content** (show name, episode titles, addon names, track names, bitrates) is fixture data.

## Screens / views

There is one screen. Everything else is a layer over it.

### Player screen

**Purpose.** Watch a video; change what is playing (subtitle track, audio track, speed,
episode) and how subtitles look, without leaving playback.

**Layout.** Full-bleed, landscape-locked, black background. The mpv surface fills the box; all
chrome is absolutely positioned over it, in this z-order:

| Layer | z | Notes |
| --- | --- | --- |
| mpv surface | 0 | Also the tap target that toggles chrome |
| Subtitle caption | 1 | Bottom-centred, lifts when the bottom bar is visible |
| Scrims | 2 | Top and bottom gradients, non-interactive |
| Top bar / centre controls / bottom bar | 3 | The chrome; fades together |
| Episode drawer | 55 | Bottom sheet |
| Right rail | 60 | Plus a scrim over the video |
| Locked scrim + unlock pill | 70/80 | |
| Up next card | 75 | Bottom-right |
| PiP overlay + floating window | 80/90 | |
| Error card | 80 | Covers everything |

**Horizontal safe padding** (`hPad`) is the device's horizontal safe inset, floored at the
values below. All chrome uses it. In Compose: `WindowInsets.safeDrawing`, and the responsive
classification already in `ui/Responsive.kt` for the phone/tablet split.

| Frame | Logical size | hPad | Rail width | Vertical chrome padding |
| --- | --- | --- | --- | --- |
| Android phone, landscape | 892 × 412 | 24 | 372 | top 12, bottom 18 |
| Android tablet, landscape | 1280 × 800 | 40 | 440 | top 20, bottom 26 |
| iPhone, landscape | 874 × 402 | 59 | 372 | top 12, bottom 18 |

Tablet also scales up: title 19sp (vs 16.5), chip value 13.5sp (vs 12.5), play button 88dp
(vs 72), centre gap 44dp (vs 34), episode card 200 × 112 (vs 168 × 94), caption 21sp base
(vs 16), up-next card 360dp (vs 320).

### Scrims

- Top: `height 128dp`, `linear-gradient(180deg, rgba(4,5,8,.72) → rgba(4,5,8,0))`
- Bottom: `height 186dp`, `linear-gradient(180deg, rgba(4,5,8,0) → rgba(4,5,8,.82))`

Both non-interactive. They fade in with the chrome over 180 ms.

### Top bar

Row, `gap 14dp`, `padding: <vertical> hPad 0`.

1. **Back button.** 38dp circle. Fill `rgba(9,11,16,.5)` + 18dp blur, border 1dp
   `rgba(255,255,255,.10)`. Icon `HaloIcons.ChevronLeft` at 22dp, tint `HaloColors.Text`.
   Must route through the same wind-down path as the current screen's `leave` lambda — see the
   long comment in `PlayerScreen.kt`; skipping `windDownForExit()` deadlocks the surface.
2. **Title block.** Column, `gap 3dp`, weight 1, clipped with ellipsis.
   - Line 1: show title. 16.5sp / `FontWeight.Bold` / letter-spacing −0.2sp / `HaloColors.Text`.
   - Line 2: row, `gap 8dp` — episode tag (`S02E04`) 11.5sp `FontWeight.ExtraBold`
     letter-spacing 0.4sp `HaloColors.Accent`, then episode name 12.5sp `HaloColors.TextDim`.
3. **Stream badges.** Row, `gap 6dp`. Each: `padding 5×8dp`, radius 6dp, fill
   `rgba(255,255,255,.07)`, border 1dp `rgba(255,255,255,.09)`, monospace 10sp, colour
   `#C7CDD9`. Content is resolution and codec only — `1080p`, `HEVC 10-bit`.
   **Do not surface the debrid provider or any account-identifying source name here.**
4. **Utility pill.** Row of three 36dp circular icon buttons inside a 3dp-padded pill: fill
   `rgba(9,11,16,.55)` + 18dp blur, border 1dp `rgba(255,255,255,.12)`, radius pill. Order:
   Picture in Picture, fit mode, lock. Hover/press: `rgba(255,255,255,.12)`.
   Only three controls live here — this is the whole point of the redesign, and the reason the
   old six-icon pill is gone.

### Centre controls

Row, centred, `gap 34dp` (44 tablet).

- **Seek −10 / +10.** 52dp circle, fill `rgba(9,11,16,.42)` + 14dp blur, border 1dp
  `rgba(255,255,255,.10)`. Column content: 20dp icon, then `10` at 10sp
  `FontWeight.ExtraBold`, tabular figures, `margin-top −1dp`. Press: `rgba(255,255,255,.14)`.
  The old player used −10 / +30; this design makes both 10 s, since a symmetric pair is a
  clearer target pair and +30 is served better by the scrubber.
- **Play / pause.** 72dp circle (88 tablet), fill `rgba(255,255,255,.16)` + 18dp blur, border
  1dp `rgba(255,255,255,.14)`, icon 34dp (40 tablet), tint `HaloColors.Text`.

### Bottom bar

Column, `gap 10dp` (14 tablet), `padding: 0 hPad <vertical>`.

**Row 1 — state chips.** Row, `gap 8dp`, wrapping. Four chips, then a spacer, then
`<remaining> left` in monospace 11.5sp `HaloColors.TextDim`.

Each chip is a column, `gap 2dp`, `padding 7×12dp`, radius 12dp, 18dp blur:

| | Idle | Active (its rail tab is open) |
| --- | --- | --- |
| Fill | `rgba(9,11,16,.45)` | `rgba(10,132,255,.16)` |
| Border 1dp | `rgba(255,255,255,.12)` | `rgba(10,132,255,.55)` |
| Kicker 9sp, ExtraBold, ls 0.9sp | `HaloColors.TextDim` | `#7EC0FF` |
| Value 12.5sp, Bold | `HaloColors.Text` | `HaloColors.Text` |

Chips, left to right — kicker / value / action:

1. `SUBTITLES` / `English · ASS` (language + format of the active track, or `Off`) / open rail on Subtitles
2. `AUDIO` / `English` / open rail on Audio
3. `SPEED` / `1×` / open rail on Speed
4. `EPISODES` / `S02E04` / toggle the episode drawer

This row is the redesign's load-bearing idea: the current state of every playback choice is
readable without opening anything.

**Row 2 — transport.** Row, `gap 14dp`, vertically centred.

- Elapsed: monospace 12sp (13 tablet), `min-width 52dp`, `HaloColors.Text`.
- Track: 6dp tall, radius pill, base `rgba(255,255,255,.16)`; buffered fill
  `rgba(255,255,255,.30)`; played fill white. Hit area 34dp tall.
- Thumb: 14dp white circle, shadow `0 2dp 10dp rgba(0,0,0,.5)`; grows to 18dp while scrubbing
  (120 ms).
- Duration: monospace 12sp (13 tablet), `min-width 52dp`, `rgba(255,255,255,.55)`, right-aligned.

**Scrub preview.** While scrubbing, a 176dp-wide card floats 30dp above the track, centred on
the scrub position: 99dp tall frame, radius 10dp, border 1dp `rgba(255,255,255,.14)`, with the
target timecode in monospace 10sp bottom-left over a `rgba(0,0,0,.72)` gradient. Fades in over
120 ms. This is new — the old player had no preview. Backing it needs frame extraction (mpv
can do it, or use a sprite sheet if one is ever available); until then, ship the card with the
timecode and a neutral frame rather than dropping the affordance.

### Right rail (Audio / Subtitles / Speed)

Slides in from the right over a `rgba(4,5,8,.5)` scrim; tapping the scrim closes it.
Width per table above. Fill `rgba(18,20,27,.90)` + 30dp blur, left border 1dp
`rgba(255,255,255,.11)`, top-left and bottom-left radius 22dp. Enter: 220 ms,
`cubic-bezier(.2,.8,.2,1)`, 28dp translate + fade.

This replaces both of the old player's panels: the 48%-wide `SelectSheet` side sheet and the
full-screen three-column subtitles overlay. The video stays visible, which is what makes
tuning subtitle size and delay possible at all.

**Header.** `padding 16dp 18dp 12dp`. Title `Playback` 17sp Bold; subtitle
`Applies live — nothing reloads` 11.5sp `HaloColors.TextDim`. Close button 34dp circle,
`rgba(255,255,255,.08)`.

**Tabs.** Reuse `Segmented` from `ui/HaloControls.kt` verbatim — the prototype reproduces its
values (track `rgba(255,255,255,.06)`, 3dp padding, radius 9dp, thumb `rgba(255,255,255,.16)`
radius 7dp, label 13.5sp SemiBold, active white / idle `HaloColors.TextDim`). Order:
Audio, Subtitles, Speed. Margin `0 18dp 14dp`.

**Body.** Scrolling column, `padding 0 18dp 20dp`, `gap 10dp`.

Shared list-row anatomy (used by subtitle and audio rows): row, `gap 10dp`, full width,
`padding 11×10dp`, radius 12dp, fill transparent or `rgba(255,255,255,.08)` when selected.
Label 14.5sp — selected white Bold, otherwise `rgba(255,255,255,.90)` Medium. Detail 11.5sp
`rgba(255,255,255,.45)`, 2dp below. Trailing: optional download tick
(`HaloColors.Success`, 15dp), a format badge, and an 8dp `HaloColors.Accent` dot when selected.

**Format badge.** `padding 4×6dp`, radius 5dp, border 1dp `rgba(255,255,255,.10)`, fill
`rgba(255,255,255,.05)`, monospace 9.5sp ExtraBold. Colour by format: ASS `HaloColors.Gold`,
SRT `HaloColors.Success`, PGS `HaloColors.TextDim`, audio codecs `#C7CDD9`.

#### Subtitles tab

Sections `IN THIS FILE` and `FROM ADDONS`, each labelled 10.5sp Bold ls 1sp
`HaloColors.TextDim`. In-file rows come from `PlayerTracks.subtitles`; the first row is `Off`
(detail `No subtitles`, no badge). Addon rows come from the Stremio subtitle addons, with a
download tick when the file is already on disk.

Then a hairline (`rgba(255,255,255,.09)`), an `APPEARANCE` label, and four cards. Card chrome:
`padding 12dp`, radius 12dp, fill `rgba(255,255,255,.055)`, border 1dp `rgba(255,255,255,.08)`.

1. **Size.** Label `Size` 13.5sp Bold; hint 11.5sp `HaloColors.TextDim` — `mpv sub-scale, live`,
   or `Bitmap track — scaled, not restyled` for PGS. Current value right-aligned, monospace
   13sp Bold. Slider below: 4dp track `rgba(255,255,255,.16)`, fill `HaloColors.Accent`, 16dp
   white thumb; range **50 %–200 %**, continuous. Scale ticks 50 / 100 / 200 in monospace 10sp
   `#565E70`. Applies live via `PlayerPort.setSubtitleScale`, and the caption on the video
   rescales as the thumb moves (120 ms ease-out). The old player offered four fixed steps
   (75/100/125/150) that were creation-time VLC options; a continuous live slider is the
   capability the mpv rewrite buys, and the reason the user asked for this.
2. **Delay.** Stepper: two 34dp circles (`rgba(255,255,255,.10)`) around a 74dp-min pill
   (`rgba(255,255,255,.07)`, monospace 11.5sp Bold) that shows `+150 ms` / `0 ms` / `−50 ms` and
   resets to zero when tapped. Step 50 ms, clamped ±5000 ms. `setSubtitleDelay`.
3. **Track styling.** A 44 × 26dp switch (knob 20dp white; on `HaloColors.Accent`, off
   `#424753`) plus the font chip row. The hint text under the label states what the switch
   currently means, and it differs by format:
   - ASS, on: `Keeping the script's own fonts and positions`
   - ASS, off: `Overriding the script with Halo styling`
   - SRT: `Plain text track — Halo styling always applies` (switch is irrelevant)
   - PGS: `PGS is rendered images — font and outline do not apply` — switch disabled at 45 %
     opacity, font row dimmed to 40 %
   Font chips (`Default`, `Inter`, `Serif`, `Mono`) are `padding 7×12dp`, radius pill, 12sp
   Bold; selected `HaloColors.Accent` on white text, idle `rgba(255,255,255,.08)` on
   `HaloColors.TextDim`. They dim to 40 % when ASS track styling is on, because in that mode
   they do nothing. The families must stay identical to the desktop client's `SUBTITLE_FONTS`
   so a synced `subtitleFontFamily` means the same thing everywhere: `Inter`,
   `Source Serif 4`, `JetBrains Mono`, and platform default.
4. **Preview.** A `PREVIEW` label over a 52dp-min striped strip showing the caption at 82 % of
   its on-video size, so size and font choices are legible even when the current frame is dark.

#### Audio tab

`TRACKS` list from `PlayerTracks.audio` (label = language, detail = track index and sample
rate, badge = codec and channel layout), a hairline, then an audio-delay stepper matching the
subtitle one. Audio delay is not on `PlayerPort` yet — see gaps below.

#### Speed tab

`SPEED` label over a 3-column grid, `gap 8dp`. Each cell: column, `padding 12×6dp`, radius
12dp; selected fill `rgba(10,132,255,.16)` border `rgba(10,132,255,.55)`, idle
`rgba(255,255,255,.05)` border `rgba(255,255,255,.08)`. Rate 15sp ExtraBold (selected white,
idle `#C7CDD9`); detail 10.5sp `HaloColors.TextDim` — `Normal`, `25% slower`, `50% faster`.
Rates: 0.5, 0.75, 1, 1.25, 1.5, 2 — same set as the old player. Below, a note card:
`Pitch is corrected up to 2×. Subtitle timing follows the rate automatically.`

### Episode drawer

Bottom sheet, full width. Fill `rgba(18,20,27,.92)` + 30dp blur, top border 1dp
`rgba(255,255,255,.11)`, top radius 22dp, `padding 14dp hPad <vertical>`. Enter: 240 ms
`cubic-bezier(.2,.8,.2,1)`, 14dp rise + fade.

Header row: `Season 2` 15sp Bold, `10 episodes · 3 downloaded` 11.5sp `HaloColors.TextDim`,
32dp close circle. Then a horizontally scrolling row, `gap 12dp`, of episode cards: 168dp wide
(200 tablet), thumbnail 94dp tall (112 tablet) radius 10dp with a 3dp progress bar along its
bottom edge, border `rgba(10,132,255,.70)` for the current episode and `rgba(255,255,255,.10)`
otherwise. Under it: tag in monospace 11sp Bold (`HaloColors.Accent` when current, else
`HaloColors.TextDim`), optional download tick, then the episode name 12.5sp SemiBold
(`HaloColors.Text` when current, else `#C7CDD9`), clipped.

New in this design. It should read from the same meta the detail screen uses, and selecting an
episode should follow the binge-stream path the old player used for autoplay
(`getNextEpisode` → matched stream, or the stream picker when there is no binge match).

### Buffering

Centred column, `gap 12dp`, non-interactive. A 64dp ring — 3dp track
`rgba(255,255,255,.12)`, `HaloColors.Accent` arc, 1 s linear spin — with the cache percentage
in monospace 14sp Bold at its centre. Below, a pill (`padding 10×18dp`, radius 14dp, fill
`rgba(5,7,12,.82)`, border 1dp `rgba(255,255,255,.14)`): `Filling the buffer` 13sp Bold, then
throughput and cached seconds in monospace 11sp `HaloColors.TextDim`. No provider name.

The percentage matters: it is what distinguishes a seek that is making progress from a stall.
The old player showed it for the same reason.

### Gesture HUD

Centred pill: row, `gap 14dp`, `padding 14×20dp`, radius 16dp, fill `rgba(5,7,12,.82)`, border
1dp `rgba(255,255,255,.18)`. 26dp icon (brightness or volume), then a column with the value in
monospace 13sp Bold over a 132 × 4dp meter (`rgba(255,255,255,.22)` track, `HaloColors.Accent`
fill). Additionally, the active half of the screen gets a faint
`rgba(10,132,255,.10) → transparent` wash so it is obvious which side is being dragged.

Gestures to implement, all from `PlayerGestureLayer.tsx` in the old app: single tap toggles
chrome (280 ms double-tap window), double-tap left/right seeks ∓10 s, vertical drag on the left
half sets brightness and on the right half sets volume (baseline captured at gesture start,
full travel = 70 % of screen height, one native call per whole percent), pinch switches fit
mode at a 12 % scale threshold. Once a gesture commits to pinch, single-touch moves are ignored
for the rest of it.

### Locked

A `rgba(4,5,8,.28)` scrim swallows all input. An `Unlock` pill sits at the vertical centre,
`hPad` from the left edge: row, `gap 7dp`, `padding 10×16dp`, radius pill, fill
`rgba(5,7,12,.82)`, border 1dp `rgba(255,255,255,.20)`, 19dp closed-lock icon, label 13sp Bold.
As in the old player, the pill itself auto-hides after 3 s and any tap on the scrim brings it
back.

### Up next

Bottom-right card, `hPad` from the right edge, 320dp wide (360 tablet), `padding 16dp`, radius
18dp, fill `rgba(18,20,27,.92)` + 30dp blur, border 1dp `rgba(255,255,255,.12)`. Rise-in 240 ms.

`UP NEXT` 10.5sp ExtraBold ls 1.1sp `HaloColors.Accent`, then `in 8 s` in monospace 11sp
`HaloColors.TextDim`. Below: a 104 × 59dp thumbnail beside the next episode's tag (14.5sp Bold)
and title (12.5sp `HaloColors.TextDim`). Then a 3dp countdown bar (`HaloColors.Accent` over
`rgba(255,255,255,.14)`, width animating linearly over the countdown), then two actions:
`Cancel` (glass, `rgba(255,255,255,.07)`, border `rgba(255,255,255,.11)`, weight 1) and
`Play now` (white fill, black label, 16dp play icon, weight 1.2).

Countdown is 8 s here, against the old player's 5 s, and the card no longer blacks out the
video — the episode you are watching keeps playing behind it. Keep the old player's race
guard: the countdown, `Play now` and `Cancel` all funnel through a one-shot advance so whichever
fires first wins.

### Picture in Picture

The video area dims to `rgba(4,5,8,.82)` with `Playing in Picture in Picture` 13.5sp
`HaloColors.TextDim` and a glass `Return to Halo` button. A 236 × 133dp window (288 × 162
tablet) sits bottom-right, radius 14dp, border 1dp `rgba(255,255,255,.16)`, shadow
`0 20dp 50dp rgba(0,0,0,.6)`, entering with a 260 ms scale-from-1.08 + fade. It carries a
minimal control strip over a bottom gradient: 16dp pause glyph and a 3dp progress line.

This is the in-app representation of the handoff; the real PiP window is drawn by the OS.

### Error

Covers the screen on `#05070C`. Card, `min(392dp, 70%)` wide, `padding 20dp`, radius 16dp, fill
`rgba(20,22,30,.92)`, border 1dp `rgba(255,255,255,.09)`, shadow `0 24dp 60dp rgba(0,0,0,.5)`.

- Row: 6dp `HaloColors.Danger` dot, `PLAYBACK FAILED` 10sp ExtraBold ls 1.2sp
  `HaloColors.Danger`.
- Headline `This source could not be played.` 16sp Bold ls −0.2sp, 10dp below.
- Guidance `Others for this episode are usually fine.` 12.5sp `HaloColors.TextDim`, 5dp below.
- Diagnostic, 14dp below and separated by a 1dp `rgba(255,255,255,.07)` rule: the engine's own
  message in monospace 10.5sp `#6F7789`. Show what mpv said; do not add HTTP status codes or
  host names.
- Actions, 16dp below: `Retry` sized to its label (glass), then `Pick another source` taking
  the remaining width (white fill, black label). One white hero action, per the design system.

## Interactions & behaviour

| Trigger | Result |
| --- | --- |
| Tap the video | Toggle chrome |
| Chrome shown | Auto-hides after 3 s idle; never while paused, scrubbing, or with the rail or drawer open |
| Play / pause | Toggles; also cancels the auto-hide |
| Seek buttons | ∓10 s, re-arm auto-hide |
| Drag or tap the track | Seek; preview card follows the pointer |
| Any chip | Opens the rail on that tab, or toggles the drawer; the chip goes to its active style |
| Rail scrim / close | Dismiss rail |
| Select a track | Applies immediately, dot moves, the matching chip's value updates |
| Size slider | Applies live; on-video caption rescales in 120 ms |
| Lock | Hides chrome, raises the locked scrim |
| PiP button | Enters the PiP presentation |
| Playback ends with a next episode | Up next card, 8 s countdown, then advance |
| Load fails | Error card |

**Animation vocabulary.** Chrome fade 180–200 ms ease-out. Bottom bar and any rising sheet:
220–240 ms `cubic-bezier(.2,.8,.2,1)` with a 14dp rise. Rail: 220 ms, same curve, 28dp
horizontal. Scrub thumb growth and caption rescale: 120 ms. Countdown bar: linear over its
duration. Nothing else moves.

**Subtitle memory.** Carry over the old player's rules — remember the last explicit choice per
video and per item, so a language selection follows you to the next episode; only explicit taps
write the memory. `apps/mobile/src/subtitleMemory.ts` is the reference.

## State

Beyond `PlayerState` in `player/PlayerPresenter.kt`, the screen needs:

| State | Type | Notes |
| --- | --- | --- |
| `chromeVisible` | Bool | With a re-armable 3 s hide timer |
| `rail` | `Audio` / `Subtitles` / `Speed` / null | |
| `episodeDrawerOpen` | Bool | Mutually exclusive with the rail |
| `scrubFraction` | Float? | Non-null only while scrubbing |
| `locked` | Bool | Plus its own 3 s pill-hide timer |
| `cachePercent` | Int | Buffering readout |
| `hudValue` | brightness/volume + value | Transient, ~500 ms after gesture end |
| `upNextSecondsRemaining` | Int? | Non-null once the countdown starts |
| `nextEpisode` | video + matched stream | Prefetched at playback start |
| `subtitleTrackStyling` | Bool | ASS: keep script styling or override |
| `subtitleOutline` | none/thin/normal/thick | Persisted setting |

Playback-shaped state (position, duration, tracks, subtitle scale/delay/font) already lives in
`PlayerState`; keep it there and let the screen read it.

## Engine gaps to close

The design assumes capabilities the current boundary does not expose. Each needs plumbing
through `PlayerPort` and its two hosts before the matching control can be honest:

1. **Cache percentage** — for the buffering readout. mpv reports `cache-buffering-state`;
   nothing surfaces it yet.
2. **Subtitle outline and shadow** — the old player had both (`libassOutline`-equivalent VLC
   options); `PlayerPort` currently has scale, delay, font and add only.
3. **ASS style override** — `sub-ass-override` in mpv terms, driving the track-styling switch.
4. **Track format** — `PlayerTrack` carries `id`, `label`, `language`. It needs the codec
   (`ass` / `subrip` / `hdmv_pgs_subtitle`, and audio codec plus channel layout) for the badges
   and for the format-conditional behaviour.
5. **Audio delay** — the Audio tab shows a stepper; there is no `setAudioDelay`.
6. **Fit mode** — `contain` / `cover`, persisted, plus the pinch gesture that switches it.
7. **Frame extraction for the scrub preview.**
8. **PiP** — iOS `AVPictureInPictureController` against the mpv layer, Android
   `PictureInPictureParams`; the old app got this from the libVLC package.
9. **Next-episode prefetch** — the API call exists (`getNextEpisode`, exercised in
   `apps/api/test/nextVideo.test.ts`); it needs a native caller at playback start.

## Design tokens

Every value below already exists in `ui/HaloTheme.kt`. Reference the Kotlin constants.

**Colours.** `Background #0A0C11` · `Surface #14161D` · `SurfaceHigh #1C202A` ·
`Border #252A35` · `Text #F4F6FB` · `TextDim #8B93A5` · `Accent #0A84FF` · `Danger #FF6B6B` ·
`Success #5DD39E` · `Primary #FFFFFF` / `OnPrimary #000000` · `OnAccent #FFFFFF` ·
`Gold #FFD479` · `Glass rgba(255,255,255,.07)` · `GlassBorder rgba(255,255,255,.11)` ·
`Hairline rgba(255,255,255,.11)` · `FieldFill rgba(255,255,255,.09)` ·
`SheetTint rgba(20,22,30,.72)` · `OverlayPill rgba(5,7,12,.82)`.

Player-only additions, not in `HaloTheme.kt` — add them there rather than inlining:
scrim top `rgba(4,5,8,.72)`, scrim bottom `rgba(4,5,8,.82)`, chrome glass
`rgba(9,11,16,.45–.55)`, rail fill `rgba(18,20,27,.90)`, drawer fill `rgba(18,20,27,.92)`,
chip-active fill `rgba(10,132,255,.16)` with border `rgba(10,132,255,.55)` and label `#7EC0FF`,
meta text `#C7CDD9`, diagnostic text `#6F7789`, tick labels `#565E70`, switch-off `#424753`.

**Spacing.** `Xs 4` · `Sm 8` · `Md 16` · `Lg 24` · `Xl 32`, plus the literal 6/7/10/11/12/14/18
values called out per component above.

**Radius.** `Sm 8` · `Md 12` · `Lg 16` · `Xl 20` · `Pill 999`. Rail and drawer use 22dp;
scrub preview and episode thumb 10dp; badges 5–6dp.

**Type.** Platform default sans (Roboto on Android, SF on iOS) for everything except numerics
and technical strings, which use `JetBrains Mono` — timecodes, percentages, delays, track ids,
badges, engine messages. Weights map to `FontWeight.Medium / SemiBold / Bold / ExtraBold`.
Sizes are given per component; the smallest text on the screen is 9sp (chip kickers) and
9.5sp (badges), both all-caps and tightly tracked. Tabular figures wherever a number changes.

**Shadows.** Play/scrub thumb `0 2dp 10dp rgba(0,0,0,.5)` · error card
`0 24dp 60dp rgba(0,0,0,.5)` · PiP window `0 20dp 50dp rgba(0,0,0,.6)`. Blur radii: 14dp on
centre buttons, 18dp on top-bar chrome and chips, 30dp on rail and drawer. Use the module's
existing Haze backdrop-blur setup.

## Assets

Nothing binary. Icons are path data — five lifted verbatim from `ui/HaloIcons.kt`, the rest
prototype stand-ins to be replaced from Material Icons per the Fidelity section. Fonts are
Roboto and JetBrains Mono; `apps/desktop/fonts/JetBrainsMono-Regular.ttf` is already in the
repo under OFL and can be bundled for the native app. Video frames and episode stills are
placeholders.

## Files

- `Halo Player — native.dc.html` — the design to build.
- `Halo Player — old parity reference.dc.html` — the current Expo player for comparison.
- `support.js` — runtime for opening either file in a browser.

Source files these were derived from, worth reading before starting:

- `halo/apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/screens/PlayerScreen.kt`
- `halo/apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/player/PlayerContract.kt`
- `halo/apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/player/PlayerPresenter.kt`
- `halo/apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/ui/HaloTheme.kt`
- `halo/apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/ui/HaloControls.kt`
- `halo/apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/ui/HaloIcons.kt`
- `halo/apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/ui/SelectSheet.kt`
- `halo-main/apps/mobile/app/player.tsx` and `src/components/{SubtitlesSheet,SelectSheet,PlayerGestureLayer}.tsx`
- `halo-main/apps/mobile/src/theme.ts`

## Suggested order of work

1. Read the files above. Confirm the departure from `direction-approved.md` is intended.
2. Add the missing icons to `HaloIcons.kt` and the player-only colours to `HaloTheme.kt`.
3. Build the chrome against the existing engine: scrims, top bar, centre controls, bottom bar
   with chips and transport. Chips can read from `PlayerState` on day one.
4. Add the rail with all three tabs, wired to the `selectAudioTrack` / `selectSubtitleTrack` /
   speed and the live subtitle setters that already exist.
5. Close the engine gaps in the order they block visible controls: track format → cache
   percent → ASS override and outline → fit mode → audio delay.
6. Add gestures, then the episode drawer, then up next, then PiP.
7. Instrument as the module already does: the ownership and playback XCUITest suites are the
   right place for "chrome auto-hides", "rail applies without reload" and "back winds down".
