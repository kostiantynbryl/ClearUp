# Roadmap

## First release — implemented in source

- [x] Compose application shell
- [x] Light, Dark, System and AMOLED themes
- [x] ClearUp by NORVEXA branding
- [x] Storage overview
- [x] Risk-aware scanner rules
- [x] Android system trash workflow
- [x] Storage analyzer and application manager
- [x] Protected path and package exclusions
- [x] Local history and JSON export
- [x] Exact duplicate hashing
- [x] WorkManager scan scheduling and notifications
- [x] Root allowlist and guarded app actions
- [x] Root orphan-directory detection and audit
- [x] Shizuku/Sui UserService operations and audit
- [x] Consent-based Accessibility cache fallback
- [x] Empty-directory scanner with canonical-path and symlink safeguards
- [x] Verified GitHub Releases update flow
- [x] Unit, lint, R8 and custom safety verification workflow

## First release completion

- [ ] Green Android CI on the release candidate
- [ ] Final version and changelog
- [ ] Persistent production signing key
- [ ] Signed APK and SHA-256 asset
- [ ] Installation and update-path verification
- [ ] Merge to `main` and publish GitHub Release

## Post-release candidates

These features are intentionally excluded from the first stable release until they receive a separate device-calibration and safety cycle.

- [ ] Similar-photo perceptual hashing
- [ ] Blurred and dark-photo quality suggestions
- [ ] Configurable custom scan rules
- [ ] Low-free-space automation trigger
- [ ] Idle-device constraint
- [ ] Notification actions
- [ ] Optional safe-category cleanup after a separate explicit opt-in design review

## Distribution safeguards

- [x] GitHub Releases metadata
- [x] Mandatory SHA-256 verification
- [x] Package-name and signing-certificate verification
- [ ] Signed release workflow using the same protected key for every update
- [ ] Changelog generation
- [ ] Rollback guidance
- [ ] No public release before the complete verification cycle passes
