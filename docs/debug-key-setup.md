# Private debug key setup

Run these commands from the repository root.

The source `open_weather_api_key` resource stays empty. A populated tracked
placeholder fails closed instead of being used as a key.
Ordinary checkout release builds keep `BuildConfig.OPENWEATHER_API_KEY` empty and
ignore the local debug file. Debug builds can read the ignored root file
`debug-weather.properties`. The separately protected internal CI overlay can
explicitly embed a key; see [CI and internal release setup](../ci/README.md).
Without a local key, the configuration message and request/location guards remain
active.

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
launch the app, contact a provider or change an account. Historical live validation
of an older revision is recorded in the
[validation history](validation-history.md#historical-validation-before-quiet-precision).
Do not put a real key in documentation.

A configured debug build embeds an **extractable** key in its APK, generated code,
classes and local build state. Keep these outputs private; do not publish them,
attach them to a PR, or use verbose logging/build scans. Debug task outputs are
excluded from build caching and debug tasks cannot store a configuration cache.
Use `--no-build-cache --no-configuration-cache` for private configured builds.
Launching the normal screen does not automatically request weather or location.
Submitting a city or tapping **Use my location** starts the selected lookup. Live
weather requests send the configured key and city query to OpenWeather. The entry
helper itself never launches the app or makes a request.

All instrumentation screen tests use the in-memory fixture transport, independent
of any configured debug key. They do not launch the provider-backed activity.
In ordinary checkout builds, the release field/resource remain empty even when
the debug file is present.

Before entering a real key, run the fixture-only configuration checks:

```sh
python3 -m unittest discover -s scripts -p 'test_debug_key_entry.py'
python3 scripts/verify_debug_key_setup.py
```

The integration checker refuses to run if an existing local key file is present;
it never reads or overwrites a user credential. File creation is exclusive, and
cleanup leaves an independently replaced or edited configuration untouched.
A file lock prevents concurrent entry from overwriting the checker's fixture.
It uses a synthetic marker to verify debug inclusion, release BuildConfig/resource/APK exclusion, release
independence from a malformed file, and rejection of unsafe permissions. It then
removes its temporary local configuration and restores an empty-key debug build.
An unsigned release APK is built only for this fixture isolation check; it is not
signed or published.

The historical exposed key was removed from source in the prior milestone. Its
removal does not revoke it or remove it from upstream history. Provider-side
credentials and rotation are handled separately. Approved private internal builds
may deliberately embed a key; ordinary checkout release builds stay key-free.
Do not restore historical values.
