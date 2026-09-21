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

---

## Prerequisites

- [ ] Install a Java 21 runtime on the laptop (not present by default).
- [ ] Confirm the laptop can reach the terminal's local IP on the network.
- [ ] Confirm the laptop can reach the StayDesk Render backend over the internet.

---

## Install the bridge service

- [ ] Build the bridge agent jar (`./gradlew bootJar` in `bridge-agent/`) and copy
      `bridge-agent.jar` to a working folder on the laptop (e.g. `C:\StayDeskBridge\`).
- [ ] Download [WinSW](https://github.com/winsw/winsw) and place it in the same
      folder, renamed to `StayDeskBridge.exe`.
- [ ] Copy `bridge-agent/winsw/StayDeskBridge.xml` into the same folder.
- [ ] Set three Windows environment variables (System Properties → Advanced →
      Environment Variables) — these are machine-specific and are never
      committed to the repo:
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
