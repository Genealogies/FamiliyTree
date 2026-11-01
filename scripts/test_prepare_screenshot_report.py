import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import sys
import unittest
from unittest.mock import patch

from PIL import Image, PngImagePlugin


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    "prepare_report", ROOT / "scripts/prepare-screenshot-report.py"
)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


class PrepareScreenshotReportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.git("init", "-q")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test")
        self.android = self.repo / report.ANDROID
        self.ios = self.repo / report.IOS
        self.android.mkdir(parents=True)
        self.ios.mkdir(parents=True)
        self.artifacts = self.root / "artifacts"
        self.snapshots = self.artifacts / "snapshots"
        (self.snapshots / "images").mkdir(parents=True)
        self.build = self.artifacts / "build"
        self.failures = self.build / "paparazzi/failures"
        self.failures.mkdir(parents=True)
        self.ios_results = self.artifacts / "ios"
        self.ios_results.mkdir()
        (self.ios_results / "result.json").write_text('{"failures":[]}')
        self.output = self.root / "report"

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.repo).decode().strip()

    def commit(self):
        self.git("add", ".")
        self.git("-c", "commit.gpgsign=false", "commit", "-qm", "test", "--allow-empty")
        return self.git("rev-parse", "HEAD")

    def image(self, path, color, size=(20, 30)):
        Image.new("RGB", size, color).save(path)

    def prepare(self, base):
        with patch.object(report, "ROOT", self.repo):
            report.prepare(base, self.build, self.snapshots, self.ios_results, self.output)

    def test_passing_tests_still_report_updated_android_and_ios_references(self):
        for directory in (self.android, self.ios):
            self.image(directory / "screen.png", "white")
        base = self.commit()
        for directory in (self.android, self.ios):
            self.image(directory / "screen.png", "blue")
        self.commit()
        self.image(self.ios_results / "screen.png", "blue")
        self.prepare(base)
        with Image.open(self.output / "android-snapshots/images/screen.png") as image:
            self.assertEqual(image.getpixel((0, 0)), (255, 255, 255))
        with Image.open(self.output / "android-build/paparazzi/failures/screen.png") as image:
            self.assertEqual(image.getpixel((0, 0)), (0, 0, 255))
        with Image.open(self.output / "ios/screen-diff.png") as image:
            self.assertEqual(image.getpixel((0, 0)), (255, 0, 0))
        self.assertEqual(self.result()["failures"], ["screen: PR-base reference changed"])
        self.assertEqual(json.loads((self.ios_results / "result.json").read_text())["failures"], [])

    def test_unchanged_references_do_not_create_reports(self):
        self.image(self.android / "screen.png", "white")
        self.image(self.ios / "screen.png", "white")
        base = self.commit()
        self.commit()
        self.prepare(base)
        self.assertEqual(self.result()["failures"], [])
        self.assertFalse(list((self.output / "android-build/paparazzi/failures").glob("*.png")))

    def test_added_and_removed_screens_have_explicit_placeholder_panels(self):
        for directory in (self.android, self.ios):
            self.image(directory / "removed.png", "white")
        base = self.commit()
        for directory in (self.android, self.ios):
            (directory / "removed.png").unlink()
            self.image(directory / "added.png", "blue")
        self.commit()
        self.prepare(base)
        self.assertEqual(len(self.result()["failures"]), 2)
        for directory in (self.output / "ios", self.output / "android-build/paparazzi/failures"):
            self.assertTrue((directory / "added.png").is_file())
            self.assertTrue((directory / "removed.png").is_file())
        with Image.open(self.output / "ios/removed.png") as image:
            self.assertEqual(image.size, (20, 30))
        self.assertTrue((self.output / "ios/added-expected.png").exists())

    def test_existing_failures_are_kept_and_changed_reference_uses_rendered_actual(self):
        self.image(self.android / "changed.png", "white")
        self.image(self.ios / "changed.png", "white")
        base = self.commit()
        self.image(self.android / "changed.png", "blue")
        self.image(self.ios / "changed.png", "blue")
        self.commit()
        self.image(self.failures / "changed.png", "green")
        self.image(self.failures / "delta-changed.png", "red")
        self.image(self.failures / "unchanged.png", "green")
        self.image(self.failures / "delta-unchanged.png", "red")
        self.image(self.snapshots / "images/unchanged.png", "white")
        self.image(self.ios_results / "changed.png", "green")
        self.image(self.ios_results / "unchanged.png", "green")
        (self.ios_results / "result.json").write_text(json.dumps({
            "failures": ["changed: failed", "unchanged: failed"],
        }))
        self.prepare(base)
        self.assertEqual(len(self.result()["failures"]), 2)
        self.assertIn("unchanged: failed", self.result()["failures"])
        self.assertTrue((self.output / "android-build/paparazzi/failures/delta-unchanged.png").exists())
        for path in ("ios/changed.png", "android-build/paparazzi/failures/changed.png"):
            with Image.open(self.output / path) as image:
                self.assertEqual(image.getpixel((0, 0)), (0, 128, 0))

    def test_metadata_only_changes_do_not_create_visual_reports(self):
        self.image(self.ios / "screen.png", "white")
        base = self.commit()
        metadata = PngImagePlugin.PngInfo()
        metadata.add_text("note", "different encoding, identical pixels")
        Image.new("RGB", (20, 30), "white").save(self.ios / "screen.png", pnginfo=metadata)
        self.commit()
        self.prepare(base)
        self.assertEqual(self.result()["failures"], [])

    def test_dimension_changes_are_reported(self):
        self.image(self.ios / "screen.png", "white")
        base = self.commit()
        self.image(self.ios / "screen.png", "white", (40, 50))
        self.commit()
        self.prepare(base)
        self.assertEqual(len(self.result()["failures"]), 1)
        with Image.open(self.output / "ios/screen.png") as image:
            self.assertEqual(image.size, (40, 50))

    def test_missing_results_fail_without_creating_output(self):
        base = self.commit()
        (self.ios_results / "result.json").unlink()
        with self.assertRaises(FileNotFoundError):
            self.prepare(base)
        self.assertFalse(self.output.exists())

    def test_passing_reference_updates_reach_horizontal_pr_comment(self):
        self.image(self.android / "screen.png", "white")
        self.image(self.ios / "screen.png", "white")
        base = self.commit()
        self.image(self.android / "screen.png", "blue")
        self.image(self.ios / "screen.png", "blue")
        self.commit()
        self.prepare(base)
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        posted = self.root / "posted.json"
        curl = bin_dir / "curl"
        curl.write_text(f"#!{sys.executable}\n" + """
import json, os, sys
args = sys.argv[1:]
url = next(arg for arg in args if arg.startswith("https://"))
if "uploads.github.com" in url:
    print('{"url":"https://github.com/user-attachments/assets/test"}\\n201')
elif "-d" in args:
    with open(os.environ["POSTED"], "w") as out:
        out.write(args[args.index("-d") + 1])
    print('{"id":999}')
elif "/comments" in url:
    print("[]")
else:
    print('{"id":123}')
""")
        curl.chmod(0o755)
        result = subprocess.run(
            ["zsh", str(ROOT / "screenShotScript/screenShotCompare.sh"), "--combined",
             str(self.output / "android-build"), str(self.output / "android-snapshots"),
             str(self.output / "ios"), "false"],
            cwd=self.repo, capture_output=True, text=True, timeout=20,
            env=dict(os.environ, PATH=f"{bin_dir}:{os.environ['PATH']}",
                     POSTED=str(posted), CLASSIC_TOKEN="test",
                     GITHUB_REF_NAME="15/merge", GITHUB_REPOSITORY="example/app"),
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        body = json.loads(posted.read_text())["body"]
        self.assertIn("with 2 screenshot(s)", body)
        self.assertEqual(body.count("![screenshot]"), 2)
        images = list((self.repo / "screenshotDiffs").glob("*.png"))
        self.assertEqual(len(images), 2)
        for path in images:
            with Image.open(path) as image:
                self.assertEqual(image.size, (60, 30))
                self.assertEqual(image.getpixel((0, 0)), (255, 255, 255))
                self.assertEqual(image.getpixel((20, 0)), (255, 0, 0))
                self.assertEqual(image.getpixel((40, 0)), (0, 0, 255))

    def result(self):
        return json.loads((self.output / "ios/result.json").read_text())


if __name__ == "__main__":
    unittest.main()
