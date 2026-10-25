# Build toolchain

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

The application ID is `ivica.weatherapplication`. UI code uses Java/XML and
ViewBinding. Dependencies are pinned in `app/build.gradle`; the wrapper JAR and
distribution match Gradle's official published checksums.

## Local setup

Install the Android 37.0 platform and Build Tools 36.0.0 through Android Studio's
SDK Manager or the official command-line tools. Set `JAVA_HOME` to JDK 21 and
`ANDROID_HOME` to the SDK, or set the SDK path in an ignored `local.properties`.
Do not commit machine-specific SDK paths or credentials.

Use the [README quick start](../README.md#quick-start) for build and test commands.
Check [private debug key handling](debug-key-setup.md) before using an already
configured checkout.

## Fixture UI tests

Select an available local device explicitly. Instrumentation uses the in-memory
debug fixtures, independent of any local key, and makes no weather-provider calls:

```sh
ANDROID_SERIAL="your-device-serial" ./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Replace `your-device-serial` with the selected device's serial. Reports are under
`app/build/reports/`; connected instrumentation results are under
`app/build/outputs/androidTest-results/connected/`.

## Dependency troubleshooting

Offline builds require the project's dependencies to have been downloaded first.
If artifact resolution fails with an experimental read-only cache, compare with
an ordinary writable cache and refresh only this project's required dependency
graph. Do not purge unrelated shared caches. For a key-free checkout:

```sh
./gradlew --no-daemon --refresh-dependencies :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

The [Gradle dependency-cache documentation](https://docs.gradle.org/current/userguide/dependency_caching.html)
distinguishes artifact files from repository metadata and describes read-only
cache sharing. A past resolution issue is noted in the
[validation history](validation-history.md#historical-validation-before-quiet-precision).

## Compatibility references

- [AGP 9.3.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [Gradle 9.8.2](https://docs.gradle.org/9.8.2/release-notes.html)
- [Gradle Java and AGP compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Android 17 target behavior](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Play target requirements](https://developer.android.com/google/play/requirements/target-sdk)
