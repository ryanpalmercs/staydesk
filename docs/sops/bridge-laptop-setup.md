---
title: StayDesk — Terminal Bridge Laptop Setup
last_updated: 2026-09-21
pdf_options:
  format: Letter
  margin: 1.25in 1in 1in 1in
stylesheet: ../style/staydesk.css
---

# Terminal Bridge Laptop Setup

One-time technical setup for the front-desk Windows laptop that runs the
`StayDeskBridge` service, connecting StayDesk to the Ingenico terminal.
This is not a daily front-desk task — for day-to-day troubleshooting, see
`emergency-contacts.md`'s "Terminal / bridge down" section instead.

An alternative to manually copying the bundle folder is `bridge-agent/winsw/StayDeskBridge.iss`
— an Inno Setup script that packages the same files into one installer `.exe`
(fills in the three env vars automatically, installs and starts the service).
Fill in the placeholder values on a local copy first; never commit real
secrets into it. Steps below describe the manual-folder path.

---

## Prerequisites

- [ ] Confirm the laptop can reach the terminal's local IP on the network.
- [ ] Confirm the laptop can reach the StayDesk Render backend over the internet.

No separate Java install is needed on the laptop — the bundle below ships
its own portable runtime, so there's nothing to install or fight PATH/version
conflicts over on the target machine itself.

---

## Build the portable bundle (once, ahead of time — not on the laptop)

Assemble one self-contained folder that can be copied wholesale via flash
drive to any Windows machine:

```
StayDeskBridge\
├── StayDeskBridge.exe   (WinSW, renamed from the downloaded release)
├── StayDeskBridge.xml   (from bridge-agent/winsw/)
├── bridge-agent.jar     (from ./gradlew bootJar)
└── jre\                 (portable Temurin 21 JRE — unzipped, not installed)
    └── bin\java.exe
```

- [ ] Build the jar: `./gradlew bootJar` in `bridge-agent/`, copy the output
      into the bundle folder as `bridge-agent.jar`.
- [ ] Download WinSW (`curl -L -o StayDeskBridge.exe
      https://github.com/winsw/winsw/releases/latest/download/WinSW-x64.exe`),
      place it in the bundle folder.
- [ ] Copy `bridge-agent/winsw/StayDeskBridge.xml` into the bundle folder as-is
      — its `<executable>` already points at `%BASE%\jre\bin\java.exe`, a path
      relative to wherever the folder ends up.
- [ ] Download a **portable (`.zip`) Temurin 21 JRE** — not the `.msi`
      installer — from https://adoptium.net/temurin/releases/?version=21
      (choose JRE, Windows, x64, `.zip`). Unzip it, and rename/move the
      extracted top-level folder to `jre` inside the bundle so `jre\bin\java.exe`
      exists.
- [ ] Copy the whole `StayDeskBridge\` folder to a flash drive.

## Install on the laptop

- [ ] Copy the `StayDeskBridge\` folder from the flash drive onto the laptop
      (e.g. `C:\StayDeskBridge\`).
- [ ] Set three Windows environment variables (System Properties → Advanced →
      Environment Variables) — these are machine-specific and are never
      committed to the repo, and are **not** part of the portable bundle:
  - `BRIDGE_RENDER_URL` — the real `wss://<render-app>.onrender.com/bridge/terminal`
  - `BRIDGE_SHARED_SECRET` — the shared secret configured on the backend
  - `BRIDGE_TERMINAL_URL` — the terminal's local WebSocket address (its LAN IP)
- [ ] From an elevated command prompt in that folder, run
      `StayDeskBridge.exe install`.
- [ ] Confirm the service shows **Automatic** startup type in `services.msc`.
- [ ] Start the service and confirm it comes up (see Verification below).

---

## Laptop OS configuration

- [ ] Disable sleep/hibernate (Settings → Power) so the laptop never drops
      off the terminal's network mid-shift.
- [ ] Confirm auto-login is configured for whichever account the service
      should run under.
- [ ] Set Windows Update's active hours / restart window to overnight, after
      nightly settlement — so an automatic reboot never lands mid-checkout.

---

## Verification

- [ ] `services.msc` shows `StayDeskBridge` as **Running**.
- [ ] Bridge agent log (in the install folder) shows successful connections
      to both Render and the terminal.
- [ ] From the StayDesk app, confirm the check-in screen does **not** show
      "Terminal isn't responding" for the paired device.
- [ ] Reboot the laptop and confirm the service starts automatically without
      anyone logging in to launch it manually.
- [ ] Manually stop the service (or end the `java` process) and confirm it
      restarts on its own within about a minute.

---

*Setup complete. Record the date and who performed setup below.*

**Performed by:** ____________________________  **Date:** ____________
