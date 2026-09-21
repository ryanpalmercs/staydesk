; Template only — fill in the three REPLACE-ME values in [Registry] on your
; local copy before compiling, and never commit that filled-in version.

[Setup]
AppName=StayDesk Terminal Bridge
AppVersion=1.0
DefaultDirName={autopf}\StayDeskBridge
DisableDirPage=yes
DisableProgramGroupPage=yes
OutputBaseFilename=StayDeskBridgeSetup
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64

[Files]
Source: "bridge-agent.jar"; DestDir: "{app}"
Source: "StayDeskBridge.exe"; DestDir: "{app}"
Source: "StayDeskBridge.xml"; DestDir: "{app}"
Source: "jre\*"; DestDir: "{app}\jre"; Flags: recursesubdirs

[Registry]
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; ValueType: string; ValueName: "BRIDGE_RENDER_URL"; ValueData: "wss://api.martinhousemotel.com/bridge/terminal"
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; ValueType: string; ValueName: "BRIDGE_SHARED_SECRET"; ValueData: "REPLACE-ME"
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; ValueType: string; ValueName: "BRIDGE_TERMINAL_URL"; ValueData: "ws://REPLACE-ME:PORT/tsi/v1/payment"

[Run]
Filename: "{app}\StayDeskBridge.exe"; Parameters: "install"; WorkingDir: "{app}"; Flags: runhidden
Filename: "{app}\StayDeskBridge.exe"; Parameters: "start"; WorkingDir: "{app}"; Flags: runhidden

[UninstallRun]
Filename: "{app}\StayDeskBridge.exe"; Parameters: "stop"; WorkingDir: "{app}"; Flags: runhidden
Filename: "{app}\StayDeskBridge.exe"; Parameters: "uninstall"; WorkingDir: "{app}"; Flags: runhidden
