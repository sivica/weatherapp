#!/usr/bin/env python3
"""macOS masked entry; stores only the ignored owner-only local configuration."""
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys

CONFIG_FILE = Path(__file__).resolve().parents[1] / "debug-weather.properties"


class ConfigurationError(Exception):
    pass


def open_configuration(path, *, exclusive=False):
    """Claim an owner-only file and lock out concurrent entry/checker writes."""
    flags = os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW
    if exclusive:
        flags |= os.O_EXCL
    descriptor = os.open(path, flags, 0o600)
    try:
        fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        info = os.fstat(descriptor)
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_nlink != 1:
            raise ConfigurationError("Destination must be a regular file owned only by this user.")
        os.fchmod(descriptor, 0o600)
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def save_key(path, key, *, exclusive=False):
    """Never put a key into argv, environment, logs, or a temporary file."""
    if not re.fullmatch(r"[A-Za-z0-9]{16,128}", key):
        raise ConfigurationError("Invalid API-key format; no value was displayed.")
    descriptor = open_configuration(path, exclusive=exclusive)
    try:
        os.ftruncate(descriptor, 0)
        with os.fdopen(descriptor, "w", encoding="ascii", closefd=False) as output:
            output.write("OPENWEATHER_API_KEY=" + key + "\n")
            output.flush()
            os.fsync(descriptor)
    finally:
        os.close(descriptor)


def prompt_key(path):
    message = (
        "Enter only your OpenWeather debug API key, not your account password.\n\n"
        "Destination: " + str(path) + "\nOwner-only permissions: 0600. Git ignored.\n\n"
        "Debug APKs/build outputs can expose this key. Keep them private. "
        "No live request will be made by this entry step."
    )
    script = (
        "set response to display dialog " + json.dumps(message, ensure_ascii=False)
        + ' default answer "" with hidden answer buttons {"Cancel", "Save locally"}'
        + ' default button "Save locally" cancel button "Cancel"'
        + ' with title "Weatherapp local debug key"\ntext returned of response'
    )
    result = subprocess.run(["/usr/bin/osascript", "-e", script],
                            capture_output=True, text=True, check=False)
    if result.returncode:
        if "(-128)" in result.stderr:
            return None
        error = re.search(r"\((-\d+)\)", result.stderr)
        code = error.group(1) if error else str(result.returncode)
        raise ConfigurationError("Masked macOS dialog unavailable (OS error " + code + ").")
    return result.stdout.rstrip("\r\n").strip()


def main():
    if sys.platform != "darwin":
        print("This masked-entry helper requires macOS.", file=sys.stderr)
        return 2
    ignored = subprocess.run(["git", "check-ignore", "--quiet", "--no-index", str(CONFIG_FILE)],
                             cwd=CONFIG_FILE.parent, capture_output=True)
    tracked = subprocess.run(["git", "ls-files", "--error-unmatch", str(CONFIG_FILE)],
                             cwd=CONFIG_FILE.parent, capture_output=True)
    if ignored.returncode or tracked.returncode == 0:
        print("Refusing entry: the local configuration must be ignored and untracked.", file=sys.stderr)
        return 2
    try:
        key = prompt_key(CONFIG_FILE)
        if key is None:
            print("Entry cancelled; local configuration unchanged.")
            return 130
        save_key(CONFIG_FILE, key)
        key = None
        print("Debug key saved locally with owner-only permissions. No request was made.")
        return 0
    except ConfigurationError as error:
        # Our errors contain only fixed descriptions or an OS error number.
        print(str(error), file=sys.stderr)
        return 2
    except OSError:
        print("Unable to save the protected local configuration. No value was displayed.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
