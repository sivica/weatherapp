# Weather application

A Java/XML Android weather app using ViewBinding, Retrofit and OpenWeather's
Current Weather endpoint. The current milestone modernizes the build and fixes
weather mapping, response validation, request ordering and screen state.

## Build requirements

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 9.3.3 |
| Gradle wrapper | 9.8.2, official SHA-256 pinned |
| Gradle daemon JDK | 21 |
| Compile SDK | 37.0 |
| Target SDK | 37 |
| SDK Build Tools | 36.0.0 |
| Minimum SDK | 24 |
| Java source/bytecode | 8 |

The application ID remains `ivica.weatherapplication`. UI code remains Java/XML;
no Compose migration is included. Dependencies are pinned in `app/build.gradle`.
The wrapper JAR and distribution match Gradle's official published checksums.

Set `JAVA_HOME` to a JDK 21 installation and `ANDROID_HOME` to the Android SDK, or
set an SDK path in an untracked `local.properties`. Android Studio's bundled JBR
21 is suitable. Do not commit local SDK paths or credentials.

```sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Compatibility references:

- [AGP 9.3.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [Gradle 9.8.2](https://docs.gradle.org/9.8.2/release-notes.html)
- [Gradle Java and AGP compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Android 17 target behavior](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Play target requirements](https://developer.android.com/google/play/requirements/target-sdk)

## Weather behavior

- Current temperature uses `main.temp`, rather than `temp_max`.
- Metric wind speed is converted from m/s to km/h by multiplying by 3.6.
- Null or incomplete payloads, invalid numeric values, HTTP errors and conversion
  failures display an error without partially updating the screen.
- Each request owns its query options. A newer search cancels its predecessor and
  ignores older success/failure callbacks.
- A ViewModel retains loading, success and error state plus in-flight work across
  Activity recreation. Search text survives recreation; resubmitting a city
  retries a failed request. Process-death restoration of weather data is pending.
- Location permission requests include coarse and fine access together.
  Approximate access is sufficient. Refusal leaves manual city search available,
  and delayed location results cannot overwrite an explicit search.
- Geocoding is asynchronous on Android 13+ and runs on a background executor on
  older versions. System-bar, cutout and keyboard insets support edge-to-edge UI.

## Private debug key configuration

The source `open_weather_api_key` resource stays empty. A populated tracked placeholder fails closed instead of being used as a key.
The generated `BuildConfig.OPENWEATHER_API_KEY` is empty for release and is populated only for
debug from the ignored root file `debug-weather.properties`. Release generation
does not read that file. Without a local key, the existing configuration message
and request/location guards remain active.

On macOS, the local masked-entry helper can save an existing API key directly:

```sh
python3 scripts/configure_debug_weather_key.py
```

It requests only the API key, not an account password. The native dialog masks
input. The value passes through a private process pipe into the ignored file,
never through command arguments, environment variables, chat or console output.
The helper creates no temporary key copy, refuses links or an unsafe destination,
and sets owner read/write permissions (`0600`) before writing. Debug generation
also rejects a configuration file with unsafe permissions. Saving does not build,
launch the app, contact a provider or change an account. Real key entry has not
been performed as part of this change.

A configured debug build embeds an **extractable** key in its APK, generated code,
classes and local build state. Keep these outputs private; do not publish them,
attach them to a PR, or use verbose logging/build scans. Debug task outputs are
excluded from build caching and debug tasks cannot store a configuration cache.
Use `--no-build-cache --no-configuration-cache` for private configured builds.
Launching a configured production screen can request startup weather after
location access; live tests require a separate explicit decision. The entry
helper itself never launches or makes a request.

All instrumentation screen tests use the in-memory fixture transport, independent
of any configured debug key. They do not launch the provider-backed activity.
The release field/resource remain empty even when the debug file is present.

Before entering a real key, run the fixture-only configuration checks:

```sh
python3 -m unittest discover -s scripts -p 'test_debug_key_entry.py'
python3 scripts/verify_debug_key_setup.py
```

The integration checker refuses to run if an existing local key file is present;
it never reads or overwrites a user credential. It uses a synthetic marker to
verify debug inclusion, release BuildConfig/resource/APK exclusion, release
independence from a malformed file, and rejection of unsafe permissions. It then
removes its temporary local configuration and restores an empty-key debug build.
An unsigned release APK is built only for this fixture isolation check; it is not
signed or published.

The historical exposed key was removed from source in the prior milestone. Its
removal does not revoke it or remove it from upstream history. Provider-side
credentials and rotation are handled separately; no account action, replacement
key creation or cloud secret storage is part of this code change. Do not restore
historical values. A service proxy remains an option for a future distributed app.


## Validation

The final target-37 APK was checked on 2026-10-24:

- Clean network-enabled and clean offline debug builds, unit tests, lint and
  instrumentation compilation passed. Release-manifest validation passed earlier.
- **21 unit tests passed**, with zero failures, errors or skips. Fixtures and fakes
  cover weather mapping, malformed responses, cancellation, stale callbacks,
  missing-key behavior and location-permission coordination.
- **9 instrumentation tests passed on Android 17/API 37 ARM64**, with zero
  failures, errors or skips; the earlier API 36 run also passed all nine. They
  cover loading/success/error/retry, current
  temperature and wind units, repeated searches, recreation, landscape rotation,
  missing-key protection, actual location-permission refusal followed by manual
  search, and application package identity. The success and landscape tests now
  also assert that toolbar and weather content avoid system bars and cutouts.
- API 37 QA used a new isolated Pixel 7 AVD in read-only mode without a data wipe
  or saved snapshot; it was shut down after verification. Existing AVDs were not
  modified. No physical phone was paired or used.
- **Lint: 0 errors and 0 warnings.** No baseline, disabled checks or severity
  overrides are used. Non-lint Java 8, legacy geocoder and Gradle configuration
  deprecation messages remain visible.
- Debug fixture activities are absent from the release merged manifest.
- Debug configuration checks: 6 masked-entry helper tests and the fixture-only
  debug/release isolation checks pass. The compiled release key resource is empty,
  and the local key path is verified ignored/untracked and fixture files use `0600`.

The non-exported fixture screens and in-memory transport live only in
`app/src/debug`. Their calls have no HTTP implementation and use no provider
credential. Production `MainActivity` retains the empty-key guard.

To run device tests, explicitly select an emulator with `ANDROID_SERIAL`, then:

```sh
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Reports are generated under `app/build/reports/`; instrumentation XML results are
under `app/build/outputs/androidTest-results/connected/`. Build outputs, local
logs, device data and screenshots are not committed.

## Offline dependency resolution

An offline miss for `androidx.recyclerview:recyclerview:1.2.1` was reproduced with
an experimental shared read-only cache attached. The cached AAR matched Google's
published checksum; the problem was in artifact resolution/cache metadata, not a
corrupt binary. Refreshes within that shared-cache configuration were insufficient.
Refreshing the selected component in a normal writable cache and warming the full
app/build/test graph repaired offline resolution. No dependency version change or
shared-cache purge was needed.

For a similar cache state, use the ordinary writable Gradle cache, refresh/warm
only the project's required graph, then verify from clean project outputs:

```sh
unset GRADLE_RO_DEP_CACHE
./gradlew --no-daemon --refresh-dependencies :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
./gradlew --no-daemon --offline clean :app:clean :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

The clean network-enabled and offline passes each executed all 83 tasks, including
21 passing unit tests and lint with zero issues. An additional offline build with
the old read-only cache attached also passed after the writable-cache metadata
was populated. Existing shared caches were neither purged nor changed. Gradle's
[cache documentation](https://docs.gradle.org/current/userguide/dependency_caching.html)
explains the separate artifact and repository-metadata caches and recommends
immutable snapshots, rather than a live installation cache, for read-only sharing.

## API 37 test environment

The official stable image was `system-images;android-37.0;google_apis;arm64-v8a`,
revision 8 (Android 17/API 37.0), downloaded and installed through the SDK manager
in a task-isolated SDK directory. It requires Emulator 37.2.11 or newer, so
Emulator 37.2.12 was installed there as well; the pre-existing emulator stayed
unchanged. Existing platform-tools were reused without downloading another copy.

| Item | Download size | Installed disk usage |
| --- | --- | --- |
| API 37.0 Google APIs ARM64 image r8 | 2,104,098,168 bytes | approximately 5.3 GiB |
| Required Emulator 37.2.12 | 416,112,708 bytes | approximately 1.2 GiB |
| Isolated AVD data/configuration | no separate download | approximately 35 MiB |

The isolated SDK/AVD area totals approximately 6.6 GiB and is retained for reuse.
Free disk was approximately 163 GiB before installation and 156 GiB after QA and
shutdown. No existing device was wiped, no unrelated SDK package was updated,
and no paid service or provider credential was used.

## Remaining limits

- API 24/31/35 device coverage, process-death restoration, broader accessibility
  and OEM testing, live weather validation and provider-key rotation are pending.
- The device suite uses fixture weather. It covers permission refusal and manual
  search, but does not validate live geocoding/GPS accuracy or provider responses.
- Non-lint Java 8, legacy geocoder and Gradle configuration deprecation messages
  remain visible; no suppression was added to hide them.
- An unsigned release APK was built with synthetic input only for key-isolation
  verification. No release signing, store upload or deployment was performed.
  This is not yet a release-ready product.
