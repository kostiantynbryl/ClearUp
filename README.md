# ClearUp by NORVEXA

ClearUp is a privacy-first Android storage cleaner for direct distribution outside Google Play. It combines a simple interface with transparent scan rules, local processing and safe Android confirmation flows.

## Implemented source foundation

- Kotlin, Jetpack Compose and Material 3.
- System, Light, Dark and true AMOLED themes.
- Branding: **ClearUp by NORVEXA**.
- Storage overview based on `StatFs`.
- MediaStore scanner with explicit rules for temporary files, old APKs, screenshots, empty files and large files.
- Risk classification: Safe, Review and Caution.
- Safe preselection can be enabled or disabled; `.log`, `.bak` and `.old` always require review.
- Android 11+ system trash flow through `MediaStore.createTrashRequest`.
- Storage category analyzer.
- Installed application manager with optional `StorageStats` data.
- Root actions for user apps: cache/code_cache cleanup, force-stop, freeze and unfreeze.
- Exact duplicate detection using local SHA-256.
- Protected path and package exclusions.
- Local scan, cleanup, app-action and update history.
- Periodic WorkManager scanning with charging constraints and notifications; no background deletion.
- Root detection from an explicit access screen and Shizuku status/permission integration.
- GitHub Releases update checker with mandatory `.sha256`, package-name and signing-certificate verification.
- Local DataStore settings.
- No ads, analytics SDKs, account or cloud processing.

## Development policy

Active work is performed on `develop/clearup-foundation`. No intermediate APK, AAB or GitHub Release is produced. A signed build will be prepared only after the agreed scope is implemented, statically reviewed and ready for a complete device test cycle.

## Build prerequisites

- JDK 17.
- Android SDK 36.
- Gradle 9.5.1 via the checksum-verified bootstrap scripts included in the repository.

## Package

`com.norvexa.clearup`

## Update release contract

A release must contain both assets:

```text
ClearUp-<version>.apk
ClearUp-<version>.apk.sha256
```

The app refuses the update when the hash file is absent, SHA-256 differs, package name differs or the APK is signed with another certificate.

## Safety model

ClearUp never silently removes user media. Results include category, reason and risk. Personal files are sent to Android's system trash only after user confirmation. Root commands validate package names, target user apps only in the UI and are blocked for protected packages and ClearUp itself.
