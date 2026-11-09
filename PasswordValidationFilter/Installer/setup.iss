#define AppId "{{4e569a63-839f-4ba6-8521-bc1be8cb5b3c}"
[Setup]
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Registry]
Root: HKLM; Subkey: "Software\DigitalIdentity"; Flags: uninsdeletekeyifempty
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; Flags: uninsdeletekeyifempty
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "InstallPath"; ValueData: "{app}"
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "version"; ValueData: "{#AppVersion}"
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "clientApiKey"; ValueData: "CHANGE_ME"; Flags: createvalueifdoesntexist   
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "os2faktorBaseUrl"; ValueData: "https://CHANGE-ME-os2faktor-idp-url/"; Flags: createvalueifdoesntexist
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "CallbackLogPath"; ValueData: ""; Flags: createvalueifdoesntexist
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: dword; ValueName: "EnableEventViewerLogging"; ValueData: "0"; Flags: createvalueifdoesntexist

[Files]
Source: "lsa_notification_package_install.ps1"; DestDir: "{tmp}"
Source: "lsa_notification_package_uninstall.ps1"; DestDir: "{app}"

[Run]
Filename: "powershell.exe"; \
  Parameters: "-ExecutionPolicy Bypass -File ""{tmp}\lsa_notification_package_install.ps1"" -Package {#AppName}"; \
  WorkingDir: {app}; Flags: runhidden

[UninstallRun]
Filename: "powershell.exe"; \
  Parameters: "-ExecutionPolicy Bypass -File ""{app}\lsa_notification_package_uninstall.ps1"" -Package {#AppName}"; \
  WorkingDir: {app}; Flags: runhidden
