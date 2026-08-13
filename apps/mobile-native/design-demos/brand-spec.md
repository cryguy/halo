# Halo mobile brand specification

## Source of truth

The visual source of truth is the locally tracked `origin/main` Expo mobile client at commit `41a15c8`. The design drafts may explore different Android interpretations, but they must not invent a different brand.

## Required assets

- Halo mark: `apps/mobile/assets/android-icon-foreground.png`
- App icon composition: `apps/mobile/assets/icon.png`
- Android adaptive background: `apps/mobile/assets/android-icon-background.png`
- Poster and background artwork for the prototypes: Halo fixture-title IDs served through `images.metahub.space`

Every prototype HTML embeds its required raster assets as data URLs so it opens directly from `file://` without a server.

## Color roles

| Role | Value | Use |
| --- | --- | --- |
| Canvas | `#0a0c11` | App background |
| Surface | `#14161d` | Poster fallback and solid panels |
| High surface | `#1c202a` | Pickers and stronger controls |
| Border | `#252a35` | Quiet structural boundaries |
| Primary text | `#f4f6fb` | Titles and primary labels |
| Secondary text | `#8b93a5` | Metadata and explanations |
| Interactive accent | `#0a84ff` | Tabs, links, selection, small actions |
| Hero action | `#ffffff` | The single most prominent action on a screen |
| Hero action text | `#000000` | Label and icon on the white hero action |
| Success | `#5dd39e` | Connected and completed state |
| Danger | `#ff6b6b` | Destructive and error state |
| Rating | `#ffd479` | Rating metadata only |

## Glass system

- Glass fill: `rgba(255,255,255,0.07)`
- Glass border: `rgba(255,255,255,0.11)`
- Field fill: `rgba(255,255,255,0.09)`
- Floating tab tint: `rgba(15,17,23,0.72)`
- Glass means real background blur when implemented in Compose. A translucent fill without blur is only a prototype fallback.

## Geometry and rhythm

- Spacing scale: 4, 8, 16, 24, and 32 dp.
- Radius scale: 8, 12, 16, and 20 dp, plus full pills.
- Default poster ratio: 2:3.
- Phone poster rows use 112 to 132 dp wide cards depending on context.
- Titles use a compact, heavy hierarchy. Metadata stays quiet.
- One white hero action should win the visual hierarchy. Blue is reserved for interaction and state.

## Type

- Original Android UI behavior uses the platform sans stack with the main ramp defined in `theme.ts`.
- Large title: 30 sp, extra bold, tight tracking.
- Screen title: 24 sp, extra bold.
- Section heading: 18 sp, bold.
- Body: 14 sp with 20 sp line height.
- Overline: 12 sp, semibold, spaced uppercase.
- Bundled Inter, Source Serif 4, and JetBrains Mono fonts exist primarily for subtitle consistency. A design direction may test Source Serif 4 for display text, but that is a visible deviation from strict parity.

## Image treatment

- Real poster art carries the product identity. Do not replace it with gradients, generated illustration, or decorative stock photography.
- Hero artwork receives a bottom-weighted near-black scrim so title and actions remain legible.
- Poster rows are poster-forward. Labels appear only where identification would otherwise be ambiguous.

## Prohibited drift

- No purple technology gradient.
- No multicolor category system without real semantic categories.
- No decorative emoji.
- No generic dashboard statistics.
- No bright accent competing with the white hero action.
- No rounded-card treatment around every block.
- No web-style sidebar on phone.
