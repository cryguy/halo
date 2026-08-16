# Halo provider issues

## Diagnosis

The recurring Sources and download failures are primarily caused by the
configured stream provider, not by a generally unhealthy Halo API process.
The app also makes the incident look worse because it reduces several
different request failures to the same generic message and requires manual
retry.

This is a diagnosis recorded on 2026-08-16. ShyVM was inspected read-only. No
remote service, configuration, database, or data was changed.

## Evidence

### Halo API and host

- The `halo-api` PM2 process was online, with zero restarts and approximately
  two days of uptime.
- The local `/health` endpoint returned HTTP 200 in under one millisecond.
- The public `https://halo.ditto.moe/health` endpoint returned HTTP 200 for
  20 out of 20 checks, at approximately 250 ms per check.
- Ten unauthenticated public `/streams` checks consistently returned HTTP 401.
  This proves that the public route was reachable and responding.
- The host showed no CPU or event-loop pressure during inspection.

These checks do not prove that every authenticated request is healthy at every
moment, but they rule out a continuously crashed or overloaded Halo service.

### Configured addons

The live database contained these global addons:

| Addon | Capability |
| --- | --- |
| Cinemeta | Catalog and metadata |
| OpenSubtitles v3 | Subtitles |
| Torrentio TB | Streams, plus limited metadata |

`Torrentio TB` is the only configured stream-capable addon. A temporary issue
with it therefore leaves Halo with no alternative stream provider.

### Direct provider probes from ShyVM

- The Torrentio manifest responded five out of five times, between roughly
  9 ms and 237 ms.
- A representative Torrentio stream-resolution request timed out three out of
  five times at a 12-second limit.
- The two successful stream-resolution responses returned HTTP 200 but
  contained zero streams.

The provider is therefore reachable, but its stream-resolution path is
intermittent and can return no playable result.

## How the app presents this

The API fans out to capable addons and applies a 10-second timeout per addon.
It returns addon failures in the `errors` field when the request itself
completes:

- `apps/api/src/app.ts`, `/streams` fan-out and timeout handling
- `apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/screens/StreamsScreen.kt`, source failure rendering

The Sources screen maps an unclassified transport or authentication exception
to `Could not reach your Halo server.` A clean response containing addon
timeouts is rendered differently, so the screenshot's exact message indicates a
request-level failure, or a connection that failed before the response was
received. It does not by itself prove that the whole host was down.

Source retries are manual. The cache keeps the failed state until the user
presses Retry, which explains why several taps can be needed when the provider
is slow or intermittent.

## Why downloads can fail separately

Downloads use the resolved provider URL directly. They do not transfer media
through the Halo API after source selection.

The Android download worker treats network and server failures as transient and
retries up to five times with exponential backoff starting at 30 seconds. HTTP
401 and 403 responses are treated as `SourceExpired`, which requires selecting
the source again to obtain a fresh URL. A retry of the same expired URL cannot
repair that case.

Relevant implementation points:

- `apps/mobile-native/composeApp/src/commonMain/kotlin/moe/ditto/halo/downloads/DownloadTransfer.kt`
- `apps/mobile-native/composeApp/src/androidMain/kotlin/moe/ditto/halo/downloads/HaloDownloadWorker.kt`
- `apps/mobile-native/composeApp/src/androidMain/kotlin/moe/ditto/halo/downloads/AndroidBackgroundDownloadPort.kt`

## Recommended follow-up

1. Add or repair a second stream-capable addon. The current single-provider
   configuration makes every stream lookup depend on Torrentio TB.
2. Add bounded automatic retry with jitter for request-level network and
   temporary server failures, while retaining the manual Retry button.
3. Preserve and display sanitized addon failure reasons, such as a provider
   timeout, instead of presenting every transport exception as a Halo-server
   outage.
4. When a download source expires, refresh the source before retrying rather
   than repeatedly submitting the same URL.

The host-side addon configuration and the app-side retry behavior are separate
changes. This document records the issue only; neither change was implemented
as part of this diagnosis.

## Verification

The local API test suite passed during this investigation:

- 7 test files passed
- 103 tests passed

Those tests validate the API contract and error handling. They do not replace
live provider monitoring, since the observed failure is intermittent external
stream resolution.
