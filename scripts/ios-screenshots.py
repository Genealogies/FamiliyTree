#!/usr/bin/env python3
"""Capture or verify the four app screens on the pinned iOS simulator."""

import argparse
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time

from PIL import Image, ImageChops


ROOT = Path(__file__).resolve().parents[1]
BASELINES = ROOT / "screenshots/ios"
OUTPUT = ROOT / "build/screenshots/ios"
SCREENS = ("login", "dashboard", "settings", "detail", "familytree")
PREFERRED_DEVICE_NAME = "iPhone 17 Pro"
BUNDLE_ID = "com.hannes.familytree"
# The status bar (clock/Wi-Fi/battery) is simulator chrome, not app UI. The
# simctl status_bar override that pins its content is not reliably honored on
# every host, so ignore this band instead of depending on it. Verified clear
# of the topmost app content (screen titles start around y=265 on this device).
IGNORED_TOP_PIXELS = 200
# A plain "iPhone <N>[ Pro]" name. Excludes multi-screen/foldable variants
# (e.g. "iPhone Duo") and non-numbered ones (e.g. "iPhone Air", "iPhone 17e"),
# which aren't safe fallbacks: they may have a different aspect ratio, or
# legitimately expose more than one display themselves.
IPHONE_NAME_RE = re.compile(r"^iPhone (\d+)( Pro)?( Max)?$")


def simctl(*args, **kwargs):
    return subprocess.check_output(
        ["xcrun", "simctl", *args], text=True, **kwargs
    ).strip()


def pick_device_type(supported):
    """Pick PREFERRED_DEVICE_NAME if available, else the newest plain iPhone.

    Returns None if the runtime doesn't support any plain iPhone at all (e.g.
    a trimmed runtime that only ships exotic device types like "iPhone Duo"),
    so callers can try a different runtime instead of silently picking an
    unrelated device.
    """
    exact = next((d for d in supported if d["name"] == PREFERRED_DEVICE_NAME), None)
    if exact:
        return exact
    candidates = []
    for device in supported:
        match = IPHONE_NAME_RE.match(device["name"])
        if match:
            candidates.append((int(match.group(1)), bool(match.group(2)), device))
    if candidates:
        # Highest model number, preferring the "Pro" variant at that number.
        candidates.sort(key=lambda entry: (entry[0], entry[1]), reverse=True)
        return candidates[0][2]
    return None


def resolve_runtime_and_device_type():
    """Pick an available iOS runtime and a matching plain-iPhone device type.

    Xcode versions ship different runtimes/device types over time, and a
    runtime isn't guaranteed to support a plain iPhone at all (some trimmed
    runtimes only ship exotic device types, e.g. "iPhone Duo"). So instead of
    just using the newest runtime, try runtimes from newest to oldest and use
    the first one that supports PREFERRED_DEVICE_NAME or another plain
    iPhone, only falling back to the newest runtime's own device catalog
    (whatever that is) if no runtime has a plain iPhone at all.
    """
    runtimes = json.loads(simctl("list", "runtimes", "--json"))["runtimes"]
    ios_runtimes = [
        runtime
        for runtime in runtimes
        if runtime.get("isAvailable")
        and runtime["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-")
        and runtime.get("supportedDeviceTypes")
    ]
    if not ios_runtimes:
        sys.exit("No available iOS simulator runtime found")
    ios_runtimes.sort(
        key=lambda runtime: tuple(int(part) for part in runtime["version"].split(".")),
        reverse=True,
    )
    for runtime in ios_runtimes:
        device_type = pick_device_type(runtime["supportedDeviceTypes"])
        if device_type is not None:
            return runtime["identifier"], device_type["identifier"]
    # No runtime supports a plain iPhone; fall back to the newest runtime's
    # last device type, though this may not even be an iPhone.
    runtime = ios_runtimes[0]
    device_type = runtime["supportedDeviceTypes"][-1]
    return runtime["identifier"], device_type["identifier"]


def compare(expected_path, actual_path, diff_path):
    with Image.open(expected_path) as expected, Image.open(actual_path) as actual:
        expected = expected.convert("RGB")
        actual = actual.convert("RGB")
        if expected.size != actual.size:
            comparison = Image.new(
                "RGB",
                (expected.width + actual.width, max(expected.height, actual.height)),
                "red",
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
                Image.new("RGB", actual.size, (255, 0, 0)),
                actual,
                mask,
            ).save(diff_path)
            return f"{changed} changed pixels ({changed / (actual.width * actual.height):.2%})"
    return None


def resolve_display_arg(device):
    """Return a simctl `--display` value, or None to let simctl auto-resolve.

    Some hosts expose more than one display and simctl then refuses to guess
    ("Cannot choose between N displays"), requiring an explicit identifier.
    The display's port/device name ("primary", "PurpleMain", ...) is not
    stable across Xcode/simulator versions, so first try without --display at
    all (works whenever simctl can resolve a single main display on its own)
    and only fall back to the connected screen's numeric Screen ID - parsed
    from `enumerate`'s "Connected Screens" section - when that is rejected.
    """
    probe = OUTPUT / f".display-probe-{device}.png"
    try:
        simctl("io", device, "screenshot", str(probe))
        return None
    except subprocess.CalledProcessError:
        pass
    finally:
        probe.unlink(missing_ok=True)
    enumeration = simctl("io", device, "enumerate")
    match = re.search(r"Connected Screens:.*?Screen ID: (\d+)", enumeration, re.DOTALL)
    if not match:
        raise RuntimeError("Could not resolve a unique display for screenshot capture")
    return match.group(1)


def capture(device, screen, destination, display_arg):
    env = dict(os.environ, SIMCTL_CHILD_SCREENSHOT_ROUTE=screen)
    simctl(
        "launch", "--terminate-running-process", device, BUNDLE_ID,
        "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
        "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryM",
        env=env,
    )
    time.sleep(3)
    display_args = [f"--display={display_arg}"] if display_arg else []
    # Require two identical frames so a launch/Compose animation is not recorded.
    previous = None
    for _ in range(20):
        simctl("io", device, "screenshot", *display_args, str(destination))
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
    print(f"Resolved runtime={runtime} device_type={device_type}")
    device = simctl("create", "Screenshot tests", device_type, runtime)
    failures = []
    try:
        simctl("boot", device)
        simctl("bootstatus", device, "-b")
        print(simctl("io", device, "enumerate"))
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
        display_arg = resolve_display_arg(device)
        print(f"Resolved display_arg={display_arg!r}")
        for screen in SCREENS:
            actual = OUTPUT / f"{screen}.png"
            baseline = BASELINES / f"{screen}.png"
            diff = OUTPUT / f"{screen}-diff.png"
            diff.unlink(missing_ok=True)
            capture(device, screen, actual, display_arg)
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
