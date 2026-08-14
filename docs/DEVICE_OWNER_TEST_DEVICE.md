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

Open Sentinel and inspect the Device Owner security dashboard. A successful
validation must show:

- header banner: **Device Owner verified**;
- management mode: Device Owner;
- Device Owner validation: Verified Device Owner;
- expected receiver registered and active in technical details;
- Profile Owner: no; and
- no configuration or availability errors.

Any **Not Device Owner**, **Configuration error**, or **Validation
unavailable** result must be treated as a failed validation. The application
does not attempt to repair, repeat, or initiate provisioning. Security
controls may remain visible for diagnostics; they still fail through the
trusted authorization path.

### Reversible screen-capture policy

Only after Device Owner validation succeeds, use the **Screen capture** card:

1. Select **Disable**. The card and session activity must report **Applied**.
2. Select **Enable** to restore the original policy. The operation must report
   **Applied**.
3. Record the correlation ID shown on the card and in session activity.

Any **Denied**, **Failed**, unavailable state, or post-write confirmation
mismatch is a failed test. Do not bypass the validation or attempt another
policy operation.

### Reversible camera policy

Only after Device Owner validation succeeds, use the **Camera** card:

1. Select **Disable**. Android must block camera access and Sentinel must
   report **Applied**.
2. Select **Enable** to restore the original policy. Android must allow
   camera access and Sentinel must report **Applied**.
3. Record the authoritative correlation ID returned by Sentinel. The trigger's
   caller request ID is diagnostic input and is not approval identity.

Any **Denied**, validation uncertainty, exception, unavailable state, or
post-write confirmation mismatch is a failed test. Do not bypass validation.

### Reversible status-bar policy

Status-bar controls require Android 14 (API 34) or newer. On older devices
the card must show that the capability is unavailable and must not claim
success. Only after Device Owner validation succeeds on API 34+, use the
**Status bar** card the same way as the other two policies.

### Session activity

The dashboard **Session activity** list is NON-PERSISTENT in-memory history
for the current app session. It is cleared when the app restarts and is not
an audit log. Use it only to confirm the latest Applied / Denied / Failed
outcome and correlation ID during this development workflow.

Production QR enrollment is intentionally not implemented.
