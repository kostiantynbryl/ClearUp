# Privacy baseline

ClearUp processes filenames, paths, file sizes, media metadata and installed-package information locally on the device.

The project does not include:

- analytics SDKs
- advertising SDKs
- account creation
- remote file upload
- remote photo classification
- telemetry enabled by default

## Accessibility helper

The optional Accessibility helper is disabled until the user accepts a separate in-app disclosure and enables the service in Android settings.

For a user-initiated cache request, the helper temporarily inspects the accessibility tree of allowlisted system-settings packages to locate:

- the exact selected application label or package name
- the storage-and-cache entry
- the exact clear-cache control

The helper does not store screen dumps, unrelated window text, user input or content from the selected application. It does not send accessibility information over the network. The latest request state is kept locally so the user can see whether the action completed, failed or was cancelled.

The update checker requests only public release metadata and selected APK assets from the configured ClearUp GitHub repository. Update installation remains user initiated.
