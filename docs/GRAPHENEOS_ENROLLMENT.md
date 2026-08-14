# GrapheneOS Device Owner enrollment

Primary production target: **GrapheneOS on supported Pixel hardware**.

Stock Android remains a **reference/test environment only**. ADB
`dpm set-device-owner` is not the intended production enrollment method.

This procedure uses public Android fully-managed Device Owner provisioning
only. Sentinel does not assume Google Mobile Services, Google Play services,
managed Google Play, Google accounts, or Android Enterprise cloud APIs.

## What Sentinel implements

During Android 12+ fully-managed provisioning, the platform may invoke:

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

ADB is not invoked from the application. Sentinel does not factory-reset
devices, wipe data, or host/upload the APK.

## QR payload

QR provisioning requires the **final signed APK** to be reachable by the
target device at an **HTTPS** URL during setup. Sentinel does not host or
upload that APK. Generate the JSON on a developer workstation after signing:

```bash
./gradlew :provisioning-qr:run --args="--apk /absolute/path/to/signed.apk --url https://example.test/sentinel.apk"
```

See `docs/QR_PROVISIONING.md` for checksum rules, optional Wi-Fi extras, and
how to encode the JSON as a QR image with workstation tooling.

The permanent Sentinel signing keystore remains local and outside this
repository. Never commit keystore files, passwords, or private keys.

## GrapheneOS validation procedure

Perform this only on a **dedicated test Pixel** running current GrapheneOS.
Do not use a personal or production device.

1. Use a dedicated test Pixel running current GrapheneOS.
2. Factory-reset the device. A true Device Owner provisioning test requires
   setup-wizard enrollment, not an already-configured owner.
3. During initial setup, attempt **standards-compliant QR provisioning** if
   the GrapheneOS setup flow exposes the platform enrollment entry point
   (the same public Android fully-managed QR contract used on AOSP).
4. Point the QR payload at the final **signed** Sentinel APK over HTTPS.
5. Verify Sentinel becomes Device Owner.
6. Open Sentinel and confirm the dashboard reports **Verified Device Owner**.
7. Verify camera, screen capture, and status-bar controls still function
   through the existing trusted dashboard commands. Enrollment must not have
   auto-disabled those protections.
8. Reboot and confirm Device Owner is preserved and the dashboard still
   reports **Verified Device Owner**.
9. Record any GrapheneOS-specific behavior separately from stock Android.
   Do not treat a stock Android result as a GrapheneOS result.

## GrapheneOS-specific workarounds

**Do not code a GrapheneOS-specific workaround without evidence from a real
device test.**

If GrapheneOS does not expose or accept the standard QR provisioning path,
stop at this standards-compliant implementation and record the exact
real-device blocker. Do not bypass setup security, do not use hidden APIs,
and do not factory-reset or provision from inside Sentinel.

This repository has not yet been validated on a physical GrapheneOS Pixel.
Until that test is performed, GrapheneOS enrollment behavior is **unverified**.

## Stock Android reference

Stock Android / emulator `dpm set-device-owner` remains available as a
development reference path. See `docs/DEVICE_OWNER_TEST_DEVICE.md`. That
path is not production enrollment.
