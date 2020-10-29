#define AppId "{{e59ce7e7-2fc9-48b9-8f82-bda996c833b8}"
#define AppSourceDir "..\OS2faktor Password Agent\bin\Debug"
#define AppName "OS2faktorPasswordAgent"
#define AppVersion "1.0.0"
#define AppPublisher "Digital Identity"
#define AppURL "http://digital-identity.dk/"
#define AppExeName "OS2faktorPasswordAgent"

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

[Run]
Filename: "{app}\OS2faktor Password Agent.exe"; Parameters: "install" 

[UninstallRun]
Filename: "{app}\OS2faktor Password Agent.exe"; Parameters: "uninstall"
