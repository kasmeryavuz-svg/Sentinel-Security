# Sentinel Security

Minimal, fail-safe Android device-management skeleton.

## Safety

This project contains no real wipe, factory-reset, deletion, Device Owner
provisioning, accessibility, shell, or ADB functionality. Its passive
`DeviceAdminReceiver` declares only Android's `disable-camera` metadata policy
and no callbacks. The only wipe-shaped action is `SafeMockWipeAction`; it is
isolated to fail-safe simulation mode, logs `WIPE WOULD EXECUTE`, and returns a
simulated result.

All sensitive-action requests follow one controlled path:

```text
UI -> SensitiveActionController -> TriggerEvaluator -> DecisionEngine
   -> private approval -> ActionExecutor -> typed policy action
   -> device-management backend -> verified DevicePolicyManager boundary
```

Invalid, missing, expired, unavailable, disabled, or exceptional states are
denied. The Android app can access only `SensitiveActionController.submit`,
which accepts trigger input. Decisions, approval capabilities, device actions,
and the executor are internal to the separate `sensitive-actions` implementation
module. App code compiles only against the submit-only `sensitive-actions-api`.
Approvals are identity-bound, single-use, and accepted only by the executor
paired with the issuing decision engine. Authoritative correlation IDs are
created inside the controller; caller request IDs are diagnostic input only.

## Packages

- `ui`: status-only Android UI.
- `trigger`: trigger input parsing and validation.
- `decision`: centralized, fail-safe sensitive-action decisions.
- `action`: public trigger-based controller, internal controlled executor, and
  safe mock action.
- `device-management`: typed, allowlisted device-policy boundary.
- `persistence`: state storage boundary and in-memory implementation.
- `logging`: structured logging abstraction and Android implementation.
- `app`: dependency wiring and application entry point.

The `app` module contains Android UI and dependency wiring. The
`device-management` module exposes a read-only status model and contains all
direct `DevicePolicyManager` queries. It also reports Device Owner and Profile
Owner provisioning readiness without starting provisioning or exposing
provisioning intents, and validates an already-provisioned Device Owner using
read-only package, receiver, ownership, and active-admin checks. Its build
fails if a non-allowlisted policy operation appears in production source. The
only mutators are screen-capture and camera disable toggles, each followed by
an immediate read-back using the expected admin component. Repository-wide DPM
boundary checks and merged debug/release DeviceAdmin metadata checks prevent app
or variant overrides from widening those allowlists.
See `docs/DEVICE_OWNER_TEST_DEVICE.md` for the development-only disposable
test-device workflow and `docs/POLICY_ARCHITECTURE.md` for trust boundaries,
approval lifecycle, mutation verification, and the safe capability checklist.
The pure Kotlin `sensitive-actions` module independently owns the decision and
execution security boundary behind its narrow API module.

## Build

```bash
./gradlew test
./gradlew assembleDebug
```