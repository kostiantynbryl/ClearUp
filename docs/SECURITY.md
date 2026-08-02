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
- Root commands are restricted to `cache`, `code_cache`, force-stop, freeze and unfreeze.
- The UI blocks root actions for system apps, protected packages and ClearUp itself.
- Root code never writes to `/system`, `/vendor`, `/product`, boot partitions or app databases.

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
- File names, paths, application data and scan history remain on the device.
