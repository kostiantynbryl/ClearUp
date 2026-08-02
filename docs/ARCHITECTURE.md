# ClearUp architecture

## Layers

- **UI:** Jetpack Compose screens and ViewModels.
- **Domain models:** platform-neutral scan, storage and application entities.
- **Data:** MediaStore scanner, StorageStats, DataStore, SQLite audits and cleanup executors.
- **Privileged adapters:** Root shell and Shizuku UserService implementations kept separate from standard mode.

## Scanner contract

Each result contains an Android content URI, display path, byte size, category, reason and risk level. Rules are deterministic and testable. A rule can recommend selection only when the item is classed as `SAFE`.

## Cleanup contract

- Android 11+: move selected MediaStore items into the system trash with a system confirmation dialog.
- Android 8–10: direct ContentResolver deletion only after an in-app confirmation.
- Privileged cleanup is not mixed into the standard executor; every operation is validated and auditable.

## Privileged application actions

`AppsViewModel` selects one backend in this order:

1. Root, after explicit Root detection.
2. Shizuku/Sui, after binder, API and permission checks.
3. Standard Android system screens.

The Shizuku adapter consists of:

- an AIDL contract;
- a remote `UserService` running with the Shizuku/Sui UID;
- a client-side connection manager;
- a pure Java command policy with a fixed operation allowlist;
- a local audit store.

The remote service never receives a free-form shell command. It receives an operation identifier, a validated package name and a validated Android user ID, then builds a fixed argument list for `pm` or `am`.

## Theme system

`ThemeMode` supports:

1. System
2. Light
3. Dark
4. AMOLED, using a pure `#000000` background and near-black surfaces

The `ClearUp by NORVEXA` brand remains visible in the top bar, settings and about screen.
