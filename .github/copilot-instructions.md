# Copilot instructions

## Project

- This is a Kotlin Multiplatform app named `KmpDashboardApp`, using Compose Multiplatform for Android and iOS.
- The shared app UI and navigation live in `composeApp/src/commonMain/kotlin/com/example/kmpdashboard/`.
- Android-specific entry-point code is in `composeApp/src/androidMain/`; iOS-specific Kotlin code is in `composeApp/src/iosMain/`. The native iOS app and its Xcode project are under `iosApp/`.
- The iOS app uses a SwiftUI entry point to host the shared Compose UI. Its Xcode build invokes Gradle to embed and sign the Kotlin framework.
- The iOS deployment target is iOS 15.0; the Kotlin framework targets device and simulator architectures.
- The Android application namespace and Kotlin package are `com.example.kmpdashboard`.

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
- Run relevant tests when present. There are currently no test source files in the repository; do not claim tests passed if none were run.
