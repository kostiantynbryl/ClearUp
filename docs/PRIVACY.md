# Privacy baseline

ClearUp processes filenames, paths, file sizes, media metadata and installed-package information locally on the device.

The project does not include:

- analytics SDKs
- advertising SDKs
- account creation
- remote file upload
- remote photo classification
- telemetry enabled by default

## Empty-directory scanner

The scanner reads directory names, canonical paths, modification times and current child counts only inside the fixed public storage roots. It does not upload the directory list and does not inspect file contents. Scan and cleanup summaries are written to the local history database.

## Accessibility helper

The optional Accessibility helper is disabled until the user accepts a separate in-app disclosure and enables the service in Android settings.

For a user-initiated cache request, the helper temporarily inspects the accessibility tree of allowlisted system-settings packages to locate:

- the exact selected application label or package name
- the storage-and-cache entry
- the exact clear-cache control

The helper does not store screen dumps, unrelated window text, user input or content from the selected application. It does not send accessibility information over the network. The latest request state is kept locally so the user can see whether the action completed, failed or was cancelled.

## History export

JSON export is optional and user initiated. The report contains only the local history fields displayed by ClearUp: event type, item count, byte count, note and timestamps. Reports are cached locally and are disclosed to another application only after the user chooses a share target. ClearUp grants that target temporary read access to the selected report URI.

The update checker requests only public release metadata and selected APK assets from the configured ClearUp GitHub repository. Update installation remains user initiated.
