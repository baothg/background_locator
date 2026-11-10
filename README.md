# background_locator

A Flutter plugin for getting location updates even when the app is killed — sends locations to a Dart callback running in a background isolate, with a distance filter.

Android and iOS. Not published on pub.dev — depend on it via git:

```yaml
dependencies:
  background_locator:
    git:
      url: https://github.com/baothg/background_locator.git
```

## Setup

### Android

The plugin merges location/foreground-service permissions automatically, but the service and boot receiver must be declared in your app manifest:

```xml
<service
    android:name="com.thgbao.background_locator.IsolateHolderService"
    android:exported="false"
    android:foregroundServiceType="location" />
<receiver
    android:name="com.thgbao.background_locator.BootBroadcastReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```

Tracking across process death and reboot requires `ACCESS_BACKGROUND_LOCATION` — a "while in use" grant is not enough to restart from boot.

### iOS

Add `NSLocationAlwaysAndWhenInUseUsageDescription` and `NSLocationWhenInUseUsageDescription` to `Info.plist`, and enable the *Location updates* background mode.

## Usage

```dart
import 'package:background_locator/background_locator.dart';
import 'package:background_locator/location_dto.dart';
import 'package:background_locator/settings/android_settings.dart';
import 'package:background_locator/settings/ios_settings.dart';

@pragma('vm:entry-point')
void onLocation(LocationDto location) {}

Future<void> startTracking() async {
  await BackgroundLocator.initialize();
  await BackgroundLocator.registerLocationUpdate(
    onLocation,
    androidSettings: const AndroidSettings(
      interval: 10,
      distanceFilter: 10,
      androidNotificationSettings: AndroidNotificationSettings(
        notificationChannelName: 'Location service',
        notificationTitle: 'Tracking location',
        notificationMsg: 'Tracking your location in background',
      ),
    ),
    iosSettings: const IOSSettings(
      distanceFilter: 10,
      showsBackgroundLocationIndicator: true,
    ),
  );
}
```

Call this from the main isolate after Flutter binding initialization and permission grants. Implement `onLocation` to persist locations quickly using background-isolate-safe storage. See `example/` for a complete app.

## Important notes

**All callbacks run in a background isolate**, not the main isolate: the location callback, `initCallback`, `disposeCallback`, `notificationTapCallback` and the error reporter. They cannot touch the UI directly — use `IsolateNameServer` + `SendPort` to forward data to your main isolate.

**Control tracking from the main app isolate.** Call `initialize()` during app startup and await it before registering location updates. Do not recursively initialize or register tracking inside location/init/dispose/error callbacks; the service restores its own dispatcher. `MissingPluginException` means the calling engine/isolate has no registered native handler; it is not proof that a callback ran in the wrong isolate. Plugin availability in a headless engine depends on engine/plugin registration.

**Keep callbacks fast (<10s).** A callback that does not return within 10 seconds trips `BACKGROUND_LOCATOR_TIMEOUT` and is reported to the error reporter. This is a warning, not cancellation or completion: the engine stays alive until Dart actually replies, including after the native service stops. A callback that never returns can therefore retain the engine indefinitely. Bound network/Firebase initialization with app-side deadlines and persist data before returning; do not fire-and-forget work that must survive engine destruction. Repeated consecutive errors with the same `code` are deduplicated for 60 seconds. The dedupe window is held in memory; process death clears it, and different codes are not deduplicated together.

**Await tracking commands.** On Android, `registerLocationUpdate` acknowledges when the service has applied the settings; re-registering updates the provider request and notification without restarting the engine or re-running `initCallback`. `unRegisterLocationUpdate` acknowledges at the end of `onDestroy`, after native cleanup and canceling restore retries, not after a fixed delay. Dispose callbacks are dispatched before destruction. It does not wait for Dart callbacks to finish. Await unregister before registering again. Both methods may throw `PlatformException` (`SERVICE_COMMAND_FAILED`, `SERVICE_COMMAND_TIMEOUT`, `LOCATION_PERMISSION_DENIED`); a 30-second acknowledgement deadline reports failure, not success, and does not prove the command was canceled. Catch failures and check service state before retrying.

**Restore retries:** transient Android restore failures preserve tracking intent and retry in the foreground service after 1, 2, 4, 8, 16, 32, then 60 seconds (capped). Successful start resets the backoff; unregister/destroy cancels pending retries. Missing permissions or invalid request settings are not treated as transient startup errors. Android/OEM process scheduling can delay retries; `START_STICKY` is not a restart guarantee.

**`autoStop` semantics:** when `autoStop: true`, the plugin requests unregister on Flutter's `detached` lifecycle event, not on `paused`. This event is not a reliable swipe-away/process-kill notification and may also occur when the Flutter view is detached. For tracking independent of the UI lifecycle, leave `autoStop: false`. Android force-stop prevents automatic restart until the user launches the app again.

**Android settings validation:** `interval` (seconds) and `wakeLockTime` (minutes) must be positive signed 32-bit integers. `distanceFilter` must be finite, non-negative and fit an Android float. Invalid values throw `ArgumentError` before the registration channel call; catch these alongside `PlatformException`.

**`initCallback` data** may contain `wasKilled: true` if the previous tracking session was killed by the OS (process death, crash) instead of a graceful unregister — re-run your cleanup there.

**Error reporting:** `BackgroundLocator.setErrorReporter(fn)` registers a top-level/static function receiving `{source, code, message, stack, timestamp}` for every caught plugin error — including failures that happen while the app is killed (queued, delivered on next dispatcher start). Use it to forward to Crashlytics or your own reporter; pass `null` to unregister.

**OEM battery killers** (Xiaomi/OPPO/vivo/Samsung...) may still kill the foreground service despite `START_STICKY`. If your use case needs reliable background tracking, prompt users to disable battery optimization (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and enable autostart in OEM settings — this part is app-side UX, the plugin cannot do it.

## Initialization and persistence

This package uses its own Android preference files and namespaced iOS keys. It does not import settings, tracking flags, queued errors or callback handles from other implementations, or from earlier snapshots that used shared storage. Existing data is left untouched; no compatibility bridge is included.

On first launch, initialize from the main isolate, register the error reporter if required, and register location updates with the app's current callbacks/settings if tracking is desired. Restoration is only available after successful setup with this package. Do not skip initialization solely because `isServiceRunning()` is true.

Use only one location-tracking plugin implementation. Check `flutter pub deps` and the merged Android manifest for unwanted dependencies or duplicate services/receivers. There is no runtime mutual-exclusion guard against unrelated plugins. On iOS, custom registrant imports must use `background_locator`; refresh Pods and confirm only one locator plugin is registered.

Run `flutter clean` once when changing plugin copies, then `flutter pub get` and a full native rebuild, not hot reload. Pin production git dependencies to a verified commit. Keep the consumer app's application ID and signing identity; the example identifiers are not production settings.

Before rollout, test backgrounding, process death, reboot, permission revocation, notification taps and repeated stop→start with slow callbacks. Confirm a single service/notification, no duplicate uploads and successful callback delivery. OS scheduling and force-stop restrictions still apply; builds and unit tests do not guarantee uninterrupted tracking.

## License

MIT — see [LICENSE](LICENSE).
