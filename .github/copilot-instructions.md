# Copilot instructions

## Project

- This is a Kotlin Multiplatform app named `FamilyTree`, using Compose Multiplatform for Android and iOS.
- The shared app UI and navigation live in `hannes`.
- Android-specific entry-point code is in `composeApp/src/androidMain/`; iOS-specific Kotlin code is in `composeApp/src/iosMain/`. The native iOS app and its Xcode project are under `iosApp/`.
- The iOS app uses a SwiftUI entry point to host the shared Compose UI. Its Xcode build invokes Gradle to embed and sign the Kotlin framework.
- The iOS deployment target is iOS 15.0; the Kotlin framework targets device and simulator architectures.
- The Android application namespace and Kotlin package are `com.hannes.familytree`.

## Implementation

- Put UI and logic in `commonMain` when they can be shared. Keep platform APIs and platform entry points in their respective source sets.
- Follow the existing Compose patterns: screens are composable functions, navigation is represented by `Route` and dispatched by `AppRouter`, and screen interactions are passed through callbacks.
- Use Kotlin's official code style, preserve the existing package and directory structure, and keep changes focused.
- Reuse the current Compose and Kotlin dependencies where possible. If a dependency is needed, declare it in the appropriate source set in `composeApp/build.gradle.kts`.
- Do not add credentials, generated build outputs, or machine-specific configuration to source control.

## Build and verification

- Use the Gradle wrapper (`./gradlew`) rather than a locally installed Gradle.
- For an Android debug build, run `./gradlew :composeApp:assembleDebug`.
- For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and build the `iosApp` scheme; its build phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`.
- For broader multiplatform verification, run `./gradlew build` on a machine with the required platform toolchains (including Xcode for Apple targets).
- Shared tests live in `composeApp/src/commonTest`. Run Android unit tests with `./gradlew :composeApp:testDebugUnitTest`.
- On macOS, run shared tests on an installed iOS simulator with `./gradlew :composeApp:iosX64Test -PiosSimulatorDevice="<simulator UDID>"` for Intel, or `:composeApp:iosSimulatorArm64Test` for Apple Silicon. Find UDIDs with `xcrun simctl list devices available`.
- `.github/workflows/ci.yml` builds and tests both platforms on every pull request and supports manual dispatch. Android uses Ubuntu and Java 17; iOS uses an Apple Silicon macOS runner and Xcode 26.0.1 to build the unsigned simulator app and run Kotlin tests. The Xcode project has no native XCTest target.
- CI uploads test reports even on failures and the debug APK after a successful Android build.
- UI screenshot checks run on both platforms. See `screenshots/README.md` for baseline locations, verification, and intentional baseline updates; never automatically accept visual changes in CI.
- On a pull request, `scripts/report_screenshot_diffs.py` posts/updates one PR comment per platform with expected/actual/diff images when a screen differs, using GitHub's user-attachments API (no external server or secrets). It never fails the job itself; the dedicated verification step already does that.
