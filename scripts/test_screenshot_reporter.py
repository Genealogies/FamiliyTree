import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from PIL import Image, ImageChops


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "screenShotScript/screenShotCompare.sh"


class ScreenshotReporterTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.calls = self.root / "calls.jsonl"
        curl = self.bin / "curl"
        curl.write_text(
            f"#!{sys.executable}\n"
            """
import json, os, sys
from urllib.parse import parse_qs, urlparse
args = sys.argv[1:]
with open(os.environ["MOCK_CALLS"], "a") as out:
    out.write(json.dumps(args) + "\\n")
url = next(arg for arg in args if arg.startswith("https://"))
method = args[args.index("-X") + 1]
if "uploads.github.com" in url:
    print('{"url":"https://github.com/user-attachments/assets/test"}\\n201')
elif method == "POST":
    if os.environ.get("MOCK_FAIL_POST"):
        print('{"message":"Forbidden"}')
        sys.exit(22)
    print('{"id":999}')
elif method == "DELETE":
    pass
elif "/comments?" in url:
    if parse_qs(urlparse(url).query)["page"] == ["1"]:
        comments = [{"id": n, "body": "Unrelated comment"} for n in range(98)]
        comments += [
            {"id": 101, "body": "<!-- screenshot-compare:android --> old"},
            {"id": 102, "body": "<!-- screenshot-diff:ios --> old"},
        ]
    else:
        comments = [{"id": 103, "body": "Screenshot differs: old report"}]
    print(json.dumps(comments))
else:
    print('{"id":123}')
"""
        )
        curl.chmod(0o755)
        self.env = dict(
            os.environ,
            PATH=f"{self.bin}:{os.environ['PATH']}",
            CLASSIC_TOKEN="test-token",
            GITHUB_REF_NAME="6/merge",
            GITHUB_REPOSITORY="example/app",
            MOCK_CALLS=str(self.calls),
        )
        self.ios = self.root / "ios"
        self.ios.mkdir()
        (self.ios / "result.json").write_text('{"failures":[]}')
        self.failures = self.root / "build/paparazzi/failures"
        self.failures.mkdir(parents=True)
        self.baselines = self.root / "snapshots/images"
        self.baselines.mkdir(parents=True)

    def run_reporter(self, mode="--combined"):
        if mode == "--ios":
            directories = [str(self.ios)]
        elif mode == "--paparazzi":
            directories = [str(self.root / "build"), str(self.root / "snapshots")]
        else:
            directories = [
                str(self.root / "build"), str(self.root / "snapshots"), str(self.ios),
            ]
        return subprocess.run(
            ["zsh", str(SCRIPT), mode, *directories, "false"],
            cwd=self.root, env=self.env, capture_output=True, text=True, timeout=20,
        )

    def read_calls(self):
        return [json.loads(line) for line in self.calls.read_text().splitlines()]

    def add_failures(self):
        name = "com.example_ScreenshotTest_dashboard"
        Image.new("RGB", (20, 30), "blue").save(self.failures / f"{name}.png")
        Image.new("RGB", (20, 30), "red").save(self.failures / f"delta-{name}.png")
        Image.new("RGB", (20, 30), "white").save(self.baselines / f"{name}.png")
        (self.ios / "result.json").write_text(
            '{"failures":["dashboard: changed pixels"]}'
        )
        for name, color in (
            ("dashboard", "blue"), ("dashboard-expected", "white"),
            ("dashboard-diff", "magenta"),
        ):
            Image.new("RGB", (20, 30), color).save(self.ios / f"{name}.png")

    def test_posts_one_combined_comment_then_deletes_all_old_reports(self):
        self.add_failures()
        result = self.run_reporter()
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        calls = self.read_calls()
        posts = [call for call in calls if "POST" in call and "-d" in call]
        self.assertEqual(len(posts), 1)
        body = json.loads(posts[0][posts[0].index("-d") + 1])["body"]
        self.assertEqual(body.count("![screenshot]"), 2)
        self.assertIn("with 2 screenshot(s)", body)
        self.assertIn("<!-- screenshot-compare:all -->\n\n", body)
        self.assertNotIn("<br/>", body)
        deletes = [call for call in calls if "DELETE" in call]
        self.assertEqual(len(deletes), 3)
        for call, comment_id in zip(deletes, (101, 102, 103)):
            self.assertTrue(call[-1].endswith(f"/{comment_id}"))
            self.assertGreater(calls.index(call), calls.index(posts[0]))
        self.assertFalse(any("PATCH" in call for call in calls))
        uploads = [call for call in calls if "--data-binary" in call]
        self.assertEqual(len(uploads), 2)
        for call, diff_color in zip(uploads, ((255, 0, 0), (255, 0, 255))):
            path = self.root / "screenshotDiffs" / call[call.index("--data-binary") + 1][1:]
            with Image.open(path) as image:
                self.assertEqual(image.size, (60, 30))
                self.assertEqual(image.getpixel((0, 0)), (255, 255, 255))
                self.assertEqual(image.getpixel((20, 0)), diff_color)
                self.assertEqual(image.getpixel((40, 0)), (0, 0, 255))
        self.assertEqual(len(list((self.root / "screenshotDiffs").glob("*.png"))), 2)
        self.assertTrue((self.ios / "dashboard-expected.png").exists())
        self.assertTrue((self.ios / "dashboard-diff.png").exists())
        self.assertTrue((self.failures / "com.example_ScreenshotTest_dashboard.png").exists())

    def test_missing_expected_and_diff_still_produces_one_comparison(self):
        (self.ios / "result.json").write_text('{"failures":["dashboard: missing baseline"]}')
        Image.new("RGB", (160, 30), "blue").save(self.ios / "dashboard.png")
        result = self.run_reporter()
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        images = list((self.root / "screenshotDiffs").glob("*.png"))
        self.assertEqual(len(images), 1)
        with Image.open(images[0]) as image:
            self.assertEqual(image.size, (480, 30))
            self.assertEqual(image.getpixel((320, 0)), (0, 0, 255))
            for x in (0, 160):
                placeholder = image.crop((x, 0, x + 160, 30))
                self.assertIsNotNone(
                    ImageChops.difference(
                        placeholder, Image.new("RGB", placeholder.size, "white")
                    ).getbbox()
                )

    def test_standalone_modes_upload_one_comparison(self):
        self.add_failures()
        for mode, platform in (("--ios", "ios"), ("--paparazzi", "android")):
            with self.subTest(mode=mode):
                result = self.run_reporter(mode)
                self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
                calls = self.read_calls()
                uploads = [call for call in calls if "--data-binary" in call]
                self.assertEqual(len(uploads), 1)
                posts = [call for call in calls if "POST" in call and "-d" in call]
                body = json.loads(posts[0][posts[0].index("-d") + 1])["body"]
                self.assertIn(f"<!-- screenshot-compare:{platform} -->", body)
                self.assertEqual(body.count("![screenshot]"), 1)
                self.calls.unlink()

    def test_different_dimensions_are_not_resized_or_cropped(self):
        self.add_failures()
        Image.new("RGB", (40, 50), "blue").save(self.ios / "dashboard.png")
        result = self.run_reporter()
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        image_path = next((self.root / "screenshotDiffs").glob("*ios-dashboard.png"))
        with Image.open(image_path) as image:
            self.assertEqual(image.size, (80, 50))
            self.assertEqual(image.getpixel((0, 29)), (255, 255, 255))
            self.assertEqual(image.getpixel((20, 29)), (255, 0, 255))
            self.assertEqual(image.getpixel((20, 30)), (255, 255, 255))
            self.assertEqual(image.getpixel((79, 49)), (0, 0, 255))

    def test_invalid_image_preserves_old_reports(self):
        self.add_failures()
        (self.ios / "dashboard.png").write_bytes(b"invalid")
        self.assertNotEqual(self.run_reporter().returncode, 0)
        self.assertFalse(self.calls.exists())

    def test_no_differences_only_deletes_old_reports(self):
        result = self.run_reporter()
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        calls = self.read_calls()
        self.assertFalse(any("POST" in call for call in calls))
        self.assertEqual(sum("DELETE" in call for call in calls), 3)

    def test_failed_post_preserves_old_reports(self):
        self.add_failures()
        self.env["MOCK_FAIL_POST"] = "1"
        self.assertNotEqual(self.run_reporter().returncode, 0)
        self.assertFalse(any("DELETE" in call for call in self.read_calls()))

    def test_missing_results_preserves_old_reports(self):
        (self.ios / "result.json").unlink()
        self.assertNotEqual(self.run_reporter().returncode, 0)
        self.assertFalse(self.calls.exists())


if __name__ == "__main__":
    unittest.main()
