#define AppId "{{4e569a63-839f-4ba6-8521-bc1be8cb5b3c}"
#define AppSourceDir "..\"
#define AppName "OS2faktorPasswordValidationFilter"
#define AppVersion "1.0.0"
#define AppPublisher "Digital Identity"
#define AppURL "http://digital-identity.dk/"
#define AppExeName "OS2faktorPasswordValidationFilter"

[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}
DefaultDirName={commonpf}\{#AppPublisher}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputBaseFilename={#AppExeName}
Compression=lzma
SolidCompression=yes
SourceDir={#AppSourceDir}
OutputDir=Installer
ArchitecturesInstallIn64BitMode=x64 ia64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "x64\Release\OS2faktorPasswordValidationFilter.dll"; DestDir: "{sys}";
Source: "OS2faktorBackendCallback\bin\Release\net8.0\*.exe"; DestDir: "{app}\OS2faktorBackendCallback"; Flags: ignoreversion
Source: "OS2faktorBackendCallback\bin\Release\net8.0\*.dll"; DestDir: "{app}\OS2faktorBackendCallback"; Flags: ignoreversion
Source: "OS2faktorBackendCallback\bin\Release\net8.0\*.pdb"; DestDir: "{app}\OS2faktorBackendCallback"; Flags: ignoreversion
Source: "OS2faktorBackendCallback\bin\Release\net8.0\*.json"; DestDir: "{app}\OS2faktorBackendCallback"; Flags: ignoreversion

[Registry]
Root: HKLM; Subkey: "Software\DigitalIdentity"; Flags: uninsdeletekeyifempty
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; Flags: uninsdeletekeyifempty
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "InstallPath"; ValueData: "{app}"
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "version"; ValueData: "{#AppVersion}"
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "clientApiKey"; ValueData: "CHANGE_ME"; Flags: createvalueifdoesntexist   
Root: HKLM; Subkey: "Software\DigitalIdentity\OS2faktorPasswordFilter"; ValueType: string; ValueName: "os2faktorBaseUrl"; ValueData: "https://CHANGE-ME-os2faktor-idp-url/"; Flags: createvalueifdoesntexist
Root: HKLM; Subkey: "System\CurrentControlSet\Control\Lsa"; ValueType: multisz; ValueName: "Notification Packages"; ValueData: "{olddata}{break}OS2faktorPasswordValidationFilter";

[Run]

[UninstallRun]

