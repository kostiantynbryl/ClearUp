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
- Automatic application-action backend selection: Root, then Shizuku/Sui, then the consented Accessibility helper, then standard Android mode.
- Root actions for user apps: cache/code_cache cleanup, force-stop, freeze and unfreeze.
- Shizuku UserService actions for user apps: force-stop, freeze and unfreeze; cache-only cleanup is enabled only on Android 13+.
- User-driven Accessibility cache helper for devices without Root or Shizuku, with a prominent disclosure, explicit consent and a 90-second request timeout.
- Accessibility automation is limited to allowlisted settings packages, exact localized `Clear cache` labels and exact safe resource IDs; gestures and free-form UI automation are disabled.
- Public empty-directory scanner with selectable 7/14/30/90-day age thresholds, no automatic selection and deletion-time revalidation.
- Empty-directory scanning excludes storage roots, `Android/`, symbolic links, inaccessible directories and any folder that is no longer empty.
- Root orphan-directory scanner with fixed path allowlists and an installed-package recheck before deletion.
- Separate local Root and Shizuku operation audits with action, target, exit code and truncated output.
- Exact duplicate detection using local SHA-256.
- Protected path and package exclusions.
- Local scan, cleanup, app-action and update history.
- JSON history export from the app cache through read-only `FileProvider` sharing.
- Periodic WorkManager scanning with charging constraints and notifications; no background deletion.
- Root detection and Shizuku lifecycle/permission integration from the dedicated access screen.
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

ClearUp never silently removes user media. Results include category, reason and risk. Personal files are sent to Android's system trash only after user confirmation. Privileged app actions validate package names, target user apps only in the UI and are blocked for protected packages and ClearUp itself. Shizuku does not expose arbitrary shell execution: the remote service accepts only four fixed operations and constructs argument arrays without a shell interpreter. Because older Android versions do not safely guarantee `pm clear --cache-only`, ClearUp refuses that Shizuku action below Android 13 instead of risking application data.

The Accessibility helper is not declared as a general accessibility tool. It can be enabled only after a separate in-app disclosure and affirmative consent. Every request is started by the user for one selected package, expires after 90 seconds and verifies the target application's label or package name before each click. The service is restricted to allowlisted system-settings packages and never clicks labels such as `Clear storage`, `Clear data`, `Стереть данные` or their supported translations. It does not perform gestures, enter text, press global navigation actions or run cleanup in the background.

Public empty directories are never selected automatically. A candidate must be a canonical descendant of one of the fixed public roots, older than the selected threshold, not a symbolic link and empty at scan time. Immediately before deletion, ClearUp resolves the path again, verifies the allowlist and confirms that the directory still contains no entries. Root package leftovers follow a separate flow and are deleted only after Android confirms that the package is no longer installed.

History reports are created under `cache/reports`, exposed only through the existing non-exported `FileProvider` and shared with a temporary read grant chosen by the user.
