# UI screenshot checks

Every pull request verifies committed reference screenshots for **Login,
Dashboard, Settings, and Dashboard Detail (item 1)** on Android and iOS. The
checks cover the initial light-mode state of each screen, not interaction flows,
dark mode, or every device size. Add a screenshot case when adding a screen.

CI never records or accepts baselines automatically. A missing baseline or a
visual difference fails the platform's job. Review the uploaded artifacts for
the expected image, actual image, and visual diff before accepting an intentional
UI change.

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
