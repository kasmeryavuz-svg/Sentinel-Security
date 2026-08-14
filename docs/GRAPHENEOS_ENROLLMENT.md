# GrapheneOS Device Owner enrollment

Primary production target: **GrapheneOS on supported Pixel hardware**.

Stock Android remains a **reference/test environment only**.

This document uses public Android fully-managed Device Owner provisioning
contracts only. Sentinel does not assume Google Mobile Services, Google Play
services, managed Google Play, Google accounts, or Android Enterprise cloud
APIs.

## Current status

**A. Sentinel is standards-compliant and ready.** The app implements the
public Android 12+ fully-managed provisioning contract
(`GET_PROVISIONING_MODE`, `ADMIN_POLICY_COMPLIANCE`, and log-only
`onProfileProvisioningComplete`). It can participate in Android fully-managed
QR provisioning **when the OS setup flow exposes that platform entry point**.

**B. GrapheneOS production QR enrollment is not yet confirmed.** Current
GrapheneOS SetupWizard2 still requires real platform validation and may not
expose the standard ManagedProvisioning QR entry point. Do not claim GrapheneOS
QR enrollment as supported until a real supported GrapheneOS setup flow
accepts that entry.

**C. ADB remains the practical GrapheneOS test method until then.** Until
GrapheneOS exposes and accepts a suitable provisioning entry point, ADB
`dpm set-device-owner` is the practical **reference/testing** Device Owner
assignment method on GrapheneOS. Do not implement a workaround or bypass.

**D. Do not factory-reset a device merely to discover whether the QR entry
point exists.** Factory reset is not a QR-discovery step.

ADB is not invoked from the application. Sentinel does not factory-reset
devices, wipe data, or host/upload the APK.

## What Sentinel implements

During Android 12+ fully-managed provisioning, if the platform invokes Sentinel:

1. `android.app.action.GET_PROVISIONING_MODE` — Sentinel selects **only**
   fully-managed Device Owner mode (`PROVISIONING_MODE_FULLY_MANAGED_DEVICE`).
   Managed-profile mode is never selected. If fully-managed mode is not
   offered, Sentinel fails closed.
2. `android.app.action.ADMIN_POLICY_COMPLIANCE` — Sentinel verifies the
   current Device Owner relationship through the existing read-only validation
   API. It returns success only for **Verified Device Owner**. It does not
   mutate camera, screen-capture, or status-bar policy during enrollment.
3. `DeviceAdminReceiver.onProfileProvisioningComplete` — log-only. No
   persistence, no policy mutation, no auto-disable of protections.

## QR payload (workstation tooling)

If a platform setup flow later exposes standard QR provisioning, the payload
requires the **final signed APK** to be reachable by the target device at an
**HTTPS** URL during setup. Sentinel does not host or upload that APK.
Generate the JSON on a developer workstation after signing:

```bash
./gradlew :provisioning-qr:run --args="--apk /absolute/path/to/signed.apk --url https://example.test/sentinel.apk"
```

See `docs/QR_PROVISIONING.md` for checksum rules, APK signature verification,
optional Wi-Fi extras, and how to encode the JSON as a QR image with
workstation tooling.

The permanent Sentinel signing keystore remains local and outside this
repository. Never commit keystore files, passwords, or private keys.

## GrapheneOS Device Owner testing today

Perform this only on a **dedicated test Pixel** running current GrapheneOS.
Do not use a personal or production device.

Until GrapheneOS QR enrollment is confirmed, assign Device Owner with the
workstation ADB reference path in `docs/DEVICE_OWNER_TEST_DEVICE.md`. Then:

1. Verify Sentinel becomes Device Owner.
2. Open Sentinel and confirm the dashboard reports **Verified Device Owner**.
3. Verify camera, screen capture, and status-bar controls still function
   through the existing trusted dashboard commands.
4. Reboot and confirm Device Owner is preserved and the dashboard still
   reports **Verified Device Owner**.
5. Record any GrapheneOS-specific behavior separately from stock Android.
   Do not treat a stock Android result as a GrapheneOS result.

## If GrapheneOS later exposes the platform QR entry

Only after a real supported GrapheneOS setup flow is known to accept the
standard ManagedProvisioning QR entry should QR enrollment be treated as a
GrapheneOS production path. That future check still must not bypass setup
security, use hidden APIs, or factory-reset/provision from inside Sentinel.

Do not code a GrapheneOS-specific workaround without evidence from a real
device test. If GrapheneOS does not expose or accept the standard QR path,
stop at this standards-compliant implementation and record the exact
real-device blocker.

## Stock Android reference

Stock Android / emulator `dpm set-device-owner` remains available as a
development reference path. See `docs/DEVICE_OWNER_TEST_DEVICE.md`.
