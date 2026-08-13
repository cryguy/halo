# Halo Android rewrite direction brief

## Purpose

The immediate deliverable is the approved visual contract for the native rewrite, not production UI code. The original Expo mobile app already has a coherent system and is the design source of truth. Direction A answers the selected question directly: the Kotlin Multiplatform Android client should reproduce that system as literally as native constraints allow. It uses the same real product content, colors, artwork, actions, and navigation concepts.

Direction A is the implementation contract for every mobile screen in the rewrite. The implementation scope remains the complete original inventory: login, Home, search, Library, Downloads, Settings, detail, source picker, player, and supporting sheets. The prototype intentionally shows only six screens because six devices expose the load-bearing visual rules. Adding every secondary or transient state would make the contract harder to review without changing the decision.

## Audience and use context

The primary audience is a Halo user browsing a self-hosted media library on an Android phone. They expect the app to feel native, fast, cinematic, and quiet. They are likely using the app in low light, often one-handed, and care more about recognizable artwork and immediate playback than about decorative branding. Tablet support remains a requirement, but the prototype uses a Pixel-class phone canvas because phone hierarchy is the hardest constraint. Tablet behavior will be derived from the approved direction using the existing responsive rules.

## Required content and screens

The approved prototype uses representative fixture content from the original app:

- Home: Watch title, search entry, All/Movies/Series filter, The Matrix hero, Continue Watching, and Popular Movies.
- Search: editable search field, grouped results from installed addons, and real poster labels.
- Detail: Breaking Bad hero, metadata, library action, season choice, and episode rows.
- Library: saved-title poster grid with the same content filter.
- Sources: grouped 4K, 1080p, and torrent fixture sources with file details and download actions.
- Settings: addon management, playback preferences, server status, and sign out.

Every device must be interactive enough to test the navigation model. The Home hero or a poster opens Detail. The season control opens a sheet. Source rows produce visible state feedback. Settings controls toggle. Bottom tabs switch between the primary sections independently within each phone.

## Emotional tone

The base tone is cinematic restraint: near-black, calm, legible, and led by real artwork. The UI should disappear when content is available and become structured when the user reaches operational surfaces such as Sources or Settings. Blue communicates system interaction. White communicates the one action that advances the viewing task. Glass is reserved for chrome that truly floats above content.

## Output format

- One self-contained HTML file in `apps/mobile-native/design-demos`.
- One complete screenshot of the approved direction.
- Six Pixel-style Android frames.
- Desktop review canvas sized for a two-row comparison, with each phone rendered at a compact but legible scale.
- No external runtime, CDN, or local HTTP server required.
- Raster assets and optional fonts embedded as data URLs.

## Approved direction

Direction A, Strict parity, is the implementation contract. It copies the original screen geometry and hierarchy as closely as an HTML review artifact can. Native implementation should preserve the original action roles, poster treatment, glass chrome, spacing, responsive rules, and navigation hierarchy. Platform APIs may differ, but those differences must not become visible design drift unless a native constraint makes parity impossible.

## Assumptions and decisions made for this gate

- The requested phrase "copy the original design" means visual and interaction parity is more important than a redesign. This makes Direction A the load-bearing baseline.
- No external Figma file or separate design system is assumed. The original app code and tracked assets are treated as authoritative.
- Real fixture poster art is content, not decoration, and is required in every direction.
- This approved design contract does not authorize changing Kotlin production screens.
- Player controls are excluded from the prototype because their landscape geometry does not compare cleanly beside portrait devices. They remain mandatory during implementation.
- Copy may be shortened to fit a direction board, but behavior, roles, and semantic labels must remain faithful.

## Form derivation

- Narrative role: Home is discovery, Search is intent, Detail is evaluation, Library is ownership, Sources is operational choice, and Settings is configuration.
- Viewing distance: approximately 10 to 40 cm on phone, with the review board also readable on a desktop monitor.
- Visual temperature: calm, dark, cinematic, and trustworthy.
- Capacity: each phone needs one clear primary action or content anchor, plus enough secondary content to reveal hierarchy without becoming a poster collage.
- Visual motif: a luminous blue Halo mark and control accent floating over a near-black field of real media artwork. The motif comes directly from the app icon, token roles, and poster-led original UI.
