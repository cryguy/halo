# Approved design direction

## Decision

- Selected direction: Direction A, Strict parity
- User selection: `yes direction A..go with it remove other two`
- Approval date: 2026-08-11
- Source design: `origin/main` at commit `41a15c8c9d3a41bb7ff30daceee6dd27340fe4aa`

## Directions shown

- Direction A, Strict parity: `screenshots/direction-a-strict-parity.png`
- Direction B, Android edge-to-edge: `screenshots/direction-b-edge-to-edge.png`, removed after selection at the user's request
- Direction C, Cinematic editorial: `screenshots/direction-c-cinematic-editorial.png`, removed after selection at the user's request

## Retained artifact

- Interactive prototype: `Halo Android Direction A - Strict Parity.html`
- Screenshot: `screenshots/direction-a-strict-parity.png`

## Implementation contract

- Preserve the original glassy-dark system from `apps/mobile/src/theme.ts`.
- Preserve near-black canvas, real poster artwork, blue interaction accents, and one white hero action per screen.
- Preserve the original screen hierarchy, bottom navigation, search behavior, segmented filters, poster density, detail composition, source grouping, and settings grouping.
- Preserve phone and tablet responsiveness derived from current window dimensions.
- Treat visible departures from the original as constraints to document, not opportunities to redesign.

## Documented departure: the player screen

- Approved: 2026-08-13
- Design source: `Player_Design/` at the repository root (`README.md` is the spec,
  `Halo Player - native.dc.html` the prototype, `PLAN.md` the build checklist)

The contract above says departures from the original get documented rather than redesigned. The
player is the one screen where that could not be honoured, because there is nothing to hold
parity with. The Expo player is a portrait-first surface with a VLC control set bolted on, and
half of what the native player has to expose (playback speed, cache state, subtitle styling,
fit mode, an episode drawer) either does not exist there or exists as a modal list. Holding
parity would have meant reproducing an interface that was never designed.

So the player was designed from scratch as a landscape screen, and
`Player_Design/Halo Player - old parity reference.dc.html` records what it replaces. The rest of
the contract still binds it: the glassy-dark system, the near-black canvas, the blue interaction
accent, the one white hero action (play), and the phone/tablet split driven by window dimensions
are all carried over unchanged, and the design adds no new colour or type primitives beyond a
set of player-only fills recorded in `ui/HaloTheme.kt`.

No other screen departs from Direction A.
