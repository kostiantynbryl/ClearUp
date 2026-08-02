# ClearUp 0.1.0

First public release of **ClearUp by NORVEXA**.

## Storage cleanup

- Storage overview and category analyzer.
- Risk-aware scanning for temporary files, old APK files, screenshots, empty files and large files.
- Android system-trash flow for personal media.
- Exact duplicate detection using local SHA-256.
- Empty public-directory review with canonical-path, symlink and age safeguards.
- Protected path and package exclusions.

## Application maintenance

- Installed application list with optional storage statistics.
- Root cache/code_cache cleanup, force-stop, freeze and unfreeze for eligible user apps.
- Root orphan-directory review with an installed-package recheck before deletion.
- Shizuku/Sui force-stop, freeze and unfreeze; cache-only cleanup on Android 13 and newer.
- Optional user-consented Accessibility fallback for exact system `Clear cache` controls.

## Privacy and safety

- Local processing only; no advertising or analytics SDKs.
- No account, cloud upload or background deletion.
- Separate local Root and Shizuku audit records.
- Local history with JSON export through a restricted FileProvider.
- Verified self-update flow requiring SHA-256, matching package name and matching signing certificate.
- Light, Dark, System and true AMOLED themes.

## Compatibility

- Android 8.0 or newer.
- Target SDK 36.
- Root and Shizuku are optional.

## Known limitations

- OEM system-settings layouts can differ; the Accessibility helper refuses unknown controls rather than guessing.
- Similar-photo and photo-quality suggestions are reserved for a later release after device calibration.
- The first release requires a complete device test before broad distribution.
