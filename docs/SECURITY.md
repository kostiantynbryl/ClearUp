# Security model

## Standard cleanup

- Standard mode uses public Android APIs and system confirmation dialogs.
- Personal media is never silently deleted.
- Items are classified as Safe, Review or Caution.
- Log, backup and old-file extensions are never automatically treated as safe.
- Android 11+ cleanup uses the system MediaStore trash request.

## Root

- Root detection runs only after the user opens the dedicated access screen.
- Package names are validated against a strict allowlist pattern before shell execution.
- App actions are restricted to `cache`, `code_cache`, force-stop, freeze and unfreeze.
- The UI blocks Root actions for system apps, protected packages and ClearUp itself.
- Orphan scanning reads only five fixed package-directory roots.
- Entries with invalid package names, installed packages and protected packages are discarded.
- Before deleting an orphan directory, ClearUp repeats `pm path` and refuses deletion if the package exists.
- A deletion path must exactly equal an allowlisted root plus the validated package name.
- No orphan is selected automatically, and deletion requires a separate confirmation.
- Root code never writes to `/system`, `/vendor`, `/product`, boot partitions or app databases.

## Root audit

- Root operations are recorded locally with action, target, success state, exit code and truncated output.
- Commands themselves, file contents and directory listings are not stored in the audit database.
- The audit is limited to the latest 300 entries and can be cleared by the user.

## Shizuku

- Shizuku/Sui status, API version and execution UID are read locally.
- API versions below 11 are rejected because UserService is unavailable.
- Permission is requested only from the dedicated access screen and state refreshes on binder and permission events.
- No privileged operation is executed before explicit permission.
- The UserService accepts only four operations: cache-only cleanup, force-stop, freeze and unfreeze.
- Package names and Android user IDs are validated before command construction.
- Commands are passed as argument arrays directly to `ProcessBuilder`; no shell interpreter or user-provided command text is accepted.
- Cache cleanup uses `pm clear --cache-only` only on Android 13 and newer.
- On Android 8–12, ClearUp refuses Shizuku cache cleanup rather than falling back to `pm clear`, which could erase application data.
- The UI blocks Shizuku actions for system apps, protected packages and ClearUp itself.
- Shizuku operations use a separate local audit database limited to the latest 300 entries.

## Accessibility cache helper

- The helper is a fallback only when Root and Shizuku are unavailable.
- A prominent in-app disclosure and affirmative consent are required before opening Android Accessibility settings.
- ClearUp does not declare `isAccessibilityTool=true` because its primary purpose is storage maintenance rather than disability support.
- Every request is initiated by the user for one selected non-system, non-protected package.
- A request expires after 90 seconds and can be cancelled from the ClearUp setup screen.
- The service receives events only from an explicit allowlist of Android and OEM settings packages.
- Window-content retrieval is used only to identify the selected application, the storage entry and the exact cache-clear control.
- The selected application's exact label or package name must be visible before every click.
- Safe controls are matched through exact localized labels or narrowly named resource IDs.
- Labels for clearing storage or application data are kept in an explicit denylist.
- The service does not perform gestures, enter text, use global navigation actions or accept free-form automation instructions.
- Failed, cancelled and completed requests are persisted locally as the latest session state; completed actions are also written to the common app-action history.

## Application-action backend selection

- Root has priority when it was explicitly detected from the access screen.
- Shizuku/Sui is used when Root is unavailable and a supported server is running with permission.
- The Accessibility helper is used only when Root and Shizuku are unavailable, its service is enabled and consent remains active.
- Standard Android system screens remain available when no automation backend is ready.

## Automation

- WorkManager performs scanning and notification only.
- Background deletion is not implemented.
- Charging and battery constraints can be applied.
- Accessibility requests are never started by WorkManager or another background component.

## Updates

- Release metadata is loaded only from the configured public GitHub repository.
- A companion `.sha256` asset is mandatory.
- The downloaded APK is limited to 300 MB.
- Package name must match ClearUp.
- The signing certificate must match the currently installed app.
- Installation is delegated to Android's system package installer.

## Privacy

- Exact duplicate detection uses local SHA-256 and never uploads file contents.
- Accessibility window text is evaluated in memory and is not stored as a screen dump.
- File names, paths, application data, scan history and privileged-operation audits remain on the device.
