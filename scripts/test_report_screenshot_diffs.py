"""Unit tests for scripts/report_screenshot_diffs.py's pure logic.

Only the file-collection and markdown-building helpers are covered here;
network/gh calls are exercised manually against a real PR instead, since they
need a GitHub token and repository.
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from report_screenshot_diffs import build_body, collect_ios, marker


class CollectIosTests(unittest.TestCase):
    def test_no_result_file_returns_empty(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(collect_ios(Path(tmp)), [])

    def test_parses_failures_and_locates_images(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            (output_dir / "result.json").write_text(
                json.dumps({"failures": ["dashboard: 42348 changed pixels (1.34%)"]})
            )
            (output_dir / "dashboard.png").write_bytes(b"actual")
            (output_dir / "dashboard-expected.png").write_bytes(b"expected")
            (output_dir / "dashboard-diff.png").write_bytes(b"diff")

            entries = collect_ios(output_dir)

            self.assertEqual(len(entries), 1)
            entry = entries[0]
            self.assertEqual(entry["name"], "dashboard")
            self.assertEqual(entry["detail"], "42348 changed pixels (1.34%)")
            self.assertTrue(entry["expected"].is_file())
            self.assertTrue(entry["actual"].is_file())
            self.assertTrue(entry["diff"].is_file())

    def test_missing_baseline_failure_has_no_diff_image(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            (output_dir / "result.json").write_text(
                json.dumps({"failures": ["login: missing committed baseline"]})
            )
            (output_dir / "login.png").write_bytes(b"actual")

            entries = collect_ios(output_dir)

            self.assertEqual(len(entries), 1)
            self.assertIsNone(entries[0]["expected"])
            self.assertIsNone(entries[0]["diff"])
            self.assertTrue(entries[0]["actual"].is_file())


class BuildBodyTests(unittest.TestCase):
    def test_includes_marker_and_image_links(self):
        entries = [{"name": "dashboard", "detail": "differs"}]
        urls = {
            ("dashboard", "expected"): "https://example.invalid/expected.png",
            ("dashboard", "actual"): "https://example.invalid/actual.png",
            ("dashboard", "diff"): "https://example.invalid/diff.png",
        }

        body = build_body("ios", entries, urls)

        self.assertIn(marker("ios"), body)
        self.assertIn("https://example.invalid/expected.png", body)
        self.assertIn("https://example.invalid/actual.png", body)
        self.assertIn("https://example.invalid/diff.png", body)
        self.assertIn("dashboard", body)

    def test_missing_image_renders_placeholder(self):
        entries = [{"name": "login", "detail": "missing committed baseline"}]

        body = build_body("ios", entries, urls={})

        self.assertIn("–", body)


if __name__ == "__main__":
    unittest.main()
