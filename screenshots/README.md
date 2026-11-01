# UI screenshot checks

Every pull request verifies committed reference screenshots for **Login,
Members, Family Group, Trees, Settings, all three edit screens, and Family Tree** on Android
and iOS. The checks cover the initial light-mode state of each screen, not
interaction flows, dark mode, or every device size. Add a screenshot case when
adding a screen.

New installations start with empty lists. Each tab supports adding, searching,
sorting, editing, and deleting records, with family-group filters for members
and trees. Member cards show ID, birth, parents, spouses, and family groups;
group cards show name, slug, description, and member count; tree cards show
title, family, root, and the equivalent WordPress shortcode.

Tapping a member opens the native
editor based on the WordPress Pro plugin's member edit form: name components,
gender, repeatable births/deaths/events, parent families (pedigree and LDS
sealing), spouse families (order and events), contacts, notes, custom fields,
additional information, an image gallery, and family-group assignment.
Use the Section selector to switch between these areas.
Children and siblings are derived from parent links; related members can be
opened from the editor. Save validates relationships and writes members and
gallery images, family groups, and tree settings together to the app's private
`members.json`, surviving app restarts. Earlier member-only files are migrated
on load, preserving existing group assignments. Data is saved atomically;
load/save failures are displayed rather than overwriting unreadable data.
Unsaved changes require confirmation before leaving the editor.
Gallery images are limited to 10 per member and 5 MB per image.
Additional information is a multiline text field, not WordPress's rich-text
editor. There is no WordPress server synchronization, media library, publishing
workflow in this local editor. Imported GEDCOM
trees remain separate from the local member list.

The Family Group editor follows the plugin's taxonomy form: name, slug,
description, default tree, possible roots (members without parents), a
Generate Default Tree action, and a filtered Members Page.
The Tree editor includes family/root selection and all eight plugin settings
sections: General, Visibility, Container, Box, Line, Image, Name Text, Other Text.
Colors use six-digit hex notation. Font families are entered as text rather
than downloaded from Google Fonts.

View Tree renders the saved group's actual members and parent/spouse links.
The native preview supports root selection, ancestor/descendant generation
limits, horizontal/vertical positioning, hiding spouses/female descendants,
name/date/gender visibility, and hiding living members' birth dates.
It is **not** a port of the plugin's HTML/CSS renderer: style variants, CSS
backgrounds, box/line/image/text styling, root highlighting, popup/collapse,
generation labels, sibling ordering, marriage icons, web links, GT Frontend,
and Ajax settings are retained but are not rendered by the native preview.
The editor states this limitation explicitly. There are no WordPress authors,
roles, remote pages, or publish/trash semantics in this local app.

Deleting records removes related references; deleting a group does not delete
its members or trees. Tree roots and default trees must belong to their family.
Tabs switch between sections;
the Menu opens Family Tree, Settings, or Logout. Import GED is available on each
list and in Settings. A successful import opens Family Tree with the parsed data;
an invalid file displays an error without replacing the existing tree.
Android also captures each member and tree settings section. Screenshot runs
use fixed sample data and bypass saved genealogy data for
deterministic baselines.

CI never records or accepts baselines automatically. A missing baseline or a
visual difference fails the platform's job. Review the uploaded artifacts for
the expected image, actual image, and visual diff before accepting an intentional
UI change.

## Reviewing diffs on the pull request itself

After both platform jobs finish, a single reporting job downloads their test
artifacts. `scripts/prepare-screenshot-report.py` compares committed reference
images against the PR event's base commit and combines those changes with
test failures in a separate staging directory. The reporting checkout uses
the PR head commit; the base commit is fetched explicitly, so the comparison
does not depend on a shallow checkout containing branch history.
Updated references are reported even when tests pass; added and removed
screenshots use labeled placeholder panels. PNG metadata-only changes are
ignored. A failed test uses its rendered actual image, not the new reference.
Unchanged references with passing tests are omitted.

The job then runs `screenShotScript/screenShotCompare.sh --combined`. It uploads
one horizontal comparison image per failed screenshot through GitHub's
user-attachments API using the `CLASSIC_TOKEN` repository secret. Each image
shows **expected (old) | diff | actual (new)** from left to right; unavailable
panels are labeled, and the original test artifacts remain unchanged.
For changed references, expected means the **PR-base reference**; for test
failures without a reference change, it means the current test reference.
Original baselines and test artifacts are never modified.
Each run with differences creates a **new**
PR comment containing both platforms' comparisons, then deletes all previous
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
`composeApp/src/androidUnitTest/snapshots/images`. Comparison allows no pixel
differences. Paparazzi 2 requires a Java 21 or newer runtime; Android app
bytecode remains targeted to Java 17.

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
host. More than 0.05% of pixels outside that band exceeding the rounding tolerance, or a
dimension change, fails. Red pixels in `*-diff.png` mark differences.

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
