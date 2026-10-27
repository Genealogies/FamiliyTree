import importlib.util
from pathlib import Path
import tempfile
import unittest

from PIL import Image


spec = importlib.util.spec_from_file_location(
    "ios_screenshots", Path(__file__).with_name("ios-screenshots.py")
)
screenshots = importlib.util.module_from_spec(spec)
spec.loader.exec_module(screenshots)


class CompareTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.expected = Path(self.directory.name) / "expected.png"
        self.actual = Path(self.directory.name) / "actual.png"
        self.diff = Path(self.directory.name) / "diff.png"
        Image.new("RGB", (10, 10), (100, 100, 100)).save(self.expected)

    def compare(self):
        return screenshots.compare(self.expected, self.actual, self.diff)

    def test_identical_images_pass(self):
        self.actual.write_bytes(self.expected.read_bytes())
        self.assertIsNone(self.compare())

    def test_rounding_noise_passes(self):
        Image.new("RGB", (10, 10), (108, 100, 100)).save(self.actual)
        self.assertIsNone(self.compare())

    def test_one_changed_pixel_fails_and_writes_diff(self):
        expected = Image.new("RGB", (10, 300), (100, 100, 100))
        expected.save(self.expected)
        actual = expected.copy()
        actual.putpixel((0, screenshots.IGNORED_TOP_PIXELS + 1), (109, 100, 100))
        actual.save(self.actual)
        self.assertIn("1 changed pixels", self.compare())
        self.assertTrue(self.diff.is_file())

    def test_resizing_fails(self):
        Image.new("RGB", (11, 10)).save(self.actual)
        self.assertIn("size changed", self.compare())
        self.assertTrue(self.diff.is_file())

    def test_status_bar_band_is_ignored(self):
        # Simulate a tall screenshot where only the status bar differs
        # (e.g. the simctl status_bar override wasn't honored on this host).
        expected = Image.new("RGB", (10, 300), (100, 100, 100))
        expected.save(self.expected)
        actual = expected.copy()
        actual.putpixel((0, screenshots.IGNORED_TOP_PIXELS - 1), (200, 50, 50))
        actual.save(self.actual)
        self.assertIsNone(self.compare())

    def test_change_below_status_bar_band_fails(self):
        expected = Image.new("RGB", (10, 300), (100, 100, 100))
        expected.save(self.expected)
        actual = expected.copy()
        actual.putpixel((0, screenshots.IGNORED_TOP_PIXELS), (200, 50, 50))
        actual.save(self.actual)
        self.assertIn("1 changed pixels", self.compare())


if __name__ == "__main__":
    unittest.main()
