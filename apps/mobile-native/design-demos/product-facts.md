# Halo product facts

Verified from the locally tracked `origin/main` snapshot at commit `41a15c8` on 2026-08-11.

## Product

- Halo is an open media center that consumes the Stremio addon protocol.
- The established mobile client is the Expo app in `apps/mobile`.
- The rewrite target is the Kotlin Multiplatform Compose app in `apps/mobile-native`.
- The Android package is `moe.ditto.halo`.
- The original client supports phones and tablets. Layout class is based on the smallest window dimension, while poster density follows current width.

## Core mobile flow

1. Connect to a self-hosted Halo server and authenticate.
2. Browse Home catalogs, search installed addons, or open the saved Library.
3. Open a movie or series detail page.
4. Choose a source, optionally download it, and start playback.
5. Manage addons, playback preferences, and the server session in Settings.

## Original screen inventory

- Login
- Home
- Search
- Library
- Downloads
- Settings and addon management
- Movie and series detail
- Source picker
- Landscape player and its sheets

## Direction-draft scope

The approved prototype shows six representative surfaces: Home, Search, Detail, Library, Sources, and Settings. These are sufficient to judge hierarchy, chrome, poster treatment, density, and action styling. Login, Downloads, and the landscape player stay in the required full-parity implementation scope.

## Evidence used

- `apps/mobile/src/theme.ts`
- `apps/mobile/src/responsive.ts`
- `apps/mobile/src/components/ui.tsx`
- `apps/mobile/src/components/PosterCard.tsx`
- `apps/mobile/src/components/CatalogRow.tsx`
- `apps/mobile/app/**/*.tsx`
- `apps/mobile/app.json`
- `apps/mobile/assets/android-icon-foreground.png`
- `apps/api/dev/fixtureAddons.ts`
