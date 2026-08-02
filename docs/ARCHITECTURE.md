# ClearUp architecture

## Layers

- **UI:** Jetpack Compose screens and ViewModels.
- **Domain models:** platform-neutral scan, storage and application entities.
- **Data:** MediaStore scanner, public-directory scanner, StorageStats, DataStore, SharedPreferences session state, SQLite audits, report export and cleanup executors.
- **Privileged adapters:** Root shell and Shizuku UserService implementations kept separate from standard mode.
- **User-assisted adapter:** a narrowly scoped AccessibilityService that operates only inside allowlisted system-settings packages.

## Scanner contract

Each MediaStore result contains an Android content URI, display path, byte size, category, reason and risk level. Rules are deterministic and testable. A rule can recommend selection only when the item is classed as `SAFE`.

The empty-directory scanner uses a separate filesystem contract because directories are not represented reliably as MediaStore items:

- only fixed public storage roots are traversed;
- canonical path policy is platform-neutral and unit tested;
- symlinks and `Android/` are excluded;
- traversal is cancellable and bounded;
- candidates are never preselected.

## Cleanup contract

- Android 11+: move selected MediaStore items into the system trash with a system confirmation dialog.
- Android 8–10: direct ContentResolver deletion only after an in-app confirmation.
- Empty directories: resolve and validate the canonical path again, confirm the directory is still empty, then delete only that directory.
- Privileged cleanup is not mixed into the standard executor; every operation is validated and auditable.
- Accessibility cache cleanup is a user-driven system-settings workflow, not a background cleanup executor.

## Application actions

`AppsViewModel` selects one backend in this order:

1. Root, after explicit Root detection.
2. Shizuku/Sui, after binder, API and permission checks.
3. Accessibility, after a prominent disclosure, affirmative consent and enabled-service verification.
4. Standard Android system screens.

The Shizuku adapter consists of:

- an AIDL contract;
- a remote `UserService` running with the Shizuku/Sui UID;
- a client-side connection manager;
- a pure Java command policy with a fixed operation allowlist;
- a local audit store.

The remote service never receives a free-form shell command. It receives an operation identifier, a validated package name and a validated Android user ID, then builds a fixed argument list for `pm` or `am`.

## Accessibility cache workflow

The fallback consists of:

- `AccessibilityCacheCoordinator`, which persists consent, one active request and its terminal state;
- `AccessibilityCachePolicy`, a pure Kotlin allowlist/denylist policy covered by unit tests;
- `ClearUpAccessibilityService`, a finite-state controller with no gesture capability;
- `AccessibilitySetupScreen`, which presents disclosure, consent, service status and cancellation;
- the application manager integration that starts a request for one selected app and opens its Android details page.

The finite-state flow is:

1. `WAITING_APP_DETAILS`
2. `WAITING_STORAGE_PAGE`
3. `COMPLETED`, `FAILED` or `CANCELLED`

A request expires after 90 seconds. Before each click, the service confirms that the current accessibility tree belongs to an allowlisted settings package and contains the selected app's exact label or package name. It clicks only an exact storage label/resource ID or exact clear-cache label/resource ID. Clear-data and clear-storage labels are explicitly denied.

## Empty-directory workflow

- `EmptyDirectoryPolicy` defines canonical descendant and depth rules without Android dependencies.
- `EmptyDirectoryRepository` performs bounded scanning and deletion-time validation on `Dispatchers.IO`.
- `EmptyDirectoriesViewModel` keeps selection explicit and records scan/cleanup history.
- `EmptyDirectoriesScreen` handles storage permission, age thresholds, review and confirmation.

After deletion, the repository scans again so skipped, failed or newly changed directories remain accurately represented in the UI.

## Report export

`ReportExporter` serializes the current local history snapshot to JSON in `cache/reports`, keeps at most ten cached reports and returns an `ACTION_SEND` intent containing a FileProvider URI. The export surface receives no raw filesystem path and only a temporary read grant.

## Theme system

`ThemeMode` supports:

1. System
2. Light
3. Dark
4. AMOLED, using a pure `#000000` background and near-black surfaces

The `ClearUp by NORVEXA` brand remains visible in the top bar, settings and about screen.
