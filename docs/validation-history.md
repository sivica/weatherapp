# Validation evidence and history

## Current app revision

App code at [`13d8db4483d3b9804143c2061985e5d98671641a`](https://github.com/sivica/weatherapp/commit/13d8db4483d3b9804143c2061985e5d98671641a)
was verified on **2026-10-25** with **25 unit tests**, **12 fixture UI tests on API 36
ARM64**, clean debug/release lint, app/test APK and ordinary release bundle builds,
and key-free release verification. [Post-merge main CI](https://github.com/sivica/weatherapp/actions/runs/40201847773)
passed. The UI and its review fixes have not been published in the last verified
internal release.

The [Quiet precision QA record](quiet-precision/README.md) owns detailed UI
coverage, limits and the current fixture screenshots. Earlier test counts in that
record are labeled separately from its release-review regression results.
Private signed-bundle verification must run again for any future internal release;
ordinary key-free CI is not proof of that step.

## Known limits

Current UI acceptance on API 37 and physical devices, TalkBack, granted-location
behavior and rotation during the permission dialog remain unverified. Broader OEM
coverage and live GPS/geocoding accuracy are also outside the verified scope.
Weather data is retained across Activity recreation, but process-death restoration
is not implemented. Older device/provider runs below do not close these gaps.

## Verified internal distribution

| Version code | Revision / evidence | Scope |
| --- | --- | --- |
| 1 | Initial manually uploaded 1.0 bundle, recorded 2026-10-25 | Historical owner-only internal bootstrap |
| 2 | `071ae861`; [first automated internal release](https://github.com/sivica/weatherapp/actions/runs/40192544804) | Historical owner-only internal release |
| 3 | `ac092805`; [branding internal release](https://github.com/sivica/weatherapp/actions/runs/40196639232) | Last verified published 1.0 internal release; owner-only |

The code-3 workflow completed verification, private signing, internal upload and
readback, and cleanup. That release predates the new UI. A successful upload does
not verify Play installation or acceptance on the owner's own phone. No newer
release is claimed here. The [CI guide](../ci/README.md) owns publishing guards,
version selection and credential handling.

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
