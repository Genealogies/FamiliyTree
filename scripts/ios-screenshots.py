#!/usr/bin/env python3
"""Capture or verify the four app screens on the pinned iOS simulator."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

from PIL import Image, ImageChops


ROOT = Path(__file__).resolve().parents[1]
BASELINES = ROOT / "screenshots/ios"
OUTPUT = ROOT / "build/screenshots/ios"
SCREENS = ("login", "dashboard", "settings", "detail")
PREFERRED_DEVICE_NAME = "iPhone 17 Pro"
BUNDLE_ID = "com.hannes.familytree"
# The status bar (clock/Wi-Fi/battery) is simulator chrome, not app UI. The
# simctl status_bar override that pins its content is not reliably honored on
# every host, so ignore this band instead of depending on it. Verified clear
# of the topmost app content (screen titles start around y=265 on this device).
IGNORED_TOP_PIXELS = 200


def simctl(*args, **kwargs):
    return subprocess.check_output(
        ["xcrun", "simctl", *args], text=True, **kwargs
    ).strip()


def resolve_runtime_and_device_type():
    """Pick the newest available iOS runtime and a matching device type.

    Xcode versions ship different runtimes/device types over time, so instead of
    pinning identifiers we resolve them at run time: use the highest-versioned
    available iOS runtime, then prefer PREFERRED_DEVICE_NAME among the device
    types it supports, falling back to whatever it does support.
    """
    runtimes = json.loads(simctl("list", "runtimes", "--json"))["runtimes"]
    ios_runtimes = [
        runtime
        for runtime in runtimes
        if runtime.get("isAvailable")
        and runtime["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-")
    ]
    if not ios_runtimes:
        sys.exit("No available iOS simulator runtime found")
    runtime = max(
        ios_runtimes,
        key=lambda runtime: tuple(int(part) for part in runtime["version"].split(".")),
    )
    supported = runtime.get("supportedDeviceTypes", [])
    if not supported:
        sys.exit(f"No device types supported by runtime {runtime['identifier']}")
    device_type = next(
        (device for device in supported if device["name"] == PREFERRED_DEVICE_NAME),
        supported[-1],
    )
    return runtime["identifier"], device_type["identifier"]


def compare(expected_path, actual_path, diff_path):
    with Image.open(expected_path) as expected, Image.open(actual_path) as actual:
        expected = expected.convert("RGB")
        actual = actual.convert("RGB")
        if expected.size != actual.size:
            comparison = Image.new(
                "RGB",
                (expected.width + actual.width, max(expected.height, actual.height)),
                "magenta",
            )
            comparison.paste(expected, (0, 0))
            comparison.paste(actual, (expected.width, 0))
            comparison.save(diff_path)
            return f"size changed: {expected.size} -> {actual.size}"
        difference = ImageChops.difference(expected, actual)
        # Ignore tiny per-channel rounding noise, but no changed pixels above it.
        mask = difference.point(lambda value: 255 if value > 8 else 0)
        red, green, blue = mask.split()
        mask = ImageChops.lighter(ImageChops.lighter(red, green), blue)
        mask.paste(0, (0, 0, mask.width, IGNORED_TOP_PIXELS))
        changed = mask.histogram()[255]
        if changed:
            Image.composite(
                Image.new("RGB", actual.size, "magenta"),
                actual,
                mask,
            ).save(diff_path)
            return f"{changed} changed pixels ({changed / (actual.width * actual.height):.2%})"
    return None


def capture(device, screen, destination):
    env = dict(os.environ, SIMCTL_CHILD_SCREENSHOT_ROUTE=screen)
    simctl(
        "launch", "--terminate-running-process", device, BUNDLE_ID,
        "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
        "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryM",
        env=env,
    )
    time.sleep(3)
    # Require two identical frames so a launch/Compose animation is not recorded.
    previous = None
    for _ in range(20):
        # Pin the display explicitly: some runners expose more than one host
        # display, and simctl refuses to guess which one to capture from
        # ("Cannot choose between 2 displays") without this.
        simctl("io", device, "screenshot", "--display=internal", str(destination))
        with Image.open(destination) as image:
            current = image.convert("RGB").tobytes()
        if current == previous:
            return
        previous = current
        time.sleep(0.5)
    raise RuntimeError(f"{screen}: UI did not settle")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record", action="store_true")
    parser.add_argument("--app", type=Path, required=True, help="Built Debug iosApp.app")
    args = parser.parse_args()
    if not args.app.is_dir():
        parser.error(f"App bundle not found: {args.app}")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if args.record:
        BASELINES.mkdir(parents=True, exist_ok=True)
    runtime, device_type = resolve_runtime_and_device_type()
    device = simctl("create", "Screenshot tests", device_type, runtime)
    failures = []
    try:
        simctl("boot", device)
        simctl("bootstatus", device, "-b")
        simctl("ui", device, "appearance", "light")
        simctl(
            "status_bar", device, "override", "--time", "9:41",
            "--dataNetwork", "wifi", "--wifiMode", "active", "--wifiBars", "3",
            "--batteryState", "charged", "--batteryLevel", "100",
        )
        simctl("install", device, str(args.app.resolve()))
        # A fresh iOS runtime can display first-boot system notification banners.
        simctl("launch", device, BUNDLE_ID)
        time.sleep(30)
        for screen in SCREENS:
            actual = OUTPUT / f"{screen}.png"
            baseline = BASELINES / f"{screen}.png"
            diff = OUTPUT / f"{screen}-diff.png"
            diff.unlink(missing_ok=True)
            capture(device, screen, actual)
            if args.record:
                baseline.write_bytes(actual.read_bytes())
                print(f"Recorded {screen}")
            elif not baseline.is_file():
                failures.append(f"{screen}: missing committed baseline")
            else:
                (OUTPUT / f"{screen}-expected.png").write_bytes(baseline.read_bytes())
                failure = compare(baseline, actual, diff)
                if failure:
                    failures.append(f"{screen}: {failure}")
        (OUTPUT / "result.json").write_text(json.dumps({"failures": failures}, indent=2))
    finally:
        try:
            simctl("shutdown", device)
        finally:
            simctl("delete", device)
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
