# Development-only Device Owner testing

> **WARNING:** Perform this workflow only on a dedicated, disposable test
> device or emulator. Never use a customer, employee, production, or
> personally owned device. Device Owner changes the device's management state
> and normally requires a newly prepared device with no accounts.

This document is for development validation only. It is not a production
enrollment workflow. Sentinel does not execute ADB, provisioning commands, or
Device Owner setup from inside the application.

## Prerequisites

- Use a disposable Android test device or emulator that can be safely
  re-created.
- Ensure the device has no user accounts and has not completed an incompatible
  management setup.
- Enable developer options and USB debugging manually on the test device.
- Build and install the debug application through Android Studio or the
  developer workstation's normal Android tooling.

The expected admin component is:

```text
com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver
```

## Supported development provisioning

From the developer workstation—not from Sentinel—use Android's `dpm`
development command:

```shell
adb shell dpm set-device-owner \
  com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver
```

If Android rejects the command, do not bypass its checks or use hidden APIs.
Confirm that the test device is in a provisionable state, that the debug
application is installed, and that the component above matches the installed
package. Re-create or restore only the disposable test device if a clean state
is required.

## Validation

Open Sentinel and inspect the test-device diagnostics. A successful validation
must show:

- management mode: Device Owner;
- Device Owner verification: `VERIFIED_DEVICE_OWNER`;
- expected receiver registered and active;
- Profile Owner: no; and
- no configuration or availability errors.

Any `NOT_DEVICE_OWNER`, `CONFIGURATION_ERROR`, or `UNAVAILABLE` result must be
treated as a failed validation. The application does not attempt to repair,
repeat, or initiate provisioning.

### Reversible screen-capture policy

Only after Device Owner validation succeeds, use the
**TEST DEVICE — SCREEN CAPTURE POLICY** section:

1. Select **Disable screen capture**. The operation must report matching
   requested and observed disabled states.
2. Select **Enable screen capture** to restore the original policy. The
   operation must report matching requested and observed enabled states.
3. Record the correlation ID shown for each operation.

Any denial, failure, unavailable state, or post-write read-back mismatch is a
failed test. Do not bypass the validation or attempt another policy operation.

### Reversible camera policy

Only after Device Owner validation succeeds, use the
**TEST DEVICE — CAMERA POLICY** section:

1. Select **Disable camera**. Android must block camera access and Sentinel must
   report matching requested and observed disabled states.
2. Select **Enable camera** to restore the original policy. Android must allow
   camera access and Sentinel must report matching requested and observed
   enabled states.
3. Record the authoritative correlation ID returned by Sentinel. The trigger's
   caller request ID is diagnostic input and is not approval identity.

Any denial, validation uncertainty, exception, unavailable state, or post-write
read-back mismatch is a failed test. Do not bypass validation.

Production QR enrollment is intentionally not implemented.
