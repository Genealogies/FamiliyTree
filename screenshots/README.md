# UI screenshot checks

Every pull request verifies committed reference screenshots for **Login,
Dashboard, Settings, and Dashboard Detail (item 1)** on Android and iOS. The
checks cover the initial light-mode state of each screen, not interaction flows,
dark mode, or every device size. Add a screenshot case when adding a screen.

CI never records or accepts baselines automatically. A missing baseline or a
visual difference fails the platform's job. Review the uploaded artifacts for
the expected image, actual image, and visual diff before accepting an intentional
UI change.

## Reviewing diffs on the pull request itself

On every PR, whether or not the screenshot check passes, each platform's job
runs `scripts/report_screenshot_diffs.py`. If a screen differs, it uploads the
expected/actual/diff images via GitHub's own user-attachments API (the same
mechanism the web UI uses for drag-and-drop image uploads in issues/PRs, so no
external image host or extra secrets are needed) and posts one PR comment per
platform with a side-by-side table. Re-running the job updates that same
comment in place instead of adding duplicates, and the comment is removed once
the PR's screens match the baselines again. Because it uses the default
`GITHUB_TOKEN`, this comment cannot be posted for pull requests from forks
(that token is read-only there); the dedicated verification step still fails
the job in that case, so the artifacts remain the way to review the diff.

## Android

Paparazzi renders the real Compose `App` on the JVM using a fixed Pixel 5 device
configuration, without an emulator. References are in
`composeApp/src/test/snapshots/images`. Comparison allows no pixel differences.

```shell
./gradlew :composeApp:verifyPaparazziDebug
# Accept intentional changes, then review and commit the updated PNGs:
./gradlew :composeApp:recordPaparazziDebug
```

The HTML comparison report is in `composeApp/build/reports/paparazzi/debug`.
CI uploads it, failure images, and reference screenshots with the Android test
reports.

## iOS

The capture script creates and deletes its own **iPhone 17 Pro / iOS 26.0**
simulator, forces light mode, English (US), standard text size, and a fixed
status bar. CI builds with **Xcode 26.0.1** on Apple Silicon macOS. Use the same toolchain
and runtime when updating references; renderer or OS updates require reviewing
and regenerating baselines. References are in `screenshots/ios`.

The Debug build accepts `SCREENSHOT_ROUTE` only for screenshot capture; Release
builds always start at Login. Capture waits for first-boot notifications to
expire and for identical consecutive frames. Comparison ignores per-channel
rounding differences of up to 8/255 and the top 200px status bar band (clock,
Wi-Fi, battery); that band is simulator chrome, not app UI, and the
`simctl status_bar override` that pins it is not reliably honored on every
host. **Any pixel** outside that band exceeding the rounding tolerance, or a
dimension change, fails. Magenta pixels in `*-diff.png` mark differences.

```shell
python3 -m venv build/screenshot-venv
build/screenshot-venv/bin/pip install -r scripts/requirements-screenshots.txt
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/ios CODE_SIGNING_ALLOWED=NO build
build/screenshot-venv/bin/python scripts/ios-screenshots.py \
  --app build/ios/Build/Products/Debug-iphonesimulator/iosApp.app
# Accept intentional changes, then review and commit the updated PNGs:
build/screenshot-venv/bin/python scripts/ios-screenshots.py --record \
  --app build/ios/Build/Products/Debug-iphonesimulator/iosApp.app
```

Actual images, expected images, diffs, and `result.json` are written under
`build/screenshots/ios` and uploaded with the iOS test reports, even on failure.
The comparator's boundary tests run with:

```shell
build/screenshot-venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
```
