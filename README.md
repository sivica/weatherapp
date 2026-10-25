# Vedro

A current-weather Android app with a Java/XML interface, city search and optional
location lookup. Weather comes from OpenWeather's Current Weather endpoint.

- Current temperature in °C and wind speed in km/h.
- Quiet precision UI with clear loading, error and retry states.
- Search remains available when location permission is refused.
- Latest requests win; screen state and pending weather requests survive rotation
  and Activity recreation. Process-death restoration is not implemented.

## Status

As of **2026-10-25**:

| Scope | Last verified state |
| --- | --- |
| Published app | **1.0 / code 3**, owner-only Google Play internal testing; no public download. [Release run](https://github.com/sivica/weatherapp/actions/runs/40196639232). |
| New UI and fixes | App code at [`13d8db4`](https://github.com/sivica/weatherapp/commit/13d8db4483d3b9804143c2061985e5d98671641a) is merged into `main`, but was **not published in that release**. [Passing main CI](https://github.com/sivica/weatherapp/actions/runs/40201847773). |

The package ID remains `ivica.weatherapplication`. A successful upload does not
establish installation or acceptance on the owner's own phone.

## Screenshots

| Current weather — sample data | Error and retry — sample data |
| --- | --- |
| <img src="docs/quiet-precision/quiet-success.png" alt="Vedro showing sample Skopje weather: partly cloudy, 24 degrees Celsius and 12 kilometres per hour" width="230"> | <img src="docs/quiet-precision/quiet-error.png" alt="Vedro sample error state with Retry Skopje, city search and Use my location" width="230"> |

These are genuine app captures using **in-memory fixture data**, not live weather
or the owner's location. The selected UI uses a light palette in both system
appearance modes. [More states and UI QA](docs/quiet-precision/README.md#screenshots)
· [Capture provenance and historical screenshots](docs/validation-history.md#screenshot-provenance).

## Quick start

Use **JDK 21**, Android **SDK 37.0** and **Build Tools 36.0.0**.
[Full toolchain and setup](docs/build-toolchain.md).

1. Clone the repository, enter its root, and set `JAVA_HOME` and `ANDROID_HOME`
   (or use an ignored `local.properties` SDK path).
2. In a fresh checkout without `debug-weather.properties`, build and check the app:

   ```sh
   ./gradlew --no-daemon :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
   ```

Without a key, the normal screen shows a configuration message and makes no
weather or location request. An existing configured checkout embeds its local
key in debug outputs, so the same command is not necessarily a key-free build.
For an optional private debug build, use the
[macOS masked-entry helper and key-isolation instructions](docs/debug-key-setup.md).
The helper itself does not launch the app or contact OpenWeather.

To run the fixture UI tests, select a local device explicitly, then run:

```sh
ANDROID_SERIAL="your-device-serial" ./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Replace `your-device-serial` with an available local device's serial. Fixture tests
use the in-memory transport, independent of any local key; they do not call the
weather provider.

## Validation and limits

For the app code at `13d8db4`, verified **2026-10-25**:

| Check | Result |
| --- | --- |
| App unit tests | **25 passed** |
| Fixture UI | **12 passed** on a local API 36 ARM64 emulator |
| Debug/release lint | No issues |
| Builds and key-free verification | Debug/test APKs and ordinary release bundle passed; no fixture activities in release |
| Pipeline helpers | 23 CI safety tests, 9 debug-key helper tests and workflow verification passed |

The current fixture coverage includes state transitions, retry, request ordering,
rotation, permission refusal, insets, 200% fonts, day/night artwork metadata and
rounded-zero accessibility values. Earlier API 37 emulator/physical-device and
live-provider checks apply to the **older UI**, not acceptance of this revision.
[Revision-scoped evidence and history](docs/validation-history.md).

Current API 37/physical-device acceptance, TalkBack, granted-location behavior and
rotation during the permission dialog remain unverified. Broader OEM coverage,
live GPS/geocoding accuracy and process-death restoration are also outside the
verified scope. Reports are generated under `app/build/reports/`.

## Keys and privacy

Embedded weather keys are **extractable from the app and build outputs**. Never
commit keys or publish key-bearing APKs/AABs, generated code or private logs.
Ordinary release builds ignore local debug configuration and remain key-free;
only an explicitly selected private internal CI build can embed the approved key.
Historical exposed values must not be restored.

City queries go over HTTPS to OpenWeather. **Use my location** requests optional
permission and uses Android location/geocoding services to determine a city first.
The app does not automatically request location or weather at startup.

## Development documentation

- [Build toolchain and dependency troubleshooting](docs/build-toolchain.md)
- [Private debug key setup and isolation checks](docs/debug-key-setup.md)
- [Current validation and dated history](docs/validation-history.md)
- [Quiet precision UI states and fixture evidence](docs/quiet-precision/README.md)
- [GitHub checks and protected internal-release workflow](ci/README.md)
- [Vedro / Clear sky branding assets](branding/README.md)
