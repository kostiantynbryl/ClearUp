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

- Shizuku status is read locally.
- Permission is requested only from the dedicated access screen.
- No privileged operation is executed before explicit permission.

## Automation

- WorkManager performs scanning and notification only.
- Background deletion is not implemented.
- Charging and battery constraints can be applied.

## Updates

- Release metadata is loaded only from the configured public GitHub repository.
- A companion `.sha256` asset is mandatory.
- The downloaded APK is limited to 300 MB.
- Package name must match ClearUp.
- The signing certificate must match the currently installed app.
- Installation is delegated to Android's system package installer.

## Privacy

- Exact duplicate detection uses local SHA-256 and never uploads file contents.
- File names, paths, application data, scan history and Root audit remain on the device.
