# OS2faktor Password Validation Filter — Developer Guide

## Build (Windows — native)

### Prerequisites

| Dependency | Notes |
|---|---|
| **CMake** (3.27+) | |
| **Visual C++ SDK** | |
| **MSBuild** | |
| **vcpkg** | `VCPKG_ROOT` environment variable must be set |
| **InnoSetup** | Required for CPack installer generation |

### Build

```powershell
cmake --preset windows
cmake --build build --config Release
cpack --config build\CPackConfig.cmake
```

- `cmake --preset windows` — initializes the project with the Visual Studio generator and vcpkg.
- `cmake --build build --config Release` — compiles the DLL in Release mode.
- `cpack --config build/CPackConfig.cmake` — creates the installer via InnoSetup.

## Build (Linux — cross-compile to Windows)

Cross-compilation uses MinGW to produce a Windows DLL from Linux.

### Prerequisites

| Dependency | Notes |
|---|---|
| **CMake** (3.27+) | |
| **MinGW** | `x86_64-w64-mingw32-g++` |
| **Ninja** | |
| **vcpkg** | `VCPKG_ROOT` environment variable must be set |

### Build

```bash
cmake --preset linux
cmake --build build
```

## Configuration

Registry entries created by the installer under `HKLM\Software\DigitalIdentity\OS2faktorPasswordFilter`:

| Value Name | Type | Description |
|---|---|---|
| `InstallPath` | `REG_SZ` | Installation directory |
| `version` | `REG_SZ` | Installed version |
| `clientApiKey` | `REG_SZ` | API key for authenticating with the OS2faktor server (default: `CHANGE_ME`) |
| `os2faktorBaseUrl` | `REG_SZ` | Base URL of the OS2faktor server (default: `https://CHANGE-ME-os2faktor-idp-url/`) |
| `CallbackLogPath` | `REG_SZ` | Path to the log file (empty = file logging disabled) |
| `EnableEventViewerLogging` | `REG_DWORD` | Enable Event Viewer logging (default: `0` = disabled) |

Additionally, `OS2faktorPasswordValidationFilter` is appended to the `Notification Packages` multi-string value under `HKLM\System\CurrentControlSet\Control\Lsa` to register the password filter with LSA.

## Test

1. Ensure the filter is installed
2. Open **lusrmgr.msc** (Local Users and Groups)
3. Select a user and choose **Reset Password**
4. Enter and confirm a new password
5. Verify the request reached the server (e.g. Mockoon) or check the logs (see [Logging](#logging))

## Logging

| Source | Location |
|---|---|
| **Event Viewer** | Windows Logs → Application under `DigitalIdentity-OS2faktorPasswordFilter` |
| **Log file** | Path specified by the `CallbackLogPath` registry value |

## Uninstall

1. Run the uninstaller
2. Delete `OS2faktorPasswordValidationFilter` from the LSA `Notification Packages` registry value (see [Configuration](#configuration))
3. Restart
4. Delete `OS2faktorPasswordValidationFilter.dll` from `C:\Windows\System32`

## Reinstall

1. Run the uninstaller
2. Delete `OS2faktorPasswordValidationFilter` from the LSA `Notification Packages` registry value (see [Configuration](#configuration))
3. Run the newly-built installer
4. Restart

## Troubleshooting

| Error | Fix |
|---|---|
| `file INSTALL cannot find .../OS2faktorPasswordValidationFilter.dll` | The DLL was built in Debug mode. Run `cmake --build build --config Release` to build the Release DLL, then run `cpack` again. |
