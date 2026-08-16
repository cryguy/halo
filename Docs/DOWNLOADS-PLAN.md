# Halo native downloads

## Current outcome

Halo has a real device-local download engine on Android and iOS. The product
keeps one transfer active at a time, resumes ranged Android partials, preserves
offline playback, and lets the operating system own active work when the app is
backgrounded or its ordinary process dies.

Android uses a persistent WorkManager `CoroutineWorker` promoted as a
`dataSync` foreground service. iOS uses a Swift background `URLSession` with the
stable identifier `moe.ditto.halo.background-downloads.v1`.

The source implementation is complete on Windows. Android Kotlin, common tests,
fixture tests, and iOS Kotlin compilation are verified there. Swift compilation,
Xcode unit tests, XCUITest, iOS background relaunch, and notification behavior
remain pending until a Mac or iOS CI runner is available.

## Product behavior

- One entry exists per video. Downloads are grouped by title and play through
  the normal player route from device storage.
- Only one OS job is submitted. The oldest queued entry starts next after a
  completion, permanent failure, pause, or removal.
- Any connected network is allowed, including cellular.
- Android keeps ranged `.part` files and guards resume with `If-Range`.
- iOS suspends and resumes a live background task. A partial file migrated from
  the old engine remains paused and is not removed until the replacement
  background task succeeds.
- Preferred-subtitle lookup runs best effort beside the video start. It never
  blocks or fails the video job.
- Pause preserves bytes. Cancel removes the entry and all files it owns.
- Sign-out durably pauses queued and active entries. A later sign-in does not
  resume them automatically.
- HTTP 401 and 403 become `SourceExpired`. Halo does not silently resolve or
  switch releases. The viewer must choose a source again.
- Connectivity and transient HTTP failures retry up to five times with
  exponential backoff starting at 30 seconds.
- Android force-stop and iOS user force-quit are operating-system exceptions.
  Work can resume after Halo is opened again where the platform permits it.

## Ownership and concurrency

`DeviceDownloadRuntime` is application-scoped. `SignedInGraph` no longer owns a
transfer coroutine or download HTTP client. A signed-in graph supplies only an
optional authenticated subtitle lookup and removes that binding when the
session closes.

Every selected attempt receives a random opaque job ID. The v2 entry stores the
current ID, and every platform snapshot or event must match it exactly. A late
completion from a paused, cancelled, or replaced job therefore cannot overwrite
newer state.

The runtime is the only ordinary index writer. Progress is throttled by the
platform transfer and serialized with queue mutations through one mutex. Before
changing restored states, startup asks WorkManager or background URLSession for
the jobs the operating system still owns. An active index row with no OS job is
treated as the persist-before-enqueue crash window and is submitted again.

Replacement keeps files from the previous attempt until the new target has
completed. This prevents cancellation races and failed replacement from
destroying bytes already on the device.

## Persistence and security

The ordinary index is `halo.downloads.v2`. It contains media context, relative
file names, byte counts, typed sanitized failure state, timestamps, a SHA-256
source fingerprint, and an opaque job ID. It does not contain a resolved source
URL or HTTP validator.

The only record containing the source URL is `ProtectedDownloadRequest`:

- Android encrypts each record with AES-GCM. The non-exportable key is held by
  Android Keystore and ciphertext files live under `noBackupFilesDir`.
- iOS stores each record as a Keychain generic-password item with
  `AfterFirstUnlockThisDeviceOnly` accessibility.

WorkManager input, URLSession `taskDescription`, event payloads, notifications,
and diagnostics contain only the opaque job ID and sanitized numeric or enum
state. URLs, response bodies, tokens, raw exceptions, and HTTP validators are
never copied into those surfaces.

The v1 migration follows this order:

1. Decode each v1 entry independently.
2. Persist a URL-free journal that assigns one opaque job ID to each recoverable row.
3. Write every recoverable source request to protected storage.
4. Write the complete URL-free v2 index.
5. Remove v1 and the journal only after v2 succeeds.

If protected storage or v2 writing fails, v1 remains authoritative and the
runtime refuses mutations that could discard unreadable records. Repeating an
interrupted migration with the same v1 digest reuses the journal's job IDs, so
it cannot orphan a fresh protected request on every launch. If a crash leaves
both versions, v2 wins and the next read removes stale v1. Completed files and
Android partials retain their existing names.

## Android implementation

- `HaloApplication` creates the process-wide runtime before activities or
  workers use it.
- `HaloDownloadWorker` runs under WorkManager with
  `NetworkType.CONNECTED`, unique work per job ID, and exponential backoff.
- The worker reuses `HttpRangeTransfer` for `Range`, `If-Range`, validator
  checks, a stall watchdog, throttled progress, and atomic completion.
- The foreground notification has private lock-screen visibility and explicit
  Pause and Cancel actions. Its content intent opens Downloads.
- Notification permission is requested on first use without delaying the job.
  Completion and failure notifications are posted only when permission exists.
- The manifest declares notification, foreground-service, `dataSync`, and boot
  permissions, and merges the WorkManager foreground service with the
  `dataSync` type.

## iOS implementation

- `BackgroundDownloadService` is retained by `AppDelegate` and implements the
  Kotlin `HaloIosBackgroundDownloadHost` protocol.
- Its configuration allows cellular access, waits for connectivity, disables
  discretionary scheduling, and asks iOS to relaunch the app for session
  events.
- Only the opaque job ID appears in `taskDescription`.
- Delegate reconciliation combines live tasks with protected terminal records
  before Kotlin changes persisted state.
- A protected request remains in the internal `stored` state until URLSession
  owns a task. Reconciliation does not expose that state as an enqueued OS job,
  so a queued second entry cannot bypass the single-transfer runtime.
- Pause and Cancel acknowledge URLSession task suspension or cancellation
  before Kotlin pumps the queue or deletes files.
- Completion moves the temporary file into Halo's download directory and then
  removes a migrated legacy partial.
- `AppDelegate` saves the system background-session completion handler. The
  service calls it only from `urlSessionDidFinishEvents`.
- Notification authorization is non-blocking. Sanitized completion and failure
  notifications are issued only while Halo is not active and authorization is
  present.

## Test infrastructure and verification

Common tests cover serial queueing, OS reconciliation, automatic crash-window
restart, durable pause and sign-out, stale-event rejection, replacement races,
source-expiry behavior, non-blocking subtitle lookup, v1 migration ordering,
crash idempotency, and absence of source URLs from ordinary state.

Android instrumentation checks the connected-network work constraint, opaque
worker input, encrypted request recovery, and private notification actions.
The fixture server provides deterministic generated media, exact ranges,
`If-Range`, throttling, deliberate connection interruption, and controlled HTTP
failure counts.

Windows verification commands from `apps/mobile-native`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :composeApp:testDebugUnitTest
.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata
.\gradlew.bat :composeApp:compileKotlinIosSimulatorArm64
.\gradlew.bat :composeApp:assembleDebug
python -m unittest discover -s fixtures/tests -v
```

With an emulator or device attached:

```powershell
.\gradlew.bat :composeApp:connectedDebugAndroidTest
```

Mac verification remains:

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
cd iosApp
xcodegen generate
xcodebuild test -project Halo.xcodeproj -scheme Halo
```
