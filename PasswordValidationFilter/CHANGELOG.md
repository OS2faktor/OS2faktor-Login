# Changelog — OS2faktor Password Validation Filter

## [1.2.0]

### Added
- Release builds can now be Microsoft-signed for LSA, so the filter loads on domain controllers with LSA protection (`RunAsPPL`) enabled — see `Signing/README.md`
- Installer and uninstaller are now Authenticode-signed at compile time, removing the SmartScreen warning on install

## [1.1.0]

### Added
- The callback now relays whether the password was changed by the user themselves or by an administrator

### Fixed
- Installer now uses `restartreplace` on the DLL, allowing in-place upgrades without access denied errors when LSASS holds the old DLL locked

## [1.0.0]

Initial release.
