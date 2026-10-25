# Validation evidence and history

## Current manual-city-only revision

Verified on **2026-10-25**: **17 unit tests**, **13 fixture tests on API 37 ARM64**, clean debug/release lint,
app/test APK and ordinary release bundle builds, and key-free release validation.
Eight tests for the deleted location controller were removed; weather mapping,
request cancellation and latest-request ordering tests remain.

The app now uses manual city search only. The location action, foreground
permissions, fused-location/geocoder implementation, debug location activity and
Play services location dependency are removed. Both merged manifests and the
resolved release dependency graph are checked for location remnants.

The [Quiet precision QA record](quiet-precision/README.md) contains historical
results and fixture screenshots. Current fixture coverage includes idle startup without location permissions,
city search, error/retry, latest response ordering, rotation, large fonts and the
privacy link. No AVD data was wiped, and the task emulator was stopped afterward. Private signing and Play readback are separate checks in
any internal-release workflow.

## Known limits

No new live-provider, physical-device or TalkBack acceptance was run. Broader OEM
coverage remains unverified. Weather data is retained across Activity recreation,
but process-death restoration is not implemented. Manual city queries still leave
the device over HTTPS; removing device location does not mean no data collection.

## Verified internal distribution

| Version code | Evidence | Scope |
| --- | --- | --- |
| 1 | Initial manually uploaded 1.0 bundle, recorded 2026-10-25 | Historical owner-only bootstrap |
| 2 | [First automated release](https://github.com/sivica/weatherapp/actions/runs/40192544804) | Owner-only internal |
| 3 | [Branding release](https://github.com/sivica/weatherapp/actions/runs/40196639232) | Owner-only internal |
| 4 | [Quiet precision release](https://github.com/sivica/weatherapp/actions/runs/40206782524) | Owner-only internal |
| 5 | [Privacy-link release](https://github.com/sivica/weatherapp/actions/runs/40211564673), source `ed4c11036458500a371617401b7e71cee398b1a1` | Last verified available internal release; contains optional device location |

The manual-city-only change has not yet been released. A successful upload does
not verify Play installation on the owner's own phone. The
[CI guide](../ci/README.md) describes publishing guards and credential handling.

## Historical validation before Quiet precision

These are dated results for the **earlier implementation**, not coverage of the
current UI:

- **2026-10-24:** 21 unit tests and 9 fixture instrumentation tests passed on API 36
  and API 37 emulators; lint reported no issues. Key-free release isolation passed.
- **2026-10-25:** 9 fixture tests passed on a Google-hosted physical Pixel 11/API 37.
  The installed QA APKs were key-free and made no live weather requests.
- **2026-10-25:** a separate private emulator smoke made three fixed-city requests:
  Skopje and London returned HTTP 200; an invalid city returned HTTP 404. This does
  not establish current provider availability or GPS/geocoding accuracy.
- Earlier offline dependency resolution passed after refreshing the selected
  project's writable-cache metadata. No dependency version change or shared-cache
  purge was needed. See the [troubleshooting recipe](build-toolchain.md#dependency-troubleshooting).

Exact commit identifiers for the older device captures were not recorded in this
history. Their counts and images must not be presented as current app acceptance.

## Screenshot provenance

Current README images reuse the genuine native fixture captures already in
[the Quiet precision QA record](quiet-precision/README.md#screenshots). They show
sample Skopje conditions and a sample retry state; the daylight layout pictured is
unchanged by the later metadata/rounding fixes. They are not Figma renders, live
weather or the owner's location. Their fixture-data labels remain visible.

The legacy captures below are retained only as **historical evidence**, not
screenshots of the current interface or a claim of current dark-theme support.
They contain synthetic Zagreb weather: clear sky, 12.5 °C, 18.0 km/h. The files
were copied without pixel edits; visual content and PNG text metadata were
checked for personal content. Their hashes match the original PR captures.

| Historical capture | SHA-256 |
| --- | --- |
| [Legacy light UI, 2026-10-24](images/fixture-light.png) | `3f5edc5400136246dbed4561ec2614304d2a35d1e599588834fc839fbcaff8a7` |
| [Legacy dark UI, physical Pixel 11, 2026-10-25](images/fixture-pixel11-dark.png) | `0c0fb857ab1f58bb8d39c803542769b086cbf50eac06447abb3555d95916cf83` |
