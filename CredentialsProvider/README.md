# OS2faktor Login - Windows Credential Provider

Windows Credential Provider (WCP) for OS2faktor Login - an enterprise authentication system integrating Windows login with the OS2faktor identity provider.

## Features

- **Single Sign-On**: Establish browser SSO sessions directly from Windows login
- **Password Reset**: Reset forgotten passwords from the login screen via MitID
- **Password Change**: Change passwords via Ctrl+Alt+Del, syncing both Windows and OS2faktor

## Prerequisites

- **VC Redistributable x64** - Install silently: `VC_redist.x64.exe /q /norestart`
- **Windows 11 24H2+**: GPO `Windows Components\Windows Logon Options\Enable MPR notification for the system` must be explicitly **enabled**

## Installation

1. Build the MSI with Advanced Installer: `AdvancedInstaller/os2faktor.aip`
2. Install silently: `msiexec /i os2faktor-CredentialProvider.msi /quiet`

## Building

Open the `.sln` files in Visual Studio 2019/2022 and build, or use MSBuild from the command line:

### C++ Projects

```powershell
msbuild CredentialProvider/os2faktorCredentialProvider.sln /p:Configuration=Release /p:Platform=x64
msbuild CredentialManager/CredentialManager.sln /p:Configuration=Release /p:Platform=x64
```

### C# Projects

```powershell
msbuild CredentialProviderFunctionality/CredentialProviderFunctionality.sln /p:Configuration=Release
```

### Browser Extension

```bash
bash extensions/build.sh  # Creates app.zip
```

## LSA Signing (Required for Windows 11 24H2+)

Windows 11 24H2 enforces LSA protection by default. The DLLs must be signed by Microsoft to load into LSASS.

### Prerequisites

1. **SimplySign Desktop** - Install from [Certum](https://support.certum.eu/en/cert-offer-software-and-libraries/)
2. **Windows SDK** - For `signtool.exe`. Add to PATH:
   ```powershell
   # Find signtool location
   Get-ChildItem -Path "C:\Program Files (x86)\Windows Kits" -Recurse -Filter "signtool.exe" | Select-Object FullName
   ```
3. **Partner Center access** - [Hardware Dashboard](https://partner.microsoft.com/dashboard/hardware/Search) with registered EV certificate

### Signing Workflow

```powershell
cd Signing

# 1. Build DLLs first (Release x64), then create CAB
.\prepare-cab.ps1

# 2. Get SimplySign token, enter in SimplySign Desktop, then sign
.\sign-cab.ps1

# 3. Submit Output\os2faktor-lsa.cab to Partner Center (File Signing Services > Submit New LSA)
#    Download Signed_*.zip to Microsoft-Signed-Dlls/

# 4. Extract and integrate signed DLLs
.\integrate-signed.ps1

# 5. Build MSI in AdvancedInstaller (F7)
```

### Verifying Signatures

```powershell
signtool.exe verify /pa /v os2faktorCredentialManager.dll
```

Should show certificate chain ending with "Microsoft Windows Software Compatibility Publisher".

## Configuration

All settings are stored in Windows Registry at:
```
HKEY_LOCAL_MACHINE\SOFTWARE\DigitalIdentity\OS2faktorLogin
```

### Required Settings

| Setting | Type | Description |
|---------|------|-------------|
| `clientApiKey` | REG_SZ | Client key from OS2faktor Login admin portal |
| `clientID` | REG_SZ | Client identifier |
| `os2faktorUserDomain` | REG_SZ | AD domain of users |
| `os2faktorBaseUrl` | REG_SZ | OS2faktor Identity Provider URL (not Selvbetjening) |

### Browser Session Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `EstablishSessionChrome` | DWORD | 1 | Enable/disable SSO for Chrome (1/0) |
| `EstablishSessionEdge` | DWORD | 1 | Enable/disable SSO for Edge (1/0) |
| `EstablishSessionFirefox` | DWORD | 0 | Enable/disable SSO for Firefox (1/0) |
| `CustomParametersChrome` | REG_SZ | | Browser startup params (e.g., `--no-first-run`) |
| `CustomParametersEdge` | REG_SZ | | Browser startup params |
| `CustomParametersFirefox` | REG_SZ | | Browser startup params |
| `WaitForDiskAccess` | REG_SZ | | Path to wait for before session establishment (for roaming profiles, e.g., `G:\`) |

### UI Customization

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `ResetPasswordLinkText` | REG_SZ | "Jeg har glemt mit kodeord" | Text for reset password link on login screen |

### Logging (disabled by default)

| Setting | Description |
|---------|-------------|
| `CredentialManagerLogPath` | General SSO establishment logs |
| `CredentialProviderLogPath` | WCP component logs |
| `CreateSessionLogPath` | Session handover logs (token retrieval) |
| `SessionEstablisherLogPath` | Session handover logs (token-to-browser exchange) |
| `ResetPasswordLogPath` | Password reset from login screen logs |
| `ChangePasswordLogPath` | Ctrl+Alt+Del password change logs |

## Credential Provider Filter

The WCP implements `ICredentialProviderFilter` to selectively filter out other credential providers per usage scenario. Configuration is done via registry keys under:

```
HKEY_LOCAL_MACHINE\SOFTWARE\DigitalIdentity\OS2faktorLogin\Filter\{CLSID}
```

Where `{CLSID}` is the GUID of the credential provider to filter.

### Filter Settings (DWORD, 1 = filter, 0 = allow)

| Setting | Description |
|---------|-------------|
| `FilterLogon` | Filter during Windows logon |
| `FilterUnlockWorkstation` | Filter during workstation unlock |
| `FilterChangePassword` | Filter during Ctrl+Alt+Del password change |
| `FilterCredUi` | Filter during credential UI prompts |
| `FilterPlap` | Filter during Pre-Logon Access Provider scenarios |

### Example

To filter a credential provider with GUID `{12345678-1234-1234-1234-123456789abc}` during logon:

```reg
[HKEY_LOCAL_MACHINE\SOFTWARE\DigitalIdentity\OS2faktorLogin\Filter\{12345678-1234-1234-1234-123456789abc}]
"FilterLogon"=dword:00000001
```
