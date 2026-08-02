# ClearUp architecture

## Layers

- **UI:** Jetpack Compose screens and ViewModels.
- **Domain models:** platform-neutral scan, storage and application entities.
- **Data:** MediaStore scanner, StorageStats, DataStore and cleanup executors.
- **Privileged adapters:** future Root, Shizuku and Accessibility implementations, kept separate from standard mode.

## Scanner contract

Each result contains an Android content URI, display path, byte size, category, reason and risk level. Rules are deterministic and testable. A rule can recommend selection only when the item is classed as `SAFE`.

## Cleanup contract

- Android 11+: move selected MediaStore items into the system trash with a system confirmation dialog.
- Android 8–10: direct ContentResolver deletion only after an in-app confirmation.
- Privileged cleanup: not mixed into the standard executor; every command must be logged and auditable.

## Theme system

`ThemeMode` supports:

1. System
2. Light
3. Dark
4. AMOLED, using a pure `#000000` background and near-black surfaces

The `ClearUp by NORVEXA` brand remains visible in the top bar, settings and about screen.
