#!/usr/bin/env python3
"""Fixture-only integration checks. Refuses to run if a user config already exists."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as E

from configure_debug_weather_key import save_key

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "debug-weather.properties"
FIXTURE = "FixtureWeatherKey0123456789ABCDEF"
LOG = ROOT / "build/reports/debug-key-setup/integration.log"


def gradle(stage, tasks, expect_success=True):
    environment = os.environ.copy()
    environment.pop("GRADLE_RO_DEP_CACHE", None)
    result = subprocess.run([str(ROOT / "gradlew"), "--no-daemon", "--offline",
                             "--no-configuration-cache", "--no-build-cache"] + tasks,
                            cwd=ROOT, env=environment, capture_output=True, text=True)
    LOG.parent.mkdir(parents=True, exist_ok=True)
    with LOG.open("a") as log:
        log.write("\nSTAGE: " + stage + "\n" + result.stdout + result.stderr)
    if (result.returncode == 0) != expect_success:
        raise RuntimeError("Gradle integration stage failed: " + stage + ". See the local fixture-only report.")
    return result.stdout + result.stderr


def field(variant):
    candidates = [p for p in (ROOT / "app/build/generated").rglob("BuildConfig.java")
                  if variant in p.parts and "androidTest" not in str(p)]
    if len(candidates) != 1:
        raise RuntimeError("Expected one generated BuildConfig source for " + variant)
    match = re.search(r'OPENWEATHER_API_KEY\s*=\s*("(?:\\.|[^"\\])*")\s*;', candidates[0].read_text())
    if not match:
        raise RuntimeError("Missing generated weather-key field.")
    return json.loads(match.group(1))


def apk_contains(variant, value):
    name = "app-debug.apk" if variant == "debug" else "app-release-unsigned.apk"
    path = ROOT / "app/build/outputs/apk" / variant / name
    with zipfile.ZipFile(path) as archive:
        return any(value.encode("ascii") in archive.read(name) for name in archive.namelist())


def release_resource_empty():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        local = ROOT / "local.properties"
        if local.exists():
            sdk = next((line.split("=", 1)[1] for line in local.read_text().splitlines()
                        if line.startswith("sdk.dir=")), None)
    version = re.search(r'buildToolsVersion\s*=\s*"([^"\n]+)"',
                        (ROOT / "app/build.gradle").read_text()).group(1)
    tool = Path(sdk) / "build-tools" / version / "aapt2"
    apk = ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"
    result = subprocess.run([str(tool), "dump", "resources", str(apk)],
                            capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError("Unable to inspect compiled release resources.")
    lines = result.stdout.splitlines()
    for index, line in enumerate(lines):
        if "string/open_weather_api_key" in line:
            following = [value.strip() for value in lines[index + 1:index + 5] if value.strip()]
            return bool(following) and following[0].endswith('""')
    return False


def main():
    if CONFIG.exists() or CONFIG.is_symlink():
        print("Stopped: existing local configuration was not read or changed.", file=sys.stderr)
        return 2
    ignored = subprocess.run(["git", "check-ignore", "--quiet", "--no-index", str(CONFIG)], cwd=ROOT)
    tracked = subprocess.run(["git", "ls-files", "--error-unmatch", str(CONFIG)], cwd=ROOT,
                             capture_output=True)
    if ignored.returncode or tracked.returncode == 0:
        print("Stopped: local key file must be ignored and untracked.", file=sys.stderr)
        return 2
    try:
        save_key(CONFIG, FIXTURE)
        gradle("fixture debug/release isolation", [":app:assembleDebug", ":app:assembleRelease"])
        assert field("debug") == FIXTURE
        assert field("release") == ""
        assert apk_contains("debug", FIXTURE)
        assert not apk_contains("release", FIXTURE)
        assert release_resource_empty()
        resource = E.parse(ROOT / "app/src/main/res/values/strings.xml").getroot()
        assert not next(value for value in resource if value.get("name") == "open_weather_api_key").text
        print("PASS: fixture reaches debug only; release BuildConfig, resource and APK exclude it.")

        CONFIG.write_text("OPENWEATHER_API_KEY=" + r"\uNOTHEX" + "\n", encoding="ascii")
        gradle("release ignores malformed local file", [":app:generateReleaseBuildConfig", "--rerun-tasks"])
        assert field("release") == ""
        print("PASS: release generation does not read malformed local debug configuration.")

        save_key(CONFIG, FIXTURE)
        CONFIG.chmod(0o644)
        result = gradle("reject unsafe permissions", [":app:generateDebugBuildConfig"], expect_success=False)
        assert "owner-only permissions (0600)" in result
        print("PASS: debug refuses a local key file with unsafe permissions.")
    finally:
        CONFIG.unlink(missing_ok=True)
    gradle("restore missing-key debug build", [":app:assembleDebug", ":app:lintDebug",
            ":app:testDebugUnitTest", ":app:assembleDebugAndroidTest"])
    assert field("debug") == ""
    assert field("release") == ""
    print("PASS: missing-key build restored; debug build, lint, unit tests and test APK pass.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (AssertionError, OSError, RuntimeError):
        print("Configuration isolation checks failed; no real key was used. See the local fixture-only report.", file=sys.stderr)
        sys.exit(1)
