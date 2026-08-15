# Android and GrapheneOS wipe-platform preflight

Updated for the Checkpoint 17B entry review. Research only. This
document does **not** add a destructive API to executable production
code.

Research only. This document does **not** add a destructive API to
executable production code.

Every claim is classified:

- `VERIFIED_ANDROID` — official Android SDK / API reference
- `VERIFIED_GRAPHENEOS` — current primary GrapheneOS documentation
- `REPO_PROVEN` — proven by this repository’s sources or tests
- `UNRESOLVED_REQUIRES_DEVICE_TEST` — must not be guessed

**NO REAL WIPE IS IMPLEMENTED.**
**NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED.**

## Repository SDK coordinates

| Coordinate | Value | Classification |
| --- | --- | --- |
| `compileSdk` | 36 | `REPO_PROVEN` (`app/build.gradle.kts`) |
| `minSdk` | 26 | `REPO_PROVEN` |
| `targetSdk` | 36 | `REPO_PROVEN` |
| Production DPM mutators | `setScreenCaptureDisabled`, `setCameraDisabled`, `setStatusBarDisabled` | `REPO_PROVEN` |
| DeviceAdmin metadata | exactly `disable-camera` | `REPO_PROVEN` |

Sources consulted:

- [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [DeviceAdminInfo](https://developer.android.com/reference/android/app/admin/DeviceAdminInfo)
- [API 34 diff (wipeDevice added)](https://developer.android.com/sdk/api_diff/34/changes/android.app.admin.DevicePolicyManager)
- [GrapheneOS FAQ](https://grapheneos.org/faq)
- [GrapheneOS features](https://grapheneos.org/features)
- [GrapheneOS install / verified boot](https://grapheneos.org/install/web)

Forum posts are **not** treated as authoritative.

## Android destructive APIs

| Claim | Classification |
| --- | --- |
| `DevicePolicyManager.wipeData(int)` exists since API 8 | `VERIFIED_ANDROID` |
| `DevicePolicyManager.wipeData(int, CharSequence)` exists since API 28 | `VERIFIED_ANDROID` |
| `DevicePolicyManager.wipeDevice(int)` exists since API 34 | `VERIFIED_ANDROID` |
| This repository compiles against SDK 36, so the compile classpath includes API 34+ | `REPO_PROVEN` + `VERIFIED_ANDROID` |
| `wipeDevice` is absent before API 34. Default for API 26–33: unsupported, fail closed | `VERIFIED_ANDROID` (API added-in) |
| Calling `wipeData` from the primary / last full user as an app targeting API 34+ (`UPSIDE_DOWN_CAKE`) throws `IllegalStateException` | `VERIFIED_ANDROID` |
| Sentinel `targetSdk` is 36, so a future device-wide wipe cannot use `wipeData` on the primary user | `REPO_PROVEN` + `VERIFIED_ANDROID` |
| Apps that want to wipe the entire device should use `wipeDevice` | `VERIFIED_ANDROID` |
| `wipeDevice` requires the calling Device Owner or organization-owned Profile Owner to have requested `DeviceAdminInfo.USES_POLICY_WIPE_DATA`; otherwise `SecurityException` | `VERIFIED_ANDROID` |
| `USES_POLICY_WIPE_DATA` is declared by a `wipe-data` tag under `uses-policies` | `VERIFIED_ANDROID` |
| Current Sentinel metadata does not declare `wipe-data` | `REPO_PROVEN` |
| Privileged alternatives (`MASTER_CLEAR`, or `MANAGE_DEVICE_POLICY_WIPE_DATA` plus `MANAGE_DEVICE_POLICY_ACROSS_USERS`) exist in the public throws clause and are **not** Sentinel’s intended path | `VERIFIED_ANDROID` (documented alternative); refused by Checkpoint 16 |
| `clearDeviceOwnerApp` is deprecated and advises factory reset instead; it remains forbidden | `VERIFIED_ANDROID` + `REPO_PROVEN` (DEX denylist) |

### Flags (documented)

| Flag | Added | Documented on | Classification |
| --- | --- | --- | --- |
| `WIPE_EXTERNAL_STORAGE` | API 9 | `wipeData` (also listed for `wipeDevice` flags) | `VERIFIED_ANDROID` |
| `WIPE_RESET_PROTECTION_DATA` | API 22 | `wipeData`; Device Owner only or `SecurityException` | `VERIFIED_ANDROID` |
| `WIPE_EUICC` | API 28 | `wipeDevice` | `VERIFIED_ANDROID` |
| `WIPE_SILENTLY` | API 29 | `wipeData`; illegal on the reason-bearing `wipeData` overload | `VERIFIED_ANDROID` |

`wipeDevice` for apps targeting API 34+:

- explicitly requests a device factory reset
- respects supported flags regardless of calling user

`VERIFIED_ANDROID`.

For apps targeting API 33 or below, a non-system-user `wipeDevice` may
fall back to a user wipe and ignore some flags. Sentinel targets API 36;
the API 33 fallback is recorded because `minSdk` is 26.
`VERIFIED_ANDROID` + `REPO_PROVEN`.

There is no getter that can prove a factory reset completed.
`VERIFIED_ANDROID` (no such API) + Checkpoint 16 design.

Profile-owner “relinquish device” `wipeData` behavior is out of scope.
Sentinel is a fully-managed Device Owner DPC. `REPO_PROVEN`.

## GrapheneOS

| Claim | Classification |
| --- | --- |
| GrapheneOS on supported Pixel hardware is Sentinel’s production target | `REPO_PROVEN` (`docs/GRAPHENEOS_ENROLLMENT.md`) |
| A factory reset of the device wipes all Weaver slots on the secure element | `VERIFIED_GRAPHENEOS` (FAQ) |
| GrapheneOS does **not** provide Google Factory Reset Protection | `VERIFIED_GRAPHENEOS` (FAQ: “Does GrapheneOS provide Factory Reset Protection?” → No) |
| Duress PIN/password irreversibly wipes the device and installed eSIMs; this is an OS unlock-credential feature, not a DPC API | `VERIFIED_GRAPHENEOS` (features overview) |
| Unlocking the bootloader performs the same secure-element wipe as a factory reset | `VERIFIED_GRAPHENEOS` (install guide) |
| GrapheneOS QR Device Owner enrollment is not yet confirmed | `REPO_PROVEN` (`docs/GRAPHENEOS_ENROLLMENT.md`) |
| Behavior of `DevicePolicyManager.wipeDevice` / `wipeData` on GrapheneOS Pixels | `UNRESOLVED_REQUIRES_DEVICE_TEST` |
| Interaction of `WIPE_RESET_PROTECTION_DATA` with GrapheneOS (no Google FRP; owner-binding / installer verification) | `UNRESOLVED_REQUIRES_DEVICE_TEST` |
| Whether `WIPE_EUICC` is appropriate or effective on the production Pixel target via DPM | `UNRESOLVED_REQUIRES_DEVICE_TEST` |
| Whether any Sentinel product policy should ever allow `WIPE_SILENTLY` | Unresolved product policy; Checkpoint 16 recommends forbidding it until explicit review |
| Whether a user-scoped wipe is ever in scope | Product default: out of scope (`USER_SCOPED_WIPE` is denied) |
| PackageManager signing-info binding | Unresolved optional improvement; not implemented |
| Hardware-backed confirmation dialog availability to this DPC | `UNRESOLVED_REQUIRES_DEVICE_TEST` |

Do not resolve the unresolved rows by guessing. Default until resolved:
**NO WIPE**.

## Stock Android / Pixel (documented)

On stock Android, including Pixel factory images, the public API 34+
`wipeDevice` contract is the documented whole-device path for an app
with `targetSdk` 34+. Sentinel `targetSdk` is 36, so a future
device-wide wipe cannot use `wipeData` on the primary user.
`VERIFIED_ANDROID` + `REPO_PROVEN`.

This is **not** a hardware test. Expected behavior on stock Android /
Pixel is the documented platform behavior only. No disposable-device
validation has been performed.

Device Owner remains mandatory for the intended future path. Profile
Owner is out of scope. `REPO_PROVEN`.

Required DeviceAdmin metadata for any future real implementation is
`<wipe-data>` (`USES_POLICY_WIPE_DATA`). Current metadata is exactly
`disable-camera`. `REPO_PROVEN`.

Expected scope, if ever implemented later, is whole-device factory
reset (`DEVICE_FACTORY_RESET`). `USER_SCOPED_WIPE` is deny-only.
`REPO_PROVEN`.

## 17A / 17B executable boundary

17A and 17B production and simulation sources do not invoke `wipeData`
or `wipeDevice`, do not declare `<wipe-data>`, and do not add a
destructive DPM wrapper. Simulation ends at
`Checkpoint17ASimulationSink` and is not production-reachable.
`REPO_PROVEN`.

GrapheneOS `wipeDevice` / `wipeData` behavior remains
`UNRESOLVED_REQUIRES_DEVICE_TEST`. Do not resolve that row by guessing.

See `docs/WIPE_17B_ENTRY_REVIEW.md`.
