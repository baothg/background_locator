# Architecture

Internal reference for the plugin's tracking pipeline — how pieces fit, the
invariants that make it reliable, and where to look before optimizing.

## Purpose

Deliver continuous location updates to a Dart callback that runs in a
**background isolate**, surviving UI backgrounding, process death, service
restarts and reboots — without silent-fail states where the foreground
notification is still up but locations or callbacks have stopped.

## Components

| Layer | Files | Role |
| --- | --- | --- |
| Dart API | `lib/background_locator.dart` | `initialize`, `registerLocationUpdate`, `unRegisterLocationUpdate`, `isServiceRunning`, `updateNotificationText`, `setErrorReporter`, `autoStop` |
| Dart dispatcher | `lib/callback_dispatcher.dart` | Entrypoint of the background isolate; registers the background-channel handler and notifies native it is ready |
| Dart support | `lib/settings/*`, `lib/utils/settings_util.dart`, `lib/keys.dart`, `lib/auto_stop_handler.dart`, `lib/location_dto.dart` | Settings serialization + validation, channel/method names, lifecycle observer, payload decoding |
| Plugin (Android) | `android/.../BackgroundLocatorPlugin.kt` | Main-engine `MethodChannel` handler; sends service commands with `ResultReceiver` acknowledgements |
| Service (Android) | `IsolateHolderService.kt` | Foreground service: owns location provider, background `FlutterEngine`, wake lock, pluggables, restore/retry |
| Providers | `provider/*` | `GoogleLocationProviderClient` (FusedLocation) and `AndroidLocationProviderClient` (`LocationManager`, gps+network) |
| State | `PreferencesManager.kt`, `Keys.kt`, `BackgroundCallState.kt` | Persisted settings/callback handles/flags, command/callback one-shot state, restore backoff |
| Errors | `ErrorReporter.kt` | Single funnel: dedupe → error callback via `BCM_ERROR`, or persisted queue when no dispatcher |
| Boot | `BootBroadcastReceiver.kt` | `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `MY_PACKAGE_REPLACED` → restart tracking |
| iOS | `ios/Classes/*` | Headless `FlutterEngine`, `CLLocationManager`, region monitoring to relaunch after termination |

## Flows

### Register

1. Main isolate: `initialize()` stores the `callbackDispatcher` handle.
2. `registerLocationUpdate` validates settings in Dart (interval/wakeLockTime
   1..Int32, distanceFilter finite ≥ 0), persists callbacks+settings, sets
   `trackingEnabled`, sends `ACTION_START` with a `ResultReceiver`.
3. Service `onStartCommand`: permission check → `startLocatorService` (create or
   reuse background engine, run `callbackDispatcher`) → `applySettings` (build
   `LocationRequestOptions`, update notification fields, recreate provider
   request, re-acquire wake lock, attach init/dispose pluggables) →
   `isServiceRunning = true` → ack `send(0)`.
4. Dispatcher runs in the new engine, sets its handler, calls
   `METHOD_SERVICE_INITIALIZED` → service sets `isDispatcherReady` and flushes
   the persisted error queue.

### Location → Dart

`provider → onLocationUpdated → invokeBackgroundMethod(BCM_SEND_LOCATION) →
background channel → Dart callback`.

- Before dispatch: `pendingBackgroundCalls++` and, if the dispatcher is not yet
  ready, retry every 500 ms (×20) — warmup drops are retried, not lost.
- A 10 s timer reports `BACKGROUND_LOCATOR_TIMEOUT` **as a warning only**: it
  never decrements the counter. Only a real Dart reply (`success`, `error`,
  `notImplemented`) completes a call.
- Engine destroy is allowed only when `!isServiceCreated && !isServiceRunning &&
  pendingBackgroundCalls == 0`, 1 s after the last reply — a service still
  starting up cannot lose its engine.

### Unregister

`unRegisterPlugin` clears `trackingEnabled`, sends `ACTION_SHUTDOWN` →
`shutdownHolderService`: cancel restore retry, clear flags, stop updates +
wake lock, `stopSelf`, dispatch dispose pluggables. Acknowledgement `send(0)`
fires at the **end of `onDestroy`** — callers can only restart after teardown
finished. Errors reply `send(1)`; a 30 s deadline reports
`SERVICE_COMMAND_TIMEOUT` (a failure, never fabricated success).

### Restore after kill / reboot

- `START_STICKY`: null intent → `restoreStartIntent()` rebuilds the start
  intent from persisted settings (requires `trackingEnabled`,
  `hasLocationPermission`, `hasBackgroundLocationPermission`).
- Transient restore/start failures report `RESTORE_FAILED`/`COMMAND_FAILED`,
  **keep** `trackingEnabled`, and retry with exponential backoff
  1–2–4–8–16–32–60 s, at most 10 attempts — the budget exhausted path reports
  `RESTORE_GAVE_UP`, clears tracking state and stops. Permanent errors
  (SecurityException, IllegalArgumentException, IllegalStateException such as a
  missing dispatcher) never retry. Success or unregister cancels the retry.
  `ForegroundServiceStartNotAllowedException` on restore is neither: tracking
  intent is kept and the service stops quietly for the next launch/boot.
- Shutdown and notification-update commands dispatch via `startService` — only
  `ACTION_START` is ever launched as a foreground service, so commands that
  never enter foreground cannot trip the FGS deadline.
- `onCreate` never calls `startForeground` — a service launched only to shut
  down or restore without permission must not enter foreground. `ACTION_START`
  enters foreground immediately after the permission check, before any branch
  that can throw — otherwise a mid-command failure leaves the
  `startForegroundService` deadline unmet (`ForegroundServiceDidNotStartInTimeException`).
- `wasKilled: true` is injected into `initCallback` data when the previous
  session ended without a graceful unregister (`SESSION_ACTIVE` left true).
- `BootBroadcastReceiver` re-runs registration only when tracking was enabled,
  a dispatcher handle exists, and location permissions still hold.

### Errors

`ErrorReporter.report(source, code, throwable)` — dedupes identical `code`s
within 60 s (in-memory only), logs, then delivers `{source, code, message,
stack, timestamp}` to the app's error callback. If no reporter/dispatcher is
ready, entries persist to a bounded queue (20) flushed on dispatcher init or
when `setErrorReporter` registers while a dispatcher is alive.

## Invariants (do not break when optimizing)

1. A service command only reports success after the service itself
   acknowledges; timers never fake completion.
2. `pendingBackgroundCalls` decrements **only** on a real Dart reply. A timeout
   warns; it does not complete the call — the engine must never be destroyed
   under a running callback (a never-returning callback retains the engine by
   design).
3. Engine destroy additionally requires the service to be neither created nor
   running — no destruction during start-up windows.
4. Stop must acknowledge from end-of-`onDestroy`; a following start must never
   race the previous teardown (`pendingShutdownResults`/`pendingStops` guard).
5. Providers are rebuilt on every `applySettings`; the old client's listener is
   detached before removal so stale callbacks/errors cannot affect the new
   request. `acceptingLocations` gates stale events.
6. Restore failures never clear `trackingEnabled`/`SESSION_ACTIVE`; only
   unregister, missing data, or revoked permissions do.
7. Persisted storage is namespaced to this package (`PREF_NAME`, callback prefs
   file, iOS `NSUserDefaults` keys, notification action); nothing imports or
   shares state with other implementations.
8. All plugin errors funnel to `ErrorReporter` — logging + optional app
   callback/queue. No Toasts or user-facing UI.

## Known limits

- Callbacks run in a headless isolate: no plugin APIs from inside them, keep
  them < 10 s and bounded (network/Firebase deadlines) or they hold the engine.
- `START_STICKY` and boot receivers do not guarantee restart on all OEMs;
  background location permission is required for post-kill/reboot restore.
- `autoStop` unregisters on `detached`, not `paused` — it is not a reliable
  swipe-away signal.
- iOS path shares the channel/method contract but is untested in this release.

## Optimization candidates

- `invokeBackgroundMethod` allocates a new `MethodChannel` per call — reuse the
  cached `backgroundChannel` (verify lifecycle ordering first).
- `getNotification()` builds a full builder per update; cache builder/channel.
- `Log.d`/debug-level noise in hot paths (`onLocationUpdated`, `startHolderService`).
- `GoogleLocationProviderClient` uses deprecated `LocationRequest.create()` /
  setters — migrate to `LocationRequest.Builder`.
- Deprecated `stopForeground(Boolean)`, `getParcelableExtra`, manifest
  `package=` attributes, and `onStatusChanged` override.
- `AndroidLocationProviderClient` emits an initial `getLastKnownLocation` —
  consider gating stale locations by age.
- Unused: `IsolateHolderExtension.getBinaryMessenger`, `IsolateHolderService.setCallback`,
  `InitPluggable.isInitCallbackCalled`.
- Error queue has no replay on engine recreation edge cases; dedupe window is
  process-local.
