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

## Credentials

`open_weather_api_key` is an empty, nonsecret placeholder. Without a configured
key, the app shows a configuration message and blocks weather requests and
startup location lookup. Build, lint and fixture tests need no provider key.

The previously embedded key was removed from current source. **Its removal does
not revoke it or remove it from upstream Git history.** No provider-account
change, replacement-key configuration or history rewrite is included. Do not
restore the exposed value or use it for validation.

Before live validation, handle the exposed key in the provider account and choose
a suitable secret setup outside tracked source. A key bundled in an APK remains
extractable; a service proxy is an option for a future distributed app. No such
setup or proxy is implemented in this milestone.

OpenWeather's [key-management FAQ](https://openweathermap.org/faq) explains that
accounts must retain at least one key and deletion is offered when more than one
exists. Rotation and a small live-provider acceptance check remain separate work.

## Validation

The final target-37 APK was checked on 2026-10-24:

- Debug build, instrumentation compilation and release-manifest validation passed.
- **21 unit tests passed**, with zero failures, errors or skips. Fixtures and fakes
  cover weather mapping, malformed responses, cancellation, stale callbacks,
  missing-key behavior and location-permission coordination.
- **9 instrumentation tests passed** on an Android 16/API 36 ARM64 emulator, with
  zero failures, errors or skips. They cover loading/success/error/retry, current
  temperature and wind units, repeated searches, recreation, landscape rotation,
  missing-key protection, actual location-permission refusal followed by manual
  search, and application package identity.
- The emulator used read-only mode without a data wipe or saved snapshot and was
  shut down after verification. No physical phone was paired or used.
- **Lint: 0 errors and 0 warnings.** No baseline, disabled checks or severity
  overrides are used. Non-lint Java 8, legacy geocoder and Gradle configuration
  deprecation messages remain visible.
- Debug fixture activities are absent from the release merged manifest.

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

## Remaining limits

- API 37 runtime acceptance is pending: a compile SDK was installed, but no API 37
  runtime image was available. No new image was downloaded for this milestone.
- API 24/31/35 device coverage, process-death restoration, broader accessibility
  and OEM testing, live weather validation and provider-key rotation are pending.
- Network-enabled dependency resolution passes. Offline resolution currently
  fails for `androidx.recyclerview:recyclerview:1.2.1` at
  `:app:dataBindingMergeDependencyArtifactsDebug` despite its AAR being cached.
  The exact cache-metadata cause is unresolved. This does not introduce network
  weather requests into fixture tests.
- No release APK, store upload or deployment has been performed. This is a review
  milestone and is not yet a release-ready product.
