# Sentinel Security

Minimal, fail-safe Android device-management skeleton.

## Safety

This project contains no real wipe, factory-reset, deletion, Device Admin,
Device Owner provisioning, accessibility, shell, or ADB functionality. The
only wipe-shaped action is `SafeMockWipeAction`; it logs
`WIPE WOULD EXECUTE` and returns a simulated result.

All sensitive-action requests follow one controlled path:

```text
Trigger -> SensitiveActionController -> DecisionEngine -> private approval
                                                  -> ActionExecutor
```

Invalid, missing, expired, unavailable, disabled, or exceptional states are
denied. The Android app can access only `SensitiveActionController.submit`,
which accepts trigger input. Decisions, approval capabilities, device actions,
and the executor are internal to the separate `sensitive-actions` module.
Approvals are identity-bound, single-use, and accepted only by the executor
paired with the issuing decision engine.

## Packages

- `ui`: status-only Android UI.
- `trigger`: trigger input parsing and validation.
- `decision`: centralized, fail-safe sensitive-action decisions.
- `action`: public trigger-based controller, internal controlled executor, and
  safe mock action.
- `policy`: non-destructive device-policy boundary.
- `persistence`: state storage boundary and in-memory implementation.
- `logging`: structured logging abstraction and Android implementation.
- `app`: dependency wiring and application entry point.

The `app` module contains Android UI and platform adapters. The pure Kotlin
`sensitive-actions` module owns the decision and execution security boundary.

## Build

```bash
./gradlew test
./gradlew assembleDebug
```