# Release process

ClearUp releases are published only after the draft release pull request passes compile, unit, lint, R8 and custom safety checks.

## Required public assets

For version `<version>` publish exactly:

```text
ClearUp-<version>.apk
ClearUp-<version>.apk.sha256
```

The checksum file uses the standard format:

```text
<64-character SHA-256>  ClearUp-<version>.apk
```

## First production release

The first merge into `main` runs `.github/workflows/publish-release.yml`.

The workflow:

1. Refuses development, alpha, beta or release-candidate version names.
2. Repeats safety checks, debug compilation, unit tests, lint and R8 analysis.
3. Generates a 4096-bit RSA production signing key outside the repository working tree.
4. Builds a minified and resource-shrunk signed APK.
5. Verifies the APK signature and package name `com.norvexa.clearup`.
6. Generates SHA-256 after signing.
7. Uploads the APK and checksum as private workflow artifacts.
8. Uploads the keystore, certificate information and credentials as a separate private workflow artifact with 90-day retention.
9. Publishes only the APK and checksum to the public GitHub Release.

The private signing-backup artifact must be downloaded immediately and stored in at least two secure offline locations. It must never be attached to a GitHub Release, committed to Git, sent through public chat or copied into the application package.

## Future releases

Every future update must use the exact same production keystore. Before the second release, move the base64-encoded keystore and its credentials into protected GitHub Actions secrets or build locally with the protected key.

Required protected values:

```text
CLEARUP_RELEASE_KEYSTORE
CLEARUP_RELEASE_STORE_PASSWORD
CLEARUP_RELEASE_KEY_ALIAS
CLEARUP_RELEASE_KEY_PASSWORD
```

The release build accepts the corresponding Gradle properties:

```text
clearup.release.storeFile
clearup.release.storePassword
clearup.release.keyAlias
clearup.release.keyPassword
```

## Security requirements

- Verify the package is `com.norvexa.clearup`.
- Verify the signing certificate against the previous production release.
- Never create a second production key for the same package unless updates from the old installation are intentionally abandoned.
- Generate SHA-256 only after final signing.
- Upload APK and checksum to the same GitHub Release.
- Test clean installation and installation over the previous signed version.
- Retain the previous APK and checksum for rollback.

## Verification commands

```bash
apksigner verify --verbose --print-certs ClearUp-<version>.apk
sha256sum ClearUp-<version>.apk
```

## Rollback guidance

Android does not permit a lower `versionCode` to replace a newer installation. A rollback release therefore requires a new higher `versionCode`, the same production signing certificate and the previously stable source state. User data compatibility must be reviewed before publication.
