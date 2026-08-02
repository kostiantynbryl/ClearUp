# ClearUp by NORVEXA

ClearUp is a privacy-first Android storage cleaner. It combines a simple interface with transparent scan rules and safe deletion through Android's system trash confirmation.

## Current foundation

- Kotlin + Jetpack Compose + Material 3.
- Light, Dark, System and true AMOLED themes.
- Storage overview using `StatFs`.
- MediaStore scanner with explicit rules for temporary files, old APKs, screenshots and large files.
- Safe selection model: only low-risk temporary files are preselected.
- Android 11+ system trash flow through `MediaStore.createTrashRequest`.
- Storage category analyzer.
- Installed application manager with optional StorageStats data.
- Local DataStore settings.
- No ads, analytics SDKs, account or cloud processing.

## Development policy

Active work is performed on `develop/clearup-foundation`. No intermediate APK files or GitHub Releases are published. A signed build will be prepared only after the agreed scope is implemented and reviewed.

## Build prerequisites

- Android Studio Quail 2 or newer.
- JDK 17.
- Android SDK 36.
- Gradle 9.5.1 via the checked-in checksum-verified bootstrap scripts.

## Package

`com.norvexa.clearup`

## Safety model

ClearUp never silently removes user media. Files are classified with a risk level, shown to the user and sent to the Android system trash only after confirmation. Root, Shizuku and Accessibility backends will be isolated behind separate privileged adapters before they are enabled.
