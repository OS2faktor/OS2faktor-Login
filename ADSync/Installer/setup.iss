#define AppId "{{e7ecb42f-a16d-414a-aaf9-41658ae9d53c}"
#define AppSourceDir "..\OS2faktorADSync\bin\Debug"
#define AppName "OS2faktorCoreDataSync"
#define AppVersion "1.1.0"
#define AppPublisher "Digital Identity"
#define AppURL "http://digital-identity.dk/"
#define AppExeName "OS2faktorCoreDataSync"

[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}
DefaultDirName={pf}\{#AppPublisher}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputBaseFilename={#AppExeName}
Compression=lzma
SolidCompression=yes
SourceDir={#AppSourceDir}
OutputDir=..\..\..\Installer

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "*.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "*.config"; DestDir: "{app}"; Flags: ignoreversion onlyifdoesntexist
Source: "*.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "*.pdb"; DestDir: "{app}"; Flags: ignoreversion
Source: "*.txt"; DestDir: "{app}"; Flags: ignoreversion

[Run]
Filename: "{app}\OS2faktorADSync.exe"; Parameters: "install" 

[UninstallRun]
Filename: "{app}\OS2faktorADSync.exe"; Parameters: "uninstall"
