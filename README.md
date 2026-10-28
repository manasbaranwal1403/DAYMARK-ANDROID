# Daymark for Android

A native Android port of the Daymark Windows floating countdown widget. This project uses the Android SDK and standard Java APIs; it has no third-party runtime libraries.

## Included features

- Custom event title and target date, with a live countdown preview.
- Floating overlay above other apps through Android's **Display over other apps** permission.
- Drag to reposition and drag the lower-right handle to resize. Position and size are saved.
- Adaptive layout: the year-progress dots appear below the countdown on taller widgets and beside it on wide widgets. The dots show passed days in a muted color, today in the accent color, and remaining days in the main text color.
- Background opacity control that keeps text readable.
- Individual HEX color settings for the background, main text/future dots, supporting text/passed dots, accent/today, and border.
- Lock position/size control.
- Click-through mode, with an ongoing notification action to restore touch interaction. Click-through requires notifications to be enabled; it automatically switches off after 30 minutes as a safety fallback, and never starts enabled after a restart.
- Optional launch on device boot. This is subject to Android/OEM background-start restrictions; if the system blocks it, open Daymark and start the widget manually.
- Local settings stored in app preferences.

## Important Android differences

Android overlay windows are inherently above regular application windows while the service runs, so the pin control locks or unlocks the widget's position and size instead of duplicating a Windows topmost toggle. The Android system permission is required to draw over other apps. To use click-through safely, allow Daymark notifications; while it is on, use the ongoing notification's **Turn click-through OFF** action. A 30-minute safety timeout restores touch interaction even if you cannot get to the notification.

## Build an APK with Android Studio

1. Install Android Studio with Android SDK Platform 35 and JDK 17.
2. Extract this folder and open the `daymark-android` folder as a project.
3. Allow Android Studio to sync the Gradle project.
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The debug APK will be written to `app/build/outputs/apk/debug/app-debug.apk`.
6. Copy the APK to your phone, open it, and allow installation from that source if Android asks.

The app's debug APK is suitable for local testing and sideloading. A distributable/release APK should be signed with your own release key before sharing broadly.

## Build through GitHub Actions

The included workflow `.github/workflows/build-apk.yml` builds a debug APK and uploads it as the artifact `daymark-android-debug-apk` whenever you push to `main`/`master` or manually run the workflow. To use it, upload this project to a GitHub repository, then open **Actions → Build Daymark Android APK → Run workflow**. Download the resulting artifact from the workflow run.

## Permissions

- **Display over other apps**: needed for the floating widget.
- **Notifications**: needed for the foreground service status and reliable click-through recovery action.
- **Run at startup** is opt-in and only starts the widget if overlay permission is already granted.

## Project details

- Application ID: `com.daymark.android`
- Minimum Android version: Android 8.0 (API 26)
- Compile/target SDK: 35
- Java: 17

## Build verification status

This source package was assembled in an environment without an installed Android SDK/Gradle and without network access to download them. The XML and project structure can be checked locally, but an APK was **not compiled or device-tested in this environment**. Android Studio or the included GitHub Actions workflow can produce the APK on a machine/runner with the Android build toolchain.
