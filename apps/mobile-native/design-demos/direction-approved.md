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
