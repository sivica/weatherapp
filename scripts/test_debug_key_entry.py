import contextlib
import io
import os
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch
import subprocess

from configure_debug_weather_key import ConfigurationError, prompt_key, save_key

FIXTURE = "FixtureWeatherKey0123456789ABCDEF"


class KeyEntryTests(unittest.TestCase):
    def test_saves_only_owner_read_write_without_console_output(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "debug-weather.properties"
            output = io.StringIO()
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
                save_key(path, FIXTURE)
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            self.assertEqual("OPENWEATHER_API_KEY=" + FIXTURE + "\n", path.read_text())
            self.assertEqual("", output.getvalue())
            self.assertEqual([path], list(Path(directory).iterdir()))

    def test_tightens_existing_permissions_before_replacing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "debug-weather.properties"
            path.write_text("old fixture")
            path.chmod(0o644)
            save_key(path, FIXTURE)
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))

    def test_rejects_symlink_without_changing_target(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "unrelated"
            target.write_text("unchanged")
            link = Path(directory) / "debug-weather.properties"
            link.symlink_to(target)
            with self.assertRaises(OSError):
                save_key(link, FIXTURE)
            self.assertEqual("unchanged", target.read_text())

    def test_rejects_hardlink_without_changing_target(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "unrelated"
            target.write_text("unchanged")
            link = Path(directory) / "debug-weather.properties"
            os.link(target, link)
            with self.assertRaises(ConfigurationError):
                save_key(link, FIXTURE)
            self.assertEqual("unchanged", target.read_text())

    def test_invalid_input_is_never_written_or_in_exception(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "debug-weather.properties"
            invalid = "Fixture\nInjected=not-a-key"
            with self.assertRaises(ConfigurationError) as result:
                save_key(path, invalid)
            self.assertNotIn(invalid, str(result.exception))
            self.assertFalse(path.exists())

    @patch("configure_debug_weather_key.subprocess.run")
    def test_cancel_has_no_value_and_uses_masked_dialog(self, run):
        run.return_value = subprocess.CompletedProcess([], 1, "", "User canceled. (-128)")
        self.assertIsNone(prompt_key(Path("debug-weather.properties")))
        self.assertIn("with hidden answer", run.call_args.args[0][-1])
        self.assertTrue(run.call_args.kwargs["capture_output"])


if __name__ == "__main__":
    unittest.main()
