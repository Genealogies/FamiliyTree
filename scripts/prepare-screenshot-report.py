#!/usr/bin/env python3
"""Prepare PR-base comparisons alongside test failures for the shared reporter."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "composeApp/src/test/snapshots/images"
IOS = "screenshots/ios"
RED_DIFF = ROOT / "screenShotScript/redDiff.py"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def reference_paths(ref, directory):
    return {
        path.decode()
        for path in git("ls-tree", "-r", "--name-only", "-z", ref, "--", directory).split(b"\0")
        if path.endswith(b".png")
    }


def same_pixels(left, right):
    with Image.open(left) as old, Image.open(right) as new:
        return old.size == new.size and ImageChops.difference(
            old.convert("RGBA"), new.convert("RGBA")
        ).getbbox(alpha_only=False) is None


def placeholder(path, size, message):
    image = Image.new("RGB", size, "white")
    ImageDraw.Draw(image).text((8, 8), message, fill="black")
    image.save(path)


def prepare(base_ref, android_build, snapshots, ios_dir, output, head_ref="HEAD"):
    result_path = ios_dir / "result.json"
    result = json.loads(result_path.read_text())
    if not isinstance(result.get("failures"), list):
        raise ValueError("Expected an iOS failures array")
    if not snapshots.is_dir():
        raise FileNotFoundError(f"Missing Android reference artifacts: {snapshots}")
    output.mkdir(parents=True, exist_ok=False)
    staged_build = output / "android-build"
    staged_snapshots = output / "android-snapshots"
    staged_ios = output / "ios"
    failures = staged_build / "paparazzi/failures"
    failures.mkdir(parents=True)
    shutil.copytree(snapshots, staged_snapshots)
    shutil.copytree(ios_dir, staged_ios)
    original_failures = android_build / "paparazzi/failures"
    if original_failures.is_dir():
        shutil.copytree(original_failures, failures, dirs_exist_ok=True)

    for platform, directory in (("android", ANDROID), ("ios", IOS)):
        base_paths = reference_paths(base_ref, directory)
        head_paths = reference_paths(head_ref, directory)
        for path in sorted(base_paths | head_paths):
            old = git("show", f"{base_ref}:{path}") if path in base_paths else None
            new = git("show", f"{head_ref}:{path}") if path in head_paths else None
            if old == new:
                continue
            name = Path(path).stem
            if platform == "android":
                expected = staged_snapshots / "images" / f"{name}.png"
                actual = failures / f"{name}.png"
                diff = failures / f"delta-{name}.png"
                rendered = original_failures / f"{name}.png"
            else:
                expected = staged_ios / f"{name}-expected.png"
                actual = staged_ios / f"{name}.png"
                diff = staged_ios / f"{name}-diff.png"
                rendered = ios_dir / f"{name}.png"
            expected.parent.mkdir(parents=True, exist_ok=True)
            # Inspect reference pixels separately, without disturbing a test failure.
            old_path = output / "old.png"
            new_path = output / "new.png"
            if old is not None:
                old_path.write_bytes(old)
            if new is not None:
                new_path.write_bytes(new)
            if old is not None and new is not None and same_pixels(old_path, new_path):
                continue
            if new is not None:
                if rendered.is_file():
                    shutil.copyfile(rendered, actual)
                else:
                    actual.write_bytes(new)
            if old is not None:
                expected.write_bytes(old)
            available = expected if new is None else actual
            with Image.open(available) as image:
                size = image.size
            if old is None:
                placeholder(expected, size, "New screenshot - no PR-base reference")
            if new is None:
                placeholder(actual, size, "Screenshot removed in this PR")
            if old is None or new is None:
                placeholder(diff, size, "Added screenshot" if old is None else "Removed screenshot")
            else:
                subprocess.check_call([
                    sys.executable, str(RED_DIFF), str(expected), str(actual), str(diff),
                ])
            if platform == "ios":
                result["failures"] = [
                    failure for failure in result["failures"]
                    if failure.split(":", 1)[0].strip() != name
                ] + [f"{name}: PR-base reference changed"]
    for name in ("old.png", "new.png"):
        (output / name).unlink(missing_ok=True)
    (staged_ios / "result.json").write_text(json.dumps(result))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--android-build", type=Path, required=True)
    parser.add_argument("--android-snapshots", type=Path, required=True)
    parser.add_argument("--ios-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.base_ref, args.android_build, args.android_snapshots, args.ios_dir, args.output)


if __name__ == "__main__":
    main()
