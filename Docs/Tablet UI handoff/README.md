# Handoff: Halo native tablet layout

## Overview

The tablet layout for the signed-in app in `apps/mobile-native` — Home, Library, Search,
Detail, Sources, Downloads and Settings at 1280 × 800 and 1366 × 1024, landscape. It is not a
redesign: every colour, radius, type size and component is the one already in `HaloTheme.kt`
and the existing screen files. What changes is the frame those components sit in — navigation,
gutters, how many posters fit across, and which surfaces become side-by-side instead of
stacked.

The player is out of scope; it already has its own handoff in `design_handoff_native_player`.

## About the design file

`Halo Tablet — native.dc.html` is a **design reference written as HTML**, not production code.
Open it by serving the folder over HTTP (`python -m http.server`) with `support.js` as a
sibling. The device switcher at the top toggles the two tablet tiers; the screen switcher
picks which surface is shown.

**The task is to recreate these layouts in Compose Multiplatform**, using the module's existing
composables, `HaloTheme.kt` constants and the branching already in `ui/Responsive.kt` — not to
port HTML.

## Fidelity

**High fidelity on layout and spacing; the visual system is unchanged and already shipped.**
Poster art, backdrops and episode stills are striped placeholders with monospace labels; real
images come from Cinemeta meta. Titles, episode names, addon names and stream strings are
fixture data.

## What is new at tablet width

Six changes, in the order they matter:

1. **A left navigation rail replaces the floating bottom bar in landscape.** Portrait keeps the
   bottom bar exactly as it is today — see the Portrait section. The rail is the same four tabs, the same icons, the same
   `HaloColors.TabBarTint` glass and the same accent-marks-selection rule — turned vertical.
2. **Browse headers go horizontal.** `ScreenHeader` stacks title → search → filter on a phone.
   At tablet width the search field and the segmented filter sit on the title's baseline, on
   the right, which reclaims roughly 90 dp of vertical space on every browse screen.
3. **Posters run seven across** in landscape at both tiers, against six from the current
   `posterColumnsFor`.
4. **Detail keeps a full-bleed hero** and lays episodes out in a multi-column grid below it —
   two columns at 1280, three at 1366 — replacing the phone's single list and the current
   `twoPane` split.
5. **Sources arrives as a right rail** over the title rather than as a pushed screen, matching
   the player's rail idiom.
6. **The rail stays visible on Detail and Sources.** On a phone both cover the tab bar because
   there is no room; on a tablet there is, and losing the rail for a title makes going back to
   Library a two-step trip. `ChromeCoveringRoutes` should keep covering only the player.

## Frame metrics

| | Tablet, landscape | Tablet, portrait | Large, landscape | Large, portrait |
| --- | --- | --- | --- | --- |
| Logical size | 1280 × 800 | 800 × 1280 | 1366 × 1024 | 1024 × 1366 |
| Navigation | left rail 88 | bottom bar 56 | left rail 88 | bottom bar 56 |
| Screen gutter (`--g`) | 32 | 24 | 40 | 32 |
| Poster columns | 7 | 5 | 7 | 6 |
| Poster grid gap | column 14, row 20 | column 14, row 20 | column 14, row 20 | column 14, row 20 |
| Home hero height | 340 | 300 | 380 | 340 |
| Detail hero height | 420 | 400 | 500 | 460 |
| Episode grid columns | 2 | 1 | 3 | 2 |
| Sources rail width | 440 | 400 | 480 | 440 |
| Shelf poster width | 168 | 156 | 178 | 168 |
| Catalog-row poster width | 156 | 144 | 164 | 156 |
| Bottom content padding | 32 | 96 (`TabBarSpace`) | 32 | 96 |

## Portrait

Portrait keeps the phone's chrome model and inherits every tablet gain that is not about
width. Specifically:

- The floating bottom bar returns, unchanged: `HaloColors.TabBarTint` glass, 1dp top hairline,
  56dp row, four equal-weight tabs, accent on the selected one. Screens end their content with
  `HaloDimensions.TabBarSpace` again, because the bar floats over them.
- Browse headers keep the horizontal arrangement: on Home the filter and the search field share
  one row, `gap 14dp`, filter left. Library keeps its title on its own line with the search
  field and filter in a row beneath it, since three elements do not fit across 800dp.
- Detail keeps the full-bleed hero; the facts card sits under the synopsis instead of beside
  it, and episodes fall to one column at 800 and two at 1024.
- The sources rail still comes in from the right, at the narrower widths above.

Phone values are unchanged: gutter `HaloSpacing.Md`, 3–4 columns, hero 210, `PosterWidth` 112.

## Navigation rail

Column, width 88, full height, `padding 18dp 0 14dp`, fill `HaloColors.TabBarTint` with the
same 24dp Haze blur the bottom bar uses, 1dp `HaloColors.GlassBorder` on its trailing edge.

- **Mark.** 34dp square, radius 11dp, `HaloColors.Accent`, white `H` at 17sp ExtraBold.
  24dp below it, then the tabs.
- **Tab.** 72dp wide, `padding 10dp 0`, radius 14dp, column, `gap 4dp`: 24dp icon over a
  10.5sp SemiBold label. Selected: fill `rgba(10,132,255,.14)`, icon and label
  `HaloColors.Accent`, filled icon variant. Idle: no fill, `HaloColors.Text` — unselected tabs
  stay bright, same reasoning as the bottom bar. Tabs are 2dp apart.
- Order is unchanged: Home, Library, Downloads, Settings.
- Rail insets are `WindowInsets.safeDrawing` on the leading edge; screens no longer need
  `HaloDimensions.TabBarSpace` at the bottom in landscape, because nothing floats over them.
  Keep it for portrait.

`Responsive.kt` gains one derived value for this — something like
`usesNavigationRail = isTablet && isLandscape` — and `HaloShell` picks rail or bar from it.

## Browse header (Home, Library)

Row, `gap 24dp`, aligned on the baseline, `padding 0 --g`, 22dp above the content.

- Title `HaloType.LargeTitle` on the left.
- Right group: `flex 0 1 620dp`, row, `gap 14dp` — `SearchFieldButton` taking the remaining
  width, then `Segmented` sized to its three labels. Both components are used verbatim; only
  their placement changes.
- Below 900 dp of content width the group wraps back under the title, which is the phone
  arrangement, so one composable covers both.

## Home

- Hero: full width inside the gutters, height per table, radius `HaloRadius.Xl`, same
  `HeroScrim` and the same title / `MetaLine` / Play block, inset 26dp from the left and 24dp
  from the bottom (phone: `HaloSpacing.Md`). Title 30sp.
- Shelves and catalog rows are `CatalogRow` unchanged, at the poster widths in the table,
  `RowGap` 14 (phone 11), 26dp between rows.

## Library

`PosterGrid` at 7 columns, content padding `--g` horizontally, column gap 14, row gap 20.
Header spans the grid as it does now.

## Search

- Field row: `SearchField` capped at 720dp, `gap 16dp`, then Cancel. Full width would put the
  clear button a hand's width from the text on a tablet.
- Recent terms become a wrapping chip row under the field — `padding 7dp 14dp`, radius pill,
  fill `HaloColors.Glass`, border `HaloColors.GlassBorder`, 12.5sp `HaloColors.TextMeta` —
  instead of the phone's stacked list. Grouped result rows are `CatalogRow` with labels on, at
  150dp posters.

## Detail

Full-bleed hero at the height in the table, back button at `--g` / 20dp, title 36sp over
`MetaLine`. Below it:

- **Actions and synopsis** on the left, capped at 700dp: the `Sources` and `My List` buttons
  in a row (`gap 10dp`, sized to their labels rather than stretched — a 1200dp-wide primary
  button reads as a banner), then the description at 14sp / 21dp.
- **Facts card** on the right, `flex 0 0 300dp`, `padding 14dp 16dp`, radius `HaloRadius.Lg`,
  `HaloColors.Glass` over `GlassBorder`, rows of label / value at 12.5sp. New at tablet width:
  it uses space the phone does not have, and every value in it already exists on `MetaDetail`.
- **Season row**: the existing `SeasonChip`, with `10 episodes · 3 watched` in monospace 11.5sp
  `HaloColors.TextDim` beside it.
- **Episode grid**: `EpisodeRow` as a card — `padding 10dp`, radius 14dp, fill
  `rgba(255,255,255,.045)`, border 1dp `rgba(255,255,255,.07)`, thumbnail 148 × 84 — in a grid
  of 2 (1280) or 3 (1366) columns, `gap 12dp`. The watched tick and the progress bar keep their
  current rules.

This retires the `twoPane` branch in `DetailScreen.kt`: a hero that shrinks to half width was
the one place the tablet lost the art the screen is built around.

## Sources rail

Slides in from the right over a `rgba(4,5,8,.5)` scrim; tapping the scrim closes it. Width per
table. Fill `rgba(18,20,27,.94)` + 30dp blur, left border 1dp `rgba(255,255,255,.11)`,
top-left and bottom-left radius 22dp. Enter 220 ms `cubic-bezier(.2,.8,.2,1)`, 28dp translate
+ fade — the player's rail values exactly.

Header `padding 18dp 20dp 12dp`: `Sources` 19sp Bold over the title and episode tag at 12.5sp
`HaloColors.TextDim`, 34dp close circle `rgba(255,255,255,.08)`. Body scrolls,
`padding 0 20dp 22dp`, `gap 18dp` between addon groups. Groups and rows are `AddonGroup` and
`StreamRow` unchanged.

Navigation changes with it: choosing a source from the rail goes straight to the player and
back returns to the title, which is what `StreamsRoute`'s `popUpTo` already arranges. The
pushed `StreamsScreen` stays for phone and portrait.

## Downloads and Settings

Unchanged in structure. Both stay single-column at `contentMaxWidth` (700dp), centred in the
content area rather than in the window — the rail shifts the optical centre 88dp right, and
centring on the window leaves the form looking pushed left. Settings cards, group labels,
dividers and rows are the existing `SettingsComponents.kt`.

## Tokens

No new colours, radii or type styles. New layout constants, which belong in `Responsive.kt`
next to the existing ones rather than inline in screens:

`NavRailWidth 88` · `TabletGutter 32` / `LargeTabletGutter 40` · `PosterGridGap 14` /
`PosterGridRowGap 20` · `TabletPosterColumns 7` · `SourcesRailWidth 440` / `480` ·
`EpisodeGridColumns 2` / `3` · `FactsCardWidth 300` · `SearchFieldMaxWidth 720` ·
`BrowseHeaderControlsWidth 620`.

## Suggested order of work

1. Add `usesNavigationRail` to `Responsive.kt` and build `HaloNavRail` beside `HaloTabBar` in
   `HaloShell.kt`; switch on it and drop the bottom padding allowance in landscape.
2. Widen the gutters and raise `posterColumnsFor` for the tablet tiers. Home and Library land
   almost entirely from those two steps.
3. Give `ScreenHeader` its horizontal arrangement above a width threshold.
4. Rebuild the detail body: hero, actions + facts row, episode grid; remove `twoPane`.
5. Turn the sources picker into a rail on tablet landscape, keeping the pushed screen elsewhere.
6. Check Downloads and Settings centre inside the content area, not the window.

## Files

- `Halo Tablet — native.dc.html` — the design to build.
- `support.js` — runtime for opening it in a browser.
