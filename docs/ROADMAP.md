# Roadmap

## Foundation — implemented in source

- [x] Compose application shell
- [x] Light, Dark, System and AMOLED themes
- [x] ClearUp by NORVEXA branding
- [x] Storage overview
- [x] Risk-aware scanner rules
- [x] System trash workflow
- [x] Analyzer and application manager
- [x] Protected path and package exclusions
- [x] Local history
- [x] Exact duplicate hashing
- [x] WorkManager scan scheduling
- [x] Root allowlist and guarded app actions
- [x] Shizuku state and permission flow
- [x] Verified GitHub Releases update flow

## Deep cleanup

- [ ] Similar-photo perceptual hashing
- [ ] Empty-directory scanner
- [ ] Root orphan-directory detection
- [ ] Media quality analysis
- [ ] Exportable scan reports
- [ ] Configurable custom scan rules

## Privileged backends

- [ ] Shizuku package and cache operations through a dedicated privileged service
- [ ] Root audit-log details for every executed command
- [ ] Accessibility fallback for OEM application-settings screens

## Automation

- [ ] Low-free-space trigger
- [ ] Idle-device constraint
- [ ] Notification actions
- [ ] Optional safe-category cleanup after a separate explicit opt-in design review

## Distribution

- [x] GitHub Releases metadata
- [x] SHA-256 verification
- [x] package-name and signing-certificate verification
- [ ] signed release workflow
- [ ] changelog generation
- [ ] rollback guidance
- [ ] no release until the first complete test cycle
