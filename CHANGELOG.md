# ClearUp 0.1.1

Maintenance release focused on real cleanup behavior, permissions, batch cache cleaning and a simpler interface.

## Fixed storage cleaning

- ClearUp now checks storage access before scanning instead of silently converting Android `SecurityException` into an empty result.
- On Android 11 and newer, the cleaning screen explicitly guides the user to the system **All files access** permission required for a complete cleaner scan.
- Permission state is refreshed when returning from Android Settings and scanning starts automatically once access is available.
- After Android confirms a trash operation, ClearUp performs a fresh scan instead of only hiding rows locally.
- Cancelling Android's trash confirmation now leaves the list intact and reports that nothing changed.

## Screenshots are not junk

- Screenshots remain review-only suggestions.
- Screenshots are never automatically selected by Safe mode.
- REVIEW and CAUTION items are excluded from the headline reclaimable-space total.
- The scan UI explicitly labels screenshots as personal files that require manual selection.

## Batch hidden-cache cleanup

- Added multi-select application cache cleanup with search, Select all and a single bottom action.
- Automatic backend priority remains **Root → Shizuku → Accessibility**.
- Root batch cleanup clears only `cache` and `code_cache` for eligible user applications.
- Shizuku batch cleanup uses Android's cache-only package command on supported Android versions.
- Accessibility can process a persistent queue of selected applications, opening their system pages one by one and clicking only an exact safe **Clear cache** control.
- Accessibility still rejects clear-data/storage controls, unknown Settings packages and unknown UI labels.
- Batch progress and failures are visible in the application list.

## Interface

- Primary navigation is reduced to four destinations: Home, Cleanup, Apps and Settings.
- Tools remain available from Home instead of occupying a permanent tab.
- Home now prioritizes two actions: **Check and clean** and **Clean app cache**.
- App cache management is sorted by cache size when Android exposes storage statistics.
- Layout uses clearer hierarchy, larger spacing, grouped surfaces and one primary action per task.
- Updated light/dark palette while retaining System, Light, Dark and AMOLED modes.

## Release safety

- Version code 2 / version 0.1.1.
- Release workflow no longer generates a new signing key for each version.
- It restores the existing private production signing backup and refuses to publish if it is unavailable.
- Before publishing, the signing certificate is compared with the previous production APK to guarantee update compatibility.

## Compatibility

- Android 8.0 or newer.
- Target SDK 36.
- Root, Shizuku and Accessibility are optional; hidden-cache capabilities depend on the available backend.
