# Sentinel Security

Minimal, fail-safe Android device-management skeleton.

## Safety

This project contains no real wipe, factory-reset, deletion, accessibility,
shell, or in-app ADB functionality. Sentinel implements the public Android 12+
fully-managed provisioning contract and is ready to participate when an OS
setup flow exposes that QR entry point. GrapheneOS production QR enrollment is
not yet confirmed; until a real supported GrapheneOS setup flow accepts it,
ADB `dpm set-device-owner` remains the practical reference/testing Device
Owner assignment method. The application itself does not factory-reset
devices, host or upload APKs, or assign Device Owner via ADB.
Its `DeviceAdminReceiver` declares only Android's `disable-camera` metadata
policy. Provisioning completion is log-only and does not mutate policy. The
only wipe-shaped action is `SafeMockWipeAction`; it is
isolated to fail-safe simulation mode, logs `WIPE WOULD EXECUTE`, and returns a
simulated result.

All sensitive-action requests follow one controlled path:

```text
UI -> SensitiveActionController -> TriggerEvaluator -> DecisionEngine
   -> private approval -> ActionExecutor -> typed policy action
   -> device-management backend -> verified DevicePolicyManager boundary
```

Invalid, missing, expired, unavailable, disabled, or exceptional states are
denied. The Android app depends only on the `device-management` facade and can
access only `SensitiveActionController.submit` plus read-only status providers.
The `device-management-impl` and `sensitive-actions` implementation artifacts are
absent from every app compile classpath and are packaged only at runtime.
Approvals are identity-bound, single-use, and accepted only by the executor
paired with the issuing decision engine. Authoritative correlation IDs are
created inside the controller; caller request IDs are diagnostic input only.

## Packages

- `ui`: product Device Owner security dashboard. It reads status through
  public providers, including the read-only durable audit log, and submits
  only the six trusted commands through `SensitiveActionController`.
- `provisioning`: Android 12+ `GET_PROVISIONING_MODE` and
  `ADMIN_POLICY_COMPLIANCE` activities. Fully-managed Device Owner only;
  no policy mutation.
- `provisioning-qr`: local workstation generator for QR provisioning JSON.
  Not packaged into the Android application.
- `trigger`: trigger input parsing and validation.
- `decision`: centralized, fail-safe sensitive-action decisions.
- `action`: public trigger-based controller, internal controlled executor, and
  safe mock action.
- `device-management`: minimal public Android facade.
- `device-management-api`: facade contracts and read-only status models.
- `device-management-impl`: typed, allowlisted Android policy implementation.
- `persistence`: state storage boundary and in-memory implementation.
- `logging`: structured logging abstraction and Android implementation.
- `app`: dependency wiring and application entry point.

The `app` module contains Android UI, facade wiring, and the Android 12+
fully-managed provisioning activities. The
`device-management-impl` module contains all direct `DevicePolicyManager`
queries. It also reports Device Owner and Profile
Owner provisioning readiness without starting provisioning or exposing
provisioning intents, and validates an already-provisioned Device Owner using
read-only package, receiver, ownership, and active-admin checks. Its build
fails if a non-allowlisted policy operation appears in compiled production
bytecode. The
only mutators are screen-capture, camera, and status-bar disable toggles, each
followed by
an immediate read-back using the expected admin component (status-bar read-back
uses `isStatusBarDisabled()` and requires API 34+). Repository-wide DPM
boundary checks, dynamic/reflection/native guards, and per-variant effective
DeviceAdmin metadata checks prevent app or variant overrides from widening those
allowlists.
See `docs/AUDIT.md` for durable local audit persistence and its tamper-evidence
limits, `docs/GRAPHENEOS_ENROLLMENT.md` for GrapheneOS enrollment status (QR is
standards-ready, not yet confirmed on GrapheneOS SetupWizard2),
`docs/QR_PROVISIONING.md` for local QR JSON generation,
`docs/DEVICE_OWNER_TEST_DEVICE.md` for the development-only disposable
test-device workflow, and `docs/POLICY_ARCHITECTURE.md` for trust boundaries,
approval lifecycle, mutation verification, and the safe capability checklist.
The pure Kotlin `sensitive-actions` module independently owns the decision and
execution security boundary behind its narrow API module.

## Build

```bash
./gradlew test
./gradlew assembleDebug
```