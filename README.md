# Vedro

Vedro is a current-weather Android app built with Java/XML and ViewBinding.
Search by city to get current conditions from OpenWeather. Device location is not requested.
Source code is available here; the app has no public download.

- Current temperature in °C and wind speed in km/h.
- Quiet precision interface with compact artwork and clear loading/error states.
- Explicit retry and manual city search, without location permissions.
- Latest searches win: older responses are ignored, and weather state and pending
  requests survive rotation and Activity recreation.

## Screenshots

| Current weather | Error and retry |
| --- | --- |
| <img src="docs/quiet-precision/quiet-success.png" alt="Vedro showing sample Skopje weather: partly cloudy, 24 degrees Celsius and 12 kilometres per hour" width="230"> | <img src="docs/quiet-precision/quiet-error.png" alt="Vedro sample error state with Retry Skopje and city search" width="230"> |

Sample fixture data in genuine app captures.

## Quick start

Use **JDK 21**, Android **SDK 37.0** and **Build Tools 36.0.0**. Clone the repository,
enter its root, and set `JAVA_HOME` and `ANDROID_HOME` (or use an ignored
`local.properties` SDK path).

For a fresh checkout without `debug-weather.properties`:

```sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

An API key is optional for building; without one, the normal screen shows a
configuration message instead of weather. For private live testing, follow the
key-setup instructions linked below. Weather requests start only when you submit a city.

## Testing

Unit and fixture UI tests cover weather mapping, request ordering, retries and
screen recreation; fixtures run without provider keys or live weather requests,
and lint checks the app.
[Validation and known limits](docs/validation-history.md) records revision-scoped
results, device coverage and remaining acceptance gaps.

## Keys and privacy

Embedded weather keys are **extractable** from apps and build outputs. Never
commit keys or publish configured APKs/AABs, generated code or private logs.
City queries use HTTPS to OpenWeather. The app does not access device location.

## Documentation

- [Build toolchain and local device tests](docs/build-toolchain.md)
- [Private debug key setup](docs/debug-key-setup.md)
- [UI states and fixture evidence](docs/quiet-precision/README.md)
- [CI and internal-release safeguards](ci/README.md)
- [Clear sky branding assets](branding/README.md)
