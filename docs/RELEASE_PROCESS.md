# Release process

No release is created during feature development.

## Required assets

For version `1.0.0` publish:

```text
ClearUp-1.0.0.apk
ClearUp-1.0.0.apk.sha256
```

The checksum file may contain the standard format:

```text
<64-character SHA-256>  ClearUp-1.0.0.apk
```

## Security requirements

1. Build with the protected NORVEXA release keystore outside the repository.
2. Verify the package is `com.norvexa.clearup`.
3. Verify the signing certificate against the previous production release.
4. Generate SHA-256 after signing and alignment.
5. Upload APK and checksum to the same GitHub Release.
6. Never upload keystore files, passwords or signing properties.
7. Test installation over the previous signed version before publishing the release.

## Suggested local verification

```bash
apksigner verify --verbose --print-certs ClearUp-1.0.0.apk
sha256sum ClearUp-1.0.0.apk > ClearUp-1.0.0.apk.sha256
```
