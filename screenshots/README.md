# UI screenshot checks

Every pull request verifies committed reference screenshots for **Login,
Dashboard, Settings, Dashboard Detail (item 1), and Family Tree** on Android
and iOS. The checks cover the initial light-mode state of each screen, not
interaction flows, dark mode, or every device size. Add a screenshot case when
adding a screen.

CI never records or accepts baselines automatically. A missing baseline or a
visual difference fails the platform's job. Review the uploaded artifacts for
the expected image, actual image, and visual diff before accepting an intentional
UI change.

## Reviewing diffs on the pull request itself

After both platform jobs finish, a single reporting job downloads their test
artifacts and runs `screenShotScript/screenShotCompare.sh --combined`. It uploads
the expected/actual/diff images through GitHub's user-attachments API using the
`CLASSIC_TOKEN` repository secret. Each run with differences creates a **new**
PR comment containing both platforms' images, then deletes all previous
screenshot reports (including older platform-specific comments). A run with no
differences removes the previous reports without creating a comment.

The reporting job runs even if screenshot verification fails; the platform
checks remain red when a screenshot differs. If artifacts are unavailable or
posting the new comment fails, previous comments are preserved and the reporting
job fails visibly. Repository secrets are unavailable to fork PRs, so use the
uploaded test artifacts to review their differences.

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

The capture script creates and deletes its own simulator, preferring an
**iPhone 17 Pro** device on the newest available iOS runtime (falling back to
whatever the installed Xcode ships if that device isn't available), forces
light mode, English (US), standard text size, and a fixed status bar. CI
currently builds with **Xcode 27.0** on Apple Silicon macOS. Because the
runtime/device are resolved dynamically, a CI Xcode upgrade keeps working
without code changes, but renderer or OS updates can still shift pixels —
review and regenerate baselines after upgrading the CI toolchain. References
are in `screenshots/ios`.

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
