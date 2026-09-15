# Darn Spatial Home

Darn Spatial Home is a real, offline-first Android **Home/launcher** optimized for the Samsung Galaxy S24 Ultra. It uses a hardware-accelerated custom Canvas surface to render layered material panels, perspective page movement, depth-aware parallax, shadows, wallpaper translucency, responsive physics, and haptics. It contains no advertising, analytics, accounts, tracking, or network-dependent features.

## First-version features

- Three horizontally swipable spatial home pages, a four-app dock, wallpaper backdrop, edge-to-edge inset handling, and an S24 Ultra-focused portrait layout.
- Installed-app discovery through Android's public launcher APIs, an app drawer, live app search, and direct launching.
- Long-press an app in the drawer to add it to the first free home slot. Tap shortcuts to launch them. The 18 placements persist locally.
- Tap the upper area of a home page for settings. Animation energy and perspective depth can be adjusted, and **Default Home** opens Android's Home-app settings.
- Returning with the system Home gesture resets the launcher to its first home page.

The drawer opens with an upward swipe (or by tapping the lower portion of the main panel). Swipe horizontally to change pages. Touches produce an expanding cyan surface ripple; pages rotate around a perspective camera while following the finger; and shortcut icons sit above a converging illuminated floor grid. The first four alphabetically discovered apps populate the dock so it works immediately. For the strongest effect, open settings by tapping above the home panel and raise **Perspective depth**.

## Build and download entirely from a phone

The repository intentionally does not commit the binary `gradle-wrapper.jar`. Its checked-in wrapper configuration records Gradle 8.9 for normal developer tooling, while CI safely installs Gradle 8.9 with the official Gradle GitHub Action. This avoids binary-patch transfer problems.

1. Open this repository's **Actions** tab in GitHub.
2. Choose **Android APK**, tap **Run workflow**, and wait for the green build.
3. Open the completed run and download **S24-Ultra-Launcher-debug-apk** under Artifacts.
4. Extract the artifact ZIP and open `app-debug.apk`. Permit installation from GitHub/the browser or file manager when Android asks.
5. Press Home and select **Darn Spatial Home**, or go to **Settings → Apps → Choose default apps → Home app**.

No Android Studio or desktop is required for that process. Android may warn that a debug APK is from an unknown source; this is expected for a self-built artifact. The debug artifact expires after 30 days, but any workflow run creates another.

## Local build

Install Android SDK Platform 35, Java 17, and Gradle 8.9, set `ANDROID_HOME`, then run:

```bash
gradle projects lintDebug testDebugUnitTest assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Java 17, compile/target SDK 35, and minimum SDK 31 are pinned deliberately.

## Permissions, privacy, and Android limitations

The app requests no dangerous permissions and works offline. Android package visibility is limited to launchable activities via a manifest `<queries>` declaration. App data contains only visual preferences and shortcut component names. Uninstalled apps disappear automatically.

Android does not give third-party launchers every One UI capability. Samsung-exclusive animations, task-recents integration, notification badges, widgets, folders, icon packs, and secure-folder apps are not implemented in this stable first version. No private Samsung API, root, Knox change, or custom ROM is required. Widget hosting is a documented future extension rather than a simulated feature.

## Architecture

- `AppRepository` owns app discovery and launching.
- `LauncherPreferences` owns local visual and shortcut state.
- `SpatialLauncherView` owns rendering, animation, gestures, drawer, search, dock, and placement interaction.
- `MainActivity` owns lifecycle, edge-to-edge setup, and meaningful visual settings.

All artwork is original vector or programmatically rendered material; there are no copied launcher assets or runtime image URLs.
