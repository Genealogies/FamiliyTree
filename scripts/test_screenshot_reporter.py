import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


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

    def run_reporter(self):
        return subprocess.run(
            [
                "zsh", str(SCRIPT), "--combined", str(self.root / "build"),
                str(self.root / "snapshots"), str(self.ios), "false",
            ],
            cwd=self.root, env=self.env, capture_output=True, text=True, timeout=20,
        )

    def read_calls(self):
        return [json.loads(line) for line in self.calls.read_text().splitlines()]

    def add_failures(self):
        name = "com.example_ScreenshotTest_dashboard"
        (self.failures / f"{name}.png").write_bytes(b"actual")
        (self.failures / f"delta-{name}.png").write_bytes(b"diff")
        (self.baselines / f"{name}.png").write_bytes(b"expected")
        (self.ios / "result.json").write_text(
            '{"failures":["dashboard: changed pixels"]}'
        )
        for name in ("dashboard", "dashboard-expected", "dashboard-diff"):
            (self.ios / f"{name}.png").write_bytes(b"image")

    def test_posts_one_combined_comment_then_deletes_all_old_reports(self):
        self.add_failures()
        result = self.run_reporter()
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        calls = self.read_calls()
        posts = [call for call in calls if "POST" in call and "-d" in call]
        self.assertEqual(len(posts), 1)
        body = json.loads(posts[0][posts[0].index("-d") + 1])["body"]
        self.assertEqual(body.count("![screenshot]"), 6)
        self.assertIn("<!-- screenshot-compare:all -->\n\n", body)
        self.assertNotIn("<br/>", body)
        deletes = [call for call in calls if "DELETE" in call]
        self.assertEqual(len(deletes), 3)
        for call, comment_id in zip(deletes, (101, 102, 103)):
            self.assertTrue(call[-1].endswith(f"/{comment_id}"))
            self.assertGreater(calls.index(call), calls.index(posts[0]))
        self.assertFalse(any("PATCH" in call for call in calls))

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
